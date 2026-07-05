package com.ucuenca.gestion.controllers

import javafx.fxml.FXML
import javafx.scene.control._
import javafx.scene.layout.VBox
import javafx.event.ActionEvent
import javafx.stage.FileChooser
import java.io.File
import java.nio.file.{Files, Paths, StandardCopyOption}
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
  private var selectedFile: Option[File] = None

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

    // Resolver sesión de tutor académico
    SessionManager.getUsuario match {
      case Some(usuario) =>
        tutorCI = usuario.identificacion
        cargarAlumnosTutorados()
      case None =>
        showError("Sesión inválida o expirada.")
    }
  }

  private def limpiarCampos(): Unit = {
    selectedFile = None
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
        showSuccess("No registra alumnos bajo su tutoría académica.")
      }
    } catch {
      case NonFatal(e) =>
        showError(s"Error al cargar nómina de alumnos: ${e.getMessage}")
    }
  }

  private def evaluarEstadoBloqueoF3(seleccion: F3EstudianteItem): Unit = {
    try {
      // 1. Obtener el estado del último Formulario 2
      val f2Opt = Formulario2DB.buscarUltimaEvaluacionPorPractica(seleccion.idPractica)
      
      f2Opt match {
        case Some(eval) if eval.estaConforme =>
          // Desbloqueado (tutor empresarial ya cargó conformidad)
          panelBloqueoF3.setVisible(false)
          panelEscrituraF3.setVisible(true)
        case _ =>
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
    try {
      val srcFile = new java.io.File("docs/archivos_pdf/formulario_3.pdf")
      if (!srcFile.exists()) {
        showError("Error: No se pudo encontrar el archivo de plantilla original en 'docs/archivos_pdf/formulario_3.pdf'.")
        return
      }

      val fileChooser = new FileChooser()
      fileChooser.setTitle("Guardar Plantilla Formulario 3 (Informe)")
      fileChooser.setInitialFileName("Plantilla_Formulario3_Informe.pdf")
      fileChooser.getExtensionFilters.add(new FileChooser.ExtensionFilter("Archivos PDF (*.pdf)", "*.pdf"))

      val dest = fileChooser.showSaveDialog(btnDescargarPlantillaF3.getScene.getWindow)
      if (dest != null) {
        java.nio.file.Files.copy(
          srcFile.toPath,
          dest.toPath,
          java.nio.file.StandardCopyOption.REPLACE_EXISTING
        )
        showSuccess(s"Plantilla guardada exitosamente en: ${dest.getName}")
      }
    } catch {
      case scala.util.control.NonFatal(e) =>
        showError(s"Error al guardar la plantilla: ${e.getMessage}")
    }
  }

  @FXML
  def handleSubirArchivo(event: ActionEvent): Unit = {
    val fileChooser = new FileChooser()
    fileChooser.setTitle("Seleccionar Informe Académico Firmado")
    fileChooser.getExtensionFilters.add(new FileChooser.ExtensionFilter("Archivos PDF (*.pdf)", "*.pdf"))
    val file = fileChooser.showOpenDialog(btnSubirInformeFirmado.getScene.getWindow)
    if (file != null) {
      selectedFile = Some(file)
      lblEstadoArchivoF3.setText(file.getName)
      lblEstadoArchivoF3.setStyle("-fx-text-fill: #1e293b; -fx-font-style: normal; -fx-font-weight: bold;")
      lblEstado.setVisible(false)
      btnEnviarF3.setDisable(false)
    }
  }

  @FXML
  def handleEnviarF3(event: ActionEvent): Unit = {
    val seleccion = cmbEstudiantesF3.getValue
    if (seleccion == null) {
      showError("Debe seleccionar un estudiante.")
      return
    }

    selectedFile match {
      case Some(file) =>
        try {
          val destFile = new java.io.File("docs/archivos_pdf/" + file.getName)
          val dir = destFile.getParentFile
          if (!dir.exists()) dir.mkdirs()

          java.nio.file.Files.copy(
            file.toPath,
            destFile.toPath,
            java.nio.file.StandardCopyOption.REPLACE_EXISTING
          )

          val bytes = java.nio.file.Files.readAllBytes(destFile.toPath)

          Formulario3Logic.emitirFormulario3(seleccion.idPractica, bytes, file.getName) match {
            case Right(_) =>
              val dbPath = "docs/archivos_pdf/" + file.getName
              DB.localTx { implicit session =>
                sql"""
                  UPDATE archivo_pdf
                  SET ruta_segura_servidor = ${dbPath}
                  WHERE nombre_original = ${file.getName}
                    AND tipo_archivo = 'T3_FORMULARIO_3_INFORME'::tipo_archivo_pdf
                """.update.apply()
              }

              showSuccess("¡Informe final (Formulario 3) emitido y enviado al coordinador exitosamente!")
              limpiarCampos()
              evaluarEstadoBloqueoF3(seleccion)
            case Left(Formulario3Failure.Validacion(msg)) =>
              showError(msg)
            case Left(Formulario3Failure.ErrorPersistencia(msg)) =>
              showError(s"Error de base de datos al guardar informe: $msg")
          }
        } catch {
          case scala.util.control.NonFatal(e) =>
            showError(s"Error al procesar la subida: ${e.getMessage}")
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
