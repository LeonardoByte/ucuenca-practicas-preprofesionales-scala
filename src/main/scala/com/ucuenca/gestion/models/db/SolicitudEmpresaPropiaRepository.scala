package com.ucuenca.gestion.models.db

import scalikejdbc._
import com.ucuenca.gestion.models.dto.{RegistrarSolicitudDTO, SolicitudPropiaRevisionDTO}
import com.ucuenca.gestion.models.entities.SolicitudEmpresaPropia
import com.ucuenca.gestion.utils.PasswordHasher

object SolicitudEmpresaPropiaRepository {

  def registrar(dto: RegistrarSolicitudDTO)(implicit session: DBSession = AutoSession): Int = {
    val pdfId = sql"""
      INSERT INTO archivo_pdf (tipo_archivo, nombre_original, ruta_segura_servidor)
      VALUES (
        'T4_OFICIO_SOLICITUD_PROP_INICIAL'::tipo_archivo_pdf,
        ${dto.pdfNombreOriginal},
        ${dto.pdfRutaSegura}
      )
    """.updateAndReturnGeneratedKey.apply().toInt

    sql"""
      INSERT INTO solicitud_empresa_propia (
        ci_estudiante_ref,
        nombre_entidad_externa,
        ruc_empresa_propia,
        contacto_empresa_propia,
        horas_empresa_propia,
        direccion_empresa_propia,
        mision_empresa_propia,
        vision_empresa_propia,
        contenido_oficio_transcrito,
        ci_supervisor_externo,
        nombres_supervisor_externo,
        email_supervisor_externo,
        telefono_supervisor_externo,
        oficio_solicitud_inicial_pdf,
        estado_tramite,
        fecha_registro
      ) VALUES (
        ${dto.ciEstudianteRef},
        ${dto.nombreEntidadExterna},
        ${dto.rucEmpresaPropia},
        ${dto.contactoEmpresaPropia},
        ${dto.horasEmpresaPropia},
        ${dto.direccionEmpresaPropia},
        ${dto.misionEmpresaPropia},
        ${dto.visionEmpresaPropia},
        ${dto.contenidoOficioTranscrito},
        ${dto.ciSupervisorExterno},
        ${dto.nombresSupervisorExterno},
        ${dto.emailSupervisorExterno},
        ${dto.telefonoSupervisorExterno},
        ${pdfId},
        'PENDIENTE'::estado_convenio,
        CURRENT_DATE
      )
    """.updateAndReturnGeneratedKey.apply().toInt
  }

  def listarPendientesConNombreEstudiante()(implicit session: DBSession = AutoSession): List[SolicitudPropiaRevisionDTO] = {
    sql"""
      SELECT s.*, u.nombres_completos as nombre_estudiante
      FROM solicitud_empresa_propia s
      INNER JOIN usuario u ON s.ci_estudiante_ref = u.identificacion
      WHERE s.estado_tramite = 'PENDIENTE'::estado_convenio
      ORDER BY s.fecha_registro DESC
    """.map { rs =>
      SolicitudPropiaRevisionDTO(
        idSolicitudPropia = rs.int("id_solicitud_propia"),
        ciEstudianteRef = rs.string("ci_estudiante_ref"),
        nombreEstudiante = rs.string("nombre_estudiante"),
        nombreEntidadExterna = rs.string("nombre_entidad_externa"),
        rucEmpresaPropia = rs.string("ruc_empresa_propia"),
        contactoEmpresaPropia = rs.string("contacto_empresa_propia"),
        horasEmpresaPropia = rs.int("horas_empresa_propia"),
        direccionEmpresaPropia = rs.string("direccion_empresa_propia"),
        misionEmpresaPropia = rs.string("mision_empresa_propia"),
        visionEmpresaPropia = rs.string("vision_empresa_propia"),
        contenidoOficioTranscrito = rs.string("contenido_oficio_transcrito"),
        ciSupervisorExterno = rs.string("ci_supervisor_externo"),
        nombresSupervisorExterno = rs.string("nombres_supervisor_externo"),
        emailSupervisorExterno = rs.string("email_supervisor_externo"),
        telefonoSupervisorExterno = rs.string("telefono_supervisor_externo"),
        oficioSolicitudInicialPDF = rs.int("oficio_solicitud_inicial_pdf"),
        estadoTramite = rs.string("estado_tramite"),
        fechaRegistro = rs.localDate("fecha_registro")
      )
    }.list.apply()
  }

