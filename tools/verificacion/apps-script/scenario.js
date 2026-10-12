// scenario.js -- deterministic seeds + step sequence. Pure data (JSON-able); harness.js runs it.
// Cells: {$date: iso} = a real Date in the cell (what Sheets does when it auto-converts a date).
// Args:  {$undefined: true} = undefined argument.
'use strict';

const NOW = '2026-10-01T15:30:00-03:00';
const D = (iso) => ({ $date: iso });
const U = { $undefined: true };

const MOV_HEADERS = ['ID', 'Fecha', 'Monto', 'Tipo', 'Categoría', 'Responsable', 'Es Común', 'Descripción', 'Metodo Pago', 'Ticket URL', 'Eliminado', 'Propietario', 'Plan ID', 'Cuota N°', 'Evitable'];

/** Movement row A..O from an object (defaults mimic what the script writes). cols=14 => legacy sheet. */
function mv(o, cols = 15) {
  const row = [
    o.id, o.fecha, o.monto, o.tipo, o.cat ?? '', o.resp ?? 'Santiago', o.comun ?? false, o.desc ?? '',
    o.metodo ?? 'Billetera Virtual', o.ticket ?? '', o.elim ?? false, o.prop ?? (o.resp ?? 'Santiago'),
    o.planId ?? '', o.cuota ?? 0, o.evit ?? false,
  ];
  return row.slice(0, cols);
}
const ap = (mes, quien, deQuien, medio, monto) => {
  const resp = quien === 'r' ? 'Rocío' : 'Santiago';
  const prop = deQuien === 'r' ? 'Rocío' : 'Santiago';
  const metodo = medio === 'efec' ? 'Efectivo' : 'Billetera Virtual';
  return { id: `apertura-${mes}-${quien}-${deQuien}-${medio}`, fecha: `${mes}-01 00:00`, monto, tipo: 'Apertura', cat: 'Saldo inicial', resp, prop, metodo, desc: `Saldo inicial ${mes} - ${resp} - ${metodo}` + (resp !== prop ? ` (de ${prop})` : '') };
};

// --------------------------------------------------------------------------------------------- seeds
const agosto = [
  ap('2026-08', 's', 's', 'efec', 10000),
  ap('2026-08', 's', 's', 'virt', 50000),
  ap('2026-08', 'r', 'r', 'efec', 5000),
  ap('2026-08', 'r', 'r', 'virt', 30000),
  ap('2026-08', 'r', 's', 'virt', 2000),
  { id: 'legacy-si-s', fecha: '2026-08-01 00:00', monto: 1000, tipo: 'Aporte', cat: 'Saldo inicial', resp: 'Santiago', desc: 'Saldo inicial (legacy)' },
  { id: 'legacy-si-r', fecha: '2026-08-01 00:00', monto: 500, tipo: 'Aporte', cat: 'saldo inicial', resp: 'Rocío', desc: 'Saldo inicial (legacy, minúsculas)' },
  { id: 'ago-sueldo-s', fecha: '2026-08-01 09:00', monto: 200000, tipo: 'Aporte', cat: 'Sueldo', resp: 'Santiago', desc: 'Sueldo agosto' },
  { id: 'ago-sueldo-r', fecha: '2026-08-02 09:00', monto: 180000, tipo: 'Aporte', cat: ' Sueldo ', resp: 'Rocío', prop: '', desc: 'Sueldo (con espacios, propietario vacío)' },
  { id: 'ago-super', fecha: '2026-08-03 18:00', monto: 8000, tipo: 'Gasto', cat: 'Supermercado', resp: 'Santiago', comun: true, desc: 'Coto', metodo: 'Efectivo' },
  { id: 'ago-ropa-r', fecha: '2026-08-05 12:00', monto: 3000, tipo: 'Gasto', cat: 'Ropa', resp: 'Rocío', comun: 'TRUE', desc: 'Remera (esComun "TRUE")' },
  { id: 'ago-cuota-a1', fecha: D('2026-08-15T13:00:00-03:00'), monto: 10000, tipo: 'Gasto', cat: 'Tecnología', resp: 'Santiago', desc: 'Notebook 1/3 (fecha Date)', ticket: 'https://drive.google.com/old-ticket', planId: 'plan-A', cuota: 1 },
  { id: 12345, fecha: '2026-08-10 10:00', monto: 1000, tipo: 'Aporte', cat: 'Sueldo', resp: 'Santiago', desc: 'id numérico' },
  { id: 'ago-transf-s', fecha: '2026-08-20 10:00', monto: 1500, tipo: 'Transferencia', cat: 'Transferencia', resp: 'Santiago', prop: 'Rocío', desc: 'Pre-corte, salda deuda' },
  { id: 'ago-transf-r', fecha: '2026-08-21 10:00', monto: 500, tipo: 'Transferencia', cat: 'Transferencia', resp: 'Rocío', prop: 'Rocío', metodo: 'Efectivo', desc: 'Rocío transfiere lo suyo' },
  { id: 'ago-ambos', fecha: '2026-08-22 20:00', monto: 4000, tipo: 'Gasto', cat: 'Salidas', resp: 'Rocío', prop: 'Ambos', desc: 'Cena (propietario Ambos)' },
  { id: 'ago-borrado', fecha: '2026-08-23 10:00', monto: 9999, tipo: 'Gasto', cat: 'Hogar', resp: 'Santiago', elim: true, planId: 'plan-B', cuota: 1, desc: 'borrado con cuota' },
  null, // empty row in the middle
  { id: 'ago-otro', fecha: '2026-08-24 10:00', monto: 700, tipo: 'Gasto', cat: 'Varios', resp: 'Otro', desc: 'responsable desconocido' },
  { id: 'ago-sin-metodo', fecha: '2026-08-31 23:59', monto: 1200, tipo: 'Gasto', cat: 'Varios', resp: 'Santiago', metodo: '', desc: 'método vacío' },
  { id: 'ago-transf-r-s', fecha: '2026-08-31 23:59', monto: 300, tipo: 'Transferencia', cat: 'Transferencia', resp: 'Rocío', prop: 'Santiago', desc: 'Pre-corte, Rocío devuelve' },
];

