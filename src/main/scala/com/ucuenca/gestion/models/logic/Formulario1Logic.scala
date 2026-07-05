package com.ucuenca.gestion.models.logic

import com.ucuenca.gestion.models.entities.ExpedienteFormulario1
import com.ucuenca.gestion.models.db.{Formulario1DB, CronogramaRepository}
import com.ucuenca.gestion.models.enums.{EstadoActividad, EstadoCronograma}
import scalikejdbc._
import scala.util.control.NonFatal

sealed trait Formulario1Failure
object Formulario1Failure {
  case class Validacion(mensaje: String)        extends Formulario1Failure
  case class ErrorPersistencia(mensaje: String) extends Formulario1Failure
}

object Formulario1Logic {

  /**
   * Obtiene el expediente de Formulario 1 para una práctica.
   */
  def buscarExpediente(idPractica: Int): Either[Formulario1Failure, Option[ExpedienteFormulario1]] = {
    try {
      Right(Formulario1DB.buscarExpediente(idPractica))
    } catch {
      case NonFatal(e) =>
        Left(Formulario1Failure.ErrorPersistencia(s"Error al buscar expediente: ${e.getMessage}"))
    }
  }

  /**
   * Verifica si el trámite está permitido en base a las reglas legales de convenio / carta compromiso.
   */
  def verificarTramitePermitido(idPractica: Int, ciEstudiante: String): Either[Formulario1Failure, Boolean] = {
    try {
      val convenioFormalizado = Formulario1DB.verificarConvenioFormalizado(idPractica)
      if (convenioFormalizado) {
        Right(true) // Bypass legal
      } else {
        val cartaExiste = Formulario1DB.verificarCartaCompromisoExiste(ciEstudiante)
        Right(cartaExiste)
      }
    } catch {
      case NonFatal(e) =>
        Left(Formulario1Failure.ErrorPersistencia(s"Error al verificar controles legales: ${e.getMessage}"))
    }
  }

  /**
   * Guarda un archivo PDF en disco e inserta su metadata en base de datos.
   */
  private def registrarPdf(nombreOriginal: String, bytes: Array[Byte])(implicit session: DBSession): Int = {
    val dir = new java.io.File("uploads/formulario1")
    if (!dir.exists()) dir.mkdirs()
    val file = new java.io.File(dir, nombreOriginal)
    java.nio.file.Files.write(file.toPath, bytes)

    sql"""
      INSERT INTO archivo_pdf (tipo_archivo, nombre_original, ruta_segura_servidor)
      VALUES ('T1_FORMULARIO_1_PLAN'::tipo_archivo_pdf, ${nombreOriginal}, ${"uploads/formulario1/" + nombreOriginal})
    """.updateAndReturnGeneratedKey.apply().toInt
  }

  /**
   * El estudiante presenta el Formulario 1 inicial con su firma implícita.
   * Valida restricciones de actividades (3-6) y controles legales (convenio/carta).
   */
  def presentarFormulario1(idPractica: Int, pdfBytes: Array[Byte], pdfName: String): Either[Formulario1Failure, Unit] = {
    try {
      // 1. Validar cantidad de tareas aprobadas
      val aprobadas = CronogramaRepository.contarTareasAprobadas(idPractica)
      if (aprobadas < 3 || aprobadas > 6) {
        return Left(Formulario1Failure.Validacion(s"El plan debe contener entre 3 y 6 actividades aprobadas metodológicamente por el tutor académico. Actual: $aprobadas"))
      }

      // Obtener CI del estudiante desde la práctica
      val ciEstudianteOpt = DB.readOnly { implicit session =>
        sql"SELECT ci_estudiante_ref FROM practica_registro WHERE id_practica = ${idPractica}"
          .map(rs => rs.string("ci_estudiante_ref")).single.apply()
      }

      ciEstudianteOpt match {
        case Some(ciEstudiante) =>
          // 2. Validar control legal
          val convenioFormalizado = Formulario1DB.verificarConvenioFormalizado(idPractica)
          val cartaExiste = Formulario1DB.verificarCartaCompromisoExiste(ciEstudiante)
          if (!convenioFormalizado && !cartaExiste) {
            return Left(Formulario1Failure.Validacion("Trámite congelado: la empresa no posee convenio formalizado y no se registra la entrega física de la Carta Compromiso en ventanilla."))
          }

          // 3. Registrar PDF y expediente
          DB.localTx { implicit session =>
            val pdfId = registrarPdf(pdfName, pdfBytes)
            Formulario1DB.insertarOActualizarExpediente(idPractica, pdfId)
          }
          Right(())

        case None =>
          Left(Formulario1Failure.Validacion("No se pudo identificar al estudiante asociado a la práctica."))
      }
    } catch {
      case NonFatal(e) =>
        Left(Formulario1Failure.ErrorPersistencia(s"Error al presentar el expediente: ${e.getMessage}"))
    }
  }

  /**
   * Registra la firma digital del tutor empresarial.
   */
  def tutorEmpresaFirmar(idPractica: Int, pdfBytes: Array[Byte], pdfName: String): Either[Formulario1Failure, Unit] = {
    try {
      val expOpt = Formulario1DB.buscarExpediente(idPractica)
      if (expOpt.isEmpty) {
        return Left(Formulario1Failure.Validacion("No existe un trámite de Formulario 1 presentado por el estudiante."))
      }

      val exp = expOpt.get
      if (exp.firmaEmpresarialValida) {
        return Left(Formulario1Failure.Validacion("El expediente ya cuenta con la firma del tutor empresarial."))
      }

      DB.localTx { implicit session =>
        val pdfId = registrarPdf(pdfName, pdfBytes)
        Formulario1DB.actualizarFirmaEmpresa(idPractica, pdfId)
      }
      Right(())
    } catch {
      case NonFatal(e) =>
        Left(Formulario1Failure.ErrorPersistencia(s"Error al firmar por tutor empresarial: ${e.getMessage}"))
    }
  }

