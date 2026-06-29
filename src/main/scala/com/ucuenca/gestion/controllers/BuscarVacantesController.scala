package com.ucuenca.gestion.controllers

import javafx.fxml.FXML
import javafx.scene.control._
import javafx.event.ActionEvent
import javafx.beans.property.SimpleStringProperty
import javafx.stage.FileChooser
import com.ucuenca.gestion.models.dto.OfertaBolsaDTO
import com.ucuenca.gestion.models.logic.{BolsaEmpleoLogic, BolsaFailure}
import com.ucuenca.gestion.models.db.OfertaRepository
import com.ucuenca.gestion.utils.SessionManager
import scala.util.control.NonFatal

class BuscarVacantesController {

  @FXML var txtBuscarOfertas: TextField = _
  @FXML var tblOfertasBolsa: TableView[OfertaBolsaDTO] = _
  @FXML var colTitulo: TableColumn[OfertaBolsaDTO, String] = _
  @FXML var colPuesto: TableColumn[OfertaBolsaDTO, String] = _
  @FXML var colEmpresa: TableColumn[OfertaBolsaDTO, String] = _

  @FXML var lblTituloPuesto: Label = _
  @FXML var lblNombreEmpresa: Label = _
  @FXML var lblDuracionHoras: Label = _
  @FXML var lblVacantesDisponibles: Label = _
  @FXML var lblUbicacionEmpresa: Label = _
  @FXML var lblDescripcionPuesto: Label = _
  @FXML var lblRequisitosPuesto: Label = _
  @FXML var lblActividadesPuesto: Label = _

  @FXML var btnRevisarOfertaPDF: Button = _
  @FXML var btnDescargarOfertaPDF: Button = _
  @FXML var btnPostularBolsa: Button = _
  @FXML var lblEstado: Label = _

  private var todasLasOfertas: List[OfertaBolsaDTO] = Nil

