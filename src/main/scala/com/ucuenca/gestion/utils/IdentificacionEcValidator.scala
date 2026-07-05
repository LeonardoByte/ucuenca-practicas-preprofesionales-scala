package com.ucuenca.gestion.utils

object IdentificacionEcValidator {

  /**
   * Valida una cédula de identidad ecuatoriana (10 dígitos).
   * Aplica el algoritmo de Módulo 10 sobre los primeros 9 dígitos.
   */
  def esCedulaValida(cedula: String): Boolean = {
    // Validación de formato básico: no nula, longitud 10 y solo dígitos
    if (cedula == null || cedula.length != 10 || !cedula.forall(_.isDigit)) return false

    val d = cedula.map(_.asDigit)
    val provincia = d(0) * 10 + d(1)
    val esCodigo30 = provincia == 30

    // Validación de provincia (01 a 24) o código consular (30)
    if ((provincia < 1 || provincia > 24) && !esCodigo30) return false

    // El tercer dígito debe ser menor o igual a 5 (Persona natural)
    if (d(2) > 5 && !esCodigo30) return false

    // Módulo 10 sobre los primeros 9 dígitos. El verificador está en d(9)
    modulo10(d.take(9)) == d(9)
  }

  /**
   * Valida un RUC ecuatoriano (13 dígitos) según sus tres tipos de estructura:
   * 1. Persona Natural (Tercer dígito < 6)
   * 2. Institución Pública (Tercer dígito = 6)
   * 3. Sociedad Privada / Extranjero sin cédula (Tercer dígito = 9)
   */
  def esRucValido(ruc: String): Boolean = {
    // Validación de formato básico: no nulo, longitud 13 y solo dígitos
    if (ruc == null || ruc.length != 13 || !ruc.forall(_.isDigit)) return false

    val d = ruc.map(_.asDigit)
    val provincia = d(0) * 10 + d(1)
    val esCodigo30 = provincia == 30

    // Validación de provincia (01 a 24) o código consular (30)
    if ((provincia < 1 || provincia > 24) && !esCodigo30) return false

    // El sufijo de establecimiento comercial nunca puede ser 000
    if (ruc.substring(10) == "000") return false

    d(2) match {
      case tercerDigito if DocsNatural(tercerDigito, esCodigo30) =>
        // Persona natural: Módulo 10 sobre 9 dígitos. Verificador en d(9).
        modulo10(d.take(9)) == d(9)

      case 6 =>
        // Inst. Pública: Módulo 11 sobre 8 dígitos. Coeficientes (3,2,7,6,5,4,3,2). Verificador en d(8).
        modulo11(d.take(8), Seq(3, 2, 7, 6, 5, 4, 3, 2)) == d(8)

      case 9 =>
        // Soc. Privada: Módulo 11 sobre 9 dígitos. Coeficientes (4,3,2,7,6,5,4,3,2). Verificador en d(9).
        modulo11(d.take(9), Seq(4, 3, 2, 7, 6, 5, 4, 3, 2)) == d(9)

      case _ =>
        false
    }
  }

  private def DocsNatural(tercerDigito: Int, esCodigo30: Boolean): Boolean = {
    tercerDigito <= 5 || esCodigo30
  }

  /**
   * Algoritmo de Módulo 10 (Algoritmo de Luhn modificado para Ecuador).
   * Multiplica posiciones impares por 2 (y resta 9 si el producto >= 10) y pares por 1.
   */
  private def modulo10(digitos: Seq[Int]): Int = {
    val coeficientes = Seq(2, 1, 2, 1, 2, 1, 2, 1, 2)
    val suma = digitos.zip(coeficientes).map { case (digito, coef) =>
      val producto = digito * coef
      if (producto >= 10) producto - 9 else producto
    }.sum
    val resto = suma % 10
    if (resto == 0) 0 else 10 - resto
  }

  /**
   * Algoritmo de Módulo 11 adaptado a la normativa tributaria del SRI.
   * Si el residuo es 0 o 1, el dígito verificador asignado por el SRI es obligatoriamente 0.
   */
  private def modulo11(digitos: Seq[Int], coeficientes: Seq[Int]): Int = {
    val suma = digitos.zip(coeficientes).map { case (digito, coef) => digito * coef }.sum
    val residuo = suma % 11

    // Regla oficial del SRI: Si residuo es 0 o 1, el dígito es 0. De lo contrario, 11 - residuo.
    if (residuo == 0 || residuo == 1) 0
    else 11 - residuo
  }
}