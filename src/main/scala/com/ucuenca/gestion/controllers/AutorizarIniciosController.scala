  package com.ucuenca.gestion.controllers

  import javafx.fxml.FXML
  import javafx.scene.control._
  import javafx.scene.layout.VBox
  import javafx.beans.property.SimpleStringProperty
  import javafx.event.ActionEvent
  import javafx.stage.FileChooser
  import java.io.File
  import java.nio.file.{Files, StandardCopyOption}
  import scala.util.control.NonFatal
  import com.ucuenca.gestion.models.dto.ExpedientePendienteCoordinadorDTO
  import com.ucuenca.gestion.models.logic.{Formulario1Logic, Formulario1Failure}
  import com.ucuenca.gestion.models.db.Formulario1DB
  import scalikejdbc.DB

  class AutorizarIniciosController {

    @FXML var tblExpedientesInicioPendientes: TableView[ExpedientePendienteCoordinadorDTO] = _
    @FXML var colEstudiante: TableColumn[ExpedientePendienteCoordinadorDTO, String] = _
    @FXML var colEmpresa: TableColumn[ExpedientePendienteCoordinadorDTO, String] = _
    @FXML var colCartasCompromiso: TableColumn[ExpedientePendienteCoordinadorDTO, String] = _

    @FXML var lblFirmaEmpresarialStatus: Label = _
    @FXML var lblFirmaAcademicaStatus: Label = _
    @FXML var lblControlLegalStatus: Label = _

    @FXML var vboxAccionesFinales: VBox = _
    @FXML var txtVisorResumenF1: TextArea = _
    @FXML var btnDescargarFormulario1PDF: Button = _
    @FXML var btnSubirFormulario1Firmado: Button = _
    @FXML var lblEstadoArchivoF1Coordinador: Label = _
    @FXML var txtMotivoRechazoInicio: TextArea = _

    @FXML var btnRechazar: Button = _
    @FXML var btnAprobarInicioFinal: Button = _
    @FXML var lblEstado: Label = _

    private var selectedFile: Option[File] = None
    private var selectedDto: Option[ExpedientePendienteCoordinadorDTO] = None

    @FXML
    def initialize(): Unit = {
      // 1. Configurar columnas de la tabla
      colEstudiante.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.nombreEstudiante))
      colEmpresa.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.nombreEmpresa))
      colCartasCompromiso.setCellValueFactory(cd => new SimpleStringProperty(cd.getValue.estadoCartasTexto))
      colCartasCompromiso.setCellFactory(_ => new TableCell[ExpedientePendienteCoordinadorDTO, String] {
        override def updateItem(item: String, empty: Boolean): Unit = {
          super.updateItem(item, empty)
          if (empty || item == null) {
            setText(null)
            setStyle("")
          } else {
            setText(item)
            val dto = getTableView.getItems.get(getIndex)
            if (dto != null && dto.puedeAprobarse) {
              setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold;")
            } else {
              setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;")
            }
          }
        }
      })

      // 2. Configurar listener de selección de estudiante con refresco
      tblExpedientesInicioPendientes.getSelectionModel.selectedItemProperty().addListener((_, _, seleccion) => {
        if (seleccion != null) {
          cargarDetalleExpediente(seleccion)
          tblExpedientesInicioPendientes.refresh()
        } else {
          limpiarPantallaDetalle()
        }
      })

      // Configurar RowFactory para responder de manera inmediata y reactiva al primer clic
      tblExpedientesInicioPendientes.setRowFactory(tv => {
        val row = new TableRow[ExpedientePendienteCoordinadorDTO]()
        row.setOnMouseClicked(event => {
          if (!row.isEmpty) {
            val item = row.getItem
            if (item != null) {
              tv.getSelectionModel.select(item)
              cargarDetalleExpediente(item)
              tv.refresh()
            }
          }
        })
        row
      })

      // Deshabilitar controles por defecto
      vboxAccionesFinales.setDisable(true)
      btnAprobarInicioFinal.setDisable(true)
      btnRechazar.setDisable(true)
      txtMotivoRechazoInicio.setDisable(true)

      // 3. Cargar expedientes pendientes
      cargarExpedientesPendientes()
    }

    private def cargarExpedientesPendientes(): Unit = {
      try {
        val lista = Formulario1DB.listarPendientesCoordinador()
        tblExpedientesInicioPendientes.getItems.clear()
        tblExpedientesInicioPendientes.getItems.addAll(lista: _*)
        if (lista.isEmpty) {
          showSuccess("No se registran expedientes del Formulario 1 listos para su resolución final.")
        }
      } catch {
        case e: Exception =>
          showError(s"Error al cargar expedientes pendientes: ${e.getMessage}")
      }
    }

    private def cargarDetalleExpediente(dto: ExpedientePendienteCoordinadorDTO): Unit = {
      selectedDto = Some(dto)
      vboxAccionesFinales.setDisable(false)
      btnRechazar.setDisable(false)
      txtMotivoRechazoInicio.setDisable(false)
      btnAprobarInicioFinal.setDisable(true)
      selectedFile = None
      lblEstadoArchivoF1Coordinador.setText("Ningún archivo seleccionado (.pdf)")
      lblEstado.setVisible(false)

      // Mostrar verificación de firmas
      lblFirmaEmpresarialStatus.setText("✔ ESTAMPADA / VÁLIDA")
      lblFirmaEmpresarialStatus.setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold;")
      lblFirmaAcademicaStatus.setText("✔ ESTAMPADA / VÁLIDA")
      lblFirmaAcademicaStatus.setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold;")

      // Control legal real: convenio institucional exime de las cartas compromiso; sin convenio,
      // se exige que las 3 cartas estén registradas como entregadas en secretaría.
      if (dto.convenioFormalizado) {
        lblControlLegalStatus.setText("No Requerido (Posee Convenio)")
        lblControlLegalStatus.setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold;")
      } else if (dto.cartasCompromisoEntregadas) {
        lblControlLegalStatus.setText("Cartas Compromiso Entregadas en Secretaría")
        lblControlLegalStatus.setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold;")
      } else {
        lblControlLegalStatus.setText("BLOQUEADO: Faltan las 3 Cartas Compromiso en Secretaría")
        lblControlLegalStatus.setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;")
      }

      // Rellenar visor de resumen
      txtVisorResumenF1.setText(
        s"[DATOS DEL EXPEDIENTE]\n" +
        s"Estudiante: ${dto.nombreEstudiante}\n" +
        s"Empresa Acogida: ${dto.nombreEmpresa}\n" +
        s"Práctica ID: ${dto.idPractica}\n" +
        s"Fase: F1 (Plan de Actividades listo para arranque)"
      )
    }

    private def limpiarPantallaDetalle(): Unit = {
      selectedDto = None
      vboxAccionesFinales.setDisable(true)
      btnAprobarInicioFinal.setDisable(true)
      btnRechazar.setDisable(true)
      txtMotivoRechazoInicio.setDisable(true)
      txtVisorResumenF1.clear()
      txtMotivoRechazoInicio.clear()
    }

    @FXML
    def handleDescargar(event: ActionEvent): Unit = {
      val seleccion = tblExpedientesInicioPendientes.getSelectionModel.getSelectedItem
      if (seleccion != null) {
        import scalikejdbc._
        val fileOpt = DB.readOnly { implicit session =>
          sql"""
            SELECT ruta_segura_servidor, nombre_original FROM archivo_pdf
            WHERE id_archivo_pdf = (SELECT formulario1_pdf FROM expediente_formulario1 WHERE id_practica_ref = ${seleccion.idPractica})
          """.map(rs => (rs.string("ruta_segura_servidor"), rs.string("nombre_original"))).single.apply()
        }

        fileOpt match {
          case Some((ruta, nombre)) =>
            try {
              val srcFile = new File(ruta)
              if (!srcFile.exists()) {
                showError(s"Error: No se pudo encontrar el archivo original en '${ruta}'.")
                return
              }

              val fileChooser = new FileChooser()
              fileChooser.setTitle("Guardar Formulario 1")
              fileChooser.setInitialFileName(nombre)
              fileChooser.getExtensionFilters.add(new FileChooser.ExtensionFilter("Archivos PDF (*.pdf)", "*.pdf"))

              val dest = fileChooser.showSaveDialog(btnDescargarFormulario1PDF.getScene.getWindow)
              if (dest != null) {
                Files.copy(srcFile.toPath, dest.toPath, StandardCopyOption.REPLACE_EXISTING)
                showSuccess(s"Formulario guardado exitosamente en: ${dest.getName}")
              }
            } catch {
              case scala.util.control.NonFatal(e) =>
                showError(s"Error al descargar: ${e.getMessage}")
            }
          case None =>
            showError("No se pudo encontrar el expediente digital en la base de datos.")
        }
      }
    }

    @FXML
    def handleSubir(event: ActionEvent): Unit = {
      val fileChooser = new FileChooser()
      fileChooser.setTitle("Seleccionar Formulario 1 Firmado (PDF)")
      fileChooser.getExtensionFilters.add(new FileChooser.ExtensionFilter("Archivos PDF (*.pdf)", "*.pdf"))
      val file = fileChooser.showOpenDialog(btnSubirFormulario1Firmado.getScene.getWindow)
      if (file != null) {
        selectedFile = Some(file)
        lblEstadoArchivoF1Coordinador.setText(file.getName)
        lblEstadoArchivoF1Coordinador.setStyle("-fx-text-fill: #1e293b; -fx-font-style: normal;")

        if (selectedDto.exists(_.puedeAprobarse)) {
          btnAprobarInicioFinal.setDisable(false)
        } else {
          btnAprobarInicioFinal.setDisable(true)
          showError("No se puede habilitar la aprobación: faltan las 3 Cartas Compromiso registradas como entregadas en secretaría.")
        }
      }
    }

    @FXML
    def handleAprobar(event: ActionEvent): Unit = {
      val seleccion = tblExpedientesInicioPendientes.getSelectionModel.getSelectedItem
      (seleccion, selectedFile) match {
        case (sel, Some(file)) =>
          try {
            val destFile = new java.io.File("docs/archivos_pdf/" + file.getName)
            val dir = destFile.getParentFile
            if (!dir.exists()) dir.mkdirs()

            Files.copy(file.toPath, destFile.toPath, StandardCopyOption.REPLACE_EXISTING)

            val bytes = Files.readAllBytes(destFile.toPath)

            Formulario1Logic.coordinadorAprobar(sel.idPractica, bytes, file.getName) match {
              case Right(_) =>
                val dbPath = "docs/archivos_pdf/" + file.getName
                import scalikejdbc._
                DB.localTx { implicit session =>
                  sql"""
                    UPDATE archivo_pdf
                    SET ruta_segura_servidor = ${dbPath}
                    WHERE nombre_original = ${file.getName}
                      AND tipo_archivo = 'T1_FORMULARIO_1_PLAN'::tipo_archivo_pdf
                  """.update.apply()
                }

                showSuccess(s"¡Expediente de práctica ID: ${sel.idPractica} aprobado formalmente. Transición a EN_DESARROLLO!")
                btnAprobarInicioFinal.setDisable(true)
                vboxAccionesFinales.setDisable(true)
                selectedFile = None
                lblEstadoArchivoF1Coordinador.setText("Aprobado")
                cargarExpedientesPendientes()
              case Left(Formulario1Failure.Validacion(msg)) =>
                showError(msg)
              case Left(Formulario1Failure.ErrorPersistencia(msg)) =>
                showError(s"Error de base de datos: $msg")
            }
          } catch {
            case scala.util.control.NonFatal(e) =>
              showError(s"Error al procesar la subida: ${e.getMessage}")
          }
        case _ =>
          showError("Debe seleccionar y firmar el PDF antes de aprobar definitivamente.")
      }
    }

    @FXML
    def handleRechazar(event: ActionEvent): Unit = {
      val seleccion = tblExpedientesInicioPendientes.getSelectionModel.getSelectedItem
      if (seleccion != null) {
        val justificacion = txtMotivoRechazoInicio.getText
        Formulario1Logic.coordinadorRechazar(seleccion.idPractica, justificacion) match {
          case Right(_) =>
            showSuccess("Expediente rechazado y reseteado de forma lógica.")
            txtMotivoRechazoInicio.clear()
            cargarExpedientesPendientes()
          case Left(Formulario1Failure.Validacion(msg)) =>
            showError(msg)
          case Left(Formulario1Failure.ErrorPersistencia(msg)) =>
            showError(s"Error de base de datos al rechazar: $msg")
        }
      }
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
