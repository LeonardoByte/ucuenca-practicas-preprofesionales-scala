package com.ucuenca.gestion.utils

import javafx.scene.control.Alert
import javafx.scene.control.Alert.AlertType
import scala.util.control.NonFatal

object AyudaHandler {

  /**
   * Muestra un Alert nativo de JavaFX con información sobre el software.
   */
  def mostrarAcercaDelPrograma(): Unit = {
    val alert = new Alert(AlertType.INFORMATION)
    alert.setTitle("Acerca del programa")
    alert.setHeaderText("Sistema de Gestión de Prácticas Preprofesionales")
    alert.setContentText(
      "Propósito: Administración, validación y seguimiento del ciclo de prácticas preprofesionales universitarias.\n" +
      "Versión: 1.0.0-RC1\n" +
      "Licencia: MIT License\n" +
      "© 2026 Universidad de Cuenca. Todos los derechos reservados."
    )
    alert.showAndWait()
  }

  /**
   * Abre el perfil de GitHub del desarrollador en el navegador por defecto.
   */
  def abrirPerfilDesarrollador(): Unit = {
    abrirUrlEnNavegador("https://github.com/LeonardoByte")
  }

  /**
   * Abre la URL del repositorio remoto de GitHub en el navegador por defecto.
   */
  def abrirRepositorioGitHub(): Unit = {
    abrirUrlEnNavegador("https://github.com/LeonardoByte/ucuenca-practicas-preprofesionales-scala")
  }

  private def abrirUrlEnNavegador(url: String): Unit = {
    try {
      if (java.awt.Desktop.isDesktopSupported) {
        val desktop = java.awt.Desktop.getDesktop
        desktop.browse(new java.net.URI(url))
      } else {
        println(s"Desktop no soportado. No se puede abrir la URL: $url")
      }
    } catch {
      case NonFatal(e) =>
        println(s"Error al abrir la URL $url en el navegador: ${e.getMessage}")
    }
  }
}
