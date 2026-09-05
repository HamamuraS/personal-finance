package com.example.data.notifications

/**
 * Filtro de repetidos para las detecciones de monto.
 *
 * Los bancos no publican una notificación y se olvidan: la **actualizan** (cambian el texto, suman
 * el detalle del comercio, la re-postean al abrir la app), y cada actualización vuelve a disparar
 * `onNotificationPosted`. Sin esto, un solo pago generaba tres o cuatro avisos idénticos.
 *
 * La clave es (package, monto) y la ventana [ventanaMillis]: dos pagos reales del mismo importe en
 * la misma app dentro de dos minutos se pierden uno, y es el trade-off correcto — pagar dos veces
 * lo mismo en dos minutos es raro, y que la app grite cuatro veces por cada compra es seguro.
 *
 * No es thread-safe por sí solo; el listener lo usa desde el hilo principal del servicio.
 */
class DedupDeMontos(private val ventanaMillis: Long = 2 * 60 * 1000L) {

    private val vistos = HashMap<String, Long>()

    /**
     * `true` si esta detección es nueva (y queda registrada), `false` si es un repetido dentro de
     * la ventana.
     */
    fun aceptar(packageName: String, monto: Double, ahora: Long): Boolean {
        purgar(ahora)
        val clave = "$packageName|$monto"
        val anterior = vistos[clave]
        if (anterior != null && !vencido(anterior, ahora)) return false
        vistos[clave] = ahora
        return true
    }

    /**
     * Una marca vence al pasar la ventana **o** si quedó en el futuro. Lo segundo pasa cuando el
     * reloj del teléfono salta hacia atrás (cambio de hora, sincronización de red): sin esa rama,
     * un importe podía quedar bloqueado hasta que el reloj alcanzara la marca vieja.
     */
    private fun vencido(marca: Long, ahora: Long): Boolean {
        val transcurrido = ahora - marca
        return transcurrido >= ventanaMillis || transcurrido < 0
    }

    /** Saca lo que ya venció, para que el mapa no crezca mientras el servicio esté vivo. */
    private fun purgar(ahora: Long) {
        val vencidos = vistos.filterValues { vencido(it, ahora) }.keys.toList()
        vencidos.forEach { vistos.remove(it) }
    }
}
