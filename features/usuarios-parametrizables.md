# Usuarios Parametrizables — Plan de implementación

> Estado: **Fases 1 y 2 implementadas** (2026-07-22). Compila (`assembleDebug` OK) y los tests puros
> pasan (`UsuariosConfigTest`, `UsuarioSerializationTest`). Fase 3 sigue fuera de alcance. Pendiente
> operativo: **desplegar la v7.0 del `google-apps-script.js`** en el spreadsheet real para que el
> modo nube responda `GET_USERS`/`PUT user` (en modo demo ya funciona todo local). Queda como
> cleanup opcional el helper `rememberCardColors()` de §10.3 (no afecta la feature).
> Última actualización del plan original: 2026-07-22.
>
> Objetivo de este documento: planear cómo dejar de tener "Santiago" y "Rocío" con colores
> hardcodeados y pasar a **dos usuarios configurables** (nombre + color elegibles), con una
> **identidad persistente** ("ya sé que sos vos al entrar") y un Ajustes que muestra *quién sos*
> con selector de color y logout — **sin romper los datos existentes** y empezando a **emprolijar**
> el código, que ya creció bastante.

---

## 1. Objetivo y alcance

- **Elegir el nombre** de cada usuario y **el color** asociado a su ícono y a la interfaz.
- Al abrir la app, que **ya sepa quién sos** (identidad recordada); en Ajustes no se elige "quién
  soy" sino que se **ve** el nombre propio, se puede **cambiar el color** y **cerrar sesión**.
- Fuente de verdad de los usuarios en una **hoja nueva `Usuarios`** del spreadsheet actual.
- **Se mantienen exactamente 2 usuarios** (ver la restricción de §2). "Santiago", "Rocío",
  "Marcos"… son **etiquetas configurables** de esos dos lugares, no usuarios nuevos.
- Preparar la base para **parametrizar** el resto del código (sacar los literales `"Santiago"`/
  `"Rocío"` desparramados por la UI).

**No-objetivos (por ahora):** soportar 3+ usuarios reales; login con contraseña/cuenta; permisos.

---

## 2. Restricción fundamental: la app es de **dos** usuarios

El motor contable (`AccountingEngine`, ver [accounting-engine-model]) modela la **propiedad cruzada
entre dos partes** con un único acumulador con signo (`netSantiagoEnRocio`): "cuánta plata neta
tiene la persona A en las cuentas de la persona B". Esa estructura es **intrínsecamente binaria**:
saldos, gasto común 50/50, transferencias y devoluciones están todos escritos alrededor de "yo vs.
el otro".

Conclusión de diseño: **parametrizamos nombre y color, no la cantidad.** Hay siempre **dos lugares
(slots)** de usuario. Generalizar a N usuarios sería reescribir el motor (propiedad cruzada N×N) y
queda explícitamente **fuera de alcance** (se menciona como Fase 3 opcional).

---

## 3. Qué está hardcodeado hoy (inventario)

Para dimensionar el "emprolijar", esto es lo que hoy asume los nombres/colores fijos:

| Lugar | Qué asume |
|-------|-----------|
| `ui/theme/Color.kt` | `GeoGreenPrimary` (Santiago), `GeoBlueTertiary` (Rocío) + variantes dark |
| `ui/theme/Theme.kt` | `MyApplicationTheme(isRocio: Boolean)` invierte `primary`↔`tertiary` si el activo es Rocío |
| `ui/theme/UserColors.kt` | `personaColor(persona, currentUser)` compara contra `"Rocío"` (¡ya centralizado! ver §7) |
| `data/PreferencesHelper.kt` | `currentUserProfile` default `"Santiago"` |
| `ui/components/SettingsScreen.kt` | selector "¿Quién está usando la app?" con `"Santiago"`/`"Rocío"` |
| `AddMovementScreen`, `DashboardScreen`, `ReportsScreen`, `CuotasScreen` | literales `"Santiago"`/`"Rocío"`, filtros `Todos/Santiago/Rocío`, títulos "Gastos de Santiago", tarjetas sugeridas `"Visa Santiago"/"BBVA Rocío"/"Ualá Rocío"` |
| **Datos almacenados** | `Movement.responsable`, `Movement.propietario`, `CuotaPlan.propietario` guardan el **nombre** (`"Santiago"`/`"Rocío"`) |
| `AccountingEngine` | lógica escrita en términos de Santiago/Rocío (`netSantiagoEnRocio`, `santiago*`/`rocio*`) |

