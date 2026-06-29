package com.ucuenca.gestion.models.dto

case class OfertaBolsaDTO(
  idOferta: Int,
  rucEmpresaRef: String,
  nombreEmpresa: String,
  ubicacionEmpresa: String,
  tituloOferta: String,
  vacantesSolicitadas: Int,
  duracionHoras: Int,
  descripcionGeneral: String,
  requisitosObligatorios: String,
  actividadesEspecificas: String,
  plantillaOfertaPDF: Int,
  estadoOferta: String,
  fechaSolicitud: java.time.LocalDate
)
