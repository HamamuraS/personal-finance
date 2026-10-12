/**
 * Script de Google Apps Script para la sincronización de la App de Finanzas Personales.
 *
 * Versión: 8.3 (Aperturas al día al cargar en un mes cerrado; el protocolo con la app no cambia)
 * @description Este script requiere acceso a Google Drive para guardar los tickets.
 *
 * Novedades v8.3:
 *  - Escribir, mover o dar de baja un movimiento con fecha de un mes ya cerrado recalcula en el
 *    momento las aperturas de los meses siguientes. Antes el saldo del mes en curso no lo reflejaba
 *    hasta el trigger diario de la madrugada siguiente (ej.: pagar una cuota atrasada, que se fecha
 *    en el mes de la cuota, tardaba hasta un día en descontarse).
 *
 * Novedades v8.2:
 *  - Las filas de apertura se escriben SIEMPRE completas (6 por mes), también las de propiedad
 *    cruzada en cero. Antes, si la deuda se saldaba o cambiaba de lado, la fila cruzada vieja
 *    quedaba viva en la hoja y la apertura sumaba plata y deuda que ya no existían.
 *  - "Cuota pagada" cuenta solo números dentro del plan (1..cantidad): un movimiento con una cuota
 *    de más ya no da por completo un plan que no lo está.
 *  - Toda escritura es un upsert por id, también el POST (versiones viejas de la app): reintentar
 *    ya no duplica filas. Mover un movimiento de mes escribe la fila nueva antes de borrar la vieja.
 *  - Los triggers y las funciones del editor que escriben toman el mismo candado que doPost.
 *  - doGet/doPost siempre responden JSON (antes, una acción no reconocida no devolvía nada).
 *  - Un PUT que actualiza una fila existente informa también cómo le fue a la imagen.
 *  - "Eliminado" y "Es Común" aceptan los mismos valores verdaderos que el resto (incluye "TRUE").
 *  - Interno: un solo criterio para qué hojas son de movimientos, columnas con nombre y helpers
 *    compartidos para encabezados y triggers.
 *
 * Novedades v8.0:
 *  - Tipo "Cambio propio" en `aplicarMovimientos`: canje de efectivo por transferencia con alguien de
 *    afuera (familia, cajero). Solo cruza los buckets de quien lo carga.
 *  - Mensaje del día: cada movimiento va con "hace N días" y lo de los últimos días se manda aparte;
 *    solo sobre eso puede preguntar o comentar algo puntual.
 *
 * Novedades v7.8:
 *  - Mensaje del día con Gemini: generarMensajesDelDia() (trigger ~2 AM) escribe un mensaje por
 *    persona en la hoja "Usuarios" (E/F) y GET_USERS lo devuelve. Requiere la propiedad del script
 *    GEMINI_API_KEY; ver el bloque "MENSAJE DEL DÍA" al final del archivo.
 *
 * Novedades v7.7:
 *  - Tipos nuevos de movimiento en `aplicarMovimientos` (port de `AccountingEngine.compute`):
 *    "Cambio" (canje efectivo <-> transferencia entre los dos, solo cruza buckets) y "Devolución"
 *    (pago de deuda: mueve plata y cancela propiedad cruzada).
 *  - Una "Transferencia" que cambia de dueño YA NO salda deuda desde CORTE_TRANSFERENCIA_SIN_DEVOLUCION;
 *    las anteriores conservan la regla vieja para no alterar aperturas ya calculadas.
 *  - Columna O "Evitable" en las hojas de movimientos y columna L "Evitable" en "Planes". Vacío =
 *    no evitable.
 *  - El PUT de movimientos busca el id en TODAS las hojas: si la edición cambió la fecha de mes,
 *    borra la fila vieja y escribe en la hoja del mes nuevo (antes quedaba duplicado).
 *  - renombrarSueldoAIngreso(): opcional, para correr a mano (la app ya lee "Sueldo" como "Ingreso").
 *
 * Novedades v7.2:
 *  - Filas de apertura por hoja (tipo "Apertura", categoría "Saldo inicial"): cada mes lleva su
 *    propio arrastre desagregado por persona × medio de pago × propietario, así que su saldo deja
 *    de depender de las hojas anteriores y las viejas se pueden archivar.
 *  - Funciones de migración para correr a mano desde el editor: previsualizarAperturas(),
 *    migrarAperturas(), escribirApertura(mes) y borrarLegacySaldoInicial(). Ver el bloque
 *    "SALDO INICIAL MATERIALIZADO" al final del archivo.
 *
 * Novedades v7.0:
 *  - Hoja nueva "Usuarios" (ver USERS_SHEET): nombre + color por usuario. Se EXCLUYE de los tres
 *    recorridos de movimientos (doGet, getPlans, handleLogicalDelete) igual que "Planes".
 *  - doGet?action=GET_USERS: devuelve las filas de "Usuarios" (o [] si la hoja no existe; el
 *    cliente cae a su config DEFAULT).
 *  - doPost con entity:"user" y action:"PUT": edita nombre/colorId de un usuario buscándolo por
 *    slotKey (col A, inmutable). Siembra la hoja con los dos slots legacy si falta. No hay POST/DELETE.
 *
 * Novedades v6.0:
 *  - Hoja nueva "Planes" para compras en cuotas (ver PLANS_SHEET). Se EXCLUYE del doGet de
 *    movimientos para que sus filas no se lean como movimientos.
 *  - Movimientos: 2 columnas nuevas M (planId) y N (cuotaNumero) que vinculan un gasto con la
 *    cuota que paga. El estado "cuota pagada" es DERIVADO (existe el movimiento), no se duplica.
 *  - doGet?action=GET_PLANS&filtro=pendientes|pagos: lista planes filtrando por si están
 *    completamente pagos (derivado escaneando los movimientos), ordenados por creación desc.
 *  - doPost con entity:"plan": alta (POST), edición (PUT) y baja lógica (DELETE) de planes.
 */

const monthNames = ["Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"];
const defaultFolderId = "1LT_t2a7WBFe6wGjwJ5XuTYsS7gvjr3jU";
const PLANS_SHEET = "Planes";
const USERS_SHEET = "Usuarios";

/** Huso de la app (Argentina no tiene horario de verano). */
const ZONA_ARGENTINA = "America/Argentina/Buenos_Aires";

/** Color de fondo de los encabezados de todas las hojas. */
const COLOR_ENCABEZADO = "#e2e8f0";

/** Las dos claves de slot de los usuarios (ver la hoja "Usuarios"). Son datos: no se renombran. */
const SANTIAGO = "Santiago";
const ROCIO = "Rocío";
const AMBOS = "Ambos";

/** Columnas (base 0) de una hoja de movimientos, A..O. */
const COL = {
  ID: 0, FECHA: 1, MONTO: 2, TIPO: 3, CATEGORIA: 4, RESPONSABLE: 5, ES_COMUN: 6, DESCRIPCION: 7,
  METODO: 8, TICKET: 9, ELIMINADO: 10, PROPIETARIO: 11, PLAN_ID: 12, CUOTA: 13, EVITABLE: 14
};

/** Columnas (base 0) de la hoja "Planes", A..L. */
const COL_PLAN = {
  ID: 0, CREACION: 1, DESCRIPCION: 2, MONTO: 3, CANTIDAD: 4, PRIMERA: 5, PROPIETARIO: 6,
  CATEGORIA: 7, TARJETA: 8, ELIMINADO: 9, PREVIAS: 10, EVITABLE: 11
};
const PLAN_COLS = 12;

/** ¿[sheet] guarda movimientos? Todas las hojas menos las de planes y usuarios. */
function esHojaDeMovimientos(sheet) {
  const nombre = sheet.getName();
  return nombre !== PLANS_SHEET && nombre !== USERS_SHEET;
}

/** Las hojas que guardan movimientos, en el orden de la planilla. */
function hojasDeMovimientos(ss) {
  return ss.getSheets().filter(esHojaDeMovimientos);
}

/** Valor de una celda como texto ("" si está vacía). */
function texto(v) {
  return v ? v.toString() : "";
}

/**
 * Corre [fn] con el candado del script (el mismo que toma doPost), para que un trigger o una función
 * del editor no escriba en medio de una escritura de la app: los dos trabajan con números de fila, y
 * una fila borrada por el otro corre todos los índices. Si el candado no se libera a tiempo, no
 * escribe nada y lo deja logueado.
 */
function conCandado(nombre, fn) {
  const lock = LockService.getScriptLock();
  try {
    lock.waitLock(LOCK_TIMEOUT_MS);
  } catch (lockErr) {
    Logger.log(nombre + ": el script está ocupado con otra escritura, no se hizo nada.");
    return null;
  }
  try {
    return fn();
  } finally {
    lock.releaseLock();
  }
}

/** Pone [nombre] como encabezado de la columna [col] (base 1) si está vacío, agregando columnas si faltan. */
function asegurarEncabezado(sheet, col, nombre) {
  if (sheet.getMaxColumns() < col) sheet.insertColumnsAfter(sheet.getMaxColumns(), col - sheet.getMaxColumns());
  const celda = sheet.getRange(1, col);
  if (!celda.getValue()) {
    celda.setValue(nombre).setFontWeight("bold").setBackground(COLOR_ENCABEZADO);
  }
}

/** Crea una hoja con sus encabezados en negrita y la primera fila fija. */
function crearHojaConEncabezados(ss, nombre, encabezados) {
  const sheet = ss.insertSheet(nombre);
  sheet.appendRow(encabezados);
  sheet.getRange(1, 1, 1, encabezados.length).setFontWeight("bold").setBackground(COLOR_ENCABEZADO);
  sheet.setFrozenRows(1);
  return sheet;
}

/** Instala un trigger diario de [handler] a la [hora] dada (reemplaza el que hubiera). */
function instalarTriggerDiario(handler, hora, zona) {
  desinstalarTriggers(handler);
  let builder = ScriptApp.newTrigger(handler).timeBased().atHour(hora).everyDays(1);
  if (zona) builder = builder.inTimezone(zona);
  builder.create();
}

/** Quita los triggers de [handler]. Devuelve cuántos quitó. */
function desinstalarTriggers(handler) {
  let n = 0;
  ScriptApp.getProjectTriggers().forEach(function (t) {
    if (t.getHandlerFunction() === handler) { ScriptApp.deleteTrigger(t); n++; }
  });
  return n;
}

/**
 * Función para forzar la solicitud de permisos de Drive.
 */
function triggerAuthorization() {
  const folder = DriveApp.getFolderById(defaultFolderId);
  Logger.log("Acceso a carpeta verificado: " + folder.getName());
}

function jsonOutput(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}

/** True si una celda booleana (Eliminado, Es Común, Evitable) representa verdadero. Vacío = false. */
function esVerdadero(v) {
  return v === true || v === 'true' || v === 'TRUE' || v === 'VERDADERO';
}

/** True si el valor de una celda "eliminado" representa verdadero. */
function isEliminado(v) {
  return esVerdadero(v);
}

/** Encabezados de una hoja de movimientos (A..O). */
const MOVEMENT_HEADERS = ["ID", "Fecha", "Monto", "Tipo", "Categoría", "Responsable", "Es Común", "Descripción", "Metodo Pago", "Ticket URL", "Eliminado", "Propietario", "Plan ID", "Cuota N°", "Evitable"];
const MOVEMENT_COLS = MOVEMENT_HEADERS.length;

/** Devuelve la hoja de movimientos [nombre], creándola con sus encabezados si no existe. */
function hojaDeMovimientos(ss, nombre) {
  const sheet = ss.getSheetByName(nombre) || crearHojaConEncabezados(ss, nombre, MOVEMENT_HEADERS);
  // Las hojas de mes anteriores a la v7.7 no tienen la columna O.
  asegurarEncabezado(sheet, MOVEMENT_COLS, "Evitable");
  return sheet;
}

