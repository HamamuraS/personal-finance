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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import com.example.data.Movement
import com.example.ui.BalanceBreakdown
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    userProfile: String,
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
    onMonthSelected: (String) -> Unit
) {
    // Filtros: persona (responsable) + tipo + categorías (multi-selección)
    var filterPerson by remember { mutableStateOf("Todos") }   // Todos | Santiago | Rocío
    var filterTipo by remember { mutableStateOf("Todos") }     // Todos | Gastos | Aportes | Transfer.
    var selectedCategories by remember { mutableStateOf<Set<String>>(emptySet()) }
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
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Tarjeta de Pozo Común
                item {
                    PozoComunCard(userProfile = userProfile, balance = balance, formatMoney = formatMoney)
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
                        DesgloseSocioCard(
                            modifier = Modifier.weight(1f),
                            nombre = "Santiago",
                            saldo = balance.santiagoSaldoFinal,
                            enMano = balance.santiagoEnMano,
                            aportes = balance.santiagoAportes,
                            personales = balance.santiagoGastosPersonales,
                            formatMoney = formatMoney,
                            avatarColor = MaterialTheme.colorScheme.primary
                        )
                        DesgloseSocioCard(
                            modifier = Modifier.weight(1f),
                            nombre = "Rocío",
                            saldo = balance.rocioSaldoFinal,
                            enMano = balance.rocioEnMano,
                            aportes = balance.rocioAportes,
                            personales = balance.rocioGastosPersonales,
                            formatMoney = formatMoney,
                            avatarColor = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                // Gasto común total (una sola vez; antes se repetía simétrico en rojo en cada tarjeta)
                item {
                    GastosComunesCard(total = balance.gastosComunesTotales, formatMoney = formatMoney)
                }

                // Relación de propiedad cruzada (una sola vez, no redundante por tarjeta)
                item {
                    DineroCruzadoCard(externoSantiago = balance.santiagoExterno, formatMoney = formatMoney)
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

                // Filtrado: persona (responsable) -> tipo -> categorías (multi)
                val baseFiltered = movements.filter { m ->
                    (filterPerson == "Todos" || m.responsable.equals(filterPerson, ignoreCase = true)) &&
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
                        onPersonChange = { filterPerson = it },
                        tipo = filterTipo,
                        onTipoChange = { filterTipo = it },
                        availableCategories = availableCategories,
                        selectedCategories = effectiveCategories,
                        onToggleCategory = { cat ->
                            selectedCategories = if (selectedCategories.contains(cat)) {
                                selectedCategories - cat
                            } else {
                                selectedCategories + cat
                            }
                        },
                        onClearCategories = { selectedCategories = emptySet() }
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
                            onDelete = { onDeleteMovement(mov) },
                            onDuplicate = { onDuplicateMovement(mov) }
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
    onDelete: () -> Unit,
    onDuplicate: () -> Unit
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
            onDelete = onDelete,
            onDuplicate = onDuplicate
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
    onDelete: () -> Unit,
    onDuplicate: () -> Unit
) {
    var showDuplicateDialog by remember { mutableStateOf(false) }

    if (showDuplicateDialog) {
        AlertDialog(
            onDismissRequest = { showDuplicateDialog = false },
            title = { Text("¿Duplicar Movimiento?") },
            text = { Text("Se creará una copia de este gasto con la fecha de hoy.") },
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
            .fillMaxWidth(),
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
            val isSantiagoAporte = isAporte && movement.responsable.equals("Santiago", ignoreCase = true)
            val isRocioAporte = isAporte && movement.responsable.equals("Rocío", ignoreCase = true)
            
            val aporteColor = when {
                isSantiagoAporte -> MaterialTheme.colorScheme.primary // Santiago siempre verde
                isRocioAporte -> MaterialTheme.colorScheme.tertiary // Rocío siempre azul
                else -> MaterialTheme.colorScheme.tertiary // Fallback para transferencias u otros
            }
            
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
                    val responsableTag = movement.responsable
                    val metodoTag = if (movement.metodoPago.contains("Efectivo", ignoreCase = true)) "💵" else "💳"
                    val subtitulo = if (movement.descripcion.isNotEmpty()) {
                        "$metodoTag $responsableTag • ${movement.descripcion}"
                    } else {
                        "$metodoTag $responsableTag"
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
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "De ${movement.propietario}",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
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
                         Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "A ${movement.responsable}",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
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

@Composable
fun PozoComunCard(userProfile: String, balance: BalanceBreakdown, formatMoney: NumberFormat) {
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
            
            // Santiago siempre a la izquierda (primary/verde), Rocío a la derecha (tertiary/azul)
            val santiagoRatio = santiagoRatioUnsafe
            val rocioRatio = 1.0 - santiagoRatio

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color(0xFF5D625C) else Color(0xFFC2CDC1))
            ) {
                // Barra Santiago (Verde)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(if (santiagoRatio > 0) santiagoRatio.toFloat() else 0.001f)
                        .background(MaterialTheme.colorScheme.primary)
                )
                // Barra Rocío (Azul)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(if (rocioRatio > 0) rocioRatio.toFloat() else 0.001f)
                        .background(MaterialTheme.colorScheme.tertiary)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Columna 1 (Santiago - Verde)
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Santiago", fontSize = 12.sp, color = labelColor, fontWeight = FontWeight.SemiBold)
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
                    
                    // Desglose
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
                }

                // Columna 2 (Rocío - Azul)
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Rocío", fontSize = 12.sp, color = labelColor, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary))
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

                    // Desglose
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
fun DineroCruzadoCard(externoSantiago: Double, formatMoney: NumberFormat) {
    // Umbral para ignorar redondeos de centavos
    if (kotlin.math.abs(externoSantiago) < 1.0) return

    val isDark = MaterialTheme.colorScheme.background == com.example.ui.theme.DarkBackground
    val cardBg = if (isDark) MaterialTheme.colorScheme.surface else Color.White
    val cardBorder = if (isDark) Color(0xFF333833) else Color(0xFFE2E8F0)
    val textMainColor = if (isDark) Color.White else Color(0xFF191C19)

    // Quién tiene plata en la cuenta de quién
    val dueno = if (externoSantiago > 0) "Santiago" else "Rocío"
    val cuentaDe = if (externoSantiago > 0) "Rocío" else "Santiago"
    val monto = kotlin.math.abs(externoSantiago)
    val acento = if (externoSantiago > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary

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

            // Balance Total
            Column {
                Text(
                    text = "Saldo Total",
                    fontSize = 11.sp,
                    color = textMainColor.copy(alpha = 0.5f)
                )
                Text(
                    text = formatMoney.format(saldo),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = textMainColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Saldo físico en sus propias cuentas (efectivo + virtual)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "En sus cuentas",
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
                    color = Color(0xFF386B3F),
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
                    listOf("Todos", "Santiago", "Rocío").forEach { p ->
                        val selected = person == p
                        val accent = when (p) {
                            "Santiago" -> MaterialTheme.colorScheme.primary
                            "Rocío" -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (selected) accent.copy(alpha = 0.15f) else Color.Transparent)
                                .clickable { onPersonChange(p) }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = p,
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
