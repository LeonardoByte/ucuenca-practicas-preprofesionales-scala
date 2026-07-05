package com.ucuenca.gestion.controllers

import javafx.fxml.FXML
import javafx.scene.control._
import javafx.beans.property.SimpleStringProperty
import com.ucuenca.gestion.models.logic.{DashboardLogic, DashboardFailure}
import com.ucuenca.gestion.utils.SessionManager
import scalikejdbc._
import scala.util.control.NonFatal

case class TutoradoTableItem(
  nombreEstudiante: String,
  nombreEmpresa: String,
  cicloActual: Int,
  etapa: String,
  horas: String
)

class AlumnosTutoradosController {

  @FXML var lblAlumnosInicio: Label = _
  @FXML var lblAlumnosDesarrollo: Label = _
  @FXML var lblAlumnosEvaluacion: Label = _
  @FXML var lblTareasPendientes: Label = _
  @FXML var lblUsuarioNombreDashboard: Label = _

  @FXML var tblAlumnosTutorados: TableView[TutoradoTableItem] = _
  @FXML var colEstudiante: TableColumn[TutoradoTableItem, String] = _
  @FXML var colEmpresa: TableColumn[TutoradoTableItem, String] = _
  @FXML var colCiclo: TableColumn[TutoradoTableItem, String] = _
  @FXML var colEtapa: TableColumn[TutoradoTableItem, String] = _
  @FXML var colHoras: TableColumn[TutoradoTableItem, String] = _

  @FXML
  def initialize(): Unit = {
    // 1. Configurar columnas de la tabla
    colEstudiante.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.nombreEstudiante))
    colEmpresa.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.nombreEmpresa))
    colCiclo.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.cicloActual.toString))
    colEtapa.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.etapa))
    colHoras.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.horas))

    // 2. Obtener sesión e inicializar datos
    SessionManager.getUsuario match {
      case Some(usuario) =>
        cargarName(usuario.identificacion)
        cargarMetricas(usuario.identificacion)
        cargarTablaTutorados(usuario.identificacion)
      case None =>
        System.err.println("Sesión inválida o expirada en el panel de tutor académico.")
    }
  }

  private def cargarName(tutorAcadCI: String): Unit = {
    DashboardLogic.nameUser(tutorAcadCI) match {
      case Right(nombre) =>lblUsuarioNombreDashboard.setText(nombre)
      case Left(DashboardFailure.ErrorCarga(msg)) =>
        System.err.println(s"Error al cargar el nombre del tutor académico: $msg")
    }
  }

  private def cargarMetricas(tutorCI: String): Unit = {
    DashboardLogic.fetchAcademicTutorMetrics(tutorCI) match {
      case Right(metrics) =>
        lblAlumnosInicio.setText(s"${metrics.getOrElse("inicio", 0)} Alumnos")
        lblAlumnosDesarrollo.setText(s"${metrics.getOrElse("desarrollo", 0)} Alumnos")
        lblAlumnosEvaluacion.setText(s"${metrics.getOrElse("evaluacion", 0)} Alumnos")
        lblTareasPendientes.setText(s"${metrics.getOrElse("tareas", 0)} Tareas")
      case Left(DashboardFailure.ErrorCarga(msg)) =>
        System.err.println(s"Error al cargar métricas de tutor académico: $msg")
    }
  }

  private def cargarTablaTutorados(tutorCI: String): Unit = {
    try {
      val list = DB.readOnly { implicit session =>
        sql"""
          SELECT 
            u_est.nombres_completos AS nombre_estudiante,
            u_emp.nombres_completos AS nombre_empresa,
            ep.ciclo_actual,
            pr.estado_cronograma,
            pr.horas_acumuladas,
            pr.horas_totales_requeridas
          FROM practica_registro pr
          INNER JOIN estudiante_perfil ep ON pr.ci_estudiante_ref = ep.identificacion
          INNER JOIN usuario u_est ON ep.identificacion = u_est.identificacion
          INNER JOIN usuario u_emp ON pr.ruc_empresa_ref = u_emp.identificacion
          WHERE pr.id_tutor_academico_ref = ${tutorCI}
          ORDER BY u_est.nombres_completos ASC
        """.map { rs =>
          val estadoStr = rs.string("estado_cronograma")
          val etapaFriendly = estadoStr match {
            case "TUTOR_ACADEMICO_PENDIENTE" | "F1_PENDIENTE" => "Fase de Inicio (F1)"
            case "EN_DESARROLLO" => "Desarrollo"
            case "F2_F3_PENDIENTE" => "Evaluación Final (F2/F3)"
            case "CERRADA_VALIDA" => "Cerrada y Calificada"
            case other => other
          }
          val hrs = s"${rs.int("horas_acumuladas")} / ${rs.int("horas_totales_requeridas")} hs"
          TutoradoTableItem(
            nombreEstudiante = rs.string("nombre_estudiante"),
            nombreEmpresa = rs.string("nombre_empresa"),
            cicloActual = rs.int("ciclo_actual"),
            etapa = etapaFriendly,
            horas = hrs
          )
        }.list.apply()
      }

      tblAlumnosTutorados.getItems.clear()
      list.foreach(tblAlumnosTutorados.getItems.add)

    } catch {
      case NonFatal(e) =>
        System.err.println(s"Error al cargar nómina de tutorados en la tabla: ${e.getMessage}")
    }
  }
}
