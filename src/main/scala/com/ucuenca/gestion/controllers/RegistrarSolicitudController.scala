package com.ucuenca.gestion.controllers

import javafx.fxml.FXML
import javafx.event.ActionEvent
import javafx.scene.control.{Button, Label, Spinner, SpinnerValueFactory, TextArea, TextField}
import javafx.stage.FileChooser
import scala.util.control.NonFatal
import com.ucuenca.gestion.models.dto.RegistrarSolicitudDTO
import com.ucuenca.gestion.models.logic.{RegistrarSolicitudLogic, SolicitudEmpresaPropiaFailure}
import com.ucuenca.gestion.utils.SessionManager

class RegistrarSolicitudController {

  // Company identification
  @FXML var txtNombreEmpresaPropia: TextField      = _
  @FXML var txtRucEmpresaPropia: TextField         = _
  @FXML var txtContactoEmpresaPropia: TextField    = _
  @FXML var spnHorasEmpresaPropia: Spinner[Integer] = _
  @FXML var txtDireccionMatrizPropia: TextField    = _
  // Institutional profile
  @FXML var txtMisionEmpresaPropia: TextArea       = _
  @FXML var txtVisionEmpresaPropia: TextArea       = _
  // External supervisor
  @FXML var txtCiSupervisorExterno: TextField      = _
  @FXML var txtNombresSupervisorExterno: TextField = _
  @FXML var txtEmailSupervisorExterno: TextField   = _
  @FXML var txtTelefonoSupervisorExterno: TextField = _
  // Office transcript
  @FXML var txtContenidoOficio: TextArea           = _
  // PDF upload controls
  @FXML var btnDescargarPlantillaOficio: Button    = _
  @FXML var btnSubirOficioLlenado: Button          = _
  @FXML var lblEstadoArchivoOficio: Label          = _
  @FXML var btnEnviarSolicitud: Button             = _
  @FXML var lblEstado: Label                       = _

  private var oficioFile: java.io.File = _

  @FXML
  def initialize(): Unit = {
    spnHorasEmpresaPropia.setValueFactory(
      new SpinnerValueFactory.IntegerSpinnerValueFactory(40, 400, 240, 10)
    )
  }

  @FXML
  def handleDescargarPlantillaOficio(event: ActionEvent): Unit = {
    try {
      val srcFile = new java.io.File("docs/archivos_pdf/solicitud_de_oficio.pdf")
      if (!srcFile.exists()) {
        showError("Error: No se encontró el archivo de plantilla en 'docs/archivos_pdf/solicitud_de_oficio.pdf'.")
        return
      }
      val fileChooser = new FileChooser()
      fileChooser.setTitle("Guardar Plantilla del Oficio de Solicitud")
      fileChooser.setInitialFileName("plantilla_oficio_solicitud.pdf")
      fileChooser.getExtensionFilters.add(new FileChooser.ExtensionFilter("Archivos PDF (*.pdf)", "*.pdf"))
      val dest = fileChooser.showSaveDialog(btnDescargarPlantillaOficio.getScene.getWindow)
      if (dest != null) {
        java.nio.file.Files.copy(
          srcFile.toPath,
          dest.toPath,
          java.nio.file.StandardCopyOption.REPLACE_EXISTING
        )
        showSuccess(s"Plantilla guardada exitosamente en: ${dest.getName}")
      }
    } catch {
      case NonFatal(e) =>
        showError(s"Error al descargar la plantilla: ${e.getMessage}")
    }
  }

  @FXML
  def handleSubirOficioLlenado(event: ActionEvent): Unit = {
    val fileChooser = new FileChooser()
    fileChooser.setTitle("Seleccionar Oficio Digital Finalizado (PDF)")
    fileChooser.getExtensionFilters.add(new FileChooser.ExtensionFilter("Archivos PDF (*.pdf)", "*.pdf"))
    val selected = fileChooser.showOpenDialog(btnSubirOficioLlenado.getScene.getWindow)
    if (selected != null) {
      oficioFile = selected
      lblEstadoArchivoOficio.setText(selected.getName)
      showSuccess(s"Archivo cargado: ${selected.getName}")
    }
  }

