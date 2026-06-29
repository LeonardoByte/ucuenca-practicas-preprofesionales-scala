package com.ucuenca.gestion.models.logic

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll
import scalikejdbc._
import com.ucuenca.gestion.utils.DatabaseConnection
import com.ucuenca.gestion.models.dto.CrearOfertaDTO
import com.ucuenca.gestion.models.enums.EstadoOferta

class CrearOfertaLogicSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  val testEmpresaRuc = "0505050505001"

  override def beforeAll(): Unit = {
    DatabaseConnection.initialize()
    
    DB.localTx { implicit session =>
      // Limpiar ofertas previas asociadas a la empresa de prueba
      sql"DELETE FROM postulacion_bolsa WHERE id_oferta_ref IN (SELECT id_oferta FROM oferta_convocatoria WHERE ruc_empresa_ref = ${testEmpresaRuc})".update.apply()
      sql"DELETE FROM oferta_convocatoria WHERE ruc_empresa_ref = ${testEmpresaRuc}".update.apply()
      sql"DELETE FROM archivo_pdf WHERE tipo_archivo = 'T7_PLANTILLA_FORMATO_OFERTA'::tipo_archivo_pdf".update.apply()
    }
  }

  override def afterAll(): Unit = {
    DB.localTx { implicit session =>
      sql"DELETE FROM postulacion_bolsa WHERE id_oferta_ref IN (SELECT id_oferta FROM oferta_convocatoria WHERE ruc_empresa_ref = ${testEmpresaRuc})".update.apply()
      sql"DELETE FROM oferta_convocatoria WHERE ruc_empresa_ref = ${testEmpresaRuc}".update.apply()
      sql"DELETE FROM archivo_pdf WHERE tipo_archivo = 'T7_PLANTILLA_FORMATO_OFERTA'::tipo_archivo_pdf".update.apply()
    }
  }

  "CrearOfertaLogic" should "rechazar ofertas con campos obligatorios vacíos o nulos" in {
    val dto = CrearOfertaDTO(
      rucEmpresaRef = testEmpresaRuc,
      tituloOferta = "Desarrollador Backend",
      vacantesSolicitadas = 5,
      duracionHoras = 120,
      descripcionGeneral = "Descripción",
      requisitosObligatorios = "Requisitos",
      actividadesEspecificas = "Actividades",
      pdfNombreOriginal = "plantilla.pdf",
      pdfRutaSegura = "uploads/plantilla.pdf"
    )

    CrearOfertaLogic.crear(dto.copy(tituloOferta = "")) shouldBe Left(CrearOfertaFailure.Validacion("El título de la oferta es obligatorio."))
    CrearOfertaLogic.crear(dto.copy(descripcionGeneral = "   ")) shouldBe Left(CrearOfertaFailure.Validacion("La descripción general es obligatoria."))
    CrearOfertaLogic.crear(dto.copy(requisitosObligatorios = null)) shouldBe Left(CrearOfertaFailure.Validacion("Los requisitos obligatorios son obligatorios."))
    CrearOfertaLogic.crear(dto.copy(actividadesEspecificas = "")) shouldBe Left(CrearOfertaFailure.Validacion("Las actividades específicas son obligatorias."))
  }

  it should "rechazar ofertas si no se adjunta un PDF firmado válido" in {
    val dto = CrearOfertaDTO(
      rucEmpresaRef = testEmpresaRuc,
      tituloOferta = "Desarrollador Backend",
      vacantesSolicitadas = 5,
      duracionHoras = 120,
      descripcionGeneral = "Descripción",
      requisitosObligatorios = "Requisitos",
      actividadesEspecificas = "Actividades",
      pdfNombreOriginal = "",
      pdfRutaSegura = ""
    )

    CrearOfertaLogic.crear(dto) shouldBe Left(CrearOfertaFailure.Validacion("El archivo PDF firmado de la oferta es obligatorio."))
    CrearOfertaLogic.crear(dto.copy(pdfNombreOriginal = "pdf.pdf", pdfRutaSegura = "")) shouldBe Left(CrearOfertaFailure.Validacion("El archivo PDF firmado de la oferta es obligatorio."))
  }

  it should "rechazar ofertas si las vacantes no están en el rango de 1 a 10" in {
    val dto = CrearOfertaDTO(
      rucEmpresaRef = testEmpresaRuc,
      tituloOferta = "Desarrollador Backend",
      vacantesSolicitadas = 0,
      duracionHoras = 120,
      descripcionGeneral = "Descripción",
      requisitosObligatorios = "Requisitos",
      actividadesEspecificas = "Actividades",
      pdfNombreOriginal = "plantilla.pdf",
      pdfRutaSegura = "uploads/plantilla.pdf"
    )

    CrearOfertaLogic.crear(dto) shouldBe Left(CrearOfertaFailure.Validacion("La cantidad de vacantes debe estar estrictamente entre 1 y 10 cupos."))
    CrearOfertaLogic.crear(dto.copy(vacantesSolicitadas = 11)) shouldBe Left(CrearOfertaFailure.Validacion("La cantidad de vacantes debe estar estrictamente entre 1 y 10 cupos."))
  }

  it should "rechazar ofertas si la duración no está en el rango de 40 a 400 horas" in {
    val dto = CrearOfertaDTO(
      rucEmpresaRef = testEmpresaRuc,
      tituloOferta = "Desarrollador Backend",
      vacantesSolicitadas = 5,
      duracionHoras = 39,
      descripcionGeneral = "Descripción",
      requisitosObligatorios = "Requisitos",
      actividadesEspecificas = "Actividades",
      pdfNombreOriginal = "plantilla.pdf",
      pdfRutaSegura = "uploads/plantilla.pdf"
    )

    CrearOfertaLogic.crear(dto) shouldBe Left(CrearOfertaFailure.Validacion("La duración de la práctica debe estar estrictamente entre 40 y 400 horas."))
    CrearOfertaLogic.crear(dto.copy(duracionHoras = 401)) shouldBe Left(CrearOfertaFailure.Validacion("La duración de la práctica debe estar estrictamente entre 40 y 400 horas."))
  }

  it should "crear la oferta exitosamente con estado PENDIENTE si pasa todas las validaciones" in {
    val dto = CrearOfertaDTO(
      rucEmpresaRef = testEmpresaRuc,
      tituloOferta = "Desarrollador Backend Scala",
      vacantesSolicitadas = 3,
      duracionHoras = 160,
      descripcionGeneral = "Desarrollo de microservicios con Scala.",
      requisitosObligatorios = "Conocimientos básicos de Scala, SQL y Git.",
      actividadesEspecificas = "1. Escribir tests unitarios\n2. Codificar endpoints REST",
      pdfNombreOriginal = "oferta_firmada_scala.pdf",
      pdfRutaSegura = "uploads/oferta_firmada_scala.pdf"
    )

    val result = CrearOfertaLogic.crear(dto)
    result.isRight shouldBe true
    val ofertaId = result.toOption.get

    // Consultar directamente de la base de datos para validar estado y relaciones
    val dbOferta = DB.readOnly { implicit session =>
      sql"""
        SELECT o.ruc_empresa_ref, o.titulo_oferta, o.estado_oferta, p.tipo_archivo
        FROM oferta_convocatoria o
        JOIN archivo_pdf p ON o.plantilla_oferta_pdf = p.id_archivo_pdf
        WHERE o.id_oferta = ${ofertaId}
      """.map { rs =>
        (rs.string("ruc_empresa_ref"), rs.string("titulo_oferta"), rs.string("estado_oferta"), rs.string("tipo_archivo"))
      }.single.apply()
    }

    dbOferta shouldBe Some((testEmpresaRuc, "Desarrollador Backend Scala", "PENDIENTE", "T7_PLANTILLA_FORMATO_OFERTA"))
  }
}
