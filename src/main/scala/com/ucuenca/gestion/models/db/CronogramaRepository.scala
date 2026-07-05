package com.ucuenca.gestion.models.db

import scalikejdbc._
import com.ucuenca.gestion.models.entities.{PracticaRegistro, ActividadCronograma}
import com.ucuenca.gestion.models.enums.{OrigenRama, EstadoCronograma, OrigenCreacionActividad, EstadoActividad}
import com.ucuenca.gestion.models.dto.StudentTutoradoDTO

object CronogramaRepository {

  /**
   * Busca el registro de práctica de un estudiante por su CI.
   */
  def buscarPracticaPorEstudiante(ciEstudiante: String)(implicit session: DBSession = AutoSession): Option[PracticaRegistro] = {
    sql"""
      SELECT * FROM practica_registro
      WHERE ci_estudiante_ref = ${ciEstudiante}
    """.map { rs =>
      PracticaRegistro(
        idPractica = rs.int("id_practica"),
        ciEstudianteRef = rs.string("ci_estudiante_ref"),
        rucEmpresaRef = rs.string("ruc_empresa_ref"),
        idTutorAcademicoRef = rs.stringOpt("id_tutor_academico_ref"),
        idTutorEmpresarialRef = rs.string("id_tutor_empresarial_ref"),
        origenRama = OrigenRama.valueOf(rs.string("origen_rama")),
        estadoCronograma = EstadoCronograma.valueOf(rs.string("estado_cronograma")),
        horasAcumuladas = rs.int("horas_acumuladas"),
        horasTotalesRequeridas = rs.int("horas_totales_requeridas"),
        notaFinal = rs.bigDecimalOpt("nota_final").map(BigDecimal(_))
      )
    }.single.apply()
  }

  /**
   * Busca el registro de práctica por su ID.
   */
  def buscarPracticaPorId(idPractica: Int)(implicit session: DBSession = AutoSession): Option[PracticaRegistro] = {
    sql"""
      SELECT * FROM practica_registro
      WHERE id_practica = ${idPractica}
    """.map { rs =>
      PracticaRegistro(
        idPractica = rs.int("id_practica"),
        ciEstudianteRef = rs.string("ci_estudiante_ref"),
        rucEmpresaRef = rs.string("ruc_empresa_ref"),
        idTutorAcademicoRef = rs.stringOpt("id_tutor_academico_ref"),
        idTutorEmpresarialRef = rs.string("id_tutor_empresarial_ref"),
        origenRama = OrigenRama.valueOf(rs.string("origen_rama")),
        estadoCronograma = EstadoCronograma.valueOf(rs.string("estado_cronograma")),
        horasAcumuladas = rs.int("horas_acumuladas"),
        horasTotalesRequeridas = rs.int("horas_totales_requeridas"),
        notaFinal = rs.bigDecimalOpt("nota_final").map(BigDecimal(_))
      )
    }.single.apply()
  }

  /**
   * Obtiene la nómina de estudiantes (idPractica, nombreEstudiante) asignados a un tutor empresarial.
   */
  def listarEstudiantesAsignados(ciTutorEmp: String)(implicit session: DBSession = AutoSession): List[(Int, String)] = {
    sql"""
      SELECT pr.id_practica, u.nombres_completos
      FROM practica_registro pr
      INNER JOIN usuario u ON pr.ci_estudiante_ref = u.identificacion
      WHERE pr.id_tutor_empresarial_ref = ${ciTutorEmp}
      ORDER BY u.nombres_completos ASC
    """.map(rs => (rs.int("id_practica"), rs.string("nombres_completos"))).list.apply()
  }

  /**
   * Obtiene las actividades activas de una práctica (excluyendo descartadas).
   */
  def listarActividadesPorPractica(idPractica: Int)(implicit session: DBSession = AutoSession): List[ActividadCronograma] = {
    sql"""
      SELECT * FROM actividad_cronograma
      WHERE id_practica_ref = ${idPractica}
        AND estado_actividad NOT IN ('DESCARTAR_ESTUDIANTE'::estado_actividad, 'DESCARTAR_TUTOR'::estado_actividad)
      ORDER BY numero_secuencial ASC
    """.map { rs =>
      ActividadCronograma(
        idActividad = rs.int("id_actividad"),
        idPracticaRef = rs.int("id_practica_ref"),
        numeroSecuencial = rs.int("numero_secuencial"),
        descripcionTarea = rs.string("descripcion_tarea"),
        origenCreacion = OrigenCreacionActividad.valueOf(rs.string("origen_creacion")),
        estadoActividad = EstadoActividad.valueOf(rs.string("estado_actividad")),
        comentarioObservacion = rs.stringOpt("comentario_observacion"),
        fechaRegistro = rs.localDate("fecha_registro")
      )
    }.list.apply()
  }

