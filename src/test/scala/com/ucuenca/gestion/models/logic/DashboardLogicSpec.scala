package com.ucuenca.gestion.models.logic

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll
import scalikejdbc._
import com.ucuenca.gestion.utils.DatabaseConnection
import com.ucuenca.gestion.models.db.DashboardRepository

class DashboardLogicSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  val studentCi = "4141414141"
  val companyRuc = "0909090909015"
  val tutorEmpCi = "4242424242"
  val tutorAcadCi = "4343434343"

  var pdfMallaId: Int = _
  var practiceId: Int = _

  override def beforeAll(): Unit = {
    DatabaseConnection.initialize()

    DB.localTx { implicit session =>
      // Limpieza de seguridad
      sql"DELETE FROM actividad_cronograma WHERE id_practica_ref IN (SELECT id_practica FROM practica_registro WHERE ci_estudiante_ref = ${studentCi})".update.apply()
      sql"DELETE FROM formulario3_informe WHERE id_practica_ref IN (SELECT id_practica FROM practica_registro WHERE ci_estudiante_ref = ${studentCi})".update.apply()
      sql"DELETE FROM formulario2_evaluacion WHERE id_practica_ref IN (SELECT id_practica FROM practica_registro WHERE ci_estudiante_ref = ${studentCi})".update.apply()
      sql"DELETE FROM expediente_formulario1 WHERE id_practica_ref IN (SELECT id_practica FROM practica_registro WHERE ci_estudiante_ref = ${studentCi})".update.apply()
      sql"DELETE FROM validacion_carta_compromiso WHERE ci_estudiante = ${studentCi}".update.apply()
      sql"DELETE FROM practica_registro WHERE ci_estudiante_ref = ${studentCi}".update.apply()
      sql"DELETE FROM estudiante_perfil WHERE identificacion = ${studentCi}".update.apply()
      sql"DELETE FROM tutor_empresarial_perfil WHERE identificacion = ${tutorEmpCi}".update.apply()
      sql"DELETE FROM empresa_perfil WHERE identificacion = ${companyRuc}".update.apply()
      sql"DELETE FROM usuario WHERE identificacion IN (${studentCi}, ${tutorEmpCi}, ${tutorAcadCi}, ${companyRuc})".update.apply()
      sql"DELETE FROM archivo_pdf WHERE nombre_original = 'malla_dash.pdf'".update.apply()

      // 1. Crear PDF
      pdfMallaId = sql"INSERT INTO archivo_pdf (tipo_archivo, nombre_original, ruta_segura_servidor) VALUES ('T9_MALLA_ACADEMICA'::tipo_archivo_pdf, 'malla_dash.pdf', 'uploads/m_dash.pdf')".updateAndReturnGeneratedKey.apply().toInt

      // 2. Crear usuarios
      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${studentCi}, 'Estudiante Dash', 'est_dash@test.com', 'ESTUDIANTE'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()
      sql"INSERT INTO estudiante_perfil (identificacion, ciclo_actual, id_carrera_ref, estado_matricula, estado_estudiante_practica, malla_academica_pdf) VALUES (${studentCi}, 8, 1, 'REGULAR'::estado_matricula, 'CON_PRACTICA_ACTIVA'::estado_estudiante_practica, ${pdfMallaId})".update.apply()

      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${companyRuc}, 'Empresa Dash', 'emp_dash@test.com', 'EMPRESA'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()
      sql"INSERT INTO empresa_perfil (identificacion, direccion_matriz, mision, vision, estado_convenio) VALUES (${companyRuc}, 'Matriz Dash', 'M', 'V', 'PENDIENTE'::estado_convenio)".update.apply()

      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${tutorEmpCi}, 'Tutor Emp Dash', 't_emp_dash@test.com', 'TUTOR_EMPRESARIAL'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()
      sql"INSERT INTO tutor_empresarial_perfil (identificacion, empresa_id_ref, telefono_contacto) VALUES (${tutorEmpCi}, ${companyRuc}, '0998765432')".update.apply()

      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${tutorAcadCi}, 'Tutor Acad Dash', 't_acad_dash@test.com', 'TUTOR_ACADEMICO'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()

      // 3. Crear Práctica
      practiceId = sql"""
        INSERT INTO practica_registro (ci_estudiante_ref, ruc_empresa_ref, id_tutor_academico_ref, id_tutor_empresarial_ref, origen_rama, estado_cronograma, horas_acumuladas, horas_totales_requeridas)
        VALUES (${studentCi}, ${companyRuc}, ${tutorAcadCi}, ${tutorEmpCi}, 'BOLSA_EMPLEO'::origen_rama, 'EN_DESARROLLO'::estado_cronograma, 50, 160)
      """.updateAndReturnGeneratedKey.apply().toInt

      // 4. Crear Actividad PENDIENTE
      sql"""
        INSERT INTO actividad_cronograma (id_practica_ref, numero_secuencial, descripcion_tarea, origen_creacion, estado_actividad)
        VALUES (${practiceId}, 1, 'Tarea Test Dash', 'ESTUDIANTE'::origen_creacion_actividad, 'PENDIENTE'::estado_actividad)
      """.update.apply()
    }
  }

  override def afterAll(): Unit = {
    DB.localTx { implicit session =>
      sql"DELETE FROM actividad_cronograma WHERE id_practica_ref = ${practiceId}".update.apply()
      sql"DELETE FROM practica_registro WHERE id_practica = ${practiceId}".update.apply()
      sql"DELETE FROM estudiante_perfil WHERE identificacion = ${studentCi}".update.apply()
      sql"DELETE FROM tutor_empresarial_perfil WHERE identificacion = ${tutorEmpCi}".update.apply()
      sql"DELETE FROM empresa_perfil WHERE identificacion = ${companyRuc}".update.apply()
      sql"DELETE FROM usuario WHERE identificacion IN (${studentCi}, ${tutorEmpCi}, ${tutorAcadCi}, ${companyRuc})".update.apply()
      sql"DELETE FROM archivo_pdf WHERE id_archivo_pdf = ${pdfMallaId}".update.apply()
    }
  }

  "DashboardLogic" should "calcular correctamente métricas del Administrador" in {
    val r = DashboardLogic.fetchAdminMetrics()
    r.isRight shouldBe true
    val m = r.getOrElse(Map.empty)
    m("dbCheck") shouldBe 1
    m("practicasActivas") should be >= 1
    m("estudiantes") should be >= 1
    m("tutoresAcad") should be >= 1
    m("empresas") should be >= 1
  }

  it should "calcular correctamente métricas del Coordinador" in {
    val r = DashboardLogic.fetchCoordinatorMetrics()
    r.isRight shouldBe true
  }

  it should "calcular correctamente métricas de la Empresa" in {
    val r = DashboardLogic.fetchCompanyMetrics(companyRuc)
    r.isRight shouldBe true
    val m = r.getOrElse(Map.empty)
    m("convenio") shouldBe "PENDIENTE"
    m("tutores") shouldBe "1"
  }

  it should "calcular correctamente métricas de la Secretaría" in {
    val r = DashboardLogic.fetchSecretaryMetrics()
    r.isRight shouldBe true
  }

  it should "calcular correctamente métricas del Tutor Académico" in {
    val r = DashboardLogic.fetchAcademicTutorMetrics(tutorAcadCi)
    r.isRight shouldBe true
    val m = r.getOrElse(Map.empty)
    m("inicio") shouldBe 0
    m("desarrollo") shouldBe 1
    m("tareas") shouldBe 1
  }

  it should "calcular correctamente métricas del Tutor Empresarial" in {
    val r = DashboardLogic.fetchCorporateTutorMetrics(tutorEmpCi)
    r.isRight shouldBe true
    val m = r.getOrElse(Map.empty)
    m("inicio") shouldBe 0
    m("desarrollo") shouldBe 1
    m("tareas") shouldBe 1
  }
}
