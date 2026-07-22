# Módulo de Cuotas — Plan de implementación

> Estado: **Fase 1 implementada** ✅ · Alcance de este documento: **Fase 1 (MVP)** en detalle + idea central de las fases siguientes.
> Última actualización: 2026-07-21.
>
> **Nota de implementación:** compila (`:app:compileDebugKotlin`) y los tests del motor puro
> (`CuotasEngineTest`) pasan. Falta desplegar la versión 6.0 del Apps Script en el spreadsheet
> (imprescindible el punto ⚠️ de excluir `Planes` del `doGet` de movimientos).

## 1. Objetivo

Permitir cargar **compras en cuotas** (monto por cuota × cantidad de cuotas) y **confirmar
manualmente el pago de cada cuota**, momento en el cual se debita del dinero disponible.
El módulo también funciona como **recordatorio** de las cuotas a pagar y permite **proyectar
cuánto se pagará en la tarjeta** en un mes dado.

## 2. Decisiones tomadas

1. **Almacenamiento:** se reutiliza el spreadsheet actual (una hoja nueva `Planes`). Una sola Web App URL.
2. **Estado de pago:** se agregan **2 columnas nuevas** a `Movement` (`planId`, `cuotaNumero`). El estado
   "cuota pagada" es **derivado** (existe un movimiento que la referencia), no duplicado.
3. **Navegación:** nueva pestaña **Cuotas** en la `NavigationBar`.
4. **Alcance:** se implementa la **Fase 1** completa; las fases 2 y 3 quedan documentadas a nivel de idea.

## 3. Idea central del diseño

Una compra en cuotas es un **plan (proyección/pasivo)**, no un movimiento:

- **Plan de cuotas** = metadatos de la compra. **No toca el saldo.**
- **Pago de una cuota** = al confirmar, se genera un **`Movement` de tipo "Gasto"** por el monto de la cuota.
  El `AccountingEngine` existente lo debita del dinero disponible **sin ningún cambio en el motor**.
- Una cuota está **pagada** si existe un `Movement` no eliminado con su `planId` + `cuotaNumero`
  (estado derivado). Las cuotas **impagas** son solo proyección: alimentan recordatorios y el total
  mensual de tarjeta, sin afectar balances.

```
Plan "Notebook" ($100.000 × 12, primera 2026-08, tarjeta Visa Santiago, propietario Santiago)
        │
        ├── genera 12 cuotas proyectadas (ago-26 … jul-27)   → recordatorios + proyección de tarjeta
        │
        └── al confirmar cuota 3  →  crea Movement(Gasto, $100.000, planId=…, cuotaNumero=3)  → debita saldo
```

Ventajas: el débito reutiliza toda la lógica probada (efectivo/virtual); si se borra el movimiento del
débito, la cuota vuelve automáticamente a "pendiente"; cero inconsistencia de estado.

## 4. Regla de negocio: los planes son SIEMPRE personales

Un gasto en cuotas **nunca es común**. Esto simplifica el modelo respecto de `Movement`:

- El plan tiene **un único `propietario` (owner)** ∈ { `Santiago`, `Rocío` }. **No existe** `esComun`
  ni propietario `Ambos`.
- El `Movement` generado al confirmar una cuota es un **gasto personal**:
  `esComun = false`, `propietario = plan.propietario`, `responsable = plan.propietario`
  (el dueño lo paga desde su propia cuenta). Al ser `responsable == propietario`, **no se genera
  propiedad cruzada**: es un gasto personal limpio.

## 5. Modelo de datos

### 5.1 Nueva entidad `CuotaPlan` (nuevo archivo `data/CuotaPlan.kt`)

