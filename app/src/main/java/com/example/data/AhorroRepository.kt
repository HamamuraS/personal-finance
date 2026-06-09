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
}
