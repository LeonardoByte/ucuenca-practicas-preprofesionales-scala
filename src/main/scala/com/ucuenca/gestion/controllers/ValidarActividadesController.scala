package com.ucuenca.gestion.controllers

import javafx.fxml.FXML
import javafx.scene.control._
import javafx.beans.property.SimpleStringProperty
import javafx.event.ActionEvent
import com.ucuenca.gestion.models.dto.StudentTutoradoDTO
import com.ucuenca.gestion.models.entities.ActividadCronograma
import com.ucuenca.gestion.models.enums.{EstadoActividad, OrigenCreacionActividad}
import com.ucuenca.gestion.models.logic.{ValidacionActividadesLogic, ValidacionActividadesFailure}
import com.ucuenca.gestion.utils.SessionManager

class ValidarActividadesController {

  @FXML var tblEstudiantesTutorados: TableView[StudentTutoradoDTO] = _
  @FXML var colEstudiante: TableColumn[StudentTutoradoDTO, String] = _
  @FXML var colEmpresa: TableColumn[StudentTutoradoDTO, String] = _
  @FXML var colCiclo: TableColumn[StudentTutoradoDTO, String] = _
  @FXML var colPendientesCount: TableColumn[StudentTutoradoDTO, String] = _

  @FXML var tblValidarActividadesDocente: TableView[ActividadCronograma] = _
  @FXML var colDescripcionAct: TableColumn[ActividadCronograma, String] = _
  @FXML var colOrigenAct: TableColumn[ActividadCronograma, String] = _

  @FXML var txtCorreccionMetodologica: TextArea = _
  @FXML var btnRechazar: Button = _
  @FXML var btnAprobar: Button = _
  @FXML var lblEstado: Label = _

  private var currentTutorCI: String = _

  @FXML
  def initialize(): Unit = {
    // 1. Configurar columnas de estudiantes
    colEstudiante.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.nombreEstudiante))
    colEmpresa.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.nombreEmpresa))
    colCiclo.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.cicloActual.toString))
    colPendientesCount.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.pendientesCount.toString))

    // 2. Configurar columnas de actividades
    colDescripcionAct.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.descripcionTarea))
    colOrigenAct.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.origenCreacion match {
      case OrigenCreacionActividad.ESTUDIANTE       => "Estudiante"
      case OrigenCreacionActividad.TUTOR_EMPRESARIAL => "Tutor Empresarial"
      case other                                    => other.toString
    }))

    // 3. Configurar listeners de selección
    tblEstudiantesTutorados.getSelectionModel.selectedItemProperty().addListener((_, _, seleccion) => {
      if (seleccion != null) {
        cargarActividadesPendientes(seleccion.idPractica)
      } else {
        limpiarDetallesActividades()
      }
    })

    tblValidarActividadesDocente.getSelectionModel.selectedItemProperty().addListener((_, _, seleccion) => {
      val seleccionable = seleccion != null
      btnAprobar.setDisable(!seleccionable)
      btnRechazar.setDisable(!seleccionable)
      txtCorreccionMetodologica.setDisable(!seleccionable)
      if (!seleccionable) {
        txtCorreccionMetodologica.clear()
      }
    })

    // Deshabilitar controles de edición por defecto
    btnAprobar.setDisable(true)
    btnRechazar.setDisable(true)
    txtCorreccionMetodologica.setDisable(true)

    // 4. Cargar lista de estudiantes bajo tutoría
    SessionManager.getUsuario match {
      case Some(usuario) =>
        currentTutorCI = usuario.identificacion
        cargarListaEstudiantes()
      case None =>
        showError("Sesión inválida o expirada.")
    }
  }

  private def cargarListaEstudiantes(): Unit = {
    ValidacionActividadesLogic.listarEstudiantes(currentTutorCI) match {
      case Right(lista) =>
        tblEstudiantesTutorados.getItems.clear()
        tblEstudiantesTutorados.getItems.addAll(lista: _*)
        if (lista.isEmpty) {
          showSuccess("No registra estudiantes con cronogramas asignados.")
        }
      case Left(failure) =>
        showError(s"Error al cargar estudiantes tutorados: $failure")
    }
  }

  private def cargarActividadesPendientes(idPractica: Int): Unit = {
    ValidacionActividadesLogic.listarActividadesPendientes(idPractica) match {
      case Right(lista) =>
        tblValidarActividadesDocente.getItems.clear()
        tblValidarActividadesDocente.getItems.addAll(lista: _*)
        txtCorreccionMetodologica.clear()
      case Left(failure) =>
        showError(s"Error al cargar actividades pendientes: $failure")
    }
  }

  private def limpiarDetallesActividades(): Unit = {
    tblValidarActividadesDocente.getItems.clear()
    txtCorreccionMetodologica.clear()
    btnAprobar.setDisable(true)
    btnRechazar.setDisable(true)
    txtCorreccionMetodologica.setDisable(true)
  }

  @FXML
  def handleAprobar(event: ActionEvent): Unit = {
    val seleccionActividad = tblValidarActividadesDocente.getSelectionModel.getSelectedItem
    if (seleccionActividad != null) {
      ValidacionActividadesLogic.aprobarActividad(seleccionActividad.idActividad) match {
        case Right(_) =>
          showSuccess("¡Actividad convalidada y aprobada metodológicamente!")
          txtCorreccionMetodologica.clear()
          refrescarPantalla()
        case Left(ValidacionActividadesFailure.Validacion(msg)) =>
          showError(msg)
        case Left(ValidacionActividadesFailure.ErrorPersistencia(msg)) =>
          showError(s"Error al aprobar actividad: $msg")
      }
    }
  }

  @FXML
  def handleRechazar(event: ActionEvent): Unit = {
    val seleccionActividad = tblValidarActividadesDocente.getSelectionModel.getSelectedItem
    if (seleccionActividad != null) {
      val comentario = txtCorreccionMetodologica.getText
      ValidacionActividadesLogic.rechazarActividad(seleccionActividad.idActividad, comentario) match {
        case Right(_) =>
          showSuccess("Actividad rechazada inmutablemente con observaciones.")
          txtCorreccionMetodologica.clear()
          refrescarPantalla()
        case Left(ValidacionActividadesFailure.Validacion(msg)) =>
          showError(msg)
        case Left(ValidacionActividadesFailure.ErrorPersistencia(msg)) =>
          showError(s"Error al rechazar actividad: $msg")
      }
    }
  }

  private def refrescarPantalla(): Unit = {
    // Guardar selección actual de estudiante para re-seleccionarlo tras la actualización
    val estSeleccionadoOpt = Option(tblEstudiantesTutorados.getSelectionModel.getSelectedItem)
    
    cargarListaEstudiantes()

    estSeleccionadoOpt.foreach { est =>
      // Volver a seleccionar el estudiante en la lista
      val items = tblEstudiantesTutorados.getItems
      var i = 0
      var encontrado = false
      while (i < items.size() && !encontrado) {
        if (items.get(i).idPractica == est.idPractica) {
          tblEstudiantesTutorados.getSelectionModel.select(i)
          encontrado = true
        }
        i += 1
      }
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
