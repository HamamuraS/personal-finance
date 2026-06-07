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
    val totalGastosMes: Double,
    val santiagoEfectivo: Double,
    val santiagoVirtual: Double,
    val rocioEfectivo: Double,
    val rocioVirtual: Double
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
            
            // Si el mes seleccionado es el actual, verificar arrastre de saldo
            if (_isCurrentMonth.value) {
                checkAndInjectInitialBalances(currentRealMonth)
            }
            
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
        metodoPago: String,
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
                descripcion = descripcion,
                metodoPago = metodoPago
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
     * Verifica si el mes actual ya tiene los saldos iniciales (Aportes con categoría "Saldo inicial")
     * Si no los tiene, busca el mes anterior, calcula sus saldos finales e inyecta los nuevos movimientos.
     */
    private fun checkAndInjectInitialBalances(currentMonth: String) {
        viewModelScope.launch {
            // Buscamos si ya existen movimientos de "Saldo inicial" en el mes actual
            val hasInitialBalance = _movements.value.any { 
                it.tipo.lowercase() == "aporte" && it.categoria == "Saldo inicial" 
            }
            
            if (hasInitialBalance) return@launch
            
            // Determinar mes anterior (yyyy-MM)
            val sdf = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US)
            val cal = java.util.Calendar.getInstance()
            cal.time = sdf.parse(currentMonth) ?: return@launch
            cal.add(java.util.Calendar.MONTH, -1)
            val prevMonth = sdf.format(cal.time)
            
            // Si no hay datos previos o el mes anterior no está en availableMonths, no podemos arrastrar nada fiable
            if (!_availableMonths.value.contains(prevMonth)) return@launch
            
            // Calcular balances del mes anterior
            val prevMonthMovements = _cachedAllMovements.filter {
                it.fecha.startsWith(prevMonth)
            }
            if (prevMonthMovements.isEmpty()) return@launch
            
            val prevBalance = calculateBalances(prevMonthMovements)
            
            // Inyectar aportes de saldo inicial para Santiago y Rocío
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
            
            var success = true
            if (prevBalance.santiagoSaldoFinal != 0.0) {
                val movS = Movement(
                    fecha = today,
                    monto = prevBalance.santiagoSaldoFinal,
                    tipo = "Aporte",
                    categoria = "Saldo inicial",
                    responsable = "Santiago",
                    esComun = false,
                    descripcion = "Arrastre de mes anterior ($prevMonth)"
                )
                success = success && repository.saveMovement(_spreadsheetId.value, movS)
            }
            
            if (prevBalance.rocioSaldoFinal != 0.0) {
                val movR = Movement(
                    fecha = today,
                    monto = prevBalance.rocioSaldoFinal,
                    tipo = "Aporte",
                    categoria = "Saldo inicial",
                    responsable = "Rocío",
                    esComun = false,
                    descripcion = "Arrastre de mes anterior ($prevMonth)"
                )
                success = success && repository.saveMovement(_spreadsheetId.value, movR)
            }
            
            if (success) {
                doRefreshData()
            }
        }
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

        // Desglose de saldos por método de pago para el pozo
        var sEfectivo = 0.0
        var sVirtual = 0.0
        var rEfectivo = 0.0
        var rVirtual = 0.0

        for (m in list) {
            val isSantiago = m.responsable.equals("Santiago", ignoreCase = true)
            val isEfectivo = m.metodoPago.equals("Efectivo", ignoreCase = true)

            when (m.tipo.lowercase()) {
                "aporte" -> {
                    if (isSantiago) {
                        sAportes += m.monto
                        if (isEfectivo) sEfectivo += m.monto else sVirtual += m.monto
                    } else {
                        rAportes += m.monto
                        if (isEfectivo) rEfectivo += m.monto else rVirtual += m.monto
                    }
                    totalAportesMes += m.monto
                }
                "gasto" -> {
                    if (m.esComun) {
                        gastosComunesTotales += m.monto
                        // Los gastos comunes impactan 50% a cada uno en el método de pago usado
                        if (isSantiago) {
                            if (isEfectivo) sEfectivo -= m.monto else sVirtual -= m.monto
                            // Pero contablemente, cada uno paga la mitad.
                            // Si Santiago paga 100 en efectivo, su efectivo baja 100,
                            // pero Rocío le "debe" 50.
                        } else {
                            if (isEfectivo) rEfectivo -= m.monto else rVirtual -= m.monto
                        }
                    } else {
                        if (isSantiago) {
                            sGastosPersonales += m.monto
                            if (isEfectivo) sEfectivo -= m.monto else sVirtual -= m.monto
                        } else {
                            rGastosPersonales += m.monto
                            if (isEfectivo) rEfectivo -= m.monto else rVirtual -= m.monto
                        }
                    }
                    totalGastosMes += m.monto
                }
                "transferencia" -> {
                    if (isSantiago) {
                        sTransfersHechas += m.monto
                        if (isEfectivo) sEfectivo -= m.monto else sVirtual -= m.monto
                        // Y el otro recibe
                        if (isEfectivo) rEfectivo += m.monto else rVirtual += m.monto
                    } else {
                        rTransfersHechas += m.monto
                        if (isEfectivo) rEfectivo -= m.monto else rVirtual -= m.monto
                        // Y Santiago recibe
                        if (isEfectivo) sEfectivo += m.monto else sVirtual += m.monto
                    }
                }
            }
        }

        // Un gasto común se divide exactamente 50% entre los dos contablemente
        val sGastosComunes = gastosComunesTotales / 2.0
        val rGastosComunes = gastosComunesTotales / 2.0

        // El saldo final contable ya contempla las transferencias y gastos comunes.
        // Los saldos por método de pago (sEfectivo, sVirtual, etc) también deben 
        // ajustarse por la "deuda" generada por los gastos comunes pagados por el otro.
        
        // Si Santiago pagó un gasto común de 100, su sEfectivo/sVirtual bajó 100,
        // pero solo debería haber bajado 50. Rocío le debe 50.
        // Vamos a simplificar: los saldos por método muestran la DISPONIBILIDAD REAL
        // de dinero de cada uno en cada bolsa, considerando quién puso qué y quién pagó qué.

        // Balances Finales Contables
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
            totalGastosMes = totalGastosMes,
            santiagoEfectivo = sEfectivo,
            santiagoVirtual = sVirtual,
            rocioEfectivo = rEfectivo,
            rocioVirtual = rVirtual
        )
    }
}
