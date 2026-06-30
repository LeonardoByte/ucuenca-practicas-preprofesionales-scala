package com.ucuenca.gestion.models.logic

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll
import scalikejdbc._
import com.ucuenca.gestion.utils.DatabaseConnection
import java.time.LocalDate

class AnalyticsLogicSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  val studentCi = "6161616161"
  val companyRuc = "0707070707019"
  val tutorEmpCi = "6262626262"
  val tutorAcadCi = "6363636363"

  var pdfMallaId: Int = _
  var practiceId: Int = _
  var f2Id: Int = _

  override def beforeAll(): Unit = {
    DatabaseConnection.initialize()

    DB.localTx { implicit session =>
      // Limpieza de seguridad
      sql"DELETE FROM actividad_cronograma WHERE id_practica_ref IN (SELECT id_practica FROM practica_registro WHERE ci_estudiante_ref = ${studentCi})".update.apply()
      sql"DELETE FROM auditoria_cierre WHERE id_practica_ref IN (SELECT id_practica FROM practica_registro WHERE ci_estudiante_ref = ${studentCi})".update.apply()
      sql"DELETE FROM formulario3_informe WHERE id_practica_ref IN (SELECT id_practica FROM practica_registro WHERE ci_estudiante_ref = ${studentCi})".update.apply()
      sql"DELETE FROM formulario2_evaluacion WHERE id_practica_ref IN (SELECT id_practica FROM practica_registro WHERE ci_estudiante_ref = ${studentCi})".update.apply()
      sql"DELETE FROM expediente_formulario1 WHERE id_practica_ref IN (SELECT id_practica FROM practica_registro WHERE ci_estudiante_ref = ${studentCi})".update.apply()
      sql"DELETE FROM practica_registro WHERE ci_estudiante_ref = ${studentCi}".update.apply()
      sql"DELETE FROM estudiante_perfil WHERE identificacion = ${studentCi}".update.apply()
      sql"DELETE FROM tutor_empresarial_perfil WHERE identificacion = ${tutorEmpCi}".update.apply()
      sql"DELETE FROM empresa_perfil WHERE identificacion = ${companyRuc}".update.apply()
      sql"DELETE FROM usuario WHERE identificacion IN (${studentCi}, ${tutorEmpCi}, ${tutorAcadCi}, ${companyRuc})".update.apply()
      sql"DELETE FROM archivo_pdf WHERE nombre_original IN ('malla_an.pdf', 'f2_an.pdf')".update.apply()

      // 1. Crear PDF
      pdfMallaId = sql"INSERT INTO archivo_pdf (tipo_archivo, nombre_original, ruta_segura_servidor) VALUES ('T9_MALLA_ACADEMICA'::tipo_archivo_pdf, 'malla_an.pdf', 'uploads/m_an.pdf')".updateAndReturnGeneratedKey.apply().toInt
      val pdfF2Id = sql"INSERT INTO archivo_pdf (tipo_archivo, nombre_original, ruta_segura_servidor) VALUES ('T2_FORMULARIO_2_RUBRICA'::tipo_archivo_pdf, 'f2_an.pdf', 'uploads/f2_an.pdf')".updateAndReturnGeneratedKey.apply().toInt

      // 2. Crear usuarios
      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${studentCi}, 'Estudiante An', 'est_an@test.com', 'ESTUDIANTE'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()
      sql"INSERT INTO estudiante_perfil (identificacion, ciclo_actual, id_carrera_ref, estado_matricula, estado_estudiante_practica, malla_academica_pdf) VALUES (${studentCi}, 8, 1, 'REGULAR'::estado_matricula, 'CON_PRACTICA_ACTIVA'::estado_estudiante_practica, ${pdfMallaId})".update.apply()

      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${companyRuc}, 'Empresa An', 'emp_an@test.com', 'EMPRESA'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()
      sql"INSERT INTO empresa_perfil (identificacion, direccion_matriz, mision, vision, estado_convenio) VALUES (${companyRuc}, 'Matriz An', 'M', 'V', 'FORMALIZADO'::estado_convenio)".update.apply()

      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${tutorEmpCi}, 'Tutor Emp An', 't_emp_an@test.com', 'TUTOR_EMPRESARIAL'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()
      sql"INSERT INTO tutor_empresarial_perfil (identificacion, empresa_id_ref, telefono_contacto) VALUES (${tutorEmpCi}, ${companyRuc}, '0998765432')".update.apply()

      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${tutorAcadCi}, 'Tutor Acad An', 't_acad_an@test.com', 'TUTOR_ACADEMICO'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()

      // 3. Crear Práctica (branch EMPRESA_PROPIA para testear bypasses)
      practiceId = sql"""
        INSERT INTO practica_registro (ci_estudiante_ref, ruc_empresa_ref, id_tutor_academico_ref, id_tutor_empresarial_ref, origen_rama, estado_cronograma, horas_acumuladas, horas_totales_requeridas)
        VALUES (${studentCi}, ${companyRuc}, ${tutorAcadCi}, ${tutorEmpCi}, 'EMPRESA_PROPIA'::origen_rama, 'EN_DESARROLLO'::estado_cronograma, 90, 240)
      """.updateAndReturnGeneratedKey.apply().toInt

      // 4. Crear solicitud empresa propia
      sql"""
        INSERT INTO solicitud_empresa_propia (
          ci_estudiante_ref, ruc_empresa_propia, nombre_entidad_externa,
          contacto_empresa_propia, horas_empresa_propia, direccion_empresa_propia,
          mision_empresa_propia, vision_empresa_propia, contenido_oficio_transcrito,
          ci_supervisor_externo, nombres_supervisor_externo, email_supervisor_externo,
          telefono_supervisor_externo, oficio_solicitud_inicial_pdf, codigo_oficio_vuelta,
          id_tutor_acad_asignado, estado_tramite
        ) VALUES (
          ${studentCi}, ${companyRuc}, 'Empresa An',
          'contact@empresa.com', 240, 'Direccion An',
          'M', 'V', 'Oficio...',
          ${tutorEmpCi}, 'Tutor Emp An', 't_emp_an@test.com',
          '0998765432', ${pdfMallaId}, 'UCUENCA-VINC-2026-0001',
          ${tutorAcadCi}, 'FORMALIZADO'::estado_convenio
        )
      """.update.apply()

      // 5. Crear F2 RECHAZADO para comentarios históricos
      f2Id = sql"""
        INSERT INTO formulario2_evaluacion (id_practica_ref, formulario2_pdf, estado_formulario2, justificacion_rechazo_docente, contenido_rubrica_indexado)
        VALUES (${practiceId}, ${pdfF2Id}, 'RECHAZADO'::estado_formulario2, 'Rúbrica inválida por baja puntuación', '{}')
      """.updateAndReturnGeneratedKey.apply().toInt

      // 6. Crear actividad rechazada
      sql"""
        INSERT INTO actividad_cronograma (id_practica_ref, numero_secuencial, descripcion_tarea, origen_creacion, estado_actividad, comentario_observacion)
        VALUES (${practiceId}, 1, 'Tarea An', 'ESTUDIANTE'::origen_creacion_actividad, 'RECHAZADA'::estado_actividad, 'Actividad no relacionada')
      """.update.apply()
    }
  }

  override def afterAll(): Unit = {
    DB.localTx { implicit session =>
      sql"DELETE FROM actividad_cronograma WHERE id_practica_ref = ${practiceId}".update.apply()
      sql"DELETE FROM formulario2_evaluacion WHERE id_practica_ref = ${practiceId}".update.apply()
      sql"DELETE FROM solicitud_empresa_propia WHERE ci_estudiante_ref = ${studentCi}".update.apply()
      sql"DELETE FROM practica_registro WHERE id_practica = ${practiceId}".update.apply()
      sql"DELETE FROM estudiante_perfil WHERE identificacion = ${studentCi}".update.apply()
      sql"DELETE FROM tutor_empresarial_perfil WHERE identificacion = ${tutorEmpCi}".update.apply()
      sql"DELETE FROM empresa_perfil WHERE identificacion = ${companyRuc}".update.apply()
      sql"DELETE FROM usuario WHERE identificacion IN (${studentCi}, ${tutorEmpCi}, ${tutorAcadCi}, ${companyRuc})".update.apply()
      sql"DELETE FROM archivo_pdf WHERE id_archivo_pdf = ${pdfMallaId}".update.apply()
    }
  }

  "AnalyticsLogic" should "reconstruir correctamente la línea de vida cronológica de 6 etapas de la práctica" in {
    val r = AnalyticsLogic.getTimeline(practiceId)
    r.isRight shouldBe true
    val details = r.getOrElse(fail("No timeline found"))
    details.idPractica shouldBe practiceId
    details.origenRama shouldBe "EMPRESA_PROPIA"
    
    // Deberían haber 6 etapas en total
    details.stages.size shouldBe 6
    
    // Etapa 1 y 2 deben marcarse como Omitidas (debido a origen EMPRESA_PROPIA)
    details.stages(0).estado shouldBe "Omitida"
    details.stages(1).estado shouldBe "Omitida"
    
    // Etapa 3 debe ser Oficio Aprobado
    details.stages(2).estado should include("FORMALIZADO")
  }

  it should "permitir buscar prácticas asociadas por criterios de búsqueda de estudiante o empresa" in {
    val r = AnalyticsLogic.searchPractices("Estudiante An")
    r.isRight shouldBe true
    val list = r.getOrElse(Nil)
    list.map(_.idPractica) should contain(practiceId)
  }

  it should "extraer de forma íntegra y ordenada el historial de comentarios de rechazo de las retroalimentaciones" in {
    val r = AnalyticsLogic.getRejectionComments(practiceId)
    r.isRight shouldBe true
    val list = r.getOrElse(Nil)
    
    // Debe contener el comentario de F2 y el comentario de la actividad
    list.map(_.comentario) should contain allOf(
      "Rúbrica inválida por baja puntuación",
      "Actividad no relacionada"
    )
  }

  it should "ejecutar consultas GROUP BY sobre reportes analíticos con o sin filtros de carrera" in {
    // 1. Estudiantes Activos
    val r1 = AnalyticsLogic.fetchActiveStudents(None)
    r1.isRight shouldBe true
    r1.getOrElse(Nil).map(_.nombreEstudiante) should contain("Estudiante An")

    // 2. Ranking de Empresas
    val r2 = AnalyticsLogic.fetchTopHostCompanies(None)
    r2.isRight shouldBe true
    r2.getOrElse(Nil).map(_.empresa) should contain("Empresa An")

    // 3. Porcentaje de Postulaciones
    val r3 = AnalyticsLogic.fetchPostulationPercentages(None)
    r3.isRight shouldBe true

    // 4. Índices de Satisfacción
    val r4 = AnalyticsLogic.fetchSatisfactionAverages(None)
    r4.isRight shouldBe true
  }
}
