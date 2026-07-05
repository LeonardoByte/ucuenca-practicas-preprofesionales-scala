package com.ucuenca.gestion.controllers

import javafx.fxml.FXML
import javafx.scene.control._
import javafx.scene.layout.VBox
import javafx.event.ActionEvent
import javafx.stage.FileChooser
import java.io.File
import java.nio.file.{Files, Paths, StandardCopyOption}
import scala.util.control.NonFatal
import com.ucuenca.gestion.models.entities.PracticaRegistro
import com.ucuenca.gestion.models.logic.{Formulario1Logic, Formulario1Failure, CronogramaLogic}
import com.ucuenca.gestion.utils.SessionManager
import scalikejdbc._

class Formulario1Controller {

  @FXML var panelBloqueoSecretaria: VBox = _
  @FXML var lblTextoBloqueo: Label = _
  @FXML var panelPermitidoConvenio: VBox = _
  @FXML var lblTextoPermitido: Label = _

  @FXML var btnDescargarPlantillaFormulario: Button = _
  @FXML var btnDescargarPlantillaCarta: Button = _
  @FXML var btnSubirFormularioLlenado: Button = _
  @FXML var lblEstadoArchivoFormulario: Label = _
  @FXML var btnPresentarFormulario1: Button = _
  @FXML var lblEstado: Label = _

  private var activePractica: Option[PracticaRegistro] = None
  private var selectedFile: Option[File] = None

  @FXML
  def initialize(): Unit = {
    cargarExpedienteEstudiante()
  }

  private def cargarExpedienteEstudiante(): Unit = {
    SessionManager.getUsuario match {
      case Some(usuario) =>
        val ci = usuario.identificacion
        
        // Buscar la práctica
        val prOpt = DB.readOnly { implicit session =>
          sql"SELECT * FROM practica_registro WHERE ci_estudiante_ref = ${ci}"
            .map { rs =>
              PracticaRegistro(
                idPractica = rs.int("id_practica"),
                ciEstudianteRef = rs.string("ci_estudiante_ref"),
                rucEmpresaRef = rs.string("ruc_empresa_ref"),
                idTutorAcademicoRef = rs.stringOpt("id_tutor_academico_ref"),
                idTutorEmpresarialRef = rs.string("id_tutor_empresarial_ref"),
                origenRama = com.ucuenca.gestion.models.enums.OrigenRama.valueOf(rs.string("origen_rama")),
                estadoCronograma = com.ucuenca.gestion.models.enums.EstadoCronograma.valueOf(rs.string("estado_cronograma")),
                horasAcumuladas = rs.int("horas_acumuladas"),
                horasTotalesRequeridas = rs.int("horas_totales_requeridas"),
                notaFinal = rs.bigDecimalOpt("nota_final").map(BigDecimal(_))
              )
            }.single.apply()
        }

        prOpt match {
          case Some(pr) =>
            activePractica = Some(pr)
            
            // 1. Verificar Convenio / Carta Compromiso (Control Legal)
            Formulario1Logic.verificarTramitePermitido(pr.idPractica, ci) match {
              case Right(true) =>
                panelPermitidoConvenio.setVisible(true)
                panelBloqueoSecretaria.setVisible(false)
                btnSubirFormularioLlenado.setDisable(false)
              case Right(false) =>
                panelPermitidoConvenio.setVisible(false)
                panelBloqueoSecretaria.setVisible(true)
                btnSubirFormularioLlenado.setDisable(true)
                showError("Trámite bloqueado: La empresa no tiene convenio vigente y no se ha certificado la Carta Compromiso.")
              case Left(err) =>
                showError(s"Error al verificar controles legales: $err")
                btnSubirFormularioLlenado.setDisable(true)
            }

            // 2. Verificar Rango de Actividades Aprobadas (3-6)
            val countRes = DB.readOnly { implicit session =>
              sql"SELECT COUNT(1) FROM actividad_cronograma WHERE id_practica_ref = ${pr.idPractica} AND estado_actividad = 'APROBADA'::estado_actividad"
                .map(rs => rs.int(1)).single.apply().getOrElse(0)
            }
            if (countRes < 3 || countRes > 6) {
              btnSubirFormularioLlenado.setDisable(true)
              showError(s"Trámite bloqueado: Requiere entre 3 y 6 actividades aprobadas en el cronograma. Actual: $countRes")
            }

            // 3. Revisar si hay un expediente previo y si fue rechazado
            Formulario1Logic.buscarExpediente(pr.idPractica) match {
              case Right(Some(exp)) =>
                if (exp.justificacionRechazoInicio.isDefined) {
                  showError(s"OBSERVACIÓN DE RECHAZO PREVIO DEL COORDINADOR:\n${exp.justificacionRechazoInicio.get}\n(Por favor corrija su plan y vuelva a presentar)")
                }
              case _ => // No expediente
            }

          case None =>
            showError("No registra una práctica inicializada en el sistema.")
            deshabilitarTodo()
        }

      case None =>
        showError("Sesión inválida o expirada.")
        deshabilitarTodo()
    }
  }

