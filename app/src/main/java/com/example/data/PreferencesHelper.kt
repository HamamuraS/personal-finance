package com.example.data

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class PreferencesHelper(context: Context) {
    private val prefs = context.getSharedPreferences("ahorro_prefs", Context.MODE_PRIVATE)
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    
    // Convertidores de JSON
    private val movementType = Types.newParameterizedType(List::class.java, Movement::class.java)
    private val listAdapter = moshi.adapter<List<Movement>>(movementType)
    private val planType = Types.newParameterizedType(List::class.java, CuotaPlan::class.java)
    private val planListAdapter = moshi.adapter<List<CuotaPlan>>(planType)
    private val usuarioType = Types.newParameterizedType(List::class.java, Usuario::class.java)
    private val usuarioListAdapter = moshi.adapter<List<Usuario>>(usuarioType)
    private val pendingType = Types.newParameterizedType(List::class.java, PendingMovement::class.java)
    private val pendingListAdapter = moshi.adapter<List<PendingMovement>>(pendingType)

    companion object {
        private const val KEY_USE_LOCAL_DEMO = "use_local_demo"
        private const val KEY_LOCAL_MOVEMENTS = "local_movements"
        private const val KEY_SHEETS_CACHE = "sheets_cache"
        private const val KEY_USER_PROFILE = "user_profile"
        private const val KEY_IS_DARK_MODE = "is_dark_mode"
        private const val KEY_LOCAL_PLANS = "local_plans"
        private const val KEY_PLANS_CACHE = "plans_cache"
        private const val KEY_USERS_CACHE = "users_cache"
        private const val KEY_HAS_CHOSEN_IDENTITY = "has_chosen_identity"
        private const val KEY_NOTIF_PERMISO_PEDIDO = "notif_permiso_pedido"
        private const val KEY_LAST_CIERRE_NOTIFICADO = "last_cierre_notificado"
        private const val KEY_LAST_ATRASO_NOTIFICADO = "last_atraso_notificado"
        private const val KEY_LAST_FETCH_AT = "last_fetch_at"
        private const val KEY_PENDING_MOVEMENTS = "pending_movements"

        /** Ventana de frescura del cache: no se refresca solo hasta que pasen 6 horas. */
        const val FETCH_TTL_MILLIS = 6L * 60 * 60 * 1000

        /**
         * Candado de la cola de pendientes, compartido por **todas** las instancias: el ViewModel,
         * el worker de subida, el de sync y la Activity construyen cada uno la suya.
         */
        private val COLA_LOCK = Any()
    }

    var isDarkMode: Boolean
        get() = prefs.getBoolean(KEY_IS_DARK_MODE, true)
        set(value) {
            prefs.edit().putBoolean(KEY_IS_DARK_MODE, value).apply()
        }

    // Conexión: viene del APK (ver [AppConfig]), ya no se edita desde Ajustes. Se lee siempre de
    // la config compilada y no de SharedPreferences, para que un valor viejo guardado en un
    // teléfono no le gane al que trae la versión instalada.
    val scriptUrl: String get() = AppConfig.SCRIPT_URL

    val folderId: String get() = AppConfig.DRIVE_FOLDER_ID

    // Si el build no trae configuración usable, arranca en demo en vez de pegarle a una URL inválida.
    var useLocalDemo: Boolean
        get() = prefs.getBoolean(KEY_USE_LOCAL_DEMO, !AppConfig.isConfigured)
        set(value) = prefs.edit().putBoolean(KEY_USE_LOCAL_DEMO, value).apply()

    // Identidad activa en ESTE dispositivo. Guarda el slotKey del usuario ("Santiago"/"Rocío"):
    // mismo espacio de valores que antes, ahora interpretado como clave interna del slot.
    var currentUserProfile: String
        get() = prefs.getString(KEY_USER_PROFILE, "Santiago") ?: "Santiago"
        set(value) = prefs.edit().putString(KEY_USER_PROFILE, value).apply()

    // ¿Ya eligió quién es en este dispositivo? Si no, la app muestra el picker "¿Quién sos?".
    var hasChosenIdentity: Boolean
        get() = prefs.getBoolean(KEY_HAS_CHOSEN_IDENTITY, false)
        set(value) = prefs.edit().putBoolean(KEY_HAS_CHOSEN_IDENTITY, value).apply()

    // ¿Ya se pidió el permiso de notificaciones automáticamente? Se pide UNA sola vez: Android
    // deja de mostrar el diálogo tras dos rechazos, así que insistir en cada arranque quemaría el
    // pedido. Después de eso queda el botón de Ajustes como camino manual.
    var notifPermisoPedido: Boolean
        get() = prefs.getBoolean(KEY_NOTIF_PERMISO_PEDIDO, false)
        set(value) = prefs.edit().putBoolean(KEY_NOTIF_PERMISO_PEDIDO, value).apply()

    // --- Notificaciones de cuotas (deduplicación: evita re-notificar el mismo período si el
    // Worker corre más de una vez, p. ej. por un reintento) ---

    // Último mes (yyyy-MM) para el que ya se notificó el recordatorio de cierre de mes.
    var lastCierreNotificado: String
        get() = prefs.getString(KEY_LAST_CIERRE_NOTIFICADO, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_CIERRE_NOTIFICADO, value).apply()

    // Último lunes (yyyy-MM-dd) para el que ya se notificó el recordatorio de atrasos.
    var lastAtrasoNotificado: String
        get() = prefs.getString(KEY_LAST_ATRASO_NOTIFICADO, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_ATRASO_NOTIFICADO, value).apply()

    // --- Frescura del cache ---

    /**
     * Momento (epoch millis) del último fetch de red que **realmente funcionó**. Lo estampa
     * [AhorroRepository.fetchMovements] en la rama de éxito, que es el único lugar donde se sabe
     * que la respuesta vino del server: `fetchMovements` devuelve el cache indistinguiblemente
     * cuando la red falla, así que marcarlo desde el ViewModel daría por fresco un fetch fallido y
     * dejaría la app 6 horas con datos viejos.
     *
     * Como todos los caminos que traen datos pasan por ahí (refresh manual, alta de movimiento,
     * pago de cuota, ABM de planes y los workers de segundo plano), todos renuevan la ventana.
     */
    var lastFetchAt: Long
        get() = prefs.getLong(KEY_LAST_FETCH_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_FETCH_AT, value).apply()

    /**
     * ¿Pasó la ventana de frescura? En el primer arranque (sin cache) siempre es `true`.
     *
     * Un `lastFetchAt` en el futuro (el reloj del teléfono saltó hacia adelante y después se
     * corrigió) también cuenta como vencido: si no, la app se quedaría sin refrescar sola hasta
     * que el reloj alcanzara esa marca.
     */
    fun necesitaFetchAutomatico(ahora: Long = System.currentTimeMillis()): Boolean {
        val transcurrido = ahora - lastFetchAt
        return transcurrido >= FETCH_TTL_MILLIS || transcurrido < 0
    }

    // Guarda los movimientos para el modo local
    fun saveLocalMovements(movements: List<Movement>) {
        try {
            val json = listAdapter.toJson(movements)
            prefs.edit().putString(KEY_LOCAL_MOVEMENTS, json).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Obtiene los movimientos del modo local
    fun getLocalMovements(): List<Movement> {
        val json = prefs.getString(KEY_LOCAL_MOVEMENTS, null) ?: return getMockMovements()
        return try {
            listAdapter.fromJson(json) ?: getMockMovements()
        } catch (e: Exception) {
            getMockMovements()
        }
    }

    // Guarda los movimientos cacheados que vienen de Google Sheets
    fun saveSheetsCache(movements: List<Movement>) {
        try {
            val json = listAdapter.toJson(movements)
            prefs.edit().putString(KEY_SHEETS_CACHE, json).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Obtiene los movimientos cacheados de Google Sheets
    fun getSheetsCache(): List<Movement> {
        val json = prefs.getString(KEY_SHEETS_CACHE, null) ?: return emptyList()
        return try {
            listAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // --- Módulo de cuotas ---

    // Planes en modo local (demo). Persisten todos (pendientes y completos); el filtrado
    // pendientes/pagos lo hace el repositorio derivando "completo" de los movimientos.
    fun saveLocalPlans(plans: List<CuotaPlan>) {
        try {
            prefs.edit().putString(KEY_LOCAL_PLANS, planListAdapter.toJson(plans)).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getLocalPlans(): List<CuotaPlan> {
        val json = prefs.getString(KEY_LOCAL_PLANS, null) ?: return getMockPlans()
        return try {
            planListAdapter.fromJson(json) ?: getMockPlans()
        } catch (e: Exception) {
            getMockPlans()
        }
    }

    // Cache de los planes PENDIENTES traídos de la red (los pagos son on-demand, no se cachean).
    fun savePlansCache(plans: List<CuotaPlan>) {
        try {
            prefs.edit().putString(KEY_PLANS_CACHE, planListAdapter.toJson(plans)).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getPlansCache(): List<CuotaPlan> {
        val json = prefs.getString(KEY_PLANS_CACHE, null) ?: return emptyList()
        return try {
            planListAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // --- Usuarios parametrizables ---

    // Cache de los dos usuarios (nombre + color). Sirve tanto para el arranque instantáneo desde la
    // red como para persistir las ediciones locales en modo demo. Vacío = todavía sin datos (DEFAULT).
    fun saveUsersCache(users: List<Usuario>) {
        try {
            prefs.edit().putString(KEY_USERS_CACHE, usuarioListAdapter.toJson(users)).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getUsersCache(): List<Usuario> {
        val json = prefs.getString(KEY_USERS_CACHE, null) ?: return emptyList()
        return try {
            usuarioListAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // --- Cola de movimientos pendientes de subir (ver [PendingMovement]) ---
    //
    // Toda mutación es read-modify-write, y el ViewModel y los workers la tocan desde hilos
    // distintos. El lock vive en el `companion object` **a propósito**: cada uno construye su
    // propio `PreferencesHelper` (el VM, el Worker de subida, el de sync, la Activity), así que un
    // `@Synchronized` de instancia no excluiría nada — cada quien tomaría su propio candado. Con
    // uno solo para toda la clase, un alta nueva no puede pisar el vaciado que está haciendo el
    // worker (ni al revés), que era la forma de perder un movimiento ya dado por registrado.

    fun getPendingMovements(): List<PendingMovement> {
        synchronized(COLA_LOCK) {
            val json = prefs.getString(KEY_PENDING_MOVEMENTS, null) ?: return emptyList()
            return try {
                pendingListAdapter.fromJson(json) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    private fun writePendingMovements(pendientes: List<PendingMovement>) {
        try {
            // commit() y no apply(): si el proceso muere justo después de encolar, un movimiento
            // que el usuario ya dio por registrado no puede perderse en el buffer de escritura.
            prefs.edit().putString(KEY_PENDING_MOVEMENTS, pendingListAdapter.toJson(pendientes)).commit()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun savePendingMovements(pendientes: List<PendingMovement>) = synchronized(COLA_LOCK) {
        writePendingMovements(pendientes)
    }

    /** Alta (o reemplazo, si ya estaba) de un pendiente por id de movimiento. */
    fun upsertPendingMovement(pendiente: PendingMovement) = synchronized(COLA_LOCK) {
        val actuales = getPendingMovements().toMutableList()
        val idx = actuales.indexOfFirst { it.movement.id == pendiente.movement.id }
        if (idx != -1) actuales[idx] = pendiente else actuales.add(pendiente)
        writePendingMovements(actuales)
    }

    /**
     * Aplica [transform] a un pendiente **solo si sigue en la cola**. Devuelve `false` si ya no
     * está — o sea, si se subió mientras tanto.
     *
     * Es la diferencia entre actualizar y resucitar: un `upsert` a secas vuelve a encolar un
     * movimiento ya escrito en la planilla, y como el alta es un `append`, terminaba duplicado.
     */
    fun updatePendingMovement(
        movementId: String,
        transform: (PendingMovement) -> PendingMovement
    ): Boolean {
        synchronized(COLA_LOCK) {
            val actuales = getPendingMovements().toMutableList()
            val idx = actuales.indexOfFirst { it.movement.id == movementId }
            if (idx == -1) return false
            actuales[idx] = transform(actuales[idx])
            writePendingMovements(actuales)
            return true
        }
    }

    /**
     * Saca de la cola la versión [subido] de un movimiento, si nadie la editó mientras se subía.
     * Devuelve `true` si la sacó.
     */
    fun removeUploadedPendingMovement(subido: Movement): Boolean = synchronized(COLA_LOCK) {
        val actuales = getPendingMovements()
        val restantes = quitarSiNoCambio(actuales, subido)
        writePendingMovements(restantes)
        restantes.size != actuales.size
    }

    /** Saca un pendiente de la cola. Se llama cuando el usuario borra la fila antes de que se
     *  llegara a subir. */
    fun removePendingMovement(movementId: String) = synchronized(COLA_LOCK) {
        writePendingMovements(getPendingMovements().filterNot { it.movement.id == movementId })
    }

    /**
     * Destraba los pendientes que quedaron esperando una compresión de ticket que ya no existe.
     * Se llama al arrancar: si el proceso murió en el medio, esa compresión no se puede retomar
     * (la URI de la galería/cámara ya no es accesible), así que el movimiento se sube sin foto en
     * vez de quedar atascado para siempre.
     */
    fun liberarPendientesEsperandoTicket() {
        synchronized(COLA_LOCK) {
            val actuales = getPendingMovements()
            if (actuales.none { it.esperandoTicket }) return
            writePendingMovements(actuales.map { it.copy(esperandoTicket = false) })
        }
    }

    // Planes de ejemplo para que el módulo no empiece vacío en modo demo (todas las cuotas impagas).
    private fun getMockPlans(): List<CuotaPlan> {
        return listOf(
            CuotaPlan(
                fechaCreacion = "2026-07-05 10:00", descripcion = "Notebook Lenovo",
                montoPorCuota = 150000.0, cantidadCuotas = 12, fechaPrimeraCuota = "2026-07",
                propietario = "Santiago", categoria = "Utilería", tarjeta = "Visa Santiago"
            ),
            CuotaPlan(
                fechaCreacion = "2026-06-18 19:30", descripcion = "Heladera no-frost",
                montoPorCuota = 80000.0, cantidadCuotas = 6, fechaPrimeraCuota = "2026-06",
                propietario = "Rocío", categoria = "Otros", tarjeta = "Naranja"
            )
        )
    }

    // Genera datos iniciales hermosos para que la app no empiece vacía
    private fun getMockMovements(): List<Movement> {
        return listOf(
            Movement(fecha = "2026-05-01", monto = 5000000.0, tipo = "Aporte", categoria = "Ingreso", responsable = "Santiago", esComun = false, descripcion = "Aporte de sueldo regular"),
            Movement(fecha = "2026-05-02", monto = 3000000.0, tipo = "Aporte", categoria = "Ingreso", responsable = "Rocío", esComun = false, descripcion = "Aporte de sueldo regular"),
            Movement(fecha = "2026-05-05", monto = 1200000.0, tipo = "Gasto", categoria = "Alquiler", responsable = "Santiago", esComun = true, descripcion = "Pago de alquiler compartido"),
            Movement(fecha = "2026-05-10", monto = 350000.0, tipo = "Gasto", categoria = "Supermercado", responsable = "Rocío", esComun = true, descripcion = "Compras semanales de víveres"),
            Movement(fecha = "2026-05-12", monto = 500000.0, tipo = "Gasto", categoria = "Ropa", responsable = "Santiago", esComun = false, descripcion = "Zapatillas nuevas (Personal)"),
            Movement(fecha = "2026-05-15", monto = 200000.0, tipo = "Gasto", categoria = "Regalos", responsable = "Rocío", esComun = false, descripcion = "Regalo cumpleaños amiga (Personal)"),
            Movement(fecha = "2026-05-20", monto = 150000.0, tipo = "Transferencia", categoria = "Ajuste", responsable = "Santiago", esComun = false, descripcion = "Transferencia interna a Rocío")
        )
    }
}
