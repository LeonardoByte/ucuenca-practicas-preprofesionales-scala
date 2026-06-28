package com.ucuenca.gestion.models.dto

case class OfertaRevisionDTO(
  idOferta: Int,
  rucEmpresaRef: String,
  nombreEmpresa: String,
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
