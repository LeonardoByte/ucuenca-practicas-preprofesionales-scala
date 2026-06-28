package com.ucuenca.gestion.controllers

import javafx.fxml.FXML
import javafx.scene.layout.StackPane
import javafx.event.ActionEvent
import com.ucuenca.gestion.views._
import com.ucuenca.gestion.utils.NavigationManager

class TutorAcademicoMainController {

  @FXML
  var panelCentralDisp: StackPane = _

  @FXML
  def handleAlumnosTutorados(event: ActionEvent): Unit = {
    TutorAcadAlumnosView.renderInto(panelCentralDisp)
  }

  @FXML
  def handleValidarActividades(event: ActionEvent): Unit = {
    TutorAcadValidarActividadesView.renderInto(panelCentralDisp)
  }

  @FXML
  def handleFirmarFormulario1(event: ActionEvent): Unit = {
    TutorAcadFirmarFormulario1View.renderInto(panelCentralDisp)
  }

  @FXML
  def handleRevisarFormulario2(event: ActionEvent): Unit = {
    TutorAcadRevisarFormulario2View.renderInto(panelCentralDisp)
  }

  @FXML
  def handleEmitirFormulario3(event: ActionEvent): Unit = {
    TutorAcadEmitirFormulario3View.renderInto(panelCentralDisp)
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
