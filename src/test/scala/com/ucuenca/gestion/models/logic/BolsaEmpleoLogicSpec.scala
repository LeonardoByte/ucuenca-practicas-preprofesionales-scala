package com.ucuenca.gestion.models.logic

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll
import scalikejdbc._
import com.ucuenca.gestion.utils.DatabaseConnection
import com.ucuenca.gestion.models.dto.CrearOfertaDTO
import com.ucuenca.gestion.models.db.OfertaRepository
import com.ucuenca.gestion.models.db.PostulacionBolsaRepository
import com.ucuenca.gestion.models.enums.EstadoEstudiantePractica

class BolsaEmpleoLogicSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  val testEmpresaRuc = "0505050505001"
  val eligibleStudentCi = "0101010101"
  val ineligibleCycleStudentCi = "0202020202"
  val ineligibleStatusStudentCi = "0303030303"
  var testOfertaAprobadaId: Int = _

  override def beforeAll(): Unit = {
    DatabaseConnection.initialize()

    DB.localTx { implicit session =>
      sql"DELETE FROM practica_registro WHERE ci_estudiante_ref IN (${eligibleStudentCi}, ${ineligibleCycleStudentCi}, ${ineligibleStatusStudentCi})".update.apply()
      sql"DELETE FROM postulacion_bolsa WHERE ci_estudiante_ref IN (${eligibleStudentCi}, ${ineligibleCycleStudentCi}, ${ineligibleStatusStudentCi})".update.apply()
      sql"DELETE FROM solicitud_empresa_propia WHERE ci_estudiante_ref IN (${eligibleStudentCi}, ${ineligibleCycleStudentCi}, ${ineligibleStatusStudentCi})".update.apply()
      sql"DELETE FROM estudiante_perfil WHERE identificacion IN (${eligibleStudentCi}, ${ineligibleCycleStudentCi}, ${ineligibleStatusStudentCi})".update.apply()
      sql"DELETE FROM usuario WHERE identificacion IN (${eligibleStudentCi}, ${ineligibleCycleStudentCi}, ${ineligibleStatusStudentCi})".update.apply()
      sql"DELETE FROM oferta_convocatoria WHERE ruc_empresa_ref = ${testEmpresaRuc}".update.apply()
      sql"DELETE FROM archivo_pdf WHERE nombre_original IN ('malla.pdf', 'conv.pdf')".update.apply()

      // Crear PDF de malla académica base
      val pdfId = sql"""
        INSERT INTO archivo_pdf (tipo_archivo, nombre_original, ruta_segura_servidor)
        VALUES ('T9_MALLA_ACADEMICA'::tipo_archivo_pdf, 'malla.pdf', 'uploads/malla.pdf')
      """.updateAndReturnGeneratedKey.apply().toInt

      // Crear usuarios y perfiles de estudiantes
      // Estudiante 1: Elegible (Ciclo 7, SIN_PRACTICA)
      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${eligibleStudentCi}, 'Estudiante Elegible', 'eleg@test.com', 'ESTUDIANTE'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()
      sql"INSERT INTO estudiante_perfil (identificacion, ciclo_actual, id_carrera_ref, estado_matricula, estado_estudiante_practica, malla_academica_pdf) VALUES (${eligibleStudentCi}, 7, 1, 'REGULAR'::estado_matricula, 'SIN_PRACTICA'::estado_estudiante_practica, ${pdfId})".update.apply()

      // Estudiante 2: Inelegible Ciclo (Ciclo 5, SIN_PRACTICA)
      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${ineligibleCycleStudentCi}, 'Estudiante Ciclo Bajo', 'ciclo@test.com', 'ESTUDIANTE'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()
      sql"INSERT INTO estudiante_perfil (identificacion, ciclo_actual, id_carrera_ref, estado_matricula, estado_estudiante_practica, malla_academica_pdf) VALUES (${ineligibleCycleStudentCi}, 5, 1, 'REGULAR'::estado_matricula, 'SIN_PRACTICA'::estado_estudiante_practica, ${pdfId})".update.apply()

      // Estudiante 3: Inelegible Estado (Ciclo 8, EN_PRACTICA)
      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${ineligibleStatusStudentCi}, 'Estudiante Ocupado', 'ocup@test.com', 'ESTUDIANTE'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()
      sql"INSERT INTO estudiante_perfil (identificacion, ciclo_actual, id_carrera_ref, estado_matricula, estado_estudiante_practica, malla_academica_pdf) VALUES (${ineligibleStatusStudentCi}, 8, 1, 'REGULAR'::estado_matricula, 'CON_PRACTICA_ACTIVA'::estado_estudiante_practica, ${pdfId})".update.apply()

      // Crear oferta de prueba
      val dto = CrearOfertaDTO(
        rucEmpresaRef = testEmpresaRuc,
        tituloOferta = "Puesto Aprobado de Bolsa",
        vacantesSolicitadas = 5,
        duracionHoras = 160,
        descripcionGeneral = "Detalles",
        requisitosObligatorios = "Requisitos",
        actividadesEspecificas = "Actividades",
        pdfNombreOriginal = "conv.pdf",
        pdfRutaSegura = "uploads/conv.pdf"
      )
      testOfertaAprobadaId = OfertaRepository.crearOferta(dto)

      // Aprobar la oferta
      OfertaRepository.aprobarOferta(testOfertaAprobadaId)
    }
  }

  override def afterAll(): Unit = {
    DB.localTx { implicit session =>
      sql"DELETE FROM practica_registro WHERE ci_estudiante_ref IN (${eligibleStudentCi}, ${ineligibleCycleStudentCi}, ${ineligibleStatusStudentCi})".update.apply()
      sql"DELETE FROM postulacion_bolsa WHERE ci_estudiante_ref IN (${eligibleStudentCi}, ${ineligibleCycleStudentCi}, ${ineligibleStatusStudentCi})".update.apply()
      sql"DELETE FROM solicitud_empresa_propia WHERE ci_estudiante_ref IN (${eligibleStudentCi}, ${ineligibleCycleStudentCi}, ${ineligibleStatusStudentCi})".update.apply()
      sql"DELETE FROM estudiante_perfil WHERE identificacion IN (${eligibleStudentCi}, ${ineligibleCycleStudentCi}, ${ineligibleStatusStudentCi})".update.apply()
      sql"DELETE FROM usuario WHERE identificacion IN (${eligibleStudentCi}, ${ineligibleCycleStudentCi}, ${ineligibleStatusStudentCi})".update.apply()
      sql"DELETE FROM oferta_convocatoria WHERE ruc_empresa_ref = ${testEmpresaRuc}".update.apply()
      sql"DELETE FROM archivo_pdf WHERE nombre_original IN ('malla.pdf', 'conv.pdf')".update.apply()
    }
  }

  "BolsaEmpleoLogic" should "listar las ofertas aprobadas en la bolsa" in {
    val result = BolsaEmpleoLogic.listarOfertasAprobadas()
    result.isRight shouldBe true
    val lista = result.toOption.get
    lista.exists(_.idOferta == testOfertaAprobadaId) shouldBe true
  }

  it should "permitir la postulación si el alumno es de ciclo >= 6 y estado SIN_PRACTICA" in {
    val result = BolsaEmpleoLogic.postular(eligibleStudentCi, testOfertaAprobadaId)
    result.isRight shouldBe true

    PostulacionBolsaRepository.yaPostulo(eligibleStudentCi, testOfertaAprobadaId) shouldBe true
  }

  it should "bloquear la postulación si el alumno pertenece a un ciclo menor a 6" in {
    val result = BolsaEmpleoLogic.postular(ineligibleCycleStudentCi, testOfertaAprobadaId)
    result shouldBe Left(BolsaFailure.Validacion("El estudiante debe pertenecer estrictamente al sexto ciclo académico o superior para postular."))
  }

  it should "bloquear la postulación si el alumno no cuenta con el estado de práctica SIN_PRACTICA" in {
    val result = BolsaEmpleoLogic.postular(ineligibleStatusStudentCi, testOfertaAprobadaId)
    result shouldBe Left(BolsaFailure.Validacion("El estudiante debe registrar exactamente un estado de práctica 'SIN_PRACTICA' para postular."))
  }

  it should "rechazar postulaciones duplicadas para una misma vacante" in {
    // Intentar postular de nuevo con el alumno elegible que ya postuló
    val result = BolsaEmpleoLogic.postular(eligibleStudentCi, testOfertaAprobadaId)
    result shouldBe Left(BolsaFailure.Validacion("Ya cuenta con una postulación activa para esta vacante."))
  }
}
