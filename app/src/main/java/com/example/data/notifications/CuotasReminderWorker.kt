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
 *
 * **Recuperación del aviso de cierre.** WorkManager no garantiza el horario: con Doze, ahorro de
 * batería agresivo o el teléfono apagado, la corrida del día 31 puede caer recién el 1°. Antes eso
 * perdía el aviso de ese mes para siempre (una sola oportunidad, y el flag de deduplicación impedía
 * el reintento). Ahora el worker también se despierta los primeros [DIAS_RECUPERO_CIERRE] días del
 * mes siguiente y, si nunca avisó por el mes que cerró, lo hace ahí. El de atrasos no lo necesita:
 * los atrasos siguen existiendo el lunes siguiente, así que se auto-cura.
 */
class CuotasReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        val ARGENTINA_TZ: TimeZone = TimeZone.getTimeZone("America/Argentina/Buenos_Aires")

        /** Días del mes siguiente en los que todavía se intenta el aviso de cierre no enviado. */
        const val DIAS_RECUPERO_CIERRE = 5
    }

    override suspend fun doWork(): Result {
        val prefs = PreferencesHelper(applicationContext)
        val hoy = Calendar.getInstance(ARGENTINA_TZ)
        val esLunes = hoy.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY

        val mesFormat = SimpleDateFormat("yyyy-MM", Locale.US).apply { timeZone = ARGENTINA_TZ }
        val diaFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = ARGENTINA_TZ }
        val mesActual = mesFormat.format(hoy.time)
        val hoyIso = diaFormat.format(hoy.time)

        // Decisión de fechas: pura y testeable (ver CuotasEngineTest).
        val cierre = CuotasEngine.avisoDeCierre(
            mesActual = mesActual,
            diaDelMes = hoy.get(Calendar.DAY_OF_MONTH),
            ultimoDiaDelMes = hoy.getActualMaximum(Calendar.DAY_OF_MONTH),
            yaAvisado = prefs.lastCierreNotificado,
            diasDeRecupero = DIAS_RECUPERO_CIERRE
        )

        // Nada que evaluar hoy: no hace falta ni refrescar datos.
        if (!cierre.corresponde && !esLunes) return Result.success()

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

            if (cierre.corresponde) {
                val total = CuotasEngine.cuotasImpagasDelMes(cierre.mes, misPlanes, movimientos)
                    .sumOf { (_, cuota) -> cuota.monto }
                if (total > 0.0) {
                    CuotasNotifier.notificarCierreDeMes(
                        applicationContext, total, cierre.mes, yaCerro = cierre.esRecupero
                    )
                    prefs.lastCierreNotificado = cierre.mes
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
