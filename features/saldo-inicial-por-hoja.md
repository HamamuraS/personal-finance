# Saldo inicial materializado por hoja

## El problema

Hasta la v7.1 el saldo inicial de un mes **no existía como dato**: se derivaba replayando todos los
movimientos de todos los meses anteriores (`AccountingEngine.opening(prior)` sobre `monthOf < mes`).

Consecuencias:

- **Ninguna hoja vieja se podía archivar.** Medido sobre la planilla real: borrar la hoja "Mayo 2026"
  (25 filas) hacía que la apertura de agosto de Santiago pasara de `2.590.220,23` a `121.597,03`.
- **Los errores se arrastran para siempre.** Un gasto no registrado en mayo infla el saldo de agosto
  y no hay forma de cortar el arrastre.
- **Auditar un mes obliga a replayar los anteriores.**

Las filas `Saldo inicial` que inyectaban las versiones viejas no servían: eran un único `Aporte` por
persona, siempre como "Billetera Virtual" y sin propietario, así que perdían el efectivo y la
propiedad cruzada. Por eso estaban filtradas.

## La solución

Cada hoja de mes lleva sus propias **filas de apertura** con el stock inicial desagregado. Reusan las
columnas existentes:

| Columna | Significado en una fila de apertura |
|---|---|
| `Tipo` | `Apertura` (reservado; marca la fila como *stock*, no como flujo) |
| `Categoría` | `Saldo inicial` (reservado, nunca elegible por el usuario) |
| `Responsable` | De quién es la cuenta donde está físicamente el dinero |
| `Propietario` | De quién es realmente el dinero |
| `Metodo Pago` | `Efectivo` \| `Billetera Virtual` |
| `Monto` | **Puede ser negativo** |

Son 4 filas (2 personas × 2 medios) más una quinta cuando hay propiedad cruzada.

### Por qué hace falta el propietario

El estado inicial **no** son cuatro números. Es un vector de cinco: los cuatro buckets físicos más
`netSantiagoEnRocio`, el neto de dinero de uno que está en cuentas del otro. Sin la quinta pieza, un
préstamo se evapora al cambiar de mes.

Ejemplo real (cierre de agosto 2026): Rocío tiene 69.955,96 físicos, de los cuales 60.000 son de
Santiago. La apertura de septiembre queda:

| Responsable | Propietario | Método | Monto |
|---|---|---|---|
| Santiago | Santiago | Efectivo | 55.450,00 |
| Santiago | Santiago | Billetera Virtual | 3.540.917,09 |
| Rocío | Rocío | Efectivo | 11.400,00 |
| Rocío | Rocío | Billetera Virtual | **−2.544,04** |
| Rocío | Santiago | Billetera Virtual | 60.000,00 |

Las dos últimas suman los 57.455,96 virtuales de Rocío y reconstruyen su posición externa de
−60.000. El monto negativo es correcto: gastó más de lo propio y está usando el préstamo.

La propiedad cruzada se ancla siempre al bucket **virtual** de quien tiene el dinero físicamente. Da
igual a qué medio se impute (el neto no depende del medio de pago), pero fijarlo mantiene la
generación determinística.

## Resolución del arrastre

`AccountingEngine.openingFor(all, mes)`:

1. El mes **tiene** filas de apertura → se usan tal cual. No mira ningún mes anterior.
2. No las tiene → replay desde la apertura disponible más reciente que sea anterior.
3. No hay ninguna apertura en toda la planilla → replay completo desde cero (modo ≤ 7.1).

El paso 2 es lo que permite purgar de a poco: alcanza con que *algún* mes anterior tenga su apertura.

## Puntos finos

- **La apertura es stock, no flujo.** Si se cargaran como `Aporte` normal contaminarían
  `totalAportesMes` y todo el tab de Métricas. `compute()` las saltea explícitamente y entran solo
  por el parámetro `opening`.
