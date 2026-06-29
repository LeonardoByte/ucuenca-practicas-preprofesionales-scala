package com.ucuenca.gestion.controllers

import javafx.fxml.FXML
import javafx.scene.control._
import javafx.event.ActionEvent
import javafx.beans.property.SimpleStringProperty
import javafx.stage.FileChooser
import com.ucuenca.gestion.models.dto.SolicitudPropiaRevisionDTO
import com.ucuenca.gestion.models.logic.{RevisionOficiosLogic, RevisionOficiosFailure}
import com.ucuenca.gestion.models.db.OfertaRepository
import scala.util.control.NonFatal

case class TutorItem(ci: String, nombre: String) {
  override def toString: String = nombre
}

class RevisionOficiosController {

  @FXML var tblSolicitudesEmpresaPropia: TableView[SolicitudPropiaRevisionDTO] = _
  @FXML var colEstudiante: TableColumn[SolicitudPropiaRevisionDTO, String] = _
  @FXML var colEntidadExterna: TableColumn[SolicitudPropiaRevisionDTO, String] = _

  @FXML var txtVisorOficioAlumno: TextArea = _
  @FXML var btnRevisarOficioPDF: Button = _
  @FXML var btnDescargarOficioPDF: Button = _

  @FXML var txtCodigoOficioVuelta: TextField = _
  @FXML var cmbTutorAcadAsignado: ComboBox[TutorItem] = _

  @FXML var txtMotivoDenegacionOficio: TextArea = _
  @FXML var btnRechazar: Button = _
  @FXML var btnAprobar: Button = _
  @FXML var lblEstado: Label = _

