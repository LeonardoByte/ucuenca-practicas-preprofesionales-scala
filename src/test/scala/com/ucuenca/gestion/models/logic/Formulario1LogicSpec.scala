package com.ucuenca.gestion.models.logic

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll
import scalikejdbc._
import com.ucuenca.gestion.utils.DatabaseConnection
import com.ucuenca.gestion.models.enums.{RolUsuario, EstadoCronograma, EstadoConvenio}
import com.ucuenca.gestion.models.db.{Formulario1DB, CronogramaRepository}

class Formulario1LogicSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  // Test entities
  val companyPendingRuc = "0909090909006"
  val companyFormalizedRuc = "0909090909007"
  
  val student1Ci = "2424242424"
  val student2Ci = "2525252525"
  
  val tutorEmpCi = "2626262626"
  val tutorAcadCi = "2727272727"

  var pdfMallaId: Int = _
  var practicePendingId: Int = _
  var practiceFormalizedId: Int = _

  override def beforeAll(): Unit = {
    DatabaseConnection.initialize()

    DB.localTx { implicit session =>
      // Clean up previous test data
      sql"DELETE FROM expediente_formulario1 WHERE id_practica_ref IN (SELECT id_practica FROM practica_registro WHERE ci_estudiante_ref IN (${student1Ci}, ${student2Ci}))".update.apply()
      sql"DELETE FROM validacion_carta_compromiso WHERE ci_estudiante IN (${student1Ci}, ${student2Ci})".update.apply()
      sql"DELETE FROM actividad_cronograma WHERE id_practica_ref IN (SELECT id_practica FROM practica_registro WHERE ci_estudiante_ref IN (${student1Ci}, ${student2Ci}))".update.apply()
      sql"DELETE FROM practica_registro WHERE ci_estudiante_ref IN (${student1Ci}, ${student2Ci})".update.apply()
      sql"DELETE FROM estudiante_perfil WHERE identificacion IN (${student1Ci}, ${student2Ci})".update.apply()
      sql"DELETE FROM tutor_empresarial_perfil WHERE identificacion = ${tutorEmpCi}".update.apply()
      sql"DELETE FROM empresa_perfil WHERE identificacion IN (${companyPendingRuc}, ${companyFormalizedRuc})".update.apply()
      sql"DELETE FROM usuario WHERE identificacion IN (${student1Ci}, ${student2Ci}, ${tutorEmpCi}, ${tutorAcadCi}, ${companyPendingRuc}, ${companyFormalizedRuc})".update.apply()
      sql"DELETE FROM archivo_pdf WHERE nombre_original = 'malla_test_f1.pdf'".update.apply()

      // 1. Create PDF
      pdfMallaId = sql"INSERT INTO archivo_pdf (tipo_archivo, nombre_original, ruta_segura_servidor) VALUES ('T9_MALLA_ACADEMICA'::tipo_archivo_pdf, 'malla_test_f1.pdf', 'uploads/m_f.pdf')".updateAndReturnGeneratedKey.apply().toInt

      // 2. Create Companies
      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${companyPendingRuc}, 'Empresa P F1', 'emp_p_f1@test.com', 'EMPRESA'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()
      sql"INSERT INTO empresa_perfil (identificacion, direccion_matriz, mision, vision, estado_convenio) VALUES (${companyPendingRuc}, 'Matriz P', 'M', 'V', 'PENDIENTE'::estado_convenio)".update.apply()

      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${companyFormalizedRuc}, 'Empresa F F1', 'emp_f_f1@test.com', 'EMPRESA'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()
      sql"INSERT INTO empresa_perfil (identificacion, direccion_matriz, mision, vision, estado_convenio) VALUES (${companyFormalizedRuc}, 'Matriz F', 'M', 'V', 'FORMALIZADO'::estado_convenio)".update.apply()

      // 3. Create Tutor Empresarial
      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${tutorEmpCi}, 'Tutor Emp F1', 'tut_emp_f1@test.com', 'TUTOR_EMPRESARIAL'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()
      sql"INSERT INTO tutor_empresarial_perfil (identificacion, empresa_id_ref, telefono_contacto) VALUES (${tutorEmpCi}, ${companyPendingRuc}, '0912345678')".update.apply()

      // 4. Create Tutor Academico
      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${tutorAcadCi}, 'Tutor Acad F1', 'tut_acad_f1@test.com', 'TUTOR_ACADEMICO'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()

      // 5. Create Students
      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${student1Ci}, 'Estudiante 1 F1', 'est1_f1@test.com', 'ESTUDIANTE'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()
      sql"INSERT INTO estudiante_perfil (identificacion, ciclo_actual, id_carrera_ref, estado_matricula, estado_estudiante_practica, malla_academica_pdf) VALUES (${student1Ci}, 7, 1, 'REGULAR'::estado_matricula, 'CON_PRACTICA_ACTIVA'::estado_estudiante_practica, ${pdfMallaId})".update.apply()

      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${student2Ci}, 'Estudiante 2 F1', 'est2_f1@test.com', 'ESTUDIANTE'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()
      sql"INSERT INTO estudiante_perfil (identificacion, ciclo_actual, id_carrera_ref, estado_matricula, estado_estudiante_practica, malla_academica_pdf) VALUES (${student2Ci}, 7, 1, 'REGULAR'::estado_matricula, 'CON_PRACTICA_ACTIVA'::estado_estudiante_practica, ${pdfMallaId})".update.apply()

      // 6. Create practice records
      practicePendingId = sql"""
        INSERT INTO practica_registro (ci_estudiante_ref, ruc_empresa_ref, id_tutor_academico_ref, id_tutor_empresarial_ref, origen_rama, estado_cronograma, horas_totales_requeridas)
        VALUES (${student1Ci}, ${companyPendingRuc}, ${tutorAcadCi}, ${tutorEmpCi}, 'BOLSA_EMPLEO'::origen_rama, 'F1_PENDIENTE'::estado_cronograma, 160)
      """.updateAndReturnGeneratedKey.apply().toInt

      practiceFormalizedId = sql"""
        INSERT INTO practica_registro (ci_estudiante_ref, ruc_empresa_ref, id_tutor_academico_ref, id_tutor_empresarial_ref, origen_rama, estado_cronograma, horas_totales_requeridas)
        VALUES (${student2Ci}, ${companyFormalizedRuc}, ${tutorAcadCi}, ${tutorEmpCi}, 'BOLSA_EMPLEO'::origen_rama, 'F1_PENDIENTE'::estado_cronograma, 160)
      """.updateAndReturnGeneratedKey.apply().toInt
    }
  }

  override def afterAll(): Unit = {
    DB.localTx { implicit session =>
      sql"DELETE FROM expediente_formulario1 WHERE id_practica_ref IN (${practicePendingId}, ${practiceFormalizedId})".update.apply()
      sql"DELETE FROM validacion_carta_compromiso WHERE ci_estudiante IN (${student1Ci}, ${student2Ci})".update.apply()
      sql"DELETE FROM actividad_cronograma WHERE id_practica_ref IN (${practicePendingId}, ${practiceFormalizedId})".update.apply()
      sql"DELETE FROM practica_registro WHERE ci_estudiante_ref IN (${student1Ci}, ${student2Ci})".update.apply()
      sql"DELETE FROM estudiante_perfil WHERE identificacion IN (${student1Ci}, ${student2Ci})".update.apply()
      sql"DELETE FROM tutor_empresarial_perfil WHERE identificacion = ${tutorEmpCi}".update.apply()
      sql"DELETE FROM empresa_perfil WHERE identificacion IN (${companyPendingRuc}, ${companyFormalizedRuc})".update.apply()
      sql"DELETE FROM usuario WHERE identificacion IN (${student1Ci}, ${student2Ci}, ${tutorEmpCi}, ${tutorAcadCi}, ${companyPendingRuc}, ${companyFormalizedRuc})".update.apply()
      sql"DELETE FROM archivo_pdf WHERE id_archivo_pdf = ${pdfMallaId}".update.apply()
    }
  }

  "Formulario1Logic" should "bloquear presentacion si las tareas aprobadas no estan en el rango de 3 a 6" in {
    // Caso 1: 0 tareas aprobadas
    val r1 = Formulario1Logic.presentarFormulario1(practiceFormalizedId, Array[Byte](1, 2, 3), "f1_est2.pdf")
    r1 shouldBe Left(Formulario1Failure.Validacion("El plan debe contener entre 3 y 6 actividades aprobadas metodológicamente por el tutor académico. Actual: 0"))

    // Insertar 2 tareas aprobadas (todavía fuera de rango)
    DB.localTx { implicit session =>
      sql"INSERT INTO actividad_cronograma (id_practica_ref, numero_secuencial, descripcion_tarea, origen_creacion, estado_actividad) VALUES (${practiceFormalizedId}, 1, 'Tarea 1 aprobada', 'ESTUDIANTE'::origen_creacion_actividad, 'APROBADA'::estado_actividad)".update.apply()
      sql"INSERT INTO actividad_cronograma (id_practica_ref, numero_secuencial, descripcion_tarea, origen_creacion, estado_actividad) VALUES (${practiceFormalizedId}, 2, 'Tarea 2 aprobada', 'ESTUDIANTE'::origen_creacion_actividad, 'APROBADA'::estado_actividad)".update.apply()
    }

    val r2 = Formulario1Logic.presentarFormulario1(practiceFormalizedId, Array[Byte](1, 2, 3), "f1_est2.pdf")
    r2 shouldBe Left(Formulario1Failure.Validacion("El plan debe contener entre 3 y 6 actividades aprobadas metodológicamente por el tutor académico. Actual: 2"))
  }

  it should "bloquear presentacion si la empresa no tiene convenio y no hay carta de compromiso" in {
    // Insertar 3 tareas aprobadas para student1 (empresa sin convenio)
    DB.localTx { implicit session =>
      sql"INSERT INTO actividad_cronograma (id_practica_ref, numero_secuencial, descripcion_tarea, origen_creacion, estado_actividad) VALUES (${practicePendingId}, 1, 'Tarea 1', 'ESTUDIANTE'::origen_creacion_actividad, 'APROBADA'::estado_actividad)".update.apply()
      sql"INSERT INTO actividad_cronograma (id_practica_ref, numero_secuencial, descripcion_tarea, origen_creacion, estado_actividad) VALUES (${practicePendingId}, 2, 'Tarea 2', 'ESTUDIANTE'::origen_creacion_actividad, 'APROBADA'::estado_actividad)".update.apply()
      sql"INSERT INTO actividad_cronograma (id_practica_ref, numero_secuencial, descripcion_tarea, origen_creacion, estado_actividad) VALUES (${practicePendingId}, 3, 'Tarea 3', 'ESTUDIANTE'::origen_creacion_actividad, 'APROBADA'::estado_actividad)".update.apply()
    }

    // student1 no tiene carta de compromiso validada y su empresa no tiene convenio
    val r1 = Formulario1Logic.presentarFormulario1(practicePendingId, Array[Byte](1, 2, 3), "f1_est1.pdf")
    r1 shouldBe Left(Formulario1Failure.Validacion("Trámite congelado: la empresa no posee convenio formalizado y no se registra la entrega física de la Carta Compromiso en ventanilla."))
  }

  it should "permitir presentacion si la empresa tiene convenio (bypass legal) o si hay carta de compromiso" in {
    // 1. student2 (empresa con convenio formalizado): debe permitir directo
    // Añadir tercera tarea aprobada para student2
    DB.localTx { implicit session =>
      sql"INSERT INTO actividad_cronograma (id_practica_ref, numero_secuencial, descripcion_tarea, origen_creacion, estado_actividad) VALUES (${practiceFormalizedId}, 3, 'Tarea 3 aprobada', 'ESTUDIANTE'::origen_creacion_actividad, 'APROBADA'::estado_actividad)".update.apply()
    }

    val r1 = Formulario1Logic.presentarFormulario1(practiceFormalizedId, Array[Byte](1, 2, 3), "f1_est2.pdf")
    r1.isRight shouldBe true

    // 2. student1 (empresa sin convenio): registrar carta compromiso
    DB.localTx { implicit session =>
      val pdfId = sql"INSERT INTO archivo_pdf (tipo_archivo, nombre_original, ruta_segura_servidor) VALUES ('T6_CARTA_COMPROMISO'::tipo_archivo_pdf, 'carta.pdf', 'uploads/c.pdf')".updateAndReturnGeneratedKey.apply().toInt
      sql"INSERT INTO validacion_carta_compromiso (ci_estudiante, entregado_tres_copias, carta_compromiso_pdf) VALUES (${student1Ci}, TRUE, ${pdfId})".update.apply()
    }

    val r2 = Formulario1Logic.presentarFormulario1(practicePendingId, Array[Byte](1, 2, 3), "f1_est1.pdf")
    r2.isRight shouldBe true
  }

  it should "hacer cumplir el orden consecutivo y secuencial de firmas" in {
    // student2 ya presento F1 (idExpedienteF1 existe, firma_empresarial = false, firma_academica = false)

    // 1. Tutor Academico intenta firmar antes que el Tutor Empresarial -> debe fallar
    val rAcad = Formulario1Logic.tutorAcademicoFirmar(practiceFormalizedId, Array[Byte](4, 5, 6), "f1_acad.pdf")
    rAcad shouldBe Left(Formulario1Failure.Validacion("Flujo de firmas incorrecto: se requiere la firma del tutor empresarial antes de proceder con la firma del tutor académico."))

    // 2. Coordinador intenta autorizar antes que firmen los tutores -> debe fallar
    val rCoord = Formulario1Logic.coordinadorAprobar(practiceFormalizedId, Array[Byte](7, 8, 9), "f1_coord.pdf")
    rCoord shouldBe Left(Formulario1Failure.Validacion("Flujo de firmas incorrecto: el plan requiere la firma de ambos tutores antes de la resolución final del coordinador."))

    // 3. Tutor Empresarial firma -> debe tener éxito
    val rEmp = Formulario1Logic.tutorEmpresaFirmar(practiceFormalizedId, Array[Byte](4, 5, 6), "f1_emp.pdf")
    rEmp.isRight shouldBe true

    // 4. Tutor Academico intenta autorizar/coordinar antes que firme el tutor academico -> debe fallar
    val rCoord2 = Formulario1Logic.coordinadorAprobar(practiceFormalizedId, Array[Byte](7, 8, 9), "f1_coord.pdf")
    rCoord2 shouldBe Left(Formulario1Failure.Validacion("Flujo de firmas incorrecto: el plan requiere la firma de ambos tutores antes de la resolución final del coordinador."))

    // 5. Tutor Academico firma -> debe tener éxito
    val rAcad2 = Formulario1Logic.tutorAcademicoFirmar(practiceFormalizedId, Array[Byte](7, 8, 9), "f1_acad.pdf")
    rAcad2.isRight shouldBe true
  }

  it should "permitir al coordinador rechazar (reseteo logico sin eliminar fila)" in {
    // Coordinador rechaza student2
    val result = Formulario1Logic.coordinadorRechazar(practiceFormalizedId, "Corregir el cronograma técnico propuesto")
    result shouldBe Right(())

    // Verificar que la fila no se elimino y las firmas se resetearon
    val expOpt = Formulario1DB.buscarExpediente(practiceFormalizedId)
    expOpt.isDefined shouldBe true
    val exp = expOpt.get
    exp.firmaEmpresarialValida shouldBe false
    exp.firmaAcademicaValida shouldBe false
    exp.estadoDeCoordinador shouldBe false
    exp.justificacionRechazoInicio shouldBe Some("Corregir el cronograma técnico propuesto")

    // Volver a hacer firmas para el siguiente paso del test
    Formulario1Logic.tutorEmpresaFirmar(practiceFormalizedId, Array[Byte](1), "f1_e.pdf").isRight shouldBe true
    Formulario1Logic.tutorAcademicoFirmar(practiceFormalizedId, Array[Byte](2), "f1_a.pdf").isRight shouldBe true
  }

  it should "aprobar el expediente y transicionar la practica a EN_DESARROLLO al autorizar el coordinador" in {
    // student2 tiene ambas firmas listas de nuevo
    val result = Formulario1Logic.coordinadorAprobar(practiceFormalizedId, Array[Byte](10, 11), "f1_final.pdf")
    result shouldBe Right(())

    // Verificar en DB
    val exp = Formulario1DB.buscarExpediente(practiceFormalizedId).get
    exp.estadoDeCoordinador shouldBe true
    exp.fechaAutorizacion shouldBe Some(java.time.LocalDate.now())

    // Verificar estado_cronograma de la practica transiciono a EN_DESARROLLO
    val pr = DB.readOnly { implicit session =>
      sql"SELECT estado_cronograma FROM practica_registro WHERE id_practica = ${practiceFormalizedId}"
        .map(rs => rs.string("estado_cronograma")).single.apply()
    }
    pr shouldBe Some("EN_DESARROLLO")
  }
}
