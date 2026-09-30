/**
 * Script de Google Apps Script para la sincronización de la App de Finanzas Personales.
 *
 * Versión: 7.7 (Cambio de dinero, pago de deuda, gastos evitables, edición de movimientos)
 * @description Este script requiere acceso a Google Drive para guardar los tickets.
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
 * Novedades v7.7.1:
 *  - Mensaje del día con Gemini: generarMensajesDelDia() (trigger ~2 AM) escribe un mensaje por
 *    persona en la hoja "Usuarios" (E/F) y GET_USERS lo devuelve. Requiere la propiedad del script
 *    GEMINI_API_KEY; ver el bloque "MENSAJE DEL DÍA" al final del archivo.
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

/** True si el valor de una celda "eliminado" representa verdadero. */
function isEliminado(v) {
  return v === true || v === 'true' || v === 'VERDADERO';
}

/** True si una celda booleana (Es Común, Evitable) representa verdadero. Vacío = false. */
function esVerdadero(v) {
  return v === true || v === 'true' || v === 'TRUE' || v === 'VERDADERO';
}

/** Encabezados de una hoja de movimientos (A..O). */
const MOVEMENT_HEADERS = ["ID", "Fecha", "Monto", "Tipo", "Categoría", "Responsable", "Es Común", "Descripción", "Metodo Pago", "Ticket URL", "Eliminado", "Propietario", "Plan ID", "Cuota N°", "Evitable"];
const MOVEMENT_COLS = MOVEMENT_HEADERS.length;

/** Devuelve la hoja de movimientos [nombre], creándola con sus encabezados si no existe. */
function hojaDeMovimientos(ss, nombre) {
  let sheet = ss.getSheetByName(nombre);
  if (!sheet) {
    sheet = ss.insertSheet(nombre);
    sheet.appendRow(MOVEMENT_HEADERS);
    sheet.getRange(1, 1, 1, MOVEMENT_COLS).setFontWeight("bold").setBackground("#e2e8f0");
    sheet.setFrozenRows(1);
  }
  asegurarColumnaEvitable(sheet);
  return sheet;
}

