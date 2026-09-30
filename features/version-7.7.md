# Versión 7.7 — análisis y plan

> Estado: **implementado (2026-09-30), sin commitear**. Compila, los tests unitarios pasan y el port
> del motor al Apps Script se verificó contra el motor Kotlin con node. **Falta:** probar la UI en un
> teléfono y desplegar la v7.7 del Apps Script. Las decisiones que estaban abiertas quedaron resueltas
> (✅, ver [Preguntas abiertas](#preguntas-abiertas)); los 🔧 se implementaron tal como se proponen.

## Resumen

| # | Pedido | Toca el motor contable | Toca la planilla / Apps Script | Tamaño |
|---|---|---|---|---|
| 1 | Transferencia "Cambio de dinero" | Sí (tipo nuevo) | Sí (port del motor) | M |
| 2 | "Perdonar deuda" → "Saldo externo" | No (solo nombre) | No | S |
| 3 | "Mover plata" deja de saldar deuda; "Saldo externo" también paga | **Sí (cambia una regla)** | Sí (port del motor) | L |
| 4 | Gastos evitables / no evitables + tablero "Gastos acumulados" | No (solo métricas) | Sí (columna nueva en 2 hojas) | L |
| 5 | Categoría "Corrección" en Gasto y Aporte | No | No | S |
| 6 | Editar un movimiento desde el detalle (PUT) | No | **Sí (el PUT hoy no soporta cambio de mes)** | L |
| 7 | Aporte solo a mi nombre | No | No | S |
| 8 | "Sueldo" → "Ingreso"; aportes/gastos de Inicio y Métricas solo cuentan lo "real" | No (contadores nuevos) | No | M |

Hay **dos puntos que cambian la contabilidad** (1 y 3) y los dos tienen que ir **espejados en
`aplicarMovimientos` del Apps Script**: si no, las aperturas que escribe el servidor
(`actualizarAperturas`, `escribirApertura`) calcularían con las reglas viejas y el saldo inicial del
mes siguiente saldría distinto del que muestra la app.

Todo lo que agrega columnas o tipos exige **desplegar la v7.7 del Apps Script**. La app nueva
contra el script viejo funciona pero pierde datos: el script viejo escribe 14 columnas y descarta
`evitable`.

---

## 1. Tipo de transferencia "Cambio de dinero"

### Qué se pide
Registrar un canje entre los dos: Ro me da efectivo y yo le transfiero (o al revés). Nadie gana ni
pierde plata y nadie le debe nada a nadie; solo cambia **en qué forma** tiene cada uno su plata.

### Estado actual
No hay forma de cargarlo. Con dos transferencias ("es del otro") en sentidos opuestos, la primera
salda deuda y la segunda es regalo (o al revés), y el resultado depende del orden y de la deuda
vigente. Con dos "sigue siendo mía" se netean, pero ensucia el historial con dos filas.

### Propuesta
Tipo propio en la planilla: **`Cambio`** (constante `AccountingEngine.TIPO_CAMBIO`).

| Columna | Valor |
|---|---|
| `Responsable` | Quien lo registra (el usuario activo, como siempre) |
| `Propietario` | = responsable (no hay cambio de dueño) |
| `Metodo Pago` | **Lo que entrega el responsable**: `Efectivo` = "doy efectivo, recibo transferencia"; `Billetera Virtual` = "transfiero, recibo efectivo" |

Regla en el motor (responsable = S, método = Efectivo):

```
sEfec -= m ; sVirt += m      // yo entrego efectivo y recibo en la cuenta
rEfec += m ; rVirt -= m      // el otro al revés
netSR sin cambios, sin flujos (no suma a aportes, gastos ni transfers)
```

Con método virtual se invierten los signos. El pozo total y los saldos finales no se mueven; cambian
solo los 💵/💳 de cada uno. Es una regla nueva y aislada: no afecta ningún movimiento existente.

### UI
- En la pestaña **Transf.**, el selector "¿Qué estás haciendo?" pasa de 2 a 3 botones:
  **Mover plata · Cambio de dinero · Saldo externo** (mismo `ModoTransferenciaBoton`, entran bien
  en una fila de 3 con el texto a 11sp).
- En modo Cambio: se oculta "Método de pago" y "¿La plata sigue siendo tuya?", y aparece
  "¿Qué entregás?" con dos opciones: **💵 → 💳 Doy efectivo** / **💳 → 💵 Transfiero**.
- Categorías 🔧: `Categorias.CAMBIOS = ["Cambio de dinero", "Otros"]`.
- Tarjeta del listado: ícono `CurrencyExchange`, monto **sin signo** (como el perdón), tag "Cambio".
  El detalle muestra "Entregó: efectivo / transferencia".
- El filtro "Transfer." de Inicio lo incluye.

### Archivos
`AccountingEngine.kt` (caso nuevo en `compute`), `google-apps-script.js` (`aplicarMovimientos`),
`Categorias.kt`, `AddMovementScreen.kt`, `DashboardScreen.kt` (ítem, detalle, filtro),
`MovementNotifier.kt` (emoji), `AccountingLogicTest.kt`.

---

## 2. "Perdonar deuda" pasa a llamarse "Saldo externo"

### Propuesta
Es un cambio de **etiqueta**, no de dato: en la planilla el tipo sigue siendo `Condonación`
(renombrarlo obligaría a migrar filas y a mantener un alias para siempre). Lo que cambia es la UI:

- El botón del submodo dice **"Saldo externo"** con bajada según el caso (ver punto 3).
- El tag del listado sigue diciendo "Perdón" para las condonaciones y suma "Pago deuda" para el
  subtipo nuevo del punto 3.
- Categorías de condonación se mantienen (`Perdón de deuda`, `Ajuste`, `Otros`).

En la práctica este punto se implementa junto con el 3, porque "Saldo externo" absorbe las dos
funciones.

---

## 3. "Mover plata" deja de descontar deuda; "Saldo externo" paga o perdona

### El problema, con números
Hoy Santiago tiene 300k en cuentas de Rocío (🤝 "Ro debe 300k"). Si Rocío le transfiere 50k a
Santiago, en cualquiera de las dos variantes se descuenta de la deuda:

- **"No, es de Santiago"** → el motor primero **salda** (`devolucion = min(monto, deuda)`) y solo el
  excedente es regalo. Deuda: 250k.
- **"Sí, es mía"** → Rocío estaciona 50k suyos en la cuenta de Santiago. Como el motor guarda la
  propiedad cruzada como **un único neto con signo** (`netSantiagoEnRocio`), eso compensa contra los
  300k. Deuda neta: 250k.

No hay forma de decir "te regalo 50k y la deuda sigue igual".

### Propuesta

**a) "Mover plata" = la plata sale de mi cuenta y la deuda no se toca.**
La variante "es del otro" pasa a ser **siempre regalo completo**: se elimina la devolución
automática. Rocío −50k físico, Santiago +50k físico, patrimonio de Rocío −50k, 🤝 sigue en 300k.

La variante "sí, es mía" ✅ (decidido: se mantiene) **no se puede separar de la deuda sin volver a dos acumuladores** (que es
justamente lo que se reemplazó en 2026-07 porque era confuso): si Rocío deja plata suya en la cuenta
de Santiago mientras Santiago tiene plata en la de ella, el neto *es* menor. Propongo dejarla como
está: es correcta y es lo que dice el botón. Alternativa: sacarla del formulario (¿se usa?).

**b) "Saldo externo" = la única forma de mover la deuda, con doble función** según el signo del 🤝
del usuario activo, que la app ya conoce (`balance.santiagoExterno` / `rocioExterno`):

