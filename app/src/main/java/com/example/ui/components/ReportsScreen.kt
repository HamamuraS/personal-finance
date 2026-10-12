package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Categorias
import com.example.data.CuotaPlan
import com.example.data.Movement
import com.example.data.UsuariosConfig
import com.example.domain.AccountingEngine
import com.example.domain.BalanceBreakdown
import com.example.domain.CuotasEngine
import com.example.ui.components.common.AppCard
import com.example.ui.components.common.IconoEnCirculo
import com.example.ui.components.common.rememberDinero
import com.example.ui.theme.appTextMuted
import com.example.ui.theme.personaColor
import com.example.util.Fechas
import java.text.NumberFormat

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
    val tarjetaTotals = remember(selectedMonth, plans, allMovements) {
        CuotasEngine.totalTarjetaPorMes(selectedMonth, plans, allMovements)
    }

    val formatMoney = rememberDinero()

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
                        tint = appTextMuted
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
            // Gastos "reales": sin las correcciones, que son ajustes para cuadrar con el banco.
            val gastos = remember(movements) { movements.filter { AccountingEngine.esGastoReal(it) } }

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
                    // Ingresos por persona (solo aportes de categoría Ingreso)
                    item {
                        ReportAportesCard(userProfile = userProfile, config = config, balance = balance, formatMoney = formatMoney)
                    }

                    // Gastos por persona, partidos en no evitable / evitable
                    item {
                        GastosAcumuladosCard(
                            userProfile = userProfile,
                            config = config,
                            porPersona = config.todos.map { it.slotKey to AccountingEngine.gastosPorEvitabilidad(movements, it.slotKey) },
                            formatMoney = formatMoney
                        )
                    }

                    // Gastos Totales Combinados
                    item {
                        ReportCategoriasCard(
                            title = "Gastos Totales (Combinados)",
                            sumPorCategoria = AccountingEngine.gastosPorCategoria(gastos) { it.monto },
                            formatMoney = formatMoney,
                            accentColor = MaterialTheme.colorScheme.primary,
                            onCategoriaClick = { onVerDetalle(null, it) }
                        )
                    }

                    // Gastos de cada persona (su parte de cada gasto)
                    config.todos.forEach { usuario ->
                        item {
                            ReportCategoriasCard(
                                title = "Gastos de ${usuario.nombre}",
                                sumPorCategoria = AccountingEngine.gastosPorCategoria(gastos) {
                                    AccountingEngine.porcionDelGasto(it, usuario.slotKey)
                                },
                                formatMoney = formatMoney,
                                accentColor = personaColor(usuario.slotKey, userProfile),
                                onCategoriaClick = { onVerDetalle(usuario.slotKey, it) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportAportesCard(userProfile: String, config: UsuariosConfig, balance: BalanceBreakdown, formatMoney: NumberFormat) {
    AppCard(radio = 24.dp, elevacion = 1.dp) {
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
            val ingresos = config.todos.map { it to balance.de(it.slotKey).ingresos }
            val totalAportes = ingresos.sumOf { it.second }
            // La parte de cada uno sobre el total (en partes iguales si no hubo ingresos). La última
            // es lo que falta para 1, así las barras siempre suman exactamente la barra entera.
            val partes = ingresos.map { (_, monto) ->
                if (totalAportes > 0) (monto / totalAportes).toFloat() else 1f / ingresos.size
            }.let { p -> p.dropLast(1) + (1f - p.dropLast(1).sum()) }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ingresos.forEachIndexed { i, (usuario, monto) ->
                    // Color de identidad estable (invariante al usuario activo).
                    val color = personaColor(usuario.slotKey, userProfile)
                    val pct = partes[i]
                    Column(modifier = if (i > 0) Modifier.padding(top = 8.dp) else Modifier) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(usuario.nombre, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(formatMoney.format(monto), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { pct },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape),
                            color = color,
                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                        )
                    }
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
 * de todos (igual que "Ingresos Acumulados") y se parte en dos tramos: el color de la persona es lo
 * **no evitable** y el rojo lo **evitable**, con el monto de cada parcial debajo.
 */
@Composable
private fun GastosAcumuladosCard(
    userProfile: String,
    config: UsuariosConfig,
    porPersona: List<Pair<String, AccountingEngine.GastosPorEvitabilidad>>,
    formatMoney: NumberFormat
) {
    val total = porPersona.sumOf { it.second.total }

    AppCard(radio = 24.dp, elevacion = 1.dp) {
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
                porPersona.forEach { (slotKey, gastos) ->
                    GastoPersonaFila(
                        nombre = config.nombreDe(slotKey),
                        gastos = gastos,
                        total = total,
                        color = personaColor(slotKey, userProfile),
                        formatMoney = formatMoney
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
                    Text("Total Gastado", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatMoney.format(total), fontSize = 16.sp, fontWeight = FontWeight.Black)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("🍰 Evitable", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        formatMoney.format(porPersona.sumOf { it.second.evitable }),
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
 * Gastos de una tarjeta de Métricas agrupados por rubro (🍽 Comida, 🏠 Casa…): 7 filas en vez de
 * 15. Tocar un rubro de una sola categoría va directo a Inicio filtrado; uno de varias despliega sus
 * categorías, cada una con su salto a Inicio, más "Ver todo el rubro".
 */
@Composable
private fun ReportCategoriasCard(
    title: String,
    sumPorCategoria: List<Pair<String, Double>>,
    formatMoney: NumberFormat,
    accentColor: Color,
    onCategoriaClick: (Set<String>) -> Unit = {}
) {
    val rubros = remember(sumPorCategoria) { Categorias.agruparPorRubro(sumPorCategoria) }
    var abiertos by remember { mutableStateOf(setOf<String>()) }

    AppCard(radio = 24.dp, elevacion = 1.dp) {
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

            // Los montos ya vienen atribuidos por el llamador (ver [AccountingEngine.gastosPorCategoria]).
            val totalGastos = sumPorCategoria.sumOf { it.second }

            if (totalGastos == 0.0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No hay gastos registrados todavía", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun TarjetasPorMesCard(
    mes: String,
    totales: Map<String, Double>,
    formatMoney: NumberFormat
) {
    val accent = MaterialTheme.colorScheme.tertiary
    val total = totales.values.sum()
    val ordenadas = totales.entries.sortedByDescending { it.value }

    AppCard(radio = 24.dp, elevacion = 1.dp) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconoEnCirculo(Icons.Default.CreditCard, accent, tamano = 32.dp, alphaFondo = 0.12f, tamanoIcono = 18.dp)
                Column {
                    Text("Total en tarjetas", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(Fechas.etiquetaDeMes(mes), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
