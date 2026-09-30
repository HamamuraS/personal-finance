package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.data.Usuario
import com.example.data.UsuariosConfig

/**
 * ¿Está activo el tema oscuro? Antes cada pantalla lo deducía comparando
 * `MaterialTheme.colorScheme.background == DarkBackground`, que es cierto por casualidad: alcanza
 * con que el fondo del tema claro coincida con esa constante para que toda la UI se pinte al revés.
 * Ahora lo publica el propio tema, que es el único que lo sabe.
 */
val LocalIsDarkTheme = staticCompositionLocalOf { true }

/**
 * Texto/ícono deliberadamente atenuado (una cuota futura, un placeholder, una etiqueta menor).
 * Es un color real y no un `onSurface.copy(alpha = 0.35f)`: ese alpha sobre blanco daba 2.3:1 y
 * volvía invisible al sol justo lo que igual hay que poder leer. Este llega a 4.6:1 en claro.
 */
val appTextMuted: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.outline

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = Color(0xFF111411),
    secondary = DarkSecondary,
    onSecondary = Color(0xFF111411),
    tertiary = DarkTertiary,
    onTertiary = Color(0xFF111411),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceVariant,
    surfaceContainerLow = DarkSurface,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = DarkError,
    onError = Color.White,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    scrim = Color.Black,
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    secondary = LightSecondary,
    onSecondary = Color.White,
    tertiary = LightTertiary,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceVariant,
    surfaceContainerLow = LightSurface,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = LightError,
    onError = Color.White,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    scrim = LightScrim,
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

    CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