/** Nombre de la hoja de mes donde va un movimiento con fecha "yyyy-MM-dd…" ("Movimientos" si no parsea). */
function hojaDeFecha(fecha) {
  const dateParts = String(fecha || "").split("-");
  if (dateParts.length >= 2) {
    const monthNum = parseInt(dateParts[1], 10) - 1;
    if (monthNum >= 0 && monthNum <= 11) return monthNames[monthNum] + " " + dateParts[0];
  }
  return "Movimientos";
}

/**
 * Busca un movimiento por id en todas las hojas de movimientos, empezando por [preferida] (la del mes
 * de la fecha, donde está casi siempre: todas las altas de la cola son PUT y no conviene leer todas
 * las hojas en cada una). null si no existe.
 */
function buscarMovimiento(ss, id, preferida) {
  const hojas = hojasDeMovimientos(ss);
  hojas.sort(function (a, b) { return (a.getName() === preferida.getName() ? 0 : 1) - (b.getName() === preferida.getName() ? 0 : 1); });
  for (let h = 0; h < hojas.length; h++) {
    const sheet = hojas[h];
    const ids = sheet.getRange(1, 1, Math.max(sheet.getLastRow(), 1), 1).getValues();
    for (let i = 1; i < ids.length; i++) {
      if (ids[i][0] == id) {
        const fila = sheet.getRange(i + 1, 1, 1, Math.max(sheet.getLastColumn(), 1)).getValues()[0];
        return { sheet: sheet, fila: i + 1, valores: fila };
      }
    }
  }
  return null;
}

/** Devuelve "yyyy-MM". Si Sheets guardó la celda como Date, la formatea; si no, la pasa a string. */
function toYearMonth(v) {
  if (v instanceof Date) {
    return Utilities.formatDate(v, Session.getScriptTimeZone(), "yyyy-MM");
  }
  return v ? v.toString() : "";
}

/** Devuelve "yyyy-MM-dd HH:mm" (sortable). Formatea si la celda es Date. */
function toDateTime(v) {
  if (v instanceof Date) {
    return Utilities.formatDate(v, Session.getScriptTimeZone(), "yyyy-MM-dd HH:mm");
  }
  return v ? v.toString() : "";
}

function doGet(e) {
  try {
    const action = (e && e.parameter && e.parameter.action) ? e.parameter.action : null;

    // --- Endpoint del módulo de cuotas ---
    if (action === 'GET_PLANS') {
      return getPlans(e);
    }

    // --- Endpoint de usuarios parametrizables ---
    if (action === 'GET_USERS') {
      return getUsers(e);
    }

    // --- Movimientos (action=GET, o cualquier otra cosa: comportamiento por defecto) ---
    return getMovements();
  } catch (err) {
    return jsonOutput({ status: "ERROR", message: err.toString() });
  }
}

/** Todos los movimientos vivos de todas las hojas de movimientos, tal como los espera la app. */
function getMovements() {
  let allData = [];
  hojasDeMovimientos(SpreadsheetApp.getActiveSpreadsheet()).forEach(function (sheet) {
    const data = sheet.getDataRange().getValues();
    const rows = data.slice(1)
      // Ni los eliminados ni las filas vacías (sin id ni fecha) son movimientos.
      .filter(function (row) { return !isEliminado(row[COL.ELIMINADO]) && (row[COL.ID] || row[COL.FECHA]); })
      .map(function (row) {
        return {
          id: texto(row[COL.ID]),
          // Tal cual la celda (si Sheets la guardó como Date, la app la normaliza).
          fecha: texto(row[COL.FECHA]),
          monto: Number(row[COL.MONTO]) || 0,
          tipo: texto(row[COL.TIPO]),
          categoria: texto(row[COL.CATEGORIA]),
          responsable: texto(row[COL.RESPONSABLE]),
          esComun: esVerdadero(row[COL.ES_COMUN]),
          descripcion: texto(row[COL.DESCRIPCION]),
          metodoPago: row[COL.METODO] ? row[COL.METODO].toString() : VIRTUAL,
          ticketUrl: texto(row[COL.TICKET]),
          eliminado: false,
          propietario: row[COL.PROPIETARIO] ? row[COL.PROPIETARIO].toString() : texto(row[COL.RESPONSABLE]),
          planId: texto(row[COL.PLAN_ID]),
          cuotaNumero: Number(row[COL.CUOTA]) || 0,
          evitable: esVerdadero(row[COL.EVITABLE])
        };
      });
    allData = allData.concat(rows);
  });
  return jsonOutput({ status: "SUCCESS", data: allData });
}

/**
 * Lista los planes de cuotas. Deriva "completamente pago" escaneando TODAS las hojas de
 * movimientos (fuente única de verdad): un plan está completo si tiene tantas cuotas distintas
 * pagadas como cuotas totales.
 *
 * @param filtro "pendientes" (default) = no completos | "pagos" = completos.
 * Ambos casos se devuelven ordenados por fechaCreacion descendente.
 */
function getPlans(e) {
  const filtro = (e && e.parameter && e.parameter.filtro) ? e.parameter.filtro : 'pendientes';
  const ss = SpreadsheetApp.getActiveSpreadsheet();

  // 1) paidMap: planId -> Set de cuotaNumero pagadas (derivado de los movimientos).
  const paidMap = {};
  hojasDeMovimientos(ss).forEach(function (sheet) {
    const data = sheet.getDataRange().getValues();
    for (let i = 1; i < data.length; i++) {
      const row = data[i];
      if (isEliminado(row[COL.ELIMINADO])) continue;
      const planId = texto(row[COL.PLAN_ID]);
      if (!planId) continue;
      const cuotaNum = Number(row[COL.CUOTA]) || 0;
      if (cuotaNum <= 0) continue;
      if (!paidMap[planId]) paidMap[planId] = {};
      paidMap[planId][cuotaNum] = true;
    }
  });

  // 2) Recorrer la hoja de planes, derivar "completo" y filtrar.
  const plansSheet = ss.getSheetByName(PLANS_SHEET);
  let plans = [];
  if (plansSheet) {
    const data = plansSheet.getDataRange().getValues();
    for (let i = 1; i < data.length; i++) {
      const row = data[i];
      if (isEliminado(row[COL_PLAN.ELIMINADO])) continue;
      const id = texto(row[COL_PLAN.ID]);
      if (!id) continue;

      const cantidadCuotas = Number(row[COL_PLAN.CANTIDAD]) || 0;
      // "Pagada" = tiene movimiento vivo O figura en el snapshot del corte (columna K). Sin la
      // unión, purgar hojas viejas devolvía a pendiente cuotas ya pagadas. Solo cuentan los números
      // del plan (1..cantidad), igual que en la app (`CuotasEngine.pagadasDe`).
      const previas = parsePrevias(row.length > COL_PLAN.PREVIAS ? row[COL_PLAN.PREVIAS] : "");
      const pagadasSet = {};
      const esDelPlan = function (k) { return k >= 1 && k <= cantidadCuotas; };
      if (paidMap[id]) Object.keys(paidMap[id]).map(Number).filter(esDelPlan).forEach(function (k) { pagadasSet[k] = true; });
      previas.filter(esDelPlan).forEach(function (k) { pagadasSet[k] = true; });
      const pagadas = Object.keys(pagadasSet).length;
      const completo = cantidadCuotas > 0 && pagadas >= cantidadCuotas;

      if (filtro === 'pagos' && !completo) continue;
      if (filtro !== 'pagos' && completo) continue; // "pendientes"

      plans.push({
        id: id,
        fechaCreacion: toDateTime(row[COL_PLAN.CREACION]),
        descripcion: texto(row[COL_PLAN.DESCRIPCION]),
        montoPorCuota: Number(row[COL_PLAN.MONTO]) || 0,
        cantidadCuotas: cantidadCuotas,
        fechaPrimeraCuota: toYearMonth(row[COL_PLAN.PRIMERA]),
        propietario: texto(row[COL_PLAN.PROPIETARIO]),
        categoria: texto(row[COL_PLAN.CATEGORIA]),
        tarjeta: texto(row[COL_PLAN.TARJETA]),
        eliminado: false,
        cuotasPagadasPrevias: previas,
        evitable: esVerdadero(row.length > COL_PLAN.EVITABLE ? row[COL_PLAN.EVITABLE] : "")
      });
    }
  }

  // 3) Ordenar por fechaCreacion descendente (ISO -> orden lexicográfico).
  plans.sort((a, b) => (a.fechaCreacion < b.fechaCreacion) ? 1 : ((a.fechaCreacion > b.fechaCreacion) ? -1 : 0));

  return jsonOutput({ status: "SUCCESS", plans: plans });
}

/** Milisegundos que una escritura espera el candado antes de rendirse. */
const LOCK_TIMEOUT_MS = 30000;

/**
 * Punto de entrada de TODAS las escrituras. Serializa con un candado de script porque el upsert es
 * leer-todas-las-filas -> buscar-el-id -> escribir, y eso no es atómico: dos requests simultáneos
 * con el mismo id leerían los dos "no existe" y appendearían los dos. Una fila duplicada rompía la
 * app entera hasta borrarla a mano (el listado de Inicio usa el id como key del LazyColumn).
 *
 * Hoy es difícil que pase —la cola de subida está serializada en el cliente y los ids son por
 * dispositivo— pero nada del lado del servidor lo impedía, y ahora hay dos teléfonos escribiendo.
 */
function doPost(e) {
  const lock = LockService.getScriptLock();
  try {
    lock.waitLock(LOCK_TIMEOUT_MS);
  } catch (lockErr) {
    return jsonOutput({
      status: "ERROR",
      message: "El servidor está ocupado con otra escritura, reintentá en unos segundos."
    });
  }
  try {
    return handlePost(e);
  } finally {
    lock.releaseLock();
  }
}

