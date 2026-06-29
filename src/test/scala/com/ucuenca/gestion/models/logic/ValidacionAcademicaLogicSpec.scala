package com.ucuenca.gestion.models.logic

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll
import scalikejdbc._
import com.ucuenca.gestion.utils.DatabaseConnection
import com.ucuenca.gestion.models.dto.CrearOfertaDTO
import com.ucuenca.gestion.models.db.OfertaRepository
import com.ucuenca.gestion.models.db.PostulacionBolsaRepository
import com.ucuenca.gestion.models.enums.EstadoPostulacion

class ValidacionAcademicaLogicSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  val testEmpresaRuc = "0505050505001"
  val studentCi = "0909090909"
  var testOfertaId: Int = _
  var testPostulacionId: Int = _

  override def beforeAll(): Unit = {
    DatabaseConnection.initialize()

    DB.localTx { implicit session =>
      // Limpiar datos previos
      sql"DELETE FROM postulacion_bolsa WHERE ci_estudiante_ref = ${studentCi}".update.apply()
      sql"DELETE FROM estudiante_perfil WHERE identificacion = ${studentCi}".update.apply()
      sql"DELETE FROM usuario WHERE identificacion = ${studentCi}".update.apply()
      sql"DELETE FROM oferta_convocatoria WHERE ruc_empresa_ref = ${testEmpresaRuc}".update.apply()
      sql"DELETE FROM archivo_pdf WHERE nombre_original IN ('malla_val.pdf', 'conv_val.pdf')".update.apply()

      // Crear PDF de malla académica base
      val pdfId = sql"""
        INSERT INTO archivo_pdf (tipo_archivo, nombre_original, ruta_segura_servidor)
        VALUES ('T9_MALLA_ACADEMICA'::tipo_archivo_pdf, 'malla_val.pdf', 'uploads/malla_val.pdf')
      """.updateAndReturnGeneratedKey.apply().toInt

      // Crear estudiante
      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${studentCi}, 'Estudiante Validar', 'val@test.com', 'ESTUDIANTE'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()
      sql"INSERT INTO estudiante_perfil (identificacion, ciclo_actual, id_carrera_ref, estado_matricula, estado_estudiante_practica, malla_academica_pdf) VALUES (${studentCi}, 8, 1, 'REGULAR'::estado_matricula, 'SIN_PRACTICA'::estado_estudiante_practica, ${pdfId})".update.apply()

      // Crear oferta aprobada
      val dto = CrearOfertaDTO(
        rucEmpresaRef = testEmpresaRuc,
        tituloOferta = "Puesto Test Validar",
        vacantesSolicitadas = 3,
        duracionHoras = 120,
        descripcionGeneral = "Detalles",
        requisitosObligatorios = "Requisitos",
        actividadesEspecificas = "Actividades",
        pdfNombreOriginal = "conv_val.pdf",
        pdfRutaSegura = "uploads/conv_val.pdf"
      )
      testOfertaId = OfertaRepository.crearOferta(dto)
      OfertaRepository.aprobarOferta(testOfertaId)

      // Crear postulación PENDIENTE
      testPostulacionId = sql"""
        INSERT INTO postulacion_bolsa (ci_estudiante_ref, id_oferta_ref, estado_postulacion)
        VALUES (${studentCi}, ${testOfertaId}, 'PENDIENTE'::estado_postulacion)
      """.updateAndReturnGeneratedKey.apply().toInt
    }
  }

  override def afterAll(): Unit = {
    DB.localTx { implicit session =>
      sql"DELETE FROM postulacion_bolsa WHERE ci_estudiante_ref = ${studentCi}".update.apply()
      sql"DELETE FROM estudiante_perfil WHERE identificacion = ${studentCi}".update.apply()
      sql"DELETE FROM usuario WHERE identificacion = ${studentCi}".update.apply()
      sql"DELETE FROM oferta_convocatoria WHERE ruc_empresa_ref = ${testEmpresaRuc}".update.apply()
      sql"DELETE FROM archivo_pdf WHERE nombre_original IN ('malla_val.pdf', 'conv_val.pdf')".update.apply()
    }
  }

  "ValidacionAcademicaLogic" should "listar las postulaciones en estado PENDIENTE" in {
    val result = ValidacionAcademicaLogic.listarPendientes()
    result.isRight shouldBe true
    val lista = result.toOption.get
    lista.exists(_.idPostulacion == testPostulacionId) shouldBe true
  }

  it should "permitir la aprobación académica manteniendo el estado PENDIENTE" in {
    val result = ValidacionAcademicaLogic.aprobar(testPostulacionId)
    result.isRight shouldBe true

    // Verificar en base que sigue PENDIENTE
    val estadoOpt = DB.readOnly { implicit session =>
      sql"SELECT estado_postulacion FROM postulacion_bolsa WHERE id_postulacion = ${testPostulacionId}"
        .map(rs => rs.string("estado_postulacion")).single.apply()
    }
    estadoOpt shouldBe Some("PENDIENTE")
  }

  it should "rechazar una postulación si el comentario de rechazo está vacío o nulo" in {
    val result1 = ValidacionAcademicaLogic.rechazar(testPostulacionId, "")
    result1 shouldBe Left(BolsaFailure.Validacion("El comentario de justificación técnica del rechazo es obligatorio."))

    val result2 = ValidacionAcademicaLogic.rechazar(testPostulacionId, "   ")
    result2 shouldBe Left(BolsaFailure.Validacion("El comentario de justificación técnica del rechazo es obligatorio."))
  }

  it should "permitir el rechazo académico actualizando a RECHAZADA y guardando la justificación" in {
    val comentario = "No cumple con el promedio de la carrera o competencias técnicas"
    val result = ValidacionAcademicaLogic.rechazar(testPostulacionId, comentario)
    result.isRight shouldBe true

    // Verificar estado y comentario
    val (estado, comentarioDb) = DB.readOnly { implicit session =>
      sql"SELECT estado_postulacion, comentario_rechazo FROM postulacion_bolsa WHERE id_postulacion = ${testPostulacionId}"
        .map(rs => (rs.string("estado_postulacion"), rs.string("comentario_rechazo"))).single.apply().get
    }
    estado shouldBe "RECHAZADA"
    comentarioDb shouldBe comentario
  }
}
