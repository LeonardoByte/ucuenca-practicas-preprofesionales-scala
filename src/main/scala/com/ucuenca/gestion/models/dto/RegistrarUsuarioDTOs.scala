package com.ucuenca.gestion.models.dto

import com.ucuenca.gestion.models.enums._

case class EstudianteDTO(
  identificacion: String,
  nombresCompletos: String,
  correoElectronico: String,
  password: String,
  cicloActual: Int,
  idCarreraRef: Int,
  estadoMatricula: EstadoMatricula,
  estadoPractica: EstadoEstudiantePractica,
  mallaNombre: String,
  mallaRuta: String,
  cvNombre: Option[String],
  cvRuta: Option[String]
)

case class EmpresaDTO(
  identificacion: String,
  nombresCompletos: String,
  correoElectronico: String,
  password: String,
  direccionMatriz: String,
  mision: String,
  vision: String,
  estadoConvenio: EstadoConvenio
)

case class TutorEmpresarialDTO(
  identificacion: String,
  nombresCompletos: String,
  correoElectronico: String,
  password: String,
  empresaIdRef: String,
  telefonoContacto: String
)

case class UsuarioGeneralDTO(
  identificacion: String,
  nombresCompletos: String,
  correoElectronico: String,
  password: String,
  rol: RolUsuario
)
