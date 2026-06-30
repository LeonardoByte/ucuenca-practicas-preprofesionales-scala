package com.ucuenca.gestion.models.db

import scalikejdbc._
import com.ucuenca.gestion.models.dto.CartaCompromisoPendienteDTO

object ValidacionCartaRepository {

  /**
   * Obtiene los alumnos pendientes de validación de Carta de Compromiso.
   * Filtra por inicio de fase y empresa receptora sin convenio marco.
   */
  def listarPendientes(searchQuery: Option[String])(implicit session: DBSession = AutoSession): List[CartaCompromisoPendienteDTO] = {
    val q = searchQuery.map(_.trim.toLowerCase).filter(_.nonEmpty)
    
    q match {
      case Some(searchStr) =>
        val likeStr = s"%$searchStr%"
        sql"""
          SELECT 
            pr.ci_estudiante_ref AS ci_estudiante,
            u.nombres_completos AS nombre_estudiante
          FROM practica_registro pr
          INNER JOIN estudiante_perfil ep ON pr.ci_estudiante_ref = ep.identificacion
          INNER JOIN usuario u ON ep.identificacion = u.identificacion
          INNER JOIN empresa_perfil emp ON pr.ruc_empresa_ref = emp.identificacion
          LEFT JOIN validacion_carta_compromiso vcc ON pr.ci_estudiante_ref = vcc.ci_estudiante
          WHERE pr.estado_cronograma IN ('TUTOR_ACADEMICO_PENDIENTE'::estado_cronograma, 'F1_PENDIENTE'::estado_cronograma)
            AND emp.estado_convenio != 'FORMALIZADO'::estado_convenio
            AND vcc.ci_estudiante IS NULL
            AND (LOWER(u.nombres_completos) LIKE ${likeStr} OR LOWER(pr.ci_estudiante_ref) LIKE ${likeStr})
          ORDER BY u.nombres_completos ASC
        """.map { rs =>
          CartaCompromisoPendienteDTO(
            ciEstudiante = rs.string("ci_estudiante"),
            nombreEstudiante = rs.string("nombre_estudiante")
          )
        }.list.apply()
        
      case None =>
        sql"""
          SELECT 
            pr.ci_estudiante_ref AS ci_estudiante,
            u.nombres_completos AS nombre_estudiante
          FROM practica_registro pr
          INNER JOIN estudiante_perfil ep ON pr.ci_estudiante_ref = ep.identificacion
          INNER JOIN usuario u ON ep.identificacion = u.identificacion
          INNER JOIN empresa_perfil emp ON pr.ruc_empresa_ref = emp.identificacion
          LEFT JOIN validacion_carta_compromiso vcc ON pr.ci_estudiante_ref = vcc.ci_estudiante
          WHERE pr.estado_cronograma IN ('TUTOR_ACADEMICO_PENDIENTE'::estado_cronograma, 'F1_PENDIENTE'::estado_cronograma)
            AND emp.estado_convenio != 'FORMALIZADO'::estado_convenio
            AND vcc.ci_estudiante IS NULL
          ORDER BY u.nombres_completos ASC
        """.map { rs =>
          CartaCompromisoPendienteDTO(
            ciEstudiante = rs.string("ci_estudiante"),
            nombreEstudiante = rs.string("nombre_estudiante")
          )
        }.list.apply()
    }
  }

  /**
   * Certifica la entrega física insertando en la tabla validacion_carta_compromiso
   * y registrando la firma digital en archivo_pdf.
   */
  def registrarValidacion(ciEstudiante: String, entregadoTresCopias: Boolean)(implicit session: DBSession = AutoSession): Unit = {
    // 1. Insertar el PDF digitalizado
    val pdfId = sql"""
      INSERT INTO archivo_pdf (tipo_archivo, nombre_original, ruta_segura_servidor)
      VALUES (
        'T6_CARTA_COMPROMISO'::tipo_archivo_pdf,
        ${s"carta_compromiso_$ciEstudiante.pdf"},
        ${s"uploads/cartas_compromiso/carta_compromiso_$ciEstudiante.pdf"}
      )
    """.updateAndReturnGeneratedKey.apply().toInt

    // 2. Insertar validación (la DB aplicará check entregado_tres_copias = TRUE)
    sql"""
      INSERT INTO validacion_carta_compromiso (ci_estudiante, entregado_tres_copias, carta_compromiso_pdf)
      VALUES (${ciEstudiante}, ${entregadoTresCopias}, ${pdfId})
    """.update.apply()
  }
}
