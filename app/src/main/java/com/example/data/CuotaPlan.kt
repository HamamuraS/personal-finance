package com.example.data

import java.util.UUID

/**
 * Plan de una compra en cuotas. Es una **proyección/pasivo**: no toca el saldo por sí mismo.
 *
 * El pago de cada cuota se materializa como un [Movement] de tipo "Gasto" que referencia este
 * plan (ver [Movement.planId] / [Movement.cuotaNumero]); el `AccountingEngine` lo debita del
 * dinero disponible como cualquier gasto. Una cuota está "pagada" si existe ese movimiento
 * (estado **derivado**, nunca duplicado aquí) **o** si figura en [cuotasPagadasPrevias].
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
    val eliminado: Boolean = false,
    /**
     * Cuotas que ya estaban pagadas al momento de un corte de mes, cuyos movimientos **pueden no
     * existir más** (columna K de la hoja "Planes", serializada como "1,2,3").
     *
     * Existe porque el estado "pagada" se deriva de los movimientos, y esos viven en la hoja del mes
     * en que se pagó la cuota: al purgar hojas viejas, cuotas ya pagadas volvían a figurar como
     * deuda aunque el saldo estuviera bien (la apertura ya había incorporado la plata gastada).
     *
     * Es un **conjunto**, no un contador: las cuotas no se pagan necesariamente en orden, así que un
     * "pagadas hasta N" marcaría las equivocadas. Y se escribe siempre por **unión**, nunca por
     * reemplazo: recalcular el corte después de purgar vería menos movimientos y, reemplazando,
     * borraría el registro.
     */
    val cuotasPagadasPrevias: List<Int> = emptyList(),
    /**
     * Columna L de la hoja "Planes": si la compra es evitable. Los pagos del plan **copian** este
     * valor al `Movement` en el momento de pagar (no se deriva: los planes terminados no están
     * cargados). Vacío = no evitable, igual que en los movimientos.
     */
    val evitable: Boolean = false
) {
    val montoTotal: Double get() = montoPorCuota * cantidadCuotas
}
