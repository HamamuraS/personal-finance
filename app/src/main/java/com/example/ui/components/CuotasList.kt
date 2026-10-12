package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CuotaPlan
import com.example.data.Movement
import com.example.data.UsuariosConfig
import com.example.domain.CuotaProgramada
import com.example.domain.CuotasEngine
import com.example.domain.EstadoCuota
import com.example.domain.estadoEn
import com.example.ui.DashboardFilters
import com.example.ui.components.common.AppCard
import com.example.ui.components.common.BotonRecargar
import com.example.ui.components.common.FiltroDeCategorias
import com.example.ui.components.common.FiltroDePersona
import com.example.ui.components.common.IconoEnCirculo
import com.example.ui.components.common.alternar
import com.example.ui.components.common.rememberDinero
import com.example.ui.theme.appTextMuted
import com.example.ui.theme.personaColor
import com.example.ui.theme.personaColorDeContenido
import com.example.util.Fechas
import java.text.NumberFormat

/** Título visible de un plan: su descripción, o un genérico si quedó vacía. */
internal val CuotaPlan.titulo: String get() = descripcion.ifBlank { "Compra en cuotas" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CuotasListContent(
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
    val formatMoney = rememberDinero()
    // Filtro de owner: default = usuario activo.
    var filterOwner by remember { mutableStateOf(currentUser) } // Todos o un slotKey
    var selectedCategories by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun applyFilters(list: List<CuotaPlan>): List<CuotaPlan> {
        val byOwner = list.filter { filterOwner == DashboardFilters.TODOS || it.propietario.equals(filterOwner, ignoreCase = true) }
        return byOwner.filter { selectedCategories.isEmpty() || selectedCategories.contains(it.categoria) }
    }

    val pendingFiltered = applyFilters(plans).sortedByDescending { it.fechaCreacion }
    val paidFiltered = applyFilters(paidPlans).sortedByDescending { it.fechaCreacion }

    // "Pagar la tarjeta": cuotas impagas del último resumen CERRADO (no del mes en curso: la tarjeta
    // cierra a fin de mes), agrupadas por tarjeta. Solo aplica a TUS planes (no se pueden pagar las
    // cuotas de la otra persona), sin importar el filtro de propietario.
    val mesActual = Fechas.mesActual()
    val mesAPagar = CuotasEngine.mesAPagar(mesActual)
    val tarjetasAPagar = remember(plans, allMovements, currentUser, mesAPagar) {
        CuotasEngine.tarjetasAPagar(mesAPagar, plans, allMovements, currentUser)
    }
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
                actions = { BotonRecargar(isLoading, onRefresh) },
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
                    onToggleCategory = { cat -> selectedCategories = selectedCategories.alternar(cat) },
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
                        mesActual = mesActual,
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
                            tint = appTextMuted,
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
                            mesActual = mesActual,
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
                tint = appTextMuted
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
                color = appTextMuted,
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
    mesActual: String,
    formatMoney: NumberFormat,
    currentUser: String,
    config: UsuariosConfig,
    attenuated: Boolean,
    onClick: () -> Unit
) {
    val textMain = MaterialTheme.colorScheme.onSurface
    val alpha = if (attenuated) 0.55f else 1f

    val ownerColor = personaColor(plan.propietario, currentUser)

    val pagadas = cronograma.count { it.pagada }
    val total = plan.cantidadCuotas
    val progreso = if (total > 0) pagadas.toFloat() / total else 0f
    val proxima = cronograma.firstOrNull { !it.pagada }

    // Estado del mes en curso: resaltamos el plan si tiene una cuota impaga que vence este mes
    // (o si viene atrasada de meses anteriores, que es aún más urgente).
    val pendienteEsteMes = !attenuated && cronograma.any { it.estadoEn(mesActual) == EstadoCuota.VENCE_ESTE_MES }
    val atrasada = !attenuated && cronograma.any { it.estadoEn(mesActual) == EstadoCuota.ATRASADA }
    val resaltar = pendienteEsteMes || atrasada
    val resaltColor = if (atrasada) MaterialTheme.colorScheme.error else ownerColor
    // Color legible sobre el badge relleno.
    val onResalt = if (atrasada) MaterialTheme.colorScheme.onError else personaColorDeContenido(plan.propietario, currentUser)

    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        border = BorderStroke(if (resaltar) 1.5.dp else 1.dp, if (resaltar) resaltColor.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant)
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
                        text = plan.titulo,
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
                        else proxima?.let { "Próxima: ${Fechas.etiquetaDeMes(it.mesVencimiento)}" } ?: "—",
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
internal fun MiniTag(text: String, color: Color, soft: Boolean = false) {
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
    AppCard {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            FiltroDePersona("Propietario", owner, config, currentUser, onOwnerChange)

            FiltroDeCategorias(
                disponibles = availableCategories,
                seleccionadas = selectedCategories,
                onToggle = onToggleCategory,
                onLimpiar = onClearCategories,
                describirIcono = false
            )

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

    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        containerColor = primary,
        border = null,
        elevacion = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            IconoEnCirculo(Icons.Default.CreditCard, onPrimary, tamano = 44.dp, alphaFondo = 0.18f)
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
                    text = "$cantidad ${if (cantidad == 1) "cuota" else "cuotas"} de ${Fechas.etiquetaDeMes(mes)}",
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
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = onPrimary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
