package com.example.data.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Programa [CuotasReminderWorker] para correr una vez por día, alineado a ~9:00am hora argentina.
 * Idempotente: `enqueueUniquePeriodicWork` con `KEEP` no reprograma si ya había un job encolado
 * (p. ej. si `schedule()` se llama en cada `onCreate` de la Activity).
 */
object CuotasReminderScheduler {
    private const val WORK_NAME = "cuotas_reminder_daily"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<CuotasReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(millisUntilNext9amArgentina(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun millisUntilNext9amArgentina(): Long {
        val tz = CuotasReminderWorker.ARGENTINA_TZ
        val now = Calendar.getInstance(tz)
        val target = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!target.after(now)) {
            target.add(Calendar.DAY_OF_MONTH, 1)
        }
        return target.timeInMillis - now.timeInMillis
    }
}
