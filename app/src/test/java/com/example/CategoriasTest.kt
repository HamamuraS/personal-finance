package com.example

import com.example.data.Categorias
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El catálogo de categorías es único: el alta de movimientos y el alta de cuotas leen la misma
 * lista. Antes estaba duplicada literalmente en las dos pantallas y se desincronizaba sola.
 */
class CategoriasTest {

    @Test
    fun consumoInmediatoVaEntreVerduleriaYAlimentosFrescos() {
        val i = Categorias.GASTOS.indexOf("Consumo inmediato")
        assertEquals(Categorias.GASTOS.indexOf("Verdulería") + 1, i)
        assertEquals(Categorias.GASTOS.indexOf("Alimentos frescos") - 1, i)
    }

    @Test
    fun cuotasUsaElMismoSetQueLosGastos() {
        assertEquals(Categorias.GASTOS, Categorias.CUOTAS)
    }

    @Test
    fun cadaTipoResuelveSuListaYSuDefault() {
        assertEquals(Categorias.APORTES, Categorias.deTipo("Aporte"))
        assertEquals(Categorias.TRANSFERENCIAS, Categorias.deTipo("Transferencia"))
        assertEquals(Categorias.GASTOS, Categorias.deTipo("Gasto"))
        // Tipo desconocido: cae a gastos en vez de romper.
        assertEquals(Categorias.GASTOS, Categorias.deTipo("Cualquiera"))

        assertEquals("Sueldo", Categorias.defaultDeTipo("Aporte"))
        assertEquals("Ajuste", Categorias.defaultDeTipo("Transferencia"))
        assertEquals("Transporte", Categorias.defaultDeTipo("Gasto"))
    }

    @Test
    fun otrosEsSiempreElUltimoRecursoDeCadaLista() {
        // Las categorías nuevas se agregan antes de "Otros", que es el cajón de sastre.
        listOf(Categorias.GASTOS, Categorias.APORTES, Categorias.TRANSFERENCIAS).forEach { lista ->
            assertEquals("Otros", lista.last())
        }
        assertEquals(Categorias.GASTOS.size - 2, Categorias.GASTOS.indexOf("Donación"))
        assertEquals(Categorias.TRANSFERENCIAS.size - 2, Categorias.TRANSFERENCIAS.indexOf("Rescate"))
    }

    @Test
    fun noHayCategoriasRepetidas() {
        listOf(Categorias.GASTOS, Categorias.APORTES, Categorias.TRANSFERENCIAS).forEach { lista ->
            assertEquals(lista.size, lista.distinct().size)
            assertTrue(lista.none { it.isBlank() })
        }
    }
}