function handlePost(e) {
  let imgStatus = "No procesada";
  try {
    const contents = e.postData.contents;
    const json = JSON.parse(contents);
    const action = json.action;

    // --- Módulo de cuotas: escritura de planes ---
    if (json.entity === 'plan') {
      if (action === 'DELETE') {
        return handlePlanDelete(json.id);
      }
      return handlePlanUpsert(action, json.plan);
    }

    // --- Usuarios parametrizables: solo edición (PUT) de nombre/color por slotKey ---
    if (json.entity === 'user') {
      return handleUserUpsert(json.user);
    }

    // --- Corte de mes: congelar qué cuotas ya estaban pagadas antes de `mes` ---
    if (action === 'SNAPSHOT_CUOTAS') {
      return snapshotCuotasPrevias(json.mes);
    }

    // --- Movimientos ---
    const folderId = json.folderId || defaultFolderId;

    if (action === 'DELETE' && json.id) {
      return handleLogicalDelete(json.id);
    }

    // POST y PUT son lo mismo: un upsert por id. La app manda siempre PUT; el POST (append ciego)
    // solo llegaba de versiones viejas, y un reintento suyo duplicaba la fila.
    if ((action === 'POST' || action === 'PUT') && json.body) {
      const mov = json.body;

      // --- LÓGICA DE SUBIDA DE IMAGEN BASE64 ---
      if (json.imageInfo && json.imageInfo.base64) {
        try {
          const folder = DriveApp.getFolderById(folderId);
          const contentType = json.imageInfo.contentType || "image/jpeg";
          const fileName = "ticket_" + mov.id + ".jpg";

          const decoded = Utilities.base64Decode(json.imageInfo.base64);
          const blob = Utilities.newBlob(decoded, contentType, fileName);
          const file = folder.createFile(blob);

          mov.ticketUrl = file.getUrl();

          try {
            file.setSharing(DriveApp.Access.ANYONE_WITH_LINK, DriveApp.Permission.VIEW);
          } catch (sharingErr) {
            imgStatus = "Imagen guardada pero falló setSharing: " + sharingErr.toString();
          }

          if (imgStatus === "No procesada") {
            imgStatus = "Imagen guardada OK: " + mov.ticketUrl;
          }
        } catch (imgErr) {
          imgStatus = "ERROR GUARDANDO IMAGEN: " + imgErr.toString();
        }
      }

      const ss = SpreadsheetApp.getActiveSpreadsheet();
      const sheet = hojaDeMovimientos(ss, hojaDeFecha(mov.fecha));

      const filaDe = function (ticketUrlAnterior) {
        return [
          mov.id,
          mov.fecha,
          mov.monto,
          mov.tipo,
          mov.categoria,
          mov.responsable,
          mov.esComun,
          mov.descripcion,
          mov.metodoPago || VIRTUAL,
          mov.ticketUrl || ticketUrlAnterior || "",
          false, // Columna Eliminado
          mov.propietario || mov.responsable,
          mov.planId || "",          // Columna M
          mov.cuotaNumero || 0,      // Columna N
          mov.evitable === true      // Columna O
        ];
      };

      // Upsert por id. Se busca en TODAS las hojas y no solo en la del mes de la fecha: una
      // edición puede haber cambiado la fecha de mes, y buscando solo en la hoja nueva la fila vieja
      // quedaba viva y el movimiento contaba dos veces.
      const conImagen = function (mensaje) {
        return imgStatus === "No procesada" ? mensaje : mensaje + " | " + imgStatus;
      };
      // Un movimiento con fecha de un mes ya cerrado deja vieja la apertura de los meses siguientes;
      // se recalculan antes de responder (ver [recalcularAperturasPosteriores]). Las filas de
      // apertura no: las escribe el propio recálculo o el botón de Ajustes.
      const mesesTocados = esFilaApertura(mov.tipo) ? [] : [mesDeFecha(mov.fecha)];
      const conAperturas = function (mensaje) {
        const extra = recalcularAperturasPosteriores(mesesTocados);
        return extra ? mensaje + " | " + extra : mensaje;
      };

      const existente = buscarMovimiento(ss, mov.id, sheet);
      if (existente) {
        const ticketAnterior = existente.valores.length > COL.TICKET ? existente.valores[COL.TICKET] : "";
        if (!esFilaApertura(mov.tipo)) mesesTocados.push(mesDeFecha(existente.valores[COL.FECHA]));
        if (existente.sheet.getName() === sheet.getName()) {
          existente.sheet.getRange(existente.fila, 1, 1, MOVEMENT_COLS).setValues([filaDe(ticketAnterior)]);
          return jsonOutput({ status: "SUCCESS", message: conAperturas(conImagen("Actualizado OK")) });
        }
        // Cambió de mes: se escribe en la hoja nueva y recién después se borra físicamente la vieja
        // (si fallara la escritura, el movimiento no se pierde). Una baja lógica dejaría dos filas
        // con el mismo id, y el listado de la app usa el id como clave.
        sheet.appendRow(filaDe(ticketAnterior));
        existente.sheet.deleteRow(existente.fila);
        return jsonOutput({ status: "SUCCESS", message: conAperturas(conImagen("Movido de " + existente.sheet.getName() + " a " + sheet.getName())) });
      }

      sheet.appendRow(filaDe(""));

      return jsonOutput({ status: "SUCCESS", message: conAperturas(imgStatus) });
    }

    return jsonOutput({ status: "ERROR", message: "Pedido no reconocido (acción: " + action + ")" });
  } catch (err) {
    return jsonOutput({ status: "ERROR", message: err.toString(), imgStatus: imgStatus });
  }
}

/**
 * Alta o edición de un plan de cuotas en la hoja "Planes" (upsert por id; [action] ya no distingue).
 * Crea la hoja con sus encabezados si no existe. Las columnas de fecha (B y F) se fuerzan a
 * formato texto para que Sheets no convierta "yyyy-MM" / "yyyy-MM-dd HH:mm" en objetos Date.
 */
function handlePlanUpsert(action, plan) {
  if (!plan || !plan.id) {
    return jsonOutput({ status: "ERROR", message: "Plan inválido (falta id)" });
  }

  const ss = SpreadsheetApp.getActiveSpreadsheet();
  let sheet = ss.getSheetByName(PLANS_SHEET);
  if (!sheet) {
    sheet = crearHojaConEncabezados(ss, PLANS_SHEET, ["ID", "Fecha Creación", "Descripción", "Monto Por Cuota", "Cantidad Cuotas", "Primera Cuota", "Propietario", "Categoría", "Tarjeta", "Eliminado", COLUMNA_PREVIAS, COLUMNA_EVITABLE_PLAN]);
    // Forzar texto en las columnas de fecha para preservar el formato "yyyy-MM"/"yyyy-MM-dd HH:mm".
    sheet.getRange("B:B").setNumberFormat("@");
    sheet.getRange("F:F").setNumberFormat("@");
  }

  asegurarColumnaPrevias(sheet);
  // Las hojas "Planes" anteriores a la v7.7 no tienen la columna L.
  asegurarEncabezado(sheet, PLAN_COLS, COLUMNA_EVITABLE_PLAN);
  const delPayload = parsePrevias(plan.cuotasPagadasPrevias);

  const rowValues = function (previas) {
    return [
      plan.id,
      plan.fechaCreacion,
      plan.descripcion,
      plan.montoPorCuota,
      plan.cantidadCuotas,
      plan.fechaPrimeraCuota,
      plan.propietario,
      plan.categoria,
      plan.tarjeta || "",
      false,
      formatPrevias(previas),
      plan.evitable === true
    ];
  };

  // Upsert por id, sea alta o edición: reintentar un alta no duplica el plan.
  const data = sheet.getDataRange().getValues();
  for (let i = 1; i < data.length; i++) {
    if (String(data[i][COL_PLAN.ID]) === String(plan.id)) {
      // La columna K se escribe SIEMPRE por unión con lo que ya había: un cliente con el plan
      // cacheado de antes del corte mandaría una lista más corta y, reemplazando, borraría el
      // registro de cuotas pagadas cuyos movimientos ya no existen.
      const previas = unirPrevias(parsePrevias(data[i].length > COL_PLAN.PREVIAS ? data[i][COL_PLAN.PREVIAS] : ""), delPayload);
      sheet.getRange(i + 1, 1, 1, PLAN_COLS).setValues([rowValues(previas)]);
      return jsonOutput({ status: "SUCCESS", message: "Plan actualizado OK" });
    }
  }

  sheet.appendRow(rowValues(delPayload));
  return jsonOutput({ status: "SUCCESS", message: "Plan creado OK" });
}

/** Encabezado de la columna K de la hoja "Planes" (snapshot de cuotas del corte de mes). */
const COLUMNA_PREVIAS = "Cuotas Pagadas Previas";

/** Encabezado de la columna L de la hoja "Planes" (compra evitable; vacío = no evitable). */
const COLUMNA_EVITABLE_PLAN = "Evitable";

/** "1,2,3" (o un array) -> [1,2,3]. Tolera vacío, espacios y basura. */
function parsePrevias(v) {
  if (v === null || v === undefined || v === "") return [];
  const partes = Array.isArray(v) ? v : v.toString().split(",");
  const out = [];
  partes.forEach(function (x) {
    const n = Number(String(x).trim());
    if (n > 0 && out.indexOf(n) === -1) out.push(n);
  });
  return out.sort(function (a, b) { return a - b; });
}

/** [3,1] -> "1,3". Inversa de [parsePrevias]. */
function formatPrevias(arr) {
  return parsePrevias(arr).join(",");
}

/** Unión de dos listas de cuotas. NUNCA reemplazo: el snapshot solo puede crecer. */
function unirPrevias(a, b) {
  return parsePrevias(parsePrevias(a).concat(parsePrevias(b)));
}

/** Escribe el encabezado de la columna K si la hoja "Planes" es anterior al snapshot. */
function asegurarColumnaPrevias(sheet) {
  // Una hoja creada a mano puede tener menos de 11 columnas; getRange("K:K") tiraría excepción y se
  // llevaría puesto el guardado del plan entero.
  const col = COL_PLAN.PREVIAS + 1;
  if (sheet.getMaxColumns() < col) sheet.insertColumnsAfter(sheet.getMaxColumns(), col - sheet.getMaxColumns());

  // La columna va a TEXTO, igual que las de fecha. Sin esto Sheets interpreta el contenido como
  // número: con una sola cuota es inofensivo ("1" -> 1, que `parsePrevias` vuelve a leer bien), pero
  // en una planilla con coma decimal "1,2" se convierte en el número 1.2 y el snapshot de ese plan
  // se pierde en silencio — justo el caso de un plan con dos cuotas ya pagadas antes del corte.
  sheet.getRange("K:K").setNumberFormat("@");
  asegurarEncabezado(sheet, col, COLUMNA_PREVIAS);
}

/**
 * Congela, para cada plan, qué cuotas ya estaban pagadas ANTES de [mes], escribiéndolas por unión
 * en la columna K de la hoja "Planes".
 *
 * Es la mitad "cuotas" del corte de mes. El estado "pagada" se deriva del movimiento que la pagó, y
 * ese movimiento vive en la hoja del mes en que se pagó: sin este snapshot, purgar las hojas viejas
 * hace reaparecer como deuda cuotas ya saldadas, aunque el saldo esté bien (la apertura ya incorporó
 * la plata gastada).
 *
 * Idempotente y seguro de correr de más: solo agrega.
 */
function snapshotCuotasPrevias(mes) {
  if (!mes) return jsonOutput({ status: "ERROR", message: "Falta el mes del snapshot" });

  const previas = {};
  leerTodosLosMovimientos().forEach(function (m) {
    if (!m.planId || !m.cuotaNumero || m.cuotaNumero <= 0) return;
    if (!m.mes || m.mes >= mes) return;   // solo lo ya cerrado
    if (!previas[m.planId]) previas[m.planId] = [];
    if (previas[m.planId].indexOf(m.cuotaNumero) === -1) previas[m.planId].push(m.cuotaNumero);
  });

  const sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(PLANS_SHEET);
  if (!sheet) return jsonOutput({ status: "SUCCESS", message: "No hay hoja de planes: nada que congelar" });
  asegurarColumnaPrevias(sheet);

  const data = sheet.getDataRange().getValues();
  let tocados = 0;
  for (let i = 1; i < data.length; i++) {
    const id = texto(data[i][COL_PLAN.ID]);
    if (!id || !previas[id]) continue;
    const actual = parsePrevias(data[i].length > COL_PLAN.PREVIAS ? data[i][COL_PLAN.PREVIAS] : "");
    const union = unirPrevias(actual, previas[id]);
    if (union.length !== actual.length) {
      sheet.getRange(i + 1, COL_PLAN.PREVIAS + 1).setValue(formatPrevias(union));
      tocados++;
    }
  }

  return jsonOutput({
    status: "SUCCESS",
    message: "Snapshot de cuotas previo a " + mes + ": " + tocados + " plan(es) actualizados."
  });
}

