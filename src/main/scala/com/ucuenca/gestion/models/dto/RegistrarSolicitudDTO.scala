package com.ucuenca.gestion.models.dto

case class RegistrarSolicitudDTO(
  ciEstudianteRef: String,
  // Company identification
  nombreEntidadExterna: String,
  rucEmpresaPropia: String,
  contactoEmpresaPropia: String,
  horasEmpresaPropia: Int,
  // JIT company profile fields
  direccionEmpresaPropia: String,
  misionEmpresaPropia: String,
  visionEmpresaPropia: String,
  // Core office transcript
  contenidoOficioTranscrito: String,
  // JIT external supervisor fields
  ciSupervisorExterno: String,
  nombresSupervisorExterno: String,
  emailSupervisorExterno: String,
  telefonoSupervisorExterno: String,
  // Initial office PDF (T4_OFICIO_SOLICITUD_PROP_INICIAL)
  pdfNombreOriginal: String,
  pdfRutaSegura: String
)
