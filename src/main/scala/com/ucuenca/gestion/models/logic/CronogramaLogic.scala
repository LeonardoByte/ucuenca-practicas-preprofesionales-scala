package com.ucuenca.gestion.models.logic

import com.ucuenca.gestion.models.entities.{PracticaRegistro, ActividadCronograma}
import com.ucuenca.gestion.models.enums.{OrigenCreacionActividad, EstadoActividad}
import com.ucuenca.gestion.models.db.{CronogramaRepository, PracticaRegistroRepository}
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

  /**
   * El tutor empresarial registra horas efectivamente trabajadas por el estudiante.
   * Exige un valor estrictamente positivo (solo se puede incrementar) y que no
   * supere el tiempo restante para culminar la práctica (horas totales requeridas
   * por la oferta menos las horas ya acumuladas). Retorna la práctica actualizada
   * para refrescar la interfaz inmediatamente.
   */
  def registrarHorasTrabajadas(idPractica: Int, horas: Int): Either[CronogramaFailure, PracticaRegistro] = {
    if (horas <= 0) {
      return Left(CronogramaFailure.Validacion("Las horas a registrar deben ser un valor estrictamente positivo."))
    }
    try {
      DB.localTx { implicit session =>
        CronogramaRepository.buscarPracticaPorId(idPractica) match {
          case None =>
            Left(CronogramaFailure.Validacion("No se encontró el registro de la práctica seleccionada."))
          case Some(pr) =>
            val restantes = pr.horasTotalesRequeridas - pr.horasAcumuladas
            if (horas > restantes) {
              Left(CronogramaFailure.Validacion(s"Las horas ingresadas superan el tiempo restante para culminar la práctica (quedan $restantes horas)."))
            } else if (!PracticaRegistroRepository.registrarHorasTrabajadas(idPractica, horas)) {
              Left(CronogramaFailure.ErrorPersistencia("No se pudo actualizar las horas acumuladas de la práctica."))
            } else {
              Right(pr.copy(horasAcumuladas = pr.horasAcumuladas + horas))
            }
        }
      }
    } catch {
      case NonFatal(e) =>
        Left(CronogramaFailure.ErrorPersistencia(s"Error al registrar horas trabajadas: ${e.getMessage}"))
    }
  }

  /**
   * SOLO DESARROLLO: adelanta horas_acumuladas en bloques (ej. +40, +200) para
   * agilizar demos manuales del cronograma. No forma parte del flujo de negocio
   * validado (el docente/tutor empresarial no invoca esto); no wirear en pantallas
   * de producción sin dejarlo visualmente identificado como herramienta de dev.
   */
  def devSimularAvanceHoras(idPractica: Int, horas: Int): Either[CronogramaFailure, Unit] = {
    if (horas <= 0) {
      return Left(CronogramaFailure.Validacion("El bloque de horas a simular debe ser positivo."))
    }
    try {
      DB.localTx { implicit session =>
        PracticaRegistroRepository.devSumarHorasAcumuladas(idPractica, horas)
      }
      Right(())
    } catch {
      case NonFatal(e) =>
        Left(CronogramaFailure.ErrorPersistencia(s"Error al simular avance de horas: ${e.getMessage}"))
    }
  }
}
