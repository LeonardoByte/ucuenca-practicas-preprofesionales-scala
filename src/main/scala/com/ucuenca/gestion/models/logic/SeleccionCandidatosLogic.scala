package com.ucuenca.gestion.models.logic

import com.ucuenca.gestion.models.dto.CandidatoBolsaDTO
import com.ucuenca.gestion.models.db.GestionCandidatosRepository
import scalikejdbc.DB
import scala.util.control.NonFatal

object SeleccionCandidatosLogic {

  /**
   * Obtiene la lista de candidatos validados por la carrera para una empresa en particular.
   */
  def listarCandidatos(companyRuc: String): Either[BolsaFailure, List[CandidatoBolsaDTO]] = {
    try {
      Right(GestionCandidatosRepository.listarCandidatosPorEmpresa(companyRuc))
    } catch {
      case NonFatal(e) =>
        Left(BolsaFailure.ErrorPersistencia(s"Error al listar candidatos: ${e.getMessage}"))
    }
  }

  /**
   * Obtiene la nómina de tutores/supervisores técnicos de la empresa.
   */
  def listarTutores(companyRuc: String): Either[BolsaFailure, List[(String, String)]] = {
    try {
      Right(GestionCandidatosRepository.listarTutoresEmpresariales(companyRuc))
    } catch {
      case NonFatal(e) =>
        Left(BolsaFailure.ErrorPersistencia(s"Error al listar tutores de la empresa: ${e.getMessage}"))
    }
  }

  /**
   * Acepta transaccionalmente un candidato, vinculándole un tutor empresarial.
   * Ejecuta en cascada la inicialización de la práctica, la anulación automática
   * de postulaciones paralelas del alumno, el cierre de la oferta por límite de cupos,
   * y la liberación/rechazo de los demás postulantes si la oferta se llena.
   */
  def aceptar(
    idPostulacion: Int,
    rucEmpresa: String,
    ciEstudiante: String,
    idTutorEmpresarial: String,
    horasTotales: Int
  ): Either[BolsaFailure, Unit] = {
    try {
      val trimmedTutor = Option(idTutorEmpresarial).getOrElse("").trim
      if (trimmedTutor.isEmpty) {
        return Left(BolsaFailure.Validacion("Debe seleccionar obligatoriamente un supervisor técnico de la nómina."))
      }

      DB.localTx { implicit session =>
        GestionCandidatosRepository.aceptarCandidato(
          idPostulacion = idPostulacion,
          rucEmpresa = rucEmpresa,
          ciEstudiante = ciEstudiante,
          idTutorEmpresarial = trimmedTutor,
          horasTotales = horasTotales
        )
      }
      Right(())
    } catch {
      case NonFatal(e) =>
        Left(BolsaFailure.ErrorPersistencia(s"Error al procesar la aceptación del candidato: ${e.getMessage}"))
    }
  }

  /**
   * Rechaza corporativamente a un postulante exigiendo una justificación.
   */
  def rechazar(idPostulacion: Int, comentario: String): Either[BolsaFailure, Unit] = {
    try {
      val trimmedComment = Option(comentario).getOrElse("").trim
      if (trimmedComment.isEmpty) {
        return Left(BolsaFailure.Validacion("El motivo de rechazo es obligatorio."))
      }

      DB.localTx { implicit session =>
        GestionCandidatosRepository.rechazarCandidato(idPostulacion, trimmedComment)
      }
      Right(())
    } catch {
      case NonFatal(e) =>
        Left(BolsaFailure.ErrorPersistencia(s"Error al procesar el rechazo del candidato: ${e.getMessage}"))
    }
  }
}
