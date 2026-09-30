package com.example

import com.example.data.Movement
import com.example.ui.AccountingEngine
import com.example.ui.OpeningBalance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests del motor contable puro [AccountingEngine]. No requiere Android ni Robolectric.
 */
class AccountingLogicTest {

    private val delta = 0.001

    @Test
    fun ejemploDeLosCienMil() {
        // Ejemplo del usuario:
        // 1) Santiago aporta 100k a su cuenta virtual.
        // 2) Santiago transfiere 100k a Rocío, pero el dinero SIGUE siendo de Santiago.
        // 3) Compra común de 40k desde la cuenta de Rocío (20k cada uno).
        val movs = listOf(
            Movement(fecha = "2026-06-01", monto = 100000.0, tipo = "Aporte", categoria = "Sueldo Santiago",
                responsable = "Santiago", propietario = "Santiago", esComun = false, metodoPago = "Billetera Virtual"),
            Movement(fecha = "2026-06-02", monto = 100000.0, tipo = "Transferencia", categoria = "Ajuste",
                responsable = "Santiago", propietario = "Santiago", esComun = false, metodoPago = "Billetera Virtual"),
            Movement(fecha = "2026-06-03", monto = 40000.0, tipo = "Gasto", categoria = "Super",
                responsable = "Rocío", propietario = "Ambos", esComun = true, metodoPago = "Billetera Virtual")
        )

        val b = AccountingEngine.compute(movs)

        // Físico: Santiago 0, Rocío 60k (100k recibidos - 40k gastados)
        assertEquals(0.0, b.santiagoEnMano, delta)
        assertEquals(60000.0, b.rocioEnMano, delta)
        // Santiago mantiene 80k propios dentro de la cuenta de Rocío ("recuperé 80k")
        assertEquals(80000.0, b.sEnRocio, delta)
        assertEquals(80000.0, b.santiagoExterno, delta)
        assertEquals(-80000.0, b.rocioExterno, delta)
        // Patrimonios
        assertEquals(80000.0, b.santiagoSaldoFinal, delta)
        assertEquals(-20000.0, b.rocioSaldoFinal, delta)
        // Pozo total = dinero físico existente = 60k
        assertEquals(60000.0, b.totalPozo, delta)
    }

    @Test
    fun gastoComunSimpleGeneraReclamo() {
        // Santiago paga 5960 de un gasto común. Rocío le debe la mitad (2980).
        val movs = listOf(
            Movement(fecha = "2026-05-30", monto = 5960.0, tipo = "Gasto", categoria = "Otros comunes",
                responsable = "Santiago", propietario = "Ambos", esComun = true, metodoPago = "Billetera Virtual")
        )
        val b = AccountingEngine.compute(movs)
        assertEquals(2980.0, b.santiagoExterno, delta)
        assertEquals(-2980.0, b.rocioExterno, delta)
        assertEquals(2980.0, b.sEnRocio, delta)
        assertEquals(0.0, b.rEnSantiago, delta)
    }

    @Test
    fun efectivoSeArrastraSeparadoDeVirtual() {
        // Bug 0: el efectivo NO debe agruparse como virtual al arrastrar.
        val opening = AccountingEngine.opening(
            listOf(
                Movement(fecha = "2026-06-07", monto = 145800.0, tipo = "Aporte", categoria = "Otros",
                    responsable = "Santiago", propietario = "Santiago", esComun = false, metodoPago = "Efectivo"),
                Movement(fecha = "2026-06-13", monto = 43771.0, tipo = "Gasto", categoria = "Otros",
                    responsable = "Santiago", propietario = "Santiago", esComun = false, metodoPago = "Efectivo"),
                Movement(fecha = "2026-06-08", monto = 50000.0, tipo = "Aporte", categoria = "Otros",
                    responsable = "Santiago", propietario = "Santiago", esComun = false, metodoPago = "Billetera Virtual")
            )
        )
        assertEquals(102029.0, opening.santiagoEfectivo, delta)   // 145800 - 43771
        assertEquals(50000.0, opening.santiagoVirtual, delta)

        // Al computar el mes siguiente con este arrastre, el efectivo se conserva.
        val b = AccountingEngine.compute(emptyList(), opening)
        assertEquals(102029.0, b.santiagoEfectivo, delta)
        assertEquals(50000.0, b.santiagoVirtual, delta)
        assertEquals(152029.0, b.santiagoSaldoFinal, delta)
    }

    @Test
    fun transferenciaPropiaNoCambiaPozoNiPatrimonio() {
        val movs = listOf(
            Movement(fecha = "2026-06-01", monto = 100000.0, tipo = "Aporte", categoria = "Sueldo",
                responsable = "Santiago", propietario = "Santiago", esComun = false, metodoPago = "Billetera Virtual"),
            Movement(fecha = "2026-06-02", monto = 30000.0, tipo = "Transferencia", categoria = "Ajuste",
                responsable = "Santiago", propietario = "Santiago", esComun = false, metodoPago = "Billetera Virtual")
        )
        val b = AccountingEngine.compute(movs)
        // El dinero se movió a la cuenta de Rocío pero sigue siendo de Santiago.
        assertEquals(100000.0, b.santiagoSaldoFinal, delta)
        assertEquals(0.0, b.rocioSaldoFinal, delta)
        assertEquals(30000.0, b.sEnRocio, delta)
        assertEquals(70000.0, b.santiagoEnMano, delta)
        assertEquals(30000.0, b.rocioEnMano, delta)
    }

