package com.ucuenca.gestion.controllers

import javafx.fxml.FXML
import javafx.scene.control._
import javafx.event.ActionEvent
import javafx.beans.property.SimpleStringProperty
import com.ucuenca.gestion.models.entities.{PracticaRegistro, ActividadCronograma}
import com.ucuenca.gestion.models.enums.{OrigenCreacionActividad, EstadoActividad, EstadoCronograma}
import com.ucuenca.gestion.models.logic.{CronogramaLogic, CronogramaFailure}
import com.ucuenca.gestion.utils.SessionManager

class PropuestaActividadesController {

  @FXML var txtNuevaActividadEstudiante: TextField = _
  @FXML var btnRegistrarActividad: Button = _
  @FXML var lblContadorTareasValidadas: Label = _
  @FXML var tblActividadesPlanificacion: TableView[ActividadCronograma] = _
  @FXML var colNumero: TableColumn[ActividadCronograma, String] = _
  @FXML var colDescripcion: TableColumn[ActividadCronograma, String] = _
  @FXML var colEstado: TableColumn[ActividadCronograma, String] = _
  @FXML var colObservaciones: TableColumn[ActividadCronograma, String] = _
  @FXML var colAccionActividad: TableColumn[ActividadCronograma, String] = _
  @FXML var lblEstado: Label = _

  private var activePractica: Option[PracticaRegistro] = None

  @FXML
  def initialize(): Unit = {
    // 1. Configurar fábricas de valores de las celdas
    colNumero.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.numeroSecuencial.toString))
    colDescripcion.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.descripcionTarea))
    colEstado.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.estadoActividad match {
      case EstadoActividad.PENDIENTE => "Pendiente"
      case EstadoActividad.APROBADA  => "Aprobada"
      case EstadoActividad.RECHAZADA => "Rechazada"
      case other                     => other.toString
    }))
    colObservaciones.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.comentarioObservacion.getOrElse("-")))

    // 2. Configurar botón descartar en la columna de acción
    colAccionActividad.setCellFactory(_ => new TableCell[ActividadCronograma, String] {
      private val btnDesc = new Button("Descartar")
      btnDesc.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 9.5pt;")

      override def updateItem(item: String, empty: Boolean): Unit = {
        super.updateItem(item, empty)
        if (empty) {
          setGraphic(null)
          setText(null)
        } else {
          val actividad = getTableView.getItems.get(getIndex)
          if (actividad != null && (actividad.estadoActividad == EstadoActividad.PENDIENTE || actividad.estadoActividad == EstadoActividad.RECHAZADA)) {
            // Verificar si el cronograma permite modificaciones
            val editable = activePractica.exists(pr => pr.estadoCronograma == EstadoCronograma.TUTOR_ACADEMICO_PENDIENTE || pr.estadoCronograma == EstadoCronograma.F1_PENDIENTE)
            btnDesc.setDisable(!editable)
            btnDesc.setOnAction(_ => descartar(actividad.idActividad))
            setGraphic(btnDesc)
            setText(null)
          } else {
            setGraphic(null)
            setText("-")
          }
        }
      }
    })

    // 3. Cargar la práctica y actividades del estudiante autenticado
    cargarDatosEstudiante()
  }

  private def cargarDatosEstudiante(): Unit = {
    SessionManager.getUsuario match {
      case Some(usuario) =>
        CronogramaLogic.buscarPracticaEstudiante(usuario.identificacion) match {
          case Right(Some(pr)) =>
            activePractica = Some(pr)
            
            // Habilitar o deshabilitar edición de actividades según estado
            val editable = pr.estadoCronograma == EstadoCronograma.TUTOR_ACADEMICO_PENDIENTE || pr.estadoCronograma == EstadoCronograma.F1_PENDIENTE
            txtNuevaActividadEstudiante.setDisable(!editable)
            btnRegistrarActividad.setDisable(!editable)

            if (!editable) {
              showSuccess("Cronograma en ejecución o autorizado. Edición de actividades bloqueada.")
            }

            // Cargar actividades
            actualizarTablaYMetricas(pr.idPractica)

          case Right(None) =>
            showError("No registra una práctica preprofesional inicializada o activa en el sistema.")
            txtNuevaActividadEstudiante.setDisable(true)
            btnRegistrarActividad.setDisable(true)
            
          case Left(failure) =>
            showError(s"Error al cargar expediente: $failure")
            txtNuevaActividadEstudiante.setDisable(true)
            btnRegistrarActividad.setDisable(true)
        }
      case None =>
        showError("Sesión inválida o expirada.")
        txtNuevaActividadEstudiante.setDisable(true)
        btnRegistrarActividad.setDisable(true)
    }
  }

  private def actualizarTablaYMetricas(idPractica: Int): Unit = {
    // Cargar actividades en grilla
    CronogramaLogic.listarActividades(idPractica) match {
      case Right(lista) =>
        tblActividadesPlanificacion.getItems.clear()
        tblActividadesPlanificacion.getItems.addAll(lista: _*)
      case Left(failure) =>
        showError(s"Error al cargar cronograma: $failure")
    }

    // Actualizar validador de Formulario 1
    CronogramaLogic.contarAprobadas(idPractica) match {
      case Right(aprobadas) =>
        val valida = aprobadas >= 3 && aprobadas <= 6
        if (valida) {
          lblContadorTareasValidadas.setText(s"Tareas validadas actuales: $aprobadas / Aprobado")
          lblContadorTareasValidadas.setStyle("-fx-text-fill: #059669; -fx-font-weight: bold; -fx-font-size: 11pt;")
        } else {
          lblContadorTareasValidadas.setText(s"Tareas validadas actuales: $aprobadas / Bloqueado")
          lblContadorTareasValidadas.setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold; -fx-font-size: 11pt;")
        }
      case Left(_) =>
        lblContadorTareasValidadas.setText("Error de conteo")
    }
  }

  @FXML
  def handleRegistrar(event: ActionEvent): Unit = {
    activePractica match {
      case Some(pr) =>
        val desc = txtNuevaActividadEstudiante.getText
        CronogramaLogic.proponerActividad(pr.idPractica, desc, OrigenCreacionActividad.ESTUDIANTE) match {
          case Right(_) =>
            txtNuevaActividadEstudiante.clear()
            showSuccess("¡Propuesta de actividad registrada de manera exitosa!")
            actualizarTablaYMetricas(pr.idPractica)
          case Left(CronogramaFailure.Validacion(msg)) =>
            showError(msg)
          case Left(CronogramaFailure.ErrorPersistencia(msg)) =>
            showError(s"Error al registrar actividad: $msg")
        }
      case None =>
        showError("No cuenta con una práctica activa para registrar actividades.")
    }
  }

  private def descartar(idActividad: Int): Unit = {
    activePractica match {
      case Some(pr) =>
        CronogramaLogic.descartarActividad(idActividad, EstadoActividad.DESCARTAR_ESTUDIANTE) match {
          case Right(_) =>
            showSuccess("Actividad descartada lógicamente y archivada en auditoría.")
            actualizarTablaYMetricas(pr.idPractica)
          case Left(CronogramaFailure.Validacion(msg)) =>
            showError(msg)
          case Left(CronogramaFailure.ErrorPersistencia(msg)) =>
            showError(s"Error al descartar la actividad: $msg")
        }
      case None =>
        showError("Sesión de práctica no válida.")
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
