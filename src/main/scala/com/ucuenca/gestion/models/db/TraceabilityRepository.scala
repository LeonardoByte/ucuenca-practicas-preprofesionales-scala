package com.ucuenca.gestion.models.db

import scalikejdbc._
import java.time.LocalDate

case class TraceabilityPracticeListItem(
  idPractica: Int,
  estudiante: String,
  empresa: String,
  fechaRegistro: LocalDate,
  estado: String
)

case class StageDetails(
  nombre: String,
  estado: String,
  detalle: String,
  concluida: Boolean,
  alerta: Boolean
)

case class PracticeTimelineDetails(
  idPractica: Int,
  estudiante: String,
  empresa: String,
  origenRama: String,
  stages: List[StageDetails]
)

case class RejectionCommentDTO(
  etapa: String,
  comentario: String,
  fecha: LocalDate
)

object TraceabilityRepository {

  /**
   * Busca prácticas asociadas por nombres de estudiante, RUC/identificación, o nombres de empresa.
   */
  def searchPractices(criteria: String)(implicit session: DBSession = AutoSession): List[TraceabilityPracticeListItem] = {
    val clean = criteria.trim.toLowerCase
    val likeStr = s"%$clean%"
    
    sql"""
      SELECT 
        pr.id_practica,
        u_est.nombres_completos AS estudiante,
        u_emp.nombres_completos AS empresa,
        COALESCE(sep.fecha_registro, pb.fecha_postulacion, CURRENT_DATE) AS fecha_registro,
        pr.estado_cronograma::text AS estado
      FROM practica_registro pr
      INNER JOIN estudiante_perfil ep ON pr.ci_estudiante_ref = ep.identificacion
      INNER JOIN usuario u_est ON ep.identificacion = u_est.identificacion
      INNER JOIN usuario u_emp ON pr.ruc_empresa_ref = u_emp.identificacion
      LEFT JOIN solicitud_empresa_propia sep ON pr.ci_estudiante_ref = sep.ci_estudiante_ref
      LEFT JOIN postulacion_bolsa pb ON pr.ci_estudiante_ref = pb.ci_estudiante_ref AND pb.estado_postulacion = 'APROBADA'::estado_postulacion
      WHERE LOWER(u_est.nombres_completos) LIKE ${likeStr}
         OR LOWER(u_emp.nombres_completos) LIKE ${likeStr}
         OR LOWER(pr.ci_estudiante_ref) LIKE ${likeStr}
      ORDER BY fecha_registro DESC
    """.map { rs =>
      TraceabilityPracticeListItem(
        idPractica = rs.int("id_practica"),
        estudiante = rs.string("estudiante"),
        empresa = rs.string("empresa"),
        fechaRegistro = rs.localDate("fecha_registro"),
        estado = rs.string("estado")
      )
    }.list.apply()
  }

