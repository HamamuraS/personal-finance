package com.example

import androidx.compose.ui.graphics.Color
import com.example.data.Usuario
import com.example.data.UsuariosConfig
import com.example.ui.theme.DEFAULT_USER_COLOR_PRESET
import com.example.ui.theme.USER_COLOR_PRESETS
import com.example.ui.theme.colorIdDePersona
import com.example.ui.theme.presetOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests puros (sin Android) del modelo de usuarios parametrizables: [UsuariosConfig], presets de
 * color y la decisión de color por persona. Cubre §14 de features/usuarios-parametrizables.md.
 */
class UsuariosConfigTest {

    // -------------------- UsuariosConfig: helpers --------------------

    @Test
    fun byKeyEsCaseInsensitiveYNombreDeCaeAlSlotKey() {
        val cfg = UsuariosConfig.DEFAULT
        assertEquals("Santiago", cfg.byKey("santiago")?.slotKey)
        assertEquals("Rocío", cfg.byKey("Rocío")?.slotKey)
        assertNull(cfg.byKey("Marcos"))
        // nombreDe cae al propio slotKey si no reconoce (dato viejo/corrupto).
        assertEquals("Marcos", cfg.nombreDe("Marcos"))
        assertEquals("Santiago", cfg.nombreDe("Santiago"))
    }

    @Test
    fun elOtroDevuelveLaContraparte() {
        val cfg = UsuariosConfig.DEFAULT
        assertEquals("Rocío", cfg.elOtro("Santiago").slotKey)
        assertEquals("Santiago", cfg.elOtro("Rocío").slotKey)
        // Una clave desconocida no es el primario → devuelve el primario (no rompe).
        assertEquals("Santiago", cfg.elOtro("desconocido").slotKey)
    }

    // -------------------- UsuariosConfig.fromList: robustez / fallback --------------------

    @Test
    fun fromListVacioONuloCaeADefault() {
        assertEquals(UsuariosConfig.DEFAULT, UsuariosConfig.fromList(emptyList()))
        assertEquals(UsuariosConfig.DEFAULT, UsuariosConfig.fromList(null))
    }

    @Test
    fun fromListParcialConservaSlotKeyYOrdenCanonicos() {
        // Solo llega el secundario, con orden "raro"; el primario cae al default.
        val cfg = UsuariosConfig.fromList(listOf(Usuario("Rocío", "Rochi", "violet", 9)))
        assertEquals("Santiago", cfg.primario.slotKey)
        assertEquals("Santiago", cfg.primario.nombre)   // default
        assertEquals("green", cfg.primario.colorId)     // default
        assertEquals(0, cfg.primario.orden)
        // Del secundario se adopta nombre y color, pero slotKey/orden quedan canónicos.
        assertEquals("Rocío", cfg.secundario.slotKey)
        assertEquals("Rochi", cfg.secundario.nombre)
        assertEquals("violet", cfg.secundario.colorId)
        assertEquals(1, cfg.secundario.orden)
    }

    @Test
    fun fromListConNombreOColorVacioCaeAlDefaultDeEseCampo() {
        val cfg = UsuariosConfig.fromList(listOf(Usuario("Santiago", "", "", 0)))
        assertEquals("Santiago", cfg.primario.nombre)  // ifBlank -> default
        assertEquals("green", cfg.primario.colorId)    // ifBlank -> default
    }

    // -------------------- Retrocompat: renombrar no rompe el match ni migra datos --------------------

    @Test
    fun renombrarSoloCambiaLaEtiquetaNoElSlotKey() {
        // "Santiago" -> "Santi": un Movement viejo con responsable="Santiago" sigue asociado al
        // slot primario; el slotKey (clave interna que ve el motor/los datos) no cambia.
        val cfg = UsuariosConfig.fromList(
            listOf(
                Usuario("Santiago", "Santi", "green", 0),
                Usuario("Rocío", "Ro", "blue", 1),
            )
        )
        assertEquals("Santi", cfg.nombreDe("Santiago"))
        assertEquals("Santiago", cfg.byKey("Santiago")?.slotKey)
        assertEquals(0, cfg.primario.orden)
    }

