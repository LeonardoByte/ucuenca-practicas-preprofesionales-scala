package com.ucuenca.gestion.controllers

import javafx.fxml.FXML
import javafx.scene.control._
import javafx.event.ActionEvent
import javafx.beans.property.SimpleStringProperty
import com.ucuenca.gestion.models.dto.AsignacionDocenteDTO
import com.ucuenca.gestion.models.logic.{AsignarDocentesLogic, AsignarDocentesFailure}
import scala.jdk.CollectionConverters._

case class DocenteTutorItem(ci: String, nombre: String) {
  override def toString: String = nombre
}

class AsignarDocentesController {

  @FXML var tblAsignacionDocente: TableView[AsignacionDocenteDTO] = _
  @FXML var colEstudiante: TableColumn[AsignacionDocenteDTO, String] = _
  @FXML var colCarrera: TableColumn[AsignacionDocenteDTO, String] = _
  @FXML var colEmpresa: TableColumn[AsignacionDocenteDTO, String] = _
  @FXML var colOferta: TableColumn[AsignacionDocenteDTO, String] = _
  @FXML var colTutorAcademico: TableColumn[AsignacionDocenteDTO, String] = _
  @FXML var btnGuardar: Button = _
  @FXML var lblEstado: Label = _

  private var activeTutors: List[DocenteTutorItem] = Nil

  @FXML
  def initialize(): Unit = {
    // 1. Configurar fábricas de valores de las columnas fijas
    colEstudiante.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.nombreEstudiante))
    colCarrera.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.carrera))
    colEmpresa.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.nombreEmpresa))
    colOferta.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.tituloOferta))

    // 2. Cargar tutores activos de la base de datos
    cargarTutores()

    // 3. Configurar la columna de selección de tutor con un ComboBox por celda
    colTutorAcademico.setCellFactory(_ => new TableCell[AsignacionDocenteDTO, String] {
      private val cmb = new ComboBox[DocenteTutorItem]()
      cmb.setMaxWidth(Double.MaxValue)
      
      // Llenar el combo box con los tutores activos
      activeTutors.foreach(cmb.getItems.add)

      override def updateItem(item: String, empty: Boolean): Unit = {
        super.updateItem(item, empty)
        if (empty) {
          setGraphic(null)
          setText(null)
        } else {
          val rowItem = getTableView.getItems.get(getIndex)
          
          // Desactivar listener temporalmente para evitar ejecuciones fantasmas
          cmb.setOnAction(null)

          if (rowItem != null) {
            rowItem.idTutorAcademicoRef match {
              case Some(ci) =>
                val found = activeTutors.find(_.ci == ci).orNull
                cmb.setValue(found)
              case None =>
                cmb.setValue(null)
            }
          }

          // Vincular selección del combobox con la mutación del DTO
          cmb.setOnAction(_ => {
            val selected = cmb.getValue
            if (rowItem != null) {
              if (selected != null) {
                rowItem.idTutorAcademicoRef = Some(selected.ci)
              } else {
                rowItem.idTutorAcademicoRef = None
              }
            }
          })

          setGraphic(cmb)
          setText(null)
        }
      }
    })

    // 4. Cargar las prácticas que esperan asignación
    cargarAsignacionesPendientes()
  }

  private def cargarTutores(): Unit = {
    AsignarDocentesLogic.listarTutoresActivos() match {
      case Right(tutores) =>
        activeTutors = tutores.map(t => DocenteTutorItem(t.identificacion, t.nombresCompletos))
      case Left(failure) =>
        showError(s"Error al obtener tutores académicos: $failure")
    }
  }

  private def cargarAsignacionesPendientes(): Unit = {
    AsignarDocentesLogic.listarPendientes() match {
      case Right(lista) =>
        tblAsignacionDocente.getItems.clear()
        tblAsignacionDocente.getItems.addAll(lista: _*)
        lblEstado.setVisible(false)
      case Left(failure) =>
        showError(s"Error al cargar las prácticas pendientes: $failure")
    }
  }

  @FXML
  def handleGuardar(event: ActionEvent): Unit = {
    val items = tblAsignacionDocente.getItems.asScala.toList
    val asignacionesValidas = items.flatMap { item =>
      item.idTutorAcademicoRef.map(ci => (item.idPractica, ci))
    }

    if (asignacionesValidas.isEmpty) {
      showError("Debe seleccionar al menos un tutor académico antes de guardar.")
      return
    }

    AsignarDocentesLogic.guardarAsignaciones(asignacionesValidas) match {
      case Right(_) =>
        showSuccess(s"¡Se registraron ${asignacionesValidas.size} asignaciones de tutores académicos con éxito!")
        // Recargar grilla (las prácticas ya asignadas desaparecen ya que cambian a F1_PENDIENTE)
        cargarAsignacionesPendientes()
      case Left(AsignarDocentesFailure.Validacion(msg)) =>
        showError(msg)
      case Left(AsignarDocentesFailure.ErrorPersistencia(msg)) =>
        showError(s"Error de persistencia: $msg")
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
