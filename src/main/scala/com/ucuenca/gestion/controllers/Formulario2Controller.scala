package com.ucuenca.gestion.controllers

import javafx.fxml.FXML
import javafx.scene.control._
import javafx.scene.layout.VBox
import javafx.event.ActionEvent
import javafx.stage.FileChooser
import java.io.File
import java.nio.file.Files
import com.ucuenca.gestion.models.entities.PracticaRegistro
import com.ucuenca.gestion.models.db.{CronogramaRepository, Formulario2DB}
import com.ucuenca.gestion.models.logic.{Formulario2Logic, Formulario2Failure}
import com.ucuenca.gestion.models.enums.EstadoCronograma
import com.ucuenca.gestion.utils.SessionManager
import scalikejdbc._
import scala.util.control.NonFatal

case class F2EstudianteItem(idPractica: Int, nombre: String) {
  override def toString: String = nombre
}

class Formulario2Controller {

  @FXML var cmbEstudiantesF2: ComboBox[F2EstudianteItem] = _
  @FXML var panelBloqueoF2: VBox = _
  @FXML var panelCargaF2: VBox = _

  @FXML var btnDescargarPlantillaF2: Button = _
  @FXML var btnSubirEvaluacionLlena: Button = _
  @FXML var lblEstadoArchivoF2: Label = _
  @FXML var txtRetroalimentacionDocenteF2: TextArea = _
  @FXML var btnTransmitirF2: Button = _
  @FXML var lblEstado: Label = _

  private var tutorCI: String = _
  private var selectedFileBytes: Option[Array[Byte]] = None
  private var selectedFileName: Option[String] = None

  @FXML
  def initialize(): Unit = {
    // Estado inicial bloqueado
    panelBloqueoF2.setVisible(true)
    panelCargaF2.setVisible(false)
    lblEstado.setVisible(false)

    // Listener para el ComboBox de selección de estudiante
    cmbEstudiantesF2.setOnAction(_ => {
      val seleccion = cmbEstudiantesF2.getValue
      if (seleccion != null) {
        limpiarCampos()
        evaluarRestriccionesEstudiante(seleccion)
      } else {
        panelBloqueoF2.setVisible(true)
        panelCargaF2.setVisible(false)
      }
    })

    // Cargar sesión del Tutor Empresarial
    SessionManager.getUsuario match {
      case Some(usuario) =>
        tutorCI = usuario.identificacion
        cargarEstudiantesAsignados()
      case None =>
        showError("Sesión inválida o expirada.")
    }
  }

  private def limpiarCampos(): Unit = {
    selectedFileBytes = None
    selectedFileName = None
    lblEstadoArchivoF2.setText("Ningún archivo seleccionado (.pdf)")
    lblEstadoArchivoF2.setStyle("-fx-text-fill: #64748b; -fx-font-style: italic;")
    lblEstado.setVisible(false)
  }

  private def cargarEstudiantesAsignados(): Unit = {
    try {
      val estudiantes = CronogramaRepository.listarEstudiantesAsignados(tutorCI)
      cmbEstudiantesF2.getItems.clear()
      estudiantes.foreach { case (idPr, nombre) =>
        cmbEstudiantesF2.getItems.add(F2EstudianteItem(idPr, nombre))
      }
      if (estudiantes.isEmpty) {
        showSuccess("No registra practicantes asignados para supervisión.")
      }
    } catch {
      case NonFatal(e) =>
        showError(s"Error al listar estudiantes: ${e.getMessage}")
    }
  }

  private def evaluarRestriccionesEstudiante(seleccion: F2EstudianteItem): Unit = {
    try {
      val prOpt = DB.readOnly { implicit session =>
        sql"""
          SELECT id_practica, ci_estudiante_ref, ruc_empresa_ref, id_tutor_academico_ref, 
                 id_tutor_empresarial_ref, origen_rama, estado_cronograma, horas_acumuladas, 
                 horas_totales_requeridas, nota_final
          FROM practica_registro
          WHERE id_practica = ${seleccion.idPractica}
        """.map { rs =>
          PracticaRegistro(
            idPractica = rs.int("id_practica"),
            ciEstudianteRef = rs.string("ci_estudiante_ref"),
            rucEmpresaRef = rs.string("ruc_empresa_ref"),
            idTutorAcademicoRef = rs.stringOpt("id_tutor_academico_ref"),
            idTutorEmpresarialRef = rs.string("id_tutor_empresarial_ref"),
            origenRama = com.ucuenca.gestion.models.enums.OrigenRama.valueOf(rs.string("origen_rama")),
            estadoCronograma = EstadoCronograma.valueOf(rs.string("estado_cronograma")),
            horasAcumuladas = rs.int("horas_acumuladas"),
            horasTotalesRequeridas = rs.int("horas_totales_requeridas"),
            notaFinal = rs.bigDecimalOpt("nota_final").map(BigDecimal(_))
          )
        }.single.apply()
      }

      prOpt match {
        case Some(pr) =>
          if (pr.estadoCronograma == EstadoCronograma.F2_F3_PENDIENTE) {
            // Estudiante solicitó la evaluación -> Desbloqueamos el panel
            panelBloqueoF2.setVisible(false)
            panelCargaF2.setVisible(true)

            // Mostrar observaciones de rechazos previos si los hubiera
            val ultimaEvalOpt = Formulario2DB.buscarUltimaEvaluacionPorPractica(pr.idPractica)
            ultimaEvalOpt match {
              case Some(eval) if eval.estaRechazado =>
                val obs = eval.justificacionRechazoDocente.getOrElse("Rechazado sin justificación.")
                txtRetroalimentacionDocenteF2.setText(s"[RECHAZADO] Observación del Tutor Académico:\n$obs")
                txtRetroalimentacionDocenteF2.setStyle("-fx-text-fill: #dc2626; -fx-font-family: 'Courier New';")
              case _ =>
                txtRetroalimentacionDocenteF2.setText("[Ninguna observación de rechazo registrada. De acuerdo a las reglas del sistema, si el tutor académico no da la conformidad, la evaluación se anula y requerirá que repita la carga desde cero con un nuevo archivo PDF.]")
                txtRetroalimentacionDocenteF2.setStyle("-fx-text-fill: #64748b; -fx-font-family: 'Courier New';")
            }
          } else {
            // Resto de estados -> Bloqueado
            panelBloqueoF2.setVisible(true)
            panelCargaF2.setVisible(false)
          }
        case None =>
          showError("No se encuentra el registro de la práctica seleccionada.")
      }
    } catch {
      case NonFatal(e) =>
        showError(s"Error al evaluar estado de la práctica: ${e.getMessage}")
    }
  }

