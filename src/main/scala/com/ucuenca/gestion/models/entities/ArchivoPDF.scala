package com.ucuenca.gestion.models.entities

import com.ucuenca.gestion.models.enums.TipoArchivoPDF
import java.time.LocalDateTime

case class ArchivoPDF(
  idArchivoPDF: Int,
  tipoArchivo: TipoArchivoPDF,
  nombreOriginal: String,
  rutaSeguraServidor: String,
  fechaCarga: LocalDateTime
)
