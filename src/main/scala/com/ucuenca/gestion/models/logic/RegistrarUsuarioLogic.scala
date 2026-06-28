package com.ucuenca.gestion.models.logic

import com.ucuenca.gestion.models.entities._
import com.ucuenca.gestion.models.enums._
import com.ucuenca.gestion.models.db.UsuarioRepository
import com.ucuenca.gestion.models.dto._
import com.ucuenca.gestion.utils.PasswordHasher
import scalikejdbc.DB
import scala.util.control.NonFatal

sealed trait RegistroFailure
object RegistroFailure {
  case class Validacion(mensaje: String) extends RegistroFailure
  case class ErrorPersistencia(mensaje: String) extends RegistroFailure
}

object RegistrarUsuarioLogic {

  // --- Métodos de registro ---

  def registrarEstudiante(dto: EstudianteDTO): Either[RegistroFailure, Unit] = {
    for {
      _ <- validarUsuarioBase(dto.identificacion, dto.nombresCompletos, dto.correoElectronico, dto.password, isEmpresa = false)
      _ <- validarIdentificacionPersona(dto.identificacion)
      _ <- validarCiclo(dto.cicloActual)
      _ <- validarMalla(dto.mallaNombre, dto.mallaRuta)
      res <- ejecutarTransaccion { implicit session =>
        val passHash = PasswordHasher.hash(dto.password)
        val usuario = Usuario(dto.identificacion, dto.nombresCompletos, dto.correoElectronico, RolUsuario.ESTUDIANTE, EstadoCuenta.ACTIVA)
        val creds = UsuarioSistema(0, dto.correoElectronico, passHash, dto.identificacion)
        
        val mallaPdf = ArchivoPDF(0, TipoArchivoPDF.T9_MALLA_ACADEMICA, dto.mallaNombre, dto.mallaRuta, java.time.LocalDateTime.now())
        val cvPdfOpt = (dto.cvNombre, dto.cvRuta) match {
          case (Some(name), Some(path)) if name.trim.nonEmpty && path.trim.nonEmpty =>
            Some(ArchivoPDF(0, TipoArchivoPDF.T10_CURRICULUM_VITAE, name, path, java.time.LocalDateTime.now()))
          case _ => None
        }

        UsuarioRepository.crearEstudiante(usuario, creds, mallaPdf, cvPdfOpt, dto.cicloActual, dto.idCarreraRef, dto.estadoMatricula, dto.estadoPractica)
      }
    } yield res
  }

  def registrarEmpresa(dto: EmpresaDTO): Either[RegistroFailure, Unit] = {
    for {
      _ <- validarUsuarioBase(dto.identificacion, dto.nombresCompletos, dto.correoElectronico, dto.password, isEmpresa = true)
      _ <- validarIdentificacionEmpresa(dto.identificacion)
      _ <- validarCamposObligatorios(Map(
        "Dirección matriz" -> dto.direccionMatriz,
        "Misión" -> dto.mision,
        "Visión" -> dto.vision
      ))
      res <- ejecutarTransaccion { implicit session =>
        val passHash = PasswordHasher.hash(dto.password)
        val usuario = Usuario(dto.identificacion, dto.nombresCompletos, dto.correoElectronico, RolUsuario.EMPRESA, EstadoCuenta.ACTIVA)
        val creds = UsuarioSistema(0, dto.correoElectronico, passHash, dto.identificacion)
        
        UsuarioRepository.crearEmpresa(usuario, creds, dto.direccionMatriz, dto.mision, dto.vision, dto.estadoConvenio)
      }
    } yield res
  }

  def registrarTutorEmpresarial(dto: TutorEmpresarialDTO): Either[RegistroFailure, Unit] = {
    for {
      _ <- validarUsuarioBase(dto.identificacion, dto.nombresCompletos, dto.correoElectronico, dto.password, isEmpresa = false)
      _ <- validarIdentificacionPersona(dto.identificacion)
      _ <- validarTelefonoTutor(dto.telefonoContacto)
      _ <- validarEmpresaAsociada(dto.empresaIdRef)
      res <- ejecutarTransaccion { implicit session =>
        val passHash = PasswordHasher.hash(dto.password)
        val usuario = Usuario(dto.identificacion, dto.nombresCompletos, dto.correoElectronico, RolUsuario.TUTOR_EMPRESARIAL, EstadoCuenta.ACTIVA)
        val creds = UsuarioSistema(0, dto.correoElectronico, passHash, dto.identificacion)

        UsuarioRepository.crearTutorEmpresarial(usuario, creds, dto.empresaIdRef, dto.telefonoContacto)
      }
    } yield res
  }

