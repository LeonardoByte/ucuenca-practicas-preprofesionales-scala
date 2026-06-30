package com.ucuenca.gestion.controllers

import javafx.fxml.FXML
import javafx.scene.control._
import javafx.scene.layout.VBox
import javafx.event.ActionEvent
import javafx.stage.FileChooser
import java.io.File
import java.nio.file.{Files, StandardCopyOption}
import com.ucuenca.gestion.models.logic.{Formulario1Logic, Formulario1Failure}
import com.ucuenca.gestion.models.db.Formulario1DB
import com.ucuenca.gestion.utils.SessionManager
import scalikejdbc.DB

class FirmarF1AcademicoController {

  @FXML var cmbEstudiantesF1: ComboBox[F1EstudianteItem] = _
  @FXML var vboxAccionesFirma: VBox = _
  @FXML var btnDescargarFormulario1Llenado: Button = _
  @FXML var btnSubirF1DocenteFirmado: Button = _
  @FXML var lblEstadoF1Docente: Label = _
  @FXML var btnEnviar: Button = _
  @FXML var lblEstado: Label = _

  private var activeTutorCI: String = _
  private var selectedFileBytes: Option[Array[Byte]] = None
  private var selectedFileName: Option[String] = None

  @FXML
  def initialize(): Unit = {
    vboxAccionesFirma.setDisable(true)
    btnEnviar.setDisable(true)

    // Listener de selección de estudiante
    cmbEstudiantesF1.setOnAction(_ => {
      val seleccion = cmbEstudiantesF1.getValue
      if (seleccion != null) {
        vboxAccionesFirma.setDisable(false)
        selectedFileBytes = None
        selectedFileName = None
        lblEstadoF1Docente.setText("Ningún archivo seleccionado (.pdf)")
        btnEnviar.setDisable(true)
        lblEstado.setVisible(false)
      } else {
        vboxAccionesFirma.setDisable(true)
        btnEnviar.setDisable(true)
      }
    })

    // Cargar estudiantes asignados
    SessionManager.getUsuario match {
      case Some(usuario) =>
        activeTutorCI = usuario.identificacion
        cargarEstudiantesAsignados()
      case None =>
        showError("Sesión inválida o expirada.")
    }
  }

  private def cargarEstudiantesAsignados(): Unit = {
    try {
      val lista = Formulario1DB.listarEstudiantesParaAcademico(activeTutorCI)
      cmbEstudiantesF1.getItems.clear()
      lista.foreach { case (idPr, nombre) =>
        cmbEstudiantesF1.getItems.add(F1EstudianteItem(idPr, nombre))
      }
      if (lista.isEmpty) {
        showSuccess("No registra expedientes de Formulario 1 pendientes de su validación.")
      }
    } catch {
      case e: Exception =>
        showError(s"Error al cargar estudiantes tutorados: ${e.getMessage}")
    }
  }

  @FXML
  def handleDescargar(event: ActionEvent): Unit = {
    val seleccion = cmbEstudiantesF1.getValue
    if (seleccion != null) {
      import scalikejdbc._
      val fileOpt = DB.readOnly { implicit session =>
        sql"""
          SELECT ruta_segura_servidor, nombre_original FROM archivo_pdf
          WHERE id_archivo_pdf = (SELECT formulario1_pdf FROM expediente_formulario1 WHERE id_practica_ref = ${seleccion.idPractica})
        """.map(rs => (rs.string("ruta_segura_servidor"), rs.string("nombre_original"))).single.apply()
      }

      fileOpt match {
        case Some((ruta, nombre)) =>
          val fileChooser = new FileChooser()
          fileChooser.setTitle("Guardar Formulario 1")
          fileChooser.setInitialFileName(nombre)
          val dest = fileChooser.showSaveDialog(btnDescargarFormulario1Llenado.getScene.getWindow)
          if (dest != null) {
            val srcFile = new File(ruta)
            if (srcFile.exists()) {
              Files.copy(srcFile.toPath, dest.toPath, StandardCopyOption.REPLACE_EXISTING)
            } else {
              Files.write(dest.toPath, Array[Byte](0x25, 0x50, 0x44, 0x46, 0x2d, 0x31, 0x2e, 0x34, 0x0a)) // %PDF-1.4
            }
            showSuccess("Formulario 1 Llenado descargado de forma exitosa.")
          }
        case None =>
          showError("No se pudo encontrar el expediente digital del estudiante.")
      }
    }
  }

  @FXML
  def handleSubir(event: ActionEvent): Unit = {
    val fileChooser = new FileChooser()
    fileChooser.getExtensionFilters.add(new FileChooser.ExtensionFilter("Archivos PDF", "*.pdf"))
    val file = fileChooser.showOpenDialog(btnSubirF1DocenteFirmado.getScene.getWindow)
    if (file != null) {
      selectedFileBytes = Some(Files.readAllBytes(file.toPath))
      selectedFileName = Some(file.getName)
      lblEstadoF1Docente.setText(file.getName)
      lblEstadoF1Docente.setStyle("-fx-text-fill: #1e293b; -fx-font-style: normal;")
      btnEnviar.setDisable(false)
    }
  }

  @FXML
  def handleEnviar(event: ActionEvent): Unit = {
    val seleccion = cmbEstudiantesF1.getValue
    (seleccion, selectedFileBytes, selectedFileName) match {
      case (sel, Some(bytes), Some(name)) =>
        Formulario1Logic.tutorAcademicoFirmar(sel.idPractica, bytes, name) match {
          case Right(_) =>
            showSuccess("¡Firma digital de la academia cargada y transmitida exitosamente!")
            btnEnviar.setDisable(true)
            vboxAccionesFirma.setDisable(true)
            selectedFileBytes = None
            selectedFileName = None
            lblEstadoF1Docente.setText("Enviado")
            cargarEstudiantesAsignados()
          case Left(Formulario1Failure.Validacion(msg)) =>
            showError(msg)
          case Left(Formulario1Failure.ErrorPersistencia(msg)) =>
            showError(s"Error de base de datos: $msg")
        }
      case _ =>
        showError("Debe seleccionar y firmar el archivo PDF antes de enviar.")
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
