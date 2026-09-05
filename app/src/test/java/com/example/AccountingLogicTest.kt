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
}
