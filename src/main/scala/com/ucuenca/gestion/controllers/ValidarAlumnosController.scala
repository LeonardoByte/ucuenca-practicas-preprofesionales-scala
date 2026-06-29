package com.ucuenca.gestion.controllers

import javafx.fxml.FXML
import javafx.scene.control._
import javafx.event.ActionEvent
import javafx.beans.property.SimpleStringProperty
import javafx.stage.FileChooser
import com.ucuenca.gestion.models.dto.PostulacionPendienteDTO
import com.ucuenca.gestion.models.logic.{ValidacionAcademicaLogic, BolsaFailure}
import com.ucuenca.gestion.models.db.OfertaRepository
import scala.util.control.NonFatal

class ValidarAlumnosController {

  @FXML var tblPostulacionesPendientes: TableView[PostulacionPendienteDTO] = _
  @FXML var colEstudiante: TableColumn[PostulacionPendienteDTO, String] = _
  @FXML var colOferta: TableColumn[PostulacionPendienteDTO, String] = _
  @FXML var colFecha: TableColumn[PostulacionPendienteDTO, String] = _

  @FXML var lblCicloCumple: Label = _
  @FXML var lblPracticaApto: Label = _
  @FXML var lblEstado: Label = _

  @FXML var txtExtractoCV: TextArea = _
  @FXML var txtMotivoRechazoPostulacion: TextArea = _

  @FXML var btnRevisarMallaPDF: Button = _
  @FXML var btnDescargarMallaPDF: Button = _
  @FXML var btnRevisarCVPDF: Button = _
  @FXML var btnDescargarCVPDF: Button = _
  @FXML var btnDenegar: Button = _
  @FXML var btnAprobar: Button = _

