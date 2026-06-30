package com.ucuenca.gestion.models.db

import scalikejdbc._

case class ActiveStudentReportDTO(
  nombreEstudiante: String,
  carrera: String,
  empresa: String,
  horasAcumuladas: Int,
  estado: String
)

case class TopCompanyReportDTO(
  empresa: String,
  ruc: String,
  totalPracticantes: Int
)

case class PostulationPeriodReportDTO(
  estado: String,
  cantidad: Int,
  porcentaje: Double
)

case class SatisfactionReportDTO(
  empresa: String,
  promedioNota: Double
)

object AnalyticsRepository {

  /**
   * Obtiene la nómina de estudiantes con prácticas activas filtrado opcionalmente por carrera.
   */
  def getActiveStudents(carreraIdOpt: Option[Int])(implicit session: DBSession = AutoSession): List[ActiveStudentReportDTO] = {
    val query = carreraIdOpt match {
      case Some(carreraId) =>
        sql"""
          SELECT 
            u_est.nombres_completos AS nombre_estudiante,
            c.nombre_carrera AS carrera,
            u_emp.nombres_completos AS empresa,
            pr.horas_acumuladas,
            pr.estado_cronograma::text AS estado
          FROM practica_registro pr
          INNER JOIN estudiante_perfil ep ON pr.ci_estudiante_ref = ep.identificacion
          INNER JOIN usuario u_est ON ep.identificacion = u_est.identificacion
          INNER JOIN carrera c ON ep.id_carrera_ref = c.id_carrera
          INNER JOIN usuario u_emp ON pr.ruc_empresa_ref = u_emp.identificacion
          WHERE pr.estado_cronograma <> 'CERRADA_VALIDA'::estado_cronograma
            AND ep.id_carrera_ref = ${carreraId}
          ORDER BY u_est.nombres_completos ASC
        """
      case None =>
        sql"""
          SELECT 
            u_est.nombres_completos AS nombre_estudiante,
            c.nombre_carrera AS carrera,
            u_emp.nombres_completos AS empresa,
            pr.horas_acumuladas,
            pr.estado_cronograma::text AS estado
          FROM practica_registro pr
          INNER JOIN estudiante_perfil ep ON pr.ci_estudiante_ref = ep.identificacion
          INNER JOIN usuario u_est ON ep.identificacion = u_est.identificacion
          INNER JOIN carrera c ON ep.id_carrera_ref = c.id_carrera
          INNER JOIN usuario u_emp ON pr.ruc_empresa_ref = u_emp.identificacion
          WHERE pr.estado_cronograma <> 'CERRADA_VALIDA'::estado_cronograma
          ORDER BY u_est.nombres_completos ASC
        """
    }

    query.map { rs =>
      ActiveStudentReportDTO(
        nombreEstudiante = rs.string("nombre_estudiante"),
        carrera = rs.string("carrera"),
        empresa = rs.string("empresa"),
        horasAcumuladas = rs.int("horas_acumuladas"),
        estado = rs.string("estado")
      )
    }.list.apply()
  }

  /**
   * Listado de empresas con mayor número de practicantes activos.
   */
  def getTopHostCompanies(carreraIdOpt: Option[Int])(implicit session: DBSession = AutoSession): List[TopCompanyReportDTO] = {
    val query = carreraIdOpt match {
      case Some(carreraId) =>
        sql"""
          SELECT 
            u_emp.nombres_completos AS empresa,
            ep.identificacion AS ruc,
            COUNT(pr.id_practica) AS total_practicantes
          FROM practica_registro pr
          INNER JOIN estudiante_perfil ep_est ON pr.ci_estudiante_ref = ep_est.identificacion
          INNER JOIN empresa_perfil ep ON pr.ruc_empresa_ref = ep.identificacion
          INNER JOIN usuario u_emp ON ep.identificacion = u_emp.identificacion
          WHERE ep_est.id_carrera_ref = ${carreraId}
          GROUP BY u_emp.nombres_completos, ep.identificacion
          ORDER BY total_practicantes DESC
        """
      case None =>
        sql"""
          SELECT 
            u_emp.nombres_completos AS empresa,
            ep.identificacion AS ruc,
            COUNT(pr.id_practica) AS total_practicantes
          FROM practica_registro pr
          INNER JOIN empresa_perfil ep ON pr.ruc_empresa_ref = ep.identificacion
          INNER JOIN usuario u_emp ON ep.identificacion = u_emp.identificacion
          GROUP BY u_emp.nombres_completos, ep.identificacion
          ORDER BY total_practicantes DESC
        """
    }

    query.map { rs =>
      TopCompanyReportDTO(
        empresa = rs.string("empresa"),
        ruc = rs.string("ruc"),
        totalPracticantes = rs.int("total_practicantes")
      )
    }.list.apply()
  }

