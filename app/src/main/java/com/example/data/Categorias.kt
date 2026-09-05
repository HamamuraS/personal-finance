package com.example.data

/**
 * Catálogo único de categorías.
 *
 * Antes las listas estaban duplicadas literalmente en `AddMovementScreen` y `CuotasScreen`, con un
 * comentario pidiendo mantenerlas iguales a mano. Cada categoría nueva había que agregarla en los
 * dos lados y era cuestión de tiempo que se desincronizaran: una compra en cuotas podía quedar con
 * una categoría que el alta de gastos no ofrecía.
 *
 * La categoría viaja a la planilla como texto libre (no hay validación ni en el modelo ni en el
 * Apps Script), así que agregar una acá es seguro y retrocompatible: los movimientos viejos
 * conservan la suya aunque deje de estar en la lista.
 */
object Categorias {

    val APORTES = listOf("Sueldo", "Transferencias", "Otros")

    val GASTOS = listOf(
        "Transporte", "Servicios", "Animales", "Supermercado", "Verdulería",
        "Consumo inmediato", "Alimentos frescos", "Farmacia", "Indumentaria",
        "Cuidado personal", "Salidas", "Gustos", "Utilería", "Donación", "Otros"
    )

    val TRANSFERENCIAS = listOf("Ajuste", "Reembolso", "Rescate", "Otros")

    /**
     * Categorías de una condonación (perdón de deuda). En el alta es un submodo de "Transferencia",
     * pero en la planilla es un tipo propio (`AccountingEngine.TIPO_CONDONACION`), así que necesita
     * su propia lista: las de transferencia hablan de plata que se mueve y acá no se mueve nada.
     */
    val CONDONACIONES = listOf("Perdón de deuda", "Ajuste", "Otros")

    /** Categorías que puede tener el gasto que genera cada cuota (mismo set que un gasto normal). */
    val CUOTAS = GASTOS

    /** Categorías válidas para un tipo de movimiento. Fallback: las de gasto. */
    fun deTipo(tipo: String): List<String> = when (tipo) {
        "Aporte" -> APORTES
        "Transferencia" -> TRANSFERENCIAS
        // Literal a propósito: `AccountingEngine` vive en la capa de UI y `data` no depende de ella.
        "Condonación" -> CONDONACIONES
        else -> GASTOS
    }

    /** Categoría por defecto de un tipo (la primera de su lista). */
    fun defaultDeTipo(tipo: String): String = deTipo(tipo).first()
}
