package com.ucuenca.gestion.utils

import scalikejdbc._

object DatabaseSeeder {
  /**
   * Si la tabla de usuarios está vacía o si se fuerza la limpieza,
   * inserta datos semilla de desarrollo para facilitar la prueba manual y automatizada.
   */
  def seedIfEmpty()(implicit session: DBSession = AutoSession): Unit = {
    // 0. Sembrar carreras
    val carreraCount = sql"SELECT count(*) FROM carrera".map(rs => rs.long(1)).single.apply().getOrElse(0L)
    if (carreraCount == 0) {
      println("Sembrando carreras universitarias...")
      sql"INSERT INTO carrera (nombre_carrera) VALUES ('Computación')".update.apply()
      sql"INSERT INTO carrera (nombre_carrera) VALUES ('Electricidad')".update.apply()
      sql"INSERT INTO carrera (nombre_carrera) VALUES ('Ingeniería Civil')".update.apply()
      sql"INSERT INTO carrera (nombre_carrera) VALUES ('Telecomunicaciones')".update.apply()
    }

    val count = sql"SELECT count(*) FROM usuario".map(rs => rs.long(1)).single.apply().getOrElse(0L)
    if (count == 0) {
      forceSeed()
    }
  }

  /**
   * Borra y siembra la base de datos de manera limpia.
   */
  def forceSeed()(implicit session: DBSession = AutoSession): Unit = {
    println("Limpiando base de datos para reinserción de datos semilla...")
    // Eliminar datos en orden de dependencia
    sql"DELETE FROM postulacion_bolsa".update.apply()
    sql"DELETE FROM validacion_carta_compromiso".update.apply()
    sql"DELETE FROM actividad_cronograma".update.apply()
    sql"DELETE FROM expediente_formulario1".update.apply()
    sql"DELETE FROM formulario2_evaluacion".update.apply()
    sql"DELETE FROM formulario3_informe".update.apply()
    sql"DELETE FROM auditoria_cierre".update.apply()
    sql"DELETE FROM practica_registro".update.apply()
    sql"DELETE FROM solicitud_empresa_propia".update.apply()
    sql"DELETE FROM oferta_convocatoria".update.apply()
    sql"DELETE FROM tutor_empresarial_perfil".update.apply()
    sql"DELETE FROM solicitud_convenio".update.apply()
    sql"DELETE FROM estudiante_perfil".update.apply()
    sql"DELETE FROM empresa_perfil".update.apply()
    sql"DELETE FROM usuario_sistema".update.apply()
    sql"DELETE FROM usuario".update.apply()
    sql"DELETE FROM archivo_pdf".update.apply()

    println("Insertando datos semilla de desarrollo completos...")

    // --- PDF Archivos semilla ---
    val pdfMallaId = sql"""
      INSERT INTO archivo_pdf (tipo_archivo, nombre_original, ruta_segura_servidor)
      VALUES ('T9_MALLA_ACADEMICA'::tipo_archivo_pdf, 'record_academico.pdf', 'docs/archivos_pdf/formulario_1.pdf')
    """.updateAndReturnGeneratedKey.apply().toInt

    val pdfOferta1Id = sql"""
      INSERT INTO archivo_pdf (tipo_archivo, nombre_original, ruta_segura_servidor)
      VALUES ('T7_PLANTILLA_FORMATO_OFERTA'::tipo_archivo_pdf, 'formato_oferta_fullstack.pdf', 'docs/archivos_pdf/formato_de_oferta.pdf')
    """.updateAndReturnGeneratedKey.apply().toInt

    val pdfOferta2Id = sql"""
      INSERT INTO archivo_pdf (tipo_archivo, nombre_original, ruta_segura_servidor)
      VALUES ('T7_PLANTILLA_FORMATO_OFERTA'::tipo_archivo_pdf, 'formato_oferta_datascientist.pdf', 'docs/archivos_pdf/formato_de_oferta.pdf')
    """.updateAndReturnGeneratedKey.apply().toInt

    // 1. Administrador Activo (admin / admin123)
    sql"""
      INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta)
      VALUES ('0101010101', 'Admin Principal', 'admin@ucuenca.edu.ec', 'ADMIN'::rol_usuario, 'ACTIVA'::estado_cuenta)
    """.update.apply()
    sql"""
      INSERT INTO usuario_sistema (username, password_hash, identificacion_usuario_ref)
      VALUES ('admin', ${PasswordHasher.hash("admin123")}, '0101010101')
    """.update.apply()

    // 2. Estudiante Activo (estudiante / estudiante123)
    sql"""
      INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta)
      VALUES ('0202020202', 'Juan Perez', 'juan.perez@ucuenca.edu.ec', 'ESTUDIANTE'::rol_usuario, 'ACTIVA'::estado_cuenta)
    """.update.apply()
    sql"""
      INSERT INTO usuario_sistema (username, password_hash, identificacion_usuario_ref)
      VALUES ('estudiante', ${PasswordHasher.hash("estudiante123")}, '0202020202')
    """.update.apply()
    sql"""
      INSERT INTO estudiante_perfil (identificacion, ciclo_actual, id_carrera_ref, estado_matricula, estado_estudiante_practica, malla_academica_pdf)
      VALUES ('0202020202', 7, 1, 'REGULAR'::estado_matricula, 'SIN_PRACTICA'::estado_estudiante_practica, ${pdfMallaId})
    """.update.apply()

    // 3. Estudiante Suspendido (suspendido / suspendido123)
    sql"""
      INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta)
      VALUES ('0303030303', 'Maria Lopez (Suspendida)', 'maria.lopez@ucuenca.edu.ec', 'ESTUDIANTE'::rol_usuario, 'SUSPENDIDA'::estado_cuenta)
    """.update.apply()
    sql"""
      INSERT INTO usuario_sistema (username, password_hash, identificacion_usuario_ref)
      VALUES ('suspendido', ${PasswordHasher.hash("suspendido123")}, '0303030303')
    """.update.apply()
    sql"""
      INSERT INTO estudiante_perfil (identificacion, ciclo_actual, id_carrera_ref, estado_matricula, estado_estudiante_practica, malla_academica_pdf)
      VALUES ('0303030303', 8, 1, 'REGULAR'::estado_matricula, 'SIN_PRACTICA'::estado_estudiante_practica, ${pdfMallaId})
    """.update.apply()

    // 4. Coordinador Activo (coordinador / coord123)
    sql"""
      INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta)
      VALUES ('0404040404', 'Dr. Carlos Mendoza', 'carlos.mendoza@ucuenca.edu.ec', 'COORDINADOR'::rol_usuario, 'ACTIVA'::estado_cuenta)
    """.update.apply()
    sql"""
      INSERT INTO usuario_sistema (username, password_hash, identificacion_usuario_ref)
      VALUES ('coordinador', ${PasswordHasher.hash("coord123")}, '0404040404')
    """.update.apply()

    // 5. Empresa Activa (empresa / empresa123)
    sql"""
      INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta)
      VALUES ('0505050505001', 'Empresa Tech S.A.', 'contacto@tech.com', 'EMPRESA'::rol_usuario, 'ACTIVA'::estado_cuenta)
    """.update.apply()
    sql"""
      INSERT INTO usuario_sistema (username, password_hash, identificacion_usuario_ref)
      VALUES ('empresa', ${PasswordHasher.hash("empresa123")}, '0505050505001')
    """.update.apply()
    sql"""
      INSERT INTO empresa_perfil (identificacion, direccion_matriz, mision, vision, estado_convenio)
      VALUES ('0505050505001', 'Av. 12 de Abril, Cuenca', 'Innovación tecnológica.', 'Ser líderes globales.', 'FORMALIZADO'::estado_convenio)
    """.update.apply()

    // 6. Secretaria (secretaria / secre123)
    sql"""
      INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta)
      VALUES ('0606060606', 'Lic. Diana Velez', 'diana.velez@ucuenca.edu.ec', 'SECRETARIA'::rol_usuario, 'ACTIVA'::estado_cuenta)
    """.update.apply()
    sql"""
      INSERT INTO usuario_sistema (username, password_hash, identificacion_usuario_ref)
      VALUES ('secretaria', ${PasswordHasher.hash("secre123")}, '0606060606')
    """.update.apply()

    // 7. Tutor Académico (tutoracad / tutor123)
    sql"""
      INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta)
      VALUES ('0707070707', 'Ing. Esteban Jara', 'esteban.jara@ucuenca.edu.ec', 'TUTOR_ACADEMICO'::rol_usuario, 'ACTIVA'::estado_cuenta)
    """.update.apply()
    sql"""
      INSERT INTO usuario_sistema (username, password_hash, identificacion_usuario_ref)
      VALUES ('tutoracad', ${PasswordHasher.hash("tutor123")}, '0707070707')
    """.update.apply()

    // 8. Tutor Empresarial (tutoremp / tutor123)
    sql"""
      INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta)
      VALUES ('0808080808', 'Ing. Roberto Andrade', 'roberto.andrade@tech.com', 'TUTOR_EMPRESARIAL'::rol_usuario, 'ACTIVA'::estado_cuenta)
    """.update.apply()
    sql"""
      INSERT INTO usuario_sistema (username, password_hash, identificacion_usuario_ref)
      VALUES ('tutoremp', ${PasswordHasher.hash("tutor123")}, '0808080808')
    """.update.apply()
    sql"""
      INSERT INTO tutor_empresarial_perfil (identificacion, empresa_id_ref, telefono_contacto)
      VALUES ('0808080808', '0505050505001', '0999999999')
    """.update.apply()

    // --- Ofertas semilla ---
    // Oferta 1: PENDIENTE (Para que la revise el coordinador)
    sql"""
      INSERT INTO oferta_convocatoria (ruc_empresa_ref, titulo_oferta, vacantes_solicitadas, duracion_horas, descripcion_general, requisitos_obligatorios, actividades_especificas, plantilla_oferta_pdf, estado_oferta)
      VALUES ('0505050505001', 'Desarrollador Full Stack Scala', 3, 120, 'Desarrollo de microservicios con Scala y Play Framework.', 'Scala, Git, bases de datos SQL.', 'Programación de APIs backend, testing unitario.', ${pdfOferta1Id}, 'PENDIENTE'::estado_oferta)
    """.update.apply()

    // Oferta 2: APROBADA (Para que el estudiante la vea en BuscarVacantes)
    val oferta2Id = sql"""
      INSERT INTO oferta_convocatoria (ruc_empresa_ref, titulo_oferta, vacantes_solicitadas, duracion_horas, descripcion_general, requisitos_obligatorios, actividades_especificas, plantilla_oferta_pdf, estado_oferta, fecha_publicacion)
      VALUES ('0505050505001', 'Practicante Data Scientist', 2, 160, 'Modelamiento predictivo y flujos de análisis de datos.', 'Python, pandas, SQL, nociones de ML.', 'Construcción de tableros, limpieza de sets de datos.', ${pdfOferta2Id}, 'APROBADA'::estado_oferta, CURRENT_DATE)
    """.updateAndReturnGeneratedKey.apply().toInt

    // Postulación semilla de Juan Perez a la oferta aprobada
    sql"""
      INSERT INTO postulacion_bolsa (ci_estudiante_ref, id_oferta_ref, estado_postulacion)
      VALUES ('0202020202', ${oferta2Id}, 'PENDIENTE'::estado_postulacion)
    """.update.apply()

    println("Base de datos sembrada con éxito.")
  }
}
