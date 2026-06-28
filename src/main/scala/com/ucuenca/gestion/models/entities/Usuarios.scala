package com.ucuenca.gestion.models.entities

import com.ucuenca.gestion.models.enums._

case class Usuario(
  identificacion: String,
  nombresCompletos: String,
  correoElectronico: String,
  rol: RolUsuario,
  estadoCuenta: EstadoCuenta
) {
  def estaActivo: Boolean = estadoCuenta == EstadoCuenta.ACTIVA
}

case class EstudiantePerfil(
  identificacion: String,
  cicloActual: Int,
  idCarreraRef: Int,
  estadoMatricula: EstadoMatricula,
  estadoEstudiantePractica: EstadoEstudiantePractica,
  mallaAcademicaPDF: Int,
  curriculumVitaePDF: Option[Int]
) {
  def puedePostular: Boolean =
    cicloActual >= 6 && estadoEstudiantePractica == EstadoEstudiantePractica.SIN_PRACTICA

  def tieneCV: Boolean = curriculumVitaePDF.isDefined
}

case class EmpresaPerfil(
  identificacion: String,
  direccionMatriz: String,
  mision: String,
  vision: String,
  estadoConvenio: EstadoConvenio
) {
  def tieneConvenioVigente: Boolean = estadoConvenio == EstadoConvenio.FORMALIZADO
}

case class TutorEmpresarialPerfil(
  identificacion: String,
  empresaIdRef: String,
  telefonoContacto: String
)
