package com.example

import com.example.ui.components.saludoDeHoy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** El título de Inicio: un saludo y un emoji distinto por cada día de la semana. */
class SaludoDeHoyTest {

    private val dias = listOf(
        Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
        Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
    )
    private val nombres = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")

    @Test
    fun cadaDiaTieneSuNombreYUnEmojiDistinto() {
        val saludos = dias.map { saludoDeHoy(it) }
        saludos.zip(nombres).forEach { (saludo, nombre) ->
            assertTrue(saludo, saludo.startsWith("Hoy es $nombre "))
        }
        // Siete emojis distintos: lo que queda después del nombre del día.
        val emojis = saludos.zip(nombres).map { (saludo, nombre) -> saludo.removePrefix("Hoy es $nombre ") }
        assertEquals(7, emojis.distinct().size)
    }
}
