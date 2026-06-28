package com.ucuenca.gestion.models.logic

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll
import scalikejdbc._
import com.ucuenca.gestion.utils.DatabaseConnection
import com.ucuenca.gestion.models.enums.{RolUsuario, EstadoCuenta}
import com.ucuenca.gestion.models.entities.Usuario

class DirectorioLogicSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  override def beforeAll(): Unit = {
    DatabaseConnection.initialize()
    cleanup()

    // Sembrar usuarios de prueba
    DB.localTx { implicit session =>
      sql"""
        INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta)
        VALUES ('0101019901', 'Directorio User Uno', 'dir1@test.com', 'ADMIN'::rol_usuario, 'ACTIVA'::estado_cuenta)
      """.update.apply()

      sql"""
        INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta)
        VALUES ('0101019902', 'Directorio User Dos', 'dir2@test.com', 'ESTUDIANTE'::rol_usuario, 'SUSPENDIDA'::estado_cuenta)
      """.update.apply()

      sql"""
        INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta)
        VALUES ('0101019903', 'Empresa Test Tres', 'dir3@test.com', 'EMPRESA'::rol_usuario, 'ACTIVA'::estado_cuenta)
      """.update.apply()
    }
  }

  override def afterAll(): Unit = {
    cleanup()
  }

  private def cleanup(): Unit = {
    DB.localTx { implicit session =>
      sql"DELETE FROM usuario WHERE identificacion IN ('0101019901', '0101019902', '0101019903')".update.apply()
    }
  }

  "DirectorioLogic" should "listar todos los usuarios si los filtros están vacíos" in {
    val result = DirectorioLogic.buscarUsuarios("", null, null)
    result.isRight shouldBe true
    val lista = result.toOption.get
    lista.exists(_.identificacion == "0101019901") shouldBe true
    lista.exists(_.identificacion == "0101019902") shouldBe true
    lista.exists(_.identificacion == "0101019903") shouldBe true
  }

  it should "filtrar por texto libre (nombre parcial, identificacion o correo)" in {
    // Buscar por nombre parcial
    val porNombre = DirectorioLogic.buscarUsuarios("Dos", null, null).toOption.get
    porNombre.size shouldBe 1
    porNombre.head.identificacion shouldBe "0101019902"

    // Buscar por identificación parcial
    val porId = DirectorioLogic.buscarUsuarios("19903", null, null).toOption.get
    porId.size shouldBe 1
    porId.head.identificacion shouldBe "0101019903"

    // Buscar por correo parcial
    val porCorreo = DirectorioLogic.buscarUsuarios("dir1@", null, null).toOption.get
    porCorreo.size shouldBe 1
    porCorreo.head.identificacion shouldBe "0101019901"
  }

  it should "filtrar por rol y estado de cuenta simultáneamente" in {
    val porRolYEstado = DirectorioLogic.buscarUsuarios("", RolUsuario.EMPRESA, EstadoCuenta.ACTIVA).toOption.get
    porRolYEstado.exists(_.identificacion == "0101019903") shouldBe true
    porRolYEstado.forall(_.rol == RolUsuario.EMPRESA) shouldBe true
    porRolYEstado.forall(_.estadoCuenta == EstadoCuenta.ACTIVA) shouldBe true
  }

  it should "actualizar correctamente los datos de perfil con validaciones" in {
    // Validar nombre corto
    DirectorioLogic.actualizarContacto("0101019901", "Ab", "cambiado@test.com") shouldBe Left(DirectorioFailure.Validacion("El nombre completo debe tener al menos 3 caracteres."))
    
    // Validar email inválido
    DirectorioLogic.actualizarContacto("0101019901", "Nombre Nuevo", "email_invalido") shouldBe Left(DirectorioFailure.Validacion("El correo electrónico no es válido."))

    // Actualización exitosa
    val actualizacion = DirectorioLogic.actualizarContacto("0101019901", "Nombre Modificado", "cambiado@test.com")
    actualizacion shouldBe Right(())

    DB.readOnly { implicit session =>
      val user = sql"SELECT nombres_completos, correo_electronico FROM usuario WHERE identificacion = '0101019901'"
        .map(rs => (rs.string(1), rs.string(2))).single.apply().get
      user._1 shouldBe "Nombre Modificado"
      user._2 shouldBe "cambiado@test.com"
    }
  }

  it should "alternar el estado de cuenta (suspensión lógica) de forma persistente" in {
    // Suspender cuenta activa
    val suspender = DirectorioLogic.cambiarEstado("0101019903", EstadoCuenta.SUSPENDIDA)
    suspender shouldBe Right(EstadoCuenta.SUSPENDIDA)

    DB.readOnly { implicit session =>
      val estado = sql"SELECT estado_cuenta FROM usuario WHERE identificacion = '0101019903'"
        .map(rs => rs.string(1)).single.apply().get
      estado shouldBe "SUSPENDIDA"
    }

    // Activar cuenta suspendida
    val activar = DirectorioLogic.cambiarEstado("0101019903", EstadoCuenta.ACTIVA)
    activar shouldBe Right(EstadoCuenta.ACTIVA)

    DB.readOnly { implicit session =>
      val estado = sql"SELECT estado_cuenta FROM usuario WHERE identificacion = '0101019903'"
        .map(rs => rs.string(1)).single.apply().get
      estado shouldBe "ACTIVA"
    }
  }
}
