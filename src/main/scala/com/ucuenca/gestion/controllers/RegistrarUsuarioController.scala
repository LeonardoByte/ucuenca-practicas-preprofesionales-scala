package com.ucuenca.gestion.controllers

import javafx.fxml.FXML
import javafx.scene.control._
import javafx.scene.layout._
import javafx.event.ActionEvent
import com.ucuenca.gestion.models.enums._
import com.ucuenca.gestion.models.entities._
import com.ucuenca.gestion.models.dto._
import com.ucuenca.gestion.models.db.{CarreraRepository, EmpresaRepository}
import com.ucuenca.gestion.models.logic.{RegistrarUsuarioLogic, RegistroFailure}
import scala.util.control.NonFatal

case class CarreraItem(id: Int, nombre: String) {
  override def toString: String = nombre
}

case class EmpresaItem(id: String, nombre: String) {
  override def toString: String = nombre
}

class RegistrarUsuarioController {

  // --- Campos Comunes FXML ---
  @FXML var txtNombres: TextField = _
  @FXML var txtCedula: TextField = _
  @FXML var txtEmail: TextField = _
  @FXML var txtPassword: PasswordField = _
  @FXML var cmbRolUsuario: ComboBox[RolUsuario] = _

  // --- Paneles de Especialización ---
  @FXML var paneEstudiante: VBox = _
  @FXML var paneEmpresa: VBox = _
  @FXML var paneTutorEmp: VBox = _
  @FXML var paneSinAtributos: VBox = _

  // --- Campos Estudiante ---
  @FXML var spnCiclo: Spinner[java.lang.Integer] = _
  @FXML var cmbCarrera: ComboBox[CarreraItem] = _
  @FXML var cmbEstadoMatricula: ComboBox[EstadoMatricula] = _
  @FXML var cmbEstadoPractica: ComboBox[EstadoEstudiantePractica] = _
  @FXML var btnSubirMalla: Button = _
  @FXML var lblMallaPdf: Label = _
  @FXML var btnSubirCV: Button = _
  @FXML var lblCvPdf: Label = _

  // --- Campos Empresa ---
  @FXML var txtMatriz: TextField = _
  @FXML var txtMision: TextArea = _
  @FXML var txtVision: TextArea = _
  @FXML var cmbEstadoConvenio: ComboBox[EstadoConvenio] = _

  // --- Campos Tutor Empresarial ---
  @FXML var cmbEmpresaAsociada: ComboBox[EmpresaItem] = _
  @FXML var txtTelfTutor: TextField = _

  // --- Estado e Interacción ---
  @FXML var lblEstado: Label = _
  @FXML var btnConsolidar: Button = _

  private var mallaFile: java.io.File = _
  private var cvFile: java.io.File = _

