package com.ucuenca.gestion.models.db

import scalikejdbc._
import com.ucuenca.gestion.models.entities.{Usuario, UsuarioSistema}
import com.ucuenca.gestion.models.enums.{RolUsuario, EstadoCuenta}

object UsuarioSistemaRepository {

  /**
   * Busca un usuario y sus credenciales de sistema por su nombre de usuario.
   * Realiza un JOIN entre usuario_sistema y usuario.
   */
  def buscarPorUsername(username: String)(implicit session: DBSession = AutoSession): Option[(UsuarioSistema, Usuario)] = {
    sql"""
      SELECT 
        us.id_usuario_sistema, 
        us.username, 
        us.password_hash, 
        us.identificacion_usuario_ref,
        u.identificacion, 
        u.nombres_completos, 
        u.correo_electronico, 
        u.rol, 
        u.estado_cuenta
      FROM usuario_sistema us
      INNER JOIN usuario u ON us.identificacion_usuario_ref = u.identificacion
      WHERE us.username = ${username}
    """.map { rs =>
      val credenciales = UsuarioSistema(
        idUsuarioSistema = rs.int("id_usuario_sistema"),
        username = rs.string("username"),
        passwordHash = rs.string("password_hash"),
        identificacionUsuarioRef = rs.string("identificacion_usuario_ref")
      )

      val usuario = Usuario(
        identificacion = rs.string("identificacion"),
        nombresCompletos = rs.string("nombres_completos"),
        correoElectronico = rs.string("correo_electronico"),
        rol = RolUsuario.valueOf(rs.string("rol")),
        estadoCuenta = EstadoCuenta.valueOf(rs.string("estado_cuenta"))
      )

      (credenciales, usuario)
    }.single.apply()
  }
}
