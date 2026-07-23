/**
 * Script de Google Apps Script para la sincronización de la App de Finanzas Personales.
 *
 * Versión: 7.0 (Usuarios parametrizables)
 * @description Este script requiere acceso a Google Drive para guardar los tickets.
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
      const pagadas = paidMap[id] ? Object.keys(paidMap[id]).length : 0;
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
        eliminado: false
      });
    }
  }

  // 3) Ordenar por fechaCreacion descendente (ISO -> orden lexicográfico).
  plans.sort((a, b) => (a.fechaCreacion < b.fechaCreacion) ? 1 : ((a.fechaCreacion > b.fechaCreacion) ? -1 : 0));

  return jsonOutput({ status: "SUCCESS", plans: plans });
}

function doPost(e) {
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
    sheet.appendRow(["ID", "Fecha Creación", "Descripción", "Monto Por Cuota", "Cantidad Cuotas", "Primera Cuota", "Propietario", "Categoría", "Tarjeta", "Eliminado"]);
    sheet.getRange(1, 1, 1, 10).setFontWeight("bold").setBackground("#e2e8f0");
    sheet.setFrozenRows(1);
    // Forzar texto en las columnas de fecha para preservar el formato "yyyy-MM"/"yyyy-MM-dd HH:mm".
    sheet.getRange("B:B").setNumberFormat("@");
    sheet.getRange("F:F").setNumberFormat("@");
  }

  const rowValues = [
    plan.id,
    plan.fechaCreacion,
    plan.descripcion,
    plan.montoPorCuota,
    plan.cantidadCuotas,
    plan.fechaPrimeraCuota,
    plan.propietario,
    plan.categoria,
    plan.tarjeta || "",
    false
  ];

  if (action === 'PUT') {
    const data = sheet.getDataRange().getValues();
    for (let i = 1; i < data.length; i++) {
      if (data[i][0] == plan.id) {
        sheet.getRange(i + 1, 1, 1, 10).setValues([rowValues]);
        return jsonOutput({ status: "SUCCESS", message: "Plan actualizado OK" });
      }
    }
    // Si no se encontró, cae a append (alta).
  }

  sheet.appendRow(rowValues);
  return jsonOutput({ status: "SUCCESS", message: "Plan creado OK" });
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
