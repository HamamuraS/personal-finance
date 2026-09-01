package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import com.example.data.CuotaPlan
import com.example.data.Movement
import com.example.data.UsuariosConfig
import com.example.ui.AccountingEngine
import com.example.ui.BalanceBreakdown
import com.example.ui.DashboardFilters
import com.example.ui.CuotaRecordatorio
import com.example.ui.CuotasEngine
import com.example.ui.theme.personaColor
import java.text.NumberFormat
import java.util.Locale

/**
 * Posición del panel de filtros dentro del `LazyColumn` de Inicio.
 *
 * `LazyListState` solo entiende de índices, así que hay que contar los `item` que van antes:
 * pozo común, [opcional] recordatorio de cuotas, título "Saldos Individuales", fila de socios,
 * gastos comunes, dinero cruzado y el título "Movimientos Recientes".
 *
 * Está separado y con nombre para que se vea que existe: agregar o sacar una tarjeta del
 * encabezado obliga a tocar esta cuenta.
 */
private fun indiceDeFiltros(hayRecordatorioDeCuotas: Boolean): Int =
    if (hayRecordatorioDeCuotas) 7 else 6

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    userProfile: String,
    config: UsuariosConfig,
    balance: BalanceBreakdown,
    movements: List<Movement>,
    isLoading: Boolean,
    useLocalDemo: Boolean,
    onRefresh: () -> Unit,
    onDeleteMovement: (Movement) -> Unit,
    onDuplicateMovement: (Movement) -> Unit,
    availableMonths: List<String>,
    selectedMonth: String,
    isCurrentMonth: Boolean,
    onMonthSelected: (String) -> Unit,
    plans: List<CuotaPlan> = emptyList(),
    allMovements: List<Movement> = emptyList(),
    onConfirmCuota: (CuotaPlan, Int, String, String, Double?, () -> Unit) -> Unit = { _, _, _, _, _, _ -> },
    // Filtros: búsqueda por descripción + persona + tipo + categorías. Viven en el ViewModel (ver
    // [DashboardFilters]) para sobrevivir el cambio de pestaña y para que Métricas pueda aplicarlos.
    filters: DashboardFilters = DashboardFilters(),
    onFiltersChange: (DashboardFilters) -> Unit = {},
    // Altas encoladas que la planilla todavía no confirmó, con su cantidad de intentos fallidos:
    // se listan igual (fila optimista) pero marcadas, para que se vea que el guardado sigue en
    // curso — o que falló y se está reintentando.
    pendingStates: Map<String, Int> = emptyMap(),
    /**
     * Señal de un solo uso: al llegar desde Métricas hay que abrir la pantalla directo a la altura
     * de los filtros, porque lo que se vino a ver es el listado filtrado y no los saldos. Se
     * consume con [onScrollAFiltrosConsumido] para no volver a saltar cada vez que se entra a
     * Inicio a mano.
     */
    scrollAFiltros: Boolean = false,
    onScrollAFiltrosConsumido: () -> Unit = {}
) {
    // Se calcula acá arriba, y no dentro del LazyColumn, porque de esta tarjeta depende el índice
    // del panel de filtros (ver [indiceDeFiltros]).
    val mesActualReal = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date())
    val recordatoriosCuotas = CuotasEngine
        .recordatoriosDelMes(CuotasEngine.mesAPagar(mesActualReal), plans, allMovements)
        .filter { it.plan.propietario.equals(userProfile, ignoreCase = true) }

    val listState = rememberLazyListState()
    LaunchedEffect(scrollAFiltros) {
        if (!scrollAFiltros) return@LaunchedEffect
        listState.scrollToItem(indiceDeFiltros(hayRecordatorioDeCuotas = recordatoriosCuotas.isNotEmpty()))
        onScrollAFiltrosConsumido()
    }

    val filterPerson = filters.persona
    val filterTipo = filters.tipo
    val selectedCategories = filters.categorias
    val searchQuery = filters.query
    val formatMoney = remember {
        java.text.DecimalFormat("#,##0.00").apply {
            val symbols = java.text.DecimalFormatSymbols()
            symbols.groupingSeparator = '.'
            symbols.decimalSeparator = ','
            decimalFormatSymbols = symbols
            positivePrefix = "$ "
            negativePrefix = "-$ "
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    var expanded by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.clickable { expanded = true },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (selectedMonth.isNotEmpty()) selectedMonth else "Fondo Compartido",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                            Text(
                                text = if (useLocalDemo) "Modo Local (Demo)" else "Sincronizado con Google Sheets",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            availableMonths.forEach { month ->
                                DropdownMenuItem(
                                    text = { Text(month) },
                                    onClick = { 
                                        onMonthSelected(month)
                                        expanded = false 
                                    }
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !isLoading) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Recargar")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ¡OJO! Si se agrega o saca un `item` de acá arriba, hay que actualizar
                // [indiceDeFiltros] o el salto desde Métricas cae en la tarjeta equivocada.

                // Tarjeta de Pozo Común
                item {
                    PozoComunCard(userProfile = userProfile, config = config, balance = balance, formatMoney = formatMoney)
                }

                // Recordatorio: cuotas a pagar del usuario activo (incluye atrasadas), independiente
                // del mes que se esté visualizando. Se corta en el último resumen CERRADO, no en el
                // mes en curso: la tarjeta cierra a fin de mes, así que en agosto se paga julio.
                if (recordatoriosCuotas.isNotEmpty()) {
                    item {
                        CuotasRecordatorioCard(
                            recordatorios = recordatoriosCuotas,
                            formatMoney = formatMoney,
                            isLoading = isLoading,
                            onConfirmCuota = onConfirmCuota
                        )
                    }
                }

                // Desglose de Saldos Individuales
                item {
                    Text(
                        text = "Saldos Individuales",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Slot primario ("Santiago" legacy) ↔ balance.santiago*; secundario ↔ rocio*.
                        DesgloseSocioCard(
                            modifier = Modifier.weight(1f),
                            nombre = config.primario.nombre,
                            saldo = balance.santiagoSaldoFinal,
                            enMano = balance.santiagoEnMano,
                            aportes = balance.santiagoAportes,
                            personales = balance.santiagoGastosPersonales,
                            formatMoney = formatMoney,
                            avatarColor = personaColor(config.primario.slotKey, userProfile)
                        )
                        DesgloseSocioCard(
                            modifier = Modifier.weight(1f),
                            nombre = config.secundario.nombre,
                            saldo = balance.rocioSaldoFinal,
                            enMano = balance.rocioEnMano,
                            aportes = balance.rocioAportes,
                            personales = balance.rocioGastosPersonales,
                            formatMoney = formatMoney,
                            avatarColor = personaColor(config.secundario.slotKey, userProfile)
                        )
                    }
                }

                // Gasto común total (una sola vez; antes se repetía simétrico en rojo en cada tarjeta)
                item {
                    GastosComunesCard(total = balance.gastosComunesTotales, formatMoney = formatMoney)
                }

                // Relación de propiedad cruzada (una sola vez, no redundante por tarjeta)
                item {
                    DineroCruzadoCard(
                        externoSantiago = balance.santiagoExterno,
                        currentUserProfile = userProfile,
                        config = config,
                        formatMoney = formatMoney
                    )
                }

                // Historial Reciente de Movimientos
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Movimientos Recientes",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        
                        if (useLocalDemo) {
                            Text(
                                text = "Mantén presionado para borrar",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                // Filtrado: descripción -> persona (propietario) -> tipo -> categorías (multi).
                // La persona se resuelve con [AccountingEngine.perteneceA]: de quién ES el movimiento,
                // no de qué cuenta salió. Un gasto de Rocío pagado desde la cuenta de Santiago cae
                // bajo Rocío, igual que en las tarjetas de saldos y en Métricas. Los comunes /
                // "Ambos" pertenecen a los dos, así que aparecen filtre quien filtre.
                val query = searchQuery.trim()
                val baseFiltered = movements.filter { m ->
                    (query.isEmpty() || m.descripcion.contains(query, ignoreCase = true)) &&
                        (filterPerson == DashboardFilters.TODOS || AccountingEngine.perteneceA(m, filterPerson)) &&
                        when (filterTipo) {
                            "Gastos" -> m.tipo.equals("Gasto", ignoreCase = true)
                            "Aportes" -> m.tipo.equals("Aporte", ignoreCase = true)
                            "Transfer." -> m.tipo.equals("Transferencia", ignoreCase = true)
                            else -> true
                        }
                }
                // Las categorías disponibles se derivan de lo que realmente quedó visible
                val availableCategories = baseFiltered.map { it.categoria }.distinct().sorted()
                // Solo consideramos las categorías seleccionadas que siguen estando disponibles
                val effectiveCategories = selectedCategories intersect availableCategories.toSet()
                val filteredMovements = baseFiltered.filter {
                    effectiveCategories.isEmpty() || effectiveCategories.contains(it.categoria)
                }

                // Panel de Filtros (rediseñado)
                item {
                    MovementFilters(
                        person = filterPerson,
                        currentUser = userProfile,
                        config = config,
                        searchQuery = searchQuery,
                        onSearchChange = { onFiltersChange(filters.copy(query = it)) },
                        onPersonChange = { onFiltersChange(filters.copy(persona = it)) },
                        tipo = filterTipo,
                        onTipoChange = { onFiltersChange(filters.copy(tipo = it)) },
                        availableCategories = availableCategories,
                        selectedCategories = effectiveCategories,
                        onToggleCategory = { cat ->
                            val nuevas = if (selectedCategories.contains(cat)) {
                                selectedCategories - cat
                            } else {
                                selectedCategories + cat
                            }
                            onFiltersChange(filters.copy(categorias = nuevas))
                        },
                        onClearCategories = { onFiltersChange(filters.copy(categorias = emptySet())) }
                    )
                }

                if (filteredMovements.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Sin movimientos registrados para el filtro",
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                } else {
                    items(filteredMovements, key = { it.id }) { mov ->
                        SwipeableMovementItem(
                            movement = mov,
                            formatMoney = formatMoney,
                            useLocalDemo = useLocalDemo,
                            isCurrentMonth = isCurrentMonth,
                            currentUserProfile = userProfile,
                            config = config,
                            onDelete = { onDeleteMovement(mov) },
                            onDuplicate = { onDuplicateMovement(mov) },
                            intentosPendientes = pendingStates[mov.id]
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableMovementItem(
    movement: Movement,
    formatMoney: NumberFormat,
    useLocalDemo: Boolean,
    isCurrentMonth: Boolean,
    currentUserProfile: String,
    config: UsuariosConfig,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    /** null = ya está en la planilla. 0 = subiendo. > 0 = falló y se está reintentando. */
    intentosPendientes: Int? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var hasVibrated by remember { mutableStateOf(false) }
    
    val isOwnMovement = movement.responsable.equals(currentUserProfile, ignoreCase = true)
    
    // Usamos un solo estado y una técnica para romper la referencia circular
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            false 
        },
        positionalThreshold = { distance -> distance * 0.6f }
    )

    // Lógica de activación: Solo si es propio y el progreso REAL cruza el 60%
    LaunchedEffect(dismissState.progress) {
        if (!isOwnMovement) return@LaunchedEffect

        val isFarEnough = dismissState.progress >= 0.6f
        val isGoingToDismiss = dismissState.targetValue == SwipeToDismissBoxValue.EndToStart
        
        if (isGoingToDismiss && isFarEnough) {
            if (!hasVibrated) {
                val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator?.vibrate(android.os.VibrationEffect.createOneShot(40, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    vibrator?.vibrate(40)
                }
                hasVibrated = true
            }
            
            if (dismissState.currentValue == SwipeToDismissBoxValue.Settled) {
                 showDeleteDialog = true
            }
        } else {
            if (dismissState.progress < 0.1f) {
                hasVibrated = false
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("¿Eliminar Movimiento?") },
            text = { Text("Tranqui, puede recuperarse si se elimina.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteDialog = false
                }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = isOwnMovement, // Solo permite deslizar si es propio
        backgroundContent = {
            if (isOwnMovement) {
                val color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(20.dp))
                        .background(color)
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Eliminar",
                        tint = Color.White
                    )
                }
            }
        }
    ) {
        MovementItem(
            movement = movement,
            formatMoney = formatMoney,
            useLocalDemo = useLocalDemo,
            isCurrentMonth = isCurrentMonth,
            currentUserProfile = currentUserProfile,
            config = config,
            onDelete = onDelete,
            onDuplicate = onDuplicate,
            intentosPendientes = intentosPendientes
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MovementItem(
    movement: Movement,
    formatMoney: NumberFormat,
    useLocalDemo: Boolean,
    isCurrentMonth: Boolean,
    currentUserProfile: String,
    config: UsuariosConfig,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    /** null = ya está en la planilla. 0 = subiendo. > 0 = falló y se está reintentando. */
    intentosPendientes: Int? = null
) {
    var showDuplicateDialog by remember { mutableStateOf(false) }
    var showDetail by remember { mutableStateOf(false) }

    if (showDetail) {
        MovementDetailDialog(
            movement = movement,
            config = config,
            currentUserProfile = currentUserProfile,
            formatMoney = formatMoney,
            onDismiss = { showDetail = false }
        )
    }

    if (showDuplicateDialog) {
        AlertDialog(
            onDismissRequest = { showDuplicateDialog = false },
            title = { Text("¿Duplicar Movimiento?") },
            text = { Text("Se creará una copia a tu nombre (${config.nombreDe(currentUserProfile)}), como gasto personal y con la fecha de hoy.") },
            confirmButton = {
                TextButton(onClick = {
                    onDuplicate()
                    showDuplicateDialog = false
                }) {
                    Text("Duplicar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDuplicateDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    val isDark = MaterialTheme.colorScheme.background == com.example.ui.theme.DarkBackground
    val cardBg = if (isDark) MaterialTheme.colorScheme.surface else Color.White
    val cardBorder = if (isDark) Color(0xFF333833) else Color(0xFFF1F5F9)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDetail = true },   // tocar la tarjeta abre el detalle completo
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardBg
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Icono contextual
            val isGasto = movement.tipo.lowercase() == "gasto"
            val isAporte = movement.tipo.lowercase() == "aporte"

            // El color del aporte es el de identidad del aportante (estable ante el usuario activo).
            // Solo se usa cuando isAporte, y `responsable` siempre es un slotKey.
            val aporteColor = if (isAporte) personaColor(movement.responsable, currentUserProfile)
            else MaterialTheme.colorScheme.tertiary // Fallback para transferencias u otros
            
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isAporte -> aporteColor.copy(alpha = 0.1f)
                            isGasto && movement.esComun -> MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                            isGasto -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                            else -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f) // Transferencia
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        isAporte -> Icons.Default.KeyboardArrowUp
                        isGasto -> Icons.Default.KeyboardArrowDown
                        else -> Icons.Default.Refresh
                    },
                    contentDescription = null,
                    tint = when {
                        isAporte -> aporteColor
                        isGasto && movement.esComun -> MaterialTheme.colorScheme.error
                        isGasto -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        else -> MaterialTheme.colorScheme.tertiary
                    }
                )
            }

            // Detalles del movimiento
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = movement.categoria,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Text(
                        text = if (isAporte) "+${formatMoney.format(movement.monto)}" else "-${formatMoney.format(movement.monto)}",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = when {
                            isAporte -> aporteColor
                            isGasto && movement.esComun -> MaterialTheme.colorScheme.error
                            isGasto -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.tertiary
                        }
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val responsableTag = config.nombreDe(movement.responsable)
                    val metodoTag = if (movement.metodoPago.contains("Efectivo", ignoreCase = true)) "💵" else "💳"
                    // La fila optimista se marca: encolada pero sin confirmar. Si ya hubo
                    // intentos fallidos se dice, porque la notificación de error puede no haber
                    // llegado nunca (se descarta si el permiso de notificaciones está denegado).
                    val pendienteTag = when {
                        intentosPendientes == null -> ""
                        intentosPendientes > 0 -> "⚠️ Sin guardar, reintentando • "
                        else -> "⏳ Guardando… • "
                    }
                    val subtitulo = if (movement.descripcion.isNotEmpty()) {
                        "$pendienteTag$metodoTag $responsableTag • ${movement.descripcion}"
                    } else {
                        "$pendienteTag$metodoTag $responsableTag"
                    }
                    Text(
                        text = subtitulo,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1.5f)
                    )

                    // Icono de Ticket
                    if (movement.ticketUrl.isNotEmpty()) {
                        val context = androidx.compose.ui.platform.LocalContext.current
                        Icon(
                            imageVector = Icons.Default.Receipt,
                            contentDescription = "Ver Ticket",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(movement.ticketUrl))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        // Fallback or error
                                    }
                                }
                                .padding(horizontal = 2.dp)
                        )
                    }

                    // Tag Compartido vs Personal
                    if (isGasto) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Botón Duplicar si es Transporte Público
                            if (movement.categoria.contains("Transporte", ignoreCase = true)) {
                                IconButton(
                                    onClick = { showDuplicateDialog = true },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Duplicar",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            if (movement.propietario != movement.responsable && movement.propietario != "Ambos") {
                                // Tag que nombra al propietario: usa su color de identidad estable.
                                val propietarioColor = personaColor(movement.propietario, currentUserProfile)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(propietarioColor.copy(alpha = 0.1f))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "De ${config.nombreDe(movement.propietario)}",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = propietarioColor
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (movement.esComun) MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                                    )
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (movement.esComun) "Común" else "Pers.",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (movement.esComun) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    } else if (movement.tipo.lowercase() == "transferencia") {
                        val isPropia = movement.propietario == movement.responsable
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (isPropia) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                    else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f)
                                )
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isPropia) "Mía" else "Transf.",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isPropia) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                            )
                        }
                    } else if (movement.tipo.lowercase() == "aporte" && movement.propietario != movement.responsable) {
                         // Tag que nombra a la cuenta destino (responsable): color de identidad estable.
                         val responsableColor = personaColor(movement.responsable, currentUserProfile)
                         Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(responsableColor.copy(alpha = 0.08f))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "A ${config.nombreDe(movement.responsable)}",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = responsableColor
                            )
                        }
                    }
                }

                // Mostrar fecha muy minimal abajo
                Text(
                    text = formatDateMinimal(movement.fecha).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

/**
 * Detalle completo de un movimiento en un diálogo. Se abre al tocar la tarjeta del listado; su razón
 * principal es poder leer la **descripción entera**, que no entra en la tarjeta compacta.
 */
@Composable
private fun MovementDetailDialog(
    movement: Movement,
    config: UsuariosConfig,
    currentUserProfile: String,
    formatMoney: NumberFormat,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isAporte = movement.tipo.equals("Aporte", ignoreCase = true)
    val isGasto = movement.tipo.equals("Gasto", ignoreCase = true)
    val montoColor = when {
        isAporte -> personaColor(movement.responsable, currentUserProfile)
        isGasto && movement.esComun -> MaterialTheme.colorScheme.error
        isGasto -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.tertiary
    }
    val signo = if (isAporte) "+" else "-"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(movement.categoria, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "$signo${formatMoney.format(movement.monto)}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp,
                    color = montoColor
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                DetailRow("Tipo", movement.tipo)
                DetailRow("Fecha", formatDateMinimal(movement.fecha))
                DetailRow("Responsable", config.nombreDe(movement.responsable))
                if (isGasto) {
                    DetailRow("Distribución", if (movement.esComun) "Común (50/50)" else "Personal")
                }
                DetailRow("Propietario", config.nombreDe(movement.propietario))
                DetailRow("Método de pago", movement.metodoPago)
                // Descripción completa (el motivo de este popup): texto entero, con saltos de línea.
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Descripción",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = movement.descripcion.ifBlank { "Sin descripción" },
                        fontSize = 14.sp,
                        color = if (movement.descripcion.isBlank())
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
                if (movement.ticketUrl.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(movement.ticketUrl)))
                            } catch (e: Exception) { /* sin visor disponible */ }
                        },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ver ticket", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar", fontWeight = FontWeight.Bold) }
        }
    )
}

