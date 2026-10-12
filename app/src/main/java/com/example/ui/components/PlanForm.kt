package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Categorias
import com.example.data.CuotaPlan
import com.example.data.UsuariosConfig
import com.example.ui.CuotaDraft
import com.example.ui.components.common.EvitableToggle
import com.example.ui.components.common.FlowRow
import com.example.ui.components.common.FormLabel
import com.example.ui.components.common.MontoCajeroCard
import com.example.ui.components.common.rememberDinero
import com.example.util.Fechas
import com.example.util.Formato
import java.util.Calendar

// Categorías del gasto que generará cada cuota. Mismo set que los gastos del alta de movimientos:
// ambos leen el catálogo compartido [Categorias], así que no se pueden desincronizar.
private val CATEGORIAS_CUOTAS = Categorias.CUOTAS

/**
 * Tarjetas sugeridas (selección por chip). Se derivan de los nombres actuales de los usuarios: con
 * la config por defecto dan "Visa Santiago"/"BBVA Rocío"/"Ualá Rocío", y si se renombra un usuario,
 * la sugerencia lo acompaña. Los planes ya guardados conservan su `tarjeta` (texto libre).
 */
private fun tarjetasSugeridas(config: UsuariosConfig): List<String> = listOf(
    "Visa ${config.primario.nombre}",
    "BBVA ${config.secundario.nombre}",
    "Ualá ${config.secundario.nombre}"
)

/** Alta (con borrador persistente) o edición (sembrada del plan) de una compra en cuotas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlanFormContent(
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
    var montoText by remember { mutableStateOf(existing?.let { Formato.montoParaInput(it.montoPorCuota) } ?: draft.montoText) }
    var cantidadText by remember { mutableStateOf(existing?.cantidadCuotas?.toString() ?: draft.cantidadText) }
    // El plan es siempre del usuario activo (o conserva su dueño al editar): no se pregunta.
    val propietario = existing?.propietario ?: currentUser
    var categoria by remember { mutableStateOf(existing?.categoria ?: draft.categoria.ifEmpty { CATEGORIAS_CUOTAS.first() }) }
    var tarjeta by remember { mutableStateOf(existing?.tarjeta ?: draft.tarjeta) }
    var primeraCuota by remember { mutableStateOf(existing?.fechaPrimeraCuota ?: draft.primeraCuota.ifEmpty { Fechas.mesActual() }) }
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
        categoria = CATEGORIAS_CUOTAS.first(); tarjeta = ""; primeraCuota = Fechas.mesActual()
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
            MontoCajeroCard(etiqueta = "MONTO POR CUOTA", monto = montoText, onMontoChange = { montoText = it })

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
                    val fmt = rememberDinero()
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
                Text(Fechas.etiquetaDeMes(primeraCuota), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            // Categoría
            FormLabel("Categoría del gasto")
            // Mismo selector que el alta de gastos, sin "Corrección" (una compra en cuotas nunca es
            // un ajuste contra el banco).
            CategoryPicker(
                tipo = com.example.data.TipoMovimiento.GASTO,
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
    var year by remember { mutableStateOf(parts.getOrNull(0)?.toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)) }
    var month by remember { mutableStateOf(parts.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 12) ?: 1) }

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
                    IconButton(onClick = { year-- }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Año anterior") }
                    Text("$year", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = { year++ }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Año siguiente") }
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
                                    Fechas.MESES[mNum - 1].take(3),
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
