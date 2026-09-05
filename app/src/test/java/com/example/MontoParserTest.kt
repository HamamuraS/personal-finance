package com.example

import com.example.data.notifications.MontoParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * El parser es la única pieza frágil de la detección de montos: todo lo demás (allowlist, dedup,
 * notificación) es plomería. Por eso es puro y por eso se prueba con los textos que efectivamente
 * mandan las billeteras, en formato argentino (punto de miles, coma decimal).
 */
class MontoParserTest {

    @Test
    fun leeElFormatoArgentino() {
        assertEquals(1234.56, MontoParser.primerMonto("Pagaste $1.234,56")!!, 0.001)
        assertEquals(5000.0, MontoParser.primerMonto("Pagaste $5.000")!!, 0.001)
        assertEquals(5000.0, MontoParser.primerMonto("Pagaste $5000")!!, 0.001)
        assertEquals(12345678.9, MontoParser.primerMonto("Transferencia por $12.345.678,90")!!, 0.001)
        assertEquals(99.5, MontoParser.primerMonto("Consumo de $99,5")!!, 0.001)
    }

    @Test
    fun toleraElEspacioYElPrefijoDeMoneda() {
        assertEquals(1500.0, MontoParser.primerMonto("Se acreditaron $ 1.500 en tu cuenta")!!, 0.001)
        assertEquals(1500.0, MontoParser.primerMonto("Débito por AR$1.500")!!, 0.001)
    }

    @Test
    fun descartaElSaldoYSeQuedaConElMovimiento() {
        // El caso real: el banco mete el saldo en el mismo mensaje y siempre después del importe.
        assertEquals(
            5000.0,
            MontoParser.primerMonto("Pagaste $5.000 · saldo disponible $12.300")!!,
            0.001
        )
        assertEquals(
            2500.0,
            MontoParser.primerMonto("Compra por $2.500. Límite disponible: $80.000")!!,
            0.001
        )
        // Sin acentos: cada entidad escribe "límite" como quiere.
        assertEquals(
            2500.0,
            MontoParser.primerMonto("Compra por $2.500. Limite restante $80.000")!!,
            0.001
        )
    }

    @Test
    fun siSoloHayUnSaldoNoDevuelveNada() {
        // Notificación puramente informativa: no hubo movimiento, no hay que ofrecer un alta.
        assertNull(MontoParser.primerMonto("Tu saldo disponible es $12.300"))
    }

    @Test
    fun silencioAntesQueFalsoPositivo() {
        assertNull(MontoParser.primerMonto("Tenés un mensaje nuevo"))
        assertNull(MontoParser.primerMonto(""))
        assertNull(MontoParser.primerMonto(null))
        // Un número sin signo de peso no es un importe: puede ser un código, una fecha, un CBU.
        assertNull(MontoParser.primerMonto("Tu código es 123456"))
        // Cero no es un movimiento que valga la pena ofrecer.
        assertNull(MontoParser.primerMonto("Operación por $0"))
    }

    @Test
    fun noConfundeUnDecimalConSeparadorDeMiles() {
        // Los tres dígitos del grupo son obligatorios: `$5.00` no son cinco mil.
        assertEquals(5.0, MontoParser.primerMonto("Pagaste $5.00")!!, 0.001)
    }

    @Test
    fun elTextoParaElFormularioNoArrastraDecimalesInventados() {
        // El campo del alta espera dígitos con punto decimal, y un importe redondo va sin `.0`.
        assertEquals("5000", MontoParser.primerMontoComoTexto("Pagaste $5.000"))
        assertEquals("1234.56", MontoParser.primerMontoComoTexto("Pagaste $1.234,56"))
        assertNull(MontoParser.primerMontoComoTexto("Tenés un mensaje nuevo"))
    }
}