El punto delicado es la última fila: **los datos y el motor usan el nombre como clave**. De ahí la
idea central de §4.

---

## 4. Idea central del diseño: `slotKey` estable + nombre/color como **dato**

El truco para permitir "renombrarme" **sin migrar datos ni tocar el motor**:

> Separar la **clave interna** (estable, opaca) del **nombre visible** (editable).

- Cada usuario tiene un **`slotKey` inmutable** que **es el nombre legacy**: `"Santiago"` y `"Rocío"`.
  Es lo que se sigue guardando en `Movement`/`CuotaPlan` y lo que consume el motor. **Nunca cambia.**
- El **`nombre` visible** es un campo aparte, editable, que vive en la config de usuarios. La UI
  muestra `nombre`; el modelo y el motor siguen viendo `slotKey`.
- El **color** también es dato editable, asociado al `slotKey`.

Así, renombrar "Santiago" → "Santi" solo cambia una etiqueta de presentación: los movimientos
históricos (`responsable = "Santiago"`) siguen matcheando, el motor no se entera, y **no hay
migración**. El `slotKey` queda como un identificador interno opaco (deuda legacy tolerable, se
puede neutralizar en la Fase 3 opcional).

```
slotKey "Santiago"  ── nombre: "Santi"   color: verde     (slot PRIMARY)
slotKey "Rocío"     ── nombre: "Ro"      color: violeta   (slot SECONDARY)
        │                    │                 │
        │                    │                 └── alimenta el tema (primary/tertiary) y personaColor
        │                    └── lo que ve el usuario en toda la UI
        └── lo que se guarda en Movement.responsable/propietario y CuotaPlan.propietario (SIN CAMBIOS)
```

---

## 5. Modelo de datos

### 5.1 Nueva entidad `Usuario` (nuevo archivo `data/Usuario.kt`)

```kotlin
data class Usuario(
    val slotKey: String,      // "Santiago" | "Rocío"  — INMUTABLE, clave interna/legacy
    val nombre: String,       // etiqueta visible, editable ("Santi", "Marcos", …)
    val colorId: String,      // id de preset de color (ver §5.4), ej. "green" | "violet"
    val orden: Int = 0        // 0 = slot primario (verde histórico), 1 = secundario
)
```

Se guarda un **`colorId`** (preset curado) en vez de un hex libre: garantiza contraste en claro/oscuro
sin cálculos frágiles en runtime (ver §5.4 y §7). Un hex arbitrario queda como alternativa futura.

### 5.2 Config en memoria: `UsuariosConfig`

Un pequeño holder con los dos usuarios y helpers, para que la UI no vuelva a hablar de "Santiago"
literal:

```kotlin
data class UsuariosConfig(val primario: Usuario, val secundario: Usuario) {
    val todos get() = listOf(primario, secundario)
    fun byKey(slotKey: String): Usuario? = todos.firstOrNull { it.slotKey == slotKey }
    fun nombreDe(slotKey: String): String = byKey(slotKey)?.nombre ?: slotKey
    fun elOtro(slotKey: String): Usuario = if (primario.slotKey == slotKey) secundario else primario

    companion object {
        // Fallback cableado: la app NUNCA queda sin usuarios (primer arranque / offline / hoja vacía).
        val DEFAULT = UsuariosConfig(
            primario   = Usuario("Santiago", "Santiago", "green",  0),
            secundario = Usuario("Rocío",    "Rocío",    "blue",   1),
        )
    }
}
```

### 5.3 Layout de la hoja `Usuarios` (Google Sheets)

| Col | Campo | Notas |
|-----|-------|-------|
| A | slotKey | `"Santiago"` / `"Rocío"` — **no editable**, clave |
| B | nombre | etiqueta visible, editable |
| C | colorId | preset de color (§5.4) |
| D | orden | 0 primario / 1 secundario |

**Seed inicial (clave para retrocompatibilidad):** dos filas con `slotKey`=nombre legacy y sus
colores actuales (`green`, `blue`). Como el `slotKey` coincide con lo que ya está en los datos,
**todo sigue matcheando sin migrar nada**.

### 5.4 Presets de color (`ui/theme/UserColorPalette.kt`)

