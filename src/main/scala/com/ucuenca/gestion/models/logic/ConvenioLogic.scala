package com.ucuenca.gestion.models.logic

import com.ucuenca.gestion.models.entities.SolicitudConvenio
import com.ucuenca.gestion.models.db.SolicitudConvenioRepository
import com.ucuenca.gestion.models.enums.EstadoConvenio
import scalikejdbc._
import scala.util.control.NonFatal

sealed trait ConvenioFailure
object ConvenioFailure {
  case class Validacion(mensaje: String)        extends ConvenioFailure
  case class ErrorPersistencia(mensaje: String) extends ConvenioFailure
}

object ConvenioLogic {

  def registrarSolicitud(
    rucEmpresa: String,
    razonSocial: String,
    representanteLegal: String,
    direccionMatriz: String,
    mision: String,
    vision: String,
    pdfNombre: String,
    pdfRuta: String
  ): Either[ConvenioFailure, Int] = {
    // 1. Validar campos obligatorios
    if (razonSocial == null || razonSocial.trim.isEmpty)
      return Left(ConvenioFailure.Validacion("El nombre o razón social es obligatorio."))
    if (rucEmpresa == null || rucEmpresa.trim.length != 13 || !rucEmpresa.trim.forall(_.isDigit))
      return Left(ConvenioFailure.Validacion("El RUC de la empresa debe tener exactamente 13 dígitos numéricos."))
    if (representanteLegal == null || representanteLegal.trim.isEmpty)
      return Left(ConvenioFailure.Validacion("El representante legal es obligatorio."))
    if (direccionMatriz == null || direccionMatriz.trim.isEmpty)
      return Left(ConvenioFailure.Validacion("La dirección de la sede matriz es obligatoria."))
    if (mision == null || mision.trim.isEmpty)
      return Left(ConvenioFailure.Validacion("La misión de la empresa es obligatoria."))
    if (vision == null || vision.trim.isEmpty)
      return Left(ConvenioFailure.Validacion("La visión de la empresa es obligatoria."))
    if (pdfNombre == null || pdfNombre.trim.isEmpty || pdfRuta == null || pdfRuta.trim.isEmpty)
      return Left(ConvenioFailure.Validacion("El documento PDF del convenio firmado es obligatorio."))

    try {
      // 2. Controlar estados y concurrencia
      SolicitudConvenioRepository.buscarPorRuc(rucEmpresa) match {
        case Some(sol) =>
          if (sol.estadoConvenio == EstadoConvenio.FORMALIZADO) {
            Left(ConvenioFailure.Validacion("La empresa ya cuenta con un convenio formalizado vigente."))
          } else if (sol.estadoConvenio == EstadoConvenio.PENDIENTE) {
            Left(ConvenioFailure.Validacion("Ya existe un trámite de convenio pendiente para esta empresa."))
          } else {
            // Si está rechazado, se permite sobreescribir
            Right(DB.localTx { implicit session =>
              SolicitudConvenioRepository.registrar(rucEmpresa.trim, razonSocial.trim, representanteLegal.trim, direccionMatriz.trim, mision.trim, vision.trim, pdfNombre, pdfRuta)
            })
          }
        case None =>
          Right(DB.localTx { implicit session =>
            SolicitudConvenioRepository.registrar(rucEmpresa.trim, razonSocial.trim, representanteLegal.trim, direccionMatriz.trim, mision.trim, vision.trim, pdfNombre, pdfRuta)
          })
      }
    } catch {
      case NonFatal(e) =>
        Left(ConvenioFailure.ErrorPersistencia(s"Error al registrar solicitud de convenio: ${e.getMessage}"))
    }
  }

  def listarPendientes(): Either[ConvenioFailure, List[SolicitudConvenio]] = {
    try {
      Right(SolicitudConvenioRepository.listarPendientes())
    } catch {
      case NonFatal(e) =>
        Left(ConvenioFailure.ErrorPersistencia(s"Error al listar solicitudes pendientes: ${e.getMessage}"))
    }
  }

  def aprobarConvenio(id: Int, ruc: String): Either[ConvenioFailure, Unit] = {
    try {
      SolicitudConvenioRepository.buscarPorId(id) match {
        case Some(sol) =>
          if (sol.estadoConvenio == EstadoConvenio.PENDIENTE) {
            DB.localTx { implicit session =>
              SolicitudConvenioRepository.actualizarAprobacion(id, ruc)
            }
            Right(())
          } else {
            Left(ConvenioFailure.Validacion("La solicitud seleccionada ya no se encuentra pendiente."))
          }
        case None =>
          Left(ConvenioFailure.Validacion("La solicitud especificada no existe."))
      }
    } catch {
      case NonFatal(e) =>
        Left(ConvenioFailure.ErrorPersistencia(s"Error al formalizar el convenio: ${e.getMessage}"))
    }
  }

  def rechazarConvenio(id: Int, ruc: String, causas: String): Either[ConvenioFailure, Unit] = {
    if (causas == null || causas.trim.isEmpty) {
      return Left(ConvenioFailure.Validacion("Las causas de rechazo administrativo son obligatorias si rechaza la solicitud."))
    }

    try {
      SolicitudConvenioRepository.buscarPorId(id) match {
        case Some(sol) =>
          if (sol.estadoConvenio == EstadoConvenio.PENDIENTE) {
            DB.localTx { implicit session =>
              SolicitudConvenioRepository.actualizarRechazo(id, ruc, causas.trim)
            }
            Right(())
          } else {
            Left(ConvenioFailure.Validacion("La solicitud seleccionada ya no se encuentra pendiente."))
          }
        case None =>
          Left(ConvenioFailure.Validacion("La solicitud especificada no existe."))
      }
    } catch {
      case NonFatal(e) =>
        Left(ConvenioFailure.ErrorPersistencia(s"Error al rechazar el convenio: ${e.getMessage}"))
    }
  }

  def obtenerEstadoConvenioEmpresa(ruc: String): Either[ConvenioFailure, Option[SolicitudConvenio]] = {
    try {
      Right(SolicitudConvenioRepository.buscarPorRuc(ruc))
    } catch {
      case NonFatal(e) =>
        Left(ConvenioFailure.ErrorPersistencia(s"Error al consultar estado de convenio de la empresa: ${e.getMessage}"))
    }
  }
}
