package com.example.data.notifications

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.ScreenTab
import com.example.data.Movement
import kotlin.math.absoluteValue

/**
 * Notificaciones del alta de movimientos: el aviso de que la escritura en la planilla terminó.
 *
 * Existe porque el registro pasó a ser asincrónico (ver
 * [com.example.data.upload.MovementUploadWorker]): al tocar "Registrar" la app vuelve a Inicio al
 * instante y la subida sigue en segundo plano, incluso con la app cerrada. Sin este aviso el
 * usuario no tendría forma de saber que terminó — ni, sobre todo, que **falló**.
 *
 * Canal propio, separado del de cuotas, para que se puedan silenciar por separado desde Android.
 */
object MovementNotifier {

    const val CHANNEL_ID = "movimientos"
    private const val NOTIF_ID_PENDIENTES = 2001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Movimientos registrados",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Confirmación de los movimientos guardados en la planilla" }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /** Emoji según el tipo de movimiento. */
    private fun emojiTipo(tipo: String): String = when (tipo.lowercase()) {
        "aporte" -> "💰"
        "transferencia" -> "🔁"
        "cambio" -> "💱"
        "condonación", "condonacion", "devolución", "devolucion" -> "🤝"
        else -> "💸"
    }

    /** Emoji según la categoría. Cae en 🧾 para cualquier categoría que no esté mapeada. */
    private fun emojiCategoria(categoria: String): String = when (categoria.lowercase()) {
        "transporte" -> "🚌"
        "servicios" -> "🧾"
        "animales" -> "🐾"
        "supermercado" -> "🛒"
        "verdulería" -> "🥬"
        "consumo inmediato" -> "🥤"
        "alimentos frescos" -> "🐟"
        "farmacia" -> "💊"
        "indumentaria" -> "👕"
        "cuidado personal" -> "🧴"
        "salidas" -> "🍻"
        "gustos" -> "🍫"
        "utilería" -> "🔧"
        "sueldo" -> "🤑"
        "transferencias" -> "🔁"
        "ajuste" -> "⚖️"
        "reembolso" -> "↩️"
        else -> "🧾"
    }

    /** "Gasto" -> "Gasto registrado"; el título lleva el emoji del tipo. */
    fun notificarGuardado(context: Context, movement: Movement) {
        val monto = CuotasNotifier.formatMoney().format(movement.monto)
        val titulo = "${emojiTipo(movement.tipo)} ${movement.tipo} guardado"
        val texto = buildString {
            append("$monto en ${movement.categoria} ${emojiCategoria(movement.categoria)}")
            if (movement.descripcion.isNotBlank()) append(" — ${movement.descripcion}")
            append(" · ya está en la planilla ✅")
        }
        // Id derivado del movimiento: varias altas seguidas se apilan en vez de pisarse.
        show(context, movement.id.hashCode().absoluteValue, titulo, texto)
    }

    /**
     * Aviso de que quedaron altas sin subir. No es un descarte: siguen en la cola y se reintentan
     * solas (ver [com.example.data.PendingMovement]); el aviso es para que el usuario no crea que
     * ya está guardado. Id fijo: siempre interesa el estado actual, no el historial.
     */
    fun notificarPendientes(context: Context, cantidad: Int) {
        val texto = if (cantidad == 1) {
            "Un movimiento no se pudo guardar todavía ⏳ Sigue en la cola y se reintenta solo cuando haya conexión."
        } else {
            "$cantidad movimientos no se pudieron guardar todavía ⏳ Siguen en la cola y se reintentan solos cuando haya conexión."
        }
        show(context, NOTIF_ID_PENDIENTES, "Guardado pendiente", texto)
    }

    /**
     * Notificación del trabajo de subida en curso. La pide WorkManager cuando corre el worker como
     * servicio en primer plano, que es el camino de las versiones de Android anteriores a la 12
     * (en la 12+ el trabajo expedited va como job de alta prioridad y esto no se usa).
     *
     * Es discreta a propósito: `PRIORITY_LOW` y sin sonido. Lo que le importa al usuario es el
     * aviso de "guardado", no el de "guardando".
     */
    fun notificacionDeSubidaEnCurso(context: Context): android.app.Notification {
        ensureChannel(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Guardando en la planilla…")
            .setContentText("Terminando de registrar tus movimientos ⏳")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(contentIntent(context))
            .build()
    }

    /** Id de la notificación de "subiendo" (la del servicio en primer plano). */
    const val NOTIF_ID_SUBIENDO = 2002

    /** Al tocar la notificación, abre (o trae al frente) la app en Inicio. */
    private fun contentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_TAB, ScreenTab.INICIO.name)
        }
        return PendingIntent.getActivity(
            context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    @SuppressLint("MissingPermission") // chequeado con CuotasNotifier.hasPermission()
    private fun show(context: Context, notifId: Int, titulo: String, texto: String) {
        if (!CuotasNotifier.hasPermission(context)) return
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(context))
            .build()
        NotificationManagerCompat.from(context).notify(notifId, notification)
    }
}
