package com.ucuenca.gestion.models.logic

import com.ucuenca.gestion.models.entities._
import com.ucuenca.gestion.models.enums._
import com.ucuenca.gestion.models.db.EstudianteRepository
import scala.util.control.NonFatal

sealed trait PerfilFailure
object PerfilFailure {
  case class Validacion(mensaje: String) extends PerfilFailure
  case class ErrorPersistencia(mensaje: String) extends PerfilFailure
}

object EstudiantePerfilLogic {

  /**
   * Carga el perfil del estudiante junto a sus documentos analíticos y hojas de vida.
   */
  def obtenerPerfilCompleto(identificacion: String): Either[PerfilFailure, (Usuario, EstudiantePerfil, Option[ArchivoPDF], Option[ArchivoPDF])] = {
    try {
      EstudianteRepository.buscarPerfil(identificacion) match {
        case Some(datos) => Right(datos)
        case None => Left(PerfilFailure.Validacion("No se encontró la ficha curricular del estudiante."))
      }
    } catch {
      case NonFatal(e) =>
        Left(PerfilFailure.ErrorPersistencia(s"Error al leer datos del perfil: ${e.getMessage}"))
    }
  }

  /**
   * Carga y asocia un documento PDF (Malla Curricular o CV) al perfil del estudiante.
   * Valida la extensión estrictamente a formato PDF.
   */
  def cargarDocumento(
    identificacion: String,
    nombreArchivo: String,
    rutaSegura: String,
    esMalla: Boolean
  ): Either[PerfilFailure, ArchivoPDF] = {
    if (nombreArchivo == null || !nombreArchivo.toLowerCase.endsWith(".pdf")) {
      Left(PerfilFailure.Validacion("El formato del archivo es inválido. Solo se admiten archivos PDF."))
    } else {
      try {
        val tipo = if (esMalla) TipoArchivoPDF.T9_MALLA_ACADEMICA else TipoArchivoPDF.T10_CURRICULUM_VITAE
        val pdf = ArchivoPDF(
          idArchivoPDF = 0,
          tipoArchivo = tipo,
          nombreOriginal = nombreArchivo,
          rutaSeguraServidor = rutaSegura,
          fechaCarga = java.time.LocalDateTime.now()
        )

        EstudianteRepository.actualizarDocumento(identificacion, pdf, esMalla)
        Right(pdf)
      } catch {
        case NonFatal(e) =>
          Left(PerfilFailure.ErrorPersistencia(s"Error de persistencia del documento: ${e.getMessage}"))
      }
    }
  }

  /**
   * Regla de Estado Lock: Bloquea y deshabilita si el estado es diferente a SIN_PRACTICA.
   */
  def validarBloqueoPostulacion(estadoPractica: EstadoEstudiantePractica): Boolean = {
    estadoPractica != EstadoEstudiantePractica.SIN_PRACTICA
  }

  /**
   * Obtiene la cantidad de ofertas de empleo aprobadas y publicadas en la bolsa de empleo.
   */
  def obtenerOfertasDisponiblesCount(): Either[PerfilFailure, Int] = {
    try {
      import scalikejdbc._
      val count = DB.readOnly { implicit session =>
        sql"SELECT COUNT(*) FROM oferta_convocatoria WHERE estado_oferta = 'APROBADA'"
          .map(rs => rs.int(1)).single.apply().getOrElse(0)
      }
      Right(count)
    } catch {
      case NonFatal(e) =>
        Left(PerfilFailure.ErrorPersistencia(s"Error al contar ofertas disponibles: ${e.getMessage}"))
    }
  }
}
