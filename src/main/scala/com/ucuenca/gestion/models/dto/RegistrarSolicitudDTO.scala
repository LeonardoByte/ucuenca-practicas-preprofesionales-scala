package com.ucuenca.gestion.models.dto

case class RegistrarSolicitudDTO(
  ciEstudianteRef: String,
  nombreEntidadExterna: String,
  contactoEmpresaPropia: String,
  horasEmpresaPropia: Int,
  contenidoOficioTranscrito: String,
  pdfNombreOriginal: String,
  pdfRutaSegura: String
)
