package com.ucuenca.gestion.models.db

import scalikejdbc._
import com.ucuenca.gestion.models.entities._
import com.ucuenca.gestion.models.enums._

object UsuarioRepository {

  /**
   * Registra un estudiante con su perfil y documentos asociados dentro de una transacción.
   */
  def crearEstudiante(
    usuario: Usuario,
    credenciales: UsuarioSistema,
    mallaPdf: ArchivoPDF,
    cvPdf: Option[ArchivoPDF],
    cicloActual: Int,
    idCarreraRef: Int,
    estadoMatricula: EstadoMatricula,
    estadoPractica: EstadoEstudiantePractica
  )(implicit session: DBSession = AutoSession): Unit = {
    insertUsuario(usuario)
    insertCredenciales(credenciales)
    val mallaId = insertArchivoPdf(mallaPdf)
    val cvIdOpt = cvPdf.map(pdf => insertArchivoPdf(pdf))

    sql"""
      INSERT INTO estudiante_perfil (
        identificacion,
        ciclo_actual,
        id_carrera_ref,
        estado_matricula,
        estado_estudiante_practica,
        malla_academica_pdf,
        curriculum_vitae_pdf
      ) VALUES (
        ${usuario.identificacion},
        ${cicloActual},
        ${idCarreraRef},
        ${estadoMatricula.toString}::estado_matricula,
        ${estadoPractica.toString}::estado_estudiante_practica,
        ${mallaId},
        ${cvIdOpt}
      )
    """.update.apply()
  }

  /**
   * Registra una empresa con su perfil corporativo dentro de una transacción.
   */
  def crearEmpresa(
    usuario: Usuario,
    credenciales: UsuarioSistema,
    direccionMatriz: String,
    misionVision: String,
    estadoConvenio: EstadoConvenio
  )(implicit session: DBSession = AutoSession): Unit = {
    insertUsuario(usuario)
    insertCredenciales(credenciales)

    sql"""
      INSERT INTO empresa_perfil (
        identificacion,
        direccion_matriz,
        mision_vision,
        estado_convenio
      ) VALUES (
        ${usuario.identificacion},
        ${direccionMatriz},
        ${misionVision},
        ${estadoConvenio.toString}::estado_convenio
      )
    """.update.apply()
  }

  /**
   * Registra un tutor empresarial dentro de una transacción.
   */
  def crearTutorEmpresarial(
    usuario: Usuario,
    credenciales: UsuarioSistema,
    empresaIdRef: String,
    telefonoContacto: String
  )(implicit session: DBSession = AutoSession): Unit = {
    insertUsuario(usuario)
    insertCredenciales(credenciales)

    sql"""
      INSERT INTO tutor_empresarial_perfil (
        identificacion,
        empresa_id_ref,
        telefono_contacto
      ) VALUES (
        ${usuario.identificacion},
        ${empresaIdRef},
        ${telefonoContacto}
      )
    """.update.apply()
  }

  /**
   * Registra un usuario general (Admin, Coordinador, Secretaría, Tutor Académico)
   * que no posee tabla de perfil especializada, dentro de una transacción.
   */
  def crearUsuarioGeneral(
    usuario: Usuario,
    credenciales: UsuarioSistema
  )(implicit session: DBSession = AutoSession): Unit = {
    insertUsuario(usuario)
    insertCredenciales(credenciales)
  }

  private def insertUsuario(u: Usuario)(implicit session: DBSession): Unit = {
    sql"""
      INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta)
      VALUES (
        ${u.identificacion},
        ${u.nombresCompletos},
        ${u.correoElectronico},
        ${u.rol.toString}::rol_usuario,
        ${u.estadoCuenta.toString}::estado_cuenta
      )
    """.update.apply()
  }

  private def insertCredenciales(c: UsuarioSistema)(implicit session: DBSession): Unit = {
    sql"""
      INSERT INTO usuario_sistema (username, password_hash, identificacion_usuario_ref)
      VALUES (
        ${c.username},
        ${c.passwordHash},
        ${c.identificacionUsuarioRef}
      )
    """.update.apply()
  }

  private def insertArchivoPdf(pdf: ArchivoPDF)(implicit session: DBSession): Int = {
    sql"""
      INSERT INTO archivo_pdf (tipo_archivo, nombre_original, ruta_segura_servidor)
      VALUES (
        ${pdf.tipoArchivo.toString}::tipo_archivo_pdf,
        ${pdf.nombreOriginal},
        ${pdf.rutaSeguraServidor}
      )
    """.updateAndReturnGeneratedKey.apply().toInt
  }
}
