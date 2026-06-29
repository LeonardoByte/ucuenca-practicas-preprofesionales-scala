package com.ucuenca.gestion.models.db

import scalikejdbc._
import com.ucuenca.gestion.models.dto.OfertaBolsaDTO

object PostulacionBolsaRepository {

  /**
   * Obtiene todas las ofertas aprobadas por la coordinación, ordenadas de forma descendente.
   * Incluye la razón social de la empresa y la dirección matriz.
   */
  def listarOfertasAprobadas()(implicit session: DBSession = AutoSession): List[OfertaBolsaDTO] = {
    sql"""
      SELECT o.id_oferta, o.ruc_empresa_ref, u.nombres_completos AS nombre_empresa, ep.direccion_matriz AS ubicacion_empresa,
             o.titulo_oferta, o.vacantes_solicitadas, o.duracion_horas, o.descripcion_general,
             o.requisitos_obligatorios, o.actividades_especificas, o.plantilla_oferta_pdf, o.estado_oferta,
             p.fecha_carga::date AS fecha_solicitud
      FROM oferta_convocatoria o
      INNER JOIN usuario u ON o.ruc_empresa_ref = u.identificacion
      INNER JOIN empresa_perfil ep ON o.ruc_empresa_ref = ep.identificacion
      INNER JOIN archivo_pdf p ON o.plantilla_oferta_pdf = p.id_archivo_pdf
      WHERE o.estado_oferta = 'APROBADA'
      ORDER BY o.id_oferta DESC
    """.map { rs =>
      OfertaBolsaDTO(
        idOferta = rs.int("id_oferta"),
        rucEmpresaRef = rs.string("ruc_empresa_ref"),
        nombreEmpresa = rs.string("nombre_empresa"),
        ubicacionEmpresa = rs.string("ubicacion_empresa"),
        tituloOferta = rs.string("titulo_oferta"),
        vacantesSolicitadas = rs.int("vacantes_solicitadas"),
        duracionHoras = rs.int("duracion_horas"),
        descripcionGeneral = rs.string("descripcion_general"),
        requisitosObligatorios = rs.string("requisitos_obligatorios"),
        actividadesEspecificas = rs.string("actividades_especificas"),
        plantillaOfertaPDF = rs.int("plantilla_oferta_pdf"),
        estadoOferta = rs.string("estado_oferta"),
        fechaSolicitud = rs.localDate("fecha_solicitud")
      )
    }.list.apply()
  }

  /**
   * Registra una postulación a la bolsa de empleo para un estudiante y oferta determinados, forzando PENDIENTE.
   */
  def registrarPostulacion(ciEstudiante: String, idOferta: Int)(implicit session: DBSession = AutoSession): Unit = {
    sql"""
      INSERT INTO postulacion_bolsa (
        ci_estudiante_ref,
        id_oferta_ref,
        estado_postulacion
      ) VALUES (
        ${ciEstudiante},
        ${idOferta},
        'PENDIENTE'::estado_postulacion
      )
    """.update.apply()
  }

  /**
   * Verifica si el estudiante ya cuenta con una postulación previa (en cualquier estado) para la oferta especificada.
   */
  def yaPostulo(ciEstudiante: String, idOferta: Int)(implicit session: DBSession = AutoSession): Boolean = {
    sql"""
      SELECT COUNT(1)
      FROM postulacion_bolsa
      WHERE ci_estudiante_ref = ${ciEstudiante} AND id_oferta_ref = ${idOferta}
    """.map(rs => rs.int(1)).single.apply().getOrElse(0) > 0
  }
}
