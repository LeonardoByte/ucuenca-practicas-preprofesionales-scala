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

  // Identificaciones válidas según el algoritmo oficial ecuatoriano (Módulo 10 / Módulo 11)
  private val ciEstudiante = "0101010106"
  private val ciAdmin = "0202020202"
  private val ciTutor = "0909090904"
  private val rucEmpresa = "0190506002001"

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
      sql"DELETE FROM tutor_empresarial_perfil WHERE identificacion = ${ciTutor}".update.apply()
      sql"DELETE FROM empresa_perfil WHERE identificacion = ${rucEmpresa}".update.apply()
      sql"DELETE FROM estudiante_perfil WHERE identificacion = ${ciEstudiante}".update.apply()
      sql"DELETE FROM usuario WHERE identificacion IN (${ciEstudiante}, ${rucEmpresa}, ${ciTutor}, ${ciAdmin})".update.apply()
      sql"DELETE FROM archivo_pdf WHERE nombre_original LIKE 'test_%'".update.apply()
    }
  }

  "RegistrarUsuarioLogic" should "rechazar nombres vacíos o muy cortos" in {
    val dto = EstudianteDTO(
      identificacion = ciEstudiante,
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

    RegistrarUsuarioLogic.registrarEstudiante(dtoShort) shouldBe Left(RegistroFailure.Validacion("La cédula de identidad ingresada no es válida. Verifique los 10 dígitos."))
    RegistrarUsuarioLogic.registrarEstudiante(dtoAlpha) shouldBe Left(RegistroFailure.Validacion("La cédula de identidad ingresada no es válida. Verifique los 10 dígitos."))
  }

  it should "rechazar identificación de estudiante con 10 dígitos numéricos pero dígito verificador inválido" in {
    // "0101010101" tiene el formato correcto (10 dígitos, provincia y tercer dígito válidos)
    // pero su dígito verificador (Módulo 10) no coincide: el correcto es 6, no 1.
    val dto = EstudianteDTO(
      identificacion = "0101010101",
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
    RegistrarUsuarioLogic.registrarEstudiante(dto) shouldBe Left(RegistroFailure.Validacion("La cédula de identidad ingresada no es válida. Verifique los 10 dígitos."))
  }

  it should "rechazar ciclo de estudiante fuera de los límites 1 a 10" in {
    val dtoLow = EstudianteDTO(
      identificacion = ciEstudiante,
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
      identificacion = ciEstudiante,
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
      identificacion = ciEstudiante,
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
      val userOpt = sql"SELECT nombres_completos, rol FROM usuario WHERE identificacion = ${ciEstudiante}".map(rs => (rs.string(1), rs.string(2))).single.apply()
      userOpt shouldBe Some(("Juan Perez", "ESTUDIANTE"))

      val studentOpt = sql"SELECT ciclo_actual FROM estudiante_perfil WHERE identificacion = ${ciEstudiante}".map(rs => rs.int(1)).single.apply()
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
    RegistrarUsuarioLogic.registrarEmpresa(dto) shouldBe Left(RegistroFailure.Validacion("El RUC ingresado no es válido. Verifique los 13 dígitos y el dígito verificador."))
  }

  it should "rechazar identificación de empresa con 13 dígitos pero dígito verificador inválido" in {
    // Mismo prefijo que el RUC válido de empresa, pero con el dígito verificador alterado.
    val dto = EmpresaDTO(
      identificacion = "0190506003001",
      nombresCompletos = "Empresa Test S.A.",
      correoElectronico = "contacto@testemp.com",
      password = "password123",
      direccionMatriz = "Av. Principal",
      mision = "Mision",
      vision = "Vision",
      estadoConvenio = EstadoConvenio.PENDIENTE
    )
    RegistrarUsuarioLogic.registrarEmpresa(dto) shouldBe Left(RegistroFailure.Validacion("El RUC ingresado no es válido. Verifique los 13 dígitos y el dígito verificador."))
  }

  it should "registrar exitosamente una empresa" in {
    val dto = EmpresaDTO(
      identificacion = rucEmpresa,
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
      val epOpt = sql"SELECT direccion_matriz, mision, vision FROM empresa_perfil WHERE identificacion = ${rucEmpresa}".map(rs => (rs.string(1), rs.string(2), rs.string(3))).single.apply()
      epOpt.isDefined shouldBe true
      epOpt.get._1 shouldBe "Av. de las Américas, Cuenca"
      epOpt.get._2 should include("Ofrecer servicios de calidad.")
      epOpt.get._3 should include("Ser referentes nacionales.")
    }
  }

  it should "rechazar tutor empresarial si el teléfono no es de 10 dígitos o no numérico" in {
    val dto = TutorEmpresarialDTO(
      identificacion = ciTutor,
      nombresCompletos = "Tutor Test",
      correoElectronico = "tutor@test.com",
      password = "password123",
      empresaIdRef = rucEmpresa,
      telefonoContacto = "123"
    )
    RegistrarUsuarioLogic.registrarTutorEmpresarial(dto) shouldBe Left(RegistroFailure.Validacion("El teléfono del tutor empresarial debe tener exactamente 10 dígitos numéricos."))
  }

  it should "registrar exitosamente un tutor empresarial" in {
    val dto = TutorEmpresarialDTO(
      identificacion = ciTutor,
      nombresCompletos = "Tutor Test",
      correoElectronico = "test.tutor@ucuenca.edu.ec",
      password = "password123",
      empresaIdRef = rucEmpresa,
      telefonoContacto = "0999999999"
    )
    RegistrarUsuarioLogic.registrarTutorEmpresarial(dto) shouldBe Right(())

    DB.readOnly { implicit session =>
      val tutorOpt = sql"SELECT telefono_contacto FROM tutor_empresarial_perfil WHERE identificacion = ${ciTutor}".map(rs => rs.string(1)).single.apply()
      tutorOpt shouldBe Some("0999999999")
    }
  }

  it should "registrar exitosamente un usuario general (e.g. ADMIN)" in {
    val dto = UsuarioGeneralDTO(
      identificacion = ciAdmin,
      nombresCompletos = "Administrador Dos",
      correoElectronico = "test.admin_2@ucuenca.edu.ec",
      password = "password123",
      rol = RolUsuario.ADMIN
    )
    RegistrarUsuarioLogic.registrarUsuarioGeneral(dto) shouldBe Right(())

    DB.readOnly { implicit session =>
      val userOpt = sql"SELECT nombres_completos FROM usuario WHERE identificacion = ${ciAdmin}".map(rs => rs.string(1)).single.apply()
      userOpt shouldBe Some("Administrador Dos")
    }
  }
}
