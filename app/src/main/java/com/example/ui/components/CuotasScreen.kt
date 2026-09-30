package com.example.ui.components

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CuotaPlan
import com.example.data.Categorias
import com.example.data.Movement
import com.example.data.UsuariosConfig
import com.example.ui.AhorroViewModel
import com.example.ui.CuotaDraft
import com.example.ui.CuotaProgramada
import com.example.ui.CuotasEngine
import com.example.ui.theme.personaColor
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

// Categorías del gasto que generará cada cuota. Mismo set que los gastos del alta de movimientos:
// ambos leen el catálogo compartido [Categorias], así que no se pueden desincronizar.
private val CATEGORIAS_CUOTAS = Categorias.CUOTAS

// Tarjetas sugeridas (selección por chip). Se derivan de los nombres actuales de los usuarios:
// con la config por defecto dan "Visa Santiago"/"BBVA Rocío"/"Ualá Rocío" (idénticas a antes), y si
// se renombra un usuario, la sugerencia lo acompaña. Los planes ya guardados conservan su `tarjeta`.
private fun tarjetasSugeridas(config: UsuariosConfig): List<String> = listOf(
    "Visa ${config.primario.nombre}",
    "BBVA ${config.secundario.nombre}",
    "Ualá ${config.secundario.nombre}"
)

/** Formato de dinero consistente con el resto de la app ($ 1.234,56). */
private fun cuotasMoneyFormat(): java.text.DecimalFormat =
    java.text.DecimalFormat("#,##0.00").apply {
        val symbols = java.text.DecimalFormatSymbols()
        symbols.groupingSeparator = '.'
        symbols.decimalSeparator = ','
        decimalFormatSymbols = symbols
        positivePrefix = "$ "
        negativePrefix = "-$ "
    }

