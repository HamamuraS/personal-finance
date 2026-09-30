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

    /**
     * Aporte que es plata nueva de verdad (sueldo, honorarios…). Es lo único que Inicio y Métricas
     * cuentan como "ingreso": el resto de los aportes (transferencias, correcciones) son movimientos
     * internos o ajustes. Se llamaba "Sueldo" hasta la v7.7; ver [esIngreso].
     */
    const val INGRESO = "Ingreso"

    /** Ajuste para que la app cuadre con el banco. No cuenta como ingreso ni como gasto "real". */
    const val CORRECCION = "Corrección"

    val APORTES = listOf(INGRESO, "Transferencias", CORRECCION, "Otros")

    val GASTOS = listOf(
        "Transporte", "Servicios", "Animales", "Supermercado", "Verdulería",
        "Consumo inmediato", "Alimentos frescos", "Farmacia", "Indumentaria",
        "Cuidado personal", "Salidas", "Gustos", "Utilería", "Donación", CORRECCION, "Otros"
    )

    val TRANSFERENCIAS = listOf("Ajuste", "Reembolso", "Rescate", "Otros")

    /**
     * Categorías de una condonación (perdón de deuda). En el alta es el "Saldo externo" con saldo a
     * favor, pero en la planilla es un tipo propio (`AccountingEngine.TIPO_CONDONACION`), así que
     * necesita su propia lista: las de transferencia hablan de plata que se mueve y acá no se mueve
     * nada.
     */
    val CONDONACIONES = listOf("Perdón de deuda", "Ajuste", "Otros")

    /** Categorías de una devolución (pago de deuda): el "Saldo externo" de quien debe. */
    val DEVOLUCIONES = listOf("Pago de deuda", "Ajuste", "Otros")

    /** Categorías de un cambio de dinero (efectivo por transferencia entre los dos). */
    val CAMBIOS = listOf("Cambio de dinero", "Otros")

    /** Categorías que puede tener el gasto que genera cada cuota (mismo set que un gasto normal). */
    val CUOTAS = GASTOS

    /** Categorías válidas para un tipo de movimiento. Fallback: las de gasto. */
    fun deTipo(tipo: String): List<String> = when (tipo) {
        "Aporte" -> APORTES
        "Transferencia" -> TRANSFERENCIAS
        // Literales a propósito: `AccountingEngine` vive en la capa de UI y `data` no depende de ella.
        "Condonación" -> CONDONACIONES
        "Devolución" -> DEVOLUCIONES
        "Cambio" -> CAMBIOS
        else -> GASTOS
    }

    /** Categoría por defecto de un tipo (la primera de su lista). */
    fun defaultDeTipo(tipo: String): String = deTipo(tipo).first()

    /**
     * ¿La categoría de un aporte es un ingreso? Acepta "Sueldo", el nombre anterior a la v7.7: las
     * filas viejas no se migran, se leen con este alias.
     */
    fun esIngreso(categoria: String): Boolean =
        categoria.trim().equals(INGRESO, ignoreCase = true) || categoria.trim().equals("Sueldo", ignoreCase = true)

    /** ¿Es una corrección? Tolera la forma sin tilde por si se carga a mano en la planilla. */
    fun esCorreccion(categoria: String): Boolean =
        categoria.trim().equals(CORRECCION, ignoreCase = true) || categoria.trim().equals("Correccion", ignoreCase = true)
}