  @FXML
  def initialize(): Unit = {
    // 1. Configurar fábricas de valores de las columnas
    colTitulo.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.tituloOferta))
    colPuesto.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.tituloOferta)) // se muestra el puesto/título de oferta
    colEmpresa.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.nombreEmpresa))

    // 2. Configurar deshabilitación inicial de botones
    btnRevisarOfertaPDF.setDisable(true)
    btnDescargarOfertaPDF.setDisable(true)
    btnPostularBolsa.setDisable(true)

    // 3. Listener de selección de la tabla
    tblOfertasBolsa.getSelectionModel.selectedItemProperty().addListener((_, _, seleccionada) => {
      mostrarDetalleOferta(seleccionada)
    })

    // 4. Configurar listener de búsqueda
    txtBuscarOfertas.textProperty().addListener((_, _, _) => {
      filtrarOfertas()
    })

    // 5. Cargar ofertas
    cargarOfertasBolsa()
  }

  /**
   * Carga el catálogo de ofertas de empleo aprobadas de la base de datos.
   */
  private def cargarOfertasBolsa(): Unit = {
    BolsaEmpleoLogic.listarOfertasAprobadas() match {
      case Right(lista) =>
        todasLasOfertas = lista
        tblOfertasBolsa.getItems.clear()
        tblOfertasBolsa.getItems.addAll(lista: _*)
      case Left(failure) =>
        showError(s"Error al cargar las vacantes: $failure")
    }
  }

  /**
   * Muestra la información técnica detallada de la oferta seleccionada en el panel derecho.
   */
  private def mostrarDetalleOferta(oferta: OfertaBolsaDTO): Unit = {
    if (oferta != null) {
      lblTituloPuesto.setText(oferta.tituloOferta)
      lblNombreEmpresa.setText(oferta.nombreEmpresa)
      lblDuracionHoras.setText(s"${oferta.duracionHoras} Horas")
      lblVacantesDisponibles.setText(s"${oferta.vacantesSolicitadas} cupos")
      lblUbicacionEmpresa.setText(oferta.ubicacionEmpresa)
      lblDescripcionPuesto.setText(oferta.descripcionGeneral)
      lblRequisitosPuesto.setText(oferta.requisitosObligatorios)
      lblActividadesPuesto.setText(oferta.actividadesEspecificas)

      btnRevisarOfertaPDF.setDisable(false)
      btnDescargarOfertaPDF.setDisable(false)
      btnPostularBolsa.setDisable(false)
      lblEstado.setVisible(false)
    } else {
      lblTituloPuesto.setText("Seleccione una oferta de la lista")
      lblNombreEmpresa.setText("Para visualizar los requisitos y detalles")
      lblDuracionHoras.setText("-- Horas")
      lblVacantesDisponibles.setText("-- cupos")
      lblUbicacionEmpresa.setText("--")
      lblDescripcionPuesto.setText("--")
      lblRequisitosPuesto.setText("--")
      lblActividadesPuesto.setText("--")

      btnRevisarOfertaPDF.setDisable(true)
      btnDescargarOfertaPDF.setDisable(true)
      btnPostularBolsa.setDisable(true)
    }
  }

  /**
   * Filtra las ofertas en base al input en el TextField de búsqueda.
   */
  private def filtrarOfertas(): Unit = {
    val filterText = txtBuscarOfertas.getText.toLowerCase.trim
    if (filterText.isEmpty) {
      tblOfertasBolsa.getItems.clear()
      tblOfertasBolsa.getItems.addAll(todasLasOfertas: _*)
    } else {
      val filtradas = todasLasOfertas.filter { o =>
        o.tituloOferta.toLowerCase.contains(filterText) ||
        o.nombreEmpresa.toLowerCase.contains(filterText) ||
        o.descripcionGeneral.toLowerCase.contains(filterText)
      }
      tblOfertasBolsa.getItems.clear()
      tblOfertasBolsa.getItems.addAll(filtradas: _*)
    }
  }

  /**
   * Abre de forma nativa el PDF firmado en el visor del sistema operativo.
   */
  @FXML
  def handleRevisarPDF(event: ActionEvent): Unit = {
    val selected = tblOfertasBolsa.getSelectionModel.getSelectedItem
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
                showError("El sistema operativo no soporta la apertura directa de escritorio.")
              }
            } else {
              showError("El archivo de la oferta no se encuentra en la ruta del servidor.")
            }
          case None =>
            showError("No se encontró la metadata del PDF asociado.")
        }
      } catch {
        case NonFatal(e) =>
          showError(s"Error al abrir el PDF: ${e.getMessage}")
      }
    }
  }

  /**
   * Guarda localmente el PDF de la oferta utilizando un FileChooser.
   */
  @FXML
  def handleDescargarPDF(event: ActionEvent): Unit = {
    val selected = tblOfertasBolsa.getSelectionModel.getSelectedItem
    if (selected != null) {
      try {
        OfertaRepository.buscarPdfPorId(selected.plantillaOfertaPDF) match {
          case Some(pdf) =>
            val srcFile = new java.io.File(pdf.rutaSeguraServidor)
            if (srcFile.exists()) {
              val fileChooser = new FileChooser()
              fileChooser.setTitle("Descargar Documento de Oferta")
              fileChooser.setInitialFileName(pdf.nombreOriginal)
              fileChooser.getExtensionFilters.add(new FileChooser.ExtensionFilter("Archivos PDF (*.pdf)", "*.pdf"))
              
              val dest = fileChooser.showSaveDialog(btnDescargarOfertaPDF.getScene.getWindow)
              if (dest != null) {
                java.nio.file.Files.copy(
                  srcFile.toPath,
                  dest.toPath,
                  java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
                showSuccess(s"Documento guardado en: ${dest.getName}")
              }
            } else {
              showError("El archivo de plantilla no existe en el servidor.")
            }
          case None =>
            showError("No se encontró la metadata del PDF.")
        }
      } catch {
        case NonFatal(e) =>
          showError(s"Error de descarga: ${e.getMessage}")
      }
    }
  }

  /**
   * Ejecuta la postulación a la vacante seleccionada, validando el perfil de ciclo y práctica del alumno.
   */
  @FXML
  def handlePostular(event: ActionEvent): Unit = {
    val selected = tblOfertasBolsa.getSelectionModel.getSelectedItem
    if (selected != null) {
      SessionManager.getUsuario match {
        case Some(usuario) =>
          BolsaEmpleoLogic.postular(usuario.identificacion, selected.idOferta) match {
            case Right(_) =>
              showSuccess(s"¡Postulación registrada con éxito a '${selected.tituloOferta}'!")
              btnPostularBolsa.setDisable(true)
            case Left(BolsaFailure.Validacion(msg)) =>
              showError(msg)
            case Left(BolsaFailure.ErrorPersistencia(msg)) =>
              showError(s"Error técnico de persistencia: $msg")
          }
        case None =>
          showError("Sesión inválida o expirada.")
      }
    } else {
      showError("Por favor, seleccione una oferta de la lista primero.")
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