/** Baja lógica de un plan: marca la columna J (index 9) = true en la hoja "Planes". */
function handlePlanDelete(id) {
  if (!id) return jsonOutput({ status: "ERROR", message: "Falta id del plan" });

  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const sheet = ss.getSheetByName(PLANS_SHEET);
  if (!sheet) return jsonOutput({ status: "ERROR", message: "No existe la hoja de planes" });

  const data = sheet.getDataRange().getValues();
  for (let i = 1; i < data.length; i++) {
    if (String(data[i][COL_PLAN.ID]) === String(id)) {
      sheet.getRange(i + 1, COL_PLAN.ELIMINADO + 1).setValue(true); // Columna J
      return jsonOutput({ status: "SUCCESS", message: "Plan eliminado (baja lógica) OK" });
    }
  }
  return jsonOutput({ status: "ERROR", message: "ID de plan no encontrado" });
}

// =================================================================================================
// Usuarios parametrizables (hoja "Usuarios")
// =================================================================================================

/**
 * Devuelve la hoja "Usuarios", creándola y sembrándola con los dos slots legacy si no existe.
 * La columna A (slotKey) se fuerza a texto: es la clave interna inmutable ("Santiago"/"Rocío").
 */
function getUsersSheetSeeded() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  let sheet = ss.getSheetByName(USERS_SHEET);
  if (!sheet) {
    sheet = crearHojaConEncabezados(ss, USERS_SHEET, ["slotKey", "Nombre", "ColorId", "Orden"]);
    sheet.getRange("A:A").setNumberFormat("@");
    // Seed: slotKey = nombre legacy y colores actuales -> retrocompatible con los datos existentes.
    sheet.appendRow([SANTIAGO, SANTIAGO, "green", 0]);
    sheet.appendRow([ROCIO, ROCIO, "blue", 1]);
  }
  return sheet;
}

/**
 * Lee la hoja "Usuarios". Si no existe, devuelve users:[] (el cliente cae a su config DEFAULT).
 * No la crea en el GET para no tener efectos secundarios en una lectura.
 */
function getUsers(e) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const sheet = ss.getSheetByName(USERS_SHEET);
  let users = [];
  if (sheet) {
    const data = sheet.getDataRange().getValues();
    for (let i = 1; i < data.length; i++) {
      const row = data[i];
      const slotKey = row[0] ? row[0].toString() : "";
      if (!slotKey) continue;
      users.push({
        slotKey: slotKey,
        nombre: row[1] ? row[1].toString() : slotKey,
        colorId: row[2] ? row[2].toString() : "",
        orden: Number(row[3]) || 0,
        // Mensaje del día (v7.8, columnas E/F): lo escribe generarMensajesDelDia().
        mensaje: row.length > 4 && row[4] ? row[4].toString() : "",
        mensajeFecha: row.length > 5 && row[5]
          ? (row[5] instanceof Date ? Utilities.formatDate(row[5], ZONA_ARGENTINA, "yyyy-MM-dd") : row[5].toString())
          : ""
      });
    }
  }
  return jsonOutput({ status: "SUCCESS", users: users });
}

/**
 * Edita (PUT) un usuario: busca por slotKey (col A, inmutable) y actualiza nombre (B) y colorId (C).
 * Siembra la hoja con los dos slots legacy si faltaba, de modo que el slotKey siempre se encuentre.
 * No existe alta/baja de usuarios desde la app.
 */
function handleUserUpsert(user) {
  if (!user || !user.slotKey) {
    return jsonOutput({ status: "ERROR", message: "Usuario inválido (falta slotKey)" });
  }
  const sheet = getUsersSheetSeeded();
  const data = sheet.getDataRange().getValues();
  for (let i = 1; i < data.length; i++) {
    if (String(data[i][0]) === String(user.slotKey)) {
      if (user.nombre !== undefined && user.nombre !== null) sheet.getRange(i + 1, 2).setValue(user.nombre);
      if (user.colorId !== undefined && user.colorId !== null) sheet.getRange(i + 1, 3).setValue(user.colorId);
      return jsonOutput({ status: "SUCCESS", message: "Usuario actualizado OK" });
    }
  }
  return jsonOutput({ status: "ERROR", message: "slotKey no encontrado: " + user.slotKey });
}

function handleLogicalDelete(id) {
  const sheets = hojasDeMovimientos(SpreadsheetApp.getActiveSpreadsheet());

  for (let sheet of sheets) {
    const data = sheet.getDataRange().getValues();
    for (let i = 1; i < data.length; i++) {
      if (data[i][COL.ID] == id) {
        // Marcar columna K como TRUE
        sheet.getRange(i + 1, COL.ELIMINADO + 1).setValue(true);
        const extra = esFilaApertura(data[i][COL.TIPO]) ? "" :
          recalcularAperturasPosteriores([mesDeFecha(data[i][COL.FECHA])]);
        return jsonOutput({ status: "SUCCESS", message: "Eliminado (baja lógica) OK" + (extra ? " | " + extra : "") });
      }
    }
  }

  return jsonOutput({ status: "ERROR", message: "ID no encontrado" });
}

// =================================================================================================
// SALDO INICIAL MATERIALIZADO (arrastre por hoja)
// =================================================================================================
//
// Hasta la v7.1 el saldo inicial de un mes se derivaba replayando TODOS los meses anteriores, así
// que ninguna hoja vieja se podía archivar sin romper los números. Desde la v7.2 cada hoja lleva
// sus propias filas de apertura (tipo "Apertura", categoría "Saldo inicial") con el stock inicial
// desagregado por persona x medio de pago x propietario.
//
// Las columnas se reusan tal cual: Responsable = de quién es la cuenta física; Propietario = de
// quién es realmente el dinero (así sobrevive el dinero prestado al cambio de mes); Metodo Pago =
// Efectivo | Billetera Virtual; Monto puede ser NEGATIVO.
//
// FUNCIONES PARA EJECUTAR A MANO DESDE EL EDITOR (en este orden):
//   1. previsualizarAperturas()      -> no escribe nada, sólo loguea qué quedaría en cada mes.
//   2. migrarAperturas()             -> escribe/actualiza las filas de apertura de todos los meses.
//   3. borrarLegacySaldoInicial()    -> borra las filas viejas de arrastre (Aporte "Saldo inicial").
//
// Son idempotentes: los ids son determinísticos, así que volver a correrlas pisa las filas en vez
// de duplicarlas.

const TIPO_APERTURA = "Apertura";
const CATEGORIA_APERTURA = "Saldo inicial";
const EFECTIVO = "Efectivo";
const VIRTUAL = "Billetera Virtual";

/**
 * Desde esta fecha una "Transferencia" que cambia de dueño es regalo completo y NO salda la deuda del
 * receptor (eso ahora es una "Devolución" explícita). Las anteriores conservan la regla vieja porque
 * ya saldaron deuda en la planilla (julio y agosto 2026). Espejo de
 * `AccountingEngine.CORTE_TRANSFERENCIA_SIN_DEVOLUCION`: si se cambia, cambiarlo en los dos lados.
 */
const CORTE_TRANSFERENCIA_SIN_DEVOLUCION = "2026-09-01";

function esFilaApertura(tipo) {
  return String(tipo || "").toLowerCase() === TIPO_APERTURA.toLowerCase();
}

/** Filas de arrastre de versiones <= 7.1: un Aporte "Saldo inicial" por persona. Se ignoran. */
function esLegacySaldoInicial(tipo, categoria) {
  return !esFilaApertura(tipo) &&
    String(categoria || "").toLowerCase() === CATEGORIA_APERTURA.toLowerCase();
}

function normalizarPropietario(propietario, responsable) {
  const p = String(propietario || "").trim().toLowerCase();
  if (p === SANTIAGO.toLowerCase()) return SANTIAGO;
  if (p === ROCIO.toLowerCase()) return ROCIO;
  if (p === AMBOS.toLowerCase()) return AMBOS;
  return String(responsable || "").trim().toLowerCase() === ROCIO.toLowerCase() ? ROCIO : SANTIAGO;
}

/**
 * Lee TODOS los movimientos de todas las hojas de meses, conservando en qué hoja y fila está cada
 * uno. Excluye los eliminados lógicamente.
 */
function leerTodosLosMovimientos() {
  const out = [];
  hojasDeMovimientos(SpreadsheetApp.getActiveSpreadsheet()).forEach(function (sheet) {
    const nombre = sheet.getName();
    const data = sheet.getDataRange().getValues();
    for (let i = 1; i < data.length; i++) {
      const row = data[i];
      if (!row[COL.ID] && !row[COL.FECHA]) continue;          // fila vacía
      if (isEliminado(row[COL.ELIMINADO])) continue;
      out.push({
        hoja: nombre,
        fila: i + 1,
        id: texto(row[COL.ID]),
        fecha: toDateTime(row[COL.FECHA]),
        mes: toYearMonth(row[COL.FECHA]).substring(0, 7),
        monto: Number(row[COL.MONTO]) || 0,
        tipo: texto(row[COL.TIPO]),
        categoria: texto(row[COL.CATEGORIA]),
        responsable: texto(row[COL.RESPONSABLE]),
        esComun: esVerdadero(row[COL.ES_COMUN]),
        metodoPago: row[COL.METODO] ? row[COL.METODO].toString() : VIRTUAL,
        propietario: row[COL.PROPIETARIO] ? row[COL.PROPIETARIO].toString() : texto(row[COL.RESPONSABLE]),
        planId: texto(row[COL.PLAN_ID]),
        cuotaNumero: Number(row[COL.CUOTA]) || 0,
        evitable: esVerdadero(row[COL.EVITABLE])
      });
    }
  });
  return out;
}

/**
 * Aplica una lista de movimientos sobre un estado y devuelve el estado resultante. Port exacto de
 * `AccountingEngine.compute` (app/src/main/java/com/example/domain/AccountingEngine.kt) restringido al
 * stock: los flujos del periodo (aportes/gastos) no hacen falta acá.
 */
