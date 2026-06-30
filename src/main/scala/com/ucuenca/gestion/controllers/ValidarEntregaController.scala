package com.ucuenca.gestion.controllers

import javafx.fxml.FXML
import javafx.scene.control._
import javafx.beans.property.SimpleStringProperty
import com.ucuenca.gestion.models.dto.CartaCompromisoPendienteDTO
import com.ucuenca.gestion.models.logic.{ValidacionCartaLogic, ValidacionCartaFailure}

class ValidarEntregaController {

  @FXML var txtBuscarAlumnoSecretaria: TextField = _
  @FXML var tblCartasSecretaria: TableView[CartaCompromisoPendienteDTO] = _
  @FXML var colCedula: TableColumn[CartaCompromisoPendienteDTO, String] = _
  @FXML var colNombre: TableColumn[CartaCompromisoPendienteDTO, String] = _
  @FXML var colCertificacionCopias: TableColumn[CartaCompromisoPendienteDTO, String] = _
  @FXML var colFecha: TableColumn[CartaCompromisoPendienteDTO, String] = _
  @FXML var lblEstado: Label = _

  @FXML
  def initialize(): Unit = {
    // 1. Configurar fábricas de valores
    colCedula.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.ciEstudiante))
    colNombre.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.nombreEstudiante))
    colFecha.setCellValueFactory(_ => new SimpleStringProperty("Pendiente"))

    // 2. Configurar CheckBox interactivo de certificación
    colCertificacionCopias.setCellFactory(_ => new TableCell[CartaCompromisoPendienteDTO, String] {
      private val chk = new CheckBox("Recibido")

      override def updateItem(item: String, empty: Boolean): Unit = {
        super.updateItem(item, empty)
        if (empty) {
          setGraphic(null)
          setText(null)
        } else {
          val dto = getTableView.getItems.get(getIndex)
          if (dto != null) {
            chk.setSelected(false)
            chk.setOnAction(_ => {
              if (chk.isSelected) {
                certificar(dto.ciEstudiante, chk)
              }
            })
            setGraphic(chk)
            setText(null)
          } else {
            setGraphic(null)
            setText(null)
          }
        }
      }
    })

    // 3. Listener para búsqueda incremental
    txtBuscarAlumnoSecretaria.textProperty().addListener((_, _, nv) => {
      cargarDatos(Option(nv).filter(_.trim.nonEmpty))
    })

    // 4. Carga inicial
    cargarDatos(None)
  }

  private def cargarDatos(filtroOpt: Option[String]): Unit = {
    ValidacionCartaLogic.listarPendientes(filtroOpt) match {
      case Right(lista) =>
        tblCartasSecretaria.getItems.clear()
        tblCartasSecretaria.getItems.addAll(lista: _*)
        if (lista.isEmpty && filtroOpt.isDefined) {
          showSuccess("No se encontraron coincidencias para la búsqueda.")
        } else {
          lblEstado.setVisible(false)
        }
      case Left(failure) =>
        showError(s"Error al cargar expediente: $failure")
    }
  }

  private def certificar(ciEstudiante: String, chk: CheckBox): Unit = {
    ValidacionCartaLogic.certificarCarta(ciEstudiante, entregadoTresCopias = true) match {
      case Right(_) =>
        showSuccess(s"¡Carta de compromiso certificada y digitalizada con éxito para CI: $ciEstudiante!")
        // Recargar datos para que se remueva la fila
        cargarDatos(Option(txtBuscarAlumnoSecretaria.getText).filter(_.trim.nonEmpty))
      case Left(ValidacionCartaFailure.Validacion(msg)) =>
        showError(msg)
        chk.setSelected(false)
      case Left(ValidacionCartaFailure.ErrorPersistencia(msg)) =>
        showError(s"Error al certificar documento: $msg")
        chk.setSelected(false)
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
