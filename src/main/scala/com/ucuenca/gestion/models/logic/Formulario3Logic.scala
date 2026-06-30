package com.ucuenca.gestion.models.logic

import com.ucuenca.gestion.models.db.{Formulario3Repository, Formulario2DB}
import com.ucuenca.gestion.models.enums.{EstadoCronograma, EstadoFormulario2}
import scalikejdbc._
import scala.util.control.NonFatal

sealed trait Formulario3Failure
object Formulario3Failure {
  case class Validacion(mensaje: String)        extends Formulario3Failure
  case class ErrorPersistencia(mensaje: String) extends Formulario3Failure
}

object Formulario3Logic {

  /**
   * Evalúa la rúbrica presentada por la empresa (Formulario 2).
   * El Tutor Académico aprueba (CONFORME) o rechaza (RECHAZADO) con justificación obligatoria.
   */
  def evaluarRubrica(idPractica: Int, aprobado: Boolean, justificacion: String): Either[Formulario3Failure, Unit] = {
    val trimmedJustificacion = Option(justificacion).getOrElse("").trim
    if (!aprobado && trimmedJustificacion.length < 5) {
      return Left(Formulario3Failure.Validacion("La justificación del rechazo es obligatoria y debe tener al menos 5 caracteres."))
    }

    try {
      // 1. Buscar la última evaluación registrada
      val ultimaEvalOpt = Formulario2DB.buscarUltimaEvaluacionPorPractica(idPractica)
      
      ultimaEvalOpt match {
        case Some(eval) =>
          if (eval.estadoFormulario2 != EstadoFormulario2.PENDIENTE_REVISION) {
            Left(Formulario3Failure.Validacion(s"No se puede evaluar: La rúbrica ya ha sido auditada previamente con estado '${eval.estadoFormulario2.toString}'."))
          } else {
            val nuevoEstado = if (aprobado) "CONFORME" else "RECHAZADO"
            val justificacionOpt = if (aprobado) None else Some(trimmedJustificacion)

            DB.localTx { implicit session =>
              Formulario3Repository.actualizarEstadoFormulario2(idPractica, nuevoEstado, justificacionOpt)
            }
            Right(())
          }
        case None =>
          Left(Formulario3Failure.Validacion("No se encontró ningún registro del Formulario 2 para evaluar."))
      }
    } catch {
      case NonFatal(e) =>
        Left(Formulario3Failure.ErrorPersistencia(s"Error al evaluar la rúbrica corporativa: ${e.getMessage}"))
    }
  }

  /**
   * Emite el informe académico final (Formulario 3).
   * Enorgullece la regla de bloqueo en cascada: Sólo se emite si el Formulario 2 está en estado CONFORME.
   */
  def emitirFormulario3(idPractica: Int, pdfBytes: Array[Byte], pdfName: String): Either[Formulario3Failure, Unit] = {
    if (pdfBytes == null || pdfBytes.length == 0) {
      return Left(Formulario3Failure.Validacion("El archivo PDF provisto no contiene datos válidos."))
    }

    try {
      // 1. Validar si la práctica existe
      val prExiste = DB.readOnly { implicit session =>
        sql"SELECT COUNT(1) FROM practica_registro WHERE id_practica = ${idPractica}"
          .map(rs => rs.int(1)).single.apply().getOrElse(0) > 0
      }
      if (!prExiste) {
        return Left(Formulario3Failure.Validacion("La práctica especificada no existe en el sistema."))
      }

      // 2. Validar que no se haya emitido previamente el Formulario 3
      val existeF3 = Formulario3Repository.buscarFormulario3PorPractica(idPractica).isDefined
      if (existeF3) {
        return Left(Formulario3Failure.Validacion("El informe final (Formulario 3) ya ha sido emitido y guardado para esta práctica."))
      }

      // 3. Validar restricción de bloqueo: El Formulario 2 debe ser CONFORME
      val ultimaEvalOpt = Formulario2DB.buscarUltimaEvaluacionPorPractica(idPractica)
      ultimaEvalOpt match {
        case Some(eval) if eval.estaConforme =>
          // Proceder con el registro
          val dir = new java.io.File("uploads/formulario3")
          if (!dir.exists()) dir.mkdirs()
          val file = new java.io.File(dir, pdfName)
          java.nio.file.Files.write(file.toPath, pdfBytes)

          DB.localTx { implicit session =>
            val pdfId = Formulario3Repository.insertarArchivoPDF(pdfName)
            Formulario3Repository.insertarFormulario3(idPractica, pdfId)
          }
          Right(())

        case Some(eval) =>
          Left(Formulario3Failure.Validacion(s"Trámite bloqueado: El Formulario 2 del estudiante se encuentra en estado '${eval.estadoFormulario2.toString}' y debe ser aprobado ('CONFORME') antes de poder emitir el Formulario 3."))
        
        case None =>
          Left(Formulario3Failure.Validacion("Trámite bloqueado: El tutor empresarial no ha subido ni calificado aún la evaluación del Formulario 2."))
      }
    } catch {
      case NonFatal(e) =>
        Left(Formulario3Failure.ErrorPersistencia(s"Error técnico al emitir el Formulario 3: ${e.getMessage}"))
    }
  }
}
