package com.ucuenca.gestion.models.logic

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll
import scalikejdbc._
import com.ucuenca.gestion.utils.DatabaseConnection
import com.ucuenca.gestion.models.dto.CrearOfertaDTO
import com.ucuenca.gestion.models.enums.EstadoOferta
import com.ucuenca.gestion.models.db.OfertaRepository

class MonitoreoOfertasLogicSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  val testEmpresaRuc = "0505050505001"
  var testOfertaPendienteId: Int = _
  var testOfertaRechazadaId: Int = _

  override def beforeAll(): Unit = {
    DatabaseConnection.initialize()

    DB.localTx { implicit session =>
      // Limpiar ofertas previas asociadas a la empresa de prueba
      sql"DELETE FROM postulacion_bolsa WHERE id_oferta_ref IN (SELECT id_oferta FROM oferta_convocatoria WHERE ruc_empresa_ref = ${testEmpresaRuc})".update.apply()
      sql"DELETE FROM oferta_convocatoria WHERE ruc_empresa_ref = ${testEmpresaRuc}".update.apply()
      sql"DELETE FROM archivo_pdf WHERE tipo_archivo = 'T7_PLANTILLA_FORMATO_OFERTA'::tipo_archivo_pdf".update.apply()

      // Crear oferta de prueba pendiente
      val dtoPendiente = CrearOfertaDTO(
        rucEmpresaRef = testEmpresaRuc,
        tituloOferta = "Oferta de Prueba Pendiente",
        vacantesSolicitadas = 2,
        duracionHoras = 100,
        descripcionGeneral = "Desc",
        requisitosObligatorios = "Req",
        actividadesEspecificas = "Act",
        pdfNombreOriginal = "test_pend.pdf",
        pdfRutaSegura = "uploads/test_pend.pdf"
      )
      testOfertaPendienteId = OfertaRepository.crearOferta(dtoPendiente)

      // Crear oferta de prueba rechazada
      val dtoRechazada = CrearOfertaDTO(
        rucEmpresaRef = testEmpresaRuc,
        tituloOferta = "Oferta de Prueba Rechazada",
        vacantesSolicitadas = 3,
        duracionHoras = 120,
        descripcionGeneral = "Desc",
        requisitosObligatorios = "Req",
        actividadesEspecificas = "Act",
        pdfNombreOriginal = "test_rech.pdf",
        pdfRutaSegura = "uploads/test_rech.pdf"
      )
      testOfertaRechazadaId = OfertaRepository.crearOferta(dtoRechazada)

      // Forzar estado RECHADA y agregar retroalimentación en la base de datos
      sql"""
        UPDATE oferta_convocatoria
        SET estado_oferta = 'RECHAZADA'::estado_oferta,
            justificacion_coordinador = 'Faltan detalles de actividades técnicas.'
        WHERE id_oferta = ${testOfertaRechazadaId}
      """.update.apply()
    }
  }

  override def afterAll(): Unit = {
    DB.localTx { implicit session =>
      sql"DELETE FROM postulacion_bolsa WHERE id_oferta_ref IN (SELECT id_oferta FROM oferta_convocatoria WHERE ruc_empresa_ref = ${testEmpresaRuc})".update.apply()
      sql"DELETE FROM oferta_convocatoria WHERE ruc_empresa_ref = ${testEmpresaRuc}".update.apply()
      sql"DELETE FROM archivo_pdf WHERE tipo_archivo = 'T7_PLANTILLA_FORMATO_OFERTA'::tipo_archivo_pdf".update.apply()
    }
  }

  "MonitoreoOfertasLogic" should "listar las ofertas asociadas a una empresa en orden cronológico inverso" in {
    val result = MonitoreoOfertasLogic.listarPorEmpresa(testEmpresaRuc)
    result.isRight shouldBe true
    val lista = result.toOption.get
    lista.exists(_.idOferta == testOfertaPendienteId) shouldBe true
    lista.exists(_.idOferta == testOfertaRechazadaId) shouldBe true
    
    // El orden debe ser por id desc
    lista.head.idOferta shouldBe testOfertaRechazadaId
  }

  it should "permitir forzar el cierre manual de una oferta en estado PENDIENTE" in {
    val result = MonitoreoOfertasLogic.forzarCierre(testOfertaPendienteId)
    result.isRight shouldBe true

    val dbOferta = OfertaRepository.buscarPorId(testOfertaPendienteId)
    dbOferta.isDefined shouldBe true
    dbOferta.get.estadoOferta shouldBe EstadoOferta.CERRADA_MANUAL
  }

  it should "rechazar el cierre manual si la oferta no existe" in {
    val result = MonitoreoOfertasLogic.forzarCierre(-999)
    result shouldBe Left(MonitoreoFailure.Validacion("La oferta especificada no existe."))
  }

  it should "rechazar el cierre manual de una oferta que ya está rechazada" in {
    val result = MonitoreoOfertasLogic.forzarCierre(testOfertaRechazadaId)
    result shouldBe Left(MonitoreoFailure.Validacion("Solo se pueden cerrar manualmente ofertas activas (PENDIENTE o APROBADA)."))
  }
}
