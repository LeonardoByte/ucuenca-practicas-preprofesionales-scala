package com.ucuenca.gestion.models.logic

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll
import scalikejdbc._
import com.ucuenca.gestion.utils.DatabaseConnection
import com.ucuenca.gestion.models.dto.RegistrarSolicitudDTO

class RegistrarSolicitudLogicSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  val testEstudianteCI = "0404040404"

  private def baseDto: RegistrarSolicitudDTO = RegistrarSolicitudDTO(
    ciEstudianteRef           = testEstudianteCI,
    nombreEntidadExterna      = "GAD Municipal del Cantón Cuenca",
    rucEmpresaPropia          = "0160076090001",
    contactoEmpresaPropia     = "contacto@gad.gob.ec",
    horasEmpresaPropia        = 160,
    direccionEmpresaPropia    = "Av. Mariscal Sucre y Huayna Cápac, Cuenca",
    misionEmpresaPropia       = "Fomentar el desarrollo sostenible y equitativo del cantón Cuenca.",
    visionEmpresaPropia       = "Ser referente de gestión pública eficiente y transparente en Ecuador.",
    contenidoOficioTranscrito = "Por medio del presente oficio solicito la autorización...",
    ciSupervisorExterno       = "0102030400",
    nombresSupervisorExterno  = "Ing. Carlos Andrés Peña López",
    emailSupervisorExterno    = "supervisor@gad.gob.ec",
    telefonoSupervisorExterno = "0987654321",
    pdfNombreOriginal         = "oficio_solicitud.pdf",
    pdfRutaSegura             = "uploads/oficio_solicitud.pdf"
  )

  override def beforeAll(): Unit = {
    DatabaseConnection.initialize()

    DB.localTx { implicit session =>
      sql"DELETE FROM solicitud_empresa_propia WHERE ci_estudiante_ref = ${testEstudianteCI}".update.apply()
      sql"DELETE FROM estudiante_perfil WHERE identificacion = ${testEstudianteCI}".update.apply()
      sql"DELETE FROM usuario WHERE identificacion = ${testEstudianteCI}".update.apply()
      sql"DELETE FROM archivo_pdf WHERE nombre_original IN ('malla_test_ep.pdf', 'oficio_solicitud_test.pdf')".update.apply()

      val mallaId = sql"""
        INSERT INTO archivo_pdf (tipo_archivo, nombre_original, ruta_segura_servidor)
        VALUES ('T9_MALLA_ACADEMICA'::tipo_archivo_pdf, 'malla_test_ep.pdf', 'uploads/malla_test_ep.pdf')
      """.updateAndReturnGeneratedKey.apply().toInt

      sql"""
        INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta)
        VALUES (${testEstudianteCI}, 'Estudiante Empresa Propia', 'ep@test.com', 'ESTUDIANTE'::rol_usuario, 'ACTIVA'::estado_cuenta)
      """.update.apply()

      sql"""
        INSERT INTO estudiante_perfil (identificacion, ciclo_actual, id_carrera_ref, estado_matricula, estado_estudiante_practica, malla_academica_pdf)
        VALUES (${testEstudianteCI}, 7, 1, 'REGULAR'::estado_matricula, 'SIN_PRACTICA'::estado_estudiante_practica, ${mallaId})
      """.update.apply()
    }
  }

  override def afterAll(): Unit = {
    DB.localTx { implicit session =>
      sql"DELETE FROM solicitud_empresa_propia WHERE ci_estudiante_ref = ${testEstudianteCI}".update.apply()
      sql"DELETE FROM estudiante_perfil WHERE identificacion = ${testEstudianteCI}".update.apply()
      sql"DELETE FROM usuario WHERE identificacion = ${testEstudianteCI}".update.apply()
      sql"DELETE FROM archivo_pdf WHERE nombre_original IN ('malla_test_ep.pdf', 'oficio_solicitud_test.pdf')".update.apply()
    }
  }

  // ─── Company name ────────────────────────────────────────────────────────────

  "RegistrarSolicitudLogic" should "rechazar la solicitud si el nombre de la entidad está vacío" in {
    val resultado = RegistrarSolicitudLogic.registrar(baseDto.copy(nombreEntidadExterna = ""))
    resultado shouldBe Left(SolicitudEmpresaPropiaFailure.Validacion("El nombre de la institución/empresa es obligatorio."))
  }

  it should "rechazar la solicitud si el nombre de la entidad es nulo" in {
    val resultado = RegistrarSolicitudLogic.registrar(baseDto.copy(nombreEntidadExterna = null))
    resultado shouldBe Left(SolicitudEmpresaPropiaFailure.Validacion("El nombre de la institución/empresa es obligatorio."))
  }

  // ─── RUC ─────────────────────────────────────────────────────────────────────

  it should "rechazar si el RUC tiene menos de 13 dígitos" in {
    val resultado = RegistrarSolicitudLogic.registrar(baseDto.copy(rucEmpresaPropia = "016007609000"))
    resultado shouldBe Left(SolicitudEmpresaPropiaFailure.Validacion("El RUC de la empresa no es válido. Verifique los 13 dígitos y el dígito verificador."))
  }

  it should "rechazar si el RUC tiene más de 13 dígitos" in {
    val resultado = RegistrarSolicitudLogic.registrar(baseDto.copy(rucEmpresaPropia = "01600760900015"))
    resultado shouldBe Left(SolicitudEmpresaPropiaFailure.Validacion("El RUC de la empresa no es válido. Verifique los 13 dígitos y el dígito verificador."))
  }

  it should "rechazar si el RUC contiene caracteres no numéricos" in {
    val resultado = RegistrarSolicitudLogic.registrar(baseDto.copy(rucEmpresaPropia = "016007609000X"))
    resultado shouldBe Left(SolicitudEmpresaPropiaFailure.Validacion("El RUC de la empresa no es válido. Verifique los 13 dígitos y el dígito verificador."))
  }

  it should "rechazar si el RUC tiene 13 dígitos numéricos pero dígito verificador inválido" in {
    val resultado = RegistrarSolicitudLogic.registrar(baseDto.copy(rucEmpresaPropia = "0160076080001"))
    resultado shouldBe Left(SolicitudEmpresaPropiaFailure.Validacion("El RUC de la empresa no es válido. Verifique los 13 dígitos y el dígito verificador."))
  }

  // ─── Company contact email ────────────────────────────────────────────────────

  it should "rechazar la solicitud si el correo institucional de la empresa está vacío" in {
    val resultado = RegistrarSolicitudLogic.registrar(baseDto.copy(contactoEmpresaPropia = "   "))
    resultado shouldBe Left(SolicitudEmpresaPropiaFailure.Validacion("El correo institucional de la empresa es obligatorio."))
  }

  // ─── JIT company profile fields ──────────────────────────────────────────────

  it should "rechazar si la dirección de la sede matriz está vacía" in {
    val resultado = RegistrarSolicitudLogic.registrar(baseDto.copy(direccionEmpresaPropia = ""))
    resultado shouldBe Left(SolicitudEmpresaPropiaFailure.Validacion("La dirección de la sede matriz es obligatoria."))
  }

  it should "rechazar si la misión de la organización está vacía" in {
    val resultado = RegistrarSolicitudLogic.registrar(baseDto.copy(misionEmpresaPropia = ""))
    resultado shouldBe Left(SolicitudEmpresaPropiaFailure.Validacion("La misión de la organización es obligatoria."))
  }

  it should "rechazar si la visión de la organización está vacía" in {
    val resultado = RegistrarSolicitudLogic.registrar(baseDto.copy(visionEmpresaPropia = ""))
    resultado shouldBe Left(SolicitudEmpresaPropiaFailure.Validacion("La visión de la organización es obligatoria."))
  }

  // ─── Office transcript ────────────────────────────────────────────────────────

  it should "rechazar la solicitud si el contenido del oficio transcrito está vacío" in {
    val resultado = RegistrarSolicitudLogic.registrar(baseDto.copy(contenidoOficioTranscrito = ""))
    resultado shouldBe Left(SolicitudEmpresaPropiaFailure.Validacion("El contenido del oficio transcrito es obligatorio."))
  }

  // ─── External supervisor ─────────────────────────────────────────────────────

  it should "rechazar si la cédula del supervisor externo tiene menos de 10 dígitos" in {
    val resultado = RegistrarSolicitudLogic.registrar(baseDto.copy(ciSupervisorExterno = "010203040"))
    resultado shouldBe Left(SolicitudEmpresaPropiaFailure.Validacion("La cédula del supervisor externo no es válida. Verifique los 10 dígitos."))
  }

  it should "rechazar si la cédula del supervisor externo tiene más de 10 dígitos" in {
    val resultado = RegistrarSolicitudLogic.registrar(baseDto.copy(ciSupervisorExterno = "01020304050"))
    resultado shouldBe Left(SolicitudEmpresaPropiaFailure.Validacion("La cédula del supervisor externo no es válida. Verifique los 10 dígitos."))
  }

  it should "rechazar si la cédula del supervisor externo tiene 10 dígitos numéricos pero dígito verificador inválido" in {
    val resultado = RegistrarSolicitudLogic.registrar(baseDto.copy(ciSupervisorExterno = "0102030405"))
    resultado shouldBe Left(SolicitudEmpresaPropiaFailure.Validacion("La cédula del supervisor externo no es válida. Verifique los 10 dígitos."))
  }

  it should "rechazar si el nombre del supervisor externo está vacío" in {
    val resultado = RegistrarSolicitudLogic.registrar(baseDto.copy(nombresSupervisorExterno = ""))
    resultado shouldBe Left(SolicitudEmpresaPropiaFailure.Validacion("El nombre completo del supervisor externo es obligatorio."))
  }

  it should "rechazar si el correo del supervisor externo no contiene @" in {
    val resultado = RegistrarSolicitudLogic.registrar(baseDto.copy(emailSupervisorExterno = "supervisorgad.gob.ec"))
    resultado shouldBe Left(SolicitudEmpresaPropiaFailure.Validacion("El correo electrónico del supervisor externo no es válido."))
  }

  it should "rechazar si el teléfono del supervisor externo tiene menos de 10 dígitos" in {
    val resultado = RegistrarSolicitudLogic.registrar(baseDto.copy(telefonoSupervisorExterno = "098765432"))
    resultado shouldBe Left(SolicitudEmpresaPropiaFailure.Validacion("El teléfono del supervisor externo debe tener exactamente 10 dígitos numéricos."))
  }

  it should "rechazar si el teléfono del supervisor externo contiene letras" in {
    val resultado = RegistrarSolicitudLogic.registrar(baseDto.copy(telefonoSupervisorExterno = "09876543AB"))
    resultado shouldBe Left(SolicitudEmpresaPropiaFailure.Validacion("El teléfono del supervisor externo debe tener exactamente 10 dígitos numéricos."))
  }

  // ─── Hours range ─────────────────────────────────────────────────────────────

  it should "rechazar si las horas propuestas son inferiores al mínimo permitido de 40" in {
    val resultado = RegistrarSolicitudLogic.registrar(baseDto.copy(horasEmpresaPropia = 39))
    resultado shouldBe Left(SolicitudEmpresaPropiaFailure.Validacion("La duración de la práctica debe estar estrictamente entre 40 y 400 horas."))
  }

  it should "rechazar si las horas propuestas superan el máximo permitido de 400" in {
    val resultado = RegistrarSolicitudLogic.registrar(baseDto.copy(horasEmpresaPropia = 401))
    resultado shouldBe Left(SolicitudEmpresaPropiaFailure.Validacion("La duración de la práctica debe estar estrictamente entre 40 y 400 horas."))
  }

  it should "aceptar exactamente 40 horas como límite inferior válido (falla solo en PDF vacío)" in {
    val resultado = RegistrarSolicitudLogic.registrar(baseDto.copy(horasEmpresaPropia = 40, pdfNombreOriginal = "", pdfRutaSegura = ""))
    resultado shouldBe Left(SolicitudEmpresaPropiaFailure.Validacion("El archivo PDF del oficio firmado es obligatorio."))
  }

  it should "aceptar exactamente 400 horas como límite superior válido (falla solo en PDF vacío)" in {
    val resultado = RegistrarSolicitudLogic.registrar(baseDto.copy(horasEmpresaPropia = 400, pdfNombreOriginal = "", pdfRutaSegura = ""))
    resultado shouldBe Left(SolicitudEmpresaPropiaFailure.Validacion("El archivo PDF del oficio firmado es obligatorio."))
  }

  // ─── PDF ─────────────────────────────────────────────────────────────────────

  it should "rechazar la solicitud si no se adjunta un PDF firmado" in {
    val resultado = RegistrarSolicitudLogic.registrar(baseDto.copy(pdfNombreOriginal = "", pdfRutaSegura = ""))
    resultado shouldBe Left(SolicitudEmpresaPropiaFailure.Validacion("El archivo PDF del oficio firmado es obligatorio."))
  }

  it should "rechazar si el nombre del PDF está vacío aunque la ruta sea válida" in {
    val resultado = RegistrarSolicitudLogic.registrar(baseDto.copy(pdfNombreOriginal = ""))
    resultado shouldBe Left(SolicitudEmpresaPropiaFailure.Validacion("El archivo PDF del oficio firmado es obligatorio."))
  }

  // ─── Student state ───────────────────────────────────────────────────────────

  it should "bloquear el trámite si el estudiante ya tiene una práctica activa" in {
    DB.localTx { implicit session =>
      sql"""
        UPDATE estudiante_perfil
        SET estado_estudiante_practica = 'CON_PRACTICA_ACTIVA'::estado_estudiante_practica
        WHERE identificacion = ${testEstudianteCI}
      """.update.apply()
    }

    val resultado = RegistrarSolicitudLogic.registrar(baseDto)
    resultado shouldBe Left(SolicitudEmpresaPropiaFailure.Validacion(
      "No es posible iniciar un trámite de empresa propia: el estudiante ya cuenta con una práctica activa o en proceso."
    ))

    DB.localTx { implicit session =>
      sql"""
        UPDATE estudiante_perfil
        SET estado_estudiante_practica = 'SIN_PRACTICA'::estado_estudiante_practica
        WHERE identificacion = ${testEstudianteCI}
      """.update.apply()
    }
  }

  // ─── Happy path ──────────────────────────────────────────────────────────────

  it should "registrar la solicitud exitosamente en estado PENDIENTE con todos los campos JIT" in {
    val dto = baseDto.copy(pdfNombreOriginal = "oficio_solicitud_test.pdf", pdfRutaSegura = "uploads/oficio_solicitud_test.pdf")
    val resultado = RegistrarSolicitudLogic.registrar(dto)

    resultado.isRight shouldBe true
    val solicitudId = resultado.toOption.get

    val dbRow = DB.readOnly { implicit session =>
      sql"""
        SELECT s.ci_estudiante_ref, s.nombre_entidad_externa, s.ruc_empresa_propia,
               s.horas_empresa_propia, s.estado_tramite, p.tipo_archivo
        FROM solicitud_empresa_propia s
        JOIN archivo_pdf p ON s.oficio_solicitud_inicial_pdf = p.id_archivo_pdf
        WHERE s.id_solicitud_propia = ${solicitudId}
      """.map { rs =>
        (
          rs.string("ci_estudiante_ref"),
          rs.string("nombre_entidad_externa"),
          rs.string("ruc_empresa_propia"),
          rs.int("horas_empresa_propia"),
          rs.string("estado_tramite"),
          rs.string("tipo_archivo")
        )
      }.single.apply()
    }

    dbRow shouldBe Some((
      testEstudianteCI,
      "GAD Municipal del Cantón Cuenca",
      "0160076090001",
      160,
      "PENDIENTE",
      "T4_OFICIO_SOLICITUD_PROP_INICIAL"
    ))
  }
}
