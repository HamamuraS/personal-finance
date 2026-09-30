package com.example.data

/**
 * Un movimiento **encolado**: ya se registró desde la app pero todavía no se escribió en la
 * planilla. Vive en `SharedPreferences` (no solo en memoria) para dos cosas:
 *
 *  1. **Fila optimista.** Inicio lo muestra apenas se toca "Registrar", con un cartel de pendiente,
 *     en vez de dejar la pantalla bloqueada esperando al Apps Script.
 *  2. **Reintento automático.** Si no hay red, o si se agotan los reintentos con backoff del
 *     Worker, el movimiento **queda acá**. Vuelve a intentarse solo en el próximo disparador
 *     (arranque de la app, sync de las 06/18 o alta de otro movimiento). Nunca se descarta: la
 *     única salida de esta cola es que la planilla confirme la escritura.
 *
 * @param ticketPath ruta del JPEG ya comprimido en `cacheDir`. El base64 de una foto pesa cientos
 *        de KB y el `Data` de WorkManager está limitado a 10 KB, así que la imagen viaja por disco
 *        y solo el path va en la cola. Se borra al confirmarse la escritura.
 * @param intentos cuántas veces se intentó subir (acumulado entre corridas del Worker).
 * @param esperandoTicket el alta se encoló pero su foto todavía se está comprimiendo. El drenador
 *        **saltea** estos: sin la bandera, cualquier otro disparador (otra alta, el sync de las
 *        06/18) podía subir el movimiento en esa ventana y el ticket se perdía en silencio. Al
 *        arrancar la app se limpia siempre: una compresión en curso no sobrevive al proceso.
 */
data class PendingMovement(
    val movement: Movement,
    val ticketPath: String = "",
    val encoladoEn: Long = System.currentTimeMillis(),
    val intentos: Int = 0,
    val ultimoError: String = "",
    val esperandoTicket: Boolean = false
)

/**
 * La cola después de que la planilla confirmó la subida de [subido]: saca la entrada **solo si sigue
 * siendo la misma versión** que se subió.
 *
 * Desde que los movimientos se pueden editar (v7.7), el usuario puede reencolar un movimiento
 * mientras el drenado está subiendo la versión anterior. Sacarlo por id a secas borraba de la cola la
 * versión editada, que nunca llegaba a la planilla. Si cambió, queda para el próximo drenado.
 */
fun quitarSiNoCambio(cola: List<PendingMovement>, subido: Movement): List<PendingMovement> =
    cola.filterNot { it.movement.id == subido.id && it.movement == subido }