  private def deshabilitarTodo(): Unit = {
    btnSubirFormularioLlenado.setDisable(true)
    btnPresentarFormulario1.setDisable(true)
    btnDescargarPlantillaFormulario.setDisable(true)
    btnDescargarPlantillaCarta.setDisable(true)
  }

  @FXML
  def handleDescargarPlantillaForm(event: ActionEvent): Unit = {
    try {
      val srcFile = new java.io.File("docs/archivos_pdf/formulario_1.pdf")
      if (!srcFile.exists()) {
        showError("Error: No se pudo encontrar el archivo de plantilla original en 'docs/archivos_pdf/formulario_1.pdf'.")
        return
      }

      val fileChooser = new FileChooser()
      fileChooser.setTitle("Guardar Plantilla Formulario 1")
      fileChooser.setInitialFileName("Plantilla_Formulario1.pdf")
      fileChooser.getExtensionFilters.add(new FileChooser.ExtensionFilter("Archivos PDF (*.pdf)", "*.pdf"))

      val dest = fileChooser.showSaveDialog(btnDescargarPlantillaFormulario.getScene.getWindow)
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
        showError(s"Error al descargar la plantilla: ${e.getMessage}")
    }
  }

  @FXML
  def handleDescargarPlantillaCarta(event: ActionEvent): Unit = {
    try {
      val srcFile = new java.io.File("docs/archivos_pdf/carta_compromiso.pdf")
      if (!srcFile.exists()) {
        showError("Error: No se pudo encontrar el archivo de plantilla original en 'docs/archivos_pdf/carta_compromiso.pdf'.")
        return
      }

      val fileChooser = new FileChooser()
      fileChooser.setTitle("Guardar Plantilla Carta Compromiso")
      fileChooser.setInitialFileName("Formato_CartaCompromiso.pdf")
      fileChooser.getExtensionFilters.add(new FileChooser.ExtensionFilter("Archivos PDF (*.pdf)", "*.pdf"))

      val dest = fileChooser.showSaveDialog(btnDescargarPlantillaCarta.getScene.getWindow)
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
        showError(s"Error al descargar la plantilla: ${e.getMessage}")
    }
  }

  @FXML
  def handleSubirFormulario(event: ActionEvent): Unit = {
    val fileChooser = new FileChooser()
    fileChooser.setTitle("Seleccionar Formulario 1 Firmado (PDF)")
    fileChooser.getExtensionFilters.add(new FileChooser.ExtensionFilter("Archivos PDF (*.pdf)", "*.pdf"))
    val file = fileChooser.showOpenDialog(btnSubirFormularioLlenado.getScene.getWindow)
    if (file != null) {
      selectedFile = Some(file)
      lblEstadoArchivoFormulario.setText(file.getName)
      lblEstadoArchivoFormulario.setStyle("-fx-text-fill: #1e293b; -fx-font-style: normal;")
      btnPresentarFormulario1.setDisable(false)
    }
  }

  @FXML
  def handlePresentar(event: ActionEvent): Unit = {
    (activePractica, selectedFile) match {
      case (Some(pr), Some(file)) =>
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

          Formulario1Logic.presentarFormulario1(pr.idPractica, bytes, file.getName) match {
            case Right(_) =>
              val dbPath = "docs/archivos_pdf/" + file.getName
              DB.localTx { implicit session =>
                sql"""
                  UPDATE archivo_pdf
                  SET ruta_segura_servidor = ${dbPath}
                  WHERE nombre_original = ${file.getName}
                    AND tipo_archivo = 'T1_FORMULARIO_1_PLAN'::tipo_archivo_pdf
                """.update.apply()
              }

              showSuccess("¡Formulario 1 presentado y enviado exitosamente al circuito de firmas!")
              btnPresentarFormulario1.setDisable(true)
              selectedFile = None
              lblEstadoArchivoFormulario.setText("Presentado")
            case Left(Formulario1Failure.Validacion(msg)) =>
              showError(msg)
            case Left(Formulario1Failure.ErrorPersistencia(msg)) =>
              showError(s"Error de base de datos: $msg")
          }
        } catch {
          case scala.util.control.NonFatal(e) =>
            showError(s"Error al procesar la subida: ${e.getMessage}")
        }
      case _ =>
        showError("Debe seleccionar un archivo PDF firmado antes de presentar.")
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
