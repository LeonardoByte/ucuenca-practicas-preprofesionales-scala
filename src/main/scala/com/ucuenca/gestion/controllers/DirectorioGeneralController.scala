package com.ucuenca.gestion.controllers

import javafx.fxml.FXML
import javafx.scene.control._
import javafx.scene.layout.VBox
import javafx.event.ActionEvent
import javafx.beans.property.SimpleStringProperty
import com.ucuenca.gestion.models.entities.Usuario
import com.ucuenca.gestion.models.enums.{RolUsuario, EstadoCuenta}
import com.ucuenca.gestion.models.logic.{DirectorioLogic, DirectorioFailure}
import scala.util.control.NonFatal

class DirectorioGeneralController {

  // --- Filtros ---
  @FXML var txtBuscar: TextField = _
  @FXML var cmbRol: ComboBox[String] = _
  @FXML var cmbEstado: ComboBox[String] = _

  // --- Tabla ---
  @FXML var tblDirectorioGeneral: TableView[Usuario] = _
  @FXML var colNombres: TableColumn[Usuario, String] = _
  @FXML var colIdentificacion: TableColumn[Usuario, String] = _
  @FXML var colRol: TableColumn[Usuario, String] = _
  @FXML var colCorreo: TableColumn[Usuario, String] = _
  @FXML var colEstado: TableColumn[Usuario, String] = _

  // --- Notificación ---
  @FXML var lblEstado: Label = _

  // --- Botones Principales ---
  @FXML var btnModificar: Button = _
  @FXML var btnCambiarEstado: Button = _

  // --- Formulario de Edición ---
  @FXML var paneEdicion: VBox = _
  @FXML var txtEditNombres: TextField = _
  @FXML var txtEditCorreo: TextField = _

  /**
   * Método de inicialización automática de JavaFX.
   */
  @FXML
  def initialize(): Unit = {
    // 1. Configurar que el panel de edición colapse al ocultarse
    paneEdicion.managedProperty().bind(paneEdicion.visibleProperty())

    // 2. Configurar fábricas de valores para las columnas de la tabla
    colNombres.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.nombresCompletos))
    colIdentificacion.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.identificacion))
    colRol.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.rol.toString))
    colCorreo.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.correoElectronico))
    colEstado.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.estadoCuenta.toString))

    // 3. Poblar dropdowns de filtros
    cmbRol.getItems.add("TODOS")
    RolUsuario.values().foreach(r => cmbRol.getItems.add(r.toString))
    cmbRol.setValue("TODOS")

    cmbEstado.getItems.add("TODOS")
    EstadoCuenta.values().foreach(e => cmbEstado.getItems.add(e.toString))
    cmbEstado.setValue("TODOS")

    // 4. Cargar la nómina de usuarios inicial
    ejecutarBusqueda()
  }

  /**
   * Ejecuta la consulta de búsqueda parametrizada y refresca la grilla.
   */
  private def ejecutarBusqueda(): Unit = {
    val texto = txtBuscar.getText
    val rolStr = cmbRol.getValue
    val estadoStr = cmbEstado.getValue

    val rol = if (rolStr == null || rolStr == "TODOS") null else RolUsuario.valueOf(rolStr)
    val estado = if (estadoStr == null || estadoStr == "TODOS") null else EstadoCuenta.valueOf(estadoStr)

    DirectorioLogic.buscarUsuarios(texto, rol, estado) match {
      case Right(usuarios) =>
        tblDirectorioGeneral.getItems.clear()
        tblDirectorioGeneral.getItems.addAll(usuarios: _*)
      case Left(DirectorioFailure.ErrorPersistencia(msg)) =>
        showError(s"Error técnico de persistencia: $msg")
      case _ =>
    }
  }

  /**
   * Manejador genérico para eventos en los campos de filtro.
   */
  @FXML
  def handleFiltroCambio(event: javafx.event.Event): Unit = {
    ejecutarBusqueda()
  }

  /**
   * Despliega la sección de modificación inferior con los datos del usuario seleccionado.
   */
  @FXML
  def handleModificar(event: ActionEvent): Unit = {
    val seleccionado = tblDirectorioGeneral.getSelectionModel.getSelectedItem
    if (seleccionado == null) {
      showError("Por favor, seleccione un usuario de la tabla.")
      return
    }
    lblEstado.setVisible(false)

    txtEditNombres.setText(seleccionado.nombresCompletos)
    txtEditCorreo.setText(seleccionado.correoElectronico)
    paneEdicion.setVisible(true)
  }

  /**
   * Cancela la modificación en curso y oculta el panel de edición.
   */
  @FXML
  def handleCancelar(event: ActionEvent): Unit = {
    paneEdicion.setVisible(false)
    txtEditNombres.clear()
    txtEditCorreo.clear()
  }

  /**
   * Guarda los cambios de nombres y correo del usuario seleccionado.
   */
  @FXML
  def handleGuardar(event: ActionEvent): Unit = {
    val seleccionado = tblDirectorioGeneral.getSelectionModel.getSelectedItem
    if (seleccionado == null) return

    val nombres = txtEditNombres.getText
    val correo = txtEditCorreo.getText

    DirectorioLogic.actualizarContacto(seleccionado.identificacion, nombres, correo) match {
      case Right(_) =>
        showSuccess(s"¡Usuario '${seleccionado.identificacion}' actualizado correctamente!")
        paneEdicion.setVisible(false)
        ejecutarBusqueda()
      case Left(DirectorioFailure.Validacion(msg)) =>
        showError(msg)
      case Left(DirectorioFailure.ErrorPersistencia(msg)) =>
        showError(s"Error de base de datos: $msg")
    }
  }

  /**
   * Cambia lógicamente el estado de la cuenta (ACTIVA / SUSPENDIDA) del usuario seleccionado.
   * No ejecuta borrado físico.
   */
  @FXML
  def handleCambiarEstado(event: ActionEvent): Unit = {
    val seleccionado = tblDirectorioGeneral.getSelectionModel.getSelectedItem
    if (seleccionado == null) {
      showError("Por favor, seleccione un usuario de la tabla.")
      return
    }

    val nuevoEstado = if (seleccionado.estadoCuenta == EstadoCuenta.ACTIVA) {
      EstadoCuenta.SUSPENDIDA
    } else {
      EstadoCuenta.ACTIVA
    }

    DirectorioLogic.cambiarEstado(seleccionado.identificacion, nuevoEstado) match {
      case Right(estado) =>
        showSuccess(s"Estado de cuenta para '${seleccionado.correoElectronico}' cambiado a $estado.")
        ejecutarBusqueda()
      case Left(DirectorioFailure.Validacion(msg)) =>
        showError(msg)
      case Left(DirectorioFailure.ErrorPersistencia(msg)) =>
        showError(s"Error de base de datos: $msg")
    }
  }

  // --- Auxiliares visuales ---

  private def showSuccess(msg: String): Unit = {
    lblEstado.setText(msg)
    lblEstado.setStyle("-fx-text-fill: #059669; -fx-font-weight: bold;")
    lblEstado.setVisible(true)
  }

  private def showError(msg: String): Unit = {
    lblEstado.setText(msg)
    lblEstado.setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;")
    lblEstado.setVisible(true)
  }
}
