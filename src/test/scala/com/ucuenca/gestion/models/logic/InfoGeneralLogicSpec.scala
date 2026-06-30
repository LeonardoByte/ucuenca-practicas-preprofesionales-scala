package com.ucuenca.gestion.models.logic

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll
import scalikejdbc._
import com.ucuenca.gestion.utils.DatabaseConnection

class InfoGeneralLogicSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  val studentCi = "5151515151"
  val companyRuc = "0808080808018"
  val tutorEmpCi = "5252525252"
  val tutorAcadCi = "5353535353"

  var pdfMallaId: Int = _
  var practiceId: Int = _

  override def beforeAll(): Unit = {
    DatabaseConnection.initialize()

    DB.localTx { implicit session =>
      // Limpieza de seguridad
      sql"DELETE FROM actividad_cronograma WHERE id_practica_ref IN (SELECT id_practica FROM practica_registro WHERE ci_estudiante_ref = ${studentCi})".update.apply()
      sql"DELETE FROM practica_registro WHERE ci_estudiante_ref = ${studentCi}".update.apply()
      sql"DELETE FROM estudiante_perfil WHERE identificacion = ${studentCi}".update.apply()
      sql"DELETE FROM tutor_empresarial_perfil WHERE identificacion = ${tutorEmpCi}".update.apply()
      sql"DELETE FROM empresa_perfil WHERE identificacion = ${companyRuc}".update.apply()
      sql"DELETE FROM usuario WHERE identificacion IN (${studentCi}, ${tutorEmpCi}, ${tutorAcadCi}, ${companyRuc})".update.apply()
      sql"DELETE FROM archivo_pdf WHERE nombre_original = 'malla_info.pdf'".update.apply()

      // 1. Crear PDF
      pdfMallaId = sql"INSERT INTO archivo_pdf (tipo_archivo, nombre_original, ruta_segura_servidor) VALUES ('T9_MALLA_ACADEMICA'::tipo_archivo_pdf, 'malla_info.pdf', 'uploads/m_info.pdf')".updateAndReturnGeneratedKey.apply().toInt

      // 2. Crear usuarios
      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${studentCi}, 'Estudiante Info', 'est_info@test.com', 'ESTUDIANTE'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()
      sql"INSERT INTO estudiante_perfil (identificacion, ciclo_actual, id_carrera_ref, estado_matricula, estado_estudiante_practica, malla_academica_pdf) VALUES (${studentCi}, 7, 1, 'REGULAR'::estado_matricula, 'CON_PRACTICA_ACTIVA'::estado_estudiante_practica, ${pdfMallaId})".update.apply()

      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${companyRuc}, 'Empresa Info', 'emp_info@test.com', 'EMPRESA'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()
      sql"INSERT INTO empresa_perfil (identificacion, direccion_matriz, mision, vision, estado_convenio) VALUES (${companyRuc}, 'Matriz Info', 'M', 'V', 'FORMALIZADO'::estado_convenio)".update.apply()

      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${tutorEmpCi}, 'Tutor Emp Info', 't_emp_info@test.com', 'TUTOR_EMPRESARIAL'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()
      sql"INSERT INTO tutor_empresarial_perfil (identificacion, empresa_id_ref, telefono_contacto) VALUES (${tutorEmpCi}, ${companyRuc}, '0998765432')".update.apply()

      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${tutorAcadCi}, 'Tutor Acad Info', 't_acad_info@test.com', 'TUTOR_ACADEMICO'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()

      // 3. Crear Práctica
      practiceId = sql"""
        INSERT INTO practica_registro (ci_estudiante_ref, ruc_empresa_ref, id_tutor_academico_ref, id_tutor_empresarial_ref, origen_rama, estado_cronograma, horas_acumuladas, horas_totales_requeridas)
        VALUES (${studentCi}, ${companyRuc}, ${tutorAcadCi}, ${tutorEmpCi}, 'BOLSA_EMPLEO'::origen_rama, 'EN_DESARROLLO'::estado_cronograma, 80, 240)
      """.updateAndReturnGeneratedKey.apply().toInt
    }
  }

  override def afterAll(): Unit = {
    DB.localTx { implicit session =>
      sql"DELETE FROM practica_registro WHERE id_practica = ${practiceId}".update.apply()
      sql"DELETE FROM estudiante_perfil WHERE identificacion = ${studentCi}".update.apply()
      sql"DELETE FROM tutor_empresarial_perfil WHERE identificacion = ${tutorEmpCi}".update.apply()
      sql"DELETE FROM empresa_perfil WHERE identificacion = ${companyRuc}".update.apply()
      sql"DELETE FROM usuario WHERE identificacion IN (${studentCi}, ${tutorEmpCi}, ${tutorAcadCi}, ${companyRuc})".update.apply()
      sql"DELETE FROM archivo_pdf WHERE id_archivo_pdf = ${pdfMallaId}".update.apply()
    }
  }

  "InfoGeneralLogic" should "obtener con éxito los detalles completos de la práctica activa del estudiante" in {
    val r = InfoGeneralLogic.obtenerDetallesPractica(studentCi)
    r.isRight shouldBe true
    val details = r.getOrElse(fail("No details found"))
    details.practica.horasAcumuladas shouldBe 80
    details.practica.horasTotalesRequeridas shouldBe 240
    details.nombreEmpresa shouldBe "Empresa Info"
    details.convenioNombre shouldBe "FORMALIZADO"
    details.tutorAcademicoNombre should include("Tutor Acad Info")
    details.tutorEmpresarialNombre should include("Tutor Emp Info")
  }

  it should "retornar un error de validación cuando el estudiante no registre una práctica activa" in {
    val r = InfoGeneralLogic.obtenerDetallesPractica("9999999999")
    r.isLeft shouldBe true
    r.left.map {
      case InfoGeneralFailure.Validacion(msg) => msg should include("No se localizó")
      case other => fail(s"Unexpected failure type: $other")
    }
  }
}
