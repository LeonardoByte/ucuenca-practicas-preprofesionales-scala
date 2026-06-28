package com.ucuenca.gestion.models.logic

import com.ucuenca.gestion.models.entities.Usuario
import com.ucuenca.gestion.models.enums.{RolUsuario, EstadoCuenta}
import com.ucuenca.gestion.models.db.DirectorioRepository
import scala.util.control.NonFatal

sealed trait DirectorioFailure
object DirectorioFailure {
  case class Validacion(mensaje: String) extends DirectorioFailure
  case class ErrorPersistencia(mensaje: String) extends DirectorioFailure
}

object DirectorioLogic {

  /**
   * Obtiene la nómina de usuarios filtrada de forma concurrente por texto, rol y estado.
   */
  def buscarUsuarios(
    textoBusqueda: String,
    rol: RolUsuario,
    estado: EstadoCuenta
  ): Either[DirectorioFailure, Seq[Usuario]] = {
    try {
      val textoOpt = Option(textoBusqueda).filter(_.trim.nonEmpty)
      val rolOpt = Option(rol)
      val estadoOpt = Option(estado)

      Right(DirectorioRepository.buscarUsuarios(textoOpt, rolOpt, estadoOpt))
    } catch {
      case NonFatal(e) =>
        Left(DirectorioFailure.ErrorPersistencia(s"Error al buscar usuarios: ${e.getMessage}"))
    }
  }

  /**
   * Modifica el nombre completo y correo de un usuario, aplicando validaciones de longitud y formato.
   */
  def actualizarContacto(
    identificacion: String,
    nombresCompletos: String,
    correoElectronico: String
  ): Either[DirectorioFailure, Unit] = {
    if (nombresCompletos == null || nombresCompletos.trim.length < 3) {
      Left(DirectorioFailure.Validacion("El nombre completo debe tener al menos 3 caracteres."))
    } else if (correoElectronico == null || !correoElectronico.contains("@") || !correoElectronico.contains(".")) {
      Left(DirectorioFailure.Validacion("El correo electrónico no es válido."))
    } else {
      try {
        val rows = DirectorioRepository.actualizarUsuario(identificacion, nombresCompletos.trim, correoElectronico.trim)
        if (rows > 0) {
          Right(())
        } else {
          Left(DirectorioFailure.Validacion("No se encontró el usuario a actualizar."))
        }
      } catch {
        case NonFatal(e) =>
          Left(DirectorioFailure.ErrorPersistencia(s"Error al actualizar datos de contacto: ${e.getMessage}"))
      }
    }
  }

  /**
   * Actualiza lógicamente el estado de cuenta del usuario (ACTIVA o SUSPENDIDA).
   * No se permite eliminación física (DELETE).
   */
  def cambiarEstado(
    identificacion: String,
    nuevoEstado: EstadoCuenta
  ): Either[DirectorioFailure, EstadoCuenta] = {
    if (nuevoEstado == null) {
      Left(DirectorioFailure.Validacion("El estado de cuenta no puede ser nulo."))
    } else {
      try {
        val rows = DirectorioRepository.cambiarEstadoCuenta(identificacion, nuevoEstado)
        if (rows > 0) {
          Right(nuevoEstado)
        } else {
          Left(DirectorioFailure.Validacion("No se encontró el usuario seleccionado."))
        }
      } catch {
        case NonFatal(e) =>
          Left(DirectorioFailure.ErrorPersistencia(s"Error al suspender/activar cuenta: ${e.getMessage}"))
      }
    }
  }
}
