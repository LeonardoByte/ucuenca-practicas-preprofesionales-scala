package com.ucuenca.gestion.utils

import javafx.stage.Stage
import javafx.scene.Scene
import javafx.scene.Parent
import javafx.scene.layout.StackPane
import com.ucuenca.gestion.views._

object NavigationManager {
  private var stage: Stage = _

  def setStage(s: Stage): Unit = {
    stage = s
  }

  def getStage: Stage = stage

  def showLogin(): Unit = {
    if (stage == null) {
      throw new IllegalStateException("NavigationManager no ha sido inicializado con Stage")
    }
    val root = LoginView.loadNode()
    val scene = new Scene(root, 1024, 768)
    stage.setScene(scene)
    stage.setTitle("Gestión Prácticas - Acceso al Sistema")
    stage.centerOnScreen()
    stage.show()
  }

  def showMainForRole(rol: String): Unit = {
    if (stage == null) {
      throw new IllegalStateException("NavigationManager no ha sido inicializado con Stage")
    }

    val mainView: DynamicViewTemplate = rol match {
      case "ADMIN"               => AdministradorMainView
      case "COORDINADOR"         => CoordinadorMainView
      case "EMPRESA"             => EmpresaMainView
      case "ESTUDIANTE"          => EstudianteMainView
      case "SECRETARIA"          => SecretariaMainView
      case "TUTOR_ACADEMICO"     => TutorAcademicoMainView
      case "TUTOR_EMPRESARIAL"   => TutorEmpresarialMainView
      case _ => throw new IllegalArgumentException(s"Rol de usuario desconocido: $rol")
    }

    val root = mainView.loadNode()
    
    // Localizar el espacio central de visualización para cargar la sub-vista por defecto
    val centralPane = root.lookup("#panelCentralDisp").asInstanceOf[StackPane]
    if (centralPane != null) {
      val defaultSubView: DynamicViewTemplate = rol match {
        case "ADMIN"               => AdminEstadoSistemaView
        case "COORDINADOR"         => CoordinadorMetricasView
        case "EMPRESA"             => EmpresaCuadroMandoView
        case "ESTUDIANTE"          => EstudiantePerfilView
        case "SECRETARIA"          => SecretariaResumenView
        case "TUTOR_ACADEMICO"     => TutorAcadAlumnosView
        case "TUTOR_EMPRESARIAL"   => TutorEmpMisAlumnosView
      }
      defaultSubView.renderInto(centralPane)
    }

    val scene = new Scene(root, 1280, 850)
    stage.setScene(scene)
    stage.setTitle(s"Gestión Prácticas - Panel de $rol")
    stage.centerOnScreen()
  }
}