    @Test
    fun transferenciaRegaloSinCruceCambiaDeDueno() {
        // Sin cruce previo: transferir marcando "es del otro" es un regalo real.
        val movs = listOf(
            Movement(fecha = "2026-06-01", monto = 100000.0, tipo = "Aporte", categoria = "Sueldo",
                responsable = "Santiago", propietario = "Santiago", esComun = false, metodoPago = "Billetera Virtual"),
            Movement(fecha = "2026-06-02", monto = 30000.0, tipo = "Transferencia", categoria = "Regalo",
                responsable = "Santiago", propietario = "Rocío", esComun = false, metodoPago = "Billetera Virtual")
        )
        val b = AccountingEngine.compute(movs)
        assertEquals(70000.0, b.santiagoSaldoFinal, delta)
        assertEquals(30000.0, b.rocioSaldoFinal, delta)
        assertEquals(0.0, b.santiagoExterno, delta)
    }

    @Test
    fun transferenciaDevuelveDineroCruzado() {
        // Escenario del usuario: Santiago tiene plata en la cuenta de Rocío; Rocío se la transfiere
        // de vuelta (marcándola como de Santiago). Debe DESCONTAR del dinero cruzado, no ser regalo.
        val movs = listOf(
            Movement(fecha = "2026-06-01", monto = 100000.0, tipo = "Aporte", categoria = "Sueldo",
                responsable = "Santiago", propietario = "Santiago", esComun = false, metodoPago = "Billetera Virtual"),
            Movement(fecha = "2026-06-02", monto = 100000.0, tipo = "Transferencia", categoria = "Ajuste",
                responsable = "Santiago", propietario = "Santiago", esComun = false, metodoPago = "Billetera Virtual"),
            Movement(fecha = "2026-06-03", monto = 40000.0, tipo = "Transferencia", categoria = "Ajuste",
                responsable = "Rocío", propietario = "Santiago", esComun = false, metodoPago = "Billetera Virtual")
        )
        val b = AccountingEngine.compute(movs)
        assertEquals(60000.0, b.sEnRocio, delta)         // 100k parkeados - 40k devueltos
        assertEquals(60000.0, b.santiagoExterno, delta)
        // Patrimonios sin cambios (solo se reubicó plata)
        assertEquals(100000.0, b.santiagoSaldoFinal, delta)
        assertEquals(0.0, b.rocioSaldoFinal, delta)
        assertEquals(40000.0, b.santiagoEnMano, delta)
        assertEquals(60000.0, b.rocioEnMano, delta)
    }

    @Test
    fun transferenciaMixtaDevuelveYRegala() {
        // Rocío tiene 30k en la cuenta de Santiago; Santiago le transfiere 50k marcándolos como de ella.
        // 30k son devolución (sin cambio patrimonial) y 20k son regalo (cambia patrimonio).
        val movs = listOf(
            Movement(fecha = "2026-06-01", monto = 30000.0, tipo = "Aporte", categoria = "Sueldo Rocío",
                responsable = "Santiago", propietario = "Rocío", esComun = false, metodoPago = "Billetera Virtual"),
            Movement(fecha = "2026-06-02", monto = 100000.0, tipo = "Aporte", categoria = "Sueldo",
                responsable = "Santiago", propietario = "Santiago", esComun = false, metodoPago = "Billetera Virtual"),
            Movement(fecha = "2026-06-03", monto = 50000.0, tipo = "Transferencia", categoria = "Ajuste",
                responsable = "Santiago", propietario = "Rocío", esComun = false, metodoPago = "Billetera Virtual")
        )
        val b = AccountingEngine.compute(movs)
        assertEquals(0.0, b.santiagoExterno, delta)       // se saldó el cruce
        assertEquals(80000.0, b.santiagoSaldoFinal, delta) // 100k - 20k regalados
        assertEquals(50000.0, b.rocioSaldoFinal, delta)    // 30k + 20k
    }

    @Test
    fun gastoPersonalDeUnoPagadoDesdeCuentaDelOtro() {
        // Escenario del usuario: Rocío hace un gasto que en realidad es un pago de Santiago.
        val movs = listOf(
            Movement(fecha = "2026-06-01", monto = 50000.0, tipo = "Transferencia", categoria = "Ajuste",
                responsable = "Santiago", propietario = "Santiago", esComun = false, metodoPago = "Billetera Virtual"),
            Movement(fecha = "2026-06-02", monto = 20000.0, tipo = "Gasto", categoria = "Otros",
                responsable = "Rocío", propietario = "Santiago", esComun = false, metodoPago = "Billetera Virtual")
        )
        val b = AccountingEngine.compute(movs)
        // Santiago parkeó 50k en Rocío; su gasto de 20k desde ahí baja su dinero cruzado a 30k.
        assertEquals(30000.0, b.sEnRocio, delta)
        assertEquals(20000.0, b.santiagoGastosPersonales, delta)
    }