  @FXML
  def initialize(): Unit = {
    // 1. Configurar fábricas de valores de las columnas
    colEstudiante.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.nombreEstudiante))
    colEntidadExterna.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.nombreEntidadExterna))

    // 2. Configurar deshabilitación inicial de botones
    btnRevisarOficioPDF.setDisable(true)
    btnDescargarOficioPDF.setDisable(true)

    // 3. Listener de selección de filas
    tblSolicitudesEmpresaPropia.getSelectionModel.selectedItemProperty().addListener((_, _, seleccionada) => {
      val seleccionado = seleccionada != null
      btnRevisarOficioPDF.setDisable(!seleccionado)
      btnDescargarOficioPDF.setDisable(!seleccionado)

      if (seleccionado) {
        mostrarDetalleSolicitud(seleccionada)
        cargarSiguienteCodigoOficio()
      } else {
        limpiarDetalleSolicitud()
      }
    })

    // 4. Cargar tutores académicos en el combo box
    cargarTutoresAcademicos()

    // 5. Cargar solicitudes iniciales
    cargarSolicitudesPendientes()
  }

  private def cargarTutoresAcademicos(): Unit = {
    RevisionOficiosLogic.listarTutoresActivos() match {
      case Right(tutores) =>
        cmbTutorAcadAsignado.getItems.clear()
        tutores.foreach { t =>
          cmbTutorAcadAsignado.getItems.add(TutorItem(t.identificacion, t.nombresCompletos))
        }
      case Left(failure) =>
        showError(s"Error al cargar la lista de tutores académicos: $failure")
    }
  }

  private def cargarSolicitudesPendientes(): Unit = {
    RevisionOficiosLogic.listarPendientes() match {
      case Right(lista) =>
        tblSolicitudesEmpresaPropia.getItems.clear()
        tblSolicitudesEmpresaPropia.getItems.addAll(lista: _*)
        lblEstado.setVisible(false)
      case Left(failure) =>
        showError(s"Error al cargar las solicitudes pendientes: $failure")
    }
  }

  private def cargarSiguienteCodigoOficio(): Unit = {
    RevisionOficiosLogic.obtenerSiguienteCodigo() match {
      case Right(codigo) =>
        txtCodigoOficioVuelta.setText(codigo)
      case Left(failure) =>
        showError(s"Error al generar código de oficio: $failure")
    }
  }

  private def mostrarDetalleSolicitud(sel: SolicitudPropiaRevisionDTO): Unit = {
    val visorText =
      s"""Estudiante: ${sel.nombreEstudiante} (CI: ${sel.ciEstudianteRef})
         |Empresa: ${sel.nombreEntidadExterna} (RUC: ${sel.rucEmpresaPropia})
         |Horas Propuestas: ${sel.horasEmpresaPropia}
         |Correo de Contacto: ${sel.contactoEmpresaPropia}
         |Dirección Matriz: ${sel.direccionEmpresaPropia}
         |Misión:
         |${sel.misionEmpresaPropia}
         |Visión:
         |${sel.visionEmpresaPropia}
         |
         |Supervisor Externo:
         |CI: ${sel.ciSupervisorExterno}
         |Nombre: ${sel.nombresSupervisorExterno}
         |Correo: ${sel.emailSupervisorExterno}
         |Teléfono: ${sel.telefonoSupervisorExterno}
         |
         |--------------------------------------------------
         |Oficio Transcrito:
         |${sel.contenidoOficioTranscrito}
         |""".stripMargin
    txtVisorOficioAlumno.setText(visorText)
    txtMotivoDenegacionOficio.clear()
  }

  private def limpiarDetalleSolicitud(): Unit = {
    txtVisorOficioAlumno.setText("[CONTENIDO TRANSCRIPTO DEL ACUERDO PRELIMINAR Y DATOS DE LA EMPRESA EXTERNA]")
    txtCodigoOficioVuelta.clear()
    cmbTutorAcadAsignado.getSelectionModel.clearSelection()
    txtMotivoDenegacionOficio.clear()
  }

  @FXML
  def handleRevisarPDF(event: ActionEvent): Unit = {
    val selected = tblSolicitudesEmpresaPropia.getSelectionModel.getSelectedItem
    if (selected != null) {
      try {
        OfertaRepository.buscarPdfPorId(selected.oficioSolicitudInicialPDF) match {
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

  @FXML
  def handleDescargarPDF(event: ActionEvent): Unit = {
    val selected = tblSolicitudesEmpresaPropia.getSelectionModel.getSelectedItem
    if (selected != null) {
      try {
        OfertaRepository.buscarPdfPorId(selected.oficioSolicitudInicialPDF) match {
          case Some(pdf) =>
            val srcFile = new java.io.File(pdf.rutaSeguraServidor)
            if (srcFile.exists()) {
              val fileChooser = new FileChooser()
              fileChooser.setTitle("Exportar Documento de Solicitud de Oficio")
              fileChooser.setInitialFileName(pdf.nombreOriginal)
              fileChooser.getExtensionFilters.add(new FileChooser.ExtensionFilter("Archivos PDF (*.pdf)", "*.pdf"))

              val dest = fileChooser.showSaveDialog(btnDescargarOficioPDF.getScene.getWindow)
              if (dest != null) {
                java.nio.file.Files.copy(
                  srcFile.toPath,
                  dest.toPath,
                  java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
                showSuccess(s"Documento guardado exitosamente en: ${dest.getName}")
              }
            } else {
              showError("Error: El archivo original no existe en el servidor.")
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

  @FXML
  def handleAprobar(event: ActionEvent): Unit = {
    val selected = tblSolicitudesEmpresaPropia.getSelectionModel.getSelectedItem
    if (selected != null) {
      val tutorSelected = cmbTutorAcadAsignado.getSelectionModel.getSelectedItem
      if (tutorSelected == null) {
        showError("Debe asignar un tutor académico de la universidad.")
        return
      }

      val codigoOficio = txtCodigoOficioVuelta.getText
      if (codigoOficio == null || codigoOficio.trim.isEmpty) {
        showError("El código de oficio de presentación de vuelta es obligatorio.")
        return
      }

      RevisionOficiosLogic.aprobar(selected.idSolicitudPropia, tutorSelected.ci, codigoOficio) match {
        case Right(_) =>
          showSuccess(s"¡Aprobación con éxito! El trámite del estudiante '${selected.nombreEstudiante}' ha sido formalizado y su práctica registrada.")
          cargarSolicitudesPendientes()
          limpiarDetalleSolicitud()
        case Left(RevisionOficiosFailure.Validacion(msg)) =>
          showError(msg)
        case Left(RevisionOficiosFailure.ErrorPersistencia(msg)) =>
          showError(s"Error técnico de persistencia: $msg")
      }
    } else {
      showError("Por favor, seleccione una solicitud de la lista antes de aprobar.")
    }
  }

  @FXML
  def handleRechazar(event: ActionEvent): Unit = {
    val selected = tblSolicitudesEmpresaPropia.getSelectionModel.getSelectedItem
    if (selected != null) {
      val justificacion = txtMotivoDenegacionOficio.getText
      if (justificacion == null || justificacion.trim.isEmpty) {
        showError("La justificación de denegación es obligatoria si rechaza el trámite.")
        return
      }

      RevisionOficiosLogic.rechazar(selected.idSolicitudPropia, justificacion) match {
        case Right(_) =>
          showSuccess(s"El trámite de '${selected.nombreEstudiante}' ha sido rechazado.")
          cargarSolicitudesPendientes()
          limpiarDetalleSolicitud()
        case Left(RevisionOficiosFailure.Validacion(msg)) =>
          showError(msg)
        case Left(RevisionOficiosFailure.ErrorPersistencia(msg)) =>
          showError(s"Error técnico de persistencia: $msg")
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
