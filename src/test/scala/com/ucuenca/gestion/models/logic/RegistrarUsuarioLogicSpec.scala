package com.ucuenca.gestion.models.logic

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll
import scalikejdbc._
import com.ucuenca.gestion.utils.DatabaseConnection
import com.ucuenca.gestion.models.enums.{RolUsuario, EstadoCuenta, EstadoMatricula, EstadoEstudiantePractica, EstadoConvenio}
import com.ucuenca.gestion.models.dto._
import com.ucuenca.gestion.models.entities.Usuario

class RegistrarUsuarioLogicSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  override def beforeAll(): Unit = {
    DatabaseConnection.initialize()
    cleanup()
    // Asegurar carrera semilla para pruebas
    DB.localTx { implicit session =>
      val exists = sql"SELECT count(*) FROM carrera WHERE id_carrera = 9999".map(rs => rs.long(1)).single.apply().getOrElse(0L) > 0
      if (!exists) {
        sql"INSERT INTO carrera (id_carrera, nombre_carrera) VALUES (9999, 'Carrera Test')".update.apply()
      }
    }
  }

  override def afterAll(): Unit = {
    cleanup()
    DB.localTx { implicit session =>
      sql"DELETE FROM carrera WHERE id_carrera = 9999".update.apply()
    }
  }

  private def cleanup(): Unit = {
    DB.localTx { implicit session =>
      sql"DELETE FROM usuario_sistema WHERE username IN ('test_est', 'test_emp', 'test_tutor', 'test_admin_2')".update.apply()
      sql"DELETE FROM tutor_empresarial_perfil WHERE identificacion IN ('0909090909', '0909090908')".update.apply()
      sql"DELETE FROM empresa_perfil WHERE identificacion IN ('0505050505002', '0505050505003')".update.apply()
      sql"DELETE FROM estudiante_perfil WHERE identificacion IN ('0101010102', '0101010103')".update.apply()
      sql"DELETE FROM usuario WHERE identificacion IN ('0101010102', '0101010103', '0505050505002', '0505050505003', '0909090909', '0909090908', '0101010105')".update.apply()
      sql"DELETE FROM archivo_pdf WHERE nombre_original LIKE 'test_%'".update.apply()
    }
  }

  "RegistrarUsuarioLogic" should "rechazar nombres vacíos o muy cortos" in {
    val dto = EstudianteDTO(
      identificacion = "0101010102",
      nombresCompletos = "",
      correoElectronico = "test@ucuenca.edu.ec",
      password = "password123",
      cicloActual = 5,
      idCarreraRef = 9999,
      estadoMatricula = EstadoMatricula.REGULAR,
      estadoPractica = EstadoEstudiantePractica.SIN_PRACTICA,
      mallaNombre = "test_malla.pdf",
      mallaRuta = "/safe/test_malla.pdf",
      cvNombre = None,
      cvRuta = None
    )
    RegistrarUsuarioLogic.registrarEstudiante(dto) shouldBe Left(RegistroFailure.Validacion("El nombre completo debe tener al menos 3 caracteres."))
  }

  it should "rechazar identificación de estudiante con longitud diferente de 10 dígitos o no numérica" in {
    val dtoShort = EstudianteDTO(
      identificacion = "123",
      nombresCompletos = "Juan Perez",
      correoElectronico = "test@ucuenca.edu.ec",
      password = "password123",
      cicloActual = 5,
      idCarreraRef = 9999,
      estadoMatricula = EstadoMatricula.REGULAR,
      estadoPractica = EstadoEstudiantePractica.SIN_PRACTICA,
      mallaNombre = "test_malla.pdf",
      mallaRuta = "/safe/test_malla.pdf",
      cvNombre = None,
      cvRuta = None
    )
    val dtoAlpha = dtoShort.copy(identificacion = "01010101a2")

    RegistrarUsuarioLogic.registrarEstudiante(dtoShort) shouldBe Left(RegistroFailure.Validacion("La identificación para personas debe tener exactamente 10 dígitos numéricos."))
    RegistrarUsuarioLogic.registrarEstudiante(dtoAlpha) shouldBe Left(RegistroFailure.Validacion("La identificación para personas debe tener exactamente 10 dígitos numéricos."))
  }

  it should "rechazar ciclo de estudiante fuera de los límites 1 a 10" in {
    val dtoLow = EstudianteDTO(
      identificacion = "0101010102",
      nombresCompletos = "Juan Perez",
      correoElectronico = "test@ucuenca.edu.ec",
      password = "password123",
      cicloActual = 0,
      idCarreraRef = 9999,
      estadoMatricula = EstadoMatricula.REGULAR,
      estadoPractica = EstadoEstudiantePractica.SIN_PRACTICA,
      mallaNombre = "test_malla.pdf",
      mallaRuta = "/safe/test_malla.pdf",
      cvNombre = None,
      cvRuta = None
    )
    val dtoHigh = dtoLow.copy(cicloActual = 11)

    RegistrarUsuarioLogic.registrarEstudiante(dtoLow) shouldBe Left(RegistroFailure.Validacion("El ciclo académico debe estar entre 1 y 10."))
    RegistrarUsuarioLogic.registrarEstudiante(dtoHigh) shouldBe Left(RegistroFailure.Validacion("El ciclo académico debe estar entre 1 y 10."))
  }

  it should "rechazar registro de estudiante si falta el PDF de malla" in {
    val dto = EstudianteDTO(
      identificacion = "0101010102",
      nombresCompletos = "Juan Perez",
      correoElectronico = "test@ucuenca.edu.ec",
      password = "password123",
      cicloActual = 5,
      idCarreraRef = 9999,
      estadoMatricula = EstadoMatricula.REGULAR,
      estadoPractica = EstadoEstudiantePractica.SIN_PRACTICA,
      mallaNombre = "",
      mallaRuta = "",
      cvNombre = None,
      cvRuta = None
    )
    RegistrarUsuarioLogic.registrarEstudiante(dto) shouldBe Left(RegistroFailure.Validacion("El archivo PDF de la malla académica es obligatorio."))
  }

  it should "registrar exitosamente un estudiante con perfil y malla académica" in {
    val dto = EstudianteDTO(
      identificacion = "0101010102",
      nombresCompletos = "Juan Perez",
      correoElectronico = "test.est@ucuenca.edu.ec",
      password = "password123",
      cicloActual = 5,
      idCarreraRef = 9999,
      estadoMatricula = EstadoMatricula.REGULAR,
      estadoPractica = EstadoEstudiantePractica.SIN_PRACTICA,
      mallaNombre = "test_malla.pdf",
      mallaRuta = "/safe/test_malla.pdf",
      cvNombre = Some("test_cv.pdf"),
      cvRuta = Some("/safe/test_cv.pdf")
    )
    RegistrarUsuarioLogic.registrarEstudiante(dto) shouldBe Right(())

    // Verificar en la BD
    DB.readOnly { implicit session =>
      val userOpt = sql"SELECT nombres_completos, rol FROM usuario WHERE identificacion = '0101010102'".map(rs => (rs.string(1), rs.string(2))).single.apply()
      userOpt shouldBe Some(("Juan Perez", "ESTUDIANTE"))

      val studentOpt = sql"SELECT ciclo_actual FROM estudiante_perfil WHERE identificacion = '0101010102'".map(rs => rs.int(1)).single.apply()
      studentOpt shouldBe Some(5)
    }
  }

  it should "rechazar identificación de empresa con longitud diferente de 13 dígitos o no numérica" in {
    val dto = EmpresaDTO(
      identificacion = "123456789012",
      nombresCompletos = "Empresa Test S.A.",
      correoElectronico = "contacto@testemp.com",
      password = "password123",
      direccionMatriz = "Av. Principal",
      mision = "Mision",
      vision = "Vision",
      estadoConvenio = EstadoConvenio.PENDIENTE
    )
    RegistrarUsuarioLogic.registrarEmpresa(dto) shouldBe Left(RegistroFailure.Validacion("La identificación RUC para empresas debe tener exactamente 13 dígitos numéricos."))
  }

  it should "registrar exitosamente una empresa" in {
    val dto = EmpresaDTO(
      identificacion = "0505050505002",
      nombresCompletos = "Empresa Test S.A.",
      correoElectronico = "test.emp@ucuenca.edu.ec",
      password = "password123",
      direccionMatriz = "Av. de las Américas, Cuenca",
      mision = "Ofrecer servicios de calidad.",
      vision = "Ser referentes nacionales.",
      estadoConvenio = EstadoConvenio.PENDIENTE
    )
    RegistrarUsuarioLogic.registrarEmpresa(dto) shouldBe Right(())

    DB.readOnly { implicit session =>
      val epOpt = sql"SELECT direccion_matriz, mision, vision FROM empresa_perfil WHERE identificacion = '0505050505002'".map(rs => (rs.string(1), rs.string(2), rs.string(3))).single.apply()
      epOpt.isDefined shouldBe true
      epOpt.get._1 shouldBe "Av. de las Américas, Cuenca"
      epOpt.get._2 should include("Ofrecer servicios de calidad.")
      epOpt.get._3 should include("Ser referentes nacionales.")
    }
  }

  it should "rechazar tutor empresarial si el teléfono no es de 10 dígitos o no numérico" in {
    val dto = TutorEmpresarialDTO(
      identificacion = "0909090909",
      nombresCompletos = "Tutor Test",
      correoElectronico = "tutor@test.com",
      password = "password123",
      empresaIdRef = "0505050505002",
      telefonoContacto = "123"
    )
    RegistrarUsuarioLogic.registrarTutorEmpresarial(dto) shouldBe Left(RegistroFailure.Validacion("El teléfono del tutor empresarial debe tener exactamente 10 dígitos numéricos."))
  }

  it should "registrar exitosamente un tutor empresarial" in {
    val dto = TutorEmpresarialDTO(
      identificacion = "0909090909",
      nombresCompletos = "Tutor Test",
      correoElectronico = "test.tutor@ucuenca.edu.ec",
      password = "password123",
      empresaIdRef = "0505050505002",
      telefonoContacto = "0999999999"
    )
    RegistrarUsuarioLogic.registrarTutorEmpresarial(dto) shouldBe Right(())

    DB.readOnly { implicit session =>
      val tutorOpt = sql"SELECT telefono_contacto FROM tutor_empresarial_perfil WHERE identificacion = '0909090909'".map(rs => rs.string(1)).single.apply()
      tutorOpt shouldBe Some("0999999999")
    }
  }

  it should "registrar exitosamente un usuario general (e.g. ADMIN)" in {
    val dto = UsuarioGeneralDTO(
      identificacion = "0101010105",
      nombresCompletos = "Administrador Dos",
      correoElectronico = "test.admin_2@ucuenca.edu.ec",
      password = "password123",
      rol = RolUsuario.ADMIN
    )
    RegistrarUsuarioLogic.registrarUsuarioGeneral(dto) shouldBe Right(())

    DB.readOnly { implicit session =>
      val userOpt = sql"SELECT nombres_completos FROM usuario WHERE identificacion = '0101010105'".map(rs => rs.string(1)).single.apply()
      userOpt shouldBe Some("Administrador Dos")
    }
  }
}
