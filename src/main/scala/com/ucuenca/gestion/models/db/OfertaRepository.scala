package com.ucuenca.gestion.models.db

import scalikejdbc._
import com.ucuenca.gestion.models.dto.CrearOfertaDTO
import com.ucuenca.gestion.models.entities.OfertaConvocatoria
import com.ucuenca.gestion.models.enums.EstadoOferta

object OfertaRepository {

  /**
   * Inserta un archivo PDF y la oferta asociada dentro de una misma sesión de base de datos.
   * Asume que se ejecuta dentro de una transacción gestionada por la capa de lógica.
   * Retorna el id de la oferta generada.
   */
  def crearOferta(dto: CrearOfertaDTO)(implicit session: DBSession = AutoSession): Int = {
    // 1. Insertar el PDF de plantilla en la tabla archivo_pdf
    val pdfId = sql"""
      INSERT INTO archivo_pdf (tipo_archivo, nombre_original, ruta_segura_servidor)
      VALUES (
        'T7_PLANTILLA_FORMATO_OFERTA'::tipo_archivo_pdf,
        ${dto.pdfNombreOriginal},
        ${dto.pdfRutaSegura}
      )
    """.updateAndReturnGeneratedKey.apply().toInt

    // 2. Insertar la oferta en la tabla oferta_convocatoria enlazando el pdfId y forzando PENDIENTE
    val ofertaId = sql"""
      INSERT INTO oferta_convocatoria (
        ruc_empresa_ref,
        titulo_oferta,
        vacantes_solicitadas,
        duracion_horas,
        descripcion_general,
        requisitos_obligatorios,
        actividades_especificas,
        plantilla_oferta_pdf,
        estado_oferta
      ) VALUES (
        ${dto.rucEmpresaRef},
        ${dto.tituloOferta},
        ${dto.vacantesSolicitadas},
        ${dto.duracionHoras},
        ${dto.descripcionGeneral},
        ${dto.requisitosObligatorios},
        ${dto.actividadesEspecificas},
        ${pdfId},
        'PENDIENTE'::estado_oferta
      )
    """.updateAndReturnGeneratedKey.apply().toInt

    ofertaId
  }

  /**
   * Retorna todas las ofertas publicadas por una empresa, ordenadas por ID descendente (cronología inversa).
   */
  def buscarPorEmpresa(rucEmpresa: String)(implicit session: DBSession = AutoSession): List[OfertaConvocatoria] = {
    sql"""
      SELECT id_oferta, ruc_empresa_ref, titulo_oferta, vacantes_solicitadas, duracion_horas,
             descripcion_general, requisitos_obligatorios, actividades_especificas, plantilla_oferta_pdf,
             estado_oferta, justificacion_coordinador, fecha_publicacion
      FROM oferta_convocatoria
      WHERE ruc_empresa_ref = ${rucEmpresa}
      ORDER BY id_oferta DESC
    """.map { rs =>
      OfertaConvocatoria(
        idOferta = rs.int("id_oferta"),
        rucEmpresaRef = rs.string("ruc_empresa_ref"),
        tituloOferta = rs.string("titulo_oferta"),
        vacantesSolicitadas = rs.int("vacantes_solicitadas"),
        duracionHoras = rs.int("duracion_horas"),
        descripcionGeneral = rs.string("descripcion_general"),
        requisitosObligatorios = rs.string("requisitos_obligatorios"),
        actividadesEspecificas = rs.string("actividades_especificas"),
        plantillaOfertaPDF = rs.int("plantilla_oferta_pdf"),
        estadoOferta = EstadoOferta.valueOf(rs.string("estado_oferta")),
        justificacionCoordinador = rs.stringOpt("justificacion_coordinador"),
        fechaPublicacion = rs.localDateOpt("fecha_publicacion")
      )
    }.list.apply()
  }

  /**
   * Busca una oferta por su ID único.
   */
  def buscarPorId(idOferta: Int)(implicit session: DBSession = AutoSession): Option[OfertaConvocatoria] = {
    sql"""
      SELECT id_oferta, ruc_empresa_ref, titulo_oferta, vacantes_solicitadas, duracion_horas,
             descripcion_general, requisitos_obligatorios, actividades_especificas, plantilla_oferta_pdf,
             estado_oferta, justificacion_coordinador, fecha_publicacion
      FROM oferta_convocatoria
      WHERE id_oferta = ${idOferta}
    """.map { rs =>
      OfertaConvocatoria(
        idOferta = rs.int("id_oferta"),
        rucEmpresaRef = rs.string("ruc_empresa_ref"),
        tituloOferta = rs.string("titulo_oferta"),
        vacantesSolicitadas = rs.int("vacantes_solicitadas"),
        duracionHoras = rs.int("duracion_horas"),
        descripcionGeneral = rs.string("descripcion_general"),
        requisitosObligatorios = rs.string("requisitos_obligatorios"),
        actividadesEspecificas = rs.string("actividades_especificas"),
        plantillaOfertaPDF = rs.int("plantilla_oferta_pdf"),
        estadoOferta = EstadoOferta.valueOf(rs.string("estado_oferta")),
        justificacionCoordinador = rs.stringOpt("justificacion_coordinador"),
        fechaPublicacion = rs.localDateOpt("fecha_publicacion")
      )
    }.single.apply()
  }

  /**
   * Modifica el estado de una oferta a CERRADA_MANUAL.
   */
  def cerrarOferta(idOferta: Int)(implicit session: DBSession = AutoSession): Unit = {
    sql"""
      UPDATE oferta_convocatoria
      SET estado_oferta = 'CERRADA_MANUAL'::estado_oferta
      WHERE id_oferta = ${idOferta}
    """.update.apply()
  }
}
