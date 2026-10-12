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
 * Config de usuarios vigente, publicada por [MyApplicationTheme] para que [personaColor] pueda
 * resolver el color de **cualquier** persona sin depender de que sean exactamente dos.
 */
val LocalUsuarios = staticCompositionLocalOf { UsuariosConfig.DEFAULT }

/**
 * Tema de la app, con el color de marca **parametrizado por usuario**.
 *
 * Invariante: el **usuario activo siempre es `primary`** ("tu interfaz con tu color"). `tertiary` es
 * el color del otro, que la UI usa como acento de "lo que no es tuyo" (transferencias, tarjetas).
 * Para pintar a una persona concreta se usa [personaColor], que resuelve su propio color.
 */
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    usuarios: UsuariosConfig = UsuariosConfig.DEFAULT,
    currentUserKey: String = UsuariosConfig.DEFAULT.primario.slotKey,
    content: @Composable () -> Unit,
) {
    val base = if (darkTheme) DarkColorScheme else LightColorScheme
    val activo = usuarios.byKey(currentUserKey) ?: usuarios.primario
    val mine = presetOf(activo.colorId).resolve(darkTheme)                                // "lo tuyo" → primary
    val yours = presetOf(usuarios.elOtro(currentUserKey).colorId).resolve(darkTheme)    // el otro   → tertiary

    val colorScheme = base.copy(
        primary = mine.brand,
        onPrimary = mine.on,
        tertiary = yours.brand,
        onTertiary = yours.on,
    )

    CompositionLocalProvider(LocalIsDarkTheme provides darkTheme, LocalUsuarios provides usuarios) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
