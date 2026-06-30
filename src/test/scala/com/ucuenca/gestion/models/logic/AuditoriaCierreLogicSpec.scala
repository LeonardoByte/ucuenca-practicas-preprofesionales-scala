package com.ucuenca.gestion.models.logic

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll
import scalikejdbc._
import com.ucuenca.gestion.utils.DatabaseConnection
import com.ucuenca.gestion.models.enums.{RolUsuario, EstadoCronograma, EstadoFormulario2}
import com.ucuenca.gestion.models.db.{AuditoriaRepository, Formulario2DB, Formulario3Repository}

class AuditoriaCierreLogicSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  val companyPendingRuc = "0909090909013"
  val companyFormalizedRuc = "0909090909014"

  val student1Ci = "3737373737" // Empresa sin convenio, sin cartas compromiso
  val student2Ci = "3838383838" // Empresa con convenio (Bypass legal)
  
  val tutorEmpCi = "3939393939"
  val tutorAcadCi = "4040404040"

  var pdfMallaId: Int = _
  var pdfF1Id: Int = _
  var pdfF2Id: Int = _
  var pdfF3Id: Int = _
  var practicePendingId: Int = _
  var practiceFormalizedId: Int = _

  override def beforeAll(): Unit = {
    DatabaseConnection.initialize()

    DB.localTx { implicit session =>
      // Limpieza preventiva
      sql"DELETE FROM auditoria_cierre WHERE id_practica_ref IN (SELECT id_practica FROM practica_registro WHERE ci_estudiante_ref IN (${student1Ci}, ${student2Ci}))".update.apply()
      sql"DELETE FROM formulario3_informe WHERE id_practica_ref IN (SELECT id_practica FROM practica_registro WHERE ci_estudiante_ref IN (${student1Ci}, ${student2Ci}))".update.apply()
      sql"DELETE FROM formulario2_evaluacion WHERE id_practica_ref IN (SELECT id_practica FROM practica_registro WHERE ci_estudiante_ref IN (${student1Ci}, ${student2Ci}))".update.apply()
      sql"DELETE FROM validacion_carta_compromiso WHERE ci_estudiante IN (${student1Ci}, ${student2Ci})".update.apply()
      sql"DELETE FROM practica_registro WHERE ci_estudiante_ref IN (${student1Ci}, ${student2Ci})".update.apply()
      sql"DELETE FROM estudiante_perfil WHERE identificacion IN (${student1Ci}, ${student2Ci})".update.apply()
      sql"DELETE FROM tutor_empresarial_perfil WHERE identificacion = ${tutorEmpCi}".update.apply()
      sql"DELETE FROM empresa_perfil WHERE identificacion IN (${companyPendingRuc}, ${companyFormalizedRuc})".update.apply()
      sql"DELETE FROM usuario WHERE identificacion IN (${student1Ci}, ${student2Ci}, ${tutorEmpCi}, ${tutorAcadCi}, ${companyPendingRuc}, ${companyFormalizedRuc})".update.apply()
      sql"DELETE FROM archivo_pdf WHERE nombre_original IN ('m_audit.pdf', 'f1_audit.pdf', 'f2_audit.pdf', 'f3_audit.pdf')".update.apply()

      // 1. Crear PDFs
      pdfMallaId = sql"INSERT INTO archivo_pdf (tipo_archivo, nombre_original, ruta_segura_servidor) VALUES ('T9_MALLA_ACADEMICA'::tipo_archivo_pdf, 'm_audit.pdf', 'uploads/m.pdf')".updateAndReturnGeneratedKey.apply().toInt
      pdfF1Id = sql"INSERT INTO archivo_pdf (tipo_archivo, nombre_original, ruta_segura_servidor) VALUES ('T1_FORMULARIO_1_PLAN'::tipo_archivo_pdf, 'f1_audit.pdf', 'uploads/f1.pdf')".updateAndReturnGeneratedKey.apply().toInt
      pdfF2Id = sql"INSERT INTO archivo_pdf (tipo_archivo, nombre_original, ruta_segura_servidor) VALUES ('T2_FORMULARIO_2_RUBRICA'::tipo_archivo_pdf, 'f2_audit.pdf', 'uploads/f2.pdf')".updateAndReturnGeneratedKey.apply().toInt
      pdfF3Id = sql"INSERT INTO archivo_pdf (tipo_archivo, nombre_original, ruta_segura_servidor) VALUES ('T3_FORMULARIO_3_INFORME'::tipo_archivo_pdf, 'f3_audit.pdf', 'uploads/f3.pdf')".updateAndReturnGeneratedKey.apply().toInt

      // 2. Crear Empresas y Tutors
      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${companyPendingRuc}, 'Empresa P Audit', 'emp_p@test.com', 'EMPRESA'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()
      sql"INSERT INTO empresa_perfil (identificacion, direccion_matriz, mision, vision, estado_convenio) VALUES (${companyPendingRuc}, 'Matriz P', 'M', 'V', 'PENDIENTE'::estado_convenio)".update.apply()

      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${companyFormalizedRuc}, 'Empresa F Audit', 'emp_f@test.com', 'EMPRESA'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()
      sql"INSERT INTO empresa_perfil (identificacion, direccion_matriz, mision, vision, estado_convenio) VALUES (${companyFormalizedRuc}, 'Matriz F', 'M', 'V', 'FORMALIZADO'::estado_convenio)".update.apply()

      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${tutorEmpCi}, 'Tutor Emp Audit', 'tut_emp@test.com', 'TUTOR_EMPRESARIAL'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()
      sql"INSERT INTO tutor_empresarial_perfil (identificacion, empresa_id_ref, telefono_contacto) VALUES (${tutorEmpCi}, ${companyPendingRuc}, '0998765432')".update.apply()

      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${tutorAcadCi}, 'Tutor Acad Audit', 'tut_acad@test.com', 'TUTOR_ACADEMICO'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()

      // 3. Crear Estudiantes y Prácticas
      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${student1Ci}, 'Estudiante 1 Audit', 'est1@test.com', 'ESTUDIANTE'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()
      sql"INSERT INTO estudiante_perfil (identificacion, ciclo_actual, id_carrera_ref, estado_matricula, estado_estudiante_practica, malla_academica_pdf) VALUES (${student1Ci}, 7, 1, 'REGULAR'::estado_matricula, 'CON_PRACTICA_ACTIVA'::estado_estudiante_practica, ${pdfMallaId})".update.apply()

      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${student2Ci}, 'Estudiante 2 Audit', 'est2@test.com', 'ESTUDIANTE'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()
      sql"INSERT INTO estudiante_perfil (identificacion, ciclo_actual, id_carrera_ref, estado_matricula, estado_estudiante_practica, malla_academica_pdf) VALUES (${student2Ci}, 8, 1, 'REGULAR'::estado_matricula, 'CON_PRACTICA_ACTIVA'::estado_estudiante_practica, ${pdfMallaId})".update.apply()

      practicePendingId = sql"""
        INSERT INTO practica_registro (ci_estudiante_ref, ruc_empresa_ref, id_tutor_academico_ref, id_tutor_empresarial_ref, origen_rama, estado_cronograma, horas_acumuladas, horas_totales_requeridas)
        VALUES (${student1Ci}, ${companyPendingRuc}, ${tutorAcadCi}, ${tutorEmpCi}, 'BOLSA_EMPLEO'::origen_rama, 'F2_F3_PENDIENTE'::estado_cronograma, 160, 160)
      """.updateAndReturnGeneratedKey.apply().toInt

      practiceFormalizedId = sql"""
        INSERT INTO practica_registro (ci_estudiante_ref, ruc_empresa_ref, id_tutor_academico_ref, id_tutor_empresarial_ref, origen_rama, estado_cronograma, horas_acumuladas, horas_totales_requeridas)
        VALUES (${student2Ci}, ${companyFormalizedRuc}, ${tutorAcadCi}, ${tutorEmpCi}, 'BOLSA_EMPLEO'::origen_rama, 'F2_F3_PENDIENTE'::estado_cronograma, 160, 160)
      """.updateAndReturnGeneratedKey.apply().toInt

      // 4. Llenar expedientes F1, F2 (CONFORME) y F3 para ambos
      sql"INSERT INTO expediente_formulario1 (id_practica_ref, formulario1_pdf, estado_de_coordinador) VALUES (${practicePendingId}, ${pdfF1Id}, TRUE)".update.apply()
      sql"INSERT INTO expediente_formulario1 (id_practica_ref, formulario1_pdf, estado_de_coordinador) VALUES (${practiceFormalizedId}, ${pdfF1Id}, TRUE)".update.apply()

      sql"INSERT INTO formulario2_evaluacion (id_practica_ref, formulario2_pdf, estado_formulario2, contenido_rubrica_indexado) VALUES (${practicePendingId}, ${pdfF2Id}, 'CONFORME'::estado_formulario2, 'Rubrica 1')".update.apply()
      sql"INSERT INTO formulario2_evaluacion (id_practica_ref, formulario2_pdf, estado_formulario2, contenido_rubrica_indexado) VALUES (${practiceFormalizedId}, ${pdfF2Id}, 'CONFORME'::estado_formulario2, 'Rubrica 2')".update.apply()

      sql"INSERT INTO formulario3_informe (id_practica_ref, formulario3_pdf) VALUES (${practicePendingId}, ${pdfF3Id})".update.apply()
      sql"INSERT INTO formulario3_informe (id_practica_ref, formulario3_pdf) VALUES (${practiceFormalizedId}, ${pdfF3Id})".update.apply()
    }
  }

  override def afterAll(): Unit = {
    DB.localTx { implicit session =>
      sql"DELETE FROM auditoria_cierre WHERE id_practica_ref IN (${practicePendingId}, ${practiceFormalizedId})".update.apply()
      sql"DELETE FROM formulario3_informe WHERE id_practica_ref IN (${practicePendingId}, ${practiceFormalizedId})".update.apply()
      sql"DELETE FROM formulario2_evaluacion WHERE id_practica_ref IN (${practicePendingId}, ${practiceFormalizedId})".update.apply()
      sql"DELETE FROM expediente_formulario1 WHERE id_practica_ref IN (${practicePendingId}, ${practiceFormalizedId})".update.apply()
      sql"DELETE FROM validacion_carta_compromiso WHERE ci_estudiante IN (${student1Ci}, ${student2Ci})".update.apply()
      sql"DELETE FROM practica_registro WHERE id_practica IN (${practicePendingId}, ${practiceFormalizedId})".update.apply()
      sql"DELETE FROM estudiante_perfil WHERE identificacion IN (${student1Ci}, ${student2Ci})".update.apply()
      sql"DELETE FROM tutor_empresarial_perfil WHERE identificacion = ${tutorEmpCi}".update.apply()
      sql"DELETE FROM empresa_perfil WHERE identificacion IN (${companyPendingRuc}, ${companyFormalizedRuc})".update.apply()
      sql"DELETE FROM usuario WHERE identificacion IN (${student1Ci}, ${student2Ci}, ${tutorEmpCi}, ${tutorAcadCi}, ${companyPendingRuc}, ${companyFormalizedRuc})".update.apply()
      sql"DELETE FROM archivo_pdf WHERE id_archivo_pdf IN (${pdfMallaId}, ${pdfF1Id}, ${pdfF2Id}, ${pdfF3Id})".update.apply()
    }
  }

  "AuditoriaCierreLogic" should "bloquear la aprobación si la calificación está fuera de los límites de 0.00 y 100.00" in {
    // Caso 1: Negativa
    val r1 = AuditoriaCierreLogic.aprobarCierre(practiceFormalizedId, BigDecimal("-1.50"), secretariaValidado = false)
    r1.isLeft shouldBe true
    r1 match {
      case Left(AuditoriaCierreFailure.Validacion(msg)) =>
        msg should include ("La calificación final debe ser un valor decimal estrictamente comprendido entre 0.00 y 100.00")
      case _ => fail("Debe validar límites inferiores.")
    }

    // Caso 2: Excede 100
    val r2 = AuditoriaCierreLogic.aprobarCierre(practiceFormalizedId, BigDecimal("100.01"), secretariaValidado = false)
    r2.isLeft shouldBe true
    r2 match {
      case Left(AuditoriaCierreFailure.Validacion(msg)) =>
        msg should include ("La calificación final debe ser un valor decimal estrictamente comprendido entre 0.00 y 100.00")
      case _ => fail("Debe validar límites superiores.")
    }
  }

  it should "bloquear la aprobación por falta de cartas compromiso si la empresa no tiene convenio y secretaría no ha validado" in {
    val r = AuditoriaCierreLogic.aprobarCierre(practicePendingId, BigDecimal("95.00"), secretariaValidado = false)
    r.isLeft shouldBe true
    r match {
      case Left(AuditoriaCierreFailure.Validacion(msg)) =>
        msg should include ("La empresa asociada no tiene convenio vigente y no se ha certificado la entrega física de las Cartas de Compromiso")
      case _ => fail("Debe bloquear cierre legal sin cartas de compromiso validadas.")
    }
  }

  it should "aprobar con éxito el cierre legal si la empresa tiene convenio marco formalizado (bypass legal)" in {
    val r = AuditoriaCierreLogic.aprobarCierre(practiceFormalizedId, BigDecimal("90.00"), secretariaValidado = false)
    r shouldBe Right(())

    // Verificar en BD que nota se guardó y estado_cronograma es CERRADA_VALIDA
    val pr = DB.readOnly { implicit session =>
      sql"SELECT nota_final, estado_cronograma FROM practica_registro WHERE id_practica = ${practiceFormalizedId}"
        .map(rs => (rs.bigDecimal("nota_final"), rs.string("estado_cronograma"))).single.apply()
    }
    pr.isDefined shouldBe true
    val (notaFinal, estado) = pr.get
    BigDecimal(notaFinal) shouldBe BigDecimal("90.00")
    estado shouldBe "CERRADA_VALIDA"

    // Verificar que se haya insertado registro de auditoria exitoso
    val audOpt = DB.readOnly { implicit session =>
      sql"SELECT * FROM auditoria_cierre WHERE id_practica_ref = ${practiceFormalizedId}"
        .map(rs => (rs.string("estado_auditoria"), rs.boolean("validacion_fisica_secretaria_sincronizada"))).single.apply()
    }
    audOpt.isDefined shouldBe true
    val (estadoAud, cartasSinc) = audOpt.get
    estadoAud shouldBe "APROBADO"
  }

  it should "aprobar con éxito si secretaría ha validado o si el coordinador fuerza la validación mediante el parámetro" in {
    // Usamos el estudiante 1 (sin convenio, pero pasando secretariaValidado = true)
    val r = AuditoriaCierreLogic.aprobarCierre(practicePendingId, BigDecimal("88.50"), secretariaValidado = true)
    r shouldBe Right(())

    val pr = DB.readOnly { implicit session =>
      sql"SELECT nota_final, estado_cronograma FROM practica_registro WHERE id_practica = ${practicePendingId}"
        .map(rs => (rs.bigDecimal("nota_final"), rs.string("estado_cronograma"))).single.apply()
    }
    pr.isDefined shouldBe true
    val (notaFinal, estado) = pr.get
    BigDecimal(notaFinal) shouldBe BigDecimal("88.50")
    estado shouldBe "CERRADA_VALIDA"
  }

  it should "permitir rechazar el expediente y registrar la auditoría inmutable e incrementar secuencial_version" in {
    val studentCCi = "393939393C"
    var practiceCId = 0
    DB.localTx { implicit session =>
      sql"DELETE FROM auditoria_cierre WHERE id_practica_ref IN (SELECT id_practica FROM practica_registro WHERE ci_estudiante_ref = ${studentCCi})".update.apply()
      sql"DELETE FROM formulario2_evaluacion WHERE id_practica_ref IN (SELECT id_practica FROM practica_registro WHERE ci_estudiante_ref = ${studentCCi})".update.apply()
      sql"DELETE FROM practica_registro WHERE ci_estudiante_ref = ${studentCCi}".update.apply()
      sql"DELETE FROM estudiante_perfil WHERE identificacion = ${studentCCi}".update.apply()
      sql"DELETE FROM usuario WHERE identificacion = ${studentCCi}".update.apply()

      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${studentCCi}, 'Estudiante C Audit', 'estC@test.com', 'ESTUDIANTE'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()
      sql"INSERT INTO estudiante_perfil (identificacion, ciclo_actual, id_carrera_ref, estado_matricula, estado_estudiante_practica, malla_academica_pdf) VALUES (${studentCCi}, 8, 1, 'REGULAR'::estado_matricula, 'CON_PRACTICA_ACTIVA'::estado_estudiante_practica, ${pdfMallaId})".update.apply()

      practiceCId = sql"""
        INSERT INTO practica_registro (ci_estudiante_ref, ruc_empresa_ref, id_tutor_academico_ref, id_tutor_empresarial_ref, origen_rama, estado_cronograma, horas_acumuladas, horas_totales_requeridas)
        VALUES (${studentCCi}, ${companyFormalizedRuc}, ${tutorAcadCi}, ${tutorEmpCi}, 'BOLSA_EMPLEO'::origen_rama, 'F2_F3_PENDIENTE'::estado_cronograma, 160, 160)
      """.updateAndReturnGeneratedKey.apply().toInt

      sql"INSERT INTO formulario2_evaluacion (id_practica_ref, formulario2_pdf, estado_formulario2, contenido_rubrica_indexado) VALUES (${practiceCId}, ${pdfF2Id}, 'CONFORME'::estado_formulario2, 'Rubrica C')".update.apply()
    }

    // Primer Rechazo -> versión 1
    val r1 = AuditoriaCierreLogic.rechazarCierre(practiceCId, "Falta la firma del tutor académico en el Formulario 3.")
    r1 shouldBe Right(())

    val aud1 = DB.readOnly { implicit session =>
      sql"SELECT secuencial_version, estado_auditoria, observaciones_expediente FROM auditoria_cierre WHERE id_practica_ref = ${practiceCId} ORDER BY secuencial_version DESC LIMIT 1"
        .map(rs => (rs.int("secuencial_version"), rs.string("estado_auditoria"), rs.string("observaciones_expediente"))).single.apply().get
    }

    aud1._1 shouldBe 1
    aud1._2 shouldBe "RECHAZADO"
    aud1._3 shouldBe "Falta la firma del tutor académico en el Formulario 3."

    // Verificar que el conforme de F2 se reseteó a RECHAZADO
    val evalF2 = Formulario2DB.buscarUltimaEvaluacionPorPractica(practiceCId).get
    evalF2.estadoFormulario2 shouldBe EstadoFormulario2.RECHAZADO

    // Simular que vuelven a subir F2 y aprueban F2 de nuevo
    DB.localTx { implicit session =>
      sql"UPDATE formulario2_evaluacion SET estado_formulario2 = 'CONFORME'::estado_formulario2 WHERE id_practica_ref = ${practiceCId}".update.apply()
    }

    // Segundo Rechazo -> versión 2
    val r2 = AuditoriaCierreLogic.rechazarCierre(practiceCId, "Falta la firma del tutor empresarial en el Formulario 2.")
    r2 shouldBe Right(())

    val aud2 = DB.readOnly { implicit session =>
      sql"SELECT secuencial_version, estado_auditoria, observaciones_expediente FROM auditoria_cierre WHERE id_practica_ref = ${practiceCId} ORDER BY secuencial_version DESC LIMIT 1"
        .map(rs => (rs.int("secuencial_version"), rs.string("estado_auditoria"), rs.string("observaciones_expediente"))).single.apply().get
    }

    aud2._1 shouldBe 2
    aud2._2 shouldBe "RECHAZADO"
    aud2._3 shouldBe "Falta la firma del tutor empresarial en el Formulario 2."

    // Limpieza
    DB.localTx { implicit session =>
      sql"DELETE FROM auditoria_cierre WHERE id_practica_ref = ${practiceCId}".update.apply()
      sql"DELETE FROM formulario2_evaluacion WHERE id_practica_ref = ${practiceCId}".update.apply()
      sql"DELETE FROM practica_registro WHERE id_practica = ${practiceCId}".update.apply()
      sql"DELETE FROM estudiante_perfil WHERE identificacion = ${studentCCi}".update.apply()
      sql"DELETE FROM usuario WHERE identificacion = ${studentCCi}".update.apply()
    }
  }
}
