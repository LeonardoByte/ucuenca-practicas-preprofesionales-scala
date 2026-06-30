package com.ucuenca.gestion.models.logic

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll
import scalikejdbc._
import com.ucuenca.gestion.utils.DatabaseConnection
import com.ucuenca.gestion.models.enums.{RolUsuario, EstadoCronograma, EstadoFormulario2}
import com.ucuenca.gestion.models.db.{Formulario2DB, Formulario3Repository}

class Formulario3LogicSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  val companyRuc = "0909090909012"
  
  val studentACi = "3232323232" // Práctica A: Sin Formulario 2
  val studentBCi = "3333333333" // Práctica B: Formulario 2 PENDIENTE_REVISION (para auditar)
  val studentCCi = "3434343434" // Práctica C: Formulario 2 CONFORME (para emitir Formulario 3)

  val tutorEmpCi = "3535353535"
  val tutorAcadCi = "3636363636"

  var pdfMallaId: Int = _
  var pdfF2Id: Int = _
  var practiceAId: Int = _
  var practiceBId: Int = _
  var practiceCId: Int = _

  override def beforeAll(): Unit = {
    DatabaseConnection.initialize()

    DB.localTx { implicit session =>
      // Limpieza de datos anteriores
      sql"DELETE FROM formulario3_informe WHERE id_practica_ref IN (SELECT id_practica FROM practica_registro WHERE ci_estudiante_ref IN (${studentACi}, ${studentBCi}, ${studentCCi}))".update.apply()
      sql"DELETE FROM formulario2_evaluacion WHERE id_practica_ref IN (SELECT id_practica FROM practica_registro WHERE ci_estudiante_ref IN (${studentACi}, ${studentBCi}, ${studentCCi}))".update.apply()
      sql"DELETE FROM practica_registro WHERE ci_estudiante_ref IN (${studentACi}, ${studentBCi}, ${studentCCi})".update.apply()
      sql"DELETE FROM estudiante_perfil WHERE identificacion IN (${studentACi}, ${studentBCi}, ${studentCCi})".update.apply()
      sql"DELETE FROM tutor_empresarial_perfil WHERE identificacion = ${tutorEmpCi}".update.apply()
      sql"DELETE FROM empresa_perfil WHERE identificacion = ${companyRuc}".update.apply()
      sql"DELETE FROM usuario WHERE identificacion IN (${studentACi}, ${studentBCi}, ${studentCCi}, ${tutorEmpCi}, ${tutorAcadCi}, ${companyRuc})".update.apply()
      sql"DELETE FROM archivo_pdf WHERE nombre_original IN ('malla_test_f3.pdf', 'f2_eval_test.pdf', 'informe_final.pdf')".update.apply()

      // 1. Crear PDFs de prueba
      pdfMallaId = sql"INSERT INTO archivo_pdf (tipo_archivo, nombre_original, ruta_segura_servidor) VALUES ('T9_MALLA_ACADEMICA'::tipo_archivo_pdf, 'malla_test_f3.pdf', 'uploads/m_f3.pdf')".updateAndReturnGeneratedKey.apply().toInt
      pdfF2Id = sql"INSERT INTO archivo_pdf (tipo_archivo, nombre_original, ruta_segura_servidor) VALUES ('T2_FORMULARIO_2_RUBRICA'::tipo_archivo_pdf, 'f2_eval_test.pdf', 'uploads/f2_t.pdf')".updateAndReturnGeneratedKey.apply().toInt

      // 2. Crear Empresa y Tutors
      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${companyRuc}, 'Empresa F3', 'emp_f3@test.com', 'EMPRESA'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()
      sql"INSERT INTO empresa_perfil (identificacion, direccion_matriz, mision, vision, estado_convenio) VALUES (${companyRuc}, 'Matriz F3', 'M', 'V', 'FORMALIZADO'::estado_convenio)".update.apply()

      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${tutorEmpCi}, 'Tutor Emp F3', 'tut_emp_f3@test.com', 'TUTOR_EMPRESARIAL'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()
      sql"INSERT INTO tutor_empresarial_perfil (identificacion, empresa_id_ref, telefono_contacto) VALUES (${tutorEmpCi}, ${companyRuc}, '0998765432')".update.apply()

      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${tutorAcadCi}, 'Tutor Acad F3', 'tut_acad_f3@test.com', 'TUTOR_ACADEMICO'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()

      // 3. Crear Estudiantes y Prácticas
      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${studentACi}, 'Estudiante A F3', 'estA_f3@test.com', 'ESTUDIANTE'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()
      sql"INSERT INTO estudiante_perfil (identificacion, ciclo_actual, id_carrera_ref, estado_matricula, estado_estudiante_practica, malla_academica_pdf) VALUES (${studentACi}, 7, 1, 'REGULAR'::estado_matricula, 'CON_PRACTICA_ACTIVA'::estado_estudiante_practica, ${pdfMallaId})".update.apply()

      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${studentBCi}, 'Estudiante B F3', 'estB_f3@test.com', 'ESTUDIANTE'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()
      sql"INSERT INTO estudiante_perfil (identificacion, ciclo_actual, id_carrera_ref, estado_matricula, estado_estudiante_practica, malla_academica_pdf) VALUES (${studentBCi}, 8, 1, 'REGULAR'::estado_matricula, 'CON_PRACTICA_ACTIVA'::estado_estudiante_practica, ${pdfMallaId})".update.apply()

      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${studentCCi}, 'Estudiante C F3', 'estC_f3@test.com', 'ESTUDIANTE'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()
      sql"INSERT INTO estudiante_perfil (identificacion, ciclo_actual, id_carrera_ref, estado_matricula, estado_estudiante_practica, malla_academica_pdf) VALUES (${studentCCi}, 8, 1, 'REGULAR'::estado_matricula, 'CON_PRACTICA_ACTIVA'::estado_estudiante_practica, ${pdfMallaId})".update.apply()

      practiceAId = sql"""
        INSERT INTO practica_registro (ci_estudiante_ref, ruc_empresa_ref, id_tutor_academico_ref, id_tutor_empresarial_ref, origen_rama, estado_cronograma, horas_acumuladas, horas_totales_requeridas)
        VALUES (${studentACi}, ${companyRuc}, ${tutorAcadCi}, ${tutorEmpCi}, 'BOLSA_EMPLEO'::origen_rama, 'F2_F3_PENDIENTE'::estado_cronograma, 160, 160)
      """.updateAndReturnGeneratedKey.apply().toInt

      practiceBId = sql"""
        INSERT INTO practica_registro (ci_estudiante_ref, ruc_empresa_ref, id_tutor_academico_ref, id_tutor_empresarial_ref, origen_rama, estado_cronograma, horas_acumuladas, horas_totales_requeridas)
        VALUES (${studentBCi}, ${companyRuc}, ${tutorAcadCi}, ${tutorEmpCi}, 'BOLSA_EMPLEO'::origen_rama, 'F2_F3_PENDIENTE'::estado_cronograma, 160, 160)
      """.updateAndReturnGeneratedKey.apply().toInt

      practiceCId = sql"""
        INSERT INTO practica_registro (ci_estudiante_ref, ruc_empresa_ref, id_tutor_academico_ref, id_tutor_empresarial_ref, origen_rama, estado_cronograma, horas_acumuladas, horas_totales_requeridas)
        VALUES (${studentCCi}, ${companyRuc}, ${tutorAcadCi}, ${tutorEmpCi}, 'BOLSA_EMPLEO'::origen_rama, 'F2_F3_PENDIENTE'::estado_cronograma, 160, 160)
      """.updateAndReturnGeneratedKey.apply().toInt

      // 4. Registrar Formulario 2 para Práctica B (PENDIENTE_REVISION)
      sql"""
        INSERT INTO formulario2_evaluacion (id_practica_ref, formulario2_pdf, estado_formulario2, contenido_rubrica_indexado)
        VALUES (${practiceBId}, ${pdfF2Id}, 'PENDIENTE_REVISION'::estado_formulario2, 'Evaluacion Empresa Estudiante B')
      """.update.apply()

      // 5. Registrar Formulario 2 para Práctica C (CONFORME)
      sql"""
        INSERT INTO formulario2_evaluacion (id_practica_ref, formulario2_pdf, estado_formulario2, contenido_rubrica_indexado)
        VALUES (${practiceCId}, ${pdfF2Id}, 'CONFORME'::estado_formulario2, 'Evaluacion Empresa Estudiante C')
      """.update.apply()
    }
  }

  override def afterAll(): Unit = {
    DB.localTx { implicit session =>
      sql"DELETE FROM formulario3_informe WHERE id_practica_ref IN (${practiceAId}, ${practiceBId}, ${practiceCId})".update.apply()
      sql"DELETE FROM formulario2_evaluacion WHERE id_practica_ref IN (${practiceAId}, ${practiceBId}, ${practiceCId})".update.apply()
      sql"DELETE FROM practica_registro WHERE id_practica IN (${practiceAId}, ${practiceBId}, ${practiceCId})".update.apply()
      sql"DELETE FROM estudiante_perfil WHERE identificacion IN (${studentACi}, ${studentBCi}, ${studentCCi})".update.apply()
      sql"DELETE FROM tutor_empresarial_perfil WHERE identificacion = ${tutorEmpCi}".update.apply()
      sql"DELETE FROM empresa_perfil WHERE identificacion = ${companyRuc}".update.apply()
      sql"DELETE FROM usuario WHERE identificacion IN (${studentACi}, ${studentBCi}, ${studentCCi}, ${tutorEmpCi}, ${tutorAcadCi}, ${companyRuc})".update.apply()
      sql"DELETE FROM archivo_pdf WHERE id_archivo_pdf IN (${pdfMallaId}, ${pdfF2Id})".update.apply()
      sql"DELETE FROM archivo_pdf WHERE nombre_original = 'informe_final.pdf'".update.apply()
    }
  }

  "Formulario3Logic (Rubric Audit)" should "bloquear la revisión de rúbrica si la práctica no tiene rúbrica registrada" in {
    val r = Formulario3Logic.evaluarRubrica(practiceAId, aprobado = true, "")
    r.isLeft shouldBe true
    r match {
      case Left(Formulario3Failure.Validacion(msg)) =>
        msg should include ("No se encontró ningún registro del Formulario 2 para evaluar")
      case _ => fail("Debe fallar indicando la falta de rúbrica.")
    }
  }

  it should "exigir justificación obligatoria (mínimo 5 caracteres) al rechazar la rúbrica corporativa" in {
    // Caso 1: Justificación vacía
    val r1 = Formulario3Logic.evaluarRubrica(practiceBId, aprobado = false, "")
    r1.isLeft shouldBe true
    r1 match {
      case Left(Formulario3Failure.Validacion(msg)) =>
        msg should include ("La justificación del rechazo es obligatoria y debe tener al menos 5 caracteres")
      case _ => fail("Debe validar longitud del motivo de rechazo.")
    }

    // Caso 2: Justificación muy corta
    val r2 = Formulario3Logic.evaluarRubrica(practiceBId, aprobado = false, "Mal")
    r2.isLeft shouldBe true
    r2 match {
      case Left(Formulario3Failure.Validacion(msg)) =>
        msg should include ("La justificación del rechazo es obligatoria y debe tener al menos 5 caracteres")
      case _ => fail("Debe validar longitud del motivo de rechazo.")
    }
  }

  it should "permitir rechazar y congelar el registro como RECHAZADO con observaciones persistidas" in {
    val r = Formulario3Logic.evaluarRubrica(practiceBId, aprobado = false, "La rúbrica técnica carece de firmas institucionales.")
    r shouldBe Right(())

    // Verificar en base de datos
    val eval = Formulario2DB.buscarUltimaEvaluacionPorPractica(practiceBId).get
    eval.estadoFormulario2 shouldBe EstadoFormulario2.RECHAZADO
    eval.justificacionRechazoDocente shouldBe Some("La rúbrica técnica carece de firmas institucionales.")
  }

  it should "permitir registrar la Conformidad Académica de una rúbrica en estado PENDIENTE" in {
    // Registrar una nueva evaluación PENDIENTE en Práctica B
    DB.localTx { implicit session =>
      sql"""
        INSERT INTO formulario2_evaluacion (id_practica_ref, formulario2_pdf, estado_formulario2, contenido_rubrica_indexado)
        VALUES (${practiceBId}, ${pdfF2Id}, 'PENDIENTE_REVISION'::estado_formulario2, 'Evaluacion Empresa Estudiante B V2')
      """.update.apply()
    }

    val r = Formulario3Logic.evaluarRubrica(practiceBId, aprobado = true, "")
    r shouldBe Right(())

    val eval = Formulario2DB.buscarUltimaEvaluacionPorPractica(practiceBId).get
    eval.estadoFormulario2 shouldBe EstadoFormulario2.CONFORME
  }

  "Formulario3Logic (Formulario 3 Emission)" should "bloquear la emisión de Formulario 3 si la rúbrica está en estado RECHAZADO o PENDIENTE (bloqueo en cascada)" in {
    val bytes = Array[Byte](0x25, 0x50, 0x44, 0x46)
    
    // Práctica A: Sin Formulario 2
    val rA = Formulario3Logic.emitirFormulario3(practiceAId, bytes, "informe_final.pdf")
    rA.isLeft shouldBe true
    rA match {
      case Left(Formulario3Failure.Validacion(msg)) =>
        msg should include ("El tutor empresarial no ha subido ni calificado aún la evaluación")
      case _ => fail("Debe bloquear en cascada.")
    }

    // Práctica B: Ahora es CONFORME tras el test anterior, pero vamos a resetearla temporalmente a PENDIENTE
    DB.localTx { implicit session =>
      sql"""
        UPDATE formulario2_evaluacion 
        SET estado_formulario2 = 'PENDIENTE_REVISION'::estado_formulario2
        WHERE id_practica_ref = ${practiceBId}
      """.update.apply()
    }
    val rB = Formulario3Logic.emitirFormulario3(practiceBId, bytes, "informe_final.pdf")
    rB.isLeft shouldBe true
    rB match {
      case Left(Formulario3Failure.Validacion(msg)) =>
        msg should include ("debe ser aprobado ('CONFORME') antes de poder emitir el Formulario 3")
      case _ => fail("Debe bloquear en cascada si está PENDIENTE.")
    }
  }

  it should "permitir emitir el Formulario 3 de forma atómica cuando la rúbrica es CONFORME" in {
    val bytes = Array[Byte](0x25, 0x50, 0x44, 0x46, 0x2d, 0x31)
    val r = Formulario3Logic.emitirFormulario3(practiceCId, bytes, "informe_final.pdf")
    r shouldBe Right(())

    // Verificar en archivo_pdf
    val pdfOpt = DB.readOnly { implicit session =>
      sql"SELECT * FROM archivo_pdf WHERE nombre_original = 'informe_final.pdf'"
        .map(rs => (rs.int("id_archivo_pdf"), rs.string("tipo_archivo"))).single.apply()
    }
    pdfOpt.isDefined shouldBe true
    val (pdfId, tipoArchivo) = pdfOpt.get
    tipoArchivo shouldBe "T3_FORMULARIO_3_INFORME"

    // Verificar en formulario3_informe
    val f3Opt = Formulario3Repository.buscarFormulario3PorPractica(practiceCId)
    f3Opt.isDefined shouldBe true
    f3Opt.get.formulario3PDF shouldBe pdfId
  }
}
