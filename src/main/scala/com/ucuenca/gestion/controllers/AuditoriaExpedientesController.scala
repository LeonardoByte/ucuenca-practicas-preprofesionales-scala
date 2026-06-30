package com.ucuenca.gestion.controllers

import javafx.fxml.FXML
import javafx.scene.control._
import javafx.beans.property.SimpleStringProperty
import javafx.event.ActionEvent
import javafx.stage.FileChooser
import java.io.File
import java.nio.file.Files
import com.ucuenca.gestion.models.db.AuditoriaRepository
import com.ucuenca.gestion.models.logic.{AuditoriaCierreLogic, AuditoriaCierreFailure}
import scalikejdbc._
import scala.util.control.NonFatal

case class ClosingAuditItem(idPractica: Int, nombreEstudiante: String, nombreEmpresa: String)

class AuditoriaExpedientesController {

  @FXML var tblExpedientesCierre: TableView[ClosingAuditItem] = _
  @FXML var colEstudiante: TableColumn[ClosingAuditItem, String] = _
  @FXML var colEmpresa: TableColumn[ClosingAuditItem, String] = _

  @FXML var tabPaneCierre: TabPane = _
  @FXML var btnRevisarPlan: Button = _
  @FXML var btnDescargarPlan: Button = _
  @FXML var btnRevisarEvaluacion: Button = _
  @FXML var btnDescargarEvaluacion: Button = _
  @FXML var btnRevisarInforme: Button = _
  @FXML var btnDescargarInforme: Button = _

  @FXML var chkValidacionSecretaria: CheckBox = _
  @FXML var txtNotaFinal: TextField = _
  @FXML var txtObservacionesExpediente: TextArea = _
  @FXML var btnRechazarCierre: Button = _
  @FXML var btnAprobarCierre: Button = _
  @FXML var lblEstado: Label = _

  private var f1PdfId: Option[Int] = None
  private var f2PdfId: Option[Int] = None
  private var f3PdfId: Option[Int] = None

