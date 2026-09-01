package com.example.data.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Programa [BackgroundSyncWorker] cada 12 horas, alineado a las 06:00 y 18:00 hora argentina.
 *
 * Idempotente: `enqueueUniquePeriodicWork` con `KEEP` no reprograma si ya hay un job encolado, así
 * que llamarlo en cada `onCreate` de la Activity no reinicia el ciclo.
 *
 * **Nota sobre la precisión.** WorkManager no garantiza el horario: con Doze o ahorro de batería
 * agresivo la corrida puede atrasarse, y un periódico de 12 h acumula deriva con el tiempo. Es
 * aceptable porque nada depende del minuto exacto — el refresco solo tiene que pasar "un par de
 * veces por día" y el aviso de cierre de mes ya tiene su propia ventana de recuperación de varios
 * días (ver [BackgroundSyncWorker.DIAS_RECUPERO_CIERRE]).
 */
object BackgroundSyncScheduler {

    private const val WORK_NAME = "background_sync_12h"

    /** Nombre del trabajo único que programaban las versiones <= 7.3 (recordatorio diario 9:00). */
    private const val LEGACY_WORK_NAME = "cuotas_reminder_daily"

    /** Horas (en huso argentino) en las que se quiere que corra la sincronización. */
    val HORAS_DE_CORRIDA = listOf(6, 18)

    fun schedule(context: Context) {
        val workManager = WorkManager.getInstance(context)

        // El worker diario de cuotas se fusionó con este. Sin cancelarlo, los teléfonos que vienen
        // de una versión anterior se quedarían con los dos jobs encolados y traerían la planilla
        // de más.
        workManager.cancelUniqueWork(LEGACY_WORK_NAME)

        val request = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(12, TimeUnit.HOURS)
            .setInitialDelay(millisHastaProximaCorrida(), TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()

        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun millisHastaProximaCorrida(): Long =
        millisHastaProximaCorrida(Calendar.getInstance(BackgroundSyncWorker.ARGENTINA_TZ))

    /**
     * Milisegundos desde [ahora] hasta la próxima de [HORAS_DE_CORRIDA]. Separado del reloj del
     * sistema para poder testearlo (ver `BackgroundSyncSchedulerTest`).
     */
    fun millisHastaProximaCorrida(ahora: Calendar): Long {
        val proxima = HORAS_DE_CORRIDA
            .map { hora ->
                (ahora.clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, hora)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    if (!after(ahora)) add(Calendar.DAY_OF_MONTH, 1)
                }
            }
            .minOf { it.timeInMillis }
        return proxima - ahora.timeInMillis
    }
}
