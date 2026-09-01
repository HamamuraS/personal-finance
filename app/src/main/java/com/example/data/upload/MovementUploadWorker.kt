package com.example.data.upload

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Base64
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.data.AhorroRepository
import com.example.data.ImageInfo
import com.example.data.PendingMovement
import com.example.data.PreferencesHelper
import com.example.data.notifications.MovementNotifier
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Drena la cola de movimientos pendientes (ver [PendingMovement]) contra la planilla.
 *
 * Es un **drenador de cola**, no un worker por movimiento: lee todo lo pendiente y lo sube en orden
 * de encolado. Así, si el usuario carga tres gastos seguidos, se escriben de a uno y en orden —
 * tres `appendRow` concurrentes contra la misma hoja son una race condition del lado del Apps
 * Script. Por lo mismo la cola se encola como trabajo único con `APPEND_OR_REPLACE`.
 *
 * **Nada se descarta nunca.** Un movimiento sale de la cola solo cuando la planilla confirma la
 * escritura. Si falla, se le suma un intento y queda pendiente: WorkManager reintenta con backoff
 * exponencial hasta [MAX_REINTENTOS], y si aun así no entra, el próximo disparador (arranque de la
 * app, sync de las 06/18, o el alta del siguiente movimiento) lo vuelve a tomar. La falta de red no
 * llega ni a ser un error: la constraint `CONNECTED` hace que el worker ni arranque hasta que haya
 * conexión.
 */
class MovementUploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        /** Reintentos con backoff dentro de una misma corrida antes de dejarlo para más tarde. */
        const val MAX_REINTENTOS = 4

        /**
         * Serializa el drenado dentro del proceso. El alta contra el Apps Script es un `append`
         * (el upsert por id es otra acción), así que dos drenados en paralelo escriben la MISMA
         * fila dos veces. El trabajo único de WorkManager ya evita que se solapen dos corridas de
         * este worker, pero no cubre a quien llame a [drenar] por fuera.
         */
        private val drenadoLock = Mutex()

        /**
         * Sube lo que haya pendiente. Devuelve cuántos quedaron sin subir.
         *
         * Los pendientes con `esperandoTicket` se saltean: su foto todavía se está comprimiendo y
         * subirlos ahora los dejaría sin ticket para siempre. Vuelven en el próximo drenado.
         */
        suspend fun drenar(context: Context, prefs: PreferencesHelper): Int = drenadoLock.withLock {
            val pendientes = prefs.getPendingMovements().filterNot { it.esperandoTicket }
            if (pendientes.isEmpty()) return@withLock 0

            val repo = AhorroRepository(prefs)
            var fallados = 0

            for (pendiente in pendientes.sortedBy { it.encoladoEn }) {
                val ok = try {
                    repo.saveMovement(
                        webAppUrl = prefs.scriptUrl,
                        movement = pendiente.movement,
                        imageInfo = leerTicket(pendiente.ticketPath),
                        folderId = prefs.folderId,
                        // PUT y no POST: el alta tiene que ser IDEMPOTENTE porque esta cola
                        // reintenta sola. `POST` es un append ciego, así que si la planilla llegó
                        // a escribir la fila pero la respuesta se perdió (timeout, red cortada),
                        // el reintento la apendeaba de nuevo y quedaban dos filas con el mismo id
                        // — lo que después reventaba el listado de Inicio, que usa el id como key.
                        // `PUT` busca por id y reemplaza; si no existe, appendea igual.
                        action = "PUT"
                    )
                } catch (e: Exception) {
                    registrarFallo(prefs, pendiente, e.localizedMessage ?: e.javaClass.simpleName)
                    fallados++
                    continue
                }

                if (ok) {
                    borrarTicket(pendiente.ticketPath)
                    prefs.removePendingMovement(pendiente.movement.id)
                    MovementNotifier.notificarGuardado(context, pendiente.movement)
                } else {
                    registrarFallo(prefs, pendiente, "La planilla rechazó la escritura")
                    fallados++
                }
            }
            fallados
        }

        /** Suma un intento al pendiente sin sacarlo de la cola (read-modify-write por id). */
        private fun registrarFallo(prefs: PreferencesHelper, pendiente: PendingMovement, error: String) {
            val actual = prefs.getPendingMovements()
                .firstOrNull { it.movement.id == pendiente.movement.id } ?: return
            prefs.upsertPendingMovement(
                actual.copy(intentos = actual.intentos + 1, ultimoError = error)
            )
        }

        /** Relee el JPEG comprimido del disco y lo pasa a base64 para el Apps Script. */
        private fun leerTicket(path: String): ImageInfo? {
            if (path.isBlank()) return null
            val file = File(path)
            if (!file.exists()) return null
            return try {
                ImageInfo(base64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP))
            } catch (e: Exception) {
                null
            }
        }

        private fun borrarTicket(path: String) {
            if (path.isBlank()) return
            runCatching { File(path).delete() }
        }
    }

    /**
     * Notificación del servicio en primer plano. WorkManager la exige para poder correr un trabajo
     * expedited en Android 11 y anteriores, donde no existen los jobs expedited del sistema y el
     * equivalente es un foreground service. En Android 12+ no se usa.
     */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notificacion = MovementNotifier.notificacionDeSubidaEnCurso(applicationContext)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                MovementNotifier.NOTIF_ID_SUBIENDO,
                notificacion,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(MovementNotifier.NOTIF_ID_SUBIENDO, notificacion)
        }
    }

    override suspend fun doWork(): Result {
        val prefs = PreferencesHelper(applicationContext)
        val fallados = drenar(applicationContext, prefs)

        if (fallados == 0) {
            // Releer la planilla deja el cache (y la ventana de frescura) al día, así que al volver
            // a abrir la app el movimiento ya está sin necesidad de otro fetch.
            runCatching { AhorroRepository(prefs).fetchMovements(prefs.scriptUrl) }
            return Result.success()
        }

        if (runAttemptCount < MAX_REINTENTOS) return Result.retry()

        // Se agotó el backoff: quedan en la cola y se reintentan en el próximo disparador.
        MovementNotifier.notificarPendientes(applicationContext, fallados)
        return Result.success()
    }
}
