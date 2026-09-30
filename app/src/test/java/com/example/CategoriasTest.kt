package com.example

import com.example.data.Categorias
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El catálogo de categorías es único: el alta de movimientos y el alta de cuotas leen la misma
 * lista. Antes estaba duplicada literalmente en las dos pantallas y se desincronizaba sola.
 */
class CategoriasTest {

    private val todasLasListas = listOf(
        Categorias.GASTOS, Categorias.APORTES, Categorias.TRANSFERENCIAS, Categorias.CONDONACIONES,
        Categorias.DEVOLUCIONES, Categorias.CAMBIOS
    )

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
        assertEquals(Categorias.CONDONACIONES, Categorias.deTipo("Condonación"))
        assertEquals(Categorias.DEVOLUCIONES, Categorias.deTipo("Devolución"))
        assertEquals(Categorias.CAMBIOS, Categorias.deTipo("Cambio"))
        assertEquals(Categorias.GASTOS, Categorias.deTipo("Gasto"))
        // Tipo desconocido: cae a gastos en vez de romper.
        assertEquals(Categorias.GASTOS, Categorias.deTipo("Cualquiera"))

        assertEquals("Ingreso", Categorias.defaultDeTipo("Aporte"))
        assertEquals("Ajuste", Categorias.defaultDeTipo("Transferencia"))
        assertEquals("Perdón de deuda", Categorias.defaultDeTipo("Condonación"))
        assertEquals("Pago de deuda", Categorias.defaultDeTipo("Devolución"))
        assertEquals("Cambio de dinero", Categorias.defaultDeTipo("Cambio"))
        assertEquals("Transporte", Categorias.defaultDeTipo("Gasto"))
    }

    @Test
    fun otrosEsSiempreElUltimoRecursoDeCadaLista() {
        // Las categorías nuevas se agregan antes de "Otros", que es el cajón de sastre.
        todasLasListas.forEach { lista -> assertEquals("Otros", lista.last()) }
        assertEquals(Categorias.GASTOS.size - 3, Categorias.GASTOS.indexOf("Donación"))
        assertEquals(Categorias.TRANSFERENCIAS.size - 2, Categorias.TRANSFERENCIAS.indexOf("Rescate"))
    }

    @Test
    fun correccionExisteEnGastosYAportes() {
        assertEquals(Categorias.GASTOS.size - 2, Categorias.GASTOS.indexOf(Categorias.CORRECCION))
        assertEquals(Categorias.APORTES.size - 2, Categorias.APORTES.indexOf(Categorias.CORRECCION))
        assertTrue(Categorias.esCorreccion("Corrección"))
        assertTrue(Categorias.esCorreccion("correccion "))
        assertFalse(Categorias.esCorreccion("Otros"))
    }

    @Test
    fun sueldoSeLeeComoIngreso() {
        // "Sueldo" era el nombre hasta la v7.7: las filas viejas no se migran, se leen con alias.
        assertFalse(Categorias.APORTES.contains("Sueldo"))
        assertTrue(Categorias.esIngreso("Ingreso"))
        assertTrue(Categorias.esIngreso("Sueldo"))
        assertTrue(Categorias.esIngreso(" sueldo "))
        assertFalse(Categorias.esIngreso("Transferencias"))
        assertFalse(Categorias.esIngreso("Corrección"))
    }

    @Test
    fun noHayCategoriasRepetidas() {
        todasLasListas.forEach { lista ->
            assertEquals(lista.size, lista.distinct().size)
            assertTrue(lista.none { it.isBlank() })
        }
    }
}
