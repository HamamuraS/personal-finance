package com.example

import com.example.data.Categorias
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertEquals(Categorias.CONDONACIONES, Categorias.deTipo("Condonación"))
        assertEquals(Categorias.GASTOS, Categorias.deTipo("Gasto"))
        // Tipo desconocido: cae a gastos en vez de romper.
        assertEquals(Categorias.GASTOS, Categorias.deTipo("Cualquiera"))

        assertEquals("Sueldo", Categorias.defaultDeTipo("Aporte"))
        assertEquals("Ajuste", Categorias.defaultDeTipo("Transferencia"))
        assertEquals("Perdón de deuda", Categorias.defaultDeTipo("Condonación"))
        assertEquals("Transporte", Categorias.defaultDeTipo("Gasto"))
    }

    @Test
    fun otrosEsSiempreElUltimoRecursoDeCadaLista() {
        // Las categorías nuevas se agregan antes de "Otros", que es el cajón de sastre.
        listOf(Categorias.GASTOS, Categorias.APORTES, Categorias.TRANSFERENCIAS, Categorias.CONDONACIONES)
            .forEach { lista -> assertEquals("Otros", lista.last()) }
        // "Cambio de dinero" se agregó justo encima de "Otros" en las tres listas que lo tienen.
        listOf(Categorias.GASTOS, Categorias.APORTES, Categorias.TRANSFERENCIAS).forEach { lista ->
            assertEquals(lista.size - 2, lista.indexOf("Cambio de dinero"))
        }
        assertEquals(Categorias.GASTOS.size - 3, Categorias.GASTOS.indexOf("Donación"))
        assertEquals(Categorias.TRANSFERENCIAS.size - 3, Categorias.TRANSFERENCIAS.indexOf("Rescate"))
    }

    @Test
    fun lasCarpetasCubrenExactamenteElCatalogoDeGastos() {
        // Es LA invariante del selector por carpetas: una categoría que quede fuera de todo grupo
        // sigue existiendo en el catálogo pero se vuelve inalcanzable en la UI. Que rompa acá.
        val enCarpetas = Categorias.GRUPOS_GASTOS.flatMap { it.categorias }
        assertEquals(enCarpetas.size, enCarpetas.distinct().size)   // ninguna en dos carpetas
        assertEquals(Categorias.GASTOS.toSet(), enCarpetas.toSet())
    }

    @Test
    fun soloLosGastosSeAgrupanEnCarpetas() {
        // Las otras listas tienen 3-5 categorías: agruparlas sería peor que dejarlas planas.
        assertEquals(Categorias.GRUPOS_GASTOS, Categorias.gruposDeTipo("Gasto"))
        assertNull(Categorias.gruposDeTipo("Aporte"))
        assertNull(Categorias.gruposDeTipo("Transferencia"))
        assertNull(Categorias.gruposDeTipo("Condonación"))
    }

    @Test
    fun grupoDeUbicaLaCategoriaSinImportarLaCapitalizacion() {
        assertEquals("Comida", Categorias.grupoDe("Supermercado")?.nombre)
        assertEquals("Comida", Categorias.grupoDe("supermercado")?.nombre)
        assertNull(Categorias.grupoDe("Una categoría vieja que ya no está"))
    }

    @Test
    fun frecuentesRespetaElOrdenDeUsoYFiltraPorTipo() {
        val historial = listOf("Salidas", "Sueldo", "salidas", "Farmacia", "Transporte")

        // Se conserva el orden del historial, sin repetir y sin colar categorías de otro tipo
        // ("Sueldo" es de aportes).
        assertEquals(
            listOf("Salidas", "Farmacia", "Transporte"),
            Categorias.recientes(historial, "Gasto")
        )
        // Y al revés: en un aporte no aparece ninguna categoría de gasto.
        assertEquals(listOf("Sueldo"), Categorias.recientes(historial, "Aporte"))
    }

    @Test
    fun frecuentesSeCortaEnElMaximoYToleraElHistorialVacio() {
        assertEquals(3, Categorias.recientes(Categorias.GASTOS, "Gasto", max = 3).size)
        assertTrue(Categorias.recientes(emptyList(), "Gasto").isEmpty())
        // Categorías viejas que ya no están en el catálogo no ensucian la fila.
        assertTrue(Categorias.recientes(listOf("Alquiler", "Regalos"), "Gasto").isEmpty())
    }

    @Test
    fun noHayCategoriasRepetidas() {
        listOf(Categorias.GASTOS, Categorias.APORTES, Categorias.TRANSFERENCIAS, Categorias.CONDONACIONES).forEach { lista ->
            assertEquals(lista.size, lista.distinct().size)
            assertTrue(lista.none { it.isBlank() })
        }
    }
}
