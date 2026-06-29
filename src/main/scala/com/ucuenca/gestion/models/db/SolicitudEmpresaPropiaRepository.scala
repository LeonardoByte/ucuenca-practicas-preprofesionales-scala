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
        ruc_empresa_propia,
        contacto_empresa_propia,
        horas_empresa_propia,
        direccion_empresa_propia,
        mision_empresa_propia,
        vision_empresa_propia,
        contenido_oficio_transcrito,
        ci_supervisor_externo,
        nombres_supervisor_externo,
        email_supervisor_externo,
        telefono_supervisor_externo,
        oficio_solicitud_inicial_pdf,
        estado_tramite,
        fecha_registro
      ) VALUES (
        ${dto.ciEstudianteRef},
        ${dto.nombreEntidadExterna},
        ${dto.rucEmpresaPropia},
        ${dto.contactoEmpresaPropia},
        ${dto.horasEmpresaPropia},
        ${dto.direccionEmpresaPropia},
        ${dto.misionEmpresaPropia},
        ${dto.visionEmpresaPropia},
        ${dto.contenidoOficioTranscrito},
        ${dto.ciSupervisorExterno},
        ${dto.nombresSupervisorExterno},
        ${dto.emailSupervisorExterno},
        ${dto.telefonoSupervisorExterno},
        ${pdfId},
        'PENDIENTE'::estado_convenio,
        CURRENT_DATE
      )
    """.updateAndReturnGeneratedKey.apply().toInt
  }
}