  /**
   * Reconstruye la línea de vida cronológica de 6 etapas de la práctica.
   */
  def getTimeline(idPractica: Int)(implicit session: DBSession = AutoSession): Option[PracticeTimelineDetails] = {
    // Buscar la práctica base
    val practiceOpt = sql"""
      SELECT 
        pr.id_practica,
        u_est.nombres_completos AS estudiante,
        u_emp.nombres_completos AS empresa,
        pr.origen_rama::text AS origen_rama,
        pr.ci_estudiante_ref
      FROM practica_registro pr
      INNER JOIN estudiante_perfil ep ON pr.ci_estudiante_ref = ep.identificacion
      INNER JOIN usuario u_est ON ep.identificacion = u_est.identificacion
      INNER JOIN usuario u_emp ON pr.ruc_empresa_ref = u_emp.identificacion
      WHERE pr.id_practica = ${idPractica}
    """.map { rs =>
      (rs.int("id_practica"), rs.string("estudiante"), rs.string("empresa"), rs.string("origen_rama"), rs.string("ci_estudiante_ref"))
    }.single.apply()

    practiceOpt.map { case (id, estudiante, empresa, origenRama, ciEstudiante) =>
      var stages = List.empty[StageDetails]

      if (origenRama == "BOLSA_EMPLEO") {
        // --- 1. Oferta ---
        val ofertaOpt = sql"""
          SELECT oc.titulo_oferta, oc.estado_oferta::text
          FROM postulacion_bolsa pb
          INNER JOIN oferta_convocatoria oc ON pb.id_oferta_ref = oc.id_oferta
          WHERE pb.ci_estudiante_ref = ${ciEstudiante} AND pb.estado_postulacion = 'APROBADA'::estado_postulacion
        """.map(rs => (rs.string("titulo_oferta"), rs.string("estado_oferta"))).single.apply()

        ofertaOpt match {
          case Some((titulo, estado)) =>
            stages = stages :+ StageDetails(
              nombre = "Etapa 1: Oferta de Convocatoria Corporativa",
              estado = s"Aprobada: $estado",
              detalle = s"Oferta de empleo vinculada: $titulo",
              concluida = true,
              alerta = false
            )
          case None =>
            stages = stages :+ StageDetails(
              nombre = "Etapa 1: Oferta de Convocatoria Corporativa",
              estado = "Pendiente",
              detalle = "No se localizó oferta aprobada en la bolsa vinculada a este cronograma.",
              concluida = false,
              alerta = true
            )
        }

        // --- 2. Postulación ---
        val postulacionOpt = sql"""
          SELECT pb.estado_postulacion::text, pb.fecha_postulacion
          FROM postulacion_bolsa pb
          WHERE pb.ci_estudiante_ref = ${ciEstudiante} AND pb.estado_postulacion = 'APROBADA'::estado_postulacion
        """.map(rs => (rs.string("estado_postulacion"), rs.localDate("fecha_postulacion"))).single.apply()

        postulacionOpt match {
          case Some((estado, fecha)) =>
            stages = stages :+ StageDetails(
              nombre = "Etapa 2: Postulación del Estudiante en Bolsa",
              estado = s"Estado: $estado",
              detalle = s"Postulación enviada el $fecha y aprobada académicamente.",
              concluida = true,
              alerta = false
            )
          case None =>
            stages = stages :+ StageDetails(
              nombre = "Etapa 2: Postulación del Estudiante en Bolsa",
              estado = "Pendiente",
              detalle = "No registra postulación aprobada para este flujo.",
              concluida = false,
              alerta = true
            )
        }

        // --- 3. Oficio ---
        stages = stages :+ StageDetails(
          nombre = "Etapa 3: Oficio de Petición Autogestionada (Empresa Propia)",
          estado = "Omitida",
          detalle = "Bypass de oficio. Flujo originado vía bolsa de empleo directa.",
          concluida = true,
          alerta = false
        )

      } else {
        // origenRama == "EMPRESA_PROPIA"
        stages = stages :+ StageDetails(
          nombre = "Etapa 1: Oferta de Convocatoria Corporativa",
          estado = "Omitida",
          detalle = "Bypass de oferta. Estudiante gestionó plaza por cuenta propia.",
          concluida = true,
          alerta = false
        )

        stages = stages :+ StageDetails(
          nombre = "Etapa 2: Postulación del Estudiante en Bolsa",
          estado = "Omitida",
          detalle = "Bypass de postulación de bolsa.",
          concluida = true,
          alerta = false
        )

        // --- 3. Oficio ---
        val oficioOpt = sql"""
          SELECT estado_tramite::text, codigo_oficio_vuelta, fecha_registro
          FROM solicitud_empresa_propia
          WHERE ci_estudiante_ref = ${ciEstudiante}
        """.map(rs => (rs.string("estado_tramite"), rs.stringOpt("codigo_oficio_vuelta"), rs.localDate("fecha_registro"))).single.apply()

        oficioOpt match {
          case Some((estado, codigoOpt, fecha)) =>
            stages = stages :+ StageDetails(
              nombre = "Etapa 3: Oficio de Petición Autogestionada (Empresa Propia)",
              estado = s"Estado: $estado",
              detalle = s"Solicitud registrada el $fecha. Oficio de vuelta: ${codigoOpt.getOrElse("Pendiente de emisión")}",
              concluida = estado == "FORMALIZADO",
              alerta = estado == "RECHAZADO"
            )
          case None =>
            stages = stages :+ StageDetails(
              nombre = "Etapa 3: Oficio de Petición Autogestionada (Empresa Propia)",
              estado = "Pendiente",
              detalle = "No registra solicitud autogestionada registrada en la base de datos.",
              concluida = false,
              alerta = true
            )
        }
      }

      // --- 4. Formulario 1 (Plan Técnico) ---
      val f1Opt = sql"""
        SELECT firma_empresarial_valida, firma_academica_valida, estado_de_coordinador, justificacion_rechazo_inicio
        FROM expediente_formulario1
        WHERE id_practica_ref = ${id}
      """.map { rs =>
        (rs.boolean("firma_empresarial_valida"), rs.boolean("firma_academica_valida"), rs.boolean("estado_de_coordinador"), rs.stringOpt("justificacion_rechazo_inicio"))
      }.single.apply()

      f1Opt match {
        case Some((firmaEmp, firmaAcad, aprobadoCoord, rechazoOpt)) =>
          if (aprobadoCoord) {
            stages = stages :+ StageDetails(
              nombre = "Etapa 4: Formulario 1 - Plan Técnico de Aprendizaje",
              estado = "Aprobado por Coordinación",
              detalle = s"Consolidado. Firmas: Empresa: $firmaEmp, Tutor: $firmaAcad",
              concluida = true,
              alerta = false
            )
          } else if (rechazoOpt.isDefined) {
            stages = stages :+ StageDetails(
              nombre = "Etapa 4: Formulario 1 - Plan Técnico de Aprendizaje",
              estado = "Rechazado Metodológicamente",
              detalle = s"Rechazo: ${rechazoOpt.get}",
              concluida = false,
              alerta = true
            )
          } else {
            stages = stages :+ StageDetails(
              nombre = "Etapa 4: Formulario 1 - Plan Técnico de Aprendizaje",
              estado = "En Revisión / Firmas Pendientes",
              detalle = s"Firmas recolectadas -> Empresa: $firmaEmp, Tutor: $firmaAcad",
              concluida = false,
              alerta = false
            )
          }
        case None =>
          stages = stages :+ StageDetails(
            nombre = "Etapa 4: Formulario 1 - Plan Técnico de Aprendizaje",
            estado = "Pendiente de Carga",
            detalle = "El plan técnico de aprendizaje (F1) no ha sido inicializado por el estudiante.",
            concluida = false,
            alerta = false
          )
      }

      // --- 5. Formulario 2 (Rúbrica de Desempeño) ---
      val f2Opt = sql"""
        SELECT estado_formulario2::text, justificacion_rechazo_docente, fecha_registro
        FROM formulario2_evaluacion
        WHERE id_practica_ref = ${id}
        ORDER BY fecha_registro DESC
        LIMIT 1
      """.map { rs =>
        (rs.string("estado_formulario2"), rs.stringOpt("justificacion_rechazo_docente"))
      }.single.apply()

      f2Opt match {
        case Some((estado, rechazoOpt)) =>
          if (estado == "CONFORME") {
            stages = stages :+ StageDetails(
              nombre = "Etapa 5: Formulario 2 - Calificación Técnica del Desempeño",
              estado = "Conforme / Aprobado",
              detalle = "Rúbrica calificada de forma exitosa por el tutor empresarial y aprobada por el académico.",
              concluida = true,
              alerta = false
            )
          } else if (estado == "RECHAZADO") {
            stages = stages :+ StageDetails(
              nombre = "Etapa 5: Formulario 2 - Calificación Técnica del Desempeño",
              estado = "Rechazado por Tutor Académico",
              detalle = s"Rechazo: ${rechazoOpt.getOrElse("Sin justificación")}",
              concluida = false,
              alerta = true
            )
          } else {
            stages = stages :+ StageDetails(
              nombre = "Etapa 5: Formulario2 - Calificación Técnica del Desempeño",
              estado = "Pendiente de Revisión Académica",
              detalle = "Rúbrica técnica cargada por el tutor de acogida, en revisión del tutor docente.",
              concluida = false,
              alerta = false
            )
          }
        case None =>
          stages = stages :+ StageDetails(
            nombre = "Etapa 5: Formulario 2 - Calificación Técnica del Desempeño",
            estado = "Pendiente de Carga",
            detalle = "El tutor empresarial aún no registra el formulario de evaluación de desempeño.",
            concluida = false,
            alerta = false
          )
      }

      // --- 6. Formulario 3 / Cierre (Informe Consolidado & Auditoría) ---
      val f3Opt = sql"""
        SELECT id_f3_informe FROM formulario3_informe WHERE id_practica_ref = ${id}
      """.map(rs => rs.int("id_f3_informe")).single.apply()

      val auditOpt = sql"""
        SELECT estado_auditoria, observaciones_expediente, secuencial_version
        FROM auditoria_cierre
        WHERE id_practica_ref = ${id}
        ORDER BY secuencial_version DESC
        LIMIT 1
      """.map { rs =>
        (rs.string("estado_auditoria"), rs.stringOpt("observaciones_expediente"), rs.int("secuencial_version"))
      }.single.apply()

      (f3Opt, auditOpt) match {
        case (Some(_), Some((estado, observacionesOpt, version))) =>
          if (estado == "APROBADO") {
            stages = stages :+ StageDetails(
              nombre = "Etapa 6: Formulario 3 - Informe Académico Consolidado",
              estado = "Auditoría Concluida y Aprobada",
              detalle = s"Expediente de cierre aprobado en versión $version. Práctica CERRADA_VALIDA.",
              concluida = true,
              alerta = false
            )
          } else if (estado == "RECHAZADO") {
            stages = stages :+ StageDetails(
              nombre = "Etapa 6: Formulario 3 - Informe Académico Consolidado",
              estado = s"Rechazado en Auditoría (V$version)",
              detalle = s"Rechazo: ${observacionesOpt.getOrElse("Sin observaciones")}",
              concluida = false,
              alerta = true
            )
          } else {
            stages = stages :+ StageDetails(
              nombre = "Etapa 6: Formulario 3 - Informe Académico Consolidado",
              estado = "Informe Emitido / En Auditoría de Coordinación",
              detalle = s"En revisión del coordinador de carrera (Versión $version).",
              concluida = false,
              alerta = false
            )
          }
        case (Some(_), None) =>
          stages = stages :+ StageDetails(
            nombre = "Etapa 6: Formulario 3 - Informe Académico Consolidado",
            estado = "Informe Académico Emitido",
            detalle = "El tutor docente cargó el informe consolidado final. Listo para auditar.",
            concluida = false,
            alerta = false
          )
        case (None, _) =>
          stages = stages :+ StageDetails(
            nombre = "Etapa 6: Formulario 3 - Informe Académico Consolidado",
            estado = "Pendiente de Emisión",
            detalle = "El tutor académico docente aún no emite el informe consolidado final.",
            concluida = false,
            alerta = false
          )
      }

      PracticeTimelineDetails(
        idPractica = id,
        estudiante = estudiante,
        empresa = empresa,
        origenRama = origenRama,
        stages = stages
      )
    }
  }

