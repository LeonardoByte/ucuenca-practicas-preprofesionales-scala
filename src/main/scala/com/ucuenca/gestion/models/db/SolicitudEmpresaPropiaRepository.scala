package com.ucuenca.gestion.models.db

import scalikejdbc._
import com.ucuenca.gestion.models.dto.RegistrarSolicitudDTO

object SolicitudEmpresaPropiaRepository {

  def registrar(dto: RegistrarSolicitudDTO)(implicit session: DBSession = AutoSession): Int = {
    val pdfId = sql"""
      INSERT INTO archivo_pdf (tipo_archivo, nombre_original, ruta_segura_servidor)
      VALUES (
        'T4_OFICIO_SOLICITUD_PROP_INICIAL'::tipo_archivo_pdf,
        ${dto.pdfNombreOriginal},
        ${dto.pdfRutaSegura}
      )
    """.updateAndReturnGeneratedKey.apply().toInt

    sql"""
      INSERT INTO solicitud_empresa_propia (
        ci_estudiante_ref,
        nombre_entidad_externa,
        contacto_empresa_propia,
        horas_empresa_propia,
        contenido_oficio_transcrito,
        oficio_solicitud_inicial_pdf,
        estado_tramite,
        fecha_registro
      ) VALUES (
        ${dto.ciEstudianteRef},
        ${dto.nombreEntidadExterna},
        ${dto.contactoEmpresaPropia},
        ${dto.horasEmpresaPropia},
        ${dto.contenidoOficioTranscrito},
        ${pdfId},
        'PENDIENTE'::estado_convenio,
        CURRENT_DATE
      )
    """.updateAndReturnGeneratedKey.apply().toInt
  }
}
