package com.ucuenca.gestion.models.dto

case class ExpedientePendienteCoordinadorDTO(
  idPractica: Int,
  ciEstudiante: String,
  nombreEstudiante: String,
  nombreEmpresa: String,
  convenioFormalizado: Boolean,
  cartasCompromisoEntregadas: Boolean
) {
  def puedeAprobarse: Boolean = convenioFormalizado || cartasCompromisoEntregadas

  def estadoCartasTexto: String =
    if (convenioFormalizado) "No Requerido (Posee Convenio)"
    else if (cartasCompromisoEntregadas) "Entregadas en Secretaría"
    else "Pendiente de Entrega en Secretaría"
}