/** Fila etiqueta/valor para el diálogo de detalle. */
@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun PozoComunCard(userProfile: String, config: UsuariosConfig, balance: BalanceBreakdown, formatMoney: NumberFormat) {
    val isDark = MaterialTheme.colorScheme.background == com.example.ui.theme.DarkBackground
    val cardBg = if (isDark) Color(0xFF2E332F) else Color(0xFFE8F3E9)
    val cardBorder = if (isDark) Color(0xFF414941) else Color(0xFFDCE5DB)
    val labelColor = if (isDark) Color(0xFFCDD3CD) else Color(0xFF414941)
    val textMainColor = if (isDark) Color.White else Color(0xFF1A1C19)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardBg
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Pozo Común Total",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = labelColor,
                letterSpacing = 0.5.sp
            )
            
            Text(
                text = formatMoney.format(balance.totalPozo),
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                color = textMainColor,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Split Bar (Si se dividiera hoy)
            val santiagoRatioUnsafe = if (balance.totalPozo > 0) (balance.santiagoSaldoFinal / balance.totalPozo).coerceIn(0.0, 1.0) else 0.5

            // Slot primario siempre a la izquierda, secundario a la derecha (colores estables).
            val santiagoRatio = santiagoRatioUnsafe
            val rocioRatio = 1.0 - santiagoRatio

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color(0xFF5D625C) else Color(0xFFC2CDC1))
            ) {
                // Barra del slot primario
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(if (santiagoRatio > 0) santiagoRatio.toFloat() else 0.001f)
                        .background(personaColor(config.primario.slotKey, userProfile))
                )
                // Barra del slot secundario
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(if (rocioRatio > 0) rocioRatio.toFloat() else 0.001f)
                        .background(personaColor(config.secundario.slotKey, userProfile))
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Columna 1 (slot primario)
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(personaColor(config.primario.slotKey, userProfile)))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(config.primario.nombre, fontSize = 12.sp, color = labelColor, fontWeight = FontWeight.SemiBold)
                    }
                    val saldoS = balance.santiagoSaldoFinal
                    val efecS = balance.santiagoEfectivo
                    val virtS = balance.santiagoVirtual
                    
                    Text(
                        text = formatMoney.format(saldoS),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = textMainColor
                    )
                    
                    // Desglose. 💵 y 💳 son el dinero FÍSICO (lo que se concilia contra el banco);
                    // 🤝 es la posición cruzada. Las tres líneas suman el titular de arriba.
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        Text("💵", fontSize = 10.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(formatMoney.format(efecS), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textMainColor.copy(alpha = 0.7f))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("💳", fontSize = 10.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(formatMoney.format(virtS), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textMainColor.copy(alpha = 0.7f))
                    }
                    if (kotlin.math.abs(balance.santiagoExterno) >= 1.0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🤝", fontSize = 10.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                formatMoney.format(balance.santiagoExterno),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (balance.santiagoExterno < 0) MaterialTheme.colorScheme.error
                                        else personaColor(config.primario.slotKey, userProfile)
                            )
                        }
                    }
                }

                // Columna 2 (slot secundario)
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(config.secundario.nombre, fontSize = 12.sp, color = labelColor, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(personaColor(config.secundario.slotKey, userProfile)))
                    }
                    val saldoR = balance.rocioSaldoFinal
                    val efecR = balance.rocioEfectivo
                    val virtR = balance.rocioVirtual

                    Text(
                        text = formatMoney.format(saldoR),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = textMainColor
                    )

                    // Desglose (espejado). Mismo criterio que la columna izquierda.
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        Text(formatMoney.format(efecR), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textMainColor.copy(alpha = 0.7f))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("💵", fontSize = 10.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formatMoney.format(virtR), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textMainColor.copy(alpha = 0.7f))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("💳", fontSize = 10.sp)
                    }
                    if (kotlin.math.abs(balance.rocioExterno) >= 1.0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                formatMoney.format(balance.rocioExterno),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (balance.rocioExterno < 0) MaterialTheme.colorScheme.error
                                        else personaColor(config.secundario.slotKey, userProfile)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("🤝", fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Muestra, una sola vez, la posición de propiedad cruzada entre ambos.
 * [externoSantiago] es la posición externa neta de Santiago (positiva = tiene plata en cuentas
 * de Rocío; negativa = Rocío tiene plata en cuentas de Santiago). Como es simétrica, no tiene
 * sentido repetirla en cada tarjeta.
 */
@Composable
fun DineroCruzadoCard(externoSantiago: Double, currentUserProfile: String, config: UsuariosConfig, formatMoney: NumberFormat) {
    // Umbral para ignorar redondeos de centavos
    if (kotlin.math.abs(externoSantiago) < 1.0) return

    val isDark = MaterialTheme.colorScheme.background == com.example.ui.theme.DarkBackground
    val cardBg = if (isDark) MaterialTheme.colorScheme.surface else Color.White
    val cardBorder = if (isDark) Color(0xFF333833) else Color(0xFFE2E8F0)
    val textMainColor = if (isDark) Color.White else Color(0xFF191C19)

    // Quién tiene plata en la cuenta de quién. externoSantiago > 0 = el primario tiene plata en la
    // cuenta del secundario. El acento representa al dueño (persona concreta), así que usa su color
    // de identidad estable (por slotKey), no `primary` a secas.
    val duenoKey = if (externoSantiago > 0) config.primario.slotKey else config.secundario.slotKey
    val cuentaKey = if (externoSantiago > 0) config.secundario.slotKey else config.primario.slotKey
    val dueno = config.nombreDe(duenoKey)
    val cuentaDe = config.nombreDe(cuentaKey)
    val monto = kotlin.math.abs(externoSantiago)
    val acento = personaColor(duenoKey, currentUserProfile)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(acento.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = null,
                    tint = acento
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Dinero cruzado",
                    fontSize = 11.sp,
                    color = textMainColor.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "$dueno tiene plata en la cuenta de $cuentaDe",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textMainColor
                )
            }
            Text(
                text = formatMoney.format(monto),
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = acento
            )
        }
    }
}

