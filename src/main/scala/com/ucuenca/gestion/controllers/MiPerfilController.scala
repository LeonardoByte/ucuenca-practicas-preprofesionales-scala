package com.ucuenca.gestion.controllers

import javafx.fxml.FXML
import javafx.scene.control._
import javafx.event.ActionEvent
import com.ucuenca.gestion.models.entities._
import com.ucuenca.gestion.models.enums._
import com.ucuenca.gestion.models.logic.{EstudiantePerfilLogic, PerfilFailure}
import com.ucuenca.gestion.utils.SessionManager
import scala.util.control.NonFatal

class MiPerfilController {

  // --- Requisitos ---
  @FXML var lblCiclo: Label = _
  @FXML var lblRestriccion: Label = _
  @FXML var lblMatricula: Label = _

  // --- Indicadores ---
  @FXML var lblOfertasDisponibles: Label = _
  @FXML var lblEstadoPractica: Label = _
  @FXML var lblNotificacion: Label = _

  // --- Currículum Vitae (CV) ---
  @FXML var btnSubirCV: Button = _
  @FXML var lblEstadoCV: Label = _
  @FXML var btnRevisarCV: Button = _
  @FXML var btnDescargarCV: Button = _

  // --- Malla Curricular ---
  @FXML var btnSubirMalla: Button = _
  @FXML var lblEstadoMalla: Label = _
  @FXML var btnRevisarMalla: Button = _
  @FXML var btnDescargarMalla: Button = _

  private var estudianteId: String = _
  private var cvPdfEntity: Option[ArchivoPDF] = None
  private var mallaPdfEntity: Option[ArchivoPDF] = None

  /**
   * Inicializa la vista cargando la sesión y el perfil.
   */
  @FXML
  def initialize(): Unit = {
    SessionManager.getUsuario match {
      case Some(usuario) =>
        estudianteId = usuario.identificacion
        cargarDatosPerfil()
      case None =>
        showError("Sesión inválida. Por favor, reinicie el sistema.")
    }
  }

  /**
   * Carga el perfil del estudiante desde la capa lógica y refresca los componentes de la interfaz.
   */
  private def cargarDatosPerfil(): Unit = {
    EstudiantePerfilLogic.obtenerPerfilCompleto(estudianteId) match {
      case Right((usuario, perfil, mallaOpt, cvOpt)) =>
        mallaPdfEntity = mallaOpt
        cvPdfEntity = cvOpt

        // 1. Mostrar ciclo y matrícula
        lblCiclo.setText(s"Ciclo ${perfil.cicloActual}")
        lblMatricula.setText(perfil.estadoMatricula.toString)

        // 2. Mostrar aptitud de ciclo
        if (perfil.cicloActual >= 6) {
          lblRestriccion.setText("CUMPLIDO (Apto)")
          lblRestriccion.setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold;")
        } else {
          lblRestriccion.setText(s"NO APTO (Requiere Ciclo >= 6)")
          lblRestriccion.setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;")
        }

        // 3. Estado de la Práctica e Indicador de Ofertas
        lblEstadoPractica.setText(perfil.estadoEstudiantePractica.toString)
        if (EstudiantePerfilLogic.validarBloqueoPostulacion(perfil.estadoEstudiantePractica)) {
          lblOfertasDisponibles.setText("0")
          lblOfertasDisponibles.setStyle("-fx-font-size: 24pt; -fx-font-weight: bold; -fx-text-fill: #dc2626;")
          lblEstadoPractica.setStyle("-fx-font-size: 20pt; -fx-font-weight: bold; -fx-text-fill: #ea580c;") // Naranja oscuro (Orange 600)
        } else {
          lblOfertasDisponibles.setText("12")
          lblOfertasDisponibles.setStyle("-fx-font-size: 24pt; -fx-font-weight: bold; -fx-text-fill: #3b82f6;")
          lblEstadoPractica.setStyle("-fx-font-size: 20pt; -fx-font-weight: bold; -fx-text-fill: #10b981;") // Verde (Emerald 500)
        }

        // 4. Estado CV
        cvOpt match {
          case Some(cv) =>
            lblEstadoCV.setText(s"CV cargado: ${cv.nombreOriginal}")
            lblEstadoCV.setStyle("-fx-text-fill: #10b981; -fx-font-style: italic;")
            btnRevisarCV.setDisable(false)
            btnDescargarCV.setDisable(false)
          case None =>
            lblEstadoCV.setText("Ningún currículum cargado.")
            lblEstadoCV.setStyle("-fx-text-fill: #64748b; -fx-font-style: italic;")
            btnRevisarCV.setDisable(true)
            btnDescargarCV.setDisable(true)
        }

        // 5. Estado Malla
        mallaOpt match {
          case Some(malla) =>
            lblEstadoMalla.setText(s"Malla cargada: ${malla.nombreOriginal}")
            lblEstadoMalla.setStyle("-fx-text-fill: #10b981; -fx-font-style: italic;")
            btnRevisarMalla.setDisable(false)
            btnDescargarMalla.setDisable(false)
          case None =>
            lblEstadoMalla.setText("Ninguna malla cargada.")
            lblEstadoMalla.setStyle("-fx-text-fill: #64748b; -fx-font-style: italic;")
            btnRevisarMalla.setDisable(true)
            btnDescargarMalla.setDisable(true)
        }

      case Left(PerfilFailure.Validacion(msg)) =>
        showError(msg)
      case Left(PerfilFailure.ErrorPersistencia(msg)) =>
        showError(s"Error técnico de persistencia: $msg")
    }
  }

