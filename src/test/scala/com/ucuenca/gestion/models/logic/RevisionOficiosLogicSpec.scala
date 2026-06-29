package com.ucuenca.gestion.models.logic

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll
import scalikejdbc._
import com.ucuenca.gestion.utils.DatabaseConnection
import com.ucuenca.gestion.models.dto.RegistrarSolicitudDTO
import com.ucuenca.gestion.models.db.SolicitudEmpresaPropiaRepository
import com.ucuenca.gestion.models.enums.{EstadoConvenio, EstadoEstudiantePractica}

class RevisionOficiosLogicSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  val testEstudianteCI = "0808080808"
  val testTutorCI = "0909090909"
  val testRucEmpresa = "0102030405001"
  val testSupervisorCI = "0777777777"
  var testSolicitudId: Int = _

  private def baseDto: RegistrarSolicitudDTO = RegistrarSolicitudDTO(
    ciEstudianteRef           = testEstudianteCI,
    nombreEntidadExterna      = "Empresa JIT Test S.A.",
    rucEmpresaPropia          = testRucEmpresa,
    contactoEmpresaPropia     = "jit@empresa.com",
    horasEmpresaPropia        = 240,
    direccionEmpresaPropia    = "Calle Falsa 123",
    misionEmpresaPropia       = "Mision jit",
    visionEmpresaPropia       = "Vision jit",
    contenidoOficioTranscrito = "Transcripcion de prueba...",
    ciSupervisorExterno       = testSupervisorCI,
    nombresSupervisorExterno  = "Carlos Supervisor",
    emailSupervisorExterno    = "supervisor@empresa.com",
    telefonoSupervisorExterno = "0999999999",
    pdfNombreOriginal         = "solicitud.pdf",
    pdfRutaSegura             = "uploads/solicitud.pdf"
  )

  override def beforeAll(): Unit = {
    DatabaseConnection.initialize()

    DB.localTx { implicit session =>
      cleanDb()

      // Seed Student malla PDF
      val mallaId = sql"""
        INSERT INTO archivo_pdf (tipo_archivo, nombre_original, ruta_segura_servidor)
        VALUES ('T9_MALLA_ACADEMICA'::tipo_archivo_pdf, 'malla_test.pdf', 'uploads/malla_test.pdf')
      """.updateAndReturnGeneratedKey.apply().toInt

      // Seed Student
      sql"""
        INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta)
        VALUES (${testEstudianteCI}, 'Estudiante Spec', 'student@spec.com', 'ESTUDIANTE'::rol_usuario, 'ACTIVA'::estado_cuenta)
      """.update.apply()

      sql"""
        INSERT INTO estudiante_perfil (identificacion, ciclo_actual, id_carrera_ref, estado_matricula, estado_estudiante_practica, malla_academica_pdf)
        VALUES (${testEstudianteCI}, 7, 1, 'REGULAR'::estado_matricula, 'SIN_PRACTICA'::estado_estudiante_practica, ${mallaId})
      """.update.apply()

      // Seed Academic Tutor
      sql"""
        INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta)
        VALUES (${testTutorCI}, 'Tutor Spec', 'tutor@spec.com', 'TUTOR_ACADEMICO'::rol_usuario, 'ACTIVA'::estado_cuenta)
      """.update.apply()

      // Register request
      testSolicitudId = SolicitudEmpresaPropiaRepository.registrar(baseDto)
    }
  }

  override def afterAll(): Unit = {
    DB.localTx { implicit session =>
      cleanDb()
    }
  }

  private def cleanDb()(implicit session: DBSession): Unit = {
    // 1. Clean practice
    sql"DELETE FROM practica_registro WHERE ci_estudiante_ref = ${testEstudianteCI}".update.apply()
    
    // 2. Clean request
    sql"DELETE FROM solicitud_empresa_propia WHERE ci_estudiante_ref = ${testEstudianteCI}".update.apply()

    // 3. Clean supervisor
    sql"DELETE FROM tutor_empresarial_perfil WHERE identificacion = ${testSupervisorCI}".update.apply()
    sql"DELETE FROM usuario_sistema WHERE username = ${testSupervisorCI}".update.apply()
    sql"DELETE FROM usuario WHERE identificacion = ${testSupervisorCI}".update.apply()

    // 4. Clean company
    sql"DELETE FROM empresa_perfil WHERE identificacion = ${testRucEmpresa}".update.apply()
    sql"DELETE FROM usuario_sistema WHERE username = ${testRucEmpresa}".update.apply()
    sql"DELETE FROM usuario WHERE identificacion = ${testRucEmpresa}".update.apply()

    // 5. Clean academic tutor
    sql"DELETE FROM usuario WHERE identificacion = ${testTutorCI}".update.apply()

    // 6. Clean student
    sql"DELETE FROM estudiante_perfil WHERE identificacion = ${testEstudianteCI}".update.apply()
    sql"DELETE FROM usuario WHERE identificacion = ${testEstudianteCI}".update.apply()

    // 7. Clean pdf files
    sql"DELETE FROM archivo_pdf WHERE nombre_original IN ('malla_test.pdf', 'solicitud.pdf') OR ruta_segura_servidor LIKE '%oficio_vuelta%'".update.apply()
  }

  "RevisionOficiosLogic" should "listar las solicitudes autogestionadas pendientes" in {
    val result = RevisionOficiosLogic.listarPendientes()
    result.isRight shouldBe true
    val lista = result.toOption.get
    lista.exists(_.idSolicitudPropia == testSolicitudId) shouldBe true
    lista.find(_.idSolicitudPropia == testSolicitudId).get.estadoTramite shouldBe "PENDIENTE"
  }

  it should "permitir calcular el siguiente codigo secuencial" in {
    val result = RevisionOficiosLogic.obtenerSiguienteCodigo()
    result.isRight shouldBe true
    result.toOption.get should startWith("UCUENCA-VINC-2026-")
  }

  it should "rechazar el rechazo si la justificación está vacía" in {
    val result = RevisionOficiosLogic.rechazar(testSolicitudId, "  ")
    result shouldBe Left(RevisionOficiosFailure.Validacion("La justificación de denegación es obligatoria si rechaza el trámite."))
  }

  it should "rechazar la aprobación si el código del oficio de vuelta no sigue el formato" in {
    val result = RevisionOficiosLogic.aprobar(testSolicitudId, testTutorCI, "INVALID-CODE")
    result.isLeft shouldBe true
    result.left.toOption.get.asInstanceOf[RevisionOficiosFailure.Validacion].mensaje should include("El código de oficio debe seguir estrictamente el formato")
  }

  it should "rechazar la aprobación si no se provee un tutor académico" in {
    val result = RevisionOficiosLogic.aprobar(testSolicitudId, "", "UCUENCA-VINC-2026-0001")
    result.isLeft shouldBe true
    result.left.toOption.get.asInstanceOf[RevisionOficiosFailure.Validacion].mensaje should include("Debe asignar un tutor académico")
  }

  it should "permitir rechazar una solicitud pendiente y no crear perfiles JIT" in {
    val result = RevisionOficiosLogic.rechazar(testSolicitudId, "Causa de rechazo del oficio")
    result.isRight shouldBe true

    val solOpt = SolicitudEmpresaPropiaRepository.buscarPorId(testSolicitudId)
    solOpt.isDefined shouldBe true
    solOpt.get.estadoTramite shouldBe EstadoConvenio.RECHAZADO
    solOpt.get.justificacionDenegacion shouldBe Some("Causa de rechazo del oficio")

    // Verificar que NO se crearon perfiles JIT
    val empCount = DB.readOnly { implicit s =>
      sql"SELECT COUNT(*) FROM empresa_perfil WHERE identificacion = ${testRucEmpresa}".map(_.int(1)).single.apply().getOrElse(0)
    }
    empCount shouldBe 0
  }

  it should "permitir aprobar una solicitud transicionando de vuelta a PENDIENTE y ejecutando flujo atómico JIT" in {
    // Volver a poner a PENDIENTE para probar aprobación
    DB.localTx { implicit session =>
      sql"UPDATE solicitud_empresa_propia SET estado_tramite = 'PENDIENTE'::estado_convenio, justificacion_denegacion = NULL WHERE id_solicitud_propia = ${testSolicitudId}".update.apply()
    }

    val result = RevisionOficiosLogic.aprobar(testSolicitudId, testTutorCI, "UCUENCA-VINC-2026-0001")
    result.isRight shouldBe true

    val solOpt = SolicitudEmpresaPropiaRepository.buscarPorId(testSolicitudId)
    solOpt.isDefined shouldBe true
    solOpt.get.estadoTramite shouldBe EstadoConvenio.FORMALIZADO
    solOpt.get.codigoOficioVuelta shouldBe Some("UCUENCA-VINC-2026-0001")
    solOpt.get.idTutorAcadAsignado shouldBe Some(testTutorCI)
    solOpt.get.oficioPresentacionVueltaPDF.isDefined shouldBe true

    // Verificar creación JIT de Empresa
    val companyOpt = DB.readOnly { implicit s =>
      sql"SELECT * FROM empresa_perfil WHERE identificacion = ${testRucEmpresa}".map(rs => rs.string("identificacion")).single.apply()
    }
    companyOpt shouldBe Some(testRucEmpresa)

    // Verificar creación JIT de Tutor Empresarial
    val supOpt = DB.readOnly { implicit s =>
      sql"SELECT * FROM tutor_empresarial_perfil WHERE identificacion = ${testSupervisorCI}".map(rs => rs.string("identificacion")).single.apply()
    }
    supOpt shouldBe Some(testSupervisorCI)

    // Verificar actualización del estado del estudiante a CON_PRACTICA_ACTIVA
    val estOpt = DB.readOnly { implicit s =>
      sql"SELECT estado_estudiante_practica FROM estudiante_perfil WHERE identificacion = ${testEstudianteCI}"
        .map(rs => rs.string("estado_estudiante_practica")).single.apply()
    }
    estOpt shouldBe Some("CON_PRACTICA_ACTIVA")

    // Verificar creación de practica_registro en estado F1_PENDIENTE
    val prOpt = DB.readOnly { implicit s =>
      sql"SELECT id_practica, estado_cronograma, origen_rama FROM practica_registro WHERE ci_estudiante_ref = ${testEstudianteCI}"
        .map(rs => (rs.string("estado_cronograma"), rs.string("origen_rama"))).single.apply()
    }
    prOpt shouldBe Some(("F1_PENDIENTE", "EMPRESA_PROPIA"))
  }

  it should "rechazar operaciones sobre solicitudes que no existen" in {
    RevisionOficiosLogic.aprobar(-99, testTutorCI, "UCUENCA-VINC-2026-0001") shouldBe Left(RevisionOficiosFailure.Validacion("La solicitud especificada no existe."))
    RevisionOficiosLogic.rechazar(-99, "Justificacion") shouldBe Left(RevisionOficiosFailure.Validacion("La solicitud especificada no existe."))
  }
}
