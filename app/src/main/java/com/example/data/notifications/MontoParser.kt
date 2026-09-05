package com.example.data.notifications

/**
 * Extrae **el monto y nada más** del texto de la notificación de una billetera.
 *
 * Es todo lo que la app lee de esas notificaciones: ni comercio, ni contraparte, ni si es gasto o
 * ingreso, ni categoría. Eso no es una limitación a superar más adelante, es el diseño: al parsear
 * un solo número, lo único que tiene que sobrevivir a un cambio de copy del banco es que siga
 * escribiendo el importe con signo de peso. Un único parser sirve para los cuatro bancos y para los
 * que vengan, sin reglas de clasificación ni listas de palabras clave por entidad.
 *
 * Es puro a propósito (sin Android, sin estado): la lógica frágil de todo esto es el parseo, y así
 * se prueba entera en `MontoParserTest` sin emulador.
 */
object MontoParser {

    /**
     * Formato argentino: punto de miles y coma decimal. Dos alternativas, en orden:
     *
     *  - `\d{1,3}(\.\d{3})+` — con separador de miles (`1.234`, `12.345.678`). Se exige el grupo de
     *    **exactamente** tres dígitos para no confundir un `$5.00` con cinco pesos.
     *  - `\d+` — sin separador (`5000`).
     *
     * y en las dos, decimales opcionales con coma (`,50`, `,5`).
     */
    private val REGEX_MONTO = Regex("""\$\s*((?:\d{1,3}(?:\.\d{3})+|\d+)(?:,\d{1,2})?)""")

    /**
     * Si alguna de estas palabras aparece justo antes del importe, no es el movimiento: es el saldo
     * que el banco agrega al final del mensaje ("Pagaste $5.000 · saldo disponible $12.300").
     * Se comparan sin acentos para no depender de cómo lo escriba cada entidad.
     */
    private val PALABRAS_DE_CONTEXTO = listOf(
        "saldo", "disponible", "limite", "cupo", "restante", "total acumulado"
    )

    /** Cuántos caracteres antes del `$` se miran para decidir si el importe es un saldo. */
    private const val VENTANA_CONTEXTO = 32

    /**
     * El primer importe del texto que no sea un saldo, o `null` si no hay ninguno.
     *
     * `null` es la respuesta correcta y frecuente: ante la duda no se notifica nada. Un falso
     * positivo cuesta más que un silencio, porque el usuario igual va a tipear el monto a mano.
     */
    fun primerMonto(texto: String?): Double? {
        if (texto.isNullOrBlank()) return null
        for (match in REGEX_MONTO.findAll(texto)) {
            if (esSaldo(texto, match.range.first)) continue
            val valor = aDouble(match.groupValues[1]) ?: continue
            if (valor > 0.0) return valor
        }
        return null
    }

    /** El monto formateado como lo espera el campo del alta: dígitos con punto decimal. */
    fun primerMontoComoTexto(texto: String?): String? {
        val monto = primerMonto(texto) ?: return null
        return if (monto % 1.0 == 0.0) monto.toLong().toString() else monto.toString()
    }

    private fun esSaldo(texto: String, posicionDelSigno: Int): Boolean {
        val desde = (posicionDelSigno - VENTANA_CONTEXTO).coerceAtLeast(0)
        val contexto = sinAcentos(texto.substring(desde, posicionDelSigno).lowercase())
        return PALABRAS_DE_CONTEXTO.any { contexto.contains(it) }
    }

    private fun sinAcentos(s: String): String = s
        .replace('á', 'a').replace('é', 'e').replace('í', 'i')
        .replace('ó', 'o').replace('ú', 'u')

    private fun aDouble(crudo: String): Double? =
        crudo.replace(".", "").replace(',', '.').toDoubleOrNull()
}
