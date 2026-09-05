package com.example.data.notifications

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.example.data.PreferencesHelper

/**
 * Escucha las notificaciones de las billeteras para ahorrarse la parte más laboriosa del alta:
 * tipear el importe.
 *
 * **Qué lee y qué no.** De la notificación se extrae únicamente el monto ([MontoParser]). No se
 * lee comercio, ni contraparte, ni si es gasto o ingreso, ni categoría — todo eso lo sigue eligiendo
 * el usuario a mano. El texto no se guarda, no se loguea y no sale del teléfono: se parsea en
 * memoria, se saca un número y se descarta. El monto llega a la planilla solo si el usuario después
 * confirma el alta.
 *
 * **De qué apps.** Solo las de [PAQUETES_BANCARIOS] más las que el usuario haya agregado desde el
 * modo descubrimiento. Cualquier otra se descarta en la primera línea de [onNotificationPosted],
 * antes de tocarle el contenido. El chequeo de package es lo primero justamente para que "no leer"
 * sea una propiedad del código y no una promesa.
 *
 * **Es opcional.** La app funciona completa sin este permiso; encenderlo requiere pasar por la
 * pantalla de divulgación y después habilitarlo a mano en los Ajustes de Android (no hay diálogo
 * runtime para el acceso a notificaciones).
 */
class BilleterasNotificationListener : NotificationListenerService() {

    private val dedup = DedupDeMontos()

    override fun onNotificationPosted(notificacion: StatusBarNotification?) {
        val paquete = notificacion?.packageName ?: return

        // La propia app queda afuera siempre: si no, la notificación "Detectamos un movimiento por
        // $X" se leería a sí misma y se retroalimentaría en loop.
        if (paquete == packageName) return

        val prefs = PreferencesHelper(applicationContext)
        if (!prefs.deteccionMontosActiva) return

        val descubriendo = prefs.descubrimientoActivo()
        if (!descubriendo && !esBillieteraConocida(paquete, prefs)) return

        // Recién acá se toca el contenido, y solo los dos campos de texto visibles.
        val extras = notificacion.notification?.extras ?: return
        val texto = listOfNotNull(
            extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
        ).joinToString(" ")

        val monto = MontoParser.primerMonto(texto) ?: return   // sin monto, silencio

        val ahora = System.currentTimeMillis()
        if (!dedup.aceptar(paquete, monto, ahora)) return

        // Lo único que se persiste: el package (para el modo descubrimiento) y la fecha (para que
        // una muerte silenciosa del servicio se note en Ajustes). Del texto no queda nada.
        prefs.ultimoPackageDetectado = paquete
        prefs.ultimaDeteccionAt = ahora

        // En modo descubrimiento no se notifica desde apps desconocidas: el objetivo ahí es ver el
        // package en Ajustes, no llenar de avisos por cualquier notificación con un número.
        if (descubriendo && !esBillieteraConocida(paquete, prefs)) return

        MontoDetectadoNotifier.notificar(applicationContext, monto)
    }

    override fun onNotificationRemoved(notificacion: StatusBarNotification?) { /* sin acción */ }

    private fun esBillieteraConocida(paquete: String, prefs: PreferencesHelper): Boolean =
        paquete in PAQUETES_BANCARIOS || paquete in prefs.packagesBancariosExtra

    companion object {

        /**
         * Lista curada de billeteras. Es una lista **en código y a mano** porque la alternativa
         * (escanear las apps instaladas) exige `QUERY_ALL_PACKAGES`, un permiso restringido en Play
         * que habría que justificar ante revisión para una comodidad como esta.
         *
         * Los nombres de package cambian entre versiones y países, así que hay que tomarlos como
         * un punto de partida: si una billetera no dispara nada, el modo descubrimiento de Ajustes
         * muestra el package real de su última notificación con monto y permite agregarlo. Lo que
         * se agregue ahí queda en [PreferencesHelper.packagesBancariosExtra] y vale igual que esta
         * lista.
         */
        val PAQUETES_BANCARIOS = setOf(
            // Ualá
            "com.uala",
            "ar.com.uala",
            // Santander Argentina
            "ar.com.santanderrio.movil",
            "com.santanderrio.movil",
            // BBVA Argentina
            "com.bbva.nxt_argentina",
            // Naranja X
            "com.tarjetanaranja.mobile",
            "com.naranja.naranjax",
        )

        /** Nombre visible de cada billetera soportada, para los textos de Ajustes y la divulgación. */
        val BILLETERAS_SOPORTADAS = listOf("Ualá", "Santander", "BBVA", "Naranja X")

        /**
         * ¿Le dio el usuario acceso a las notificaciones a esta app? No hay permiso runtime: se
         * habilita a mano en los Ajustes de Android, así que el único chequeo posible es preguntarle
         * al sistema por su lista de listeners habilitados.
         */
        fun tienePermiso(context: Context): Boolean =
            context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

        /**
         * Pide que Android vuelva a bindear el servicio. Se llama al abrir la app porque el listener
         * puede quedar muerto tras una actualización del APK o una matanza por optimización de
         * batería de algunos fabricantes, y el sistema no siempre lo revive solo. Es idempotente y
         * no hace nada si el permiso no está dado.
         */
        fun pedirRebind(context: Context) {
            if (!tienePermiso(context)) return
            runCatching {
                requestRebind(ComponentName(context, BilleterasNotificationListener::class.java))
            }
        }
    }
}
