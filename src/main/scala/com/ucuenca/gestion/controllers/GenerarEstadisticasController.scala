package com.ucuenca.gestion.controllers

import javafx.fxml.FXML
import javafx.scene.control._
import javafx.beans.property.{SimpleStringProperty, SimpleDoubleProperty, SimpleIntegerProperty}
import javafx.event.ActionEvent
import javafx.stage.FileChooser
import com.ucuenca.gestion.models.db._
import com.ucuenca.gestion.models.logic.AnalyticsLogic
import scalikejdbc._
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

case class AnalyticsCarreraItem(idCarrera: Int, nombreCarrera: String) {
  override def toString: String = nombreCarrera
}

class GenerarEstadisticasController {

  @FXML var cmbPeriodo: ComboBox[String] = _
  @FXML var cmbCarrera: ComboBox[AnalyticsCarreraItem] = _
  @FXML var tabPaneInformes: TabPane = _
  @FXML var btnExportar: Button = _

  // Tabla 1: Estudiantes Activos
  @FXML var tblActiveStudents: TableView[ActiveStudentReportDTO] = _
  @FXML var colActiveStudent: TableColumn[ActiveStudentReportDTO, String] = _
  @FXML var colActiveCareer: TableColumn[ActiveStudentReportDTO, String] = _
  @FXML var colActiveCompany: TableColumn[ActiveStudentReportDTO, String] = _
  @FXML var colActiveHours: TableColumn[ActiveStudentReportDTO, String] = _
  @FXML var colActiveStage: TableColumn[ActiveStudentReportDTO, String] = _

  // Tabla 2: Ranking Empresas
  @FXML var tblTopCompanies: TableView[TopCompanyReportDTO] = _
  @FXML var colTopCompany: TableColumn[TopCompanyReportDTO, String] = _
  @FXML var colTopRuc: TableColumn[TopCompanyReportDTO, String] = _
  @FXML var colTopCount: TableColumn[TopCompanyReportDTO, String] = _

  // Tabla 3: Porcentaje Postulaciones
  @FXML var tblPostulations: TableView[PostulationPeriodReportDTO] = _
  @FXML var colPostulationState: TableColumn[PostulationPeriodReportDTO, String] = _
  @FXML var colPostulationCount: TableColumn[PostulationPeriodReportDTO, String] = _
  @FXML var colPostulationPercent: TableColumn[PostulationPeriodReportDTO, String] = _

  // Tabla 4: Satisfacción Corporativa
  @FXML var tblSatisfaction: TableView[SatisfactionReportDTO] = _
  @FXML var colSatCompany: TableColumn[SatisfactionReportDTO, String] = _
  @FXML var colSatAverage: TableColumn[SatisfactionReportDTO, String] = _

