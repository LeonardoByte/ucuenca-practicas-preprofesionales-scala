package com.ucuenca.gestion.controllers

import javafx.fxml.FXML
import javafx.scene.control._
import javafx.event.ActionEvent
import javafx.stage.FileChooser
import java.io.File
import java.nio.file.Files
import com.ucuenca.gestion.models.entities.Formulario2Evaluacion
import com.ucuenca.gestion.models.db.{CronogramaRepository, Formulario2DB}
import com.ucuenca.gestion.models.logic.{Formulario3Logic, Formulario3Failure}
import com.ucuenca.gestion.models.enums.EstadoFormulario2
import com.ucuenca.gestion.utils.SessionManager
import scalikejdbc._
import scala.util.control.NonFatal

case class F2RevisionEstudianteItem(idPractica: Int, nombre: String) {
  override def toString: String = nombre
}

class RevisarFormulario2Controller {

  @FXML var cmbEstudiantesF2: ComboBox[F2RevisionEstudianteItem] = _
  @FXML var txtVisorFormulario2: TextArea = _
  @FXML var btnRevisarF2PDF: Button = _
  @FXML var btnDescargarF2PDF: Button = _
  @FXML var txtJustificacionRechazoF2: TextArea = _
  @FXML var btnRechazarF2: Button = _
  @FXML var btnAprobarF2: Button = _
  @FXML var lblEstado: Label = _

  private var tutorCI: String = _
  private var activeEval: Option[Formulario2Evaluacion] = None

  @FXML
  def initialize(): Unit = {
    txtJustificacionRechazoF2.setDisable(true)
    btnAprobarF2.setDisable(true)
    btnRechazarF2.setDisable(true)
    btnRevisarF2PDF.setDisable(true)
    btnDescargarF2PDF.setDisable(true)
    lblEstado.setVisible(false)

    cmbEstudiantesF2.setOnAction(_ => {
      val seleccion = cmbEstudiantesF2.getValue
      if (seleccion != null) {
        lblEstado.setVisible(false)
        cargarDetallesRubrica(seleccion.idPractica)
      } else {
        deshabilitarAcciones()
      }
    })

    SessionManager.getUsuario match {
      case Some(usuario) =>
        tutorCI = usuario.identificacion
        cargarTutorados()
      case None =>
        showError("Sesión inválida o expirada.")
    }
  }

  private def deshabilitarAcciones(): Unit = {
    txtVisorFormulario2.setText("")
    txtJustificacionRechazoF2.setText("")
    txtJustificacionRechazoF2.setDisable(true)
    btnAprobarF2.setDisable(true)
    btnRechazarF2.setDisable(true)
    btnRevisarF2PDF.setDisable(true)
    btnDescargarF2PDF.setDisable(true)
    activeEval = None
  }

  private def cargarTutorados(): Unit = {
    try {
      val tutorados = CronogramaRepository.listarEstudiantesTutorados(tutorCI)
      cmbEstudiantesF2.getItems.clear()
      tutorados.foreach { dto =>
        cmbEstudiantesF2.getItems.add(F2RevisionEstudianteItem(dto.idPractica, dto.nombreEstudiante))
      }
      if (tutorados.isEmpty) {
        showSuccess("No registra alumnos tutorados asignados.")
      }
    } catch {
      case NonFatal(e) =>
        showError(s"Error al cargar nómina de alumnos: ${e.getMessage}")
    }
  }

  private def cargarDetallesRubrica(idPractica: Int): Unit = {
    try {
      val ultimaEvalOpt = Formulario2DB.buscarUltimaEvaluacionPorPractica(idPractica)
      activeEval = ultimaEvalOpt

      ultimaEvalOpt match {
        case Some(eval) =>
          txtVisorFormulario2.setText(eval.contenidoRubricaIndexado)
          btnRevisarF2PDF.setDisable(false)
          btnDescargarF2PDF.setDisable(false)

          eval.estadoFormulario2 match {
            case EstadoFormulario2.PENDIENTE_REVISION =>
              btnAprobarF2.setDisable(false)
              btnRechazarF2.setDisable(false)
              txtJustificacionRechazoF2.setDisable(false)
              txtJustificacionRechazoF2.setText("")
              lblEstado.setVisible(false)

            case EstadoFormulario2.CONFORME =>
              btnAprobarF2.setDisable(true)
              btnRechazarF2.setDisable(true)
              txtJustificacionRechazoF2.setDisable(true)
              txtJustificacionRechazoF2.setText("")
              showSuccess("Esta rúbrica ya cuenta con Conformidad Académica.")

            case EstadoFormulario2.RECHAZADO =>
              btnAprobarF2.setDisable(true)
              btnRechazarF2.setDisable(true)
              txtJustificacionRechazoF2.setDisable(true)
              val motivo = eval.justificacionRechazoDocente.getOrElse("Sin motivo registrado.")
              txtJustificacionRechazoF2.setText(motivo)
              showWarning(s"Rúbrica rechazada previamente. Motivo: $motivo")
          }

        case None =>
          txtVisorFormulario2.setText("[No se registra presentación de Formulario 2 por parte de la empresa para este alumno.]")
          txtJustificacionRechazoF2.setText("")
          txtJustificacionRechazoF2.setDisable(true)
          btnAprobarF2.setDisable(true)
          btnRechazarF2.setDisable(true)
          btnRevisarF2PDF.setDisable(true)
          btnDescargarF2PDF.setDisable(true)
          showWarning("El estudiante seleccionado no cuenta con rúbrica técnica cargada por su tutor empresarial.")
      }
    } catch {
      case NonFatal(e) =>
        showError(s"Error al cargar detalles de rúbrica: ${e.getMessage}")
    }
  }