  /**
   * Resumen porcentual de las postulaciones del periodo agrupados por estado.
   */
  def getPostulationPercentages(carreraIdOpt: Option[Int])(implicit session: DBSession = AutoSession): List[PostulationPeriodReportDTO] = {
    val query = carreraIdOpt match {
      case Some(carreraId) =>
        sql"""
          SELECT 
            pb.estado_postulacion::text AS estado,
            COUNT(*) AS cantidad,
            ROUND(COUNT(*) * 100.0 / NULLIF((SELECT COUNT(*) FROM postulacion_bolsa pb2 INNER JOIN estudiante_perfil ep2 ON pb2.ci_estudiante_ref = ep2.identificacion WHERE ep2.id_carrera_ref = ${carreraId}), 0), 2) AS porcentaje
          FROM postulacion_bolsa pb
          INNER JOIN estudiante_perfil ep ON pb.ci_estudiante_ref = ep.identificacion
          WHERE ep.id_carrera_ref = ${carreraId}
          GROUP BY pb.estado_postulacion
        """
      case None =>
        sql"""
          SELECT 
            pb.estado_postulacion::text AS estado,
            COUNT(*) AS cantidad,
            ROUND(COUNT(*) * 100.0 / NULLIF((SELECT COUNT(*) FROM postulacion_bolsa), 0), 2) AS porcentaje
          FROM postulacion_bolsa pb
          GROUP BY pb.estado_postulacion
        """
    }

    query.map { rs =>
      PostulationPeriodReportDTO(
        estado = rs.string("estado"),
        cantidad = rs.int("cantidad"),
        porcentaje = rs.double("porcentaje")
      )
    }.list.apply()
  }

  /**
   * Reporte de índices de satisfacción (calificación final promedio) agrupado por empresa receptora.
   */
  def getSatisfactionAverages(carreraIdOpt: Option[Int])(implicit session: DBSession = AutoSession): List[SatisfactionReportDTO] = {
    val query = carreraIdOpt match {
      case Some(carreraId) =>
        sql"""
          SELECT 
            u_emp.nombres_completos AS empresa,
            COALESCE(ROUND(AVG(pr.nota_final), 2), 0.0) AS promedio_nota
          FROM practica_registro pr
          INNER JOIN estudiante_perfil ep ON pr.ci_estudiante_ref = ep.identificacion
          INNER JOIN usuario u_emp ON pr.ruc_empresa_ref = u_emp.identificacion
          WHERE pr.estado_cronograma = 'CERRADA_VALIDA'::estado_cronograma
            AND ep.id_carrera_ref = ${carreraId}
          GROUP BY u_emp.nombres_completos
          ORDER BY promedio_nota DESC
        """
      case None =>
        sql"""
          SELECT 
            u_emp.nombres_completos AS empresa,
            COALESCE(ROUND(AVG(pr.nota_final), 2), 0.0) AS promedio_nota
          FROM practica_registro pr
          INNER JOIN usuario u_emp ON pr.ruc_empresa_ref = u_emp.identificacion
          WHERE pr.estado_cronograma = 'CERRADA_VALIDA'::estado_cronograma
          GROUP BY u_emp.nombres_completos
          ORDER BY promedio_nota DESC
        """
    }

    query.map { rs =>
      SatisfactionReportDTO(
        empresa = rs.string("empresa"),
        promedioNota = rs.double("promedio_nota")
      )
    }.list.apply()
  }
}
