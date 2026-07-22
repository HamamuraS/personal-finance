package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Color de identidad de una persona, **estable** sin importar quién sea el usuario activo.
 *
 * [MyApplicationTheme] intercambia `primary` ↔ `tertiary` cuando el usuario activo es Rocío, para
 * que "lo tuyo" siempre caiga en `primary`. En una vista **compartida** (que muestra a ambas
 * personas a la vez) tomar `primary`/`tertiary` a secas pintaría a la otra persona con el color
 * equivocado en cuanto cambia el usuario logueado. Este helper deshace esa inversión para que:
 *   - Santiago == verde (primary base)
 *   - Rocío    == azul  (tertiary base)
 * se mantengan constantes en toda la app, esté logueado quien esté.
 *
 * Regla práctica:
 *   - Si el acento representa a una **persona concreta** en una vista donde puede aparecer
 *     cualquiera de las dos → `personaColor(persona, currentUser)`.
 *   - Si representa "vos" / la acción principal → `MaterialTheme.colorScheme.primary` directo
 *     (ya es el color del usuario activo por la inversión del tema).
 *
 * NOTA (parametrización futura): hoy la identidad se resuelve por nombre contra "Rocío". Cuando los
 * usuarios sean configurables (ver features/usuarios-parametrizables.md), este helper debería
 * recibir el color de cada usuario desde la config y dejar de comparar strings.
 */
@Composable
fun personaColor(persona: String, currentUser: String): Color {
    val userRocio = currentUser.equals("Rocío", ignoreCase = true)
    val esRocio = persona.equals("Rocío", ignoreCase = true)
    return if (esRocio) {
        if (userRocio) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    } else {
        if (userRocio) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    }
}