  @FXML
  def initialize(): Unit = {
    // 1. Configurar Fábricas de Celda de las 4 tablas
    colActiveStudent.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.nombreEstudiante))
    colActiveCareer.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.carrera))
    colActiveCompany.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.empresa))
    colActiveHours.setCellValueFactory(cd => new SimpleStringProperty(s"${cd.getValue.horasAcumuladas} hrs"))
    colActiveStage.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.estado))

    colTopCompany.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.empresa))
    colTopRuc.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.ruc))
    colTopCount.setCellValueFactory(cd => new SimpleStringProperty(s"${cd.getValue.totalPracticantes} Alumnos"))

    colPostulationState.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.estado))
    colPostulationCount.setCellValueFactory(cd => new SimpleStringProperty(s"${cd.getValue.cantidad} Postulaciones"))
    colPostulationPercent.setCellValueFactory(cd => new SimpleStringProperty(f"${cd.getValue.porcentaje}%.2f%%"))

    colSatCompany.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.empresa))
    colSatAverage.setCellValueFactory(cd => new SimpleStringProperty(f"${cd.getValue.promedioNota}%.2f / 100.00"))

    // 2. Sembrar Filtros
    cmbPeriodo.getItems.addAll("Ciclo Lectivo 2026 (Actual)", "Todos los Históricos")
    cmbPeriodo.setValue("Ciclo Lectivo 2026 (Actual)")
    
    cargarCarreras()

    // 3. Listener de selección del combo de carrera
    cmbCarrera.valueProperty().addListener((_, _, _) => {
      cargarTablas()
    })

    // 4. Cargar tablas inicialmente
    cargarTablas()
  }

  private def cargarCarreras(): Unit = {
    try {
      val list = DB.readOnly { implicit session =>
        sql"SELECT id_carrera, nombre_carrera FROM carrera ORDER BY nombre_carrera ASC"
          .map(rs => AnalyticsCarreraItem(rs.int("id_carrera"), rs.string("nombre_carrera"))).list.apply()
      }
      cmbCarrera.getItems.clear()
      list.foreach(cmbCarrera.getItems.add)
    } catch {
      case NonFatal(e) =>
        System.err.println(s"Error al cargar listado de carreras: ${e.getMessage}")
    }
  }

  private def cargarTablas(): Unit = {
    val selectedCarrera = Option(cmbCarrera.getValue).map(_.idCarrera)

    // 1. Estudiantes Activos
    AnalyticsLogic.fetchActiveStudents(selectedCarrera) match {
      case Right(list) =>
        tblActiveStudents.getItems.clear()
        list.foreach(tblActiveStudents.getItems.add)
      case Left(f) =>
        System.err.println(s"Error al poblar reporte de estudiantes activos: $f")
    }

    // 2. Ranking de Empresas
    AnalyticsLogic.fetchTopHostCompanies(selectedCarrera) match {
      case Right(list) =>
        tblTopCompanies.getItems.clear()
        list.foreach(tblTopCompanies.getItems.add)
      case Left(f) =>
        System.err.println(s"Error al poblar ranking de empresas: $f")
    }

    // 3. Resumen de Postulaciones
    AnalyticsLogic.fetchPostulationPercentages(selectedCarrera) match {
      case Right(list) =>
        tblPostulations.getItems.clear()
        list.foreach(tblPostulations.getItems.add)
      case Left(f) =>
        System.err.println(s"Error al poblar porcentajes de postulación: $f")
    }

    // 4. Averages de Satisfacción Corporativa
    AnalyticsLogic.fetchSatisfactionAverages(selectedCarrera) match {
      case Right(list) =>
        tblSatisfaction.getItems.clear()
        list.foreach(tblSatisfaction.getItems.add)
      case Left(f) =>
        System.err.println(s"Error al poblar promedios de satisfacción corporativa: $f")
    }
  }

  @FXML
  def handleClearFilters(event: ActionEvent): Unit = {
    cmbCarrera.setValue(null)
    cargarTablas()
  }

  /**
   * Exporta la tabla del informe actualmente visible en el TabPane a un archivo CSV plano.
   */
  @FXML
  def handleExportar(event: ActionEvent): Unit = {
    tabPaneInformes.getSelectionModel.getSelectedIndex match {
      case 0 => exportarTablaACSV(tblActiveStudents, "estudiantes_con_practicas_activas")
      case 1 => exportarTablaACSV(tblTopCompanies, "ranking_empresas_receptoras")
      case 2 => exportarTablaACSV(tblPostulations, "porcentajes_postulaciones")
      case _ => exportarTablaACSV(tblSatisfaction, "satisfaccion_desempeno_corporativo")
    }
  }

  private def exportarTablaACSV[S](tabla: TableView[S], nombreArchivoBase: String): Unit = {
    if (tabla.getItems.isEmpty) {
      val alert = new Alert(Alert.AlertType.WARNING)
      alert.setTitle("Exportación no disponible")
      alert.setHeaderText(null)
      alert.setContentText("El informe seleccionado no contiene datos para exportar.")
      alert.showAndWait()
      return
    }

    val fileChooser = new FileChooser()
    fileChooser.setTitle("Exportar Reporte a CSV")
    fileChooser.setInitialFileName(s"$nombreArchivoBase.csv")
    fileChooser.getExtensionFilters.add(new FileChooser.ExtensionFilter("Archivo CSV", "*.csv"))
    val file = fileChooser.showSaveDialog(btnExportar.getScene.getWindow)
    if (file != null) {
      try {
        val columnas = tabla.getColumns
        val encabezado = columnas.asScala.map(col => escaparCSV(col.getText)).mkString(",")
        val filas = tabla.getItems.asScala.map { item =>
          columnas.asScala.map { col =>
            val columna = col.asInstanceOf[TableColumn[S, Any]]
            val valor = Option(columna.getCellData(item)).map(_.toString).getOrElse("")
            escaparCSV(valor)
          }.mkString(",")
        }
        val contenido = (Seq(encabezado) ++ filas).mkString("\r\n")
        java.nio.file.Files.write(file.toPath, contenido.getBytes("UTF-8"))

        val alert = new Alert(Alert.AlertType.INFORMATION)
        alert.setTitle("Exportación exitosa")
        alert.setHeaderText(null)
        alert.setContentText(s"Reporte exportado exitosamente: ${file.getName}")
        alert.showAndWait()
      } catch {
        case NonFatal(e) =>
          val alert = new Alert(Alert.AlertType.ERROR)
          alert.setTitle("Error al exportar")
          alert.setHeaderText(null)
          alert.setContentText(s"No se pudo exportar el archivo: ${e.getMessage}")
          alert.showAndWait()
      }
    }
  }

  private def escaparCSV(valor: String): String = {
    if (valor.contains(",") || valor.contains("\"") || valor.contains("\n")) {
      "\"" + valor.replace("\"", "\"\"") + "\""
    } else {
      valor
    }
  }
}
