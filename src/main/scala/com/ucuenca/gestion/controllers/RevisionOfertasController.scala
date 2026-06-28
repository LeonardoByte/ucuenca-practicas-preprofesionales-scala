package com.ucuenca.gestion.controllers

import javafx.fxml.FXML
import javafx.scene.control._
import javafx.event.ActionEvent
import javafx.beans.property.SimpleStringProperty
import javafx.stage.FileChooser
import com.ucuenca.gestion.models.dto.OfertaRevisionDTO
import com.ucuenca.gestion.models.logic.{RevisionOfertasLogic, RevisionFailure}
import com.ucuenca.gestion.models.db.OfertaRepository
import scala.util.control.NonFatal

class RevisionOfertasController {

  @FXML var tblRevisionOfertas: TableView[OfertaRevisionDTO] = _
  @FXML var colEmpresa: TableColumn[OfertaRevisionDTO, String] = _
  @FXML var colTitulo: TableColumn[OfertaRevisionDTO, String] = _
  @FXML var colDescripcion: TableColumn[OfertaRevisionDTO, String] = _
  @FXML var colVacantes: TableColumn[OfertaRevisionDTO, String] = _
  @FXML var colFecha: TableColumn[OfertaRevisionDTO, String] = _
  @FXML var btnRevisarOfertaPDF: Button = _
  @FXML var btnDescargarOfertaPDF: Button = _
  @FXML var txtJustificacionOferta: TextArea = _
  @FXML var lblEstado: Label = _

  @FXML
  def initialize(): Unit = {
    // 1. Configurar fábricas de valores de las columnas
    colEmpresa.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.nombreEmpresa))
    colTitulo.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.tituloOferta))
    colDescripcion.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.descripcionGeneral))
    colVacantes.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.vacantesSolicitadas.toString))
    colFecha.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.fechaSolicitud.toString))

    // 2. Configurar deshabilitación inicial de botones de acción
    btnRevisarOfertaPDF.setDisable(true)
    btnDescargarOfertaPDF.setDisable(true)

    // 3. Listener de selección de filas
    tblRevisionOfertas.getSelectionModel.selectedItemProperty().addListener((_, _, seleccionada) => {
      val seleccionado = seleccionada != null
      btnRevisarOfertaPDF.setDisable(!seleccionado)
      btnDescargarOfertaPDF.setDisable(!seleccionado)
      if (!seleccionado) {
        txtJustificacionOferta.clear()
      }
    })

    // 4. Cargar ofertas iniciales
    cargarOfertasPendientes()
  }

  /**
   * Carga el listado de ofertas pendientes.
   */
  private def cargarOfertasPendientes(): Unit = {
    RevisionOfertasLogic.listarPendientes() match {
      case Right(lista) =>
        tblRevisionOfertas.getItems.clear()
        tblRevisionOfertas.getItems.addAll(lista: _*)
        lblEstado.setVisible(false)
      case Left(failure) =>
        showError(s"Error al cargar las ofertas pendientes: $failure")
    }
  }

  /**
   * Abre de forma nativa el archivo PDF firmado de la oferta en el visor del sistema operativo.
   */
  @FXML
  def handleRevisarPDF(event: ActionEvent): Unit = {
    val selected = tblRevisionOfertas.getSelectionModel.getSelectedItem
    if (selected != null) {
      try {
        OfertaRepository.buscarPdfPorId(selected.plantillaOfertaPDF) match {
          case Some(pdf) =>
            val file = new java.io.File(pdf.rutaSeguraServidor)
            if (file.exists()) {
              if (java.awt.Desktop.isDesktopSupported) {
                java.awt.Desktop.getDesktop.open(file)
                showSuccess("Abriendo visor de PDF del sistema...")
              } else {
                showError("Error: El sistema operativo no soporta la apertura directa de archivos de escritorio.")
              }
            } else {
              showError(s"Error: El archivo físico no existe en la ruta: ${pdf.rutaSeguraServidor}")
            }
          case None =>
            showError("Error: La metadata del PDF asociado no fue encontrada en la base de datos.")
        }
      } catch {
        case NonFatal(e) =>
          showError(s"Error al abrir el PDF: ${e.getMessage}")
      }
    }
  }

  /**
   * Descarga/Exporta el PDF de la oferta firmada a una ruta local seleccionada por el coordinador.
   */
  @FXML
  def handleDescargarPDF(event: ActionEvent): Unit = {
    val selected = tblRevisionOfertas.getSelectionModel.getSelectedItem
    if (selected != null) {
      try {
        OfertaRepository.buscarPdfPorId(selected.plantillaOfertaPDF) match {
          case Some(pdf) =>
            val srcFile = new java.io.File(pdf.rutaSeguraServidor)
            if (srcFile.exists()) {
              val fileChooser = new FileChooser()
              fileChooser.setTitle("Exportar Documento de Oferta")
              fileChooser.setInitialFileName(pdf.nombreOriginal)
              fileChooser.getExtensionFilters.add(new FileChooser.ExtensionFilter("Archivos PDF (*.pdf)", "*.pdf"))
              
              val dest = fileChooser.showSaveDialog(btnDescargarOfertaPDF.getScene.getWindow)
              if (dest != null) {
                java.nio.file.Files.copy(
                  srcFile.toPath,
                  dest.toPath,
                  java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
                showSuccess(s"Documento guardado exitosamente en: ${dest.getName}")
              }
            } else {
              showError("Error: El archivo original de plantilla no existe en el servidor.")
            }
          case None =>
            showError("Error: No se encontró la metadata del PDF asociado.")
        }
      } catch {
        case NonFatal(e) =>
          showError(s"Error de descarga: ${e.getMessage}")
      }
    }
  }

  /**
   * Aprueba la convocatoria de la oferta seleccionada.
   */
  @FXML
  def handleAprobar(event: ActionEvent): Unit = {
    val selected = tblRevisionOfertas.getSelectionModel.getSelectedItem
    if (selected != null) {
      RevisionOfertasLogic.aprobar(selected.idOferta) match {
        case Right(_) =>
          showSuccess(s"¡Aprobación con éxito! La convocatoria '${selected.tituloOferta}' ha sido liberada.")
          cargarOfertasPendientes()
        case Left(RevisionFailure.Validacion(msg)) =>
          showError(msg)
        case Left(RevisionFailure.ErrorPersistencia(msg)) =>
          showError(s"Error técnico de persistencia: $msg")
      }
    } else {
      showError("Por favor, seleccione una oferta de la grilla antes de aprobar.")
    }
  }

  /**
   * Rechaza la convocatoria de la oferta seleccionada exigiendo justificación.
   */
  @FXML
  def handleRechazar(event: ActionEvent): Unit = {
    val selected = tblRevisionOfertas.getSelectionModel.getSelectedItem
    if (selected != null) {
      val justificacion = txtJustificacionOferta.getText
      RevisionOfertasLogic.rechazar(selected.idOferta, justificacion) match {
        case Right(_) =>
          showSuccess(s"Convocatoria '${selected.tituloOferta}' rechazada y notificada.")
          cargarOfertasPendientes()
        case Left(RevisionFailure.Validacion(msg)) =>
          showError(msg)
        case Left(RevisionFailure.ErrorPersistencia(msg)) =>
          showError(s"Error técnico de persistencia: $msg")
      }
    } else {
      showError("Por favor, seleccione una oferta de la grilla antes de rechazar.")
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
