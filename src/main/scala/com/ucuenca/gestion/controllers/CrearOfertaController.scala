package com.ucuenca.gestion.controllers

import javafx.fxml.FXML
import javafx.scene.control._
import javafx.event.ActionEvent
import javafx.stage.FileChooser
import com.ucuenca.gestion.models.dto.CrearOfertaDTO
import com.ucuenca.gestion.models.logic.{CrearOfertaLogic, CrearOfertaFailure}
import com.ucuenca.gestion.utils.SessionManager

class CrearOfertaController {

  @FXML var txtTituloOferta: TextField = _
  @FXML var spnVacantes: Spinner[java.lang.Integer] = _
  @FXML var spnDuracionHoras: Spinner[java.lang.Integer] = _
  @FXML var txtDescripcionOferta: TextArea = _
  @FXML var txtRequisitosOferta: TextArea = _
  @FXML var txtActividadesOferta: TextArea = _
  @FXML var btnDescargarPlantilla: Button = _
  @FXML var btnSubirOfertaFirmada: Button = _
  @FXML var lblEstadoArchivoOferta: Label = _
  @FXML var lblEstado: Label = _

  private var pdfFile: java.io.File = _

  @FXML
  def initialize(): Unit = {
    // Configurar valores por defecto y límites de los Spinners para evitar inputs fuera de los permitidos por FXML
    // spnVacantes: 1 a 10
    // spnDuracionHoras: 40 a 400
    spnVacantes.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 10, 1))
    spnDuracionHoras.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(40, 400, 240, 10))
  }

  /**
   * Descarga la plantilla oficial de oferta a la ubicación elegida por el usuario.
   */
  @FXML
  def handleDescargarPlantilla(event: ActionEvent): Unit = {
    try {
      val srcFile = new java.io.File("docs/archivos_pdf/formato_de_oferta.pdf")
      if (!srcFile.exists()) {
        showError("Error: No se pudo encontrar el archivo de plantilla original en 'docs/archivos_pdf/formato_de_oferta.pdf'.")
        return
      }

      val fileChooser = new FileChooser()
      fileChooser.setTitle("Guardar Plantilla de Oferta")
      fileChooser.setInitialFileName("formato_de_oferta.pdf")
      fileChooser.getExtensionFilters.add(new FileChooser.ExtensionFilter("Archivos PDF (*.pdf)", "*.pdf"))

      val dest = fileChooser.showSaveDialog(btnDescargarPlantilla.getScene.getWindow)
      if (dest != null) {
        java.nio.file.Files.copy(
          srcFile.toPath,
          dest.toPath,
          java.nio.file.StandardCopyOption.REPLACE_EXISTING
        )
        showSuccess(s"Plantilla guardada exitosamente en: ${dest.getName}")
      }
    } catch {
      case scala.util.control.NonFatal(e) =>
        showError(s"Error al descargar la plantilla: ${e.getMessage}")
    }
  }

  /**
   * Abre un selector de archivos para subir el PDF de la oferta firmada.
   */
  @FXML
  def handleSubirOfertaFirmada(event: ActionEvent): Unit = {
    val fileChooser = new FileChooser()
    fileChooser.setTitle("Seleccionar Formato de Oferta Firmada (PDF)")
    fileChooser.getExtensionFilters.add(new FileChooser.ExtensionFilter("Archivos PDF (*.pdf)", "*.pdf"))
    val selected = fileChooser.showOpenDialog(btnSubirOfertaFirmada.getScene.getWindow)
    if (selected != null) {
      pdfFile = selected
      lblEstadoArchivoOferta.setText(selected.getName)
      showSuccess(s"Archivo cargado: ${selected.getName}")
    }
  }

  /**
   * Envía los datos capturados y publica la convocatoria.
   */
  @FXML
  def handlePublicarConvocatoria(event: ActionEvent): Unit = {
    // 1. Obtener la sesión activa de la empresa
    val activeUserOpt = SessionManager.getUsuario
    if (activeUserOpt.isEmpty) {
      showError("Error de sesión: No se encuentra una empresa autenticada en el sistema.")
      return
    }

    val empresaUser = activeUserOpt.get
    val rucEmpresa = empresaUser.identificacion

    // 2. Validar que se haya subido un PDF
    if (pdfFile == null) {
      showError("El archivo PDF firmado de la oferta es obligatorio.")
      return
    }

    // Copiar el archivo al directorio de subidas
    val pdfPath = copyFileToUploads(pdfFile, s"oferta_empresa_${rucEmpresa}_${System.currentTimeMillis()}.pdf")

    val titulo = txtTituloOferta.getText
    val vacantes = if (spnVacantes.getValue != null) spnVacantes.getValue.intValue() else 0
    val duracion = if (spnDuracionHoras.getValue != null) spnDuracionHoras.getValue.intValue() else 0
    val descripcion = txtDescripcionOferta.getText
    val requisitos = txtRequisitosOferta.getText
    val actividades = txtActividadesOferta.getText

    val dto = CrearOfertaDTO(
      rucEmpresaRef = rucEmpresa,
      tituloOferta = titulo,
      vacantesSolicitadas = vacantes,
      duracionHoras = duracion,
      descripcionGeneral = descripcion,
      requisitosObligatorios = requisitos,
      actividadesEspecificas = actividades,
      pdfNombreOriginal = pdfFile.getName,
      pdfRutaSegura = pdfPath
    )

    CrearOfertaLogic.crear(dto) match {
      case Right(ofertaId) =>
        showSuccess(s"¡Éxito! Convocatoria publicada con ID ${ofertaId} en estado PENDIENTE.")
        clearForm()
      case Left(CrearOfertaFailure.Validacion(msg)) =>
        showError(msg)
      case Left(CrearOfertaFailure.ErrorPersistencia(msg)) =>
        showError(s"Error al persistir la oferta: $msg")
    }
  }

  private def copyFileToUploads(source: java.io.File, destName: String): String = {
    val uploadsDir = new java.io.File("uploads")
    if (!uploadsDir.exists()) {
      uploadsDir.mkdirs()
    }
    val destFile = new java.io.File(uploadsDir, destName)
    java.nio.file.Files.copy(
      source.toPath,
      destFile.toPath,
      java.nio.file.StandardCopyOption.REPLACE_EXISTING
    )
    destFile.getAbsolutePath
  }

  private def clearForm(): Unit = {
    txtTituloOferta.clear()
    spnVacantes.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 10, 1))
    spnDuracionHoras.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(40, 400, 240, 10))
    txtDescripcionOferta.clear()
    txtRequisitosOferta.clear()
    txtActividadesOferta.clear()
    pdfFile = null
    lblEstadoArchivoOferta.setText("Ningún archivo seleccionado (.pdf)")
  }

  private def showSuccess(msg: String): Unit = {
    lblEstado.setText(msg)
    lblEstado.setStyle("-fx-text-fill: #059669; -fx-font-weight: bold;")
    lblEstado.setVisible(true)
  }

  private def showError(msg: String): Unit = {
    lblEstado.setText(msg)
    lblEstado.setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;")
    lblEstado.setVisible(true)
  }
}
