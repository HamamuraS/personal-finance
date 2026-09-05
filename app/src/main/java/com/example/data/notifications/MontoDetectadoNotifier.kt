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

/**
 * El aviso propio de la app cuando [BilleterasNotificationListener] detecta un importe: "Detectamos
 * un movimiento por $X". Al tocarlo se abre la pestaña Nuevo con el monto ya cargado y el resto del
 * formulario en blanco.
 *
 * Canal propio, separado del de movimientos y del de cuotas, para que se pueda silenciar sin perder
 * los otros dos: es el canal con más chance de molestar.
 */
object MontoDetectadoNotifier {

    const val CHANNEL_ID = "montos_detectados"
    private const val NOTIF_ID = 2003

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Montos detectados",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Avisos con el importe detectado en las notificaciones de tus billeteras" }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission") // chequeado con CuotasNotifier.hasPermission()
    fun notificar(context: Context, monto: Double) {
        if (!CuotasNotifier.hasPermission(context)) return
        ensureChannel(context)
        val montoFormateado = CuotasNotifier.formatMoney().format(monto)
        val texto = "Tocá para registrarlo: se abre el alta con el importe ya cargado y elegís vos el resto."
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("💸 Detectamos un movimiento por $montoFormateado")
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(context, monto))
            .build()
        // Id fijo: siempre interesa el último importe detectado, no la pila de todos los del día.
        NotificationManagerCompat.from(context).notify(NOTIF_ID, notification)
    }

    /**
     * Abre la pestaña Nuevo sembrando el monto.
     *
     * `SINGLE_TOP` (y `launchMode="singleTop"` en el manifest) en vez de `CLEAR_TOP`: con
     * `CLEAR_TOP` la Activity se destruía y se recreaba en lugar de recibir `onNewIntent`, lo que se
     * llevaba puesto el ViewModel y con él cualquier borrador a medio escribir. Justo en esta
     * notificación eso sería peor que en las otras: es la que se toca **mientras** se está cargando
     * un movimiento.
     */
    private fun contentIntent(context: Context, monto: Double): PendingIntent {
        val montoTexto = if (monto % 1.0 == 0.0) monto.toLong().toString() else monto.toString()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_TAB, ScreenTab.NUEVO.name)
            putExtra(MainActivity.EXTRA_MONTO_SUGERIDO, montoTexto)
        }
        return PendingIntent.getActivity(
            context,
            // Request code derivado del monto: si no, `FLAG_UPDATE_CURRENT` sobre un request code
            // fijo reusaría el PendingIntent viejo y abriría el alta con el importe anterior.
            montoTexto.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