  /**
   * Registra la firma digital del tutor académico (requiere firma empresarial previa).
   */
  def tutorAcademicoFirmar(idPractica: Int, pdfBytes: Array[Byte], pdfName: String): Either[Formulario1Failure, Unit] = {
    try {
      val expOpt = Formulario1DB.buscarExpediente(idPractica)
      if (expOpt.isEmpty) {
        return Left(Formulario1Failure.Validacion("No existe un trámite de Formulario 1 presentado por el estudiante."))
      }

      val exp = expOpt.get
      if (!exp.firmaEmpresarialValida) {
        return Left(Formulario1Failure.Validacion("Flujo de firmas incorrecto: se requiere la firma del tutor empresarial antes de proceder con la firma del tutor académico."))
      }
      if (exp.firmaAcademicaValida) {
        return Left(Formulario1Failure.Validacion("El expediente ya cuenta con la firma del tutor académico."))
      }

      DB.localTx { implicit session =>
        val pdfId = registrarPdf(pdfName, pdfBytes)
        Formulario1DB.actualizarFirmaAcademica(idPractica, pdfId)
      }
      Right(())
    } catch {
      case NonFatal(e) =>
        Left(Formulario1Failure.ErrorPersistencia(s"Error al firmar por tutor académico: ${e.getMessage}"))
    }
  }

  /**
   * El coordinador aprueba formalmente el inicio de la práctica (requiere firmas de ambos tutores).
   * Almacena el PDF final y cambia el estado a EN_DESARROLLO de forma atómica.
   */
  def coordinadorAprobar(idPractica: Int, pdfBytes: Array[Byte], pdfName: String): Either[Formulario1Failure, Unit] = {
    try {
      val expOpt = Formulario1DB.buscarExpediente(idPractica)
      if (expOpt.isEmpty) {
        return Left(Formulario1Failure.Validacion("No existe un expediente presentado para esta práctica."))
      }

      val exp = expOpt.get
      if (!exp.firmaEmpresarialValida || !exp.firmaAcademicaValida) {
        return Left(Formulario1Failure.Validacion("Flujo de firmas incorrecto: el plan requiere la firma de ambos tutores antes de la resolución final del coordinador."))
      }
      if (exp.estadoDeCoordinador) {
        return Left(Formulario1Failure.Validacion("El plan ya ha sido aprobado previamente."))
      }

      // Control legal: la aprobación no puede otorgarse si la empresa no tiene convenio formalizado
      // y las 3 cartas compromiso no están registradas como entregadas en secretaría. Se revalida aquí
      // (además del bloqueo visual del botón en la vista) para no depender únicamente del cliente.
      val ciEstudianteOpt = DB.readOnly { implicit session =>
        sql"SELECT ci_estudiante_ref FROM practica_registro WHERE id_practica = ${idPractica}"
          .map(rs => rs.string("ci_estudiante_ref")).single.apply()
      }
      ciEstudianteOpt match {
        case None =>
          return Left(Formulario1Failure.Validacion("No se pudo identificar al estudiante asociado a la práctica."))
        case Some(ciEstudiante) =>
          val convenioFormalizado = Formulario1DB.verificarConvenioFormalizado(idPractica)
          if (!convenioFormalizado && !Formulario1DB.verificarCartaCompromisoExiste(ciEstudiante)) {
            return Left(Formulario1Failure.Validacion("No se puede aprobar el inicio: la empresa no posee convenio formalizado y las 3 cartas compromiso no han sido registradas como entregadas en secretaría."))
          }
      }

      DB.localTx { implicit session =>
        val pdfId = registrarPdf(pdfName, pdfBytes)
        Formulario1DB.autorizarPlan(idPractica, pdfId)
      }
      Right(())
    } catch {
      case NonFatal(e) =>
        Left(Formulario1Failure.ErrorPersistencia(s"Error al aprobar el inicio: ${e.getMessage}"))
    }
  }

  /**
   * El coordinador rechaza el inicio (reinicia las firmas y guarda la causa del rechazo).
   */
  def coordinadorRechazar(idPractica: Int, justificacion: String): Either[Formulario1Failure, Unit] = {
    val trimmed = Option(justificacion).getOrElse("").trim
    if (trimmed.length < 5) {
      return Left(Formulario1Failure.Validacion("La justificación del rechazo es obligatoria y debe contener al menos 5 caracteres."))
    }

    try {
      val expOpt = Formulario1DB.buscarExpediente(idPractica)
      if (expOpt.isEmpty) {
        return Left(Formulario1Failure.Validacion("No existe un expediente presentado para esta práctica."))
      }

      val exp = expOpt.get
      if (!exp.firmaEmpresarialValida || !exp.firmaAcademicaValida) {
        return Left(Formulario1Failure.Validacion("Flujo de firmas incorrecto: el plan requiere la firma de ambos tutores antes de la resolución final del coordinador."))
      }

      DB.localTx { implicit session =>
        Formulario1DB.rechazarExpediente(idPractica, trimmed)
      }
      Right(())
    } catch {
      case NonFatal(e) =>
        Left(Formulario1Failure.ErrorPersistencia(s"Error al rechazar el inicio: ${e.getMessage}"))
    }
  }
}