    @Test
    fun invariantesSeMantienenConMezclaYArrastre() {
        val opening = OpeningBalance(
            santiagoEfectivo = 14900.0, santiagoVirtual = 590295.0,
            rocioEfectivo = 0.0, rocioVirtual = 19465.0, netSantiagoEnRocio = 2980.0
        )
        val movs = listOf(
            Movement(fecha = "2026-07-01", monto = 300000.0, tipo = "Aporte", categoria = "Sueldo",
                responsable = "Santiago", propietario = "Santiago", esComun = false, metodoPago = "Efectivo"),
            Movement(fecha = "2026-07-02", monto = 50000.0, tipo = "Aporte", categoria = "Sueldo Rocío",
                responsable = "Rocío", propietario = "Santiago", esComun = false, metodoPago = "Billetera Virtual"),
            Movement(fecha = "2026-07-03", monto = 40000.0, tipo = "Gasto", categoria = "Super",
                responsable = "Rocío", propietario = "Ambos", esComun = true, metodoPago = "Billetera Virtual"),
            Movement(fecha = "2026-07-04", monto = 12000.0, tipo = "Gasto", categoria = "Ropa",
                responsable = "Santiago", propietario = "Santiago", esComun = false, metodoPago = "Efectivo"),
            Movement(fecha = "2026-07-05", monto = 8000.0, tipo = "Gasto", categoria = "Otros",
                responsable = "Santiago", propietario = "Rocío", esComun = false, metodoPago = "Billetera Virtual"),
            Movement(fecha = "2026-07-06", monto = 70000.0, tipo = "Transferencia", categoria = "Ajuste",
                responsable = "Santiago", propietario = "Santiago", esComun = false, metodoPago = "Billetera Virtual"),
            Movement(fecha = "2026-07-07", monto = 25000.0, tipo = "Transferencia", categoria = "Ajuste",
                responsable = "Rocío", propietario = "Santiago", esComun = false, metodoPago = "Billetera Virtual"),
            Movement(fecha = "2026-07-08", monto = 15000.0, tipo = "Transferencia", categoria = "Regalo",
                responsable = "Santiago", propietario = "Rocío", esComun = false, metodoPago = "Efectivo")
        )
        val b = AccountingEngine.compute(movs, opening)

        // Invariante 1: el pozo total = todo el dinero físico existente
        assertEquals(b.santiagoEnMano + b.rocioEnMano, b.totalPozo, delta)
        // Invariante 2: pozo = suma de patrimonios
        assertEquals(b.santiagoSaldoFinal + b.rocioSaldoFinal, b.totalPozo, delta)
        // Invariante 3: posición externa simétrica
        assertEquals(-b.rocioExterno, b.santiagoExterno, delta)
        // Invariante 4: saldoFinal = enMano + externo, para cada uno
        assertEquals(b.santiagoEnMano + b.santiagoExterno, b.santiagoSaldoFinal, delta)
        assertEquals(b.rocioEnMano + b.rocioExterno, b.rocioSaldoFinal, delta)
        // Invariante 5: enMano = efectivo + virtual
        assertEquals(b.santiagoEfectivo + b.santiagoVirtual, b.santiagoEnMano, delta)
        assertEquals(b.rocioEfectivo + b.rocioVirtual, b.rocioEnMano, delta)

        // Invariante 6: el pozo = arrastre + aportes del mes - gastos del mes (las transferencias no cambian el pozo)
        val pozoOpening = opening.santiagoEfectivo + opening.santiagoVirtual + opening.rocioEfectivo + opening.rocioVirtual
        val aportes = movs.filter { it.tipo == "Aporte" }.sumOf { it.monto }
        val gastos = movs.filter { it.tipo == "Gasto" }.sumOf { it.monto }
        assertEquals(pozoOpening + aportes - gastos, b.totalPozo, delta)
    }

    @Test
    fun elOrdenDeEntradaNoAlteraElResultado() {
        // El motor ordena cronológicamente; pasar la lista al revés debe dar lo mismo.
        val movs = listOf(
            Movement(fecha = "2026-06-01", monto = 100000.0, tipo = "Aporte", categoria = "Sueldo",
                responsable = "Santiago", propietario = "Santiago", esComun = false, metodoPago = "Billetera Virtual"),
            Movement(fecha = "2026-06-02", monto = 100000.0, tipo = "Transferencia", categoria = "Ajuste",
                responsable = "Santiago", propietario = "Santiago", esComun = false, metodoPago = "Billetera Virtual"),
            Movement(fecha = "2026-06-03", monto = 40000.0, tipo = "Transferencia", categoria = "Ajuste",
                responsable = "Rocío", propietario = "Santiago", esComun = false, metodoPago = "Billetera Virtual")
        )
        val asc = AccountingEngine.compute(movs)
        val desc = AccountingEngine.compute(movs.reversed())
        assertEquals(asc.santiagoExterno, desc.santiagoExterno, delta)
        assertEquals(asc.santiagoSaldoFinal, desc.santiagoSaldoFinal, delta)
    }

    // ---------------------------------------------------------------------------------------------
    // Saldo inicial materializado (filas de apertura)
    // ---------------------------------------------------------------------------------------------

    private fun assertOpeningEquals(esperado: OpeningBalance, real: OpeningBalance) {
        assertEquals(esperado.santiagoEfectivo, real.santiagoEfectivo, delta)
        assertEquals(esperado.santiagoVirtual, real.santiagoVirtual, delta)
        assertEquals(esperado.rocioEfectivo, real.rocioEfectivo, delta)
        assertEquals(esperado.rocioVirtual, real.rocioVirtual, delta)
        assertEquals(esperado.netSantiagoEnRocio, real.netSantiagoEnRocio, delta)
    }

    @Test
    fun lasFilasDeAperturaHacenRoundTripExacto() {
        // Los tres signos posibles de la propiedad cruzada.
        val casos = listOf(
            OpeningBalance(60450.0, 2529770.23, 10500.0, 25117.96, 0.0),
            OpeningBalance(55450.0, 3540917.09, 11400.0, 57455.96, 60000.0),   // Santiago en Rocío
            OpeningBalance(1000.0, 200000.0, 500.0, 3000.0, -45000.0)          // Rocío en Santiago
        )
        for (o in casos) {
            val filas = AccountingEngine.openingRowsFor("2026-09", o)
            assertOpeningEquals(o, AccountingEngine.openingFromRows(filas))
        }
    }

    @Test
    fun laAperturaConservaElPrestamoCruzado() {
        // El caso real: Santiago le prestó 60k a Rocío y quedaron estacionados en su cuenta.
        // Rocío tiene 69.955,96 físicos pero solo 9.955,96 son suyos.
        val cierre = OpeningBalance(
            santiagoEfectivo = 55450.0, santiagoVirtual = 3540917.09,
            rocioEfectivo = 12500.0, rocioVirtual = 57455.96,
            netSantiagoEnRocio = 60000.0
        )
        val filas = AccountingEngine.openingRowsFor("2026-09", cierre)

        // Se necesita una fila extra para el dinero de Santiago que está en la cuenta de Rocío.
        assertEquals(5, filas.size)
        val cruzada = filas.single { it.responsable == "Rocío" && it.propietario == "Santiago" }
        assertEquals(60000.0, cruzada.monto, delta)

        // Septiembre arranca solo con esas filas: el préstamo sobrevive al cambio de mes.
        val b = AccountingEngine.compute(emptyList(), AccountingEngine.openingFromRows(filas))
        assertEquals(69955.96, b.rocioEnMano, delta)
        assertEquals(-60000.0, b.rocioExterno, delta)
        assertEquals(9955.96, b.rocioSaldoFinal, delta)
        assertEquals(60000.0, b.sEnRocio, delta)
    }

