package com.ucuenca.gestion.controllers

import javafx.fxml.FXML
import javafx.scene.layout.StackPane
import javafx.event.ActionEvent
import com.ucuenca.gestion.views._
import com.ucuenca.gestion.utils.NavigationManager

class EstudianteMainController {

  @FXML
  var panelCentralDisp: StackPane = _

  /**
   * Carga la sub-pantalla "Mi Perfil" del estudiante en el panel central.
   */
  @FXML
  def handleMiPerfil(event: ActionEvent): Unit = {
    EstudiantePerfilView.renderInto(panelCentralDisp)
  }

  /**
   * Cierra la sesión activa y retorna a la vista externa de Login.
   */
  @FXML
  def handleCerrarSesion(event: ActionEvent): Unit = {
    NavigationManager.showLogin()
  }

  /**
   * Termina y cierra completamente la aplicación JavaFX.
   */
  @FXML
  def handleSalirSistema(event: ActionEvent): Unit = {
    javafx.application.Platform.exit()
  }
}
