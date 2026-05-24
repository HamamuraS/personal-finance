package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

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

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    isRocio: Boolean = false,
    content: @Composable () -> Unit,
) {
    val baseColorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    
    // Invertir colores si el usuario es Rocío (Azul pasa a ser Primary, Verde a Tertiary)
    val colorScheme = if (isRocio) {
        baseColorScheme.copy(
            primary = baseColorScheme.tertiary,
            tertiary = baseColorScheme.primary,
            onPrimary = baseColorScheme.onPrimary
        )
    } else {
        baseColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
