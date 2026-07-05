package com.ucuenca.gestion.models.logic

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll
import scalikejdbc._
import com.ucuenca.gestion.utils.DatabaseConnection
import com.ucuenca.gestion.models.db.SolicitudConvenioRepository
import com.ucuenca.gestion.models.enums.{EstadoConvenio, RolUsuario}

class ConvenioLogicSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  // RUC válido de sociedad privada (provincia 01, tercer dígito 9, dígito verificador Módulo 11 correcto)
  val testRuc = "0191234502001"
  var testSolicitudId: Int = _

  override def beforeAll(): Unit = {
    DatabaseConnection.initialize()

    DB.localTx { implicit session =>
      cleanDb()

      // Seed Company User and Profile for approval propagation test
      sql"""
        INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta)
        VALUES (${testRuc}, 'Empresa Spec S.A.', 'empresa@spec.com', 'EMPRESA'::rol_usuario, 'ACTIVA'::estado_cuenta)
      """.update.apply()

      sql"""
        INSERT INTO empresa_perfil (identificacion, direccion_matriz, mision, vision, estado_convenio)
        VALUES (${testRuc}, 'Av. Spec 123', 'Mision Spec', 'Vision Spec', 'PENDIENTE'::estado_convenio)
      """.update.apply()
    }
  }

  override def afterAll(): Unit = {
    DB.localTx { implicit session =>
      cleanDb()
    }
  }

  private def cleanDb()(implicit session: DBSession): Unit = {
    // 1. Collect PDF IDs associated with the company's covenant requests
    val pdfIds = sql"SELECT convenio_documento_pdf FROM solicitud_convenio WHERE ruc_empresa = ${testRuc}"
      .map(_.int("convenio_documento_pdf")).list.apply()

    // 2. Delete covenant request
    sql"DELETE FROM solicitud_convenio WHERE ruc_empresa = ${testRuc}".update.apply()

    // 3. Delete profiles and users
    sql"DELETE FROM empresa_perfil WHERE identificacion = ${testRuc}".update.apply()
    sql"DELETE FROM usuario_sistema WHERE username = ${testRuc}".update.apply()
    sql"DELETE FROM usuario WHERE identificacion = ${testRuc}".update.apply()

    // 4. Delete specific PDFs that are now unreferenced
    if (pdfIds.nonEmpty) {
      sql"DELETE FROM archivo_pdf WHERE id_archivo_pdf IN (${pdfIds})".update.apply()
    }

    // 5. Clean up any remaining orphaned test PDFs
    sql"""
      DELETE FROM archivo_pdf
      WHERE nombre_original = 'convenio_test.pdf'
        AND id_archivo_pdf NOT IN (SELECT convenio_documento_pdf FROM solicitud_convenio)
    """.update.apply()
  }

  "ConvenioLogic" should "rechazar el registro si faltan campos obligatorios" in {
    val res = ConvenioLogic.registrarSolicitud(
      rucEmpresa = testRuc,
      razonSocial = "",
      representanteLegal = "Juan Perez",
      direccionMatriz = "Calle 1",
      mision = "Mision",
      vision = "Vision",
      pdfNombre = "convenio_test.pdf",
      pdfRuta = "uploads/convenio_test.pdf"
    )
    res shouldBe Left(ConvenioFailure.Validacion("El nombre o razón social es obligatorio."))
  }

  it should "rechazar el registro si el RUC tiene formato inválido" in {
    val res = ConvenioLogic.registrarSolicitud(
      rucEmpresa = "12345",
      razonSocial = "Empresa Test",
      representanteLegal = "Juan Perez",
      direccionMatriz = "Calle 1",
      mision = "Mision",
      vision = "Vision",
      pdfNombre = "convenio_test.pdf",
      pdfRuta = "uploads/convenio_test.pdf"
    )
    res shouldBe Left(ConvenioFailure.Validacion("El RUC de la empresa no es válido. Verifique los 13 dígitos y el dígito verificador."))
  }

  it should "registrar exitosamente un convenio PENDIENTE si es la primera vez" in {
    val res = ConvenioLogic.registrarSolicitud(
      rucEmpresa = testRuc,
      razonSocial = "Empresa Spec S.A.",
      representanteLegal = "Representante Spec",
      direccionMatriz = "Av. Spec 123",
      mision = "Mision Spec",
      vision = "Vision Spec",
      pdfNombre = "convenio_test.pdf",
      pdfRuta = "uploads/convenio_test.pdf"
    )
    res.isRight shouldBe true
    testSolicitudId = res.toOption.get

    val solOpt = SolicitudConvenioRepository.buscarPorId(testSolicitudId)
    solOpt.isDefined shouldBe true
    solOpt.get.estadoConvenio shouldBe EstadoConvenio.PENDIENTE
  }

  it should "rechazar un segundo registro si la solicitud actual está PENDIENTE" in {
    val res = ConvenioLogic.registrarSolicitud(
      rucEmpresa = testRuc,
      razonSocial = "Empresa Spec S.A.",
      representanteLegal = "Representante Spec",
      direccionMatriz = "Av. Spec 123",
      mision = "Mision Spec",
      vision = "Vision Spec",
      pdfNombre = "convenio_test.pdf",
      pdfRuta = "uploads/convenio_test.pdf"
    )
    res shouldBe Left(ConvenioFailure.Validacion("Ya existe un trámite de convenio pendiente para esta empresa."))
  }

  it should "listar solicitudes de convenio pendientes en la bandeja" in {
    val res = ConvenioLogic.listarPendientes()
    res.isRight shouldBe true
    res.toOption.get.exists(_.idSolicitudConvenio == testSolicitudId) shouldBe true
  }

  it should "rechazar la denegación si las causas del rechazo están vacías" in {
    val res = ConvenioLogic.rechazarConvenio(testSolicitudId, testRuc, "  ")
    res shouldBe Left(ConvenioFailure.Validacion("Las causas de rechazo administrativo son obligatorias si rechaza la solicitud."))
  }

  it should "permitir rechazar un convenio pendiente y propagar estado de rechazo al perfil" in {
    val res = ConvenioLogic.rechazarConvenio(testSolicitudId, testRuc, "Documento no firmado notaralmente")
    res.isRight shouldBe true

    val sol = SolicitudConvenioRepository.buscarPorId(testSolicitudId).get
    sol.estadoConvenio shouldBe EstadoConvenio.RECHAZADO
    sol.causasRechazoSecretaria shouldBe Some("Documento no firmado notaralmente")

    val empEstado = DB.readOnly { implicit s =>
      sql"SELECT estado_convenio FROM empresa_perfil WHERE identificacion = ${testRuc}"
        .map(_.string("estado_convenio")).single.apply().get
    }
    empEstado shouldBe "RECHAZADO"
  }

  it should "permitir registrar (sobreescribir/reiniciar) si la solicitud anterior fue rechazada" in {
    val res = ConvenioLogic.registrarSolicitud(
      rucEmpresa = testRuc,
      razonSocial = "Empresa Spec S.A. Corregida",
      representanteLegal = "Representante Spec Corregido",
      direccionMatriz = "Av. Spec 123 Corregido",
      mision = "Mision Spec",
      vision = "Vision Spec",
      pdfNombre = "convenio_test.pdf",
      pdfRuta = "uploads/convenio_test.pdf"
    )
    res.isRight shouldBe true
    val nuevoId = res.toOption.get
    nuevoId shouldBe testSolicitudId // Debe sobreescribir la misma fila

    val sol = SolicitudConvenioRepository.buscarPorId(testSolicitudId).get
    sol.estadoConvenio shouldBe EstadoConvenio.PENDIENTE
    sol.causasRechazoSecretaria shouldBe None
    sol.razonSocial shouldBe "Empresa Spec S.A. Corregida"
  }

  it should "permitir aprobar un convenio pendiente y propagar estado de formalización al perfil" in {
    val res = ConvenioLogic.aprobarConvenio(testSolicitudId, testRuc)
    res.isRight shouldBe true

    val sol = SolicitudConvenioRepository.buscarPorId(testSolicitudId).get
    sol.estadoConvenio shouldBe EstadoConvenio.FORMALIZADO

    val empEstado = DB.readOnly { implicit s =>
      sql"SELECT estado_convenio FROM empresa_perfil WHERE identificacion = ${testRuc}"
        .map(_.string("estado_convenio")).single.apply().get
    }
    empEstado shouldBe "FORMALIZADO"
  }

  it should "bloquear nuevos registros si la solicitud está en estado FORMALIZADO" in {
    val res = ConvenioLogic.registrarSolicitud(
      rucEmpresa = testRuc,
      razonSocial = "Empresa Spec S.A.",
      representanteLegal = "Representante Spec",
      direccionMatriz = "Av. Spec 123",
      mision = "Mision Spec",
      vision = "Vision Spec",
      pdfNombre = "convenio_test.pdf",
      pdfRuta = "uploads/convenio_test.pdf"
    )
    res shouldBe Left(ConvenioFailure.Validacion("La empresa ya cuenta con un convenio formalizado vigente."))
  }
}
