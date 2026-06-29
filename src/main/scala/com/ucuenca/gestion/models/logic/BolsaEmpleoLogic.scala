package com.ucuenca.gestion.models.logic

import com.ucuenca.gestion.models.dto.OfertaBolsaDTO
import com.ucuenca.gestion.models.enums.EstadoEstudiantePractica
import com.ucuenca.gestion.models.db.PostulacionBolsaRepository
import com.ucuenca.gestion.models.db.EstudianteRepository
import scalikejdbc.DB
import scala.util.control.NonFatal

sealed trait BolsaFailure
object BolsaFailure {
  case class Validacion(mensaje: String) extends BolsaFailure
  case class ErrorPersistencia(mensaje: String) extends BolsaFailure
}

object BolsaEmpleoLogic {

  /**
   * Obtiene la nómina de convocatorias en estado APROBADA disponibles para postulaciones.
   */
  def listarOfertasAprobadas(): Either[BolsaFailure, List[OfertaBolsaDTO]] = {
    try {
      Right(PostulacionBolsaRepository.listarOfertasAprobadas())
    } catch {
      case NonFatal(e) =>
        Left(BolsaFailure.ErrorPersistencia(s"Error al listar ofertas de la bolsa: ${e.getMessage}"))
    }
  }

  /**
   * Registra una postulación a una oferta aprobada de la bolsa de empleo.
   * Realiza validaciones estrictas sobre el ciclo y estado de práctica del alumno.
   */
  def postular(ciEstudiante: String, idOferta: Int): Either[BolsaFailure, Unit] = {
    try {
      // 1. Validar unicidad (evitar postulaciones redundantes)
      if (PostulacionBolsaRepository.yaPostulo(ciEstudiante, idOferta)) {
        return Left(BolsaFailure.Validacion("Ya cuenta con una postulación activa para esta vacante."))
      }

      // 2. Cargar expediente del estudiante
      EstudianteRepository.buscarPerfil(ciEstudiante) match {
        case Some((_, perfil: com.ucuenca.gestion.models.entities.EstudiantePerfil, _, _)) =>
          // 3. Validar ciclo académico >= 6
          if (perfil.cicloActual < 6) {
            return Left(BolsaFailure.Validacion("El estudiante debe pertenecer estrictamente al sexto ciclo académico o superior para postular."))
          }

          // 4. Validar estado de la práctica == SIN_PRACTICA
          if (perfil.estadoEstudiantePractica != EstadoEstudiantePractica.SIN_PRACTICA) {
            return Left(BolsaFailure.Validacion("El estudiante debe registrar exactamente un estado de práctica 'SIN_PRACTICA' para postular."))
          }

          // 5. Registrar postulación
          DB.localTx { implicit session =>
            PostulacionBolsaRepository.registrarPostulacion(ciEstudiante, idOferta)
          }
          Right(())

        case None =>
          Left(BolsaFailure.Validacion("No se pudo encontrar el expediente del estudiante."))
      }
    } catch {
      case NonFatal(e) =>
        Left(BolsaFailure.ErrorPersistencia(s"Error de persistencia al registrar postulación: ${e.getMessage}"))
    }
  }
}
