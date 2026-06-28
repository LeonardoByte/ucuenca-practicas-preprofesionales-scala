package com.ucuenca.gestion.controllers

import javafx.fxml.FXML
import javafx.scene.layout.StackPane
import javafx.event.ActionEvent
import com.ucuenca.gestion.views._
import com.ucuenca.gestion.utils.NavigationManager

class EmpresaMainController {

  @FXML
  var panelCentralDisp: StackPane = _

  /**
   * Carga la sub-pantalla Cuadro de Mando de la empresa en el panel central.
   */
  @FXML
  def handleCuadroMando(event: ActionEvent): Unit = {
    EmpresaCuadroMandoView.renderInto(panelCentralDisp)
  }

  /**
   * Carga la sub-pantalla de creación de ofertas de practicantes en el panel central.
   */
  @FXML
  def handleCrearOferta(event: ActionEvent): Unit = {
    EmpresaCrearOfertaView.renderInto(panelCentralDisp)
  }

  /**
   * Carga la sub-pantalla del historial de ofertas publicadas en el panel central.
   */
  @FXML
  def handleHistorialOfertas(event: ActionEvent): Unit = {
    EmpresaHistorialOfertasView.renderInto(panelCentralDisp)
  }

  /**
   * Carga la sub-pantalla de gestión de candidatos postulados en el panel central.
   */
  @FXML
  def handleGestionCandidatos(event: ActionEvent): Unit = {
    EmpresaGestionCandidatosView.renderInto(panelCentralDisp)
  }

  /**
   * Carga la sub-pantalla para tramitar convenios legales en el panel central.
   */
  @FXML
  def handleTramitarConvenio(event: ActionEvent): Unit = {
    EmpresaTramitarConvenioView.renderInto(panelCentralDisp)
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
