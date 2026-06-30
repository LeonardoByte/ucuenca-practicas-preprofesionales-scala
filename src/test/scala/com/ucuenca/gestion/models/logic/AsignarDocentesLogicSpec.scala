package com.ucuenca.gestion.models.logic

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll
import scalikejdbc._
import com.ucuenca.gestion.utils.DatabaseConnection
import com.ucuenca.gestion.models.enums.{EstadoCronograma, RolUsuario, EstadoCuenta}
import com.ucuenca.gestion.models.db.PracticaRegistroRepository

class AsignarDocentesLogicSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  val companyRuc = "0909090909001"
  val studentCi = "1515151515"
  val tutorEmpCi = "1414141414"
  val tutorAcadCi = "1313131313"

  var offerId: Int = _
  var postulationId: Int = _
  var practicaId: Int = _

  override def beforeAll(): Unit = {
    DatabaseConnection.initialize()

    DB.localTx { implicit session =>
      // Clean up previous test data
      sql"DELETE FROM practica_registro WHERE ci_estudiante_ref = ${studentCi}".update.apply()
      sql"DELETE FROM postulacion_bolsa WHERE ci_estudiante_ref = ${studentCi}".update.apply()
      sql"DELETE FROM oferta_convocatoria WHERE ruc_empresa_ref = ${companyRuc}".update.apply()
      sql"DELETE FROM estudiante_perfil WHERE identificacion = ${studentCi}".update.apply()
      sql"DELETE FROM tutor_empresarial_perfil WHERE identificacion = ${tutorEmpCi}".update.apply()
      sql"DELETE FROM empresa_perfil WHERE identificacion = ${companyRuc}".update.apply()
      sql"DELETE FROM usuario WHERE identificacion IN (${studentCi}, ${tutorEmpCi}, ${tutorAcadCi}, ${companyRuc})".update.apply()
      sql"DELETE FROM archivo_pdf WHERE nombre_original IN ('malla_test_tutor.pdf', 'oferta_test_tutor.pdf')".update.apply()

      // 1. Create PDFs
      val pdfMallaId = sql"INSERT INTO archivo_pdf (tipo_archivo, nombre_original, ruta_segura_servidor) VALUES ('T9_MALLA_ACADEMICA'::tipo_archivo_pdf, 'malla_test_tutor.pdf', 'uploads/m_t.pdf')".updateAndReturnGeneratedKey.apply().toInt
      val pdfOfertaId = sql"INSERT INTO archivo_pdf (tipo_archivo, nombre_original, ruta_segura_servidor) VALUES ('T7_PLANTILLA_FORMATO_OFERTA'::tipo_archivo_pdf, 'oferta_test_tutor.pdf', 'uploads/o_t.pdf')".updateAndReturnGeneratedKey.apply().toInt

      // 2. Create Company
      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${companyRuc}, 'Empresa Test Tutor', 'emp_tutor@test.com', 'EMPRESA'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()
      sql"INSERT INTO empresa_perfil (identificacion, direccion_matriz, mision, vision, estado_convenio) VALUES (${companyRuc}, 'Direccion Matrix', 'Mision', 'Vision', 'FORMALIZADO'::estado_convenio)".update.apply()

      // 3. Create Tutor Empresarial
      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${tutorEmpCi}, 'Tutor Emp Test', 'tut_emp@test.com', 'TUTOR_EMPRESARIAL'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()
      sql"INSERT INTO tutor_empresarial_perfil (identificacion, empresa_id_ref, telefono_contacto) VALUES (${tutorEmpCi}, ${companyRuc}, '0912345678')".update.apply()

      // 4. Create Student
      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${studentCi}, 'Estudiante Test Tutor', 'est_tutor@test.com', 'ESTUDIANTE'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()
      sql"INSERT INTO estudiante_perfil (identificacion, ciclo_actual, id_carrera_ref, estado_matricula, estado_estudiante_practica, malla_academica_pdf) VALUES (${studentCi}, 7, 1, 'REGULAR'::estado_matricula, 'CON_PRACTICA_ACTIVA'::estado_estudiante_practica, ${pdfMallaId})".update.apply()

      // 5. Create Tutor Academico
      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${tutorAcadCi}, 'Tutor Acad Test', 'tut_acad@test.com', 'TUTOR_ACADEMICO'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()

      // 6. Create Offer
      offerId = sql"""
        INSERT INTO oferta_convocatoria (ruc_empresa_ref, titulo_oferta, vacantes_solicitadas, duracion_horas, descripcion_general, requisitos_obligatorios, actividades_especificas, plantilla_oferta_pdf, estado_oferta)
        VALUES (${companyRuc}, 'Oferta Test Tutor', 2, 160, 'Desc', 'Req', 'Act', ${pdfOfertaId}, 'APROBADA'::estado_oferta)
      """.updateAndReturnGeneratedKey.apply().toInt

      // 7. Create Postulation APROBADA
      postulationId = sql"""
        INSERT INTO postulacion_bolsa (ci_estudiante_ref, id_oferta_ref, estado_postulacion)
        VALUES (${studentCi}, ${offerId}, 'APROBADA'::estado_postulacion)
      """.updateAndReturnGeneratedKey.apply().toInt

      // 8. Create practica_registro in TUTOR_ACADEMICO_PENDIENTE
      practicaId = sql"""
        INSERT INTO practica_registro (ci_estudiante_ref, ruc_empresa_ref, id_tutor_academico_ref, id_tutor_empresarial_ref, origen_rama, estado_cronograma, horas_totales_requeridas)
        VALUES (${studentCi}, ${companyRuc}, NULL, ${tutorEmpCi}, 'BOLSA_EMPLEO'::origen_rama, 'TUTOR_ACADEMICO_PENDIENTE'::estado_cronograma, 160)
      """.updateAndReturnGeneratedKey.apply().toInt
    }
  }

  override def afterAll(): Unit = {
    DB.localTx { implicit session =>
      sql"DELETE FROM practica_registro WHERE ci_estudiante_ref = ${studentCi}".update.apply()
      sql"DELETE FROM postulacion_bolsa WHERE ci_estudiante_ref = ${studentCi}".update.apply()
      sql"DELETE FROM oferta_convocatoria WHERE ruc_empresa_ref = ${companyRuc}".update.apply()
      sql"DELETE FROM estudiante_perfil WHERE identificacion = ${studentCi}".update.apply()
      sql"DELETE FROM tutor_empresarial_perfil WHERE identificacion = ${tutorEmpCi}".update.apply()
      sql"DELETE FROM empresa_perfil WHERE identificacion = ${companyRuc}".update.apply()
      sql"DELETE FROM usuario WHERE identificacion IN (${studentCi}, ${tutorEmpCi}, ${tutorAcadCi}, ${companyRuc})".update.apply()
      sql"DELETE FROM archivo_pdf WHERE nombre_original IN ('malla_test_tutor.pdf', 'oferta_test_tutor.pdf')".update.apply()
    }
  }

  "AsignarDocentesLogic" should "listar la practica pendiente de asignacion con todos sus detalles" in {
    val result = AsignarDocentesLogic.listarPendientes()
    result.isRight shouldBe true
    val lista = result.toOption.get
    lista.exists(_.idPractica == practicaId) shouldBe true
    
    val item = lista.find(_.idPractica == practicaId).get
    item.ciEstudiante shouldBe studentCi
    item.nombreEstudiante shouldBe "Estudiante Test Tutor"
    item.nombreEmpresa shouldBe "Empresa Test Tutor"
    item.tituloOferta shouldBe "Oferta Test Tutor"
    item.idTutorAcademicoRef shouldBe None
  }

  it should "listar los tutores academicos activos disponibles" in {
    val result = AsignarDocentesLogic.listarTutoresActivos()
    result.isRight shouldBe true
    val tutores = result.toOption.get
    tutores.exists(_.identificacion == tutorAcadCi) shouldBe true
    
    val tutor = tutores.find(_.identificacion == tutorAcadCi).get
    tutor.nombresCompletos shouldBe "Tutor Acad Test"
    tutor.rol shouldBe RolUsuario.TUTOR_ACADEMICO
    tutor.estadoCuenta shouldBe EstadoCuenta.ACTIVA
  }

  it should "rechazar el guardado si la lista de asignaciones esta vacia" in {
    val result = AsignarDocentesLogic.guardarAsignaciones(Nil)
    result shouldBe Left(AsignarDocentesFailure.Validacion("No se especificaron asignaciones de tutores académicos para guardar."))
  }

  it should "rechazar si alguna asignacion especifica un tutor vacio o nulo" in {
    val result = AsignarDocentesLogic.guardarAsignaciones(List((practicaId, "")))
    result shouldBe Left(AsignarDocentesFailure.Validacion("El tutor académico asignado no puede estar vacío."))
    
    val resultNull = AsignarDocentesLogic.guardarAsignaciones(List((practicaId, null)))
    resultNull shouldBe Left(AsignarDocentesFailure.Validacion("El tutor académico asignado no puede estar vacío."))
  }

  it should "guardar exitosamente la asignacion del tutor y actualizar el estado de la practica a F1_PENDIENTE" in {
    val result = AsignarDocentesLogic.guardarAsignaciones(List((practicaId, tutorAcadCi)))
    result shouldBe Right(())

    // Verificar en base de datos
    val dbPractica = DB.readOnly { implicit session =>
      sql"SELECT id_tutor_academico_ref, estado_cronograma FROM practica_registro WHERE id_practica = ${practicaId}"
        .map(rs => (rs.stringOpt("id_tutor_academico_ref"), rs.string("estado_cronograma")))
        .single.apply()
    }

    dbPractica shouldBe Some((Some(tutorAcadCi), "F1_PENDIENTE"))
  }
}
