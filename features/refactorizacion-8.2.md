# Refactorización 8.2

Limpieza interna después de muchas iteraciones. La regla fue **no cambiar lo que ve el usuario**,
salvo bugs. Cada cambio se verificó comparando la versión anterior con la nueva:

- capturas de todas las pantallas;
- un registro de los pedidos al Web App;
- el Apps Script corriendo en un entorno simulado.

Ver `tools/verificacion/`.

Los documentos anteriores de `features/` describen cómo se hizo cada cosa en su momento; los nombres
de archivo y de función que mencionan pueden haber cambiado (tabla al final).

## Bugs corregidos

| Bug | Dónde | Efecto |
|---|---|---|
| Las filas de apertura cruzadas solo se escribían si eran ≠ 0 | `AccountingEngine.openingRowsFor` y `filasDeApertura` (script) | Si la deuda se saldaba o cambiaba de lado, la fila vieja quedaba viva y la apertura sumaba plata y deuda inexistentes. Ahora van siempre las 6 filas. |
| Un movimiento borrado quedaba en el cache marcado como eliminado | `AhorroRepository.deleteMovement`, `PreferencesHelper.getSheetsCache` | Si el refresco posterior fallaba, el movimiento volvía a mostrarse y a contar. |
| `registrarFallo` leía la cola y después hacía upsert | `MovementUploadWorker` | Un pendiente que el usuario borraba en ese instante volvía a la cola. |
| Log HTTP con cuerpo completo en release | `WebAppClient` | La URL del Web App (una credencial) y los tickets iban a logcat. Ahora solo en debug. |
| Timeouts de 10 s | `WebAppClient` | El script puede esperar hasta 30 s el candado: escrituras que el servidor sí hacía volvían como error. |
| Un slotKey desconocido pisaba al segundo usuario en el cache | `UsuariosConfig.conUsuario` | — |
| Montos ≥ 10 millones en notación científica | diálogo de pago y edición de plan | Se sembraba "1.23456785E7" en el campo. |
| "Hoy"/"Ayer" quedaban fijos con el formulario abierto pasada la medianoche | `AtajosDeFecha` | — |
| `FlowRow` ignoraba el espaciado pedido **y estiraba cada chip a todo el ancho** | `common/Comunes.kt` | Visible: los chips de categorías (Inicio, Cuotas), las tarjetas sugeridas y los colores de Ajustes ahora se acomodan en renglones, y los colores son círculos (antes eran barras). |
| Comparaciones de dueño sensibles a mayúsculas | `Presentacion.kt`, `BalanceBreakdown.de` | — |
| "Cuota pagada" contaba números fuera del plan | `getPlans` (script) | Un plan podía darse por completo sin estarlo. |
| POST y acción desconocida en planes hacían append | `handlePost`, `handlePlanUpsert` (script) | Un reintento duplicaba la fila. Ahora todo es upsert. |
| `doPost` no respondía nada ante un pedido no reconocido; `doGet` sin try/catch | script | Ahora siempre responde JSON. |
| Mover un movimiento de mes borraba antes de escribir | script | Si fallaba la escritura, se perdía. |
| Triggers y funciones del editor escribían sin candado | script | Podían correr índices de fila en medio de una escritura de la app. |
| "Eliminado"/"Es Común" no aceptaban `TRUE` | script | Mismo criterio que el resto de los booleanos. |
| Selector de año con fallback fijo en 2026 | `MonthYearPickerDialog`, selector de fecha | — |
| La notificación de "guardado" no tenía emoji para "Ingreso" | `MovementNotifier` | — |

## Estructura nueva

- **`domain/`**: los motores puros (`AccountingEngine`, `CuotasEngine`), antes en `ui/`. `data`
  (workers) ya no depende de `ui`.
- **`data/Dominio.kt`**: los valores que viajan a la planilla (`TipoMovimiento`, `MetodoPago`,
  `PROPIETARIO_AMBOS`) y `Movement.mes`. Nada de literales sueltos.
- **`util/Fechas.kt`**, **`util/Formato.kt`**: el "ahora" en los formatos de la app, la lectura de
  fechas de Sheets, los nombres de mes y el formato de montos. Antes había unas 15 copias.
- **`data/SheetsService.kt`**:
  - `WebAppClient` es un solo cliente HTTP por proceso.
  - `WebApp` reúne las constantes del protocolo.
  - Hay un `get` y un `post`, en vez de cuatro métodos iguales.
- **`AhorroRepository`**: la URL y la carpeta salen de la config; ningún método las recibe. Los
  pedidos pasan por un único `pedir()`, que controla `SUCCESS`, loguea y propaga la cancelación. El
  trabajo de red y JSON va en `Dispatchers.IO`.