/** Escribe el encabezado de la columna O en hojas de mes anteriores a la v7.7. */
function asegurarColumnaEvitable(sheet) {
  if (sheet.getMaxColumns() < MOVEMENT_COLS) {
    sheet.insertColumnsAfter(sheet.getMaxColumns(), MOVEMENT_COLS - sheet.getMaxColumns());
  }
  const celda = sheet.getRange(1, MOVEMENT_COLS);
  if (!celda.getValue()) {
    celda.setValue("Evitable").setFontWeight("bold").setBackground("#e2e8f0");
  }
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
  const hojas = ss.getSheets().filter(function (sh) {
    return sh.getName() !== PLANS_SHEET && sh.getName() !== USERS_SHEET;
  });
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
  const action = (e && e.parameter && e.parameter.action) ? e.parameter.action : null;

  // --- Endpoint del módulo de cuotas ---
  if (action === 'GET_PLANS') {
    return getPlans(e);
  }

  // --- Endpoint de usuarios parametrizables ---
  if (action === 'GET_USERS') {
    return getUsers(e);
  }

  // --- Movimientos (comportamiento por defecto) ---
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const sheets = ss.getSheets();
  let allData = [];

  sheets.forEach(sheet => {
    // ⚠️ Las hojas de planes y usuarios NO contienen movimientos: hay que saltarlas.
    if (sheet.getName() === PLANS_SHEET || sheet.getName() === USERS_SHEET) return;

    const data = sheet.getDataRange().getValues();
    if (data.length > 1) {
      const rows = data.slice(1).map(row => {
        // Ignorar si está marcado como eliminado (Columna K / index 10)
        if (isEliminado(row[10])) return null;

        return {
          id: row[0] ? row[0].toString() : "",
          fecha: row[1] ? row[1].toString() : "",
          monto: Number(row[2]) || 0,
          tipo: row[3] ? row[3].toString() : "",
          categoria: row[4] ? row[4].toString() : "",
          responsable: row[5] ? row[5].toString() : "",
          esComun: row[6] === true || row[6] === 'true' || row[6] === 'VERDADERO',
          descripcion: row[7] ? row[7].toString() : "",
          metodoPago: row[8] ? row[8].toString() : "Billetera Virtual",
          ticketUrl: row[9] ? row[9].toString() : "",
          eliminado: false,
          propietario: row[11] ? row[11].toString() : (row[5] ? row[5].toString() : ""),
          planId: row[12] ? row[12].toString() : "",
          cuotaNumero: Number(row[13]) || 0,
          evitable: esVerdadero(row[14])
        };
      }).filter(r => r !== null);
      allData = allData.concat(rows);
    }
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
  ss.getSheets().forEach(sheet => {
    if (sheet.getName() === PLANS_SHEET || sheet.getName() === USERS_SHEET) return; // solo movimientos
    const data = sheet.getDataRange().getValues();
    for (let i = 1; i < data.length; i++) {
      const row = data[i];
      if (isEliminado(row[10])) continue;
      const planId = row[12] ? row[12].toString() : "";
      if (!planId) continue;
      const cuotaNum = Number(row[13]) || 0;
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
      if (isEliminado(row[9])) continue; // Columna J / index 9
      const id = row[0] ? row[0].toString() : "";
      if (!id) continue;

      const cantidadCuotas = Number(row[4]) || 0;
      // "Pagada" = tiene movimiento vivo O figura en el snapshot del corte (columna K). Sin la
      // unión, purgar hojas viejas devolvía a pendiente cuotas ya pagadas.
      const previas = parsePrevias(row.length > 10 ? row[10] : "");
      const pagadasSet = {};
      if (paidMap[id]) Object.keys(paidMap[id]).forEach(function (k) { pagadasSet[Number(k)] = true; });
      previas.forEach(function (k) { if (k >= 1 && k <= cantidadCuotas) pagadasSet[k] = true; });
      const pagadas = Object.keys(pagadasSet).length;
      const completo = cantidadCuotas > 0 && pagadas >= cantidadCuotas;

      if (filtro === 'pagos' && !completo) continue;
      if (filtro !== 'pagos' && completo) continue; // "pendientes"

      plans.push({
        id: id,
        fechaCreacion: toDateTime(row[1]),
        descripcion: row[2] ? row[2].toString() : "",
        montoPorCuota: Number(row[3]) || 0,
        cantidadCuotas: cantidadCuotas,
        fechaPrimeraCuota: toYearMonth(row[5]),
        propietario: row[6] ? row[6].toString() : "",
        categoria: row[7] ? row[7].toString() : "",
        tarjeta: row[8] ? row[8].toString() : "",
        eliminado: false,
        cuotasPagadasPrevias: previas,
        evitable: esVerdadero(row.length > 11 ? row[11] : "")
      });
    }
  }

  // 3) Ordenar por fechaCreacion descendente (ISO -> orden lexicográfico).
  plans.sort((a, b) => (a.fechaCreacion < b.fechaCreacion) ? 1 : ((a.fechaCreacion > b.fechaCreacion) ? -1 : 0));

  return jsonOutput({ status: "SUCCESS", plans: plans });
}

/** Segundos que una escritura espera el candado antes de rendirse. */
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
          mov.metodoPago || "Billetera Virtual",
          mov.ticketUrl || ticketUrlAnterior || "",
          false, // Columna Eliminado
          mov.propietario || mov.responsable,
          mov.planId || "",          // Columna M
          mov.cuotaNumero || 0,      // Columna N
          mov.evitable === true      // Columna O
        ];
      };

      // PUT = upsert por id. Se busca en TODAS las hojas y no solo en la del mes de la fecha: una
      // edición puede haber cambiado la fecha de mes, y buscando solo en la hoja nueva la fila vieja
      // quedaba viva y el movimiento contaba dos veces.
      if (action === 'PUT') {
        const existente = buscarMovimiento(ss, mov.id, sheet);
        if (existente) {
          const ticketAnterior = existente.valores.length > 9 ? existente.valores[9] : "";
          if (existente.sheet.getName() === sheet.getName()) {
            existente.sheet.getRange(existente.fila, 1, 1, MOVEMENT_COLS).setValues([filaDe(ticketAnterior)]);
            return jsonOutput({ status: "SUCCESS", message: "Actualizado OK" });
          }
          // Cambió de mes: se borra físicamente la fila vieja (una baja lógica dejaría dos filas
          // con el mismo id, y el listado de la app usa el id como clave) y se escribe en la nueva.
          existente.sheet.deleteRow(existente.fila);
          sheet.appendRow(filaDe(ticketAnterior));
          return jsonOutput({ status: "SUCCESS", message: "Movido de " + existente.sheet.getName() + " a " + sheet.getName() });
        }
      }

      sheet.appendRow(filaDe(""));

      return jsonOutput({ status: "SUCCESS", message: imgStatus });
    }
  } catch (err) {
    return jsonOutput({ status: "ERROR", message: err.toString(), imgStatus: imgStatus });
  }
}

