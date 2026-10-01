package com.example

import com.example.data.Usuario
import com.example.data.UsuariosConfig
import com.example.data.mensajeDelDiaVisible
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Mensaje del día (v7.8): lo genera el Apps Script y viaja en la hoja Usuarios. Sin mensaje válido
 * de hoy, Inicio tiene que quedar exactamente como antes.
 */
class MensajeDelDiaTest {

    private val hoy = "2026-09-30"
    private val santi = Usuario("Santiago", "Santi", "green", 0,
        mensaje = "¡Debe haber estado bueno ese café Martínez! ☕", mensajeFecha = hoy)

    @Test
    fun seMuestraSoloSiEsDeHoyYEstaActivo() {
        assertEquals(santi.mensaje, mensajeDelDiaVisible(santi, hoy, activo = true))
        assertNull("apagado por .env", mensajeDelDiaVisible(santi, hoy, activo = false))
        assertNull("de ayer: el trigger no corrió", mensajeDelDiaVisible(santi, "2026-10-01", activo = true))
        assertNull("sin mensaje", mensajeDelDiaVisible(santi.copy(mensaje = "  "), hoy, activo = true))
        assertNull("sin usuario", mensajeDelDiaVisible(null, hoy, activo = true))
    }

    @Test
    fun fromListConservaElMensaje() {
        val cfg = UsuariosConfig.fromList(listOf(santi))
        assertEquals(santi.mensaje, cfg.primario.mensaje)
        assertEquals(hoy, cfg.primario.mensajeFecha)
        assertEquals("", cfg.secundario.mensaje)
    }

    @Test
    fun elCacheViejoSinMensajeSigueLeyendose() {
        // JSON de usuarios guardado por versiones anteriores (y el de un script sin la v7.8).
        val adapter = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            .adapter<List<Usuario>>(Types.newParameterizedType(List::class.java, Usuario::class.java))
        val viejo = """[{"slotKey":"Santiago","nombre":"Santi","colorId":"green","orden":0}]"""
        val leido = adapter.fromJson(viejo)!!.single()
        assertEquals("", leido.mensaje)
        assertNull(mensajeDelDiaVisible(leido, hoy, activo = true))
    }
}
