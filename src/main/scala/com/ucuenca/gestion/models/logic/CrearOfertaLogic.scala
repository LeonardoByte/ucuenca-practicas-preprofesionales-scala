package com.ucuenca.gestion.models.logic

import com.ucuenca.gestion.models.dto.CrearOfertaDTO
import com.ucuenca.gestion.models.db.OfertaRepository
import scalikejdbc.DB
import scala.util.control.NonFatal

sealed trait CrearOfertaFailure
object CrearOfertaFailure {
  case class Validacion(mensaje: String) extends CrearOfertaFailure
  case class ErrorPersistencia(mensaje: String) extends CrearOfertaFailure
}

object CrearOfertaLogic {

  /**
   * Valida la oferta de practicantes y la registra atómicamente en la base de datos.
   * Retorna Either[CrearOfertaFailure, Int] donde Int es el id de la oferta creada.
   */
  def crear(dto: CrearOfertaDTO): Either[CrearOfertaFailure, Int] = {
    for {
      _ <- validarCampos(dto)
      _ <- validarLimitesTecnicos(dto)
      _ <- validarPdfSoporte(dto)
      id <- ejecutarPersistencia(dto)
    } yield id
  }

  private def validarCampos(dto: CrearOfertaDTO): Either[CrearOfertaFailure, Unit] = {
    if (dto.tituloOferta == null || dto.tituloOferta.trim.isEmpty) {
      Left(CrearOfertaFailure.Validacion("El título de la oferta es obligatorio."))
    } else if (dto.descripcionGeneral == null || dto.descripcionGeneral.trim.isEmpty) {
      Left(CrearOfertaFailure.Validacion("La descripción general es obligatoria."))
    } else if (dto.requisitosObligatorios == null || dto.requisitosObligatorios.trim.isEmpty) {
      Left(CrearOfertaFailure.Validacion("Los requisitos obligatorios son obligatorios."))
    } else if (dto.actividadesEspecificas == null || dto.actividadesEspecificas.trim.isEmpty) {
      Left(CrearOfertaFailure.Validacion("Las actividades específicas son obligatorias."))
    } else {
      Right(())
    }
  }

  private def validarLimitesTecnicos(dto: CrearOfertaDTO): Either[CrearOfertaFailure, Unit] = {
    if (dto.vacantesSolicitadas < 1 || dto.vacantesSolicitadas > 10) {
      Left(CrearOfertaFailure.Validacion("La cantidad de vacantes debe estar estrictamente entre 1 y 10 cupos."))
    } else if (dto.duracionHoras < 40 || dto.duracionHoras > 400) {
      Left(CrearOfertaFailure.Validacion("La duración de la práctica debe estar estrictamente entre 40 y 400 horas."))
    } else {
      Right(())
    }
  }

  private def validarPdfSoporte(dto: CrearOfertaDTO): Either[CrearOfertaFailure, Unit] = {
    if (dto.pdfNombreOriginal == null || dto.pdfNombreOriginal.trim.isEmpty ||
        dto.pdfRutaSegura == null || dto.pdfRutaSegura.trim.isEmpty) {
      Left(CrearOfertaFailure.Validacion("El archivo PDF firmado de la oferta es obligatorio."))
    } else {
      Right(())
    }
  }

  private def ejecutarPersistencia(dto: CrearOfertaDTO): Either[CrearOfertaFailure, Int] = {
    try {
      Right(DB.localTx { implicit session =>
        OfertaRepository.crearOferta(dto)
      })
    } catch {
      case NonFatal(e) =>
        Left(CrearOfertaFailure.ErrorPersistencia(s"Error al registrar la oferta en la base de datos: ${e.getMessage}"))
    }
  }
}