function aplicarMovimientos(movs, estado) {
  let sEfec = estado.sEfec, sVirt = estado.sVirt;
  let rEfec = estado.rEfec, rVirt = estado.rVirt;
  let netSR = estado.netSR;

  const ordenados = movs.slice().sort(function (a, b) {
    if (a.fecha === b.fecha) return a.id < b.id ? -1 : 1;
    return a.fecha < b.fecha ? -1 : 1;
  });

  ordenados.forEach(function (m) {
    if (esFilaApertura(m.tipo) || esLegacySaldoInicial(m.tipo, m.categoria)) return;
    const respS = String(m.responsable).toLowerCase() === SANTIAGO.toLowerCase();
    const respR = String(m.responsable).toLowerCase() === ROCIO.toLowerCase();
    if (!respS && !respR) return;

    const prop = normalizarPropietario(m.propietario, m.responsable);
    const propS = prop === SANTIAGO, propR = prop === ROCIO, propAmbos = prop === AMBOS;
    const efec = String(m.metodoPago).toLowerCase() === EFECTIVO.toLowerCase();
    const monto = m.monto;

    switch (String(m.tipo).toLowerCase()) {
      case "aporte":
        if (respS) { if (efec) sEfec += monto; else sVirt += monto; }
        else { if (efec) rEfec += monto; else rVirt += monto; }
        if (propAmbos) { const h = monto / 2; netSR += respS ? -h : h; }
        else if (propS) { if (respR) netSR += monto; }
        else if (propR) { if (respS) netSR -= monto; }
        break;

      case "gasto": {
        const comun = m.esComun || propAmbos;
        if (respS) { if (efec) sEfec -= monto; else sVirt -= monto; }
        else { if (efec) rEfec -= monto; else rVirt -= monto; }
        if (comun) { const h = monto / 2; netSR += respS ? h : -h; }
        else if (propS) { if (respR) netSR -= monto; }
        else if (propR) { if (respS) netSR += monto; }
        break;
      }

      case "transferencia": {
        // Antes del corte, la que cambia de dueño primero saldaba deuda; después es regalo puro.
        const saldaPrimero = String(m.fecha) < CORTE_TRANSFERENCIA_SIN_DEVOLUCION;
        if (respS) {
          if (efec) { sEfec -= monto; rEfec += monto; } else { sVirt -= monto; rVirt += monto; }
          if (propS) netSR += monto;
          else if (propR && saldaPrimero) netSR += Math.min(monto, Math.max(0, -netSR)); // devolución
        } else {
          if (efec) { rEfec -= monto; sEfec += monto; } else { rVirt -= monto; sVirt += monto; }
          if (propR) netSR -= monto;
          else if (propS && saldaPrimero) netSR -= Math.min(monto, Math.max(0, netSR)); // devolución
        }
        break;
      }

      // Pago de deuda: la plata sale de la cuenta del responsable y cancela lo que el otro tenía
      // estacionado en ella (clampeado; el excedente es regalo y no toca la propiedad cruzada).
      case "devolución":
      case "devolucion":
        if (respS) {
          if (efec) { sEfec -= monto; rEfec += monto; } else { sVirt -= monto; rVirt += monto; }
          netSR += Math.min(monto, Math.max(0, -netSR));
        } else {
          if (efec) { rEfec -= monto; sEfec += monto; } else { rVirt -= monto; sVirt += monto; }
          netSR -= Math.min(monto, Math.max(0, netSR));
        }
        break;

      // Cambio de dinero: el responsable entrega el medio de `metodoPago` y recibe el otro. Se cruzan
      // los buckets de cada uno; la propiedad cruzada no cambia.
      // Cambio propio (con alguien de afuera): solo se cruzan los buckets del responsable.
      case "cambio propio":
        if (respS) {
          if (efec) { sEfec -= monto; sVirt += monto; } else { sVirt -= monto; sEfec += monto; }
        } else {
          if (efec) { rEfec -= monto; rVirt += monto; } else { rVirt -= monto; rEfec += monto; }
        }
        break;

      case "cambio": {
        const entregaS = respS ? monto : -monto;
        if (efec) { sEfec -= entregaS; sVirt += entregaS; rEfec += entregaS; rVirt -= entregaS; }
        else { sVirt -= entregaS; sEfec += entregaS; rVirt += entregaS; rEfec -= entregaS; }
        break;
      }

      // Perdón de deuda: el responsable (acreedor) renuncia a lo que el otro tiene de él. NO toca
      // ningún bucket físico, solo cancela propiedad cruzada; clampeado para que perdonar de más no
      // genere deuda en el sentido contrario. Sin este caso, recalcular el saldo inicial resucitaría
      // la deuda perdonada en la apertura del mes siguiente.
      case "condonación":
      case "condonacion":
        if (respS) netSR -= Math.min(monto, Math.max(0, netSR));  // el primario perdona al secundario
        else netSR += Math.min(monto, Math.max(0, -netSR));       // el secundario perdona al primario
        break;
    }
  });

  return { sEfec: sEfec, sVirt: sVirt, rEfec: rEfec, rVirt: rVirt, netSR: netSR };
}

/**
 * Serializa un estado a las filas de apertura de un mes. Inversa de leerlas.
 * La propiedad cruzada se ancla al bucket VIRTUAL de quien tiene el dinero físicamente (el neto no
 * depende del medio de pago, pero fijarlo mantiene la generación determinística).
 */
function filasDeApertura(mes, estado) {
  const net = estado.netSR;
  const sVirtDeRocio = net < 0 ? -net : 0;
  const rVirtDeSantiago = net > 0 ? net : 0;

  // Las 6 filas van SIEMPRE, aunque la propiedad cruzada sea cero: la escritura es un upsert por id
  // y no borra lo que ya no se emite, así que una fila cruzada de una escritura anterior quedaba viva
  // (y sumando) cuando la deuda se saldaba o cambiaba de lado. Mismo orden que `openingRowsFor`.
  const filas = [
    [SANTIAGO, SANTIAGO, EFECTIVO, estado.sEfec],
    [SANTIAGO, SANTIAGO, VIRTUAL, estado.sVirt - sVirtDeRocio],
    [SANTIAGO, ROCIO, VIRTUAL, sVirtDeRocio],
    [ROCIO, ROCIO, EFECTIVO, estado.rEfec],
    [ROCIO, ROCIO, VIRTUAL, estado.rVirt - rVirtDeSantiago],
    [ROCIO, SANTIAGO, VIRTUAL, rVirtDeSantiago]
  ];

  return filas.map(function (f) {
    const responsable = f[0], propietario = f[1], metodo = f[2], monto = f[3];
    const quien = responsable === ROCIO ? "r" : "s";
    const deQuien = propietario === ROCIO ? "r" : "s";
    const medio = metodo === EFECTIVO ? "efec" : "virt";
    const deOtro = propietario !== responsable ? " (de " + propietario + ")" : "";
    return {
      id: "apertura-" + mes + "-" + quien + "-" + deQuien + "-" + medio,
      fecha: mes + "-01 00:00",
      monto: monto,
      tipo: TIPO_APERTURA,
      categoria: CATEGORIA_APERTURA,
      responsable: responsable,
      esComun: false,
      descripcion: "Saldo inicial " + mes + " · " + responsable + " · " + metodo + deOtro,
      metodoPago: metodo,
      propietario: propietario
    };
  });
}

/** ¿El estado no tiene nada? (todos los buckets y la propiedad cruzada en cero, salvo centavos). */
function esEstadoVacio(st) {
  return Math.abs(st.sEfec) < 0.005 && Math.abs(st.sVirt) < 0.005 &&
    Math.abs(st.rEfec) < 0.005 && Math.abs(st.rVirt) < 0.005 && Math.abs(st.netSR) < 0.005;
}

/** Estado que representan las filas de apertura ya escritas en las filas [data] de una hoja. */
function estadoEscritoDe(data) {
  const filas = [];
  for (let i = 1; i < data.length; i++) {
    const row = data[i];
    if (!esFilaApertura(row[COL.TIPO])) continue;
    filas.push({
      responsable: texto(row[COL.RESPONSABLE]),
      propietario: texto(row[COL.PROPIETARIO]),
      metodoPago: row[COL.METODO] ? row[COL.METODO].toString() : VIRTUAL,
      monto: Number(row[COL.MONTO]) || 0
    });
  }
  return estadoDesdeFilasDeApertura(filas);
}

/** "2026-08" -> "Agosto 2026". Devuelve null si el mes no es parseable. */
function nombreHojaDeMes(mes) {
  const partes = String(mes).split("-");
  if (partes.length < 2) return null;
  const idx = parseInt(partes[1], 10) - 1;
  if (isNaN(idx) || idx < 0 || idx > 11) return null;
  return monthNames[idx] + " " + partes[0];
}

/**
 * Reconstruye el estado a partir de filas de apertura ya escritas. Inversa de [filasDeApertura];
 * port de `AccountingEngine.openingFromRows`.
 */
function estadoDesdeFilasDeApertura(filas) {
  let sEfec = 0, sVirt = 0, rEfec = 0, rVirt = 0, netSR = 0;
  filas.forEach(function (m) {
    const respS = String(m.responsable).toLowerCase() === SANTIAGO.toLowerCase();
    const respR = String(m.responsable).toLowerCase() === ROCIO.toLowerCase();
    if (!respS && !respR) return;
    const efec = String(m.metodoPago).toLowerCase() === EFECTIVO.toLowerCase();

    if (respS) { if (efec) sEfec += m.monto; else sVirt += m.monto; }
    else { if (efec) rEfec += m.monto; else rVirt += m.monto; }

    const prop = normalizarPropietario(m.propietario, m.responsable);
    if (prop === SANTIAGO) { if (respR) netSR += m.monto; }
    else if (prop === ROCIO) { if (respS) netSR -= m.monto; }
    else { const h = m.monto / 2; netSR += respS ? -h : h; } // Ambos
  });
  return { sEfec: sEfec, sVirt: sVirt, rEfec: rEfec, rVirt: rVirt, netSR: netSR };
}

/** Filas de apertura ya escritas en un mes (vacío si no tiene). */
function filasAperturaEscritasDe(movimientos, mes) {
  return movimientos.filter(function (m) { return m.mes === mes && esFilaApertura(m.tipo); });
}

/**
 * Pliega el historial mes a mes y devuelve { mes: estadoInicialDeEseMes }.
 *
 * El primer mes disponible se SIEMBRA con su propia fila de apertura si la tiene: una vez que se
 * archivan las hojas viejas, esa fila es la única fuente de verdad del arrastre y arrancar de cero
 * borraría todo el patrimonio acumulado. De ahí en adelante se encadenan valores recalculados (no
 * los guardados), así que corregir un movimiento viejo se propaga a todos los meses siguientes.
 */
function calcularAperturasPorMes(movimientos) {
  const meses = {};
  movimientos.forEach(function (m) { if (m.mes) meses[m.mes] = true; });
  const ordenados = Object.keys(meses).sort();
  if (!ordenados.length) return {};

  let estado = estadoDesdeFilasDeApertura(filasAperturaEscritasDe(movimientos, ordenados[0]));
  const aperturas = {};
  ordenados.forEach(function (mes, i) {
    if (i > 0) aperturas[mes] = estado;
    estado = aplicarMovimientos(movimientos.filter(function (m) { return m.mes === mes; }), estado);
  });
  return aperturas;
}

/** "$ 1.234,56" con separadores, para poder comparar de un vistazo contra el homebanking. */
function fmtPlata(n) {
  const neg = n < 0;
  const partes = Math.abs(n).toFixed(2).split(".");
  const entero = partes[0].replace(/\B(?=(\d{3})+(?!\d))/g, ".");
  return (neg ? "-$ " : "$ ") + entero + "," + partes[1];
}

/** Bloque de texto con el estado de una persona: físico, cruzado y patrimonial. */
function describirPersona(nombre, efec, virt, cruzado) {
  const fisico = efec + virt;
  return "  " + nombre + "\n" +
    "     efectivo ............ " + fmtPlata(efec) + "\n" +
    "     en cuenta ........... " + fmtPlata(virt) + "\n" +
    "     EN SU PODER (físico)  " + fmtPlata(fisico) + "   <- conciliar contra el banco\n" +
    "     " + (cruzado >= 0 ? "le deben ..........." : "debe ...............") + " " + fmtPlata(cruzado) + "\n" +
    "     LE CORRESPONDE ...... " + fmtPlata(fisico + cruzado);
}

/**
 * PASO 1 - No escribe nada. Para cada mes loguea con qué ARRANCA y con qué CIERRA.
 *
 * Ojo: la fila de apertura guarda el arranque del mes, no el estado de hoy. El número que hay que
 * comparar contra el homebanking es el CIERRE del último mes.
 */
