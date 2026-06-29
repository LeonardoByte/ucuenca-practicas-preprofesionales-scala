package com.ucuenca.gestion.models.db

import scalikejdbc._
import com.ucuenca.gestion.models.dto.CandidatoBolsaDTO

object GestionCandidatosRepository {

  /**
   * Obtiene la lista de candidatos que postularon a ofertas de la empresa y fueron validados por el coordinador.
   */
  def listarCandidatosPorEmpresa(companyRuc: String)(implicit session: DBSession = AutoSession): List[CandidatoBolsaDTO] = {
    sql"""
      SELECT p.id_postulacion, p.ci_estudiante_ref, u.nombres_completos AS nombre_estudiante,
             p.id_oferta_ref, o.titulo_oferta, p.fecha_postulacion, ep.ciclo_actual,
             ep.malla_academica_pdf, ep.curriculum_vitae_pdf
      FROM postulacion_bolsa p
      INNER JOIN estudiante_perfil ep ON p.ci_estudiante_ref = ep.identificacion
      INNER JOIN usuario u ON ep.identificacion = u.identificacion
      INNER JOIN oferta_convocatoria o ON p.id_oferta_ref = o.id_oferta
      WHERE o.ruc_empresa_ref = ${companyRuc} AND p.estado_postulacion = 'VALIDADA_COORDINADOR'::estado_postulacion
      ORDER BY p.fecha_postulacion ASC
    """.map { rs =>
      CandidatoBolsaDTO(
        idPostulacion = rs.int("id_postulacion"),
        ciEstudiante = rs.string("ci_estudiante_ref"),
        nombreEstudiante = rs.string("nombre_estudiante"),
        idOferta = rs.int("id_oferta_ref"),
        tituloOferta = rs.string("titulo_oferta"),
        fechaPostulacion = rs.localDate("fecha_postulacion"),
        cicloActual = rs.int("ciclo_actual"),
        mallaAcademicaPDF = rs.int("malla_academica_pdf"),
        curriculumVitaePDF = rs.string("curriculum_vitae_pdf") match {
          case null => None
          case _ => Some(rs.int("curriculum_vitae_pdf"))
        }
      )
    }.list.apply()
  }

  /**
   * Obtiene la nómina de tutores/supervisores empresariales pertenecientes a la empresa.
   */
  def listarTutoresEmpresariales(companyRuc: String)(implicit session: DBSession = AutoSession): List[(String, String)] = {
    sql"""
      SELECT t.identificacion, u.nombres_completos
      FROM tutor_empresarial_perfil t
      INNER JOIN usuario u ON t.identificacion = u.identificacion
      WHERE t.empresa_id_ref = ${companyRuc}
      ORDER BY u.nombres_completos ASC
    """.map(rs => (rs.string("identificacion"), rs.string("nombres_completos"))).list.apply()
  }

  /**
   * Ejecuta transaccionalmente la aceptación del candidato, asociándolo a un supervisor técnico,
   * inicializando su práctica y gatillando cancelaciones masivas de postulaciones paralelas y cierres de cupo.
   */
  def aceptarCandidato(
    idPostulacion: Int,
    rucEmpresa: String,
    ciEstudiante: String,
    idTutorEmpresarial: String,
    horasTotales: Int
  )(implicit session: DBSession = AutoSession): Unit = {
    // 1. Obtener detalles de la oferta de la postulación
    val idOferta = sql"SELECT id_oferta_ref FROM postulacion_bolsa WHERE id_postulacion = ${idPostulacion}"
      .map(rs => rs.int("id_oferta_ref")).single.apply()
      .getOrElse(throw new IllegalArgumentException("No se encontró la oferta asociada a la postulación."))

    // 2. Actualizar el estado de la postulación elegida a APROBADA
    sql"""
      UPDATE postulacion_bolsa
      SET estado_postulacion = 'APROBADA'::estado_postulacion
      WHERE id_postulacion = ${idPostulacion}
    """.update.apply()

    // 3. Inicializar registro de la práctica
    sql"""
      INSERT INTO practica_registro (
        ci_estudiante_ref,
        ruc_empresa_ref,
        id_tutor_academico_ref,
        id_tutor_empresarial_ref,
        origen_rama,
        estado_cronograma,
        horas_totales_requeridas
      ) VALUES (
        ${ciEstudiante},
        ${rucEmpresa},
        NULL,
        ${idTutorEmpresarial},
        'BOLSA_EMPLEO'::origen_rama,
        'TUTOR_ACADEMICO_PENDIENTE'::estado_cronograma,
        ${horasTotales}
      )
    """.update.apply()

    // 4. Actualizar el perfil del estudiante a 'CON_PRACTICA_ACTIVA'
    sql"""
      UPDATE estudiante_perfil
      SET estado_estudiante_practica = 'CON_PRACTICA_ACTIVA'::estado_estudiante_practica
      WHERE identificacion = ${ciEstudiante}
    """.update.apply()

    // 5. Cancelación masiva de todas las demás aplicaciones activas del estudiante
    sql"""
      UPDATE postulacion_bolsa
      SET estado_postulacion = 'CANCELADA_AUTOMATICO'::estado_postulacion
      WHERE ci_estudiante_ref = ${ciEstudiante}
        AND id_postulacion <> ${idPostulacion}
        AND estado_postulacion IN ('PENDIENTE'::estado_postulacion, 'VALIDADA_COORDINADOR'::estado_postulacion)
    """.update.apply()

    // 6. Control de llenado de cupos de la convocatoria
    val limiteCupos = sql"SELECT vacantes_solicitadas FROM oferta_convocatoria WHERE id_oferta = ${idOferta}"
      .map(rs => rs.int("vacantes_solicitadas")).single.apply().getOrElse(0)

    val cuposAprobados = sql"SELECT COUNT(1) FROM postulacion_bolsa WHERE id_oferta_ref = ${idOferta} AND estado_postulacion = 'APROBADA'::estado_postulacion"
      .map(rs => rs.int(1)).single.apply().getOrElse(0)

    if (cuposAprobados >= limiteCupos) {
      // Marcar oferta como CERRADA_CUPOS
      sql"""
        UPDATE oferta_convocatoria
        SET estado_oferta = 'CERRADA_CUPOS'::estado_oferta
        WHERE id_oferta = ${idOferta}
      """.update.apply()

      // Liberar/Rechazar candidatos restantes en espera
      sql"""
        UPDATE postulacion_bolsa
        SET estado_postulacion = 'RECHAZADA'::estado_postulacion,
            comentario_rechazo = 'Convocatoria cerrada por límite de cupos alcanzado.'
        WHERE id_oferta_ref = ${idOferta}
          AND estado_postulacion IN ('PENDIENTE'::estado_postulacion, 'VALIDADA_COORDINADOR'::estado_postulacion)
      """.update.apply()
    }
  }

  /**
   * Rechaza corporativamente una postulación, cambiando su estado a RECHAZADA.
   */
  def rechazarCandidato(idPostulacion: Int, comentario: String)(implicit session: DBSession = AutoSession): Unit = {
    sql"""
      UPDATE postulacion_bolsa
      SET estado_postulacion = 'RECHAZADA'::estado_postulacion,
          comentario_rechazo = ${comentario}
      WHERE id_postulacion = ${idPostulacion}
    """.update.apply()
  }
}