/**
 * Alta (POST) o edición (PUT) de un plan de cuotas en la hoja "Planes".
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
    sheet = ss.insertSheet(PLANS_SHEET);
    sheet.appendRow(["ID", "Fecha Creación", "Descripción", "Monto Por Cuota", "Cantidad Cuotas", "Primera Cuota", "Propietario", "Categoría", "Tarjeta", "Eliminado", COLUMNA_PREVIAS, COLUMNA_EVITABLE_PLAN]);
    sheet.getRange(1, 1, 1, 12).setFontWeight("bold").setBackground("#e2e8f0");
    sheet.setFrozenRows(1);
    // Forzar texto en las columnas de fecha para preservar el formato "yyyy-MM"/"yyyy-MM-dd HH:mm".
    sheet.getRange("B:B").setNumberFormat("@");
    sheet.getRange("F:F").setNumberFormat("@");
  }

  asegurarColumnaPrevias(sheet);
  asegurarColumnaEvitablePlan(sheet);
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

  if (action === 'PUT') {
    const data = sheet.getDataRange().getValues();
    for (let i = 1; i < data.length; i++) {
      if (data[i][0] == plan.id) {
        // La columna K se escribe SIEMPRE por unión con lo que ya había: un cliente con el plan
        // cacheado de antes del corte mandaría una lista más corta y, reemplazando, borraría el
        // registro de cuotas pagadas cuyos movimientos ya no existen.
        const previas = unirPrevias(parsePrevias(data[i].length > 10 ? data[i][10] : ""), delPayload);
        sheet.getRange(i + 1, 1, 1, 12).setValues([rowValues(previas)]);
        return jsonOutput({ status: "SUCCESS", message: "Plan actualizado OK" });
      }
    }
    // Si no se encontró, cae a append (alta).
  }

  sheet.appendRow(rowValues(delPayload));
  return jsonOutput({ status: "SUCCESS", message: "Plan creado OK" });
}

/** Encabezado de la columna K de la hoja "Planes" (snapshot de cuotas del corte de mes). */
const COLUMNA_PREVIAS = "Cuotas Pagadas Previas";

/** Encabezado de la columna L de la hoja "Planes" (compra evitable; vacío = no evitable). */
const COLUMNA_EVITABLE_PLAN = "Evitable";

