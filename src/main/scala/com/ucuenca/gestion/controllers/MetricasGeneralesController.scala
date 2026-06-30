package com.ucuenca.gestion.controllers

import javafx.fxml.FXML
import javafx.scene.control.Label
import com.ucuenca.gestion.models.logic.{DashboardLogic, DashboardFailure}

class MetricasGeneralesController {

  @FXML var lblPostulacionesPendientes: Label = _
  @FXML var lblOfertasPendientes: Label = _
  @FXML var lblOficiosPendientes: Label = _
  @FXML var lblAsignacionesPendientes: Label = _
  @FXML var lblAuditoriasPendientes: Label = _

  @FXML
  def initialize(): Unit = {
    cargarMetricas()
  }

  private def cargarMetricas(): Unit = {
    DashboardLogic.fetchCoordinatorMetrics() match {
      case Right(metrics) =>
        lblPostulacionesPendientes.setText(metrics.getOrElse("postulaciones", 0).toString)
        lblOfertasPendientes.setText(metrics.getOrElse("ofertas", 0).toString)
        lblOficiosPendientes.setText(metrics.getOrElse("oficios", 0).toString)
        lblAsignacionesPendientes.setText(metrics.getOrElse("asignaciones", 0).toString)
        lblAuditoriasPendientes.setText(metrics.getOrElse("auditorias", 0).toString)

      case Left(DashboardFailure.ErrorCarga(msg)) =>
        System.err.println(s"Error al cargar métricas de coordinación: $msg")
    }
  }
}
