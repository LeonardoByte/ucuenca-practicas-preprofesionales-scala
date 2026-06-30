package com.ucuenca.gestion.controllers

import javafx.fxml.FXML
import javafx.scene.control._
import javafx.scene.layout._
import javafx.beans.property.SimpleStringProperty
import javafx.event.ActionEvent
import com.ucuenca.gestion.models.db.{TraceabilityPracticeListItem, RejectionCommentDTO, StageDetails}
import com.ucuenca.gestion.models.logic.AnalyticsLogic
import java.time.format.DateTimeFormatter

class TrazabilidadController {

  @FXML var txtBuscarCriterio: TextField = _
  @FXML var tblListaIntermediaPracticas: TableView[TraceabilityPracticeListItem] = _
  @FXML var colCodigo: TableColumn[TraceabilityPracticeListItem, String] = _
  @FXML var colEstudiante: TableColumn[TraceabilityPracticeListItem, String] = _
  @FXML var colEmpresa: TableColumn[TraceabilityPracticeListItem, String] = _
  @FXML var colFecha: TableColumn[TraceabilityPracticeListItem, String] = _
  @FXML var colEstado: TableColumn[TraceabilityPracticeListItem, String] = _

  @FXML var vboxTimeline: VBox = _
  @FXML var txtAreaLecturaRetroalimentacion: TextArea = _

  private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

  @FXML
  def initialize(): Unit = {
    // 1. Configurar columnas de la tabla intermedia
    colCodigo.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.idPractica.toString))
    colEstudiante.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.estudiante))
    colEmpresa.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.empresa))
    colFecha.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.fechaRegistro.format(dateFormatter)))
    colEstado.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.estado))

    // 2. Evento de selección de fila
    tblListaIntermediaPracticas.getSelectionModel.selectedItemProperty().addListener((_, _, selectedItem) => {
      if (selectedItem != null) {
        cargarDetallesAuditoria(selectedItem.idPractica)
      }
    })
  }

  @FXML
  def handleBuscar(event: ActionEvent): Unit = {
    val criterio = txtBuscarCriterio.getText
    if (criterio == null || criterio.trim.isEmpty) {
      val alert = new Alert(Alert.AlertType.WARNING)
      alert.setTitle("Advertencia")
      alert.setHeaderText(null)
      alert.setContentText("Debe ingresar un criterio de búsqueda (nombre del alumno o RUC/empresa).")
      alert.showAndWait()
      return
    }

    AnalyticsLogic.searchPractices(criterio) match {
      case Right(list) =>
        tblListaIntermediaPracticas.getItems.clear()
        vboxTimeline.getChildren.clear()
        txtAreaLecturaRetroalimentacion.clear()
        
        if (list.isEmpty) {
          val alert = new Alert(Alert.AlertType.INFORMATION)
          alert.setTitle("Búsqueda")
          alert.setHeaderText(null)
          alert.setContentText("No se encontraron prácticas asociadas al criterio provisto.")
          alert.showAndWait()
        } else {
          list.foreach(tblListaIntermediaPracticas.getItems.add)
        }
      case Left(failure) =>
        System.err.println(s"Error en auditoría search: $failure")
    }
  }

  private def cargarDetallesAuditoria(idPractica: Int): Unit = {
    // 1. Cargar timeline de 6 etapas
    AnalyticsLogic.getTimeline(idPractica) match {
      case Right(timeline) =>
        vboxTimeline.getChildren.clear()
        
        timeline.stages.foreach { stage =>
          val nodeBox = new HBox()
          nodeBox.setSpacing(15)
          nodeBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT)
          nodeBox.setStyle("-fx-padding: 8 10; -fx-background-color: #f8fafc; -fx-border-radius: 4; -fx-border-width: 0 0 0 4px;")

          // Colores de borde y texto según estado de la etapa
          if (stage.alerta) {
            nodeBox.setStyle(nodeBox.getStyle + " -fx-border-color: #ef4444; -fx-background-color: #fef2f2;")
          } else if (stage.concluida) {
            nodeBox.setStyle(nodeBox.getStyle + " -fx-border-color: #10b981;")
          } else {
            nodeBox.setStyle(nodeBox.getStyle + " -fx-border-color: #94a3b8;")
          }

          val labelBox = new VBox()
          labelBox.setSpacing(3)
          
          val lblTitulo = new Label(stage.nombre)
          lblTitulo.setStyle("-fx-font-weight: bold; -fx-font-size: 10pt; -fx-text-fill: #1e293b;")
          
          val lblInfo = new Label(s"Estado: ${stage.estado} — ${stage.detalle}")
          lblInfo.setStyle("-fx-font-size: 9pt; -fx-text-fill: #475569;")
          lblInfo.setWrapText(true)
          
          labelBox.getChildren.addAll(lblTitulo, lblInfo)
          HBox.setHgrow(labelBox, Priority.ALWAYS)
          nodeBox.getChildren.add(labelBox)
          
          vboxTimeline.getChildren.add(nodeBox)
        }

      case Left(failure) =>
        System.err.println(s"Error al renderizar timeline: $failure")
    }

    // 2. Cargar historial de rechazos
    AnalyticsLogic.getRejectionComments(idPractica) match {
      case Right(comments) =>
        txtAreaLecturaRetroalimentacion.clear()
        if (comments.isEmpty) {
          txtAreaLecturaRetroalimentacion.setText("No se registran observaciones de rechazo o inconsistencias en los bucles de retroalimentación para esta práctica.")
        } else {
          val sb = new java.lang.StringBuilder()
          comments.foreach { c =>
            sb.append(s"[${c.fecha.format(dateFormatter)} - ${c.etapa}]:\n")
            sb.append(s"» ${c.comentario}\n\n")
          }
          txtAreaLecturaRetroalimentacion.setText(sb.toString)
        }
      case Left(failure) =>
        txtAreaLecturaRetroalimentacion.setText(s"Error al recuperar historial: $failure")
    }
  }
}
