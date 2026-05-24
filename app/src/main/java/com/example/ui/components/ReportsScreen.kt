package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
fun ReportsScreen(
    userProfile: String,
    balance: BalanceBreakdown,
    movements: List<Movement>,
    availableMonths: List<String>,
    selectedMonth: String,
    onMonthSelected: (String) -> Unit
) {
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
                                    text = if (selectedMonth.isNotEmpty()) "Reportes - $selectedMonth" else "Métricas y Reportes",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        if (movements.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Registra movimientos para ver reportes",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Tarjeta 1: Aportes por socio (Santiago vs Rocío)
                item {
                    ReportAportesCard(userProfile = userProfile, balance = balance, formatMoney = formatMoney)
                }

                // Tarjeta 2: Gastos por Categoría
                item {
                    ReportCategoriasCard(movements = movements, formatMoney = formatMoney)
                }

                // Tarjeta 3: Distribución Histórica Mensual
                item {
                    ReportHistoricoMensualCard(movements = movements, formatMoney = formatMoney)
                }
            }
        }
    }
}

@Composable
fun ReportAportesCard(userProfile: String, balance: BalanceBreakdown, formatMoney: NumberFormat) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val cardBg = if (isDark) MaterialTheme.colorScheme.surface else Color.White
    val cardBorder = if (isDark) Color(0xFF333833) else Color(0xFFE2E8F0)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Aportes Acumulados",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            val totalAportes = balance.santiagoAportes + balance.rocioAportes
            val sSantiagoPct = if (totalAportes > 0) (balance.santiagoAportes / totalAportes).toFloat() else 0.5f
            val sRocioPct = 1f - sSantiagoPct

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val isUserRocio = userProfile == "Rocío"
                val santiagoColor = if (isUserRocio) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                val rocioColor    = if (isUserRocio) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary

                // Fila Santiago
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Santiago", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(formatMoney.format(balance.santiagoAportes), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = santiagoColor)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { sSantiagoPct },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = santiagoColor,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                    )
                }

                // Fila Rocío
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Rocío", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(formatMoney.format(balance.rocioAportes), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = rocioColor)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { sRocioPct },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = rocioColor,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Total Aportado", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    Text(formatMoney.format(totalAportes), fontSize = 16.sp, fontWeight = FontWeight.Black)
                }

                Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1.5f)) {
                    Text("Diferencia de Aportes", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    val diff = balance.santiagoAportes - balance.rocioAportes
                    val diffAbs = kotlin.math.abs(diff)
                    val pagador = if (diff > 0) "Santiago aportó más" else if (diff < 0) "Rocío aportó más" else ""
                    val isUserRocio = userProfile == "Rocío"
                    val diffColor = if (diff > 0) {
                        if (isUserRocio) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                    } else if (diff < 0) {
                        if (isUserRocio) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                    } else MaterialTheme.colorScheme.onSurface

                    Text(
                        text = if (diff == 0.0) "Equitativo" else "$pagador\n(${formatMoney.format(diffAbs)})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = diffColor,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                    )
                }
            }
        }
    }
}

@Composable
fun ReportCategoriasCard(movements: List<Movement>, formatMoney: NumberFormat) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val cardBg = if (isDark) MaterialTheme.colorScheme.surface else Color.White
    val cardBorder = if (isDark) Color(0xFF333833) else Color(0xFFE2E8F0)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Gastos Totales por Categoría",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Filtrar gastos, agruparlos y ordenarlos
            val gastos = movements.filter { it.tipo.lowercase() == "gasto" }
            val sumPorCategoria = gastos.groupBy { it.categoria }
                .mapValues { (_, list) -> list.sumOf { it.monto } }
                .toList()
                .sortedByDescending { it.second }

            val totalGastos = sumPorCategoria.sumOf { it.second }

            if (totalGastos == 0.0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val label = "No hay gastos registrados todavía"
                    Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            } else {
                sumPorCategoria.take(6).forEach { (catName, amount) ->
                    val percentage = (amount / totalGastos).toFloat()
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = if (percentage > 0.4) 1f else 0.5f))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = catName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${(percentage * 100).toInt()}%",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = formatMoney.format(amount),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { percentage },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReportHistoricoMensualCard(movements: List<Movement>, formatMoney: NumberFormat) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val cardBg = if (isDark) MaterialTheme.colorScheme.surface else Color.White
    val cardBorder = if (isDark) Color(0xFF333833) else Color(0xFFE2E8F0)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Evolución Mensual (Aportes vs Gastos)",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Agrupar por Mes (YYYY-MM de las fechas)
            val formatMes = { f: String -> if (f.length >= 7) f.substring(0, 7) else "Otro" }
            
            val aportesMensuales = movements.filter { it.tipo.lowercase() == "aporte" }
                .groupBy { formatMes(it.fecha) }
                .mapValues { (_, list) -> list.sumOf { it.monto } }

            val gastosMensuales = movements.filter { it.tipo.lowercase() == "gasto" }
                .groupBy { formatMes(it.fecha) }
                .mapValues { (_, list) -> list.sumOf { it.monto } }

            // Unir todos los meses existentes
            val todosMeses = (aportesMensuales.keys + gastosMensuales.keys).sorted().takeLast(4)

            if (todosMeses.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Sin datos históricos suficientes", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    val maxMonto = listOf(
                        aportesMensuales.values.maxOrNull() ?: 1.0,
                        gastosMensuales.values.maxOrNull() ?: 1.0
                    ).maxOrNull() ?: 1.0

                    todosMeses.forEach { mes ->
                        val aporteVal = aportesMensuales[mes] ?: 0.0
                        val gastoVal = gastosMensuales[mes] ?: 0.0

                        val hAporte = (aporteVal / maxMonto).toFloat().coerceIn(0.02f, 1f)
                        val hGasto = (gastoVal / maxMonto).toFloat().coerceIn(0.02f, 1f)

                        // Columna del mes
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            // Barras
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Aporte verde
                                Box(
                                    modifier = Modifier
                                        .width(14.dp)
                                        .fillMaxHeight(hAporte)
                                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                                // Gasto rojo/rosa
                                Box(
                                    modifier = Modifier
                                        .width(14.dp)
                                        .fillMaxHeight(hGasto)
                                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Traducir YYYY-MM a "Ene", "Feb", etc.
                            val mesSimplificado = when (mes.takeLast(2)) {
                                "01" -> "Ene"
                                "02" -> "Feb"
                                "03" -> "Mar"
                                "04" -> "Abr"
                                "05" -> "May"
                                "06" -> "Jun"
                                "07" -> "Jul"
                                "08" -> "Ago"
                                "09" -> "Sep"
                                "10" -> "Oct"
                                "11" -> "Nov"
                                "12" -> "Dic"
                                else -> mes
                            }
                            Text(
                                text = mesSimplificado,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Leyenda del Gráfico
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Aportes", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.width(16.dp))
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error.copy(alpha = 0.8f)))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Gastos", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
        }
    }
}
