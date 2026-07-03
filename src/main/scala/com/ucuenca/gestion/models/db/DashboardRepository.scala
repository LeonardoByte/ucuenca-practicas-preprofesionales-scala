package com.ucuenca.gestion.models.db

import scalikejdbc._

object DashboardRepository {

  /**
   * Metodo apra la obtencion del nombre del usuario
   */
  def getName(coordinadorCI: String)(implicit session: DBSession = AutoSession): String = {
    sql"SELECT nombres_completos FROM usuario WHERE identificacion = $coordinadorCI"
      .map(rs => rs.string(1))
      .single
      .apply()
      .getOrElse("N/A")
  }

  /**
   * Obtiene métricas en tiempo real para el rol Administrador.
   */
  def getAdminMetrics()(implicit session: DBSession = AutoSession): Map[String, Int] = {
    val dbCheck = try {
      sql"SELECT 1".map(rs => rs.int(1)).single.apply().getOrElse(0)
    } catch {
      case _: Exception => 0
    }
    
    val practicasActivas = sql"SELECT COUNT(1) FROM practica_registro WHERE estado_cronograma <> 'CERRADA_VALIDA'::estado_cronograma"
      .map(rs => rs.int(1)).single.apply().getOrElse(0)

    val estudiantes = sql"SELECT COUNT(1) FROM usuario WHERE rol = 'ESTUDIANTE'::rol_usuario"
      .map(rs => rs.int(1)).single.apply().getOrElse(0)

    val tutoresAcad = sql"SELECT COUNT(1) FROM usuario WHERE rol = 'TUTOR_ACADEMICO'::rol_usuario"
      .map(rs => rs.int(1)).single.apply().getOrElse(0)

    val empresas = sql"SELECT COUNT(1) FROM usuario WHERE rol = 'EMPRESA'::rol_usuario"
      .map(rs => rs.int(1)).single.apply().getOrElse(0)

    val tutoresEmp = sql"SELECT COUNT(1) FROM usuario WHERE rol = 'TUTOR_EMPRESARIAL'::rol_usuario"
      .map(rs => rs.int(1)).single.apply().getOrElse(0)

    val coordinadores = sql"SELECT COUNT(1) FROM usuario WHERE rol = 'COORDINADOR'::rol_usuario"
      .map(rs => rs.int(1)).single.apply().getOrElse(0)

    val secretarias = sql"SELECT COUNT(1) FROM usuario WHERE rol = 'SECRETARIA'::rol_usuario"
      .map(rs => rs.int(1)).single.apply().getOrElse(0)

    val admins = sql"SELECT COUNT(1) FROM usuario WHERE rol = 'ADMIN'::rol_usuario"
      .map(rs => rs.int(1)).single.apply().getOrElse(0)

    Map(
      "dbCheck" -> dbCheck,
      "practicasActivas" -> practicasActivas,
      "estudiantes" -> estudiantes,
      "tutoresAcad" -> tutoresAcad,
      "empresas" -> empresas,
      "tutoresEmp" -> tutoresEmp,
      "coordinadores" -> coordinadores,
      "secretarias" -> secretarias,
      "admins" -> admins
    )
  }