const septiembre = [
  ap('2026-09', 's', 's', 'efec', 1),
  ap('2026-09', 'r', 's', 'virt', 999),
  { id: 'sep-sueldo-r', fecha: '2026-09-01 09:00', monto: 150000, tipo: 'Aporte', cat: 'Sueldo', resp: 'Rocío', desc: 'Sueldo septiembre' },
  { id: 'sep-corte', fecha: '2026-09-01 08:00', monto: 1000, tipo: 'Transferencia', cat: 'Transferencia', resp: 'Santiago', prop: 'Rocío', desc: 'Día del corte (regalo)' },
  { id: 'sep-transf-r-s', fecha: '2026-09-05 12:00', monto: 700, tipo: 'Transferencia', cat: 'Transferencia', resp: 'Rocío', prop: 'Santiago', desc: 'Post-corte' },
  { id: 'sep-transf-own', fecha: '2026-09-06 12:00', monto: 300, tipo: 'Transferencia', cat: 'Transferencia', resp: 'Santiago', prop: 'Santiago', metodo: 'Efectivo', desc: 'Presta lo suyo' },
  { id: 'sep-comun-str', fecha: '2026-09-07 19:00', monto: 12000, tipo: 'Gasto', cat: 'Supermercado', resp: 'Santiago', comun: 'true', evit: 'TRUE', desc: 'Super (esComun "true")' },
  { id: 'sep-condon-s', fecha: '2026-09-10 10:00', monto: 500, tipo: 'Condonación', cat: 'Deuda', resp: 'Santiago', desc: 'Perdona' },
  { id: 'sep-condon-r', fecha: '2026-09-10 11:00', monto: 200, tipo: 'Condonacion', cat: 'Deuda', resp: 'Rocío', desc: 'Perdona sin tilde' },
  { id: 'sep-devol-r', fecha: '2026-09-11 10:00', monto: 800, tipo: 'Devolución', cat: 'Deuda', resp: 'Rocío', metodo: 'Efectivo', desc: 'Devuelve' },
  { id: 'sep-devol-s', fecha: '2026-09-11 11:00', monto: 100, tipo: 'Devolucion', cat: 'Deuda', resp: 'Santiago', desc: 'Devuelve sin tilde' },
  { id: 'sep-cambio-s', fecha: '2026-09-12 10:00', monto: 2000, tipo: 'Cambio', cat: 'Cambio', resp: 'Santiago', metodo: 'Efectivo', desc: 'Cambio' },
  { id: 'sep-cambio-r', fecha: '2026-09-12 11:00', monto: 1000, tipo: 'Cambio', cat: 'Cambio', resp: 'Rocío', desc: 'Cambio virtual' },
  { id: 'sep-cprop-s', fecha: '2026-09-13 10:00', monto: 3000, tipo: 'Cambio propio', cat: 'Cambio', resp: 'Santiago', metodo: 'Efectivo', desc: 'Cajero' },
  { id: 'sep-cprop-r', fecha: '2026-09-13 11:00', monto: 500, tipo: 'cambio propio', cat: 'Cambio', resp: 'Rocío', desc: 'Familia' },
  { id: 'sep-cuota-a2', fecha: '2026-09-15 10:00', monto: 10000, tipo: 'Gasto', cat: 'Tecnología', resp: 'Santiago', planId: 'plan-A', cuota: 2, desc: 'Notebook 2/3' },
  { id: 'sep-cuota-b2', fecha: '2026-09-15 11:00', monto: 5000, tipo: 'Gasto', cat: 'Hogar', resp: 'Rocío', planId: 'plan-B', cuota: 2, desc: 'Heladera 2/2' },
  { id: 'sep-cuota-c1', fecha: '2026-09-16 10:00', monto: 2000, tipo: 'Gasto', cat: 'Tecnología', resp: 'Santiago', planId: 'plan-C', cuota: 1, desc: 'Celular 1/2' },
  { id: 'sep-cuota-c5', fecha: '2026-09-16 11:00', monto: 2000, tipo: 'Gasto', cat: 'Tecnología', resp: 'Santiago', planId: 'plan-C', cuota: 5, desc: 'Celular cuota 5 (> cantidad)' },
  { id: 'sep-elim-v', fecha: '2026-09-17 10:00', monto: 111, tipo: 'Gasto', cat: 'Varios', elim: 'VERDADERO', desc: 'eliminado VERDADERO' },
  { id: 'sep-elim-t', fecha: '2026-09-17 11:00', monto: 222, tipo: 'Gasto', cat: 'Varios', elim: 'true', desc: 'eliminado "true"' },
  { id: 'sep-elim-T', fecha: '2026-09-17 12:00', monto: 333, tipo: 'Gasto', cat: 'Varios', elim: 'TRUE', desc: 'eliminado "TRUE"' },
  { id: 'sep-ticket', fecha: '2026-09-20 10:00', monto: 4500, tipo: 'Gasto', cat: 'Farmacia', resp: 'Rocío', ticket: 'https://drive.google.com/ticket-sep', desc: 'Con ticket' },
  { id: 'sep-move', fecha: '2026-09-21 10:00', monto: 2500, tipo: 'Gasto', cat: 'Transporte', resp: 'Santiago', desc: 'Se mueve a octubre' },
  { id: 'sep-ramen', fecha: D('2026-09-28T21:00:00-03:00'), monto: 15000, tipo: 'Gasto', cat: 'Salidas', resp: 'Rocío', evit: true, desc: 'Ramen barrio chino (Date)' },
  { id: 'sep-pizza', fecha: '2026-09-29 21:30', monto: 9000.6, tipo: 'Gasto', cat: 'Salidas', resp: 'Santiago', evit: 'true', desc: 'Pizza | Güerrín' },
  { id: 'sep-perro', fecha: '2026-09-30 10:00', monto: 6000, tipo: 'Gasto', cat: 'Mascotas', resp: 'Rocío', comun: true, evit: 'VERDADERO', desc: 'Veterinaria Firulais' },
  { id: 'sep-misfiled', fecha: '2026-10-01 08:00', monto: 900, tipo: 'Gasto', cat: 'Transporte', resp: 'Santiago', desc: 'Fecha de octubre en hoja de septiembre' },
];

