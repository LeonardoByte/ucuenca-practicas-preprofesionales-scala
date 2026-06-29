package com.ucuenca.gestion.controllers

import javafx.fxml.FXML
import javafx.scene.control._
import javafx.event.ActionEvent
import javafx.stage.FileChooser
import scala.util.control.NonFatal
import com.ucuenca.gestion.models.logic.{ConvenioLogic, ConvenioFailure}
import com.ucuenca.gestion.models.enums.EstadoConvenio
import com.ucuenca.gestion.utils.SessionManager

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
  @FXML var btnEnviarSolicitudConvenio: Button = _

  private var convenioFile: java.io.File = _

  @FXML
  def initialize(): Unit = {
    if (btnEnviarSolicitudConvenio != null) {
      btnEnviarSolicitudConvenio.setDisable(false)
    }
    SessionManager.getUsuario match {
      case Some(usuario) if usuario.rol == com.ucuenca.gestion.models.enums.RolUsuario.EMPRESA =>
        txtRucEmpresa.setText(usuario.identificacion)
        txtRucEmpresa.setDisable(true)
        cargarEstadoConvenio(usuario.identificacion)
      case _ =>
        // Para tests o perfiles administrativos que dejen libre el RUC
    }
  }

  private def mostrarAlerta(titulo: String, cabecera: String, mensaje: String, tipo: Alert.AlertType): Unit = {
    val alert = new Alert(tipo)
    alert.setTitle(titulo)
    alert.setHeaderText(cabecera)
    alert.setContentText(mensaje)
    alert.showAndWait()
  }

  private def cargarEstadoConvenio(ruc: String): Unit = {
    ConvenioLogic.obtenerEstadoConvenioEmpresa(ruc) match {
      case Right(Some(sol)) =>
        sol.estadoConvenio match {
          case EstadoConvenio.RECHAZADO =>
            txtJustificacionRechazoConvenio.setText(
              s"Rechazo anterior (${sol.fechaPresentacion}):\n${sol.causasRechazoSecretaria.getOrElse("Sin causa especificada.")}"
            )
            showError("Su solicitud de convenio previa fue rechazada. Puede corregir los datos y volver a postular.")
            if (btnEnviarSolicitudConvenio != null) btnEnviarSolicitudConvenio.setDisable(false)
          case EstadoConvenio.PENDIENTE =>
            txtJustificacionRechazoConvenio.setText("Trámite pendiente en Secretaría General.")
            showSuccess("Ya cuenta con una solicitud de convenio pendiente de revisión.")
            if (btnEnviarSolicitudConvenio != null) btnEnviarSolicitudConvenio.setDisable(true)
          case EstadoConvenio.FORMALIZADO =>
            txtJustificacionRechazoConvenio.setText("Convenio Marco formalizado y vigente.")
            showSuccess("La empresa ya cuenta con una alianza legal formalizada.")
            if (btnEnviarSolicitudConvenio != null) btnEnviarSolicitudConvenio.setDisable(true)
          case _ =>
            if (btnEnviarSolicitudConvenio != null) btnEnviarSolicitudConvenio.setDisable(false)
        }
      case _ =>
        txtJustificacionRechazoConvenio.setText("[Ningún convenio registrado anteriormente.]")
        if (btnEnviarSolicitudConvenio != null) btnEnviarSolicitudConvenio.setDisable(false)
    }
  }

  @FXML
  def handleDescargarPlantillaConvenio(event: ActionEvent): Unit = {
    try {
      val srcFile = new java.io.File("docs/archivos_pdf/solicitud_de_convenio.pdf")
      if (!srcFile.exists()) {
        showError("Error: No se pudo encontrar el modelo base en 'docs/archivos_pdf/solicitud_de_convenio.pdf'.")
        mostrarAlerta("Error", "Archivo no encontrado", "No se pudo encontrar el modelo base en 'docs/archivos_pdf/solicitud_de_convenio.pdf'.", Alert.AlertType.ERROR)
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
        mostrarAlerta("Modelo Guardado", "Descarga exitosa", s"El modelo de convenio se guardó en: ${dest.getName}", Alert.AlertType.INFORMATION)
      }
    } catch {
      case NonFatal(e) =>
        showError(s"Error al descargar el modelo: ${e.getMessage}")
        mostrarAlerta("Error", "Excepción al descargar", e.getMessage, Alert.AlertType.ERROR)
    }
  }

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

  @FXML
  def handleEnviarSolicitudConvenio(event: ActionEvent): Unit = {
    val ruc = txtRucEmpresa.getText
    val razonSocial = txtRazonSocial.getText
    val representante = txtRepresentanteLegal.getText
    val direccion = txtDireccionMatriz.getText
    val mision = txtMisionEmpresa.getText
    val vision = txtVisionEmpresa.getText

    if (convenioFile == null) {
      val errMsg = "Debe adjuntar el archivo PDF del convenio firmado antes de enviar."
      showError(errMsg)
      mostrarAlerta("Archivo Requerido", "Falta convenio firmado", errMsg, Alert.AlertType.ERROR)
      return
    }

    val activeUserOpt = SessionManager.getUsuario
    val targetRuc = if (activeUserOpt.isDefined) activeUserOpt.get.identificacion else ruc

    val pdfPath = copyFileToUploads(
      convenioFile,
      s"convenio_${targetRuc}_${System.currentTimeMillis()}.pdf"
    )

    ConvenioLogic.registrarSolicitud(
      rucEmpresa = targetRuc,
      razonSocial = razonSocial,
      representanteLegal = representante,
      direccionMatriz = direccion,
      mision = mision,
      vision = vision,
      pdfNombre = convenioFile.getName,
      pdfRuta = pdfPath
    ) match {
      case Right(solId) =>
        val msg = s"¡Trámite enviado con éxito! Su solicitud de convenio #$solId quedó registrada en estado PENDIENTE."
        showSuccess(msg)
        mostrarAlerta("Envío Exitoso", "Trámite de Convenio Recibido", msg, Alert.AlertType.INFORMATION)
        clearForm()
        cargarEstadoConvenio(targetRuc)
      case Left(ConvenioFailure.Validacion(msg)) =>
        showError(msg)
        mostrarAlerta("Error de Validación", "No se pudo enviar la solicitud", msg, Alert.AlertType.ERROR)
      case Left(ConvenioFailure.ErrorPersistencia(msg)) =>
        val errMsg = s"Error técnico al registrar la solicitud: $msg"
        showError(errMsg)
        mostrarAlerta("Error de Persistencia", "Fallo al guardar en base de datos", errMsg, Alert.AlertType.ERROR)
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
    txtRazonSocial.clear()
    txtRepresentanteLegal.clear()
    txtDireccionMatriz.clear()
    txtMisionEmpresa.clear()
    txtVisionEmpresa.clear()
    convenioFile = null
    lblEstadoArchivoConvenio.setText("Ningún archivo seleccionado (.pdf)")
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
