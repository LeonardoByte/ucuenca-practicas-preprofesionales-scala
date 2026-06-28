package com.ucuenca.gestion.controllers

import javafx.fxml.FXML
import javafx.scene.control._
import javafx.event.ActionEvent
import javafx.beans.property.SimpleStringProperty
import com.ucuenca.gestion.models.entities.OfertaConvocatoria
import com.ucuenca.gestion.models.enums.EstadoOferta
import com.ucuenca.gestion.models.logic.{MonitoreoOfertasLogic, MonitoreoFailure}
import com.ucuenca.gestion.utils.SessionManager

class HistorialOfertasController {

  @FXML var tblHistorialOfertas: TableView[OfertaConvocatoria] = _
  @FXML var colTitulo: TableColumn[OfertaConvocatoria, String] = _
  @FXML var colFecha: TableColumn[OfertaConvocatoria, String] = _
  @FXML var colCupos: TableColumn[OfertaConvocatoria, String] = _
  @FXML var colEstado: TableColumn[OfertaConvocatoria, String] = _
  @FXML var colAccionCierre: TableColumn[OfertaConvocatoria, String] = _
  @FXML var txtComentarioRechazoCoordinador: TextArea = _
  @FXML var btnForzarCierreOferta: Button = _
  @FXML var lblEstado: Label = _

  private val placeholderObservacion = "[Ninguna observación registrada. Si la oferta es rechazada por el coordinador, aquí se renderizará el motivo obligatorio de forma automática.]"

  @FXML
  def initialize(): Unit = {
    // 1. Configurar fábricas de valores de celdas
    colTitulo.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.tituloOferta))
    colFecha.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.fechaPublicacion.map(_.toString).getOrElse("No publicada")))
    colCupos.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.vacantesSolicitadas.toString))
    colEstado.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.estadoOferta.toString))
    colAccionCierre.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.estadoOferta.toString))

    // 2. Configurar el botón en la columna de acción de cierre
    colAccionCierre.setCellFactory(_ => new TableCell[OfertaConvocatoria, String] {
      private val btn = new Button("Forzar Cierre")
      btn.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 9.5pt;")

      override def updateItem(item: String, empty: Boolean): Unit = {
        super.updateItem(item, empty)
        if (empty || item == null) {
          setGraphic(null)
          setText(null)
        } else {
          val oferta = getTableView.getItems.get(getIndex)
          if (oferta.estadoOferta == EstadoOferta.PENDIENTE || oferta.estadoOferta == EstadoOferta.APROBADA) {
            btn.setOnAction(_ => cerrarConvocatoria(oferta.idOferta))
            setGraphic(btn)
            setText(null)
          } else {
            setGraphic(null)
            setText(item match {
              case "CERRADA_MANUAL" => "Cerrada Manualmente"
              case "CERRADA_CUPOS"  => "Cerrada (Cupos Llenos)"
              case "RECHAZADA"      => "Rechazada"
              case _                => item
            })
          }
        }
      }
    })

    // 3. Listener de selección de filas
    tblHistorialOfertas.getSelectionModel.selectedItemProperty().addListener((_, _, seleccionada) => {
      actualizarObservacionesYAcciones(seleccionada)
    })

    // 4. Cargar datos iniciales
    cargarHistorial()
  }

  /**
   * Carga el historial de ofertas publicadas por la empresa autenticada.
   */
  private def cargarHistorial(): Unit = {
    SessionManager.getUsuario match {
      case Some(usuario) =>
        MonitoreoOfertasLogic.listarPorEmpresa(usuario.identificacion) match {
          case Right(lista) =>
            tblHistorialOfertas.getItems.clear()
            tblHistorialOfertas.getItems.addAll(lista: _*)
            lblEstado.setVisible(false)
          case Left(failure) =>
            showError(s"Error al cargar historial: $failure")
        }
      case None =>
        showError("Sesión expirada o inválida.")
    }
  }

  /**
   * Actualiza el cuadro de retroalimentación de rechazo y el estado del botón de cierre.
   */
  private def actualizarObservacionesYAcciones(oferta: OfertaConvocatoria): Unit = {
    if (oferta != null) {
      if (oferta.estadoOferta == EstadoOferta.RECHAZADA) {
        txtComentarioRechazoCoordinador.setText(oferta.justificacionCoordinador.getOrElse("Rechazada sin justificación registrada."))
      } else {
        txtComentarioRechazoCoordinador.setText(placeholderObservacion)
      }

      // Habilitar o deshabilitar botón de cierre según el estado de la oferta
      val esActiva = oferta.estadoOferta == EstadoOferta.PENDIENTE || oferta.estadoOferta == EstadoOferta.APROBADA
      btnForzarCierreOferta.setDisable(!esActiva)
    } else {
      txtComentarioRechazoCoordinador.setText(placeholderObservacion)
      btnForzarCierreOferta.setDisable(true)
    }
  }

  /**
   * Manejador de evento al presionar el botón de forzar cierre manual.
   */
  @FXML
  def handleForzarCierre(event: ActionEvent): Unit = {
    val selected = tblHistorialOfertas.getSelectionModel.getSelectedItem
    if (selected != null) {
      cerrarConvocatoria(selected.idOferta)
    } else {
      showError("Por favor, seleccione una oferta de la grilla primero.")
    }
  }

  /**
   * Cierra la convocatoria y actualiza la vista.
   */
  private def cerrarConvocatoria(idOferta: Int): Unit = {
    MonitoreoOfertasLogic.forzarCierre(idOferta) match {
      case Right(_) =>
        showSuccess("Convocatoria cerrada manualmente de manera exitosa.")
        cargarHistorial()
      case Left(MonitoreoFailure.Validacion(msg)) =>
        showError(msg)
      case Left(MonitoreoFailure.ErrorPersistencia(msg)) =>
        showError(s"Error técnico de persistencia: $msg")
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
