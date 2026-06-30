package com.ucuenca.gestion.models.logic

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll
import scalikejdbc._
import com.ucuenca.gestion.utils.DatabaseConnection
import com.ucuenca.gestion.models.enums.{RolUsuario, OrigenCreacionActividad, EstadoActividad}
import com.ucuenca.gestion.models.db.CronogramaRepository

class ValidacionActividadesLogicSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  val companyRuc = "0909090909003"
  val studentCi = "1818181818"
  val tutorEmpCi = "1919191919"
  val tutorAcadCi = "2020202020"

  var pdfMallaId: Int = _
  var practicaId: Int = _
  var act1Id: Int = _
  var act2Id: Int = _

  override def beforeAll(): Unit = {
    DatabaseConnection.initialize()

    DB.localTx { implicit session =>
      // Clean up previous test data
      sql"DELETE FROM actividad_cronograma WHERE id_practica_ref IN (SELECT id_practica FROM practica_registro WHERE ci_estudiante_ref = ${studentCi})".update.apply()
      sql"DELETE FROM practica_registro WHERE ci_estudiante_ref = ${studentCi}".update.apply()
      sql"DELETE FROM estudiante_perfil WHERE identificacion = ${studentCi}".update.apply()
      sql"DELETE FROM tutor_empresarial_perfil WHERE identificacion = ${tutorEmpCi}".update.apply()
      sql"DELETE FROM empresa_perfil WHERE identificacion = ${companyRuc}".update.apply()
      sql"DELETE FROM usuario WHERE identificacion IN (${studentCi}, ${tutorEmpCi}, ${tutorAcadCi}, ${companyRuc})".update.apply()
      sql"DELETE FROM archivo_pdf WHERE nombre_original = 'malla_test_val.pdf'".update.apply()

      // 1. Create PDF
      pdfMallaId = sql"INSERT INTO archivo_pdf (tipo_archivo, nombre_original, ruta_segura_servidor) VALUES ('T9_MALLA_ACADEMICA'::tipo_archivo_pdf, 'malla_test_val.pdf', 'uploads/m_v.pdf')".updateAndReturnGeneratedKey.apply().toInt

      // 2. Create Company
      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${companyRuc}, 'Empresa Test Val', 'emp_val@test.com', 'EMPRESA'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()
      sql"INSERT INTO empresa_perfil (identificacion, direccion_matriz, mision, vision, estado_convenio) VALUES (${companyRuc}, 'Matriz Val', 'Mision', 'Vision', 'FORMALIZADO'::estado_convenio)".update.apply()

      // 3. Create Tutor Empresarial
      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${tutorEmpCi}, 'Tutor Emp Val', 'tut_emp_v@test.com', 'TUTOR_EMPRESARIAL'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()
      sql"INSERT INTO tutor_empresarial_perfil (identificacion, empresa_id_ref, telefono_contacto) VALUES (${tutorEmpCi}, ${companyRuc}, '0912121212')".update.apply()

      // 4. Create Student
      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${studentCi}, 'Estudiante Test Val', 'est_val@test.com', 'ESTUDIANTE'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()
      sql"INSERT INTO estudiante_perfil (identificacion, ciclo_actual, id_carrera_ref, estado_matricula, estado_estudiante_practica, malla_academica_pdf) VALUES (${studentCi}, 7, 1, 'REGULAR'::estado_matricula, 'CON_PRACTICA_ACTIVA'::estado_estudiante_practica, ${pdfMallaId})".update.apply()

      // 5. Create Tutor Academico
      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${tutorAcadCi}, 'Tutor Acad Val', 'tut_acad_v@test.com', 'TUTOR_ACADEMICO'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()

      // 6. Create practica_registro
      practicaId = sql"""
        INSERT INTO practica_registro (ci_estudiante_ref, ruc_empresa_ref, id_tutor_academico_ref, id_tutor_empresarial_ref, origen_rama, estado_cronograma, horas_totales_requeridas)
        VALUES (${studentCi}, ${companyRuc}, ${tutorAcadCi}, ${tutorEmpCi}, 'BOLSA_EMPLEO'::origen_rama, 'F1_PENDIENTE'::estado_cronograma, 160)
      """.updateAndReturnGeneratedKey.apply().toInt

      // 7. Create Activities in PENDIENTE
      act1Id = sql"""
        INSERT INTO actividad_cronograma (id_practica_ref, numero_secuencial, descripcion_tarea, origen_creacion, estado_actividad, fecha_registro)
        VALUES (${practicaId}, 1, 'Actividad A Evaluar 1', 'ESTUDIANTE'::origen_creacion_actividad, 'PENDIENTE'::estado_actividad, CURRENT_DATE)
      """.updateAndReturnGeneratedKey.apply().toInt

      act2Id = sql"""
        INSERT INTO actividad_cronograma (id_practica_ref, numero_secuencial, descripcion_tarea, origen_creacion, estado_actividad, fecha_registro)
        VALUES (${practicaId}, 2, 'Actividad A Evaluar 2', 'TUTOR_EMPRESARIAL'::origen_creacion_actividad, 'PENDIENTE'::estado_actividad, CURRENT_DATE)
      """.updateAndReturnGeneratedKey.apply().toInt
    }
  }

  override def afterAll(): Unit = {
    DB.localTx { implicit session =>
      sql"DELETE FROM actividad_cronograma WHERE id_practica_ref = ${practicaId}".update.apply()
      sql"DELETE FROM practica_registro WHERE id_practica = ${practicaId}".update.apply()
      sql"DELETE FROM estudiante_perfil WHERE identificacion = ${studentCi}".update.apply()
      sql"DELETE FROM tutor_empresarial_perfil WHERE identificacion = ${tutorEmpCi}".update.apply()
      sql"DELETE FROM empresa_perfil WHERE identificacion = ${companyRuc}".update.apply()
      sql"DELETE FROM usuario WHERE identificacion IN (${studentCi}, ${tutorEmpCi}, ${tutorAcadCi}, ${companyRuc})".update.apply()
      sql"DELETE FROM archivo_pdf WHERE id_archivo_pdf = ${pdfMallaId}".update.apply()
    }
  }

  "ValidacionActividadesLogic" should "listar estudiantes asignados al tutor academico con contador de pendientes" in {
    val result = ValidacionActividadesLogic.listarEstudiantes(tutorAcadCi)
    result.isRight shouldBe true
    val lista = result.toOption.get
    lista.exists(_.idPractica == practicaId) shouldBe true
    
    val student = lista.find(_.idPractica == practicaId).get
    student.nombreEstudiante shouldBe "Estudiante Test Val"
    student.nombreEmpresa shouldBe "Empresa Test Val"
    student.cicloActual shouldBe 7
    student.pendientesCount shouldBe 2
  }

  it should "listar las actividades pendientes individuales de la practica seleccionada" in {
    val result = ValidacionActividadesLogic.listarActividadesPendientes(practicaId)
    result.isRight shouldBe true
    val lista = result.toOption.get
    lista.size shouldBe 2
    lista.exists(_.idActividad == act1Id) shouldBe true
    lista.exists(_.idActividad == act2Id) shouldBe true
  }

  it should "permitir la aprobacion rapida de una tarea propuesta" in {
    val result = ValidacionActividadesLogic.aprobarActividad(act1Id)
    result shouldBe Right(())

    // Verificar en DB
    val dbState = DB.readOnly { implicit session =>
      sql"SELECT estado_actividad, comentario_observacion FROM actividad_cronograma WHERE id_actividad = ${act1Id}"
        .map(rs => (rs.string("estado_actividad"), rs.stringOpt("comentario_observacion")))
        .single.apply()
    }
    dbState shouldBe Some(("APROBADA", None))
  }

  it should "rechazar el rechazo si la justificacion esta vacia o es muy corta" in {
    val r1 = ValidacionActividadesLogic.rechazarActividad(act2Id, "")
    r1 shouldBe Left(ValidacionActividadesFailure.Validacion("Las observaciones de corrección son obligatorias al rechazar y deben tener al menos 5 caracteres."))

    val r2 = ValidacionActividadesLogic.rechazarActividad(act2Id, "    ")
    r2 shouldBe Left(ValidacionActividadesFailure.Validacion("Las observaciones de corrección son obligatorias al rechazar y deben tener al menos 5 caracteres."))

    val r3 = ValidacionActividadesLogic.rechazarActividad(act2Id, "Aju")
    r3 shouldBe Left(ValidacionActividadesFailure.Validacion("Las observaciones de corrección son obligatorias al rechazar y deben tener al menos 5 caracteres."))
  }

  it should "permitir rechazar una tarea ingresando el motivo de correccion obligatorio" in {
    val result = ValidacionActividadesLogic.rechazarActividad(act2Id, "Mejorar la redacción técnica de la base de datos")
    result shouldBe Right(())

    // Verificar en DB
    val dbState = DB.readOnly { implicit session =>
      sql"SELECT estado_actividad, comentario_observacion FROM actividad_cronograma WHERE id_actividad = ${act2Id}"
        .map(rs => (rs.string("estado_actividad"), rs.stringOpt("comentario_observacion")))
        .single.apply()
    }
    dbState shouldBe Some(("RECHAZADA", Some("Mejorar la redacción técnica de la base de datos")))
  }

  it should "bloquear cualquier modificacion posterior de actividades ya evaluadas (inmutabilidad)" in {
    // Intentar evaluar de nuevo act1 (que ya esta aprobada)
    val r1 = ValidacionActividadesLogic.aprobarActividad(act1Id)
    r1 shouldBe Left(ValidacionActividadesFailure.Validacion("La actividad seleccionada ya ha sido evaluada y no se puede modificar."))

    val r2 = ValidacionActividadesLogic.rechazarActividad(act1Id, "Otro comentario de prueba")
    r2 shouldBe Left(ValidacionActividadesFailure.Validacion("La actividad seleccionada ya ha sido evaluada y no se puede modificar."))

    // Intentar evaluar de nuevo act2 (que ya esta rechazada)
    val r3 = ValidacionActividadesLogic.aprobarActividad(act2Id)
    r3 shouldBe Left(ValidacionActividadesFailure.Validacion("La actividad seleccionada ya ha sido evaluada y no se puede modificar."))
  }
}