const octubre = [
  { id: 'oct-cafe', fecha: '2026-10-01 08:30', monto: 3500, tipo: 'Gasto', cat: 'Café', resp: 'Santiago', evit: true, desc: 'Café Martínez' },
  { id: 'oct-aporte-r', fecha: '2026-10-01 09:00', monto: 5000, tipo: 'Aporte', cat: 'Ingreso', resp: 'Rocío', desc: 'Venta' },
  { id: 'oct-larga', fecha: '2026-10-01 07:00', monto: 4200, tipo: 'Gasto', cat: 'Regalos', resp: 'Rocío', desc: 'Regalo de cumpleaños para la abuela Marta con moño\nrojo | grande' },
  { id: 'oct-transf', fecha: '2026-10-01 10:00', monto: 1000, tipo: 'Transferencia', cat: 'Transferencia', resp: 'Rocío', prop: 'Santiago', desc: 'No va al mensaje' },
  { id: 'oct-futuro', fecha: '2026-10-15 10:00', monto: 20000, tipo: 'Gasto', cat: 'Servicios', resp: 'Santiago', desc: 'Luz (programado)' },
];

const movSheet = (name, list, cols = 15) => ({
  name, maxCols: cols, frozenRows: 1,
  rows: [MOV_HEADERS.slice(0, cols)].concat(list.map((o) => (o ? mv(o, cols) : new Array(cols).fill('')))),
});

const PLAN_HEADERS_10 = ['ID', 'Fecha Creación', 'Descripción', 'Monto Por Cuota', 'Cantidad Cuotas', 'Primera Cuota', 'Propietario', 'Categoría', 'Tarjeta', 'Eliminado'];
const planesLegacy = {
  name: 'Planes', maxCols: 10, frozenRows: 1, colFormats: { B: '@', F: '@' },
  rows: [
    PLAN_HEADERS_10,
    ['plan-A', '2026-08-15 12:00', 'Notebook', 10000, 3, '2026-08', 'Santiago', 'Tecnología', 'Visa', false],
    ['plan-B', '2026-07-10 09:00', 'Heladera', 5000, 2, '2026-07', 'Rocío', 'Hogar', 'Master', false],
    ['plan-C', '2026-09-01 10:00', 'Celular', 2000, 2, '2026-09', 'Santiago', 'Tecnología', '', false],
    ['plan-D', '2026-06-01 10:00', 'Borrado', 1000, 1, '2026-06', 'Santiago', 'Varios', '', true],
    ['plan-E', D('2026-09-20T18:45:00-03:00'), 'Sillón (fechas Date)', 7000, 6, D('2026-10-01T00:00:00-03:00'), 'Rocío', 'Hogar', 'Visa', 'FALSE'],
    ['plan-F', '2026-09-25 08:00', 'Cantidad cero', 100, 0, '2026-10', 'Santiago', 'Varios', '', false],
    ['', '2026-09-26 08:00', 'Fila sin id', 1, 1, '2026-10', 'Santiago', '', '', false],
    ['plan-G', '2026-09-27 08:00', 'Eliminado VERDADERO', 1, 1, '2026-10', 'Rocío', '', '', 'VERDADERO'],
  ],
};

const usuarios = {
  name: 'Usuarios', maxCols: 6, frozenRows: 1, colFormats: { A: '@' },
  rows: [
    ['slotKey', 'Nombre', 'ColorId', 'Orden', 'Mensaje', 'Fecha mensaje'],
    ['Santiago', 'Santi', 'green', 0, '¡Buen finde!', D('2026-09-30T02:00:00-03:00')],
    ['', 'Fantasma', 'red', 2, '', ''],
    ['Rocío', '', 'blue', 1, '', ''],
  ],
};

const notas = { name: 'Notas', maxCols: 5, rows: [['Nota', 'Detalle'], ['Recordatorio', 'pagar luz']] };