  @FXML
  def handleEnviarSolicitud(event: ActionEvent): Unit = {
    val activeUserOpt = SessionManager.getUsuario
    if (activeUserOpt.isEmpty) {
      showError("Error de sesión: no hay un estudiante autenticado en el sistema.")
      return
    }

    val ciEstudiante = activeUserOpt.get.identificacion

    if (oficioFile == null) {
      showError("Debe adjuntar el archivo PDF del oficio firmado antes de enviar.")
      return
    }

    val pdfPath = copyFileToUploads(
      oficioFile,
      s"oficio_propia_${ciEstudiante}_${System.currentTimeMillis()}.pdf"
    )

    val dto = RegistrarSolicitudDTO(
      ciEstudianteRef           = ciEstudiante,
      nombreEntidadExterna      = txtNombreEmpresaPropia.getText,
      rucEmpresaPropia          = txtRucEmpresaPropia.getText,
      contactoEmpresaPropia     = txtContactoEmpresaPropia.getText,
      horasEmpresaPropia        = Option(spnHorasEmpresaPropia.getValue).map(_.intValue()).getOrElse(0),
      direccionEmpresaPropia    = txtDireccionMatrizPropia.getText,
      misionEmpresaPropia       = txtMisionEmpresaPropia.getText,
      visionEmpresaPropia       = txtVisionEmpresaPropia.getText,
      contenidoOficioTranscrito = txtContenidoOficio.getText,
      ciSupervisorExterno       = txtCiSupervisorExterno.getText,
      nombresSupervisorExterno  = txtNombresSupervisorExterno.getText,
      emailSupervisorExterno    = txtEmailSupervisorExterno.getText,
      telefonoSupervisorExterno = txtTelefonoSupervisorExterno.getText,
      pdfNombreOriginal         = oficioFile.getName,
      pdfRutaSegura             = pdfPath
    )

    RegistrarSolicitudLogic.registrar(dto) match {
      case Right(solicitudId) =>
        showSuccess(s"¡Trámite enviado con éxito! Su solicitud #$solicitudId quedó registrada en estado PENDIENTE.")
        clearForm()
      case Left(SolicitudEmpresaPropiaFailure.Validacion(msg)) =>
        showError(msg)
      case Left(SolicitudEmpresaPropiaFailure.ErrorPersistencia(msg)) =>
        showError(s"Error técnico al registrar la solicitud: $msg")
    }
  }

  private def copyFileToUploads(source: java.io.File, destName: String): String = {
    val uploadsDir = new java.io.File("uploads")
    if (!uploadsDir.exists()) uploadsDir.mkdirs()
    val destFile = new java.io.File(uploadsDir, destName)
    java.nio.file.Files.copy(
      source.toPath,
      destFile.toPath,
      java.nio.file.StandardCopyOption.REPLACE_EXISTING
    )
    destFile.getAbsolutePath
  }

  private def clearForm(): Unit = {
    txtNombreEmpresaPropia.clear()
    txtRucEmpresaPropia.clear()
    txtContactoEmpresaPropia.clear()
    spnHorasEmpresaPropia.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(40, 400, 240, 10))
    txtDireccionMatrizPropia.clear()
    txtMisionEmpresaPropia.clear()
    txtVisionEmpresaPropia.clear()
    txtContenidoOficio.clear()
    txtCiSupervisorExterno.clear()
    txtNombresSupervisorExterno.clear()
    txtEmailSupervisorExterno.clear()
    txtTelefonoSupervisorExterno.clear()
    oficioFile = null
    lblEstadoArchivoOficio.setText("Ningún archivo seleccionado (.pdf)")
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
