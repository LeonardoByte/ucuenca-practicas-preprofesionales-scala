package com.ucuenca.gestion.models.logic

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll
import scalikejdbc._
import com.ucuenca.gestion.utils.DatabaseConnection
import com.ucuenca.gestion.models.dto.CrearOfertaDTO
import com.ucuenca.gestion.models.enums.EstadoOferta
import com.ucuenca.gestion.models.db.OfertaRepository

class RevisionOfertasLogicSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  val testEmpresaRuc = "0505050505001"
  var testOfertaId: Int = _

  override def beforeAll(): Unit = {
    DatabaseConnection.initialize()

    DB.localTx { implicit session =>
      // Limpiar ofertas previas asociadas a la empresa de prueba
      sql"DELETE FROM oferta_convocatoria WHERE ruc_empresa_ref = ${testEmpresaRuc}".update.apply()
      sql"DELETE FROM archivo_pdf WHERE tipo_archivo = 'T7_PLANTILLA_FORMATO_OFERTA'::tipo_archivo_pdf".update.apply()

      // Crear oferta de prueba pendiente
      val dto = CrearOfertaDTO(
        rucEmpresaRef = testEmpresaRuc,
        tituloOferta = "Desarrollador React Native",
        vacantesSolicitadas = 1,
        duracionHoras = 160,
        descripcionGeneral = "Apps móviles.",
        requisitosObligatorios = "React, JS.",
        actividadesEspecificas = "Desarrollo.",
        pdfNombreOriginal = "react.pdf",
        pdfRutaSegura = "uploads/react.pdf"
      )
      testOfertaId = OfertaRepository.crearOferta(dto)
    }
  }

  override def afterAll(): Unit = {
    DB.localTx { implicit session =>
      sql"DELETE FROM oferta_convocatoria WHERE ruc_empresa_ref = ${testEmpresaRuc}".update.apply()
      sql"DELETE FROM archivo_pdf WHERE tipo_archivo = 'T7_PLANTILLA_FORMATO_OFERTA'::tipo_archivo_pdf".update.apply()
    }
  }

  "RevisionOfertasLogic" should "listar las ofertas pendientes de revisión" in {
    val result = RevisionOfertasLogic.listarPendientes()
    result.isRight shouldBe true
    val lista = result.toOption.get
    lista.exists(_.idOferta == testOfertaId) shouldBe true
    lista.find(_.idOferta == testOfertaId).get.estadoOferta shouldBe "PENDIENTE"
  }

  it should "permitir aprobar una oferta pendiente registrando la fecha de publicación actual" in {
    val result = RevisionOfertasLogic.aprobar(testOfertaId)
    result.isRight shouldBe true

    val dbOferta = OfertaRepository.buscarPorId(testOfertaId)
    dbOferta.isDefined shouldBe true
    dbOferta.get.estadoOferta shouldBe EstadoOferta.APROBADA
    dbOferta.get.fechaPublicacion.isDefined shouldBe true
    dbOferta.get.fechaPublicacion.get shouldBe java.time.LocalDate.now()
  }

  it should "rechazar la operación de rechazo si la justificación técnica de rechazo está vacía" in {
    // Primero, volvemos a poner la oferta a PENDIENTE para probar el rechazo
    DB.localTx { implicit session =>
      sql"UPDATE oferta_convocatoria SET estado_oferta = 'PENDIENTE'::estado_oferta WHERE id_oferta = ${testOfertaId}".update.apply()
    }

    val result = RevisionOfertasLogic.rechazar(testOfertaId, "  ")
    result shouldBe Left(RevisionFailure.Validacion("La justificación técnica de rechazo es obligatoria."))
  }

  it should "permitir rechazar una oferta pendiente exigiendo la justificación técnica" in {
    val result = RevisionOfertasLogic.rechazar(testOfertaId, "La oferta no detalla suficientes actividades técnicas.")
    result.isRight shouldBe true

    val dbOferta = OfertaRepository.buscarPorId(testOfertaId)
    dbOferta.isDefined shouldBe true
    dbOferta.get.estadoOferta shouldBe EstadoOferta.RECHAZADA
    dbOferta.get.justificacionCoordinador shouldBe Some("La oferta no detalla suficientes actividades técnicas.")
  }

  it should "rechazar operaciones sobre ofertas que no existen" in {
    RevisionOfertasLogic.aprobar(-99) shouldBe Left(RevisionFailure.Validacion("La oferta especificada no existe."))
    RevisionOfertasLogic.rechazar(-99, "Justificacion") shouldBe Left(RevisionFailure.Validacion("La oferta especificada no existe."))
  }
}
