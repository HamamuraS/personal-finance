package com.example.golden

import android.content.Context
import com.example.data.CuotaPlan
import com.example.data.Movement
import com.example.data.PreferencesHelper
import com.example.data.Usuario
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Datos fijos para las pruebas "de oro" (capturas de pantalla y caracterización del repositorio).
 *
 * Las fechas se arman relativas al mes en curso para que la UI (que mira el reloj real) siempre
 * tenga movimientos del mes, cuotas del resumen cerrado, etc. Los ids son fijos: nada aleatorio.
 */
object GoldenFixture {

    private val mesFmt = SimpleDateFormat("yyyy-MM", Locale.US)
    private val diaFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    val mes: String get() = mesFmt.format(Date())
    val mesAnterior: String get() = mesFmt.format(Calendar.getInstance().apply { add(Calendar.MONTH, -1) }.time)
    val mesTrasAnterior: String get() = mesFmt.format(Calendar.getInstance().apply { add(Calendar.MONTH, -2) }.time)
    val hoy: String get() = diaFmt.format(Date())

    private fun mov(
        id: String,
        fecha: String,
        monto: Double,
        tipo: String,
        categoria: String,
        responsable: String,
        esComun: Boolean = false,
        propietario: String = responsable,
        descripcion: String = "",
        metodoPago: String = "Billetera Virtual",
        ticketUrl: String = "",
        planId: String = "",
        cuotaNumero: Int = 0,
        evitable: Boolean = false
    ) = Movement(
        id = id, fecha = fecha, monto = monto, tipo = tipo, categoria = categoria,
        responsable = responsable, esComun = esComun, propietario = propietario,
        descripcion = descripcion, metodoPago = metodoPago, ticketUrl = ticketUrl,
        planId = planId, cuotaNumero = cuotaNumero, evitable = evitable
    )

    fun movimientos(): List<Movement> {
        val p = mesAnterior
        val m = mes
        return listOf(
            mov("p01", "$p-05 09:00", 500000.0, "Aporte", "Ingreso", "Santiago", descripcion = "Sueldo"),
            mov("p02", "$p-05 10:00", 400000.0, "Aporte", "Ingreso", "Rocío", descripcion = "Sueldo"),
            mov("p03", "$p-10 18:30", 80000.0, "Gasto", "Supermercado", "Santiago", esComun = true, propietario = "Ambos", descripcion = "Compra del mes"),
            mov("p04", "$p-12 12:00", 50000.0, "Transferencia", "Ajuste", "Santiago", descripcion = "La guardo en tu cuenta"),
            mov("p05", "$p-15 20:00", 12000.0, "Gasto", "Farmacia", "Rocío", metodoPago = "Efectivo"),
            mov("cuota-plan-note-1", "$p-03 11:00", 150000.0, "Gasto", "Utilería", "Santiago",
                descripcion = "Cuota 1/6 — Notebook", planId = "plan-note", cuotaNumero = 1),

            mov("m01", "$m-01 08:00", 600000.0, "Aporte", "Ingreso", "Santiago", descripcion = "Sueldo de octubre"),
            mov("m02", "$m-01 08:30", 5000.0, "Aporte", "Corrección", "Rocío", descripcion = "Ajuste con el banco"),
            mov("m03", "$m-01 09:00", 45000.0, "Gasto", "Servicios", "Rocío", esComun = true, propietario = "Ambos", descripcion = "Luz y gas"),
            mov("m04", "$m-01 09:15", 1200.0, "Gasto", "Transporte", "Santiago", metodoPago = "Efectivo", descripcion = "Colectivo"),
            mov("m05", "$m-01 09:30", 30000.0, "Gasto", "Indumentaria", "Santiago", propietario = "Rocío", evitable = true, descripcion = "Campera para Ro"),
            mov("m06", "$m-01 10:00", 20000.0, "Transferencia", "Ajuste", "Rocío", propietario = "Santiago", descripcion = "Regalo"),
            mov("m07", "$m-01 10:30", 10000.0, "Condonación", "Perdón de deuda", "Santiago", propietario = "Rocío"),
            mov("m08", "$m-01 11:00", 5000.0, "Devolución", "Pago de deuda", "Rocío", propietario = "Santiago"),
            mov("m09", "$m-01 11:30", 3000.0, "Cambio", "Cambio de dinero", "Santiago", metodoPago = "Efectivo"),
            mov("m10", "$m-01 12:00", 2000.0, "Cambio propio", "Cambio de dinero", "Rocío"),
            mov("m11", "$m-01 12:30", 15000.0, "Gasto", "Salidas", "Santiago", evitable = true,
                descripcion = "Cena de cumpleaños con amigos en el bodegón de siempre, con postre y café incluidos"),
            mov("m12", "$m-01 13:00", 25000.0, "Aporte", "Transferencias", "Santiago", propietario = "Rocío", descripcion = "Plata de Ro en mi cuenta"),
            mov("m13", "$m-01 13:30", 8000.0, "Gasto", "Verdulería", "Rocío", ticketUrl = "https://drive.google.com/x", descripcion = "Feria"),
            mov("m14", "$m-01 14:00", 9999.99, "Gasto", "Consumo inmediato", "Rocío", evitable = true)
        )
    }

