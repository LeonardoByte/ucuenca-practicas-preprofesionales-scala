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
    // Company identification
    if (dto.nombreEntidadExterna == null || dto.nombreEntidadExterna.trim.isEmpty)
      Left(SolicitudEmpresaPropiaFailure.Validacion("El nombre de la institución/empresa es obligatorio."))
    else if (dto.rucEmpresaPropia == null || dto.rucEmpresaPropia.trim.length != 13 || !dto.rucEmpresaPropia.trim.forall(_.isDigit))
      Left(SolicitudEmpresaPropiaFailure.Validacion("El RUC de la empresa debe tener exactamente 13 dígitos numéricos."))
    else if (dto.contactoEmpresaPropia == null || dto.contactoEmpresaPropia.trim.isEmpty)
      Left(SolicitudEmpresaPropiaFailure.Validacion("El correo institucional de la empresa es obligatorio."))
    // JIT company profile fields
    else if (dto.direccionEmpresaPropia == null || dto.direccionEmpresaPropia.trim.isEmpty)
      Left(SolicitudEmpresaPropiaFailure.Validacion("La dirección de la sede matriz es obligatoria."))
    else if (dto.misionEmpresaPropia == null || dto.misionEmpresaPropia.trim.isEmpty)
      Left(SolicitudEmpresaPropiaFailure.Validacion("La misión de la organización es obligatoria."))
    else if (dto.visionEmpresaPropia == null || dto.visionEmpresaPropia.trim.isEmpty)
      Left(SolicitudEmpresaPropiaFailure.Validacion("La visión de la organización es obligatoria."))
    // Core office transcript
    else if (dto.contenidoOficioTranscrito == null || dto.contenidoOficioTranscrito.trim.isEmpty)
      Left(SolicitudEmpresaPropiaFailure.Validacion("El contenido del oficio transcrito es obligatorio."))
    // JIT external supervisor fields
    else if (dto.ciSupervisorExterno == null || dto.ciSupervisorExterno.trim.length != 10 || !dto.ciSupervisorExterno.trim.forall(_.isDigit))
      Left(SolicitudEmpresaPropiaFailure.Validacion("La cédula del supervisor externo debe tener exactamente 10 dígitos numéricos."))
    else if (dto.nombresSupervisorExterno == null || dto.nombresSupervisorExterno.trim.isEmpty)
      Left(SolicitudEmpresaPropiaFailure.Validacion("El nombre completo del supervisor externo es obligatorio."))
    else if (dto.emailSupervisorExterno == null || !dto.emailSupervisorExterno.contains("@"))
      Left(SolicitudEmpresaPropiaFailure.Validacion("El correo electrónico del supervisor externo no es válido."))
    else if (dto.telefonoSupervisorExterno == null || dto.telefonoSupervisorExterno.trim.length != 10 || !dto.telefonoSupervisorExterno.trim.forall(_.isDigit))
      Left(SolicitudEmpresaPropiaFailure.Validacion("El teléfono del supervisor externo debe tener exactamente 10 dígitos numéricos."))
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