function previsualizarAperturas() {
  const movs = leerTodosLosMovimientos();

  const meses = {};
  movs.forEach(function (m) { if (m.mes) meses[m.mes] = true; });
  const ordenados = Object.keys(meses).sort();

  Logger.log("Movimientos leídos: " + movs.length + " | meses: " + ordenados.join(", "));

  // Mismo criterio que calcularAperturasPorMes: el primer mes se siembra con su apertura escrita,
  // porque los meses anteriores pueden estar archivados.
  let estado = estadoDesdeFilasDeApertura(filasAperturaEscritasDe(movs, ordenados[0]));
  ordenados.forEach(function (mes, i) {
    const apertura = estado;
    const cierre = aplicarMovimientos(movs.filter(function (m) { return m.mes === mes; }), apertura);
    const ultimo = (i === ordenados.length - 1);

    let txt = "=== " + mes + " (" + nombreHojaDeMes(mes) + ") ===\n";
    if (i === 0) {
      txt += "  [ARRANCA] mes más viejo disponible; su apertura es el ancla y no se recalcula\n" +
        describirPersona(SANTIAGO, apertura.sEfec, apertura.sVirt, apertura.netSR) + "\n" +
        describirPersona(ROCIO, apertura.rEfec, apertura.rVirt, -apertura.netSR) + "\n";
    } else {
      txt += "  [ARRANCA EL " + mes + "-01]  -> " + filasDeApertura(mes, apertura).length + " filas de apertura\n" +
        describirPersona(SANTIAGO, apertura.sEfec, apertura.sVirt, apertura.netSR) + "\n" +
        describirPersona(ROCIO, apertura.rEfec, apertura.rVirt, -apertura.netSR) + "\n";
    }
    txt += "  [CIERRA]" + (ultimo ? "  <<< ESTADO DE HOY >>>" : "") + "\n" +
      describirPersona(SANTIAGO, cierre.sEfec, cierre.sVirt, cierre.netSR) + "\n" +
      describirPersona(ROCIO, cierre.rEfec, cierre.rVirt, -cierre.netSR);
    Logger.log(txt);

    estado = cierre;
  });

  Logger.log(
    "Nada fue escrito.\n" +
    "Compará el bloque [CIERRA] del último mes contra tu homebanking y tu billetera:\n" +
    "  - 'EN SU PODER' es la plata que existe físicamente. Si no coincide, faltan cargar movimientos.\n" +
    "  - 'LE CORRESPONDE' ya descuenta lo prestado. NO es lo que dice el banco.\n" +
    "Si el físico cierra, corré migrarAperturas()."
  );
}

// -------------------------------------------------------------------------------------------------
// Automatización: trigger diario que mantiene las aperturas al día
// -------------------------------------------------------------------------------------------------

const TRIGGER_APERTURAS = "actualizarAperturas";

/**
 * Corré esto UNA vez desde el editor para automatizar todo. Deja un trigger diario (~4 AM) que
 * mantiene las aperturas al día solo. Es idempotente: volver a correrlo no duplica el trigger.
 */
function instalarTriggerDeAperturas() {
  instalarTriggerDiario(TRIGGER_APERTURAS, 4);
  Logger.log("Trigger diario instalado (~4 AM): " + TRIGGER_APERTURAS + "().");
  Logger.log("Verificalo en el editor, panel izquierdo, ícono del reloj (Activadores).");
}

/** Quita el trigger diario. El saldo inicial vuelve a depender del botón de Ajustes. */
function desinstalarTriggerDeAperturas() {
  Logger.log("Triggers quitados: " + desinstalarTriggers(TRIGGER_APERTURAS));
}

/**
 * Lo que corre el trigger. Recalcula TODAS las aperturas (no solo la del mes nuevo) y se asegura de
 * que el mes calendario actual tenga la suya, creando la hoja si hace falta.
 *
 * Recalcular todo a diario en vez de escribir una sola vez el día 1 es a propósito: los movimientos
 * se cargan con atraso. Si el 3 de septiembre agregás un gasto con fecha 31 de agosto, la apertura
 * de septiembre queda vieja. Lo que entra por la app ya lo corrige doPost en el momento
 * ([recalcularAperturasPosteriores]); el trigger queda de red para lo que se edita a mano en la
 * planilla o si ese recálculo falló. Escribir una única vez dejaría el error congelado para siempre.
 */
function actualizarAperturas() {
  return conCandado("actualizarAperturas", function () {
    const movs = leerTodosLosMovimientos();
    const aperturas = calcularAperturasPorMes(movs);
    let total = escribirAperturas(aperturas);

    // El mes calendario actual puede no tener movimientos todavía (típico los primeros días).
    // Le escribimos igual su apertura para que la hoja nazca con el arrastre puesto.
    const mesActual = mesCalendarioActual();
    if (!aperturas[mesActual] && !filasAperturaEscritasDe(movs, mesActual).length) {
      const previos = movs.filter(function (m) { return m.mes && m.mes < mesActual; });
      if (previos.length) {
        total += escribirAperturaDeMes(mesActual, calcularAperturaDe(previos, mesActual));
      }
    }

    Logger.log("Aperturas actualizadas: " + total + " filas.");
    return total;
  });
}

/** "yyyy-MM" de hoy en el huso del script. */
function mesCalendarioActual() {
  return Utilities.formatDate(new Date(), Session.getScriptTimeZone(), "yyyy-MM");
}

/** "yyyy-MM" de una celda o string de fecha; "" si no parsea a un mes válido. */
function mesDeFecha(fecha) {
  const mes = toYearMonth(fecha).substring(0, 7);
  return /^\d{4}-(0[1-9]|1[0-2])$/.test(mes) ? mes : "";
}

/**
 * Recalcula en el momento las aperturas de los meses posteriores al más viejo de [meses], si ese
 * mes ya cerró (es anterior al mes calendario actual). Lo llama doPost después de escribir o dar de
 * baja un movimiento, con el candado ya tomado (por eso no pasa por [conCandado]).
 *
 * La app arma el saldo de un mes con su apertura escrita y no mira los meses anteriores
 * (`AccountingEngine.openingFor`). Sin esto, un movimiento con fecha vieja —el caso típico es
 * pagar una cuota atrasada, que se fecha en el mes de la cuota— no movía el saldo del mes en curso
 * hasta que [actualizarAperturas] corría a la madrugada siguiente.
 *
 * Devuelve un texto para el mensaje de la respuesta ("" si no hizo falta). Nunca tira: el
 * movimiento ya quedó guardado y, si esto falla, el trigger diario lo corrige igual.
 */
function recalcularAperturasPosteriores(meses) {
  const mesActual = mesCalendarioActual();
  const desde = meses.filter(function (m) { return m && m < mesActual; }).sort()[0];
  if (!desde) return "";
  try {
    SpreadsheetApp.flush();
    const aperturas = calcularAperturasPorMes(leerTodosLosMovimientos());
    let total = 0;
    Object.keys(aperturas).sort().forEach(function (mes) {
      if (mes > desde) total += escribirAperturaDeMes(mes, aperturas[mes]);
    });
    return "Aperturas posteriores a " + desde + " recalculadas (" + total + " filas)";
  } catch (err) {
    Logger.log("No se pudieron recalcular las aperturas posteriores a " + desde + ": " + err);
    return "Aperturas sin recalcular (las corrige el trigger diario): " + err;
  }
}

/** Escribe las [aperturas] ({mes: estado}) en sus hojas. Devuelve cuántas filas tocó. */
function escribirAperturas(aperturas) {
  let total = 0;
  Object.keys(aperturas).sort().forEach(function (mes) {
    total += escribirAperturaDeMes(mes, aperturas[mes]);
  });
  return total;
}

/** PASO 2 - Escribe/actualiza las filas de apertura de TODOS los meses (menos el primero). */
function migrarAperturas() {
  conCandado("migrarAperturas", function () {
    const total = escribirAperturas(calcularAperturasPorMes(leerTodosLosMovimientos()));
    Logger.log("Listo: " + total + " filas de apertura escritas/actualizadas.");
    Logger.log("Revisá los saldos en la app y después corré borrarLegacySaldoInicial().");
  });
}

/**
 * Recalcula y reescribe la apertura de UN mes puntual. Ej: escribirApertura("2026-09").
 *
 * El arrastre sale de encadenar los meses anteriores desde el más viejo disponible, que se siembra
 * con su propia apertura escrita (ver [calcularAperturasPorMes]). Es distinto de lo que hace la app
 * (`AccountingEngine.openingFor`, que confía en la apertura escrita más reciente): acá se RECALCULA
 * a propósito, para que corregir un movimiento viejo se propague a las aperturas siguientes.
 */
function escribirApertura(mes) {
  conCandado("escribirApertura", function () {
    const previos = leerTodosLosMovimientos().filter(function (m) { return m.mes && m.mes < mes; });
    const estado = calcularAperturaDe(previos, mes);
    const n = escribirAperturaDeMes(mes, estado);
    Logger.log("Apertura de " + mes + ": " + n + " filas.");
  });
}

/** Estado con el que arranca [mes], derivado de los movimientos anteriores a él. */
function calcularAperturaDe(movimientosPrevios, mes) {
  const aperturas = calcularAperturasPorMes(movimientosPrevios);
  const meses = {};
  movimientosPrevios.forEach(function (m) { if (m.mes) meses[m.mes] = true; });
  const ordenados = Object.keys(meses).sort();
  if (!ordenados.length) return { sEfec: 0, sVirt: 0, rEfec: 0, rVirt: 0, netSR: 0 };

  // El arrastre de `mes` es el cierre del último mes previo = su apertura + sus movimientos.
  const ultimo = ordenados[ordenados.length - 1];
  const aperturaUltimo = aperturas[ultimo] ||
    estadoDesdeFilasDeApertura(filasAperturaEscritasDe(movimientosPrevios, ultimo));
  return aplicarMovimientos(
    movimientosPrevios.filter(function (m) { return m.mes === ultimo; }),
    aperturaUltimo
  );
}

/** Upsert por id de las filas de apertura de un mes. Devuelve cuántas filas tocó. */
function escribirAperturaDeMes(mes, estado) {
  const nombre = nombreHojaDeMes(mes);
  if (!nombre) { Logger.log("Mes inválido, se omite: " + mes); return 0; }

  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const sheet = hojaDeMovimientos(ss, nombre);

  // Índice id -> nº de fila, para pisar en vez de duplicar.
  const data = sheet.getDataRange().getValues();
  const filaPorId = {};
  for (let i = 1; i < data.length; i++) {
    if (data[i][COL.ID]) filaPorId[data[i][COL.ID].toString()] = i + 1;
  }

  // Nunca pisar una apertura con saldo por una en cero. Un estado vacío con una apertura ya escrita
  // significa que faltan los meses anteriores (hojas purgadas), no que el patrimonio sea cero:
  // escribirlo se llevaría puesto todo el arrastre. Espejo de `AccountingEngine.chequearRecalculo`.
  if (esEstadoVacio(estado) && !esEstadoVacio(estadoEscritoDe(data))) {
    Logger.log("Apertura de " + mes + " NO tocada: el recálculo da cero y la escrita tiene saldo " +
      "(¿faltan los meses anteriores?).");
    return 0;
  }

  const filas = filasDeApertura(mes, estado);
  filas.forEach(function (f) {
    const valores = [[
      f.id, f.fecha, f.monto, f.tipo, f.categoria, f.responsable, f.esComun,
      f.descripcion, f.metodoPago, "", false, f.propietario, "", 0, false
    ]];
    const existente = filaPorId[f.id];
    if (existente) {
      sheet.getRange(existente, COL.FECHA + 1).setNumberFormat("@");
      sheet.getRange(existente, 1, 1, MOVEMENT_COLS).setValues(valores);
    } else {
      sheet.appendRow(valores[0]);
      // La fecha va como texto para que Sheets no la convierta en Date.
      sheet.getRange(sheet.getLastRow(), COL.FECHA + 1).setNumberFormat("@").setValue(f.fecha);
    }
  });

  Logger.log(nombre + ": " + filas.length + " filas de apertura.");
  return filas.length;
}

/**
 * PASO 3 - Borra físicamente las filas de arrastre viejas (Aporte con categoría "Saldo inicial").
 * La app ya las ignora, así que esto es sólo limpieza. Corrélo DESPUÉS de validar los saldos.
 */
