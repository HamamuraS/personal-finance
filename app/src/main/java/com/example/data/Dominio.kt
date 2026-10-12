package com.example.data

/**
 * Valores que viajan a la planilla como texto. Son **datos**, no etiquetas: cambiarles una letra
 * cambia el significado de las filas ya escritas, así que se definen una sola vez acá y el resto de
 * la app (motores, pantallas, workers) los referencia en vez de repetir el literal.
 *
 * La semántica contable de cada tipo está documentada en `domain.AccountingEngine`.
 */
object TipoMovimiento {
    const val GASTO = "Gasto"
    const val APORTE = "Aporte"
    const val TRANSFERENCIA = "Transferencia"
    const val CONDONACION = "Condonación"
    const val DEVOLUCION = "Devolución"
    const val CAMBIO = "Cambio"
    const val CAMBIO_PROPIO = "Cambio propio"

    /** Filas de saldo inicial de cada hoja de mes (stock, no flujo). */
    const val APERTURA = "Apertura"
}

/** Medios de pago. `metodoPago` es texto libre en la planilla; estos son los dos que carga la app. */
object MetodoPago {
    const val EFECTIVO = "Efectivo"
    const val VIRTUAL = "Billetera Virtual"
}

/** Propietario de un movimiento de los dos (gasto común): se reparte en partes iguales. */
const val PROPIETARIO_AMBOS = "Ambos"

/** "yyyy-MM" de un movimiento, o "" si la fecha no tiene forma de fecha. */
val Movement.mes: String get() = if (fecha.length >= 7) fecha.substring(0, 7) else ""