  def buscarPorId(id: Int)(implicit session: DBSession = AutoSession): Option[SolicitudEmpresaPropia] = {
    sql"""
      SELECT * FROM solicitud_empresa_propia WHERE id_solicitud_propia = ${id}
    """.map { rs =>
      SolicitudEmpresaPropia(
        idSolicitudPropia = rs.int("id_solicitud_propia"),
        ciEstudianteRef = rs.string("ci_estudiante_ref"),
        nombreEntidadExterna = rs.string("nombre_entidad_externa"),
        rucEmpresaPropia = rs.string("ruc_empresa_propia"),
        contactoEmpresaPropia = rs.string("contacto_empresa_propia"),
        horasEmpresaPropia = rs.int("horas_empresa_propia"),
        direccionEmpresaPropia = rs.string("direccion_empresa_propia"),
        misionEmpresaPropia = rs.string("mision_empresa_propia"),
        visionEmpresaPropia = rs.string("vision_empresa_propia"),
        contenidoOficioTranscrito = rs.string("contenido_oficio_transcrito"),
        ciSupervisorExterno = rs.string("ci_supervisor_externo"),
        nombresSupervisorExterno = rs.string("nombres_supervisor_externo"),
        emailSupervisorExterno = rs.string("email_supervisor_externo"),
        telefonoSupervisorExterno = rs.string("telefono_supervisor_externo"),
        oficioSolicitudInicialPDF = rs.int("oficio_solicitud_inicial_pdf"),
        oficioPresentacionVueltaPDF = rs.intOpt("oficio_presentacion_vuelta_pdf"),
        codigoOficioVuelta = rs.stringOpt("codigo_oficio_vuelta"),
        idTutorAcadAsignado = rs.stringOpt("id_tutor_acad_asignado"),
        estadoTramite = com.ucuenca.gestion.models.enums.EstadoConvenio.valueOf(rs.string("estado_tramite")),
        justificacionDenegacion = rs.stringOpt("justificacion_denegacion"),
        fechaRegistro = rs.localDate("fecha_registro")
      )
    }.single.apply()
  }

  def obtenerSiguienteSecuencial()(implicit session: DBSession = AutoSession): Int = {
    sql"""
      SELECT COUNT(*) FROM solicitud_empresa_propia WHERE estado_tramite = 'FORMALIZADO'::estado_convenio
    """.map(_.int(1)).single.apply().getOrElse(0) + 1
  }

  def actualizarRechazo(id: Int, justificacion: String)(implicit session: DBSession = AutoSession): Unit = {
    sql"""
      UPDATE solicitud_empresa_propia
      SET estado_tramite = 'RECHAZADO'::estado_convenio,
          justificacion_denegacion = ${justificacion}
      WHERE id_solicitud_propia = ${id}
    """.update.apply()
  }

