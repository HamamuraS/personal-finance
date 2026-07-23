package com.example.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Color de usuario ya resuelto para un tema concreto: el relleno de marca ([brand]) y el color de
 * contenido legible encima ([on]).
 */
data class ResolvedUserColor(val brand: Color, val on: Color)

/**
 * Preset de color de usuario curado. En vez de dejar elegir un hex arbitrario (contraste frágil en
 * runtime), ofrecemos una lista corta de colores accesibles, cada uno con sus 4 variantes ya
 * afinadas (claro/oscuro × relleno/contenido) — igual que hoy están `GeoGreen*`/`GeoBlue*`/`GeoDark*`.
 *
 * Reglas: se evita el **rojo** (reservado a `error`/destructivo); los dos usuarios no pueden
 * compartir preset (se valida en la UI, ver SettingsScreen). Elegir un color = guardar su [id].
 */
data class UserColorPreset(
    val id: String,
    val label: String,   // etiqueta visible ("Verde")
    val light: Color,    // relleno de marca en tema claro
    val dark: Color,     // relleno de marca en tema oscuro
    val onLight: Color,  // contenido (texto/ícono) sobre el relleno claro
    val onDark: Color    // contenido sobre el relleno oscuro
) {
    fun resolve(darkTheme: Boolean): ResolvedUserColor =
        if (darkTheme) ResolvedUserColor(dark, onDark) else ResolvedUserColor(light, onLight)
}

// Contenido estándar sobre los rellenos: los `light` son oscuros → texto blanco; los `dark` son
// pasteles claros → texto casi-negro. Coincide con onPrimary del tema (blanco / 0xFF111411).
private val OnFillLight = Color.White
private val OnFillDark = Color(0xFF111411)

/**
 * Presets disponibles. Los dos primeros son exactamente los colores legacy (Santiago=verde,
 * Rocío=azul), reencuadrados como presets; el resto amplía la paleta sin chocar con el rojo de error.
 */
val USER_COLOR_PRESETS: List<UserColorPreset> = listOf(
    UserColorPreset("green", "Verde", GeoGreenPrimary, GeoDarkPrimary, OnFillLight, OnFillDark),
    UserColorPreset("blue", "Azul", GeoBlueTertiary, GeoDarkTertiary, OnFillLight, OnFillDark),
    UserColorPreset("violet", "Violeta", Color(0xFF6A4CA5), Color(0xFFC3B0E8), OnFillLight, OnFillDark),
    UserColorPreset("teal", "Turquesa", Color(0xFF176B63), Color(0xFF83CFC5), OnFillLight, OnFillDark),
    UserColorPreset("amber", "Ámbar", Color(0xFF8A6100), Color(0xFFE6C15C), OnFillLight, OnFillDark),
    UserColorPreset("rose", "Rosa", Color(0xFFB0345F), Color(0xFFE79BB4), OnFillLight, OnFillDark),
)

/** Preset por defecto si un id no se reconoce (dato viejo/corrupto). El verde histórico. */
val DEFAULT_USER_COLOR_PRESET: UserColorPreset = USER_COLOR_PRESETS.first()

/** Busca un preset por id, cayendo al [DEFAULT_USER_COLOR_PRESET] si no existe. */
fun presetOf(colorId: String): UserColorPreset =
    USER_COLOR_PRESETS.firstOrNull { it.id == colorId } ?: DEFAULT_USER_COLOR_PRESET