/** "2026-08" -> "Agosto 2026". Si no parsea, devuelve la entrada. */
fun formatMonthLabel(yyyyMM: String): String {
    val meses = listOf(
        "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    )
    val parts = yyyyMM.split("-")
    val y = parts.getOrNull(0) ?: return yyyyMM
    val m = parts.getOrNull(1)?.toIntOrNull() ?: return yyyyMM
    if (m !in 1..12) return yyyyMM
    return "${meses[m - 1]} $y"
}

private fun currentYyyyMm(): String =
    java.text.SimpleDateFormat("yyyy-MM", Locale.US).format(java.util.Date())

private sealed interface CuotasRoute {
    data object List : CuotasRoute
    data class Detail(val planId: String) : CuotasRoute
    data class Form(val planId: String?) : CuotasRoute
    data class TarjetaResumen(val tarjeta: String) : CuotasRoute
}

@Composable
fun CuotasScreen(viewModel: AhorroViewModel) {
    val plans by viewModel.plans.collectAsState()
    val paidPlans by viewModel.paidPlans.collectAsState()
    val allMovements by viewModel.allMovements.collectAsState()
    val currentUser by viewModel.currentUserProfile.collectAsState()
    val config by viewModel.usuarios.collectAsState()
    val cuotaDraft by viewModel.cuotaDraft.collectAsState()
    val showPaid by viewModel.showPaidPlans.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingPaid by viewModel.isLoadingPaidPlans.collectAsState()

    // Si hay un borrador de "nueva compra" en curso, al volver a la pestaña se reabre el formulario
    // (conservación del borrador entre pestañas). Si no, arranca en el listado.
    var route by remember {
        mutableStateOf<CuotasRoute>(if (cuotaDraft.tieneContenido) CuotasRoute.Form(null) else CuotasRoute.List)
    }

    when (val r = route) {
        is CuotasRoute.List -> CuotasListContent(
            plans = plans,
            paidPlans = paidPlans,
            allMovements = allMovements,
            currentUser = currentUser,
            config = config,
            showPaid = showPaid,
            isLoading = isLoading,
            isLoadingPaid = isLoadingPaid,
            onRefresh = { viewModel.refreshData() },
            onToggleShowPaid = { viewModel.setShowPaidPlans(it) },
            onOpenPlan = { route = CuotasRoute.Detail(it.id) },
            onNewPlan = { route = CuotasRoute.Form(null) },
            onOpenTarjeta = { route = CuotasRoute.TarjetaResumen(it) }
        )

        is CuotasRoute.TarjetaResumen -> {
            BackHandler { route = CuotasRoute.List }
            TarjetaResumenContent(
                tarjeta = r.tarjeta,
                plans = plans,
                allMovements = allMovements,
                currentUser = currentUser,
                isLoading = isLoading,
                onBack = { route = CuotasRoute.List },
                onPagar = { cuotas, metodo ->
                    viewModel.pagarCuotas(cuotas, metodo) { route = CuotasRoute.List }
                }
            )
        }

        is CuotasRoute.Detail -> {
            val plan = (plans + paidPlans).firstOrNull { it.id == r.planId }
            if (plan == null) {
                LaunchedEffect(Unit) { route = CuotasRoute.List }
            } else {
                BackHandler { route = CuotasRoute.List }
                PlanDetailContent(
                    plan = plan,
                    allMovements = allMovements,
                    currentUser = currentUser,
                    config = config,
                    isLoading = isLoading,
                    onBack = { route = CuotasRoute.List },
                    onConfirmCuota = { numero, fecha, metodo, monto, onSuccess ->
                        viewModel.confirmarCuota(plan, numero, fecha, metodo, monto, onSuccess = onSuccess)
                    },
                    onEdit = { route = CuotasRoute.Form(plan.id) },
                    onDelete = { viewModel.deletePlan(plan) { route = CuotasRoute.List } }
                )
            }
        }

        is CuotasRoute.Form -> {
            val existing = r.planId?.let { id -> (plans + paidPlans).firstOrNull { it.id == id } }
            BackHandler { route = CuotasRoute.List }
            PlanFormContent(
                existing = existing,
                historialCategorias = allMovements
                    .filter { !it.eliminado && it.tipo.equals("Gasto", ignoreCase = true) }
                    .map { it.categoria },
                currentUser = currentUser,
                config = config,
                isLoading = isLoading,
                draft = cuotaDraft,
                onDraftChange = { viewModel.setCuotaDraft(it) },
                onClearDraft = { viewModel.clearCuotaDraft() },
                onBack = { route = CuotasRoute.List },
                onSave = { descripcion, monto, cant, primera, propietario, categoria, tarjeta, evitable ->
                    if (existing == null) {
                        viewModel.addPlan(descripcion, monto, cant, primera, propietario, categoria, tarjeta, evitable) {
                            viewModel.clearCuotaDraft()   // borrador consumido al crear
                            route = CuotasRoute.List
                        }
                    } else {
                        viewModel.updatePlan(
                            existing.copy(
                                descripcion = descripcion,
                                montoPorCuota = monto,
                                cantidadCuotas = cant,
                                fechaPrimeraCuota = primera,
                                propietario = propietario,
                                categoria = categoria,
                                tarjeta = tarjeta,
                                // No reescribe los pagos ya hechos: cada pago copió el valor al pagarse.
                                evitable = evitable
                            )
                        ) { route = CuotasRoute.List }
                    }
                }
            )
        }
    }
}

// =================================================================================================
// Listado
// =================================================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CuotasListContent(
    plans: List<CuotaPlan>,
    paidPlans: List<CuotaPlan>,
    allMovements: List<Movement>,
    currentUser: String,
    config: UsuariosConfig,
    showPaid: Boolean,
    isLoading: Boolean,
    isLoadingPaid: Boolean,
    onRefresh: () -> Unit,
    onToggleShowPaid: (Boolean) -> Unit,
    onOpenPlan: (CuotaPlan) -> Unit,
    onNewPlan: () -> Unit,
    onOpenTarjeta: (String) -> Unit
) {
    val formatMoney = remember { cuotasMoneyFormat() }
    // Filtro de owner: default = usuario activo.
    var filterOwner by remember { mutableStateOf(currentUser) } // Todos | Santiago | Rocío
    var selectedCategories by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun applyFilters(list: List<CuotaPlan>): List<CuotaPlan> {
        val byOwner = list.filter { filterOwner == "Todos" || it.propietario.equals(filterOwner, ignoreCase = true) }
        return byOwner.filter { selectedCategories.isEmpty() || selectedCategories.contains(it.categoria) }
    }

    val pendingFiltered = applyFilters(plans).sortedByDescending { it.fechaCreacion }
    val paidFiltered = applyFilters(paidPlans).sortedByDescending { it.fechaCreacion }

    // "Pagar la tarjeta": cuotas impagas del último resumen CERRADO (no del mes en curso: la tarjeta
    // cierra a fin de mes), agrupadas por tarjeta. Solo aplica a TUS planes (no se pueden pagar las
    // cuotas de la otra persona), sin importar el filtro de propietario.
    val mesAPagar = CuotasEngine.mesAPagar(currentYyyyMm())
    val misPendientes = plans.filter { it.propietario.equals(currentUser, ignoreCase = true) }
    val tarjetasAPagar = CuotasEngine.cuotasImpagasDelMes(mesAPagar, misPendientes, allMovements)
        .filter { it.first.tarjeta.isNotBlank() }
        .groupBy { it.first.tarjeta }
        .toList()
        .sortedBy { it.first }
    // Las categorías disponibles se derivan de los planes cargados (pendientes + pagados si aplica).
    val availableCategories = (plans + if (showPaid) paidPlans else emptyList())
        .map { it.categoria }.distinct().sorted()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Cuotas", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(
                            text = "Compras en cuotas y recordatorios",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
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
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewPlan,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Nueva compra", fontWeight = FontWeight.Bold) }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            // Espacio inferior extra para que el FAB "Nueva compra" no tape la última tarjeta.
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Filtros
            item {
                CuotasFilters(
                    owner = filterOwner,
                    currentUser = currentUser,
                    config = config,
                    onOwnerChange = { filterOwner = it },
                    availableCategories = availableCategories,
                    selectedCategories = selectedCategories intersect availableCategories.toSet(),
                    onToggleCategory = { cat ->
                        selectedCategories = if (selectedCategories.contains(cat)) selectedCategories - cat
                        else selectedCategories + cat
                    },
                    onClearCategories = { selectedCategories = emptySet() },
                    showPaid = showPaid,
                    onToggleShowPaid = onToggleShowPaid
                )
            }

            // Sugerencias "Pagar la tarjeta" (una por tarjeta con cuotas impagas ya cerradas)
            items(tarjetasAPagar, key = { "pay-${it.first}" }) { (tarjeta, cuotas) ->
                PagarTarjetaCard(
                    tarjeta = tarjeta,
                    mes = mesAPagar,
                    cantidad = cuotas.size,
                    total = cuotas.sumOf { it.second.monto },
                    formatMoney = formatMoney,
                    onClick = { onOpenTarjeta(tarjeta) }
                )
            }

            // Pendientes
            if (pendingFiltered.isEmpty()) {
                item { EmptyPlansHint(showPaid = showPaid, hasPaid = paidFiltered.isNotEmpty()) }
            } else {
                items(pendingFiltered, key = { it.id }) { plan ->
                    PlanCard(
                        plan = plan,
                        cronograma = CuotasEngine.cronograma(plan, allMovements),
                        formatMoney = formatMoney,
                        currentUser = currentUser,
                        config = config,
                        attenuated = false,
                        onClick = { onOpenPlan(plan) }
                    )
                }
            }

            // Sección de pagados (separada y visualmente distinta)
            if (showPaid) {
                item {
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = com.example.ui.theme.appTextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Pagados",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                when {
                    isLoadingPaid -> item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    paidFiltered.isEmpty() -> item {
                        Text(
                            text = "No hay planes pagados para este filtro",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    else -> items(paidFiltered, key = { "paid-${it.id}" }) { plan ->
                        PlanCard(
                            plan = plan,
                            cronograma = CuotasEngine.cronograma(plan, allMovements),
                            formatMoney = formatMoney,
                            currentUser = currentUser,
                            config = config,
                            attenuated = true,
                            onClick = { onOpenPlan(plan) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyPlansHint(showPaid: Boolean, hasPaid: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.CreditCard,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = com.example.ui.theme.appTextMuted
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (showPaid && hasPaid) "No hay planes pendientes para este filtro"
                else "No hay compras en cuotas pendientes",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tocá \"Nueva compra\" para agregar una",
                color = com.example.ui.theme.appTextMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PlanCard(
    plan: CuotaPlan,
    cronograma: List<CuotaProgramada>,
    formatMoney: NumberFormat,
    currentUser: String,
    config: UsuariosConfig,
    attenuated: Boolean,
    onClick: () -> Unit
) {
    val isDark = com.example.ui.theme.LocalIsDarkTheme.current
    val cardBg = MaterialTheme.colorScheme.surface
    val cardBorder = MaterialTheme.colorScheme.outlineVariant
    val textMain = MaterialTheme.colorScheme.onSurface
    val alpha = if (attenuated) 0.55f else 1f

    val ownerColor = personaColor(plan.propietario, currentUser)

    val pagadas = cronograma.count { it.pagada }
    val total = plan.cantidadCuotas
    val progreso = if (total > 0) pagadas.toFloat() / total else 0f
    val proxima = cronograma.firstOrNull { !it.pagada }

    // Estado del mes en curso: resaltamos el plan si tiene una cuota impaga que vence este mes
    // (o si viene atrasada de meses anteriores, que es aún más urgente).
    val mesActual = currentYyyyMm()
    val cuotaEsteMes = cronograma.firstOrNull { it.mesVencimiento == mesActual }
    val pendienteEsteMes = !attenuated && cuotaEsteMes != null && !cuotaEsteMes.pagada
    val atrasada = !attenuated && cronograma.any { !it.pagada && it.mesVencimiento < mesActual }
    val resaltar = pendienteEsteMes || atrasada
    val resaltColor = if (atrasada) MaterialTheme.colorScheme.error else ownerColor
    // Color de texto legible sobre el badge relleno, según tema.
    val onResalt = when {
        !isDark -> Color.White
        atrasada -> Color.White
        else -> Color(0xFF111411)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(if (resaltar) 1.5.dp else 1.dp, if (resaltar) resaltColor.copy(alpha = 0.6f) else cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Badge de estado del mes (solo si hay algo pendiente ahora)
            if (resaltar) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(resaltColor)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(
                            imageVector = if (atrasada) Icons.Default.Warning else Icons.Default.Schedule,
                            contentDescription = null,
                            tint = onResalt,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = if (atrasada) "Cuota atrasada" else "A pagar este mes",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = onResalt
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = plan.descripcion.ifBlank { "Compra en cuotas" },
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = textMain.copy(alpha = alpha),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MiniTag(text = config.nombreDe(plan.propietario), color = ownerColor)
                        if (plan.tarjeta.isNotBlank()) {
                            MiniTag(text = plan.tarjeta, color = MaterialTheme.colorScheme.onSurfaceVariant, soft = true)
                        }
                        MiniTag(text = plan.categoria, color = MaterialTheme.colorScheme.onSurfaceVariant, soft = true)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatMoney.format(plan.montoPorCuota),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = textMain.copy(alpha = alpha)
                    )
                    Text(
                        text = "x $total cuotas",
                        fontSize = 11.sp,
                        color = textMain.copy(alpha = 0.5f)
                    )
                }
            }

            // Progreso
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (pagadas >= total && total > 0) "Completado"
                        else proxima?.let { "Próxima: ${formatMonthLabel(it.mesVencimiento)}" } ?: "—",
                        fontSize = 12.sp,
                        color = textMain.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "$pagadas/$total",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = ownerColor
                    )
                }
                LinearProgressIndicator(
                    progress = { progreso },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = ownerColor,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                )
            }
        }
    }
}

@Composable
private fun MiniTag(text: String, color: Color, soft: Boolean = false) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (soft) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f) else color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (soft) MaterialTheme.colorScheme.onSurfaceVariant else color,
            maxLines = 1
        )
    }
}

