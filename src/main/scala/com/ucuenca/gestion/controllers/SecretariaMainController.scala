package com.ucuenca.gestion.controllers

import javafx.fxml.FXML
import javafx.scene.layout.StackPane
import javafx.event.ActionEvent
import com.ucuenca.gestion.views._
import com.ucuenca.gestion.utils.NavigationManager

class SecretariaMainController {

  @FXML
  var panelCentralDisp: StackPane = _

  @FXML
  def handleResumenOperativo(event: ActionEvent): Unit = {
    SecretariaResumenView.renderInto(panelCentralDisp)
  }

  @FXML
  def handleValidarEntrega(event: ActionEvent): Unit = {
    SecretariaValidarEntregaView.renderInto(panelCentralDisp)
  }

  @FXML
  def handleSolicitudesConvenio(event: ActionEvent): Unit = {
    SecretariaSolicitudesConvenioView.renderInto(panelCentralDisp)
  }

  @FXML
  def handleCerrarSesion(event: ActionEvent): Unit = {
    NavigationManager.showLogin()
  }

  @FXML
  def handleSalirSistema(event: ActionEvent): Unit = {
    javafx.application.Platform.exit()
  }

  @FXML
  def handleAcercaPrograma(event: ActionEvent): Unit = {
    com.ucuenca.gestion.utils.AyudaHandler.mostrarAcercaDelPrograma()
  }

  @FXML
  def handleAcercaDesarrollador(event: ActionEvent): Unit = {
    com.ucuenca.gestion.utils.AyudaHandler.abrirPerfilDesarrollador()
  }

  @FXML
  def handleRepositorioGitHub(event: ActionEvent): Unit = {
    com.ucuenca.gestion.utils.AyudaHandler.abrirRepositorioGitHub()
  }
}
