package com.example

import com.example.ui.CuotaDraft
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fija la semántica de [CuotaDraft.tieneContenido], que decide si al volver a la pestaña Cuotas se
 * reabre el formulario de "nueva compra". Un formulario recién abierto (solo defaults de categoría/
 * mes) NO debe considerarse "con contenido" para no reabrirse solo.
 */
class CuotaDraftTest {

    @Test
    fun borradorVacioNoTieneContenido() {
        assertFalse(CuotaDraft().tieneContenido)
    }

    @Test
    fun categoriaYMesPorDefectoNoCuentanComoContenido() {
        assertFalse(CuotaDraft(categoria = "Transporte", primeraCuota = "2026-07").tieneContenido)
    }

    @Test
    fun descripcionMontoCantidadOTarjetaSiCuentan() {
        assertTrue(CuotaDraft(descripcion = "Notebook").tieneContenido)
        assertTrue(CuotaDraft(montoText = "1000").tieneContenido)
        assertTrue(CuotaDraft(cantidadText = "12").tieneContenido)
        assertTrue(CuotaDraft(tarjeta = "Visa Santiago").tieneContenido)
    }
}
