package com.ucuenca.gestion.views

import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.layout.StackPane

/**
 * Trait Base para los Inicializadores de Escenas Base y Paneles de Contenido en Scala.
 * Define la estructura declarativa sin lógica funcional.
 */
trait DynamicViewTemplate {
  def fxmlResourcePath: String

  /**
   * Carga de forma declarativa el nodo raíz del FXML.
   */
  def loadNode(): Parent = {
    val loader = new FXMLLoader(getClass.getResource(fxmlResourcePath))
    loader.load[Parent]()
  }

  /**
   * Mecanismo de Ventana Única: Reemplaza por completo el contenido visual
   * del panel central pasándole el contenedor principal.
   */
  def renderInto(centerPane: StackPane): Unit = {
    centerPane.getChildren.clear()
    centerPane.getChildren.add(loadNode())
  }
}

// ============================================================================
// INICIALIZADORES BASE DE LAS VENTANAS PRINCIPALES (Contenedores con MenuBar)
// ============================================================================

object LoginView extends DynamicViewTemplate {
  override val fxmlResourcePath: String = "/fxml/global/LoginView.fxml"
}

object AdministradorMainView extends DynamicViewTemplate {
  override val fxmlResourcePath: String = "/fxml/global/AdministradorMain.fxml"
}

object CoordinadorMainView extends DynamicViewTemplate {
  override val fxmlResourcePath: String = "/fxml/global/CoordinadorMain.fxml"
}

object EmpresaMainView extends DynamicViewTemplate {
  override val fxmlResourcePath: String = "/fxml/global/EmpresaMain.fxml"
}

object EstudianteMainView extends DynamicViewTemplate {
  override val fxmlResourcePath: String = "/fxml/global/EstudianteMain.fxml"
}

object SecretariaMainView extends DynamicViewTemplate {
  override val fxmlResourcePath: String = "/fxml/global/SecretariaMain.fxml"
}

object TutorAcademicoMainView extends DynamicViewTemplate {
  override val fxmlResourcePath: String = "/fxml/global/TutorAcademicoMain.fxml"
}

object TutorEmpresarialMainView extends DynamicViewTemplate {
  override val fxmlResourcePath: String = "/fxml/global/TutorEmpresarialMain.fxml"
}


// ============================================================================
// 1. MÓDULO: ADMINISTRADOR (Subpantallas)
// ============================================================================

object AdminEstadoSistemaView extends DynamicViewTemplate {
  override val fxmlResourcePath: String = "/fxml/admin/EstadoSistema.fxml"
}

object AdminRegistrarUsuarioView extends DynamicViewTemplate {
  override val fxmlResourcePath: String = "/fxml/admin/RegistrarUsuario.fxml"
}

object AdminDirectorioGeneralView extends DynamicViewTemplate {
  override val fxmlResourcePath: String = "/fxml/admin/DirectorioGeneral.fxml"
}

object AdminTrazabilidadView extends DynamicViewTemplate {
  override val fxmlResourcePath: String = "/fxml/admin/Trazabilidad.fxml"
}

object AdminGenerarEstadisticasView extends DynamicViewTemplate {
  override val fxmlResourcePath: String = "/fxml/admin/GenerarEstadisticas.fxml"
}


// ============================================================================
// 2. MÓDULO: COORDINADOR (Subpantallas)
// ============================================================================

object CoordinadorMetricasView extends DynamicViewTemplate {
  override val fxmlResourcePath: String = "/fxml/coordinador/MetricasGenerales.fxml"
}

object CoordinadorRevisionOfertasView extends DynamicViewTemplate {
  override val fxmlResourcePath: String = "/fxml/coordinador/RevisionOfertas.fxml"
}

object CoordinadorValidarAlumnosView extends DynamicViewTemplate {
  override val fxmlResourcePath: String = "/fxml/coordinador/ValidarAlumnos.fxml"
}

object CoordinadorRevisionOficiosView extends DynamicViewTemplate {
  override val fxmlResourcePath: String = "/fxml/coordinador/RevisionOficios.fxml"
}

object CoordinadorAsignarDocentesView extends DynamicViewTemplate {
  override val fxmlResourcePath: String = "/fxml/coordinador/AsignarDocentes.fxml"
}

object CoordinadorAutorizarIniciosView extends DynamicViewTemplate {
  override val fxmlResourcePath: String = "/fxml/coordinador/AutorizarInicios.fxml"
}

object CoordinadorAuditoriaExpedientesView extends DynamicViewTemplate {
  override val fxmlResourcePath: String = "/fxml/coordinador/AuditoriaExpedientes.fxml"
}


// ============================================================================
// 3. MÓDULO: EMPRESA (Subpantallas)
// ============================================================================