function borrarLegacySaldoInicial() {
  conCandado("borrarLegacySaldoInicial", function () {
    let borradas = 0;
    hojasDeMovimientos(SpreadsheetApp.getActiveSpreadsheet()).forEach(function (sheet) {
      const data = sheet.getDataRange().getValues();
      // De abajo hacia arriba: borrar una fila corre los índices de las de abajo.
      for (let i = data.length - 1; i >= 1; i--) {
        if (esLegacySaldoInicial(data[i][COL.TIPO], data[i][COL.CATEGORIA])) {
          sheet.deleteRow(i + 1);
          borradas++;
        }
      }
    });
    Logger.log("Filas legacy de 'Saldo inicial' borradas: " + borradas);
  });
}

/**
 * OPCIONAL (v7.7) - Renombra la categoría "Sueldo" a "Ingreso" en los aportes de todas las hojas.
 * No hace falta: la app ya trata "Sueldo" como "Ingreso". Es solo para dejar la planilla prolija.
 * Idempotente.
 */
function renombrarSueldoAIngreso() {
  conCandado("renombrarSueldoAIngreso", function () {
    let cambiadas = 0;
    hojasDeMovimientos(SpreadsheetApp.getActiveSpreadsheet()).forEach(function (sheet) {
      const data = sheet.getDataRange().getValues();
      for (let i = 1; i < data.length; i++) {
        const tipo = String(data[i][COL.TIPO] || "").toLowerCase();
        const categoria = String(data[i][COL.CATEGORIA] || "").trim().toLowerCase();
        if (tipo === "aporte" && categoria === "sueldo") {
          sheet.getRange(i + 1, COL.CATEGORIA + 1).setValue("Ingreso");
          cambiadas++;
        }
      }
    });
    Logger.log("Aportes renombrados de Sueldo a Ingreso: " + cambiadas);
  });
}

// =================================================================================================
// MENSAJE DEL DÍA (v7.8)
// =================================================================================================
//
// Todas las madrugadas (~2 AM, hora Argentina) se le pide a Gemini UN mensaje corto y amistoso por
// persona a partir de sus movimientos del mes actual y del anterior, y se guarda en la hoja
// "Usuarios" (columna E "Mensaje", F "Fecha mensaje"), pisando el del día anterior. La app lo lee
// con GET_USERS, que ya pedía al arrancar: no suma ningún request.
//
// Configuración (Configuración del proyecto > Propiedades de la secuencia de comandos):
//   GEMINI_API_KEY   (obligatoria)  la clave de Google AI Studio. NUNCA en el código ni en el repo.
//   GEMINI_MODELO    (opcional)     lista separada por comas, en orden de preferencia. Si un modelo
//                                   da error de cuota, no existe o devuelve algo inservible, se
//                                   prueba el siguiente. Default: MSG_MODELOS_DEFAULT.
//   MENSAJES_ACTIVOS (opcional)     "false" apaga la generación (no se llama a Gemini).
//
// Para empezar: cargar la clave, correr probarMensajesDelDia() (loguea y escribe los mensajes) y
// después instalarTriggerDeMensajes() UNA vez.
//
// Qué se manda: SOLO gastos y aportes vivos de las hojas del mes actual y del anterior, con día,
// quién, tipo, categoría, monto, descripción (recortada) y si fue evitable. Nada de ids, tickets,
// aperturas, transferencias ni de otras hojas.

const MSG_ZONA = ZONA_ARGENTINA;
const MSG_COL_MENSAJE = 5;        // E
const MSG_COL_FECHA = 6;          // F
const MSG_MAX_CARACTERES = 90;    // tope duro; al modelo se le piden <= 60
const TRIGGER_MENSAJES = "generarMensajesDelDia";

/**
 * Hasta cuántos días atrás un movimiento cuenta como "reciente": lo único sobre lo que el mensaje
 * puede preguntar o comentar como si acabara de pasar. Sin este corte preguntaba "¿qué tal ese ramen
 * en el barrio chino?" por algo de la semana pasada. Se genera de madrugada, así que 1 = ayer.
 */
const MSG_DIAS_RECIENTES = 3;

/**
 * Modelos por defecto, del preferido al de respaldo: el Flash más nuevo (mejor humor), el Flash-Lite
 * más nuevo (el más económico) y el 2.5 Flash como último recurso. Google renombra y retira modelos
 * seguido: con la lista, que uno desaparezca o pierda el nivel gratuito no deja a nadie sin mensaje.
 */
const MSG_MODELOS_DEFAULT = "gemini-3.8-flash,gemini-3.5-flash-lite,gemini-2.5-flash";

/** Lo que corre el trigger diario. No tira excepciones: si algo falla, queda el mensaje de ayer. */
function generarMensajesDelDia() {
  try {
    const r = calcularMensajesDelDia_();
    Logger.log(r);
  } catch (err) {
    Logger.log("Mensaje del día: falló, se conservan los anteriores. " + err);
  }
}

/** Para probar a mano desde el editor: genera, escribe y muestra lo que quedó. */
function probarMensajesDelDia() {
  Logger.log(calcularMensajesDelDia_());
}

function instalarTriggerDeMensajes() {
  instalarTriggerDiario(TRIGGER_MENSAJES, 2, MSG_ZONA);
  Logger.log("Trigger diario instalado (~2 AM Argentina): " + TRIGGER_MENSAJES + "().");
}

function desinstalarTriggerDeMensajes() {
  Logger.log("Triggers de mensajes quitados: " + desinstalarTriggers(TRIGGER_MENSAJES));
}

function calcularMensajesDelDia_() {
  const props = PropertiesService.getScriptProperties();
  if (String(props.getProperty("MENSAJES_ACTIVOS") || "true").toLowerCase() === "false") {
    return "Mensajes apagados (MENSAJES_ACTIVOS=false): no se llamó a Gemini.";
  }
  const clave = props.getProperty("GEMINI_API_KEY");
  if (!clave) return "Falta la propiedad GEMINI_API_KEY: no se generó nada.";
  const modelos = String(props.getProperty("GEMINI_MODELO") || MSG_MODELOS_DEFAULT)
    .split(",").map(function (m) { return m.trim(); }).filter(function (m) { return m; });

  const ahora = new Date();
  const hoy = Utilities.formatDate(ahora, MSG_ZONA, "yyyy-MM-dd");
  const mesActual = hoy.substring(0, 7);
  const mesAnterior = mesAnteriorDe_(mesActual);

  const sheet = getUsersSheetSeeded();
  asegurarColumnasDeMensaje_(sheet);
  const filasUsuarios = sheet.getDataRange().getValues().slice(1).filter(function (r) { return r[0]; });
  const usuarios = filasUsuarios.map(function (r) {
    return {
      slotKey: String(r[0]),
      nombre: r[1] ? String(r[1]) : String(r[0]),
      anterior: r.length >= MSG_COL_MENSAJE && r[MSG_COL_MENSAJE - 1] ? String(r[MSG_COL_MENSAJE - 1]) : ""
    };
  });
  if (usuarios.length === 0) return "La hoja Usuarios está vacía.";

  const movimientos = [mesAnterior, mesActual]
    .map(function (mes) { return movimientosParaMensaje_(mes, usuarios, hoy); })
    .join("\n");
  // Los renglones empiezan con "hace" (días): se repiten aparte los recientes para que el modelo no
  // tenga que deducirlos y para que quede claro sobre qué puede preguntar.
  const recientes = movimientos.split("\n").filter(function (l) {
    return /^\d+\|/.test(l) && parseInt(l, 10) <= MSG_DIAS_RECIENTES;
  }).join("\n");

  const prompt = promptDeMensajes_(hoy, ahora, usuarios, movimientos, recientes);
  const claves = usuarios.map(function (u) { return u.slotKey; });
  const resultado = pedirConRespaldo_(clave, modelos, prompt, claves);
  const mensajes = resultado.mensajes;
  const modelo = resultado.modelo;

  // Solo se pisa el mensaje de quien recibió uno válido: si el modelo omite a alguien, le queda el
  // de ayer (que la app deja de mostrar porque su fecha ya no es la de hoy). La escritura (no la
  // llamada a Gemini, que tarda) va con el candado, igual que las de la app.
  const escritos = [];
  conCandado("generarMensajesDelDia", function () {
    const data = sheet.getDataRange().getValues();
    for (let i = 1; i < data.length; i++) {
      const slot = String(data[i][0] || "");
      const mensaje = limpiarMensaje_(mensajes[slot]);
      if (!slot || !mensaje) continue;
      sheet.getRange(i + 1, MSG_COL_MENSAJE).setValue(mensaje);
      sheet.getRange(i + 1, MSG_COL_FECHA).setNumberFormat("@").setValue(hoy);
      escritos.push(slot + ": " + mensaje);
    }
  });
  const intentos = resultado.fallos.length ? "\nModelos descartados:\n" + resultado.fallos.join("\n") : "";
  return "Mensajes del " + hoy + " (" + modelo + "):\n" + (escritos.join("\n") || "(ninguno válido)") + intentos;
}

/** "2026-01" -> "2025-12". */
function mesAnteriorDe_(mes) {
  const y = parseInt(mes.substring(0, 4), 10), m = parseInt(mes.substring(5, 7), 10);
  return m === 1 ? (y - 1) + "-12" : y + "-" + ("0" + (m - 1)).slice(-2);
}

/** Días entre dos fechas "yyyy-MM-dd" (hasta - desde). NaN si alguna no parsea. */
function diasEntre_(desde, hasta) {
  const a = String(desde).split("-").map(Number), b = String(hasta).split("-").map(Number);
  return Math.round((Date.UTC(b[0], b[1] - 1, b[2]) - Date.UTC(a[0], a[1] - 1, a[2])) / 86400000);
}

/** "2026-09-26" -> "sáb 26". */
function diaCorto_(fecha) {
  const p = String(fecha).split("-").map(Number);
  const dias = ["dom", "lun", "mar", "mié", "jue", "vie", "sáb"];
  return dias[new Date(Date.UTC(p[0], p[1] - 1, p[2])).getUTCDay()] + " " + ("0" + p[2]).slice(-2);
}

/**
 * Los gastos y aportes vivos de la hoja de [mes], uno por renglón y con lo mínimo:
 * "hace|día|quién|tipo|categoría|monto|descripción|evitable". "hace" son los días hasta [hoy]
 * (0 = hoy, 1 = ayer) y "quién" el nombre visible del dueño (o "ambos" en los comunes). Los montos
 * van redondeados: para comentar alcanza. Lo fechado en el futuro (si lo hubiera) no se manda.
 */
function movimientosParaMensaje_(mes, usuarios, hoy) {
  const nombre = nombreHojaDeMes(mes);
  const sheet = nombre ? SpreadsheetApp.getActiveSpreadsheet().getSheetByName(nombre) : null;
  if (!sheet) return "# " + mes + ": sin datos";
  const nombreDe = {};
  usuarios.forEach(function (u) { nombreDe[u.slotKey.toLowerCase()] = u.nombre; });

  const lineas = ["# " + mes];
  sheet.getDataRange().getValues().slice(1).forEach(function (row) {
    if (!row[COL.ID] || isEliminado(row[COL.ELIMINADO])) return;
    const tipo = String(row[COL.TIPO] || "").toLowerCase();
    if (tipo !== "gasto" && tipo !== "aporte") return;
    const categoria = String(row[COL.CATEGORIA] || "");
    if (esLegacySaldoInicial(row[COL.TIPO], categoria)) return;
    const comun = esVerdadero(row[COL.ES_COMUN]) || String(row[COL.PROPIETARIO] || "").toLowerCase() === AMBOS.toLowerCase();
    const dueno = comun ? "ambos" : (nombreDe[normalizarPropietario(row[COL.PROPIETARIO], row[COL.RESPONSABLE]).toLowerCase()] || String(row[COL.RESPONSABLE] || ""));
    const fecha = toDateTime(row[COL.FECHA]).substring(0, 10);
    const hace = diasEntre_(fecha, hoy);
    if (isNaN(hace) || hace < 0) return;
    const descripcion = String(row[COL.DESCRIPCION] || "").replace(/[|\n\r]+/g, " ").trim().substring(0, 40);
    lineas.push([hace, diaCorto_(fecha), dueno, tipo, categoria, Math.round(Number(row[COL.MONTO]) || 0), descripcion, esVerdadero(row[COL.EVITABLE]) ? "evitable" : ""].join("|"));
  });
  return lineas.join("\n");
}

