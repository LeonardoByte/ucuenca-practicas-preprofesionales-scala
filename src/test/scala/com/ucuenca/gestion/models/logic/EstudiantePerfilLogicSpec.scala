package com.ucuenca.gestion.models.logic

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll
import scalikejdbc._
import com.ucuenca.gestion.utils.DatabaseConnection
import com.ucuenca.gestion.models.enums.{RolUsuario, EstadoCuenta, EstadoMatricula, EstadoEstudiantePractica}
import com.ucuenca.gestion.models.entities.Usuario

class EstudiantePerfilLogicSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  override def beforeAll(): Unit = {
    DatabaseConnection.initialize()
    cleanup()

    // Sembrar datos de prueba
    DB.localTx { implicit session =>
      // Insertar carrera si no existe (id 1)
      val existsCarrera = sql"SELECT count(*) FROM carrera WHERE id_carrera = 1".map(rs => rs.long(1)).single.apply().getOrElse(0L) > 0
      if (!existsCarrera) {
        sql"INSERT INTO carrera (id_carrera, nombre_carrera) VALUES (1, 'Ingeniería Test')".update.apply()
      }

      // Insertar PDF analítico base (id 9999)
      val existsPdf = sql"SELECT count(*) FROM archivo_pdf WHERE id_archivo_pdf = 9999".map(rs => rs.long(1)).single.apply().getOrElse(0L) > 0
      if (!existsPdf) {
        sql"""
          INSERT INTO archivo_pdf (id_archivo_pdf, tipo_archivo, nombre_original, ruta_segura_servidor)
          VALUES (9999, 'T9_MALLA_ACADEMICA'::tipo_archivo_pdf, 'analitico.pdf', '/safe/analitico.pdf')
        """.update.apply()
      }

      // Insertar usuario estudiante
      sql"""
        INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta)
        VALUES ('0101019911', 'Estudiante Perfil Test', 'est.perfil@test.com', 'ESTUDIANTE'::rol_usuario, 'ACTIVA'::estado_cuenta)
      """.update.apply()

      sql"""
        INSERT INTO estudiante_perfil (identificacion, ciclo_actual, id_carrera_ref, estado_matricula, estado_estudiante_practica, malla_academica_pdf)
        VALUES ('0101019911', 6, 1, 'REGULAR'::estado_matricula, 'SIN_PRACTICA'::estado_estudiante_practica, 9999)
      """.update.apply()
    }
  }

  override def afterAll(): Unit = {
    cleanup()
  }

  private def cleanup(): Unit = {
    DB.localTx { implicit session =>
      sql"DELETE FROM estudiante_perfil WHERE identificacion = '0101019911'".update.apply()
      sql"DELETE FROM usuario WHERE identificacion = '0101019911'".update.apply()
      sql"DELETE FROM archivo_pdf WHERE id_archivo_pdf = 9999 OR nombre_original LIKE 'perfil_test_%'".update.apply()
    }
  }

  "EstudiantePerfilLogic" should "cargar correctamente el perfil del estudiante por su identificación" in {
    val result = EstudiantePerfilLogic.obtenerPerfilCompleto("0101019911")
    result.isRight shouldBe true
    val (user, perfil, mallaOpt, cvOpt) = result.toOption.get
    user.nombresCompletos shouldBe "Estudiante Perfil Test"
    perfil.cicloActual shouldBe 6
    mallaOpt.get.nombreOriginal shouldBe "analitico.pdf"
    cvOpt shouldBe None
  }

  it should "rechazar archivos que no tengan extensión PDF" in {
    // Caso con extensión inválida
    val resultInvalid = EstudiantePerfilLogic.cargarDocumento(
      identificacion = "0101019911",
      nombreArchivo = "CV.png",
      rutaSegura = "/safe/CV.png",
      esMalla = false
    )
    resultInvalid shouldBe Left(PerfilFailure.Validacion("El formato del archivo es inválido. Solo se admiten archivos PDF."))

    // Caso con PDF válido
    val resultValid = EstudiantePerfilLogic.cargarDocumento(
      identificacion = "0101019911",
      nombreArchivo = "perfil_test_cv.pdf",
      rutaSegura = "/safe/perfil_test_cv.pdf",
      esMalla = false
    )
    resultValid.isRight shouldBe true
    resultValid.toOption.get.nombreOriginal shouldBe "perfil_test_cv.pdf"
  }

  it should "bloquear nuevas postulaciones si el estado de práctica es diferente de SIN_PRACTICA" in {
    // Estado SIN_PRACTICA -> NO bloquea
    EstudiantePerfilLogic.validarBloqueoPostulacion(EstadoEstudiantePractica.SIN_PRACTICA) shouldBe false

    // Estado CON_PRACTICA_ACTIVA -> SI bloquea
    EstudiantePerfilLogic.validarBloqueoPostulacion(EstadoEstudiantePractica.CON_PRACTICA_ACTIVA) shouldBe true

    // Estado PRACTICA_ACREDITADA -> SI bloquea
    EstudiantePerfilLogic.validarBloqueoPostulacion(EstadoEstudiantePractica.PRACTICA_ACREDITADA) shouldBe true
  }

  it should "obtener con éxito el conteo de ofertas aprobadas y publicadas" in {
    val result = EstudiantePerfilLogic.obtenerOfertasDisponiblesCount()
    result.isRight shouldBe true
    result.getOrElse(-1) should be >= 0
  }
}
