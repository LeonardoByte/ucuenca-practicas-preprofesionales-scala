package com.ucuenca.gestion.controllers

import com.ucuenca.gestion.models.db.DashboardRepository
import javafx.fxml.FXML
import javafx.scene.control._
import javafx.beans.property.SimpleStringProperty
import com.ucuenca.gestion.models.logic.{DashboardFailure, DashboardLogic}
import com.ucuenca.gestion.utils.SessionManager
import scalikejdbc._

import scala.util.control.NonFatal

case class CorporateTableItem(
  nombreEstudiante: String,
  carrera: String,
  cicloActual: Int,
  etapa: String,
  cierreStatus: String
)

class MisAlumnosController {

  @FXML var lblAlumnosInicio: Label = _
  @FXML var lblAlumnosDesarrollo: Label = _
  @FXML var lblAlumnosCierre: Label = _
  @FXML var lblTareasPlanificadas: Label = _
  @FXML var lblUsuarioNombreDashboard: Label = _

  @FXML var tblAlumnosAsignados: TableView[CorporateTableItem] = _
  @FXML var colEstudiante: TableColumn[CorporateTableItem, String] = _
  @FXML var colCarrera: TableColumn[CorporateTableItem, String] = _
  @FXML var colCiclo: TableColumn[CorporateTableItem, String] = _
  @FXML var colEtapa: TableColumn[CorporateTableItem, String] = _
  @FXML var colCierre: TableColumn[CorporateTableItem, String] = _

  @FXML
  def initialize(): Unit = {
    // 1. Configurar columnas de la tabla
    colEstudiante.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.nombreEstudiante))
    colCarrera.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.carrera))
    colCiclo.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.cicloActual.toString))
    colEtapa.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.etapa))
    colCierre.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.cierreStatus))

    // 2. Obtener sesión e inicializar datos
    SessionManager.getUsuario match {
      case Some(usuario) =>
        cargarName(usuario.identificacion)
        cargarMetricas(usuario.identificacion)
        cargarTablaAsignados(usuario.identificacion)
      case None =>
        System.err.println("Sesión inválida o expirada en el panel de tutor empresarial.")
    }
  }

  private def cargarName(tutorEmpCI: String): Unit = {
    DashboardLogic.nameUser(tutorEmpCI) match {
      case Right(nombre) =>lblUsuarioNombreDashboard.setText(nombre)
      case Left(DashboardFailure.ErrorCarga(msg)) =>
        System.err.println(s"Error al cargar el nombre del tutor empresarial: $msg")
    }
  }

  private def cargarMetricas(tutorEmpCI: String): Unit = {
    DashboardLogic.fetchCorporateTutorMetrics(tutorEmpCI) match {
      case Right(metrics) =>
        lblAlumnosInicio.setText(s"${metrics.getOrElse("inicio", 0)} Alumnos")
        lblAlumnosDesarrollo.setText(s"${metrics.getOrElse("desarrollo", 0)} Alumnos")
        lblAlumnosCierre.setText(s"${metrics.getOrElse("evaluacion", 0)} Alumnos")
        lblTareasPlanificadas.setText(s"${metrics.getOrElse("tareas", 0)} Tareas")
      case Left(DashboardFailure.ErrorCarga(msg)) =>
        System.err.println(s"Error al cargar métricas de tutor empresarial: $msg")
    }
  }

  private def cargarTablaAsignados(tutorEmpCI: String): Unit = {
    try {
      val list = DB.readOnly { implicit session =>
        sql"""
          SELECT 
            u_est.nombres_completos AS nombre_estudiante,
            c.nombre_carrera AS carrera,
            ep.ciclo_actual,
            pr.estado_cronograma
          FROM practica_registro pr
          INNER JOIN estudiante_perfil ep ON pr.ci_estudiante_ref = ep.identificacion
          INNER JOIN usuario u_est ON ep.identificacion = u_est.identificacion
          INNER JOIN carrera c ON ep.id_carrera_ref = c.id_carrera
          WHERE pr.id_tutor_empresarial_ref = ${tutorEmpCI}
          ORDER BY u_est.nombres_completos ASC
        """.map { rs =>
          val estadoStr = rs.string("estado_cronograma")
          val etapaFriendly = estadoStr match {
            case "TUTOR_ACADEMICO_PENDIENTE" | "F1_PENDIENTE" => "Fase de Inicio (F1)"
            case "EN_DESARROLLO" => "Desarrollo"
            case "F2_F3_PENDIENTE" => "Evaluación Final"
            case "CERRADA_VALIDA" => "Cerrada y Calificada"
            case other => other
          }
          val cierreStr = if (estadoStr == "F2_F3_PENDIENTE" || estadoStr == "CERRADA_VALIDA") {
            "SOLICITADA"
          } else {
            "NO SOLICITADA"
          }
          CorporateTableItem(
            nombreEstudiante = rs.string("nombre_estudiante"),
            carrera = rs.string("carrera"),
            cicloActual = rs.int("ciclo_actual"),
            etapa = etapaFriendly,
            cierreStatus = cierreStr
          )
        }.list.apply()
      }

      tblAlumnosAsignados.getItems.clear()
      list.foreach(tblAlumnosAsignados.getItems.add)

    } catch {
      case NonFatal(e) =>
        System.err.println(s"Error al cargar nómina de alumnos asignados en la tabla: ${e.getMessage}")
    }
  }
}