  /**
   * Abre FileChooser para cargar el PDF de Malla Académica.
   */
  @FXML
  def handleSubirMalla(event: ActionEvent): Unit = {
    subirDocumento(esMalla = true)
  }

  /**
   * Abre FileChooser para cargar el PDF del CV.
   */
  @FXML
  def handleSubirCV(event: ActionEvent): Unit = {
    subirDocumento(esMalla = false)
  }

  /**
   * Helper común para subir analíticos o currículums.
   */
  private def subirDocumento(esMalla: Boolean): Unit = {
    val fileChooser = new javafx.stage.FileChooser()
    val titulo = if (esMalla) "Subir Malla Académica (PDF)" else "Subir Curriculum Vitae (PDF)"
    fileChooser.setTitle(titulo)
    fileChooser.getExtensionFilters.add(new javafx.stage.FileChooser.ExtensionFilter("Archivos PDF (*.pdf)", "*.pdf"))
    
    val selected = fileChooser.showOpenDialog(btnSubirCV.getScene.getWindow)
    if (selected != null) {
      val prefijo = if (esMalla) "malla" else "cv"
      val safeName = s"${prefijo}_${estudianteId}.pdf"
      val safePath = copyFileToUploads(selected, safeName)

      EstudiantePerfilLogic.cargarDocumento(estudianteId, selected.getName, safePath, esMalla) match {
        case Right(_) =>
          showSuccess("¡Documento cargado y consolidado con éxito!")
          cargarDatosPerfil()
        case Left(PerfilFailure.Validacion(msg)) =>
          showError(msg)
        case Left(PerfilFailure.ErrorPersistencia(msg)) =>
          showError(s"Error al registrar archivo: $msg")
      }
    }
  }

  /**
   * Abre nativamente el visor PDF del CV.
   */
  @FXML
  def handleRevisarCV(event: ActionEvent): Unit = {
    revisarPdf(cvPdfEntity)
  }

  /**
   * Abre nativamente el visor PDF de la Malla.
   */
  @FXML
  def handleRevisarMalla(event: ActionEvent): Unit = {
    revisarPdf(mallaPdfEntity)
  }

  private def revisarPdf(pdfOpt: Option[ArchivoPDF]): Unit = {
    pdfOpt match {
      case Some(pdf) =>
        try {
          val file = new java.io.File(pdf.rutaSeguraServidor)
          if (file.exists()) {
            if (java.awt.Desktop.isDesktopSupported) {
              java.awt.Desktop.getDesktop.open(file)
            } else {
              showError("El visor PDF nativo no es soportado en este sistema operativo.")
            }
          } else {
            showError("El archivo no se encuentra físicamente en el servidor de carga.")
          }
        } catch {
          case NonFatal(e) =>
            showError(s"Error al abrir el visor de PDF nativo: ${e.getMessage}")
        }
      case None =>
        showError("Debe cargar primero el documento.")
    }
  }

  /**
   * Descarga el CV a una ruta seleccionada por el estudiante.
   */
  @FXML
  def handleDescargarCV(event: ActionEvent): Unit = {
    descargarPdf(cvPdfEntity)
  }

  /**
   * Descarga la Malla a una ruta seleccionada por el estudiante.
   */
  @FXML
  def handleDescargarMalla(event: ActionEvent): Unit = {
    descargarPdf(mallaPdfEntity)
  }

  private def descargarPdf(pdfOpt: Option[ArchivoPDF]): Unit = {
    pdfOpt match {
      case Some(pdf) =>
        try {
          val srcFile = new java.io.File(pdf.rutaSeguraServidor)
          if (!srcFile.exists()) {
            showError("El archivo de origen no existe.")
            return
          }

          val fileChooser = new javafx.stage.FileChooser()
          fileChooser.setTitle("Descargar Archivo PDF")
          fileChooser.setInitialFileName(pdf.nombreOriginal)
          fileChooser.getExtensionFilters.add(new javafx.stage.FileChooser.ExtensionFilter("Archivos PDF (*.pdf)", "*.pdf"))
          
          val dest = fileChooser.showSaveDialog(btnDescargarCV.getScene.getWindow)
          if (dest != null) {
            java.nio.file.Files.copy(
              srcFile.toPath,
              dest.toPath,
              java.nio.file.StandardCopyOption.REPLACE_EXISTING
            )
            showSuccess(s"¡Archivo '${pdf.nombreOriginal}' descargado exitosamente!")
          }
        } catch {
          case NonFatal(e) =>
            showError(s"Error de descarga: ${e.getMessage}")
        }
      case None =>
        showError("Debe cargar primero el documento.")
    }
  }

  // --- Auxiliares ---

  private def copyFileToUploads(source: java.io.File, destName: String): String = {
    val uploadsDir = new java.io.File("uploads")
    if (!uploadsDir.exists()) {
      uploadsDir.mkdirs()
    }
    val destFile = new java.io.File(uploadsDir, destName)
    java.nio.file.Files.copy(
      source.toPath,
      destFile.toPath,
      java.nio.file.StandardCopyOption.REPLACE_EXISTING
    )
    destFile.getAbsolutePath
  }

  private def showSuccess(msg: String): Unit = {
    lblNotificacion.setText(msg)
    lblNotificacion.setStyle("-fx-text-fill: #059669; -fx-font-weight: bold;")
    lblNotificacion.setVisible(true)
  }

  private def showError(msg: String): Unit = {
    lblNotificacion.setText(msg)
    lblNotificacion.setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;")
    lblNotificacion.setVisible(true)
  }
}