/** Filtros del listado: owner (segmentado, default usuario activo), categorías (multi) y switch de pagados. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CuotasFilters(
    owner: String,
    currentUser: String,
    config: UsuariosConfig,
    onOwnerChange: (String) -> Unit,
    availableCategories: List<String>,
    selectedCategories: Set<String>,
    onToggleCategory: (String) -> Unit,
    onClearCategories: () -> Unit,
    showPaid: Boolean,
    onToggleShowPaid: (Boolean) -> Unit
) {
    val cardBg = MaterialTheme.colorScheme.surface
    val cardBorder = MaterialTheme.colorScheme.outlineVariant
    val trackBg = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Owner segmentado
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Propietario",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        val selected = owner == key
                        val accent = if (key == "Todos") MaterialTheme.colorScheme.onSurface
                        else personaColor(key, currentUser)
                        val label = if (key == "Todos") "Todos" else config.nombreDe(key)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (selected) accent.copy(alpha = 0.15f) else Color.Transparent)
                                .clickable { onOwnerChange(key) }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Categorías (multi, desplegable)
            if (availableCategories.isNotEmpty()) {
                var expanded by remember { mutableStateOf(false) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { expanded = !expanded }
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (selectedCategories.isEmpty()) "Categorías" else "Categorías (${selectedCategories.size})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    if (!expanded && selectedCategories.isNotEmpty()) {
                        Text(
                            text = availableCategories.filter { selectedCategories.contains(it) }.joinToString(" · "),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    AnimatedVisibility(visible = expanded) {
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
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    } else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = selected,
                                        borderColor = MaterialTheme.colorScheme.outlineVariant
                                    )
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))

            // Switch de pagados
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Mostrar pagados", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Planes ya completados",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = showPaid, onCheckedChange = onToggleShowPaid)
            }
        }
    }
}

// =================================================================================================
// Detalle
// =================================================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanDetailContent(
    plan: CuotaPlan,
    allMovements: List<Movement>,
    currentUser: String,
    config: UsuariosConfig,
    isLoading: Boolean,
    onBack: () -> Unit,
    onConfirmCuota: (numero: Int, fecha: String, metodoPago: String, monto: Double, onSuccess: () -> Unit) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val formatMoney = remember { cuotasMoneyFormat() }
    val cronograma = CuotasEngine.cronograma(plan, allMovements)
    val mesActual = currentYyyyMm()
    val pagadas = cronograma.count { it.pagada }

    var cuotaAConfirmar by remember { mutableStateOf<CuotaProgramada?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val ownerColor = personaColor(plan.propietario, currentUser)
    // El plan es siempre personal: solo su dueño puede pagar/editar/eliminar. Para el otro es de
    // solo lectura.
    val canManage = plan.propietario.equals(currentUser, ignoreCase = true)

    cuotaAConfirmar?.let { cuota ->
        ConfirmarCuotaDialog(
            planDescripcion = plan.descripcion,
            numero = cuota.numero,
            cantidadCuotas = plan.cantidadCuotas,
            esUltima = cuota.numero == plan.cantidadCuotas,
            esAtrasada = cuota.mesVencimiento < mesActual,
            mesVencimiento = cuota.mesVencimiento,
            montoSugerido = plan.montoPorCuota,
            formatMoney = formatMoney,
            isLoading = isLoading,
            onDismiss = { cuotaAConfirmar = null },
            onConfirm = { fecha, metodo, monto ->
                onConfirmCuota(cuota.numero, fecha, metodo, monto) { cuotaAConfirmar = null }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("¿Eliminar plan?") },
            text = { Text("Se dará de baja el plan \"${plan.descripcion}\". Los pagos de cuotas ya registrados como movimientos se conservan.") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete() }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle del plan", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    // Editar/eliminar solo para el dueño del plan.
                    if (canManage) {
                        IconButton(onClick = onEdit, enabled = !isLoading) { Icon(Icons.Default.Edit, contentDescription = "Editar") }
                        IconButton(onClick = { showDeleteDialog = true }, enabled = !isLoading) {
                            Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Indicador de carga (p. ej. al eliminar el plan).
            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Encabezado / resumen
                item {
                    val cardBg = MaterialTheme.colorScheme.surface
                    val cardBorder = MaterialTheme.colorScheme.outlineVariant
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, cardBorder)
                    ) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(plan.descripcion.ifBlank { "Compra en cuotas" }, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                MiniTag(text = config.nombreDe(plan.propietario), color = ownerColor)
                                if (plan.tarjeta.isNotBlank()) MiniTag(text = plan.tarjeta, color = ownerColor, soft = true)
                                MiniTag(text = plan.categoria, color = ownerColor, soft = true)
                            }
                            if (!canManage) {
                                Text(
                                    text = "Solo lectura: es un plan de ${config.nombreDe(plan.propietario)}.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                DetailStat("Monto por cuota", formatMoney.format(plan.montoPorCuota), modifier = Modifier.weight(1f))
                                DetailStat("Total", formatMoney.format(plan.montoTotal), modifier = Modifier.weight(1f), alignEnd = true)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                DetailStat("Pagadas", "$pagadas de ${plan.cantidadCuotas}", modifier = Modifier.weight(1f))
                                DetailStat("Primera cuota", formatMonthLabel(plan.fechaPrimeraCuota), modifier = Modifier.weight(1f), alignEnd = true)
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "Cronograma",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                    )
                }

                items(cronograma, key = { it.numero }) { cuota ->
                    CuotaRow(
                        cuota = cuota,
                        formatMoney = formatMoney,
                        mesActual = mesActual,
                        accent = ownerColor,
                        enabled = !isLoading,
                        canPay = canManage,
                        onConfirm = { cuotaAConfirmar = cuota }
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailStat(label: String, value: String, modifier: Modifier = Modifier, alignEnd: Boolean = false) {
    Column(modifier = modifier, horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CuotaRow(
    cuota: CuotaProgramada,
    formatMoney: NumberFormat,
    mesActual: String,
    accent: Color,
    enabled: Boolean,
    canPay: Boolean,
    onConfirm: () -> Unit
) {
    val atrasada = !cuota.pagada && cuota.mesVencimiento < mesActual
    val esFutura = !cuota.pagada && cuota.mesVencimiento > mesActual
    val pagable = !cuota.pagada && !esFutura   // vence este mes o está atrasada

    val cardBg = MaterialTheme.colorScheme.surface
    val cardBorder = when {
        cuota.pagada -> accent.copy(alpha = 0.35f)
        atrasada -> MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    // Las cuotas futuras se atenúan: todavía no se pueden pagar.
    val contentAlpha = if (esFutura) 0.4f else 1f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Número / estado
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            cuota.pagada -> accent.copy(alpha = 0.15f)
                            atrasada -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                            pagable -> accent.copy(alpha = 0.12f)
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (cuota.pagada) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                } else {
                    Text("${cuota.numero}", fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        color = when {
                            atrasada -> MaterialTheme.colorScheme.error
                            pagable -> accent
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }.copy(alpha = contentAlpha))
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Cuota ${cuota.numero} · ${formatMonthLabel(cuota.mesVencimiento)}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                )
                Text(
                    text = when {
                        cuota.pagada -> "Pagada"
                        atrasada -> "Atrasada"
                        esFutura -> "Futura"
                        else -> "Vence este mes"
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        cuota.pagada -> accent
                        atrasada -> MaterialTheme.colorScheme.error
                        esFutura -> com.example.ui.theme.appTextMuted
                        else -> accent
                    }
                )
            }

            when {
                cuota.pagada -> Text(
                    formatMoney.format(cuota.monto),
                    fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                pagable && canPay -> Button(   // solo el dueño del plan puede pagar
                    onClick = onConfirm,
                    enabled = enabled,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = MaterialTheme.colorScheme.onPrimary),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Text("Pagar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                esFutura -> Text(  // futura: solo el monto, atenuado (no pagable aún)
                    formatMoney.format(cuota.monto),
                    fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    color = com.example.ui.theme.appTextMuted
                )
                else -> Text(  // pagable pero no es tu plan: solo lectura, mostramos el monto
                    formatMoney.format(cuota.monto),
                    fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// =================================================================================================
// Diálogo de confirmación de pago de cuota (reutilizable desde el Dashboard)
// =================================================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmarCuotaDialog(
    planDescripcion: String,
    numero: Int,
    cantidadCuotas: Int,
    esUltima: Boolean,
    esAtrasada: Boolean,
    mesVencimiento: String,          // "yyyy-MM" de la cuota
    montoSugerido: Double,
    formatMoney: NumberFormat,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (fecha: String, metodoPago: String, monto: Double) -> Unit
) {
    val context = LocalContext.current
    val dateFormatter = remember { java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val timeFormatter = remember { java.text.SimpleDateFormat("HH:mm", Locale.US) }
    val hoy = remember { dateFormatter.format(java.util.Date()) }
    val ayer = remember { dateFormatter.format(Calendar.getInstance().apply { add(Calendar.DATE, -1) }.time) }

    var fecha by remember { mutableStateOf(hoy) }
    var metodoPago by remember { mutableStateOf("Billetera Virtual") }
    // Monto editable (útil para ajustar el redondeo de la última cuota).
    var montoText by remember {
        mutableStateOf(
            if (montoSugerido % 1.0 == 0.0) montoSugerido.toLong().toString()
            else montoSugerido.toString()
        )
    }
    // Cuota atrasada: por defecto el gasto se registra en el MES DE LA CUOTA (no en el actual),
    // para no inflar el mes en curso al cargar un plan que ya se venía pagando.
    var cargarEnMesActual by remember { mutableStateOf(false) }
    val usarFechaManual = !esAtrasada || cargarEnMesActual

    // Bloquea el botón y muestra spinner mientras se registra el pago. Al terminar la operación
    // (isLoading -> false) se re-habilita; en caso de éxito el padre cierra el diálogo.
    var submitted by remember { mutableStateOf(false) }
    LaunchedEffect(isLoading) { if (!isLoading) submitted = false }

    AlertDialog(
        onDismissRequest = { if (!submitted) onDismiss() },
        title = { Text("Confirmar pago", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Cuota $numero/$cantidadCuotas — ${planDescripcion.ifBlank { "Compra en cuotas" }}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Monto
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Monto", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = montoText,
                        onValueChange = { input ->
                            val normalized = input.replace(',', '.')
                            if (normalized.all { it.isDigit() || it == '.' } && normalized.count { it == '.' } <= 1) {
                                montoText = normalized
                            }
                        },
                        prefix = { Text("$ ") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (esUltima) {
                        Text(
                            text = "Última cuota: podés ajustar el monto si hubo diferencia por redondeo.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Método de pago
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Método de pago", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Billetera Virtual", "Efectivo").forEach { item ->
                            val selected = metodoPago == item
                            Button(
                                onClick = { metodoPago = item },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                ),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                modifier = Modifier.weight(1f).height(42.dp)
                            ) {
                                Text(item, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }
                }

                // Cuota atrasada: elegir si el gasto se carga en el mes actual o en el de la cuota.
                if (esAtrasada) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Cargar en el mes actual", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = if (cargarEnMesActual) "El gasto se registrará en ${formatMonthLabel(currentYyyyMm())}"
                                else "Se registrará en ${formatMonthLabel(mesVencimiento)} (mes de la cuota)",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = cargarEnMesActual, onCheckedChange = { cargarEnMesActual = it })
                    }
                }

                // Fecha (solo cuando el gasto va con fecha del mes actual)
                if (usarFechaManual) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Fecha de pago", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DateShortcut("Hoy", fecha == hoy, Modifier.weight(1f)) { fecha = hoy }
                            DateShortcut("Ayer", fecha == ayer, Modifier.weight(1f)) { fecha = ayer }
                            OutlinedButton(
                                onClick = {
                                    val parts = fecha.split("-")
                                    val y = parts.getOrNull(0)?.toIntOrNull() ?: 2026
                                    val m = (parts.getOrNull(1)?.toIntOrNull() ?: 1) - 1
                                    val d = parts.getOrNull(2)?.toIntOrNull() ?: 1
                                    DatePickerDialog(context, { _, year, month, day ->
                                        val cal = Calendar.getInstance().apply { set(year, month, day) }
                                        fecha = dateFormatter.format(cal.time)
                                    }, y, m, d).show()
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                modifier = Modifier.weight(1.4f).height(42.dp)
                            ) {
                                Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(fecha, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val monto = montoText.toDoubleOrNull() ?: 0.0
            TextButton(
                enabled = monto > 0.0 && !submitted,
                onClick = {
                    val hora = timeFormatter.format(java.util.Date())
                    // Atrasada sin "cargar en mes actual" -> el gasto se fecha en el mes de la cuota.
                    val fechaFinal = if (usarFechaManual) "$fecha $hora" else "$mesVencimiento-01 $hora"
                    submitted = true
                    onConfirm(fechaFinal, metodoPago, monto)
                }
            ) {
                if (submitted) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text("Confirmar pago", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !submitted) { Text("Cancelar") } }
    )
}

@Composable
private fun DateShortcut(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
            contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        ),
        shape = RoundedCornerShape(10.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
        modifier = modifier.height(42.dp)
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

// =================================================================================================
// Alta / edición de plan
// =================================================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanFormContent(
    existing: CuotaPlan?,
    /** Categorías de los gastos, de más nueva a más vieja: alimenta los "Frecuentes" del [CategoryPicker]. */
    historialCategorias: List<String>,
    currentUser: String,
    config: UsuariosConfig,
    isLoading: Boolean,
    draft: CuotaDraft,
    onDraftChange: (CuotaDraft) -> Unit,
    onClearDraft: () -> Unit,
    onBack: () -> Unit,
    onSave: (descripcion: String, monto: Double, cantidad: Int, fechaPrimeraCuota: String, propietario: String, categoria: String, tarjeta: String, evitable: Boolean) -> Unit
) {
    val scrollState = rememberScrollState()
    val isNew = existing == null

    // En ALTA se siembra del borrador (persistente entre pestañas); en EDICIÓN, del plan existente.
    var descripcion by remember { mutableStateOf(existing?.descripcion ?: draft.descripcion) }
    var montoText by remember {
        mutableStateOf(
            existing?.let { if (it.montoPorCuota % 1.0 == 0.0) it.montoPorCuota.toLong().toString() else it.montoPorCuota.toString() } ?: draft.montoText
        )
    }
    var cantidadText by remember { mutableStateOf(existing?.cantidadCuotas?.toString() ?: draft.cantidadText) }
    // El plan es siempre del usuario activo (o conserva su dueño al editar): no se pregunta.
    val propietario = existing?.propietario ?: currentUser
    var categoria by remember { mutableStateOf(existing?.categoria ?: draft.categoria.ifEmpty { CATEGORIAS_CUOTAS.first() }) }
    var tarjeta by remember { mutableStateOf(existing?.tarjeta ?: draft.tarjeta) }
    var primeraCuota by remember { mutableStateOf(existing?.fechaPrimeraCuota ?: draft.primeraCuota.ifEmpty { currentYyyyMm() }) }
    // Los pagos del plan heredan este valor (se copia al pagar cada cuota).
    var evitable by remember { mutableStateOf(existing?.evitable ?: draft.evitable) }
    var showMonthPicker by remember { mutableStateOf(false) }

    // Volcar el borrador (solo en alta) para que sobreviva el cambio de pestaña.
    if (isNew) {
        LaunchedEffect(descripcion, montoText, cantidadText, categoria, tarjeta, primeraCuota, evitable) {
            onDraftChange(CuotaDraft(descripcion, montoText, cantidadText, categoria, tarjeta, primeraCuota, evitable))
        }
    }

    // Limpia el formulario de alta (botón "Limpiar").
    fun limpiarFormulario() {
        descripcion = ""; montoText = ""; cantidadText = ""
        categoria = CATEGORIAS_CUOTAS.first(); tarjeta = ""; primeraCuota = currentYyyyMm()
        evitable = false
        onClearDraft()
    }

    if (showMonthPicker) {
        MonthYearPickerDialog(
            initial = primeraCuota,
            onDismiss = { showMonthPicker = false },
            onSelect = { primeraCuota = it; showMonthPicker = false }
        )
    }

    val montoValido = (montoText.toDoubleOrNull() ?: 0.0) > 0.0
    val cantidadValida = (cantidadText.toIntOrNull() ?: 0) >= 1
    val formValido = descripcion.isNotBlank() && montoValido && cantidadValida && !isLoading

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "Nueva compra en cuotas" else "Editar plan", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
                },
                actions = {
                    // Mismo switch que el alta de gastos, a la izquierda de "Limpiar".
                    EvitableToggle(evitable = evitable, onToggle = { evitable = !evitable })
                    if (isNew) {
                        TextButton(onClick = { limpiarFormulario() }) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Limpiar", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Descripción
            FormLabel("¿Qué compraste?")
            OutlinedTextField(
                value = descripcion,
                onValueChange = { descripcion = it },
                placeholder = { Text("Ej. Notebook Lenovo", fontSize = 13.sp) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )

            // Monto por cuota (tipo cajero)
            val cardBg = MaterialTheme.colorScheme.surface
            val cardBorder = MaterialTheme.colorScheme.outlineVariant
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, cardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "MONTO POR CUOTA",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextField(
                        value = montoText,
                        onValueChange = { input ->
                            val normalized = input.replace(',', '.')
                            if (normalized.all { it.isDigit() || it == '.' } && normalized.count { it == '.' } <= 1) {
                                val parts = normalized.split(".")
                                if (parts.size <= 1 || parts[1].length <= 2) montoText = normalized
                            }
                        },
                        visualTransformation = NumberCommaVisualTransformation(),
                        textStyle = MaterialTheme.typography.headlineLarge.copy(
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 32.sp
                        ),
                        placeholder = {
                            Text("0", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                                    fontSize = 32.sp
                                ))
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Cantidad de cuotas + total calculado
            FormLabel("Cantidad de cuotas")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = cantidadText,
                    onValueChange = { input -> if (input.all { it.isDigit() } && input.length <= 3) cantidadText = input },
                    placeholder = { Text("12") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    modifier = Modifier.width(120.dp)
                )
                val monto = montoText.toDoubleOrNull() ?: 0.0
                val cant = cantidadText.toIntOrNull() ?: 0
                if (monto > 0 && cant > 0) {
                    val fmt = remember { cuotasMoneyFormat() }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Total del plan", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(fmt.format(monto * cant), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Mes de la primera cuota (solo mes y año)
            FormLabel("Mes de la primera cuota")
            OutlinedButton(
                onClick = { showMonthPicker = true },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(formatMonthLabel(primeraCuota), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            // Categoría
            FormLabel("Categoría del gasto")
            // Mismo selector que el alta de gastos, sin "Corrección" (una compra en cuotas nunca es
            // un ajuste contra el banco).
            CategoryPicker(
                tipo = "Gasto",
                seleccionada = categoria,
                historial = historialCategorias,
                onSelect = { categoria = it },
                incluirCorreccion = false
            )

            // Tarjeta (selección por chip; se puede deseleccionar tocando la elegida)
            FormLabel("Tarjeta (opcional)")
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                tarjetasSugeridas(config).forEach { t ->
                    val selected = tarjeta == t
                    FilterChip(
                        selected = selected,
                        onClick = { tarjeta = if (selected) "" else t },
                        label = { Text(t, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = {
                    if (!formValido) return@Button
                    onSave(
                        descripcion.trim(),
                        montoText.toDoubleOrNull() ?: 0.0,
                        cantidadText.toIntOrNull() ?: 1,
                        primeraCuota,
                        propietario,
                        categoria,
                        tarjeta.trim(),
                        evitable
                    )
                },
                enabled = formValido,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (existing == null) "Crear plan" else "Guardar cambios", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FormLabel(text: String) {
    Text(
        text = text,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * Selector de **solo mes y año** (la app guarda "yyyy-MM"). Reemplaza al DatePicker de día para
 * la primera cuota: un stepper de año y una grilla de 12 meses.
 */
@Composable
private fun MonthYearPickerDialog(
    initial: String,   // "yyyy-MM"
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val parts = initial.split("-")
    var year by remember { mutableStateOf(parts.getOrNull(0)?.toIntOrNull() ?: 2026) }
    var month by remember { mutableStateOf(parts.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 12) ?: 1) }
    val meses = listOf("Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mes de la primera cuota", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { year-- }) { Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Año anterior") }
                    Text("$year", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = { year++ }) { Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Año siguiente") }
                }
                for (fila in 0 until 4) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (col in 0 until 3) {
                            val mNum = fila * 3 + col + 1
                            val selected = mNum == month
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                                    )
                                    .clickable { month = mNum },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    meses[mNum - 1],
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect("%04d-%02d".format(year, month)) }) {
                Text("Aceptar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

// =================================================================================================
// Pago de tarjeta del mes
// =================================================================================================

/** Card destacada que sugiere pagar todas las cuotas del mes de una tarjeta. Lleva al resumen. */
@Composable
private fun PagarTarjetaCard(
    tarjeta: String,
    mes: String,
    cantidad: Int,
    total: Double,
    formatMoney: NumberFormat,
    onClick: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = primary),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(onPrimary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CreditCard, contentDescription = null, tint = onPrimary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Pagar $tarjeta",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = onPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$cantidad ${if (cantidad == 1) "cuota" else "cuotas"} de ${formatMonthLabel(mes)}",
                    fontSize = 12.sp,
                    color = onPrimary.copy(alpha = 0.85f)
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatMoney.format(total),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = onPrimary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ver resumen", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = onPrimary.copy(alpha = 0.9f))
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = onPrimary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/**
 * Resumen de una tarjeta: lista las cuotas del mes actual (impagas) de esa tarjeta, permite
 * excluir planes recalculando el total en vivo, y paga todas las incluidas de una sola vez
 * (un gasto por plan). El total no es editable directamente.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TarjetaResumenContent(
    tarjeta: String,
    plans: List<CuotaPlan>,
    allMovements: List<Movement>,
    currentUser: String,
    isLoading: Boolean,
    onBack: () -> Unit,
    onPagar: (cuotas: List<Pair<CuotaPlan, Int>>, metodoPago: String) -> Unit
) {
    val formatMoney = remember { cuotasMoneyFormat() }
    // Mismo criterio que el listado: el último resumen cerrado, no el mes en curso.
    val mesAPagar = CuotasEngine.mesAPagar(currentYyyyMm())
    // Solo cuotas de MIS planes: no se pagan las de la otra persona.
    val cuotas = CuotasEngine.cuotasImpagasDelMes(mesAPagar, plans, allMovements)
        .filter { it.first.tarjeta == tarjeta && it.first.propietario.equals(currentUser, ignoreCase = true) }
        .sortedBy { it.first.descripcion }

    // Si un refresh dejó la tarjeta sin cuotas del mes (todo pagado), volver al listado.
    if (cuotas.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
    }

    var excluidos by remember { mutableStateOf<Set<String>>(emptySet()) }
    var metodoPago by remember { mutableStateOf("Billetera Virtual") }

    val incluidas = cuotas.filter { !excluidos.contains(it.first.id) }
    val total = incluidas.sumOf { it.second.monto }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Resumen de tarjeta", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(tarjeta, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Cuotas de ${formatMonthLabel(mesAPagar)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
                item {
                    // Método de pago (se aplica a todas)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Método de pago", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Billetera Virtual", "Efectivo").forEach { item ->
                                val selected = metodoPago == item
                                Button(
                                    onClick = { metodoPago = item },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                    ),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).height(44.dp)
                                ) {
                                    Text(item, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                }
                            }
                        }
                    }
                }

                items(cuotas, key = { it.first.id }) { (plan, cuota) ->
                    val incluida = !excluidos.contains(plan.id)
                    ResumenCuotaRow(
                        plan = plan,
                        cuota = cuota,
                        incluida = incluida,
                        formatMoney = formatMoney,
                        onToggle = {
                            excluidos = if (incluida) excluidos + plan.id else excluidos - plan.id
                        }
                    )
                }
            }

            // Barra inferior: total (no editable) + Pagar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Total de la tarjeta", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (excluidos.isNotEmpty()) {
                                Text("${incluidas.size} de ${cuotas.size} cuotas", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(formatMoney.format(total), fontWeight = FontWeight.Black, fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Button(
                        onClick = { onPagar(incluidas.map { it.first to it.second.numero }, metodoPago) },
                        enabled = incluidas.isNotEmpty() && !isLoading,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (incluidas.size == cuotas.size) "Pagar la tarjeta" else "Pagar ${incluidas.size} cuota${if (incluidas.size == 1) "" else "s"}",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResumenCuotaRow(
    plan: CuotaPlan,
    cuota: CuotaProgramada,
    incluida: Boolean,
    formatMoney: NumberFormat,
    onToggle: () -> Unit
) {
    val cardBg = MaterialTheme.colorScheme.surface
    val cardBorder = MaterialTheme.colorScheme.outlineVariant
    val alpha = if (incluida) 1f else 0.4f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Checkbox(checked = incluida, onCheckedChange = { onToggle() })
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = plan.descripcion.ifBlank { "Compra en cuotas" },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Cuota ${cuota.numero}/${plan.cantidadCuotas} · ${plan.categoria}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f * alpha)
                )
            }
            Text(
                text = formatMoney.format(cuota.monto),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
}
