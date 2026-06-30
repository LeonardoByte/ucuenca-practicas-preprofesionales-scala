package com.ucuenca.gestion.controllers

import javafx.fxml.FXML
import javafx.scene.control.Label
import com.ucuenca.gestion.models.logic.{DashboardLogic, DashboardFailure}

class ResumenOperativoController {

  @FXML var lblCartasPendientes: Label = _
  @FXML var lblConveniosPendientes: Label = _

  @FXML
  def initialize(): Unit = {
    cargarMetricas()
  }

  private def cargarMetricas(): Unit = {
    DashboardLogic.fetchSecretaryMetrics() match {
      case Right(metrics) =>
        lblCartasPendientes.setText(s"${metrics.getOrElse("cartas", 0)} Carpetas")
        lblConveniosPendientes.setText(s"${metrics.getOrElse("convenios", 0)} Peticiones")
      case Left(DashboardFailure.ErrorCarga(msg)) =>
        System.err.println(s"Error al cargar métricas de secretaría: $msg")
    }
  }
}
