package com.ucuenca.gestion.models.db

import scalikejdbc._
import com.ucuenca.gestion.models.dto.AsignacionDocenteDTO

object PracticaRegistroRepository {

  /**
   * Obtiene todas las prácticas inicializadas de la bolsa de empleo que no tienen tutor académico.
   */
  def listarBolsaPendientesTutor()(implicit session: DBSession = AutoSession): List[AsignacionDocenteDTO] = {
    sql"""
      SELECT 
        pr.id_practica,
        pr.ci_estudiante_ref,
        u_est.nombres_completos AS nombre_estudiante,
        c.nombre_carrera AS carrera,
        u_emp.nombres_completos AS nombre_empresa,
        oc.titulo_oferta
      FROM practica_registro pr
      INNER JOIN estudiante_perfil ep ON pr.ci_estudiante_ref = ep.identificacion
      INNER JOIN usuario u_est ON ep.identificacion = u_est.identificacion
      INNER JOIN carrera c ON ep.id_carrera_ref = c.id_carrera
      INNER JOIN usuario u_emp ON pr.ruc_empresa_ref = u_emp.identificacion
      INNER JOIN postulacion_bolsa pb ON pr.ci_estudiante_ref = pb.ci_estudiante_ref AND pb.estado_postulacion = 'APROBADA'::estado_postulacion
      INNER JOIN oferta_convocatoria oc ON pb.id_oferta_ref = oc.id_oferta
      WHERE pr.origen_rama = 'BOLSA_EMPLEO'::origen_rama
        AND pr.estado_cronograma = 'TUTOR_ACADEMICO_PENDIENTE'::estado_cronograma
      ORDER BY pr.id_practica ASC
    """.map { rs =>
      AsignacionDocenteDTO(
        idPractica = rs.int("id_practica"),
        ciEstudiante = rs.string("ci_estudiante_ref"),
        nombreEstudiante = rs.string("nombre_estudiante"),
        carrera = rs.string("carrera"),
        nombreEmpresa = rs.string("nombre_empresa"),
        tituloOferta = rs.string("titulo_oferta"),
        idTutorAcademicoRef = None
      )
    }.list.apply()
  }

  /**
   * Asigna un tutor académico a la práctica y actualiza su estado a F1_PENDIENTE.
   */
  def asignarTutorAcademico(idPractica: Int, tutorCI: String)(implicit session: DBSession = AutoSession): Unit = {
    sql"""
      UPDATE practica_registro
      SET id_tutor_academico_ref = ${tutorCI},
          estado_cronograma = 'F1_PENDIENTE'::estado_cronograma
      WHERE id_practica = ${idPractica}
    """.update.apply()
  }

  /**
   * Registra horas trabajadas incrementando horas_acumuladas de forma atómica.
   * La condición de no exceder horas_totales_requeridas se exige en la propia
   * cláusula WHERE para evitar condiciones de carrera entre la validación y la
   * escritura. Retorna false si no se afectó ninguna fila (práctica inexistente
   * o el incremento superaría el límite).
   */
  def registrarHorasTrabajadas(idPractica: Int, horas: Int)(implicit session: DBSession = AutoSession): Boolean = {
    val affected = sql"""
      UPDATE practica_registro
      SET horas_acumuladas = horas_acumuladas + ${horas}
      WHERE id_practica = ${idPractica}
        AND horas_acumuladas + ${horas} <= horas_totales_requeridas
    """.update.apply()
    affected > 0
  }

  /**
   * Utilidad SOLO DE DESARROLLO: suma horas a horas_acumuladas para acelerar demos
   * locales del cronograma sin esperar la acumulación real. Nunca invocar desde un
   * flujo de negocio validado por el docente; el resultado se acota a
   * horas_totales_requeridas para no generar datos inconsistentes.
   */
  def devSumarHorasAcumuladas(idPractica: Int, horas: Int)(implicit session: DBSession = AutoSession): Unit = {
    sql"""
      UPDATE practica_registro
      SET horas_acumuladas = LEAST(horas_acumuladas + ${horas}, horas_totales_requeridas)
      WHERE id_practica = ${idPractica}
    """.update.apply()
  }
}
