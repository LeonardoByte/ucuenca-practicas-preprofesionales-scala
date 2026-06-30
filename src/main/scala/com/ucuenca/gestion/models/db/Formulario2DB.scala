package com.ucuenca.gestion.models.db

import scalikejdbc._
import com.ucuenca.gestion.models.entities.Formulario2Evaluacion
import com.ucuenca.gestion.models.enums.EstadoFormulario2

object Formulario2DB {

  /**
   * Obtiene todas las evaluaciones de Formulario 2 para una práctica en orden cronológico inverso.
   */
  def buscarEvaluacionesPorPractica(idPractica: Int)(implicit session: DBSession = AutoSession): List[Formulario2Evaluacion] = {
    sql"""
      SELECT id_f2_evaluacion, id_practica_ref, formulario2_pdf, estado_formulario2, 
             justificacion_rechazo_docente, contenido_rubrica_indexado, fecha_registro
      FROM formulario2_evaluacion
      WHERE id_practica_ref = ${idPractica}
      ORDER BY fecha_registro DESC
    """.map { rs =>
      Formulario2Evaluacion(
        idF2Evaluacion = rs.int("id_f2_evaluacion"),
        idPracticaRef = rs.int("id_practica_ref"),
        formulario2PDF = rs.int("formulario2_pdf"),
        estadoFormulario2 = EstadoFormulario2.valueOf(rs.string("estado_formulario2")),
        justificacionRechazoDocente = rs.stringOpt("justificacion_rechazo_docente"),
        contenidoRubricaIndexado = rs.string("contenido_rubrica_indexado"),
        fechaRegistro = rs.localDateTime("fecha_registro")
      )
    }.list.apply()
  }

  /**
   * Obtiene la última evaluación del Formulario 2 para una práctica específica.
   */
  def buscarUltimaEvaluacionPorPractica(idPractica: Int)(implicit session: DBSession = AutoSession): Option[Formulario2Evaluacion] = {
    buscarEvaluacionesPorPractica(idPractica).headOption
  }

  /**
   * Inserta un nuevo registro en la tabla formulario2_evaluacion con estado inicial PENDIENTE_REVISION.
   */
  def insertarEvaluacion(idPractica: Int, pdfId: Int, contenidoRubrica: String)(implicit session: DBSession = AutoSession): Int = {
    sql"""
      INSERT INTO formulario2_evaluacion (
        id_practica_ref, 
        formulario2_pdf, 
        estado_formulario2, 
        contenido_rubrica_indexado, 
        fecha_registro
      ) VALUES (
        ${idPractica}, 
        ${pdfId}, 
        'PENDIENTE_REVISION'::estado_formulario2, 
        ${contenidoRubrica.trim}, 
        CURRENT_TIMESTAMP
      )
    """.updateAndReturnGeneratedKey.apply().toInt
  }

  /**
   * Registra los metadatos de un archivo PDF en la tabla archivo_pdf con tipo T2_FORMULARIO_2_RUBRICA.
   */
  def insertarArchivoPDF(nombreOriginal: String)(implicit session: DBSession = AutoSession): Int = {
    val rutaSegura = "uploads/formulario2/" + nombreOriginal
    sql"""
      INSERT INTO archivo_pdf (tipo_archivo, nombre_original, ruta_segura_servidor)
      VALUES ('T2_FORMULARIO_2_RUBRICA'::tipo_archivo_pdf, ${nombreOriginal}, ${rutaSegura})
    """.updateAndReturnGeneratedKey.apply().toInt
  }

  /**
   * Actualiza de manera directa el estado_cronograma de una práctica.
   */
  def actualizarEstadoCronograma(idPractica: Int, nuevoEstado: String)(implicit session: DBSession = AutoSession): Unit = {
    sql"""
      UPDATE practica_registro
      SET estado_cronograma = ${nuevoEstado}::estado_cronograma
      WHERE id_practica = ${idPractica}
    """.update.apply()
  }
}
