package com.ucuenca.gestion.models.logic

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll
import scalikejdbc._
import com.ucuenca.gestion.utils.{DatabaseConnection, PasswordHasher}
import com.ucuenca.gestion.models.enums.{RolUsuario, EstadoCuenta}
import com.ucuenca.gestion.models.entities.Usuario

class AutenticacionLogicSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  override def beforeAll(): Unit = {
    // Inicializar conexión a la base de datos local
    DatabaseConnection.initialize()
    
    // Limpiar e insertar datos de prueba
    DB.localTx { implicit session =>
      sql"DELETE FROM usuario_sistema WHERE username IN ('test_admin', 'test_suspendido', 'test_user')".update.apply()
      sql"DELETE FROM usuario WHERE identificacion IN ('0102030405001', '0102030405002', '0102030405003')".update.apply()

      // 1. Admin activo
      sql"""
        INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta)
        VALUES ('0102030405001', 'Administrador de Prueba', 'admin@test.com', 'ADMIN'::rol_usuario, 'ACTIVA'::estado_cuenta)
      """.update.apply()
      
      sql"""
        INSERT INTO usuario_sistema (username, password_hash, identificacion_usuario_ref)
        VALUES ('test_admin', ${PasswordHasher.hash("admin123")}, '0102030405001')
      """.update.apply()

      // 2. Estudiante suspendido
      sql"""
        INSERT INTO usuario (identificacion, nombres_completos, correo_electronico, rol, estado_cuenta)
        VALUES ('0102030405002', 'Estudiante Suspendido', 'suspendido@test.com', 'ESTUDIANTE'::rol_usuario, 'SUSPENDIDA'::estado_cuenta)
      """.update.apply()

      sql"""
        INSERT INTO usuario_sistema (username, password_hash, identificacion_usuario_ref)
        VALUES ('test_suspendido', ${PasswordHasher.hash("stud123")}, '0102030405002')
      """.update.apply()
    }
  }

  override def afterAll(): Unit = {
    DB.localTx { implicit session =>
      sql"DELETE FROM usuario_sistema WHERE username IN ('test_admin', 'test_suspendido', 'test_user')".update.apply()
      sql"DELETE FROM usuario WHERE identificacion IN ('0102030405001', '0102030405002', '0102030405003')".update.apply()
    }
  }

  "AutenticacionLogic" should "rechazar campos vacíos" in {
    AutenticacionLogic.autenticar("", "") shouldBe Left(AutenticacionFailure.CamposVacios)
    AutenticacionLogic.autenticar("test_admin", "") shouldBe Left(AutenticacionFailure.CamposVacios)
    AutenticacionLogic.autenticar("", "admin123") shouldBe Left(AutenticacionFailure.CamposVacios)
  }

  it should "rechazar usuarios inexistentes" in {
    AutenticacionLogic.autenticar("no_existe", "clave123") shouldBe Left(AutenticacionFailure.CredencialesIncorrectas)
  }

  it should "rechazar contraseñas incorrectas" in {
    AutenticacionLogic.autenticar("test_admin", "incorrecta") shouldBe Left(AutenticacionFailure.CredencialesIncorrectas)
  }

  it should "rechazar cuentas suspendidas" in {
    AutenticacionLogic.autenticar("test_suspendido", "stud123") shouldBe Left(AutenticacionFailure.CuentaSuspendida)
  }

  it should "autenticar exitosamente un usuario activo" in {
    val result = AutenticacionLogic.autenticar("test_admin", "admin123")
    result.isRight shouldBe true
    val usuario = result.toOption.get
    usuario.identificacion shouldBe "0102030405001"
    usuario.rol shouldBe RolUsuario.ADMIN
    usuario.estadoCuenta shouldBe EstadoCuenta.ACTIVA
  }
}
