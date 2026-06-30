package com.ucuenca.gestion.controllers

import javafx.fxml.FXML
import javafx.scene.control._
import javafx.scene.layout.VBox
import javafx.event.ActionEvent
import javafx.stage.FileChooser
import java.io.File
import java.nio.file.Files
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
  private var selectedFileBytes: Option[Array[Byte]] = None
  private var selectedFileName: Option[String] = None

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
    val fileChooser = new FileChooser()
    fileChooser.setTitle("Guardar Plantilla Formulario 1")
    fileChooser.setInitialFileName("Plantilla_Formulario1.pdf")
    val file = fileChooser.showSaveDialog(btnDescargarPlantillaFormulario.getScene.getWindow)
    if (file != null) {
      Files.write(file.toPath, Array[Byte](0x25, 0x50, 0x44, 0x46, 0x2d, 0x31, 0x2e, 0x34, 0x0a)) // %PDF-1.4
      showSuccess("Plantilla de Formulario 1 descargada con éxito.")
    }
  }

  @FXML
  def handleDescargarPlantillaCarta(event: ActionEvent): Unit = {
    val fileChooser = new FileChooser()
    fileChooser.setTitle("Guardar Plantilla Carta Compromiso")
    fileChooser.setInitialFileName("Formato_CartaCompromiso.pdf")
    val file = fileChooser.showSaveDialog(btnDescargarPlantillaCarta.getScene.getWindow)
    if (file != null) {
      Files.write(file.toPath, Array[Byte](0x25, 0x50, 0x44, 0x46, 0x2d, 0x31, 0x2e, 0x34, 0x0a)) // %PDF-1.4
      showSuccess("Plantilla de Carta de Compromiso descargada con éxito.")
    }
  }

  @FXML
  def handleSubirFormulario(event: ActionEvent): Unit = {
    val fileChooser = new FileChooser()
    fileChooser.getExtensionFilters.add(new FileChooser.ExtensionFilter("Archivos PDF", "*.pdf"))
    val file = fileChooser.showOpenDialog(btnSubirFormularioLlenado.getScene.getWindow)
    if (file != null) {
      selectedFileBytes = Some(Files.readAllBytes(file.toPath))
      selectedFileName = Some(file.getName)
      lblEstadoArchivoFormulario.setText(file.getName)
      lblEstadoArchivoFormulario.setStyle("-fx-text-fill: #1e293b; -fx-font-style: normal;")
      btnPresentarFormulario1.setDisable(false)
    }
  }

  @FXML
  def handlePresentar(event: ActionEvent): Unit = {
    (activePractica, selectedFileBytes, selectedFileName) match {
      case (Some(pr), Some(bytes), Some(name)) =>
        Formulario1Logic.presentarFormulario1(pr.idPractica, bytes, name) match {
          case Right(_) =>
            showSuccess("¡Formulario 1 presentado y enviado exitosamente al circuito de firmas!")
            btnPresentarFormulario1.setDisable(true)
            selectedFileBytes = None
            selectedFileName = None
            lblEstadoArchivoFormulario.setText("Presentado")
          case Left(Formulario1Failure.Validacion(msg)) =>
            showError(msg)
          case Left(Formulario1Failure.ErrorPersistencia(msg)) =>
            showError(s"Error de base de datos: $msg")
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
