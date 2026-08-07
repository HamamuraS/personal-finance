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
import com.example.data.Usuario
import com.example.data.UsuariosConfig
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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

    // Conexión: fija en el APK (ver [com.example.data.AppConfig]). Ya no se edita desde Ajustes.
    private val _spreadsheetId = MutableStateFlow(prefsHelper.scriptUrl)

    private val _folderId = MutableStateFlow(prefsHelper.folderId)

    private val _useLocalDemo = MutableStateFlow(prefsHelper.useLocalDemo)
    val useLocalDemo: StateFlow<Boolean> = _useLocalDemo.asStateFlow()

    private val _currentUserProfile = MutableStateFlow(prefsHelper.currentUserProfile)
    val currentUserProfile: StateFlow<String> = _currentUserProfile.asStateFlow()

    // --- Usuarios parametrizables ---
    // Config de los dos usuarios (nombre + color). Arranca en DEFAULT y se hidrata desde cache al
    // instante + red en segundo plano (mismo patrón que movimientos/planes).
    private val _usuarios = MutableStateFlow(UsuariosConfig.DEFAULT)
    val usuarios: StateFlow<UsuariosConfig> = _usuarios.asStateFlow()

    // ¿Ya eligió identidad en este dispositivo? Si no, MainActivity muestra el picker "¿Quién sos?".
    private val _hasChosenIdentity = MutableStateFlow(prefsHelper.hasChosenIdentity)
    val hasChosenIdentity: StateFlow<Boolean> = _hasChosenIdentity.asStateFlow()

    // Usuario activo / el otro, derivados de la identidad + la config. Alimentan el tema dinámico.
    val activeUser: StateFlow<Usuario> = combine(_usuarios, _currentUserProfile) { config, key ->
        config.byKey(key) ?: config.primario
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UsuariosConfig.DEFAULT.primario)

    val otherUser: StateFlow<Usuario> = combine(_usuarios, _currentUserProfile) { config, key ->
        config.elOtro(key)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UsuariosConfig.DEFAULT.secundario)

    private val _isDarkMode = MutableStateFlow(prefsHelper.isDarkMode)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    // --- Borradores de formularios (sobreviven cambios de pestaña mientras la app está abierta) ---
    private val _movementDraft = MutableStateFlow(MovementDraft())
    val movementDraft: StateFlow<MovementDraft> = _movementDraft.asStateFlow()

    private val _cuotaDraft = MutableStateFlow(CuotaDraft())
    val cuotaDraft: StateFlow<CuotaDraft> = _cuotaDraft.asStateFlow()

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
        _hasChosenIdentity.value = prefsHelper.hasChosenIdentity

        // Punto 2: mostrar el cache al instante y refrescar en segundo plano.
        viewModelScope.launch {
            // Usuarios: cache al instante para que el tema/identidad arranquen con el color correcto.
            _usuarios.value = withContext(Dispatchers.IO) { repository.cachedUsuarios() }
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
     * ¿El mes seleccionado tiene su saldo inicial materializado? Si es `false`, el arrastre se está
     * derivando replayando meses anteriores y esas hojas todavía no se pueden purgar.
     */
    private val _tieneSaldoInicial = MutableStateFlow(false)
    val tieneSaldoInicial: StateFlow<Boolean> = _tieneSaldoInicial.asStateFlow()

    fun updateFilteredData() {
        val currentSel = _selectedMonth.value
        val actualCurrent = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date())
        _isCurrentMonth.value = (currentSel == actualCurrent)

        // Movimientos reales (sin las filas legacy de arrastre automático de versiones <= 7.1)
        val relevant = _cachedAllMovements.filter {
            !AccountingEngine.isLegacyCarryover(it) && monthOf(it).isNotEmpty()
        }

        // Arrastre: filas de apertura del mes si existen; si no, replay desde la apertura anterior.
        val opening = AccountingEngine.openingFor(relevant, currentSel)

        val delMes = relevant.filter { monthOf(it) == currentSel }
        _tieneSaldoInicial.value = delMes.any { AccountingEngine.isOpeningRow(it) }

        // La apertura es stock: no se lista como movimiento ni suma a los flujos del periodo.
        val filtered = delMes.filter { !AccountingEngine.isOpeningRow(it) }
        _movements.value = filtered
        _balance.value = AccountingEngine.compute(filtered, opening)
    }

    /**
     * Materializa el saldo inicial del mes seleccionado: calcula el arrastre replayando los meses
     * anteriores y escribe las filas [AccountingEngine.TIPO_APERTURA] en la hoja del mes.
     *
     * Es idempotente (los ids son determinísticos, así que se pisan las filas anteriores) y se
     * puede volver a correr cuando se corrige un movimiento viejo. Una vez que un mes tiene su
     * apertura, los meses anteriores dejan de intervenir en su cálculo y se pueden purgar.
     */
    fun recalcularSaldoInicial(onResult: (Boolean, String) -> Unit) {
        val mes = _selectedMonth.value
        if (mes.isEmpty()) {
            onResult(false, "No hay un mes seleccionado.")
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            // El arrastre se deriva SIEMPRE de los meses anteriores, ignorando la apertura que el
            // mes pueda tener ya escrita (si no, recalcular sería un no-op).
            val relevant = _cachedAllMovements.filter {
                !AccountingEngine.isLegacyCarryover(it) && monthOf(it).isNotEmpty() && monthOf(it) != mes
            }
            val opening = AccountingEngine.openingFor(relevant, mes)
            val filas = AccountingEngine.openingRowsFor(mes, opening)

            val ok = filas.all { repository.saveMovement(_spreadsheetId.value, it, action = "PUT") }
            if (ok) {
                doRefreshData()
                onResult(true, "Saldo inicial de $mes actualizado (${filas.size} filas).")
            } else {
                _errorMessage.value = "No se pudo escribir el saldo inicial de $mes."
                onResult(false, "No se pudo escribir el saldo inicial de $mes.")
            }
            _isLoading.value = false
        }
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
            // Usuarios (nombre/color) en segundo plano; barato y mantiene el tema al día.
            _usuarios.value = repository.fetchUsuarios(_spreadsheetId.value)
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
     * Duplica un movimiento **a nombre del usuario activo**, como gasto personal. Se usa en el botón
     * de duplicar de los gastos de transporte: si dos personas viajan juntas, una lo carga y la otra
     * toca duplicar para registrar fácilmente su propio gasto (no el de la otra persona).
     */
    fun duplicateMovement(movement: Movement, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            val now = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())
            val yo = _currentUserProfile.value
            val duplicate = movement.copy(
                id = java.util.UUID.randomUUID().toString(),
                fecha = now,
                responsable = yo,      // pasa a mi cuenta
                propietario = yo,      // gasto personal mío (sin propiedad cruzada)
                esComun = false
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
     * Alterna entre el Modo Local (Demo) y la sincronización con la planilla. La URL del Web App y
     * la carpeta de Drive vienen fijas en el APK, así que no hay nada más que configurar.
     */
    fun setUseLocalDemo(useDemo: Boolean) {
        prefsHelper.useLocalDemo = useDemo
        _useLocalDemo.value = useDemo
        refreshData()
    }

    /**
     * Elige la identidad (slot) en este dispositivo. Se recuerda: no se vuelve a preguntar hasta
     * cerrar sesión. Es lo que dispara el picker "¿Quién sos?" del primer arranque.
     */
    fun setIdentity(slotKey: String) {
        prefsHelper.currentUserProfile = slotKey
        prefsHelper.hasChosenIdentity = true
        _currentUserProfile.value = slotKey
        _hasChosenIdentity.value = true
    }

    /** Cierra sesión: olvida la identidad → vuelve a la pantalla "¿Quién sos?" (no borra datos). */
    fun logout() {
        prefsHelper.hasChosenIdentity = false
        _hasChosenIdentity.value = false
    }

    /** Edita MI nombre visible (el del slot activo). Aplica al instante y persiste (red/local). */
    fun updateMiNombre(nombre: String) = updateMiPerfil { it.copy(nombre = nombre.trim()) }

    /** Elige MI color (preset). Aplica al instante al tema y persiste (red/local). */
    fun updateMiColor(colorId: String) = updateMiPerfil { it.copy(colorId = colorId) }

    /**
     * Aplica una edición a MI usuario (el del slot activo) de forma optimista (se ve al instante) y
     * la persiste en segundo plano. El `slotKey`/`orden` no se tocan (identidad interna estable).
     */
    private fun updateMiPerfil(transform: (Usuario) -> Usuario) {
        val config = _usuarios.value
        val yo = config.byKey(_currentUserProfile.value) ?: return
        val actualizado = transform(yo)
        if (actualizado == yo) return
        // Actualización optimista del flow (el tema/identidad reaccionan al instante).
        _usuarios.value =
            if (config.primario.slotKey.equals(yo.slotKey, ignoreCase = true)) config.copy(primario = actualizado)
            else config.copy(secundario = actualizado)
        viewModelScope.launch {
            val ok = repository.updateUsuario(_spreadsheetId.value, actualizado)
            if (!ok) _errorMessage.value = "No se pudo guardar tu perfil. Se aplicó localmente."
        }
    }

    fun setIsDarkMode(isDark: Boolean) {
        prefsHelper.isDarkMode = isDark
        _isDarkMode.value = isDark
    }

    // --- Borradores ---
    fun setMovementDraft(draft: MovementDraft) { _movementDraft.value = draft }
    fun clearMovementDraft() { _movementDraft.value = MovementDraft() }
    fun setCuotaDraft(draft: CuotaDraft) { _cuotaDraft.value = draft }
    fun clearCuotaDraft() { _cuotaDraft.value = CuotaDraft() }

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
