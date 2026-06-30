package com.ucuenca.gestion.models.db

import scalikejdbc._
import com.ucuenca.gestion.models.entities.ExpedienteFormulario1
import com.ucuenca.gestion.models.dto.ExpedientePendienteCoordinadorDTO

object Formulario1DB {

  /**
   * Obtiene el expediente de Formulario 1 para una práctica.
   */
  def buscarExpediente(idPractica: Int)(implicit session: DBSession = AutoSession): Option[ExpedienteFormulario1] = {
    sql"""
      SELECT * FROM expediente_formulario1
      WHERE id_practica_ref = ${idPractica}
    """.map { rs =>
      ExpedienteFormulario1(
        idExpedienteF1 = rs.int("id_expediente_f1"),
        idPracticaRef = rs.int("id_practica_ref"),
        formulario1PDF = rs.intOpt("formulario1_pdf"),
        firmaEmpresarialValida = rs.boolean("firma_empresarial_valida"),
        firmaAcademicaValida = rs.boolean("firma_academica_valida"),
        estadoDeCoordinador = rs.boolean("estado_de_coordinador"),
        justificacionRechazoInicio = rs.stringOpt("justificacion_rechazo_inicio"),
        fechaAutorizacion = rs.localDateOpt("fecha_autorizacion")
      )
    }.single.apply()
  }

  /**
   * Verifica si el convenio marco de la empresa para la práctica está FORMALIZADO.
   */
  def verificarConvenioFormalizado(idPractica: Int)(implicit session: DBSession = AutoSession): Boolean = {
    sql"""
      SELECT ep.estado_convenio FROM practica_registro pr
      INNER JOIN empresa_perfil ep ON pr.ruc_empresa_ref = ep.identificacion
      WHERE pr.id_practica = ${idPractica}
    """.map(rs => rs.string("estado_convenio")).single.apply().contains("FORMALIZADO")
  }

  /**
   * Verifica si el estudiante tiene una validación de entrega de Carta de Compromiso en ventanilla.
   */
  def verificarCartaCompromisoExiste(ciEstudiante: String)(implicit session: DBSession = AutoSession): Boolean = {
    sql"""
      SELECT COUNT(1) FROM validacion_carta_compromiso
      WHERE ci_estudiante = ${ciEstudiante}
    """.map(rs => rs.int(1)).single.apply().getOrElse(0) > 0
  }

  /**
   * Inserta o actualiza el expediente inicial de Formulario 1 de un estudiante,
   * reseteando las firmas previas y la justificación de rechazo si existían.
   */
  def insertarOActualizarExpediente(idPractica: Int, pdfId: Int)(implicit session: DBSession = AutoSession): Unit = {
    val existe = sql"SELECT COUNT(1) FROM expediente_formulario1 WHERE id_practica_ref = ${idPractica}"
      .map(rs => rs.int(1)).single.apply().getOrElse(0) > 0

    if (existe) {
      sql"""
        UPDATE expediente_formulario1
        SET formulario1_pdf = ${pdfId},
            firma_empresarial_valida = FALSE,
            firma_academica_valida = FALSE,
            estado_de_coordinador = FALSE,
            justificacion_rechazo_inicio = NULL,
            fecha_autorizacion = NULL
        WHERE id_practica_ref = ${idPractica}
      """.update.apply()
    } else {
      sql"""
        INSERT INTO expediente_formulario1 (id_practica_ref, formulario1_pdf)
        VALUES (${idPractica}, ${pdfId})
      """.update.apply()
    }
  }

  /**
   * Registra la firma digital del tutor empresarial.
   */
  def actualizarFirmaEmpresa(idPractica: Int, pdfId: Int)(implicit session: DBSession = AutoSession): Unit = {
    sql"""
      UPDATE expediente_formulario1
      SET formulario1_pdf = ${pdfId},
          firma_empresarial_valida = TRUE
      WHERE id_practica_ref = ${idPractica}
    """.update.apply()
  }

  /**
   * Registra la firma digital del tutor académico.
   */
  def actualizarFirmaAcademica(idPractica: Int, pdfId: Int)(implicit session: DBSession = AutoSession): Unit = {
    sql"""
      UPDATE expediente_formulario1
      SET formulario1_pdf = ${pdfId},
          firma_academica_valida = TRUE
      WHERE id_practica_ref = ${idPractica}
    """.update.apply()
  }

