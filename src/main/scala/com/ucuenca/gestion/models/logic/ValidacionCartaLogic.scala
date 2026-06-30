package com.ucuenca.gestion.models.logic

import com.ucuenca.gestion.models.dto.CartaCompromisoPendienteDTO
import com.ucuenca.gestion.models.db.ValidacionCartaRepository
import scalikejdbc.DB
import scala.util.control.NonFatal

sealed trait ValidacionCartaFailure
object ValidacionCartaFailure {
  case class Validacion(mensaje: String)        extends ValidacionCartaFailure
  case class ErrorPersistencia(mensaje: String) extends ValidacionCartaFailure
}

object ValidacionCartaLogic {

  /**
   * Obtiene la lista de alumnos pendientes de certificar Carta de Compromiso.
   */
  def listarPendientes(searchQuery: Option[String]): Either[ValidacionCartaFailure, List[CartaCompromisoPendienteDTO]] = {
    try {
      Right(ValidacionCartaRepository.listarPendientes(searchQuery))
    } catch {
      case NonFatal(e) =>
        Left(ValidacionCartaFailure.ErrorPersistencia(s"Error al consultar cartas compromiso pendientes: ${e.getMessage}"))
    }
  }

  /**
   * Registra y certifica la entrega de las 3 copias físicas firmadas de la Carta de Compromiso.
   * Valida obligatoriamente que la entrega de las 3 copias sea TRUE.
   */
  def certificarCarta(ciEstudiante: String, entregadoTresCopias: Boolean): Either[ValidacionCartaFailure, Unit] = {
    if (!entregadoTresCopias) {
      return Left(ValidacionCartaFailure.Validacion("Se debe certificar la entrega física de las 3 copias de forma obligatoria."))
    }

    try {
      DB.localTx { implicit session =>
        ValidacionCartaRepository.registrarValidacion(ciEstudiante, entregadoTresCopias)
      }
      Right(())
    } catch {
      case NonFatal(e) =>
        Left(ValidacionCartaFailure.ErrorPersistencia(s"Error de persistencia al registrar certificación: ${e.getMessage}"))
    }
  }
}
