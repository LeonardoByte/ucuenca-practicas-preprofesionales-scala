package com.ucuenca.gestion.models.dto

case class AsignacionDocenteDTO(
  idPractica: Int,
  ciEstudiante: String,
  nombreEstudiante: String,
  carrera: String,
  nombreEmpresa: String,
  tituloOferta: String,
  var idTutorAcademicoRef: Option[String] = None
)
