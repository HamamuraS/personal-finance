package com.example.ui

import com.example.data.Movement

/**
 * Resultado del cálculo contable de un periodo.
 *
 * Convención de propiedad cruzada: el motor mantiene internamente un único neto con signo
 * (`netSantiagoEnRocio`). De ahí se derivan las vistas direccionales:
 *  - [sEnRocio]      = dinero (neto) de Santiago que está físicamente en cuentas de Rocío.
 *  - [rEnSantiago]   = dinero (neto) de Rocío que está físicamente en cuentas de Santiago.
 *  - [santiagoExterno] / [rocioExterno] = posición externa neta de cada uno (con signo).
 *
 * Identidad garantizada:
 *   saldoFinal = saldoEnMano (físico en cuentas propias) + saldoExterno (neto afuera)
 *   totalPozo  = santiagoEnMano + rocioEnMano  (todo el dinero físico existente)
 */
data class BalanceBreakdown(
    val totalPozo: Double,
    val santiagoAportes: Double,
    val santiagoGastosPersonales: Double,
    val rocioAportes: Double,
    val rocioGastosPersonales: Double,
    val gastosComunesTotales: Double,
    val santiagoGastosComunes: Double,
    val rocioGastosComunes: Double,
    val santiagoTransfersEnviadas: Double,
    val rocioTransfersEnviadas: Double,
    val santiagoSaldoFinal: Double,
    val rocioSaldoFinal: Double,
    val totalAportesMes: Double,
    val totalGastosMes: Double,
    val santiagoEfectivo: Double,
    val santiagoVirtual: Double,
    val rocioEfectivo: Double,
    val rocioVirtual: Double,
    val sEnRocio: Double,
    val rEnSantiago: Double,
    val santiagoEnMano: Double,
    val rocioEnMano: Double,
    val santiagoExterno: Double,
    val rocioExterno: Double
)

/**
 * Estado patrimonial al inicio de un periodo (arrastre del mes anterior).
 * Contiene el stock físico por cuenta/método y el neto de propiedad cruzada.
 */
data class OpeningBalance(
    val santiagoEfectivo: Double = 0.0,
    val santiagoVirtual: Double = 0.0,
    val rocioEfectivo: Double = 0.0,
    val rocioVirtual: Double = 0.0,
    // Positivo = Santiago posee (neto) dinero en cuentas de Rocío.
    val netSantiagoEnRocio: Double = 0.0
)

/**
 * Motor contable puro (sin dependencias de Android): recibe una lista de movimientos y un
 * arrastre inicial opcional, y devuelve el desglose completo de saldos.
 *
 * Reglas de negocio:
 *  - Todo movimiento tiene un `responsable` (dueño de la cuenta física por donde se mueve el
 *    dinero) y un `propietario` (a quién pertenece realmente ese dinero).
 *  - Aporte: entra dinero a la cuenta del responsable. Si el propietario es otro, ese dinero
 *    queda contabilizado como propiedad del propietario dentro de la cuenta del responsable.
 *  - Gasto personal: sale de la cuenta del responsable y reduce el patrimonio del propietario.
 *  - Gasto común (o propietario "Ambos"): se divide 50/50. Sale físicamente de la cuenta del
 *    responsable; la mitad del otro genera un reclamo a favor del responsable.
 *  - Transferencia: mueve dinero físicamente del responsable al otro. Si conserva propietario,
 *    solo cambia de ubicación (genera propiedad cruzada). Si cambia de dueño, es un regalo.
 *
 * El stock (físico + propiedad cruzada) arranca en [opening]; los flujos del periodo
 * (aportes, gastos, etc.) siempre arrancan en cero para reflejar solo el mes en curso.
 */
object AccountingEngine {

    private const val SANTIAGO = "Santiago"
    private const val ROCIO = "Rocío"
    private const val AMBOS = "Ambos"

    /** Deriva el arrastre inicial a partir del estado final de una lista de movimientos. */
    fun opening(list: List<Movement>): OpeningBalance {
        val b = compute(list)
        return OpeningBalance(
            santiagoEfectivo = b.santiagoEfectivo,
            santiagoVirtual = b.santiagoVirtual,
            rocioEfectivo = b.rocioEfectivo,
            rocioVirtual = b.rocioVirtual,
            netSantiagoEnRocio = b.santiagoExterno
        )
    }