  def registrarUsuarioGeneral(dto: UsuarioGeneralDTO): Either[RegistroFailure, Unit] = {
    for {
      _ <- validarUsuarioBase(dto.identificacion, dto.nombresCompletos, dto.correoElectronico, dto.password, isEmpresa = false)
      _ <- validarIdentificacionPersona(dto.identificacion)
      res <- ejecutarTransaccion { implicit session =>
        val passHash = PasswordHasher.hash(dto.password)
        val usuario = Usuario(dto.identificacion, dto.nombresCompletos, dto.correoElectronico, dto.rol, EstadoCuenta.ACTIVA)
        val creds = UsuarioSistema(0, dto.correoElectronico, passHash, dto.identificacion)

        UsuarioRepository.crearUsuarioGeneral(usuario, creds)
      }
    } yield res
  }

  // --- Validaciones ---

  private def validarUsuarioBase(
    identificacion: String,
    nombres: String,
    email: String,
    password: String,
    isEmpresa: Boolean
  ): Either[RegistroFailure, Unit] = {
    if (nombres == null || nombres.trim.length < 3) {
      Left(RegistroFailure.Validacion("El nombre completo debe tener al menos 3 caracteres."))
    } else if (email == null || !email.contains("@") || !email.contains(".")) {
      Left(RegistroFailure.Validacion("El correo electrónico no es válido."))
    } else if (password == null || password.trim.length < 6) {
      Left(RegistroFailure.Validacion("La contraseña debe tener al menos 6 caracteres."))
    } else {
      Right(())
    }
  }

  private def validarIdentificacionPersona(id: String): Either[RegistroFailure, Unit] = {
    if (id == null || id.length != 10 || !id.forall(_.isDigit)) {
      Left(RegistroFailure.Validacion("La identificación para personas debe tener exactamente 10 dígitos numéricos."))
    } else {
      Right(())
    }
  }

  private def validarIdentificacionEmpresa(id: String): Either[RegistroFailure, Unit] = {
    if (id == null || id.length != 13 || !id.forall(_.isDigit)) {
      Left(RegistroFailure.Validacion("La identificación RUC para empresas debe tener exactamente 13 dígitos numéricos."))
    } else {
      Right(())
    }
  }

  private def validarCiclo(ciclo: Int): Either[RegistroFailure, Unit] = {
    if (ciclo < 1 || ciclo > 10) {
      Left(RegistroFailure.Validacion("El ciclo académico debe estar entre 1 y 10."))
    } else {
      Right(())
    }
  }

  private def validarMalla(nombre: String, ruta: String): Either[RegistroFailure, Unit] = {
    if (nombre == null || nombre.trim.isEmpty || ruta == null || ruta.trim.isEmpty) {
      Left(RegistroFailure.Validacion("El archivo PDF de la malla académica es obligatorio."))
    } else {
      Right(())
    }
  }

  private def validarTelefonoTutor(tel: String): Either[RegistroFailure, Unit] = {
    if (tel == null || tel.length != 10 || !tel.forall(_.isDigit)) {
      Left(RegistroFailure.Validacion("El teléfono del tutor empresarial debe tener exactamente 10 dígitos numéricos."))
    } else {
      Right(())
    }
  }

  private def validarEmpresaAsociada(empresaId: String): Either[RegistroFailure, Unit] = {
    if (empresaId == null || empresaId.trim.isEmpty) {
      Left(RegistroFailure.Validacion("Debe asociar al tutor a una empresa registrada."))
    } else {
      Right(())
    }
  }

  private def validarCamposObligatorios(campos: Map[String, String]): Either[RegistroFailure, Unit] = {
    val vacios = campos.filter { case (_, v) => v == null || v.trim.isEmpty }
    if (vacios.nonEmpty) {
      Left(RegistroFailure.Validacion(s"Los siguientes campos son obligatorios: ${vacios.keys.mkString(", ")}."))
    } else {
      Right(())
    }
  }

  private def ejecutarTransaccion[A](op: scalikejdbc.DBSession => A): Either[RegistroFailure, A] = {
    try {
      Right(DB.localTx { session => op(session) })
    } catch {
      case NonFatal(e) =>
        Left(RegistroFailure.ErrorPersistencia(s"Error al guardar en base de datos: ${e.getMessage}"))
    }
  }
}