    @Test
    fun conAperturaSePuedenPurgarLosMesesAnteriores() {
        val junio = listOf(
            Movement(fecha = "2026-06-05", monto = 500000.0, tipo = "Aporte", categoria = "Sueldo",
                responsable = "Santiago", propietario = "Santiago", esComun = false, metodoPago = "Billetera Virtual"),
            Movement(fecha = "2026-06-10", monto = 80000.0, tipo = "Transferencia", categoria = "Otros",
                responsable = "Santiago", propietario = "Santiago", esComun = false, metodoPago = "Billetera Virtual"),
            Movement(fecha = "2026-06-20", monto = 30000.0, tipo = "Gasto", categoria = "Super",
                responsable = "Rocío", propietario = "Rocío", esComun = false, metodoPago = "Billetera Virtual")
        )
        val julio = listOf(
            Movement(fecha = "2026-07-03", monto = 12000.0, tipo = "Gasto", categoria = "Gustos",
                responsable = "Rocío", propietario = "Rocío", esComun = false, metodoPago = "Efectivo")
        )

        // Sin apertura: julio se deriva replayando junio (comportamiento <= 7.1).
        val derivada = AccountingEngine.openingFor(junio + julio, "2026-07")
        assertEquals(80000.0, derivada.netSantiagoEnRocio, delta)

        // Materializamos la apertura de julio y borramos junio entero.
        val apertura = AccountingEngine.openingRowsFor("2026-07", derivada)
        val soloJulio = AccountingEngine.openingFor(julio + apertura, "2026-07")
        assertOpeningEquals(derivada, soloJulio)

        // Y agosto, que no tiene apertura propia, se ancla en la de julio sin ver junio.
        val agosto = listOf(
            Movement(fecha = "2026-08-02", monto = 5000.0, tipo = "Gasto", categoria = "Gustos",
                responsable = "Santiago", propietario = "Santiago", esComun = false, metodoPago = "Efectivo")
        )
        assertOpeningEquals(
            AccountingEngine.openingFor(junio + julio + agosto, "2026-08"),
            AccountingEngine.openingFor(julio + apertura + agosto, "2026-08")
        )
    }

    @Test
    fun laAperturaNoSumaALosFlujosDelMes() {
        val apertura = AccountingEngine.openingRowsFor(
            "2026-09", OpeningBalance(0.0, 300000.0, 0.0, 50000.0, 0.0)
        )
        val gasto = Movement(fecha = "2026-09-04", monto = 7000.0, tipo = "Gasto", categoria = "Gustos",
            responsable = "Rocío", propietario = "Rocío", esComun = false, metodoPago = "Billetera Virtual")

        // Aunque las filas de apertura se cuelen en la lista del mes, compute() las ignora.
        val b = AccountingEngine.compute(apertura + gasto, AccountingEngine.openingFromRows(apertura))
        assertEquals(0.0, b.totalAportesMes, delta)
        assertEquals(7000.0, b.totalGastosMes, delta)
        assertEquals(343000.0, b.totalPozo, delta)
    }

    @Test
    fun lasFilasLegacyDeSaldoInicialSiguenIgnorandose() {
        // Versiones <= 7.1 inyectaban un Aporte "Saldo inicial" por persona. Conservarlas duplicaría.
        val legacy = Movement(fecha = "2026-07-01", monto = 608175.29, tipo = "Aporte",
            categoria = "Saldo inicial", responsable = "Santiago", propietario = "Santiago",
            esComun = false, metodoPago = "Billetera Virtual")
        val gasto = Movement(fecha = "2026-07-05", monto = 1000.0, tipo = "Gasto", categoria = "Gustos",
            responsable = "Santiago", propietario = "Santiago", esComun = false, metodoPago = "Billetera Virtual")

        assertEquals(true, AccountingEngine.isLegacyCarryover(legacy))
        assertEquals(false, AccountingEngine.isOpeningRow(legacy))

        val b = AccountingEngine.compute(listOf(legacy, gasto))
        assertEquals(-1000.0, b.santiagoSaldoFinal, delta)
        assertEquals(0.0, b.totalAportesMes, delta)

        // Y no cuentan como ancla: openingFor sigue replayando.
        val o = AccountingEngine.openingFor(listOf(legacy, gasto), "2026-08")
        assertEquals(-1000.0, o.santiagoVirtual, delta)
    }

    // --- Condonación (perdón de deuda) ------------------------------------------------------