// --------------------------------------------------------------------------------------------- events
const get = (params) => (params === undefined ? [] : [{ parameter: params, parameters: Object.fromEntries(Object.entries(params).map(([k, v]) => [k, [v]])), queryString: Object.entries(params).map(([k, v]) => `${k}=${v}`).join('&'), contextPath: '', contentLength: -1 }]);
const postRaw = (contents) => [{ postData: { contents, length: contents.length, type: 'application/json', name: 'postData' }, parameter: {}, parameters: {}, queryString: '', contextPath: '', contentLength: contents.length }];
const post = (obj) => postRaw(JSON.stringify(obj));

const mov = (o) => ({
  id: o.id, fecha: o.fecha, monto: o.monto ?? 1000, tipo: o.tipo ?? 'Gasto', categoria: o.categoria ?? 'Varios',
  responsable: o.responsable ?? 'Santiago', esComun: o.esComun ?? false, descripcion: o.descripcion ?? '', metodoPago: o.metodoPago ?? 'Billetera Virtual',
  ticketUrl: o.ticketUrl ?? '', eliminado: false, propietario: o.propietario ?? (o.responsable ?? 'Santiago'), planId: o.planId ?? '', cuotaNumero: o.cuotaNumero ?? 0, evitable: o.evitable ?? false,
});
// "ticket-jpeg" bytes, base64
const IMG = Buffer.from([0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10, 0x4a, 0x46, 0x49, 0x46, 0x7f, 0x80, 0x81, 0xfe]).toString('base64');

const plan = (o) => Object.assign({ fechaCreacion: '2026-10-01 15:30', descripcion: 'Plan', montoPorCuota: 1000, cantidadCuotas: 3, fechaPrimeraCuota: '2026-10', propietario: 'Santiago', categoria: 'Varios', tarjeta: 'Visa', eliminado: false, cuotasPagadasPrevias: [], evitable: false }, o);

const gem = (obj, extraParts = []) => ({ code: 200, body: { candidates: [{ content: { parts: extraParts.concat([{ text: typeof obj === 'string' ? obj : JSON.stringify(obj) }]) }, finishReason: 'STOP' }] } });

const s = (name, fn, args = [], setup) => ({ name, fn, args, setup });

// --------------------------------------------------------------------------------------------- phases
const phases = [];

phases.push({
  name: 'fresh',
  seed: {
    now: NOW,
    properties: { GEMINI_API_KEY: 'test-key' },
    sheets: [{ name: 'Hoja 1', rows: [] }],
  },
  steps: [
    s('doGet movimientos (planilla vacía)', 'doGet', get({})),
    s('GET_USERS sin hoja Usuarios', 'doGet', get({ action: 'GET_USERS' })),
    s('GET_PLANS sin hoja Planes', 'doGet', get({ action: 'GET_PLANS' })),
    s('plan DELETE sin hoja Planes', 'doPost', post({ entity: 'plan', action: 'DELETE', id: 'plan-X' })),
    s('SNAPSHOT_CUOTAS sin hoja Planes', 'doPost', post({ action: 'SNAPSHOT_CUOTAS', mes: '2026-10' })),
    s('previsualizarAperturas sin movimientos', 'previsualizarAperturas'),
    s('actualizarAperturas sin movimientos', 'actualizarAperturas'),
    s('migrarAperturas sin movimientos', 'migrarAperturas'),
    s('user PUT sin slotKey (no siembra)', 'doPost', post({ entity: 'user', action: 'PUT', user: { nombre: 'X' } })),
    s('user PUT slot desconocido (siembra Usuarios)', 'doPost', post({ entity: 'user', action: 'PUT', user: { slotKey: 'Nadie', nombre: 'N' } })),
    s('user PUT Rocío', 'doPost', post({ entity: 'user', action: 'PUT', user: { slotKey: 'Rocío', nombre: 'Ro', colorId: 'pink' } })),
    s('GET_USERS tras sembrar', 'doGet', get({ action: 'GET_USERS' })),
    s('plan POST crea hoja Planes', 'doPost', post({ entity: 'plan', action: 'POST', plan: plan({ id: 'plan-new', cuotasPagadasPrevias: [2, 1], evitable: true }) })),
    s('GET_PLANS tras crear Planes', 'doGet', get({ action: 'GET_PLANS' })),
    s('movement POST crea hoja Octubre', 'doPost', post({ action: 'POST', body: mov({ id: 'f-oct', fecha: '2026-10-01 10:00', descripcion: 'Primer gasto', planId: 'plan-new', cuotaNumero: 3 }) })),
    s('movement PUT crea hoja Septiembre', 'doPost', post({ action: 'PUT', body: mov({ id: 'f-sep', fecha: '2026-09-30 10:00', tipo: 'Aporte', categoria: 'Sueldo', monto: 50000, responsable: 'Rocío' }) })),
    s('generarMensajesDelDia (planilla chica)', 'generarMensajesDelDia'),
    s('actualizarAperturas con dos meses', 'actualizarAperturas'),
    s('doGet movimientos final', 'doGet', get({})),
    s('GET_PLANS final', 'doGet', get({ action: 'GET_PLANS', filtro: 'pendientes' })),
  ],
});

