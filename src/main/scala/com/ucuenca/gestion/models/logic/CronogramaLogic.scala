package com.ucuenca.gestion.models.logic

import com.ucuenca.gestion.models.entities.{PracticaRegistro, ActividadCronograma}
import com.ucuenca.gestion.models.enums.{OrigenCreacionActividad, EstadoActividad}
import com.ucuenca.gestion.models.db.CronogramaRepository
import scalikejdbc.DB
import scala.util.control.NonFatal

sealed trait CronogramaFailure
object CronogramaFailure {
  case class Validacion(mensaje: String)        extends CronogramaFailure
  case class ErrorPersistencia(mensaje: String) extends CronogramaFailure
}

object CronogramaLogic {

  /**
   * Obtiene la práctica activa de un estudiante por su CI.
   */
  def buscarPracticaEstudiante(ciEstudiante: String): Either[CronogramaFailure, Option[PracticaRegistro]] = {
    try {
      Right(CronogramaRepository.buscarPracticaPorEstudiante(ciEstudiante))
    } catch {
      case NonFatal(e) =>
        Left(CronogramaFailure.ErrorPersistencia(s"Error al buscar práctica: ${e.getMessage}"))
    }
  }

  /**
   * Obtiene los estudiantes asignados a un tutor empresarial.
   */
  def listarEstudiantesTutor(ciTutorEmp: String): Either[CronogramaFailure, List[(Int, String)]] = {
    try {
      Right(CronogramaRepository.listarEstudiantesAsignados(ciTutorEmp))
    } catch {
      case NonFatal(e) =>
        Left(CronogramaFailure.ErrorPersistencia(s"Error al listar estudiantes: ${e.getMessage}"))
    }
  }

  /**
   * Obtiene las actividades no descartadas de una práctica.
   */
  def listarActividades(idPractica: Int): Either[CronogramaFailure, List[ActividadCronograma]] = {
    try {
      Right(CronogramaRepository.listarActividadesPorPractica(idPractica))
    } catch {
      case NonFatal(e) =>
        Left(CronogramaFailure.ErrorPersistencia(s"Error al listar actividades: ${e.getMessage}"))
    }
  }

  /**
   * Registra una nueva propuesta de actividad en el cronograma.
   */
  def proponerActividad(idPractica: Int, descripcion: String, origen: OrigenCreacionActividad): Either[CronogramaFailure, Unit] = {
    val trimmed = Option(descripcion).getOrElse("").trim
    if (trimmed.length < 5) {
      return Left(CronogramaFailure.Validacion("La descripción de la actividad es obligatoria y debe contener al menos 5 caracteres."))
    }

    try {
      DB.localTx { implicit session =>
        CronogramaRepository.registrarActividad(idPractica, trimmed, origen)
      }
      Right(())
    } catch {
      case NonFatal(e) =>
        Left(CronogramaFailure.ErrorPersistencia(s"Error de persistencia al registrar actividad: ${e.getMessage}"))
    }
  }

  /**
   * Descarta lógicamente una actividad.
   */
  def descartarActividad(idActividad: Int, nuevoEstado: EstadoActividad): Either[CronogramaFailure, Unit] = {
    if (nuevoEstado != EstadoActividad.DESCARTAR_ESTUDIANTE && nuevoEstado != EstadoActividad.DESCARTAR_TUTOR) {
      return Left(CronogramaFailure.Validacion("El estado de descarte provisto es inválido."))
    }

    try {
      DB.localTx { implicit session =>
        CronogramaRepository.descartarActividad(idActividad, nuevoEstado)
      }
      Right(())
    } catch {
      case NonFatal(e) =>
        Left(CronogramaFailure.ErrorPersistencia(s"Error al descartar la actividad: ${e.getMessage}"))
    }
  }

  /**
   * Cuenta las tareas convalidadas/aprobadas de una práctica.
   */
  def contarAprobadas(idPractica: Int): Either[CronogramaFailure, Int] = {
    try {
      Right(CronogramaRepository.contarTareasAprobadas(idPractica))
    } catch {
      case NonFatal(e) =>
        Left(CronogramaFailure.ErrorPersistencia(s"Error al contar tareas aprobadas: ${e.getMessage}"))
    }
  }
}
