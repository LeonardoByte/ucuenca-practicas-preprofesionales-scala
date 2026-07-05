package com.ucuenca.gestion.controllers

import javafx.fxml.FXML
import javafx.scene.control._
import javafx.event.ActionEvent
import javafx.beans.property.SimpleStringProperty
import javafx.stage.FileChooser
import com.ucuenca.gestion.models.dto.CandidatoBolsaDTO
import com.ucuenca.gestion.models.logic.{SeleccionCandidatosLogic, BolsaFailure}
import com.ucuenca.gestion.models.db.OfertaRepository
import com.ucuenca.gestion.utils.SessionManager
import scala.util.control.NonFatal

class GestionCandidatosController {

  @FXML var cmbOfertasFiltro: ComboBox[String] = _
  @FXML var tblCandidatosDisponibles: TableView[CandidatoBolsaDTO] = _
  @FXML var colEstudiante: TableColumn[CandidatoBolsaDTO, String] = _
  @FXML var colPromedio: TableColumn[CandidatoBolsaDTO, String] = _

  @FXML var btnRevisarMallaPDF: Button = _
  @FXML var btnDescargarMallaPDF: Button = _
  @FXML var txtExtractoCV: TextArea = _
  @FXML var btnRevisarCVPDF: Button = _
  @FXML var btnDescargarCVPDF: Button = _

  @FXML var cmbTutorEmpresarial: ComboBox[String] = _

  @FXML var btnDenegar: Button = _
  @FXML var btnAceptar: Button = _
  @FXML var lblEstado: Label = _

  private var todasLasPostulaciones: List[CandidatoBolsaDTO] = Nil
  private var tutoresMap: Map[String, String] = Map.empty // Nombre -> ID

