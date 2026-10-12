package com.example.data.notifications

import android.content.Context
import com.example.ScreenTab
import com.example.util.Fechas
import com.example.util.Formato

/**
 * Muestra los recordatorios de cuotas (cierre de mes / atrasos) como notificaciones nativas.
 * No decide CUÁNDO notificar (eso es responsabilidad de [com.example.data.notifications.BackgroundSyncWorker]
 * o, para pruebas, de quien llame directamente con datos ya cargados) — solo arma y dispara.
 */
object CuotasNotifier {
    private const val NOTIF_ID_CIERRE = 1001
    private const val NOTIF_ID_ATRASO = 1002
    private const val REQUEST_CODE = 0

    /**
     * @param mes "yyyy-MM" del resumen que cierra.
     * @param yaCerro `true` cuando el aviso sale en la ventana de recuperación (el mes ya terminó y
     *        el worker no llegó a correr el último día). Cambia el texto para que no diga "este mes"
     *        cuando en realidad se está hablando del anterior.
     */
    fun notificarCierreDeMes(context: Context, total: Double, mes: String, yaCerro: Boolean = false) {
        val monto = Formato.dinero().format(total)
        val texto = if (yaCerro) {
            "Cerró ${Fechas.nombreDeMes(mes)} y te quedan $monto en cuotas por pagar en tus tarjetas 💳"
        } else {
            "Este mes te quedan $monto en cuotas por pagar en tus tarjetas 💳"
        }
        show(context, NOTIF_ID_CIERRE, "Cuotas de ${Fechas.nombreDeMes(mes)}", texto)
    }

    fun notificarAtrasos(context: Context, total: Double) {
        val texto = "Tenés ${Formato.dinero().format(total)} en cuotas atrasadas de meses anteriores 👀"
        show(context, NOTIF_ID_ATRASO, "Cuotas atrasadas", texto)
    }

    /** Al tocar la notificación, abre (o trae al frente) la app directo en la pestaña Cuotas. */
    private fun show(context: Context, notifId: Int, titulo: String, texto: String) =
        Notificaciones.mostrar(
            context, CanalDeNotificacion.CUOTAS, notifId, titulo, texto,
            Notificaciones.abrirEn(context, ScreenTab.CUOTAS, REQUEST_CODE)
        )
}