  def actualizarAprobacion(id: Int, tutorCI: String, codigoOficio: String, pdfId: Int)(implicit session: DBSession = AutoSession): Unit = {
    // 1. Resolver la solicitud
    sql"""
      UPDATE solicitud_empresa_propia
      SET estado_tramite = 'FORMALIZADO'::estado_convenio,
          codigo_oficio_vuelta = ${codigoOficio},
          id_tutor_acad_asignado = ${tutorCI},
          oficio_presentacion_vuelta_pdf = ${pdfId}
      WHERE id_solicitud_propia = ${id}
    """.update.apply()

    // 2. Obtener datos de la solicitud para el flujo atómico
    val sol = buscarPorId(id).getOrElse(throw new IllegalArgumentException(s"Solicitud $id no existe"))

    // 3. Crear empresa JIT si no existe
    val empresaExiste = sql"SELECT COUNT(*) FROM empresa_perfil WHERE identificacion = ${sol.rucEmpresaPropia}".map(_.int(1)).single.apply().getOrElse(0) > 0
    if (!empresaExiste) {
      val userExiste = sql"SELECT COUNT(*) FROM usuario WHERE identificacion = ${sol.rucEmpresaPropia}".map(_.int(1)).single.apply().getOrElse(0) > 0
      if (!userExiste) {
        sql"""
          INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta)
          VALUES (${sol.rucEmpresaPropia}, ${sol.nombreEntidadExterna}, ${sol.contactoEmpresaPropia}, 'EMPRESA'::rol_usuario, 'ACTIVA'::estado_cuenta)
        """.update.apply()

        val passHash = PasswordHasher.hash(sol.rucEmpresaPropia)
        sql"""
          INSERT INTO usuario_sistema (username, password_hash, identificacion_usuario_ref)
          VALUES (${sol.rucEmpresaPropia}, ${passHash}, ${sol.rucEmpresaPropia})
        """.update.apply()
      }

      sql"""
        INSERT INTO empresa_perfil (identificacion, direccion_matriz, mision, vision, estado_convenio)
        VALUES (${sol.rucEmpresaPropia}, ${sol.direccionEmpresaPropia}, ${sol.misionEmpresaPropia}, ${sol.visionEmpresaPropia}, 'FORMALIZADO'::estado_convenio)
      """.update.apply()
    }

    // 4. Crear tutor empresarial JIT si no existe
    val tutorEmpExiste = sql"SELECT COUNT(*) FROM tutor_empresarial_perfil WHERE identificacion = ${sol.ciSupervisorExterno}".map(_.int(1)).single.apply().getOrElse(0) > 0
    if (!tutorEmpExiste) {
      val userExiste = sql"SELECT COUNT(*) FROM usuario WHERE identificacion = ${sol.ciSupervisorExterno}".map(_.int(1)).single.apply().getOrElse(0) > 0
      if (!userExiste) {
        sql"""
          INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta)
          VALUES (${sol.ciSupervisorExterno}, ${sol.nombresSupervisorExterno}, ${sol.emailSupervisorExterno}, 'TUTOR_EMPRESARIAL'::rol_usuario, 'ACTIVA'::estado_cuenta)
        """.update.apply()

        val passHash = PasswordHasher.hash(sol.ciSupervisorExterno)
        sql"""
          INSERT INTO usuario_sistema (username, password_hash, identificacion_usuario_ref)
          VALUES (${sol.ciSupervisorExterno}, ${passHash}, ${sol.ciSupervisorExterno})
        """.update.apply()
      }

      sql"""
        INSERT INTO tutor_empresarial_perfil (identificacion, empresa_id_ref, telefono_contacto)
        VALUES (${sol.ciSupervisorExterno}, ${sol.rucEmpresaPropia}, ${sol.telefonoSupervisorExterno})
      """.update.apply()
    }

    // 5. Actualizar estado de estudiante
    sql"""
      UPDATE estudiante_perfil
      SET estado_estudiante_practica = 'CON_PRACTICA_ACTIVA'::estado_estudiante_practica
      WHERE identificacion = ${sol.ciEstudianteRef}
    """.update.apply()

    // 6. Registrar práctica central
    sql"""
      INSERT INTO practica_registro (
        ci_estudiante_ref,
        ruc_empresa_ref,
        id_tutor_academico_ref,
        id_tutor_empresarial_ref,
        origen_rama,
        estado_cronograma,
        horas_acumuladas,
        horas_totales_requeridas
      ) VALUES (
        ${sol.ciEstudianteRef},
        ${sol.rucEmpresaPropia},
        ${tutorCI},
        ${sol.ciSupervisorExterno},
        'EMPRESA_PROPIA'::origen_rama,
        'F1_PENDIENTE'::estado_cronograma,
        0,
        ${sol.horasEmpresaPropia}
      )
    """.update.apply()
  }
}
