# Notificaciones de Cuotas — Plan de implementación

> Estado: **Implementado** ✅ (2026-07-28), tal como se diseñó en este documento. Alcance de este
> documento: diseño completo de la Fase 1 (recordatorios de cierre de mes + atrasos) mencionada
> como "Fase 3 — extras" en [modulo-cuotas.md](modulo-cuotas.md#14-fases-siguientes-idea-central).
> Última actualización: 2026-07-28.
>
> **Nota de implementación:** se usó `MainActivity.onCreate()` como punto de arranque del
> scheduler (no una `Application` custom — ver §5), y se omitió por completo el `Constraints` de
> red del Worker (deja que `fetchMovements`/`fetchPlans` fallen y el `catch` dispare
> `Result.retry()`), tal como el propio documento lo dejaba como alternativa válida más simple.

## 1. Objetivo

Dos recordatorios automáticos, en segundo plano (sin abrir la app), vía notificación nativa de
Android:

1. **Cierre de mes**: el **último día de cada mes**, 9:00am hora Argentina → si el usuario activo
   tiene cuotas **pendientes del mes que cierra**, notificar el monto total (todas sus tarjetas).
2. **Atrasos**: **todos los lunes**, 9:00am hora Argentina → si tiene cuotas **atrasadas** (de
   meses anteriores, sin pagar), notificar el monto total.

Además, dos botones en **Ajustes** para disparar ambas notificaciones "mockeadas" (con los datos
ya cargados en el momento, sin esperar al horario) y poder probar el flujo de punta a punta.

## 2. Decisiones tomadas

1. **WorkManager**, no `AlarmManager`. Es la recomendación oficial de Android para trabajo diferido
   y periódico que debe sobrevivir reinicios del dispositivo (persiste solo, sin necesidad de un
   `BroadcastReceiver` de `BOOT_COMPLETED`) y respeta Doze/App Standby. No necesitamos precisión al
   segundo — "9am amistoso" tolera un corrimiento de minutos/horas si el dispositivo está en Doze;
   `AlarmManager.setExactAndAllowWhileIdle` quedaría como alternativa solo si en la práctica el
   corrimiento resulta molesto (ver §11).
2. **Un solo Worker diario, no dos periódicos mensuales/semanales.** WorkManager no soporta
   periodicidad mensual (el mes tiene 28-31 días) y una periodicidad semanal exacta alineada a las
   9am es frágil de mantener sincronizada. En cambio: **un `PeriodicWorkRequest` cada 24hs**,
   con el primer disparo calculado para caer ~9:00am hora Argentina, que en cada corrida **evalúa
   la fecha actual** (huso `America/Argentina/Buenos_Aires`, fijo en UTC-3 todo el año — sin DST)
   y decide qué notificar:
   - `hoy == último día del mes` → evalúa el recordatorio de cierre.
   - `hoy == lunes` → evalúa el recordatorio de atrasos.
   - (pueden darse los dos el mismo día; se envían ambas notificaciones, en canales/IDs distintos).
3. **Reutilizar 100% el motor puro `CuotasEngine`** (sin duplicar lógica de negocio):
   - Cierre de mes → `CuotasEngine.cuotasImpagasDelMes(mesActual, misPlanes, movimientos)`,
     sumando `cuota.monto` de cada par `(plan, cuota)`.
   - Atrasos → `CuotasEngine.recordatoriosDelMes(mesActual, misPlanes, movimientos)`, filtrando
     `atrasada == true` y sumando `cuota.monto`.
   - `misPlanes` = planes con `propietario == usuario activo del dispositivo` (mismo filtro que ya
     usa `CuotasScreen` para "Pagar la tarjeta": un plan de cuotas es siempre personal, así que solo
     tiene sentido recordarle a cada quien lo suyo).
4. **Refresco en segundo plano reutiliza `AhorroRepository`**, no el `AhorroViewModel` (que es
   `AndroidViewModel`, atado al ciclo de vida de la UI). El `Worker` construye su propio
   `PreferencesHelper` + `AhorroRepository` con el `Context` de la app, llama `fetchMovements` +
   `fetchPlans(soloPagos = false)` igual que `doRefreshData()`, y cachea el resultado (mismo
   `PreferencesHelper.saveSheetsCache` / `savePlansCache`) para que si el usuario abre la app
   después, ya vea datos frescos sin esperar la red.
   - **Modo local (demo):** si `useLocalDemo == true`, el Worker **no** llama a la red; lee
     `getLocalMovements()` / `getLocalPlans()` directamente (mismos datos que usa la UI en demo).
5. **Deduplicación por período**, para no re-notificar si WorkManager reintenta o si el dispositivo
   estuvo apagado el día exacto y el chequeo corre tarde: se persiste en `PreferencesHelper`
   (mismo patrón `SharedPreferences`) la última fecha notificada de cada tipo:
   - `KEY_LAST_CIERRE_NOTIFICADO` → guarda el `yyyy-MM` del mes ya notificado.
   - `KEY_LAST_ATRASO_NOTIFICADO` → guarda el `yyyy-MM-dd` del lunes ya notificado.
   - Antes de notificar, el Worker compara contra estas claves; si coincide, **omite** (ya se avisó
     este período). Esto también cubre el caso "el usuario abrió la app y ya vio/pagó todo": si el
     total pendiente da `0`, tampoco se notifica (pero si el monto es `> 0` sigue pendiente y **sí**
     se vuelve a notificar el mes/lunes siguiente aunque no haya cambiado, porque cambia la clave).
6. **Botones "mockeados" en Ajustes NO usan el Worker ni el gating de fecha/deduplicación**: llaman
   directo a la función que arma texto + dispara la notificación, usando los datos **ya cargados en
   el `AhorroViewModel`** (`allMovements` + `plans`, filtrados por el usuario activo) — sirve para
   validar formato del texto, canal, permisos e ícono sin tener que esperar al día/hora reales ni
   mockear fechas del sistema.

## 3. Arquitectura

```
┌─────────────────────────────┐        enqueue (una vez, al abrir la app / Application.onCreate)
│  AhorroApplication.onCreate │ ───────────────────────────────────────────────────┐
└─────────────────────────────┘                                                   │
                                                                                    ▼
                                                              ┌──────────────────────────────────┐
                                                              │  WorkManager (persistente)        │
                                                              │  PeriodicWorkRequest cada 24h,     │
                                                              │  initialDelay → próximas 9am ARG   │
                                                              └───────────────┬────────────────────┘
                                                                              ▼
                                                              ┌──────────────────────────────────┐
                                                              │  CuotasReminderWorker (CoroutineWorker) │
                                                              │  1. Refresca datos (Repository)   │
                                                              │  2. Calcula fecha actual (ARG TZ)  │
                                                              │  3. ¿Último día del mes? → chequea  │
                                                              │     cierre (CuotasEngine)          │
                                                              │  4. ¿Lunes? → chequea atrasos       │
                                                              │  5. Deduplica (PreferencesHelper)   │
                                                              │  6. Notifica (CuotasNotifier)       │
                                                              └──────────────────────────────────┘
                                                                              │
                                                                              ▼
                                                              ┌──────────────────────────────────┐
                                                              │  CuotasNotifier                    │
                                                              │  - crea canal (una vez)            │
                                                              │  - arma texto amistoso              │
                                                              │  - NotificationManagerCompat.notify │
                                                              └──────────────────────────────────┘
                                                                              ▲
                                                              (mismo objeto, sin gating/dedup)
                                                              ┌──────────────────────────────────┐
                                                              │  SettingsScreen "Modo desarrollador"│
                                                              │  Botón "Probar recordatorio cierre" │
                                                              │  Botón "Probar recordatorio atrasos"│
                                                              └──────────────────────────────────┘
```

## 4. Archivos nuevos

### 4.1 `data/notifications/CuotasNotifier.kt`
Objeto sin estado, responsable solo de **mostrar** la notificación (no decide cuándo):

```kotlin
object CuotasNotifier {
    const val CHANNEL_ID = "cuotas_recordatorios"
    private const val NOTIF_ID_CIERRE = 1001
    private const val NOTIF_ID_ATRASO = 1002

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID, "Recordatorios de cuotas", NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Avisos de cuotas por pagar y atrasadas" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun notificarCierreDeMes(context: Context, total: Double, formatMoney: NumberFormat) {
        if (!hasPermission(context)) return
        ensureChannel(context)
        val texto = "Este mes te quedan ${formatMoney.format(total)} en cuotas por pagar en tus tarjetas 💳"
        show(context, NOTIF_ID_CIERRE, "Cuotas del mes", texto)
    }

    fun notificarAtrasos(context: Context, total: Double, formatMoney: NumberFormat) {
        if (!hasPermission(context)) return
        ensureChannel(context)
        val texto = "Tenés ${formatMoney.format(total)} en cuotas atrasadas de meses anteriores 👀"
        show(context, NOTIF_ID_ATRASO, "Cuotas atrasadas", texto)
    }
    // ... hasPermission() chequea POST_NOTIFICATIONS (API 33+) y show() arma el NotificationCompat.Builder
}
```

Se reutiliza tal cual desde el Worker (real) y desde el botón mockeado de Ajustes (mismo resultado
visual, sin duplicar el armado del texto).

### 4.2 `data/notifications/CuotasReminderWorker.kt`
`CoroutineWorker` — hace el trabajo real (con gating de fecha + deduplicación):

```kotlin
class CuotasReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = PreferencesHelper(applicationContext)
        val repo = AhorroRepository(prefs)
        val hoyArg = Calendar.getInstance(TimeZone.getTimeZone("America/Argentina/Buenos_Aires"))
        val mesActual = SimpleDateFormat("yyyy-MM", Locale.US).apply { timeZone = hoyArg.timeZone }.format(hoyArg.time)
        val esUltimoDiaDelMes = hoyArg.get(Calendar.DAY_OF_MONTH) == hoyArg.getActualMaximum(Calendar.DAY_OF_MONTH)
        val esLunes = hoyArg.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY
        if (!esUltimoDiaDelMes && !esLunes) return Result.success() // nada que evaluar hoy

        val movimientos = if (prefs.useLocalDemo) prefs.getLocalMovements()
                           else repo.fetchMovements(prefs.scriptUrl).also { prefs.saveSheetsCache(it) }
        val planes = if (prefs.useLocalDemo) prefs.getLocalPlans()
                     else repo.fetchPlans(prefs.scriptUrl).also { prefs.savePlansCache(it) }
        val misPlanes = planes.filter { it.propietario.equals(prefs.currentUserProfile, ignoreCase = true) }

        if (esUltimoDiaDelMes && prefs.lastCierreNotificado != mesActual) {
            val total = CuotasEngine.cuotasImpagasDelMes(mesActual, misPlanes, movimientos).sumOf { it.second.monto }
            if (total > 0.0) {
                CuotasNotifier.notificarCierreDeMes(applicationContext, total, formatMoney())
                prefs.lastCierreNotificado = mesActual
            }
        }
        if (esLunes && prefs.lastAtrasoNotificado != isoHoy(hoyArg)) {
            val total = CuotasEngine.recordatoriosDelMes(mesActual, misPlanes, movimientos)
                .filter { it.atrasada }.sumOf { it.cuota.monto }
            if (total > 0.0) {
                CuotasNotifier.notificarAtrasos(applicationContext, total, formatMoney())
                prefs.lastAtrasoNotificado = isoHoy(hoyArg)
            }
        }
        return Result.success()
    }
}
```

`Result.retry()` en caso de excepción de red (WorkManager reintenta con backoff exponencial —
configurar `BackoffPolicy.LINEAR` o el default exponencial está bien).

### 4.3 `data/notifications/CuotasReminderScheduler.kt`
Encapsula el `enqueueUniquePeriodicWork` (para no duplicar el job si `Application.onCreate` corre
más de una vez):

```kotlin
object CuotasReminderScheduler {
    private const val WORK_NAME = "cuotas_reminder_daily"

    fun schedule(context: Context) {
        val delay = millisUntilNext9amArgentina()
        val request = PeriodicWorkRequestBuilder<CuotasReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
```

> ⚠️ La `Constraints.setRequiredNetworkType(CONNECTED)` no aplica en modo demo (no hay red), pero
> no es un problema: WorkManager simplemente espera a que haya red, y en modo demo el `doWork()` ni
> la necesita — igual conviene condicionar el `Constraints` a `!useLocalDemo` al construir el
> request, o directamente omitirlo y dejar que `fetchMovements` falle-y-reintente (`Result.retry()`)
> si no hay conexión.

### 4.4 Cambios en archivos existentes

- **`PreferencesHelper.kt`**: agregar `var lastCierreNotificado: String` (`KEY_LAST_CIERRE_NOTIFICADO`,
  default `""`) y `var lastAtrasoNotificado: String` (`KEY_LAST_ATRASO_NOTIFICADO`, default `""`),
  mismo patrón `SharedPreferences` que el resto de la clase.
- **`MainActivity.kt`** (o una `Application` nueva — ver §5): llamar
  `CuotasReminderScheduler.schedule(applicationContext)` una vez al arrancar.
- **`AndroidManifest.xml`**: agregar
  `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />` (requerido desde
  Android 13/API 33 para mostrar notificaciones; en versiones previas no hace falta pedir permiso).
- **`SettingsScreen.kt`**: nueva tarjeta "Notificaciones de cuotas (prueba)" con dos botones (ver §6).
- **`build.gradle.kts` (app)**: agregar dependencia `androidx.work:work-runtime-ktx` (no está en el
  catálogo `libs.versions.toml` — agregar versión + alias `androidx.work.runtime.ktx`).

## 5. ¿Dónde se llama `schedule()`?

Se necesita un punto de entrada que corra **siempre** que el proceso arranque, no solo cuando el
usuario navega a cierta pantalla. Lo más simple es una `Application` propia:

```kotlin
class AhorroApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CuotasReminderScheduler.schedule(this)
    }
}
```

y registrarla en el manifest (`<application android:name=".AhorroApplication" ...>`). Hoy no existe
una `Application` custom (el `<application>` del manifest no tiene `android:name`), así que este es
el único cambio estructural nuevo. Alternativa sin crear `Application`: llamarlo desde
`MainActivity.onCreate()` — funciona igual porque `enqueueUniquePeriodicWork` con
`ExistingPeriodicWorkPolicy.KEEP` es idempotente, pero solo se registra la primera vez que el
usuario **abre la app** después de instalar/actualizar (vs. cualquier arranque de proceso, p. ej.
un `BroadcastReceiver` de otro componente). Para el caso de uso real (una app que el usuario abre
seguido) alcanza con `MainActivity.onCreate()`; se documenta la `Application` como la opción más
robusta si en la práctica el recordatorio no llegara a dispararse por no haberse registrado a tiempo.

## 6. Botones mockeados en Ajustes

Nueva tarjeta en `SettingsScreen.kt`, junto a las demás (mismo estilo `Card` con `cardBg`/`cardBorder`),
con un texto aclaratorio ("Estas notificaciones usan los datos ya cargados, no reflejan el
horario real") y dos `Button`:

```kotlin
Button(onClick = {
    val misPlanes = plans.filter { it.propietario.equals(currentUserProfile, true) }
    val total = CuotasEngine.cuotasImpagasDelMes(mesActual, misPlanes, allMovements).sumOf { it.second.monto }
    CuotasNotifier.notificarCierreDeMes(context, total, formatMoney)
}) { Text("Probar recordatorio de cierre de mes") }

Button(onClick = {
    val misPlanes = plans.filter { it.propietario.equals(currentUserProfile, true) }
    val total = CuotasEngine.recordatoriosDelMes(mesActual, misPlanes, allMovements)
        .filter { it.atrasada }.sumOf { it.cuota.monto }
    CuotasNotifier.notificarAtrasos(context, total, formatMoney)
}) { Text("Probar recordatorio de atrasos") }
```

`viewModel.plans` / `viewModel.allMovements` / `viewModel.currentUserProfile` ya están expuestos
como `StateFlow` (mismos que usa `CuotasScreen`), así que `SettingsScreen` solo necesita
colectarlos. Si el total da `0` (no hay nada pendiente/atrasado), se puede mostrar igual con un
texto de fallback ("¡Estás al día! 🎉") para poder probar el flujo sin depender de tener datos
de prueba cargados — a diferencia del Worker real, que en ese caso **no** notifica nada.

## 7. Permiso `POST_NOTIFICATIONS` (Android 13+)

Se solicita con el mismo patrón que ya usa `CAMERA` en `AddMovementScreen.kt`
(`accompanist-permissions`, ya en el proyecto). El pedido conviene dispararlo la primera vez que el
usuario entra a la tarjeta nueva de Ajustes (o al primer arranque, con un diálogo explicativo) — si
el usuario lo rechaza, `CuotasNotifier.hasPermission()` corta silenciosamente (no forzar, no hay
forma de "pedir de nuevo" agresivamente en Android sin fricción).

## 8. Zona horaria

Argentina **no tiene horario de verano** desde 2009 (fijo en UTC-3 todo el año), así que no hace
falta lidiar con transiciones DST: `TimeZone.getTimeZone("America/Argentina/Buenos_Aires")` alcanza
para todos los cálculos (fecha actual, último día del mes, día de la semana), **sin depender de la
zona horaria configurada en el dispositivo** — importante porque el teléfono podría estar
configurado en otra zona (viajes, config regional) y el negocio ("cierre de tarjeta") es
inherentemente local a Argentina.

## 9. Testing

- **Unitario** (`CuotasReminderWorkerTest` si se extrae la lógica de gating a una función pura,
  p. ej. `EsUltimoDiaDelMes(calendar): Boolean` / `EsLunes(calendar): Boolean` en un objeto testeable
  sin dependencias de `Context`): casos límite de fin de mes (28/29/30/31), y que un lunes que
  también sea último día del mes dispare ambas.
- **Manual**: los botones mockeados de Ajustes son el mecanismo principal de prueba manual (no
  requieren esperar al día/hora real ni cambiar la fecha del sistema).
- **WorkManager real**: `adb shell` permite forzar la ejecución para validar de punta a punta:
  ```bash
  adb shell cmd jobscheduler run -f com.aistudio.ahorrocompartido.pquzx <job-id>
  ```
  (requiere ubicar el `job-id` vía `adb shell dumpsys jobscheduler`), o más simple, usar
  `WorkManagerTestInitHelper` en un test instrumentado.

## 10. Riesgos / bordes

- **Doze / restricciones de batería del fabricante** (Xiaomi, Samsung, etc. con "optimización de
  batería agresiva" para apps en background) pueden retrasar el `Worker` bastante más que unas
  horas, o directamente no ejecutarlo si la app fue "congelada" por el sistema. Es una limitación
  conocida de WorkManager en ciertos OEMs — no hay forma 100% confiable de evitarla sin pedirle al
  usuario que desactive manualmente la optimización de batería para la app (se puede sugerir un
  link a esa configuración desde Ajustes, opcional, fuera de alcance de esta fase).
  Si el corrimiento resultara inaceptable en la práctica, subir de nivel a
  `AlarmManager.setExactAndAllowWhileIdle` + `SCHEDULE_EXACT_ALARM` (Android 12+) es el siguiente
  escalón, a costa de más código y un permiso adicional.
  El corrimiento no es catastrófico igualmente: el recordatorio de cierre de mes tolera llegar
  algunas horas tarde (sigue siendo el mismo día o al día siguiente), y el de atrasos, al chequear
  `mesVencimiento < mesActual`, es correcto aunque llegue con demora.
- **App recién instalada, todavía no abierta ninguna vez**: no hay job programado hasta el primer
  `onCreate` de `MainActivity` — aceptable (no puede notificar antes de que exista una sesión con
  datos).
- **Cambio de usuario activo en el mismo dispositivo** (logout / elegir el otro perfil): el Worker
  siempre usa `prefs.currentUserProfile` en el momento de ejecutar, así que automáticamente
  recalcula para quien esté activo en ese momento — no hace falta reprogramar nada al cambiar de
  usuario.

## 11. Checklist de implementación

- [x] `build.gradle.kts` + `libs.versions.toml`: agregar `androidx.work:work-runtime-ktx`
- [x] `AndroidManifest.xml`: `POST_NOTIFICATIONS`
- [x] `PreferencesHelper.kt`: `lastCierreNotificado`, `lastAtrasoNotificado`
- [x] `data/notifications/CuotasNotifier.kt`
- [x] `data/notifications/CuotasReminderWorker.kt`
- [x] `data/notifications/CuotasReminderScheduler.kt`
- [x] Punto de arranque del scheduler (`MainActivity.onCreate`)
- [x] `SettingsScreen.kt`: tarjeta con los dos botones de prueba + solicitud de permiso
- [ ] Tests unitarios de gating de fecha (último día del mes / lunes) en huso Argentina — pendiente,
      la lógica hoy vive inline en `CuotasReminderWorker.doWork()` sin extraer a una función pura
- [ ] Prueba manual end-to-end con los botones mockeados — pendiente, requiere un dispositivo/emulador real
- [ ] Prueba manual forzando el Worker real vía `adb shell cmd jobscheduler run -f` — pendiente, ídem