/**
 * Tarjeta de saldo individual.
 *
 *  - "Le corresponde" = lo que realmente es suyo (patrimonial).
 *  - "En su poder"    = el dinero físico en sus cuentas, conciliable contra el homebanking.
 *
 * La diferencia entre ambas es la posición cruzada, que se explica una sola vez en
 * [DineroCruzadoCard] (y línea 🤝 del pozo) en vez de repetirse por tarjeta.
 */
@Composable
fun DesgloseSocioCard(
    modifier: Modifier,
    nombre: String,
    saldo: Double,
    enMano: Double,
    aportes: Double,
    personales: Double,
    formatMoney: NumberFormat,
    avatarColor: Color
) {
    val isDark = MaterialTheme.colorScheme.background == com.example.ui.theme.DarkBackground
    val cardBg = if (isDark) MaterialTheme.colorScheme.surface else Color.White
    val cardBorder = if (isDark) Color(0xFF333833) else Color(0xFFE2E8F0)
    val textMainColor = if (isDark) Color.White else Color(0xFF191C19)
    val rojo = MaterialTheme.colorScheme.error

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardBg
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header del socio
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(avatarColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = nombre.take(1),
                        color = avatarColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Text(
                    text = nombre,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = textMainColor
                )
            }

            // Lo que realmente es suyo (físico ± lo cruzado). Es el número que importa.
            Column {
                Text(
                    text = "Le corresponde",
                    fontSize = 11.sp,
                    color = textMainColor.copy(alpha = 0.5f)
                )
                Text(
                    text = formatMoney.format(saldo),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (saldo < 0) rojo else textMainColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Saldo físico en sus propias cuentas (efectivo + virtual). Es el que se concilia
            // contra el homebanking / la billetera real.
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "En su poder",
                    fontSize = 11.sp,
                    color = textMainColor.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = formatMoney.format(enMano),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = textMainColor.copy(alpha = 0.9f)
                )
            }

            HorizontalDivider(color = if (isDark) Color(0xFF333833) else Color(0xFFF1F5F9))

            // Aportes (+)
            Column {
                Text(
                    text = "Aportes (+)",
                    fontSize = 10.sp,
                    color = textMainColor.copy(alpha = 0.5f)
                )
                Text(
                    text = formatMoney.format(aportes),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = avatarColor,
                    maxLines = 1
                )
            }

            // G. Personales (-)
            Column {
                Text(
                    text = "Personales (-)",
                    fontSize = 10.sp,
                    color = textMainColor.copy(alpha = 0.5f)
                )
                Text(
                    text = formatMoney.format(personales),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textMainColor.copy(alpha = 0.7f),
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Muestra, una sola vez, el total de gastos comunes del periodo (se dividen 50/50).
 * Antes se repetía como "Comunes (-)" en rojo en ambas tarjetas, siempre con el mismo valor.
 */
@Composable
fun GastosComunesCard(total: Double, formatMoney: NumberFormat) {
    if (total < 1.0) return

    val isDark = MaterialTheme.colorScheme.background == com.example.ui.theme.DarkBackground
    val cardBg = if (isDark) MaterialTheme.colorScheme.surface else Color.White
    val cardBorder = if (isDark) Color(0xFF333833) else Color(0xFFE2E8F0)
    val textMainColor = if (isDark) Color.White else Color(0xFF191C19)
    val rojo = MaterialTheme.colorScheme.error

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(rojo.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Groups,
                    contentDescription = null,
                    tint = rojo
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Gastos comunes",
                    fontSize = 11.sp,
                    color = textMainColor.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${formatMoney.format(total / 2.0)} cada uno",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textMainColor
                )
            }
            Text(
                text = formatMoney.format(total),
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = rojo
            )
        }
    }
}

