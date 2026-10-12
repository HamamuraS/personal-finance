package com.example.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

/** Formato de montos de toda la app: "$ 1.234,56" y "-$ 1.234,56". */
object Formato {

    /** Un formateador nuevo (`DecimalFormat` no es thread-safe: no se comparte entre hilos). */
    fun dinero(): DecimalFormat = DecimalFormat("#,##0.00").apply {
        decimalFormatSymbols = DecimalFormatSymbols().apply {
            groupingSeparator = '.'
            decimalSeparator = ','
        }
        positivePrefix = "$ "
        negativePrefix = "-$ "
    }

    /**
     * Un monto como lo espera un campo de texto de monto: dígitos, punto decimal y a lo sumo dos
     * decimales, sin ceros de más ("150000", "123.4"). Nunca en notación científica: `Double.toString`
     * escribía "1.23456785E7" para montos desde diez millones.
     */
    fun montoParaInput(valor: Double): String =
        BigDecimal.valueOf(valor)
            .setScale(2, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()

    /**
     * Filtro de lo que se tipea en un campo de monto: acepta la coma como separador decimal y devuelve
     * el texto normalizado (con punto), o null si no es un monto válido. [maxDecimales] null = sin
     * límite de decimales.
     */
    fun filtrarMonto(input: String, maxDecimales: Int? = 2): String? {
        val normalizado = input.replace(',', '.')
        if (!normalizado.all { it.isDigit() || it == '.' } || normalizado.count { it == '.' } > 1) return null
        if (maxDecimales != null) {
            val partes = normalizado.split(".")
            if (partes.size > 1 && partes[1].length > maxDecimales) return null
        }
        return normalizado
    }
}
