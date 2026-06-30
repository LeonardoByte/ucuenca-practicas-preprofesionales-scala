package com.ucuenca.gestion.models.db

import scalikejdbc._
import com.ucuenca.gestion.models.entities.AuditoriaCierre

object AuditoriaRepository {

  /**
   * Obtiene la nómina de estudiantes listos para la auditoría de cierre.
   * Criterio: estado_cronograma = 'F2_F3_PENDIENTE', Formulario 2 es CONFORME, y Formulario 3 ya fue emitido.
   */
  def listarPendientesCierre()(implicit session: DBSession = AutoSession): List[(Int, String, String)] = {
    sql"""
      SELECT 
        pr.id_practica,
        u_est.nombres_completos AS nombre_estudiante,
        u_emp.nombres_completos AS nombre_empresa
      FROM practica_registro pr
      INNER JOIN estudiante_perfil ep ON pr.ci_estudiante_ref = ep.identificacion
      INNER JOIN usuario u_est ON ep.identificacion = u_est.identificacion
      INNER JOIN usuario u_emp ON pr.ruc_empresa_ref = u_emp.identificacion
      INNER JOIN formulario2_evaluacion f2 ON pr.id_practica = f2.id_practica_ref
      INNER JOIN formulario3_informe f3 ON pr.id_practica = f3.id_practica_ref
      WHERE pr.estado_cronograma = 'F2_F3_PENDIENTE'::estado_cronograma
        AND f2.estado_formulario2 = 'CONFORME'::estado_formulario2
        AND f2.fecha_registro = (
          SELECT MAX(fecha_registro) FROM formulario2_evaluacion
          WHERE id_practica_ref = pr.id_practica
        )
      ORDER BY u_est.nombres_completos ASC
    """.map(rs => (rs.int("id_practica"), rs.string("nombre_estudiante"), rs.string("nombre_empresa"))).list.apply()
  }

  /**
   * Verifica si el estudiante tiene cargada la validación física de cartas compromiso por secretaría.
   */
  def verificarValidacionSecretaria(ciEstudiante: String)(implicit session: DBSession = AutoSession): Boolean = {
    sql"""
      SELECT COUNT(1) FROM validacion_carta_compromiso
      WHERE ci_estudiante = ${ciEstudiante}
    """.map(rs => rs.int(1)).single.apply().getOrElse(0) > 0
  }

  /**
   * Verifica si la empresa del estudiante tiene un convenio marco FORMALIZADO.
   */
  def verificarConvenioEmpresa(rucEmpresa: String)(implicit session: DBSession = AutoSession): Boolean = {
    sql"""
      SELECT estado_convenio FROM empresa_perfil
      WHERE identificacion = ${rucEmpresa}
    """.map(rs => rs.string("estado_convenio")).single.apply().contains("FORMALIZADO")
  }

  /**
   * Obtiene los IDs de los PDFs de Formulario 1, 2 y 3 para la auditoría visual de expedientes.
   */
  def obtenerPDFsExpediente(idPractica: Int)(implicit session: DBSession = AutoSession): (Option[Int], Option[Int], Option[Int]) = {
    val f1Opt = sql"SELECT formulario1_pdf FROM expediente_formulario1 WHERE id_practica_ref = ${idPractica}"
      .map(rs => rs.intOpt("formulario1_pdf")).single.apply().flatten

    val f2Opt = sql"""
      SELECT formulario2_pdf FROM formulario2_evaluacion
      WHERE id_practica_ref = ${idPractica} AND estado_formulario2 = 'CONFORME'::estado_formulario2
      ORDER BY fecha_registro DESC LIMIT 1
    """.map(rs => rs.intOpt("formulario2_pdf")).single.apply().flatten

    val f3Opt = sql"SELECT formulario3_pdf FROM formulario3_informe WHERE id_practica_ref = ${idPractica}"
      .map(rs => rs.intOpt("formulario3_pdf")).single.apply().flatten

    (f1Opt, f2Opt, f3Opt)
  }

  /**
   * Inserta un nuevo registro en auditoria_cierre calculando de forma correlativa secuencial_version.
   */
  def insertarAuditoria(
    idPractica: Int, 
    estado: String, 
    observaciones: Option[String], 
    secretariaSincronizada: Boolean
  )(implicit session: DBSession = AutoSession): Int = {
    val nextVersion = sql"""
      SELECT COALESCE(MAX(secuencial_version), 0) + 1 
      FROM auditoria_cierre 
      WHERE id_practica_ref = ${idPractica}
    """.map(rs => rs.int(1)).single.apply().getOrElse(1)

    sql"""
      INSERT INTO auditoria_cierre (
        id_practica_ref, 
        secuencial_version, 
        estado_auditoria, 
        observaciones_expediente, 
        validacion_fisica_secretaria_sincronizada
      ) VALUES (
        ${idPractica}, 
        ${nextVersion}, 
        ${estado}, 
        ${observaciones}, 
        ${secretariaSincronizada}
      )
    """.updateAndReturnGeneratedKey.apply().toInt
  }

  /**
   * Registra la nota final cuantitativa del estudiante y actualiza el cronograma a CERRADA_VALIDA.
   */
  def aprobarCierrePractica(idPractica: Int, nota: BigDecimal)(implicit session: DBSession = AutoSession): Unit = {
    sql"""
      UPDATE practica_registro
      SET nota_final = ${nota},
          estado_cronograma = 'CERRADA_VALIDA'::estado_cronograma
      WHERE id_practica = ${idPractica}
    """.update.apply()
  }

  /**
   * Resetea el conforme del último Formulario 2 tras un rechazo para permitir la resubisión.
   * Esto garantiza inmutabilidad y reinicia el bucle de control de auditoría de cierres.
   */
  def resetearConformeF2(idPractica: Int)(implicit session: DBSession = AutoSession): Unit = {
    sql"""
      UPDATE formulario2_evaluacion
      SET estado_formulario2 = 'RECHAZADO'::estado_formulario2
      WHERE id_practica_ref = ${idPractica}
        AND estado_formulario2 = 'CONFORME'::estado_formulario2
    """.update.apply()
  }
}