En vez de un color arbitrario, una lista curada de colores accesibles, cada uno con sus 4 variantes
ya afinadas (como hoy están `GeoGreen*`/`GeoBlue*`/`GeoDark*`):

```kotlin
data class UserColorPreset(
    val id: String,            // "green", "blue", "violet", "teal", "amber", "rose"
    val light: Color,          // brand en tema claro   (= hoy GeoGreenPrimary / GeoBlueTertiary)
    val dark: Color,           // brand en tema oscuro   (= hoy GeoDarkPrimary / GeoDarkTertiary)
    val onLight: Color,        // texto/ícono sobre relleno en claro (normalmente blanco)
    val onDark: Color          // ídem en oscuro (normalmente 0xFF111411)
)
```

- Se evita el **rojo** (reservado a `error`/destructivo) y colores que choquen con él.
- Elegir un color = guardar su `id`. Extensible agregando presets.
- Regla anti-choque: los dos usuarios **no pueden compartir preset** (validar al elegir; ver §13).

---

## 6. Backend (Apps Script) — `google-apps-script.js`

Constante nueva: `const USERS_SHEET = "Usuarios";` (análoga a `PLANS_SHEET`).

### 6.1 Exclusión obligatoria (⚠️ misma lección que `Planes`)

La hoja `Usuarios` **no contiene movimientos**. Hay que **saltarla** en los tres recorridos que hoy
iteran hojas:

1. `doGet` de movimientos (loop `sheets.forEach`, línea ~67).
2. `getPlans` → construcción del `paidMap` (loop de movimientos, línea ~115).
3. `handleLogicalDelete` (loop `for (let sheet of sheets)`, línea ~350).

```js
if (sheet.getName() === PLANS_SHEET || sheet.getName() === USERS_SHEET) return; // o continue
```

> Si se omite, las filas de usuarios se leerían como movimientos y romperían los balances.

### 6.2 Endpoints de usuarios

**Lectura — `doGet` con `action=GET_USERS`:** devuelve las 2 filas de `Usuarios`. Si la hoja no
existe, devolver `{status:"SUCCESS", users:[]}` (el cliente cae al `DEFAULT`).

**Escritura — `doPost` con `entity:"user"`:** `PUT` busca por `slotKey` (col A) y actualiza `nombre`
(B) y `colorId` (C). **No hay POST/DELETE** de usuarios: siempre son 2 filas fijas, sembradas la
primera vez (por el seed o por un `PUT` que crea la hoja+filas si faltan). `slotKey` es inmutable.

---

## 7. Theming dinámico (el corazón del color parametrizable)

Hoy `MyApplicationTheme(isRocio)` intercambia dos colores **fijos**. Se generaliza a **construir el
`ColorScheme` desde los colores configurados de los dos usuarios**:

```kotlin
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean,
    activeUser: Usuario,          // reemplaza a isRocio
    otherUser: Usuario,
    content: @Composable () -> Unit,
) {
    val base = if (darkTheme) DarkColorScheme else LightColorScheme
    val mine  = presetOf(activeUser.colorId).resolve(darkTheme)   // "lo tuyo" → primary
    val yours = presetOf(otherUser.colorId).resolve(darkTheme)    // el otro   → tertiary
    val scheme = base.copy(
        primary  = mine.brand,
        tertiary = yours.brand,
        onPrimary = mine.on,        // contraste correcto para el color elegido
    )
    MaterialTheme(colorScheme = scheme, typography = Typography, content = content)
}
```

