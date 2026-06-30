package com.ucuenca.gestion.models.logic

import com.ucuenca.gestion.models.db._
import scala.util.control.NonFatal

sealed trait AnalyticsFailure
object AnalyticsFailure {
  case class ErrorCarga(mensaje: String) extends AnalyticsFailure
}

object AnalyticsLogic {

  // --- Reports & Aggregations ---

  def fetchActiveStudents(carreraId: Option[Int]): Either[AnalyticsFailure, List[ActiveStudentReportDTO]] = {
    try {
      Right(AnalyticsRepository.getActiveStudents(carreraId))
    } catch {
      case NonFatal(e) => Left(AnalyticsFailure.ErrorCarga(s"Error al cargar estudiantes activos: ${e.getMessage}"))
    }
  }

  def fetchTopHostCompanies(carreraId: Option[Int]): Either[AnalyticsFailure, List[TopCompanyReportDTO]] = {
    try {
      Right(AnalyticsRepository.getTopHostCompanies(carreraId))
    } catch {
      case NonFatal(e) => Left(AnalyticsFailure.ErrorCarga(s"Error al cargar ranking de empresas: ${e.getMessage}"))
    }
  }

  def fetchPostulationPercentages(carreraId: Option[Int]): Either[AnalyticsFailure, List[PostulationPeriodReportDTO]] = {
    try {
      Right(AnalyticsRepository.getPostulationPercentages(carreraId))
    } catch {
      case NonFatal(e) => Left(AnalyticsFailure.ErrorCarga(s"Error al calcular porcentajes de postulación: ${e.getMessage}"))
    }
  }

  def fetchSatisfactionAverages(carreraId: Option[Int]): Either[AnalyticsFailure, List[SatisfactionReportDTO]] = {
    try {
      Right(AnalyticsRepository.getSatisfactionAverages(carreraId))
    } catch {
      case NonFatal(e) => Left(AnalyticsFailure.ErrorCarga(s"Error al obtener satisfacción corporativa: ${e.getMessage}"))
    }
  }

  // --- Traceability & Life Timeline ---

  def searchPractices(criteria: String): Either[AnalyticsFailure, List[TraceabilityPracticeListItem]] = {
    try {
      Right(TraceabilityRepository.searchPractices(criteria))
    } catch {
      case NonFatal(e) => Left(AnalyticsFailure.ErrorCarga(s"Error en el buscador de auditoría: ${e.getMessage}"))
    }
  }

  def getTimeline(idPractica: Int): Either[AnalyticsFailure, PracticeTimelineDetails] = {
    try {
      TraceabilityRepository.getTimeline(idPractica) match {
        case Some(timeline) => Right(timeline)
        case None => Left(AnalyticsFailure.ErrorCarga("La práctica especificada no existe en el registro histórico."))
      }
    } catch {
      case NonFatal(e) => Left(AnalyticsFailure.ErrorCarga(s"Error al reconstruir la línea de vida: ${e.getMessage}"))
    }
  }

  def getRejectionComments(idPractica: Int): Either[AnalyticsFailure, List[RejectionCommentDTO]] = {
    try {
      Right(TraceabilityRepository.getRejectionComments(idPractica))
    } catch {
      case NonFatal(e) => Left(AnalyticsFailure.ErrorCarga(s"Error al recuperar comentarios de rechazo: ${e.getMessage}"))
    }
  }
}
