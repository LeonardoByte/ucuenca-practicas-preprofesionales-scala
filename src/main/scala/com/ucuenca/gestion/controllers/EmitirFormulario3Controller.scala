package com.ucuenca.gestion.controllers

import javafx.fxml.FXML
import javafx.scene.control._
import javafx.scene.layout.VBox
import javafx.event.ActionEvent
import javafx.stage.FileChooser
import java.io.File
import java.nio.file.Files
import com.ucuenca.gestion.models.db.{CronogramaRepository, Formulario2DB, Formulario3Repository}
import com.ucuenca.gestion.models.enums.EstadoFormulario2
import com.ucuenca.gestion.models.logic.{Formulario3Logic, Formulario3Failure}
import com.ucuenca.gestion.utils.SessionManager
import scalikejdbc._
import scala.util.control.NonFatal

case class F3EstudianteItem(idPractica: Int, nombre: String) {
  override def toString: String = nombre
}

class EmitirFormulario3Controller {

  @FXML var cmbEstudiantesF3: ComboBox[F3EstudianteItem] = _
  @FXML var panelBloqueoF3: VBox = _
  @FXML var panelEscrituraF3: VBox = _

  @FXML var btnDescargarPlantillaF3: Button = _
  @FXML var btnSubirInformeFirmado: Button = _
  @FXML var lblEstadoArchivoF3: Label = _
  @FXML var btnEnviarF3: Button = _
  @FXML var lblEstado: Label = _

  private var tutorCI: String = _
  private var selectedFileBytes: Option[Array[Byte]] = None
  private var selectedFileName: Option[String] = None

  @FXML
  def initialize(): Unit = {
    // Configurar estado bloqueado por defecto
    panelBloqueoF3.setVisible(true)
    panelEscrituraF3.setVisible(false)
    lblEstado.setVisible(false)
    btnEnviarF3.setDisable(true)

    cmbEstudiantesF3.setOnAction(_ => {
      val seleccion = cmbEstudiantesF3.getValue
      if (seleccion != null) {
        limpiarCampos()
        evaluarEstadoBloqueoF3(seleccion)
      } else {
        panelBloqueoF3.setVisible(true)
        panelEscrituraF3.setVisible(false)
      }
    })

    SessionManager.getUsuario match {
      case Some(usuario) =>
        tutorCI = usuario.identificacion
        cargarAlumnosTutorados()
      case None =>
        showError("Sesión inválida o expirada.")
    }
  }

  private def limpiarCampos(): Unit = {
    selectedFileBytes = None
    selectedFileName = None
    lblEstadoArchivoF3.setText("Ningún archivo seleccionado (.pdf)")
    lblEstadoArchivoF3.setStyle("-fx-text-fill: #64748b; -fx-font-style: italic;")
    lblEstado.setVisible(false)
    btnEnviarF3.setDisable(true)
  }

  private def cargarAlumnosTutorados(): Unit = {
    try {
      val tutorados = CronogramaRepository.listarEstudiantesTutorados(tutorCI)
      cmbEstudiantesF3.getItems.clear()
      tutorados.foreach { dto =>
        cmbEstudiantesF3.getItems.add(F3EstudianteItem(dto.idPractica, dto.nombreEstudiante))
      }
      if (tutorados.isEmpty) {
        showSuccess("No registra alumnos tutorados asignados.")
      }
    } catch {
      case NonFatal(e) =>
        showError(s"Error al cargar estudiantes tutorados: ${e.getMessage}")
    }
  }

