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
    // Plata que cada uno le REGALÓ al otro en el periodo: la porción de una transferencia que no
    // saldaba una deuda, más lo perdonado en las condonaciones. En los dos casos baja el patrimonio
    // de quien la envía sin ser un gasto (no sale del pozo, cambia de dueño).
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
 *  - Condonación: el responsable perdona lo que el otro tiene de él. No mueve nada físico: solo
 *    cancela propiedad cruzada. Ver [TIPO_CONDONACION].
 *
 * El stock (físico + propiedad cruzada) arranca en [opening]; los flujos del periodo
 * (aportes, gastos, etc.) siempre arrancan en cero para reflejar solo el mes en curso.
 */
object AccountingEngine {

    private const val SANTIAGO = "Santiago"
    private const val ROCIO = "Rocío"
    private const val AMBOS = "Ambos"
    private const val EFECTIVO = "Efectivo"
    private const val VIRTUAL = "Billetera Virtual"

    /**
     * Tipo reservado de las filas de apertura: materializan el arrastre del mes anterior dentro de
     * la propia hoja del mes. No son un flujo (no suman a aportes/gastos del periodo), son *stock*.
     */
    const val TIPO_APERTURA = "Apertura"

    /** Categoría reservada de las filas de apertura. Nunca es elegible por el usuario. */
    const val CATEGORIA_APERTURA = "Saldo inicial"

    /**
     * Tipo de la **condonación** ("perdonar deuda"): el responsable renuncia a lo que el otro tiene
     * de él.
     *
     * Es el único movimiento que no toca ningún bucket físico —la plata ya está donde tiene que
     * estar, lo único que cambia es de quién es—, y por eso no se puede modelar como una
     * transferencia: esa *siempre* mueve plata de una cuenta a la otra, así que perdonar con una
     * transferencia descontaba el monto de la cuenta del acreedor **y encima dejaba la deuda en
     * pie** (la devolución de la transferencia mira la propiedad cruzada en el sentido contrario).
     *
     * En la UI se carga como un submodo de "Transferencia"; en la planilla es un tipo propio.
     */
    const val TIPO_CONDONACION = "Condonación"

    /** ¿[m] es una condonación? Tolera la forma sin tilde por si se carga a mano en la planilla. */
    fun isCondonacion(m: Movement): Boolean =
        m.tipo.equals(TIPO_CONDONACION, ignoreCase = true) || m.tipo.equals("Condonacion", ignoreCase = true)

    fun isOpeningRow(m: Movement): Boolean = m.tipo.equals(TIPO_APERTURA, ignoreCase = true)

    /**
     * Filas de arrastre automático que inyectaban las versiones <= 7.1: un único `Aporte` por
     * persona, siempre como "Billetera Virtual" y sin propietario, así que perdían el efectivo y la
     * propiedad cruzada. Se ignoran (el arrastre correcto son las filas [TIPO_APERTURA]).
     */
    fun isLegacyCarryover(m: Movement): Boolean =
        !isOpeningRow(m) && m.categoria.equals(CATEGORIA_APERTURA, ignoreCase = true)

    /** Deriva el arrastre inicial a partir del estado final de una lista de movimientos. */
    fun opening(list: List<Movement>, base: OpeningBalance = OpeningBalance()): OpeningBalance {
        val b = compute(list, base)
        return OpeningBalance(
            santiagoEfectivo = b.santiagoEfectivo,
            santiagoVirtual = b.santiagoVirtual,
            rocioEfectivo = b.rocioEfectivo,
            rocioVirtual = b.rocioVirtual,
            netSantiagoEnRocio = b.santiagoExterno
        )
    }

    /**
     * Reconstruye el stock inicial a partir de las filas de apertura de un mes. Cada fila aporta a
     * un bucket físico (responsable × método) y, si el propietario no es el responsable, al neto de
     * propiedad cruzada — misma semántica que un "Aporte", pero sin contar como flujo del periodo.
     */
    fun openingFromRows(rows: List<Movement>): OpeningBalance {
        var sEfec = 0.0
        var sVirt = 0.0
        var rEfec = 0.0
        var rVirt = 0.0
        var netSR = 0.0

        for (m in rows) {
            val respS = m.responsable.equals(SANTIAGO, ignoreCase = true)
            val respR = m.responsable.equals(ROCIO, ignoreCase = true)
            if (!respS && !respR) continue
            val efec = m.metodoPago.equals(EFECTIVO, ignoreCase = true)

            if (respS) { if (efec) sEfec += m.monto else sVirt += m.monto }
            else { if (efec) rEfec += m.monto else rVirt += m.monto }

            when (normalizePropietario(m)) {
                SANTIAGO -> if (respR) netSR += m.monto
                ROCIO -> if (respS) netSR -= m.monto
                else -> { // Ambos: mitad de cada uno
                    val half = m.monto / 2.0
                    if (respS) netSR -= half else netSR += half
                }
            }
        }
        return OpeningBalance(sEfec, sVirt, rEfec, rVirt, netSR)
    }