| Situación del usuario activo | Botón | Qué hace | Tipo en la planilla |
|---|---|---|---|
| Tiene saldo **a favor** (el otro le debe) | **Perdonar** | No mueve plata; la deuda baja; mi patrimonio baja | `Condonación` (existente, sin cambios) |
| Tiene saldo **en contra** (le debe al otro) | **Pagar** | La plata sale de mi cuenta (💵 o 💳) a la del otro; la deuda baja; nadie gana ni pierde | **`Devolución`** (nuevo) |
| Sin saldo | — | Deshabilitado: "No hay saldo externo entre ustedes" | — |

Por qué **dos tipos** y no un único "Saldo externo" que el motor resuelva por signo: el significado
de una fila no puede depender del estado al momento de replayar. Si se corrige un movimiento
anterior y el 🤝 cambia de signo, un perdón pasaría a ser un pago y **movería plata física** sin que
nadie lo haya pedido. Cada fila tiene que guardar la intención.

Regla de `Devolución` (responsable = R, le debe a S):
```
rVirt/rEfec -= m ; sVirt/sEfec += m        // según método
pagado = min(m, max(0, netSR))              // lo que efectivamente se debía
netSR -= pagado
excedente = m - pagado  -> regalo (rTransfers += excedente)   // solo si se carga a mano
```
Es **exactamente** la regla de hoy de "transferencia es del otro". La UI capa el monto a la deuda
(con atajo "Todo", como el perdón actual), así que el excedente solo aparece con filas cargadas a
mano en la planilla.