    /**
     * Escenario real que motivó el tipo: Rocío tenía 100k de Santiago (ya gastados) y Santiago se
     * los perdona. Cargarlo como transferencia hacía dos cosas mal —le sacaba 100k físicos a
     * Santiago y encima dejaba la deuda en pie—, porque la devolución de la transferencia mira la
     * propiedad cruzada en el sentido contrario.
     */
    @Test
    fun perdonarDeudaCancelaElCruzadoSinMoverPlata() {
        val previos = listOf(
            Movement(fecha = "2026-09-01", monto = 100000.0, tipo = "Aporte", categoria = "Sueldo",
                responsable = "Santiago", propietario = "Santiago", esComun = false, metodoPago = "Billetera Virtual"),
            Movement(fecha = "2026-09-02", monto = 100000.0, tipo = "Transferencia", categoria = "Ajuste",
                responsable = "Santiago", propietario = "Santiago", esComun = false, metodoPago = "Billetera Virtual"),
            Movement(fecha = "2026-09-03", monto = 100000.0, tipo = "Gasto", categoria = "Gustos",
                responsable = "Rocío", propietario = "Rocío", esComun = false, metodoPago = "Billetera Virtual"),
            Movement(fecha = "2026-09-04", monto = 30000.0, tipo = "Aporte", categoria = "Sueldo",
                responsable = "Rocío", propietario = "Rocío", esComun = false, metodoPago = "Efectivo")
        )
        val antes = AccountingEngine.compute(previos)
        assertEquals(100000.0, antes.santiagoExterno, delta)
        assertEquals(-70000.0, antes.rocioSaldoFinal, delta)

        val perdon = Movement(fecha = "2026-09-05", monto = 100000.0,
            tipo = AccountingEngine.TIPO_CONDONACION, categoria = "Perdón de deuda",
            responsable = "Santiago", propietario = "Rocío", esComun = false, metodoPago = "Billetera Virtual")
        val b = AccountingEngine.compute(previos + perdon)

        // La deuda desaparece...
        assertEquals(0.0, b.santiagoExterno, delta)
        assertEquals(0.0, b.sEnRocio, delta)
        assertEquals(0.0, b.rEnSantiago, delta)
        // ...sin que se mueva un peso: el físico de cada uno queda igual que antes.
        assertEquals(antes.santiagoEnMano, b.santiagoEnMano, delta)
        assertEquals(antes.rocioEnMano, b.rocioEnMano, delta)
        assertEquals(antes.totalPozo, b.totalPozo, delta)
        // Patrimonio: Santiago resigna el reclamo, Rocío se queda con lo suyo.
        assertEquals(0.0, b.santiagoSaldoFinal, delta)
        assertEquals(30000.0, b.rocioSaldoFinal, delta)
        // No es un flujo del periodo (el pozo no cambió), pero sí plata regalada.
        assertEquals(antes.totalGastosMes, b.totalGastosMes, delta)
        assertEquals(antes.totalAportesMes, b.totalAportesMes, delta)
        assertEquals(100000.0, b.santiagoTransfersEnviadas, delta)
    }

    @Test
    fun perdonarDeMasNoGeneraDeudaEnElSentidoContrario() {
        val movs = listOf(
            Movement(fecha = "2026-09-01", monto = 40000.0, tipo = "Gasto", categoria = "Super",
                responsable = "Santiago", propietario = "Ambos", esComun = true, metodoPago = "Billetera Virtual"),
            // Rocío le debe 20000, pero se perdonan 500000.
            Movement(fecha = "2026-09-02", monto = 500000.0, tipo = AccountingEngine.TIPO_CONDONACION,
                categoria = "Perdón de deuda", responsable = "Santiago", propietario = "Rocío",
                esComun = false, metodoPago = "Billetera Virtual")
        )
        val b = AccountingEngine.compute(movs)
        assertEquals(0.0, b.santiagoExterno, delta)
        assertEquals(0.0, b.rEnSantiago, delta)
        // Solo cuenta como regalado lo que realmente se perdonó.
        assertEquals(20000.0, b.santiagoTransfersEnviadas, delta)
    }

    @Test
    fun perdonarSinDeudaAFavorNoHaceNada() {
        val movs = listOf(
            // La deuda es a favor de Santiago; el que perdona es Rocío, que no tiene nada que perdonar.
            Movement(fecha = "2026-09-01", monto = 40000.0, tipo = "Gasto", categoria = "Super",
                responsable = "Santiago", propietario = "Ambos", esComun = true, metodoPago = "Billetera Virtual"),
            Movement(fecha = "2026-09-02", monto = 20000.0, tipo = AccountingEngine.TIPO_CONDONACION,
                categoria = "Perdón de deuda", responsable = "Rocío", propietario = "Santiago",
                esComun = false, metodoPago = "Billetera Virtual")
        )
        val b = AccountingEngine.compute(movs)
        assertEquals(20000.0, b.santiagoExterno, delta)
        assertEquals(0.0, b.rocioTransfersEnviadas, delta)
    }

    @Test
    fun elPerdonSobreviveAlCierreDelMes() {
        val septiembre = listOf(
            Movement(fecha = "2026-09-01", monto = 200000.0, tipo = "Aporte", categoria = "Sueldo",
                responsable = "Santiago", propietario = "Santiago", esComun = false, metodoPago = "Billetera Virtual"),
            Movement(fecha = "2026-09-02", monto = 60000.0, tipo = "Transferencia", categoria = "Ajuste",
                responsable = "Santiago", propietario = "Santiago", esComun = false, metodoPago = "Billetera Virtual"),
            Movement(fecha = "2026-09-03", monto = 60000.0, tipo = AccountingEngine.TIPO_CONDONACION,
                categoria = "Perdón de deuda", responsable = "Santiago", propietario = "Rocío",
                esComun = false, metodoPago = "Billetera Virtual")
        )
        val apertura = AccountingEngine.openingRowsFor("2026-10", AccountingEngine.opening(septiembre))

        // Sin propiedad cruzada, la apertura de octubre son solo las 4 filas propias.
        assertEquals(4, apertura.size)
        val b = AccountingEngine.compute(emptyList(), AccountingEngine.openingFromRows(apertura))
        assertEquals(0.0, b.santiagoExterno, delta)
        assertEquals(140000.0, b.santiagoSaldoFinal, delta)
        assertEquals(60000.0, b.rocioSaldoFinal, delta)
    }

