package com.ucuenca.gestion.controllers

import javafx.fxml.FXML
import javafx.scene.control.{TextField, PasswordField, Label}
import javafx.event.ActionEvent
import com.ucuenca.gestion.models.logic.{AutenticacionLogic, AutenticacionFailure}
import com.ucuenca.gestion.utils.NavigationManager

class LoginController {

  @FXML
  var txtUsuario: TextField = _

  @FXML
  var txtContrasena: PasswordField = _

  @FXML
  var lblAlertaError: Label = _

  /**
   * Procesa el evento de clic en el botón de ingreso o Enter en los campos.
   */
  @FXML
  def handleIngreso(event: ActionEvent): Unit = {
    val username = txtUsuario.getText
    val password = txtContrasena.getText

    AutenticacionLogic.autenticar(username, password) match {
      case Right(usuario) =>
        lblAlertaError.setVisible(false)
        com.ucuenca.gestion.utils.SessionManager.setUsuario(usuario)
        // Redirige al panel principal según el rol
        NavigationManager.showMainForRole(usuario.rol.toString)

      case Left(failure) =>
        val mensaje = failure match {
          case AutenticacionFailure.CamposVacios =>
            "Por favor, complete todos los campos de credenciales."
          case AutenticacionFailure.CredencialesIncorrectas =>
            "Credenciales incorrectas. Verifique e intente de nuevo."
          case AutenticacionFailure.CuentaSuspendida =>
            "Acceso Bloqueado: Su cuenta se encuentra suspendida."
          case AutenticacionFailure.ErrorPersistencia(msg) =>
            s"Error técnico de persistencia: $msg"
        }
        lblAlertaError.setText(mensaje)
        lblAlertaError.setVisible(true)
    }
  }
}
