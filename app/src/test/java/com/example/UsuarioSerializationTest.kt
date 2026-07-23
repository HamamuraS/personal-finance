package com.example

import com.example.data.Usuario
import com.example.data.UsuariosConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Round-trip de serialización de [Usuario] con el MISMO Moshi que usa PreferencesHelper
 * (KotlinJsonAdapterFactory, por reflexión). Valida que el cache de usuarios y el transporte por red
 * preserven nombre/color/slotKey/orden. Es un test puro: el Robolectric de este entorno no provisiona
 * el SDK, así que se evita depender de un Context real.
 */
class UsuarioSerializationTest {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val listType = Types.newParameterizedType(List::class.java, Usuario::class.java)
    private val adapter = moshi.adapter<List<Usuario>>(listType)

    @Test
    fun roundTripPreservaTodosLosCampos() {
        val original = UsuariosConfig(
            primario = Usuario("Santiago", "Santi", "green", 0),
            secundario = Usuario("Rocío", "Ro", "violet", 1),
        ).todos

        val json = adapter.toJson(original)
        val leidos = adapter.fromJson(json)

        assertNotNull(leidos)
        assertEquals(original, leidos)
        // Reconstruido a config, mantiene el slotKey (clave interna) y adopta nombre/color.
        val cfg = UsuariosConfig.fromList(leidos)
        assertEquals("Santiago", cfg.primario.slotKey)
        assertEquals("Santi", cfg.primario.nombre)
        assertEquals("violet", cfg.secundario.colorId)
    }

    @Test
    fun jsonCorruptoNoRevientaAlCaerADefault() {
        // getUsersCache() devuelve [] ante JSON inválido; fromList([]) -> DEFAULT.
        val leidos = try {
            adapter.fromJson("no-es-json")
        } catch (e: Exception) {
            emptyList()
        }
        assertEquals(UsuariosConfig.DEFAULT, UsuariosConfig.fromList(leidos))
    }
}
