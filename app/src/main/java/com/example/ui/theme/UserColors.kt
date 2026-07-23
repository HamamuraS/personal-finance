package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Rol del tema con el que se pinta una persona: su color activo ([PRIMARY]) o el del otro ([TERTIARY]). */
enum class PersonaThemeRole { PRIMARY, TERTIARY }

/**
 * Decisión pura (testeable, sin Compose) de con qué rol del tema se pinta una persona.
 *
 * [MyApplicationTheme] pone el color del usuario activo en `primary` y el del otro en `tertiary`.
 * Por eso, para que el color de una persona sea **estable** sin importar quién esté logueado, basta
 * preguntar "¿este slot es el del usuario activo?": si sí, `primary` (que ya es su color); si no,
 * `tertiary` (que es el color del otro, o sea de esta persona cuando ella no es la activa).
 *
 * Antes esto se decidía comparando contra el literal "Rocío"; ahora se parametriza comparando el
 * [slotKey] contra el del usuario activo. La lógica es idéntica.
 */
fun personaThemeRole(slotKey: String, currentUserKey: String): PersonaThemeRole =
    if (slotKey.equals(currentUserKey, ignoreCase = true)) PersonaThemeRole.PRIMARY
    else PersonaThemeRole.TERTIARY

/**
 * Color de identidad de una persona, **estable** sin importar quién sea el usuario activo.
 *
 * Regla práctica:
 *   - Si el acento representa a una **persona concreta** en una vista donde puede aparecer
 *     cualquiera de las dos → `personaColor(slotKey, currentUserKey)`.
 *   - Si representa "vos" / la acción principal → `MaterialTheme.colorScheme.primary` directo
 *     (ya es el color del usuario activo por la construcción del tema).
 *
 * [slotKey] y [currentUserKey] son claves internas de slot ("Santiago"/"Rocío"), no nombres
 * visibles: el color sigue a la identidad estable, no a la etiqueta editable.
 */
@Composable
fun personaColor(slotKey: String, currentUserKey: String): Color =
    when (personaThemeRole(slotKey, currentUserKey)) {
        PersonaThemeRole.PRIMARY -> MaterialTheme.colorScheme.primary
        PersonaThemeRole.TERTIARY -> MaterialTheme.colorScheme.tertiary
    }
