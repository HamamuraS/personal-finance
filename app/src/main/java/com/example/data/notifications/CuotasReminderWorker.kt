package com.example.data.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.AhorroRepository
import com.example.data.PreferencesHelper
import com.example.ui.CuotasEngine
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Corre una vez por día (ver [CuotasReminderScheduler]) y decide, según la fecha en huso horario
 * argentino, si corresponde notificar:
 *  - Último día del mes → total de cuotas pendientes del mes que cierra (todas las tarjetas).
 *  - Lunes → total de cuotas atrasadas (de meses anteriores) sin pagar.
 *
 * Reutiliza [CuotasEngine] (el mismo motor puro que usa la UI) para no duplicar la lógica de
 * negocio; el estado de "pagada" sigue siendo derivado de los movimientos, nunca duplicado.
 */
class CuotasReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        val ARGENTINA_TZ: TimeZone = TimeZone.getTimeZone("America/Argentina/Buenos_Aires")
    }

    override suspend fun doWork(): Result {
        val prefs = PreferencesHelper(applicationContext)
        val hoy = Calendar.getInstance(ARGENTINA_TZ)
        val esUltimoDiaDelMes = hoy.get(Calendar.DAY_OF_MONTH) == hoy.getActualMaximum(Calendar.DAY_OF_MONTH)
        val esLunes = hoy.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY

        // Nada que evaluar hoy: no hace falta ni refrescar datos.
        if (!esUltimoDiaDelMes && !esLunes) return Result.success()

        val mesFormat = SimpleDateFormat("yyyy-MM", Locale.US).apply { timeZone = ARGENTINA_TZ }
        val diaFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = ARGENTINA_TZ }
        val mesActual = mesFormat.format(hoy.time)
        val hoyIso = diaFormat.format(hoy.time)

        return try {
            val repo = AhorroRepository(prefs)
            val movimientos = if (prefs.useLocalDemo) {
                prefs.getLocalMovements()
            } else {
                repo.fetchMovements(prefs.scriptUrl).also { prefs.saveSheetsCache(it) }
            }
            val planes = if (prefs.useLocalDemo) {
                prefs.getLocalPlans()
            } else {
                repo.fetchPlans(prefs.scriptUrl).also { prefs.savePlansCache(it) }
            }
            val misPlanes = planes.filter { it.propietario.equals(prefs.currentUserProfile, ignoreCase = true) }

            if (esUltimoDiaDelMes && prefs.lastCierreNotificado != mesActual) {
                val total = CuotasEngine.cuotasImpagasDelMes(mesActual, misPlanes, movimientos)
                    .sumOf { (_, cuota) -> cuota.monto }
                if (total > 0.0) {
                    CuotasNotifier.notificarCierreDeMes(applicationContext, total)
                    prefs.lastCierreNotificado = mesActual
                }
            }

            if (esLunes && prefs.lastAtrasoNotificado != hoyIso) {
                val total = CuotasEngine.recordatoriosDelMes(mesActual, misPlanes, movimientos)
                    .filter { it.atrasada }
                    .sumOf { it.cuota.monto }
                if (total > 0.0) {
                    CuotasNotifier.notificarAtrasos(applicationContext, total)
                    prefs.lastAtrasoNotificado = hoyIso
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
