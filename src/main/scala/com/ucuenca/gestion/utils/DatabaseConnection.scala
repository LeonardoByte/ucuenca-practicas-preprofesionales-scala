package com.ucuenca.gestion.utils

import scalikejdbc._
import java.sql.DriverManager
import java.util.Properties

/**
 * Punto único de arranque de la capa de datos: crea la base de datos si falta,
 * inicializa el pool principal de ScalikeJDBC, construye el esquema desde
 * sql/database_setup.sql si falta, y siembra datos de desarrollo si el
 * esquema está vacío. Usado tanto por Main (antes de Application.launch())
 * como por los specs de ScalaTest (en beforeAll).
 */
object DatabaseConnection {
  private var initialized = false

  def initialize(): Unit = synchronized {
    if (initialized) return

    val properties = new Properties()
    val stream = getClass.getResourceAsStream("/database.properties")
    if (stream == null) {
      throw new RuntimeException("No se pudo encontrar database.properties en el classpath")
    }
    try {
      properties.load(stream)
    } finally {
      stream.close()
    }

    val driver = properties.getProperty("db.default.driver")
    val targetUrl = properties.getProperty("db.default.url")
    val user = properties.getProperty("db.default.user")
    val password = properties.getProperty("db.default.password")

    Class.forName(driver)

    val ultimaBarra = targetUrl.lastIndexOf('/')
    val dbName = targetUrl.substring(ultimaBarra + 1)
    val bootstrapUrl = targetUrl.substring(0, ultimaBarra + 1) + "postgres"

    // 2. Crear la base de datos 'gestion_practicas' si no existe todavía.
    ensureDatabaseExists(bootstrapUrl, user, password, dbName)

    // 3. Inicializar el pool principal apuntando a la base de datos destino.
    ConnectionPool.singleton(targetUrl, user, password)

    // 4. Si la tabla ancla 'usuario' no existe, construir todo el esquema desde el script DDL.
    if (!tablaExiste("usuario")) {
      ejecutarScriptEsquema()
    }

    // 6. Si el esquema está vacío, sembrar los datos de desarrollo.
    DatabaseSeeder.seedIfEmpty()

    initialized = true
  }

  /**
   * Verifica la existencia de la base de datos usando una conexión de arranque
   * contra 'postgres'. CREATE DATABASE no puede ejecutarse dentro de un bloque
   * transaccional, por lo que se emite con autoCommit = true.
   */
  private def ensureDatabaseExists(bootstrapUrl: String, user: String, password: String, dbName: String): Unit = {
    val conn = DriverManager.getConnection(bootstrapUrl, user, password)
    try {
      conn.setAutoCommit(true)

      val existeStmt = conn.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?")
      val existe = try {
        existeStmt.setString(1, dbName)
        val rs = existeStmt.executeQuery()
        try rs.next() finally rs.close()
      } finally existeStmt.close()

      if (!existe) {
        println(s"Base de datos '$dbName' no encontrada. Creándola...")
        val stmt = conn.createStatement()
        try stmt.executeUpdate(s"""CREATE DATABASE "$dbName"""")
        finally stmt.close()
      }
    } finally {
      conn.close()
    }
  }

  private def tablaExiste(nombreTabla: String): Boolean = {
    DB.readOnly { implicit session =>
      sql"""
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = ${nombreTabla}
      """.map(_ => true).single.apply().getOrElse(false)
    }
  }

  /**
   * Lee sql/database_setup.sql (ruta relativa al directorio de trabajo del
   * proyecto, tal como lo ejecutan sbt/el IDE) y aplica cada sentencia por
   * separado. Si una sentencia falla, se imprime la sentencia exacta y el
   * mensaje de PostgreSQL antes de relanzar la excepción: nunca se traga el
   * error en silencio, porque un fallo a mitad del DDL deja el esquema a medias
   * y las tablas siguientes fallarían igual por FKs faltantes.
   */
  private def ejecutarScriptEsquema(): Unit = {
    println("Esquema no encontrado. Construyendo estructura desde sql/database_setup.sql...")
    val archivo = new java.io.File("sql/database_setup.sql")
    if (!archivo.exists()) {
      throw new RuntimeException(s"No se encontró el script de esquema en ${archivo.getAbsolutePath}")
    }
    val script = new String(java.nio.file.Files.readAllBytes(archivo.toPath), "UTF-8")

    DB.autoCommit { implicit session =>
      dividirEnSentencias(script).foreach { sentencia =>
        try {
          SQL(sentencia).execute.apply()
        } catch {
          case e: Exception =>
            println("=" * 80)
            println("ERROR AL EJECUTAR SENTENCIA SQL DEL SCRIPT DE ESQUEMA:")
            println(sentencia)
            println(s"Mensaje de PostgreSQL: ${e.getMessage}")
            println("=" * 80)
            throw e
        }
      }
    }
    println("Estructura de base de datos creada exitosamente.")
  }

  /**
   * Limpia los comentarios de línea ('-- ...') y luego divide el script en
   * sentencias individuales por ';', ignorando los que quedan dentro de un
   * bloque delimitado por '$$' (cuerpo de función plpgsql), donde el punto y
   * coma es parte del cuerpo y no un terminador de sentencia.
   */
  private def dividirEnSentencias(script: String): List[String] = {
    val sinComentarios = script.linesIterator.map { linea =>
      val idx = linea.indexOf("--")
      if (idx >= 0) linea.substring(0, idx) else linea
    }.mkString("\n")

    val sentencias = scala.collection.mutable.ListBuffer[String]()
    val actual = new StringBuilder
    var dentroDeBloqueDolar = false
    var i = 0
    while (i < sinComentarios.length) {
      if (sinComentarios.startsWith("$$", i)) {
        dentroDeBloqueDolar = !dentroDeBloqueDolar
        actual.append("$$")
        i += 2
      } else if (sinComentarios.charAt(i) == ';' && !dentroDeBloqueDolar) {
        sentencias += actual.toString()
        actual.clear()
        i += 1
      } else {
        actual.append(sinComentarios.charAt(i))
        i += 1
      }
    }
    if (actual.toString().trim.nonEmpty) sentencias += actual.toString()
    sentencias.toList.map(_.trim).filter(_.nonEmpty)
  }
}