```kotlin
data class CuotaPlan(
    val id: String = UUID.randomUUID().toString(),
    val fechaCreacion: String,        // ISO "yyyy-MM-dd HH:mm" — orden por creación desc
    val descripcion: String,          // "Notebook Lenovo"
    val montoPorCuota: Double,        // permite cuotas con interés (no siempre total/N)
    val cantidadCuotas: Int,          // N
    val fechaPrimeraCuota: String,    // "yyyy-MM" — las siguientes son +1 mes
    val propietario: String,          // "Santiago" | "Rocío" (owner; nunca "Ambos")
    val categoria: String,            // categoría del gasto que generará cada cuota
    val tarjeta: String = "",         // "Visa Santiago", "Naranja"… para agrupar el total mensual
    val eliminado: Boolean = false
) {
    val montoTotal: Double get() = montoPorCuota * cantidadCuotas
}
```

Se guarda `montoPorCuota` (no el total) porque las cuotas suelen tener interés y no son `total/N`.

### 5.2 Extensión de `Movement` (2 columnas nuevas)

En `data/Movement.kt`, agregar al final (mismo patrón con que se agregaron `ticketUrl`/`eliminado`/`propietario`):

- `planId: String = ""`    → columna **M** (índice 12)
- `cuotaNumero: Int = 0`    → columna **N** (índice 13)

Actualizar `toRowValues()` (agrega 2 valores) y `fromRowValues()` (lee índices 12 y 13 con `getOrNull`,
default vacío/0 para filas viejas → retrocompatible).

### 5.3 Layout de la hoja `Planes` (Google Sheets)

| Col | Campo | Notas |
|-----|-------|-------|
| A | id | UUID |
| B | fechaCreacion | ISO, para ordenar desc |
| C | descripcion | |
| D | montoPorCuota | número |
| E | cantidadCuotas | entero |
| F | fechaPrimeraCuota | "yyyy-MM" |
| G | propietario | Santiago \| Rocío |
| H | categoria | |
| I | tarjeta | |
| J | eliminado | baja lógica |

## 6. Backend (Apps Script) — `google-apps-script.js`

Constante nueva: `const PLANS_SHEET = "Planes";`

### 6.1 Cambios en el flujo de movimientos (obligatorios)

1. ⚠️ **Excluir `Planes` del loop de `doGet`** de movimientos (hoy recorre *todas* las hojas). Si no,
   las filas de planes se leerían como movimientos.
   ```js
   sheets.forEach(sheet => {
     if (sheet.getName() === PLANS_SHEET) return; // saltar
     ...
   });
   ```
2. **Sumar columnas M/N** (planId, cuotaNumero) al mapeo de movimientos en `doGet` (índices 12 y 13),
   y a `appendRow` / `setValues` / headers de `doPost` (pasa de 12 a 14 columnas).

### 6.2 Endpoints de planes

**Lectura — `doGet` con `action=GET_PLANS`** (parámetro `filtro`):

- `?action=GET_PLANS&filtro=pendientes` → **default**. Devuelve solo planes **no** completamente pagos.
- `?action=GET_PLANS&filtro=pagos` → **request aparte** (la dispara el switch de la UI). Devuelve solo
  los planes completamente pagos.
- En ambos casos, **ordenados por `fechaCreacion` descendente**.

Algoritmo de "completamente pago" (derivado de los movimientos, fuente única de verdad):

```
1. Leer hoja Planes (descartar eliminado).
2. Construir paidMap: recorrer TODAS las hojas de movimientos (saltando "Planes" y filas eliminadas);
   para cada fila con planId (col M) no vacío, agregar cuotaNumero (col N) a un Set por planId.
3. Para cada plan: pagadas = paidMap[plan.id]?.size ?? 0;  completo = pagadas >= cantidadCuotas.
4. Filtrar según `filtro` (pendientes: !completo | pagos: completo).
5. Ordenar por fechaCreacion desc y devolver.
```

