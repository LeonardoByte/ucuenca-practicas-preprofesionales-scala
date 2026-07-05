  package com.ucuenca.gestion.controllers

  import com.ucuenca.gestion.models.db.DashboardRepository
  import javafx.fxml.FXML
  import javafx.scene.control.Label
  import javafx.scene.layout.VBox
  import com.ucuenca.gestion.models.logic.{DashboardFailure, DashboardLogic}
  import com.ucuenca.gestion.utils.SessionManager

  class MetricasGeneralesController {

    @FXML var lblPostulacionesPendientes: Label = _
    @FXML var lblOfertasPendientes: Label = _
    @FXML var lblOficiosPendientes: Label = _
    @FXML var lblAsignacionesPendientes: Label = _
    @FXML var lblAuditoriasPendientes: Label = _
    @FXML var lblUsuarioNombreDashboard: Label = _

    @FXML var cardAutorizarInicios: VBox = _
    @FXML var lblIniciosPendientes: Label = _

    @FXML
    def initialize(): Unit = {
      SessionManager.getUsuario match {
        case Some(usuario) =>
          cargarName(usuario.identificacion)
          cargarMetricas()
        case None =>
          System.err.println("Sesión inválida o expirada en el panel del coordinador.")
      }
    }

    private def cargarName(coordinadorCI: String): Unit = {
      DashboardLogic.nameUser(coordinadorCI) match {
        case Right(nombre) =>lblUsuarioNombreDashboard.setText(nombre)
        case Left(DashboardFailure.ErrorCarga(msg)) =>
          System.err.println(s"Error al cargar el nombre del coordinador: $msg")
      }
    }

    private def cargarMetricas(): Unit = {
      DashboardLogic.fetchCoordinatorMetrics() match {
        case Right(metrics) =>
          lblPostulacionesPendientes.setText(metrics.getOrElse("postulaciones", 0).toString)
          lblOfertasPendientes.setText(metrics.getOrElse("ofertas", 0).toString)
          lblOficiosPendientes.setText(metrics.getOrElse("oficios", 0).toString)
          lblAsignacionesPendientes.setText(metrics.getOrElse("asignaciones", 0).toString)
          lblAuditoriasPendientes.setText(metrics.getOrElse("auditorias", 0).toString)

          // Obtener cantidad de inicios de prácticas pendientes de autorización
          try {
            val iniciosCount = com.ucuenca.gestion.models.db.Formulario1DB.listarPendientesCoordinador().size
            lblIniciosPendientes.setText(iniciosCount.toString)
          } catch {
            case e: Exception =>
              System.err.println(s"Error al obtener métrica de inicios pendientes: ${e.getMessage}")
              lblIniciosPendientes.setText("0")
          }

        case Left(DashboardFailure.ErrorCarga(msg)) =>
          System.err.println(s"Error al cargar métricas de coordinación: $msg")
      }
    }

    @FXML
    def handleGoToAutorizarInicios(event: javafx.scene.input.MouseEvent): Unit = {
      val stage = com.ucuenca.gestion.utils.NavigationManager.getStage
      if (stage != null && stage.getScene != null) {
        val centralPane = stage.getScene.lookup("#panelCentralDisp").asInstanceOf[javafx.scene.layout.StackPane]
        if (centralPane != null) {
          com.ucuenca.gestion.views.CoordinadorAutorizarIniciosView.renderInto(centralPane)
        }
      }
    }
  }
