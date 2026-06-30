package com.ucuenca.gestion.models.logic

import com.ucuenca.gestion.models.db.DashboardRepository
import scala.util.control.NonFatal

sealed trait DashboardFailure
object DashboardFailure {
  case class ErrorCarga(mensaje: String) extends DashboardFailure
}

object DashboardLogic {

  def fetchAdminMetrics(): Either[DashboardFailure, Map[String, Int]] = {
    try {
      Right(DashboardRepository.getAdminMetrics())
    } catch {
      case NonFatal(e) => Left(DashboardFailure.ErrorCarga(e.getMessage))
    }
  }

  def fetchCoordinatorMetrics(): Either[DashboardFailure, Map[String, Int]] = {
    try {
      Right(DashboardRepository.getCoordinatorMetrics())
    } catch {
      case NonFatal(e) => Left(DashboardFailure.ErrorCarga(e.getMessage))
    }
  }

  def fetchCompanyMetrics(rucEmpresa: String): Either[DashboardFailure, Map[String, String]] = {
    try {
      Right(DashboardRepository.getCompanyMetrics(rucEmpresa))
    } catch {
      case NonFatal(e) => Left(DashboardFailure.ErrorCarga(e.getMessage))
    }
  }

  def fetchSecretaryMetrics(): Either[DashboardFailure, Map[String, Int]] = {
    try {
      Right(DashboardRepository.getSecretaryMetrics())
    } catch {
      case NonFatal(e) => Left(DashboardFailure.ErrorCarga(e.getMessage))
    }
  }

  def fetchAcademicTutorMetrics(tutorCI: String): Either[DashboardFailure, Map[String, Int]] = {
    try {
      Right(DashboardRepository.getAcademicTutorMetrics(tutorCI))
    } catch {
      case NonFatal(e) => Left(DashboardFailure.ErrorCarga(e.getMessage))
    }
  }

  def fetchCorporateTutorMetrics(tutorEmpCI: String): Either[DashboardFailure, Map[String, Int]] = {
    try {
      Right(DashboardRepository.getCorporateTutorMetrics(tutorEmpCI))
    } catch {
      case NonFatal(e) => Left(DashboardFailure.ErrorCarga(e.getMessage))
    }
  }
}
