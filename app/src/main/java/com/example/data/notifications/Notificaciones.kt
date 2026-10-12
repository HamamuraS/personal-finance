package com.example.data.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
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

/**
 * Canales de notificación de la app. El [id] es la identidad del canal en Android (los ajustes que
 * el usuario hizo por canal cuelgan de él): no se cambia.
 */
enum class CanalDeNotificacion(val id: String, val nombre: String, val descripcion: String) {
    CUOTAS("cuotas_recordatorios", "Recordatorios de cuotas", "Avisos de cuotas por pagar y atrasadas"),
    MOVIMIENTOS("movimientos", "Movimientos registrados", "Confirmación de los movimientos guardados en la planilla")
}

/**
 * Lo común a todas las notificaciones: permiso, canal, y el toque que abre la app en una pestaña.
 * Cada notificador ([CuotasNotifier], [MovementNotifier]) solo decide el texto.
 */
internal object Notificaciones {

    fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ActivityCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun ensureChannel(context: Context, canal: CanalDeNotificacion) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(canal.id, canal.nombre, NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = canal.descripcion }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /**
     * Al tocar la notificación, abre (o trae al frente) la app en [tab]. [requestCode] distinto por
     * notificador: con `FLAG_UPDATE_CURRENT`, dos intents con el mismo código se pisarían el extra
     * de la pestaña.
     */
    fun abrirEn(context: Context, tab: ScreenTab, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            // SINGLE_TOP y no CLEAR_TOP: con CLEAR_TOP la Activity viva se destruía y se recreaba en
            // vez de recibir `onNewIntent`, perdiendo el ViewModel y con él los borradores del alta.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_TAB, tab.name)
        }
        return PendingIntent.getActivity(
            context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun builder(context: Context, canal: CanalDeNotificacion): NotificationCompat.Builder {
        ensureChannel(context, canal)
        return NotificationCompat.Builder(context, canal.id).setSmallIcon(R.mipmap.ic_launcher)
    }

    /** Muestra un aviso de texto (con texto expandible). Sin permiso, no hace nada. */
    @SuppressLint("MissingPermission") // chequeado explícitamente con hasPermission()
    fun mostrar(
        context: Context,
        canal: CanalDeNotificacion,
        notifId: Int,
        titulo: String,
        texto: String,
        contentIntent: PendingIntent
    ) {
        if (!hasPermission(context)) return
        val notification: Notification = builder(context, canal)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        NotificationManagerCompat.from(context).notify(notifId, notification)
    }
}
