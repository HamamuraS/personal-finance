package com.example

import com.example.data.CuotaPlan
import com.example.data.Movement
import com.example.ui.AccountingEngine
import com.example.ui.CuotasEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests del motor puro [CuotasEngine]. No requiere Android ni Robolectric.
 * Verifica que el cronograma, el estado "pagada" (derivado de los movimientos), los recordatorios
 * con atrasos, el total por tarjeta y la detección de "completamente pago" sean correctos.
 */
class CuotasEngineTest {

    private val delta = 0.001

    private fun plan(
        id: String = "plan-1",
        descripcion: String = "Notebook",
        montoPorCuota: Double = 100000.0,
        cantidadCuotas: Int = 12,
        fechaPrimeraCuota: String = "2026-08",
        propietario: String = "Santiago",
        categoria: String = "Utilería",
        tarjeta: String = "Visa Santiago"
    ) = CuotaPlan(
        id = id,
        fechaCreacion = "2026-07-21 10:00",
        descripcion = descripcion,
        montoPorCuota = montoPorCuota,
        cantidadCuotas = cantidadCuotas,
        fechaPrimeraCuota = fechaPrimeraCuota,
        propietario = propietario,
        categoria = categoria,
        tarjeta = tarjeta
    )

    /** Movimiento que paga la cuota [numero] del plan [planId]. */
    private fun pago(planId: String, numero: Int, fecha: String = "2026-08-01 12:00", monto: Double = 100000.0) =
        Movement(
            fecha = fecha, monto = monto, tipo = "Gasto", categoria = "Utilería",
            responsable = "Santiago", esComun = false, propietario = "Santiago",
            metodoPago = "Billetera Virtual", planId = planId, cuotaNumero = numero
        )

    @Test
    fun cronogramaGeneraMesesConsecutivosConSaltoDeAnio() {
        val crono = CuotasEngine.cronograma(plan(cantidadCuotas = 12, fechaPrimeraCuota = "2026-08"), emptyList())
        assertEquals(12, crono.size)
        assertEquals("2026-08", crono.first().mesVencimiento)   // cuota 1
        assertEquals("2026-12", crono[4].mesVencimiento)        // cuota 5
        assertEquals("2027-01", crono[5].mesVencimiento)        // cuota 6 -> salto de año
        assertEquals("2027-07", crono.last().mesVencimiento)    // cuota 12
        // Sin movimientos, ninguna cuota está pagada
        assertTrue(crono.none { it.pagada })
        assertNull(crono.first().movimientoId)
    }

    @Test
    fun estadoPagadaSeDerivaDeLosMovimientos() {
        val p = plan(cantidadCuotas = 12)
        val movs = listOf(pago(p.id, 1), pago(p.id, 3))
        val crono = CuotasEngine.cronograma(p, movs)

        assertTrue(crono[0].pagada)   // cuota 1
        assertFalse(crono[1].pagada)  // cuota 2
        assertTrue(crono[2].pagada)   // cuota 3
        assertEquals(2, CuotasEngine.cuotasPagadas(p, movs))
        assertFalse(CuotasEngine.estaCompleto(p, movs))
    }

    @Test
    fun unMovimientoEliminadoDevuelveLaCuotaAPendiente() {
        val p = plan(cantidadCuotas = 3)
        val vivo = pago(p.id, 1)
        val borrado = pago(p.id, 2).copy(eliminado = true)
        val crono = CuotasEngine.cronograma(p, listOf(vivo, borrado))

        assertTrue(crono[0].pagada)    // cuota 1 pagada
        assertFalse(crono[1].pagada)   // cuota 2: su pago fue borrado -> vuelve a pendiente
        assertEquals(1, CuotasEngine.cuotasPagadas(p, listOf(vivo, borrado)))
    }

    @Test
    fun deteccionCompletamentePago() {
        val p = plan(cantidadCuotas = 3)
        val movs = listOf(pago(p.id, 1), pago(p.id, 2), pago(p.id, 3))
        assertTrue(CuotasEngine.estaCompleto(p, movs))
        assertEquals(3, CuotasEngine.cuotasPagadas(p, movs))
    }

