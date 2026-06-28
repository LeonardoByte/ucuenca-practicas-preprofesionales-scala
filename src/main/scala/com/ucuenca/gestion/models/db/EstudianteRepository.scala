package com.ucuenca.gestion.models.db

import scalikejdbc._
import com.ucuenca.gestion.models.entities._
import com.ucuenca.gestion.models.enums._

object EstudianteRepository {

  /**
   * Obtiene la información completa del perfil del estudiante, uniendo usuario,
   * estudiante_perfil y los PDFs asociados.
   */
  def buscarPerfil(identificacion: String)(implicit session: DBSession = AutoSession): Option[(Usuario, EstudiantePerfil, Option[ArchivoPDF], Option[ArchivoPDF])] = {
    sql"""
      SELECT 
        u.identificacion, u.nombres_completos, u.correo_electronico, u.rol, u.estado_cuenta,
        ep.ciclo_actual, ep.id_carrera_ref, ep.estado_matricula, ep.estado_estudiante_practica,
        ep.malla_academica_pdf, ep.curriculum_vitae_pdf,
        m.id_archivo_pdf AS malla_id, m.tipo_archivo AS malla_tipo, m.nombre_original AS malla_nombre, m.ruta_segura_servidor AS malla_ruta, m.fecha_carga AS malla_fecha,
        c.id_archivo_pdf AS cv_id, c.tipo_archivo AS cv_tipo, c.nombre_original AS cv_nombre, c.ruta_segura_servidor AS cv_ruta, c.fecha_carga AS cv_fecha
      FROM estudiante_perfil ep
      INNER JOIN usuario u ON ep.identificacion = u.identificacion
      INNER JOIN archivo_pdf m ON ep.malla_academica_pdf = m.id_archivo_pdf
      LEFT JOIN archivo_pdf c ON ep.curriculum_vitae_pdf = c.id_archivo_pdf
      WHERE ep.identificacion = ${identificacion}
    """.map { rs =>
      val usuario = Usuario(
        identificacion = rs.string("identificacion"),
        nombresCompletos = rs.string("nombres_completos"),
        correoElectronico = rs.string("correo_electronico"),
        rol = RolUsuario.valueOf(rs.string("rol")),
        estadoCuenta = EstadoCuenta.valueOf(rs.string("estado_cuenta"))
      )

      val perfil = EstudiantePerfil(
        identificacion = rs.string("identificacion"),
        cicloActual = rs.int("ciclo_actual"),
        idCarreraRef = rs.int("id_carrera_ref"),
        estadoMatricula = EstadoMatricula.valueOf(rs.string("estado_matricula")),
        estadoEstudiantePractica = EstadoEstudiantePractica.valueOf(rs.string("estado_estudiante_practica")),
        mallaAcademicaPDF = rs.int("malla_academica_pdf"),
        curriculumVitaePDF = rs.string("curriculum_vitae_pdf") match {
          case null => None
          case _ => Some(rs.int("curriculum_vitae_pdf"))
        }
      )

      val mallaPdf = ArchivoPDF(
        idArchivoPDF = rs.int("malla_id"),
        tipoArchivo = TipoArchivoPDF.valueOf(rs.string("malla_tipo")),
        nombreOriginal = rs.string("malla_nombre"),
        rutaSeguraServidor = rs.string("malla_ruta"),
        fechaCarga = rs.localDateTime("malla_fecha")
      )

      val cvPdfOpt = rs.string("cv_id") match {
        case null => None
        case _ => Some(ArchivoPDF(
          idArchivoPDF = rs.int("cv_id"),
          tipoArchivo = TipoArchivoPDF.valueOf(rs.string("cv_tipo")),
          nombreOriginal = rs.string("cv_nombre"),
          rutaSeguraServidor = rs.string("cv_ruta"),
          fechaCarga = rs.localDateTime("cv_fecha")
        ))
      }

      (usuario, perfil, Some(mallaPdf), cvPdfOpt)
    }.single.apply()
  }

  /**
   * Registra un nuevo documento en la tabla archivo_pdf y actualiza la correspondiente
   * clave foránea en estudiante_perfil dentro de una sola transacción.
   */
  def actualizarDocumento(
    identificacion: String,
    pdf: ArchivoPDF,
    esMalla: Boolean
  )(implicit session: DBSession = AutoSession): Int = {
    // 1. Insertar metadatos en archivo_pdf
    val pdfId = sql"""
      INSERT INTO archivo_pdf (tipo_archivo, nombre_original, ruta_segura_servidor)
      VALUES (
        ${pdf.tipoArchivo.toString}::tipo_archivo_pdf,
        ${pdf.nombreOriginal},
        ${pdf.rutaSeguraServidor}
      )
    """.updateAndReturnGeneratedKey.apply().toInt

    // 2. Actualizar clave foránea en estudiante_perfil
    if (esMalla) {
      sql"""
        UPDATE estudiante_perfil
        SET malla_academica_pdf = ${pdfId}
        WHERE identificacion = ${identificacion}
      """.update.apply()
    } else {
      sql"""
        UPDATE estudiante_perfil
        SET curriculum_vitae_pdf = ${pdfId}
        WHERE identificacion = ${identificacion}
      """.update.apply()
    }

    pdfId
  }
}
