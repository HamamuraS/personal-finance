package com.example.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.example.data.AhorroRepository
import com.example.data.CuotaPlan
import com.example.data.Movement
import com.example.data.PendingMovement
import com.example.data.PreferencesHelper
import com.example.data.TipoMovimiento
import com.example.data.Tickets
import com.example.data.Usuario
import com.example.data.UsuariosConfig
import com.example.data.mes
import com.example.data.upload.MovementUploadScheduler
import com.example.domain.AccountingEngine
import com.example.domain.BalanceBreakdown
import com.example.domain.CuotasEngine
import com.example.domain.OpeningBalance
import com.example.util.Fechas
import com.example.util.Formato
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
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

    // Altas encoladas que todavía no confirmó la planilla. Alimentan la fila optimista de Inicio y
    // sobreviven al cierre de la app (se persisten; ver [PendingMovement]).
    private val _pendingMovements = MutableStateFlow<List<PendingMovement>>(emptyList())

    /**
     * Movimientos sin confirmar, indexados por id con su cantidad de intentos fallidos. La UI los
     * marca distinto según el caso: 0 = subiendo, > 0 = falló y se está reintentando. Sin esto, un
     * alta que la planilla rechaza queda con el relojito para siempre y sin ningún aviso (la
     * notificación de error se descarta en silencio si no hay permiso de notificaciones).
     */
    val pendingStates: StateFlow<Map<String, Int>> = _pendingMovements
        .map { list -> list.associate { it.movement.id to it.intentos } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _availableMonths = MutableStateFlow<List<String>>(emptyList())
    val availableMonths: StateFlow<List<String>> = _availableMonths.asStateFlow()

    private val _selectedMonth = MutableStateFlow("")
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

    private val _useLocalDemo = MutableStateFlow(prefsHelper.useLocalDemo)
    val useLocalDemo: StateFlow<Boolean> = _useLocalDemo.asStateFlow()

    private val _currentUserProfile = MutableStateFlow(prefsHelper.currentUserProfile)
    val currentUserProfile: StateFlow<String> = _currentUserProfile.asStateFlow()

    // --- Usuarios parametrizables ---
    // Config de los usuarios (nombre + color). Arranca en DEFAULT y se hidrata desde cache al
    // instante + red en segundo plano (mismo patrón que movimientos/planes).
    private val _usuarios = MutableStateFlow(UsuariosConfig.DEFAULT)
    val usuarios: StateFlow<UsuariosConfig> = _usuarios.asStateFlow()

    // ¿Ya eligió identidad en este dispositivo? Si no, MainActivity muestra el picker "¿Quién sos?".
    private val _hasChosenIdentity = MutableStateFlow(prefsHelper.hasChosenIdentity)
    val hasChosenIdentity: StateFlow<Boolean> = _hasChosenIdentity.asStateFlow()

    private val _isDarkMode = MutableStateFlow(prefsHelper.isDarkMode)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    // --- Borradores de formularios (sobreviven cambios de pestaña mientras la app está abierta) ---
    private val _movementDraft = MutableStateFlow(MovementDraft())
    val movementDraft: StateFlow<MovementDraft> = _movementDraft.asStateFlow()

    private val _cuotaDraft = MutableStateFlow(CuotaDraft())
    val cuotaDraft: StateFlow<CuotaDraft> = _cuotaDraft.asStateFlow()

    // Filtros del historial de Inicio. Acá arriba (y no en el composable) para que sobrevivan al
    // cambio de pestaña y para que Métricas pueda aplicarlos al saltar a Inicio.
    private val _dashboardFilters = MutableStateFlow(DashboardFilters())
    val dashboardFilters: StateFlow<DashboardFilters> = _dashboardFilters.asStateFlow()

    // Señal de un solo uso para que Inicio abra a la altura de los filtros al venir de Métricas.
    // La consume la pantalla (ver [consumirScrollAFiltros]): si fuera un estado permanente, entrar
    // a Inicio a mano también saltaría el encabezado.
    private val _scrollAFiltros = MutableStateFlow(false)
    val scrollAFiltros: StateFlow<Boolean> = _scrollAFiltros.asStateFlow()

    // Estado calculado de balances
    private val _balance = MutableStateFlow(AccountingEngine.compute(emptyList()))
    val balance: StateFlow<BalanceBreakdown> = _balance.asStateFlow()

    /** Arrastre del mes seleccionado tal como lo usó el último [updateFilteredData]. */
    private var _aperturaDelMes = OpeningBalance()

    /**
     * ¿El mes seleccionado tiene su saldo inicial materializado? Si es `false`, el arrastre se está
     * derivando replayando meses anteriores y esas hojas todavía no se pueden purgar.
     */
    private val _tieneSaldoInicial = MutableStateFlow(false)
    val tieneSaldoInicial: StateFlow<Boolean> = _tieneSaldoInicial.asStateFlow()

    init {
        observarSubidas()
        loadConfigAndData()
    }

    /**
     * Sigue el estado del worker de subida para reflejar en la UI cuándo un pendiente se confirmó.
     * Cuando la cola termina, el worker ya releyó la planilla, así que alcanza con tomar el cache
     * en vez de disparar otro fetch.
     */
    private fun observarSubidas() {
        val workManager = WorkManager.getInstance(getApplication())
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(MovementUploadScheduler.WORK_NAME)
                .collect { infos ->
                    refrescarPendientes()
                    if (infos.isNotEmpty() && infos.all { it.state.isFinished }) {
                        val datos = withContext(Dispatchers.IO) {
                            if (prefsHelper.useLocalDemo) {
                                prefsHelper.getLocalMovements().filter { !it.eliminado }
                            } else {
                                prefsHelper.getSheetsCache()
                            }
                        }
                        if (datos.isNotEmpty()) applyMovements(datos)
                    }
                }
        }
    }

    /** Relee la cola persistida y recalcula la vista (la fila optimista entra por acá). */
    private fun refrescarPendientes() {
        _pendingMovements.value = prefsHelper.getPendingMovements()
        updateFilteredData()
    }

    fun loadConfigAndData() {
        _useLocalDemo.value = prefsHelper.useLocalDemo
        _currentUserProfile.value = prefsHelper.currentUserProfile
        _hasChosenIdentity.value = prefsHelper.hasChosenIdentity

        // Mostrar el cache al instante y refrescar en segundo plano.
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
            // Una compresión de ticket en curso no sobrevive al proceso: los pendientes que
            // quedaron esperándola se destraban y se suben (sin foto) en vez de quedar atascados.
            withContext(Dispatchers.IO) { prefsHelper.liberarPendientesEsperandoTicket() }
            refrescarPendientes()

            // Altas que quedaron sin subir de una sesión anterior: se reintentan al arrancar.
            if (_pendingMovements.value.isNotEmpty()) {
                MovementUploadScheduler.enqueue(getApplication(), requiereRed = !_useLocalDemo.value)
            }

            // Fetch automático con ventana de frescura: si el último fetch exitoso fue hace menos
            // de 6 h, se arranca contra el cache y no se toca la red. Antes se refrescaba en cada
            // arranque y eso era justo lo que trababa la carga de los movimientos. El refresh
            // manual, el alta de un movimiento y el sync de segundo plano siguen actualizando
            // igual — y renuevan la ventana.
            if (prefsHelper.necesitaFetchAutomatico()) refreshData()
        }
    }

    fun updateFilteredData() {
        val currentSel = _selectedMonth.value
        _isCurrentMonth.value = (currentSel == Fechas.mesActual())

        // Altas y ediciones encoladas que la planilla todavía no confirmó: se muestran y se computan
        // como cualquier otro movimiento (fila optimista). Con el mismo id, el pendiente **pisa** al
        // cacheado: en una edición el id siempre está en el cache, y descartar el pendiente (como se
        // hacía cuando solo había altas) escondía la edición hasta el próximo refresh. Tampoco se
        // cuenta dos veces en la ventana entre que el server confirma y la cola se vacía.
        val pendientes = _pendingMovements.value.map { it.movement }
        val idsPendientes = pendientes.map { it.id }.toSet()

        // Movimientos reales (sin las filas legacy de arrastre automático de versiones <= 7.1).
        // Se reordena después de mezclar: `_cachedAllMovements` ya viene ordenado, pero concatenar
        // los pendientes al final los mandaba al pie del historial — justo la fila que se quiere
        // mostrar recién registrada quedaba fuera de pantalla.
        val relevant = (_cachedAllMovements.filter { it.id !in idsPendientes } + pendientes)
            .filter { !AccountingEngine.isLegacyCarryover(it) && it.mes.isNotEmpty() }
            .sortedWith(compareByDescending<Movement> { it.fecha }.thenByDescending { it.id })

        // Arrastre: filas de apertura del mes si existen; si no, replay desde la apertura anterior.
        val opening = AccountingEngine.openingFor(relevant, currentSel)

        val delMes = relevant.filter { it.mes == currentSel }
        _tieneSaldoInicial.value = delMes.any { AccountingEngine.isOpeningRow(it) }

        // La apertura es stock: no se lista como movimiento ni suma a los flujos del periodo.
        val filtered = delMes.filter { !AccountingEngine.isOpeningRow(it) }
        _aperturaDelMes = opening
        _movements.value = filtered
        _balance.value = AccountingEngine.compute(filtered, opening)
    }

    /**
     * Balance del mes seleccionado **sin** el movimiento [movementId]. Lo usa la edición: el tope del
     * "Saldo externo" se calcula con la deuda que había antes de ese movimiento; con el balance a
     * secas, editar un perdón que canceló toda la deuda daba deuda cero y no dejaba guardar.
     */
    fun balanceSin(movementId: String): BalanceBreakdown =
        AccountingEngine.compute(_movements.value.filter { it.id != movementId }, _aperturaDelMes)

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

            val todos = _cachedAllMovements.filter {
                !AccountingEngine.isLegacyCarryover(it) && it.mes.isNotEmpty()
            }

            // Con las hojas viejas purgadas, "derivar de los meses anteriores" da cero y el botón
            // pisaría una apertura correcta con ceros. Ver [AccountingEngine.chequearRecalculo].
            val chequeo = AccountingEngine.chequearRecalculo(todos, mes)
            if (!chequeo.permitido) {
                _isLoading.value = false
                _errorMessage.value = chequeo.motivo
                onResult(false, chequeo.motivo)
                return@launch
            }

            // El arrastre se deriva SIEMPRE de los meses anteriores, ignorando la apertura que el
            // mes pueda tener ya escrita (si no, recalcular sería un no-op).
            val relevant = todos.filter { it.mes != mes }
            val opening = AccountingEngine.openingFor(relevant, mes)
            val filas = AccountingEngine.openingRowsFor(mes, opening)

            val ok = filas.all { repository.saveMovement(it) }
            if (ok) {
                // Materializar el corte cubre la plata Y las cuotas: sin el snapshot, purgar las
                // hojas anteriores devolvería a "pendiente" cuotas ya pagadas (ver CuotaPlan).
                val cuotasOk = repository.snapshotCuotasPrevias(mes)
                doRefreshData()
                val aviso = if (cuotasOk) "" else " (no se pudo guardar el snapshot de cuotas)"
                onResult(true, "Saldo inicial de $mes actualizado (${filas.size} filas).$aviso")
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

    private suspend fun doRefreshData(): Boolean {
        return try {
            // Usuarios (nombre/color) en segundo plano; barato y mantiene el tema al día.
            _usuarios.value = repository.fetchUsuarios()
            val rawList = repository.fetchMovements()
            applyMovements(rawList)
            // Planes pendientes junto con los movimientos (los pagos son on-demand).
            _plans.value = repository.fetchPlans(soloPagos = false)
            if (_showPaidPlans.value) {
                _isLoadingPaid.value = true
                _paidPlans.value = repository.fetchPlans(soloPagos = true)
                _isLoadingPaid.value = false
            }
            true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _errorMessage.value = "Error al recargar datos: ${e.localizedMessage}"
            Log.e(TAG, "Error cargando datos", e)
            false
        }
    }

    /**
     * Normaliza, ordena y publica una nueva lista de movimientos, recalculando meses disponibles
     * y balances. Se usa tanto para el cache instantáneo como para la carga de red.
     */
    private fun applyMovements(rawList: List<Movement>) {
        val list = rawList.mapIndexed { index, m ->
            // Una fila con la columna ID vacía llega con id "" y todas colisionarían entre sí:
            // se les da una clave sintética en vez de dejar que se pisen.
            m.copy(
                id = m.id.ifBlank { "sin-id-$index" },
                fecha = Fechas.normalizarFechaDeMovimiento(m.fecha)
            )
        }
        // `distinctBy` es una red de seguridad, no la solución: el id es la key del listado de
        // Inicio y dos filas con el mismo id hacían crashear la app entera al abrirla. La causa
        // (reintentos que apendeaban de nuevo) se arregló subiendo con PUT en la cola, pero una
        // planilla que ya arrastra duplicados —o una fila copiada a mano— no puede voltear la app.
        val sortedList = list
            .sortedWith(compareByDescending<Movement> { it.fecha }.thenByDescending { it.id })
            .distinctBy { it.id }
        if (sortedList.size != list.size) {
            Log.w(TAG, "Se ignoraron ${list.size - sortedList.size} filas con id duplicado")
        }
        _cachedAllMovements = sortedList
        _allMovements.value = sortedList

        val currentRealMonth = Fechas.mesActual()
        val monthsSet = sortedList.mapNotNull { it.mes.ifEmpty { null } }.toSortedSet(reverseOrder())
        monthsSet.add(currentRealMonth)

        _availableMonths.value = monthsSet.toList()
        if (_selectedMonth.value.isEmpty()) {
            _selectedMonth.value = currentRealMonth
        }

        updateFilteredData()
    }

    /**
     * Encola un movimiento nuevo y **devuelve el control al instante**.
     *
     * Antes esto era una escritura bloqueante: la pantalla quedaba trabada con el spinner en el
     * botón hasta que el Apps Script respondía. Ahora el alta se persiste en la cola de pendientes
     * (con lo cual Inicio ya la muestra como fila optimista) y la escritura la hace
     * [com.example.data.upload.MovementUploadWorker] en segundo plano, sobreviviendo a que el
     * usuario cierre o minimice la app. Al terminar, el worker notifica.
     *
     * [onEncolado] corre de forma sincrónica: es lo que dispara la vuelta a Inicio.
     */
    fun encolarMovimiento(
        fecha: String,
        monto: Double,
        tipo: String,
        categoria: String,
        responsable: String,
        esComun: Boolean,
        propietario: String,
        descripcion: String,
        metodoPago: String,
        ticketUri: Uri? = null,
        evitable: Boolean = false,
        /** Borrador de origen: si es una edición, se conservan id, vínculo con la cuota y ticket. */
        edicion: MovementDraft? = null,
        onEncolado: () -> Unit
    ) {
        val esGasto = tipo == TipoMovimiento.GASTO
        val base = Movement(
            fecha = fecha,
            monto = monto,
            tipo = tipo,
            categoria = categoria,
            responsable = responsable,
            esComun = if (esGasto) esComun else false,
            propietario = propietario,
            descripcion = descripcion,
            metodoPago = metodoPago,
            ticketUrl = "", // El script lo llenará si hay imagen
            evitable = esGasto && evitable
        )
        // Una edición es el mismo alta con el mismo id: la cola sube con PUT (upsert por id), así que
        // la fila optimista, el reintento y la notificación salen por el mismo camino. El ticket ya
        // subido viaja en el movimiento y el script lo conserva si no llega una foto nueva.
        val nuevo = edicion?.editandoId?.let { id ->
            base.copy(
                id = id,
                ticketUrl = edicion.ticketUrlExistente,
                planId = edicion.planId,
                cuotaNumero = edicion.cuotaNumero
            )
        } ?: base
        encolar(nuevo, ticketUri, onEncolado)
    }

    /**
     * Carga [movement] en el borrador del alta para editarlo. La pantalla Nuevo se siembra de ahí
     * como con cualquier borrador; al guardar, [encolarMovimiento] reusa el id.
     */
    fun cargarParaEditar(movement: Movement) {
        val partes = movement.fecha.trim().split(" ")
        _movementDraft.value = MovementDraft(
            monto = Formato.montoParaInput(movement.monto),
            tipo = movement.tipo,
            esComun = movement.esComun,
            metodoPago = movement.metodoPago,
            propietario = movement.propietario,
            descripcion = movement.descripcion,
            fecha = partes.getOrNull(0)?.take(10).orEmpty(),
            hora = partes.getOrNull(1)?.take(5).orEmpty(),
            categoria = movement.categoria,
            evitable = movement.evitable,
            editandoId = movement.id,
            ticketUrlExistente = movement.ticketUrl,
            planId = movement.planId,
            cuotaNumero = movement.cuotaNumero,
            propietarioOriginal = movement.propietario
        )
    }

    /** ¿[movement] se puede editar? Solo lo propio y del mes en curso (ver features/version-7.7.md). */
    fun puedeEditar(movement: Movement): Boolean =
        movement.responsable.equals(_currentUserProfile.value, ignoreCase = true) &&
            movement.mes == Fechas.mesActual() &&
            !AccountingEngine.isOpeningRow(movement)

    /**
     * Mete un movimiento **ya armado** en la cola de subida: fila optimista al instante, escritura
     * en segundo plano con reintento y notificación al terminar. Es el único camino de alta; el id
     * viaja con el movimiento, así que reintentar es idempotente (la cola sube con `PUT`).
     */
    private fun encolar(nuevo: Movement, ticketUri: Uri?, onEncolado: () -> Unit) {
        _errorMessage.value = null
        // Se limpia el filtro del historial: si venía uno puesto (propio o traído desde Métricas),
        // el movimiento recién cargado podía quedar escondido justo al volver a Inicio a verlo.
        _dashboardFilters.value = DashboardFilters()

        // La cola se persiste antes que nada: si el proceso muere en el próximo milisegundo, el
        // movimiento ya está a salvo y se sube en el siguiente arranque. Con foto queda marcado
        // como `esperandoTicket` para que ningún drenado se lo lleve a medio comprimir.
        // Si se está editando un alta que todavía no se subió y no se eligió foto nueva, conserva la
        // foto que ya esperaba en la cola (si no, la edición la descartaba sin avisar).
        val ticketPrevio = if (ticketUri == null) {
            _pendingMovements.value.firstOrNull { it.movement.id == nuevo.id }?.ticketPath.orEmpty()
        } else ""
        prefsHelper.upsertPendingMovement(
            PendingMovement(nuevo, ticketPath = ticketPrevio, esperandoTicket = ticketUri != null)
        )
        refrescarPendientes()
        onEncolado()

        viewModelScope.launch {
            // La compresión del ticket va después de mostrar la fila: no tiene sentido demorar el
            // feedback por una imagen.
            if (ticketUri != null) {
                val ticketPath = withContext(Dispatchers.IO) { Tickets.comprimir(getApplication(), ticketUri, nuevo.id) }
                // Update condicional, nunca upsert: si el movimiento ya no está en la cola es que
                // se subió mientras comprimíamos, y volver a insertarlo lo escribiría dos veces.
                prefsHelper.updatePendingMovement(nuevo.id) {
                    it.copy(ticketPath = ticketPath, esperandoTicket = false)
                }
                refrescarPendientes()
            }
            MovementUploadScheduler.enqueue(getApplication(), requiereRed = !_useLocalDemo.value)
        }
    }

    /**
     * Corre una escritura con el indicador de carga prendido: si [accion] se confirma, refresca los
     * datos y llama a [onSuccess]; si no, deja [mensajeDeError] para la UI.
     */
    private fun operacion(mensajeDeError: String, onSuccess: () -> Unit = {}, accion: suspend () -> Boolean) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            if (accion()) {
                doRefreshData()
                onSuccess()
            } else {
                _errorMessage.value = mensajeDeError
            }
            _isLoading.value = false
        }
    }

    /**
     * Elimina un movimiento (baja lógica)
     */
    fun deleteMovement(movement: Movement) {
        // Si todavía está en la cola, la planilla nunca lo vio: alcanza con sacarlo de ahí. Mandar
        // el DELETE contra una fila inexistente no hacía nada y el worker terminaba subiendo igual
        // el movimiento que el usuario acababa de borrar.
        val pendiente = _pendingMovements.value.firstOrNull { it.movement.id == movement.id }
        if (pendiente != null) {
            if (pendiente.ticketPath.isNotEmpty()) {
                runCatching { java.io.File(pendiente.ticketPath).delete() }
            }
            prefsHelper.removePendingMovement(movement.id)
            refrescarPendientes()
            return
        }
        operacion("Error al eliminar el movimiento") { repository.deleteMovement(movement) }
    }

    /** ¿El usuario activo puede borrar [movement]? Solo los propios (deslizar para borrar). */
    fun puedeBorrar(movement: Movement): Boolean =
        movement.responsable.equals(_currentUserProfile.value, ignoreCase = true)

    /**
     * Duplica un movimiento **a nombre del usuario activo**, como gasto personal. Se usa en el botón
     * de duplicar de los gastos de transporte: si dos personas viajan juntas, una lo carga y la otra
     * toca duplicar para registrar fácilmente su propio gasto (no el de la otra persona).
     *
     * Pasa por la **cola de subida**, igual que el alta normal, con fila optimista y reintento.
     *
     * El vínculo con un plan de cuotas y el ticket NO se copian: un duplicado es un gasto nuevo, no
     * otro pago de la misma cuota ni el mismo comprobante.
     */
    fun duplicateMovement(movement: Movement, onEncolado: () -> Unit) {
        val yo = _currentUserProfile.value
        encolar(
            movement.copy(
                id = java.util.UUID.randomUUID().toString(),
                fecha = Fechas.ahora(),
                responsable = yo,      // pasa a mi cuenta
                propietario = yo,      // gasto personal mío (sin propiedad cruzada)
                esComun = false,
                ticketUrl = "",
                planId = "",
                cuotaNumero = 0
            ),
            ticketUri = null,
            onEncolado = onEncolado
        )
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
        _usuarios.value = config.conUsuario(actualizado)
        viewModelScope.launch {
            val ok = repository.updateUsuario(actualizado)
            if (!ok) _errorMessage.value = "No se pudo guardar tu perfil. Se aplicó localmente."
        }
    }

    fun setIsDarkMode(isDark: Boolean) {
        prefsHelper.isDarkMode = isDark
        _isDarkMode.value = isDark
    }

    // --- Filtros de Inicio ---
    fun setDashboardFilters(filters: DashboardFilters) { _dashboardFilters.value = filters }

    /**
     * Deja Inicio filtrado en los gastos de unas categorías (una, o todas las de un rubro) y
     * opcionalmente de una persona. Lo llama Métricas al tocar una línea: es el atajo para ir del
     * "cuánto" al "en qué". [persona] es un slotKey, o null para la tarjeta de gastos combinados.
     */
    fun verDetalleDeGastos(persona: String?, categorias: Set<String>) {
        _dashboardFilters.value = DashboardFilters(
            persona = persona ?: DashboardFilters.TODOS,
            tipo = DashboardFilters.GASTOS,
            categorias = categorias
        )
        // Lo que se vino a ver es el listado, no los saldos: Inicio arranca en los filtros.
        _scrollAFiltros.value = true
    }

    /** La consume Inicio una vez que efectivamente scrolleó. */
    fun consumirScrollAFiltros() { _scrollAFiltros.value = false }

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
            _paidPlans.value = repository.fetchPlans(soloPagos = true)
            _isLoadingPaid.value = false
        }
    }

    /** ¿El usuario activo puede pagar/editar/eliminar [plan]? Solo los propios: el resto es de solo lectura. */
    fun puedeGestionar(plan: CuotaPlan): Boolean =
        plan.propietario.equals(_currentUserProfile.value, ignoreCase = true)

    /** Alta de un plan de cuotas (siempre personal: del usuario que lo carga). */
    fun addPlan(
        descripcion: String,
        montoPorCuota: Double,
        cantidadCuotas: Int,
        fechaPrimeraCuota: String,
        propietario: String,
        categoria: String,
        tarjeta: String,
        evitable: Boolean = false,
        onSuccess: () -> Unit = {}
    ) {
        val plan = CuotaPlan(
            fechaCreacion = Fechas.ahora(),
            descripcion = descripcion,
            montoPorCuota = montoPorCuota,
            cantidadCuotas = cantidadCuotas,
            fechaPrimeraCuota = fechaPrimeraCuota,
            propietario = propietario,
            categoria = categoria,
            tarjeta = tarjeta,
            evitable = evitable
        )
        operacion("No se pudo crear el plan de cuotas.", onSuccess) { repository.guardarPlan(plan) }
    }

    /** Edición de un plan existente (conserva el id). */
    fun updatePlan(plan: CuotaPlan, onSuccess: () -> Unit = {}) =
        operacion("No se pudo actualizar el plan.", onSuccess) { repository.guardarPlan(plan) }

    /** Baja lógica de un plan. */
    fun deletePlan(plan: CuotaPlan, onSuccess: () -> Unit = {}) =
        operacion("No se pudo eliminar el plan.", onSuccess) { repository.deletePlan(plan) }

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
            val fecha = Fechas.ahora()
            var todoOk = true
            for ((plan, numero) in cuotas) {
                // Id derivado del plan y la cuota, no un UUID nuevo por intento: si el lote falla a
                // la mitad, reintentar pisa las filas ya escritas en vez de duplicarlas (y hace
                // imposible pagar dos veces la misma cuota).
                val ok = repository.saveMovement(CuotasEngine.movimientoDePago(plan, numero, fecha, metodoPago))
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
        val movimiento = CuotasEngine.movimientoDePago(plan, numero, fecha, metodoPago, monto ?: plan.montoPorCuota)
        // Un pago puede completar el plan (pasa a "pagados"): el refresco trae movimientos y planes.
        operacion("No se pudo confirmar el pago de la cuota.", onSuccess) { repository.saveMovement(movimiento) }
    }

    private companion object {
        const val TAG = "AhorroViewModel"
    }
}