  /**
   * Obtiene métricas en tiempo real para el rol Coordinador.
   */
  def getCoordinatorMetrics()(implicit session: DBSession = AutoSession): Map[String, Int] = {
    val postulaciones = sql"SELECT COUNT(1) FROM postulacion_bolsa WHERE estado_postulacion = 'PENDIENTE'"
      .map(rs => rs.int(1)).single.apply().getOrElse(0)

    val ofertas = sql"SELECT COUNT(1) FROM oferta_convocatoria WHERE estado_oferta = 'PENDIENTE'::estado_oferta"
      .map(rs => rs.int(1)).single.apply().getOrElse(0)

    val oficios = sql"SELECT COUNT(1) FROM solicitud_empresa_propia WHERE estado_tramite = 'PENDIENTE'::estado_convenio"
      .map(rs => rs.int(1)).single.apply().getOrElse(0)

    val asignaciones = sql"SELECT COUNT(1) FROM practica_registro WHERE estado_cronograma = 'TUTOR_ACADEMICO_PENDIENTE'::estado_cronograma"
      .map(rs => rs.int(1)).single.apply().getOrElse(0)

    val auditorias = sql"""
      SELECT COUNT(1) 
      FROM practica_registro pr
      INNER JOIN formulario2_evaluacion f2 ON pr.id_practica = f2.id_practica_ref
      INNER JOIN formulario3_informe f3 ON pr.id_practica = f3.id_practica_ref
      WHERE pr.estado_cronograma = 'F2_F3_PENDIENTE'::estado_cronograma
        AND f2.estado_formulario2 = 'CONFORME'::estado_formulario2
        AND f2.fecha_registro = (
          SELECT MAX(fecha_registro) FROM formulario2_evaluacion
          WHERE id_practica_ref = pr.id_practica
        )
    """.map(rs => rs.int(1)).single.apply().getOrElse(0)

    Map(
      "postulaciones" -> postulaciones,
      "ofertas" -> ofertas,
      "oficios" -> oficios,
      "asignaciones" -> asignaciones,
      "auditorias" -> auditorias
    )
  }

  /**
   * Obtiene métricas en tiempo real para el rol Empresa.
   */
  def getCompanyMetrics(rucEmpresa: String)(implicit session: DBSession = AutoSession): Map[String, String] = {
    val cubiertas = sql"""
      SELECT COUNT(1) FROM practica_registro 
      WHERE ruc_empresa_ref = ${rucEmpresa}
    """.map(rs => rs.int(1)).single.apply().getOrElse(0)

    val totales = sql"""
      SELECT COALESCE(SUM(vacantes_solicitadas), 0) FROM oferta_convocatoria 
      WHERE ruc_empresa_ref = ${rucEmpresa} AND estado_oferta = 'APROBADA'::estado_oferta
    """.map(rs => rs.int(1)).single.apply().getOrElse(0)

    val convenio = sql"""
      SELECT estado_convenio FROM empresa_perfil 
      WHERE identificacion = ${rucEmpresa}
    """.map(rs => rs.string("estado_convenio")).single.apply().getOrElse("SIN CONVENIO")

    val tutores = sql"""
      SELECT COUNT(1) FROM tutor_empresarial_perfil 
      WHERE empresa_id_ref = ${rucEmpresa}
    """.map(rs => rs.int(1)).single.apply().getOrElse(0)

    Map(
      "vacantes" -> s"$cubiertas / $totales",
      "convenio" -> (if (convenio == "FORMALIZADO") "VIGENTE" else convenio),
      "tutores" -> tutores.toString
    )
  }

  /**
   * Obtiene métricas en tiempo real para el rol Secretaría.
   */
  def getSecretaryMetrics()(implicit session: DBSession = AutoSession): Map[String, Int] = {
    val cartas = sql"""
      SELECT COUNT(1) 
      FROM practica_registro pr
      INNER JOIN empresa_perfil emp ON pr.ruc_empresa_ref = emp.identificacion
      LEFT JOIN validacion_carta_compromiso vcc ON pr.ci_estudiante_ref = vcc.ci_estudiante
      WHERE pr.estado_cronograma IN ('TUTOR_ACADEMICO_PENDIENTE'::estado_cronograma, 'F1_PENDIENTE'::estado_cronograma)
        AND emp.estado_convenio != 'FORMALIZADO'::estado_convenio
        AND vcc.ci_estudiante IS NULL
    """.map(rs => rs.int(1)).single.apply().getOrElse(0)

    val convenios = sql"""
      SELECT COUNT(1) FROM solicitud_convenio 
      WHERE estado_convenio = 'PENDIENTE'::estado_convenio
    """.map(rs => rs.int(1)).single.apply().getOrElse(0)

    Map(
      "cartas" -> cartas,
      "convenios" -> convenios
    )
  }

