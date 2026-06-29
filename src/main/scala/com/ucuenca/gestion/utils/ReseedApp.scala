package com.ucuenca.gestion.utils

import scalikejdbc.DB

object ReseedApp extends App {
  println("Inicializando conexión a base de datos...")
  DatabaseConnection.initialize()
  
  DB.localTx { implicit session =>
    DatabaseSeeder.forceSeed()
  }
  
  println("¡Proceso de resembrado completado con éxito!")
}
