package com.example.data

import java.util.UUID

/**
 * Una fila de una hoja de mes. Los nombres de los campos son los del JSON del Web App, y ese mismo
 * JSON es el que se guarda en los caches y en la cola de pendientes: no se renombran.
 *
 * Las columnas de la planilla (A..O) las mapea el Apps Script; acá solo se documentan.
 */
data class Movement(
    val id: String = UUID.randomUUID().toString(),
    val fecha: String,               // Columna B: "yyyy-MM-dd HH:mm"
    val monto: Double,
    val tipo: String,                // Ver [TipoMovimiento]
    val categoria: String,
    val responsable: String,         // slotKey del dueño de la cuenta por donde pasa la plata
    val esComun: Boolean,            // Si es compartido o personal (para gastos)
    val propietario: String = responsable, // slotKey de quién es la plata, o [PROPIETARIO_AMBOS]
    val descripcion: String = "",
    val metodoPago: String = MetodoPago.VIRTUAL, // Ver [MetodoPago]
    val ticketUrl: String = "",      // Columna J
    val eliminado: Boolean = false,  // Columna K
    // --- Vínculo con el módulo de cuotas (retrocompatible; vacío/0 en filas viejas) ---
    val planId: String = "",         // Columna M (índice 12): plan de cuotas que este gasto paga
    val cuotaNumero: Int = 0,        // Columna N (índice 13): número de cuota (1..N) que paga
    /**
     * Columna O (índice 14): gasto evitable (gusto, lujo, opcional) vs. no evitable (mínimo
     * indispensable). Solo tiene sentido en gastos. Vacío en la planilla = **no evitable**.
     *
     * Se modela como `evitable` y no como `indispensable` a propósito: un campo ausente (JSON de un
     * script viejo, la cola de pendientes, el cache o la base demo de versiones anteriores) toma el
     * valor por defecto, y así se lee como no evitable, que es el default pedido.
     */
    val evitable: Boolean = false
)