    fun compute(list: List<Movement>, opening: OpeningBalance = OpeningBalance()): BalanceBreakdown {
        // El saldo de devolución en las transferencias depende del estado acumulado, así que
        // procesamos siempre en orden cronológico (independiente de cómo venga ordenada la lista).
        val ordered = list.sortedWith(compareBy({ it.fecha }, { it.id }))

        // Stock físico por cuenta y método (arranca en el arrastre)
        var sEfec = opening.santiagoEfectivo
        var sVirt = opening.santiagoVirtual
        var rEfec = opening.rocioEfectivo
        var rVirt = opening.rocioVirtual

        // Stock de propiedad cruzada (neto con signo): + = Santiago en cuentas de Rocío
        var netSR = opening.netSantiagoEnRocio

        // Flujos del periodo (no incluyen arrastre)
        var sAportes = 0.0
        var rAportes = 0.0
        var sPers = 0.0
        var rPers = 0.0
        var comunes = 0.0
        var sTransfers = 0.0
        var rTransfers = 0.0
        var totalAportes = 0.0
        var totalGastos = 0.0

        for (m in ordered) {
            val respS = m.responsable.equals(SANTIAGO, ignoreCase = true)
            val respR = m.responsable.equals(ROCIO, ignoreCase = true)
            if (!respS && !respR) continue

            val prop = normalizePropietario(m)
            val propS = prop == SANTIAGO
            val propR = prop == ROCIO
            val propAmbos = prop == AMBOS
            val efec = m.metodoPago.equals("Efectivo", ignoreCase = true)

            when (m.tipo.lowercase()) {
                "aporte" -> {
                    // El dinero entra físicamente a la cuenta del responsable
                    if (respS) { if (efec) sEfec += m.monto else sVirt += m.monto }
                    else { if (efec) rEfec += m.monto else rVirt += m.monto }

                    when {
                        propAmbos -> {
                            val half = m.monto / 2.0
                            // La mitad pertenece al no-responsable dentro de la cuenta del responsable
                            if (respS) netSR -= half else netSR += half
                            sAportes += half
                            rAportes += half
                        }
                        propS -> {
                            sAportes += m.monto
                            if (respR) netSR += m.monto // Santiago posee dinero en cuenta de Rocío
                        }
                        propR -> {
                            rAportes += m.monto
                            if (respS) netSR -= m.monto // Rocío posee dinero en cuenta de Santiago
                        }
                    }
                    totalAportes += m.monto
                }

                "gasto" -> {
                    val comun = m.esComun || propAmbos
                    // El gasto sale físicamente de la cuenta del responsable
                    if (respS) { if (efec) sEfec -= m.monto else sVirt -= m.monto }
                    else { if (efec) rEfec -= m.monto else rVirt -= m.monto }

                    if (comun) {
                        comunes += m.monto
                        val half = m.monto / 2.0
                        // La mitad del no-responsable la financió el responsable -> reclamo a su favor
                        if (respS) netSR += half else netSR -= half
                    } else {
                        when {
                            propS -> {
                                sPers += m.monto
                                if (respR) netSR -= m.monto // gasto de Santiago pagado desde cuenta de Rocío
                            }
                            propR -> {
                                rPers += m.monto
                                if (respS) netSR += m.monto // gasto de Rocío pagado desde cuenta de Santiago
                            }
                        }
                    }
                    totalGastos += m.monto
                }

                "transferencia" -> {
                    // El dinero SIEMPRE se mueve físicamente de la cuenta del responsable a la del otro.
                    if (respS) {
                        if (efec) { sEfec -= m.monto; rEfec += m.monto } else { sVirt -= m.monto; rVirt += m.monto }
                        when {
                            // "Sigue siendo mía": Santiago estaciona SU plata en la cuenta de Rocío.
                            propS -> netSR += m.monto
                            // "Es de Rocío": la plata es de Rocío y llega a SU cuenta. Primero salda lo
                            // que Rocío tenga estacionado en la cuenta de Santiago (devolución, sin cambio
                            // de patrimonio); el excedente es un regalo real (baja el patrimonio de Santiago).
                            propR -> {
                                val devolucion = minOf(m.monto, maxOf(0.0, -netSR)) // rEnSantiago
                                netSR += devolucion
                                sTransfers += (m.monto - devolucion) // porción regalada
                            }
                        }
                    } else {
                        if (efec) { rEfec -= m.monto; sEfec += m.monto } else { rVirt -= m.monto; sVirt += m.monto }
                        when {
                            propR -> netSR -= m.monto
                            propS -> {
                                val devolucion = minOf(m.monto, maxOf(0.0, netSR)) // sEnRocio
                                netSR -= devolucion
                                rTransfers += (m.monto - devolucion)
                            }
                        }
                    }
                }
            }
        }

        val sEnMano = sEfec + sVirt
        val rEnMano = rEfec + rVirt
        val sExterno = netSR
        val rExterno = -netSR
        val sSaldoFinal = sEnMano + sExterno
        val rSaldoFinal = rEnMano + rExterno
        val totalPozo = sEnMano + rEnMano

        return BalanceBreakdown(
            totalPozo = totalPozo,
            santiagoAportes = sAportes,
            santiagoGastosPersonales = sPers,
            rocioAportes = rAportes,
            rocioGastosPersonales = rPers,
            gastosComunesTotales = comunes,
            santiagoGastosComunes = comunes / 2.0,
            rocioGastosComunes = comunes / 2.0,
            santiagoTransfersEnviadas = sTransfers,
            rocioTransfersEnviadas = rTransfers,
            santiagoSaldoFinal = sSaldoFinal,
            rocioSaldoFinal = rSaldoFinal,
            totalAportesMes = totalAportes,
            totalGastosMes = totalGastos,
            santiagoEfectivo = sEfec,
            santiagoVirtual = sVirt,
            rocioEfectivo = rEfec,
            rocioVirtual = rVirt,
            sEnRocio = maxOf(0.0, netSR),
            rEnSantiago = maxOf(0.0, -netSR),
            santiagoEnMano = sEnMano,
            rocioEnMano = rEnMano,
            santiagoExterno = sExterno,
            rocioExterno = rExterno
        )
    }

    /** Normaliza el propietario a uno de: "Santiago", "Rocío", "Ambos". Fallback: el responsable. */
    private fun normalizePropietario(m: Movement): String {
        val p = m.propietario.trim()
        return when {
            p.equals(SANTIAGO, ignoreCase = true) -> SANTIAGO
            p.equals(ROCIO, ignoreCase = true) -> ROCIO
            p.equals(AMBOS, ignoreCase = true) -> AMBOS
            else -> if (m.responsable.equals(ROCIO, ignoreCase = true)) ROCIO else SANTIAGO
        }
    }
}
