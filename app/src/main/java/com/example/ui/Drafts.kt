package com.example.ui

/**
 * Borradores de formularios que viven en el [AhorroViewModel] para **sobrevivir cambios de pestaña**
 * mientras la app está abierta (el VM dura lo que la Activity; los `remember` de un composable se
 * pierden al desmontarse la pantalla). Campos vacíos ("") significan "usar el default" y los resuelve
 * la pantalla al mostrar/guardar, así no hace falta re-inyectar defaults al restaurar.
 */
data class MovementDraft(
    val monto: String = "",
    val tipo: String = "Gasto",
    val esComun: Boolean = false,
    val metodoPago: String = "Billetera Virtual",
    val propietario: String = "",   // "" -> se resuelve al usuario activo (o "Ambos" si común)
    val descripcion: String = "",
    val fecha: String = "",          // "yyyy-MM-dd"; "" -> hoy
    val hora: String = "",           // "HH:mm"; "" -> ahora
    val categoria: String = "",      // "" -> primera categoría del tipo
    val ticketUriString: String? = null
)

/** Borrador del alta de una compra en cuotas (solo para plan NUEVO; la edición se siembra del plan). */
data class CuotaDraft(
    val descripcion: String = "",
    val montoText: String = "",
    val cantidadText: String = "",
    val categoria: String = "",      // "" -> primera categoría
    val tarjeta: String = "",
    val primeraCuota: String = ""    // "yyyy-MM"; "" -> mes actual
) {
    /** True si hay algo cargado (para decidir si conviene conservar/mostrar el borrador). */
    val tieneContenido: Boolean
        get() = descripcion.isNotBlank() || montoText.isNotBlank() ||
            cantidadText.isNotBlank() || tarjeta.isNotBlank()
}
