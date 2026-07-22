package com.example.ui

import com.example.data.CuotaPlan
import com.example.data.Movement

/** Una cuota concreta de un plan, con su estado derivado de los movimientos. */
data class CuotaProgramada(
    val numero: Int,
    val monto: Double,
    val mesVencimiento: String,       // "yyyy-MM"
    val pagada: Boolean,
    val movimientoId: String? = null  // id del Movement que la pagó (si existe)
)

/** Una cuota impaga que toca (o ya venció) para un mes dado, con su plan de origen. */
data class CuotaRecordatorio(
    val plan: CuotaPlan,
    val cuota: CuotaProgramada,
    val atrasada: Boolean             // venció en un mes anterior al consultado
)

/**
 * Motor puro del módulo de cuotas (sin dependencias de Android; espejo de [AccountingEngine]).
 *
 * Todo lo que produce es **derivado** de `planes` + `movimientos`: nunca toca saldos. El estado
 * "pagada" de cada cuota se deduce de la existencia de un [Movement] no eliminado que la referencia
 * (`planId` + `cuotaNumero`), que es la única fuente de verdad. De este modo, si se borra el
 * movimiento del pago, la cuota vuelve automáticamente a "pendiente".
 */
object CuotasEngine {

    /**
     * Cronograma completo del plan: una entrada por cuota (1..N) con su mes de vencimiento y su
     * estado de pago. `mesVencimiento` de la cuota `k` = `fechaPrimeraCuota + (k-1)` meses.
     */
    fun cronograma(plan: CuotaPlan, movimientos: List<Movement>): List<CuotaProgramada> {
        // Pagos válidos de este plan, indexados por número de cuota (si hubiera duplicados,
        // gana el primero encontrado — igual la cuota queda "pagada").
        val pagos = movimientos
            .asSequence()
            .filter { !it.eliminado && it.planId == plan.id && it.cuotaNumero > 0 }
            .groupBy { it.cuotaNumero }

        return (1..plan.cantidadCuotas).map { numero ->
            val mov = pagos[numero]?.firstOrNull()
            CuotaProgramada(
                numero = numero,
                monto = plan.montoPorCuota,
                mesVencimiento = addMonths(plan.fechaPrimeraCuota, numero - 1),
                pagada = mov != null,
                movimientoId = mov?.id
            )
        }
    }

    /** Cantidad de cuotas distintas ya pagadas de un plan. */
    fun cuotasPagadas(plan: CuotaPlan, movimientos: List<Movement>): Int =
        movimientos
            .asSequence()
            .filter { !it.eliminado && it.planId == plan.id && it.cuotaNumero in 1..plan.cantidadCuotas }
            .map { it.cuotaNumero }
            .distinct()
            .count()

    /** Un plan está completamente pago si tiene tantas cuotas pagadas como cuotas totales. */
    fun estaCompleto(plan: CuotaPlan, movimientos: List<Movement>): Boolean =
        plan.cantidadCuotas > 0 && cuotasPagadas(plan, movimientos) >= plan.cantidadCuotas

    /**
     * Cuotas **impagas** cuyo vencimiento es `<= mes` (incluye **atrasadas** de meses previos).
     * Alimenta los recordatorios: "qué cuotas tengo que pagar a esta altura".
     * Ordenadas por mes de vencimiento y luego por descripción del plan.
     */
    fun recordatoriosDelMes(
        mes: String,
        planes: List<CuotaPlan>,
        movimientos: List<Movement>
    ): List<CuotaRecordatorio> {
        val out = mutableListOf<CuotaRecordatorio>()
        planes.asSequence().filter { !it.eliminado }.forEach { plan ->
            cronograma(plan, movimientos).forEach { cuota ->
                if (!cuota.pagada && cuota.mesVencimiento <= mes) {
                    out.add(CuotaRecordatorio(plan, cuota, atrasada = cuota.mesVencimiento < mes))
                }
            }
        }
        return out.sortedWith(compareBy({ it.cuota.mesVencimiento }, { it.plan.descripcion }))
    }

    /**
     * Total de cuotas (pagadas + impagas) que caen en `mes`, **agrupado por tarjeta**.
     * Responde "cuánto voy a pagar en cada tarjeta en tal mes". Preserva el orden de inserción.
     * Los planes sin tarjeta se agrupan bajo "Sin tarjeta".
     */
    fun totalTarjetaPorMes(
        mes: String,
        planes: List<CuotaPlan>,
        movimientos: List<Movement>
    ): Map<String, Double> {
        val out = LinkedHashMap<String, Double>()
        planes.asSequence().filter { !it.eliminado }.forEach { plan ->
            cronograma(plan, movimientos).forEach { cuota ->
                if (cuota.mesVencimiento == mes) {
                    val key = plan.tarjeta.trim().ifEmpty { "Sin tarjeta" }
                    out[key] = (out[key] ?: 0.0) + cuota.monto
                }
            }
        }
        return out
    }

    /**
     * Cuotas **impagas** cuyo vencimiento es exactamente [mes] (a lo sumo una por plan, ya que las
     * cuotas son mensuales). Es la base del "pago de tarjeta del mes": la UI las agrupa por
     * `plan.tarjeta` para ofrecer pagar todas las cuotas de una tarjeta en un solo paso.
     */
    fun cuotasImpagasDelMes(
        mes: String,
        planes: List<CuotaPlan>,
        movimientos: List<Movement>
    ): List<Pair<CuotaPlan, CuotaProgramada>> =
        planes.asSequence()
            .filter { !it.eliminado }
            .flatMap { plan ->
                cronograma(plan, movimientos)
                    .asSequence()
                    .filter { it.mesVencimiento == mes && !it.pagada }
                    .map { plan to it }
            }
            .toList()

    /** Deuda pendiente total: suma de todas las cuotas impagas de todos los planes (contexto). */
    fun compromisoFuturoTotal(planes: List<CuotaPlan>, movimientos: List<Movement>): Double {
        var total = 0.0
        planes.asSequence().filter { !it.eliminado }.forEach { plan ->
            cronograma(plan, movimientos).forEach { cuota ->
                if (!cuota.pagada) total += cuota.monto
            }
        }
        return total
    }

    /**
     * Suma `months` meses a un mes en formato "yyyy-MM" y devuelve el resultado en el mismo formato,
     * manejando el salto de año. Si la entrada no es parseable, la devuelve intacta.
     */
    fun addMonths(yyyyMM: String, months: Int): String {
        val parts = yyyyMM.split("-")
        val year = parts.getOrNull(0)?.toIntOrNull() ?: return yyyyMM
        val month = parts.getOrNull(1)?.toIntOrNull() ?: return yyyyMM
        val zeroBased = (year * 12 + (month - 1)) + months
        val newYear = zeroBased / 12
        val newMonth = zeroBased % 12 + 1
        return "%04d-%02d".format(newYear, newMonth)
    }
}
