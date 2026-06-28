package com.ucuenca.gestion.controllers

import javafx.fxml.FXML
import javafx.scene.layout.StackPane
import javafx.event.ActionEvent
import com.ucuenca.gestion.views._
import com.ucuenca.gestion.utils.NavigationManager

class AdministradorMainController {

  @FXML
  var panelCentralDisp: StackPane = _

  /**
   * Carga la sub-pantalla de Estado del Sistema en el panel central.
   */
  @FXML
  def handleEstadoSistema(event: ActionEvent): Unit = {
    AdminEstadoSistemaView.renderInto(panelCentralDisp)
  }

  /**
   * Carga la sub-pantalla de Registro de Cuentas en el panel central.
   */
  @FXML
  def handleRegistrarUsuario(event: ActionEvent): Unit = {
    AdminRegistrarUsuarioView.renderInto(panelCentralDisp)
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