    @Test
    fun cuotasDeOtroPlanNoCuentan() {
        val p = plan(id = "plan-A", cantidadCuotas = 3)
        val movs = listOf(pago("plan-B", 1), pago("plan-B", 2))
        assertEquals(0, CuotasEngine.cuotasPagadas(p, movs))
        assertTrue(CuotasEngine.cronograma(p, movs).none { it.pagada })
    }

    @Test
    fun recordatoriosIncluyenAtrasadasHastaElMesConsultado() {
        // Plan de 12 cuotas desde 2026-08. Consultamos en 2026-10 sin ningún pago.
        val p = plan(cantidadCuotas = 12, fechaPrimeraCuota = "2026-08")
        val recordatorios = CuotasEngine.recordatoriosDelMes("2026-10", listOf(p), emptyList())

        // Deben aparecer las cuotas 1, 2 y 3 (ago, sep, oct); no las futuras (nov+)
        assertEquals(3, recordatorios.size)
        assertEquals(listOf("2026-08", "2026-09", "2026-10"), recordatorios.map { it.cuota.mesVencimiento })
        // Las de ago y sep están atrasadas respecto de octubre; la de octubre no
        assertTrue(recordatorios[0].atrasada)
        assertTrue(recordatorios[1].atrasada)
        assertFalse(recordatorios[2].atrasada)
    }

    @Test
    fun recordatoriosExcluyenCuotasYaPagadas() {
        val p = plan(cantidadCuotas = 12, fechaPrimeraCuota = "2026-08")
        val movs = listOf(pago(p.id, 1))  // cuota 1 pagada
        val recordatorios = CuotasEngine.recordatoriosDelMes("2026-10", listOf(p), movs)

        // Quedan solo la 2 y la 3 (la 1 ya está paga)
        assertEquals(2, recordatorios.size)
        assertEquals(listOf("2026-09", "2026-10"), recordatorios.map { it.cuota.mesVencimiento })
    }

    @Test
    fun totalTarjetaPorMesAgrupaPorTarjeta() {
        val visa = plan(id = "p-visa", montoPorCuota = 100000.0, cantidadCuotas = 6, fechaPrimeraCuota = "2026-08", tarjeta = "Visa Santiago")
        val naranja = plan(id = "p-naranja", montoPorCuota = 50000.0, cantidadCuotas = 6, fechaPrimeraCuota = "2026-08", tarjeta = "Naranja")
        val visa2 = plan(id = "p-visa2", montoPorCuota = 30000.0, cantidadCuotas = 6, fechaPrimeraCuota = "2026-08", tarjeta = "Visa Santiago")

        val totales = CuotasEngine.totalTarjetaPorMes("2026-08", listOf(visa, naranja, visa2), emptyList())
        assertEquals(130000.0, totales["Visa Santiago"]!!, delta)  // 100k + 30k
        assertEquals(50000.0, totales["Naranja"]!!, delta)
    }

    @Test
    fun totalTarjetaPorMesSumaPagadasEImpagas() {
        // Aunque la cuota esté pagada, sigue contando en el total de la tarjeta de ese mes.
        val p = plan(id = "p", montoPorCuota = 100000.0, cantidadCuotas = 3, fechaPrimeraCuota = "2026-08", tarjeta = "Visa")
        val movs = listOf(pago(p.id, 1))  // cuota de agosto pagada
        val totalAgosto = CuotasEngine.totalTarjetaPorMes("2026-08", listOf(p), movs)
        assertEquals(100000.0, totalAgosto["Visa"]!!, delta)
    }

    @Test
    fun planSinTarjetaSeAgrupaBajoSinTarjeta() {
        val p = plan(id = "p", cantidadCuotas = 3, fechaPrimeraCuota = "2026-08", tarjeta = "")
        val totales = CuotasEngine.totalTarjetaPorMes("2026-08", listOf(p), emptyList())
        assertTrue(totales.containsKey("Sin tarjeta"))
    }