> **Motivo (ahorro de tráfico):** la hoja `Planes` crece con el tiempo. Traer por defecto solo los
> pendientes mantiene el payload chico aunque haya cientos de planes históricos. Los pagos se piden
> solo bajo demanda.
>
> **Nota de rendimiento / escalabilidad:** derivar "completo" implica escanear los movimientos en cada
> `GET_PLANS`. Para el volumen real (pareja, dozenas de planes) es aceptable y preserva una única fuente
> de verdad (sin drift si se edita/borra un débito). Si a futuro el volumen de movimientos hiciera lento
> el escaneo, la vía de escalamiento es **denormalizar** un contador `cuotasPagadas` en la fila del plan
> (actualizado al confirmar) y filtrar por ese contador — a costa de tener que repararlo si se borra un
> movimiento. Se documenta como opción, **no** se implementa en Fase 1.

**Escritura — `doPost` con discriminador `entity: "plan"`:**

- `POST` + `entity:"plan"` + `plan:{…}` → escribe una fila en `Planes`.
- `PUT` + `entity:"plan"` → busca por `id` y reemplaza (editar plan).
- `DELETE` + `entity:"plan"` + `id` → baja lógica (marca col J `eliminado = true`), reutilizando el
  patrón de `handleLogicalDelete` pero acotado a la hoja `Planes`.

## 7. Lógica pura: `CuotasEngine` (nuevo, testeable)

Objeto sin dependencias Android (espejo de `AccountingEngine`), con test `CuotasEngineTest`.
Recibe `planes` + `movimientos` y produce (todo **derivado**, nunca toca saldos):

- **`cronograma(plan, movimientos)`** → `List<CuotaProgramada>` con
  `{ numero, monto, mesVencimiento, pagada, movimientoId? }`.
  `mesVencimiento` = `fechaPrimeraCuota + (numero-1)` meses. `pagada` = hay `Movement` no eliminado con
  `planId == plan.id && cuotaNumero == numero`.
- **`recordatoriosDelMes(mes, planes, movimientos)`** → cuotas **impagas** con `mesVencimiento <= mes`
  (incluye **atrasadas** de meses previos).
- **`totalTarjetaPorMes(mes, planes, movimientos)`** → suma de cuotas (pagadas + impagas) que caen ese
  mes, **agrupadas por `tarjeta`**. Responde "cuánto voy a pagar en la tarjeta en tal mes".
- **`compromisoFuturoTotal(planes, movimientos)`** → deuda pendiente total (contexto opcional).

> Aunque la API filtra "completamente pago" del lado del server, el cliente ya tiene **todos** los
> movimientos cargados (el `doGet` de movimientos no filtra por plan), así que el estado **por cuota**
> se deriva localmente sin pedidos extra.

## 8. Capa de datos y ViewModel

### 8.1 `SheetsService.kt`
Extender `WebAppRequest` para transportar planes (`entity: String?`, `plan: CuotaPlan?`) y agregar el
tipo de respuesta de planes (o reutilizar una respuesta genérica con `plans: List<CuotaPlan>?`).

### 8.2 `AhorroRepository.kt`
- `fetchPlans(webAppUrl, soloPagos: Boolean = false): List<CuotaPlan>` → arma la URL con
  `action=GET_PLANS&filtro=pendientes|pagos`. Cachea los **pendientes** (los pagos son on-demand y no
  se cachean, o se cachean aparte).
- `savePlan(...)`, `updatePlan(...)`, `deletePlan(...)` (baja lógica).
- Los pagos de cuota **reutilizan `saveMovement`** (no hay endpoint nuevo para el débito).

### 8.3 `PreferencesHelper.kt`
- Nuevo cache `KEY_PLANS_CACHE` con adapter Moshi de `List<CuotaPlan>` (mismo patrón que el cache de
  movimientos). Solo para pendientes.

### 8.4 `AhorroViewModel.kt`
- `_plans` (pendientes) y `_paidPlans` (pagos, cargados on-demand) como StateFlows.
- Cargar pendientes en `refreshData()` junto con los movimientos; los pagos solo cuando el switch se activa
  (`loadPaidPlans()` dispara la request aparte).
