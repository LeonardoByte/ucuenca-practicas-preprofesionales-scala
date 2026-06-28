package com.ucuenca.gestion.models.dto

case class CrearOfertaDTO(
  rucEmpresaRef: String,
  tituloOferta: String,
  vacantesSolicitadas: Int,
  duracionHoras: Int,
  descripcionGeneral: String,
  requisitosObligatorios: String,
  actividadesEspecificas: String,
  pdfNombreOriginal: String,
  pdfRutaSegura: String
)