  /**
   * Aprueba el Plan de Aprendizaje y transiciona el estado de la práctica a EN_DESARROLLO de forma atómica.
   */
  def autorizarPlan(idPractica: Int, pdfId: Int)(implicit session: DBSession = AutoSession): Unit = {
    sql"""
      UPDATE expediente_formulario1
      SET formulario1_pdf = ${pdfId},
          estado_de_coordinador = TRUE,
          fecha_autorizacion = CURRENT_DATE
      WHERE id_practica_ref = ${idPractica}
    """.update.apply()

    sql"""
      UPDATE practica_registro
      SET estado_cronograma = 'EN_DESARROLLO'::estado_cronograma
      WHERE id_practica = ${idPractica}
    """.update.apply()
  }

  /**
   * Realiza un reseteo lógico al rechazar el expediente: limpia las firmas y registra el motivo.
   * Esto mantiene la fila única para que el estudiante resuba el PDF y comience un nuevo ciclo.
   */
  def rechazarExpediente(idPractica: Int, justificacion: String)(implicit session: DBSession = AutoSession): Unit = {
    sql"""
      UPDATE expediente_formulario1
      SET firma_empresarial_valida = FALSE,
          firma_academica_valida = FALSE,
          estado_de_coordinador = FALSE,
          justificacion_rechazo_inicio = ${justificacion.trim},
          fecha_autorizacion = NULL
      WHERE id_practica_ref = ${idPractica}
    """.update.apply()
  }

  /**
   * Lista estudiantes listos para la firma del tutor empresarial.
   */
  def listarEstudiantesParaEmpresa(tutorEmpCI: String)(implicit session: DBSession = AutoSession): List[(Int, String)] = {
    sql"""
      SELECT pr.id_practica, u.nombres_completos
      FROM practica_registro pr
      INNER JOIN usuario u ON pr.ci_estudiante_ref = u.identificacion
      INNER JOIN expediente_formulario1 ef1 ON pr.id_practica = ef1.id_practica_ref
      WHERE pr.id_tutor_empresarial_ref = ${tutorEmpCI}
        AND ef1.firma_empresarial_valida = FALSE
      ORDER BY u.nombres_completos ASC
    """.map(rs => (rs.int("id_practica"), rs.string("nombres_completos"))).list.apply()
  }

  /**
   * Lista estudiantes listos para la firma del tutor académico.
   */
  def listarEstudiantesParaAcademico(tutorAcadCI: String)(implicit session: DBSession = AutoSession): List[(Int, String)] = {
    sql"""
      SELECT pr.id_practica, u.nombres_completos
      FROM practica_registro pr
      INNER JOIN usuario u ON pr.ci_estudiante_ref = u.identificacion
      INNER JOIN expediente_formulario1 ef1 ON pr.id_practica = ef1.id_practica_ref
      WHERE pr.id_tutor_academico_ref = ${tutorAcadCI}
        AND ef1.firma_empresarial_valida = TRUE
        AND ef1.firma_academica_valida = FALSE
      ORDER BY u.nombres_completos ASC
    """.map(rs => (rs.int("id_practica"), rs.string("nombres_completos"))).list.apply()
  }

  /**
   * Lista estudiantes listos para la resolución final del coordinador.
   */
  def listarPendientesCoordinador()(implicit session: DBSession = AutoSession): List[ExpedientePendienteCoordinadorDTO] = {
    sql"""
      SELECT 
        pr.id_practica,
        u_est.nombres_completos AS nombre_estudiante,
        u_emp.nombres_completos AS nombre_empresa
      FROM practica_registro pr
      INNER JOIN usuario u_est ON pr.ci_estudiante_ref = u_est.identificacion
      INNER JOIN usuario u_emp ON pr.ruc_empresa_ref = u_emp.identificacion
      INNER JOIN expediente_formulario1 ef1 ON pr.id_practica = ef1.id_practica_ref
      WHERE ef1.firma_empresarial_valida = TRUE
        AND ef1.firma_academica_valida = TRUE
        AND ef1.estado_de_coordinador = FALSE
      ORDER BY u_est.nombres_completos ASC
    """.map { rs =>
      ExpedientePendienteCoordinadorDTO(
        idPractica = rs.int("id_practica"),
        nombreEstudiante = rs.string("nombre_estudiante"),
        nombreEmpresa = rs.string("nombre_empresa")
      )
    }.list.apply()
  }
}
