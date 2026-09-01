package com.example.data.upload

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Encola el drenado de la cola de movimientos pendientes. */
object MovementUploadScheduler {

    /** Trabajo único: las subidas se serializan en vez de pisarse entre sí. */
    const val WORK_NAME = "movement_upload_queue"

    /**
     * @param requiereRed `false` en Modo Local (Demo), donde el "guardado" es contra
     *        SharedPreferences: con la constraint puesta, un teléfono sin conexión dejaría el alta
     *        colgada para siempre esperando una red que no necesita.
     *
     * `APPEND_OR_REPLACE` y no `KEEP`: si ya hay un drenado en curso, encolar uno nuevo detrás
     * garantiza que el movimiento recién agregado se tome aunque el drenado actual ya haya leído
     * la cola. Si el trabajo previo terminó cancelado o fallado, lo reemplaza.
     */
    fun enqueue(context: Context, requiereRed: Boolean = true) {
        val builder = OneTimeWorkRequestBuilder<MovementUploadWorker>()
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            // Expedited es lo que hace que esto funcione con la app cerrada. Un trabajo común lo
            // toma el `GreedyScheduler` de WorkManager, que solo corre mientras el proceso está
            // vivo: al minimizar, la subida quedaba encolada y no pasaba nada hasta volver a abrir
            // la app (se ve en las diagnósticas: esas corridas salían con `Job Id: null`, o sea
            // nunca llegaron a JobScheduler). Expedited va sí o sí como job del sistema y arranca
            // el proceso solo. RUN_AS_NON_EXPEDITED_WORK_REQUEST: si se agota la cuota de trabajos
            // expedited, degrada a uno normal en vez de fallar.
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        if (requiereRed) {
            builder.setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
        }
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, builder.build())
    }
}