  /**
   * Obtiene todos los comentarios de rechazo históricos para reconstruir la retroalimentación.
   */
  def getRejectionComments(idPractica: Int)(implicit session: DBSession = AutoSession): List[RejectionCommentDTO] = {
    // 1. F1
    val f1Comments = sql"""
      SELECT justificacion_rechazo_inicio, fecha_autorizacion
      FROM expediente_formulario1
      WHERE id_practica_ref = ${idPractica} AND justificacion_rechazo_inicio IS NOT NULL
    """.map { rs =>
      RejectionCommentDTO(
        etapa = "Formulario 1 (Plan Técnico)",
        comentario = rs.string("justificacion_rechazo_inicio"),
        fecha = rs.localDateOpt("fecha_autorizacion").getOrElse(LocalDate.now())
      )
    }.list.apply()

    // 2. F2
    val f2Comments = sql"""
      SELECT justificacion_rechazo_docente, fecha_registro
      FROM formulario2_evaluacion
      WHERE id_practica_ref = ${idPractica} AND estado_formulario2 = 'RECHAZADO'::estado_formulario2
    """.map { rs =>
      RejectionCommentDTO(
        etapa = "Formulario 2 (Rúbrica)",
        comentario = rs.string("justificacion_rechazo_docente"),
        fecha = rs.localDateTime("fecha_registro").toLocalDate
      )
    }.list.apply()

    // 3. F3 / Auditoria Cierre
    val auditComments = sql"""
      SELECT observaciones_expediente, secuencial_version
      FROM auditoria_cierre
      WHERE id_practica_ref = ${idPractica} AND estado_auditoria = 'RECHAZADO'
    """.map { rs =>
      RejectionCommentDTO(
        etapa = s"Auditoría de Cierre (V${rs.int("secuencial_version")})",
        comentario = rs.string("observaciones_expediente"),
        fecha = LocalDate.now()
      )
    }.list.apply()

    // 4. Actividades Cronograma
    val activityComments = sql"""
      SELECT ac.comentario_observacion, ac.fecha_registro, ac.numero_secuencial
      FROM actividad_cronograma ac
      WHERE ac.id_practica_ref = ${idPractica} AND ac.estado_actividad = 'RECHAZADA'::estado_actividad AND ac.comentario_observacion IS NOT NULL
    """.map { rs =>
      RejectionCommentDTO(
        etapa = s"Actividad Cronograma #${rs.int("numero_secuencial")}",
        comentario = rs.string("comentario_observacion"),
        fecha = rs.localDate("fecha_registro")
      )
    }.list.apply()

    (f1Comments ++ f2Comments ++ auditComments ++ activityComments).sortBy(_.fecha.toEpochDay)
  }
}
