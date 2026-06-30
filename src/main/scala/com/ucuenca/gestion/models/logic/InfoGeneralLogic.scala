package com.ucuenca.gestion.models.logic

import com.ucuenca.gestion.models.entities.PracticaRegistro
import com.ucuenca.gestion.models.db.{CronogramaRepository, Formulario1DB}
import scalikejdbc._
import scala.util.control.NonFatal

sealed trait InfoGeneralFailure
object InfoGeneralFailure {
  case class Validacion(mensaje: String)        extends InfoGeneralFailure
  case class ErrorPersistencia(mensaje: String) extends InfoGeneralFailure
}

case class InfoGeneralDetails(
  practica: PracticaRegistro,
  fechaInicioOpt: Option[java.time.LocalDate],
  cantActividadesAprobadas: Int,
  nombreEmpresa: String,
  convenioNombre: String,
  tutorAcademicoNombre: String,
  tutorEmpresarialNombre: String
)

object InfoGeneralLogic {

  /**
   * Obtiene la práctica activa de un estudiante junto con todos los detalles de su expediente
   * envueltos en un Either monádico para garantizar la estabilidad del hilo FX.
   */
  def obtenerDetallesPractica(estudianteCi: String): Either[InfoGeneralFailure, InfoGeneralDetails] = {
    try {
      CronogramaRepository.buscarPracticaPorEstudiante(estudianteCi) match {
        case Some(pr) =>
          // 1. Obtener fecha de inicio del expediente F1
          val f1Opt = Formulario1DB.buscarExpediente(pr.idPractica)
          val fechaInicioOpt = f1Opt.flatMap(_.fechaAutorizacion)

          // 2. Contar tareas aprobadas en el cronograma
          val countAct = CronogramaRepository.contarTareasAprobadas(pr.idPractica)

          // 3. Obtener nombre de empresa y estado del convenio marco
          val (empresaNombre, convenioNombre) = DB.readOnly { implicit session =>
            sql"""
              SELECT u.nombres_completos AS empresa, ep.estado_convenio
              FROM empresa_perfil ep
              INNER JOIN usuario u ON ep.identificacion = u.identificacion
              WHERE ep.identificacion = ${pr.rucEmpresaRef}
            """.map(rs => (rs.string("empresa"), rs.string("estado_convenio"))).single.apply().getOrElse(("No asignada", "PENDIENTE"))
          }

          // 4. Obtener datos de soporte del tutor académico
          val tutorAcadText = pr.idTutorAcademicoRef match {
            case Some(ci) =>
              DB.readOnly { implicit session =>
                sql"SELECT nombres_completos, correo_electronico FROM usuario WHERE identificacion = ${ci}"
                  .map(rs => s"${rs.string("nombres_completos")} (${rs.string("correo_electronico")})").single.apply()
              }.getOrElse("Tutor académico no registrado")
            case None => "Tutor académico no asignado"
          }

          // 5. Obtener datos de soporte del tutor empresarial
          val tutorEmpText = DB.readOnly { implicit session =>
            sql"SELECT nombres_completos, correo_electronico FROM usuario WHERE identificacion = ${pr.idTutorEmpresarialRef}"
              .map(rs => s"${rs.string("nombres_completos")} (${rs.string("correo_electronico")})").single.apply()
          }.getOrElse("Tutor empresarial no asignado")

          Right(InfoGeneralDetails(
            practica = pr,
            fechaInicioOpt = fechaInicioOpt,
            cantActividadesAprobadas = countAct,
            nombreEmpresa = empresaNombre,
            convenioNombre = convenioNombre,
            tutorAcademicoNombre = tutorAcadText,
            tutorEmpresarialNombre = tutorEmpText
          ))

        case None =>
          Left(InfoGeneralFailure.Validacion("No se localizó una práctica preprofesional activa registrada para su cuenta."))
      }
    } catch {
      case NonFatal(e) =>
        Left(InfoGeneralFailure.ErrorPersistencia(s"Error al consultar el expediente de práctica: ${e.getMessage}"))
    }
  }
}