/** Escribe el encabezado de la columna L si la hoja "Planes" es anterior a la v7.7. */
function asegurarColumnaEvitablePlan(sheet) {
  if (sheet.getMaxColumns() < 12) sheet.insertColumnsAfter(sheet.getMaxColumns(), 12 - sheet.getMaxColumns());
  const celda = sheet.getRange(1, 12);
  if (!celda.getValue()) {
    celda.setValue(COLUMNA_EVITABLE_PLAN).setFontWeight("bold").setBackground("#e2e8f0");
  }
}

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
  // Una hoja creada a mano puede tener menos de 11 columnas; getRange(1, 11) tiraría excepción y se
  // llevaría puesto el guardado del plan entero.
  if (sheet.getMaxColumns() < 11) sheet.insertColumnsAfter(sheet.getMaxColumns(), 11 - sheet.getMaxColumns());

  // La columna va a TEXTO, igual que las de fecha. Sin esto Sheets interpreta el contenido como
  // número: con una sola cuota es inofensivo ("1" -> 1, que `parsePrevias` vuelve a leer bien), pero
  // en una planilla con coma decimal "1,2" se convierte en el número 1.2 y el snapshot de ese plan
  // se pierde en silencio — justo el caso de un plan con dos cuotas ya pagadas antes del corte.
  sheet.getRange("K:K").setNumberFormat("@");

  const celda = sheet.getRange(1, 11);
  if (!celda.getValue()) {
    celda.setValue(COLUMNA_PREVIAS).setFontWeight("bold").setBackground("#e2e8f0");
  }
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
    const id = data[i][0] ? data[i][0].toString() : "";
    if (!id || !previas[id]) continue;
    const actual = parsePrevias(data[i].length > 10 ? data[i][10] : "");
    const union = unirPrevias(actual, previas[id]);
    if (union.length !== actual.length) {
      sheet.getRange(i + 1, 11).setValue(formatPrevias(union));
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
    if (data[i][0] == id) {
      sheet.getRange(i + 1, 10).setValue(true); // Columna J
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
    sheet = ss.insertSheet(USERS_SHEET);
    sheet.appendRow(["slotKey", "Nombre", "ColorId", "Orden"]);
    sheet.getRange(1, 1, 1, 4).setFontWeight("bold").setBackground("#e2e8f0");
    sheet.setFrozenRows(1);
    sheet.getRange("A:A").setNumberFormat("@");
    // Seed: slotKey = nombre legacy y colores actuales -> retrocompatible con los datos existentes.
    sheet.appendRow(["Santiago", "Santiago", "green", 0]);
    sheet.appendRow(["Rocío", "Rocío", "blue", 1]);
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
        // Mensaje del día (v7.7.1, columnas E/F): lo escribe generarMensajesDelDia().
        mensaje: row.length > 4 && row[4] ? row[4].toString() : "",
        mensajeFecha: row.length > 5 && row[5]
          ? (row[5] instanceof Date ? Utilities.formatDate(row[5], "America/Argentina/Buenos_Aires", "yyyy-MM-dd") : row[5].toString())
          : ""
      });
    }
  }
  return jsonOutput({ status: "SUCCESS", users: users });
}

