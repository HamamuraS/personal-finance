package com.example.ui

import com.example.data.Movement
import com.example.data.PROPIETARIO_AMBOS
import com.example.data.TipoMovimiento
import com.example.domain.AccountingEngine

/**
 * Cómo se presenta un movimiento en las listas y el detalle. Decisiones puras (sin Compose): el
 * ícono, el color y el signo salen de acá en las dos vistas, que antes repetían la cuenta.
 */
enum class ClaseDeMovimiento { APORTE, GASTO, CONDONACION, DEVOLUCION, CAMBIO, TRANSFERENCIA }

fun Movement.clase(): ClaseDeMovimiento = when {
    tipo.equals(TipoMovimiento.APORTE, ignoreCase = true) -> ClaseDeMovimiento.APORTE
    tipo.equals(TipoMovimiento.GASTO, ignoreCase = true) -> ClaseDeMovimiento.GASTO
    AccountingEngine.isCondonacion(this) -> ClaseDeMovimiento.CONDONACION
    AccountingEngine.isDevolucion(this) -> ClaseDeMovimiento.DEVOLUCION
    AccountingEngine.isCambio(this) -> ClaseDeMovimiento.CAMBIO
    else -> ClaseDeMovimiento.TRANSFERENCIA
}

/**
 * Signo con que se muestra el monto. Un perdón de deuda no mueve plata y un cambio de dinero no
 * cambia cuánto tiene cada uno: con "-" parecería que salió plata, así que van sin signo.
 */
fun Movement.signo(): String = when (clase()) {
    ClaseDeMovimiento.APORTE -> "+"
    ClaseDeMovimiento.CONDONACION, ClaseDeMovimiento.CAMBIO -> ""
    else -> "-"
}

/** Nombre del tipo como lo dice la UI (no como está en la planilla), para el detalle. */
fun Movement.tipoVisible(): String = when {
    AccountingEngine.isCondonacion(this) -> "Saldo externo (perdón)"
    AccountingEngine.isDevolucion(this) -> "Saldo externo (pago)"
    AccountingEngine.isCambioPropio(this) -> "Cambio de dinero (con otra persona)"
    AccountingEngine.isCambio(this) -> "Cambio de dinero"
    else -> tipo
}

/** ¿Es un gasto pagado por uno pero de otra persona (no común)? Lleva la etiqueta "De …". */
fun Movement.esDeOtraPersona(): Boolean =
    !propietario.equals(responsable, ignoreCase = true) && !propietario.equals(PROPIETARIO_AMBOS, ignoreCase = true)

/**
 * ¿Se ofrece duplicarlo? Solo los transportes: si dos viajan juntos, uno lo carga y el otro lo
 * duplica a su nombre.
 */
fun Movement.esDuplicable(): Boolean = categoria.contains("Transporte", ignoreCase = true)