  /**
   * Obtiene métricas en tiempo real para el rol Tutor Académico.
   */
  def getAcademicTutorMetrics(tutorCI: String)(implicit session: DBSession = AutoSession): Map[String, Int] = {
    val inicio = sql"""
      SELECT COUNT(1) FROM practica_registro 
      WHERE id_tutor_academico_ref = ${tutorCI} 
        AND estado_cronograma IN ('TUTOR_ACADEMICO_PENDIENTE'::estado_cronograma, 'F1_PENDIENTE'::estado_cronograma)
    """.map(rs => rs.int(1)).single.apply().getOrElse(0)

    val desarrollo = sql"""
      SELECT COUNT(1) FROM practica_registro 
      WHERE id_tutor_academico_ref = ${tutorCI} 
        AND estado_cronograma = 'EN_DESARROLLO'::estado_cronograma
    """.map(rs => rs.int(1)).single.apply().getOrElse(0)

    val evaluacion = sql"""
      SELECT COUNT(1) FROM practica_registro 
      WHERE id_tutor_academico_ref = ${tutorCI} 
        AND estado_cronograma = 'F2_F3_PENDIENTE'::estado_cronograma
    """.map(rs => rs.int(1)).single.apply().getOrElse(0)

    val tareas = sql"""
      SELECT COUNT(1) 
      FROM actividad_cronograma ac
      INNER JOIN practica_registro pr ON ac.id_practica_ref = pr.id_practica
      WHERE pr.id_tutor_academico_ref = ${tutorCI} 
        AND ac.estado_actividad = 'PENDIENTE'::estado_actividad
    """.map(rs => rs.int(1)).single.apply().getOrElse(0)

    Map(
      "inicio" -> inicio,
      "desarrollo" -> desarrollo,
      "evaluacion" -> evaluacion,
      "tareas" -> tareas
    )
  }

  /**
   * Obtiene métricas en tiempo real para el rol Tutor Empresarial.
   */
  def getCorporateTutorMetrics(tutorEmpCI: String)(implicit session: DBSession = AutoSession): Map[String, Int] = {
    val inicio = sql"""
      SELECT COUNT(1) FROM practica_registro 
      WHERE id_tutor_empresarial_ref = ${tutorEmpCI} 
        AND estado_cronograma IN ('TUTOR_ACADEMICO_PENDIENTE'::estado_cronograma, 'F1_PENDIENTE'::estado_cronograma)
    """.map(rs => rs.int(1)).single.apply().getOrElse(0)

    val desarrollo = sql"""
      SELECT COUNT(1) FROM practica_registro 
      WHERE id_tutor_empresarial_ref = ${tutorEmpCI} 
        AND estado_cronograma = 'EN_DESARROLLO'::estado_cronograma
    """.map(rs => rs.int(1)).single.apply().getOrElse(0)

    val evaluacion = sql"""
      SELECT COUNT(1) FROM practica_registro 
      WHERE id_tutor_empresarial_ref = ${tutorEmpCI} 
        AND estado_cronograma = 'F2_F3_PENDIENTE'::estado_cronograma
    """.map(rs => rs.int(1)).single.apply().getOrElse(0)

    val tareas = sql"""
      SELECT COUNT(1) 
      FROM actividad_cronograma ac
      INNER JOIN practica_registro pr ON ac.id_practica_ref = pr.id_practica
      WHERE pr.id_tutor_empresarial_ref = ${tutorEmpCI} 
        AND ac.estado_actividad = 'PENDIENTE'::estado_actividad
    """.map(rs => rs.int(1)).single.apply().getOrElse(0)

    Map(
      "inicio" -> inicio,
      "desarrollo" -> desarrollo,
      "evaluacion" -> evaluacion,
      "tareas" -> tareas
    )
  }
}
