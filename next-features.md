
# Hecho

- **Versión 7.7** (detalle en `features/version-7.7.md`): cambio de dinero, "Saldo externo"
  (perdonar o pagar), transferencias "es del otro" sin devolución automática desde el corte,
  gastos evitables + tablero "Gastos acumulados", categoría Corrección, edición de movimientos,
  aporte solo a nombre propio, "Sueldo" → "Ingreso" (Inicio y Métricas cuentan solo ingresos).

- **Condonación (perdón de deuda).** Tipo propio `Condonación` en los dos motores; submodo de la
  pestaña "Transf." con atajo "perdonar todo". No mueve plata: solo cancela propiedad cruzada.
- **1 · Candado en el Apps Script.** `doPost` toma `LockService.getScriptLock()` y delega en
  `handlePost`; el upsert dejó de ser leer-y-después-escribir sin protección.
- **2 · Todas las escrituras con `PUT`.** `duplicateMovement`, `confirmarCuota`, `pagarCuotas` y
  `savePlan` pasaron de `POST` (append ciego) al upsert idempotente por id.
- **3 · `pagarCuotas` reintentable.** El id del pago sale de `CuotasEngine.idDePago(planId, numero)`
  en vez de un UUID nuevo por intento: reintentar un lote a medio escribir pisa las filas en vez de
  duplicarlas, y pagar dos veces la misma cuota se volvió imposible.
- **4a · El corte de mes cubre las cuotas.** Columna K `Cuotas Pagadas Previas` en la hoja `Planes`
  (`CuotaPlan.cuotasPagadasPrevias`), unión —nunca reemplazo— en `CuotasEngine.pagadasDe`,
  `AhorroRepository.isPlanComplete` y `getPlans`. La calcula el servidor (`SNAPSHOT_CUOTAS`), que ya
  recorre todas las hojas. El trade-off quedó documentado en `features/modulo-cuotas.md`.
- **4b · El corte se dispara solo.** `BackgroundSyncWorker.materializarCorteDelMes` escribe la
  apertura del mes en curso si le falta y pide el snapshot de cuotas **siempre**. Las dos mitades van
  por separado a propósito: el trigger diario del script (`actualizarAperturas()`, que hay que
  instalar una vez desde el editor) escribe aperturas pero no sabe nada de cuotas, así que atar el
  snapshot a "acabo de escribir la apertura" lo dejaba sin correr justo en el caso más común.
- **5 · Guard del recálculo.** `AccountingEngine.chequearRecalculo` bloquea regenerar una apertura
  cuando no quedan meses anteriores, o cuando el recálculo da cero y la escrita tiene saldo. Sin él,
  el botón de Ajustes sobre el mes más viejo que sobrevive a una purga escribía ceros y se llevaba
  puesto todo el patrimonio arrastrado. Espejado en `escribirAperturaDeMes` del script.
- **6 · Duplicar pasa por la cola.** `duplicateMovement` ya no escribe sincrónico: fila optimista,
  reintento y notificación, como cualquier alta.