/**
 * Edita (PUT) un usuario: busca por slotKey (col A, inmutable) y actualiza nombre (B) y colorId (C).
 * Siembra la hoja con los dos slots legacy si faltaba, de modo que el slotKey siempre se encuentre.
 * No existe alta/baja de usuarios: siempre son exactamente dos filas fijas.
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
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const sheets = ss.getSheets();

  for (let sheet of sheets) {
    // Planes y usuarios se gestionan por sus propios endpoints, no contienen movimientos.
    if (sheet.getName() === PLANS_SHEET || sheet.getName() === USERS_SHEET) continue;
    const data = sheet.getDataRange().getValues();
    for (let i = 1; i < data.length; i++) {
      if (data[i][0] == id) {
        // Marcar columna K (index 10) como TRUE
        sheet.getRange(i + 1, 11).setValue(true);
        return jsonOutput({ status: "SUCCESS", message: "Eliminado (baja lógica) OK" });
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
const SANTIAGO = "Santiago";
const ROCIO = "Rocío";
const AMBOS = "Ambos";
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
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const out = [];
  ss.getSheets().forEach(function (sheet) {
    const nombre = sheet.getName();
    if (nombre === PLANS_SHEET || nombre === USERS_SHEET) return;
    const data = sheet.getDataRange().getValues();
    for (let i = 1; i < data.length; i++) {
      const row = data[i];
      if (!row[0] && !row[1]) continue;          // fila vacía
      if (isEliminado(row[10])) continue;
      out.push({
        hoja: nombre,
        fila: i + 1,
        id: row[0] ? row[0].toString() : "",
        fecha: toDateTime(row[1]),
        mes: toYearMonth(row[1]).substring(0, 7),
        monto: Number(row[2]) || 0,
        tipo: row[3] ? row[3].toString() : "",
        categoria: row[4] ? row[4].toString() : "",
        responsable: row[5] ? row[5].toString() : "",
        esComun: row[6] === true || row[6] === "true" || row[6] === "VERDADERO",
        metodoPago: row[8] ? row[8].toString() : VIRTUAL,
        propietario: row[11] ? row[11].toString() : (row[5] ? row[5].toString() : ""),
        planId: row[12] ? row[12].toString() : "",
        cuotaNumero: Number(row[13]) || 0,
        evitable: esVerdadero(row[14])
      });
    }
  });
  return out;
}

/**
 * Aplica una lista de movimientos sobre un estado y devuelve el estado resultante. Port exacto de
 * `AccountingEngine.compute` (app/src/main/java/com/example/ui/AccountingEngine.kt) restringido al
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

  const filas = [
    [SANTIAGO, SANTIAGO, EFECTIVO, estado.sEfec],
    [SANTIAGO, SANTIAGO, VIRTUAL, estado.sVirt - sVirtDeRocio],
    [ROCIO, ROCIO, EFECTIVO, estado.rEfec],
    [ROCIO, ROCIO, VIRTUAL, estado.rVirt - rVirtDeSantiago]
  ];
  if (sVirtDeRocio !== 0) filas.push([SANTIAGO, ROCIO, VIRTUAL, sVirtDeRocio]);
  if (rVirtDeSantiago !== 0) filas.push([ROCIO, SANTIAGO, VIRTUAL, rVirtDeSantiago]);

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
      descripcion: "Saldo inicial " + mes + " - " + responsable + " - " + metodo + deOtro,
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
    if (!esFilaApertura(row[3])) continue;
    filas.push({
      responsable: row[5] ? row[5].toString() : "",
      propietario: row[11] ? row[11].toString() : "",
      metodoPago: row[8] ? row[8].toString() : VIRTUAL,
      monto: Number(row[2]) || 0
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
        describirPersona("Santiago", apertura.sEfec, apertura.sVirt, apertura.netSR) + "\n" +
        describirPersona("Rocío", apertura.rEfec, apertura.rVirt, -apertura.netSR) + "\n";
    } else {
      txt += "  [ARRANCA EL " + mes + "-01]  -> " + filasDeApertura(mes, apertura).length + " filas de apertura\n" +
        describirPersona("Santiago", apertura.sEfec, apertura.sVirt, apertura.netSR) + "\n" +
        describirPersona("Rocío", apertura.rEfec, apertura.rVirt, -apertura.netSR) + "\n";
    }
    txt += "  [CIERRA]" + (ultimo ? "  <<< ESTADO DE HOY >>>" : "") + "\n" +
      describirPersona("Santiago", cierre.sEfec, cierre.sVirt, cierre.netSR) + "\n" +
      describirPersona("Rocío", cierre.rEfec, cierre.rVirt, -cierre.netSR);
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
  desinstalarTriggerDeAperturas();
  ScriptApp.newTrigger(TRIGGER_APERTURAS).timeBased().atHour(4).everyDays(1).create();
  Logger.log("Trigger diario instalado (~4 AM): " + TRIGGER_APERTURAS + "().");
  Logger.log("Verificalo en el editor, panel izquierdo, ícono del reloj (Activadores).");
}

/** Quita el trigger diario. El saldo inicial vuelve a depender del botón de Ajustes. */
function desinstalarTriggerDeAperturas() {
  let n = 0;
  ScriptApp.getProjectTriggers().forEach(function (t) {
    if (t.getHandlerFunction() === TRIGGER_APERTURAS) { ScriptApp.deleteTrigger(t); n++; }
  });
  Logger.log("Triggers quitados: " + n);
}

/**
 * Lo que corre el trigger. Recalcula TODAS las aperturas (no solo la del mes nuevo) y se asegura de
 * que el mes calendario actual tenga la suya, creando la hoja si hace falta.
 *
 * Recalcular todo a diario en vez de escribir una sola vez el día 1 es a propósito: los movimientos
 * se cargan con atraso. Si el 3 de septiembre agregás un gasto con fecha 31 de agosto, la apertura
 * de septiembre queda vieja; al día siguiente el trigger la corrige sola. Escribir una única vez
 * dejaría ese error congelado para siempre.
 */
