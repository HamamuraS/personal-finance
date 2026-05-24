package com.example.ui.components

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
    availableMonths: List<String>,
    selectedMonth: String,
    isCurrentMonth: Boolean,
    onMonthSelected: (String) -> Unit
) {
    var selectedFilter by remember { mutableStateOf("Todos") }
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                    val isUserRocio = userProfile == "Rocío"
                    
                    DesgloseSocioCard(
                        modifier = Modifier.weight(1f),
                        nombre = if (isUserRocio) "Rocío" else "Santiago",
                        saldo = if (isUserRocio) balance.rocioSaldoFinal else balance.santiagoSaldoFinal,
                        aportes = if (isUserRocio) balance.rocioAportes else balance.santiagoAportes,
                        personales = if (isUserRocio) balance.rocioGastosPersonales else balance.santiagoGastosPersonales,
                        comunes = if (isUserRocio) balance.rocioGastosComunes else balance.santiagoGastosComunes,
                        formatMoney = formatMoney,
                        avatarColor = MaterialTheme.colorScheme.primary
                    )
                    DesgloseSocioCard(
                        modifier = Modifier.weight(1f),
                        nombre = if (isUserRocio) "Santiago" else "Rocío",
                        saldo = if (isUserRocio) balance.santiagoSaldoFinal else balance.rocioSaldoFinal,
                        aportes = if (isUserRocio) balance.santiagoAportes else balance.rocioAportes,
                        personales = if (isUserRocio) balance.santiagoGastosPersonales else balance.rocioGastosPersonales,
                        comunes = if (isUserRocio) balance.santiagoGastosComunes else balance.rocioGastosComunes,
                        formatMoney = formatMoney,
                        avatarColor = MaterialTheme.colorScheme.tertiary
                    )
                }
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

            // Chips Filtros de Movimientos
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Todos", "Aportes", "Gastos", "Transfer.").forEach { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selectedFilter == filter,
                                borderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)
                            )
                        )
                    }
                }
            }

            // Lista de movimientos filtrada
            val filteredMovements = movements.filter {
                when (selectedFilter) {
                    "Aportes" -> it.tipo.lowercase() == "aporte"
                    "Gastos" -> it.tipo.lowercase() == "gasto"
                    "Transfer." -> it.tipo.lowercase() == "transferencia"
                    else -> true
                }
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
                    MovementItem(
                        movement = mov,
                        formatMoney = formatMoney,
                        useLocalDemo = useLocalDemo,
                        isCurrentMonth = isCurrentMonth,
                        currentUserProfile = userProfile,
                        onDelete = { onDeleteMovement(mov) }
                    )
                }
            }
        }
    }
}

@Composable
fun PozoComunCard(userProfile: String, balance: BalanceBreakdown, formatMoney: NumberFormat) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
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
            val isUserRocio = userProfile == "Rocío"
            val santiagoRatioUnsafe = if (balance.totalPozo > 0) (balance.santiagoSaldoFinal / balance.totalPozo).coerceIn(0.0, 1.0) else 0.5
            
            // Si Rocío es principal (iziq), el azul está a la izq y santiago a la derecha
            val mainRatio = if (isUserRocio) (1.0 - santiagoRatioUnsafe) else santiagoRatioUnsafe
            val secondaryRatio = 1.0 - mainRatio

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color(0xFF5D625C) else Color(0xFFC2CDC1))
            ) {
                // Barra principal (Loggeado)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(if (mainRatio > 0) mainRatio.toFloat() else 0.001f)
                        .background(MaterialTheme.colorScheme.primary)
                )
                // Barra secundaria (Otro)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(if (secondaryRatio > 0) secondaryRatio.toFloat() else 0.001f)
                        .background(MaterialTheme.colorScheme.tertiary)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isUserRocio) "Rocío" else "Santiago", fontSize = 12.sp, color = labelColor, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        text = "${formatMoney.format(if (isUserRocio) balance.rocioSaldoFinal else balance.santiagoSaldoFinal)} (${(mainRatio * 100).toInt()}%)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = textMainColor
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isUserRocio) "Santiago" else "Rocío", fontSize = 12.sp, color = labelColor, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary))
                    }
                    Text(
                        text = "${formatMoney.format(if (isUserRocio) balance.santiagoSaldoFinal else balance.rocioSaldoFinal)} (${(secondaryRatio * 100).toInt()}%)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = textMainColor
                    )
                }
            }
        }
    }
}

@Composable
fun DesgloseSocioCard(
    modifier: Modifier,
    nombre: String,
    saldo: Double,
    aportes: Double,
    personales: Double,
    comunes: Double,
    formatMoney: NumberFormat,
    avatarColor: Color
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
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

            // Balance
            Column {
                Text(
                    text = "Saldo Hoy",
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

            // G. Comunes (-)
            Column {
                Text(
                    text = "Comunes (-)",
                    fontSize = 10.sp,
                    color = textMainColor.copy(alpha = 0.5f)
                )
                Text(
                    text = formatMoney.format(comunes),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFD32F2F).copy(alpha = 0.8f),
                    maxLines = 1
                )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MovementItem(
    movement: Movement,
    formatMoney: NumberFormat,
    useLocalDemo: Boolean,
    isCurrentMonth: Boolean,
    currentUserProfile: String,
    onDelete: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("¿Eliminar Movimiento?") },
            text = { Text("¿Desea borrar permanentemente este movimiento de la base local?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDialog = false
                }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val cardBg = if (isDark) MaterialTheme.colorScheme.surface else Color.White
    val cardBorder = if (isDark) Color(0xFF333833) else Color(0xFFF1F5F9)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onLongClick = {
                    if (useLocalDemo && isCurrentMonth) {
                        showDialog = true
                    }
                },
                onClick = {}
            ),
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
            val isOwnAporte = isAporte && movement.responsable.equals(currentUserProfile, ignoreCase = true)
            val aporteColor = if (isOwnAporte) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
            
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
                    val subtitulo = if (movement.descripcion.isNotEmpty()) {
                        "$responsableTag • ${movement.descripcion}"
                    } else {
                        responsableTag
                    }
                    Text(
                        text = subtitulo,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1.5f)
                    )

                    // Tag Compartido vs Personal
                    if (isGasto) {
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
                    } else if (movement.tipo.lowercase() == "transferencia") {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Transf.",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
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
