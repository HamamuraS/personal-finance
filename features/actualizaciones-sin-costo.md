# Actualizaciones sin costo (sin reinstalar el APK a mano)

> Estado: **Nivel 1 implementado en código** ✅ (2026-07-28) — falta solo la parte manual (crear el
> proyecto de Firebase, generar la keystore, cargar los secrets en GitHub). Guía paso a paso en
> [GUIA-ACTUALIZACIONES.md](../GUIA-ACTUALIZACIONES.md).
> Última actualización: 2026-07-28.

## 1. El problema

Hoy el flujo es: Santiago cambia algo → compila el APK localmente → se lo comparte a Rocío (por
WhatsApp/Drive/lo que sea) → Rocío lo descarga y lo reinstala a mano. Cada cambio, por chico que
sea, repite todo el ciclo manual.

Pregunta: ¿se puede lograr que Rocío reciba las actualizaciones sin que Santiago tenga que
compartirle el archivo cada vez, **sin costo**?

**Respuesta corta: sí, es factible y sin gastar nada.** Hay tres niveles de solución, de menor a
mayor esfuerzo de implementación, con distinto nivel de "automágico" resultante. Se recomienda el
**Nivel 1** para resolver el dolor real ahora mismo con el mínimo esfuerzo.

## 2. Nivel 1 (recomendado): GitHub Actions + Firebase App Distribution

**Costo: $0.** Ambos servicios son gratuitos sin límites relevantes para un uso de 2 personas.

### Cómo queda el flujo una vez armado

1. Santiago hace `git push` (o crea un tag `v7.1`, etc.) en el repo (ya existe en
   `github.com/HamamuraS/personal-finance`).
2. Un workflow de **GitHub Actions** (gratis para repos — 2000 min/mes en cuentas gratuitas, de
   sobra para este proyecto) compila el APK de release automáticamente.
3. El mismo workflow sube el APK a **Firebase App Distribution** (gratis, sin límite de testers ni
   de builds en el plan Spark).
4. Rocío, que instaló **una única vez** la app "Firebase App Tester" (o aceptó la invitación por
   link), recibe una **notificación push** en su teléfono: "Hay una versión nueva disponible" →
   toca → instala. **Sin que Santiago le mande nada a mano.**

Santiago deja de estar en el medio del todo: compilar y compartir se automatizan; a Rocío le queda
un solo toque ("Actualizar") en vez de todo el ciclo manual actual.

### Qué hay que armar

1. **Registrar la app Android en un proyecto de Firebase** (puede ser el mismo que ya existe —
   ver nota en §5 — o uno nuevo) para obtener `google-services.json`. Hoy el proyecto **no** tiene
   este archivo ni el plugin de Firebase para Android — hay que agregarlos.
2. **Firebase CLI** (`firebase appdistribution:distribute`) o el **plugin de Gradle
   `firebase-appdistribution`**, configurado con las credenciales de servicio (un
   `GOOGLE_APPLICATION_CREDENTIALS` / token, guardado como *secret* de GitHub, nunca en el repo).
3. **Un workflow `.github/workflows/release.yml`** que, en cada push a `main` (o cada tag), corra
   `./gradlew assembleRelease` y luego `./gradlew appDistributionUploadRelease`.
4. **Invitar a Rocío como tester** una sola vez desde la consola de Firebase (con su email) —
   después de aceptar la invitación la primera vez, todas las builds futuras le llegan solas.
5. **Firma de release:** ya existe `signingConfigs.release` en `app/build.gradle.kts`, que lee
   `KEYSTORE_PATH` / `STORE_PASSWORD` / `KEY_PASSWORD` de variables de entorno — el workflow de CI
   los toma de *secrets* de GitHub (nunca comitear la keystore de release ni sus contraseñas).

### Por qué este nivel y no otro

- Resuelve el 100% del dolor descrito ("tener que compartirle el APK") con la menor cantidad de
  código nuevo: nada de lógica dentro de la app, todo vive en configuración de CI + un servicio ya
  gratuito.
- No requiere publicar la app en ningún lado público (Play Store, F-Droid): sigue siendo 100%
  privada, solo entre los dos.
- Firebase App Distribution está pensado exactamente para este caso de uso ("testers internos").

## 3. Nivel 2: actualización dentro de la app (self-hosted, más código)

Si más adelante se quisiera que la propia app (sin depender de otra app de testers) le avise a
Rocío "hay una versión nueva" con un botón "Actualizar" adentro de Ajustes:

- **Publicar cada release como un GitHub Release** (gratis) con el APK como asset adjunto.
- **En la app**, un chequeo periódico (o al abrir Ajustes) que golpea la API pública de GitHub
  (`GET /repos/HamamuraS/personal-finance/releases/latest`, gratis y sin autenticación **si el
  repo es público**; si es privado, hace falta un token con permisos de lectura, más fricción) y
  compara `versionCode` contra `BuildConfig.VERSION_CODE`.
