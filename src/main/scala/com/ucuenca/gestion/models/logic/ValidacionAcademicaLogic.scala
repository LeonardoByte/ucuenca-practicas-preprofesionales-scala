package com.ucuenca.gestion.models.logic

import com.ucuenca.gestion.models.dto.PostulacionPendienteDTO
import com.ucuenca.gestion.models.db.PostulacionBolsaRepository
import scalikejdbc.DB
import scala.util.control.NonFatal

object ValidacionAcademicaLogic {

  /**
   * Obtiene el listado de postulaciones pendientes de validación académica por la coordinación.
   */
  def listarPendientes(): Either[BolsaFailure, List[PostulacionPendienteDTO]] = {
    try {
      Right(PostulacionBolsaRepository.listarPostulacionesPendientes())
    } catch {
      case NonFatal(e) =>
        Left(BolsaFailure.ErrorPersistencia(s"Error al listar postulaciones: ${e.getMessage}"))
    }
  }

  /**
   * Aprueba académicamente la postulación, manteniendo su estado en PENDIENTE.
   */
  def aprobar(id: Int): Either[BolsaFailure, Unit] = {
    try {
      DB.localTx { implicit session =>
        PostulacionBolsaRepository.aprobarPostulacion(id)
      }
      Right(())
    } catch {
      case NonFatal(e) =>
        Left(BolsaFailure.ErrorPersistencia(s"Error al aprobar postulación académica: ${e.getMessage}"))
    }
  }

  /**
   * Rechaza académicamente la postulación, exigiendo un comentario de justificación.
   */
  def rechazar(id: Int, comentario: String): Either[BolsaFailure, Unit] = {
    try {
      val trimmed = Option(comentario).getOrElse("").trim
      if (trimmed.isEmpty) {
        return Left(BolsaFailure.Validacion("El comentario de justificación técnica del rechazo es obligatorio."))
      }

      DB.localTx { implicit session =>
        PostulacionBolsaRepository.rechazarPostulacion(id, trimmed)
      }
      Right(())
    } catch {
      case NonFatal(e) =>
        Left(BolsaFailure.ErrorPersistencia(s"Error al rechazar postulación académica: ${e.getMessage}"))
    }
  }
}
