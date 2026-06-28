package com.ucuenca.gestion.models.db

import scalikejdbc._
import com.ucuenca.gestion.models.entities.Usuario
import com.ucuenca.gestion.models.enums.{RolUsuario, EstadoCuenta}

object DirectorioRepository {

  /**
   * Realiza una búsqueda dinámica sobre la tabla usuario.
   * Permite filtrar simultáneamente por texto libre (nombre, CI, correo), rol y estado de cuenta.
   */
  def buscarUsuarios(
    textoBusqueda: Option[String],
    rol: Option[RolUsuario],
    estado: Option[EstadoCuenta]
  )(implicit session: DBSession = AutoSession): Seq[Usuario] = {

    val textoCond = textoBusqueda.filter(_.trim.nonEmpty).map { text =>
      val patron = s"%${text.trim.toLowerCase}%"
      sqls"(lower(nombres_completos) LIKE ${patron} OR lower(identificacion) LIKE ${patron} OR lower(correo_electronico) LIKE ${patron})"
    }

    val rolCond = rol.map(r => sqls"rol = ${r.toString}::rol_usuario")
    val estadoCond = estado.map(e => sqls"estado_cuenta = ${e.toString}::estado_cuenta")

    val conditions: Seq[SQLSyntax] = Seq(textoCond, rolCond, estadoCond).flatten

    val whereClause = if (conditions.nonEmpty) {
      sqls"WHERE ${SQLSyntax.join(conditions, sqls"AND")}"
    } else {
      sqls""
    }

    sql"""
      SELECT identificacion, nombres_completos, correo_electronico, rol, estado_cuenta
      FROM usuario
      $whereClause
      ORDER BY nombres_completos
    """.map { rs =>
      Usuario(
        identificacion = rs.string("identificacion"),
        nombresCompletos = rs.string("nombres_completos"),
        correoElectronico = rs.string("correo_electronico"),
        rol = RolUsuario.valueOf(rs.string("rol")),
        estadoCuenta = EstadoCuenta.valueOf(rs.string("estado_cuenta"))
      )
    }.list.apply()
  }

  /**
   * Modifica los datos de nombres completos y correo electrónico de un usuario.
   */
  def actualizarUsuario(
    identificacion: String,
    nombresCompletos: String,
    correoElectronico: String
  )(implicit session: DBSession = AutoSession): Int = {
    sql"""
      UPDATE usuario
      SET nombres_completos = ${nombresCompletos},
          correo_electronico = ${correoElectronico}
      WHERE identificacion = ${identificacion}
    """.update.apply()
  }

  /**
   * Cambia el estado de la cuenta (ACTIVA / SUSPENDIDA) de un usuario de forma lógica.
   */
  def cambiarEstadoCuenta(
    identificacion: String,
    nuevoEstado: EstadoCuenta
  )(implicit session: DBSession = AutoSession): Int = {
    sql"""
      UPDATE usuario
      SET estado_cuenta = ${nuevoEstado.toString}::estado_cuenta
      WHERE identificacion = ${identificacion}
    """.update.apply()
  }
}