object EmpresaCuadroMandoView extends DynamicViewTemplate {
  override val fxmlResourcePath: String = "/fxml/empresa/CuadroMando.fxml"
}

object EmpresaCrearOfertaView extends DynamicViewTemplate {
  override val fxmlResourcePath: String = "/fxml/empresa/CrearOferta.fxml"
}

object EmpresaHistorialOfertasView extends DynamicViewTemplate {
  override val fxmlResourcePath: String = "/fxml/empresa/HistorialOfertas.fxml"
}

object EmpresaGestionCandidatosView extends DynamicViewTemplate {
  override val fxmlResourcePath: String = "/fxml/empresa/GestionCandidatos.fxml"
}

object EmpresaTramitarConvenioView extends DynamicViewTemplate {
  override val fxmlResourcePath: String = "/fxml/empresa/TramitarConvenio.fxml"
}


// ============================================================================
// 4. MÓDULO: ESTUDIANTE (Subpantallas)
// ============================================================================

object EstudiantePerfilView extends DynamicViewTemplate {
  override val fxmlResourcePath: String = "/fxml/estudiante/MiPerfil.fxml"
}

object EstudianteBuscarVacantesView extends DynamicViewTemplate {
  override val fxmlResourcePath: String = "/fxml/estudiante/BuscarVacantes.fxml"
}

object EstudianteRegistrarSolicitudView extends DynamicViewTemplate {
  override val fxmlResourcePath: String = "/fxml/estudiante/RegistrarSolicitud.fxml"
}

object EstudianteHistorialPostulacionesView extends DynamicViewTemplate {
  override val fxmlResourcePath: String = "/fxml/estudiante/HistorialPostulaciones.fxml"
}

object EstudianteInfoGeneralView extends DynamicViewTemplate {
  override val fxmlResourcePath: String = "/fxml/estudiante/InfoGeneral.fxml"
}

object EstudiantePropuestaActividadesView extends DynamicViewTemplate {
  override val fxmlResourcePath: String = "/fxml/estudiante/PropuestaActividades.fxml"
}

object EstudianteFormulario1View extends DynamicViewTemplate {
  override val fxmlResourcePath: String = "/fxml/estudiante/Formulario1.fxml"
}


// ============================================================================
// 5. MÓDULO: SECRETARÍA (Subpantallas)
// ============================================================================

object SecretariaResumenView extends DynamicViewTemplate {
  override val fxmlResourcePath: String = "/fxml/secretaria/ResumenOperativo.fxml"
}

object SecretariaValidarEntregaView extends DynamicViewTemplate {
  override val fxmlResourcePath: String = "/fxml/secretaria/ValidarEntrega.fxml"
}

object SecretariaSolicitudesConvenioView extends DynamicViewTemplate {
  override val fxmlResourcePath: String = "/fxml/secretaria/SolicitudesConvenio.fxml"
}


// ============================================================================
// 6. MÓDULO: TUTOR ACADÉMICO (Subpantallas)
// ============================================================================

object TutorAcadAlumnosView extends DynamicViewTemplate {
  override val fxmlResourcePath: String = "/fxml/tutor_acad/AlumnosTutorados.fxml"
}

object TutorAcadValidarActividadesView extends DynamicViewTemplate {
  override val fxmlResourcePath: String = "/fxml/tutor_acad/ValidarActividades.fxml"
}

object TutorAcadFirmarFormulario1View extends DynamicViewTemplate {
  override val fxmlResourcePath: String = "/fxml/tutor_acad/FirmarFormulario1Academico.fxml"
}

object TutorAcadRevisarFormulario2View extends DynamicViewTemplate {
  override val fxmlResourcePath: String = "/fxml/tutor_acad/RevisarFormulario2.fxml"
}

object TutorAcadEmitirFormulario3View extends DynamicViewTemplate {
  override val fxmlResourcePath: String = "/fxml/tutor_acad/EmitirFormulario3.fxml"
}


// ============================================================================
// 7. MÓDULO: TUTOR EMPRESARIAL (Subpantallas)
// ============================================================================

object TutorEmpMisAlumnosView extends DynamicViewTemplate {
  override val fxmlResourcePath: String = "/fxml/tutor_emp/MisAlumnos.fxml"
}

object TutorEmpPlanificarActividadesView extends DynamicViewTemplate {
  override val fxmlResourcePath: String = "/fxml/tutor_emp/PlanificarActividades.fxml"
}

object TutorEmpFirmarFormulario1View extends DynamicViewTemplate {
  override val fxmlResourcePath: String = "/fxml/tutor_emp/FirmarFormulario1Empresarial.fxml"
}

object TutorEmpFormulario2View extends DynamicViewTemplate {
  override val fxmlResourcePath: String = "/fxml/tutor_emp/Formulario2.fxml"
}