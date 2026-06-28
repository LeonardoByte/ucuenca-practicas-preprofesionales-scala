package com.ucuenca.gestion.models.db

import scalikejdbc._

object EmpresaRepository {
  /**
   * Obtiene la lista de todas las empresas registradas (identificación y nombre),
   * uniendo empresa_perfil con usuario, ordenadas por nombre.
   */
  def listarEmpresas()(implicit session: DBSession = AutoSession): Seq[(String, String)] = {
    sql"""
      SELECT ep.identificacion, u.nombres_completos
      FROM empresa_perfil ep
      INNER JOIN usuario u ON ep.identificacion = u.identificacion
      ORDER BY u.nombres_completos
    """.map(rs => (rs.string("identificacion"), rs.string("nombres_completos")))
      .list.apply()
  }
}
