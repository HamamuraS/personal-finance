package com.example.data

/**
 * Un usuario de la app. La app es de **dos** usuarios (ver features/usuarios-parametrizables.md):
 * el motor contable modela propiedad cruzada binaria, así que parametrizamos **nombre y color, no
 * la cantidad**. La UI ya no asume "el otro": recorre [UsuariosConfig.todos] / [UsuariosConfig.otros],
 * así que el día que el motor admita más personas las pantallas no necesitan reescribirse.
 *
 * Idea central del diseño: separar la **clave interna** (estable) del **nombre visible** (editable).
 * - [slotKey] es inmutable y coincide con el nombre legacy ("Santiago"/"Rocío"). Es lo que se sigue
 *   guardando en [Movement.responsable]/[Movement.propietario] y [CuotaPlan.propietario], y lo que
 *   consume el `AccountingEngine`. **Nunca cambia** → renombrar no migra datos ni toca el motor.
 * - [nombre] es la etiqueta visible en toda la UI (editable).
 * - [colorId] es el id de un preset curado (ver ui/theme/UserColorPalette.kt).
 * - [orden] 0 = slot primario (verde histórico / Santiago), 1 = secundario (azul / Rocío).
 * - [mensaje] / [mensajeFecha]: el "mensaje del día" (v7.8) que el Apps Script genera con Gemini
 *   todas las madrugadas (columnas E y F de la hoja `Usuarios`, ver `generarMensajesDelDia`). La app
 *   solo lo lee; [mensajeFecha] ("yyyy-MM-dd") dice de qué día es, para no mostrar uno viejo.
 */
data class Usuario(
    val slotKey: String,
    val nombre: String,
    val colorId: String,
    val orden: Int = 0,
    val mensaje: String = "",
    val mensajeFecha: String = ""
)

/**
 * Holder en memoria con los dos usuarios y helpers, para que la UI no vuelva a hablar de "Santiago"
 * literal. La app **nunca** queda sin usuarios: cualquier construcción cae al [DEFAULT] cableado.
 */
data class UsuariosConfig(val primario: Usuario, val secundario: Usuario) {

    val todos: List<Usuario> get() = listOf(primario, secundario)

    /** Usuario cuyo [Usuario.slotKey] coincide (case-insensitive), o null si no es ninguno. */
    fun byKey(slotKey: String): Usuario? =
        todos.firstOrNull { it.slotKey.equals(slotKey, ignoreCase = true) }

    /** Nombre visible del slot; cae al propio slotKey si no se reconoce (dato viejo/corrupto). */
    fun nombreDe(slotKey: String): String = byKey(slotKey)?.nombre ?: slotKey

    /** "El otro" respecto de [slotKey]. Si [slotKey] es el primario devuelve el secundario, si no el primario. */
    fun elOtro(slotKey: String): Usuario =
        if (primario.slotKey.equals(slotKey, ignoreCase = true)) secundario else primario

    /** Todos menos [slotKey]: la forma de hablar de "los demás" que no depende de que sean dos. */
    fun otros(slotKey: String): List<Usuario> = todos.filterNot { it.slotKey.equals(slotKey, ignoreCase = true) }

    /**
     * La config con [usuario] en el lugar del que tenga su mismo slotKey. Si no corresponde a ningún
     * slot, la config queda igual (antes un slotKey desconocido pisaba al secundario).
     */
    fun conUsuario(usuario: Usuario): UsuariosConfig = when {
        primario.slotKey.equals(usuario.slotKey, ignoreCase = true) -> copy(primario = usuario)
        secundario.slotKey.equals(usuario.slotKey, ignoreCase = true) -> copy(secundario = usuario)
        else -> this
    }

    companion object {
        /**
         * Fallback cableado con los nombres/colores legacy. Como el [Usuario.slotKey] coincide con
         * lo que ya está en los datos, todo sigue matcheando sin migrar nada.
         */
        val DEFAULT = UsuariosConfig(
            primario = Usuario(slotKey = "Santiago", nombre = "Santiago", colorId = "green", orden = 0),
            secundario = Usuario(slotKey = "Rocío", nombre = "Rocío", colorId = "blue", orden = 1),
        )

        /**
         * Arma una config robusta a partir de filas sueltas (hoja `Usuarios` o cache). La app es de
         * **dos slots fijos**: tomamos como canónicos los `slotKey`/`orden` del [DEFAULT] y solo
         * adoptamos el `nombre`/`colorId` que venga en la lista. Así nunca perdemos un slot ni
         * dejamos la app sin usuarios, aunque la fuente venga vacía, incompleta o con un solo slot.
         */
        fun fromList(users: List<Usuario>?): UsuariosConfig {
            if (users.isNullOrEmpty()) return DEFAULT
            fun pick(def: Usuario): Usuario {
                val match = users.firstOrNull { it.slotKey.equals(def.slotKey, ignoreCase = true) }
                    ?: return def
                return def.copy(
                    nombre = match.nombre.ifBlank { def.nombre },
                    colorId = match.colorId.ifBlank { def.colorId },
                    mensaje = match.mensaje,
                    mensajeFecha = match.mensajeFecha
                )
            }
            return UsuariosConfig(primario = pick(DEFAULT.primario), secundario = pick(DEFAULT.secundario))
        }
    }
}

/**
 * El mensaje del día de [usuario] **si corresponde mostrarlo**, o null para dejar el encabezado de
 * Inicio como siempre: cuando la función está apagada ([activo]), cuando no hay mensaje, o cuando no
 * es de [hoy] ("yyyy-MM-dd"). Esto último importa: el mensaje comenta lo de ayer ("¡Debe haber estado
 * bueno ese café!"), así que si el disparador dejó de correr, uno viejo diría cosas que ya no son.
 */
fun mensajeDelDiaVisible(usuario: Usuario?, hoy: String, activo: Boolean): String? {
    if (!activo || usuario == null) return null
    val texto = usuario.mensaje.trim()
    if (texto.isEmpty() || usuario.mensajeFecha.trim() != hoy) return null
    return texto
}
