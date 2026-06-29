package com.ucuenca.gestion.models.dto

case class PostulacionPendienteDTO(
  idPostulacion: Int,
  ciEstudiante: String,
  nombreEstudiante: String,
  idOferta: Int,
  tituloOferta: String,
  nombreEmpresa: String,
  fechaPostulacion: java.time.LocalDate,
  cicloActual: Int,
  estadoEstudiantePractica: String,
  mallaAcademicaPDF: Int,
  curriculumVitaePDF: Option[Int]
)