    @Test
    fun laCondonacionSeReconoceSinTilde() {
        // La app siempre escribe "Condonación", pero una fila cargada a mano en la planilla puede venir sin tilde.
        val sinTilde = Movement(fecha = "2026-09-02", monto = 20000.0, tipo = "Condonacion",
            categoria = "Perdón de deuda", responsable = "Santiago", propietario = "Rocío",
            esComun = false, metodoPago = "Billetera Virtual")
        val comun = Movement(fecha = "2026-09-01", monto = 40000.0, tipo = "Gasto", categoria = "Super",
            responsable = "Santiago", propietario = "Ambos", esComun = true, metodoPago = "Billetera Virtual")

        assertTrue(AccountingEngine.isCondonacion(sinTilde))
        assertEquals(0.0, AccountingEngine.compute(listOf(comun, sinTilde)).santiagoExterno, delta)
    }

    // --- Guard del recálculo de la apertura -------------------------------------------------

    @Test
    fun materializarUnMesSinAperturaSiempreSePermite() {
        val julio = listOf(
            Movement(fecha = "2026-07-05", monto = 500000.0, tipo = "Aporte", categoria = "Sueldo",
                responsable = "Santiago", propietario = "Santiago", esComun = false, metodoPago = "Billetera Virtual")
        )
        // Caso normal: agosto todavía no tiene apertura.
        assertTrue(AccountingEngine.chequearRecalculo(julio, "2026-08").permitido)
        // Y el primerísimo mes de la planilla, cuyo arrastre legítimamente es cero, tampoco molesta.
        assertTrue(AccountingEngine.chequearRecalculo(emptyList(), "2026-07").permitido)
    }

    @Test
    fun noSeRecalculaSiSePurgaronTodosLosMesesAnteriores() {
        // Septiembre tiene su apertura y es la única hoja que queda: recalcular daría cero y se
        // llevaría puesto todo el arrastre.
        val apertura = AccountingEngine.openingRowsFor(
            "2026-09", OpeningBalance(46650.0, 2104193.98, 108600.0, 82568.33, 372067.42)
        )
        val septiembre = apertura + Movement(fecha = "2026-09-03", monto = 1900.0, tipo = "Gasto",
            categoria = "Consumo inmediato", responsable = "Rocío", propietario = "Rocío",
            esComun = false, metodoPago = "Billetera Virtual")

        val chequeo = AccountingEngine.chequearRecalculo(septiembre, "2026-09")
        assertFalse(chequeo.permitido)
        assertTrue(chequeo.motivo.contains("2026-09"))

        // Con el mes anterior presente, el mismo recálculo se permite.
        val agosto = listOf(
            Movement(fecha = "2026-08-10", monto = 2237412.31, tipo = "Aporte", categoria = "Sueldo",
                responsable = "Santiago", propietario = "Santiago", esComun = false, metodoPago = "Billetera Virtual")
        )
        assertTrue(AccountingEngine.chequearRecalculo(septiembre + agosto, "2026-09").permitido)
    }

    @Test
    fun noSePisaUnaAperturaConSaldoPorUnaEnCero() {
        // Purga parcial: queda un mes anterior, pero solo con movimientos que se anulan entre sí,
        // así que el recálculo da cero. La apertura escrita no lo es: no se toca.
        val agosto = listOf(
            Movement(fecha = "2026-08-10", monto = 1000.0, tipo = "Aporte", categoria = "Sueldo",
                responsable = "Santiago", propietario = "Santiago", esComun = false, metodoPago = "Efectivo"),
            Movement(fecha = "2026-08-11", monto = 1000.0, tipo = "Gasto", categoria = "Gustos",
                responsable = "Santiago", propietario = "Santiago", esComun = false, metodoPago = "Efectivo")
        )
        val septiembre = AccountingEngine.openingRowsFor("2026-09", OpeningBalance(0.0, 2104193.98, 0.0, 0.0, 0.0))

        assertFalse(AccountingEngine.chequearRecalculo(agosto + septiembre, "2026-09").permitido)
    }

    // --- v7.7: cambio de dinero, devolución y transferencias sin devolución automática ------

    private fun mov(
        fecha: String, monto: Double, tipo: String, responsable: String, propietario: String = responsable,
        metodo: String = "Billetera Virtual", categoria: String = "Otros", esComun: Boolean = false,
        evitable: Boolean = false
    ) = Movement(
        fecha = fecha, monto = monto, tipo = tipo, categoria = categoria, responsable = responsable,
        propietario = propietario, esComun = esComun, metodoPago = metodo, evitable = evitable
    )

    /** Rocío tiene 300k de Santiago en su cuenta ("Ro debe 300k"), como en septiembre 2026. */
    private val rocioDebe300k = listOf(
        mov("2026-09-01 10:00", 300000.0, "Aporte", "Santiago", categoria = "Ingreso"),
        mov("2026-09-02 10:00", 300000.0, "Transferencia", "Santiago", "Santiago")
    )

    @Test
    fun cambioDeDineroCruzaLosBucketsSinTocarSaldosNiDeuda() {
        // Rocío le da 50k en efectivo a Santiago y él le transfiere: lo carga Santiago, que entrega
        // la transferencia ("Billetera Virtual") y recibe el efectivo.
        val previos = listOf(
            mov("2026-10-01 09:00", 100000.0, "Aporte", "Santiago", categoria = "Ingreso"),
            mov("2026-10-01 09:01", 100000.0, "Aporte", "Rocío", metodo = "Efectivo", categoria = "Ingreso")
        )
        val antes = AccountingEngine.compute(previos)
        val cambio = mov("2026-10-02 10:00", 50000.0, AccountingEngine.TIPO_CAMBIO, "Santiago", metodo = "Billetera Virtual")
        val b = AccountingEngine.compute(previos + cambio)

        assertEquals(50000.0, b.santiagoVirtual, delta)
        assertEquals(50000.0, b.santiagoEfectivo, delta)
        assertEquals(50000.0, b.rocioEfectivo, delta)
        assertEquals(50000.0, b.rocioVirtual, delta)
        // Nadie gana ni pierde, nadie le debe nada a nadie, y no es un flujo del periodo.
        assertEquals(antes.santiagoSaldoFinal, b.santiagoSaldoFinal, delta)
        assertEquals(antes.rocioSaldoFinal, b.rocioSaldoFinal, delta)
        assertEquals(antes.totalPozo, b.totalPozo, delta)
        assertEquals(0.0, b.santiagoExterno, delta)
        assertEquals(antes.totalAportesMes, b.totalAportesMes, delta)
        assertEquals(0.0, b.totalGastosMes, delta)
        assertEquals(0.0, b.santiagoTransfersEnviadas + b.rocioTransfersEnviadas, delta)
    }

