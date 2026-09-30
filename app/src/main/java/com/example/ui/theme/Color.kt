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
//
// v7.7.1: el claro pasó de verde-gris frío a **cálido y pastel** (crema, arena, durazno). Se
// mantuvo la regla de contraste: todo texto >= 4.5:1 contra el fondo más oscuro sobre el que va
// (surfaceVariant), y el blanco sobre los rellenos de marca >= 5.5:1.
// ---------------------------------------------------------------------------------------------

// Colores de marca de los presets de usuario (ver UserColorPalette.kt). No son "de Santiago" ni
// "de Rocío": son la fuente de los dos primeros presets. El tema arma primary/tertiary desde el
// preset elegido por cada usuario, no desde estas constantes.
// En claro son tonos algo más suaves que los originales (salvia y azul empolvado) para ir con la
// paleta cálida; siguen dando ~5.8:1 sobre las tarjetas y ~5.9:1 con texto blanco encima.
val GeoGreenPrimary = Color(0xFF3E6E4A) // Salvia (preset "green")
val GeoBlueTertiary = Color(0xFF4A6788) // Azul empolvado (preset "blue")

val GeoDarkPrimary = Color(0xFF8BBA91)
val GeoDarkTertiary = Color(0xFFA5C3DE)

// --- Tema claro -------------------------------------------------------------------------------
// Lienzo crema, tarjetas en blanco cálido (no blanco puro, que al lado del crema se ve azulado) y
// contenedores color arena. Los grises son grises tostados, no verdes: todo tira a cálido.

val LightBackground = Color(0xFFF5ECDF)      // lienzo crema (algo más tostado que las tarjetas, para que se despeguen)
val LightSurface = Color(0xFFFFFCF7)         // tarjetas, blanco cálido
val LightSurfaceVariant = Color(0xFFF1E2D0)  // arena: tarjeta del pozo, chips no seleccionados
val LightSurfaceContainer = Color(0xFFF3E7D8) // contenedores intermedios (headers, celdas)
val LightOnSurface = Color(0xFF241D19)       // texto principal, marrón muy oscuro — 16:1
val LightOnSurfaceVariant = Color(0xFF51463E) // texto secundario — 8.9:1 sobre tarjeta, 7.2:1 sobre arena
val LightOutline = Color(0xFF72655A)         // texto atenuado y bordes de foco — 5.5:1 sobre tarjeta
val LightOutlineVariant = Color(0xFFD6C4AF)  // bordes de tarjeta y divisores
val LightError = Color(0xFFB03A2E)           // terracota — 5.9:1 sobre tarjeta
val LightErrorContainer = Color(0xFFFCE3DC)
val LightOnErrorContainer = Color(0xFF45130D)
val LightSecondary = Color(0xFF6B5D51)
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
