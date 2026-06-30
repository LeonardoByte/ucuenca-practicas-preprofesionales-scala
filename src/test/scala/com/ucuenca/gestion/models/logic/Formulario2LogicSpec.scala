package com.ucuenca.gestion.models.logic

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll
import scalikejdbc._
import com.ucuenca.gestion.utils.DatabaseConnection
import com.ucuenca.gestion.models.enums.{RolUsuario, EstadoCronograma, EstadoFormulario2}
import com.ucuenca.gestion.models.db.{Formulario2DB, CronogramaRepository}

class Formulario2LogicSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  val companyRuc = "0909090909009"
  
  val studentACi = "2828282828" // No cumple horas (120/160)
  val studentBCi = "2929292929" // Si cumple horas (160/160)
  
  val tutorEmpCi = "3030303030"
  val tutorAcadCi = "3131313131"

  var pdfMallaId: Int = _
  var practiceAId: Int = _
  var practiceBId: Int = _

  override def beforeAll(): Unit = {
    DatabaseConnection.initialize()

    DB.localTx { implicit session =>
      // Limpieza de datos previos de pruebas
      sql"DELETE FROM formulario2_evaluacion WHERE id_practica_ref IN (SELECT id_practica FROM practica_registro WHERE ci_estudiante_ref IN (${studentACi}, ${studentBCi}))".update.apply()
      sql"DELETE FROM practica_registro WHERE ci_estudiante_ref IN (${studentACi}, ${studentBCi})".update.apply()
      sql"DELETE FROM estudiante_perfil WHERE identificacion IN (${studentACi}, ${studentBCi})".update.apply()
      sql"DELETE FROM tutor_empresarial_perfil WHERE identificacion = ${tutorEmpCi}".update.apply()
      sql"DELETE FROM empresa_perfil WHERE identificacion = ${companyRuc}".update.apply()
      sql"DELETE FROM usuario WHERE identificacion IN (${studentACi}, ${studentBCi}, ${tutorEmpCi}, ${tutorAcadCi}, ${companyRuc})".update.apply()
      sql"DELETE FROM archivo_pdf WHERE nombre_original = 'malla_test_f2.pdf'".update.apply()
      sql"DELETE FROM archivo_pdf WHERE nombre_original IN ('rubrica_est_b.pdf', 'rubrica_est_a.pdf')".update.apply()

      // 1. Crear PDF base para malla
      pdfMallaId = sql"INSERT INTO archivo_pdf (tipo_archivo, nombre_original, ruta_segura_servidor) VALUES ('T9_MALLA_ACADEMICA'::tipo_archivo_pdf, 'malla_test_f2.pdf', 'uploads/m_f2.pdf')".updateAndReturnGeneratedKey.apply().toInt

      // 2. Crear Empresa
      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${companyRuc}, 'Empresa F2', 'emp_f2@test.com', 'EMPRESA'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()
      sql"INSERT INTO empresa_perfil (identificacion, direccion_matriz, mision, vision, estado_convenio) VALUES (${companyRuc}, 'Matriz F2', 'M', 'V', 'FORMALIZADO'::estado_convenio)".update.apply()

      // 3. Crear Tutor Empresarial
      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${tutorEmpCi}, 'Tutor Emp F2', 'tut_emp_f2@test.com', 'TUTOR_EMPRESARIAL'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()
      sql"INSERT INTO tutor_empresarial_perfil (identificacion, empresa_id_ref, telefono_contacto) VALUES (${tutorEmpCi}, ${companyRuc}, '0998765432')".update.apply()

      // 4. Crear Tutor Académico
      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${tutorAcadCi}, 'Tutor Acad F2', 'tut_acad_f2@test.com', 'TUTOR_ACADEMICO'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()

      // 5. Crear Estudiantes
      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${studentACi}, 'Estudiante A F2', 'estA_f2@test.com', 'ESTUDIANTE'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()
      sql"INSERT INTO estudiante_perfil (identificacion, ciclo_actual, id_carrera_ref, estado_matricula, estado_estudiante_practica, malla_academica_pdf) VALUES (${studentACi}, 7, 1, 'REGULAR'::estado_matricula, 'CON_PRACTICA_ACTIVA'::estado_estudiante_practica, ${pdfMallaId})".update.apply()

      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${studentBCi}, 'Estudiante B F2', 'estB_f2@test.com', 'ESTUDIANTE'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()
      sql"INSERT INTO estudiante_perfil (identificacion, ciclo_actual, id_carrera_ref, estado_matricula, estado_estudiante_practica, malla_academica_pdf) VALUES (${studentBCi}, 8, 1, 'REGULAR'::estado_matricula, 'CON_PRACTICA_ACTIVA'::estado_estudiante_practica, ${pdfMallaId})".update.apply()

      // 6. Crear Prácticas
      // Práctica A: 120 horas acumuladas de 160 requeridas (Insuficientes)
      practiceAId = sql"""
        INSERT INTO practica_registro (ci_estudiante_ref, ruc_empresa_ref, id_tutor_academico_ref, id_tutor_empresarial_ref, origen_rama, estado_cronograma, horas_acumuladas, horas_totales_requeridas)
        VALUES (${studentACi}, ${companyRuc}, ${tutorAcadCi}, ${tutorEmpCi}, 'BOLSA_EMPLEO'::origen_rama, 'EN_DESARROLLO'::estado_cronograma, 120, 160)
      """.updateAndReturnGeneratedKey.apply().toInt

      // Práctica B: 160 horas acumuladas de 160 requeridas (Suficientes)
      practiceBId = sql"""
        INSERT INTO practica_registro (ci_estudiante_ref, ruc_empresa_ref, id_tutor_academico_ref, id_tutor_empresarial_ref, origen_rama, estado_cronograma, horas_acumuladas, horas_totales_requeridas)
        VALUES (${studentBCi}, ${companyRuc}, ${tutorAcadCi}, ${tutorEmpCi}, 'BOLSA_EMPLEO'::origen_rama, 'EN_DESARROLLO'::estado_cronograma, 160, 160)
      """.updateAndReturnGeneratedKey.apply().toInt
    }
  }

  override def afterAll(): Unit = {
    DB.localTx { implicit session =>
      sql"DELETE FROM formulario2_evaluacion WHERE id_practica_ref IN (${practiceAId}, ${practiceBId})".update.apply()
      sql"DELETE FROM practica_registro WHERE id_practica IN (${practiceAId}, ${practiceBId})".update.apply()
      sql"DELETE FROM estudiante_perfil WHERE identificacion IN (${studentACi}, ${studentBCi})".update.apply()
      sql"DELETE FROM tutor_empresarial_perfil WHERE identificacion = ${tutorEmpCi}".update.apply()
      sql"DELETE FROM empresa_perfil WHERE identificacion = ${companyRuc}".update.apply()
      sql"DELETE FROM usuario WHERE identificacion IN (${studentACi}, ${studentBCi}, ${tutorEmpCi}, ${tutorAcadCi}, ${companyRuc})".update.apply()
      sql"DELETE FROM archivo_pdf WHERE id_archivo_pdf = ${pdfMallaId}".update.apply()
      sql"DELETE FROM archivo_pdf WHERE nombre_original IN ('rubrica_est_b.pdf', 'rubrica_est_a.pdf')".update.apply()
    }
  }

  "Formulario2Logic" should "bloquear la solicitud de evaluación final si el alumno no ha acumulado las horas necesarias" in {
    val r = Formulario2Logic.solicitarEvaluacionFinal(practiceAId)
    r.isLeft shouldBe true
    r match {
      case Left(Formulario2Failure.Validacion(msg)) =>
        msg should include ("horas acumuladas (120) son inferiores a las requeridas (160)")
      case _ => fail("Debe retornar un error de validación.")
    }
  }

  it should "bloquear el registro de la rúbrica si el estudiante no se encuentra en estado F2_F3_PENDIENTE (bloqueo condicional de interfaz)" in {
    // Intentar registrar la evaluación de la Práctica B que aún está en EN_DESARROLLO
    val bytes = Array[Byte](0x25, 0x50, 0x44, 0x46)
    val r = Formulario2Logic.registrarFormulario2(practiceBId, bytes, "rubrica_est_b.pdf", "Criterio: Excelente")
    r.isLeft shouldBe true
    r match {
      case Left(Formulario2Failure.Validacion(msg)) =>
        msg should include ("El estudiante no ha solicitado la evaluación final o el estado del cronograma no lo permite")
      case _ => fail("Debe retornar un error de validación.")
    }
  }

  it should "permitir solicitar la evaluación final si el estudiante cumple con las horas y transicionar el estado del cronograma a F2_F3_PENDIENTE" in {
    val r = Formulario2Logic.solicitarEvaluacionFinal(practiceBId)
    r shouldBe Right(())

    // Verificar en base de datos
    val prOpt = CronogramaRepository.buscarPracticaPorEstudiante(studentBCi)
    prOpt.isDefined shouldBe true
    prOpt.get.estadoCronograma shouldBe EstadoCronograma.F2_F3_PENDIENTE
  }

  it should "permitir al tutor registrar la rúbrica (Formulario 2) de forma atómica cuando la práctica está en F2_F3_PENDIENTE" in {
    val bytes = Array[Byte](0x25, 0x50, 0x44, 0x46, 0x2d, 0x31)
    val r = Formulario2Logic.registrarFormulario2(practiceBId, bytes, "rubrica_est_b.pdf", "Criterio cuantitativo: 95/100, satisfactorio.")
    r shouldBe Right(())

    // 1. Verificar inserción en archivo_pdf
    val pdfOpt = DB.readOnly { implicit session =>
      sql"SELECT * FROM archivo_pdf WHERE nombre_original = 'rubrica_est_b.pdf'"
        .map { rs =>
          (rs.int("id_archivo_pdf"), rs.string("tipo_archivo"), rs.string("ruta_segura_servidor"))
        }.single.apply()
    }
    pdfOpt.isDefined shouldBe true
    val (pdfId, tipoArchivo, rutaSegura) = pdfOpt.get
    tipoArchivo shouldBe "T2_FORMULARIO_2_RUBRICA"
    rutaSegura shouldBe "uploads/formulario2/rubrica_est_b.pdf"

    // 2. Verificar inserción en formulario2_evaluacion con estado PENDIENTE_REVISION
    val f2Opt = Formulario2DB.buscarUltimaEvaluacionPorPractica(practiceBId)
    f2Opt.isDefined shouldBe true
    val eval = f2Opt.get
    eval.formulario2PDF shouldBe pdfId
    eval.estadoFormulario2 shouldBe EstadoFormulario2.PENDIENTE_REVISION
    eval.contenidoRubricaIndexado shouldBe "Criterio cuantitativo: 95/100, satisfactorio."
  }
}
