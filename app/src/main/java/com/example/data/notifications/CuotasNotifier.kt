package com.example.data.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.ScreenTab
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Muestra los recordatorios de cuotas (cierre de mes / atrasos) como notificaciones nativas.
 * No decide CUÁNDO notificar (eso es responsabilidad de [com.example.data.notifications.CuotasReminderWorker]
 * o, para pruebas, de quien llame directamente con datos ya cargados) — solo arma y dispara.
 */
object CuotasNotifier {
    const val CHANNEL_ID = "cuotas_recordatorios"
    private const val NOTIF_ID_CIERRE = 1001
    private const val NOTIF_ID_ATRASO = 1002

    fun formatMoney(): DecimalFormat = DecimalFormat("#,##0.00").apply {
        decimalFormatSymbols = DecimalFormatSymbols(Locale.US).apply {
            groupingSeparator = '.'
            decimalSeparator = ','
        }
        positivePrefix = "$ "
    }

    fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ActivityCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Recordatorios de cuotas",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Avisos de cuotas por pagar y atrasadas" }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /**
     * @param mes "yyyy-MM" del resumen que cierra.
     * @param yaCerro `true` cuando el aviso sale en la ventana de recuperación (el mes ya terminó y
     *        el worker no llegó a correr el último día). Cambia el texto para que no diga "este mes"
     *        cuando en realidad se está hablando del anterior.
     */
    fun notificarCierreDeMes(context: Context, total: Double, mes: String, yaCerro: Boolean = false) {
        val monto = formatMoney().format(total)
        val texto = if (yaCerro) {
            "Cerró ${mesLabel(mes)} y te quedan $monto en cuotas por pagar en tus tarjetas 💳"
        } else {
            "Este mes te quedan $monto en cuotas por pagar en tus tarjetas 💳"
        }
        show(context, NOTIF_ID_CIERRE, "Cuotas de ${mesLabel(mes)}", texto)
    }

    /** "2026-07" -> "julio". Si no es parseable, devuelve la entrada tal cual. */
    fun mesLabel(mes: String): String {
        val nombres = listOf(
            "enero", "febrero", "marzo", "abril", "mayo", "junio",
            "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
        )
        val n = mes.split("-").getOrNull(1)?.toIntOrNull() ?: return mes
        return nombres.getOrNull(n - 1) ?: mes
    }

    fun notificarAtrasos(context: Context, total: Double) {
        val texto = "Tenés ${formatMoney().format(total)} en cuotas atrasadas de meses anteriores 👀"
        show(context, NOTIF_ID_ATRASO, "Cuotas atrasadas", texto)
    }

    /** Al tocar la notificación, abre (o trae al frente) la app directo en la pestaña Cuotas. */
    private fun contentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_TAB, ScreenTab.CUOTAS.name)
        }
        return PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    @SuppressLint("MissingPermission") // chequeado explícitamente arriba con hasPermission()
    private fun show(context: Context, notifId: Int, titulo: String, texto: String) {
        if (!hasPermission(context)) return
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
