package com.ucuenca.gestion.models.entities

import com.ucuenca.gestion.models.enums._
import java.time.{LocalDate, LocalDateTime}

case class PracticaRegistro(
  idPractica: Int,
  ciEstudianteRef: String,
  rucEmpresaRef: String,
  idTutorAcademicoRef: Option[String],
  idTutorEmpresarialRef: String,
  origenRama: OrigenRama,
  estadoCronograma: EstadoCronograma,
  horasAcumuladas: Int,
  horasTotalesRequeridas: Int,
  notaFinal: Option[BigDecimal]
) {
  def horasCompletas: Boolean   = horasAcumuladas >= horasTotalesRequeridas
  def estaEnDesarrollo: Boolean = estadoCronograma == EstadoCronograma.EN_DESARROLLO
  def estaCerrada: Boolean      = estadoCronograma == EstadoCronograma.CERRADA_VALIDA
  def puedeIniciarCierre: Boolean = horasCompletas && estaEnDesarrollo
  def progreso: Double =
    if (horasTotalesRequeridas == 0) 0.0
    else math.min(horasAcumuladas.toDouble / horasTotalesRequeridas, 1.0)
}

case class ActividadCronograma(
  idActividad: Int,
  idPracticaRef: Int,
  numeroSecuencial: Int,
  descripcionTarea: String,
  origenCreacion: OrigenCreacionActividad,
  estadoActividad: EstadoActividad,
  comentarioObservacion: Option[String],
  fechaRegistro: LocalDate
) {
  def estaAprobada: Boolean  = estadoActividad == EstadoActividad.APROBADA
  def estaRechazada: Boolean = estadoActividad == EstadoActividad.RECHAZADA
}

object ExpedienteFormulario1 {
  val MIN_ACTIVIDADES = 3
  val MAX_ACTIVIDADES = 6
  def actividadesValidas(aprobadas: Int): Boolean =
    aprobadas >= MIN_ACTIVIDADES && aprobadas <= MAX_ACTIVIDADES
}

case class ExpedienteFormulario1(
  idExpedienteF1: Int,
  idPracticaRef: Int,
  formulario1PDF: Option[Int],
  firmaEmpresarialValida: Boolean,
  firmaAcademicaValida: Boolean,
  estadoDeCoordinador: Boolean,
  justificacionRechazoInicio: Option[String],
  fechaAutorizacion: Option[LocalDate]
) {
  def tieneTodasLasFirmas: Boolean  = firmaEmpresarialValida && firmaAcademicaValida
  def fueAutorizado: Boolean        = estadoDeCoordinador
  def listoParaCoordinador: Boolean = tieneTodasLasFirmas && formulario1PDF.isDefined
}

case class Formulario2Evaluacion(
  idF2Evaluacion: Int,
  idPracticaRef: Int,
  formulario2PDF: Int,
  estadoFormulario2: EstadoFormulario2,
  justificacionRechazoDocente: Option[String],
  contenidoRubricaIndexado: String,
  fechaRegistro: LocalDateTime
) {
  def estaConforme: Boolean        = estadoFormulario2 == EstadoFormulario2.CONFORME
  def estaRechazado: Boolean       = estadoFormulario2 == EstadoFormulario2.RECHAZADO
  def desbloqueaFormulario3: Boolean = estaConforme
}

case class Formulario3Informe(
  idF3Informe: Int,
  idPracticaRef: Int,
  formulario3PDF: Int,
  fechaEmision: LocalDate
)

object AuditoriaCierre {
  val EN_REVISION = "EN_REVISION"
  val APROBADO    = "APROBADO"
  val RECHAZADO   = "RECHAZADO"
}

case class AuditoriaCierre(
  idAuditoria: Int,
  idPracticaRef: Int,
  secuencialVersion: Int,
  estadoAuditoria: String,
  observacionesExpediente: Option[String],
  validacionFisicaSecretariaSincronizada: Boolean
) {
  def estaAprobada: Boolean  = estadoAuditoria == AuditoriaCierre.APROBADO
  def estaRechazada: Boolean = estadoAuditoria == AuditoriaCierre.RECHAZADO
}
