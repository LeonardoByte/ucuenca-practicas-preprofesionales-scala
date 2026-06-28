package com.ucuenca.gestion.models.db

import scalikejdbc._
import com.ucuenca.gestion.models.dto.CrearOfertaDTO

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
}
