package com.example

import com.example.data.Movement
import com.example.data.PendingMovement
import com.example.data.quitarSiNoCambio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regresión del crash al abrir la app tras subir un movimiento con reintentos.
 *
 * La cola de subida reintenta sola. El alta se mandaba con `POST`, que en el Apps Script es un
 * **append ciego**: si la planilla escribía la fila pero la respuesta HTTP se perdía, el reintento
 * la volvía a apendear y quedaban dos filas con el mismo id. Inicio lista los movimientos con
 * `items(..., key = { it.id })`, y dos keys iguales tiran `IllegalArgumentException` → la app
 * crasheaba al abrir, sin forma de entrar a borrar la fila.
 *
 * Ahora la cola sube con `PUT` (busca por id y reemplaza), y además la lista se deduplica antes de
 * llegar a la UI. Estos tests cubren la segunda parte, que es la que se puede testear sin red.
 */
class ColaDeSubidaTest {

    private fun mov(id: String, fecha: String = "2026-08-20 10:00", monto: Double = 1.0) = Movement(
        id = id, fecha = fecha, monto = monto, tipo = "Transferencia", categoria = "Ajuste",
        responsable = "Santiago", propietario = "Santiago", esComun = false,
        metodoPago = "Billetera Virtual"
    )

    /** Réplica de la normalización que hace `AhorroViewModel.applyMovements`. */
    private fun normalizar(filas: List<Movement>): List<Movement> =
        filas.mapIndexed { index, m -> m.copy(id = m.id.ifBlank { "sin-id-$index" }) }
            .sortedWith(compareByDescending<Movement> { it.fecha }.thenByDescending { it.id })
            .distinctBy { it.id }

    @Test
    fun dosFilasConElMismoIdColapsanEnUna() {
        // Exactamente el caso que crasheaba: la transferencia de 1 peso escrita dos veces.
        val duplicada = mov("de722d4f-3098-4ad0-bb27-e804f7e76930")
        val normalizados = normalizar(listOf(duplicada, duplicada, mov("otro-id")))

        assertEquals(2, normalizados.size)
        assertEquals(normalizados.size, normalizados.map { it.id }.distinct().size)
    }

    @Test
    fun lasClavesSiempreSonUnicas() {
        // Es la precondición de `items(..., key = { it.id })`: si esto falla, la app no abre.
        val filas = listOf(
            mov("a"), mov("a"), mov("b"),
            mov(""), mov(""), // filas viejas sin columna ID
            mov("c", fecha = "2026-07-01 09:00")
        )
        val ids = normalizar(filas).map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun lasFilasSinIdNoSePisanEntreSi() {
        // Sin id sintético, dos filas con la columna ID vacía comparten el id "" y `distinctBy`
        // borraría una de las dos: el movimiento desaparecería del listado y del balance.
        val normalizados = normalizar(listOf(mov("", monto = 100.0), mov("", monto = 200.0)))

        assertEquals(2, normalizados.size)
        assertTrue(normalizados.all { it.id.isNotBlank() })
        assertEquals(300.0, normalizados.sumOf { it.monto }, 0.001)
    }

    @Test
    fun noSePierdeNingunMovimientoLegitimo() {
        val filas = (1..20).map { mov("id-$it", monto = it.toDouble()) }
        assertEquals(filas.size, normalizar(filas).size)
        assertEquals(filas.sumOf { it.monto }, normalizar(filas).sumOf { it.monto }, 0.001)
    }

    // --- v7.7: edición mientras se sube ------------------------------------------------------

    @Test
    fun subirUnaVersionNoBorraLaEdicionQueLlegoMientrasTanto() {
        // El drenado sube la versión original; mientras tanto el usuario la edita (mismo id). Al
        // confirmar la subida, la edición tiene que quedar en la cola para el próximo drenado.
        val original = mov("a", monto = 100.0)
        val editada = original.copy(monto = 150.0)
        val cola = listOf(PendingMovement(editada), PendingMovement(mov("b")))

        val restante = quitarSiNoCambio(cola, subido = original)

        assertEquals(listOf("a", "b"), restante.map { it.movement.id })
        assertEquals(150.0, restante.first().movement.monto, 0.001)
    }

    @Test
    fun subirLaVersionVigenteLaSacaDeLaCola() {
        val m = mov("a")
        val restante = quitarSiNoCambio(listOf(PendingMovement(m, intentos = 2), PendingMovement(mov("b"))), subido = m)
        assertEquals(listOf("b"), restante.map { it.movement.id })
    }
}
