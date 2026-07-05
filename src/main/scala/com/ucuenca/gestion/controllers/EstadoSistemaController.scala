package com.ucuenca.gestion.controllers

import javafx.fxml.FXML
import javafx.scene.control.Label
import com.ucuenca.gestion.models.logic.{DashboardFailure, DashboardLogic}
import com.ucuenca.gestion.utils.SessionManager

class EstadoSistemaController {

  @FXML var lblEstadoBD: Label = _
  @FXML var lblPracticasActivas: Label = _
  @FXML var lblEstudiantes: Label = _
  @FXML var lblTutoresAcad: Label = _
  @FXML var lblEmpresas: Label = _
  @FXML var lblTutoresEmp: Label = _
  @FXML var lblCoordinadores: Label = _
  @FXML var lblSecretarias: Label = _
  @FXML var lblAdmins: Label = _
  @FXML var lblUsuarioNombreDashboard: Label = _

  @FXML
  def initialize(): Unit = {
    SessionManager.getUsuario match {
      case Some(usuario) =>
        cargarName(usuario.identificacion)
        cargarMetricas()
      case None =>
        System.err.println("Sesión inválida o expirada en el cuadro de mando del administrador.")
    }
  }

  private def cargarName(adminCI: String): Unit = {
    DashboardLogic.nameUser(adminCI) match {
      case Right(nombre) =>lblUsuarioNombreDashboard.setText(nombre)
      case Left(DashboardFailure.ErrorCarga(msg)) =>
        System.err.println(s"Error al cargar el nombre del administrador: $msg")
    }
  }

  private def cargarMetricas(): Unit = {
    DashboardLogic.fetchAdminMetrics() match {
      case Right(metrics) =>
        lblEstadoBD.setText(if (metrics.getOrElse("dbCheck", 0) == 1) "ACTIVO" else "CAÍDO")
        lblEstadoBD.setStyle(if (metrics.getOrElse("dbCheck", 0) == 1) "-fx-text-fill: #10b981; -fx-font-weight: bold;" else "-fx-text-fill: #dc2626; -fx-font-weight: bold;")

        lblPracticasActivas.setText(metrics.getOrElse("practicasActivas", 0).toString)
        lblEstudiantes.setText(metrics.getOrElse("estudiantes", 0).toString)
        lblTutoresAcad.setText(metrics.getOrElse("tutoresAcad", 0).toString)
        lblEmpresas.setText(metrics.getOrElse("empresas", 0).toString)
        lblTutoresEmp.setText(metrics.getOrElse("tutoresEmp", 0).toString)
        lblCoordinadores.setText(metrics.getOrElse("coordinadores", 0).toString)
        lblSecretarias.setText(metrics.getOrElse("secretarias", 0).toString)
        lblAdmins.setText(metrics.getOrElse("admins", 0).toString)

      case Left(DashboardFailure.ErrorCarga(msg)) =>
        lblEstadoBD.setText("ERROR")
        lblEstadoBD.setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;")
        System.err.println(s"Error al cargar métricas de administración: $msg")
    }
  }
}