  @FXML
  def initialize(): Unit = {
    // 1. Configurar columnas de la tabla
    colEstudiante.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.nombreEstudiante))
    colPromedio.setCellValueFactory(_ => new SimpleStringProperty("8.50")) // promedio simulado

    // 2. Deshabilitar botones inicialmente
    deshabilitarDetalles()

    // 3. Listener de selección de tabla
    tblCandidatosDisponibles.getSelectionModel.selectedItemProperty().addListener((_, _, seleccionada) => {
      mostrarDetalleCandidato(seleccionada)
    })

    // 4. Configurar listener del filtro de ofertas
    cmbOfertasFiltro.setOnAction(_ => {
      filtrarCandidatos()
    })

    // 5. Cargar datos desde base
    cargarDatos()
  }

  /**
   * Carga los candidatos y la nómina de tutores/supervisores técnicos de la empresa.
   */
  private def cargarDatos(): Unit = {
    SessionManager.getUsuario match {
      case Some(usuario) =>
        val companyRuc = usuario.identificacion

        // Cargar candidatos
        SeleccionCandidatosLogic.listarCandidatos(companyRuc) match {
          case Right(lista) =>
            todasLasPostulaciones = lista
            tblCandidatosDisponibles.getItems.clear()
            tblCandidatosDisponibles.getItems.addAll(lista: _*)

            // Poblar filtro de ofertas
            val ofertas = "TODOS" :: lista.map(_.tituloOferta).distinct
            cmbOfertasFiltro.getItems.clear()
            cmbOfertasFiltro.getItems.addAll(ofertas: _*)
            cmbOfertasFiltro.setValue("TODOS")

          case Left(failure) =>
            showError(s"Error al cargar candidatos: $failure")
        }

        // Cargar nómina de tutores
        SeleccionCandidatosLogic.listarTutores(companyRuc) match {
          case Right(tutores) =>
            tutoresMap = tutores.map { case (id, nombre) => nombre -> id }.toMap
            cmbTutorEmpresarial.getItems.clear()
            cmbTutorEmpresarial.getItems.addAll(tutores.map(_._2): _*)
          case Left(failure) =>
            showError(s"Error al cargar nómina de tutores: $failure")
        }

      case None =>
        showError("Sesión inválida o expirada.")
    }
  }

  /**
   * Deshabilita los detalles de candidato en el panel derecho.
   */
  private def deshabilitarDetalles(): Unit = {
    txtExtractoCV.setText("[Seleccione un estudiante de la lista]")
    cmbTutorEmpresarial.setValue(null)

    btnRevisarMallaPDF.setDisable(true)
    btnDescargarMallaPDF.setDisable(true)
    btnRevisarCVPDF.setDisable(true)
    btnDescargarCVPDF.setDisable(true)
    btnDenegar.setDisable(true)
    btnAceptar.setDisable(true)
  }

  /**
   * Muestra la información detallada del candidato seleccionado y activa los PDF correspondientes.
   */
  private def mostrarDetalleCandidato(c: CandidatoBolsaDTO): Unit = {
    if (c != null) {
      c.curriculumVitaePDF match {
        case Some(_) =>
          txtExtractoCV.setText(s"El estudiante ${c.nombreEstudiante} cuenta con Hoja de Vida (CV) adjunta en formato PDF. Listo para ser fiscalizado.")
          btnRevisarCVPDF.setDisable(false)
          btnDescargarCVPDF.setDisable(false)
        case None =>
          txtExtractoCV.setText("El estudiante NO ha cargado una Hoja de Vida (CV) en su perfil.")
          btnRevisarCVPDF.setDisable(true)
          btnDescargarCVPDF.setDisable(true)
      }

      btnRevisarMallaPDF.setDisable(false)
      btnDescargarMallaPDF.setDisable(false)
      btnDenegar.setDisable(false)
      btnAceptar.setDisable(false)
      lblEstado.setVisible(false)
    } else {
      deshabilitarDetalles()
    }
  }

  /**
   * Filtra la TableView basándose en la oferta seleccionada en el ComboBox.
   */
  private def filtrarCandidatos(): Unit = {
    val selectedOffer = cmbOfertasFiltro.getValue
    if (selectedOffer == null || selectedOffer == "TODOS") {
      tblCandidatosDisponibles.getItems.clear()
      tblCandidatosDisponibles.getItems.addAll(todasLasPostulaciones: _*)
    } else {
      val filtradas = todasLasPostulaciones.filter(_.tituloOferta == selectedOffer)
      tblCandidatosDisponibles.getItems.clear()
      tblCandidatosDisponibles.getItems.addAll(filtradas: _*)
    }
  }

  /**
   * Abre la malla analítica del estudiante natively.
   */
  @FXML
  def handleRevisarMalla(event: ActionEvent): Unit = {
    val selected = tblCandidatosDisponibles.getSelectionModel.getSelectedItem
    if (selected != null) {
      abrirPdfNativo(selected.mallaAcademicaPDF)
    }
  }

  /**
   * Descarga la malla analítica natively.
   */
  @FXML
  def handleDescargarMalla(event: ActionEvent): Unit = {
    val selected = tblCandidatosDisponibles.getSelectionModel.getSelectedItem
    if (selected != null) {
      descargarPdfNativo(selected.mallaAcademicaPDF, btnDescargarMallaPDF)
    }
  }

  /**
   * Abre el CV del estudiante natively.
   */
  @FXML
  def handleRevisarCV(event: ActionEvent): Unit = {
    val selected = tblCandidatosDisponibles.getSelectionModel.getSelectedItem
    if (selected != null) {
      selected.curriculumVitaePDF match {
        case Some(pdfId) => abrirPdfNativo(pdfId)
        case None => showError("El candidato no cuenta con CV registrado.")
      }
    }
  }

  /**
   * Descarga el CV natively.
   */
  @FXML
  def handleDescargarCV(event: ActionEvent): Unit = {
    val selected = tblCandidatosDisponibles.getSelectionModel.getSelectedItem
    if (selected != null) {
      selected.curriculumVitaePDF match {
        case Some(pdfId) => descargarPdfNativo(pdfId, btnDescargarCVPDF)
        case None => showError("El candidato no cuenta con CV registrado.")
      }
    }
  }

  /**
   * Acepta al candidato seleccionado en el panel. Exige elegir un tutor de la nómina.
   */
  @FXML
  def handleAceptar(event: ActionEvent): Unit = {
    val selected = tblCandidatosDisponibles.getSelectionModel.getSelectedItem
    if (selected != null) {
      val selectedTutorName = cmbTutorEmpresarial.getValue
      if (selectedTutorName == null || selectedTutorName.trim.isEmpty) {
        showError("Debe seleccionar obligatoriamente un supervisor técnico de la nómina.")
        return
      }

      val tutorId = tutoresMap.getOrElse(selectedTutorName, "")
      SessionManager.getUsuario match {
        case Some(usuario) =>
          val companyRuc = usuario.identificacion

          // Aceptar e inicializar práctica
          SeleccionCandidatosLogic.aceptar(
            idPostulacion = selected.idPostulacion,
            rucEmpresa = companyRuc,
            ciEstudiante = selected.ciEstudiante,
            idTutorEmpresarial = tutorId
          ) match {
            case Right(_) =>
              showSuccess(s"¡Estudiante '${selected.nombreEstudiante}' aceptado con éxito!")
              cargarDatos()
              deshabilitarDetalles()
            case Left(BolsaFailure.Validacion(msg)) =>
              showError(msg)
            case Left(BolsaFailure.ErrorPersistencia(msg)) =>
              showError(s"Error transaccional de persistencia: $msg")
          }

        case None =>
          showError("Sesión inválida o expirada.")
      }
    }
  }

  /**
   * Abre un TextInputDialog para recopilar el motivo obligatorio y rechazar al postulante.
   */
  @FXML
  def handleRechazar(event: ActionEvent): Unit = {
    val selected = tblCandidatosDisponibles.getSelectionModel.getSelectedItem
    if (selected != null) {
      val dialog = new TextInputDialog()
      dialog.setTitle("Rechazar Postulante")
      dialog.setHeaderText(s"Rechazar postulación de ${selected.nombreEstudiante}")
      dialog.setContentText("Motivo de rechazo (obligatorio):")

      val resultOpt = dialog.showAndWait()
      if (resultOpt.isPresent) {
        val motivo = resultOpt.get()
        SeleccionCandidatosLogic.rechazar(selected.idPostulacion, motivo) match {
          case Right(_) =>
            showSuccess("Postulación rechazada con éxito.")
            cargarDatos()
            deshabilitarDetalles()
          case Left(BolsaFailure.Validacion(msg)) =>
            showError(msg)
          case Left(BolsaFailure.ErrorPersistencia(msg)) =>
            showError(s"Error técnico al guardar rechazo: $msg")
        }
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
            showError("El archivo no existe en el servidor local.")
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
