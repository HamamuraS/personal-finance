package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.data.CuotaPlan
import com.example.data.Movement
import com.example.data.UsuariosConfig
import com.example.domain.CuotaProgramada
import com.example.domain.CuotasEngine
import com.example.domain.EstadoCuota
import com.example.domain.estadoEn
import com.example.ui.components.common.AppCard
import com.example.ui.components.common.ConfirmDialog
import com.example.ui.components.common.rememberDinero
import com.example.ui.theme.appTextMuted
import com.example.ui.theme.personaColor
import com.example.util.Fechas
import java.text.NumberFormat

/**
 * Detalle de un plan: resumen y cronograma. El plan es siempre personal: solo su dueño
 * ([canManage]) puede pagar, editar o eliminar; para el otro es de solo lectura.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlanDetailContent(
    plan: CuotaPlan,
    allMovements: List<Movement>,
    currentUser: String,
    config: UsuariosConfig,
    canManage: Boolean,
    isLoading: Boolean,
    onBack: () -> Unit,
    onConfirmCuota: (numero: Int, fecha: String, metodoPago: String, monto: Double, onSuccess: () -> Unit) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val formatMoney = rememberDinero()
    val cronograma = CuotasEngine.cronograma(plan, allMovements)
    val mesActual = Fechas.mesActual()
    val pagadas = cronograma.count { it.pagada }

    var cuotaAConfirmar by remember { mutableStateOf<CuotaProgramada?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val ownerColor = personaColor(plan.propietario, currentUser)

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
        ConfirmDialog(
            titulo = "¿Eliminar plan?",
            texto = "Se dará de baja el plan \"${plan.descripcion}\". Los pagos de cuotas ya registrados como movimientos se conservan.",
            confirmar = "Eliminar",
            destructivo = true,
            onConfirm = { showDeleteDialog = false; onDelete() },
            onDismiss = { showDeleteDialog = false }
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
                    AppCard(radio = 24.dp) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(plan.titulo, fontWeight = FontWeight.Bold, fontSize = 20.sp)
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
                                DetailStat("Primera cuota", Fechas.etiquetaDeMes(plan.fechaPrimeraCuota), modifier = Modifier.weight(1f), alignEnd = true)
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
    val estado = cuota.estadoEn(mesActual)
    val atrasada = estado == EstadoCuota.ATRASADA
    val esFutura = estado == EstadoCuota.FUTURA
    val pagable = estado == EstadoCuota.ATRASADA || estado == EstadoCuota.VENCE_ESTE_MES

    val cardBorder = when {
        cuota.pagada -> accent.copy(alpha = 0.35f)
        atrasada -> MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    // Las cuotas futuras se atenúan: todavía no se pueden pagar.
    val contentAlpha = if (esFutura) 0.4f else 1f

    AppCard(radio = 16.dp, border = BorderStroke(1.dp, cardBorder)) {
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
                    Text(
                        "${cuota.numero}", fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        color = when {
                            atrasada -> MaterialTheme.colorScheme.error
                            pagable -> accent
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }.copy(alpha = contentAlpha)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Cuota ${cuota.numero} · ${Fechas.etiquetaDeMes(cuota.mesVencimiento)}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                )
                Text(
                    text = when (estado) {
                        EstadoCuota.PAGADA -> "Pagada"
                        EstadoCuota.ATRASADA -> "Atrasada"
                        EstadoCuota.FUTURA -> "Futura"
                        EstadoCuota.VENCE_ESTE_MES -> "Vence este mes"
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (estado) {
                        EstadoCuota.PAGADA -> accent
                        EstadoCuota.ATRASADA -> MaterialTheme.colorScheme.error
                        EstadoCuota.FUTURA -> appTextMuted
                        EstadoCuota.VENCE_ESTE_MES -> accent
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
                    color = appTextMuted
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