- **`PreferencesHelper`**:
  - Un `JsonListPref` genérico reemplaza los cinco pares get/save copiados.
  - Si un JSON guardado no se puede leer, se copia a `<clave>_ilegible` antes de devolver el valor
    por defecto.
- **Notificaciones**: `Notificaciones.kt` agrupa canal, permiso y el toque que abre una pestaña.
  Los IDs de canal no cambiaron.
- **ViewModel**:
  - `operacion()` reemplaza las cinco copias de "cargando → escribir → refrescar → error".
  - El pago de cuotas sale de `CuotasEngine.movimientoDePago`.
  - La compresión de tickets pasó a `Tickets`.
- **UI**:
  - `ui/components/common/Comunes.kt`: `AppCard`, `IconoEnCirculo`, `EtiquetaChica`,
    `ConfirmDialog`, `BotonRecargar`, `FormLabel`, `SelectorDeMetodoPago`, `AtajosDeFecha`,
    `MontoCajeroCard`, `FiltroDePersona`, `FiltroDeCategorias`, `FlowRow`…
  - Inicio y Cuotas se partieron en archivos por sección.
  - La lógica que vivía en composables pasó a funciones puras:
    - `DashboardFilters.aplicar`
    - `Presentacion.kt` (clase, signo y etiqueta de un movimiento)
    - `CuotasEngine.tarjetasAPagar` y `EstadoCuota`
    - `AccountingEngine.gastosPorCategoria`
- **Apps Script** (un solo archivo):
  - `esHojaDeMovimientos` es el único criterio de qué hojas recorrer (antes había 7 copias).
  - Mapa de columnas `COL` / `COL_PLAN`.
  - Helpers `asegurarEncabezado`, `crearHojaConEncabezados`, `instalarTriggerDiario`,
    `conCandado`.
- **Build**:
  - Se sacaron Room, KSP y codegen de Moshi, `play-services-auth` (solo lo usaba `DriveService`,
    que no se llamaba) y las dependencias comentadas.
  - Se sacaron los tests de plantilla y el APK de mayo que estaba commiteado.
  - También el `GEMINI_API_KEY` de AI Studio.

## Preparado para más usuarios (sin cambiar el motor)

El motor sigue siendo de dos personas: la propiedad cruzada es un único neto. Lo que ya no asume
"el otro":

- `UsuariosConfig.otros()` / `conUsuario()`.
- `personaColor` resuelve el color de **cada** persona desde su preset (`colorIdDePersona`), sin
  pasar por `primary`/`tertiary`. El tema recibe la config entera.
- `BalanceBreakdown.de(slotKey)`: las pantallas piden el saldo de cada usuario en vez de leer
  `santiago*`/`rocio*`.
- Recorren `config.todos`:
  - las tarjetas de saldos de Inicio,
  - los ingresos y gastos de Métricas,
  - los filtros de persona,
  - la grilla de colores, que deshabilita los de *todos* los demás.

  Con dos usuarios dan exactamente lo mismo.
- `SANTIAGO`/`ROCIO` salen de `UsuariosConfig.DEFAULT` y no de literales.

Lo que falta para N usuarios: cambiar el motor a un neto por pares, agregar una columna
`contraparte` y hacer el backfill del historial. Ese paso sí migra datos.

## Qué se movió

| Antes | Ahora |
|---|---|
| `ui/AccountingEngine.kt`, `ui/CuotasEngine.kt` | `domain/` |
| `formatMonthLabel` (CuotasScreen) | `Fechas.etiquetaDeMes` |
| `formatDateMinimal` (DashboardScreen) | `Fechas.legible` |
| `normalizeFechaToString` (VM), `toYyyyMm`/`normalizeCreation` (repo) | `Fechas.normalizarFechaDeMovimiento`, `Fechas.aMes`, `Fechas.aFechaHora` |
| `CuotasNotifier.formatMoney`, los `DecimalFormat` de cada pantalla | `Formato.dinero` / `rememberDinero()` |
| `repo.savePlan`/`updatePlan` | `repo.guardarPlan` |
| `personaThemeRole` | `colorIdDePersona` |
| `MyApplicationTheme(activeUser, otherUser)` | `MyApplicationTheme(usuarios, currentUserKey)` |
| `BackgroundSyncWorker.ARGENTINA_TZ` | `Fechas.ARGENTINA` |
| `Movement.toRowValues`/`fromRowValues` (sin uso) | eliminados |
| `DriveService` (sin uso) | eliminado |

## Pendiente operativo

Desplegar la versión 8.2 del Apps Script. La app nueva funciona igual con el script viejo, y el
script nuevo funciona con la app vieja: el protocolo no cambió.
