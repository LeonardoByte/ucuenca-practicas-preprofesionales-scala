package com.ucuenca.gestion.models.logic

import com.ucuenca.gestion.models.dto.SolicitudPropiaRevisionDTO
import com.ucuenca.gestion.models.enums.EstadoConvenio
import com.ucuenca.gestion.models.db.SolicitudEmpresaPropiaRepository
import scalikejdbc._
import scala.util.control.NonFatal
import java.io.File
import java.nio.file.Files

sealed trait RevisionOficiosFailure
object RevisionOficiosFailure {
  case class Validacion(mensaje: String)        extends RevisionOficiosFailure
  case class ErrorPersistencia(mensaje: String) extends RevisionOficiosFailure
}

object RevisionOficiosLogic {

  /**
   * Obtiene el listado de solicitudes autogestionadas pendientes.
   */
  def listarPendientes(): Either[RevisionOficiosFailure, List[SolicitudPropiaRevisionDTO]] = {
    try {
      Right(SolicitudEmpresaPropiaRepository.listarPendientesConNombreEstudiante())
    } catch {
      case NonFatal(e) =>
        Left(RevisionOficiosFailure.ErrorPersistencia(s"Error al listar solicitudes pendientes: ${e.getMessage}"))
    }
  }

  /**
   * Genera el siguiente código secuencial único matching 'UCUENCA-VINC-2026-XXXX'.
   */
  def obtenerSiguienteCodigo(): Either[RevisionOficiosFailure, String] = {
    try {
      val sig = SolicitudEmpresaPropiaRepository.obtenerSiguienteSecuencial()
      val formatted = f"$sig%04d"
      Right(s"UCUENCA-VINC-2026-$formatted")
    } catch {
      case NonFatal(e) =>
        Left(RevisionOficiosFailure.ErrorPersistencia(s"Error al calcular código secuencial: ${e.getMessage}"))
    }
  }

  /**
   * Rechaza una solicitud autogestionada, exigiendo una justificación obligatoria.
   */
  def rechazar(idSolicitud: Int, justificacion: String): Either[RevisionOficiosFailure, Unit] = {
    if (justificacion == null || justificacion.trim.isEmpty) {
      Left(RevisionOficiosFailure.Validacion("La justificación de denegación es obligatoria si rechaza el trámite."))
    } else {
      try {
        SolicitudEmpresaPropiaRepository.buscarPorId(idSolicitud) match {
          case Some(solicitud) =>
            if (solicitud.estadoTramite == EstadoConvenio.PENDIENTE) {
              DB.localTx { implicit session =>
                SolicitudEmpresaPropiaRepository.actualizarRechazo(idSolicitud, justificacion.trim)
              }
              Right(())
            } else {
              Left(RevisionOficiosFailure.Validacion("La solicitud seleccionada ya no se encuentra pendiente."))
            }
          case None =>
            Left(RevisionOficiosFailure.Validacion("La solicitud especificada no existe."))
        }
      } catch {
        case NonFatal(e) =>
          Left(RevisionOficiosFailure.ErrorPersistencia(s"Error al rechazar la solicitud: ${e.getMessage}"))
      }
    }
  }

  /**
   * Aprueba una solicitud autogestionada. Dispara la transacción atómica:
   * - Registra el PDF de respuesta T5.
   * - Actualiza estado de la solicitud.
   * - Crea JIT Company/Tutor.
   * - Actualiza estado de estudiante.
   * - Crea registro de práctica.
   */
  def aprobar(idSolicitud: Int, tutorCI: String, codigoOficio: String): Either[RevisionOficiosFailure, Unit] = {
    val regex = "^UCUENCA-VINC-2026-\\d{4}$".r
    if (codigoOficio == null || regex.findFirstIn(codigoOficio.trim).isEmpty) {
      return Left(RevisionOficiosFailure.Validacion("El código de oficio debe seguir estrictamente el formato 'UCUENCA-VINC-2026-XXXX' where XXXX is a 4-digit sequence."))
    }
    if (tutorCI == null || tutorCI.trim.isEmpty) {
      return Left(RevisionOficiosFailure.Validacion("Debe asignar un tutor académico de la universidad."))
    }

    try {
      SolicitudEmpresaPropiaRepository.buscarPorId(idSolicitud) match {
        case Some(solicitud) =>
          if (solicitud.estadoTramite != EstadoConvenio.PENDIENTE) {
            return Left(RevisionOficiosFailure.Validacion("La solicitud seleccionada ya no se encuentra pendiente."))
          }

          DB.localTx { implicit session =>
            // 1. Crear / Persistir archivo PDF de vuelta T5
            val uploadsDir = new File("uploads")
            if (!uploadsDir.exists()) uploadsDir.mkdirs()

            val pdfName = s"oficio_vuelta_${idSolicitud}_${System.currentTimeMillis()}.pdf"
            val destFile = new File(uploadsDir, pdfName)

            val templateFile = new File("docs/archivos_pdf/solicitud_de_oficio.pdf")
            if (templateFile.exists()) {
              Files.copy(templateFile.toPath, destFile.toPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            } else {
              // Write a mock valid PDF file content
              val mockPdfContent = "%PDF-1.4\n%âãÏÓ\n1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R >>\nendobj\n4 0 obj\n<< /Length 50 >>\nstream\nBT /F1 12 Tf 70 700 Td (Oficio Vuelta Empresa Propia) Tj ET\nendstream\nendobj\nxref\n0 5\n0000000000 65535 f \n0000000015 00000 n \n0000000068 00000 n \n0000000127 00000 n \n0000000227 00000 n \ntrailer\n<< /Size 5 /Root 1 0 R >>\nstartxref\n326\n%%EOF".getBytes("UTF-8")
              Files.write(destFile.toPath, mockPdfContent)
            }

            // Registrar PDF en base de datos
            val pdfId = sql"""
              INSERT INTO archivo_pdf (tipo_archivo, nombre_original, ruta_segura_servidor)
              VALUES (
                'T5_OFICIO_PRESENTACION_PROP_VUELTA'::tipo_archivo_pdf,
                ${pdfName},
                ${destFile.getAbsolutePath}
              )
            """.updateAndReturnGeneratedKey.apply().toInt

            // 2. Ejecutar transacción atómica de persistencia
            SolicitudEmpresaPropiaRepository.actualizarAprobacion(idSolicitud, tutorCI.trim, codigoOficio.trim, pdfId)
          }
          Right(())
        case None =>
          Left(RevisionOficiosFailure.Validacion("La solicitud especificada no existe."))
      }
    } catch {
      case NonFatal(e) =>
        Left(RevisionOficiosFailure.ErrorPersistencia(s"Error al procesar la aprobación: ${e.getMessage}"))
    }
  }

  /**
   * Obtiene la lista de tutores académicos activos.
   */
  def listarTutoresActivos(): Either[RevisionOficiosFailure, List[com.ucuenca.gestion.models.entities.Usuario]] = {
    try {
      Right(com.ucuenca.gestion.models.db.DirectorioRepository.buscarUsuarios(
        textoBusqueda = None,
        rol = Some(com.ucuenca.gestion.models.enums.RolUsuario.TUTOR_ACADEMICO),
        estado = Some(com.ucuenca.gestion.models.enums.EstadoCuenta.ACTIVA)
      ).toList)
    } catch {
      case NonFatal(e) =>
        Left(RevisionOficiosFailure.ErrorPersistencia(s"Error al listar tutores académicos: ${e.getMessage}"))
    }
  }
}

