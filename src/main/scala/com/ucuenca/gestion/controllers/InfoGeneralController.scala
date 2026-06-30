package com.ucuenca.gestion.controllers

import javafx.fxml.FXML
import javafx.scene.control._
import javafx.event.ActionEvent
import com.ucuenca.gestion.models.entities._
import com.ucuenca.gestion.models.enums._
import com.ucuenca.gestion.models.db.{CronogramaRepository, Formulario1DB}
import com.ucuenca.gestion.models.logic.{Formulario2Logic, Formulario2Failure}
import com.ucuenca.gestion.utils.SessionManager
import scalikejdbc._
import java.time.format.DateTimeFormatter
import scala.util.control.NonFatal

class InfoGeneralController {

  @FXML var lblHorasAcumuladas: Label = _
  @FXML var progressHoras: ProgressBar = _
  @FXML var lblFechaInicio: Label = _
  @FXML var lblFechaFin: Label = _
  @FXML var lblCantActividades: Label = _
  @FXML var lblEmpresaActiva: Label = _
  @FXML var lblConvenioActivo: Label = _
  @FXML var lblTutorAcademicoActivo: Label = _
  @FXML var lblTutorEmpresarialActivo: Label = _

  @FXML var btnSolicitarEvaluacionFinal: Button = _
  @FXML var lblEstado: Label = _

  private var activePractica: Option[PracticaRegistro] = None
  private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

  @FXML
  def initialize(): Unit = {
    btnSolicitarEvaluacionFinal.setDisable(true)
    lblEstado.setVisible(false)

    SessionManager.getUsuario match {
      case Some(usuario) =>
        cargarDatosPractica(usuario.identificacion)
      case None =>
        showError("Sesión de usuario inválida. Por favor reingrese.")
    }
  }

  private def cargarDatosPractica(estudianteCi: String): Unit = {
    com.ucuenca.gestion.models.logic.InfoGeneralLogic.obtenerDetallesPractica(estudianteCi) match {
      case Right(details) =>
        val pr = details.practica
        activePractica = Some(pr)
        
        // 1. Mostrar Horas y Progreso
        lblHorasAcumuladas.setText(s"${pr.horasAcumuladas} / ${pr.horasTotalesRequeridas}")
        val pct = if (pr.horasTotalesRequeridas > 0) pr.horasAcumuladas.toDouble / pr.horasTotalesRequeridas.toDouble else 0.0
        progressHoras.setProgress(pct)

        // 2. Cargar fecha de inicio
        details.fechaInicioOpt match {
          case Some(fecha) =>
            lblFechaInicio.setText(fecha.format(dateFormatter))
            lblFechaFin.setText(fecha.plusMonths(3).format(dateFormatter))
          case None =>
            lblFechaInicio.setText("No autorizada")
            lblFechaFin.setText("Sujeta a desarrollo")
        }

        // 3. Cantidad de Actividades Aprobadas
        lblCantActividades.setText(s"${details.cantActividadesAprobadas} Aprobadas")

        // 4. Cargar Empresa y Convenio
        lblEmpresaActiva.setText(details.nombreEmpresa)
        val convText = if (details.convenioNombre == "FORMALIZADO") {
          "Convenio de Cooperación Interinstitucional Formalizado (Vigente)"
        } else {
          "Sin Convenio Formalizado (Certificación de Carta de Compromiso obligatoria)"
        }
        lblConvenioActivo.setText(convText)

        // 5. Tutors Info
        lblTutorAcademicoActivo.setText(details.tutorAcademicoNombre)
        lblTutorEmpresarialActivo.setText(details.tutorEmpresarialNombre)

        // 6. Conditional locking and inline feedback
        pr.estadoCronograma match {
          case EstadoCronograma.F2_F3_PENDIENTE =>
            btnSolicitarEvaluacionFinal.setDisable(true)
            showWarning("Petición de evaluación final enviada. Esperando rúbrica del tutor empresarial.")
          case EstadoCronograma.EVALUADA | EstadoCronograma.CERRADA_VALIDA =>
            btnSolicitarEvaluacionFinal.setDisable(true)
            showSuccess("La práctica ha sido evaluada y/o cerrada exitosamente.")
          case EstadoCronograma.EN_DESARROLLO =>
            if (pr.horasAcumuladas >= pr.horasTotalesRequeridas) {
              btnSolicitarEvaluacionFinal.setDisable(false)
              lblEstado.setText("¡Horas completadas! Ya puede solicitar su evaluación técnica de desempeño.")
              lblEstado.setStyle("-fx-text-fill: #2563eb; -fx-font-weight: bold;")
              lblEstado.setVisible(true)
            } else {
              btnSolicitarEvaluacionFinal.setDisable(true)
              val faltan = pr.horasTotalesRequeridas - pr.horasAcumuladas
              lblEstado.setText(s"En desarrollo. Faltan acumular $faltan horas para habilitar la solicitud de cierre.")
              lblEstado.setStyle("-fx-text-fill: #475569; -fx-font-weight: bold;")
              lblEstado.setVisible(true)
            }
          case _ =>
            btnSolicitarEvaluacionFinal.setDisable(true)
            lblEstado.setText("La práctica está en fase de inicio o planificación.")
            lblEstado.setStyle("-fx-text-fill: #64748b; -fx-font-style: italic;")
            lblEstado.setVisible(true)
        }

      case Left(com.ucuenca.gestion.models.logic.InfoGeneralFailure.Validacion(msg)) =>
        limpiarCamposPorDefecto()
        showError(msg)
      case Left(com.ucuenca.gestion.models.logic.InfoGeneralFailure.ErrorPersistencia(msg)) =>
        limpiarCamposPorDefecto()
        showError(s"Error al consultar el expediente: $msg")
    }
  }

  @FXML
  def handleSolicitarEvaluacion(event: ActionEvent): Unit = {
    activePractica match {
      case Some(pr) =>
        Formulario2Logic.solicitarEvaluacionFinal(pr.idPractica) match {
          case Right(_) =>
            showSuccess("¡Petición de evaluación final enviada exitosamente al tutor empresarial!")
            btnSolicitarEvaluacionFinal.setDisable(true)
            SessionManager.getUsuario.foreach(u => cargarDatosPractica(u.identificacion))
          case Left(Formulario2Failure.Validacion(msg)) =>
            showError(msg)
          case Left(Formulario2Failure.ErrorPersistencia(msg)) =>
            showError(s"Error de base de datos: $msg")
        }
      case None =>
        showError("Operación no permitida: No registra práctica activa.")
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

  private def limpiarCamposPorDefecto(): Unit = {
    activePractica = None

    // 1. Resetear contadores y barras de progreso
    lblHorasAcumuladas.setText("0 / 0")
    progressHoras.setProgress(0.0)

    // 2. Vaciar las fechas del cronograma
    lblFechaInicio.setText("--/--/----")
    lblFechaFin.setText("--/--/----")

    // 3. Vaciar actividades
    lblCantActividades.setText("0 Aprobadas")

    // 4. Limpiar sección del acuerdo y tutores
    lblEmpresaActiva.setText("No registrada")
    lblConvenioActivo.setText("No asignado")
    lblTutorAcademicoActivo.setText("Tutor académico no asignado")
    lblTutorEmpresarialActivo.setText("Tutor empresarial no asignado")

    // 5. Bloquear el botón de acción
    btnSolicitarEvaluacionFinal.setDisable(true)
  }
}