function actualizarAperturas() {
  const movs = leerTodosLosMovimientos();
  const aperturas = calcularAperturasPorMes(movs);
  let total = 0;
  Object.keys(aperturas).sort().forEach(function (mes) {
    total += escribirAperturaDeMes(mes, aperturas[mes]);
  });

  // El mes calendario actual puede no tener movimientos todavía (típico los primeros días).
  // Le escribimos igual su apertura para que la hoja nazca con el arrastre puesto.
  const mesActual = Utilities.formatDate(new Date(), Session.getScriptTimeZone(), "yyyy-MM");
  if (!aperturas[mesActual] && !filasAperturaEscritasDe(movs, mesActual).length) {
    const previos = movs.filter(function (m) { return m.mes && m.mes < mesActual; });
    if (previos.length) {
      total += escribirAperturaDeMes(mesActual, calcularAperturaDe(previos, mesActual));
    }
  }

  Logger.log("Aperturas actualizadas: " + total + " filas.");
  return total;
}

/** PASO 2 - Escribe/actualiza las filas de apertura de TODOS los meses (menos el primero). */
function migrarAperturas() {
  const aperturas = calcularAperturasPorMes(leerTodosLosMovimientos());
  let total = 0;
  Object.keys(aperturas).sort().forEach(function (mes) {
    total += escribirAperturaDeMes(mes, aperturas[mes]);
  });
  Logger.log("Listo: " + total + " filas de apertura escritas/actualizadas.");
  Logger.log("Revisá los saldos en la app y después corré borrarLegacySaldoInicial().");
}

/**
 * Recalcula y reescribe la apertura de UN mes puntual. Ej: escribirApertura("2026-09").
 *
 * Resuelve el arrastre igual que `AccountingEngine.openingFor`: se apoya en la apertura escrita más
 * reciente que sea anterior al mes pedido y replaya desde ahí. Por eso sigue funcionando después de
 * archivar hojas viejas (replayar desde cero daría un patrimonio truncado).
 */
function escribirApertura(mes) {
  const previos = leerTodosLosMovimientos().filter(function (m) { return m.mes && m.mes < mes; });
  const estado = calcularAperturaDe(previos, mes);
  const n = escribirAperturaDeMes(mes, estado);
  Logger.log("Apertura de " + mes + ": " + n + " filas.");
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
    if (data[i][0]) filaPorId[data[i][0].toString()] = i + 1;
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
      f.descripcion, f.metodoPago, "", false, f.propietario, "", 0
    ]];
    const existente = filaPorId[f.id];
    if (existente) {
      sheet.getRange(existente, 2).setNumberFormat("@");
      sheet.getRange(existente, 1, 1, 14).setValues(valores);
    } else {
      sheet.appendRow(valores[0]);
      // La fecha va como texto para que Sheets no la convierta en Date.
      sheet.getRange(sheet.getLastRow(), 2).setNumberFormat("@").setValue(f.fecha);
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
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  let borradas = 0;
  ss.getSheets().forEach(function (sheet) {
    const nombre = sheet.getName();
    if (nombre === PLANS_SHEET || nombre === USERS_SHEET) return;
    const data = sheet.getDataRange().getValues();
    // De abajo hacia arriba: borrar una fila corre los índices de las de abajo.
    for (let i = data.length - 1; i >= 1; i--) {
      if (esLegacySaldoInicial(data[i][3], data[i][4])) {
        sheet.deleteRow(i + 1);
        borradas++;
      }
    }
  });
  Logger.log("Filas legacy de 'Saldo inicial' borradas: " + borradas);
}

/**
 * OPCIONAL (v7.7) - Renombra la categoría "Sueldo" a "Ingreso" en los aportes de todas las hojas.
 * No hace falta: la app ya trata "Sueldo" como "Ingreso". Es solo para dejar la planilla prolija.
 * Idempotente.
 */
function renombrarSueldoAIngreso() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  let cambiadas = 0;
  ss.getSheets().forEach(function (sheet) {
    const nombre = sheet.getName();
    if (nombre === PLANS_SHEET || nombre === USERS_SHEET) return;
    const data = sheet.getDataRange().getValues();
    for (let i = 1; i < data.length; i++) {
      const tipo = String(data[i][3] || "").toLowerCase();
      const categoria = String(data[i][4] || "").trim().toLowerCase();
      if (tipo === "aporte" && categoria === "sueldo") {
        sheet.getRange(i + 1, 5).setValue("Ingreso");
        cambiadas++;
      }
    }
  });
  Logger.log("Aportes renombrados de Sueldo a Ingreso: " + cambiadas);
}