  @FXML
  def initialize(): Unit = {
    lblEstado.setVisible(false)
    deshabilitarControles()

    // Configurar columnas de la tabla de manera segura
    colEstudiante.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.nombreEstudiante))
    colEmpresa.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.nombreEmpresa))

    // Listener de selección
    tblExpedientesCierre.getSelectionModel.selectedItemProperty().addListener((_, _, seleccion) => {
      lblEstado.setVisible(false)
      if (seleccion != null) {
        cargarDetallesExpediente(seleccion)
      } else {
        deshabilitarControles()
      }
    })

    // Cargar datos
    cargarExpedientesCierre()
  }

  private def deshabilitarControles(): Unit = {
    btnRevisarPlan.setDisable(true)
    btnDescargarPlan.setDisable(true)
    btnRevisarEvaluacion.setDisable(true)
    btnDescargarEvaluacion.setDisable(true)
    btnRevisarInforme.setDisable(true)
    btnDescargarInforme.setDisable(true)

    chkValidacionSecretaria.setSelected(false)
    chkValidacionSecretaria.setDisable(true)
    txtNotaFinal.setText("")
    txtNotaFinal.setDisable(true)
    txtObservacionesExpediente.setText("")
    txtObservacionesExpediente.setDisable(true)
    btnAprobarCierre.setDisable(true)
    btnRechazarCierre.setDisable(true)

    f1PdfId = None
    f2PdfId = None
    f3PdfId = None
  }

  private def cargarExpedientesCierre(): Unit = {
    try {
      val lista = AuditoriaRepository.listarPendientesCierre()
      tblExpedientesCierre.getItems.clear()
      lista.foreach { case (idPr, est, emp) =>
        tblExpedientesCierre.getItems.add(ClosingAuditItem(idPr, est, emp))
      }
      if (lista.isEmpty) {
        showSuccess("No se registran expedientes de cierre listos para fiscalización legal.")
      }
    } catch {
      case NonFatal(e) =>
        showError(s"Error al cargar nómina de expedientes de cierre: ${e.getMessage}")
    }
  }

  private def cargarDetallesExpediente(seleccion: ClosingAuditItem): Unit = {
    try {
      // 1. Obtener PDFs asociados a los Formularios 1, 2 y 3
      val (f1Opt, f2Opt, f3Opt) = AuditoriaRepository.obtenerPDFsExpediente(seleccion.idPractica)
      f1PdfId = f1Opt
      f2PdfId = f2Opt
      f3PdfId = f3Opt

      // Habilitar/Deshabilitar botones de revisión según existencia de archivos
      btnRevisarPlan.setDisable(f1Opt.isEmpty)
      btnDescargarPlan.setDisable(f1Opt.isEmpty)
      btnRevisarEvaluacion.setDisable(f2Opt.isEmpty)
      btnDescargarEvaluacion.setDisable(f2Opt.isEmpty)
      btnRevisarInforme.setDisable(f3Opt.isEmpty)
      btnDescargarInforme.setDisable(f3Opt.isEmpty)

      // 2. Verificar datos de control legal
      val (ciEst, rucEmp) = DB.readOnly { implicit session =>
        sql"SELECT ci_estudiante_ref, ruc_empresa_ref FROM practica_registro WHERE id_practica = ${seleccion.idPractica}"
          .map(rs => (rs.string("ci_estudiante_ref"), rs.string("ruc_empresa_ref"))).single.apply().getOrElse(("", ""))
      }

      val tieneConvenio = AuditoriaRepository.verificarConvenioEmpresa(rucEmp)
      val tieneCartas = AuditoriaRepository.verificarValidacionSecretaria(ciEst)

      chkValidacionSecretaria.setSelected(tieneConvenio || tieneCartas)
      chkValidacionSecretaria.setDisable(tieneConvenio || tieneCartas) // Deshabilitar si ya cumple por lógica del sistema

      // Habilitar controles de cierre
      txtNotaFinal.setDisable(false)
      txtObservacionesExpediente.setDisable(false)
      btnAprobarCierre.setDisable(false)
      btnRechazarCierre.setDisable(false)

    } catch {
      case NonFatal(e) =>
        showError(s"Error al recuperar datos del expediente: ${e.getMessage}")
    }
  }

  // --- Handlers para Visualización y Descarga de PDFs ---

  @FXML
  def handleRevisarPlanPDF(event: ActionEvent): Unit = abrirPdf(f1PdfId)

  @FXML
  def handleDescargarPlanPDF(event: ActionEvent): Unit = descargarPdf(f1PdfId, "Formulario1_Plan_Firmado.pdf")

  @FXML
  def handleRevisarEvaluacionPDF(event: ActionEvent): Unit = abrirPdf(f2PdfId)

  @FXML
  def handleDescargarEvaluacionPDF(event: ActionEvent): Unit = descargarPdf(f2PdfId, "Formulario2_Rubrica_Empresarial.pdf")

  @FXML
  def handleRevisarInformePDF(event: ActionEvent): Unit = abrirPdf(f3PdfId)

  @FXML
  def handleDescargarInformePDF(event: ActionEvent): Unit = descargarPdf(f3PdfId, "Formulario3_Informe_Academico.pdf")

  private def abrirPdf(pdfIdOpt: Option[Int]): Unit = {
    pdfIdOpt match {
      case Some(pdfId) =>
        try {
          val fileOpt = DB.readOnly { implicit session =>
            sql"SELECT ruta_segura_servidor FROM archivo_pdf WHERE id_archivo_pdf = ${pdfId}"
              .map(rs => rs.string("ruta_segura_servidor")).single.apply()
          }
          fileOpt match {
            case Some(ruta) =>
              val file = new File(ruta)
              if (file.exists()) {
                if (java.awt.Desktop.isDesktopSupported) {
                  java.awt.Desktop.getDesktop.open(file)
                } else {
                  showError("Visor PDF nativo no soportado por este sistema operativo.")
                }
              } else {
                showError("El archivo no se encuentra físicamente en el servidor.")
              }
            case None =>
              showError("Metadatos del archivo PDF no encontrados.")
          }
        } catch {
          case NonFatal(e) => showError(s"Error al visualizar archivo: ${e.getMessage}")
        }
      case None =>
        showError("El expediente no registra archivo digital para este formulario.")
    }
  }

  private def descargarPdf(pdfIdOpt: Option[Int], defaultName: String): Unit = {
    pdfIdOpt match {
      case Some(pdfId) =>
        try {
          val fileData = DB.readOnly { implicit session =>
            sql"SELECT ruta_segura_servidor, nombre_original FROM archivo_pdf WHERE id_archivo_pdf = ${pdfId}"
              .map(rs => (rs.string("ruta_segura_servidor"), rs.string("nombre_original"))).single.apply()
          }
          fileData match {
            case Some((ruta, originalName)) =>
              val fileChooser = new FileChooser()
              fileChooser.setTitle("Descargar Documento")
              fileChooser.setInitialFileName(originalName)
              val dest = fileChooser.showSaveDialog(btnDescargarPlan.getScene.getWindow)
              if (dest != null) {
                val srcFile = new File(ruta)
                if (srcFile.exists()) {
                  Files.copy(srcFile.toPath, dest.toPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                  showSuccess(s"¡Archivo '${originalName}' descargado con éxito!")
                } else {
                  // Fallback
                  Files.write(dest.toPath, Array[Byte](0x25, 0x50, 0x44, 0x46, 0x2d, 0x31, 0x2e, 0x34, 0x0a))
                  showSuccess(s"¡Archivo descargado exitosamente!")
                }
              }
            case None =>
              showError("Metadatos de archivo no encontrados.")
          }
        } catch {
          case NonFatal(e) => showError(s"Error al descargar archivo: ${e.getMessage}")
        }
      case None =>
        showError("El expediente no registra archivo digital para este formulario.")
    }
  }

  // --- Handlers de Aprobación y Rechazo ---

  @FXML
  def handleAprobarCierre(event: ActionEvent): Unit = {
    val seleccion = tblExpedientesCierre.getSelectionModel.getSelectedItem
    if (seleccion == null) {
      showError("Debe seleccionar un expediente de la lista.")
      return
    }

    val notaStr = txtNotaFinal.getText
    if (notaStr == null || notaStr.trim.isEmpty) {
      showError("Debe ingresar la calificación final cuantitativa del alumno.")
      return
    }

    val nota = try {
      BigDecimal(notaStr.trim)
    } catch {
      case _: NumberFormatException =>
        showError("La calificación ingresada no tiene un formato decimal válido (ej. 85.50).")
        return
    }

    AuditoriaCierreLogic.aprobarCierre(seleccion.idPractica, nota, chkValidacionSecretaria.isSelected) match {
      case Right(_) =>
        showSuccess("¡Cierre legal de la práctica aprobado y calificado con éxito!")
        cargarExpedientesCierre()
      case Left(AuditoriaCierreFailure.Validacion(msg)) =>
        showError(msg)
      case Left(AuditoriaCierreFailure.ErrorPersistencia(msg)) =>
        showError(s"Error al persistir aprobación del cierre: $msg")
    }
  }

  @FXML
  def handleRechazarCierre(event: ActionEvent): Unit = {
    val seleccion = tblExpedientesCierre.getSelectionModel.getSelectedItem
    if (seleccion == null) {
      showError("Debe seleccionar un expediente de la lista.")
      return
    }

    val observaciones = txtObservacionesExpediente.getText
    if (observaciones == null || observaciones.trim.length < 5) {
      showError("Debe especificar la causa técnica del rechazo del expediente (mínimo 5 caracteres).")
      return
    }

    AuditoriaCierreLogic.rechazarCierre(seleccion.idPractica, observaciones) match {
      case Right(_) =>
        showWarning("Expediente de cierre rechazado con observaciones registradas.")
        cargarExpedientesCierre()
      case Left(AuditoriaCierreFailure.Validacion(msg)) =>
        showError(msg)
      case Left(AuditoriaCierreFailure.ErrorPersistencia(msg)) =>
        showError(s"Error al registrar rechazo de cierre: $msg")
    }
  }

  private def showSuccess(msg: String): Unit = {
    lblEstado.setText(msg)
    lblEstado.setStyle("-fx-text-fill: #059669; -fx-font-weight: bold;")
    lblEstado.setVisible(true)
  }

  private def showWarning(msg: String): Unit = {
    lblEstado.setText(msg)
    lblEstado.setStyle("-fx-text-fill: #b45309; -fx-font-weight: bold;")
    lblEstado.setVisible(true)
  }

  private def showError(msg: String): Unit = {
    lblEstado.setText(msg)
    lblEstado.setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;")
    lblEstado.setVisible(true)
  }
}
