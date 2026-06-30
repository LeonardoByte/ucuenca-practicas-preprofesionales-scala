package com.ucuenca.gestion.controllers

import javafx.fxml.FXML
import javafx.scene.control._
import javafx.event.ActionEvent
import javafx.beans.property.SimpleStringProperty
import javafx.scene.layout.VBox
import javafx.scene.layout.HBox
import com.ucuenca.gestion.models.entities.{PracticaRegistro, ActividadCronograma}
import com.ucuenca.gestion.models.enums.{OrigenCreacionActividad, EstadoActividad, EstadoCronograma}
import com.ucuenca.gestion.models.logic.{CronogramaLogic, CronogramaFailure}
import com.ucuenca.gestion.utils.SessionManager

case class EstudianteAsignadoItem(idPractica: Int, nombre: String) {
  override def toString: String = nombre
}

class PlanificarActividadesController {

  @FXML var cmbEstudianteSeleccionado: ComboBox[EstudianteAsignadoItem] = _
  @FXML var vboxEdicionActividades: VBox = _
  @FXML var txtNuevaActividadTutorEmp: TextField = _
  @FXML var btnRegistrarActividad: Button = _
  @FXML var hboxRestriccionF1: HBox = _
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
    // 1. Configurar fábricas de valores de las columnas
    colNumero.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.numeroSecuencial.toString))
    colDescripcion.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.descripcionTarea))
    colEstado.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.estadoActividad match {
      case EstadoActividad.PENDIENTE => "Pendiente"
      case EstadoActividad.APROBADA  => "Aprobada"
      case EstadoActividad.RECHAZADA => "Rechazada"
      case other                     => other.toString
    }))
    colObservaciones.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.comentarioObservacion.getOrElse("-")))

    // 2. Configurar botón de descarte lógico
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

    // 3. Listener para el selector de estudiantes
    cmbEstudianteSeleccionado.setOnAction(_ => {
      val seleccion = cmbEstudianteSeleccionado.getValue
      if (seleccion != null) {
        cargarDetallesEstudiante(seleccion.idPractica)
      } else {
        limpiarVistas()
      }
    })

    // 4. Deshabilitar controles de edición por defecto
    deshabilitarControlesEdicion()

    // 5. Cargar nómina de estudiantes del tutor
    cargarNominaEstudiantes()
  }

  private def deshabilitarControlesEdicion(): Unit = {
    txtNuevaActividadTutorEmp.setDisable(true)
    btnRegistrarActividad.setDisable(true)
    tblActividadesPlanificacion.getItems.clear()
    lblContadorTareasValidadas.setText("Seleccione un estudiante de la nómina")
    lblContadorTareasValidadas.setStyle("-fx-text-fill: #64748b; -fx-font-style: italic; -fx-font-size: 11pt;")
  }

  private def limpiarVistas(): Unit = {
    activePractica = None
    deshabilitarControlesEdicion()
    lblEstado.setVisible(false)
  }

  private def cargarNominaEstudiantes(): Unit = {
    SessionManager.getUsuario match {
      case Some(usuario) =>
        CronogramaLogic.listarEstudiantesTutor(usuario.identificacion) match {
          case Right(lista) =>
            cmbEstudianteSeleccionado.getItems.clear()
            lista.foreach { case (idPr, nombre) =>
              cmbEstudianteSeleccionado.getItems.add(EstudianteAsignadoItem(idPr, nombre))
            }
            if (lista.isEmpty) {
              showError("No registra estudiantes asignados bajo tutela académica en el sistema.")
            }
          case Left(failure) =>
            showError(s"Error al cargar nómina de estudiantes: $failure")
        }
      case None =>
        showError("Sesión inválida o expirada.")
    }
  }

  private def cargarDetallesEstudiante(idPractica: Int): Unit = {
    // Buscar los detalles de la práctica para verificar estado
    // Reutilizamos buscar por ID de base (o listamos)
    // Para simplificar, buscamos la práctica de la base directamente
    import scalikejdbc._
    val prOpt = DB.readOnly { implicit session =>
      sql"SELECT * FROM practica_registro WHERE id_practica = ${idPractica}"
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
        val editable = pr.estadoCronograma == EstadoCronograma.TUTOR_ACADEMICO_PENDIENTE || pr.estadoCronograma == EstadoCronograma.F1_PENDIENTE
        
        txtNuevaActividadTutorEmp.setDisable(!editable)
        btnRegistrarActividad.setDisable(!editable)

        if (editable) {
          lblEstado.setVisible(false)
        } else {
          showSuccess("Cronograma autorizado o en ejecución. Edición de actividades bloqueada.")
        }

        // Cargar tabla y validación F1
        actualizarTablaYMetricas(idPractica)

      case None =>
        showError("Error al cargar la información de la práctica seleccionada.")
        deshabilitarControlesEdicion()
    }
  }

  private def actualizarTablaYMetricas(idPractica: Int): Unit = {
    // Listar actividades
    CronogramaLogic.listarActividades(idPractica) match {
      case Right(lista) =>
        tblActividadesPlanificacion.getItems.clear()
        tblActividadesPlanificacion.getItems.addAll(lista: _*)
      case Left(failure) =>
        showError(s"Error al cargar cronograma: $failure")
    }

    // Contar aprobadas
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
        val desc = txtNuevaActividadTutorEmp.getText
        CronogramaLogic.proponerActividad(pr.idPractica, desc, OrigenCreacionActividad.TUTOR_EMPRESARIAL) match {
          case Right(_) =>
            txtNuevaActividadTutorEmp.clear()
            showSuccess("¡Propuesta de actividad registrada de manera exitosa!")
            actualizarTablaYMetricas(pr.idPractica)
          case Left(CronogramaFailure.Validacion(msg)) =>
            showError(msg)
          case Left(CronogramaFailure.ErrorPersistencia(msg)) =>
            showError(s"Error al registrar actividad: $msg")
        }
      case None =>
        showError("Debe seleccionar un estudiante de la lista antes de proponer actividades.")
    }
  }

  private def descartar(idActividad: Int): Unit = {
    activePractica match {
      case Some(pr) =>
        CronogramaLogic.descartarActividad(idActividad, EstadoActividad.DESCARTAR_TUTOR) match {
          case Right(_) =>
            showSuccess("Actividad descartada lógicamente por el tutor.")
            actualizarTablaYMetricas(pr.idPractica)
          case Left(CronogramaFailure.Validacion(msg)) =>
            showError(msg)
          case Left(CronogramaFailure.ErrorPersistencia(msg)) =>
            showError(s"Error al descartar la actividad: $msg")
        }
      case None =>
        showError("Práctica no válida.")
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