    @Test
    fun cambioDeDineroCargadoPorQuienEntregaEfectivo() {
        // El mismo canje cargado por Rocío: entrega efectivo, recibe la transferencia.
        val b = AccountingEngine.compute(listOf(
            mov("2026-10-02 10:00", 50000.0, AccountingEngine.TIPO_CAMBIO, "Rocío", metodo = "Efectivo")
        ))
        assertEquals(-50000.0, b.rocioEfectivo, delta)
        assertEquals(50000.0, b.rocioVirtual, delta)
        assertEquals(50000.0, b.santiagoEfectivo, delta)
        assertEquals(-50000.0, b.santiagoVirtual, delta)
        assertEquals(0.0, b.rocioSaldoFinal, delta)
        assertEquals(0.0, b.santiagoSaldoFinal, delta)
    }

    @Test
    fun transferenciaEsDelOtroDespuesDelCorteNoTocaLaDeuda() {
        // El problema que motivó el cambio: Rocío le transfiere 50k a Santiago como regalo y antes se
        // descontaba sí o sí de los 300k que le debe.
        val antes = AccountingEngine.compute(rocioDebe300k)
        val regalo = mov("2026-10-05 10:00", 50000.0, "Transferencia", "Rocío", "Santiago")
        val b = AccountingEngine.compute(rocioDebe300k + regalo)

        assertEquals(300000.0, b.santiagoExterno, delta)          // la deuda sigue entera
        assertEquals(antes.rocioEnMano - 50000.0, b.rocioEnMano, delta)
        assertEquals(antes.santiagoEnMano + 50000.0, b.santiagoEnMano, delta)
        assertEquals(antes.rocioSaldoFinal - 50000.0, b.rocioSaldoFinal, delta)
        assertEquals(antes.santiagoSaldoFinal + 50000.0, b.santiagoSaldoFinal, delta)
        assertEquals(50000.0, b.rocioTransfersEnviadas, delta)
    }

    @Test
    fun transferenciaEsDelOtroAnteriorAlCorteSigueSaldandoDeuda() {
        // Las anteriores al corte conservan la regla vieja: es el caso real del 06/08 (Santiago estaciona
        // 60k en la cuenta de Rocío y ella se los devuelve con una transferencia "es de Santiago").
        val agosto = listOf(
            mov("2026-08-03 19:34", 60000.0, "Transferencia", "Santiago", "Santiago"),
            mov("2026-08-06 21:48", 60000.0, "Transferencia", "Rocío", "Santiago")
        )
        assertTrue(agosto.last().fecha < AccountingEngine.CORTE_TRANSFERENCIA_SIN_DEVOLUCION)
        val b = AccountingEngine.compute(agosto)

        assertEquals(0.0, b.santiagoExterno, delta)
        assertEquals(0.0, b.rocioTransfersEnviadas, delta)
    }

    @Test
    fun transferenciaEsDelOtroDeSeptiembreYaNoSaldaDeuda() {
        // Reportado probando la 7.7 el 30/09: con el corte en octubre, dividir una cuenta a medias
        // ("es de Santiago") se descontaba de la deuda. Septiembre ya usa la regla nueva.
        val mitad = mov("2026-09-30 21:00", 20000.0, "Transferencia", "Rocío", "Santiago")
        val b = AccountingEngine.compute(rocioDebe300k + mitad)

        assertEquals(300000.0, b.santiagoExterno, delta)
        assertEquals(20000.0, b.rocioTransfersEnviadas, delta)
    }

    @Test
    fun transferenciaSiEsMiaSigueNeteandoContraLaDeuda() {
        // Decidido mantenerla: Rocío estaciona plata suya en la cuenta de Santiago y el neto baja.
        val estaciona = mov("2026-10-05 10:00", 72067.42, "Transferencia", "Rocío", "Rocío")
        val b = AccountingEngine.compute(rocioDebe300k + estaciona)
        assertEquals(300000.0 - 72067.42, b.santiagoExterno, delta)
        assertEquals(0.0, b.rocioTransfersEnviadas, delta)
    }

    @Test
    fun devolucionPagaLaDeudaSinCambioDePatrimonio() {
        val antes = AccountingEngine.compute(rocioDebe300k)
        val pago = mov("2026-10-05 10:00", 100000.0, AccountingEngine.TIPO_DEVOLUCION, "Rocío", "Santiago")
        val b = AccountingEngine.compute(rocioDebe300k + pago)

        assertEquals(200000.0, b.santiagoExterno, delta)
        assertEquals(antes.rocioVirtual - 100000.0, b.rocioVirtual, delta)
        assertEquals(antes.santiagoVirtual + 100000.0, b.santiagoVirtual, delta)
        assertEquals(antes.rocioSaldoFinal, b.rocioSaldoFinal, delta)
        assertEquals(antes.santiagoSaldoFinal, b.santiagoSaldoFinal, delta)
        assertEquals(0.0, b.rocioTransfersEnviadas, delta)
        assertEquals(0.0, b.totalGastosMes, delta)
    }

    @Test
    fun devolucionEnEfectivoMueveElEfectivo() {
        val pago = mov("2026-10-05 10:00", 100000.0, AccountingEngine.TIPO_DEVOLUCION, "Rocío", "Santiago", metodo = "Efectivo")
        val b = AccountingEngine.compute(rocioDebe300k + pago)
        assertEquals(-100000.0, b.rocioEfectivo, delta)
        assertEquals(100000.0, b.santiagoEfectivo, delta)
        assertEquals(200000.0, b.santiagoExterno, delta)
    }

