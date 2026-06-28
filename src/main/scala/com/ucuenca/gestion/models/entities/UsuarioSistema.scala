package com.ucuenca.gestion.models.entities

case class UsuarioSistema(
  idUsuarioSistema: Int,
  username: String,
  passwordHash: String,
  identificacionUsuarioRef: String
)
