package com.ucuenca.gestion.controllers

import javafx.fxml.FXML
import javafx.scene.control._
import javafx.event.ActionEvent
import javafx.beans.property.SimpleStringProperty
import javafx.stage.FileChooser
import com.ucuenca.gestion.models.entities.SolicitudConvenio
import com.ucuenca.gestion.models.logic.{ConvenioLogic, ConvenioFailure}
import com.ucuenca.gestion.models.db.OfertaRepository
import scala.util.control.NonFatal

class SolicitudesConvenioController {

  @FXML var tblPeticionesConvenioSecretaria: TableView[SolicitudConvenio] = _
  @FXML var colEmpresa: TableColumn[SolicitudConvenio, String] = _
  @FXML var colRuc: TableColumn[SolicitudConvenio, String] = _

  @FXML var txtVisorDatosLegalesEmpresa: TextArea = _
  @FXML var btnRevisarConvenioPDF: Button = _
  @FXML var btnDescargarConvenioPDF: Button = _

  @FXML var txtCausasRechazoConvenio: TextArea = _
  @FXML var btnRechazar: Button = _
  @FXML var btnAprobar: Button = _
  @FXML var lblEstado: Label = _

  @FXML
  def initialize(): Unit = {
    // 1. Configurar columnas
    colEmpresa.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.razonSocial))
    colRuc.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.rucEmpresa))

    // 2. Deshabilitar botones por defecto
    btnRevisarConvenioPDF.setDisable(true)
    btnDescargarConvenioPDF.setDisable(true)

    // 3. Selección de filas
    tblPeticionesConvenioSecretaria.getSelectionModel.selectedItemProperty().addListener((_, _, seleccionada) => {
      val seleccionado = seleccionada != null
      btnRevisarConvenioPDF.setDisable(!seleccionado)
      btnDescargarConvenioPDF.setDisable(!seleccionado)

      if (seleccionado) {
        mostrarDetalleConvenio(seleccionada)
      } else {
        limpiarDetalleConvenio()
      }
    })

    // 4. Cargar solicitudes
    cargarSolicitudesPendientes()
  }

  private def mostrarAlerta(titulo: String, cabecera: String, mensaje: String, tipo: Alert.AlertType): Unit = {
    val alert = new Alert(tipo)
    alert.setTitle(titulo)
    alert.setHeaderText(cabecera)
    alert.setContentText(mensaje)
    alert.showAndWait()
  }

  private def cargarSolicitudesPendientes(): Unit = {
    ConvenioLogic.listarPendientes() match {
      case Right(lista) =>
        tblPeticionesConvenioSecretaria.getItems.clear()
        tblPeticionesConvenioSecretaria.getItems.addAll(lista: _*)
        lblEstado.setVisible(false)
      case Left(failure) =>
        showError(s"Error al cargar las solicitudes pendientes: $failure")
    }
  }

  private def mostrarDetalleConvenio(sel: SolicitudConvenio): Unit = {
    val visorText =
      s"""Empresa: ${sel.razonSocial}
         |RUC Jurídico: ${sel.rucEmpresa}
         |Representante Legal: ${sel.representanteLegal}
         |Dirección Matriz: ${sel.direccionMatriz}
         |Misión:
         |${sel.mision}
         |Visión:
         |${sel.vision}
         |""".stripMargin
    txtVisorDatosLegalesEmpresa.setText(visorText)
    txtCausasRechazoConvenio.clear()
  }

  private def limpiarDetalleConvenio(): Unit = {
    txtVisorDatosLegalesEmpresa.setText("[CONTENIDO EXTRAÍDO DE LOS DOCUMENTOS JURÍDICOS, RECONOCIMIENTO DE FIRMAS Y ESTATUTO LEGAL CARGADO POR LA EMPRESA]")
    txtCausasRechazoConvenio.clear()
  }

  @FXML
  def handleRevisarPDF(event: ActionEvent): Unit = {
    val selected = tblPeticionesConvenioSecretaria.getSelectionModel.getSelectedItem
    if (selected != null) {
      try {
        OfertaRepository.buscarPdfPorId(selected.convenioDocumentoPDF) match {
          case Some(pdf) =>
            val file = new java.io.File(pdf.rutaSeguraServidor)
            if (file.exists()) {
              if (java.awt.Desktop.isDesktopSupported) {
                java.awt.Desktop.getDesktop.open(file)
                showSuccess("Abriendo visor de PDF del sistema...")
              } else {
                showError("Error: El sistema operativo no soporta la apertura directa de archivos.")
              }
            } else {
              showError(s"Error: El archivo físico no existe en la ruta: ${pdf.rutaSeguraServidor}")
            }
          case None =>
            showError("Error: La metadata del PDF asociado no fue encontrada.")
        }
      } catch {
        case NonFatal(e) =>
          showError(s"Error al abrir el PDF: ${e.getMessage}")
      }
    }
  }

  @FXML
  def handleDescargarPDF(event: ActionEvent): Unit = {
    val selected = tblPeticionesConvenioSecretaria.getSelectionModel.getSelectedItem
    if (selected != null) {
      try {
        OfertaRepository.buscarPdfPorId(selected.convenioDocumentoPDF) match {
          case Some(pdf) =>
            val srcFile = new java.io.File(pdf.rutaSeguraServidor)
            if (srcFile.exists()) {
              val fileChooser = new FileChooser()
              fileChooser.setTitle("Exportar Documento de Convenio")
              fileChooser.setInitialFileName(pdf.nombreOriginal)
              fileChooser.getExtensionFilters.add(new FileChooser.ExtensionFilter("Archivos PDF (*.pdf)", "*.pdf"))

              val dest = fileChooser.showSaveDialog(btnDescargarConvenioPDF.getScene.getWindow)
              if (dest != null) {
                java.nio.file.Files.copy(
                  srcFile.toPath,
                  dest.toPath,
                  java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
                showSuccess(s"Convenio guardado exitosamente en: ${dest.getName}")
              }
            } else {
              showError("Error: El archivo original no existe en el servidor.")
            }
          case None =>
            showError("Error: No se encontró la metadata del PDF.")
        }
      } catch {
        case NonFatal(e) =>
          showError(s"Error de descarga: ${e.getMessage}")
      }
    }
  }

  @FXML
  def handleAprobar(event: ActionEvent): Unit = {
    val selected = tblPeticionesConvenioSecretaria.getSelectionModel.getSelectedItem
    if (selected != null) {
      ConvenioLogic.aprobarConvenio(selected.idSolicitudConvenio, selected.rucEmpresa) match {
        case Right(_) =>
          val msg = s"¡Convenio formalizado con éxito! La alianza con '${selected.razonSocial}' ha sido autorizada."
          showSuccess(msg)
          mostrarAlerta("Formalización Exitosa", "Convenio Legal Formalizado", msg, Alert.AlertType.INFORMATION)
          cargarSolicitudesPendientes()
          limpiarDetalleConvenio()
        case Left(ConvenioFailure.Validacion(msg)) =>
          showError(msg)
          mostrarAlerta("Error de Validación", "No se puede formalizar el convenio", msg, Alert.AlertType.ERROR)
        case Left(ConvenioFailure.ErrorPersistencia(msg)) =>
          showError(s"Error técnico de persistencia: $msg")
          mostrarAlerta("Error de Persistencia", "Fallo de base de datos", msg, Alert.AlertType.ERROR)
      }
    } else {
      showError("Por favor, seleccione una solicitud de la lista antes de formalizar.")
    }
  }

  @FXML
  def handleRechazar(event: ActionEvent): Unit = {
    val selected = tblPeticionesConvenioSecretaria.getSelectionModel.getSelectedItem
    if (selected != null) {
      val causas = txtCausasRechazoConvenio.getText
      if (causas == null || causas.trim.isEmpty) {
        showError("Las causas de rechazo administrativo son obligatorias si rechaza la solicitud.")
        mostrarAlerta("Causas Requeridas", "Falta justificación", "Debe redactar obligatoriamente las causas del rechazo.", Alert.AlertType.WARNING)
        return
      }

      ConvenioLogic.rechazarConvenio(selected.idSolicitudConvenio, selected.rucEmpresa, causas) match {
        case Right(_) =>
          val msg = s"La solicitud de convenio de '${selected.razonSocial}' ha sido rechazada administrativamente."
          showSuccess(msg)
          mostrarAlerta("Solicitud Rechazada", "Convenio Rechazado", msg, Alert.AlertType.INFORMATION)
          cargarSolicitudesPendientes()
          limpiarDetalleConvenio()
        case Left(ConvenioFailure.Validacion(msg)) =>
          showError(msg)
          mostrarAlerta("Error de Validación", "No se puede rechazar el convenio", msg, Alert.AlertType.ERROR)
        case Left(ConvenioFailure.ErrorPersistencia(msg)) =>
          showError(s"Error técnico de persistencia: $msg")
          mostrarAlerta("Error de Persistencia", "Fallo de base de datos", msg, Alert.AlertType.ERROR)
      }
    } else {
      showError("Por favor, seleccione una solicitud de la lista antes de rechazar.")
    }
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