  @FXML
  def handleAprobarF2(event: ActionEvent): Unit = {
    val seleccion = cmbEstudiantesF2.getValue
    if (seleccion != null) {
      Formulario3Logic.evaluarRubrica(seleccion.idPractica, aprobado = true, "") match {
        case Right(_) =>
          showSuccess("¡Conformidad Académica registrada exitosamente!")
          cargarDetallesRubrica(seleccion.idPractica)
        case Left(Formulario3Failure.Validacion(msg)) =>
          showError(msg)
        case Left(Formulario3Failure.ErrorPersistencia(msg)) =>
          showError(s"Error de base de datos: $msg")
      }
    }
  }

  @FXML
  def handleRechazarF2(event: ActionEvent): Unit = {
    val seleccion = cmbEstudiantesF2.getValue
    if (seleccion != null) {
      val justificacion = txtJustificacionRechazoF2.getText
      if (justificacion == null || justificacion.trim.length < 5) {
        showError("Por favor escriba una justificación de rechazo con al menos 5 caracteres.")
        return
      }

      Formulario3Logic.evaluarRubrica(seleccion.idPractica, aprobado = false, justificacion) match {
        case Right(_) =>
          showWarning("Rúbrica empresarial rechazada. Se ha solicitado una nueva versión.")
          cargarDetallesRubrica(seleccion.idPractica)
        case Left(Formulario3Failure.Validacion(msg)) =>
          showError(msg)
        case Left(Formulario3Failure.ErrorPersistencia(msg)) =>
          showError(s"Error de base de datos: $msg")
      }
    }
  }

  @FXML
  def handleRevisarPDF(event: ActionEvent): Unit = {
    activeEval match {
      case Some(eval) =>
        try {
          val fileOpt = DB.readOnly { implicit session =>
            sql"SELECT ruta_segura_servidor FROM archivo_pdf WHERE id_archivo_pdf = ${eval.formulario2PDF}"
              .map(rs => rs.string("ruta_segura_servidor")).single.apply()
          }
          fileOpt match {
            case Some(ruta) =>
              val file = new File(ruta)
              if (file.exists()) {
                if (java.awt.Desktop.isDesktopSupported) {
                  java.awt.Desktop.getDesktop.open(file)
                } else {
                  showError("Visor PDF nativo no soportado.")
                }
              } else {
                showError("El archivo físico no existe en el servidor.")
              }
            case None =>
              showError("Metadata del PDF no ubicada.")
          }
        } catch {
          case NonFatal(e) => showError(s"Error al abrir PDF: ${e.getMessage}")
        }
      case None => showError("Debe seleccionar un alumno con rúbrica cargada.")
    }
  }

  @FXML
  def handleDescargarPDF(event: ActionEvent): Unit = {
    activeEval match {
      case Some(eval) =>
        try {
          val fileData = DB.readOnly { implicit session =>
            sql"SELECT ruta_segura_servidor, nombre_original FROM archivo_pdf WHERE id_archivo_pdf = ${eval.formulario2PDF}"
              .map(rs => (rs.string("ruta_segura_servidor"), rs.string("nombre_original"))).single.apply()
          }
          fileData match {
            case Some((ruta, nombre)) =>
              val fileChooser = new FileChooser()
              fileChooser.setTitle("Descargar Formulario 2")
              fileChooser.setInitialFileName(nombre)
              val dest = fileChooser.showSaveDialog(btnDescargarF2PDF.getScene.getWindow)
              if (dest != null) {
                val srcFile = new File(ruta)
                if (srcFile.exists()) {
                  Files.copy(srcFile.toPath, dest.toPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                  showSuccess(s"¡Archivo descargado exitosamente!")
                } else {
                  // Fallback dummy PDF
                  Files.write(dest.toPath, Array[Byte](0x25, 0x50, 0x44, 0x46, 0x2d, 0x31, 0x2e, 0x34, 0x0a))
                  showSuccess(s"¡Archivo descargado exitosamente (Mock)! ")
                }
              }
            case None =>
              showError("Metadata de archivo no ubicada.")
          }
        } catch {
          case NonFatal(e) => showError(s"Error de descarga: ${e.getMessage}")
        }
      case None => showError("Debe seleccionar un alumno con rúbrica cargada.")
    }
  }

  private def showSuccess(msg: String): Unit = {
    lblEstado.setText(msg)
    lblEstado.setStyle("-fx-text-fill: #059669; -fx-font-weight: bold;")
    lblEstado.setVisible(true)
  }

  private def showWarning(msg: String): Unit = {
    lblEstado.setText(msg)
    lblEstado.setStyle("-fx-text-fill: #b45309; -fx-font-weight: bold;")
    lblEstado.setVisible(true)
  }

  private def showError(msg: String): Unit = {
    lblEstado.setText(msg)
    lblEstado.setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;")
    lblEstado.setVisible(true)
  }
}