    @Test
    fun compromisoFuturoTotalSumaSoloImpagas() {
        val p = plan(id = "p", montoPorCuota = 100000.0, cantidadCuotas = 3, fechaPrimeraCuota = "2026-08")
        val movs = listOf(pago(p.id, 1))  // 1 pagada, quedan 2 impagas
        assertEquals(200000.0, CuotasEngine.compromisoFuturoTotal(listOf(p), movs), delta)
    }

    @Test
    fun cuotasImpagasDelMesDevuelveSoloLasDelMesYSinPagar() {
        val p = plan(id = "p1", cantidadCuotas = 12, fechaPrimeraCuota = "2026-08")
        val movs = listOf(pago("p1", 1))  // cuota de agosto (mes 1) pagada
        // Agosto: la cuota 1 ya está paga -> nada impago ese mes.
        assertTrue(CuotasEngine.cuotasImpagasDelMes("2026-08", listOf(p), movs).isEmpty())
        // Septiembre: la cuota 2 vence y está impaga.
        val sept = CuotasEngine.cuotasImpagasDelMes("2026-09", listOf(p), movs)
        assertEquals(1, sept.size)
        assertEquals("p1", sept.first().first.id)
        assertEquals(2, sept.first().second.numero)
    }

    @Test
    fun cuotasImpagasDelMesSeAgrupanPorTarjeta() {
        val visa = plan(id = "p1", montoPorCuota = 100000.0, cantidadCuotas = 6, fechaPrimeraCuota = "2026-08", tarjeta = "Visa Santiago")
        val bbva = plan(id = "p2", montoPorCuota = 50000.0, cantidadCuotas = 6, fechaPrimeraCuota = "2026-08", tarjeta = "BBVA Rocío")
        val visa2 = plan(id = "p3", montoPorCuota = 30000.0, cantidadCuotas = 6, fechaPrimeraCuota = "2026-08", tarjeta = "Visa Santiago")

        val porTarjeta = CuotasEngine.cuotasImpagasDelMes("2026-08", listOf(visa, bbva, visa2), emptyList())
            .groupBy { it.first.tarjeta }
        assertEquals(2, porTarjeta.size)
        assertEquals(130000.0, porTarjeta["Visa Santiago"]!!.sumOf { it.second.monto }, delta)  // 100k + 30k
        assertEquals(50000.0, porTarjeta["BBVA Rocío"]!!.sumOf { it.second.monto }, delta)
    }

    @Test
    fun planEliminadoNoAporta() {
        val p = plan(id = "p", cantidadCuotas = 3).copy(eliminado = true)
        assertTrue(CuotasEngine.recordatoriosDelMes("2027-01", listOf(p), emptyList()).isEmpty())
        assertEquals(0.0, CuotasEngine.compromisoFuturoTotal(listOf(p), emptyList()), delta)
        assertTrue(CuotasEngine.totalTarjetaPorMes("2026-08", listOf(p), emptyList()).isEmpty())
    }

    @Test
    fun pagarUnaCuotaGeneraGastoPersonalSinPropiedadCruzada() {
        // El pago de una cuota es un gasto personal (responsable == propietario): no debe generar
        // dinero cruzado en el AccountingEngine.
        val p = plan(id = "p", montoPorCuota = 100000.0, cantidadCuotas = 12, propietario = "Santiago")
        val movimientoDeCuota = Movement(
            fecha = "2026-08-01 12:00", monto = p.montoPorCuota, tipo = "Gasto", categoria = p.categoria,
            responsable = p.propietario, esComun = false, propietario = p.propietario,
            metodoPago = "Billetera Virtual", planId = p.id, cuotaNumero = 1,
            descripcion = "Cuota 1/12 — ${p.descripcion}"
        )
        val b = AccountingEngine.compute(listOf(movimientoDeCuota))

        assertEquals(0.0, b.santiagoExterno, delta)                 // sin propiedad cruzada
        assertEquals(100000.0, b.santiagoGastosPersonales, delta)   // gasto personal de Santiago
        assertEquals(0.0, b.gastosComunesTotales, delta)            // no es común
    }
}
