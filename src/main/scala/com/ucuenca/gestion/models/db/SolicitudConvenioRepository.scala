package com.ucuenca.gestion.models.db

import scalikejdbc._
import com.ucuenca.gestion.models.entities.SolicitudConvenio
import com.ucuenca.gestion.models.enums.EstadoConvenio

object SolicitudConvenioRepository {

  private def mapSolicitud(rs: WrappedResultSet): SolicitudConvenio = {
    SolicitudConvenio(
      idSolicitudConvenio = rs.int("id_solicitud_convenio"),
      rucEmpresa = rs.string("ruc_empresa"),
      razonSocial = rs.string("razon_social"),
      representanteLegal = rs.string("representante_legal"),
      direccionMatriz = rs.string("direccion_matriz"),
      mision = rs.string("mision"),
      vision = rs.string("vision"),
      convenioDocumentoPDF = rs.int("convenio_documento_pdf"),
      estadoConvenio = EstadoConvenio.valueOf(rs.string("estado_convenio")),
      causasRechazoSecretaria = rs.stringOpt("causas_rechazo_secretaria"),
      fechaPresentacion = rs.localDate("fecha_presentacion")
    )
  }

  def buscarPorRuc(ruc: String)(implicit session: DBSession = AutoSession): Option[SolicitudConvenio] = {
    sql"""
      SELECT * FROM solicitud_convenio WHERE ruc_empresa = ${ruc}
    """.map(mapSolicitud).single.apply()
  }

  def buscarPorId(id: Int)(implicit session: DBSession = AutoSession): Option[SolicitudConvenio] = {
    sql"""
      SELECT * FROM solicitud_convenio WHERE id_solicitud_convenio = ${id}
    """.map(mapSolicitud).single.apply()
  }

  def listarPendientes()(implicit session: DBSession = AutoSession): List[SolicitudConvenio] = {
    sql"""
      SELECT * FROM solicitud_convenio
      WHERE estado_convenio = 'PENDIENTE'::estado_convenio
      ORDER BY fecha_presentacion DESC
    """.map(mapSolicitud).list.apply()
  }

  /**
   * Registra una nueva solicitud de convenio, insertando el PDF correspondiente.
   * Si ya existe un registro rechazado, lo actualiza (sobreescribe) para reiniciar el trámite.
   */
  def registrar(
    rucEmpresa: String,
    razonSocial: String,
    representanteLegal: String,
    direccionMatriz: String,
    mision: String,
    vision: String,
    pdfNombre: String,
    pdfRuta: String
  )(implicit session: DBSession = AutoSession): Int = {
    val pdfId = sql"""
      INSERT INTO archivo_pdf (tipo_archivo, nombre_original, ruta_segura_servidor)
      VALUES ('T8_SOLICITUD_CONVENIO_MARCO'::tipo_archivo_pdf, ${pdfNombre}, ${pdfRuta})
    """.updateAndReturnGeneratedKey.apply().toInt

    val existente = buscarPorRuc(rucEmpresa)
    existente match {
      case Some(sol) =>
        sql"""
          UPDATE solicitud_convenio
          SET razon_social = ${razonSocial},
              representante_legal = ${representanteLegal},
              direccion_matriz = ${direccionMatriz},
              mision = ${mision},
              vision = ${vision},
              convenio_documento_pdf = ${pdfId},
              estado_convenio = 'PENDIENTE'::estado_convenio,
              causas_rechazo_secretaria = NULL,
              fecha_presentacion = CURRENT_DATE
          WHERE id_solicitud_convenio = ${sol.idSolicitudConvenio}
        """.update.apply()
        sol.idSolicitudConvenio

      case None =>
        sql"""
          INSERT INTO solicitud_convenio (
            ruc_empresa, razon_social, representante_legal, direccion_matriz, mision, vision, convenio_documento_pdf, estado_convenio
          ) VALUES (
            ${rucEmpresa}, ${razonSocial}, ${representanteLegal}, ${direccionMatriz}, ${mision}, ${vision}, ${pdfId}, 'PENDIENTE'::estado_convenio
          )
        """.updateAndReturnGeneratedKey.apply().toInt
    }
  }

  def actualizarAprobacion(id: Int, ruc: String)(implicit session: DBSession = AutoSession): Unit = {
    sql"""
      UPDATE solicitud_convenio
      SET estado_convenio = 'FORMALIZADO'::estado_convenio,
          causas_rechazo_secretaria = NULL
      WHERE id_solicitud_convenio = ${id}
    """.update.apply()

    sql"""
      UPDATE empresa_perfil
      SET estado_convenio = 'FORMALIZADO'::estado_convenio
      WHERE identificacion = ${ruc}
    """.update.apply()
  }

  def actualizarRechazo(id: Int, ruc: String, causas: String)(implicit session: DBSession = AutoSession): Unit = {
    sql"""
      UPDATE solicitud_convenio
      SET estado_convenio = 'RECHAZADO'::estado_convenio,
          causas_rechazo_secretaria = ${causas}
      WHERE id_solicitud_convenio = ${id}
    """.update.apply()

    sql"""
      UPDATE empresa_perfil
      SET estado_convenio = 'RECHAZADO'::estado_convenio
      WHERE identificacion = ${ruc}
    """.update.apply()
  }
}