/**
 * Tarjeta recordatorio del Dashboard: lista las cuotas impagas del usuario activo (incluidas las
 * atrasadas) con un botón de pago rápido que reutiliza el mismo diálogo que el detalle del plan.
 */
@Composable
fun CuotasRecordatorioCard(
    recordatorios: List<CuotaRecordatorio>,
    formatMoney: NumberFormat,
    isLoading: Boolean,
    onConfirmCuota: (CuotaPlan, Int, String, String, Double?, () -> Unit) -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background == com.example.ui.theme.DarkBackground
    val cardBg = if (isDark) MaterialTheme.colorScheme.surface else Color.White
    val cardBorder = if (isDark) Color(0xFF333833) else Color(0xFFE2E8F0)
    val textMain = if (isDark) Color.White else Color(0xFF191C19)
    val accent = MaterialTheme.colorScheme.primary

    var target by remember { mutableStateOf<CuotaRecordatorio?>(null) }
    target?.let { rec ->
        ConfirmarCuotaDialog(
            planDescripcion = rec.plan.descripcion,
            numero = rec.cuota.numero,
            cantidadCuotas = rec.plan.cantidadCuotas,
            esUltima = rec.cuota.numero == rec.plan.cantidadCuotas,
            esAtrasada = rec.atrasada,
            mesVencimiento = rec.cuota.mesVencimiento,
            montoSugerido = rec.plan.montoPorCuota,
            formatMoney = formatMoney,
            isLoading = isLoading,
            onDismiss = { target = null },
            onConfirm = { fecha, metodo, monto ->
                onConfirmCuota(rec.plan, rec.cuota.numero, fecha, metodo, monto) { target = null }
            }
        )
    }

    val total = recordatorios.sumOf { it.cuota.monto }
    val hayAtrasadas = recordatorios.any { it.atrasada }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier.size(32.dp).clip(CircleShape).background(accent.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CreditCard, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                    }
                    Column {
                        Text("Cuotas a pagar", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textMain)
                        if (hayAtrasadas) {
                            Text("Tenés cuotas atrasadas", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                Text(formatMoney.format(total), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = accent)
            }

            recordatorios.forEach { rec ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Cuota ${rec.cuota.numero}/${rec.plan.cantidadCuotas} — ${rec.plan.descripcion}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textMain,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (rec.atrasada) "Venció en ${formatMonthLabel(rec.cuota.mesVencimiento)}" else formatMonthLabel(rec.cuota.mesVencimiento),
                            fontSize = 10.sp,
                            color = if (rec.atrasada) MaterialTheme.colorScheme.error else textMain.copy(alpha = 0.5f)
                        )
                    }
                    Text(formatMoney.format(rec.cuota.monto), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textMain.copy(alpha = 0.8f))
                    Button(
                        onClick = { target = rec },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = MaterialTheme.colorScheme.onPrimary),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Text("Pagar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

fun formatDateMinimal(input: String): String {
    if (input.isBlank()) return "Fecha desconocida"
    try {
        if (input.matches(Regex("^[a-zA-Z]{3}\\s[a-zA-Z]{3}\\s\\d{2}\\s\\d{4}.*"))) {
            val parts = input.split(Regex("\\s+"))
            if (parts.size >= 4) {
                val month = parts[1]
                val day = parts[2]
                val year = parts[3]
                var time = if (parts.size >= 5) parts[4] else ""
                
                if (time.contains(":")) {
                    val timeParts = time.split(":")
                    if (timeParts.size >= 2) time = "${timeParts[0]}:${timeParts[1]}"
                } else {
                    time = ""
                }
                
                val mEs = when(month.lowercase()) {
                     "jan", "ene" -> "enero"; "feb" -> "febrero"; "mar" -> "marzo"; "apr", "abr" -> "abril"
                     "may" -> "mayo"; "jun" -> "junio"; "jul" -> "julio"; "aug", "ago" -> "agosto"
                     "sep" -> "septiembre"; "oct" -> "octubre"; "nov" -> "noviembre"; "dec", "dic" -> "diciembre"
                     else -> month
                }
                
                return if (time != "00:00" && time.isNotEmpty()) "$day de $mEs de $year $time" else "$day de $mEs de $year"
            }
        }
        
        if (input.matches(Regex("\\d{4}-\\d{2}-\\d{2}.*"))) {
             val datePart = input.substring(0, 10)
             val t = datePart.split("-")
             val mEs = when(t[1]) {
                     "01" -> "enero"; "02" -> "febrero"; "03" -> "marzo"; "04" -> "abril"
                     "05" -> "mayo"; "06" -> "junio"; "07" -> "julio"; "08" -> "agosto"
                     "09" -> "septiembre"; "10" -> "octubre"; "11" -> "noviembre"; "12" -> "diciembre"
                     else -> t[1]
             }
             val timePart = if (input.length > 10) input.substring(11).trim() else ""
             return if (timePart.isNotEmpty()) "${t[2]} de $mEs de ${t[0]} $timePart" else "${t[2]} de $mEs de ${t[0]}"
        }
    } catch (e: Exception) {}
    return input
}

/**
 * Panel de filtros del historial de movimientos.
 * Combina tres niveles: persona (responsable) -> tipo -> categorías (multi-selección).
 * Las categorías mostradas se derivan dinámicamente de los movimientos ya filtrados por
 * persona/tipo, de modo que solo aparecen opciones que realmente existen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovementFilters(
    person: String,
    currentUser: String,
    config: UsuariosConfig,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onPersonChange: (String) -> Unit,
    tipo: String,
    onTipoChange: (String) -> Unit,
    availableCategories: List<String>,
    selectedCategories: Set<String>,
    onToggleCategory: (String) -> Unit,
    onClearCategories: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background == com.example.ui.theme.DarkBackground
    val cardBg = if (isDark) MaterialTheme.colorScheme.surface else Color.White
    val cardBorder = if (isDark) Color(0xFF333833) else Color(0xFFE2E8F0)
    val trackBg = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 0) Búsqueda por descripción
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Buscar por descripción", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Limpiar búsqueda", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                )
            )

            // 1) Persona (control segmentado)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Persona",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(trackBg)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Valor del filtro = "Todos" o un slotKey; la etiqueta visible es el nombre.
                    listOf("Todos", config.primario.slotKey, config.secundario.slotKey).forEach { key ->
                        val selected = person == key
                        val accent = if (key == "Todos") MaterialTheme.colorScheme.onSurface
                        else personaColor(key, currentUser)
                        val label = if (key == "Todos") "Todos" else config.nombreDe(key)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (selected) accent.copy(alpha = 0.15f) else Color.Transparent)
                                .clickable { onPersonChange(key) }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // 2) Tipo de movimiento (selección única)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("Todos", "Gastos", "Aportes", "Transfer.").forEach { t ->
                    val selected = tipo == t
                    FilterChip(
                        selected = selected,
                        onClick = { onTipoChange(t) },
                        label = {
                            Text(
                                text = t,
                                fontSize = 11.sp,
                                maxLines = 1,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)
                        )
                    )
                }
            }

            // 3) Categorías (multi-selección) - desplegable, contraído por defecto.
            // El estado del filtro vive fuera de este composable, así que contraer/expandir
            // nunca deshace la selección.
            if (availableCategories.isNotEmpty()) {
                var categoriesExpanded by remember { mutableStateOf(false) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { categoriesExpanded = !categoriesExpanded }
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (categoriesExpanded) Icons.Default.KeyboardArrowUp
                                else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (categoriesExpanded) "Contraer" else "Expandir",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (selectedCategories.isEmpty()) "Categorías"
                                else "Categorías (${selectedCategories.size})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                        if (selectedCategories.isNotEmpty()) {
                            Text(
                                text = "Limpiar",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { onClearCategories() }
                            )
                        }
                    }

                    // Resumen compacto cuando está contraído y hay selección activa
                    if (!categoriesExpanded && selectedCategories.isNotEmpty()) {
                        Text(
                            text = availableCategories.filter { selectedCategories.contains(it) }
                                .joinToString(" · "),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    AnimatedVisibility(visible = categoriesExpanded) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            availableCategories.forEach { cat ->
                                val selected = selectedCategories.contains(cat)
                                FilterChip(
                                    selected = selected,
                                    onClick = { onToggleCategory(cat) },
                                    label = { Text(cat, fontSize = 11.sp) },
                                    leadingIcon = if (selected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    } else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = selected,
                                        borderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
