package com.example

import com.example.data.notifications.DedupDeMontos
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Los bancos actualizan la misma notificación varias veces y cada actualización vuelve a disparar
 * `onNotificationPosted`. Sin dedup, un solo pago generaba tres o cuatro avisos idénticos.
 */
class DedupDeMontosTest {

    private val ventana = 2 * 60 * 1000L

    @Test
    fun elMismoMontoDelMismoBancoDentroDeLaVentanaNotificaUnaSolaVez() {
        val dedup = DedupDeMontos(ventana)
        assertTrue(dedup.aceptar("com.banco", 5000.0, 0L))
        assertFalse(dedup.aceptar("com.banco", 5000.0, 1_000L))
        assertFalse(dedup.aceptar("com.banco", 5000.0, ventana - 1))
    }

    @Test
    fun pasadaLaVentanaVuelveAAceptar() {
        val dedup = DedupDeMontos(ventana)
        assertTrue(dedup.aceptar("com.banco", 5000.0, 0L))
        assertTrue(dedup.aceptar("com.banco", 5000.0, ventana))
    }

    @Test
    fun montosDistintosYBancosDistintosSonEventosDistintos() {
        val dedup = DedupDeMontos(ventana)
        assertTrue(dedup.aceptar("com.banco", 5000.0, 0L))
        // Dos compras seguidas de importes distintos: las dos valen.
        assertTrue(dedup.aceptar("com.banco", 5001.0, 100L))
        // Y el mismo importe desde otra billetera también.
        assertTrue(dedup.aceptar("com.otro", 5000.0, 100L))
    }

    @Test
    fun unRelojQueSaltaHaciaAtrasNoDejaLaDeteccionMuda() {
        val dedup = DedupDeMontos(ventana)
        assertTrue(dedup.aceptar("com.banco", 5000.0, 10 * ventana))
        // El teléfono corrigió la hora hacia atrás: la entrada vieja quedó "en el futuro". Debe
        // seguir aceptando en vez de bloquear el importe para siempre.
        assertTrue(dedup.aceptar("com.banco", 5000.0, 0L))
    }
}
