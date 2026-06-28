package com.ucuenca.gestion.models.logic

import com.ucuenca.gestion.models.entities.OfertaConvocatoria
import com.ucuenca.gestion.models.enums.EstadoOferta
import com.ucuenca.gestion.models.db.OfertaRepository
import scalikejdbc.DB
import scala.util.control.NonFatal

sealed trait MonitoreoFailure
object MonitoreoFailure {
  case class Validacion(mensaje: String) extends MonitoreoFailure
  case class ErrorPersistencia(mensaje: String) extends MonitoreoFailure
}

object MonitoreoOfertasLogic {

  /**
   * Retorna el registro cronológico de las ofertas publicadas por la organización.
   */
  def listarPorEmpresa(rucEmpresa: String): Either[MonitoreoFailure, List[OfertaConvocatoria]] = {
    try {
      Right(OfertaRepository.buscarPorEmpresa(rucEmpresa))
    } catch {
      case NonFatal(e) =>
        Left(MonitoreoFailure.ErrorPersistencia(s"Error al consultar el historial de ofertas: ${e.getMessage}"))
    }
  }

  /**
   * Fuerza el cierre manual de una convocatoria activa (en estado PENDIENTE o APROBADA).
   * Modifica su estado a CERRADA_MANUAL.
   */
  def forzarCierre(idOferta: Int): Either[MonitoreoFailure, Unit] = {
    try {
      OfertaRepository.buscarPorId(idOferta) match {
        case Some(oferta) =>
          if (oferta.estadoOferta == EstadoOferta.PENDIENTE || oferta.estadoOferta == EstadoOferta.APROBADA) {
            DB.localTx { implicit session =>
              OfertaRepository.cerrarOferta(idOferta)
            }
            Right(())
          } else {
            Left(MonitoreoFailure.Validacion("Solo se pueden cerrar manualmente ofertas activas (PENDIENTE o APROBADA)."))
          }
        case None =>
          Left(MonitoreoFailure.Validacion("La oferta especificada no existe."))
      }
    } catch {
      case NonFatal(e) =>
        Left(MonitoreoFailure.ErrorPersistencia(s"Error al forzar el cierre de la oferta: ${e.getMessage}"))
    }
  }
}
