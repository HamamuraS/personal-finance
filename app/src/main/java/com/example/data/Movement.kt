package com.example.data

import java.util.UUID

data class Movement(
    val id: String = UUID.randomUUID().toString(),
    val fecha: String,
    val monto: Double,
    val tipo: String, // "Aporte", "Gasto", "Transferencia", "Condonación", "Devolución", "Cambio", "Apertura"
    val categoria: String,
    val responsable: String, // "Santiago", "Rocío"
    val esComun: Boolean, // Si es compartido o personal (para gastos)
    val propietario: String = responsable, // "Santiago", "Rocío", "Ambos"
    val descripcion: String = "",
    val metodoPago: String = "Billetera Virtual", // "Efectivo", "Billetera Virtual"
    val ticketUrl: String = "", // Nueva columna J
    val eliminado: Boolean = false, // Columna K
    // --- Vínculo con el módulo de cuotas (retrocompatible; vacío/0 en filas viejas) ---
    val planId: String = "",    // Columna M (índice 12): plan de cuotas que este gasto paga
    val cuotaNumero: Int = 0,   // Columna N (índice 13): número de cuota (1..N) que paga
    /**
     * Columna O (índice 14): gasto evitable (gusto, lujo, opcional) vs. no evitable (mínimo
     * indispensable). Solo tiene sentido en gastos. Vacío en la planilla = **no evitable**.
     *
     * Se modela como `evitable` y no como `indispensable` a propósito: si algún deserializador ignora
     * los defaults de Kotlin (Gson lo hace), un campo ausente queda en `false`. Así el JSON de un script
     * viejo, la cola de pendientes, el cache y la base demo de versiones anteriores se leen como no
     * evitables, que es el default pedido.
     */
    val evitable: Boolean = false
) {
    // Convierte el movimiento en una fila para Google Sheets
    fun toRowValues(): List<String> {
        return listOf(
            id,
            fecha,
            monto.toString(),
            tipo,
            categoria,
            responsable,
            esComun.toString(),
            descripcion,
            metodoPago,
            ticketUrl,
            eliminado.toString(),
            propietario,
            planId,
            cuotaNumero.toString(),
            evitable.toString()
        )
    }

    companion object {
        // Crea un movimiento a partir de una lista de celdas de una fila de Google Sheets
        fun fromRowValues(row: List<String>): Movement? {
            if (row.size < 7) return null
            return try {
                Movement(
                    id = row.getOrNull(0) ?: UUID.randomUUID().toString(),
                    fecha = row.getOrNull(1) ?: "",
                    monto = row.getOrNull(2)?.toDoubleOrNull() ?: 0.0,
                    tipo = row.getOrNull(3) ?: "Gasto",
                    categoria = row.getOrNull(4) ?: "Otros",
                    responsable = row.getOrNull(5) ?: "Santiago",
                    esComun = row.getOrNull(6)?.toBooleanStrictOrNull() ?: true,
                    descripcion = row.getOrNull(7) ?: "",
                    metodoPago = row.getOrNull(8) ?: "Billetera Virtual",
                    ticketUrl = row.getOrNull(9) ?: "",
                    eliminado = row.getOrNull(10)?.toBooleanStrictOrNull() ?: false,
                    propietario = row.getOrNull(11) ?: row.getOrNull(5) ?: "Santiago",
                    planId = row.getOrNull(12) ?: "",
                    cuotaNumero = row.getOrNull(13)?.toIntOrNull() ?: 0,
                    evitable = row.getOrNull(14)?.trim()?.equals("true", ignoreCase = true) ?: false
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
