package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Geometric Balance Theme Palette
// Nota: GeoGreen*/GeoBlue*/GeoDark* ya no están atados a "Santiago"/"Rocío": son la fuente de los
// dos primeros presets de usuario (ver UserColorPalette.kt). El tema (Theme.kt) arma primary/tertiary
// desde el preset elegido por cada usuario, no desde estas constantes directamente.
val GeoGreenPrimary = Color(0xFF386B3F) // Deep Sage Green (preset "green" — slot primario histórico)
val GeoBlueTertiary = Color(0xFF4A607A) // Slate Blue / Navy (preset "blue" — slot secundario histórico)

val GeoDarkText = Color(0xFF191C19) // Deep obsidian charcoal/near black
val GeoBackground = Color(0xFFF7FAF6) // Warm sage white/mint-gray
val GeoSurface = Color(0xFFFFFFFF) // Pure white
val GeoCardBg = Color(0xFFE8F3E9) // Soft mint green background for main card
val GeoProgressTrack = Color(0xFFC2CDC1) // Track color

val GeoRedError = Color(0xFFD32F2F)

// Dark Theme Variants mapping beautifully
val GeoDarkPrimary = Color(0xFF8BBA91)
val GeoDarkTertiary = Color(0xFFA5C3DE)
val GeoDarkBackground = Color(0xFF1E211E)
val GeoDarkSurface = Color(0xFF282C28)

// Colores del tema claro
val LightPrimary = GeoGreenPrimary
val LightSecondary = Color(0xFF5D625C)
val LightTertiary = GeoBlueTertiary
val LightBackground = GeoBackground
val LightSurface = GeoSurface
val LightError = GeoRedError

// Colores del tema oscuro
val DarkPrimary = GeoDarkPrimary
val DarkSecondary = Color(0xFFBBC9BA)
val DarkTertiary = GeoDarkTertiary
val DarkBackground = GeoDarkBackground
val DarkSurface = GeoDarkSurface
val DarkError = GeoRedError

