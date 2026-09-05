package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------------------------
// Paleta de la app.
//
// El tema claro se rediseñó desde cero (v0.7.6): la versión anterior era el tema oscuro con el
// fondo dado vuelta — fondo casi blanco, tarjetas blancas sin borde perceptible (#E2E8F0 sobre
// blanco es 1.2:1) y todo el texto secundario resuelto con `onSurface.copy(alpha = 0.5f)`, que
// sobre blanco da ~3.3:1: por debajo del 4.5:1 que pide WCAG AA y, con tipografías de 10-11sp,
// directamente ilegible al sol.
//
// El criterio nuevo: **una escala de grises propia por tema**, con roles Material 3 completos
// (surface / surfaceVariant / onSurfaceVariant / outline / outlineVariant) en vez de alphas sobre
// onSurface. Cada rol de texto se eligió contra su fondo real, no contra blanco puro.
// ---------------------------------------------------------------------------------------------

// Colores de marca de los presets de usuario (ver UserColorPalette.kt). No son "de Santiago" ni
// "de Rocío": son la fuente de los dos primeros presets. El tema arma primary/tertiary desde el
// preset elegido por cada usuario, no desde estas constantes.
val GeoGreenPrimary = Color(0xFF2F6137) // Deep Sage Green (preset "green")  — 6.4:1 sobre blanco
val GeoBlueTertiary = Color(0xFF3F5871) // Slate Blue / Navy (preset "blue") — 6.7:1 sobre blanco

val GeoDarkPrimary = Color(0xFF8BBA91)
val GeoDarkTertiary = Color(0xFFA5C3DE)

// --- Tema claro -------------------------------------------------------------------------------
// Fondo levemente teñido de verde-gris para que las tarjetas BLANCAS se despeguen sin depender del
// borde; el borde además existe de verdad (#C7D0C5, 1.9:1 sobre blanco: visible sin gritar).

val LightBackground = Color(0xFFEFF2ED)      // lienzo
val LightSurface = Color(0xFFFFFFFF)         // tarjetas
val LightSurfaceVariant = Color(0xFFDFE5DC)  // contenedores/chips no seleccionados
val LightSurfaceContainer = Color(0xFFE7EBE3) // contenedores intermedios (headers, celdas)
val LightOnSurface = Color(0xFF151812)       // texto principal  — 17.6:1 sobre blanco
val LightOnSurfaceVariant = Color(0xFF414A3F) // texto secundario — 9.6:1 sobre blanco, 7.3:1 sobre surfaceVariant
val LightOutline = Color(0xFF6C756A)         // bordes de control activo/foco — 4.6:1 sobre blanco
val LightOutlineVariant = Color(0xFFC7D0C5)  // bordes de tarjeta y divisores
val LightError = Color(0xFFAE2016)           // 6.2:1 sobre blanco (el #D32F2F anterior daba 4.0:1)
val LightErrorContainer = Color(0xFFFFDAD5)
val LightOnErrorContainer = Color(0xFF410002)
val LightSecondary = Color(0xFF4E574C)
val LightScrim = Color(0xFF000000)

// --- Tema oscuro ------------------------------------------------------------------------------

val DarkBackground = Color(0xFF141714)      // lienzo
val DarkSurface = Color(0xFF1E221E)
val DarkSurfaceVariant = Color(0xFF2C312B)
val DarkSurfaceContainer = Color(0xFF262A25)
val DarkOnSurface = Color(0xFFE6EBE3)
val DarkOnSurfaceVariant = Color(0xFFC2CBBF)  // 10.3:1 sobre el fondo oscuro
val DarkOutline = Color(0xFF8B948A)
val DarkOutlineVariant = Color(0xFF3B423A)
// El rojo del tema oscuro NO se tocó: se usa además como relleno de badges con texto blanco
// encima (ver `onResalt` en CuotasScreen), así que subirlo de tono lo habría roto.
val DarkError = Color(0xFFD32F2F)
val DarkErrorContainer = Color(0xFF3A1512)
val DarkOnErrorContainer = Color(0xFFFFDAD6)
val DarkPrimary = GeoDarkPrimary
val DarkSecondary = Color(0xFFBBC9BA)
val DarkTertiary = GeoDarkTertiary

// Alias que conserva el nombre histórico usado por el resto de la UI.
val LightPrimary = GeoGreenPrimary
val LightTertiary = GeoBlueTertiary