- Se conserva la invariante actual: **el usuario activo siempre es `primary`** ("tu interfaz con tu
  color"), el otro es `tertiary`. Todo el resto de la UI que usa `primary`/`tertiary` para "vos vs.
  el otro" sigue funcionando sin cambios.
- **`personaColor` (ya centralizado en `ui/theme/UserColors.kt` en el trabajo de consistencia de
  color) solo cambia su fuente de verdad:** en vez de comparar contra `"Rocío"`, decide con el
  `orden`/`slotKey` del usuario activo. Firma sugerida:

  ```kotlin
  @Composable
  fun personaColor(slotKey: String, config: UsuariosConfig, currentUserKey: String): Color
  // esRocio/userRocio  →  "¿este slotKey es el del usuario activo?"; si sí primary, si no tertiary
  ```

  La lógica ("deshacer la inversión del tema para que el color de cada persona sea invariante a
  quién esté logueado") es idéntica; solo se parametriza el criterio. **Todas las pantallas ya la
  usan** (Dashboard/Reports/Cuotas/Settings/AddMovement), así que el color parametrizado se propaga
  solo al cambiar este helper + el tema.
- `Color.kt`: `GeoGreen*`/`GeoBlue*`/`GeoDark*` pasan a ser **dos de los presets** de §5.4 (no se
  borran; se reencuadran).

---

## 8. Identidad / "login" liviano

No es autenticación: es **recordar cuál de los 2 slots sos** en este dispositivo.

- **Persistencia (`PreferencesHelper`):** evolucionar `currentUserProfile` → sigue guardando el
  **`slotKey`** del usuario activo (mismo espacio de valores: `"Santiago"`/`"Rocío"`), más un flag
  `hasChosenIdentity` (o `slotKey` nulo/vacío = "todavía no eligió").
- **Primer arranque / post-logout:** pantalla **"¿Quién sos?"** con los 2 usuarios (nombre + color +
  avatar con inicial). Al elegir, se guarda el `slotKey` y no se vuelve a preguntar.
- **Entradas siguientes:** la app arranca directo sabiendo quién sos (lee el `slotKey` de prefs).
- **Ajustes (rediseño de la tarjeta de perfil):**
  - Muestra **"Sos {nombre}"** (identidad, no un selector).
  - **Editar mi nombre** (campo de texto; escribe `nombre` del slot vía `PUT` user).
  - **Elegir mi color** (grilla de presets §5.4; escribe `colorId`; aplica al instante al tema).
  - **Cerrar sesión**: limpia el `slotKey` de prefs → vuelve a la pantalla "¿Quién sos?".
  - Se **elimina** el actual selector "¿Quién está usando la app?" (ProfileButton x2).

> Nota: el color/nombre son **compartidos** (viven en la hoja, la ve el otro también) — coherente
> con la filosofía de identidad estable de `personaColor`. Editás **tu** perfil; el otro edita el
> suyo desde su dispositivo.

---

## 9. Capa de datos y ViewModel (espejo del módulo de cuotas)

### 9.1 `SheetsService.kt`
Extender `WebAppRequest` con `entity:"user"` + `user: Usuario?`; respuesta `GET_USERS` con
`users: List<Usuario>?`.

### 9.2 `AhorroRepository.kt`
- `fetchUsuarios(webAppUrl): UsuariosConfig` — arma `action=GET_USERS`, cachea; si falla/está vacío
  → `UsuariosConfig.DEFAULT`.
- `cachedUsuarios(): UsuariosConfig` — cache sin red, para arranque instantáneo (patrón idéntico a
  `cachedPendingPlans()`, ya existente).
- `updateUsuario(webAppUrl, usuario): Boolean` — `PUT entity:"user"`; actualiza el cache local.

### 9.3 `PreferencesHelper.kt`
- `KEY_USERS_CACHE` con adapter Moshi de `UsuariosConfig` (mismo patrón que planes).
- `currentUserProfile` (ya existe) pasa a interpretarse como **slotKey activo**; agregar
  `hasChosenIdentity`.

### 9.4 `AhorroViewModel.kt`
- `_usuarios: StateFlow<UsuariosConfig>` (default `DEFAULT`); cargar cache al instante en
  `loadConfigAndData()` y refrescar en segundo plano (igual que planes/movimientos).
- `_activeUser` / `_otherUser` derivados de `currentUserProfile` + `_usuarios`.
- `setIdentity(slotKey)`, `logout()`, `updateMiNombre(nombre)`, `updateMiColor(colorId)`.
- `MainActivity` alimenta `MyApplicationTheme(activeUser, otherUser)` desde estos flows.

---

## 10. UI

### 10.1 Pantalla "¿Quién sos?" (nueva)
Se muestra cuando `!hasChosenIdentity`. Dos tarjetas grandes (nombre + color + inicial). Reusa el
estilo de `ProfileButton` actual. Al elegir → `setIdentity`.

### 10.2 Ajustes — tarjeta de perfil (rediseño)
Como §8: "Sos {nombre}", editar nombre, grilla de colores, logout. El resto de Ajustes queda igual.

### 10.3 Emprolijar: sacar los literales `"Santiago"`/`"Rocío"` (Fase 2)
Reemplazar en las 5 pantallas los literales por la config:
- Filtros `listOf("Todos","Santiago","Rocío")` → `listOf("Todos", primario.nombre, secundario.nombre)`
  (mostrando `nombre`, filtrando por `slotKey`).
- Títulos "Gastos de Santiago"/"de Rocío" → `"Gastos de ${config.nombreDe(slotKey)}"`.
- `equals("Rocío")` / `== "Santiago"` → comparaciones por `slotKey` vía helpers de `UsuariosConfig`.
- Chips de tarjetas sugeridas `"Visa Santiago"/"BBVA Rocío"/"Ualá Rocío"` → derivarlas del nombre, o
  moverlas a config propia (ver §13).
- **Centralizar la "receta de card" repetida** (`isDark`/`cardBg`/`cardBorder`/`textMain`) en un
  helper `@Composable rememberCardColors()` en `ui/theme/` — hoy está copiada ~15 veces. (Es
  independiente del usuario, pero es la otra mitad del "emprolijar".)

---

## 11. Contrato de API (resumen)

| Acción | Método | Request (JSON) | Respuesta |
|--------|--------|----------------|-----------|
| Leer usuarios | GET | `?action=GET_USERS` | `{status, users:[{slotKey,nombre,colorId,orden}]}` |
| Editar mi perfil | POST | `{action:"PUT", entity:"user", user:{slotKey, nombre, colorId}}` | `{status, message}` |

---

## 12. Fases

### Fase 1 — Fundación: `Usuario` como dato + color + identidad (este documento, detallada)
Modelo `Usuario`/`UsuariosConfig` + presets de color, hoja `Usuarios` (con exclusiones ⚠️ y seed),
pipeline repo/VM/cache, **tema dinámico** desde colores config, `personaColor` releído de la config,
**identidad persistente** (picker + Ajustes self-profile + logout). **Cero migración de datos**
(gracias al `slotKey` = nombre legacy). Riesgo bajo.

### Fase 2 — Parametrizar la UI (emprolijar)
Sacar todos los literales `"Santiago"`/`"Rocío"` de las pantallas y usar `UsuariosConfig`; unificar
filtros; centralizar la receta de card. Mecánico, sin cambio de comportamiento, gran ganancia de
legibilidad. Riesgo bajo.

### Fase 3 — (Opcional/futuro) IDs neutros y N usuarios
Neutralizar el `slotKey` (`"Santiago"`→`"u1"`) con una **migración de datos** (nuevo action
`RENAME_KEY` que reescribe `responsable`/`propietario`/`plan.propietario` en todas las hojas) y, si
alguna vez se quisiera N>2, **reescribir `AccountingEngine`** para propiedad cruzada N×N (ver §2 —
es el trabajo grande y por eso queda fuera). Riesgo alto; solo si hace falta.

---

## 13. Bordes y consideraciones

- **Retrocompat / sin migración:** el seed con `slotKey` = nombre legacy es lo que hace que los datos
  existentes sigan válidos. Es la decisión que sostiene todo el plan.
- **Fallback robusto:** hoja ausente, offline, primer arranque, JSON inválido → `UsuariosConfig.DEFAULT`.
  La app nunca queda sin usuarios ni sin colores.
- **Modo demo:** los usuarios viven en prefs con el `DEFAULT`; editar nombre/color persiste local.
- **Choque de color:** impedir que los dos elijan el mismo preset (deshabilitar el preset ya usado por
  el otro en la grilla, o mostrar aviso). Si no, se pierde la distinción visual entre las dos personas.
- **Rename seguro:** al ser `nombre` solo etiqueta, renombrar es instantáneo y sin efectos en datos ni
  motor. (El costo: el `slotKey` interno sigue diciendo "Santiago"/"Rocío" — deuda opaca, aceptable.)
- **Concurrencia:** cada quien edita su propio slot; los `PUT` no colisionan (distinta fila). El otro
  ve el cambio al refrescar.
- **Tarjetas sugeridas** (`TARJETAS_SUGERIDAS` en `CuotasScreen`): hoy incluyen el nombre ("Visa
  Santiago"). Si se renombra, quedan desactualizadas. Opciones: (a) derivarlas del `nombre` actual,
  (b) volverlas configurables en una fase futura. Documentado, no bloqueante.
- **Límite de 2 usuarios:** es del **motor**, no de la UI. Dejarlo escrito para que nadie intente
  "agregar un tercero" desde la hoja `Usuarios` sin reescribir el motor.
- **Base ya construida:** `personaColor` está centralizado en `ui/theme/UserColors.kt` (trabajo de
  consistencia de color, item 0). Este plan se apoya en eso: cambia su fuente de verdad, no su uso.

---

## 14. Testing

- **`UsuariosConfig`** (JUnit puro): `byKey`, `nombreDe` (con fallback al slotKey), `elOtro`,
  parseo/serialización, fallback a `DEFAULT` ante datos corruptos.
- **Presets de color:** cada preset resuelve las 4 variantes; contraste `on*` sobre `light`/`dark`
  por encima de un umbral; los dos usuarios nunca comparten preset.
- **`personaColor` parametrizado:** identidad estable — para un mismo `slotKey`, el color no cambia
  según quién sea el usuario activo (test del helper con ambos usuarios activos).
- **Identidad/VM:** `setIdentity`/`logout` mutan prefs y flows correctamente; sin identidad → picker.
- **Retrocompat:** un `Movement` viejo con `responsable="Santiago"` sigue asociado al slot correcto
  tras renombrar el `nombre` a "Santi".

---

## 15. Checklist Fase 1

- [x] `data/Usuario.kt` (+ `UsuariosConfig`) y `ui/theme/UserColorPalette.kt` (presets)
- [x] `google-apps-script.js`: `USERS_SHEET`, exclusión en los 3 loops (⚠️), `GET_USERS`, `PUT` user, seed (v7.0)
- [x] `SheetsService.kt`: request/response de usuarios (`user`, `users`, `getUsers`)
- [x] `AhorroRepository.kt`: `fetchUsuarios`, `cachedUsuarios`, `updateUsuario`
- [x] `PreferencesHelper.kt`: `KEY_USERS_CACHE`; `currentUserProfile` como slotKey + `hasChosenIdentity`
- [x] `AhorroViewModel.kt`: `_usuarios`/`activeUser`/`otherUser`, `setIdentity`, `logout`, `updateMiNombre`, `updateMiColor`; carga de cache instantánea
- [x] `ui/theme/Theme.kt`: `MyApplicationTheme(activeUser, otherUser)` (reemplaza `isRocio`)
- [x] `ui/theme/UserColors.kt`: `personaColor` por `slotKey` (con `personaThemeRole` puro y testeable).
      Nota: no toma `UsuariosConfig` porque el tema ya deja el color del activo en `primary` y el del
      otro en `tertiary`; alcanza con comparar slotKeys. Cumple el objetivo (sin literal "Rocío").
- [x] `MainActivity.kt`: alimentar el tema; gate de identidad (picker si `!hasChosenIdentity`)
- [x] Pantalla "¿Quién sos?" (`IdentityPickerScreen`) + rediseño de la tarjeta de perfil (`PerfilCard`) en `SettingsScreen`

## 15b. Fase 2 (hecho, salvo cleanup de card)

- [x] Literales `"Santiago"`/`"Rocío"` fuera de las 5 pantallas: display por `config.nombreDe(...)`,
      lógica/filtros/`personaColor` por `slotKey`. Filtros de persona unificados (valor=slotKey, label=nombre).
- [x] Tarjetas sugeridas de cuotas derivadas del nombre (`tarjetasSugeridas(config)`).
- [ ] **Pendiente (opcional):** helper `rememberCardColors()` en `ui/theme/` para la "receta de card"
      repetida (§10.3). Es cosmético, ortogonal a la feature y con variantes por pantalla; se deja
      como cleanup aparte para no inflar el cambio.

## 16. Archivos afectados (referencia rápida)

**Nuevos:** `data/Usuario.kt`, `ui/theme/UserColorPalette.kt`, pantalla identidad (en
`ui/components/`). **Modificados:** `google-apps-script.js`, `SheetsService.kt`, `AhorroRepository.kt`,
`PreferencesHelper.kt`, `AhorroViewModel.kt`, `ui/theme/Theme.kt`, `ui/theme/Color.kt`,
`ui/theme/UserColors.kt`, `MainActivity.kt`, `SettingsScreen.kt`. **Fase 2:** los 5 componentes de
`ui/components/` (sacar literales) + helper de card en `ui/theme/`.
