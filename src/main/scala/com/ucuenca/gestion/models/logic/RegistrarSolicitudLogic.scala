package com.ucuenca.gestion.models.logic

import com.ucuenca.gestion.models.dto.RegistrarSolicitudDTO
import com.ucuenca.gestion.models.db.SolicitudEmpresaPropiaRepository
import com.ucuenca.gestion.models.db.EstudianteRepository
import com.ucuenca.gestion.models.enums.EstadoEstudiantePractica
import scalikejdbc.DB
import scala.util.control.NonFatal

sealed trait SolicitudEmpresaPropiaFailure
object SolicitudEmpresaPropiaFailure {
  case class Validacion(mensaje: String)        extends SolicitudEmpresaPropiaFailure
  case class ErrorPersistencia(mensaje: String) extends SolicitudEmpresaPropiaFailure
}

object RegistrarSolicitudLogic {

  def registrar(dto: RegistrarSolicitudDTO): Either[SolicitudEmpresaPropiaFailure, Int] = {
    for {
      _ <- validarCamposObligatorios(dto)
      _ <- validarRangoHoras(dto)
      _ <- validarPdf(dto)
      _ <- validarEstadoEstudiante(dto.ciEstudianteRef)
      id <- ejecutarPersistencia(dto)
    } yield id
  }

  private def validarCamposObligatorios(dto: RegistrarSolicitudDTO): Either[SolicitudEmpresaPropiaFailure, Unit] = {
    if (dto.nombreEntidadExterna == null || dto.nombreEntidadExterna.trim.isEmpty)
      Left(SolicitudEmpresaPropiaFailure.Validacion("El nombre de la institución/empresa es obligatorio."))
    else if (dto.contactoEmpresaPropia == null || dto.contactoEmpresaPropia.trim.isEmpty)
      Left(SolicitudEmpresaPropiaFailure.Validacion("El correo de contacto del encargado es obligatorio."))
    else if (dto.contenidoOficioTranscrito == null || dto.contenidoOficioTranscrito.trim.isEmpty)
      Left(SolicitudEmpresaPropiaFailure.Validacion("El contenido del oficio transcrito es obligatorio."))
    else
      Right(())
  }

  private def validarRangoHoras(dto: RegistrarSolicitudDTO): Either[SolicitudEmpresaPropiaFailure, Unit] = {
    if (dto.horasEmpresaPropia < 40 || dto.horasEmpresaPropia > 400)
      Left(SolicitudEmpresaPropiaFailure.Validacion("La duración de la práctica debe estar estrictamente entre 40 y 400 horas."))
    else
      Right(())
  }

  private def validarPdf(dto: RegistrarSolicitudDTO): Either[SolicitudEmpresaPropiaFailure, Unit] = {
    if (dto.pdfNombreOriginal == null || dto.pdfNombreOriginal.trim.isEmpty ||
        dto.pdfRutaSegura == null || dto.pdfRutaSegura.trim.isEmpty)
      Left(SolicitudEmpresaPropiaFailure.Validacion("El archivo PDF del oficio firmado es obligatorio."))
    else
      Right(())
  }

  private def validarEstadoEstudiante(ciEstudiante: String): Either[SolicitudEmpresaPropiaFailure, Unit] = {
    try {
      EstudianteRepository.buscarPerfil(ciEstudiante) match {
        case Some((_, perfil, _, _)) =>
          if (perfil.estadoEstudiantePractica != EstadoEstudiantePractica.SIN_PRACTICA)
            Left(SolicitudEmpresaPropiaFailure.Validacion(
              "No es posible iniciar un trámite de empresa propia: el estudiante ya cuenta con una práctica activa o en proceso."
            ))
          else
            Right(())
        case None =>
          Left(SolicitudEmpresaPropiaFailure.Validacion("No se encontró el expediente del estudiante en el sistema."))
      }
    } catch {
      case NonFatal(e) =>
        Left(SolicitudEmpresaPropiaFailure.ErrorPersistencia(s"Error al consultar el perfil del estudiante: ${e.getMessage}"))
    }
  }

  private def ejecutarPersistencia(dto: RegistrarSolicitudDTO): Either[SolicitudEmpresaPropiaFailure, Int] = {
    try {
      Right(DB.localTx { implicit session =>
        SolicitudEmpresaPropiaRepository.registrar(dto)
      })
    } catch {
      case NonFatal(e) =>
        Left(SolicitudEmpresaPropiaFailure.ErrorPersistencia(
          s"Error al registrar la solicitud en la base de datos: ${e.getMessage}"
        ))
    }
  }
}