  private def evaluarEstadoBloqueoF3(seleccion: F3EstudianteItem): Unit = {
    try {
      // 1. Verificar si ya existe Formulario 3
      val f3Opt = Formulario3Repository.buscarFormulario3PorPractica(seleccion.idPractica)
      if (f3Opt.isDefined) {
        panelBloqueoF3.setVisible(true)
        panelEscrituraF3.setVisible(false)
        val lblTexto = panelBloqueoF3.getChildren.get(0).asInstanceOf[Label]
        val lblDetalle = panelBloqueoF3.getChildren.get(1).asInstanceOf[Label]
        lblTexto.setText("🔒 INFORME ACADÉMICO YA EMITIDO")
        lblDetalle.setText("El Formulario 3 de informe final ya ha sido emitido y guardado exitosamente para el estudiante seleccionado. Puede verificar el expediente de cierre en el panel del coordinador.")
        return
      }

      // 2. Verificar el estado del Formulario 2 (debe ser CONFORME)
      val f2Opt = Formulario2DB.buscarUltimaEvaluacionPorPractica(seleccion.idPractica)
      f2Opt match {
        case Some(eval) if eval.estadoFormulario2 == EstadoFormulario2.CONFORME =>
          // Desbloquear
          panelBloqueoF3.setVisible(false)
          panelEscrituraF3.setVisible(true)
        case Some(eval) =>
          // Bloqueado
          panelBloqueoF3.setVisible(true)
          panelEscrituraF3.setVisible(false)
          val lblTexto = panelBloqueoF3.getChildren.get(0).asInstanceOf[Label]
          val lblDetalle = panelBloqueoF3.getChildren.get(1).asInstanceOf[Label]
          lblTexto.setText("🔒 FORMULARIO BLOQUEADO EN CASCADA")
          lblDetalle.setText(s"El informe técnico permanece bloqueado. El Formulario 2 del estudiante se encuentra en estado '${eval.estadoFormulario2.toString}' y debe ser aprobado ('CONFORME') por el tutor académico en la subpantalla de inspección antes de continuar.")
        case None =>
          // Bloqueado (tutor empresarial no subió)
          panelBloqueoF3.setVisible(true)
          panelEscrituraF3.setVisible(false)
          val lblTexto = panelBloqueoF3.getChildren.get(0).asInstanceOf[Label]
          val lblDetalle = panelBloqueoF3.getChildren.get(1).asInstanceOf[Label]
          lblTexto.setText("🔒 FORMULARIO BLOQUEADO EN CASCADA")
          lblDetalle.setText("El informe técnico permanece bloqueado. La empresa asociada no ha registrado ni transmitido aún la rúbrica de desempeño (Formulario 2) de este practicante.")
      }
    } catch {
      case NonFatal(e) =>
        showError(s"Error al evaluar restricciones: ${e.getMessage}")
    }
  }

  @FXML
  def handleDescargarPlantilla(event: ActionEvent): Unit = {
    val fileChooser = new FileChooser()
    fileChooser.setTitle("Guardar Plantilla Formulario 3 (Informe)")
    fileChooser.setInitialFileName("Plantilla_Formulario3_Informe.pdf")
    fileChooser.getExtensionFilters.add(new FileChooser.ExtensionFilter("Archivos PDF (*.pdf)", "*.pdf"))
    val file = fileChooser.showSaveDialog(btnDescargarPlantillaF3.getScene.getWindow)
    if (file != null) {
      try {
        Files.write(file.toPath, Array[Byte](0x25, 0x50, 0x44, 0x46, 0x2d, 0x31, 0x2e, 0x34, 0x0a)) // %PDF-1.4
        showSuccess("Plantilla del Formulario 3 descargada con éxito.")
      } catch {
        case NonFatal(e) =>
          showError(s"Error al guardar la plantilla: ${e.getMessage}")
      }
    }
  }

  @FXML
  def handleSubirArchivo(event: ActionEvent): Unit = {
    val fileChooser = new FileChooser()
    fileChooser.setTitle("Seleccionar Informe Académico Firmado")
    fileChooser.getExtensionFilters.add(new FileChooser.ExtensionFilter("Archivos PDF (*.pdf)", "*.pdf"))
    val file = fileChooser.showOpenDialog(btnSubirInformeFirmado.getScene.getWindow)
    if (file != null) {
      try {
        selectedFileBytes = Some(Files.readAllBytes(file.toPath))
        selectedFileName = Some(file.getName)
        lblEstadoArchivoF3.setText(file.getName)
        lblEstadoArchivoF3.setStyle("-fx-text-fill: #1e293b; -fx-font-style: normal; -fx-font-weight: bold;")
        lblEstado.setVisible(false)
        btnEnviarF3.setDisable(false)
      } catch {
        case NonFatal(e) =>
          showError(s"Error al leer el archivo seleccionado: ${e.getMessage}")
      }
    }
  }

  @FXML
  def handleEnviarF3(event: ActionEvent): Unit = {
    val seleccion = cmbEstudiantesF3.getValue
    if (seleccion == null) {
      showError("Debe seleccionar un estudiante.")
      return
    }

    (selectedFileBytes, selectedFileName) match {
      case (Some(bytes), Some(name)) =>
        Formulario3Logic.emitirFormulario3(seleccion.idPractica, bytes, name) match {
          case Right(_) =>
            showSuccess("¡Informe final (Formulario 3) emitido y enviado al coordinador exitosamente!")
            limpiarCampos()
            evaluarEstadoBloqueoF3(seleccion)
          case Left(Formulario3Failure.Validacion(msg)) =>
            showError(msg)
          case Left(Formulario3Failure.ErrorPersistencia(msg)) =>
            showError(s"Error de base de datos al guardar informe: $msg")
        }
      case _ =>
        showError("Debe seleccionar e importar el archivo PDF firmado del Formulario 3 antes de proceder.")
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
