package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CuotaPlan
import com.example.data.MetodoPago
import com.example.data.Movement
import com.example.domain.CuotaProgramada
import com.example.domain.CuotasEngine
import com.example.ui.components.common.AppCard
import com.example.ui.components.common.SelectorDeMetodoPago
import com.example.ui.components.common.Subtitulo
import com.example.ui.components.common.rememberDinero
import com.example.util.Fechas
import java.text.NumberFormat

/**
 * Resumen de una tarjeta: lista las cuotas del último resumen cerrado (impagas) de esa tarjeta,
 * permite excluir planes recalculando el total en vivo, y paga todas las incluidas de una sola vez
 * (un gasto por plan). El total no es editable directamente.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TarjetaResumenContent(
    tarjeta: String,
    plans: List<CuotaPlan>,
    allMovements: List<Movement>,
    currentUser: String,
    isLoading: Boolean,
    onBack: () -> Unit,
    onPagar: (cuotas: List<Pair<CuotaPlan, Int>>, metodoPago: String) -> Unit
) {
    val formatMoney = rememberDinero()
    // Mismo criterio que el listado: el último resumen cerrado, no el mes en curso, y solo cuotas de
    // MIS planes (no se pagan las de la otra persona).
    val mesAPagar = CuotasEngine.mesAPagar(Fechas.mesActual())
    val cuotas = CuotasEngine.tarjetasAPagar(mesAPagar, plans, allMovements, currentUser)
        .firstOrNull { it.first == tarjeta }?.second.orEmpty()
        .sortedBy { it.first.descripcion }

    // Si un refresh dejó la tarjeta sin cuotas del mes (todo pagado), volver al listado.
    if (cuotas.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
    }

    var excluidos by remember { mutableStateOf<Set<String>>(emptySet()) }
    var metodoPago by remember { mutableStateOf(MetodoPago.VIRTUAL) }

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
                        text = "Cuotas de ${Fechas.etiquetaDeMes(mesAPagar)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
                item {
                    // Método de pago (se aplica a todas)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Subtitulo("Método de pago")
                        SelectorDeMetodoPago(
                            seleccionado = metodoPago,
                            onSelect = { metodoPago = it },
                            alto = 44.dp,
                            radio = 10.dp,
                            fontSize = 12.sp
                        )
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
    val alpha = if (incluida) 1f else 0.4f

    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        radio = 16.dp
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
                    text = plan.titulo,
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
