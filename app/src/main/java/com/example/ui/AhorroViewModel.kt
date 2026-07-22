package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AhorroRepository
import com.example.data.CuotaPlan
import com.example.data.DriveService
import com.example.data.Movement
import com.example.data.PreferencesHelper
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AhorroViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsHelper = PreferencesHelper(application)
    private val repository = AhorroRepository(prefsHelper)

    private var _cachedAllMovements: List<Movement> = emptyList()

    // Todos los movimientos (sin filtrar por mes). Lo usa el módulo de cuotas para derivar el
    // estado por cuota (una cuota se paga en cualquier mes, no solo el seleccionado).
    private val _allMovements = MutableStateFlow<List<Movement>>(emptyList())
    val allMovements: StateFlow<List<Movement>> = _allMovements.asStateFlow()

    // Planes de cuotas. `_plans` = pendientes (se cargan en refreshData); `_paidPlans` = completos
    // (on-demand, cuando el usuario activa el switch "Mostrar pagados").
    private val _plans = MutableStateFlow<List<CuotaPlan>>(emptyList())
    val plans: StateFlow<List<CuotaPlan>> = _plans.asStateFlow()

    private val _paidPlans = MutableStateFlow<List<CuotaPlan>>(emptyList())
    val paidPlans: StateFlow<List<CuotaPlan>> = _paidPlans.asStateFlow()

    private val _showPaidPlans = MutableStateFlow(false)
    val showPaidPlans: StateFlow<Boolean> = _showPaidPlans.asStateFlow()

    private val _isLoadingPaid = MutableStateFlow(false)
    val isLoadingPaidPlans: StateFlow<Boolean> = _isLoadingPaid.asStateFlow()

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
    private val _spreadsheetId = MutableStateFlow(prefsHelper.scriptUrl)
    val spreadsheetId: StateFlow<String> = _spreadsheetId.asStateFlow()

    private val _folderId = MutableStateFlow(prefsHelper.folderId)
    val folderId: StateFlow<String> = _folderId.asStateFlow()

    private val _useLocalDemo = MutableStateFlow(prefsHelper.useLocalDemo)
    val useLocalDemo: StateFlow<Boolean> = _useLocalDemo.asStateFlow()

    private val _currentUserProfile = MutableStateFlow(prefsHelper.currentUserProfile)
    val currentUserProfile: StateFlow<String> = _currentUserProfile.asStateFlow()

    private val _isDarkMode = MutableStateFlow(prefsHelper.isDarkMode)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    // Estado calculado de balances
    private val _balance = MutableStateFlow(AccountingEngine.compute(emptyList()))
    val balance: StateFlow<BalanceBreakdown> = _balance.asStateFlow()

    init {
        loadConfigAndData()
    }

    fun loadConfigAndData() {
        _spreadsheetId.value = prefsHelper.scriptUrl
        _folderId.value = prefsHelper.folderId
        _useLocalDemo.value = prefsHelper.useLocalDemo
        _currentUserProfile.value = prefsHelper.currentUserProfile

        // Punto 2: mostrar el cache al instante y refrescar en segundo plano.
        viewModelScope.launch {
            if (_cachedAllMovements.isEmpty()) {
                val cached = withContext(Dispatchers.IO) { prefsHelper.getSheetsCache() }
                if (cached.isNotEmpty()) applyMovements(cached)
            }
            // Módulo de cuotas: mismo patrón. Mostramos los planes pendientes cacheados al instante
            // (los pagados siguen siendo on-demand) para que la pestaña Cuotas no arranque vacía.
            if (_plans.value.isEmpty()) {
                val cachedPlans = withContext(Dispatchers.IO) { repository.cachedPendingPlans() }
                if (cachedPlans.isNotEmpty()) _plans.value = cachedPlans
            }
            refreshData()
        }
    }

    private fun monthOf(m: Movement): String =
        if (m.fecha.length >= 7) m.fecha.substring(0, 7) else ""

    /**
     * Los aportes automáticos de arrastre ("Saldo inicial") de versiones anteriores se ignoran:
     * el arrastre ahora se calcula plegando los meses previos (ver [updateFilteredData]), así que
     * conservarlos duplicaría el saldo. Filtrarlos también repara los datos históricos existentes.
     */
    private fun isLegacyCarryover(m: Movement): Boolean =
        m.categoria.equals("Saldo inicial", ignoreCase = true)

    fun updateFilteredData() {
        val currentSel = _selectedMonth.value
        val actualCurrent = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date())
        _isCurrentMonth.value = (currentSel == actualCurrent)

        // Movimientos reales (sin las filas legacy de arrastre automático)
        val relevant = _cachedAllMovements.filter { !isLegacyCarryover(it) && monthOf(it).isNotEmpty() }

        // Arrastre: estado final de todos los meses anteriores al seleccionado.
        val prior = relevant.filter { monthOf(it) < currentSel }
        val opening = AccountingEngine.opening(prior)

        val filtered = relevant.filter { monthOf(it) == currentSel }
        _movements.value = filtered
        _balance.value = AccountingEngine.compute(filtered, opening)
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
            applyMovements(rawList)
            // Planes pendientes junto con los movimientos (los pagos son on-demand).
            _plans.value = repository.fetchPlans(_spreadsheetId.value, soloPagos = false)
            if (_showPaidPlans.value) {
                _isLoadingPaid.value = true
                _paidPlans.value = repository.fetchPlans(_spreadsheetId.value, soloPagos = true)
                _isLoadingPaid.value = false
            }
            true
        } catch (e: Exception) {
            _errorMessage.value = "Error al recargar datos: ${e.localizedMessage}"
            Log.e("AhorroViewModel", "Error cargando datos", e)
            false
        }
    }

    /**
     * Normaliza, ordena y publica una nueva lista de movimientos, recalculando meses disponibles
     * y balances. Se usa tanto para el cache instantáneo como para la carga de red.
     */
    private fun applyMovements(rawList: List<Movement>) {
        val list = rawList.map { it.copy(fecha = normalizeFechaToString(it.fecha)) }
        val sortedList = list.sortedWith(compareByDescending<Movement> { it.fecha }.thenByDescending { it.id })
        _cachedAllMovements = sortedList
        _allMovements.value = sortedList

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
        propietario: String,
        descripcion: String,
        metodoPago: String,
        ticketUri: android.net.Uri? = null,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            var imageInfo: com.example.data.ImageInfo? = null
            
            if (ticketUri != null) {
                try {
                    Log.d("AhorroViewModel", "Comprimiendo imagen de URI: $ticketUri")
                    val inputStream = getApplication<Application>().contentResolver.openInputStream(ticketUri)
                    val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                    
                    if (bitmap != null) {
                        // Corregir orientación basada en EXIF
                        val rotatedBitmap = try {
                            val exifInputStream = getApplication<Application>().contentResolver.openInputStream(ticketUri)
                            if (exifInputStream != null) {
                                val exif = ExifInterface(exifInputStream)
                                val orientation = exif.getAttributeInt(
                                    ExifInterface.TAG_ORIENTATION,
                                    ExifInterface.ORIENTATION_NORMAL
                                )
                                rotateBitmapIfRequired(bitmap, orientation)
                            } else bitmap
                        } catch (e: Exception) {
                            Log.e("AhorroViewModel", "Error leyendo EXIF", e)
                            bitmap
                        }

                        val outputStream = java.io.ByteArrayOutputStream()
                        // Comprimir a JPEG con 70% de calidad
                        rotatedBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, outputStream)
                        val bytes = outputStream.toByteArray()
                        
                        Log.d("AhorroViewModel", "Imagen comprimida. Tamaño final: ${bytes.size} bytes")
                        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        imageInfo = com.example.data.ImageInfo(base64 = base64)
                    }
                } catch (e: Exception) {
                    Log.e("AhorroViewModel", "Error comprimiendo imagen", e)
                }
            }

            val newMovement = Movement(
                fecha = fecha,
                monto = monto,
                tipo = tipo,
                categoria = categoria,
                responsable = responsable,
                esComun = if (tipo == "Gasto") esComun else false,
                propietario = propietario,
                descripcion = descripcion,
                metodoPago = metodoPago,
                ticketUrl = "" // El script lo llenará si hay imagen
            )

            try {
                val success = repository.saveMovement(_spreadsheetId.value, newMovement, imageInfo, _folderId.value)
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
     * Elimina un movimiento (baja lógica)
     */
    fun deleteMovement(movement: Movement) {
        viewModelScope.launch {
            _isLoading.value = true
            val success = repository.deleteMovement(_spreadsheetId.value, movement)
            if (success) {
                doRefreshData()
            } else {
                _errorMessage.value = "Error al eliminar el movimiento"
            }
            _isLoading.value = false
        }
    }

    /**
     * Duplica un movimiento
     */
    fun duplicateMovement(movement: Movement, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            val now = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())
            val duplicate = movement.copy(
                id = java.util.UUID.randomUUID().toString(),
                fecha = now
            )
            
            val success = repository.saveMovement(_spreadsheetId.value, duplicate, null, _folderId.value)
            if (success) {
                doRefreshData()
                onSuccess()
            } else {
                _errorMessage.value = "Error al duplicar el movimiento"
            }
            _isLoading.value = false
        }
    }

    /**
     * Guarda la configuración
     */
    fun saveSheetsConfig(newSheetIdOrUrl: String, newFolderId: String, useDemo: Boolean) {
        prefsHelper.scriptUrl = newSheetIdOrUrl
        prefsHelper.folderId = newFolderId
        prefsHelper.useLocalDemo = useDemo
        
        _spreadsheetId.value = newSheetIdOrUrl
        _folderId.value = newFolderId
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

    // ---------------------------------------------------------------------------------------------
    // Módulo de cuotas
    // ---------------------------------------------------------------------------------------------

    /** Activa/desactiva la sección de planes pagados; al activarla, dispara la request on-demand. */
    fun setShowPaidPlans(show: Boolean) {
        _showPaidPlans.value = show
        if (show) loadPaidPlans() else _paidPlans.value = emptyList()
    }

    fun loadPaidPlans() {
        viewModelScope.launch {
            _isLoadingPaid.value = true
            _paidPlans.value = repository.fetchPlans(_spreadsheetId.value, soloPagos = true)
            _isLoadingPaid.value = false
        }
    }

    /** Alta de un plan de cuotas (siempre personal: propietario ∈ {Santiago, Rocío}). */
    fun addPlan(
        descripcion: String,
        montoPorCuota: Double,
        cantidadCuotas: Int,
        fechaPrimeraCuota: String,
        propietario: String,
        categoria: String,
        tarjeta: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val now = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())
            val plan = CuotaPlan(
                fechaCreacion = now,
                descripcion = descripcion,
                montoPorCuota = montoPorCuota,
                cantidadCuotas = cantidadCuotas,
                fechaPrimeraCuota = fechaPrimeraCuota,
                propietario = propietario,
                categoria = categoria,
                tarjeta = tarjeta
            )
            val success = repository.savePlan(_spreadsheetId.value, plan)
            if (success) {
                doRefreshData()
                onSuccess()
            } else {
                _errorMessage.value = "No se pudo crear el plan de cuotas."
            }
            _isLoading.value = false
        }
    }

    /** Edición de un plan existente (conserva el id). */
    fun updatePlan(plan: CuotaPlan, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val success = repository.updatePlan(_spreadsheetId.value, plan)
            if (success) {
                doRefreshData()
                onSuccess()
            } else {
                _errorMessage.value = "No se pudo actualizar el plan."
            }
            _isLoading.value = false
        }
    }

    /** Baja lógica de un plan. */
    fun deletePlan(plan: CuotaPlan, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _isLoading.value = true
            val success = repository.deletePlan(_spreadsheetId.value, plan)
            if (success) {
                doRefreshData()
                onSuccess()
            } else {
                _errorMessage.value = "No se pudo eliminar el plan."
            }
            _isLoading.value = false
        }
    }

    /**
     * Paga en lote las cuotas indicadas (una por plan) — usado por el "Pagar la tarjeta" del mes.
     * Crea un Movement de gasto personal por cada cuota (fecha = hoy, mes actual) y refresca **una
     * sola vez** al terminar. Cada par es (plan, número de cuota).
     */
    fun pagarCuotas(
        cuotas: List<Pair<CuotaPlan, Int>>,
        metodoPago: String,
        onSuccess: () -> Unit = {}
    ) {
        if (cuotas.isEmpty()) return
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val fecha = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())
            var todoOk = true
            for ((plan, numero) in cuotas) {
                val movimiento = Movement(
                    fecha = fecha,
                    monto = plan.montoPorCuota,
                    tipo = "Gasto",
                    categoria = plan.categoria,
                    responsable = plan.propietario,
                    esComun = false,
                    propietario = plan.propietario,
                    descripcion = "Cuota $numero/${plan.cantidadCuotas} — ${plan.descripcion}",
                    metodoPago = metodoPago,
                    planId = plan.id,
                    cuotaNumero = numero
                )
                val ok = repository.saveMovement(_spreadsheetId.value, movimiento, null, _folderId.value)
                if (!ok) todoOk = false
            }
            doRefreshData()
            if (todoOk) {
                onSuccess()
            } else {
                _errorMessage.value = "Algunas cuotas no se pudieron pagar. Revisá el resumen."
            }
            _isLoading.value = false
        }
    }

    /**
     * Confirma el pago de una cuota: crea un Movement de tipo "Gasto" (personal, sin propiedad
     * cruzada) que la referencia. El AccountingEngine lo debita como cualquier gasto y, al
     * refrescar, la cuota queda "pagada" de forma derivada. [monto] permite ajustar el importe
     * (p. ej. redondeo en la última cuota); si es null se usa el monto por cuota del plan.
     */
    fun confirmarCuota(
        plan: CuotaPlan,
        numero: Int,
        fecha: String,
        metodoPago: String,
        monto: Double? = null,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val movimiento = Movement(
                fecha = fecha,
                monto = monto ?: plan.montoPorCuota,
                tipo = "Gasto",
                categoria = plan.categoria,
                responsable = plan.propietario,   // el dueño paga desde su propia cuenta
                esComun = false,
                propietario = plan.propietario,   // responsable == propietario => sin propiedad cruzada
                descripcion = "Cuota $numero/${plan.cantidadCuotas} — ${plan.descripcion}",
                metodoPago = metodoPago,
                planId = plan.id,
                cuotaNumero = numero
            )
            val success = repository.saveMovement(_spreadsheetId.value, movimiento, null, _folderId.value)
            if (success) {
                // Refresca movimientos y planes: un pago puede completar el plan (pasa a "pagados").
                doRefreshData()
                onSuccess()
            } else {
                _errorMessage.value = "No se pudo confirmar el pago de la cuota."
            }
            _isLoading.value = false
        }
    }

    /**
     * Rota un bitmap según la orientación EXIF.
     */
    private fun rotateBitmapIfRequired(img: android.graphics.Bitmap, orientation: Int): android.graphics.Bitmap {
        val matrix = android.graphics.Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return img
        }
        return android.graphics.Bitmap.createBitmap(img, 0, 0, img.width, img.height, matrix, true)
    }

}