// =================================================================================================
// MENSAJE DEL DÍA (v7.7.1)
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

const MSG_ZONA = "America/Argentina/Buenos_Aires";
const MSG_COL_MENSAJE = 5;        // E
const MSG_COL_FECHA = 6;          // F
const MSG_MAX_CARACTERES = 90;    // tope duro; al modelo se le piden <= 60
const TRIGGER_MENSAJES = "generarMensajesDelDia";

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
  desinstalarTriggerDeMensajes();
  ScriptApp.newTrigger(TRIGGER_MENSAJES).timeBased().atHour(2).everyDays(1).inTimezone(MSG_ZONA).create();
  Logger.log("Trigger diario instalado (~2 AM Argentina): " + TRIGGER_MENSAJES + "().");
}

function desinstalarTriggerDeMensajes() {
  let n = 0;
  ScriptApp.getProjectTriggers().forEach(function (t) {
    if (t.getHandlerFunction() === TRIGGER_MENSAJES) { ScriptApp.deleteTrigger(t); n++; }
  });
  Logger.log("Triggers de mensajes quitados: " + n);
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
    .map(function (mes) { return movimientosParaMensaje_(mes, usuarios); })
    .join("\n");

  const prompt = promptDeMensajes_(hoy, ahora, usuarios, movimientos);
  const claves = usuarios.map(function (u) { return u.slotKey; });
  const resultado = pedirConRespaldo_(clave, modelos, prompt, claves);
  const mensajes = resultado.mensajes;
  const modelo = resultado.modelo;

  // Solo se pisa el mensaje de quien recibió uno válido: si el modelo omite a alguien, le queda el
  // de ayer (que la app deja de mostrar porque su fecha ya no es la de hoy).
  const data = sheet.getDataRange().getValues();
  const escritos = [];
  for (let i = 1; i < data.length; i++) {
    const slot = String(data[i][0] || "");
    const texto = limpiarMensaje_(mensajes[slot]);
    if (!slot || !texto) continue;
    sheet.getRange(i + 1, MSG_COL_MENSAJE).setValue(texto);
    sheet.getRange(i + 1, MSG_COL_FECHA).setNumberFormat("@").setValue(hoy);
    escritos.push(slot + ": " + texto);
  }
  const intentos = resultado.fallos.length ? "\nModelos descartados:\n" + resultado.fallos.join("\n") : "";
  return "Mensajes del " + hoy + " (" + modelo + "):\n" + (escritos.join("\n") || "(ninguno válido)") + intentos;
}

/** "2026-01" -> "2025-12". */
function mesAnteriorDe_(mes) {
  const y = parseInt(mes.substring(0, 4), 10), m = parseInt(mes.substring(5, 7), 10);
  return m === 1 ? (y - 1) + "-12" : y + "-" + ("0" + (m - 1)).slice(-2);
}

/**
 * Los gastos y aportes vivos de la hoja de [mes], uno por renglón y con lo mínimo:
 * "dd|quién|tipo|categoría|monto|descripción|evitable". "quién" es el nombre visible del dueño
 * (o "ambos" en los comunes). Los montos van redondeados: para comentar alcanza.
 */
function movimientosParaMensaje_(mes, usuarios) {
  const nombre = nombreHojaDeMes(mes);
  const sheet = nombre ? SpreadsheetApp.getActiveSpreadsheet().getSheetByName(nombre) : null;
  if (!sheet) return "# " + mes + ": sin datos";
  const nombreDe = {};
  usuarios.forEach(function (u) { nombreDe[u.slotKey.toLowerCase()] = u.nombre; });

  const lineas = ["# " + mes];
  sheet.getDataRange().getValues().slice(1).forEach(function (row) {
    if (!row[0] || isEliminado(row[10])) return;
    const tipo = String(row[3] || "").toLowerCase();
    if (tipo !== "gasto" && tipo !== "aporte") return;
    const categoria = String(row[4] || "");
    if (esLegacySaldoInicial(row[3], categoria)) return;
    const comun = esVerdadero(row[6]) || String(row[11] || "").toLowerCase() === "ambos";
    const dueno = comun ? "ambos" : (nombreDe[normalizarPropietario(row[11], row[5]).toLowerCase()] || String(row[5] || ""));
    const dia = toDateTime(row[1]).substring(8, 10);
    const descripcion = String(row[7] || "").replace(/[|\n\r]+/g, " ").trim().substring(0, 40);
    lineas.push([dia, dueno, tipo, categoria, Math.round(Number(row[2]) || 0), descripcion, esVerdadero(row[14]) ? "evitable" : ""].join("|"));
  });
  return lineas.join("\n");
}

