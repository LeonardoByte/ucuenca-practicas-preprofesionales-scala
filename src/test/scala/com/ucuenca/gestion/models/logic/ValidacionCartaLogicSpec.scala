package com.ucuenca.gestion.models.logic

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll
import scalikejdbc._
import com.ucuenca.gestion.utils.DatabaseConnection
import com.ucuenca.gestion.models.enums.{RolUsuario, EstadoConvenio}
import com.ucuenca.gestion.models.db.ValidacionCartaRepository

class ValidacionCartaLogicSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  // Test Entities
  val companyPendingRuc = "0909090909004"
  val companyFormalizedRuc = "0909090909005"
  
  val studentPendingCi = "2121212121"
  val studentFormalizedCi = "2222222222"
  
  val tutorEmpCi = "2323232323"

  var pdfMallaId: Int = _
  var practicePendingId: Int = _
  var practiceFormalizedId: Int = _

  override def beforeAll(): Unit = {
    DatabaseConnection.initialize()

    DB.localTx { implicit session =>
      // Clean up previous test data in reverse dependency order
      sql"DELETE FROM validacion_carta_compromiso WHERE ci_estudiante IN (${studentPendingCi}, ${studentFormalizedCi})".update.apply()
      sql"DELETE FROM practica_registro WHERE ci_estudiante_ref IN (${studentPendingCi}, ${studentFormalizedCi})".update.apply()
      sql"DELETE FROM estudiante_perfil WHERE identificacion IN (${studentPendingCi}, ${studentFormalizedCi})".update.apply()
      sql"DELETE FROM tutor_empresarial_perfil WHERE identificacion = ${tutorEmpCi}".update.apply()
      sql"DELETE FROM empresa_perfil WHERE identificacion IN (${companyPendingRuc}, ${companyFormalizedRuc})".update.apply()
      sql"DELETE FROM usuario WHERE identificacion IN (${studentPendingCi}, ${studentFormalizedCi}, ${tutorEmpCi}, ${companyPendingRuc}, ${companyFormalizedRuc})".update.apply()
      sql"DELETE FROM archivo_pdf WHERE nombre_original = 'malla_test_carta.pdf'".update.apply()

      // 1. Create PDF
      pdfMallaId = sql"INSERT INTO archivo_pdf (tipo_archivo, nombre_original, ruta_segura_servidor) VALUES ('T9_MALLA_ACADEMICA'::tipo_archivo_pdf, 'malla_test_carta.pdf', 'uploads/m_c.pdf')".updateAndReturnGeneratedKey.apply().toInt

      // 2. Create Companies
      // A. Company with PENDING covenant (No formalized)
      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${companyPendingRuc}, 'Empresa Convenio Pendiente', 'emp_pend@test.com', 'EMPRESA'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()
      sql"INSERT INTO empresa_perfil (identificacion, direccion_matriz, mision, vision, estado_convenio) VALUES (${companyPendingRuc}, 'Matriz P', 'Mision', 'Vision', 'PENDIENTE'::estado_convenio)".update.apply()

      // B. Company with FORMALIZED covenant
      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${companyFormalizedRuc}, 'Empresa Convenio Formalizado', 'emp_form@test.com', 'EMPRESA'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()
      sql"INSERT INTO empresa_perfil (identificacion, direccion_matriz, mision, vision, estado_convenio) VALUES (${companyFormalizedRuc}, 'Matriz F', 'Mision', 'Vision', 'FORMALIZADO'::estado_convenio)".update.apply()

      // 3. Create Tutor Empresarial
      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${tutorEmpCi}, 'Tutor Emp Carta', 'tut_emp_ca@test.com', 'TUTOR_EMPRESARIAL'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()
      sql"INSERT INTO tutor_empresarial_perfil (identificacion, empresa_id_ref, telefono_contacto) VALUES (${tutorEmpCi}, ${companyPendingRuc}, '0912345678')".update.apply()

      // 4. Create Students
      // Student 1 (assigned to company with pending covenant)
      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${studentPendingCi}, 'Estudiante Convenio Pendiente', 'est_pend@test.com', 'ESTUDIANTE'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()
      sql"INSERT INTO estudiante_perfil (identificacion, ciclo_actual, id_carrera_ref, estado_matricula, estado_estudiante_practica, malla_academica_pdf) VALUES (${studentPendingCi}, 7, 1, 'REGULAR'::estado_matricula, 'CON_PRACTICA_ACTIVA'::estado_estudiante_practica, ${pdfMallaId})".update.apply()

      // Student 2 (assigned to company with formalized covenant)
      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${studentFormalizedCi}, 'Estudiante Convenio Formalizado', 'est_form@test.com', 'ESTUDIANTE'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()
      sql"INSERT INTO estudiante_perfil (identificacion, ciclo_actual, id_carrera_ref, estado_matricula, estado_estudiante_practica, malla_academica_pdf) VALUES (${studentFormalizedCi}, 7, 1, 'REGULAR'::estado_matricula, 'CON_PRACTICA_ACTIVA'::estado_estudiante_practica, ${pdfMallaId})".update.apply()

      // 5. Create practice records (both in initiation phase: TUTOR_ACADEMICO_PENDIENTE)
      practicePendingId = sql"""
        INSERT INTO practica_registro (ci_estudiante_ref, ruc_empresa_ref, id_tutor_academico_ref, id_tutor_empresarial_ref, origen_rama, estado_cronograma, horas_totales_requeridas)
        VALUES (${studentPendingCi}, ${companyPendingRuc}, NULL, ${tutorEmpCi}, 'BOLSA_EMPLEO'::origen_rama, 'TUTOR_ACADEMICO_PENDIENTE'::estado_cronograma, 160)
      """.updateAndReturnGeneratedKey.apply().toInt

      practiceFormalizedId = sql"""
        INSERT INTO practica_registro (ci_estudiante_ref, ruc_empresa_ref, id_tutor_academico_ref, id_tutor_empresarial_ref, origen_rama, estado_cronograma, horas_totales_requeridas)
        VALUES (${studentFormalizedCi}, ${companyFormalizedRuc}, NULL, ${tutorEmpCi}, 'BOLSA_EMPLEO'::origen_rama, 'TUTOR_ACADEMICO_PENDIENTE'::estado_cronograma, 160)
      """.updateAndReturnGeneratedKey.apply().toInt
    }
  }

  override def afterAll(): Unit = {
    DB.localTx { implicit session =>
      sql"DELETE FROM validacion_carta_compromiso WHERE ci_estudiante IN (${studentPendingCi}, ${studentFormalizedCi})".update.apply()
      sql"DELETE FROM practica_registro WHERE ci_estudiante_ref IN (${studentPendingCi}, ${studentFormalizedCi})".update.apply()
      sql"DELETE FROM estudiante_perfil WHERE identificacion IN (${studentPendingCi}, ${studentFormalizedCi})".update.apply()
      sql"DELETE FROM tutor_empresarial_perfil WHERE identificacion = ${tutorEmpCi}".update.apply()
      sql"DELETE FROM empresa_perfil WHERE identificacion IN (${companyPendingRuc}, ${companyFormalizedRuc})".update.apply()
      sql"DELETE FROM usuario WHERE identificacion IN (${studentPendingCi}, ${studentFormalizedCi}, ${tutorEmpCi}, ${companyPendingRuc}, ${companyFormalizedRuc})".update.apply()
      sql"DELETE FROM archivo_pdf WHERE id_archivo_pdf = ${pdfMallaId}".update.apply()
    }
  }

  "ValidacionCartaLogic" should "listar solo estudiantes en fase de inicio con empresas sin convenio formalizado" in {
    val result = ValidacionCartaLogic.listarPendientes(None)
    result.isRight shouldBe true
    val lista = result.toOption.get
    
    // El estudiante con convenio PENDIENTE debe aparecer
    lista.exists(_.ciEstudiante == studentPendingCi) shouldBe true
    
    // El estudiante con convenio FORMALIZADO no debe aparecer (está exento de carta compromiso)
    lista.exists(_.ciEstudiante == studentFormalizedCi) shouldBe false
  }

  it should "permitir la busqueda incremental por nombres o cedula" in {
    // Filtro por cédula exacta
    val filterCi = ValidacionCartaLogic.listarPendientes(Some(studentPendingCi)).toOption.get
    filterCi.exists(_.ciEstudiante == studentPendingCi) shouldBe true
    filterCi.size shouldBe 1

    // Filtro por nombre parcial
    val filterName = ValidacionCartaLogic.listarPendientes(Some("Convenio Pendiente")).toOption.get
    filterName.exists(_.ciEstudiante == studentPendingCi) shouldBe true

    // Filtro por cadena inexistente
    val filterEmpty = ValidacionCartaLogic.listarPendientes(Some("xyzabc")).toOption.get
    filterEmpty.isEmpty shouldBe true
  }

  it should "rechazar certificaciones si entregadoTresCopias es FALSE en la capa de logica" in {
    val result = ValidacionCartaLogic.certificarCarta(studentPendingCi, entregadoTresCopias = false)
    result shouldBe Left(ValidacionCartaFailure.Validacion("Se debe certificar la entrega física de las 3 copias de forma obligatoria."))
  }

  it should "forzar la restriccion CHECK en la base de datos si se intenta insertar FALSE directamente" in {
    // Intentar insertar false directamente por repositorio saltando la validacion de logica
    val exception = intercept[Exception] {
      DB.localTx { implicit session =>
        ValidacionCartaRepository.registrarValidacion(studentPendingCi, entregadoTresCopias = false)
      }
    }
    // Debe fallar debido al check constraint de postgresql: check (entregado_tres_copias = true)
    exception.getMessage should include("entregado_tres_copias")
  }

  it should "certificar exitosamente la entrega de las 3 copias y registrar la carta de compromiso" in {
    val result = ValidacionCartaLogic.certificarCarta(studentPendingCi, entregadoTresCopias = true)
    result shouldBe Right(())

    // Verificar en DB que se haya creado el registro
    val dbState = DB.readOnly { implicit session =>
      sql"SELECT entregado_tres_copias, carta_compromiso_pdf FROM validacion_carta_compromiso WHERE ci_estudiante = ${studentPendingCi}"
        .map(rs => (rs.boolean("entregado_tres_copias"), rs.int("carta_compromiso_pdf")))
        .single.apply()
    }
    dbState.isDefined shouldBe true
    dbState.get._1 shouldBe true
    
    val pdfId = dbState.get._2
    // Verificar que exista el PDF digitalizado
    val pdfState = DB.readOnly { implicit session =>
      sql"SELECT tipo_archivo, nombre_original FROM archivo_pdf WHERE id_archivo_pdf = ${pdfId}"
        .map(rs => (rs.string("tipo_archivo"), rs.string("nombre_original")))
        .single.apply()
    }
    pdfState shouldBe Some(("T6_CARTA_COMPROMISO", s"carta_compromiso_$studentPendingCi.pdf"))

    // Verificar que una vez certificado, el estudiante se retire de la lista de pendientes
    val listaPostVal = ValidacionCartaLogic.listarPendientes(None).toOption.get
    listaPostVal.exists(_.ciEstudiante == studentPendingCi) shouldBe false
  }
}
