package com.ucuenca.gestion.models.logic

import com.ucuenca.gestion.models.dto.AsignacionDocenteDTO
import com.ucuenca.gestion.models.entities.Usuario
import com.ucuenca.gestion.models.db.{PracticaRegistroRepository, DirectorioRepository}
import com.ucuenca.gestion.models.enums.{RolUsuario, EstadoCuenta}
import scalikejdbc.DB
import scala.util.control.NonFatal

sealed trait AsignarDocentesFailure
object AsignarDocentesFailure {
  case class Validacion(mensaje: String)        extends AsignarDocentesFailure
  case class ErrorPersistencia(mensaje: String) extends AsignarDocentesFailure
}

object AsignarDocentesLogic {

  /**
   * Obtiene la nómina de prácticas preprofesionales de la bolsa que requieren asignación de tutor.
   */
  def listarPendientes(): Either[AsignarDocentesFailure, List[AsignacionDocenteDTO]] = {
    try {
      Right(PracticaRegistroRepository.listarBolsaPendientesTutor())
    } catch {
      case NonFatal(e) =>
        Left(AsignarDocentesFailure.ErrorPersistencia(s"Error al obtener las prácticas pendientes: ${e.getMessage}"))
    }
  }

  /**
   * Obtiene todos los docentes/tutores académicos activos.
   */
  def listarTutoresActivos(): Either[AsignarDocentesFailure, List[Usuario]] = {
    try {
      Right(DirectorioRepository.buscarUsuarios(
        textoBusqueda = None,
        rol = Some(RolUsuario.TUTOR_ACADEMICO),
        estado = Some(EstadoCuenta.ACTIVA)
      ).toList)
    } catch {
      case NonFatal(e) =>
        Left(AsignarDocentesFailure.ErrorPersistencia(s"Error al obtener los tutores académicos: ${e.getMessage}"))
    }
  }

  /**
   * Registra en bloque las asignaciones de tutores a sus correspondientes registros de práctica de manera transaccional.
   */
  def guardarAsignaciones(asignaciones: List[(Int, String)]): Either[AsignarDocentesFailure, Unit] = {
    if (asignaciones.isEmpty) {
      return Left(AsignarDocentesFailure.Validacion("No se especificaron asignaciones de tutores académicos para guardar."))
    }

    val invalid = asignaciones.exists { case (_, tutorCI) => tutorCI == null || tutorCI.trim.isEmpty }
    if (invalid) {
      return Left(AsignarDocentesFailure.Validacion("El tutor académico asignado no puede estar vacío."))
    }

    try {
      DB.localTx { implicit session =>
        asignaciones.foreach { case (idPractica, tutorCI) =>
          PracticaRegistroRepository.asignarTutorAcademico(idPractica, tutorCI.trim)
        }
      }
      Right(())
    } catch {
      case NonFatal(e) =>
        Left(AsignarDocentesFailure.ErrorPersistencia(s"Error de persistencia al guardar las asignaciones: ${e.getMessage}"))
    }
  }
}
