package com.ucuenca.gestion.controllers

import javafx.fxml.FXML
import javafx.scene.layout.StackPane
import javafx.event.ActionEvent
import com.ucuenca.gestion.views._
import com.ucuenca.gestion.utils.NavigationManager

class TutorEmpresarialMainController {

  @FXML
  var panelCentralDisp: StackPane = _

  @FXML
  def handleMisAlumnos(event: ActionEvent): Unit = {
    TutorEmpMisAlumnosView.renderInto(panelCentralDisp)
  }

  @FXML
  def handlePlanificarActividades(event: ActionEvent): Unit = {
    TutorEmpPlanificarActividadesView.renderInto(panelCentralDisp)
  }

  @FXML
  def handleFirmarFormulario1(event: ActionEvent): Unit = {
    TutorEmpFirmarFormulario1View.renderInto(panelCentralDisp)
  }

  @FXML
  def handleFormulario2(event: ActionEvent): Unit = {
    TutorEmpFormulario2View.renderInto(panelCentralDisp)
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
