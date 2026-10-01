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
    fun cuotasUsaLasDeGastosMenosLaCorreccion() {
        assertEquals(Categorias.GASTOS - Categorias.CORRECCION, Categorias.CUOTAS)
    }

    // --- Rubros (v7.8) -----------------------------------------------------------------------

    @Test
    fun losRubrosCubrenExactamenteElCatalogoDeGastos() {
        // Una categoría sin rubro quedaría inalcanzable en el selector: tiene que romper el build.
        val enRubros = Categorias.RUBROS_GASTOS.flatMap { it.categorias }
        assertEquals("ninguna categoría en dos rubros", enRubros.size, enRubros.distinct().size)
        assertEquals((Categorias.GASTOS - Categorias.CORRECCION).toSet(), enRubros.toSet())
        assertFalse(Categorias.GASTOS.contains("Gustos"))  // lo reemplaza el switch 🍰 Evitable
        // Verdulería y Alimentos frescos siguen separadas, las dos en Comida.
        val comida = Categorias.RUBROS_GASTOS.first { it.nombre == "Comida" }.categorias
        assertTrue(comida.containsAll(listOf("Verdulería", "Alimentos frescos")))
    }

    @Test
    fun lasCategoriasViejasDeLaPlanillaCaenEnSuRubro() {
        // Nombres que existen en la planilla real (export 2026-09-30) y ya no están en el catálogo.
        fun rubro(cat: String) = Categorias.rubroDe(cat)?.nombre
        assertEquals("Transporte", rubro("Transporte publico"))
        assertEquals("Ropa", rubro("Ropa"))
        assertEquals("Salidas", rubro("Gustos"))
        assertEquals("Salidas", rubro("Hobbies"))
        assertEquals("Otros", rubro("Otros personales"))
        assertEquals("Otros", rubro("Otros comunes"))
        assertEquals("Otros", rubro("Cualquier texto libre"))
        assertEquals("Comida", rubro("verdulería"))
        assertEquals("Casa", rubro("Supermercado"))
        assertEquals(null, rubro("Corrección"))
        assertEquals("Transporte", Categorias.canonica("Transporte publico"))
        assertEquals("Indumentaria", Categorias.canonica("Ropa"))
    }

    @Test
    fun frecuentesCuentaUsosRecientesYNormalizaNombresViejos() {
        // De más nuevo a más viejo.
        val historial = listOf(
            "Supermercado", "Transporte publico", "Transporte", "Salidas", "Supermercado",
            "Transporte", "Corrección", "Gustos", "Aporte raro"
        )
        val frecuentes = Categorias.frecuentes(historial, "Gasto")
        // Transporte: 3 usos (uno con el nombre viejo); Supermercado: 2; Salidas: 1.
        assertEquals(listOf("Transporte", "Supermercado", "Salidas"), frecuentes)
        // Sin frecuentes para tipos de lista corta que no las muestran, pero no rompe.
        assertEquals(listOf("Otros"), Categorias.frecuentes(listOf("Otros"), "Aporte"))
    }

    @Test
    fun frecuentesDesempataPorLaMasReciente() {
        assertEquals(listOf("Salidas", "Farmacia"), Categorias.frecuentes(listOf("Salidas", "Farmacia"), "Gasto"))
    }

    @Test
    fun agruparPorRubroSumaYConservaLosNombresOriginales() {
        val grupos = Categorias.agruparPorRubro(listOf(
            "Supermercado" to 100.0, "Verdulería" to 50.0, "Transporte publico" to 30.0,
            "Transporte" to 20.0, "Gustos" to 10.0
        ))
        // Supermercado va en Casa; Comida y Transporte empatan en 50 y quedan en orden de aparición.
        assertEquals(listOf("Casa", "Comida", "Transporte", "Salidas"), grupos.map { it.rubro.nombre })
        assertEquals(100.0, grupos[0].total, 0.001)
        // Transporte junta el nombre viejo y el nuevo, cada uno con su nombre (el filtro de Inicio
        // filtra por lo que dice la planilla).
        assertEquals(listOf("Transporte publico", "Transporte"), grupos[2].categorias.map { it.first })
    }

    @Test
    fun cadaTipoResuelveSuListaYSuDefault() {
        assertEquals(Categorias.APORTES, Categorias.deTipo("Aporte"))
        assertEquals(Categorias.TRANSFERENCIAS, Categorias.deTipo("Transferencia"))
        assertEquals(Categorias.CONDONACIONES, Categorias.deTipo("Condonación"))
        assertEquals(Categorias.DEVOLUCIONES, Categorias.deTipo("Devolución"))
        assertEquals(Categorias.CAMBIOS, Categorias.deTipo("Cambio"))
        assertEquals(Categorias.CAMBIOS, Categorias.deTipo("Cambio propio"))
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
        // Variantes por persona que hay en la planilla real (14 aportes).
        assertTrue(Categorias.esIngreso("Sueldo Rocío"))
        assertTrue(Categorias.esIngreso("Sueldo Santiago"))
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
