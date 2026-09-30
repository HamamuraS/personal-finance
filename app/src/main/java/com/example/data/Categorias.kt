package com.example.data

/**
 * Un rubro de gastos: el primer nivel del catálogo (🍽 Comida, 🏠 Casa…). Existe para **presentar**
 * y **agrupar**: la categoría que viaja a la planilla sigue siendo el string suelto de siempre, así
 * que agrupar no toca ni el modelo, ni el Apps Script, ni los movimientos ya escritos.
 */
data class Rubro(val nombre: String, val emoji: String, val categorias: List<String>) {
    val etiqueta: String get() = "$emoji $nombre"
}

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
 *
 * Gastos en dos niveles (v7.7.1): [RUBROS_GASTOS] agrupa las categorías para el selector y para
 * Métricas. **La lista plana [GASTOS] sigue siendo la fuente de verdad** (su primera categoría es
 * el default de un gasto nuevo) y los rubros son una vista sobre ella; `CategoriasTest` verifica que
 * cubran exactamente el catálogo, así que una categoría sin rubro rompe el build en vez de quedar
 * inalcanzable en la UI.
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
        "Cuidado personal", "Salidas", "Utilería", "Donación", CORRECCION, "Otros"
    )

    /**
     * Rubros de gastos. "Gustos" dejó de ser una categoría (v7.7.1): eso ahora lo dice el switch
     * 🍰 Evitable, que es ortogonal al "en qué". Los gastos viejos con "Gustos" caen en 🎉 Salidas
     * (ver [rubroDe]). [CORRECCION] no tiene rubro a propósito: es un ajuste técnico, no un consumo.
     */
    val RUBROS_GASTOS = listOf(
        Rubro("Comida", "🍽", listOf("Verdulería", "Alimentos frescos", "Consumo inmediato")),
        Rubro("Casa", "🏠", listOf("Supermercado", "Servicios", "Utilería", "Animales")),
        Rubro("Transporte", "🚌", listOf("Transporte")),
        Rubro("Salud", "🩺", listOf("Farmacia", "Cuidado personal")),
        Rubro("Ropa", "👕", listOf("Indumentaria")),
        Rubro("Salidas", "🎉", listOf("Salidas")),
        Rubro("Otros", "📦", listOf("Donación", "Otros")),
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

    /**
     * Categorías que puede tener el gasto que genera cada cuota: las de un gasto normal, menos la
     * corrección (una compra en cuotas nunca es un ajuste contra el banco).
     */
    val CUOTAS = GASTOS - CORRECCION

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

    /** ¿El tipo usa el selector en dos niveles? Solo los gastos: el resto tiene 2 a 4 categorías. */
    fun usaRubros(tipo: String): Boolean = deTipo(tipo) == GASTOS

    /**
     * Nombres de gasto que existen en la planilla real pero ya no están en el catálogo, con la
     * categoría actual equivalente. No se migran las filas: se leen con este alias.
     */
    private val ALIAS_GASTOS = mapOf(
        "transporte publico" to "Transporte",
        "transporte público" to "Transporte",
        "ropa" to "Indumentaria",
        "otros personales" to "Otros",
        "otros comunes" to "Otros"
    )

    /** Categorías viejas sin equivalente exacto, pero con un rubro claro. */
    private val RUBRO_LEGACY = mapOf(
        "gustos" to "Salidas",
        "hobbies" to "Salidas"
    )

    /**
     * La categoría del catálogo que corresponde a [categoria]: la misma con la capitalización
     * canónica, o su equivalente actual si es un nombre viejo ("Transporte publico" -> "Transporte").
     * Si no hay equivalente devuelve [categoria] tal cual.
     */
    fun canonica(categoria: String): String {
        val limpia = categoria.trim()
        ALIAS_GASTOS[limpia.lowercase()]?.let { return it }
        return GASTOS.firstOrNull { it.equals(limpia, ignoreCase = true) } ?: limpia
    }

    /**
     * Rubro de un gasto con categoría [categoria]. Tolera datos viejos (alias y [RUBRO_LEGACY]) y
     * cualquier texto libre cae en 📦 Otros, así Métricas nunca pierde un gasto. `null` solo para la
     * corrección, que no pertenece a ningún rubro.
     */
    fun rubroDe(categoria: String): Rubro? {
        if (esCorreccion(categoria)) return null
        val canon = canonica(categoria)
        RUBROS_GASTOS.firstOrNull { rubro -> rubro.categorias.any { it.equals(canon, ignoreCase = true) } }
            ?.let { return it }
        RUBRO_LEGACY[categoria.trim().lowercase()]?.let { nombre ->
            return RUBROS_GASTOS.first { it.nombre == nombre }
        }
        return RUBROS_GASTOS.last() // 📦 Otros
    }

    /** Un rubro con su total y el detalle por categoría (tal como está escrita en la planilla). */
    data class TotalDeRubro(val rubro: Rubro, val total: Double, val categorias: List<Pair<String, Double>>)

    /**
     * Agrupa totales por categoría en rubros, de mayor a menor total (y cada rubro, sus categorías
     * también de mayor a menor). Lo usa Métricas para mostrar 7 filas en vez de 15. Las categorías se
     * conservan con su nombre original, así el salto a Inicio filtra por lo que dice la planilla.
     */
    fun agruparPorRubro(porCategoria: List<Pair<String, Double>>): List<TotalDeRubro> =
        porCategoria
            .groupBy { (cat, _) -> rubroDe(cat) ?: RUBROS_GASTOS.last() }
            .map { (rubro, cats) ->
                TotalDeRubro(rubro, cats.sumOf { it.second }, cats.sortedByDescending { it.second })
            }
            .sortedByDescending { it.total }

    /**
     * Las [max] categorías más usadas de [tipo], a partir de un [historial] de categorías ordenado de
     * más nuevo a más viejo. Mira solo lo reciente ([ventana]) para que un hábito viejo no tape al
     * actual; a igual cantidad de usos gana la más reciente. Los nombres viejos se cuentan como su
     * equivalente actual y lo que no está en el catálogo del tipo (o es una corrección) se ignora.
     */
    fun frecuentes(historial: List<String>, tipo: String, max: Int = 6, ventana: Int = 100): List<String> {
        val validas = deTipo(tipo)
        val usadas = historial.asSequence()
            .map { canonica(it) }
            .mapNotNull { usada -> validas.firstOrNull { it.equals(usada, ignoreCase = true) } }
            .filterNot { esCorreccion(it) }
            .take(ventana)
            .toList()
        val usos = usadas.groupingBy { it }.eachCount()
        // `distinct()` conserva el orden de aparición: de más reciente a más vieja.
        return usadas.distinct()
            .withIndex()
            .sortedWith(compareByDescending<IndexedValue<String>> { usos.getValue(it.value) }.thenBy { it.index })
            .take(max)
            .map { it.value }
    }

    /**
     * ¿La categoría de un aporte es un ingreso? Acepta los nombres anteriores a la v7.7: "Sueldo" y
     * sus variantes por persona ("Sueldo Rocío", "Sueldo Santiago"), que hay en la planilla real. Las
     * filas viejas no se migran, se leen con este alias.
     */
    fun esIngreso(categoria: String): Boolean {
        val limpia = categoria.trim()
        return limpia.equals(INGRESO, ignoreCase = true) || limpia.startsWith("Sueldo", ignoreCase = true)
    }

    /** ¿Es una corrección? Tolera la forma sin tilde por si se carga a mano en la planilla. */
    fun esCorreccion(categoria: String): Boolean =
        categoria.trim().equals(CORRECCION, ignoreCase = true) || categoria.trim().equals("Correccion", ignoreCase = true)
}
