package com.example

import com.example.data.notifications.BackgroundSyncScheduler
import com.example.data.notifications.BackgroundSyncWorker
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

/**
 * Alineación de la sincronización de segundo plano a las 06:00 y 18:00 hora argentina.
 *
 * Solo se testea el cálculo del retardo inicial: cuándo WorkManager termina corriendo el job
 * depende de Doze y del ahorro de batería, y eso no es nuestro.
 */
class BackgroundSyncSchedulerTest {

    private fun momento(hora: Int, minuto: Int = 0): Calendar =
        Calendar.getInstance(BackgroundSyncWorker.ARGENTINA_TZ).apply {
            set(2026, Calendar.AUGUST, 31, hora, minuto, 0)
            set(Calendar.MILLISECOND, 0)
        }

    private fun horasHasta(hora: Int, minuto: Int = 0): Double =
        BackgroundSyncScheduler.millisHastaProximaCorrida(momento(hora, minuto)) / 3_600_000.0

    @Test
    fun deMadrugadaEsperaHastaLasSeis() {
        assertEquals(3.0, horasHasta(3), 0.001)
    }

    @Test
    fun aLaMananaEsperaHastaLasDieciocho() {
        assertEquals(8.0, horasHasta(10), 0.001)
    }

    @Test
    fun despuesDeLasDieciochoEsperaHastaLasSeisDelDiaSiguiente()  {
        // 23:00 -> 06:00 del día siguiente = 7 h. El salto de día es lo que se está verificando.
        assertEquals(7.0, horasHasta(23), 0.001)
    }

    @Test
    fun justoEnLaHoraDeCorridaEsperaHastaLaSiguiente() {
        // A las 06:00 en punto la corrida de las 6 ya pasó: la próxima es la de las 18.
        assertEquals(12.0, horasHasta(6), 0.001)
        assertEquals(12.0, horasHasta(18), 0.001)
    }

    @Test
    fun elRetardoNuncaEsNegativoNiMayorAlIntervalo() {
        // Invariante: cualquier momento del día cae dentro de la ventana de 12 h del periódico.
        for (hora in 0..23) {
            for (minuto in listOf(0, 1, 30, 59)) {
                val espera = horasHasta(hora, minuto)
                assertEquals(
                    "Retardo fuera de rango a las $hora:$minuto -> $espera h",
                    true,
                    espera > 0.0 && espera <= 12.0
                )
            }
        }
    }
}
