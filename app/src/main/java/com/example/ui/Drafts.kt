package com.example.ui

import com.example.data.MetodoPago
import com.example.data.Movement
import com.example.data.TipoMovimiento
import com.example.domain.AccountingEngine

/**
 * Borradores de formularios que viven en el [AhorroViewModel] para **sobrevivir cambios de pestaña**
 * mientras la app está abierta (el VM dura lo que la Activity; los `remember` de un composable se
 * pierden al desmontarse la pantalla). Campos vacíos ("") significan "usar el default" y los resuelve
 * la pantalla al mostrar/guardar, así no hace falta re-inyectar defaults al restaurar.
 */
data class MovementDraft(
    val monto: String = "",
    val tipo: String = TipoMovimiento.GASTO,
    val esComun: Boolean = false,
    val metodoPago: String = MetodoPago.VIRTUAL,
    val propietario: String = "",   // "" -> se resuelve al usuario activo (o "Ambos" si común)
    val descripcion: String = "",
    val fecha: String = "",          // "yyyy-MM-dd"; "" -> hoy
    val hora: String = "",           // "HH:mm"; "" -> ahora
    val categoria: String = "",      // "" -> primera categoría del tipo
    val ticketUriString: String? = null,
    val evitable: Boolean = false,   // solo gastos; default no evitable
    // --- Edición (v7.7). Con [editandoId] null el formulario es un alta. ---
    /** Id del movimiento que se está editando: se guarda con el mismo id (upsert por PUT). */
    val editandoId: String? = null,
    /** Ticket ya subido del movimiento editado; se conserva si no se adjunta una foto nueva. */
    val ticketUrlExistente: String = "",
    /** Vínculo con una cuota: se conserva tal cual al editar un pago de cuota. */
    val planId: String = "",
    val cuotaNumero: Int = 0,
    /** Propietario con el que estaba guardado (para avisar si la edición lo cambia). */
    val propietarioOriginal: String = ""
) {
    val editando: Boolean get() = editandoId != null

    /** True si hay algo cargado que se perdería al pisar el borrador. */
    val tieneContenido: Boolean
        get() = editando || monto.isNotBlank() || descripcion.isNotBlank() || ticketUriString != null
}

/** Borrador del alta de una compra en cuotas (solo para plan NUEVO; la edición se siembra del plan). */
data class CuotaDraft(
    val descripcion: String = "",
    val montoText: String = "",
    val cantidadText: String = "",
    val categoria: String = "",      // "" -> primera categoría
    val tarjeta: String = "",
    val primeraCuota: String = "",   // "yyyy-MM"; "" -> mes actual
    val evitable: Boolean = false
) {
    /** True si hay algo cargado (para decidir si conviene conservar/mostrar el borrador). */
    val tieneContenido: Boolean
        get() = descripcion.isNotBlank() || montoText.isNotBlank() ||
            cantidadText.isNotBlank() || tarjeta.isNotBlank()
}

/**
 * Filtros del historial de movimientos de Inicio. Viven en el [AhorroViewModel] por dos motivos:
 * sobreviven al cambio de pestaña (igual que los borradores de arriba) y, sobre todo, permiten que
 * **otra pantalla los aplique** — tocar una categoría en Métricas salta a Inicio con el filtro ya
 * puesto. Mientras el estado era un `remember` del composable, nada externo podía escribirlo.
 *
 * [persona] filtra por **pertenencia patrimonial** (propietario normalizado), no por de qué cuenta
 * salió la plata: es el mismo criterio que usan las tarjetas de saldos y ahora también Métricas.
 */
data class DashboardFilters(
    val persona: String = TODOS,          // TODOS o un slotKey
    val tipo: String = TODOS,             // uno de [TIPOS]
    val categorias: Set<String> = emptySet(),
    val query: String = ""
) {
    companion object {
        const val TODOS = "Todos"
        const val GASTOS = "Gastos"
        const val APORTES = "Aportes"
        const val TRANSFERENCIAS = "Transfer."

        /** Los filtros de tipo, en el orden en que se muestran (el valor es también la etiqueta). */
        val TIPOS = listOf(TODOS, GASTOS, APORTES, TRANSFERENCIAS)
    }

    /** Resultado de aplicar los filtros: lo que se lista y las categorías que tiene sentido ofrecer. */
    data class Resultado(
        val movimientos: List<Movement>,
        /** Categorías de lo que quedó visible por búsqueda/persona/tipo, ordenadas. */
        val categoriasDisponibles: List<String>,
        /** Las categorías elegidas que siguen disponibles (las demás se ignoran sin borrarlas). */
        val categoriasEfectivas: Set<String>
    )

    /**
     * Filtra [movimientos]: descripción -> persona -> tipo -> categorías (multi). La persona se
     * resuelve por pertenencia patrimonial ([AccountingEngine.perteneceA]): de quién ES el
     * movimiento, no de qué cuenta salió; los comunes pertenecen a todos, así que siempre aparecen.
     * Las categorías disponibles se derivan de lo que quedó visible antes de filtrarlas.
     */
    fun aplicar(movimientos: List<Movement>): Resultado {
        val q = query.trim()
        val base = movimientos.filter { m ->
            (q.isEmpty() || m.descripcion.contains(q, ignoreCase = true)) &&
                (persona == TODOS || AccountingEngine.perteneceA(m, persona)) &&
                when (tipo) {
                    GASTOS -> m.tipo.equals(TipoMovimiento.GASTO, ignoreCase = true)
                    APORTES -> m.tipo.equals(TipoMovimiento.APORTE, ignoreCase = true)
                    TRANSFERENCIAS -> m.tipo.equals(TipoMovimiento.TRANSFERENCIA, ignoreCase = true) ||
                        AccountingEngine.isCondonacion(m) || AccountingEngine.isDevolucion(m) ||
                        AccountingEngine.isCambio(m)
                    else -> true
                }
        }
        val disponibles = base.map { it.categoria }.distinct().sorted()
        val efectivas = categorias intersect disponibles.toSet()
        return Resultado(
            movimientos = base.filter { efectivas.isEmpty() || efectivas.contains(it.categoria) },
            categoriasDisponibles = disponibles,
            categoriasEfectivas = efectivas
        )
    }
}