- `addPlan(...)`, `updatePlan(...)`, `deletePlan(...)`.
- **`confirmarCuota(plan, numero, fecha, metodoPago)`** → arma
  `Movement(tipo="Gasto", monto=plan.montoPorCuota, esComun=false, propietario=plan.propietario,
  responsable=plan.propietario, categoria=plan.categoria, metodoPago=<elegido>, planId=plan.id,
  cuotaNumero=numero, descripcion="Cuota {numero}/{N} — {plan.descripcion}")` y lo guarda por el camino
  existente. Al refrescar, la cuota queda "pagada" automáticamente.
- Exponer cómputos de `CuotasEngine` (recordatorios del mes seleccionado, total por tarjeta).
- Estado de filtros del listado (owner, categoría, mostrar pagos on/off).

## 9. UI

### 9.1 Nueva pestaña `Cuotas`
- Agregar `ScreenTab.CUOTAS` + `NavigationBarItem` en `MainActivity.kt`, siguiendo el `when(currentTab)`
  actual (la app no usa navigation-compose). Visible siempre (no depende del mes seleccionado).

### 9.2 `CuotasScreen` — listado de planes
- **Filtros:**
  - **Owner del plan:** control segmentado `Todos | Santiago | Rocío`. **Default = usuario activo.**
  - **Categoría:** multi-selección (reutilizar el patrón de chips desplegables de `DashboardScreen`).
  - Ambos son filtros **de vista** (client-side) sobre lo ya cargado.
- **Orden:** siempre por `fechaCreacion` descendente (el server ya lo ordena; el cliente re-ordena
  defensivamente).
- **Pendientes vs pagos:**
  - Por defecto se muestran **solo los pendientes** (lo que trajo la API).
  - Un **switch "Mostrar pagados"** dispara la **request aparte** (`filtro=pagos`) y agrega esa lista.
  - Los pagos se muestran **debajo, en una sección separada** ("Pagados"), **visualmente distintos**
    (atenuados + check/badge), claramente diferenciados de los pendientes.
- **Cada plan (card):** descripción, owner, categoría, tarjeta, progreso `pagadas/total`, monto por cuota,
  próxima cuota / mes de vencimiento. Botón `＋ Nueva compra en cuotas`.
- **Detalle del plan:** cronograma de cuotas; cada cuota pendiente con botón **Confirmar pago**
  (pide fecha —default hoy— y método de pago —default Billetera Virtual—). Las pagadas se muestran
  tildadas. Normalmente se pagan en orden, pero el modelo permite confirmar cualquier cuota pendiente.
- **Alta de plan (formulario):** al ser **solo personal**, es más simple que el de movimientos — sin
  toggle común/personal ni "Ambos". Reutiliza componentes de `AddMovementScreen` (monto tipo cajero,
  chips de categoría, selector de fecha) y agrega: cantidad de cuotas, mes de la primera cuota, tarjeta.
  El owner por defecto es el usuario activo.

### 9.3 Dashboard (`DashboardScreen.kt`)
- Tarjeta compacta **"Cuotas a pagar este mes"** (solo si hay pendientes del usuario activo), con
  confirmación rápida. Cumple el rol de recordatorio.

### 9.4 Métricas (`ReportsScreen.kt`)
- Tarjeta **"Total en tarjetas por mes"** agrupado por `tarjeta` (usa `totalTarjetaPorMes`).

## 10. Contrato de API (resumen)

| Acción | Método | Request (JSON) | Respuesta |
|--------|--------|----------------|-----------|
| Listar pendientes | GET | `?action=GET_PLANS&filtro=pendientes` | `{status, plans:[…]}` (no completos, orden creación desc) |
| Listar pagos (on-demand) | GET | `?action=GET_PLANS&filtro=pagos` | `{status, plans:[…]}` (completos, orden creación desc) |
| Crear plan | POST | `{action:"POST", entity:"plan", plan:{…}}` | `{status, message}` |
| Editar plan | POST | `{action:"PUT", entity:"plan", plan:{…}}` | `{status, message}` |
| Baja de plan | POST | `{action:"DELETE", entity:"plan", id:"…"}` | `{status, message}` |
| Pagar cuota | POST | `{action:"POST", body:{Movement con planId+cuotaNumero}}` | `{status, message}` (endpoint de movimientos existente) |