Limitación que queda 🔧: el pago lo registra **quien paga** (el responsable siempre es el usuario
activo y la plata sale de su cuenta). El acreedor no puede cargar "Ro me pagó". Hoy pasa lo mismo con
todas las transferencias.

### ⚠️ Compatibilidad con lo ya cargado ✅ (decidido: corte por fecha)

**Verificación sobre la planilla (export `Finanzas (4).xlsx`, 2026-09-30):**

| Mes | Transferencias "es del otro" vivas | Deuda que saldaron con la regla vieja |
|---|---|---|
| Junio | 3 | 0 (no había deuda en ese sentido) |
| Julio | 4 | **60.000** (31/07, Rocío → Santiago, "Reembolso") |
| Agosto | 3 | **60.000** (06/08, Rocío → Santiago, "Reembolso") |
| Septiembre | 0 (las 3 que hay están **eliminadas**) | — |

Septiembre está limpio, pero aplicar la regla nueva a todo **sí afecta**: al recalcular el saldo
inicial de agosto o septiembre, esas dos devoluciones de 60k pasarían a ser regalos y la deuda de
Rocío subiría 60k (cierre de agosto: 372.067,42 → 432.067,42). Por eso va **corte por fecha**.

La fecha del corte se fija en la **fecha de publicación de la 7.7**: con la historia alcanzaría
cualquier fecha posterior al 06/08, pero así también quedan con la regla vieja las transferencias que
se carguen con la app 7.5 hasta que se instale la nueva (cuando quien las cargó esperaba que
saldaran). Antes de publicar, confirmar en la planilla que no haya ninguna posterior a esa fecha.

**Implementado con `2026-09-01`** (`AccountingEngine.CORTE_TRANSFERENCIA_SIN_DEVOLUCION` y la
constante homónima del script; hay que cambiar las dos juntas). Primero se puso `2026-10-01`, pero
probando el 30/09 una transferencia "es de Santiago" (dividir una cuenta a medias) se siguió
descontando de la deuda: caía antes del corte. Como septiembre no tiene ninguna "es del otro" viva, el
corte se adelantó al 1° de septiembre: el mes en curso ya usa la regla nueva y agosto (de donde sale
la apertura de septiembre) conserva la vieja.