phases.push({
  name: 'main',
  seed: {
    now: NOW,
    properties: { GEMINI_API_KEY: 'test-key' },
    failingFolders: ['BAD_FOLDER'],
    sharingFailFolders: ['NO_SHARE_FOLDER'],
    triggers: [{ handler: 'actualizarAperturas', type: 'timeBased', atHour: 4, everyDays: 1 }, { handler: 'generarMensajesDelDia', type: 'timeBased', atHour: 2, everyDays: 1 }, { handler: 'otraFuncion', type: 'timeBased', everyHours: 6 }],
    sheets: [movSheet('Agosto 2026', agosto, 14), movSheet('Septiembre 2026', septiembre), planesLegacy, movSheet('Octubre 2026', octubre), usuarios, notas],
  },
  steps: [
    // ---- GET
    s('doGet sin argumento', 'doGet', [U]),
    s('doGet sin action', 'doGet', get({})),
    s('doGet action=GET', 'doGet', get({ action: 'GET' })),
    s('GET_PLANS sin filtro', 'doGet', get({ action: 'GET_PLANS' })),
    s('GET_PLANS pendientes', 'doGet', get({ action: 'GET_PLANS', filtro: 'pendientes' })),
    s('GET_PLANS pagos', 'doGet', get({ action: 'GET_PLANS', filtro: 'pagos' })),
    s('GET_PLANS filtro desconocido', 'doGet', get({ action: 'GET_PLANS', filtro: 'xyz' })),
    s('GET_USERS', 'doGet', get({ action: 'GET_USERS' })),
    s('doGet action desconocida', 'doGet', get({ action: 'UNKNOWN_ACTION' })),
    // ---- POST: errors / malformed
    s('doPost con candado ocupado', 'doPost', post({ action: 'POST', body: mov({ id: 'busy-1', fecha: '2026-10-01 10:00' }) }), { lockBusy: true }),
    s('doPost sin evento', 'doPost', [U]),
    s('doPost sin postData', 'doPost', [{ parameter: {} }]),
    s('doPost JSON inválido', 'doPost', postRaw('{"action": "POST", body: nope')),
    s('doPost body vacío (string vacío)', 'doPost', postRaw('')),
    s('doPost POST sin body', 'doPost', post({ action: 'POST' })),
    s('doPost action desconocida con body', 'doPost', post({ action: 'FOO', body: mov({ id: 'foo-1', fecha: '2026-10-01 10:00' }) })),
    s('doPost DELETE sin id', 'doPost', post({ action: 'DELETE' })),
    s('doPost sin action con body', 'doPost', post({ body: mov({ id: 'noaction-1', fecha: '2026-10-01 10:00' }) })),
    // ---- POST/PUT movements
    s('movement POST nuevo (Octubre)', 'doPost', post({ action: 'POST', body: mov({ id: 'oct-nuevo', fecha: '2026-10-01 11:00', descripcion: 'Almuerzo', evitable: true }) })),
    s('movement POST id repetido (duplica)', 'doPost', post({ action: 'POST', body: mov({ id: 'oct-nuevo', fecha: '2026-10-01 11:00', descripcion: 'Almuerzo (reintento)' }) })),
    s('movement PUT id nuevo', 'doPost', post({ action: 'PUT', body: mov({ id: 'put-nuevo', fecha: '2026-10-01 12:00', tipo: 'Aporte', categoria: 'Ingreso', responsable: 'Rocío', monto: 777 }) })),
    s('movement PUT existente mismo mes (conserva ticket)', 'doPost', post({ action: 'PUT', body: mov({ id: 'sep-ticket', fecha: '2026-09-20 10:00', monto: 4600, categoria: 'Farmacia', responsable: 'Rocío', descripcion: 'Con ticket (editado)', evitable: 'true' }) })),
    s('movement PUT id numérico en hoja legacy de 14 columnas', 'doPost', post({ action: 'PUT', body: mov({ id: '12345', fecha: '2026-08-10 10:00', tipo: 'Aporte', categoria: 'Sueldo', monto: 1100, evitable: true }) })),
    s('movement PUT cambia de mes (Sep -> Oct)', 'doPost', post({ action: 'PUT', body: mov({ id: 'sep-move', fecha: '2026-10-03 10:00', monto: 2500, categoria: 'Transporte', descripcion: 'Movido a octubre' }) })),
    s('movement PUT cambia de mes (Oct -> Ago, conserva ticket viejo vacío)', 'doPost', post({ action: 'PUT', body: mov({ id: 'oct-cafe', fecha: '2026-08-30 08:30', monto: 3500, categoria: 'Café', descripcion: 'Café Martínez (a agosto)', evitable: true }) })),
    s('movement PUT con imagen + folderId', 'doPost', post({ action: 'PUT', folderId: 'FOLDER_TEST', imageInfo: { base64: IMG, contentType: 'image/png' }, body: mov({ id: 'img-1', fecha: '2026-10-01 13:00', descripcion: 'Con imagen' }) })),
    s('movement PUT existente con imagen nueva (pisa ticket)', 'doPost', post({ action: 'PUT', imageInfo: { base64: IMG }, body: mov({ id: 'sep-ticket', fecha: '2026-09-20 10:00', monto: 4600, responsable: 'Rocío', descripcion: 'Ticket nuevo' }) })),
    s('movement POST imagen con carpeta inexistente', 'doPost', post({ action: 'POST', folderId: 'BAD_FOLDER', imageInfo: { base64: IMG, contentType: 'image/jpeg' }, body: mov({ id: 'img-bad', fecha: '2026-10-01 14:00' }) })),
    s('movement POST imagen con setSharing fallido', 'doPost', post({ action: 'POST', folderId: 'NO_SHARE_FOLDER', imageInfo: { base64: IMG }, body: mov({ id: 'img-noshare', fecha: '2026-10-01 14:10' }) })),
    s('movement POST imagen base64 inválido', 'doPost', post({ action: 'POST', imageInfo: { base64: '@@no-es-base64@@' }, body: mov({ id: 'img-b64', fecha: '2026-10-01 14:20' }) })),
    s('movement POST imageInfo sin base64 (ignorada)', 'doPost', post({ action: 'POST', imageInfo: { contentType: 'image/jpeg' }, body: mov({ id: 'img-none', fecha: '2026-10-01 14:30' }) })),
    s('movement POST mes nuevo (crea Noviembre 2026)', 'doPost', post({ action: 'POST', body: mov({ id: 'nov-1', fecha: '2026-11-05 10:00', descripcion: 'Futuro' }) })),
    s('movement POST fecha no parseable (hoja Movimientos)', 'doPost', post({ action: 'POST', body: mov({ id: 'bad-date', fecha: 'sin-fecha' }) })),
    s('movement POST body mínimo', 'doPost', post({ action: 'POST', body: { id: 'min-1' } })),
    s('movement POST mes 13', 'doPost', post({ action: 'POST', body: mov({ id: 'mes-13', fecha: '2026-13-01 10:00' }) })),
    s('movement PUT a hoja Movimientos existente', 'doPost', post({ action: 'PUT', body: mov({ id: 'bad-date', fecha: '', descripcion: 'sigue sin fecha' }) })),
    // ---- DELETE movements
    s('movement DELETE existente', 'doPost', post({ action: 'DELETE', id: 'ago-super' })),
    s('movement DELETE inexistente', 'doPost', post({ action: 'DELETE', id: 'no-existe' })),
    s('movement DELETE id que sólo está en Planes', 'doPost', post({ action: 'DELETE', id: 'plan-A' })),
    s('movement DELETE id duplicado', 'doPost', post({ action: 'DELETE', id: 'oct-nuevo' })),
    s('movement DELETE id numérico', 'doPost', post({ action: 'DELETE', id: 12345 })),
    s('movement PUT sobre fila con baja lógica (la revive)', 'doPost', post({ action: 'PUT', body: mov({ id: 'ago-super', fecha: '2026-08-03 18:00', monto: 8000, categoria: 'Supermercado', esComun: true, metodoPago: 'Efectivo', descripcion: 'Coto (re-subido)' }) })),
    // ---- plans
    s('plan POST sin plan', 'doPost', post({ entity: 'plan', action: 'POST' })),
    s('plan PUT sin id', 'doPost', post({ entity: 'plan', action: 'PUT', plan: plan({}) })),
    s('plan POST nuevo (hoja Planes legacy de 10 columnas)', 'doPost', post({ entity: 'plan', action: 'POST', plan: plan({ id: 'plan-H', descripcion: 'Bici', cuotasPagadasPrevias: '2,1, x,0', evitable: true }) })),
    s('plan PUT plan-B con previas [1] (unión)', 'doPost', post({ entity: 'plan', action: 'PUT', plan: plan({ id: 'plan-B', fechaCreacion: '2026-07-10 09:00', descripcion: 'Heladera', montoPorCuota: 5000, cantidadCuotas: 2, fechaPrimeraCuota: '2026-07', propietario: 'Rocío', categoria: 'Hogar', tarjeta: 'Master', cuotasPagadasPrevias: [1] }) })),
    s('plan PUT plan-H previas [3] (unión)', 'doPost', post({ entity: 'plan', action: 'PUT', plan: plan({ id: 'plan-H', descripcion: 'Bici', cuotasPagadasPrevias: [3] }) })),
    s('plan PUT plan-H previas [] (no achica)', 'doPost', post({ entity: 'plan', action: 'PUT', plan: plan({ id: 'plan-H', descripcion: 'Bici (editada)', tarjeta: '', cuotasPagadasPrevias: [] }) })),
    s('plan POST con id existente (duplica)', 'doPost', post({ entity: 'plan', action: 'POST', plan: plan({ id: 'plan-A', descripcion: 'Notebook (dup)' }) })),
    s('plan PUT inexistente (cae a alta)', 'doPost', post({ entity: 'plan', action: 'PUT', plan: plan({ id: 'plan-Z', cuotasPagadasPrevias: null }) })),
    s('plan action desconocida (cae a alta)', 'doPost', post({ entity: 'plan', action: 'FOO', plan: plan({ id: 'plan-FOO' }) })),
    s('plan DELETE existente', 'doPost', post({ entity: 'plan', action: 'DELETE', id: 'plan-C' })),
    s('plan DELETE inexistente', 'doPost', post({ entity: 'plan', action: 'DELETE', id: 'plan-nope' })),
    s('plan DELETE sin id', 'doPost', post({ entity: 'plan', action: 'DELETE' })),
    s('GET_PLANS pendientes tras ediciones', 'doGet', get({ action: 'GET_PLANS', filtro: 'pendientes' })),
    s('GET_PLANS pagos tras ediciones', 'doGet', get({ action: 'GET_PLANS', filtro: 'pagos' })),
    // ---- users
    s('user PUT sin user', 'doPost', post({ entity: 'user', action: 'PUT' })),
    s('user PUT Santiago nombre+color', 'doPost', post({ entity: 'user', action: 'PUT', user: { slotKey: 'Santiago', nombre: 'Santi G.', colorId: 'purple' } })),
    s('user PUT Rocío sólo color (nombre null)', 'doPost', post({ entity: 'user', action: 'PUT', user: { slotKey: 'Rocío', nombre: null, colorId: 'teal' } })),
    s('user PUT slot desconocido', 'doPost', post({ entity: 'user', action: 'PUT', user: { slotKey: 'Fantasma', nombre: 'Boo' } })),
    s('user con action POST (igual edita)', 'doPost', post({ entity: 'user', action: 'POST', user: { slotKey: 'Rocío', nombre: 'Ro' } })),
    s('GET_USERS tras ediciones', 'doGet', get({ action: 'GET_USERS' })),
    // ---- snapshot cuotas
    s('SNAPSHOT_CUOTAS sin mes', 'doPost', post({ action: 'SNAPSHOT_CUOTAS' })),
    s('SNAPSHOT_CUOTAS 2026-09', 'doPost', post({ action: 'SNAPSHOT_CUOTAS', mes: '2026-09' })),
    s('SNAPSHOT_CUOTAS 2026-10', 'doPost', post({ action: 'SNAPSHOT_CUOTAS', mes: '2026-10' })),
    s('SNAPSHOT_CUOTAS 2026-10 otra vez (idempotente)', 'doPost', post({ action: 'SNAPSHOT_CUOTAS', mes: '2026-10' })),
    s('GET_PLANS pendientes tras snapshot', 'doGet', get({ action: 'GET_PLANS' })),
    s('GET_PLANS pagos tras snapshot', 'doGet', get({ action: 'GET_PLANS', filtro: 'pagos' })),
    s('doGet movimientos tras escrituras', 'doGet', get({})),
    // ---- editor functions
    s('triggerAuthorization', 'triggerAuthorization'),
    s('previsualizarAperturas', 'previsualizarAperturas'),
    s('escribirApertura 2026-09', 'escribirApertura', ['2026-09']),
    s('escribirApertura 2026-08 (recalculo cero, no pisa)', 'escribirApertura', ['2026-08']),
    s('escribirApertura 2026-12 (crea hoja)', 'escribirApertura', ['2026-12']),
    s('escribirApertura basura', 'escribirApertura', ['basura']),
    s('escribirApertura sin argumento', 'escribirApertura', []),
    s('migrarAperturas', 'migrarAperturas'),
    s('migrarAperturas otra vez (idempotente)', 'migrarAperturas'),
    s('actualizarAperturas', 'actualizarAperturas'),
    s('actualizarAperturas con reloj en 2027-01-05 (mes actual sin hoja)', 'actualizarAperturas', [], { now: '2027-01-05T04:00:00-03:00' }),
    s('previsualizarAperturas tras migrar', 'previsualizarAperturas'),
    // ---- triggers
    s('desinstalarTriggerDeAperturas', 'desinstalarTriggerDeAperturas'),
    s('instalarTriggerDeAperturas', 'instalarTriggerDeAperturas'),
    s('instalarTriggerDeAperturas otra vez', 'instalarTriggerDeAperturas'),
    s('desinstalarTriggerDeMensajes', 'desinstalarTriggerDeMensajes'),
    s('instalarTriggerDeMensajes', 'instalarTriggerDeMensajes'),
    s('instalarTriggerDeMensajes otra vez', 'instalarTriggerDeMensajes'),
    // ---- mensaje del día
    s('generarMensajesDelDia (respuesta normal)', 'generarMensajesDelDia'),
    s('generarMensajesDelDia (400 thinking -> reintenta sin thinking)', 'generarMensajesDelDia', [], { fetchQueue: [{ code: 400, body: { error: { message: 'Unknown name "thinkingConfig"' } } }, gem({ Santiago: 'Arrancá tranqui', 'Rocío': '¿Cómo andan los perritos?' })] }),
    s('generarMensajesDelDia (429 -> siguiente modelo)', 'generarMensajesDelDia', [], { fetchQueue: [{ code: 429, body: { error: { message: 'quota' } } }, gem({ Santiago: '"Semana tranqui de gustos, bien ahí"', 'Rocío': '  ¿Valió la pena ese ramen?\n' })] }),
    s('generarMensajesDelDia (vacía, sin útiles, larga + falta uno)', 'generarMensajesDelDia', [], { fetchQueue: [{ code: 200, body: { candidates: [{ content: { parts: [{ text: 'solo pensé', thought: true }] }, finishReason: 'MAX_TOKENS' }] } }, gem({ otro: 'x', Santiago: 42 }), gem('Claro! {"Santiago": "Esta es una frase larguísima que se pasa del tope de noventa caracteres para ver cómo la recorta el script sin romper palabras"} listo')] }),
    s('generarMensajesDelDia (fallan todos)', 'generarMensajesDelDia', [], { fetchQueue: [{ code: 500, body: 'Internal' }, { throw: 'Exception: Address unavailable: generativelanguage.googleapis.com' }, { code: 200, body: 'no es json' }] }),
    s('generarMensajesDelDia (candidates vacío)', 'generarMensajesDelDia', [], { defaultFetch: { code: 200, body: { candidates: [] } } }),
    s('generarMensajesDelDia (GEMINI_MODELO custom)', 'generarMensajesDelDia', [], { setProperties: { GEMINI_MODELO: ' gemini-2.5-flash , ,gemini-x ' } }),
    s('generarMensajesDelDia (MENSAJES_ACTIVOS=FALSE)', 'generarMensajesDelDia', [], { setProperties: { MENSAJES_ACTIVOS: 'FALSE' } }),
    s('generarMensajesDelDia (sin GEMINI_API_KEY)', 'generarMensajesDelDia', [], { deleteProperties: ['GEMINI_API_KEY', 'MENSAJES_ACTIVOS', 'GEMINI_MODELO'] }),
    s('probarMensajesDelDia', 'probarMensajesDelDia', [], { setProperties: { GEMINI_API_KEY: 'test-key' } }),
    s('probarMensajesDelDia (fallan todos -> tira)', 'probarMensajesDelDia', [], { defaultFetch: { code: 503, body: 'unavailable' } }),
    s('generarMensajesDelDia con reloj 2027-01-05 (mes anterior diciembre)', 'generarMensajesDelDia', [], { now: '2027-01-05T02:00:00-03:00' }),
    s('GET_USERS con mensajes', 'doGet', get({ action: 'GET_USERS' })),
    // ---- destructive, near the end
    s('renombrarSueldoAIngreso', 'renombrarSueldoAIngreso'),
    s('renombrarSueldoAIngreso otra vez', 'renombrarSueldoAIngreso'),
    s('borrarLegacySaldoInicial', 'borrarLegacySaldoInicial'),
    s('borrarLegacySaldoInicial otra vez', 'borrarLegacySaldoInicial'),
    s('doGet movimientos final', 'doGet', get({})),
    s('GET_PLANS final', 'doGet', get({ action: 'GET_PLANS' })),
    s('función inexistente (control del harness)', 'funcionQueNoExiste'),
  ],
});

