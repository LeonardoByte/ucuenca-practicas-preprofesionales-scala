package com.ucuenca.gestion.models.dto

case class SolicitudPropiaRevisionDTO(
  idSolicitudPropia: Int,
  ciEstudianteRef: String,
  nombreEstudiante: String,
  nombreEntidadExterna: String,
  rucEmpresaPropia: String,
  contactoEmpresaPropia: String,
  horasEmpresaPropia: Int,
  direccionEmpresaPropia: String,
  misionEmpresaPropia: String,
  visionEmpresaPropia: String,
  contenidoOficioTranscrito: String,
  ciSupervisorExterno: String,
  nombresSupervisorExterno: String,
  emailSupervisorExterno: String,
  telefonoSupervisorExterno: String,
  oficioSolicitudInicialPDF: Int,
  estadoTramite: String,
  fechaRegistro: java.time.LocalDate
)
