package com.ucuenca.gestion.models.logic

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll
import scalikejdbc._
import com.ucuenca.gestion.utils.DatabaseConnection
import com.ucuenca.gestion.models.enums.{RolUsuario, OrigenCreacionActividad, EstadoActividad}
import com.ucuenca.gestion.models.db.CronogramaRepository

class CronogramaLogicSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  val companyRuc = "0909090909002"
  val studentCi = "1616161616"
  val tutorEmpCi = "1717171717"
  var pdfMallaId: Int = _
  var practicaId: Int = _

  override def beforeAll(): Unit = {
    DatabaseConnection.initialize()

    // Ensure enum values exist in DB (ALTER TYPE cannot be in a transaction)
    DB.autoCommit { implicit session =>
      try {
        sql"ALTER TYPE estado_actividad ADD VALUE 'DESCARTAR_ESTUDIANTE'".execute.apply()
      } catch {
        case _: Exception => // ignore if already exists
      }
      try {
        sql"ALTER TYPE estado_actividad ADD VALUE 'DESCARTAR_TUTOR'".execute.apply()
      } catch {
        case _: Exception => // ignore if already exists
      }
    }

    DB.localTx { implicit session =>
      // Clean up previous test data in reverse dependency order
      sql"DELETE FROM actividad_cronograma WHERE id_practica_ref IN (SELECT id_practica FROM practica_registro WHERE ci_estudiante_ref = ${studentCi})".update.apply()
      sql"DELETE FROM practica_registro WHERE ci_estudiante_ref = ${studentCi}".update.apply()
      sql"DELETE FROM estudiante_perfil WHERE identificacion = ${studentCi}".update.apply()
      sql"DELETE FROM tutor_empresarial_perfil WHERE identificacion = ${tutorEmpCi}".update.apply()
      sql"DELETE FROM empresa_perfil WHERE identificacion = ${companyRuc}".update.apply()
      sql"DELETE FROM usuario WHERE identificacion IN (${studentCi}, ${tutorEmpCi}, ${companyRuc})".update.apply()
      sql"DELETE FROM archivo_pdf WHERE nombre_original = 'malla_test_crono.pdf'".update.apply()

      // 1. Create PDF
      pdfMallaId = sql"INSERT INTO archivo_pdf (tipo_archivo, nombre_original, ruta_segura_servidor) VALUES ('T9_MALLA_ACADEMICA'::tipo_archivo_pdf, 'malla_test_crono.pdf', 'uploads/m_c.pdf')".updateAndReturnGeneratedKey.apply().toInt

      // 2. Create Company
      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${companyRuc}, 'Empresa Test Crono', 'emp_crono@test.com', 'EMPRESA'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()
      sql"INSERT INTO empresa_perfil (identificacion, direccion_matriz, mision, vision, estado_convenio) VALUES (${companyRuc}, 'Matriz Crono', 'Mision', 'Vision', 'FORMALIZADO'::estado_convenio)".update.apply()

      // 3. Create Tutor Empresarial
      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${tutorEmpCi}, 'Tutor Emp Crono', 'tut_emp_c@test.com', 'TUTOR_EMPRESARIAL'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()
      sql"INSERT INTO tutor_empresarial_perfil (identificacion, empresa_id_ref, telefono_contacto) VALUES (${tutorEmpCi}, ${companyRuc}, '0987654321')".update.apply()

      // 4. Create Student
      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${studentCi}, 'Estudiante Test Crono', 'est_crono@test.com', 'ESTUDIANTE'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()
      sql"INSERT INTO estudiante_perfil (identificacion, ciclo_actual, id_carrera_ref, estado_matricula, estado_estudiante_practica, malla_academica_pdf) VALUES (${studentCi}, 7, 1, 'REGULAR'::estado_matricula, 'CON_PRACTICA_ACTIVA'::estado_estudiante_practica, ${pdfMallaId})".update.apply()

      // 5. Create practica_registro
      practicaId = sql"""
        INSERT INTO practica_registro (ci_estudiante_ref, ruc_empresa_ref, id_tutor_academico_ref, id_tutor_empresarial_ref, origen_rama, estado_cronograma, horas_totales_requeridas)
        VALUES (${studentCi}, ${companyRuc}, NULL, ${tutorEmpCi}, 'EMPRESA_PROPIA'::origen_rama, 'F1_PENDIENTE'::estado_cronograma, 160)
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
      sql"DELETE FROM usuario WHERE identificacion IN (${studentCi}, ${tutorEmpCi}, ${companyRuc})".update.apply()
      sql"DELETE FROM archivo_pdf WHERE id_archivo_pdf = ${pdfMallaId}".update.apply()
    }
  }

  "CronogramaLogic" should "permitir al estudiante buscar su practica activa" in {
    val result = CronogramaLogic.buscarPracticaEstudiante(studentCi)
    result.isRight shouldBe true
    val prOpt = result.toOption.get
    prOpt.isDefined shouldBe true
    prOpt.get.idPractica shouldBe practicaId
  }

  it should "permitir al tutor empresarial listar los estudiantes a su cargo" in {
    val result = CronogramaLogic.listarEstudiantesTutor(tutorEmpCi)
    result.isRight shouldBe true
    val lista = result.toOption.get
    lista shouldBe List((practicaId, "Estudiante Test Crono"))
  }

  it should "rechazar propuestas de actividad vacias o demasiado cortas" in {
    CronogramaLogic.proponerActividad(practicaId, "", OrigenCreacionActividad.ESTUDIANTE) shouldBe Left(CronogramaFailure.Validacion("La descripción de la actividad es obligatoria y debe contener al menos 5 caracteres."))
    CronogramaLogic.proponerActividad(practicaId, "    ", OrigenCreacionActividad.ESTUDIANTE) shouldBe Left(CronogramaFailure.Validacion("La descripción de la actividad es obligatoria y debe contener al menos 5 caracteres."))
    CronogramaLogic.proponerActividad(practicaId, "Dev", OrigenCreacionActividad.ESTUDIANTE) shouldBe Left(CronogramaFailure.Validacion("La descripción de la actividad es obligatoria y debe contener al menos 5 caracteres."))
  }

  it should "permitir al estudiante y tutor registrar actividades de forma incremental y correcta trazabilidad" in {
    // 1. Student registers first activity
    val act1Result = CronogramaLogic.proponerActividad(practicaId, "Definición de requerimientos del sistema", OrigenCreacionActividad.ESTUDIANTE)
    act1Result shouldBe Right(())

    // 2. Tutor registers second activity
    val act2Result = CronogramaLogic.proponerActividad(practicaId, "Diseño de la base de datos relacional", OrigenCreacionActividad.TUTOR_EMPRESARIAL)
    act2Result shouldBe Right(())

    // 3. Load activities and verify fields
    val listResult = CronogramaLogic.listarActividades(practicaId)
    listResult.isRight shouldBe true
    val actividades = listResult.toOption.get
    actividades.size shouldBe 2

    val a1 = actividades.find(_.numeroSecuencial == 1).get
    a1.descripcionTarea shouldBe "Definición de requerimientos del sistema"
    a1.origenCreacion shouldBe OrigenCreacionActividad.ESTUDIANTE
    a1.estadoActividad shouldBe EstadoActividad.PENDIENTE

    val a2 = actividades.find(_.numeroSecuencial == 2).get
    a2.descripcionTarea shouldBe "Diseño de la base de datos relacional"
    a2.origenCreacion shouldBe OrigenCreacionActividad.TUTOR_EMPRESARIAL
    a2.estadoActividad shouldBe EstadoActividad.PENDIENTE
  }

  it should "permitir descarte logico de actividades e ignorarlas en los listados activos" in {
    // Obtener actividades
    val actividades = CronogramaLogic.listarActividades(practicaId).toOption.get
    val a1 = actividades.find(_.numeroSecuencial == 1).get
    val a2 = actividades.find(_.numeroSecuencial == 2).get

    // 1. Student discards a1
    val desc1 = CronogramaLogic.descartarActividad(a1.idActividad, EstadoActividad.DESCARTAR_ESTUDIANTE)
    desc1 shouldBe Right(())

    // 2. Tutor discards a2
    val desc2 = CronogramaLogic.descartarActividad(a2.idActividad, EstadoActividad.DESCARTAR_TUTOR)
    desc2 shouldBe Right(())

    // 3. Verify they are excluded from active listing
    val listaActiva = CronogramaLogic.listarActividades(practicaId).toOption.get
    listaActiva.exists(a => a.idActividad == a1.idActividad || a.idActividad == a2.idActividad) shouldBe false

    // 4. Verify they exist in DB with logical discard status
    val dbStates = DB.readOnly { implicit session =>
      sql"SELECT id_actividad, estado_actividad FROM actividad_cronograma WHERE id_practica_ref = ${practicaId}"
        .map(rs => (rs.int("id_actividad"), rs.string("estado_actividad")))
        .list.apply()
    }
    dbStates.find(_._1 == a1.idActividad).get._2 shouldBe "DESCARTAR_ESTUDIANTE"
    dbStates.find(_._1 == a2.idActividad).get._2 shouldBe "DESCARTAR_TUTOR"
  }
}
