package com.ucuenca.gestion.models.logic

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll
import scalikejdbc._
import com.ucuenca.gestion.utils.DatabaseConnection
import com.ucuenca.gestion.models.dto.CrearOfertaDTO
import com.ucuenca.gestion.models.db.OfertaRepository
import com.ucuenca.gestion.models.db.GestionCandidatosRepository
import com.ucuenca.gestion.models.enums.EstadoPostulacion

class SeleccionCandidatosLogicSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  val companyRuc = "0505050505001"
  val student1Ci = "1111111111"
  val student2Ci = "2222222222"
  val tutorEmpresarialCi = "0808080808"

  var offer1Id: Int = _
  var offer2Id: Int = _
  var postulation1Id: Int = _
  var postulation2Id: Int = _
  var parallelPostulationId: Int = _

  override def beforeAll(): Unit = {
    DatabaseConnection.initialize()

    DB.localTx { implicit session =>
      // Limpiar datos previos
      sql"DELETE FROM practica_registro WHERE ci_estudiante_ref IN (${student1Ci}, ${student2Ci}) OR id_tutor_empresarial_ref = ${tutorEmpresarialCi}".update.apply()
      sql"DELETE FROM postulacion_bolsa WHERE ci_estudiante_ref IN (${student1Ci}, ${student2Ci})".update.apply()
      sql"DELETE FROM estudiante_perfil WHERE identificacion IN (${student1Ci}, ${student2Ci})".update.apply()
      sql"DELETE FROM tutor_empresarial_perfil WHERE identificacion = ${tutorEmpresarialCi}".update.apply()
      sql"DELETE FROM usuario WHERE identificacion IN (${student1Ci}, ${student2Ci}, ${tutorEmpresarialCi})".update.apply()
      sql"DELETE FROM oferta_convocatoria WHERE ruc_empresa_ref = ${companyRuc}".update.apply()
      sql"DELETE FROM archivo_pdf WHERE nombre_original IN ('malla_sel1.pdf', 'malla_sel2.pdf', 'conv_sel1.pdf', 'conv_sel2.pdf')".update.apply()

      // Crear PDFs de malla
      val pdf1Id = sql"INSERT INTO archivo_pdf (tipo_archivo, nombre_original, ruta_segura_servidor) VALUES ('T9_MALLA_ACADEMICA'::tipo_archivo_pdf, 'malla_sel1.pdf', 'uploads/m1.pdf')".updateAndReturnGeneratedKey.apply().toInt
      val pdf2Id = sql"INSERT INTO archivo_pdf (tipo_archivo, nombre_original, ruta_segura_servidor) VALUES ('T9_MALLA_ACADEMICA'::tipo_archivo_pdf, 'malla_sel2.pdf', 'uploads/m2.pdf')".updateAndReturnGeneratedKey.apply().toInt

      // Crear tutor
      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${tutorEmpresarialCi}, 'Tutor Test', 'tutor@test.com', 'TUTOR_EMPRESARIAL'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()
      sql"INSERT INTO tutor_empresarial_perfil (identificacion, empresa_id_ref, telefono_contacto) VALUES (${tutorEmpresarialCi}, ${companyRuc}, '0999999999')".update.apply()

      // Crear estudiantes
      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${student1Ci}, 'Estudiante A', 'a@test.com', 'ESTUDIANTE'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()
      sql"INSERT INTO estudiante_perfil (identificacion, ciclo_actual, id_carrera_ref, estado_matricula, estado_estudiante_practica, malla_academica_pdf) VALUES (${student1Ci}, 7, 1, 'REGULAR'::estado_matricula, 'SIN_PRACTICA'::estado_estudiante_practica, ${pdf1Id})".update.apply()

      sql"INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta) VALUES (${student2Ci}, 'Estudiante B', 'b@test.com', 'ESTUDIANTE'::rol_usuario, 'ACTIVA'::estado_cuenta)".update.apply()
      sql"INSERT INTO estudiante_perfil (identificacion, ciclo_actual, id_carrera_ref, estado_matricula, estado_estudiante_practica, malla_academica_pdf) VALUES (${student2Ci}, 8, 1, 'REGULAR'::estado_matricula, 'SIN_PRACTICA'::estado_estudiante_practica, ${pdf2Id})".update.apply()

      // Crear oferta 1 (Con 1 sola vacante para probar cierre de cupos)
      val dto1 = CrearOfertaDTO(
        rucEmpresaRef = companyRuc,
        tituloOferta = "Desarrollador React Seleccion",
        vacantesSolicitadas = 1,
        duracionHoras = 120,
        descripcionGeneral = "React",
        requisitosObligatorios = "JS",
        actividadesEspecificas = "Dev",
        pdfNombreOriginal = "conv_sel1.pdf",
        pdfRutaSegura = "uploads/c1.pdf"
      )
      offer1Id = OfertaRepository.crearOferta(dto1)
      OfertaRepository.aprobarOferta(offer1Id)

      // Crear oferta 2 (Para postulación paralela de estudiante A)
      val dto2 = CrearOfertaDTO(
        rucEmpresaRef = companyRuc,
        tituloOferta = "Desarrollador Java Seleccion",
        vacantesSolicitadas = 3,
        duracionHoras = 160,
        descripcionGeneral = "Java",
        requisitosObligatorios = "Java",
        actividadesEspecificas = "Dev",
        pdfNombreOriginal = "conv_sel2.pdf",
        pdfRutaSegura = "uploads/c2.pdf"
      )
      offer2Id = OfertaRepository.crearOferta(dto2)
      OfertaRepository.aprobarOferta(offer2Id)

      // Postulación 1: Estudiante A a Oferta 1 (VALIDADA_COORDINADOR)
      postulation1Id = sql"INSERT INTO postulacion_bolsa (ci_estudiante_ref, id_oferta_ref, estado_postulacion) VALUES (${student1Ci}, ${offer1Id}, 'VALIDADA_COORDINADOR'::estado_postulacion)".updateAndReturnGeneratedKey.apply().toInt

      // Postulación 2: Estudiante B a Oferta 1 (VALIDADA_COORDINADOR)
      postulation2Id = sql"INSERT INTO postulacion_bolsa (ci_estudiante_ref, id_oferta_ref, estado_postulacion) VALUES (${student2Ci}, ${offer1Id}, 'VALIDADA_COORDINADOR'::estado_postulacion)".updateAndReturnGeneratedKey.apply().toInt

      // Postulación Paralela: Estudiante A a Oferta 2 (VALIDADA_COORDINADOR)
      parallelPostulationId = sql"INSERT INTO postulacion_bolsa (ci_estudiante_ref, id_oferta_ref, estado_postulacion) VALUES (${student1Ci}, ${offer2Id}, 'VALIDADA_COORDINADOR'::estado_postulacion)".updateAndReturnGeneratedKey.apply().toInt
    }
  }

  override def afterAll(): Unit = {
    DB.localTx { implicit session =>
      sql"DELETE FROM practica_registro WHERE ci_estudiante_ref IN (${student1Ci}, ${student2Ci}) OR id_tutor_empresarial_ref = ${tutorEmpresarialCi}".update.apply()
      sql"DELETE FROM postulacion_bolsa WHERE ci_estudiante_ref IN (${student1Ci}, ${student2Ci})".update.apply()
      sql"DELETE FROM estudiante_perfil WHERE identificacion IN (${student1Ci}, ${student2Ci})".update.apply()
      sql"DELETE FROM tutor_empresarial_perfil WHERE identificacion = ${tutorEmpresarialCi}".update.apply()
      sql"DELETE FROM usuario WHERE identificacion IN (${student1Ci}, ${student2Ci}, ${tutorEmpresarialCi})".update.apply()
      sql"DELETE FROM oferta_convocatoria WHERE ruc_empresa_ref = ${companyRuc}".update.apply()
      sql"DELETE FROM archivo_pdf WHERE nombre_original IN ('malla_sel1.pdf', 'malla_sel2.pdf', 'conv_sel1.pdf', 'conv_sel2.pdf')".update.apply()
    }
  }

  "SeleccionCandidatosLogic" should "listar los candidatos validados para la empresa" in {
    val result = SeleccionCandidatosLogic.listarCandidatos(companyRuc)
    result.isRight shouldBe true
    val lista = result.toOption.get
    lista.exists(_.idPostulacion == postulation1Id) shouldBe true
    lista.exists(_.idPostulacion == postulation2Id) shouldBe true
  }

  it should "permitir la aceptación de un candidato ejecutando todas las reglas en cascada" in {
    val result = SeleccionCandidatosLogic.aceptar(
      idPostulacion = postulation1Id,
      rucEmpresa = companyRuc,
      ciEstudiante = student1Ci,
      idTutorEmpresarial = tutorEmpresarialCi
    )
    result shouldBe Right(())

    DB.readOnly { implicit session =>
      // 1. Postulación aceptada
      val p1 = sql"SELECT estado_postulacion FROM postulacion_bolsa WHERE id_postulacion = ${postulation1Id}".map(rs => rs.string("estado_postulacion")).single.apply().get
      p1 shouldBe "APROBADA"

      // 2. Práctica inicializada con tutor académico NULL, estado TUTOR_ACADEMICO_PENDIENTE,
      //    y las horas totales/oferta_id heredadas de la oferta 1 (duracion_horas = 120)
      val pr = sql"""
        SELECT id_tutor_academico_ref, estado_cronograma, horas_totales_requeridas, oferta_id
        FROM practica_registro
        WHERE ci_estudiante_ref = ${student1Ci} AND id_tutor_empresarial_ref = ${tutorEmpresarialCi}
      """.map(rs => (rs.stringOpt("id_tutor_academico_ref"), rs.string("estado_cronograma"), rs.int("horas_totales_requeridas"), rs.intOpt("oferta_id"))).single.apply()
      pr shouldBe Some((None, "TUTOR_ACADEMICO_PENDIENTE", 120, Some(offer1Id)))

      // 3. Estudiante marcado como ocupado
      val ep = sql"SELECT estado_estudiante_practica FROM estudiante_perfil WHERE identificacion = ${student1Ci}".map(rs => rs.string("estado_estudiante_practica")).single.apply().get
      ep shouldBe "CON_PRACTICA_ACTIVA"

      // 4. Cancelación automática de postulaciones paralelas
      val pp = sql"SELECT estado_postulacion FROM postulacion_bolsa WHERE id_postulacion = ${parallelPostulationId}".map(rs => rs.string("estado_postulacion")).single.apply().get
      pp shouldBe "CANCELADA_AUTOMATICO"

      // 5. Oferta cerrada por cupos (limite = 1, cupo = 1)
      val o = sql"SELECT estado_oferta FROM oferta_convocatoria WHERE id_oferta = ${offer1Id}".map(rs => rs.string("estado_oferta")).single.apply().get
      o shouldBe "CERRADA_CUPOS"

      // 6. Candidato B rechazado automáticamente debido al cierre por cupos de la oferta
      val p2 = sql"SELECT estado_postulacion, comentario_rechazo FROM postulacion_bolsa WHERE id_postulacion = ${postulation2Id}".map(rs => (rs.string("estado_postulacion"), rs.string("comentario_rechazo"))).single.apply().get
      p2._1 shouldBe "RECHAZADA"
      p2._2 shouldBe "Convocatoria cerrada por límite de cupos alcanzado."
    }
  }

  it should "rechazar un candidato exigiendo un comentario de justificación" in {
    val resultEmpty = SeleccionCandidatosLogic.rechazar(postulation2Id, "   ")
    resultEmpty shouldBe Left(BolsaFailure.Validacion("El motivo de rechazo es obligatorio."))

    val resultOk = SeleccionCandidatosLogic.rechazar(postulation2Id, "No calza con el perfil técnico de React")
    resultOk.isRight shouldBe true

    val p2 = DB.readOnly { implicit session =>
      sql"SELECT estado_postulacion, comentario_rechazo FROM postulacion_bolsa WHERE id_postulacion = ${postulation2Id}"
        .map(rs => (rs.string("estado_postulacion"), rs.string("comentario_rechazo"))).single.apply().get
    }
    p2._1 shouldBe "RECHAZADA"
    p2._2 shouldBe "No calza con el perfil técnico de React"
  }
}