  @FXML
  def handleDescargarPlantilla(event: ActionEvent): Unit = {
    val fileChooser = new FileChooser()
    fileChooser.setTitle("Descargar Plantilla Formulario 2 (Rúbrica)")
    fileChooser.setInitialFileName("Plantilla_Formulario2_Rubrica.pdf")
    fileChooser.getExtensionFilters.add(new FileChooser.ExtensionFilter("Archivos PDF (*.pdf)", "*.pdf"))
    val file = fileChooser.showSaveDialog(btnDescargarPlantillaF2.getScene.getWindow)
    if (file != null) {
      try {
        Files.write(file.toPath, Array[Byte](0x25, 0x50, 0x44, 0x46, 0x2d, 0x31, 0x2e, 0x34, 0x0a)) // %PDF-1.4
        showSuccess("Plantilla vacía del Formulario 2 descargada con éxito.")
      } catch {
        case NonFatal(e) =>
          showError(s"Error al descargar la plantilla: ${e.getMessage}")
      }
    }
  }

  @FXML
  def handleSubirArchivo(event: ActionEvent): Unit = {
    val fileChooser = new FileChooser()
    fileChooser.setTitle("Seleccionar Rúbrica Técnica Completa")
    fileChooser.getExtensionFilters.add(new FileChooser.ExtensionFilter("Archivos PDF (*.pdf)", "*.pdf"))
    val file = fileChooser.showOpenDialog(btnSubirEvaluacionLlena.getScene.getWindow)
    if (file != null) {
      try {
        selectedFileBytes = Some(Files.readAllBytes(file.toPath))
        selectedFileName = Some(file.getName)
        lblEstadoArchivoF2.setText(file.getName)
        lblEstadoArchivoF2.setStyle("-fx-text-fill: #1e293b; -fx-font-style: normal; -fx-font-weight: bold;")
        lblEstado.setVisible(false)
      } catch {
        case NonFatal(e) =>
          showError(s"Error al leer el archivo seleccionado: ${e.getMessage}")
      }
    }
  }

  @FXML
  def handleTransmitirF2(event: ActionEvent): Unit = {
    val seleccion = cmbEstudiantesF2.getValue
    if (seleccion == null) {
      showError("Debe seleccionar un estudiante de la lista.")
      return
    }

    (selectedFileBytes, selectedFileName) match {
      case (Some(bytes), Some(name)) =>
        // Generar un contenido de rubrica indexado con metadatos descriptivos
        val rubricaIndexada = s"Rubrica de Evaluacion Tecnica del Desempeño. Practicante: ${seleccion.nombre}. Archivo: $name. Registrado por Tutor Empresarial CI: $tutorCI."
        
        Formulario2Logic.registrarFormulario2(seleccion.idPractica, bytes, name, rubricaIndexada) match {
          case Right(_) =>
            limpiarCampos()
            lblEstado.setStyle("-fx-text-fill: green;")
            lblEstado.setText("Formulario 2 enviado exitosamente")
            lblEstado.setVisible(true)
            // Refrescar el estado de la vista para el estudiante actual (volverá a bloquearse si la práctica sigue procesándose)
            evaluarRestriccionesEstudiante(seleccion)
          case Left(Formulario2Failure.Validacion(msg)) =>
            showError(msg)
          case Left(Formulario2Failure.ErrorPersistencia(msg)) =>
            showError(s"Error al guardar rúbrica en base de datos: $msg")
        }
      case _ =>
        showError("Debe seleccionar y cargar el archivo PDF de evaluación técnica antes de transmitir.")
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