    // -------------------- Presets de color --------------------

    @Test
    fun losDosUsuariosPorDefectoNoCompartenPreset() {
        assertNotEquals(
            UsuariosConfig.DEFAULT.primario.colorId,
            UsuariosConfig.DEFAULT.secundario.colorId
        )
    }

    @Test
    fun losIdsDePresetSonUnicosYPresetOfTieneFallback() {
        val ids = USER_COLOR_PRESETS.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        // id inexistente -> preset por defecto
        assertEquals(DEFAULT_USER_COLOR_PRESET.id, presetOf("no-existe").id)
        // los dos slotKeys por defecto resuelven a presets existentes
        assertEquals("green", presetOf(UsuariosConfig.DEFAULT.primario.colorId).id)
        assertEquals("blue", presetOf(UsuariosConfig.DEFAULT.secundario.colorId).id)
    }

    @Test
    fun cadaPresetTieneContrasteSuficienteEnClaroYOscuro() {
        // Umbral 3.0 (WCAG AA para texto grande / objetos gráficos): estos colores son rellenos de
        // acento usados con texto en negrita e íconos.
        USER_COLOR_PRESETS.forEach { p ->
            val light = p.resolve(false)
            val dark = p.resolve(true)
            assertTrue(
                "preset ${p.id} claro tiene contraste bajo (${contrast(light.brand, light.on)})",
                contrast(light.brand, light.on) >= 3.0
            )
            assertTrue(
                "preset ${p.id} oscuro tiene contraste bajo (${contrast(dark.brand, dark.on)})",
                contrast(dark.brand, dark.on) >= 3.0
            )
        }
    }

    // -------------------- personaColor parametrizado: identidad estable --------------------

    @Test
    fun elActivoSePintaConSuColor() {
        val cfg = UsuariosConfig.DEFAULT
        assertEquals("green", colorIdDePersona("Santiago", "Santiago", cfg))
        assertEquals("blue", colorIdDePersona("Rocío", "Rocío", cfg))
        // Case-insensitive (los datos históricos son canónicos, pero por robustez).
        assertEquals("blue", colorIdDePersona("rocío", "Rocío", cfg))
    }

    @Test
    fun elColorDeUnaPersonaEsEstableAnteElUsuarioActivo() {
        // Para un mismo slot, el color visto NO debe depender de quién esté logueado.
        val cfg = UsuariosConfig.DEFAULT
        assertEquals("green", colorIdDePersona("Santiago", "Santiago", cfg))
        assertEquals("green", colorIdDePersona("Santiago", "Rocío", cfg))
        assertEquals("blue", colorIdDePersona("Rocío", "Santiago", cfg))
        assertEquals("blue", colorIdDePersona("Rocío", "Rocío", cfg))
    }

    @Test
    fun unaClaveDesconocidaSePintaConElColorDelOtro() {
        // Igual que cuando esto salía del `tertiary` del tema: lo que no es el activo, es "el otro".
        val cfg = UsuariosConfig.DEFAULT
        assertEquals("blue", colorIdDePersona("Ambos", "Santiago", cfg))
        assertEquals("green", colorIdDePersona("Ambos", "Rocío", cfg))
    }

    @Test
    fun conUsuarioReemplazaPorSlotYNoPisaConUnoDesconocido() {
        val cfg = UsuariosConfig.DEFAULT
        val ro = cfg.secundario.copy(nombre = "Ro", colorId = "rose")
        assertEquals(ro, cfg.conUsuario(ro).secundario)
        assertEquals(cfg.primario, cfg.conUsuario(ro).primario)
        assertEquals(cfg, cfg.conUsuario(Usuario("Otro", "X", "amber", 5)))
        assertEquals(listOf(cfg.secundario), cfg.otros("Santiago"))
    }

    // -------------------- helpers de test --------------------

    /** Contraste WCAG entre dos colores sRGB (1..21). */
    private fun contrast(a: Color, b: Color): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun relativeLuminance(c: Color): Double {
        fun lin(channel: Float): Double {
            val s = channel.toDouble()
            return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * lin(c.red) + 0.7152 * lin(c.green) + 0.0722 * lin(c.blue)
    }
}
