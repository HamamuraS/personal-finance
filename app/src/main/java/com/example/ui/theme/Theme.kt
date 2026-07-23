package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.data.Usuario
import com.example.data.UsuariosConfig

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    secondary = DarkSecondary,
    tertiary = DarkTertiary,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = Color(0xFF111411),
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    error = DarkError
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    secondary = LightSecondary,
    tertiary = LightTertiary,
    background = LightBackground,
    surface = LightSurface,
    onPrimary = Color.White,
    onSecondary = Color(0xFF191C19),
    onBackground = Color(0xFF191C19),
    onSurface = Color(0xFF191C19),
    error = LightError
)

/**
 * Tema de la app, con el color de marca **parametrizado por usuario**.
 *
 * Invariante que se conserva del diseño anterior: el **usuario activo siempre es `primary`** ("tu
 * interfaz con tu color") y el otro es `tertiary`. Antes esto se lograba con un `isRocio` que
 * intercambiaba dos colores fijos; ahora se construye el `ColorScheme` desde los colores
 * **configurados** de cada usuario (ver UserColorPalette.kt). Todo el resto de la UI que usa
 * `primary`/`tertiary` para "vos vs. el otro" sigue funcionando sin cambios, y `personaColor`
 * (UserColors.kt) deshace la inversión donde hace falta pintar a una persona concreta.
 *
 * Con la config DEFAULT (Santiago verde / Rocío azul) el resultado es idéntico al histórico.
 */
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    activeUser: Usuario = UsuariosConfig.DEFAULT.primario,
    otherUser: Usuario = UsuariosConfig.DEFAULT.secundario,
    content: @Composable () -> Unit,
) {
    val base = if (darkTheme) DarkColorScheme else LightColorScheme
    val mine = presetOf(activeUser.colorId).resolve(darkTheme)   // "lo tuyo" → primary
    val yours = presetOf(otherUser.colorId).resolve(darkTheme)   // el otro   → tertiary

    val colorScheme = base.copy(
        primary = mine.brand,
        onPrimary = mine.on,
        tertiary = yours.brand,
        onTertiary = yours.on,
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
