package com.ucuenca.gestion.models.logic

import com.ucuenca.gestion.models.dto.OfertaRevisionDTO
import com.ucuenca.gestion.models.enums.EstadoOferta
import com.ucuenca.gestion.models.db.OfertaRepository
import scalikejdbc.DB
import scala.util.control.NonFatal

sealed trait RevisionFailure
object RevisionFailure {
  case class Validacion(mensaje: String) extends RevisionFailure
  case class ErrorPersistencia(mensaje: String) extends RevisionFailure
}

object RevisionOffersLogic {
  // Alias for our logic class
}

object RevisionOfertasLogic {

  /**
   * Obtiene el listado de ofertas pendientes de aprobación.
   */
  def listarPendientes(): Either[RevisionFailure, List[OfertaRevisionDTO]] = {
    try {
      Right(OfertaRepository.listarPendientes())
    } catch {
      case NonFatal(e) =>
        Left(RevisionFailure.ErrorPersistencia(s"Error al listar ofertas pendientes: ${e.getMessage}"))
    }
  }

  /**
   * Aprueba una convocatoria de oferta de practicantes.
   */
  def aprobar(idOferta: Int): Either[RevisionFailure, Unit] = {
    try {
      OfertaRepository.buscarPorId(idOferta) match {
        case Some(oferta) =>
          if (oferta.estadoOferta == EstadoOferta.PENDIENTE) {
            DB.localTx { implicit session =>
              OfertaRepository.aprobarOferta(idOferta)
            }
            Right(())
          } else {
            Left(RevisionFailure.Validacion("La oferta seleccionada ya no se encuentra pendiente."))
          }
        case None =>
          Left(RevisionFailure.Validacion("La oferta especificada no existe."))
      }
    } catch {
      case NonFatal(e) =>
        Left(RevisionFailure.ErrorPersistencia(s"Error al aprobar la oferta: ${e.getMessage}"))
    }
  }

  /**
   * Rechaza una convocatoria de oferta de practicantes, exigiendo justificación técnica obligatoria.
   */
  def rechazar(idOferta: Int, justificacion: String): Either[RevisionFailure, Unit] = {
    if (justificacion == null || justificacion.trim.isEmpty) {
      Left(RevisionFailure.Validacion("La justificación técnica de rechazo es obligatoria."))
    } else {
      try {
        OfertaRepository.buscarPorId(idOferta) match {
          case Some(oferta) =>
            if (oferta.estadoOferta == EstadoOferta.PENDIENTE) {
              DB.localTx { implicit session =>
                OfertaRepository.rechazarOferta(idOferta, justificacion.trim)
              }
              Right(())
            } else {
              Left(RevisionFailure.Validacion("La oferta seleccionada ya no se encuentra pendiente."))
            }
          case None =>
            Left(RevisionFailure.Validacion("La oferta especificada no existe."))
        }
      } catch {
        case NonFatal(e) =>
          Left(RevisionFailure.ErrorPersistencia(s"Error al rechazar la oferta: ${e.getMessage}"))
      }
    }
  }
}