    /**
     * Serializa un [OpeningBalance] a las filas que se escriben en la hoja del mes. Inversa exacta
     * de [openingFromRows].
     *
     * La propiedad cruzada se ancla al bucket **virtual** de quien tiene el dinero físicamente: da
     * igual a qué método se impute (el neto no depende del medio de pago), pero fijarlo mantiene la
     * generación determinística. Los ids también son determinísticos para que regenerar la apertura
     * pise las filas anteriores en vez de duplicarlas.
     *
     * @param month "yyyy-MM"
     */
    fun openingRowsFor(month: String, o: OpeningBalance): List<Movement> {
        val net = o.netSantiagoEnRocio
        // net > 0: Santiago tiene plata en la cuenta de Rocío -> sale del virtual de Rocío.
        // net < 0: Rocío tiene plata en la cuenta de Santiago -> sale del virtual de Santiago.
        val sVirtDeRocio = if (net < 0) -net else 0.0
        val rVirtDeSantiago = if (net > 0) net else 0.0

        return listOfNotNull(
            openingRow(month, SANTIAGO, SANTIAGO, EFECTIVO, o.santiagoEfectivo),
            openingRow(month, SANTIAGO, SANTIAGO, VIRTUAL, o.santiagoVirtual - sVirtDeRocio),
            if (sVirtDeRocio != 0.0) openingRow(month, SANTIAGO, ROCIO, VIRTUAL, sVirtDeRocio) else null,
            openingRow(month, ROCIO, ROCIO, EFECTIVO, o.rocioEfectivo),
            openingRow(month, ROCIO, ROCIO, VIRTUAL, o.rocioVirtual - rVirtDeSantiago),
            if (rVirtDeSantiago != 0.0) openingRow(month, ROCIO, SANTIAGO, VIRTUAL, rVirtDeSantiago) else null
        )
    }

    private fun openingRow(
        month: String,
        responsable: String,
        propietario: String,
        metodo: String,
        monto: Double
    ): Movement {
        val quien = if (responsable == ROCIO) "r" else "s"
        val deQuien = if (propietario == ROCIO) "r" else "s"
        val medio = if (metodo == EFECTIVO) "efec" else "virt"
        val deOtro = if (propietario != responsable) " (de $propietario)" else ""
        return Movement(
            id = "apertura-$month-$quien-$deQuien-$medio",
            fecha = "$month-01 00:00",
            monto = monto,
            tipo = TIPO_APERTURA,
            categoria = CATEGORIA_APERTURA,
            responsable = responsable,
            esComun = false,
            propietario = propietario,
            descripcion = "Saldo inicial $month · $responsable · $metodo$deOtro",
            metodoPago = metodo
        )
    }

    /**
     * Resuelve el stock inicial del mes [month] a partir de *toda* la lista de movimientos.
     *
     * Orden de preferencia:
     *  1. El mes tiene filas de apertura -> se usan tal cual. **No mira ningún mes anterior**, así
     *     que las hojas viejas se pueden purgar sin romper nada.
     *  2. No las tiene -> se replaya desde la apertura disponible más reciente que sea anterior.
     *  3. No hay ninguna apertura -> se replaya todo el historial desde cero (modo <= 7.1).
     */
    fun openingFor(all: List<Movement>, month: String): OpeningBalance {
        val usable = all.filter { !isLegacyCarryover(it) && monthOf(it).isNotEmpty() }
        val anchors = usable.filter { isOpeningRow(it) }.groupBy { monthOf(it) }

        anchors[month]?.let { return openingFromRows(it) }

        val anchorMonth = anchors.keys.filter { it < month }.maxOrNull()
        val base = anchorMonth?.let { openingFromRows(anchors.getValue(it)) } ?: OpeningBalance()
        val desde = anchorMonth ?: ""
        val replay = usable.filter { !isOpeningRow(it) && monthOf(it) >= desde && monthOf(it) < month }
        return opening(replay, base)
    }

    /** Resultado de [chequearRecalculo]: si se puede regenerar la apertura, y por qué no. */
    data class ChequeoDeApertura(val permitido: Boolean, val motivo: String = "")

