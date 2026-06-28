package com.ucuenca.gestion.utils

import scalikejdbc._

object DatabaseSeeder {
  /**
   * Si la tabla de usuarios está vacía, inserta datos semilla de desarrollo
   * para facilitar la prueba manual y automatizada.
   */
  def seedIfEmpty()(implicit session: DBSession = AutoSession): Unit = {
    val count = sql"SELECT count(*) FROM usuario".map(rs => rs.long(1)).single.apply().getOrElse(0L)
    if (count == 0) {
      println("Base de datos vacía. Insertando datos semilla de desarrollo...")

      // 1. Administrador Activo
      sql"""
        INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta)
        VALUES ('0101010101', 'Admin Principal', 'admin@ucuenca.edu.ec', 'ADMIN'::rol_usuario, 'ACTIVA'::estado_cuenta)
      """.update.apply()
      
      sql"""
        INSERT INTO usuario_sistema (username, password_hash, identificacion_usuario_ref)
        VALUES ('admin', ${PasswordHasher.hash("admin123")}, '0101010101')
      """.update.apply()

      // 2. Estudiante Activo
      sql"""
        INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta)
        VALUES ('0202020202', 'Juan Perez', 'juan.perez@ucuenca.edu.ec', 'ESTUDIANTE'::rol_usuario, 'ACTIVA'::estado_cuenta)
      """.update.apply()
      
      sql"""
        INSERT INTO usuario_sistema (username, password_hash, identificacion_usuario_ref)
        VALUES ('estudiante', ${PasswordHasher.hash("estudiante123")}, '0202020202')
      """.update.apply()

      // 3. Estudiante Suspendido
      sql"""
        INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta)
        VALUES ('0303030303', 'Maria Lopez (Suspendida)', 'maria.lopez@ucuenca.edu.ec', 'ESTUDIANTE'::rol_usuario, 'SUSPENDIDA'::estado_cuenta)
      """.update.apply()
      
      sql"""
        INSERT INTO usuario_sistema (username, password_hash, identificacion_usuario_ref)
        VALUES ('suspendido', ${PasswordHasher.hash("suspendido123")}, '0303030303')
      """.update.apply()

      // 4. Coordinador Activo
      sql"""
        INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta)
        VALUES ('0404040404', 'Dr. Carlos Mendoza', 'carlos.mendoza@ucuenca.edu.ec', 'COORDINADOR'::rol_usuario, 'ACTIVA'::estado_cuenta)
      """.update.apply()
      
      sql"""
        INSERT INTO usuario_sistema (username, password_hash, identificacion_usuario_ref)
        VALUES ('coordinador', ${PasswordHasher.hash("coord123")}, '0404040404')
      """.update.apply()

      // 5. Empresa Activa
      sql"""
        INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta)
        VALUES ('0505050505001', 'Empresa Tech S.A.', 'contacto@tech.com', 'EMPRESA'::rol_usuario, 'ACTIVA'::estado_cuenta)
      """.update.apply()
      
      sql"""
        INSERT INTO usuario_sistema (username, password_hash, identificacion_usuario_ref)
        VALUES ('empresa', ${PasswordHasher.hash("empresa123")}, '0505050505001')
      """.update.apply()

      println("Datos semilla de desarrollo insertados correctamente.")
    }
  }
}
