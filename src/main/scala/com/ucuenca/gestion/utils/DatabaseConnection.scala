package com.ucuenca.gestion.utils

import scalikejdbc._
import java.util.Properties

object DatabaseConnection {
  private var initialized = false

  def initialize(): Unit = synchronized {
    if (!initialized) {
      val properties = new Properties()
      val stream = getClass.getResourceAsStream("/database.properties")
      if (stream != null) {
        try {
          properties.load(stream)
        } finally {
          stream.close()
        }
        val driver = properties.getProperty("db.default.driver")
        val url = properties.getProperty("db.default.url")
        val user = properties.getProperty("db.default.user")
        val password = properties.getProperty("db.default.password")

        Class.forName(driver)
        ConnectionPool.singleton(url, user, password)

        DatabaseSeeder.seedIfEmpty()
        initialized = true
      } else {
        throw new RuntimeException("No se pudo encontrar database.properties en el classpath")
      }
    }
  }
}
