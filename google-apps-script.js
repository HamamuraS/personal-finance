/**
 * Script de Google Apps Script para la sincronización de la App de Finanzas Personales.
 *
 * Versión: 7.2 (Saldo inicial materializado por hoja)
 * @description Este script requiere acceso a Google Drive para guardar los tickets.
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
          cuotaNumero: Number(row[13]) || 0
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
        cuotasPagadasPrevias: previas
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

      const dateParts = mov.fecha.split("-");
      let sheetName = "Movimientos";
      if (dateParts.length >= 2) {
        const year = dateParts[0];
        const monthNum = parseInt(dateParts[1], 10) - 1;
        if (monthNum >= 0 && monthNum <= 11) {
            sheetName = monthNames[monthNum] + " " + year;
        }
      }

      const ss = SpreadsheetApp.getActiveSpreadsheet();
      let sheet = ss.getSheetByName(sheetName);

      if (!sheet) {
        sheet = ss.insertSheet(sheetName);
        sheet.appendRow(["ID", "Fecha", "Monto", "Tipo", "Categoría", "Responsable", "Es Común", "Descripción", "Metodo Pago", "Ticket URL", "Eliminado", "Propietario", "Plan ID", "Cuota N°"]);
        sheet.getRange(1, 1, 1, 14).setFontWeight("bold").setBackground("#e2e8f0");
        sheet.setFrozenRows(1);
      }

      // Si es un PUT, buscar por ID y reemplazar
      if (action === 'PUT') {
          const data = sheet.getDataRange().getValues();
          for (let i = 1; i < data.length; i++) {
              if (data[i][0] == mov.id) {
                  sheet.getRange(i + 1, 1, 1, 14).setValues([[
                      mov.id, mov.fecha, mov.monto, mov.tipo, mov.categoria, mov.responsable, mov.esComun, mov.descripcion, mov.metodoPago, mov.ticketUrl || data[i][9], false, mov.propietario || mov.responsable, mov.planId || "", mov.cuotaNumero || 0
                  ]]);
                  return jsonOutput({ status: "SUCCESS", message: "Actualizado OK" });
              }
          }
      }

      sheet.appendRow([
        mov.id,
        mov.fecha,
        mov.monto,
        mov.tipo,
        mov.categoria,
        mov.responsable,
        mov.esComun,
        mov.descripcion,
        mov.metodoPago || "Billetera Virtual",
        mov.ticketUrl || "",
        false, // Columna Eliminado
        mov.propietario || mov.responsable,
        mov.planId || "",       // Columna M
        mov.cuotaNumero || 0    // Columna N
      ]);

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
    sheet.appendRow(["ID", "Fecha Creación", "Descripción", "Monto Por Cuota", "Cantidad Cuotas", "Primera Cuota", "Propietario", "Categoría", "Tarjeta", "Eliminado", COLUMNA_PREVIAS]);
    sheet.getRange(1, 1, 1, 11).setFontWeight("bold").setBackground("#e2e8f0");
    sheet.setFrozenRows(1);
    // Forzar texto en las columnas de fecha para preservar el formato "yyyy-MM"/"yyyy-MM-dd HH:mm".
    sheet.getRange("B:B").setNumberFormat("@");
    sheet.getRange("F:F").setNumberFormat("@");
  }

  asegurarColumnaPrevias(sheet);
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
      formatPrevias(previas)
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
        sheet.getRange(i + 1, 1, 1, 11).setValues([rowValues(previas)]);
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
        orden: Number(row[3]) || 0
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
        cuotaNumero: Number(row[13]) || 0
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

      case "transferencia":
        if (respS) {
          if (efec) { sEfec -= monto; rEfec += monto; } else { sVirt -= monto; rVirt += monto; }
          if (propS) netSR += monto;
          else if (propR) netSR += Math.min(monto, Math.max(0, -netSR)); // devolución
        } else {
          if (efec) { rEfec -= monto; sEfec += monto; } else { rVirt -= monto; sVirt += monto; }
          if (propR) netSR -= monto;
          else if (propS) netSR -= Math.min(monto, Math.max(0, netSR)); // devolución
        }
        break;

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
  let sheet = ss.getSheetByName(nombre);
  if (!sheet) {
    sheet = ss.insertSheet(nombre);
    sheet.appendRow(["ID", "Fecha", "Monto", "Tipo", "Categoría", "Responsable", "Es Común", "Descripción", "Metodo Pago", "Ticket URL", "Eliminado", "Propietario", "Plan ID", "Cuota N°"]);
    sheet.getRange(1, 1, 1, 14).setFontWeight("bold").setBackground("#e2e8f0");
    sheet.setFrozenRows(1);
  }

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