- **Ids determinísticos** (`apertura-<mes>-<r|s>-<r|s>-<efec|virt>`): regenerar pisa las filas en vez
  de duplicarlas, tanto desde la app (upsert vía `PUT`) como desde el Apps Script.
- **Staleness.** Si se corrige un movimiento de un mes ya cerrado, las aperturas posteriores quedan
  viejas y nada avisa. Por eso existe el botón **Ajustes → Saldo inicial del mes → Recalcular**.
  Una vez purgadas las hojas viejas, la fila de apertura *es* la fuente de verdad y ya no se puede
  recalcular: es una decisión consciente.
- **Filas legacy.** Las `Saldo inicial` de tipo `Aporte` se siguen ignorando
  (`AccountingEngine.isLegacyCarryover`). Si simplemente se dejaran de filtrar, junio y julio se
  duplicarían.

## Operación

Funciones en `google-apps-script.js` (bloque "SALDO INICIAL MATERIALIZADO").

### Automático (recomendado)

`instalarTriggerDeAperturas()` — se corre **una sola vez** desde el editor. Deja un trigger diario
(~4 AM) que ejecuta `actualizarAperturas()`.

Es diario y no mensual a propósito: los movimientos se cargan con atraso. Si el 3 de septiembre se
agrega un gasto con fecha 31 de agosto, la apertura de septiembre queda vieja; al día siguiente el
trigger la corrige sola. Escribir una única vez el día 1 congelaría ese error para siempre.

`actualizarAperturas()` recalcula todas las aperturas encadenando valores recalculados (no los
guardados), y además le escribe la suya al mes calendario actual aunque todavía no tenga movimientos,
creando la hoja si hace falta. `desinstalarTriggerDeAperturas()` lo da de baja.

### Migración inicial (una vez)

1. `previsualizarAperturas()` — no escribe nada; muestra con qué **arranca** y con qué **cierra**
   cada mes. El bloque `[CIERRA]` del último mes es el estado de hoy: es lo que se concilia contra el
   homebanking.
2. `migrarAperturas()` — escribe las aperturas de todos los meses menos el primero.
3. `borrarLegacySaldoInicial()` — borra las filas viejas de arrastre. Correr **después** de validar.

`escribirApertura("2026-09")` recalcula un mes puntual.

### Archivar hojas viejas

Verificar que el mes más viejo que se conserva tenga su apertura escrita, y recién ahí borrar las
anteriores. Olvidarse de escribir una apertura **no rompe nada** mientras las hojas previas existan:
el motor cae al replay desde el ancla anterior.

> ⚠️ **`aplicarMovimientos` ignora las filas de apertura** (son stock, no flujo). Por eso todo lo que
> derive un arrastre tiene que sembrarse con la apertura escrita del mes más viejo disponible en vez
> de replayar desde cero — ver `calcularAperturasPorMes` y `calcularAperturaDe`. Replayar desde cero
> funciona mientras estén todas las hojas y da un patrimonio truncado apenas se archiva alguna
> (medido: Santiago pasaba de 3.540.917,09 a 1.011.146,86 al archivar mayo–julio).

El motor está portado a JS en `aplicarMovimientos()`, verificado contra el motor Kotlin sobre la
planilla real (mismos números en las aperturas de junio, julio, agosto y septiembre).

## Tests

`app/src/test/java/com/example/AccountingLogicTest.kt`:

- `lasFilasDeAperturaHacenRoundTripExacto` — los tres signos de la propiedad cruzada.
- `laAperturaConservaElPrestamoCruzado` — el caso real de los 60k.
- `conAperturaSePuedenPurgarLosMesesAnteriores` — borrar junio no cambia julio ni agosto.
- `laAperturaNoSumaALosFlujosDelMes` — no contamina aportes/gastos.
- `lasFilasLegacyDeSaldoInicialSiguenIgnorandose` — no duplican ni sirven de ancla.
