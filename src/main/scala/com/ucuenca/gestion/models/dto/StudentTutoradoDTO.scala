package com.ucuenca.gestion.models.dto

case class StudentTutoradoDTO(
  idPractica: Int,
  nombreEstudiante: String,
  nombreEmpresa: String,
  cicloActual: Int,
  pendientesCount: Int
)
