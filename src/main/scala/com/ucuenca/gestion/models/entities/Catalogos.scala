package com.ucuenca.gestion.models.entities

import com.ucuenca.gestion.models.enums._
import java.time.{LocalDate, LocalDateTime}

case class Carrera(
  idCarrera: Int,
  nombreCarrera: String
)

case class PeriodoAcademico(
  idPeriodo: Int,
  descripcion: String
)

case class SolicitudConvenio(
  idSolicitudConvenio: Int,
  rucEmpresa: String,
  razonSocial: String,
  representanteLegal: String,
  direccionMatriz: String,
  mision: String,
  vision: String,
  convenioDocumentoPDF: Int,
  estadoConvenio: EstadoConvenio,
  causasRechazoSecretaria: Option[String],
  fechaPresentacion: LocalDate
) {
  def esPendiente: Boolean = estadoConvenio == EstadoConvenio.PENDIENTE
  def fueAprobada: Boolean = estadoConvenio == EstadoConvenio.FORMALIZADO
}

case class ValidacionCartaCompromiso(
  ciEstudiante: String,
  fechaValidacion: LocalDateTime,
  entregadoTresCopias: Boolean,
  cartaCompromisoPDF: Int
)

case class OfertaConvocatoria(
  idOferta: Int,
  rucEmpresaRef: String,
  tituloOferta: String,
  vacantesSolicitadas: Int,
  duracionHoras: Int,
  descripcionGeneral: String,
  requisitosObligatorios: String,
  actividadesEspecificas: String,
  plantillaOfertaPDF: Int,
  estadoOferta: EstadoOferta,
  justificacionCoordinador: Option[String],
  fechaPublicacion: Option[LocalDate]
) {
  def estaAbierta: Boolean              = estadoOferta == EstadoOferta.APROBADA
  def cuposDisponibles(aceptados: Int): Int = vacantesSolicitadas - aceptados
  def debeAutoCerrar(aceptados: Int): Boolean = aceptados >= vacantesSolicitadas
}

case class PostulacionBolsa(
  idPostulacion: Int,
  ciEstudianteRef: String,
  idOfertaRef: Int,
  fechaPostulacion: LocalDate,
  estadoPostulacion: EstadoPostulacion,
  comentarioRechazo: Option[String]
) {
  def estaActiva: Boolean =
    estadoPostulacion == EstadoPostulacion.PENDIENTE ||
    estadoPostulacion == EstadoPostulacion.APROBADA
  def fueAceptada: Boolean = estadoPostulacion == EstadoPostulacion.APROBADA
}

case class SolicitudEmpresaPropia(
  idSolicitudPropia: Int,
  ciEstudianteRef: String,
  // Company identification
  nombreEntidadExterna: String,
  rucEmpresaPropia: String,
  contactoEmpresaPropia: String,
  horasEmpresaPropia: Int,
  // JIT company profile fields (materialize into empresa_perfil on approval)
  direccionEmpresaPropia: String,
  misionEmpresaPropia: String,
  visionEmpresaPropia: String,
  // Core office transcript
  contenidoOficioTranscrito: String,
  // JIT external supervisor fields (materialize into tutor_empresarial_perfil on approval)
  ciSupervisorExterno: String,
  nombresSupervisorExterno: String,
  emailSupervisorExterno: String,
  telefonoSupervisorExterno: String,
  // Documents and bilateral resolution flow
  oficioSolicitudInicialPDF: Int,
  oficioPresentacionVueltaPDF: Option[Int],
  codigoOficioVuelta: Option[String],
  idTutorAcadAsignado: Option[String],
  estadoTramite: EstadoConvenio,
  justificacionDenegacion: Option[String],
  fechaRegistro: LocalDate
) {
  def esPendiente: Boolean        = estadoTramite == EstadoConvenio.PENDIENTE
  def fueAprobada: Boolean        = estadoTramite == EstadoConvenio.FORMALIZADO
  def tieneTutorAsignado: Boolean = idTutorAcadAsignado.isDefined
}
