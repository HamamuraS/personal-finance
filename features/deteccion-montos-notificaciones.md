# Detección del monto desde las notificaciones de las billeteras

Cuando pagás con Ualá, Santander, BBVA o Naranja X, la billetera lanza una notificación con el
importe. La app la aprovecha para ahorrarte lo único laborioso del alta: tipear el número.

**No automatiza el alta.** De la notificación se saca **únicamente el monto**. Ni comercio, ni
contraparte, ni si es gasto o ingreso, ni categoría: todo eso lo seguís eligiendo a mano.

## Flujo

1. `BilleterasNotificationListener` recibe una notificación.
2. Si el package no está en la lista curada, se descarta **en la primera línea**, sin leer el
   contenido.
3. `MontoParser` busca el primer importe que no sea un saldo. Si no encuentra ninguno, silencio.
4. `DedupDeMontos` descarta el repetido si el mismo importe del mismo banco ya se vio hace menos de
   dos minutos.
5. `MontoDetectadoNotifier` lanza la notificación propia: "Detectamos un movimiento por $X".
6. Al tocarla, se abre la pestaña Nuevo con el importe cargado (`EXTRA_MONTO_SUGERIDO` →
   `AhorroViewModel.montoSugerido` → el campo del formulario). El resto se completa como siempre.

## Por qué es de bajo mantenimiento

Porque parsea un solo número. Lo único que tiene que sobrevivir a un cambio de copy del banco es que
siga escribiendo el importe con signo de peso. No hay reglas de clasificación ni listas de palabras
clave por entidad: un solo parser sirve para los cuatro bancos y para los que vengan.

## Decisiones que no son obvias

- **El formato es argentino** (`$1.234,56`): punto de miles, coma decimal. El grupo de miles exige
  exactamente tres dígitos, para que `$5.00` no se lea como cinco mil.
- **Varios importes en un mismo texto.** "Pagaste $5.000 · saldo disponible $12.300" tiene dos. Se
  toma el primero que no venga precedido (dentro de 32 caracteres) por *saldo*, *disponible*,
  *límite*, *cupo* o *restante*. Si el único importe del mensaje es un saldo, no se notifica nada:
  no hubo movimiento.
- **Deduplicación por (package, monto, 2 minutos).** Los bancos no publican y se olvidan: actualizan
  la misma notificación varias veces y cada actualización vuelve a disparar `onNotificationPosted`.
  El costo del filtro es perder el segundo de dos pagos idénticos en dos minutos; el beneficio es no
  gritar cuatro veces por compra.
- **La propia app queda excluida siempre**, o `MontoDetectadoNotifier` se leería a sí mismo en loop.
- **`launchMode="singleTop"` + `FLAG_ACTIVITY_SINGLE_TOP`.** Con el `CLEAR_TOP` que usaban los otros
  notifiers, la Activity viva se destruía y se recreaba en vez de recibir `onNewIntent`: se perdía el
  ViewModel y con él el borrador del alta a medio escribir. Es el peor momento posible para perderlo,
  porque esta notificación se toca justo mientras se está cargando un movimiento. Se cambió también
  en `MovementNotifier` y `CuotasNotifier`, que tenían el mismo problema latente.
- **El monto sugerido es un evento y no parte del borrador.** El borrador se siembra una sola vez, en
  la primera composición del alta; si la pantalla ya estaba compuesta, un cambio en el borrador no la
  toca. Por eso va en un `StateFlow` propio que la pantalla observa y después limpia (mismo patrón
  que `scrollAFiltros`).
- **Los packages no se descubren escaneando las apps instaladas.** Eso exigiría
  `QUERY_ALL_PACKAGES`, un permiso restringido en Play que habría que justificar ante revisión para
  una comodidad como esta. En su lugar hay una lista curada en código más un **modo descubrimiento**
  temporal (10 minutos, se apaga solo) que muestra en Ajustes el package de la última notificación
  con monto y permite agregarlo. Lo agregado vale igual que la lista del código.
- **Se guarda la fecha de la última detección.** No es decoración: en varios fabricantes la
  optimización de batería mata al listener sin avisar, y sin esa fecha la muerte del servicio es
  indistinguible de "no compré nada esta semana". Además, abrir la app dispara un `requestRebind`.

## Compliance con Google Play

- `BIND_NOTIFICATION_LISTENER_SERVICE` **no** está en la lista de permisos restringidos de Play: no
  hay que pedir excepción ni llenar formulario de declaración. Sí aplica la política general de
  acceso a información sensible.
- **Opt-in y prescindible.** La app funciona completa sin el permiso. El default de
  `deteccionMontosActiva` es `false` y no se enciende sola ni aunque el permiso de Android ya esté
  dado.
- **Prominent Disclosure & Consent.** `DivulgacionMontosDialog` explica qué se lee, de qué apps y
  para qué **antes** de mandar al usuario al setting de Android. Ni la política de privacidad ni el
  diálogo del sistema alcanzan para cumplir este requisito.
- **Acceso mínimo necesario**, y de forma demostrable: allowlist de packages y un solo número
  extraído. El contenido de la notificación no se guarda, no se loguea y no sale del dispositivo; el
  monto llega a la planilla solo si el usuario confirma el alta a mano.
- **Falta para publicar**: política de privacidad con URL, formulario de Data Safety declarando
  información financiera con honestidad, y notas de revisión con un video del flujo.

## Archivos

| Archivo | Rol |
| --- | --- |
| `MontoParser.kt` | Parseo del importe. Puro, con `MontoParserTest`. |
| `DedupDeMontos.kt` | Filtro de repetidos. Puro, con `DedupDeMontosTest`. |
| `BilleterasNotificationListener.kt` | El servicio: allowlist, extracción, persistencia mínima. |
| `MontoDetectadoNotifier.kt` | La notificación propia y su intent hacia la pestaña Nuevo. |
| `DeteccionMontosCard.kt` | Ajustes: interruptor, divulgación, estado y modo descubrimiento. |
