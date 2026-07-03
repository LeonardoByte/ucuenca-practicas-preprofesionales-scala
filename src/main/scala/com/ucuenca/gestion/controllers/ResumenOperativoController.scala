package com.ucuenca.gestion.controllers

import javafx.fxml.FXML
import javafx.scene.control.Label
import com.ucuenca.gestion.models.logic.{DashboardFailure, DashboardLogic}
import com.ucuenca.gestion.utils.SessionManager

class ResumenOperativoController {

  @FXML var lblCartasPendientes: Label = _
  @FXML var lblConveniosPendientes: Label = _
  @FXML var lblUsuarioNombreDashboard: Label = _

  @FXML
  def initialize(): Unit = {
    SessionManager.getUsuario match {
      case Some(usuario) =>
        cargarName(usuario.identificacion)
        cargarMetricas()
      case None =>
        System.err.println("Sesión inválida o expirada en el cuadro de mando de secretaría.")
    }
  }

  private def cargarName(secretariaCI: String): Unit = {
    DashboardLogic.nameUser(secretariaCI) match {
      case Right(nombre) =>lblUsuarioNombreDashboard.setText(s"[${nombre}]")
      case Left(DashboardFailure.ErrorCarga(msg)) =>
        System.err.println(s"Error al cargar tutor empresarial de secretaría: $msg")
    }
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