  @FXML
  def initialize(): Unit = {
    // 1. Configurar fábricas de valores de las columnas
    colEstudiante.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.nombreEstudiante))
    colOferta.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.tituloOferta))
    colFecha.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.fechaPostulacion.toString))

    // 2. Deshabilitar botones al inicio
    deshabilitarDetalles()

    // 3. Listener de selección de la tabla
    tblPostulacionesPendientes.getSelectionModel.selectedItemProperty().addListener((_, _, seleccionada) => {
      mostrarDetallePostulacion(seleccionada)
    })

    // 4. Cargar datos
    cargarPostulaciones()
  }

  /**
   * Carga el listado de postulaciones pendientes de validación.
   */
  private def cargarPostulaciones(): Unit = {
    ValidacionAcademicaLogic.listarPendientes() match {
      case Right(lista) =>
        tblPostulacionesPendientes.getItems.clear()
        tblPostulacionesPendientes.getItems.addAll(lista: _*)
      case Left(failure) =>
        showError(s"Error al cargar postulaciones: $failure")
    }
  }

  /**
   * Deshabilita los controles del panel derecho cuando no hay selección.
   */
  private def deshabilitarDetalles(): Unit = {
    lblCicloCumple.setText("--")
    lblCicloCumple.setStyle("-fx-text-fill: #64748b;")
    lblPracticaApto.setText("--")
    lblPracticaApto.setStyle("-fx-text-fill: #64748b;")
    txtExtractoCV.setText("[Seleccione un estudiante de la lista]")
    txtMotivoRechazoPostulacion.clear()

    btnRevisarMallaPDF.setDisable(true)
    btnDescargarMallaPDF.setDisable(true)
    btnRevisarCVPDF.setDisable(true)
    btnDescargarCVPDF.setDisable(true)
    btnDenegar.setDisable(true)
    btnAprobar.setDisable(true)
  }

  /**
   * Muestra la información del expediente y activa los visores de PDF del alumno seleccionado.
   */
  private def mostrarDetallePostulacion(p: PostulacionPendienteDTO): Unit = {
    if (p != null) {
      // 1. Mostrar validaciones automáticas de ciclo (>= 6)
      if (p.cicloActual >= 6) {
        lblCicloCumple.setText(s"SÍ (Ciclo ${p.cicloActual})")
        lblCicloCumple.setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold;")
      } else {
        lblCicloCumple.setText(s"NO (Ciclo ${p.cicloActual})")
        lblCicloCumple.setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;")
      }

      // 2. Mostrar validaciones automáticas de estado de práctica (SIN_PRACTICA)
      if (p.estadoEstudiantePractica == "SIN_PRACTICA") {
        lblPracticaApto.setText("SÍ (Sin práctica)")
        lblPracticaApto.setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold;")
      } else {
        lblPracticaApto.setText(s"NO (${p.estadoEstudiantePractica})")
        lblPracticaApto.setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;")
      }

      // 3. Mostrar estado del CV
      p.curriculumVitaePDF match {
        case Some(_) =>
          txtExtractoCV.setText(s"El estudiante ${p.nombreEstudiante} cuenta con Hoja de Vida (CV) adjunta en formato PDF. Listo para ser fiscalizado.")
          btnRevisarCVPDF.setDisable(false)
          btnDescargarCVPDF.setDisable(false)
        case None =>
          txtExtractoCV.setText("El estudiante NO ha cargado una Hoja de Vida (CV) en su perfil institucional.")
          btnRevisarCVPDF.setDisable(true)
          btnDescargarCVPDF.setDisable(true)
      }

      // Habilitar Malla PDF (siempre obligatoria al registrarse)
      btnRevisarMallaPDF.setDisable(false)
      btnDescargarMallaPDF.setDisable(false)

      btnDenegar.setDisable(false)
      btnAprobar.setDisable(false)
      lblEstado.setVisible(false)
    } else {
      deshabilitarDetalles()
    }
  }

  /**
   * Abre de forma nativa la malla académica del estudiante seleccionado.
   */
  @FXML
  def handleRevisarMalla(event: ActionEvent): Unit = {
    val selected = tblPostulacionesPendientes.getSelectionModel.getSelectedItem
    if (selected != null) {
      abrirPdfNativo(selected.mallaAcademicaPDF)
    }
  }

  /**
   * Descarga de forma nativa la malla académica.
   */
  @FXML
  def handleDescargarMalla(event: ActionEvent): Unit = {
    val selected = tblPostulacionesPendientes.getSelectionModel.getSelectedItem
    if (selected != null) {
      descargarPdfNativo(selected.mallaAcademicaPDF, btnDescargarMallaPDF)
    }
  }

  /**
   * Abre de forma nativa la hoja de vida (CV) del estudiante seleccionado.
   */
  @FXML
  def handleRevisarCV(event: ActionEvent): Unit = {
    val selected = tblPostulacionesPendientes.getSelectionModel.getSelectedItem
    if (selected != null) {
      selected.curriculumVitaePDF match {
        case Some(pdfId) => abrirPdfNativo(pdfId)
        case None => showError("El estudiante no cuenta con un CV registrado.")
      }
    }
  }

  /**
   * Descarga de forma nativa la hoja de vida (CV).
   */
  @FXML
  def handleDescargarCV(event: ActionEvent): Unit = {
    val selected = tblPostulacionesPendientes.getSelectionModel.getSelectedItem
    if (selected != null) {
      selected.curriculumVitaePDF match {
        case Some(pdfId) => descargarPdfNativo(pdfId, btnDescargarCVPDF)
        case None => showError("El estudiante no cuenta con un CV registrado.")
      }
    }
  }

  /**
   * Aprueba la postulación manteniendo el estado PENDIENTE.
   */
  @FXML
  def handleAprobar(event: ActionEvent): Unit = {
    val selected = tblPostulacionesPendientes.getSelectionModel.getSelectedItem
    if (selected != null) {
      ValidacionAcademicaLogic.aprobar(selected.idPostulacion) match {
        case Right(_) =>
          showSuccess("Postulación aprobada académicamente (sigue PENDIENTE para la empresa).")
          cargarPostulaciones()
          deshabilitarDetalles()
        case Left(failure) =>
          showError(s"Error al aprobar la postulación: $failure")
      }
    }
  }

  /**
   * Rechaza la postulación exigiendo una justificación.
   */
  @FXML
  def handleRechazar(event: ActionEvent): Unit = {
    val selected = tblPostulacionesPendientes.getSelectionModel.getSelectedItem
    if (selected != null) {
      val motivo = txtMotivoRechazoPostulacion.getText
      ValidacionAcademicaLogic.rechazar(selected.idPostulacion, motivo) match {
        case Right(_) =>
          showSuccess("Postulación rechazada con éxito.")
          cargarPostulaciones()
          deshabilitarDetalles()
        case Left(BolsaFailure.Validacion(msg)) =>
          showError(msg)
        case Left(BolsaFailure.ErrorPersistencia(msg)) =>
          showError(s"Error técnico de base de datos: $msg")
      }
    }
  }

  /**
   * Abre un archivo PDF usando el Desktop API nativo.
   */
  private def abrirPdfNativo(pdfId: Int): Unit = {
    try {
      OfertaRepository.buscarPdfPorId(pdfId) match {
        case Some(pdf) =>
          val file = new java.io.File(pdf.rutaSeguraServidor)
          if (file.exists()) {
            if (java.awt.Desktop.isDesktopSupported) {
              java.awt.Desktop.getDesktop.open(file)
              showSuccess("Abriendo visor de PDF nativo...")
            } else {
              showError("El sistema no soporta la apertura directa de escritorio.")
            }
          } else {
            showError("El archivo no se encuentra en el servidor local.")
          }
        case None =>
          showError("No se encontraron metadatos del archivo PDF.")
      }
    } catch {
      case NonFatal(e) =>
        showError(s"Error al abrir documento: ${e.getMessage}")
    }
  }

  /**
   * Guarda un archivo PDF localmente vía FileChooser.
   */
  private def descargarPdfNativo(pdfId: Int, ownerButton: Button): Unit = {
    try {
      OfertaRepository.buscarPdfPorId(pdfId) match {
        case Some(pdf) =>
          val srcFile = new java.io.File(pdf.rutaSeguraServidor)
          if (srcFile.exists()) {
            val fileChooser = new FileChooser()
            fileChooser.setTitle("Descargar Documento Académico")
            fileChooser.setInitialFileName(pdf.nombreOriginal)
            fileChooser.getExtensionFilters.add(new FileChooser.ExtensionFilter("Archivos PDF (*.pdf)", "*.pdf"))
            
            val dest = fileChooser.showSaveDialog(ownerButton.getScene.getWindow)
            if (dest != null) {
              java.nio.file.Files.copy(
                srcFile.toPath,
                dest.toPath,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
              )
              showSuccess(s"Documento guardado: ${dest.getName}")
            }
          } else {
            showError("El archivo físico no existe en el servidor.")
          }
        case None =>
          showError("No se encontraron metadatos del archivo.")
      }
    } catch {
      case NonFatal(e) =>
        showError(s"Error al descargar: ${e.getMessage}")
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
