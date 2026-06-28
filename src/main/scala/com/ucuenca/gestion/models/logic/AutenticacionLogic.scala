package com.ucuenca.gestion.models.logic

import com.ucuenca.gestion.models.entities.Usuario
import com.ucuenca.gestion.models.db.UsuarioSistemaRepository
import com.ucuenca.gestion.utils.PasswordHasher
import scala.util.control.NonFatal

sealed trait AutenticacionFailure
object AutenticacionFailure {
  case object CamposVacios extends AutenticacionFailure
  case object CredencialesIncorrectas extends AutenticacionFailure
  case object CuentaSuspendida extends AutenticacionFailure
  case class ErrorPersistencia(mensaje: String) extends AutenticacionFailure
}

object AutenticacionLogic {

  /**
   * Valida las credenciales y el estado de la cuenta del usuario.
   * Retorna Either[AutenticacionFailure, Usuario] encapsulando cualquier fallo.
   */
  def autenticar(username: String, password: String): Either[AutenticacionFailure, Usuario] = {
    if (username == null || username.trim.isEmpty || password == null || password.trim.isEmpty) {
      Left(AutenticacionFailure.CamposVacios)
    } else {
      try {
        UsuarioSistemaRepository.buscarPorUsername(username.trim) match {
          case Some((credenciales, usuario)) =>
            if (PasswordHasher.verify(password, credenciales.passwordHash)) {
              if (usuario.estaActivo) {
                Right(usuario)
              } else {
                Left(AutenticacionFailure.CuentaSuspendida)
              }
            } else {
              Left(AutenticacionFailure.CredencialesIncorrectas)
            }
          case None =>
            Left(AutenticacionFailure.CredencialesIncorrectas)
        }
      } catch {
        case NonFatal(e) =>
          Left(AutenticacionFailure.ErrorPersistencia(e.getMessage))
      }
    }
  }
}
