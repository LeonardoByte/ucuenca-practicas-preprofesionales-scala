package com.ucuenca.gestion.models.logic

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SmokeTest extends AnyFlatSpec with Matchers {
  "El entorno de pruebas" should "compilar y ejecutar especificaciones con ScalaTest" in {
    val a = 1
    val b = 1
    a shouldBe b
  }
}
