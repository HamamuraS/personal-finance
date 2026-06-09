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

    companion object {
        private const val KEY_SCRIPT_URL = "script_url"
        private const val KEY_FOLDER_ID = "folder_id"
        private const val KEY_USE_LOCAL_DEMO = "use_local_demo"
        private const val KEY_LOCAL_MOVEMENTS = "local_movements"
        private const val KEY_SHEETS_CACHE = "sheets_cache"
        private const val KEY_USER_PROFILE = "user_profile"
        private const val KEY_IS_DARK_MODE = "is_dark_mode"
    }

    var isDarkMode: Boolean
        get() = prefs.getBoolean(KEY_IS_DARK_MODE, true)
        set(value) {
            prefs.edit().putBoolean(KEY_IS_DARK_MODE, value).apply()
        }

    var scriptUrl: String
        get() = prefs.getString(KEY_SCRIPT_URL, "https://script.google.com/macros/s/AKfycby1ADi59OoCza8Xj-HG3nuYBgLFAmw90hlAD8fPchlLQLmMyYnM7AtVI1O5NdpU7-MHRg/exec") ?: "https://script.google.com/macros/s/AKfycby1ADi59OoCza8Xj-HG3nuYBgLFAmw90hlAD8fPchlLQLmMyYnM7AtVI1O5NdpU7-MHRg/exec"
        set(value) {
            prefs.edit().putString(KEY_SCRIPT_URL, value.trim()).apply()
        }

    var folderId: String
        get() = prefs.getString(KEY_FOLDER_ID, "1LT_t2a7WBFe6wGjwJ5XuTYsS7gvjr3jU") ?: "1LT_t2a7WBFe6wGjwJ5XuTYsS7gvjr3jU"
        set(value) {
            prefs.edit().putString(KEY_FOLDER_ID, value.trim()).apply()
        }

    var useLocalDemo: Boolean
        get() = prefs.getBoolean(KEY_USE_LOCAL_DEMO, true)
        set(value) = prefs.edit().putBoolean(KEY_USE_LOCAL_DEMO, value).apply()

    var currentUserProfile: String
        get() = prefs.getString(KEY_USER_PROFILE, "Santiago") ?: "Santiago"
        set(value) = prefs.edit().putString(KEY_USER_PROFILE, value).apply()

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

    // Genera datos iniciales hermosos para que la app no empiece vacía
    private fun getMockMovements(): List<Movement> {
        return listOf(
            Movement(fecha = "2026-05-01", monto = 5000000.0, tipo = "Aporte", categoria = "Sueldo Santiago", responsable = "Santiago", esComun = false, descripcion = "Aporte de sueldo regular"),
            Movement(fecha = "2026-05-02", monto = 3000000.0, tipo = "Aporte", categoria = "Sueldo Rocío", responsable = "Rocío", esComun = false, descripcion = "Aporte de sueldo regular"),
            Movement(fecha = "2026-05-05", monto = 1200000.0, tipo = "Gasto", categoria = "Alquiler", responsable = "Santiago", esComun = true, descripcion = "Pago de alquiler compartido"),
            Movement(fecha = "2026-05-10", monto = 350000.0, tipo = "Gasto", categoria = "Supermercado", responsable = "Rocío", esComun = true, descripcion = "Compras semanales de víveres"),
            Movement(fecha = "2026-05-12", monto = 500000.0, tipo = "Gasto", categoria = "Ropa", responsable = "Santiago", esComun = false, descripcion = "Zapatillas nuevas (Personal)"),
            Movement(fecha = "2026-05-15", monto = 200000.0, tipo = "Gasto", categoria = "Regalos", responsable = "Rocío", esComun = false, descripcion = "Regalo cumpleaños amiga (Personal)"),
            Movement(fecha = "2026-05-20", monto = 150000.0, tipo = "Transferencia", categoria = "Ajuste", responsable = "Santiago", esComun = false, descripcion = "Transferencia interna a Rocío")
        )
    }
}