- Si hay una versión más nueva: descarga el APK (`DownloadManager` o `OkHttp` a un archivo en
  `cacheDir`) y dispara el instalador del sistema
  (`Intent(Intent.ACTION_VIEW)` con `FileProvider` + `application/vnd.android.package-archive`),
  que le pide a Rocío el mismo permiso "Instalar apps desconocidas" que ya usa hoy al instalar el
  APK compartido a mano — no se puede evitar ese toque de confirmación sin ser una app de
  administración de dispositivo (fuera de alcance).
- Requiere el permiso `REQUEST_INSTALL_PACKAGES` en el manifest.

**Ventaja sobre el Nivel 1:** todo pasa dentro de la propia app (no hace falta que Rocío tenga
instalada otra app de testers). **Desventaja:** bastante más código a mantener (descarga, progreso,
manejo de errores de red, permisos) para un beneficio marginal sobre el Nivel 1 — se documenta como
posible evolución futura, no como lo que se recomienda implementar ahora.

## 4. Nivel 3: Google Play (Internal Testing track)

La opción con mejor UX final —actualizaciones **silenciosas**, sin que Rocío tenga que tocar nada,
igual que cualquier otra app de la Play Store— pero con una salvedad de costo:

- Requiere una **cuenta de Google Play Console**, que tiene un **pago único de USD 25** (no
  recurrente) si Santiago todavía no tiene una. Estrictamente no es "$0", aunque tampoco es un
  costo recurrente.
- Además de la cuenta, Play exige completar un cuestionario de contenido, política de privacidad,
  etc. incluso para un track de **pruebas internas** (privado, hasta 100 testers por email) — más
  trámite inicial que los niveles 1 y 2.
- A cambio, las actualizaciones son las más "automágicas" posibles: Play las baja y aplica solo,
  Rocío ni se entera de que hubo un cambio.

**No se recomienda como primer paso** por el costo de entrada y el trámite, pero queda documentado
como la opción a considerar si en el futuro se quisiera la experiencia más pulida posible y el pago
único de una vez deja de ser una barrera.

## 5. Nota sobre el Firebase ya presente en el repo

El archivo `firebase-applet-config.json` en la raíz del repo corresponde a un **proyecto de
Firebase para una app *web*** (`appId` contiene `:web:`), asociado al scaffold de AI Studio con el
que se generó este proyecto — **no** es la configuración de Firebase para esta app **Android**.
Se puede reutilizar el mismo proyecto de Firebase (`gen-lang-client-0676141733`) agregando la app
Android dentro de él, o crear un proyecto de Firebase nuevo dedicado — cualquiera de los dos es
gratis; se recomienda uno nuevo separado para no mezclar configuración de un scaffold ajeno con la
distribución de esta app.

## 6. Resumen / recomendación

| Nivel | Costo | Esfuerzo | UX para Rocío |
|-------|-------|----------|----------------|
| 1. GitHub Actions + Firebase App Distribution | $0 | Bajo (solo config de CI) | Notificación → 1 toque para instalar |
| 2. Updater propio in-app (GitHub Releases) | $0 | Medio (código nuevo en la app) | Botón "Actualizar" dentro de Ajustes |
| 3. Google Play (Internal Testing) | USD 25 único | Medio-alto (trámite de Play) | Automático, sin tocar nada |

**Recomendación: implementar el Nivel 1.** Resuelve el problema real hoy, sin gastar nada, y con
la menor cantidad de piezas nuevas a mantener.

## 7. Checklist (si se decide implementar el Nivel 1)

- [x] Agregar plugin `com.google.firebase.appdistribution` a Gradle (código) — **no** hizo falta
      `com.google.gms.google-services`: App Distribution no lo necesita, solo el App ID (vía env var)
- [x] Escribir `.github/workflows/release.yml` (build firmado + `appDistributionUploadRelease`)
- [x] Verificar que el plugin no rompe el build local sin credenciales configuradas
- [ ] **Manual (Santiago):** crear proyecto de Firebase y registrar la app Android — Paso 1-2 de
      [GUIA-ACTUALIZACIONES.md](../GUIA-ACTUALIZACIONES.md)
- [ ] **Manual:** generar la keystore de release + credenciales de servicio de Firebase — Pasos 5-6
- [ ] **Manual:** cargar los 6 secrets en GitHub — Paso 7
- [ ] **Manual:** invitar a Rocío como tester (grupo `testers`) — Paso 4
- [ ] **Manual:** probar de punta a punta (Paso 8): push/`workflow_dispatch` → build en CI →
      notificación en el teléfono de Rocío → instalar

### Nota técnica: por qué la versión del plugin importa

El plugin `com.google.firebase.appdistribution` versión `5.1.1` **no es compatible** con AGP 9.x
de este proyecto (falla con `Extension of type 'AppExtension' does not exist` — esperaba la API de
variantes vieja de AGP). La versión **`5.2.0`+ arregló la compatibilidad con AGP 9.0.0**; se usa
`5.3.0` (la más nueva disponible al momento de escribir esto). También se usa el bloque
`firebaseAppDistributionDefault { }` (no `firebaseAppDistribution { }` a nivel de proyecto, que el
plugin marca como deprecado para ese caso).
