package com.example.ui

import com.example.data.Categorias
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
    val rocioExterno: Double,
    // --- Flujos "reales" (v7.7): lo que muestran Inicio y Métricas. No intervienen en ningún saldo;
    // existen para separar la plata nueva de los movimientos internos entre los dos. ---
    // Aportes de categoría Ingreso (o "Sueldo", su nombre anterior), atribuidos por propietario.
    val santiagoIngresos: Double = 0.0,
    val rocioIngresos: Double = 0.0,
    // Gastos personales / comunes sin las correcciones (ajustes para cuadrar con el banco).
    val santiagoGastosPersonalesReales: Double = 0.0,
    val rocioGastosPersonalesReales: Double = 0.0,
    val gastosComunesReales: Double = 0.0
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
 *    Desde [CORTE_TRANSFERENCIA_SIN_DEVOLUCION], la que cambia de dueño ya no salda deuda.
 *  - Condonación: el responsable perdona lo que el otro tiene de él. No mueve nada físico: solo
 *    cancela propiedad cruzada. Ver [TIPO_CONDONACION].
 *  - Devolución: el responsable le paga al otro lo que tiene de él. Mueve plata y cancela propiedad
 *    cruzada, sin cambio de patrimonio. Ver [TIPO_DEVOLUCION].
 *  - Cambio: canje de efectivo por transferencia entre los dos. Solo cambia la forma en que cada uno
 *    tiene su plata. Ver [TIPO_CAMBIO].
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

    /**
     * Tipo de la **devolución** ("pagar deuda", v7.7): el responsable le devuelve al otro la plata
     * que tiene de él. La plata se mueve físicamente de la cuenta del responsable a la del otro y la
     * propiedad cruzada baja en lo mismo, así que nadie gana ni pierde patrimonio.
     *
     * Es la regla que antes aplicaba sola cualquier transferencia "es del otro" (saldar primero). Pasó
     * a ser un tipo explícito porque esa devolución automática hacía imposible transferirle plata al
     * otro sin que se descontara de la deuda. En la UI es el "Saldo externo" de quien debe; con saldo a
     * favor, el "Saldo externo" es una [TIPO_CONDONACION].
     *
     * Por qué no un único tipo "Saldo externo" que el motor resuelva por el signo de la deuda: el
     * significado de una fila no puede depender del estado al replayar. Corregir un movimiento
     * anterior podría dar vuelta el signo y convertir un perdón en un pago que mueve plata física.
     */
    const val TIPO_DEVOLUCION = "Devolución"

    /** ¿[m] es una devolución? Tolera la forma sin tilde. */
    fun isDevolucion(m: Movement): Boolean =
        m.tipo.equals(TIPO_DEVOLUCION, ignoreCase = true) || m.tipo.equals("Devolucion", ignoreCase = true)

    /**
     * Tipo del **cambio de dinero** (v7.7): el responsable le da efectivo al otro y recibe una
     * transferencia, o al revés. `metodoPago` es **lo que entrega el responsable**. Nadie gana ni
     * pierde y nadie le debe nada a nadie: no toca la propiedad cruzada ni los flujos del periodo.
     */
    const val TIPO_CAMBIO = "Cambio"

    fun isCambio(m: Movement): Boolean = m.tipo.equals(TIPO_CAMBIO, ignoreCase = true)

    /**
     * Desde esta fecha una transferencia "es del otro" es **regalo completo** y ya no salda la deuda
     * que el receptor tuviera en la cuenta del emisor (eso ahora es una [TIPO_DEVOLUCION] explícita).
     *
     * Las anteriores conservan la regla vieja porque ya saldaron deuda en la planilla real: al
     * 2026-09-30 hay dos devoluciones de 60k (31/07 y 06/08) que, recalculadas con la regla nueva,
     * subirían 60k la deuda al regenerar las aperturas de agosto o septiembre. Ver
     * `features/version-7.7.md`, punto 3. Se compara como texto contra `fecha` ("yyyy-MM-dd HH:mm").
     * Espejado en `CORTE_TRANSFERENCIA_SIN_DEVOLUCION` del Apps Script.
     *
     * Es el 1° de septiembre y no una fecha futura porque septiembre no tiene ninguna transferencia
     * "es del otro" viva (las 3 que hay están eliminadas), así que la regla nueva ya vale para el mes
     * en curso sin alterar nada. Con el corte en octubre, una transferencia cargada el 30/09 seguía
     * descontándose de la deuda. Agosto y antes quedan con la regla vieja, igual que la apertura de
     * septiembre que salió de ellos.
     */
    const val CORTE_TRANSFERENCIA_SIN_DEVOLUCION = "2026-09-01"

    /** ¿La transferencia "es del otro" [m] salda deuda primero (regla anterior a la v7.7)? */
    private fun saldaDeudaPrimero(m: Movement): Boolean = m.fecha < CORTE_TRANSFERENCIA_SIN_DEVOLUCION

    /**
     * ¿[m] cuenta como gasto "real" en Inicio y Métricas? Todo gasto salvo las correcciones, que son
     * ajustes para que la app cuadre con el banco y no un consumo.
     */
    fun esGastoReal(m: Movement): Boolean =
        m.tipo.equals("Gasto", ignoreCase = true) && !Categorias.esCorreccion(m.categoria)

    /** ¿[m] es un ingreso (aporte de categoría Ingreso, o "Sueldo" en filas viejas)? */
    fun esIngreso(m: Movement): Boolean =
        m.tipo.equals("Aporte", ignoreCase = true) && Categorias.esIngreso(m.categoria)

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
        var sIngresos = 0.0
        var rIngresos = 0.0
        var sPersReales = 0.0
        var rPersReales = 0.0
        var comunesReales = 0.0

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

                    val ingreso = Categorias.esIngreso(m.categoria)
                    when {
                        propAmbos -> {
                            val half = m.monto / 2.0
                            // La mitad pertenece al no-responsable dentro de la cuenta del responsable
                            if (respS) netSR -= half else netSR += half
                            sAportes += half
                            rAportes += half
                            if (ingreso) { sIngresos += half; rIngresos += half }
                        }
                        propS -> {
                            sAportes += m.monto
                            if (ingreso) sIngresos += m.monto
                            if (respR) netSR += m.monto // Santiago posee dinero en cuenta de Rocío
                        }
                        propR -> {
                            rAportes += m.monto
                            if (ingreso) rIngresos += m.monto
                            if (respS) netSR -= m.monto // Rocío posee dinero en cuenta de Santiago
                        }
                    }
                    totalAportes += m.monto
                }

                "gasto" -> {
                    val comun = m.esComun || propAmbos
                    val real = !Categorias.esCorreccion(m.categoria)
                    // El gasto sale físicamente de la cuenta del responsable
                    if (respS) { if (efec) sEfec -= m.monto else sVirt -= m.monto }
                    else { if (efec) rEfec -= m.monto else rVirt -= m.monto }

                    if (comun) {
                        comunes += m.monto
                        if (real) comunesReales += m.monto
                        val half = m.monto / 2.0
                        // La mitad del no-responsable la financió el responsable -> reclamo a su favor
                        if (respS) netSR += half else netSR -= half
                    } else {
                        when {
                            propS -> {
                                sPers += m.monto
                                if (real) sPersReales += m.monto
                                if (respR) netSR -= m.monto // gasto de Santiago pagado desde cuenta de Rocío
                            }
                            propR -> {
                                rPers += m.monto
                                if (real) rPersReales += m.monto
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
                            // "Es de Rocío": la plata pasa a ser de Rocío -> regalo (baja el patrimonio
                            // de Santiago) y la deuda no se toca. Antes del corte, primero saldaba lo que
                            // Rocío tuviera estacionado en la cuenta de Santiago; hoy eso es una
                            // Devolución explícita.
                            propR -> {
                                val devolucion = if (saldaDeudaPrimero(m)) minOf(m.monto, maxOf(0.0, -netSR)) else 0.0
                                netSR += devolucion
                                sTransfers += (m.monto - devolucion) // porción regalada
                            }
                        }
                    } else {
                        if (efec) { rEfec -= m.monto; sEfec += m.monto } else { rVirt -= m.monto; sVirt += m.monto }
                        when {
                            propR -> netSR -= m.monto
                            propS -> {
                                val devolucion = if (saldaDeudaPrimero(m)) minOf(m.monto, maxOf(0.0, netSR)) else 0.0
                                netSR -= devolucion
                                rTransfers += (m.monto - devolucion)
                            }
                        }
                    }
                }

                // Pago de deuda: la plata sale de la cuenta del responsable hacia la del otro y cancela
                // lo que el otro tenía estacionado en la cuenta del responsable, sin cambio de
                // patrimonio. El excedente sobre la deuda (solo posible cargando a mano: la UI lo capa)
                // es regalo, igual que en la transferencia vieja.
                "devolución", "devolucion" -> {
                    if (respS) {
                        if (efec) { sEfec -= m.monto; rEfec += m.monto } else { sVirt -= m.monto; rVirt += m.monto }
                        val pagado = minOf(m.monto, maxOf(0.0, -netSR)) // rEnSantiago
                        netSR += pagado
                        sTransfers += (m.monto - pagado)
                    } else {
                        if (efec) { rEfec -= m.monto; sEfec += m.monto } else { rVirt -= m.monto; sVirt += m.monto }
                        val pagado = minOf(m.monto, maxOf(0.0, netSR)) // sEnRocio
                        netSR -= pagado
                        rTransfers += (m.monto - pagado)
                    }
                }

                // Cambio de dinero: el responsable entrega el medio de `metodoPago` y recibe el otro.
                // Los dos buckets de cada uno se cruzan; ni el pozo, ni los saldos, ni la propiedad
                // cruzada cambian.
                "cambio" -> {
                    val entregaS = if (respS) m.monto else -m.monto // lo que Santiago entrega del medio elegido
                    if (efec) {
                        sEfec -= entregaS; sVirt += entregaS
                        rEfec += entregaS; rVirt -= entregaS
                    } else {
                        sVirt -= entregaS; sEfec += entregaS
                        rVirt += entregaS; rEfec -= entregaS
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
            rocioExterno = rExterno,
            santiagoIngresos = sIngresos,
            rocioIngresos = rIngresos,
            santiagoGastosPersonalesReales = sPersReales,
            rocioGastosPersonalesReales = rPersReales,
            gastosComunesReales = comunesReales
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

    /** Gastos de una persona partidos por evitabilidad (tablero "Gastos acumulados"). */
    data class GastosPorEvitabilidad(val necesario: Double, val evitable: Double) {
        val total: Double get() = necesario + evitable
    }

    /**
     * Suma lo que [slotKey] gastó en [movs], separando lo no evitable de lo evitable. Mismo criterio
     * de atribución que el resto de Métricas ([porcionDelGasto]: comunes 50/50) y mismas exclusiones
     * que los gastos de Inicio ([esGastoReal]: sin correcciones).
     */
    fun gastosPorEvitabilidad(movs: List<Movement>, slotKey: String): GastosPorEvitabilidad {
        var necesario = 0.0
        var evitable = 0.0
        for (m in movs) {
            if (!esGastoReal(m)) continue
            val porcion = porcionDelGasto(m, slotKey)
            if (m.evitable) evitable += porcion else necesario += porcion
        }
        return GastosPorEvitabilidad(necesario, evitable)
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