/**
 * Formatos posibles del mensaje. El script le asigna uno al azar a cada persona cada día (distinto
 * entre ellas): librado a su criterio, el modelo se clavaba en exclamaciones sobre algo puntual.
 */
const MSG_FORMATOS = [
  "una PREGUNTA amistosa y curiosa sobre algo RECIENTE de sus movimientos, como un amigo que se interesa " +
    "(ej: \"¿Cómo andan los perritos?\", \"¿Cómo estuvo esa salida?\", \"¿Valió la pena esa pizza?\")",
  "un COMENTARIO simpático o gracioso sobre algo puntual y RECIENTE con nombre propio " +
    "(ej: \"Esa provoleta del sábado pintaba bárbara 🧀\", \"Debe haber estado bueno ese café Martínez\")",
  "una FRASE DE ÁNIMO corta y genérica, sin mencionar gastos, para arrancar el día " +
    "(ej: \"Que hoy te salga todo redondo\", \"Arrancá tranqui, que viene un lindo día\")",
  "una OBSERVACIÓN o BUENA NOTICIA sobre cómo viene su mes comparado con el anterior " +
    "(ej: \"Semana tranqui de gustos, bien ahí\", \"Vas mejor que en agosto con los antojos\")"
];

/** Un formato al azar por persona, sin repetir entre ellas mientras alcancen. */
function formatosDelDia_(usuarios) {
  const orden = MSG_FORMATOS.map(function (_, i) { return i; });
  for (let i = orden.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    const t = orden[i]; orden[i] = orden[j]; orden[j] = t;
  }
  return usuarios.map(function (u, k) { return MSG_FORMATOS[orden[k % orden.length]]; });
}

function promptDeMensajes_(hoy, ahora, usuarios, movimientos, recientes) {
  const dias = ["domingo", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado"];
  const diaSemana = dias[parseInt(Utilities.formatDate(ahora, MSG_ZONA, "u"), 10) % 7];
  const quienes = usuarios.map(function (u) { return '"' + u.slotKey + '" (' + u.nombre + ')'; }).join(" y ");
  const anteriores = usuarios
    .filter(function (u) { return u.anterior; })
    .map(function (u) { return "- " + u.nombre + ": " + u.anterior; }).join("\n");
  const formatos = formatosDelDia_(usuarios);
  const formatoDeCadaUno = usuarios
    .map(function (u, k) { return "- " + u.nombre + ": " + formatos[k] + "."; }).join("\n");
  return [
    "Sos el amigo buena onda de una app de finanzas que usa una pareja en Argentina.",
    "Hoy es " + diaSemana + " " + hoy + ". Abajo están sus gastos y aportes del mes pasado y del actual,",
    "un renglón por movimiento: hace (días)|día|de quién|tipo|categoría|monto en pesos|descripción|evitable.",
    "\"hace\" = cuántos días pasaron: 0 = hoy, 1 = ayer, 7 = hace una semana.",
    "\"ambos\" = gasto compartido. \"evitable\" = lo marcaron como gusto o lujo; sin marca = necesario.",
    "",
    "Escribí UN mensaje para cada una de estas personas: " + quienes + ".",
    "",
    "Formato de hoy para cada uno (respetalo):",
    formatoDeCadaUno,
    "",
    "Fechas, MUY importante: solo podés preguntar o comentar algo puntual (\"ese ramen\", \"esa salida\")",
    "si está en LO RECIENTE (hace " + MSG_DIAS_RECIENTES + " días o menos). Algo de hace más tiempo NO se",
    "pregunta ni se comenta como si hubiera sido ayer: como mucho entra en una observación del mes",
    "(\"este mes hubo varias salidas\") o directamente no se menciona. Si decís cuándo fue, que sea",
    "correcto (\"ayer\", \"el sábado\").",
    "",
    "LO RECIENTE (hace " + MSG_DIAS_RECIENTES + " días o menos):",
    recientes || "(no cargaron nada en los últimos días)",
    "",
    "Si el formato pide algo puntual y no hay nada reciente que encaje, cambiá a una frase de ánimo,",
    "a una pregunta que no dependa de un gasto (\"¿Cómo arrancó la semana?\") o a una observación del",
    "mes. Mejor algo simple y cálido que forzado.",
    "",
    "Qué conviene mirar, cuando el formato habla de sus movimientos: descripciones con nombre propio,",
    "gustos o evitables, algo nuevo respecto del mes anterior, un ingreso, sus mascotas o salidas.",
    "",
    "Qué NO comentar:",
    "- Gastos de rutina o necesarios, aunque se repitan muchísimo: transporte, servicios, supermercado,",
    "  verdulería, farmacia, alquiler, cosas de trabajo o de salud. No se pueden evitar y comentarlos",
    "  queda tonto. Que algo sea lo más frecuente NO lo hace interesante. (Preguntar cómo andan las",
    "  mascotas sí está bien; retar por lo que cuestan, no.)",
    "- Las advertencias en broma (\"cuidado con…\") son solo para gustos o evitables, nunca para",
    "  necesidades.",
    "- Sermones, retos, culpa, consejos genéricos de ahorro, montos o cifras exactas.",
    "- Datos del otro en el mensaje de cada uno.",
    "",
    "Ejemplos malos, NO los hagas: \"¡Cuidado con tanto transporte!\" (lo necesita para trabajar),",
    "\"¡Cuánto supermercado!\" (hay que comer), \"Gastaste mucho este mes\" (sermón genérico).",
    "",
    "Forma: máximo 60 caracteres, español rioplatense, de vos, a lo sumo un emoji. Simpático y",
    "amistoso, como un amigo que te conoce. Variá la puntuación: no todo con signos de exclamación.",
    anteriores ? "No repitas la idea ni el tipo de frase de los mensajes de ayer:\n" + anteriores : "",
    "",
    "Respondé SOLO con JSON, sin texto alrededor, con las claves exactas: {" +
      usuarios.map(function (u) { return '"' + u.slotKey + '": "..."'; }).join(", ") + "}",
    "",
    movimientos
  ].join("\n");
}

/**
 * Prueba los [modelos] en orden hasta que uno devuelva al menos un mensaje válido para alguna de las
 * [claves]. Devuelve {mensajes, modelo, fallos}; tira solo si fallaron todos (y ahí queda el de ayer).
 */
function pedirConRespaldo_(clave, modelos, prompt, claves) {
  const fallos = [];
  for (let i = 0; i < modelos.length; i++) {
    try {
      const mensajes = pedirMensajesAGemini_(clave, modelos[i], prompt);
      const utiles = claves.filter(function (k) { return limpiarMensaje_(mensajes[k]); });
      if (utiles.length > 0) return { mensajes: mensajes, modelo: modelos[i], fallos: fallos };
      fallos.push(modelos[i] + ": respondió sin mensajes válidos");
    } catch (err) {
      fallos.push(modelos[i] + ": " + String(err).substring(0, 200));
    }
  }
  throw new Error("Ningún modelo sirvió.\n" + fallos.join("\n"));
}

/**
 * Configuración de generación según la familia del modelo.
 *
 * El "thinking" cuenta dentro de maxOutputTokens: con un tope chico, un modelo que piensa se queda
 * sin lugar y devuelve el JSON cortado. En la 2.5 se apaga del todo (thinkingBudget 0); en la 3.x se
 * pide lo mínimo (thinkingLevel "low") y además se deja margen de sobra. [conThinking] = false manda
 * la config sin thinkingConfig, para modelos que no aceptan ese campo.
 */
function configDeGeneracion_(modelo, conThinking) {
  const es25 = modelo.indexOf("gemini-2.5") === 0;
  const config = { responseMimeType: "application/json", temperature: 1.0, maxOutputTokens: es25 ? 400 : 2048 };
  if (conThinking) config.thinkingConfig = es25 ? { thinkingBudget: 0 } : { thinkingLevel: "low" };
  return config;
}

/**
 * Llama a [modelo] y devuelve {slotKey: mensaje}. Tira si la respuesta no sirve. Si el modelo rechaza
 * la thinkingConfig (400 que la menciona), reintenta una vez sin ella antes de darlo por perdido.
 */
function pedirMensajesAGemini_(clave, modelo, prompt) {
  let resp = llamarGemini_(clave, modelo, prompt, true);
  if (resp.getResponseCode() === 400 && /thinking/i.test(resp.getContentText())) {
    resp = llamarGemini_(clave, modelo, prompt, false);
  }
  const codigo = resp.getResponseCode();
  if (codigo !== 200) throw new Error("respondió " + codigo + ": " + resp.getContentText().substring(0, 200));

  const cuerpo = JSON.parse(resp.getContentText());
  const candidato = cuerpo.candidates && cuerpo.candidates[0];
  const partes = candidato && candidato.content && candidato.content.parts;
  // Las partes de "pensamiento" (thought: true) no son la respuesta.
  const texto = partes ? partes.filter(function (p) { return !p.thought; }).map(function (p) { return p.text || ""; }).join("") : "";
  if (!texto) throw new Error("respuesta vacía (finishReason: " + (candidato && candidato.finishReason) + ")");
  const json = texto.replace(/^[^{]*/, "").replace(/[^}]*$/, ""); // por si viene envuelto en ```json
  return JSON.parse(json);
}

function llamarGemini_(clave, modelo, prompt, conThinking) {
  return UrlFetchApp.fetch(
    "https://generativelanguage.googleapis.com/v1beta/models/" + encodeURIComponent(modelo) + ":generateContent",
    {
      method: "post",
      contentType: "application/json",
      headers: { "x-goog-api-key": clave },
      payload: JSON.stringify({
        contents: [{ role: "user", parts: [{ text: prompt }] }],
        generationConfig: configDeGeneracion_(modelo, conThinking)
      }),
      muteHttpExceptions: true
    }
  );
}

/** Recorta y sanea lo que devolvió el modelo. "" si no sirve. */
function limpiarMensaje_(v) {
  if (typeof v !== "string") return "";
  let t = v.replace(/[\r\n]+/g, " ").replace(/^["'«“\s]+|["'»”\s]+$/g, "").trim();
  if (t.length > MSG_MAX_CARACTERES) {
    t = t.substring(0, MSG_MAX_CARACTERES);
    t = t.substring(0, t.lastIndexOf(" ") > 40 ? t.lastIndexOf(" ") : MSG_MAX_CARACTERES).trim() + "…";
  }
  return t;
}

/** Encabezados de E/F en la hoja Usuarios (y F como texto, para que la fecha no se vuelva Date). */
function asegurarColumnasDeMensaje_(sheet) {
  if (sheet.getMaxColumns() < MSG_COL_FECHA) sheet.insertColumnsAfter(sheet.getMaxColumns(), MSG_COL_FECHA - sheet.getMaxColumns());
  const encabezados = [[MSG_COL_MENSAJE, "Mensaje"], [MSG_COL_FECHA, "Fecha mensaje"]];
  encabezados.forEach(function (e) { asegurarEncabezado(sheet, e[0], e[1]); });
  sheet.getRange(1, MSG_COL_FECHA, sheet.getMaxRows(), 1).setNumberFormat("@");
}
