package com.example.data

import java.util.UUID

/**
 * Plan de una compra en cuotas. Es una **proyección/pasivo**: no toca el saldo por sí mismo.
 *
 * El pago de cada cuota se materializa como un [Movement] de tipo "Gasto" que referencia este
 * plan (ver [Movement.planId] / [Movement.cuotaNumero]); el `AccountingEngine` lo debita del
 * dinero disponible como cualquier gasto. Una cuota está "pagada" si existe ese movimiento
 * (estado **derivado**, nunca duplicado aquí).
 *
 * Regla de negocio: un plan de cuotas es **siempre personal**. Tiene un único [propietario]
 * (Santiago | Rocío); no existe `esComun` ni propietario "Ambos". El gasto generado al pagar es
 * personal con `responsable == propietario`, así que no genera propiedad cruzada.
 */
data class CuotaPlan(
    val id: String = UUID.randomUUID().toString(),
    val fechaCreacion: String,        // ISO "yyyy-MM-dd HH:mm" — orden por creación desc
    val descripcion: String,          // "Notebook Lenovo"
    val montoPorCuota: Double,        // permite cuotas con interés (no siempre total/N)
    val cantidadCuotas: Int,          // N
    val fechaPrimeraCuota: String,    // "yyyy-MM" — las siguientes son +1 mes
    val propietario: String,          // "Santiago" | "Rocío" (owner; nunca "Ambos")
    val categoria: String,            // categoría del gasto que generará cada cuota
    val tarjeta: String = "",         // "Visa Santiago", "Naranja"… para agrupar el total mensual
    val eliminado: Boolean = false
) {
    val montoTotal: Double get() = montoPorCuota * cantidadCuotas
}
