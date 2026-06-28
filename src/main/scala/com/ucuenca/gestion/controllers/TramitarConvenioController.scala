package com.ucuenca.gestion.controllers

import javafx.fxml.FXML
import javafx.scene.control._
import javafx.event.ActionEvent
import javafx.stage.FileChooser

class TramitarConvenioController {

  @FXML var txtRazonSocial: TextField = _
  @FXML var txtRucEmpresa: TextField = _
  @FXML var txtRepresentanteLegal: TextField = _
  @FXML var txtDireccionMatriz: TextField = _
  @FXML var txtMisionEmpresa: TextArea = _
  @FXML var txtVisionEmpresa: TextArea = _
  @FXML var btnDescargarPlantillaConvenio: Button = _
  @FXML var btnSubirConvenioFirmado: Button = _
  @FXML var lblEstadoArchivoConvenio: Label = _
  @FXML var txtJustificacionRechazoConvenio: TextArea = _
  @FXML var lblEstado: Label = _

  private var convenioFile: java.io.File = _

  @FXML
  def initialize(): Unit = {
    // Inicialización del controlador
  }

  /**
   * Descarga el modelo de convenio PDF a la ruta local elegida por la empresa.
   */
  @FXML
  def handleDescargarPlantillaConvenio(event: ActionEvent): Unit = {
    try {
      val srcFile = new java.io.File("docs/archivos_pdf/solicitud_de_convenio.pdf")
      if (!srcFile.exists()) {
        showError("Error: No se pudo encontrar el modelo base en 'docs/archivos_pdf/solicitud_de_convenio.pdf'.")
        return
      }

      val fileChooser = new FileChooser()
      fileChooser.setTitle("Guardar Modelo de Convenio")
      fileChooser.setInitialFileName("solicitud_de_convenio.pdf")
      fileChooser.getExtensionFilters.add(new FileChooser.ExtensionFilter("Archivos PDF (*.pdf)", "*.pdf"))

      val dest = fileChooser.showSaveDialog(btnDescargarPlantillaConvenio.getScene.getWindow)
      if (dest != null) {
        java.nio.file.Files.copy(
          srcFile.toPath,
          dest.toPath,
          java.nio.file.StandardCopyOption.REPLACE_EXISTING
        )
        showSuccess(s"Modelo guardado exitosamente en: ${dest.getName}")
      }
    } catch {
      case scala.util.control.NonFatal(e) =>
        showError(s"Error al descargar el modelo: ${e.getMessage}")
    }
  }

  /**
   * Abre selector de archivos para subir el convenio firmado en formato PDF.
   */
  @FXML
  def handleSubirConvenioFirmado(event: ActionEvent): Unit = {
    val fileChooser = new FileChooser()
    fileChooser.setTitle("Seleccionar Convenio Firmado (PDF)")
    fileChooser.getExtensionFilters.add(new FileChooser.ExtensionFilter("Archivos PDF (*.pdf)", "*.pdf"))
    val selected = fileChooser.showOpenDialog(btnSubirConvenioFirmado.getScene.getWindow)
    if (selected != null) {
      convenioFile = selected
      lblEstadoArchivoConvenio.setText(selected.getName)
      showSuccess(s"Archivo cargado: ${selected.getName}")
    }
  }

  /**
   * Acción para enviar el convenio (lógica no implementada en este bloque).
   */
  @FXML
  def handleEnviarSolicitudConvenio(event: ActionEvent): Unit = {
    showError("La lógica de persistencia de Convenios Marcos se implementará en su micro-rebanada correspondiente.")
  }

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
