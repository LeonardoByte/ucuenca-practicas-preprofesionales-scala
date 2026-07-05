package com.ucuenca.gestion

import javafx.application.Application
import javafx.stage.Stage

/**
 * Clase principal de JavaFX para la interfaz gráfica.
 */
class MainApp extends Application {
  override def start(primaryStage: Stage): Unit = {
    com.ucuenca.gestion.utils.NavigationManager.setStage(primaryStage)
    com.ucuenca.gestion.utils.NavigationManager.showLogin()
  }
}

/**
 * Objeto Main ejecutable para iniciar la aplicación sin conflictos del módulo JavaFX.
 */
object Main {
  def main(args: Array[String]): Unit = {
    println("Iniciando aplicación GestiónPracticas...")
    try {
      com.ucuenca.gestion.utils.DatabaseConnection.initialize()
      println("Conexión a la base de datos establecida exitosamente.")
    } catch {
      case e: Throwable =>
        println(s"FATAL: No se pudo establecer la conexión a la base de datos: ${e.getMessage}")
        e.printStackTrace()
    }
    Application.launch(classOf[MainApp], args: _*)
  }
}
