package com.ucuenca.gestion.controllers

import javafx.fxml.FXML
import javafx.scene.layout.StackPane
import javafx.event.ActionEvent
import com.ucuenca.gestion.views._
import com.ucuenca.gestion.utils.NavigationManager

class CoordinadorMainController {

  @FXML
  var panelCentralDisp: StackPane = _

  @FXML
  def handleMetricasGenerales(event: ActionEvent): Unit = {
    CoordinadorMetricasView.renderInto(panelCentralDisp)
  }

  @FXML
  def handleRevisionOfertas(event: ActionEvent): Unit = {
    CoordinadorRevisionOfertasView.renderInto(panelCentralDisp)
  }

  @FXML
  def handleValidarAlumnos(event: ActionEvent): Unit = {
    CoordinadorValidarAlumnosView.renderInto(panelCentralDisp)
  }

  @FXML
  def handleRevisionOficios(event: ActionEvent): Unit = {
    CoordinadorRevisionOficiosView.renderInto(panelCentralDisp)
  }

  @FXML
  def handleAsignarDocentes(event: ActionEvent): Unit = {
    CoordinadorAsignarDocentesView.renderInto(panelCentralDisp)
  }

  @FXML
  def handleAuditoriaExpedientes(event: ActionEvent): Unit = {
    CoordinadorAuditoriaExpedientesView.renderInto(panelCentralDisp)
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