  /**
   * Registra una nueva actividad en el cronograma, calculando el número secuencial de forma correlativa.
   */
  def registrarActividad(idPractica: Int, descripcion: String, origen: OrigenCreacionActividad)(implicit session: DBSession = AutoSession): Int = {
    sql"""
      INSERT INTO actividad_cronograma (
        id_practica_ref,
        numero_secuencial,
        descripcion_tarea,
        origen_creacion,
        estado_actividad,
        fecha_registro
      ) VALUES (
        ${idPractica},
        COALESCE((SELECT MAX(numero_secuencial) FROM actividad_cronograma WHERE id_practica_ref = ${idPractica}), 0) + 1,
        ${descripcion.trim},
        ${origen.name}::origen_creacion_actividad,
        'PENDIENTE'::estado_actividad,
        CURRENT_DATE
      )
    """.updateAndReturnGeneratedKey.apply().toInt
  }

  /**
   * Realiza el descarte lógico de una actividad (cambiando su estado a DESCARTAR_ESTUDIANTE o DESCARTAR_TUTOR).
   */
  def descartarActividad(idActividad: Int, nuevoEstado: EstadoActividad)(implicit session: DBSession = AutoSession): Unit = {
    sql"""
      UPDATE actividad_cronograma
      SET estado_actividad = ${nuevoEstado.name}::estado_actividad
      WHERE id_actividad = ${idActividad}
    """.update.apply()
  }

  /**
   * Cuenta la cantidad de tareas en estado APROBADA para una práctica.
   */
  def contarTareasAprobadas(idPractica: Int)(implicit session: DBSession = AutoSession): Int = {
    sql"""
      SELECT COUNT(1) FROM actividad_cronograma
      WHERE id_practica_ref = ${idPractica}
        AND estado_actividad = 'APROBADA'::estado_actividad
    """.map(rs => rs.int(1)).single.apply().getOrElse(0)
  }

  // --- NUEVOS MÉTODOS PARA MICRO-SLICE 4.3 ---

  /**
   * Obtiene la lista de estudiantes asignados a un tutor académico junto con su cantidad de actividades PENDIENTES.
   */
  def listarEstudiantesTutorados(tutorCI: String)(implicit session: DBSession = AutoSession): List[StudentTutoradoDTO] = {
    sql"""
      SELECT 
        pr.id_practica,
        u_est.nombres_completos AS nombre_estudiante,
        u_emp.nombres_completos AS nombre_empresa,
        ep.ciclo_actual,
        (SELECT COUNT(1) FROM actividad_cronograma ac WHERE ac.id_practica_ref = pr.id_practica AND ac.estado_actividad = 'PENDIENTE'::estado_actividad) AS pendientes_count
      FROM practica_registro pr
      INNER JOIN estudiante_perfil ep ON pr.ci_estudiante_ref = ep.identificacion
      INNER JOIN usuario u_est ON ep.identificacion = u_est.identificacion
      INNER JOIN usuario u_emp ON pr.ruc_empresa_ref = u_emp.identificacion
      WHERE pr.id_tutor_academico_ref = ${tutorCI}
      ORDER BY u_est.nombres_completos ASC
    """.map { rs =>
      StudentTutoradoDTO(
        idPractica = rs.int("id_practica"),
        nombreEstudiante = rs.string("nombre_estudiante"),
        nombreEmpresa = rs.string("nombre_empresa"),
        cicloActual = rs.int("ciclo_actual"),
        pendientesCount = rs.int("pendientes_count")
      )
    }.list.apply()
  }

  /**
   * Obtiene las actividades que tienen estado PENDIENTE para una práctica.
   */
  def listarActividadesPendientesPorPractica(idPractica: Int)(implicit session: DBSession = AutoSession): List[ActividadCronograma] = {
    sql"""
      SELECT * FROM actividad_cronograma
      WHERE id_practica_ref = ${idPractica}
        AND estado_actividad = 'PENDIENTE'::estado_actividad
      ORDER BY numero_secuencial ASC
    """.map { rs =>
      ActividadCronograma(
        idActividad = rs.int("id_actividad"),
        idPracticaRef = rs.int("id_practica_ref"),
        numeroSecuencial = rs.int("numero_secuencial"),
        descripcionTarea = rs.string("descripcion_tarea"),
        origenCreacion = OrigenCreacionActividad.valueOf(rs.string("origen_creacion")),
        estadoActividad = EstadoActividad.valueOf(rs.string("estado_actividad")),
        comentarioObservacion = rs.stringOpt("comentario_observacion"),
        fechaRegistro = rs.localDate("fecha_registro")
      )
    }.list.apply()
  }

  /**
   * Actualiza el estado de una actividad (aprobación o rechazo), garantizando la inmutabilidad
   * al exigir que el estado actual sea estrictamente PENDIENTE.
   */
  def evaluarActividad(idActividad: Int, nuevoEstado: EstadoActividad, comentario: Option[String])(implicit session: DBSession = AutoSession): Boolean = {
    val affected = sql"""
      UPDATE actividad_cronograma
      SET estado_actividad = ${nuevoEstado.name}::estado_actividad,
          comentario_observacion = ${comentario}
      WHERE id_actividad = ${idActividad}
        AND estado_actividad = 'PENDIENTE'::estado_actividad
    """.update.apply()
    
    affected > 0
  }
}