  /**
   * Método de inicialización automática de JavaFX.
   */
  @FXML
  def initialize(): Unit = {
    // 1. Configurar que los layouts colapsen al ocultarse
    paneEstudiante.managedProperty().bind(paneEstudiante.visibleProperty())
    paneEmpresa.managedProperty().bind(paneEmpresa.visibleProperty())
    paneTutorEmp.managedProperty().bind(paneTutorEmp.visibleProperty())
    paneSinAtributos.managedProperty().bind(paneSinAtributos.visibleProperty())

    // 2. Cargar enums del sistema en los comboboxes
    cmbRolUsuario.getItems.addAll(RolUsuario.values(): _*)
    cmbEstadoMatricula.getItems.addAll(EstadoMatricula.values(): _*)
    cmbEstadoPractica.getItems.addAll(EstadoEstudiantePractica.values(): _*)
    cmbEstadoConvenio.getItems.addAll(EstadoConvenio.values(): _*)

    // 3. Poblar dropdown de Carreras universitarias
    try {
      CarreraRepository.listarTodas().foreach { case (id, nombre) =>
        cmbCarrera.getItems.add(CarreraItem(id, nombre))
      }
    } catch {
      case NonFatal(e) =>
        println(s"Advertencia: No se pudieron cargar carreras de la base de datos: ${e.getMessage}")
    }

    // 4. Poblar dropdown de Empresas aliadas registradas
    try {
      EmpresaRepository.listarEmpresas().foreach { case (id, nombre) =>
        cmbEmpresaAsociada.getItems.add(EmpresaItem(id, nombre))
      }
    } catch {
      case NonFatal(e) =>
        println(s"Advertencia: No se pudieron cargar empresas de la base de datos: ${e.getMessage}")
    }

    // 5. Configurar listener dinámico para cambio de rol
    cmbRolUsuario.setOnAction { _ =>
      val selectedRol = cmbRolUsuario.getValue
      ocultarPanelesEspecificos()

      if (selectedRol != null) {
        selectedRol match {
          case RolUsuario.ESTUDIANTE =>
            paneEstudiante.setVisible(true)
          case RolUsuario.EMPRESA =>
            paneEmpresa.setVisible(true)
          case RolUsuario.TUTOR_EMPRESARIAL =>
            paneTutorEmp.setVisible(true)
          case RolUsuario.ADMIN | RolUsuario.COORDINADOR | RolUsuario.SECRETARIA | RolUsuario.TUTOR_ACADEMICO =>
            paneSinAtributos.setVisible(true)
        }
      }
    }
  }

  /**
   * Abre un selector de archivos para subir el PDF de la malla académica.
   */
  @FXML
  def handleSubirMalla(event: ActionEvent): Unit = {
    val fileChooser = new javafx.stage.FileChooser()
    fileChooser.setTitle("Seleccionar Malla Académica (PDF)")
    fileChooser.getExtensionFilters.add(new javafx.stage.FileChooser.ExtensionFilter("Archivos PDF (*.pdf)", "*.pdf"))
    val selected = fileChooser.showOpenDialog(btnSubirMalla.getScene.getWindow)
    if (selected != null) {
      mallaFile = selected
      lblMallaPdf.setText(selected.getName)
    }
  }

  /**
   * Abre un selector de archivos para subir el PDF del CV (Opcional).
   */
  @FXML
  def handleSubirCV(event: ActionEvent): Unit = {
    val fileChooser = new javafx.stage.FileChooser()
    fileChooser.setTitle("Seleccionar Curriculum Vitae (PDF)")
    fileChooser.getExtensionFilters.add(new javafx.stage.FileChooser.ExtensionFilter("Archivos PDF (*.pdf)", "*.pdf"))
    val selected = fileChooser.showOpenDialog(btnSubirCV.getScene.getWindow)
    if (selected != null) {
      cvFile = selected
      lblCvPdf.setText(selected.getName)
    }
  }