    @Test
    fun devolucionDeMasElExcedenteEsRegaloYNoGeneraDeudaInversa() {
        // Solo posible cargando a mano en la planilla: la UI capa el monto a la deuda.
        val pago = mov("2026-10-05 10:00", 350000.0, AccountingEngine.TIPO_DEVOLUCION, "Rocío", "Santiago")
        val b = AccountingEngine.compute(rocioDebe300k + pago)
        assertEquals(0.0, b.santiagoExterno, delta)
        assertEquals(0.0, b.rEnSantiago, delta)
        assertEquals(50000.0, b.rocioTransfersEnviadas, delta)
    }

    @Test
    fun devolucionYCambioSobrevivenAlArrastreDeMes() {
        // El arrastre replaya con el mismo motor: lo que dejan de un mes al siguiente es exactamente
        // su estado final.
        val octubre = rocioDebe300k + listOf(
            mov("2026-10-05 10:00", 100000.0, AccountingEngine.TIPO_DEVOLUCION, "Rocío", "Santiago"),
            mov("2026-10-06 10:00", 20000.0, AccountingEngine.TIPO_CAMBIO, "Santiago", metodo = "Efectivo")
        )
        val fin = AccountingEngine.compute(octubre)
        val apertura = AccountingEngine.openingFor(octubre, "2026-11")
        assertEquals(fin.santiagoExterno, apertura.netSantiagoEnRocio, delta)
        assertEquals(fin.santiagoEfectivo, apertura.santiagoEfectivo, delta)
        assertEquals(fin.rocioVirtual, apertura.rocioVirtual, delta)
    }

    // --- v7.7: ingresos y gastos "reales" ---------------------------------------------------

    @Test
    fun ingresosSoloCuentanLaCategoriaIngresoOSueldo() {
        val b = AccountingEngine.compute(listOf(
            mov("2026-10-01", 100000.0, "Aporte", "Santiago", categoria = "Ingreso"),
            mov("2026-10-02", 50000.0, "Aporte", "Santiago", categoria = "Sueldo"),   // nombre viejo
            mov("2026-10-03", 30000.0, "Aporte", "Santiago", categoria = "Transferencias"),
            mov("2026-10-04", 10000.0, "Aporte", "Rocío", categoria = "Corrección"),
            mov("2026-10-05", 20000.0, "Aporte", "Rocío", "Ambos", categoria = "Ingreso")
        ))
        assertEquals(160000.0, b.santiagoIngresos, delta)
        assertEquals(10000.0, b.rocioIngresos, delta)
        // Los aportes (y el saldo) siguen contando todo.
        assertEquals(190000.0, b.santiagoAportes, delta)
        assertEquals(20000.0, b.rocioAportes, delta)
    }

    @Test
    fun gastosRealesExcluyenLasCorrecciones() {
        val b = AccountingEngine.compute(listOf(
            mov("2026-10-01", 1000.0, "Gasto", "Santiago", categoria = "Supermercado"),
            mov("2026-10-02", 200.0, "Gasto", "Santiago", categoria = "Corrección"),
            mov("2026-10-03", 400.0, "Gasto", "Rocío", "Ambos", categoria = "Servicios", esComun = true),
            mov("2026-10-04", 100.0, "Gasto", "Rocío", "Ambos", categoria = "Corrección", esComun = true)
        ))
        assertEquals(1200.0, b.santiagoGastosPersonales, delta)
        assertEquals(1000.0, b.santiagoGastosPersonalesReales, delta)
        assertEquals(500.0, b.gastosComunesTotales, delta)
        assertEquals(400.0, b.gastosComunesReales, delta)
        // La corrección sí mueve plata: el saldo la incluye.
        assertEquals(1700.0, b.totalGastosMes, delta)
    }

    @Test
    fun gastosPorEvitabilidadPartenComunesYExcluyenCorrecciones() {
        val movs = listOf(
            mov("2026-10-01", 1000.0, "Gasto", "Santiago", categoria = "Supermercado"),
            mov("2026-10-02", 300.0, "Gasto", "Santiago", categoria = "Gustos", evitable = true),
            mov("2026-10-03", 400.0, "Gasto", "Rocío", "Ambos", categoria = "Salidas", esComun = true, evitable = true),
            mov("2026-10-04", 500.0, "Gasto", "Rocío", categoria = "Farmacia"),
            mov("2026-10-05", 999.0, "Gasto", "Rocío", categoria = "Corrección", evitable = true),
            mov("2026-10-06", 5000.0, "Aporte", "Rocío", categoria = "Ingreso")
        )
        val s = AccountingEngine.gastosPorEvitabilidad(movs, "Santiago")
        val r = AccountingEngine.gastosPorEvitabilidad(movs, "Rocío")
        assertEquals(1000.0, s.necesario, delta)
        assertEquals(500.0, s.evitable, delta)     // 300 propio + la mitad del común
        assertEquals(500.0, r.necesario, delta)
        assertEquals(200.0, r.evitable, delta)
        assertEquals(2200.0, s.total + r.total, delta)
    }

    @Test
    fun evitableViajaEnLaColumnaOYVacioEsNoEvitable() {
        val gasto = mov("2026-10-01 10:00", 1000.0, "Gasto", "Santiago", evitable = true)
        val fila = gasto.toRowValues()
        assertEquals(15, fila.size)
        assertEquals("true", fila[14])
        assertTrue(Movement.fromRowValues(fila)!!.evitable)
        // Una fila de 14 columnas (anterior a la 7.7) se lee como no evitable.
        assertFalse(Movement.fromRowValues(fila.take(14))!!.evitable)
        assertFalse(Movement.fromRowValues(fila.take(14) + "")!!.evitable)
    }
}
