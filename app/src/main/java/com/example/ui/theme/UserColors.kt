package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.data.UsuariosConfig

/**
 * Decisión pura (testeable, sin Compose) del color de identidad de una persona: el `colorId` con el
 * que se la pinta, **estable** sin importar quién sea el usuario activo.
 *
 * El activo se pinta con su color (el mismo `primary` del tema). Cualquier otra persona, con el suyo;
 * una clave que no corresponde a nadie cae al color del otro respecto del activo, que es lo que se
 * veía cuando esto se resolvía con el `tertiary` del tema.
 */
fun colorIdDePersona(slotKey: String, currentUserKey: String, usuarios: UsuariosConfig): String =
    if (slotKey.equals(currentUserKey, ignoreCase = true)) {
        (usuarios.byKey(currentUserKey) ?: usuarios.primario).colorId
    } else {
        (usuarios.byKey(slotKey) ?: usuarios.elOtro(currentUserKey)).colorId
    }

/**
 * Color de identidad de una persona, **estable** sin importar quién sea el usuario activo.
 *
 * Regla práctica:
 *   - Si el acento representa a una **persona concreta** en una vista donde puede aparecer
 *     cualquiera → `personaColor(slotKey, currentUserKey)`.
 *   - Si representa "vos" / la acción principal → `MaterialTheme.colorScheme.primary` directo
 *     (ya es el color del usuario activo por la construcción del tema).
 *
 * [slotKey] y [currentUserKey] son claves internas de slot ("Santiago"/"Rocío"), no nombres
 * visibles: el color sigue a la identidad estable, no a la etiqueta editable.
 */
@Composable
fun personaColor(slotKey: String, currentUserKey: String): Color =
    presetOf(colorIdDePersona(slotKey, currentUserKey, LocalUsuarios.current))
        .resolve(LocalIsDarkTheme.current).brand

/** Color legible encima del [personaColor] de esa persona (texto o ícono sobre un relleno suyo). */
@Composable
fun personaColorDeContenido(slotKey: String, currentUserKey: String): Color =
    presetOf(colorIdDePersona(slotKey, currentUserKey, LocalUsuarios.current))
        .resolve(LocalIsDarkTheme.current).on
