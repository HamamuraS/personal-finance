package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AhorroRepository
import com.example.data.Movement
import com.example.data.PreferencesHelper
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BalanceBreakdown(
    val totalPozo: Double,
    val santiagoAportes: Double,
    val santiagoGastosPersonales: Double,
    val rocioAportes: Double,
    val rocioGastosPersonales: Double,
    val gastosComunesTotales: Double,
    val santiagoGastosComunes: Double,
    val rocioGastosComunes: Double,
    val santiagoTransfersEnviadas: Double,
    val rocioTransfersEnviadas: Double,
    val santiagoSaldoFinal: Double,
    val rocioSaldoFinal: Double,
    val totalAportesMes: Double,
    val totalGastosMes: Double
)

class AhorroViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsHelper = PreferencesHelper(application)
    private val repository = AhorroRepository(prefsHelper)

    private var _cachedAllMovements: List<Movement> = emptyList()

    private val _availableMonths = MutableStateFlow<List<String>>(emptyList())
    val availableMonths: StateFlow<List<String>> = _availableMonths.asStateFlow()

    private val _selectedMonth = MutableStateFlow<String>("")
    val selectedMonth: StateFlow<String> = _selectedMonth.asStateFlow()
    
    private val _isCurrentMonth = MutableStateFlow(true)
    val isCurrentMonth: StateFlow<Boolean> = _isCurrentMonth.asStateFlow()

    // Estados de UI
    private val _movements = MutableStateFlow<List<Movement>>(emptyList())
    val movements: StateFlow<List<Movement>> = _movements.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Configuración
    private val _spreadsheetId = MutableStateFlow(prefsHelper.spreadsheetId)
    val spreadsheetId: StateFlow<String> = _spreadsheetId.asStateFlow()

    private val _useLocalDemo = MutableStateFlow(prefsHelper.useLocalDemo)
    val useLocalDemo: StateFlow<Boolean> = _useLocalDemo.asStateFlow()

    private val _currentUserProfile = MutableStateFlow(prefsHelper.currentUserProfile)
    val currentUserProfile: StateFlow<String> = _currentUserProfile.asStateFlow()

    private val _isDarkMode = MutableStateFlow(prefsHelper.isDarkMode)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    // Estado calculado de balances
    private val _balance = MutableStateFlow(calculateBalances(emptyList()))
    val balance: StateFlow<BalanceBreakdown> = _balance.asStateFlow()

    init {
        loadConfigAndData()
    }

    fun loadConfigAndData() {
        _spreadsheetId.value = prefsHelper.spreadsheetId
        _useLocalDemo.value = prefsHelper.useLocalDemo
        _currentUserProfile.value = prefsHelper.currentUserProfile
        refreshData()
    }

    fun updateFilteredData() {
        val currentSel = _selectedMonth.value
        val actualCurrent = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date())
        _isCurrentMonth.value = (currentSel == actualCurrent)

        val filtered = _cachedAllMovements.filter {
            val fechaPrefix = if (it.fecha.length >= 7) it.fecha.substring(0, 7) else ""
            fechaPrefix == currentSel
        }
        _movements.value = filtered
        _balance.value = calculateBalances(filtered)
    }

    fun setSelectedMonth(month: String) {
        _selectedMonth.value = month
        updateFilteredData()
    }

    /**
     * Actualiza la lista de movimientos y recalcula los saldos
     */
    fun refreshData() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            doRefreshData()
            _isLoading.value = false
        }
    }

    private fun normalizeFechaToString(input: String): String {
        try {
            if (input.matches(Regex("^[a-zA-Z]{3}\\s[a-zA-Z]{3}\\s\\d{2}\\s\\d{4}.*"))) {
                val parts = input.split(Regex("\\s+"))
                if (parts.size >= 4) {
                    val month = parts[1]
                    val day = parts[2].padStart(2, '0')
                    val year = parts[3]
                    var time = if (parts.size >= 5) parts[4] else ""
                    
                    if (time.contains(":")) {
                        val timeParts = time.split(":")
                        if (timeParts.size >= 2) time = "${timeParts[0]}:${timeParts[1]}"
                    } else {
                        time = ""
                    }
                    
                    val mEs = when(month.lowercase()) {
                         "jan", "ene" -> "01"; "feb" -> "02"; "mar" -> "03"; "apr", "abr" -> "04"
                         "may" -> "05"; "jun" -> "06"; "jul" -> "07"; "aug", "ago" -> "08"
                         "sep" -> "09"; "oct" -> "10"; "nov" -> "11"; "dec", "dic" -> "12"
                         else -> "01"
                    }
                    
                    return "$year-$mEs-$day${if (time != "00:00" && time.isNotEmpty()) " $time" else ""}"
                }
            }
        } catch (e: Exception) {}
        return input
    }

    private suspend fun doRefreshData(): Boolean {
        return try {
            val rawList = repository.fetchMovements(_spreadsheetId.value)
            val list = rawList.map { it.copy(fecha = normalizeFechaToString(it.fecha)) }
            val sortedList = list.sortedWith(compareByDescending<Movement> { it.fecha }.thenByDescending { it.id })
            _cachedAllMovements = sortedList
                
            val currentRealMonth = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date())
            val monthsSet = sortedList.mapNotNull { 
                if (it.fecha.length >= 7) it.fecha.substring(0, 7) else null 
            }.toSortedSet(reverseOrder())
                
            if (!monthsSet.contains(currentRealMonth)) {
                monthsSet.add(currentRealMonth)
            }
                
            _availableMonths.value = monthsSet.toList()
            if (_selectedMonth.value.isEmpty()) {
                _selectedMonth.value = currentRealMonth
            }
                
            updateFilteredData()
            true
        } catch (e: Exception) {
            _errorMessage.value = "Error al recargar datos: ${e.localizedMessage}"
            Log.e("AhorroViewModel", "Error cargando datos", e)
            false
        }
    }

    /**
     * Agrega un nuevo movimiento
     */
    fun addMovement(
        fecha: String,
        monto: Double,
        tipo: String,
        categoria: String,
        responsable: String,
        esComun: Boolean,
        descripcion: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            val newMovement = Movement(
                fecha = fecha,
                monto = monto,
                tipo = tipo,
                categoria = categoria,
                responsable = responsable,
                esComun = esComun,
                descripcion = descripcion
            )

            try {
                val success = repository.saveMovement(_spreadsheetId.value, newMovement)
                if (success) {
                    doRefreshData()
                    onSuccess()
                } else {
                    _errorMessage.value = "No se pudo sincronizar el movimiento. Asegúrese de que la URL de Web App es válida."
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error al guardar: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Elimina un movimiento localmente (solo modo demo)
     */
    fun deleteMovement(movement: Movement) {
        viewModelScope.launch {
            repository.deleteLocalMovement(movement)
            refreshData()
        }
    }

    /**
     * Guarda la configuración
     */
    fun saveSheetsConfig(newSheetIdOrUrl: String, useDemo: Boolean) {
        prefsHelper.spreadsheetId = newSheetIdOrUrl
        prefsHelper.useLocalDemo = useDemo
        
        _spreadsheetId.value = newSheetIdOrUrl
        _useLocalDemo.value = useDemo
        
        refreshData()
    }

    /**
     * Cambiar de perfil de usuario
     */
    fun setCurrentUserProfile(profile: String) {
        prefsHelper.currentUserProfile = profile
        _currentUserProfile.value = profile
    }

    fun setIsDarkMode(isDark: Boolean) {
        prefsHelper.isDarkMode = isDark
        _isDarkMode.value = isDark
    }

    /**
     * Ejecuta el cálculo completo del balance contable de acuerdo a las reglas de negocio:
     * - Aportes: Aumentan únicamente el saldo del aportante.
     * - Gastos personales: Impactan únicamente el saldo del responsable.
     * - Gastos comunes: Se dividen automáticamente 50% entre ambos (Santiago y Rocío).
     * - Transferencias: Disminuyen del responsable y aumentan del otro, sin alterar el pozo total.
     */
    private fun calculateBalances(list: List<Movement>): BalanceBreakdown {
        var sAportes = 0.0
        var sGastosPersonales = 0.0
        var sTransfersHechas = 0.0

        var rAportes = 0.0
        var rGastosPersonales = 0.0
        var rTransfersHechas = 0.0

        var gastosComunesTotales = 0.0

        // Variables secundarias para los reportes de mes en curso (p.ej. Mayo 2026)
        var totalAportesMes = 0.0
        var totalGastosMes = 0.0

        for (m in list) {
            val isSantiago = m.responsable.equals("Santiago", ignoreCase = true)

            when (m.tipo.lowercase()) {
                "aporte" -> {
                    if (isSantiago) sAportes += m.monto else rAportes += m.monto
                    totalAportesMes += m.monto
                }
                "gasto" -> {
                    if (m.esComun) {
                        gastosComunesTotales += m.monto
                    } else {
                        if (isSantiago) sGastosPersonales += m.monto else rGastosPersonales += m.monto
                    }
                    totalGastosMes += m.monto
                }
                "transferencia" -> {
                    if (isSantiago) sTransfersHechas += m.monto else rTransfersHechas += m.monto
                }
            }
        }

        // Un gasto común se divide exactamente 50% entre los dos
        val sGastosComunes = gastosComunesTotales / 2.0
        val rGastosComunes = gastosComunesTotales / 2.0

        // Balances Finales
        val sSaldoFinal = sAportes - sGastosPersonales - sGastosComunes - sTransfersHechas + rTransfersHechas
        val rSaldoFinal = rAportes - rGastosPersonales - rGastosComunes - rTransfersHechas + sTransfersHechas

        val totalPozo = sSaldoFinal + rSaldoFinal

        return BalanceBreakdown(
            totalPozo = totalPozo,
            santiagoAportes = sAportes,
            santiagoGastosPersonales = sGastosPersonales,
            rocioAportes = rAportes,
            rocioGastosPersonales = rGastosPersonales,
            gastosComunesTotales = gastosComunesTotales,
            santiagoGastosComunes = sGastosComunes,
            rocioGastosComunes = rGastosComunes,
            santiagoTransfersEnviadas = sTransfersHechas,
            rocioTransfersEnviadas = rTransfersHechas,
            santiagoSaldoFinal = sSaldoFinal,
            rocioSaldoFinal = rSaldoFinal,
            totalAportesMes = totalAportesMes,
            totalGastosMes = totalGastosMes
        )
    }
}
