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
    contactoEmpresaPropia     = "contacto@gad.gob.ec",
    horasEmpresaPropia        = 160,
    contenidoOficioTranscrito = "Por medio del presente oficio solicito la autorización...",
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

  "RegistrarSolicitudLogic" should "rechazar la solicitud si el nombre de la entidad está vacío" in {
    val resultado = RegistrarSolicitudLogic.registrar(baseDto.copy(nombreEntidadExterna = ""))
    resultado shouldBe Left(SolicitudEmpresaPropiaFailure.Validacion("El nombre de la institución/empresa es obligatorio."))
  }

  it should "rechazar la solicitud si el nombre de la entidad es nulo" in {
    val resultado = RegistrarSolicitudLogic.registrar(baseDto.copy(nombreEntidadExterna = null))
    resultado shouldBe Left(SolicitudEmpresaPropiaFailure.Validacion("El nombre de la institución/empresa es obligatorio."))
  }

  it should "rechazar la solicitud si el contacto está vacío" in {
    val resultado = RegistrarSolicitudLogic.registrar(baseDto.copy(contactoEmpresaPropia = "   "))
    resultado shouldBe Left(SolicitudEmpresaPropiaFailure.Validacion("El correo de contacto del encargado es obligatorio."))
  }

  it should "rechazar la solicitud si el contenido del oficio está vacío" in {
    val resultado = RegistrarSolicitudLogic.registrar(baseDto.copy(contenidoOficioTranscrito = ""))
    resultado shouldBe Left(SolicitudEmpresaPropiaFailure.Validacion("El contenido del oficio transcrito es obligatorio."))
  }

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

  it should "rechazar la solicitud si no se adjunta un PDF firmado" in {
    val resultado = RegistrarSolicitudLogic.registrar(baseDto.copy(pdfNombreOriginal = "", pdfRutaSegura = ""))
    resultado shouldBe Left(SolicitudEmpresaPropiaFailure.Validacion("El archivo PDF del oficio firmado es obligatorio."))
  }

  it should "rechazar si el nombre del PDF está vacío aunque la ruta sea válida" in {
    val resultado = RegistrarSolicitudLogic.registrar(baseDto.copy(pdfNombreOriginal = ""))
    resultado shouldBe Left(SolicitudEmpresaPropiaFailure.Validacion("El archivo PDF del oficio firmado es obligatorio."))
  }

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

  it should "registrar la solicitud exitosamente en estado PENDIENTE si pasa todas las validaciones" in {
    val dto = baseDto.copy(pdfNombreOriginal = "oficio_solicitud_test.pdf", pdfRutaSegura = "uploads/oficio_solicitud_test.pdf")
    val resultado = RegistrarSolicitudLogic.registrar(dto)

    resultado.isRight shouldBe true
    val solicitudId = resultado.toOption.get

    val dbRow = DB.readOnly { implicit session =>
      sql"""
        SELECT s.ci_estudiante_ref, s.nombre_entidad_externa, s.horas_empresa_propia,
               s.estado_tramite, p.tipo_archivo
        FROM solicitud_empresa_propia s
        JOIN archivo_pdf p ON s.oficio_solicitud_inicial_pdf = p.id_archivo_pdf
        WHERE s.id_solicitud_propia = ${solicitudId}
      """.map { rs =>
        (
          rs.string("ci_estudiante_ref"),
          rs.string("nombre_entidad_externa"),
          rs.int("horas_empresa_propia"),
          rs.string("estado_tramite"),
          rs.string("tipo_archivo")
        )
      }.single.apply()
    }

    dbRow shouldBe Some((
      testEstudianteCI,
      "GAD Municipal del Cantón Cuenca",
      160,
      "PENDIENTE",
      "T4_OFICIO_SOLICITUD_PROP_INICIAL"
    ))
  }
}