function promptDeMensajes_(hoy, ahora, usuarios, movimientos) {
  const dias = ["domingo", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado"];
  const diaSemana = dias[parseInt(Utilities.formatDate(ahora, MSG_ZONA, "u"), 10) % 7];
  const quienes = usuarios.map(function (u) { return '"' + u.slotKey + '" (' + u.nombre + ')'; }).join(" y ");
  const anteriores = usuarios
    .filter(function (u) { return u.anterior; })
    .map(function (u) { return "- " + u.nombre + ": " + u.anterior; }).join("\n");
  return [
    "Sos el amigo buena onda de una app de finanzas que usa una pareja en Argentina.",
    "Hoy es " + diaSemana + " " + hoy + ". Abajo están sus gastos y aportes del mes pasado y del actual,",
    "un renglón por movimiento: día|de quién|tipo|categoría|monto en pesos|descripción|evitable.",
    "\"ambos\" = gasto compartido. \"evitable\" = lo marcaron como gusto o lujo; sin marca = necesario.",
    "",
    "Escribí UN mensaje para cada una de estas personas: " + quienes + ".",
    "",
    "Qué comentar: elegí UNA sola cosa, la más interesante de cada persona, preferentemente de los",
    "últimos días. En este orden de preferencia:",
    "1. Algo puntual con nombre propio en la descripción, sobre todo gustos o compras poco habituales",
    "   (un bar, un antojo, un regalo, un hobby, una salida).",
    "2. Una novedad: algo que no aparece el mes anterior, o un día fuera de lo común.",
    "3. Una buena noticia: un ingreso, menos gustos que el mes pasado, unos días tranquilos.",
    "",
    "Qué NO comentar:",
    "- Gastos de rutina o necesarios, aunque se repitan muchísimo: transporte, servicios, supermercado,",
    "  verdulería, farmacia, animales, alquiler, cosas de trabajo o de salud. No se pueden evitar y",
    "  comentarlos queda tonto. Que algo sea lo más frecuente NO lo hace interesante.",
    "- Las advertencias en broma (\"¡cuidado con…!\") son solo para gustos o evitables, nunca para",
    "  necesidades.",
    "- Sermones, retos, culpa, consejos genéricos de ahorro, montos o cifras exactas.",
    "- Datos del otro en el mensaje de cada uno.",
    "",
    "Ejemplos buenos: \"¡Cuidado con tantas medialunas!\", \"¡Debe haber estado bueno ese café Martínez!\",",
    "\"Esa provoleta del sábado pintaba bárbara 🧀\", \"Semana tranqui de gustos, ¡bien ahí!\".",
    "Ejemplos malos, NO los hagas: \"¡Cuidado con tanto transporte!\" (lo necesita para trabajar),",
    "\"¡Cuánto supermercado!\" (hay que comer), \"Gastaste mucho este mes\" (sermón genérico).",
    "",
    "Forma: máximo 60 caracteres, español rioplatense, de vos, a lo sumo un emoji. Simpático y",
    "amistoso, como un amigo que te conoce. Si casi no hay movimientos, un saludo o ánimo corto.",
    anteriores ? "No repitas la idea de los mensajes de ayer:\n" + anteriores : "",
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
  encabezados.forEach(function (e) {
    const celda = sheet.getRange(1, e[0]);
    if (!celda.getValue()) celda.setValue(e[1]).setFontWeight("bold").setBackground("#e2e8f0");
  });
  sheet.getRange(1, MSG_COL_FECHA, sheet.getMaxRows(), 1).setNumberFormat("@");
}
