package com.example.data

import android.util.Log
import com.example.domain.CuotasEngine
import com.example.util.Fechas
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Acceso a los datos: el Web App de Apps Script en modo normal, o SharedPreferences en Modo Local
 * (Demo). Toda lectura de red cae al cache si falla; toda escritura devuelve si se confirmó.
 *
 * [scriptUrl] y [folderId] vienen fijos en el APK (ver [AppConfig]); son parámetros solo para poder
 * apuntar el repositorio a un servidor de prueba.
 */
class AhorroRepository(
    private val prefsHelper: PreferencesHelper,
    private val scriptUrl: String = prefsHelper.scriptUrl,
    private val folderId: String = prefsHelper.folderId
) {

    private val sheetsService: SheetsService get() = WebAppClient.service

    private val demo: Boolean get() = prefsHelper.useLocalDemo

    private fun urlCon(query: String): String =
        if (scriptUrl.contains("?")) "$scriptUrl&$query" else "$scriptUrl?$query"

    /**
     * Corre un pedido al Web App. Devuelve la respuesta si el servidor la confirmó (`SUCCESS`), o null
     * si no (HTTP de error, `status` distinto, excepción de red). La cancelación de la corrutina no es
     * un error: se propaga.
     */
    private suspend fun pedir(etiqueta: String, llamada: suspend () -> retrofit2.Response<WebAppResponse>): WebAppResponse? =
        try {
            val response = llamada()
            val body = response.body()
            if (response.isSuccessful && body?.status == WebApp.SUCCESS) {
                body
            } else {
                Log.e(TAG, "$etiqueta: ${response.code()} ${body?.message.orEmpty()}")
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "$etiqueta: ${e.message}", e)
            null
        }

    private suspend fun post(etiqueta: String, request: WebAppRequest): WebAppResponse? =
        pedir(etiqueta) { sheetsService.post(scriptUrl, request) }

    // ---------------------------------------------------------------------------------------------
    // Movimientos
    // ---------------------------------------------------------------------------------------------

    /** Todos los movimientos. En demo, los locales; sin URL o si la red falla, el cache. */
    suspend fun fetchMovements(): List<Movement> = withContext(Dispatchers.IO) {
        if (demo) return@withContext prefsHelper.getLocalMovements().filter { !it.eliminado }
        if (scriptUrl.isEmpty()) return@withContext prefsHelper.getSheetsCache()

        val respuesta = pedir("GET") { sheetsService.get(urlCon("action=${WebApp.GET_MOVEMENTS}")) }
            ?: return@withContext prefsHelper.getSheetsCache()
        val rows = respuesta.data ?: emptyList()
        prefsHelper.saveSheetsCache(rows)
        // Único punto donde consta que la red respondió de verdad: acá se renueva la ventana de
        // frescura que evita el fetch automático en cada arranque.
        prefsHelper.lastFetchAt = System.currentTimeMillis()
        rows
    }

    /**
     * Guarda un movimiento con `PUT`, un *upsert*: el Web App busca por id y reemplaza la fila si
     * existe, o la agrega si no. Toda escritura es así —altas de la cola, pagos de cuotas, filas de
     * apertura— para que reintentar nunca duplique.
     */
    suspend fun saveMovement(movement: Movement, imageInfo: ImageInfo? = null): Boolean = withContext(Dispatchers.IO) {
        if (demo) {
            val current = prefsHelper.getLocalMovements().toMutableList()
            current.removeAll { it.id == movement.id }
            current.add(0, movement)
            prefsHelper.saveLocalMovements(current)
            return@withContext true
        }
        if (scriptUrl.isEmpty()) return@withContext false

        val request = WebAppRequest(action = WebApp.PUT, body = movement, imageInfo = imageInfo, folderId = folderId)
        post("saveMovement", request) ?: return@withContext false
        prefsHelper.saveSheetsCache(prefsHelper.getSheetsCache().reemplazarOAgregar(movement) { it.id })
        true
    }

    /** Baja lógica de un movimiento. Confirmada, el movimiento sale del cache. */
    suspend fun deleteMovement(movement: Movement): Boolean = withContext(Dispatchers.IO) {
        if (demo) {
            val current = prefsHelper.getLocalMovements()
            if (current.any { it.id == movement.id }) {
                prefsHelper.saveLocalMovements(current.map { if (it.id == movement.id) it.copy(eliminado = true) else it })
            }
            return@withContext true
        }
        if (scriptUrl.isEmpty()) return@withContext false

        post("deleteMovement", WebAppRequest(action = WebApp.DELETE, id = movement.id)) ?: return@withContext false
        // Se saca del cache (antes quedaba marcado como eliminado y, si el refresco siguiente fallaba,
        // el movimiento borrado volvía a mostrarse y a contar en los saldos).
        prefsHelper.saveSheetsCache(prefsHelper.getSheetsCache().filterNot { it.id == movement.id })
        true
    }

    // ---------------------------------------------------------------------------------------------
    // Módulo de cuotas
    // ---------------------------------------------------------------------------------------------

    /** Repara las fechas de un plan que pudieron llegar del server como Date/GMT. */
    private fun normalizePlan(p: CuotaPlan): CuotaPlan =
        p.copy(
            fechaPrimeraCuota = Fechas.aMes(p.fechaPrimeraCuota),
            fechaCreacion = Fechas.aFechaHora(p.fechaCreacion)
        )

    private fun planesCacheados(): List<CuotaPlan> = prefsHelper.getPlansCache().map { normalizePlan(it) }

    /** Planes locales (demo) pendientes o completos, con "completo" derivado de los movimientos. */
    private fun planesLocales(completos: Boolean): List<CuotaPlan> {
        val movs = prefsHelper.getLocalMovements().filter { !it.eliminado }
        return prefsHelper.getLocalPlans()
            .filter { !it.eliminado }
            .filter { CuotasEngine.estaCompleto(it, movs) == completos }
            .sortedByDescending { it.fechaCreacion }
    }

    /**
     * Planes PENDIENTES cacheados, **sin tocar la red** y ya normalizados/ordenados. Sirve para el
     * arranque instantáneo del módulo de cuotas (mismo patrón "cache al instante + refresco en
     * segundo plano" que los movimientos). En modo demo deriva los pendientes de los planes locales.
     */
    fun cachedPendingPlans(): List<CuotaPlan> {
        if (demo) return planesLocales(completos = false)
        return planesCacheados()
            .sortedByDescending { it.fechaCreacion }
            .distinctBy { it.id }
    }

    /**
     * Trae los planes filtrados por estado. Por defecto ([soloPagos] = false) devuelve los
     * PENDIENTES (no completos) y los cachea; con [soloPagos] = true devuelve los completos
     * (on-demand, no se cachean). Siempre ordenados por fechaCreacion descendente.
     */
    suspend fun fetchPlans(soloPagos: Boolean = false): List<CuotaPlan> = withContext(Dispatchers.IO) {
        if (demo) return@withContext planesLocales(completos = soloPagos)
        val fallback = { if (soloPagos) emptyList() else planesCacheados() }
        if (scriptUrl.isEmpty()) return@withContext fallback()

        val filtro = if (soloPagos) WebApp.FILTRO_PAGOS else WebApp.FILTRO_PENDIENTES
        val respuesta = pedir("GET_PLANS") {
            sheetsService.get(urlCon("action=${WebApp.GET_PLANS}&filtro=$filtro"))
        } ?: return@withContext fallback()
        // distinctBy: la pantalla de Cuotas usa el id como key de la lista, así que un plan
        // duplicado en la planilla la haría crashear en vez de mostrarse dos veces.
        val plans = (respuesta.plans ?: emptyList())
            .map { normalizePlan(it) }
            .sortedByDescending { it.fechaCreacion }
            .distinctBy { it.id }
        if (!soloPagos) prefsHelper.savePlansCache(plans)
        plans
    }

    /**
     * Alta o edición de un plan. Va con `PUT`: el Web App resuelve el upsert por id (y si no
     * encuentra la fila, la agrega), así que reintentar un alta que quedó sin confirmar pisa la fila
     * en vez de duplicar el plan.
     */
    suspend fun guardarPlan(plan: CuotaPlan): Boolean = withContext(Dispatchers.IO) {
        if (demo) {
            prefsHelper.saveLocalPlans(prefsHelper.getLocalPlans().reemplazarOAgregar(plan, alPrincipio = true) { it.id })
            return@withContext true
        }
        if (scriptUrl.isEmpty()) return@withContext false

        post("upsertPlan", WebAppRequest(action = WebApp.PUT, entity = WebApp.ENTITY_PLAN, plan = plan))
            ?: return@withContext false
        // Mantener el cache de pendientes coherente sin re-pedir a la red.
        prefsHelper.savePlansCache(prefsHelper.getPlansCache().reemplazarOAgregar(plan, alPrincipio = true) { it.id })
        true
    }

    /** Baja lógica de un plan. */
    suspend fun deletePlan(plan: CuotaPlan): Boolean = withContext(Dispatchers.IO) {
        if (demo) {
            val current = prefsHelper.getLocalPlans()
            if (current.any { it.id == plan.id }) {
                prefsHelper.saveLocalPlans(current.map { if (it.id == plan.id) it.copy(eliminado = true) else it })
            }
            return@withContext true
        }
        if (scriptUrl.isEmpty()) return@withContext false

        post("deletePlan", WebAppRequest(action = WebApp.DELETE, entity = WebApp.ENTITY_PLAN, id = plan.id))
            ?: return@withContext false
        prefsHelper.savePlansCache(prefsHelper.getPlansCache().filterNot { it.id == plan.id })
        true
    }

    /**
     * Le pide al Web App que congele, para cada plan, qué cuotas ya estaban pagadas **antes** de
     * [mes], escribiéndolas por unión en la columna K de la hoja "Planes".
     *
     * Es la mitad "cuotas" del corte de mes: sin esto, purgar las hojas anteriores devuelve a
     * pendiente cuotas ya pagadas, porque su único rastro es el movimiento que vivía ahí. Se calcula
     * en el servidor —que ya recorre todas las hojas para derivar los planes completos— así que el
     * cliente no necesita tener cargado el historial entero.
     *
     * En modo demo no hay servidor y no hay nada que purgar: devuelve `true` sin hacer nada.
     */
    suspend fun snapshotCuotasPrevias(mes: String): Boolean = withContext(Dispatchers.IO) {
        if (demo) return@withContext true
        if (scriptUrl.isEmpty()) return@withContext false
        post("SNAPSHOT_CUOTAS", WebAppRequest(action = WebApp.SNAPSHOT_CUOTAS, mes = mes)) != null
    }

    // ---------------------------------------------------------------------------------------------
    // Usuarios parametrizables
    // ---------------------------------------------------------------------------------------------

    /**
     * Config de usuarios cacheada, **sin tocar la red**. Para el arranque instantáneo del tema y la
     * identidad (mismo patrón que [cachedPendingPlans]). Cae a [UsuariosConfig.DEFAULT] si no hay cache.
     */
    fun cachedUsuarios(): UsuariosConfig = UsuariosConfig.fromList(prefsHelper.getUsersCache())

    /** Persiste en cache la config completa reemplazando el usuario editado (por slotKey). */
    private fun persistUsuario(usuario: Usuario) {
        prefsHelper.saveUsersCache(cachedUsuarios().conUsuario(usuario).todos)
    }

    /**
     * Trae la config de usuarios. En demo/offline/fallo devuelve el cache (o [UsuariosConfig.DEFAULT]).
     * En red, `GET_USERS`; si la hoja está vacía cae al DEFAULT sin pisar el cache existente.
     */
    suspend fun fetchUsuarios(): UsuariosConfig = withContext(Dispatchers.IO) {
        if (demo || scriptUrl.isEmpty()) return@withContext cachedUsuarios()
        val respuesta = pedir("GET_USERS") { sheetsService.get(urlCon("action=${WebApp.GET_USERS}")) }
            ?: return@withContext cachedUsuarios()
        val users = respuesta.users ?: emptyList()
        val config = UsuariosConfig.fromList(users)
        if (users.isNotEmpty()) prefsHelper.saveUsersCache(config.todos)
        config
    }

    /**
     * Edita el perfil de un usuario (nombre/color). `PUT entity:"user"`; actualiza el cache local.
     * En demo solo persiste local. `slotKey`/`orden` son inmutables (no se envían para cambiarlos).
     */
    suspend fun updateUsuario(usuario: Usuario): Boolean = withContext(Dispatchers.IO) {
        if (demo) {
            persistUsuario(usuario)
            return@withContext true
        }
        if (scriptUrl.isEmpty()) return@withContext false
        post("updateUsuario", WebAppRequest(action = WebApp.PUT, entity = WebApp.ENTITY_USER, user = usuario))
            ?: return@withContext false
        persistUsuario(usuario)
        true
    }

    private companion object {
        const val TAG = "AhorroRepository"
    }
}

/** La lista con [item] en el lugar del que tenga su misma clave, o agregado si no estaba. */
internal fun <T> List<T>.reemplazarOAgregar(item: T, alPrincipio: Boolean = false, clave: (T) -> String): List<T> {
    val idx = indexOfFirst { clave(it) == clave(item) }
    return when {
        idx != -1 -> toMutableList().also { it[idx] = item }
        alPrincipio -> listOf(item) + this
        else -> this + item
    }
}
