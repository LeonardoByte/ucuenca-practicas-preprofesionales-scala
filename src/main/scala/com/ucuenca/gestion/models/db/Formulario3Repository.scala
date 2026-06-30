package com.ucuenca.gestion.models.db

import scalikejdbc._
import com.ucuenca.gestion.models.entities.Formulario3Informe

object Formulario3Repository {

  /**
   * Obtiene el registro de Formulario 3 si existe para una práctica específica.
   */
  def buscarFormulario3PorPractica(idPractica: Int)(implicit session: DBSession = AutoSession): Option[Formulario3Informe] = {
    sql"""
      SELECT id_f3_informe, id_practica_ref, formulario3_pdf, fecha_emision
      FROM formulario3_informe
      WHERE id_practica_ref = ${idPractica}
    """.map { rs =>
      Formulario3Informe(
        idF3Informe = rs.int("id_f3_informe"),
        idPracticaRef = rs.int("id_practica_ref"),
        formulario3PDF = rs.int("formulario3_pdf"),
        fechaEmision = rs.localDate("fecha_emision")
      )
    }.single.apply()
  }

  /**
   * Actualiza el estado del último Formulario 2 de una práctica con conformidad o rechazo.
   */
  def actualizarEstadoFormulario2(idPractica: Int, estado: String, justificacionOpt: Option[String])(implicit session: DBSession = AutoSession): Unit = {
    sql"""
      UPDATE formulario2_evaluacion
      SET estado_formulario2 = ${estado}::estado_formulario2,
          justificacion_rechazo_docente = ${justificacionOpt}
      WHERE id_f2_evaluacion = (
        SELECT id_f2_evaluacion FROM formulario2_evaluacion
        WHERE id_practica_ref = ${idPractica}
        ORDER BY fecha_registro DESC
        LIMIT 1
      )
    """.update.apply()
  }

  /**
   * Inserta un nuevo registro en la tabla formulario3_informe.
   */
  def insertarFormulario3(idPractica: Int, pdfId: Int)(implicit session: DBSession = AutoSession): Int = {
    sql"""
      INSERT INTO formulario3_informe (id_practica_ref, formulario3_pdf, fecha_emision)
      VALUES (${idPractica}, ${pdfId}, CURRENT_DATE)
    """.updateAndReturnGeneratedKey.apply().toInt
  }

  /**
   * Registra los metadatos de un archivo PDF en la tabla archivo_pdf con tipo T3_FORMULARIO_3_INFORME.
   */
  def insertarArchivoPDF(nombreOriginal: String)(implicit session: DBSession = AutoSession): Int = {
    val rutaSegura = "uploads/formulario3/" + nombreOriginal
    sql"""
      INSERT INTO archivo_pdf (tipo_archivo, nombre_original, ruta_segura_servidor)
      VALUES ('T3_FORMULARIO_3_INFORME'::tipo_archivo_pdf, ${nombreOriginal}, ${rutaSegura})
    """.updateAndReturnGeneratedKey.apply().toInt
  }
}