## 11. Bordes y consideraciones

- **Migración Apps Script:** desplegar la versión nueva del script; el punto ⚠️ de excluir `Planes` del
  `doGet` de movimientos es imprescindible.
- **Retrocompatibilidad:** movimientos y config existentes siguen funcionando (columnas M/N opcionales,
  default vacío/0).
- **Cuotas desiguales / redondeo:** `montoPorCuota` fijo; permitir editar el monto al confirmar la última
  cuota si hay diferencia por redondeo.
- **Idempotencia:** la UI oculta/deshabilita cuotas ya pagadas; `CuotasEngine` marca "pagada" si hay
  ≥1 movimiento vinculado.
- **Atrasos:** los recordatorios incluyen cuotas impagas de meses previos.
- **Doble escaneo en refresh:** hoy el refresh llamaría al endpoint de movimientos y al de planes
  (dos escaneos server-side). Optimización futura posible: un endpoint combinado que escanee una sola vez.

## 12. Testing (Fase 1)

- `CuotasEngineTest` (JUnit puro, como `AccountingLogicTest`): cronograma, estado pagada/impaga derivado,
  recordatorios con atrasos, `totalTarjetaPorMes` por tarjeta, detección de "completamente pago".
- Verificar que un pago de cuota genera un gasto personal correcto (sin propiedad cruzada) en el
  `AccountingEngine`.

## 13. Checklist de Fase 1

- [x] `data/CuotaPlan.kt`
- [x] `Movement.kt`: columnas `planId` (M) y `cuotaNumero` (N) + `toRowValues`/`fromRowValues`
- [x] `google-apps-script.js`: constante `PLANS_SHEET`, exclusión en `doGet` de movimientos, columnas M/N,
      `GET_PLANS` (filtro pendientes/pagos, orden creación desc, derivación de "completo"), POST/PUT/DELETE de plan
- [x] `ui/CuotasEngine.kt` (+ `CuotasEngineTest`)
- [x] `SheetsService.kt`: request/response de planes
- [x] `AhorroRepository.kt`: `fetchPlans(soloPagos)`, `savePlan`, `updatePlan`, `deletePlan`
- [x] `PreferencesHelper.kt`: cache de planes pendientes (+ planes mock en modo demo)
- [x] `AhorroViewModel.kt`: estados de planes, filtros, `confirmarCuota`, carga on-demand de pagos
- [x] `MainActivity.kt`: `ScreenTab.CUOTAS` + item de navegación
- [x] `ui/components/CuotasScreen.kt`: listado con filtros (owner default activo, categoría), switch de
      pagados (request aparte, sección separada y distinguible), detalle con confirmación de cuota, alta/edición de plan
- [x] Tarjeta recordatorio en `DashboardScreen.kt`
- [x] Tarjeta "Total en tarjetas por mes" en `ReportsScreen.kt`

---

## 14. Fases siguientes (idea central)

### Fase 2 — Recordatorios y proyección (profundización)
Enriquecer la visualización ya iniciada en Fase 1: vista de calendario/timeline de vencimientos,
proyección multi-mes del total por tarjeta (no solo el mes actual), indicadores de cuotas atrasadas
y del compromiso futuro total. Todo sigue siendo **derivado** de planes + movimientos; no requiere
cambios en el modelo ni en el motor.

### Fase 3 — Extras (opcional)
- **Notificaciones push** cerca del vencimiento de cada cuota (WorkManager — hoy no está en
  dependencias; habría que agregarlo).
- **Editar/cancelar** un plan que ya tiene cuotas pagadas (reglas de qué pasa con los débitos existentes).
- **Interés / CFT**: registrar costo financiero y comparar contra precio de contado.
- Posible **endpoint combinado** movimientos+planes para un solo escaneo server-side.
