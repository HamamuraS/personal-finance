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
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import com.example.data.Categorias
import com.example.data.CuotaPlan
import com.example.data.Movement
import com.example.data.UsuariosConfig
import com.example.ui.AccountingEngine
import com.example.ui.BalanceBreakdown
import com.example.ui.CuotasEngine
import com.example.ui.theme.personaColor
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    userProfile: String,
    config: UsuariosConfig,
    balance: BalanceBreakdown,
    movements: List<Movement>,
    availableMonths: List<String>,
    selectedMonth: String,
    onMonthSelected: (String) -> Unit,
    plans: List<CuotaPlan> = emptyList(),
    allMovements: List<Movement> = emptyList(),
    /**
     * Tocar una línea de categoría lleva a Inicio con el filtro ya aplicado (gastos + persona +
     * categoría). `persona` es el slotKey del dueño del gasto, o null en la tarjeta combinada.
     */
    onVerDetalle: (persona: String?, categorias: Set<String>) -> Unit = { _, _ -> }
) {
    // Total que caerá en cada tarjeta en el mes seleccionado (cuotas pagadas + impagas).
    val tarjetaTotals = CuotasEngine.totalTarjetaPorMes(selectedMonth, plans, allMovements)

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
        if (movements.isEmpty() && tarjetaTotals.isEmpty()) {
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
                        tint = com.example.ui.theme.appTextMuted
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Registra movimientos para ver reportes",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                // Tarjeta: Total en tarjetas por mes (módulo de cuotas)
                if (tarjetaTotals.isNotEmpty()) {
                    item {
                        TarjetasPorMesCard(
                            mes = selectedMonth,
                            totales = tarjetaTotals,
                            formatMoney = formatMoney
                        )
                    }
                }

                if (movements.isNotEmpty()) {
                    // Gastos "reales": sin las correcciones, que son ajustes para cuadrar con el banco.
                    val gastos = movements.filter { AccountingEngine.esGastoReal(it) }

                    // Tarjeta 1: Ingresos por socio (solo aportes de categoría Ingreso)
                    item {
                        ReportAportesCard(userProfile = userProfile, config = config, balance = balance, formatMoney = formatMoney)
                    }

                    // Tarjeta 1b: Gastos por socio, partidos en no evitable / evitable
                    item {
                        GastosAcumuladosCard(
                            userProfile = userProfile,
                            config = config,
                            primario = AccountingEngine.gastosPorEvitabilidad(movements, config.primario.slotKey),
                            secundario = AccountingEngine.gastosPorEvitabilidad(movements, config.secundario.slotKey),
                            formatMoney = formatMoney
                        )
                    }

                    // Tarjeta 2: Gastos Totales Combinados
                    item {
                        ReportCategoriasCard(
                            title = "Gastos Totales (Combinados)",
                            sumPorCategoria = sumPorCategoria(gastos) { it.monto },
                            formatMoney = formatMoney,
                            accentColor = MaterialTheme.colorScheme.primary,
                            onCategoriaClick = { onVerDetalle(null, it) }
                        )
                    }

                    // Tarjeta 3: Gastos del slot primario
                    item {
                        ReportCategoriasCard(
                            title = "Gastos de ${config.primario.nombre}",
                            sumPorCategoria = sumPorCategoria(gastos) {
                                AccountingEngine.porcionDelGasto(it, config.primario.slotKey)
                            },
                            formatMoney = formatMoney,
                            accentColor = personaColor(config.primario.slotKey, userProfile),
                            onCategoriaClick = { onVerDetalle(config.primario.slotKey, it) }
                        )
                    }

                    // Tarjeta 4: Gastos del slot secundario
                    item {
                        ReportCategoriasCard(
                            title = "Gastos de ${config.secundario.nombre}",
                            sumPorCategoria = sumPorCategoria(gastos) {
                                AccountingEngine.porcionDelGasto(it, config.secundario.slotKey)
                            },
                            formatMoney = formatMoney,
                            accentColor = personaColor(config.secundario.slotKey, userProfile),
                            onCategoriaClick = { onVerDetalle(config.secundario.slotKey, it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReportAportesCard(userProfile: String, config: UsuariosConfig, balance: BalanceBreakdown, formatMoney: NumberFormat) {
    val cardBg = MaterialTheme.colorScheme.surface
    val cardBorder = MaterialTheme.colorScheme.outlineVariant

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
                text = "Ingresos Acumulados",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Solo la categoría Ingreso: los demás aportes (transferencias entre ustedes,
            // correcciones) no son plata nueva.
            val totalAportes = balance.santiagoIngresos + balance.rocioIngresos
            val sSantiagoPct = if (totalAportes > 0) (balance.santiagoIngresos / totalAportes).toFloat() else 0.5f
            val sRocioPct = 1f - sSantiagoPct

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Colores de identidad estables (invariantes al usuario activo).
                val santiagoColor = personaColor(config.primario.slotKey, userProfile)
                val rocioColor = personaColor(config.secundario.slotKey, userProfile)

                // Fila del slot primario
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(config.primario.nombre, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(formatMoney.format(balance.santiagoIngresos), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = santiagoColor)
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

                // Fila del slot secundario
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(config.secundario.nombre, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(formatMoney.format(balance.rocioIngresos), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = rocioColor)
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
                    Text("Total Ingresado", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatMoney.format(totalAportes), fontSize = 16.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

/**
 * Gastos acumulados del periodo por persona. Cada barra mide la parte de esa persona sobre el gasto
 * de los dos (igual que "Ingresos Acumulados") y se parte en dos tramos: el color de la persona es lo
 * **no evitable** y el rojo lo **evitable**, con el monto de cada parcial debajo.
 */
@Composable
fun GastosAcumuladosCard(
    userProfile: String,
    config: UsuariosConfig,
    primario: AccountingEngine.GastosPorEvitabilidad,
    secundario: AccountingEngine.GastosPorEvitabilidad,
    formatMoney: NumberFormat
) {
    val cardBg = MaterialTheme.colorScheme.surface
    val cardBorder = MaterialTheme.colorScheme.outlineVariant
    val total = primario.total + secundario.total

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
                text = "Gastos Acumulados",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                GastoPersonaFila(
                    nombre = config.primario.nombre,
                    gastos = primario,
                    total = total,
                    color = personaColor(config.primario.slotKey, userProfile),
                    formatMoney = formatMoney
                )
                GastoPersonaFila(
                    nombre = config.secundario.nombre,
                    gastos = secundario,
                    total = total,
                    color = personaColor(config.secundario.slotKey, userProfile),
                    formatMoney = formatMoney
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Total Gastado", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatMoney.format(total), fontSize = 16.sp, fontWeight = FontWeight.Black)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("🍰 Evitable", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        formatMoney.format(primario.evitable + secundario.evitable),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/** Una fila de [GastosAcumuladosCard]: nombre + total, barra en dos tramos y los dos parciales. */
@Composable
private fun GastoPersonaFila(
    nombre: String,
    gastos: AccountingEngine.GastosPorEvitabilidad,
    total: Double,
    color: Color,
    formatMoney: NumberFormat
) {
    val rojo = MaterialTheme.colorScheme.error
    // Fracción de la barra entera (ancho de la card) que ocupa cada tramo.
    val fNecesario = if (total > 0) (maxOf(0.0, gastos.necesario) / total).toFloat().coerceIn(0f, 1f) else 0f
    val fEvitable = if (total > 0) (maxOf(0.0, gastos.evitable) / total).toFloat().coerceIn(0f, 1f - fNecesario) else 0f
    val fResto = (1f - fNecesario - fEvitable).coerceAtLeast(0f)

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(nombre, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(formatMoney.format(gastos.total), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
        ) {
            if (fNecesario > 0f) Box(Modifier.fillMaxHeight().weight(fNecesario).background(color))
            if (fEvitable > 0f) Box(Modifier.fillMaxHeight().weight(fEvitable).background(rojo))
            if (fResto > 0f) Spacer(Modifier.fillMaxHeight().weight(fResto))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "🍞 Necesario ${formatMoney.format(gastos.necesario)}",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
            Text(
                "🍰 Evitable ${formatMoney.format(gastos.evitable)}",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = rojo
            )
        }
    }
}

/**
 * Suma los gastos por categoría aplicando [porcion] a cada movimiento, que es lo que decide **cuánto
 * de ese gasto** entra en esta tarjeta (todo, la mitad si es común, o nada si es de la otra persona).
 * Descarta solo las categorías que quedan exactamente en cero (la persona no participó del gasto):
 * un total negativo —un reembolso cargado como gasto— se sigue listando, como antes.
 */
private fun sumPorCategoria(
    gastos: List<Movement>,
    porcion: (Movement) -> Double
): List<Pair<String, Double>> =
    gastos.groupBy { it.categoria }
        .mapValues { (_, list) -> list.sumOf(porcion) }
        .filterValues { it != 0.0 }
        .toList()
        .sortedByDescending { it.second }

/**
 * Gastos de una tarjeta de Métricas agrupados por rubro (🍽 Comida, 🏠 Casa…): 7 filas en vez de
 * 15. Tocar un rubro de una sola categoría va directo a Inicio filtrado; uno de varias despliega sus
 * categorías, cada una con su salto a Inicio, más "Ver todo el rubro".
 */
@Composable
fun ReportCategoriasCard(
    title: String,
    sumPorCategoria: List<Pair<String, Double>>,
    formatMoney: NumberFormat,
    accentColor: Color,
    onCategoriaClick: (Set<String>) -> Unit = {}
) {
    val cardBg = MaterialTheme.colorScheme.surface
    val cardBorder = MaterialTheme.colorScheme.outlineVariant
    val rubros = remember(sumPorCategoria) { Categorias.agruparPorRubro(sumPorCategoria) }
    var abiertos by remember { mutableStateOf(setOf<String>()) }

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
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Los montos ya vienen atribuidos por el llamador (ver [sumPorCategoria]).
            val totalGastos = sumPorCategoria.sumOf { it.second }

            if (totalGastos == 0.0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val label = "No hay gastos registrados todavía"
                    Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                // Se muestran TODOS los rubros con gasto (antes se truncaba a las primeras 6
                // categorías, lo que podía esconder montos menores y dar la falsa impresión de que
                // ciertos gastos —p.ej. en efectivo— no se estaban sumando).
                rubros.forEach { grupo ->
                    val percentage = (grupo.total / totalGastos).toFloat()
                    val unica = grupo.categorias.singleOrNull()?.first
                    val abierto = grupo.rubro.nombre in abiertos
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (unica != null) onCategoriaClick(setOf(unica))
                                else abiertos = if (abierto) abiertos - grupo.rubro.nombre else abiertos + grupo.rubro.nombre
                            }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Text(grupo.rubro.emoji, fontSize = 13.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = grupo.rubro.nombre,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (unica == null) {
                                    Icon(
                                        imageVector = if (abierto) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = if (abierto) "Contraer" else "Ver categorías",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${(percentage * 100).toInt()}%",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = formatMoney.format(grupo.total),
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
                            color = accentColor,
                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                        )
                    }

                    // Detalle del rubro: sus categorías (tal como están en la planilla) y el salto
                    // a Inicio con todo el rubro.
                    if (abierto && unica == null) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(start = 22.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            grupo.categorias.forEach { (cat, monto) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable { onCategoriaClick(setOf(cat)) },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(cat, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text(formatMoney.format(monto), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Text(
                                text = "Ver todo ${grupo.rubro.etiqueta} en Inicio",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = accentColor,
                                modifier = Modifier.clickable {
                                    onCategoriaClick(grupo.categorias.map { it.first }.toSet())
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Total que caerá en cada tarjeta en el mes seleccionado, agrupado por tarjeta (módulo de cuotas).
 * Suma tanto las cuotas ya pagadas como las impagas que vencen ese mes.
 */
@Composable
fun TarjetasPorMesCard(
    mes: String,
    totales: Map<String, Double>,
    formatMoney: NumberFormat
) {
    val cardBg = MaterialTheme.colorScheme.surface
    val cardBorder = MaterialTheme.colorScheme.outlineVariant
    val accent = MaterialTheme.colorScheme.tertiary
    val total = totales.values.sum()
    val ordenadas = totales.entries.sortedByDescending { it.value }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CreditCard, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                }
                Column {
                    Text("Total en tarjetas", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(formatMonthLabel(mes), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            ordenadas.forEach { (tarjeta, monto) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(accent))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(tarjeta, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(formatMoney.format(monto), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Total del mes", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                Text(formatMoney.format(total), fontSize = 16.sp, fontWeight = FontWeight.Black, color = accent)
            }
        }
    }
}

// Eliminar ReportHistoricoMensualCard ya que no se usa y se solicitó remover el gráfico
