package com.example

import com.example.data.Movement
import com.example.ui.AccountingEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Atribución de un gasto a una persona: de quién ES la plata, no de qué cuenta salió.
 *
 * Es la regla que Métricas y el filtro por persona de Inicio venían resolviendo por su cuenta
 * mirando `responsable`, y por eso un gasto de Rocío pagado desde la cuenta de Santiago aparecía
 * como gasto de Santiago. Ahora las dos pantallas llaman a [AccountingEngine], que ya era la única
 * fuente de verdad para los saldos.
 */
class AtribucionGastosTest {

    private val delta = 0.001

    private fun gasto(
        monto: Double,
        responsable: String,
        propietario: String,
        esComun: Boolean = false,
        categoria: String = "Animales"
    ) = Movement(
        fecha = "2026-08-15 12:45", monto = monto, tipo = "Gasto", categoria = categoria,
        responsable = responsable, propietario = propietario, esComun = esComun,
        metodoPago = "Billetera Virtual"
    )

    @Test
    fun gastoCruzadoSeAtribuyeAlPropietarioNoAlResponsable() {
        // Caso real de la planilla: análisis de sangre de 318.200 pagado por Santiago, pero es de Rocío.
        val m = gasto(318200.0, responsable = "Santiago", propietario = "Rocío")

        assertEquals(0.0, AccountingEngine.porcionDelGasto(m, "Santiago"), delta)
        assertEquals(318200.0, AccountingEngine.porcionDelGasto(m, "Rocío"), delta)
        assertFalse(AccountingEngine.perteneceA(m, "Santiago"))
        assertTrue(AccountingEngine.perteneceA(m, "Rocío"))
    }

    @Test
    fun coincideConElGastoPersonalQueCalculaElMotor() {
        // La atribución de las tarjetas de Métricas tiene que dar lo mismo que los saldos de Inicio.
        val movs = listOf(
            gasto(318200.0, responsable = "Santiago", propietario = "Rocío"),
            gasto(53867.42, responsable = "Santiago", propietario = "Rocío", categoria = "Supermercado"),
            gasto(43602.0, responsable = "Santiago", propietario = "Santiago", categoria = "Alimentos frescos")
        )
        val b = AccountingEngine.compute(movs)

        assertEquals(
            b.santiagoGastosPersonales,
            movs.sumOf { AccountingEngine.porcionDelGasto(it, "Santiago") },
            delta
        )
        assertEquals(
            b.rocioGastosPersonales,
            movs.sumOf { AccountingEngine.porcionDelGasto(it, "Rocío") },
            delta
        )
    }

    @Test
    fun gastoComunSePartePorLaMitadEntreLosDos() {
        val m = gasto(40000.0, responsable = "Rocío", propietario = "Ambos", esComun = true)

        assertEquals(20000.0, AccountingEngine.porcionDelGasto(m, "Santiago"), delta)
        assertEquals(20000.0, AccountingEngine.porcionDelGasto(m, "Rocío"), delta)
        // Un gasto común es de los dos: aparece filtre quien filtre.
        assertTrue(AccountingEngine.perteneceA(m, "Santiago"))
        assertTrue(AccountingEngine.perteneceA(m, "Rocío"))
    }

    @Test
    fun esComunSinPropietarioAmbosTambienSeParte() {
        // Dato viejo posible: marcado como común pero con propietario personal.
        val m = gasto(10000.0, responsable = "Santiago", propietario = "Santiago", esComun = true)

        assertEquals(5000.0, AccountingEngine.porcionDelGasto(m, "Santiago"), delta)
        assertEquals(5000.0, AccountingEngine.porcionDelGasto(m, "Rocío"), delta)
    }

    @Test
    fun lasDosPersonasSiempreSumanElTotal() {
        // Invariante de Métricas: tarjeta de Santiago + tarjeta de Rocío = tarjeta combinada.
        val movs = listOf(
            gasto(318200.0, responsable = "Santiago", propietario = "Rocío"),
            gasto(40000.0, responsable = "Rocío", propietario = "Ambos", esComun = true),
            gasto(5000.0, responsable = "Santiago", propietario = "Santiago"),
            gasto(1234.56, responsable = "Rocío", propietario = "Rocío")
        )
        val combinado = movs.sumOf { it.monto }
        val porPersona = movs.sumOf { AccountingEngine.porcionDelGasto(it, "Santiago") } +
            movs.sumOf { AccountingEngine.porcionDelGasto(it, "Rocío") }

        assertEquals(combinado, porPersona, delta)
    }

    @Test
    fun propietarioVacioCaeAlResponsable() {
        // Filas viejas de la planilla no tenían columna Propietario.
        val m = gasto(9000.0, responsable = "Rocío", propietario = "")

        assertEquals("Rocío", AccountingEngine.propietarioDe(m))
        assertEquals(9000.0, AccountingEngine.porcionDelGasto(m, "Rocío"), delta)
        assertEquals(0.0, AccountingEngine.porcionDelGasto(m, "Santiago"), delta)
    }
}
