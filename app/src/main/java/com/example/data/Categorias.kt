package com.example.data

/**
 * Un grupo ("carpeta") de categorías. Existe solo para **presentar** el catálogo: la categoría que
 * viaja a la planilla sigue siendo el string suelto de siempre, así que agrupar no toca ni el
 * modelo ni el Apps Script ni los movimientos ya escritos.
 */
data class GrupoCategorias(val nombre: String, val emoji: String, val categorias: List<String>)

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
 * **Las listas planas siguen siendo la fuente de verdad**, y [GRUPOS_GASTOS] es una vista sobre
 * ellas. Se hizo así a propósito: el orden plano define el default de cada tipo
 * ([defaultDeTipo] = la primera) y la posición de "Otros" como último recurso, y derivar la lista
 * de los grupos habría cambiado en silencio la categoría por defecto de un gasto nuevo.
 */
object Categorias {

    val APORTES = listOf("Sueldo", "Transferencias", "Cambio de dinero", "Otros")

    val GASTOS = listOf(
        "Transporte", "Servicios", "Animales", "Supermercado", "Verdulería",
        "Consumo inmediato", "Alimentos frescos", "Farmacia", "Indumentaria",
        "Cuidado personal", "Salidas", "Gustos", "Utilería", "Donación",
        "Cambio de dinero", "Otros"
    )

    val TRANSFERENCIAS = listOf("Ajuste", "Reembolso", "Rescate", "Cambio de dinero", "Otros")

    /**
     * Categorías de una condonación (perdón de deuda). En el alta es un submodo de "Transferencia",
     * pero en la planilla es un tipo propio (`AccountingEngine.TIPO_CONDONACION`), así que necesita
     * su propia lista: las de transferencia hablan de plata que se mueve y acá no se mueve nada.
     */
    val CONDONACIONES = listOf("Perdón de deuda", "Ajuste", "Otros")

    /** Categorías que puede tener el gasto que genera cada cuota (mismo set que un gasto normal). */
    val CUOTAS = GASTOS

    /**
     * Carpetas de gastos. Es la única lista que las necesita: con 16 categorías y creciendo, el
     * FlowRow plano ocupaba media pantalla del alta. Los otros tipos tienen 3-5 y se siguen
     * mostrando de corrido (ver [gruposDeTipo]).
     *
     * Invariante verificada en `CategoriasTest`: la unión de los grupos es exactamente [GASTOS] y
     * ninguna categoría está en dos grupos. Agregar una categoría a [GASTOS] sin ubicarla en una
     * carpeta rompe el test en vez de dejarla inalcanzable en la UI.
     */
    val GRUPOS_GASTOS = listOf(
        GrupoCategorias("Comida", "🍽", listOf(
            "Supermercado", "Verdulería", "Alimentos frescos", "Consumo inmediato"
        )),
        GrupoCategorias("Casa", "🏠", listOf("Servicios", "Utilería", "Animales")),
        GrupoCategorias("Salud", "🩺", listOf("Farmacia", "Cuidado personal")),
        GrupoCategorias("Transporte", "🚌", listOf("Transporte")),
        GrupoCategorias("Ocio", "🎉", listOf("Salidas", "Gustos", "Indumentaria")),
        GrupoCategorias("Otros", "📦", listOf("Donación", "Cambio de dinero", "Otros")),
    )

    /** Categorías válidas para un tipo de movimiento. Fallback: las de gasto. */
    fun deTipo(tipo: String): List<String> = when (tipo) {
        "Aporte" -> APORTES
        "Transferencia" -> TRANSFERENCIAS
        // Literal a propósito: `AccountingEngine` vive en la capa de UI y `data` no depende de ella.
        "Condonación" -> CONDONACIONES
        else -> GASTOS
    }

    /**
     * Carpetas de un tipo, o `null` si su lista es corta y conviene mostrarla plana. El selector
     * usa el `null` como señal de "no armes carpetas", así que sumar carpetas a otro tipo el día
     * que crezca es agregar una rama acá y nada más.
     */
    fun gruposDeTipo(tipo: String): List<GrupoCategorias>? = when (tipo) {
        "Aporte", "Transferencia", "Condonación" -> null
        else -> GRUPOS_GASTOS
    }

    /** Carpeta que contiene a [categoria], o `null` si no está en ninguna (dato viejo o libre). */
    fun grupoDe(categoria: String): GrupoCategorias? =
        GRUPOS_GASTOS.firstOrNull { grupo -> grupo.categorias.any { it.equals(categoria, ignoreCase = true) } }

    /**
     * Las [max] categorías más usadas recientemente para un [tipo], a partir de un [historial]
     * ordenado de más nuevo a más viejo (típicamente las categorías de los movimientos del usuario).
     *
     * Se filtra contra la lista del tipo para no ofrecer una categoría de gasto en un aporte, y se
     * normaliza contra ella para que un dato viejo con otra capitalización caiga en la categoría
     * canónica en vez de duplicarse en la fila de recientes.
     */
    fun recientes(historial: List<String>, tipo: String, max: Int = 6): List<String> {
        val validas = deTipo(tipo)
        return historial.asSequence()
            .mapNotNull { usada -> validas.firstOrNull { it.equals(usada, ignoreCase = true) } }
            .distinct()
            .take(max)
            .toList()
    }

    /** Categoría por defecto de un tipo (la primera de su lista). */
    fun defaultDeTipo(tipo: String): String = deTipo(tipo).first()
}
