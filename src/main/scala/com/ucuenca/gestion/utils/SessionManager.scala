package com.ucuenca.gestion.utils

import com.ucuenca.gestion.models.entities.Usuario

object SessionManager {
  private var loggedInUser: Option[Usuario] = None

  /**
   * Registra el usuario autenticado actualmente en el sistema.
   */
  def setUsuario(u: Usuario): Unit = {
    loggedInUser = Option(u)
  }

  /**
   * Obtiene el usuario autenticado actualmente, si existe.
   */
  def getUsuario: Option[Usuario] = loggedInUser

  /**
   * Limpia la sesión actual del usuario.
   */
  def logout(): Unit = {
    loggedInUser = None
  }
}