  /**
   * Envía los datos capturados y consolida el registro del usuario.
   */
  @FXML
  def handleRegistro(event: ActionEvent): Unit = {
    val rol = cmbRolUsuario.getValue
    if (rol == null) {
      showError("Por favor, seleccione un rol corporativo.")
      return
    }

    val id = txtCedula.getText
    val nombres = txtNombres.getText
    val email = txtEmail.getText
    val password = txtPassword.getText

    val resultado: Either[RegistroFailure, Unit] = rol match {
      case RolUsuario.ESTUDIANTE =>
        val carrera = cmbCarrera.getValue
        val matricula = cmbEstadoMatricula.getValue
        val practica = cmbEstadoPractica.getValue
        val ciclo = if (spnCiclo != null && spnCiclo.getValue != null) spnCiclo.getValue.intValue() else 5

        if (carrera == null || matricula == null || practica == null) {
          Left(RegistroFailure.Validacion("Todos los atributos específicos del estudiante son obligatorios."))
        } else if (mallaFile == null) {
          Left(RegistroFailure.Validacion("El archivo PDF de la malla académica es obligatorio."))
        } else {
          // Guardar archivos físicamente
          val mallaPath = copyFileToUploads(mallaFile, s"malla_${id}.pdf")
          val cvPathOpt = Option(cvFile).map(file => copyFileToUploads(file, s"cv_${id}.pdf"))

          val dto = EstudianteDTO(
            identificacion = id,
            nombresCompletos = nombres,
            correoElectronico = email,
            password = password,
            cicloActual = ciclo,
            idCarreraRef = carrera.id,
            estadoMatricula = matricula,
            estadoPractica = practica,
            mallaNombre = mallaFile.getName,
            mallaRuta = mallaPath,
            cvNombre = Option(cvFile).map(_.getName),
            cvRuta = cvPathOpt
          )
          RegistrarUsuarioLogic.registrarEstudiante(dto)
        }

      case RolUsuario.EMPRESA =>
        val matriz = txtMatriz.getText
        val mision = txtMision.getText
        val vision = txtVision.getText
        val convenio = cmbEstadoConvenio.getValue

        if (convenio == null) {
          Left(RegistroFailure.Validacion("El estado del convenio legal es obligatorio."))
        } else {
          val dto = EmpresaDTO(
            identificacion = id,
            nombresCompletos = nombres,
            correoElectronico = email,
            password = password,
            direccionMatriz = matriz,
            mision = mision,
            vision = vision,
            estadoConvenio = convenio
          )
          RegistrarUsuarioLogic.registrarEmpresa(dto)
        }

      case RolUsuario.TUTOR_EMPRESARIAL =>
        val empresa = cmbEmpresaAsociada.getValue
        val telf = txtTelfTutor.getText

        if (empresa == null) {
          Left(RegistroFailure.Validacion("Debe asociar al tutor a una empresa registrada."))
        } else {
          val dto = TutorEmpresarialDTO(
            identificacion = id,
            nombresCompletos = nombres,
            correoElectronico = email,
            password = password,
            empresaIdRef = empresa.id,
            telefonoContacto = telf
          )
          RegistrarUsuarioLogic.registrarTutorEmpresarial(dto)
        }

      case _ => // Admin, Coordinador, Secretaría, Tutor Académico
        val dto = UsuarioGeneralDTO(
          identificacion = id,
          nombresCompletos = nombres,
          correoElectronico = email,
          password = password,
          rol = rol
        )
        RegistrarUsuarioLogic.registrarUsuarioGeneral(dto)
    }

    resultado match {
      case Right(_) =>
        showSuccess(s"¡Registro exitoso! Cuenta '${email}' creada con ID ${id}.")
        clearForm()
      case Left(RegistroFailure.Validacion(msg)) =>
        showError(msg)
      case Left(RegistroFailure.ErrorPersistencia(msg)) =>
        showError(s"Error técnico de base de datos: $msg")
    }
  }

  // --- Auxiliares ---

  private def ocultarPanelesEspecificos(): Unit = {
    paneEstudiante.setVisible(false)
    paneEmpresa.setVisible(false)
    paneTutorEmp.setVisible(false)
    paneSinAtributos.setVisible(false)
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
    txtCedula.clear()
    txtNombres.clear()
    txtEmail.clear()
    txtPassword.clear()
    
    // Campos Estudiante
    cmbCarrera.setValue(null)
    cmbEstadoMatricula.setValue(null)
    cmbEstadoPractica.setValue(null)
    mallaFile = null
    cvFile = null
    lblMallaPdf.setText("Ningún archivo seleccionado (.pdf)")
    lblCvPdf.setText("Opcional - Puede actualizarse luego (.pdf)")

    // Campos Empresa
    txtMatriz.clear()
    txtMision.clear()
    txtVision.clear()
    cmbEstadoConvenio.setValue(null)

    // Campos Tutor
    cmbEmpresaAsociada.setValue(null)
    txtTelfTutor.clear()
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
