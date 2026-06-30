package com.ucuenca.gestion.models.logic

import com.ucuenca.gestion.models.dto.StudentTutoradoDTO
import com.ucuenca.gestion.models.entities.ActividadCronograma
import com.ucuenca.gestion.models.enums.EstadoActividad
import com.ucuenca.gestion.models.db.CronogramaRepository
import scalikejdbc.DB
import scala.util.control.NonFatal

sealed trait ValidacionActividadesFailure
object ValidacionActividadesFailure {
  case class Validacion(mensaje: String)        extends ValidacionActividadesFailure
  case class ErrorPersistencia(mensaje: String) extends ValidacionActividadesFailure
}

object ValidacionActividadesLogic {

  /**
   * Obtiene los estudiantes asignados a un tutor académico.
   */
  def listarEstudiantes(tutorCI: String): Either[ValidacionActividadesFailure, List[StudentTutoradoDTO]] = {
    try {
      Right(CronogramaRepository.listarEstudiantesTutorados(tutorCI))
    } catch {
      case NonFatal(e) =>
        Left(ValidacionActividadesFailure.ErrorPersistencia(s"Error al listar estudiantes tutorados: ${e.getMessage}"))
    }
  }

  /**
   * Obtiene las actividades con estado PENDIENTE de una práctica.
   */
  def listarActividadesPendientes(idPractica: Int): Either[ValidacionActividadesFailure, List[ActividadCronograma]] = {
    try {
      Right(CronogramaRepository.listarActividadesPendientesPorPractica(idPractica))
    } catch {
      case NonFatal(e) =>
        Left(ValidacionActividadesFailure.ErrorPersistencia(s"Error al obtener actividades pendientes: ${e.getMessage}"))
    }
  }

  /**
   * Aprueba una propuesta de actividad de manera inmutable.
   */
  def aprobarActividad(idActividad: Int): Either[ValidacionActividadesFailure, Unit] = {
    try {
      val success = DB.localTx { implicit session =>
        CronogramaRepository.evaluarActividad(idActividad, EstadoActividad.APROBADA, None)
      }
      
      if (success) Right(())
      else Left(ValidacionActividadesFailure.Validacion("La actividad seleccionada ya ha sido evaluada y no se puede modificar."))
    } catch {
      case NonFatal(e) =>
        Left(ValidacionActividadesFailure.ErrorPersistencia(s"Error de persistencia al aprobar actividad: ${e.getMessage}"))
    }
  }

  /**
   * Rechaza una propuesta de actividad exigiendo comentarios metodológicos y de forma inmutable.
   */
  def rechazarActividad(idActividad: Int, comentario: String): Either[ValidacionActividadesFailure, Unit] = {
    val trimmed = Option(comentario).getOrElse("").trim
    if (trimmed.length < 5) {
      return Left(ValidacionActividadesFailure.Validacion("Las observaciones de corrección son obligatorias al rechazar y deben tener al menos 5 caracteres."))
    }

    try {
      val success = DB.localTx { implicit session =>
        CronogramaRepository.evaluarActividad(idActividad, EstadoActividad.RECHAZADA, Some(trimmed))
      }

      if (success) Right(())
      else Left(ValidacionActividadesFailure.Validacion("La actividad seleccionada ya ha sido evaluada y no se puede modificar."))
    } catch {
      case NonFatal(e) =>
        Left(ValidacionActividadesFailure.ErrorPersistencia(s"Error de persistencia al rechazar actividad: ${e.getMessage}"))
    }
  }
}
