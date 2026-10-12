package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MetodoPago
import com.example.ui.components.common.AtajosDeFecha
import com.example.ui.components.common.SelectorDeMetodoPago
import com.example.ui.components.common.Subtitulo
import com.example.util.Fechas
import com.example.util.Formato
import java.text.NumberFormat

/**
 * Diálogo de confirmación del pago de una cuota (lo usan el detalle del plan y el recordatorio de
 * Inicio). El monto es editable (útil para el redondeo de la última cuota). Si la cuota está
 * atrasada, por defecto el gasto se fecha en el mes de la cuota y no en el actual.
 */
@Composable
internal fun ConfirmarCuotaDialog(
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
    var fecha by remember { mutableStateOf(Fechas.hoy()) }
    var metodoPago by remember { mutableStateOf(MetodoPago.VIRTUAL) }
    // Monto editable (útil para ajustar el redondeo de la última cuota).
    var montoText by remember { mutableStateOf(Formato.montoParaInput(montoSugerido)) }
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
                    Subtitulo("Monto")
                    OutlinedTextField(
                        value = montoText,
                        onValueChange = { input -> Formato.filtrarMonto(input, maxDecimales = null)?.let { montoText = it } },
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
                    Subtitulo("Método de pago")
                    SelectorDeMetodoPago(
                        seleccionado = metodoPago,
                        onSelect = { metodoPago = it },
                        alto = 42.dp,
                        radio = 10.dp,
                        fontSize = 11.sp,
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    )
                }

                // Cuota atrasada: elegir si el gasto se carga en el mes actual o en el de la cuota.
                if (esAtrasada) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Subtitulo("Cargar en el mes actual")
                            Text(
                                text = if (cargarEnMesActual) "El gasto se registrará en ${Fechas.etiquetaDeMes(Fechas.mesActual())}"
                                else "Se registrará en ${Fechas.etiquetaDeMes(mesVencimiento)} (mes de la cuota)",
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
                        Subtitulo("Fecha de pago")
                        AtajosDeFecha(
                            fecha = fecha,
                            onFecha = { fecha = it },
                            alto = 42.dp,
                            pesoSelector = 1.4f,
                            paddingSelector = 8.dp,
                            paddingAtajo = PaddingValues(horizontal = 4.dp),
                            selectorUnaLinea = true
                        )
                    }
                }
            }
        },
        confirmButton = {
            val monto = montoText.toDoubleOrNull() ?: 0.0
            TextButton(
                enabled = monto > 0.0 && !submitted,
                onClick = {
                    val hora = Fechas.horaActual()
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
