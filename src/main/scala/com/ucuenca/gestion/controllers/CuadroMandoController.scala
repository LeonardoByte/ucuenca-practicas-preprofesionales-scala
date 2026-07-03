package com.ucuenca.gestion.controllers

import javafx.fxml.FXML
import javafx.scene.control.Label
import com.ucuenca.gestion.models.logic.{DashboardLogic, DashboardFailure}
import com.ucuenca.gestion.utils.SessionManager

class CuadroMandoController {

  @FXML var lblVacantesCubiertas: Label = _
  @FXML var lblVigenciaConvenio: Label = _
  @FXML var lblTutoresRegistrados: Label = _
  @FXML var lblUsuarioNombreDashboard: Label = _

  @FXML
  def initialize(): Unit = {
    SessionManager.getUsuario match {
      case Some(usuario) =>
        cargarName(usuario.identificacion)
        cargarMetricas(usuario.identificacion)
      case None =>
        System.err.println("Sesión inválida o expirada en el cuadro de mando de empresa.")
    }
  }

  private def cargarName(rucEmpresa: String): Unit = {
    DashboardLogic.nameUser(rucEmpresa) match {
      case Right(nombre) =>lblUsuarioNombreDashboard.setText(s"[${nombre}]")
      case Left(DashboardFailure.ErrorCarga(msg)) =>
        System.err.println(s"Error al cargar el nombre de la empresa: $msg")
    }
  }

  private def cargarMetricas(rucEmpresa: String): Unit = {
    DashboardLogic.fetchCompanyMetrics(rucEmpresa) match {
      case Right(metrics) =>
        lblVacantesCubiertas.setText(metrics.getOrElse("vacantes", "0 / 0"))
        
        val convenio = metrics.getOrElse("convenio", "SIN CONVENIO")
        lblVigenciaConvenio.setText(convenio)
        if (convenio == "VIGENTE") {
          lblVigenciaConvenio.setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold;")
        } else {
          lblVigenciaConvenio.setStyle("-fx-text-fill: #ea580c; -fx-font-weight: bold;")
        }

        lblTutoresRegistrados.setText(metrics.getOrElse("tutores", "0"))

      case Left(DashboardFailure.ErrorCarga(msg)) =>
        System.err.println(s"Error al cargar métricas de empresa: $msg")
    }
  }
}
