package com.ucuenca.gestion

import javafx.application.Application
import javafx.stage.Stage
import javafx.scene.Scene
import javafx.scene.control.Label
import javafx.scene.layout.StackPane

/**
 * Clase principal de JavaFX para la interfaz gráfica.
 */
class MainApp extends Application {
  override def start(primaryStage: Stage): Unit = {
    primaryStage.setTitle("Gestión Prácticas - Prueba de Humo")

    // Componente visual básico para verificar el funcionamiento de la GUI
    val label = new Label("¡Conexión exitosa entre Scala 2.13.18, JavaFX 21 y JVM 25!")
    label.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1e3a8a;")

    val root = new StackPane(rootLabel(label))
    root.setStyle("-fx-background-color: #f0fdf4; -fx-padding: 30px;")

    val scene = new Scene(root, 600, 250)
    primaryStage.setScene(scene)
    primaryStage.show()
  }

  // Método auxiliar simple para demostrar uso de lógica interna
  private def rootLabel(lbl: Label): Label = {
    lbl
  }
}

/**
 * Objeto Main ejecutable para iniciar la aplicación sin conflictos del módulo JavaFX.
 */
object Main {
  def main(args: Array[String]): Unit = {
    println("Iniciando aplicación GestiónPracticas...")
    Application.launch(classOf[MainApp], args: _*)
  }
}
