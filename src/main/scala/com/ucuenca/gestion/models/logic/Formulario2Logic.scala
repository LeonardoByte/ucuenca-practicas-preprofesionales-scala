package com.ucuenca.gestion.models.logic

import com.ucuenca.gestion.models.db.{Formulario2DB, CronogramaRepository}
import com.ucuenca.gestion.models.entities.PracticaRegistro
import com.ucuenca.gestion.models.enums.EstadoCronograma
import scalikejdbc._
import scala.util.control.NonFatal

sealed trait Formulario2Failure
object Formulario2Failure {
  case class Validacion(mensaje: String)        extends Formulario2Failure
  case class ErrorPersistencia(mensaje: String) extends Formulario2Failure
}

object Formulario2Logic {

  /**
   * El estudiante solicita la evaluación final.
   * Valida que se cumplan las horas acumuladas requeridas y que el estado sea el adecuado.
   */
  def solicitarEvaluacionFinal(idPractica: Int): Either[Formulario2Failure, Unit] = {
    try {
      val prOpt = DB.readOnly { implicit session =>
        sql"""
          SELECT id_practica, ci_estudiante_ref, ruc_empresa_ref, id_tutor_academico_ref, 
                 id_tutor_empresarial_ref, origen_rama, estado_cronograma, horas_acumuladas, 
                 horas_totales_requeridas, nota_final
          FROM practica_registro
          WHERE id_practica = ${idPractica}
        """.map { rs =>
          PracticaRegistro(
            idPractica = rs.int("id_practica"),
            ciEstudianteRef = rs.string("ci_estudiante_ref"),
            rucEmpresaRef = rs.string("ruc_empresa_ref"),
            idTutorAcademicoRef = rs.stringOpt("id_tutor_academico_ref"),
            idTutorEmpresarialRef = rs.string("id_tutor_empresarial_ref"),
            origenRama = com.ucuenca.gestion.models.enums.OrigenRama.valueOf(rs.string("origen_rama")),
            estadoCronograma = EstadoCronograma.valueOf(rs.string("estado_cronograma")),
            horasAcumuladas = rs.int("horas_acumuladas"),
            horasTotalesRequeridas = rs.int("horas_totales_requeridas"),
            notaFinal = rs.bigDecimalOpt("nota_final").map(BigDecimal(_))
          )
        }.single.apply()
      }

      prOpt match {
        case Some(pr) =>
          if (pr.horasAcumuladas < pr.horasTotalesRequeridas) {
            Left(Formulario2Failure.Validacion(
              s"No se puede solicitar la evaluación final: Las horas acumuladas (${pr.horasAcumuladas}) son inferiores a las requeridas (${pr.horasTotalesRequeridas})."
            ))
          } else if (pr.estadoCronograma == EstadoCronograma.F2_F3_PENDIENTE) {
            Left(Formulario2Failure.Validacion("La evaluación final ya ha sido solicitada previamente y está pendiente."))
          } else if (pr.estadoCronograma == EstadoCronograma.EVALUADA || pr.estadoCronograma == EstadoCronograma.CERRADA_VALIDA) {
            Left(Formulario2Failure.Validacion("La práctica ya ha sido calificada y se encuentra en estado de cierre."))
          } else if (pr.estadoCronograma != EstadoCronograma.EN_DESARROLLO) {
            Left(Formulario2Failure.Validacion(s"Operación no válida: El cronograma se encuentra en estado '${pr.estadoCronograma.toString}'."))
          } else {
            DB.localTx { implicit session =>
              Formulario2DB.actualizarEstadoCronograma(idPractica, "F2_F3_PENDIENTE")
            }
            Right(())
          }

        case None =>
          Left(Formulario2Failure.Validacion("No se pudo ubicar el registro de práctica indicado."))
      }
    } catch {
      case NonFatal(e) =>
        Left(Formulario2Failure.ErrorPersistencia(s"Error en persistencia al solicitar evaluación: ${e.getMessage}"))
    }
  }

  /**
   * Registra el Formulario 2 (evaluación de tutor empresarial), guardando el PDF en disco
   * e insertando los registros de forma atómica en archivo_pdf y formulario2_evaluacion.
   */
  def registrarFormulario2(
    idPractica: Int, 
    pdfBytes: Array[Byte], 
    pdfName: String, 
    contenidoRubrica: String
  ): Either[Formulario2Failure, Unit] = {
    val rubricaTrim = Option(contenidoRubrica).getOrElse("").trim
    if (rubricaTrim.isEmpty) {
      return Left(Formulario2Failure.Validacion("El contenido de la rúbrica indexada no puede estar vacío."))
    }
    if (pdfBytes == null || pdfBytes.length == 0) {
      return Left(Formulario2Failure.Validacion("El archivo PDF provisto no contiene datos válidos."))
    }

    try {
      val prOpt = DB.readOnly { implicit session =>
        sql"SELECT estado_cronograma FROM practica_registro WHERE id_practica = ${idPractica}"
          .map(rs => rs.string("estado_cronograma")).single.apply()
      }

      prOpt match {
        case Some(estadoStr) =>
          val estado = EstadoCronograma.valueOf(estadoStr)
          if (estado != EstadoCronograma.F2_F3_PENDIENTE) {
            Left(Formulario2Failure.Validacion("No se puede registrar la rúbrica: El estudiante no ha solicitado la evaluación final o el estado del cronograma no lo permite."))
          } else {
            // Escribir archivo PDF en disco seguro
            val dir = new java.io.File("uploads/formulario2")
            if (!dir.exists()) dir.mkdirs()
            val file = new java.io.File(dir, pdfName)
            java.nio.file.Files.write(file.toPath, pdfBytes)

            // Guardar en Base de Datos de manera atómica
            DB.localTx { implicit session =>
              val pdfId = Formulario2DB.insertarArchivoPDF(pdfName)
              Formulario2DB.insertarEvaluacion(idPractica, pdfId, rubricaTrim)
            }
            Right(())
          }
        case None =>
          Left(Formulario2Failure.Validacion("La práctica indicada no existe en el sistema."))
      }
    } catch {
      case NonFatal(e) =>
        Left(Formulario2Failure.ErrorPersistencia(s"Error al registrar la evaluación final: ${e.getMessage}"))
    }
  }
}
