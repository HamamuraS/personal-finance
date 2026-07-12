/**
 * Script de Google Apps Script para la sincronización de la App de Finanzas Personales.
 *
 * Versión: 4.0 (Soporte para baja lógica y carpeta configurable)
 * @description Este script requiere acceso a Google Drive para guardar los tickets.
 */

const monthNames = ["Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"];
const defaultFolderId = "1LT_t2a7WBFe6wGjwJ5XuTYsS7gvjr3jU";

/**
 * Función para forzar la solicitud de permisos de Drive.
 */
function triggerAuthorization() {
  const folder = DriveApp.getFolderById(defaultFolderId);
  Logger.log("Acceso a carpeta verificado: " + folder.getName());
}

function doGet(e) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const sheets = ss.getSheets();
  let allData = [];

  sheets.forEach(sheet => {
    const data = sheet.getDataRange().getValues();
    if (data.length > 1) {
      const rows = data.slice(1).map(row => {
        // Ignorar si está marcado como eliminado (Columna K / index 10)
        const eliminado = row[10] === true || row[10] === 'true' || row[10] === 'VERDADERO';
        if (eliminado) return null;

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
          propietario: row[11] ? row[11].toString() : (row[5] ? row[5].toString() : "")
        };
      }).filter(r => r !== null);
      allData = allData.concat(rows);
    }
  });

  return ContentService.createTextOutput(JSON.stringify({ status: "SUCCESS", data: allData }))
    .setMimeType(ContentService.MimeType.JSON);
}

function doPost(e) {
  let imgStatus = "No procesada";
  try {
    const contents = e.postData.contents;
    const json = JSON.parse(contents);
    const action = json.action;
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
        sheet.appendRow(["ID", "Fecha", "Monto", "Tipo", "Categoría", "Responsable", "Es Común", "Descripción", "Metodo Pago", "Ticket URL", "Eliminado", "Propietario"]);
        sheet.getRange(1, 1, 1, 12).setFontWeight("bold").setBackground("#e2e8f0");
        sheet.setFrozenRows(1);
      }

      // Si es un PUT, buscar por ID y reemplazar
      if (action === 'PUT') {
          const data = sheet.getDataRange().getValues();
          for (let i = 1; i < data.length; i++) {
              if (data[i][0] == mov.id) {
                  sheet.getRange(i + 1, 1, 1, 12).setValues([[
                      mov.id, mov.fecha, mov.monto, mov.tipo, mov.categoria, mov.responsable, mov.esComun, mov.descripcion, mov.metodoPago, mov.ticketUrl || data[i][9], false, mov.propietario || mov.responsable
                  ]]);
                  return ContentService.createTextOutput(JSON.stringify({ status: "SUCCESS", message: "Actualizado OK" })).setMimeType(ContentService.MimeType.JSON);
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
        mov.propietario || mov.responsable
      ]);

      return ContentService.createTextOutput(JSON.stringify({
        status: "SUCCESS",
        message: imgStatus
      })).setMimeType(ContentService.MimeType.JSON);
    }
  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({
      status: "ERROR",
      message: err.toString(),
      imgStatus: imgStatus
    })).setMimeType(ContentService.MimeType.JSON);
  }
}

function handleLogicalDelete(id) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const sheets = ss.getSheets();

  for (let sheet of sheets) {
    const data = sheet.getDataRange().getValues();
    for (let i = 1; i < data.length; i++) {
      if (data[i][0] == id) {
        // Marcar columna K (index 10) como TRUE
        sheet.getRange(i + 1, 11).setValue(true);
        return ContentService.createTextOutput(JSON.stringify({ status: "SUCCESS", message: "Eliminado (baja lógica) OK" }))
          .setMimeType(ContentService.MimeType.JSON);
      }
    }
  }

  return ContentService.createTextOutput(JSON.stringify({ status: "ERROR", message: "ID no encontrado" }))
    .setMimeType(ContentService.MimeType.JSON);
}
