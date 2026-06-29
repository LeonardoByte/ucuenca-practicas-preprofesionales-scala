package com.ucuenca.gestion.models.dto

case class CandidatoBolsaDTO(
  idPostulacion: Int,
  ciEstudiante: String,
  nombreEstudiante: String,
  idOferta: Int,
  tituloOferta: String,
  fechaPostulacion: java.time.LocalDate,
  cicloActual: Int,
  mallaAcademicaPDF: Int,
  curriculumVitaePDF: Option[Int]
)
