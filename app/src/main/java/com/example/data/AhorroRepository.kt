package com.example.data

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class AhorroRepository(private val prefsHelper: PreferencesHelper) {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    private val sheetsService: SheetsService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        Retrofit.Builder()
            // The actual URL is passed via @Url, but Retrofit needs a base URL
            .baseUrl("https://script.google.com/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SheetsService::class.java)
    }

    /**
     * Obtiene todos los movimientos.
     * Si está en modo demo de red o falla, carga de cache.
     */
    suspend fun fetchMovements(webAppUrl: String): List<Movement> {
        if (prefsHelper.useLocalDemo) {
            return prefsHelper.getLocalMovements().filter { !it.eliminado }
        }

        if (webAppUrl.isEmpty()) {
            return prefsHelper.getSheetsCache()
        }

        return try {
            val requestUrl = if (webAppUrl.contains("?")) "$webAppUrl&action=GET" else "$webAppUrl?action=GET"
            val response = sheetsService.getMovements(requestUrl)
            
            if (response.isSuccessful) {
                val value = response.body()
                if (value?.status == "SUCCESS") {
                    val rows = value.data ?: emptyList()
                    prefsHelper.saveSheetsCache(rows)
                    rows
                } else {
                    Log.e("AhorroRepository", "WebApp returned error: ${value?.message}")
                    prefsHelper.getSheetsCache()
                }
            } else {
                Log.e("AhorroRepository", "Error request: ${response.code()}")
                prefsHelper.getSheetsCache()
            }
        } catch (e: Exception) {
            Log.e("AhorroRepository", "Network Exception: ${e.message}", e)
            prefsHelper.getSheetsCache()
        }
    }

    /**
     * Agrega un nuevo movimiento al Web App.
     */
    suspend fun saveMovement(
        webAppUrl: String, 
        movement: Movement, 
        imageInfo: ImageInfo? = null,
        folderId: String? = null
    ): Boolean {
        if (prefsHelper.useLocalDemo) {
            val current = prefsHelper.getLocalMovements().toMutableList()
            current.add(0, movement)
            prefsHelper.saveLocalMovements(current)
            return true
        }

        if (webAppUrl.isEmpty()) {
            return false
        }

        Log.d("AHORRO_DEBUG", "--------------------------------------------------")
        Log.d("AHORRO_DEBUG", "ENVIANDO A: $webAppUrl")
        return try {
            val req = WebAppRequest(
                action = "POST",
                body = movement,
                imageInfo = imageInfo,
                folderId = folderId
            )
            
            val response = sheetsService.addMovement(webAppUrl, req)
            Log.d("AHORRO_DEBUG", "CODIGO RESPUESTA: ${response.code()}")
            Log.d("AHORRO_DEBUG", "CUERPO: ${response.body()}")
            Log.d("AHORRO_DEBUG", "--------------------------------------------------")
            
            if (response.isSuccessful && response.body()?.status == "SUCCESS") {
                val cached = prefsHelper.getSheetsCache().toMutableList()
                cached.add(movement)
                prefsHelper.saveSheetsCache(cached)
                true
            } else {
                Log.e("AhorroRepository", "Error al agregar en WebApp")
                false
            }
        } catch (e: Exception) {
            Log.e("AhorroRepository", "Exception saveMovement: ${e.message}", e)
            false
        }
    }

    suspend fun deleteMovement(webAppUrl: String, movement: Movement): Boolean {
        if (prefsHelper.useLocalDemo) {
            val current = prefsHelper.getLocalMovements().toMutableList()
            // Baja lógica local
            val index = current.indexOfFirst { it.id == movement.id }
            if (index != -1) {
                current[index] = current[index].copy(eliminado = true)
                prefsHelper.saveLocalMovements(current)
            }
            return true
        }

        if (webAppUrl.isEmpty()) return false

        return try {
            val req = WebAppRequest(
                action = "DELETE",
                id = movement.id
            )
            val response = sheetsService.addMovement(webAppUrl, req)
            if (response.isSuccessful && response.body()?.status == "SUCCESS") {
                val cached = prefsHelper.getSheetsCache().toMutableList()
                val idx = cached.indexOfFirst { it.id == movement.id }
                if (idx != -1) {
                    cached[idx] = cached[idx].copy(eliminado = true)
                    prefsHelper.saveSheetsCache(cached)
                }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("AhorroRepository", "Exception deleteMovement: ${e.message}", e)
            false
        }
    }

    fun deleteLocalMovement(movement: Movement) {
        val current = prefsHelper.getLocalMovements().toMutableList()
        current.removeAll { it.id == movement.id }
        prefsHelper.saveLocalMovements(current)
    }

    // ---------------------------------------------------------------------------------------------
    // Módulo de cuotas
    // ---------------------------------------------------------------------------------------------

    private fun monthAbbrevToNum(abbr: String): String? = when (abbr.lowercase()) {
        "jan", "ene" -> "01"; "feb" -> "02"; "mar" -> "03"; "apr", "abr" -> "04"
        "may" -> "05"; "jun" -> "06"; "jul" -> "07"; "aug", "ago" -> "08"
        "sep" -> "09"; "oct" -> "10"; "nov" -> "11"; "dec", "dic" -> "12"
        else -> null
    }

    /** Extrae "yyyy-MM" de varios formatos, incluida la fecha "Wed Jul 01 2026 …" que Sheets puede
     *  devolver cuando guardó la celda como Date. Si no reconoce el formato, deja la entrada intacta. */
    private fun toYyyyMm(raw: String): String {
        val s = raw.trim()
        if (s.isEmpty()) return s
        Regex("^(\\d{4})-(\\d{2})").find(s)?.let { return "${it.groupValues[1]}-${it.groupValues[2]}" }
        Regex("^[A-Za-z]{3}\\s([A-Za-z]{3})\\s\\d{2}\\s(\\d{4})").find(s)?.let { m ->
            val mm = monthAbbrevToNum(m.groupValues[1]) ?: return s
            return "${m.groupValues[2]}-$mm"
        }
        return s
    }

    /** Normaliza `fechaCreacion` a "yyyy-MM-dd HH:mm" (sortable). Convierte el formato Date GMT. */
    private fun normalizeCreation(raw: String): String {
        val s = raw.trim()
        if (s.isEmpty()) return s
        if (Regex("^\\d{4}-\\d{2}-\\d{2}").containsMatchIn(s)) return s
        Regex("^[A-Za-z]{3}\\s([A-Za-z]{3})\\s(\\d{2})\\s(\\d{4})(?:\\s(\\d{2}:\\d{2}))?").find(s)?.let { m ->
            val mm = monthAbbrevToNum(m.groupValues[1]) ?: return s
            val hora = m.groupValues.getOrNull(4)?.ifEmpty { "00:00" } ?: "00:00"
            return "${m.groupValues[3]}-$mm-${m.groupValues[2]} $hora"
        }
        return s
    }

    /** Repara las fechas de un plan que pudieron llegar del server como Date/GMT. */
    private fun normalizePlan(p: CuotaPlan): CuotaPlan =
        p.copy(
            fechaPrimeraCuota = toYyyyMm(p.fechaPrimeraCuota),
            fechaCreacion = normalizeCreation(p.fechaCreacion)
        )

    /** Un plan está completo si tiene tantas cuotas distintas pagadas como cuotas totales.
     *  (Réplica local de la derivación que hace el servidor; evita que `data` dependa de `ui`.) */
    private fun isPlanComplete(plan: CuotaPlan, movs: List<Movement>): Boolean {
        if (plan.cantidadCuotas <= 0) return false
        val pagadas = movs
            .filter { !it.eliminado && it.planId == plan.id && it.cuotaNumero in 1..plan.cantidadCuotas }
            .map { it.cuotaNumero }
            .distinct()
            .size
        return pagadas >= plan.cantidadCuotas
    }

    /**
     * Planes PENDIENTES cacheados, **sin tocar la red** y ya normalizados/ordenados. Sirve para el
     * arranque instantáneo del módulo de cuotas (mismo patrón "cache al instante + refresco en
     * segundo plano" que los movimientos). En modo demo deriva los pendientes de los planes locales.
     */
    fun cachedPendingPlans(): List<CuotaPlan> {
        if (prefsHelper.useLocalDemo) {
            val movs = prefsHelper.getLocalMovements().filter { !it.eliminado }
            return prefsHelper.getLocalPlans()
                .filter { !it.eliminado }
                .filter { !isPlanComplete(it, movs) }
                .sortedByDescending { it.fechaCreacion }
        }
        return prefsHelper.getPlansCache()
            .map { normalizePlan(it) }
            .sortedByDescending { it.fechaCreacion }
    }

    /**
     * Trae los planes filtrados por estado. Por defecto ([soloPagos] = false) devuelve los
     * PENDIENTES (no completos) y los cachea; con [soloPagos] = true devuelve los completos
     * (on-demand, no se cachean). Siempre ordenados por fechaCreacion descendente.
     */
    suspend fun fetchPlans(webAppUrl: String, soloPagos: Boolean = false): List<CuotaPlan> {
        if (prefsHelper.useLocalDemo) {
            val movs = prefsHelper.getLocalMovements().filter { !it.eliminado }
            return prefsHelper.getLocalPlans()
                .filter { !it.eliminado }
                .filter { isPlanComplete(it, movs) == soloPagos }
                .sortedByDescending { it.fechaCreacion }
        }

        if (webAppUrl.isEmpty()) {
            return if (soloPagos) emptyList() else prefsHelper.getPlansCache().map { normalizePlan(it) }
        }

        val filtro = if (soloPagos) "pagos" else "pendientes"
        return try {
            val sep = if (webAppUrl.contains("?")) "&" else "?"
            val requestUrl = "$webAppUrl${sep}action=GET_PLANS&filtro=$filtro"
            val response = sheetsService.getPlans(requestUrl)

            if (response.isSuccessful && response.body()?.status == "SUCCESS") {
                val plans = (response.body()?.plans ?: emptyList())
                    .map { normalizePlan(it) }
                    .sortedByDescending { it.fechaCreacion }
                if (!soloPagos) prefsHelper.savePlansCache(plans)
                plans
            } else {
                Log.e("AhorroRepository", "GET_PLANS error: ${response.body()?.message}")
                if (soloPagos) emptyList() else prefsHelper.getPlansCache().map { normalizePlan(it) }
            }
        } catch (e: Exception) {
            Log.e("AhorroRepository", "Exception fetchPlans: ${e.message}", e)
            if (soloPagos) emptyList() else prefsHelper.getPlansCache().map { normalizePlan(it) }
        }
    }

    suspend fun savePlan(webAppUrl: String, plan: CuotaPlan): Boolean =
        upsertPlan(webAppUrl, plan, action = "POST")

    suspend fun updatePlan(webAppUrl: String, plan: CuotaPlan): Boolean =
        upsertPlan(webAppUrl, plan, action = "PUT")

    private suspend fun upsertPlan(webAppUrl: String, plan: CuotaPlan, action: String): Boolean {
        if (prefsHelper.useLocalDemo) {
            val current = prefsHelper.getLocalPlans().toMutableList()
            val idx = current.indexOfFirst { it.id == plan.id }
            if (idx != -1) current[idx] = plan else current.add(0, plan)
            prefsHelper.saveLocalPlans(current)
            return true
        }

        if (webAppUrl.isEmpty()) return false

        return try {
            val req = WebAppRequest(action = action, entity = "plan", plan = plan)
            val response = sheetsService.addMovement(webAppUrl, req)
            if (response.isSuccessful && response.body()?.status == "SUCCESS") {
                // Mantener el cache de pendientes coherente sin re-pedir a la red.
                val cached = prefsHelper.getPlansCache().toMutableList()
                val idx = cached.indexOfFirst { it.id == plan.id }
                if (idx != -1) cached[idx] = plan else cached.add(0, plan)
                prefsHelper.savePlansCache(cached)
                true
            } else {
                Log.e("AhorroRepository", "upsertPlan error: ${response.body()?.message}")
                false
            }
        } catch (e: Exception) {
            Log.e("AhorroRepository", "Exception upsertPlan: ${e.message}", e)
            false
        }
    }

    /** Baja lógica de un plan. */
    suspend fun deletePlan(webAppUrl: String, plan: CuotaPlan): Boolean {
        if (prefsHelper.useLocalDemo) {
            val current = prefsHelper.getLocalPlans().toMutableList()
            val idx = current.indexOfFirst { it.id == plan.id }
            if (idx != -1) {
                current[idx] = current[idx].copy(eliminado = true)
                prefsHelper.saveLocalPlans(current)
            }
            return true
        }

        if (webAppUrl.isEmpty()) return false

        return try {
            val req = WebAppRequest(action = "DELETE", entity = "plan", id = plan.id)
            val response = sheetsService.addMovement(webAppUrl, req)
            if (response.isSuccessful && response.body()?.status == "SUCCESS") {
                val cached = prefsHelper.getPlansCache().toMutableList()
                cached.removeAll { it.id == plan.id }
                prefsHelper.savePlansCache(cached)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("AhorroRepository", "Exception deletePlan: ${e.message}", e)
            false
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Usuarios parametrizables
    // ---------------------------------------------------------------------------------------------

    /**
     * Config de usuarios cacheada, **sin tocar la red**. Para el arranque instantáneo del tema y la
     * identidad (mismo patrón que [cachedPendingPlans]). Cae a [UsuariosConfig.DEFAULT] si no hay cache.
     */
    fun cachedUsuarios(): UsuariosConfig = UsuariosConfig.fromList(prefsHelper.getUsersCache())

    /** Persiste en cache la config completa (los dos slots) reemplazando el usuario editado. */
    private fun persistUsuario(usuario: Usuario) {
        val current = cachedUsuarios()
        val updated =
            if (current.primario.slotKey.equals(usuario.slotKey, ignoreCase = true)) current.copy(primario = usuario)
            else current.copy(secundario = usuario)
        prefsHelper.saveUsersCache(updated.todos)
    }

    /**
     * Trae la config de usuarios. En demo/offline/fallo devuelve el cache (o [UsuariosConfig.DEFAULT]).
     * En red, `GET_USERS`; si la hoja está vacía cae al DEFAULT sin pisar el cache existente.
     */
    suspend fun fetchUsuarios(webAppUrl: String): UsuariosConfig {
        if (prefsHelper.useLocalDemo || webAppUrl.isEmpty()) {
            return cachedUsuarios()
        }
        return try {
            val sep = if (webAppUrl.contains("?")) "&" else "?"
            val response = sheetsService.getUsers("$webAppUrl${sep}action=GET_USERS")
            if (response.isSuccessful && response.body()?.status == "SUCCESS") {
                val users = response.body()?.users ?: emptyList()
                val config = UsuariosConfig.fromList(users)
                if (users.isNotEmpty()) prefsHelper.saveUsersCache(config.todos)
                config
            } else {
                Log.e("AhorroRepository", "GET_USERS error: ${response.body()?.message}")
                cachedUsuarios()
            }
        } catch (e: Exception) {
            Log.e("AhorroRepository", "Exception fetchUsuarios: ${e.message}", e)
            cachedUsuarios()
        }
    }

    /**
     * Edita el perfil de un usuario (nombre/color). `PUT entity:"user"`; actualiza el cache local.
     * En demo solo persiste local. `slotKey`/`orden` son inmutables (no se envían para cambiarlos).
     */
    suspend fun updateUsuario(webAppUrl: String, usuario: Usuario): Boolean {
        if (prefsHelper.useLocalDemo) {
            persistUsuario(usuario)
            return true
        }
        if (webAppUrl.isEmpty()) return false
        return try {
            val req = WebAppRequest(action = "PUT", entity = "user", user = usuario)
            val response = sheetsService.addMovement(webAppUrl, req)
            if (response.isSuccessful && response.body()?.status == "SUCCESS") {
                persistUsuario(usuario)
                true
            } else {
                Log.e("AhorroRepository", "updateUsuario error: ${response.body()?.message}")
                false
            }
        } catch (e: Exception) {
            Log.e("AhorroRepository", "Exception updateUsuario: ${e.message}", e)
            false
        }
    }
}
