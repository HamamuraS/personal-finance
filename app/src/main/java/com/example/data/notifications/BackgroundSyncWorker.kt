package com.example.data.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.AhorroRepository
import com.example.data.PreferencesHelper
import com.example.data.upload.MovementUploadScheduler
import com.example.ui.AccountingEngine
import com.example.ui.CuotasEngine
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Sincronización periódica en segundo plano. Corre cada 12 horas (06:00 y 18:00 hora argentina, ver
 * [BackgroundSyncScheduler]) aunque el usuario no abra la app, y hace tres cosas en orden:
 *
 *  1. **Drena la cola de altas pendientes.** Es la red de seguridad de los movimientos que no se
 *     pudieron subir en su momento (ver [com.example.data.PendingMovement]).
 *  2. **Refresca la planilla.** Esto renueva `lastFetchAt`, así que abrir la app después de una
 *     corrida no dispara ningún fetch: el arranque es instantáneo contra el cache.
 *  3. **Materializa el corte del mes** si el mes en curso todavía no tiene su apertura.
 *  4. **Evalúa los recordatorios de cuotas** (cierre de mes y atrasos de los lunes).
 *
 * Antes esto eran dos schedulers: uno diario a las 9:00 que fetcheaba **solo** los días que iba a
 * notificar. Se fusionaron para no traer la planilla dos veces el mismo día y para que haya un
 * único lugar que decida cuándo se toca la red. El costo del cambio es que el recordatorio de
 * cuotas ahora sale en la corrida de las 06:00 en vez de a las 9:00.
 *
 * La decisión de fechas sigue viviendo en [CuotasEngine] (pura y testeable); acá solo se orquesta.
 */
class BackgroundSyncWorker(
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

        // 1. Altas que quedaron sin subir: se dispara el trabajo único de subida en vez de drenar
        //    acá mismo. Drenar en línea corría en paralelo con una corrida del worker de subida
        //    (son trabajos distintos para WorkManager) y, como el alta es un `append`, la misma
        //    fila terminaba escrita dos veces en la planilla. Delegando, la cola queda serializada
        //    y además el ViewModel se entera del resultado, que observa ese trabajo único.
        if (prefs.getPendingMovements().isNotEmpty()) {
            MovementUploadScheduler.enqueue(applicationContext, requiereRed = !prefs.useLocalDemo)
        }

        return try {
            // 2. Refresco. A diferencia del worker viejo, se hace SIEMPRE: es el motivo de existir
            //    de la corrida de las 06/18 y lo que mantiene fresca la ventana de `lastFetchAt`.
            val repo = AhorroRepository(prefs)
            val movimientos = if (prefs.useLocalDemo) {
                prefs.getLocalMovements()
            } else {
                repo.fetchMovements(prefs.scriptUrl)
            }
            val planes = if (prefs.useLocalDemo) {
                prefs.getLocalPlans()
            } else {
                repo.fetchPlans(prefs.scriptUrl).also { prefs.savePlansCache(it) }
            }

            // 3. Corte del mes (plata + cuotas). Escribir el saldo inicial salía de un botón en
            //    Ajustes que hay que acordarse de tocar, así que en la práctica las hojas seguían
            //    encadenadas y purgar una vieja rompía el saldo de la actual. Es seguro correrlo de
            //    más: los ids de las filas de apertura son determinísticos (regenerar pisa en vez de
            //    duplicar) y el snapshot de cuotas solo agrega.
            materializarCorteDelMes(prefs, repo, movimientos)

            // 4. Recordatorios de cuotas.
            notificarCuotas(prefs, movimientos, planes)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    /**
     * Materializa el corte del mes en curso: la **plata** (filas de apertura) y las **cuotas**
     * (snapshot de las ya pagadas). Con las dos mitades escritas, el mes deja de depender de las
     * hojas anteriores y se las puede archivar.
     *
     * Las dos mitades se disparan por separado a propósito:
     *
     *  - La **apertura** se escribe solo si falta. Si no hay ningún mes anterior del que arrastrar,
     *    o si [AccountingEngine.chequearRecalculo] lo desaconseja, no se toca: nunca se pisa una
     *    apertura con saldo por una en cero.
     *  - El **snapshot de cuotas** se pide SIEMPRE. Atarlo a "acabo de escribir la apertura" lo
     *    dejaba sin correr justo en el caso más común: la apertura del mes ya puede estar escrita
     *    por el trigger diario del Apps Script (`actualizarAperturas`), que no sabe nada de cuotas,
     *    y entonces esta función salía temprano y el snapshot no se escribía nunca. Es idempotente
     *    y solo agrega (unión, nunca reemplazo), así que correrlo de más no cuesta nada.
     */
    private suspend fun materializarCorteDelMes(
        prefs: PreferencesHelper,
        repo: AhorroRepository,
        movimientos: List<com.example.data.Movement>
    ) {
        if (prefs.useLocalDemo || prefs.scriptUrl.isEmpty()) return

        val mes = SimpleDateFormat("yyyy-MM", Locale.US)
            .apply { timeZone = ARGENTINA_TZ }
            .format(Calendar.getInstance(ARGENTINA_TZ).time)

        val usable = movimientos.filter {
            !AccountingEngine.isLegacyCarryover(it) && mesDe(it).isNotEmpty()
        }

        val faltaApertura = usable.none { mesDe(it) == mes && AccountingEngine.isOpeningRow(it) }
        val hayDeDondeArrastrar = usable.any { mesDe(it) < mes }
        if (faltaApertura && hayDeDondeArrastrar &&
            AccountingEngine.chequearRecalculo(usable, mes).permitido
        ) {
            AccountingEngine.openingRowsFor(mes, AccountingEngine.openingFor(usable, mes))
                .forEach { repo.saveMovement(prefs.scriptUrl, it, action = "PUT") }
        }

        repo.snapshotCuotasPrevias(prefs.scriptUrl, mes)
    }

    /** "yyyy-MM" de un movimiento, o "" si la fecha no tiene forma de fecha. */
    private fun mesDe(m: com.example.data.Movement): String =
        if (m.fecha.length >= 7) m.fecha.substring(0, 7) else ""

    private fun notificarCuotas(
        prefs: PreferencesHelper,
        movimientos: List<com.example.data.Movement>,
        planes: List<com.example.data.CuotaPlan>
    ) {
        val hoy = Calendar.getInstance(ARGENTINA_TZ)
        val esLunes = hoy.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY

        val mesFormat = SimpleDateFormat("yyyy-MM", Locale.US).apply { timeZone = ARGENTINA_TZ }
        val diaFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = ARGENTINA_TZ }
        val mesActual = mesFormat.format(hoy.time)
        val hoyIso = diaFormat.format(hoy.time)

        val cierre = CuotasEngine.avisoDeCierre(
            mesActual = mesActual,
            diaDelMes = hoy.get(Calendar.DAY_OF_MONTH),
            ultimoDiaDelMes = hoy.getActualMaximum(Calendar.DAY_OF_MONTH),
            yaAvisado = prefs.lastCierreNotificado,
            diasDeRecupero = DIAS_RECUPERO_CIERRE
        )
        if (!cierre.corresponde && !esLunes) return

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
    }
}
