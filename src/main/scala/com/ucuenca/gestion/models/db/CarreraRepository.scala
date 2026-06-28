package com.ucuenca.gestion.models.db

import scalikejdbc._

object CarreraRepository {
  /**
   * Obtiene la lista de todas las carreras universitarias registradas,
   * ordenadas alfabéticamente por su nombre.
   */
  def listarTodas()(implicit session: DBSession = AutoSession): Seq[(Int, String)] = {
    sql"SELECT id_carrera, nombre_carrera FROM carrera ORDER BY nombre_carrera"
      .map(rs => (rs.int("id_carrera"), rs.string("nombre_carrera")))
      .list.apply()
  }
}