Dato al margen: la transferencia viva de septiembre (12/09, Rocío, 72.067,42, "Ajuste", **"sí, es
mía"**) es justo el caso del punto 3a: fue la que bajó la deuda de 372k a 300k. Con la 7.7 sigue
funcionando igual (se decidió mantener esa variante).

Opciones que se evaluaron:
Cambiar la regla de `Transferencia` "es del otro" **recalcula distinto las transferencias
existentes** en todo mes que se replaye:

- el mes en curso (septiembre, y octubre si ya hay transferencias cuando salga la 7.7);
- cualquier mes al tocar **Ajustes → Recalcular saldo inicial**;
- cualquier `escribirApertura(mes)` / `migrarAperturas()` del script.

Los meses con apertura escrita no se ven afectados al *mostrarlos* (no replayan hacia atrás), pero
sí al *recalcularlos*. Opciones:

1. **Fecha de corte (recomendado).** `CORTE_TRANSFERENCIA_SIN_DEVOLUCION = "2026-10-XX"` (el día que
   se publique). Una `Transferencia` "es del otro" con fecha anterior al corte se sigue tratando como
   hoy (salda primero). Nada histórico cambia, ni siquiera al recalcular. Costo: una constante y un
   `if` en los dos motores, y un test que la fije.
2. **Aplicar la regla nueva a todo.** Más simple, pero el saldo de septiembre (y lo que se recalcule)
   puede cambiar. Habría que revisar antes cuántas transferencias de ese tipo saldaron deuda.
3. **Migrar filas.** Un script que convierta esas transferencias viejas a `Devolución` + excedente.
   Toca datos y parte filas en dos; no lo recomiendo.

### UI
- Submodo **Saldo externo**: la tarjeta de contexto que hoy existe para el perdón se generaliza:
  - a favor → "Ro tiene $X tuyos · Perdonar (no se mueve plata)" + botón "Todo".
  - en contra → "Tenés $X de Ro · Pagar" + botón "Todo" + **selector de método de pago** (sale plata).
- "Mover plata" pierde la bajada "Sale de tu cuenta" ambigua → 🔧 "Sale de tu cuenta, no toca la
  deuda". Y la pregunta "¿La plata sigue siendo tuya?" se mantiene si se conserva la variante (a).
- Listado/detalle: `Devolución` con ícono `Handshake`, signo "−", tag "Pago deuda".

### Archivos
`AccountingEngine.kt` (`transferencia` sin devolución + corte, caso `devolución`, `TIPO_DEVOLUCION`,
`isDevolucion`), `google-apps-script.js` (`aplicarMovimientos`), `Categorias.kt`
(`DEVOLUCIONES = ["Pago de deuda", "Ajuste", "Otros"]`), `AddMovementScreen.kt`,
`DashboardScreen.kt`, `MovementNotifier.kt`, `AccountingLogicTest.kt` (reescribir los tests de
devolución de transferencias: pasan a ser tests de `Devolución` + tests del corte).

---

## 4. Gastos evitables / no evitables

### Modelo
- `Movement.evitable: Boolean = false` → **columna O (índice 14) "Evitable"** en las hojas de mes.
- `CuotaPlan.evitable: Boolean = false` → **columna L (índice 11) "Evitable"** en `Planes`.
- Vacío / cualquier cosa que no sea `true` = **no evitable**. Todo lo histórico queda como no
  evitable sin migrar nada.
- Se nombra `evitable` (y no `indispensable`) a propósito: Gson ignora los defaults de Kotlin al
  deserializar, así que un campo ausente queda en `false`. Con `evitable`, eso coincide con el
  default pedido en la cola de pendientes (`PendingMovement`), el cache y la base local demo.
- Solo aplica a `Gasto`. En otros tipos se guarda `false`.

### Alta de gastos
- 🔧 Switch en el **TopAppBar, a la izquierda de "Limpiar"**, visible solo con tipo Gasto: un chip
  que alterna **🍞 Necesario** ↔ **🍰 Evitable** (default 🍞). Emoji + palabra corta para que no haya
  que adivinar qué significa.
- Va al `MovementDraft` (`evitable`) para sobrevivir el cambio de pestaña; "Limpiar" y el guardado
  lo vuelven a 🍞.

### Cuotas
- Mismo switch en el TopAppBar del formulario de plan (`PlanFormContent`), en alta y edición.
  `CuotaDraft.evitable`.
- `pagarCuotas` y `confirmarCuota` copian `evitable = plan.evitable` al `Movement` del pago.
- 🔧 **Se copia al pagar, no se deriva**: los planes pagados no están cargados (se piden on-demand),
  así que derivarlo desde el plan dejaría sin dato a los pagos de planes terminados. Editar el
  flag de un plan **no** reescribe los pagos ya hechos (se puede corregir cada pago con la edición
  del punto 6).

### Tablero "Gastos acumulados" (Métricas, debajo de "Aportes acumulados")
- Una barra por persona, mismo formato que "Aportes acumulados": el largo es la parte de esa persona
  sobre el total de ambos.
- Cada barra partida en dos tramos: **color de la persona = no evitable**, **rojo (`error`) =
  evitable**. Debajo, los dos parciales con su número: "🍞 $X · 🍰 $Y" y el total.
- Atribución con `AccountingEngine.porcionDelGasto` (los comunes se parten 50/50), igual que el
  resto de Métricas. Mismo criterio de exclusión que el punto 8.
- La lógica va en una función pura testeable (`gastosPorEvitabilidad(movs, slotKey)`), no en el
  composable.

### Visualización
- 🔧 Tarjeta del listado: un "🍰" chico junto al tag Común/Pers. en los gastos evitables. Detalle:
  fila "Tipo de gasto: Necesario / Evitable".

### Apps Script
- `doGet` lee `row[14]`; `leerTodosLosMovimientos` también (no lo usa el motor, pero mantiene el
  objeto completo).
- Alta/PUT escriben **15 columnas**; los encabezados de hoja nueva suman "Evitable". Para hojas
  existentes, asegurar el encabezado O1 (mismo patrón que `asegurarColumnaPrevias`).
- `handlePlanUpsert` / `getPlans`: columna L, con el mismo cuidado de conservar `Cuotas Pagadas
  Previas` (col K) al hacer PUT.
- `escribirAperturaDeMes` no necesita cambio (las aperturas no son gastos).

---

## 5. Categoría "Corrección" en Gasto y Aporte

Trivial: se agrega a `Categorias.GASTOS` y `Categorias.APORTES` antes de "Otros". La categoría viaja
como texto libre, así que es retrocompatible. Hay que ajustar `CategoriasTest` (hoy fija que
"Donación" es la anteúltima de gastos).

Relación con el punto 8: ✅ decidido — ¿una corrección cuenta como gasto/ingreso "real" en Métricas e Inicio?
Propongo que **no** (es un ajuste para que la app cuadre con el banco, no un consumo).

---

## 6. Editar un movimiento

### Flujo
1. Detalle del movimiento → botón **"Editar"** a la izquierda de "Cerrar" (`dismissButton` del
   `AlertDialog`).
2. `viewModel.cargarParaEditar(m)`: arma el `MovementDraft` desde el movimiento (partiendo `fecha`
   en fecha + hora) y marca `editandoId = m.id`. `MainActivity` cambia a la pestaña **Nuevo**.
3. `AddMovementScreen` se siembra del borrador como ya hace hoy. En modo edición: título
   "Editar movimiento", botón "Guardar cambios", y "Limpiar" pasa a "Cancelar" (descarta la edición y
   vuelve a un alta vacía).
4. Guardar → mismo `encolar()` que el alta, **con el mismo id**. La cola ya sube con `PUT` (upsert
   por id), así que no hace falta un camino nuevo: fila optimista, reintento y notificación salen
   gratis.

### Qué se conserva al editar
`id`, `planId`, `cuotaNumero` y `ticketUrl` (si no se adjunta una foto nueva; el script ya hace
`mov.ticketUrl || data[i][9]`). La hora original. Todo eso viaja en el borrador
(`ticketUrlExistente`, `planId`, `cuotaNumero`).

### Problemas encontrados que hay que resolver

1. **El PUT no soporta cambio de mes.** El script elige la hoja por la fecha *nueva* y busca el id
   solo ahí. Si edito el 01/10 y lo paso al 30/09, no lo encuentra en "Septiembre", apendea una fila
   nueva y **la vieja queda viva en "Octubre"** → movimiento duplicado (el cliente lo esconde con
   `distinctBy { id }`, pero la planilla cuenta dos veces y el server también, en las aperturas).
   Fix en el script: buscar el id en **todas** las hojas de movimientos; si está en otra hoja, darla de
   baja ahí (borrar la fila o marcar `Eliminado`) y escribir en la hoja correcta.

2. **La fila optimista no pisa a la vieja.** `updateFilteredData` descarta los pendientes cuyo id ya
   está en la lista (`filter { p -> cached.none { it.id == p.id } }`), pensado para altas. En una
   edición el id **siempre** está, así que la edición no se vería hasta el próximo refresh. Fix: el
   pendiente reemplaza al cacheado con el mismo id.

3. **Carrera en la cola.** `MovementUploadWorker.drenar` toma una foto de la cola, sube y después hace
   `removePendingMovement(id)`. Si se edita un movimiento que se está subiendo en ese momento, el
   worker borra de la cola **la versión editada** y esta nunca se sube. Fix: sacar de la cola solo si
   la entrada sigue siendo la misma que se subió (comparar `encoladoEn` o el `Movement` completo).

4. **Tope del Saldo externo.** El tope (y el atajo "Todo") se calcula con el balance actual, que
   **ya incluye** el movimiento que se está editando: al editar un perdón de 100k que canceló toda la
   deuda, el 🤝 da 0 y el botón queda deshabilitado. Fix: en edición, calcular la deuda con el
   balance del mes **sin** ese movimiento.

5. **Aperturas viejas.** Editar un movimiento de un mes que ya tiene meses posteriores con apertura
   deja esas aperturas desactualizadas (riesgo ya documentado en el motor). Hoy la pestaña Nuevo
   **solo existe en el mes en curso** (`MainActivity` la oculta y rebota si `!isCurrentMonth`).

### Alcance ✅ (decidido: propios y del mes en curso)
Propuesta: se puede editar **solo lo propio** (`responsable == usuario activo`, misma regla que el
swipe para borrar) y **solo en el mes en curso** (que es cuando existe la pestaña Nuevo). Así el punto
5 queda acotado a quien cambie la fecha hacia el mes anterior en los primeros días del mes, y para eso
alcanza con un aviso "Recordá recalcular el saldo inicial" si la fecha nueva cae en un mes cerrado.

Defaults 🔧:
- Pago de cuota (`planId` no vacío): se edita monto/fecha/método/descr./evitable, pero el **tipo
  queda fijo en Gasto** (cambiarlo rompería el vínculo con la cuota).
- Si al tocar "Editar" hay un borrador de alta con contenido, se pide confirmación antes de pisarlo.
- Un aporte viejo "de Rocío" (antes del punto 7) al editarse pasa a ser mío: se avisa en pantalla.

### Archivos
`Drafts.kt`, `AhorroViewModel.kt` (`cargarParaEditar`, `guardarEdicion`, merge de pendientes),
`AddMovementScreen.kt` (modo edición), `DashboardScreen.kt` (botón + callback), `MainActivity.kt`
(navegación), `MovementUploadWorker.kt` + `PreferencesHelper.kt` (remoción condicional),
`google-apps-script.js` (PUT multi-hoja), `ColaDeSubidaTest.kt`.

---

## 7. Aporte solo a mi nombre

Se elimina la sección "¿En qué cuenta entra?" para Aporte: `propietario = responsable = usuario
activo` siempre. Cambio de UI puro (`AddMovementScreen`: la condición que muestra la sección de
propiedad y el reset de propietario). El motor sigue sabiendo leer los aportes viejos con otro
propietario (no se borra ese código: hay filas así en la planilla).

---

## 8. "Sueldo" → "Ingreso"; aportes y gastos "reales"

### Qué se pide
En Inicio ("Aportes (+)" de cada tarjeta) y en Métricas ("Aportes acumulados") el aporte tiene que
contar **solo la categoría Ingreso**, para separar lo que es plata nueva de transferencias entre
ustedes, ajustes, cambios, etc. Y los gastos tienen que excluir las transferencias entre ustedes.

### Propuesta
- `Categorias.APORTES = ["Ingreso", "Transferencias", "Corrección", "Otros"]`.
- **Sin migrar la planilla**: `"Sueldo"` se trata como alias de `"Ingreso"` en una única función
  `Categorias.esIngreso(categoria)`. 🔧 Opcional: función `renombrarSueldoAIngreso()` en el script
  para correr una vez a mano si querés la planilla prolija.
- El motor gana contadores **nuevos** (`santiagoIngresos`, `rocioIngresos`, y los gastos "reales"
  por persona), calculados con la misma atribución por propietario que los aportes. Los contadores
  actuales no se tocan: no intervienen en ningún saldo y los tests los fijan.
- Inicio: "Aportes (+)" muestra ingresos 🔧 y pasa a llamarse **"Ingresos (+)"**. Métricas: el
  tablero pasa a **"Ingresos acumulados"** 🔧 (se puede dejar el nombre viejo si preferís).

### Qué gastos se excluyen ✅ (decidido: solo Corrección)
Hoy los gastos del motor y de Métricas **ya excluyen** las transferencias (`Transferencia`,
`Condonación`, y excluirán `Cambio` / `Devolución`): solo suman filas de tipo `Gasto`. Así que no
tengo claro qué "transferencia entre nosotros" aparece hoy como gasto. Candidatos:
- la categoría nueva **Corrección**;
- alguna categoría de gasto que usen para pasarse plata (¿"Donación"? ¿"Otros"?);
- gastos viejos cargados con una categoría "Transferencia(s)" antes de que existiera el tipo.

La exclusión se aplicaría en: "Personales (−)" de Inicio, las 3 tarjetas de categorías de Métricas y
el nuevo "Gastos acumulados".

---

## Apps Script v7.7 (resumen)

1. `aplicarMovimientos`: casos `cambio` y `devolución`; `transferencia` "es del otro" sin devolución
   (con el corte de fecha si se elige la opción 1 del punto 3).
2. Movimientos: columna O `Evitable` en lectura (doGet, `leerTodosLosMovimientos`), escritura (alta y
   PUT, 15 columnas) y encabezados.
3. PUT de movimientos buscando el id en todas las hojas y moviéndolo si cambió de mes.
4. Planes: columna L `Evitable` en `handlePlanUpsert` y `getPlans`.
5. Encabezado del archivo: "Versión: 7.7" + novedades.
6. (Opcional) `renombrarSueldoAIngreso()`.

**Hay que desplegarlo antes de repartir el APK**, o las ediciones que cambien de mes duplican filas y
los gastos evitables se guardan como no evitables.

## Orden de implementación sugerido

1. Motor + tests (puntos 1 y 3), con el port al Apps Script en el mismo paso.
2. Categorías + aporte a mi nombre + ingresos (5, 7, 8): chicos y sin riesgo.
3. Evitable (4): modelo → script → alta → cuotas → tablero.
4. Edición (6): script (PUT multi-hoja) → cola → VM → UI.
5. `./gradlew testDebugUnitTest` + prueba en modo demo + prueba contra la planilla real con el script
   nuevo desplegado.

## Tests a agregar / ajustar

- `AccountingLogicTest`: cambio de dinero (buckets se cruzan, pozo y saldos iguales); transferencia
  "es del otro" con deuda vigente → regalo completo, deuda intacta; transferencia anterior al corte →
  comportamiento viejo; `Devolución` (paga, clampea, excedente = regalo); ingresos solo de "Ingreso" /
  "Sueldo".
- `CategoriasTest`: Corrección, Ingreso como default de Aporte, listas nuevas (`CAMBIOS`,
  `DEVOLUCIONES`), `esIngreso`.
- Nuevo: `gastosPorEvitabilidad` (comunes 50/50, default no evitable, exclusiones).
- `CuotasEngineTest` / VM: el pago hereda `evitable` del plan.
- `ColaDeSubidaTest`: la edición durante una subida no se pierde.
- Serialización: un `Movement` / `PendingMovement` JSON sin `evitable` deserializa como no evitable.

## Preguntas abiertas

1. ✅ **Transferencias viejas "es del otro"** (punto 3): **corte por fecha** (la regla nueva afectaba
   dos devoluciones de 60k de julio y agosto al recalcular; ver la verificación en el punto 3).
2. ✅ **Transferencia "sí, es mía"** (punto 3a): **se mantiene** y sigue neteando contra la deuda.
3. ✅ **Gastos excluidos** de Inicio/Métricas (punto 8): **solo la categoría Corrección** (punto 5).
4. ✅ **Alcance de la edición** (punto 6): **solo lo propio y del mes en curso**.

Los 🔧 (emojis 🍞/🍰, nombres "Ingresos (+)"/"Ingresos acumulados", categorías de Cambio y
Devolución, evitable copiado al pagar, alias de Sueldo) los tomo como default salvo que digas otra
cosa.

---

# 7.7.1

- **Modo claro (traído de la rama 7.6):** `Color.kt`/`Theme.kt` con roles Material 3 completos,
  `LocalIsDarkTheme` en vez de comparar `background == DarkBackground`, textos secundarios con
  `onSurfaceVariant` / `appTextMuted` en vez de alphas sobre `onSurface`, y las barras del sistema
  siguiendo el tema de la app (`aplicarEstiloDeBarras`). Se aplicó también al código nuevo de la 7.7.
- **Notificaciones:** `SINGLE_TOP` en vez de `CLEAR_TOP` (tocar una notificación destruía la
  Activity y se perdía el borrador del alta).
- **Categorías en dos niveles:** rubros (`Categorias.RUBROS_GASTOS`: Comida, Casa, Transporte, Salud,
  Ropa, Salidas, Otros) como vista sobre la lista plana, que sigue siendo la fuente de verdad. Selector
  compartido `CategoryPicker` (Frecuentes + rubros; Corrección aparte) en Nuevo y en Cuotas (sin
  Corrección). Métricas agrupa por rubro con despliegue a categorías y salto a Inicio con una o todas.
  "Gustos" deja de ofrecerse (lo reemplaza 🍰 Evitable); Verdulería y Alimentos frescos siguen
  separadas. Nombres viejos de la planilla ("Transporte publico", "Ropa", "Otros personales/comunes",
  "Hobbies", "Gustos") se mapean sin migrar filas.
- **Fix de la 7.7:** `esIngreso` no reconocía "Sueldo Rocío" / "Sueldo Santiago" (14 aportes en la
  planilla real): ahora cuenta todo lo que empieza con "Sueldo".
- **Ajustes del selector (feedback):** Supermercado pasó a 🏠 Casa. En "Todas" van primero los
  rubros que se despliegan, después los de una sola categoría y al final Corrección; lo desplegado
  aparece justo debajo de la fila del rubro tocado (`FilasConPanel`), no al final.
- **Paleta cálida del modo claro:** crema (`#FBF5EC`), tarjetas blanco cálido (`#FFFCF7`), arena
  (`#F5E8DA`), grises tostados y error terracota. Los colores de usuario claros se suavizaron
  (salvia, azul empolvado…). Todo texto sigue >= 4.5:1 y el blanco sobre los rellenos >= 5.5:1. El
  modo oscuro no cambió.
- **Grilla pareja en el selector:** todas las celdas (frecuentes, rubros, lo desplegado y las listas
  cortas) tienen el mismo ancho y alto y la misma letra (11sp, hasta 2 renglones). Columnas según el
  ancho: `ancho / 92dp`, mínimo 3 (teléfonos comunes) y 4 desde ~370dp de contenido (S25 Ultra y
  similares). Frecuentes muestra siempre dos filas completas.
- **Un poco más de contraste en claro (feedback):** lienzo algo más tostado (`#F5ECDF`) para que las
  tarjetas se despeguen, bordes más visibles (`#D6C4AF`), texto principal/secundario/atenuado un tono
  más oscuros. Ámbar y rosa se oscurecieron apenas para seguir >= 4.5:1 sobre la tarjeta arena.
- **Título de Inicio:** "Hoy es {día} {emoji}" (un emoji por día, `saludoDeHoy`), siempre con el día
  real aunque se mire un mes viejo. El selector de mes va debajo, chico y en tipografía secundaria, con
  los meses escritos ("Septiembre 2026"). El cartel "Sincronizado con Google Sheets" se sacó; "Modo
  local (demo)" se sigue mostrando al lado del mes.
- **Mensaje del día (Gemini):** `generarMensajesDelDia()` en el Apps Script, con disparador diario
  ~2 AM hora Argentina (`instalarTriggerDeMensajes()`). Hace **un** request por día con los gastos y
  aportes vivos de la hoja del mes actual y la del anterior, solo día|de quién|tipo|categoría|monto
  redondeado|descripción (40 caracteres)|evitable: unos 4.000 tokens con los datos de sep-2026. Pide un
  mensaje de hasta 60 caracteres por persona, en JSON, y lo escribe en la hoja `Usuarios` (E
  "Mensaje", F "Fecha mensaje"), pisando el anterior. Si falla, queda el de ayer y la app no lo
  muestra (solo muestra el de **hoy**: `mensajeDelDiaVisible`).
  - Clave en las propiedades del script (`GEMINI_API_KEY`), nunca en el APK ni en el repo. Opcionales:
    `GEMINI_MODELO` (lista en orden de preferencia; default `gemini-3.8-flash,gemini-3.5-flash-lite,gemini-2.5-flash`: si uno da error de cuota, no existe o devuelve JSON inservible, se prueba el siguiente; thinking al mínimo y 2048 tokens de salida en 3.x, apagado en 2.5) y `MENSAJES_ACTIVOS=false`
    para dejar de llamar a Gemini.
  - En la app: `MENSAJE_DEL_DIA=false` en `.env` (o la variable del repo `MENSAJE_DEL_DIA` en CI) lo
    apaga. Sin mensaje de hoy, el encabezado es el de siempre (TopAppBar); con mensaje pasa a un
    encabezado de alto variable: día (20sp) · mensaje (13sp, hasta 2 renglones, alineado a la
    izquierda) · selector de mes (11sp, atenuado).
  - Privacidad: en el nivel gratuito de Gemini, Google puede usar lo enviado para mejorar sus
    productos. Se aceptó a cambio de los comentarios sobre descripciones concretas.
  - Prompt ajustado tras el primer resultado real ("¡Cuidado con tanto transporte, Rou!"): elige UNA
    cosa por persona con prioridad descripción puntual/gusto > novedad > buena noticia; prohíbe
    comentar gastos de rutina o necesarios (lo más frecuente no es lo más interesante) y limita las
    advertencias en broma a lo evitable. Incluye ejemplos buenos y malos.