phases.push({
  name: 'usuarios-legacy-vacia',
  seed: {
    now: NOW,
    properties: { GEMINI_API_KEY: 'test-key' },
    sheets: [movSheet('Octubre 2026', octubre.slice(0, 2)), { name: 'Usuarios', maxCols: 4, rows: [['slotKey', 'Nombre', 'ColorId', 'Orden']] }],
  },
  steps: [
    s('GET_USERS hoja sólo con encabezado', 'doGet', get({ action: 'GET_USERS' })),
    s('generarMensajesDelDia Usuarios vacía (agrega columnas E/F)', 'generarMensajesDelDia'),
    s('user PUT con Usuarios vacía', 'doPost', post({ entity: 'user', action: 'PUT', user: { slotKey: 'Santiago', nombre: 'S' } })),
  ],
});

// Pagar una cuota atrasada la fecha en el mes de la cuota (ya cerrado): las aperturas de los meses
// siguientes se tienen que recalcular en el mismo doPost, no recién con el trigger de la madrugada.
const CUOTA_NOW = '2026-10-08T04:57:00-03:00';
const cuotaAtrasada = mov({ id: 'cuota-plan-R-3', fecha: '2026-09-01 04:57', monto: 83332.33, categoria: 'Otros', responsable: 'Rocío', descripcion: 'Cuota 3/3 — ropa', planId: 'plan-R', cuotaNumero: 3 });
phases.push({
  name: 'aperturas-tras-mes-cerrado',
  seed: {
    now: CUOTA_NOW,
    sheets: [
      movSheet('Septiembre 2026', [
        ap('2026-09', 's', 's', 'virt', 100000),
        ap('2026-09', 'r', 'r', 'virt', 200000),
        { id: 'sep-gasto-r', fecha: '2026-09-10 10:00', monto: 5000, tipo: 'Gasto', cat: 'Super', resp: 'Rocío' },
      ]),
      movSheet('Octubre 2026', [
        ap('2026-10', 's', 's', 'virt', 1), // vieja a propósito: la normaliza el primer paso
        { id: 'oct-gasto-s', fecha: '2026-10-02 10:00', monto: 1000, tipo: 'Gasto', cat: 'Café', resp: 'Santiago' },
      ]),
    ],
  },
  steps: [
    s('actualizarAperturas (línea de base)', 'actualizarAperturas'),
    s('PUT cuota atrasada fechada en septiembre (recalcula octubre)', 'doPost', post({ action: 'PUT', body: cuotaAtrasada })),
    s('PUT misma cuota otra vez (idempotente)', 'doPost', post({ action: 'PUT', body: cuotaAtrasada })),
    s('PUT gasto del mes en curso (no recalcula)', 'doPost', post({ action: 'PUT', body: mov({ id: 'oct-nuevo-r', fecha: '2026-10-08 05:00', responsable: 'Rocío' }) })),
    s('PUT fila de apertura de septiembre (no recalcula)', 'doPost', post({ action: 'PUT', body: mov({ id: 'apertura-2026-09-s-s-efec', fecha: '2026-09-01 00:00', tipo: 'Apertura', categoria: 'Saldo inicial', monto: 700, metodoPago: 'Efectivo' }) })),
    s('PUT mueve gasto de octubre a septiembre (recalcula)', 'doPost', post({ action: 'PUT', body: mov({ id: 'oct-gasto-s', fecha: '2026-09-20 10:00', categoria: 'Café' }) })),
    s('DELETE cuota atrasada (recalcula)', 'doPost', post({ action: 'DELETE', id: 'cuota-plan-R-3' })),
    s('doGet movimientos final', 'doGet', get({})),
  ],
});

module.exports = { NOW, phases };
