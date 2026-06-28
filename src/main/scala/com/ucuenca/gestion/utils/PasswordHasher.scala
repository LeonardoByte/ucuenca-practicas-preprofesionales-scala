package com.ucuenca.gestion.utils

import org.mindrot.jbcrypt.BCrypt

object PasswordHasher {
  /**
   * Hashea una contraseña usando BCrypt.
   */
  def hash(password: String): String = {
    BCrypt.hashpw(password, BCrypt.gensalt())
  }

  /**
   * Verifica si la contraseña provista coincide con el hash.
   */
  def verify(password: String, hashedPassword: String): Boolean = {
    try {
      BCrypt.checkpw(password, hashedPassword)
    } catch {
      case _: IllegalArgumentException => false
    }
  }
}