    /**
     * ¿Es seguro regenerar la apertura de [month] a partir de los meses anteriores?
     *
     * Regenerar deriva el arrastre de los meses previos **ignorando a propósito** la apertura que el
     * mes ya tenga escrita (si no, recalcular sería un no-op). Eso es correcto mientras esos meses
     * existan; si se purgaron, "derivar" da cero y la regeneración pisa una apertura correcta con
     * ceros — es decir, se lleva puesto todo el patrimonio arrastrado. Y no es un caso raro: con
     * hojas viejas borradas, ese es el estado **normal** del mes más antiguo que sobrevive.
     *
     * Se bloquea en dos situaciones, siempre con la apertura ya escrita como cosa a proteger:
     *  1. No queda ningún movimiento anterior a [month].
     *  2. El recálculo da todo cero pero la apertura escrita no lo es (purga parcial).
     *
     * Materializar el corte de un mes que **todavía no tiene** apertura siempre se permite: no hay
     * nada que perder, y es el caso normal (incluido el primer mes de la planilla, cuyo arrastre
     * legítimamente es cero).
     */
    fun chequearRecalculo(all: List<Movement>, month: String): ChequeoDeApertura {
        val usable = all.filter { !isLegacyCarryover(it) && monthOf(it).isNotEmpty() }
        val escritas = usable.filter { monthOf(it) == month && isOpeningRow(it) }
        if (escritas.isEmpty()) return ChequeoDeApertura(true)

        if (usable.none { monthOf(it) < month }) return ChequeoDeApertura(
            false,
            "No quedan meses anteriores a $month en la planilla: el arrastre daría cero y borraría " +
                "la apertura que el mes ya tiene escrita."
        )

        val nueva = openingFor(usable.filter { monthOf(it) != month }, month)
        if (esVacia(nueva) && !esVacia(openingFromRows(escritas))) return ChequeoDeApertura(
            false,
            "El recálculo da cero pero $month ya tiene una apertura con saldo: faltan meses " +
                "anteriores en la planilla."
        )
        return ChequeoDeApertura(true)
    }

    /** Un arrastre sin nada: todos los buckets y la propiedad cruzada en cero (tolerando centavos). */
    private fun esVacia(o: OpeningBalance): Boolean =
        listOf(
            o.santiagoEfectivo, o.santiagoVirtual, o.rocioEfectivo, o.rocioVirtual, o.netSantiagoEnRocio
        ).all { kotlin.math.abs(it) < 0.005 }

    private fun monthOf(m: Movement): String = if (m.fecha.length >= 7) m.fecha.substring(0, 7) else ""

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
            // Las filas de apertura son stock, no flujo: entran por [opening], nunca por acá.
            if (isOpeningRow(m) || isLegacyCarryover(m)) continue
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

                // Perdón de deuda: el responsable (acreedor) renuncia a lo que el otro tiene de él.
                // No toca ningún bucket físico; solo cancela propiedad cruzada. Se clampea contra la
                // deuda existente para que perdonar de más nunca genere deuda en el sentido
                // contrario — mismo criterio que la devolución de las transferencias. Tampoco es un
                // flujo del periodo: el pozo no cambia, así que no suma a aportes ni a gastos.
                "condonación", "condonacion" -> {
                    if (respS) {
                        val perdonado = minOf(m.monto, maxOf(0.0, netSR)) // sEnRocio
                        netSR -= perdonado
                        sTransfers += perdonado
                    } else {
                        val perdonado = minOf(m.monto, maxOf(0.0, -netSR)) // rEnSantiago
                        netSR += perdonado
                        rTransfers += perdonado
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

    /**
     * Normaliza el propietario de [m] a uno de: "Santiago", "Rocío", "Ambos".
     *
     * Es la **única** definición de "de quién es este movimiento" en la app. Fuera del motor la usan
     * Métricas y el filtro por persona de Inicio: antes agrupaban por `responsable` (de qué cuenta
     * salió la plata), que no es lo mismo y hacía que un gasto de Rocío pagado desde la cuenta de
     * Santiago apareciera como gasto de Santiago.
     */
    fun propietarioDe(m: Movement): String = normalizePropietario(m)

    /**
     * Porción de un **gasto** que le corresponde patrimonialmente a [slotKey], con el mismo criterio
     * que [compute]: los gastos personales van enteros al propietario y los comunes (o con
     * propietario "Ambos") se parten 50/50. Así la suma de las dos personas siempre da el total.
     */
    fun porcionDelGasto(m: Movement, slotKey: String): Double {
        val prop = normalizePropietario(m)
        if (m.esComun || prop == AMBOS) return m.monto / 2.0
        return if (prop.equals(slotKey, ignoreCase = true)) m.monto else 0.0
    }

    /**
     * ¿[m] "es de" [slotKey]? Criterio de pertenencia patrimonial para filtrar listados. Los
     * movimientos comunes / de "Ambos" pertenecen a las **dos** personas, así que matchean siempre.
     */
    fun perteneceA(m: Movement, slotKey: String): Boolean {
        val prop = normalizePropietario(m)
        return prop == AMBOS || m.esComun || prop.equals(slotKey, ignoreCase = true)
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