    fun planes(): List<CuotaPlan> = listOf(
        CuotaPlan(
            id = "plan-note", fechaCreacion = "$mesTrasAnterior-20 10:00", descripcion = "Notebook",
            montoPorCuota = 150000.0, cantidadCuotas = 6, fechaPrimeraCuota = mesTrasAnterior,
            propietario = "Santiago", categoria = "Utilería", tarjeta = "Visa Santiago"
        ),
        CuotaPlan(
            id = "plan-hela", fechaCreacion = "$mesAnterior-18 19:30", descripcion = "Heladera",
            montoPorCuota = 80000.0, cantidadCuotas = 3, fechaPrimeraCuota = mesAnterior,
            propietario = "Rocío", categoria = "Utilería", tarjeta = "BBVA Rocío", evitable = true
        ),
        CuotaPlan(
            id = "plan-auri", fechaCreacion = "$mesTrasAnterior-02 09:00", descripcion = "Auriculares",
            montoPorCuota = 20000.0, cantidadCuotas = 2, fechaPrimeraCuota = mesTrasAnterior,
            propietario = "Santiago", categoria = "Salidas", tarjeta = "Visa Santiago",
            cuotasPagadasPrevias = listOf(1, 2)
        ),
        CuotaPlan(
            id = "plan-bici", fechaCreacion = "$mes-01 07:00", descripcion = "Bici",
            montoPorCuota = 12345678.5, cantidadCuotas = 12, fechaPrimeraCuota = mes,
            propietario = "Santiago", categoria = "Transporte", tarjeta = ""
        )
    )

    fun usuarios(): List<Usuario> = listOf(
        Usuario("Santiago", "Santiago", "green", 0, mensaje = "¡Arrancó el mes con todo! El sueldo ya está adentro.", mensajeFecha = hoy),
        Usuario("Rocío", "Rocío", "blue", 1)
    )

    /** Deja las preferencias listas para arrancar la app en modo demo con estos datos. */
    fun sembrar(context: Context, usuario: String, oscuro: Boolean, conIdentidad: Boolean = true) {
        val prefs = PreferencesHelper(context)
        prefs.useLocalDemo = true
        prefs.currentUserProfile = usuario
        prefs.hasChosenIdentity = conIdentidad
        prefs.isDarkMode = oscuro
        prefs.notifPermisoPedido = true
        prefs.lastFetchAt = 0L
        prefs.saveLocalMovements(movimientos())
        prefs.saveLocalPlans(planes())
        prefs.saveUsersCache(usuarios())
    }
}
