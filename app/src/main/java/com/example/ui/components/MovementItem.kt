package com.example.ui.components

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MetodoPago
import com.example.data.Movement
import com.example.data.TipoMovimiento
import com.example.data.UsuariosConfig
import com.example.domain.AccountingEngine
import com.example.ui.ClaseDeMovimiento
import com.example.ui.clase
import com.example.ui.components.common.AppCard
import com.example.ui.components.common.ConfirmDialog
import com.example.ui.components.common.EtiquetaChica
import com.example.ui.components.common.abrirUrl
import com.example.ui.esDeOtraPersona
import com.example.ui.esDuplicable
import com.example.ui.signo
import com.example.ui.theme.appTextMuted
import com.example.ui.theme.personaColor
import com.example.ui.tipoVisible
import com.example.util.Fechas
import java.text.NumberFormat
import java.util.Locale

/**
 * Tarjeta de un movimiento del historial, con deslizar a la izquierda para borrar (solo si
 * [puedeBorrar]: un movimiento ajeno no se desliza).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwipeableMovementItem(
    movement: Movement,
    formatMoney: NumberFormat,
    currentUserProfile: String,
    config: UsuariosConfig,
    puedeBorrar: Boolean,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    /** null = ya está en la planilla. 0 = subiendo. > 0 = falló y se está reintentando. */
    intentosPendientes: Int? = null,
    /** null = no se puede editar (no es propio o no es del mes en curso). */
    onEdit: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var hasVibrated by remember { mutableStateOf(false) }

    // El deslizamiento nunca se confirma solo: al cruzar el 60 % se pide confirmación con un diálogo.
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { false },
        positionalThreshold = { distance -> distance * 0.6f }
    )

    // Lógica de activación: solo si se puede borrar y el progreso REAL cruza el 60 %.
    LaunchedEffect(dismissState.progress) {
        if (!puedeBorrar) return@LaunchedEffect

        val isFarEnough = dismissState.progress >= 0.6f
        val isGoingToDismiss = dismissState.targetValue == SwipeToDismissBoxValue.EndToStart

        if (isGoingToDismiss && isFarEnough) {
            if (!hasVibrated) {
                vibrarCorto(context)
                hasVibrated = true
            }
            if (dismissState.currentValue == SwipeToDismissBoxValue.Settled) {
                showDeleteDialog = true
            }
        } else if (dismissState.progress < 0.1f) {
            hasVibrated = false
        }
    }

    if (showDeleteDialog) {
        ConfirmDialog(
            titulo = "¿Eliminar Movimiento?",
            texto = "Tranqui, puede recuperarse si se elimina.",
            confirmar = "Eliminar",
            destructivo = true,
            onConfirm = {
                onDelete()
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = puedeBorrar,
        backgroundContent = {
            if (puedeBorrar) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color.White)
                }
            }
        }
    ) {
        MovementItem(
            movement = movement,
            formatMoney = formatMoney,
            currentUserProfile = currentUserProfile,
            config = config,
            onDuplicate = onDuplicate,
            intentosPendientes = intentosPendientes,
            onEdit = onEdit
        )
    }
}

@Suppress("DEPRECATION")
private fun vibrarCorto(context: android.content.Context) {
    val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        vibrator?.vibrate(android.os.VibrationEffect.createOneShot(40, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        vibrator?.vibrate(40)
    }
}

@Composable
private fun MovementItem(
    movement: Movement,
    formatMoney: NumberFormat,
    currentUserProfile: String,
    config: UsuariosConfig,
    onDuplicate: () -> Unit,
    /** null = ya está en la planilla. 0 = subiendo. > 0 = falló y se está reintentando. */
    intentosPendientes: Int? = null,
    onEdit: (() -> Unit)? = null
) {
    var showDuplicateDialog by remember { mutableStateOf(false) }
    var showDetail by remember { mutableStateOf(false) }

    if (showDetail) {
        MovementDetailDialog(
            movement = movement,
            config = config,
            currentUserProfile = currentUserProfile,
            formatMoney = formatMoney,
            onDismiss = { showDetail = false },
            onEdit = onEdit?.let { editar -> { showDetail = false; editar() } }
        )
    }

    if (showDuplicateDialog) {
        ConfirmDialog(
            titulo = "¿Duplicar Movimiento?",
            texto = "Se creará una copia a tu nombre (${config.nombreDe(currentUserProfile)}), como gasto personal y con la fecha de hoy.",
            confirmar = "Duplicar",
            onConfirm = {
                onDuplicate()
                showDuplicateDialog = false
            },
            onDismiss = { showDuplicateDialog = false }
        )
    }

    // Tocar la tarjeta abre el detalle completo.
    AppCard(modifier = Modifier.fillMaxWidth().clickable { showDetail = true }, elevacion = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val clase = movement.clase()
            val isGasto = clase == ClaseDeMovimiento.GASTO
            val isAporte = clase == ClaseDeMovimiento.APORTE

            // El color del aporte es el de identidad del aportante (estable ante el usuario activo).
            // Solo se usa cuando isAporte, y `responsable` siempre es un slotKey.
            val aporteColor = if (isAporte) personaColor(movement.responsable, currentUserProfile)
            else MaterialTheme.colorScheme.tertiary // Fallback para transferencias u otros

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
                    imageVector = when (clase) {
                        ClaseDeMovimiento.APORTE -> Icons.Default.KeyboardArrowUp
                        ClaseDeMovimiento.GASTO -> Icons.Default.KeyboardArrowDown
                        ClaseDeMovimiento.CONDONACION -> Icons.Default.VolunteerActivism
                        ClaseDeMovimiento.DEVOLUCION -> Icons.Default.Handshake
                        ClaseDeMovimiento.CAMBIO -> Icons.Default.CurrencyExchange
                        ClaseDeMovimiento.TRANSFERENCIA -> Icons.Default.Refresh
                    },
                    contentDescription = null,
                    tint = when {
                        isAporte -> aporteColor
                        isGasto && movement.esComun -> MaterialTheme.colorScheme.error
                        isGasto -> MaterialTheme.colorScheme.onSurfaceVariant
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
                        text = movement.signo() + formatMoney.format(movement.monto),
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
                    val responsableTag = config.nombreDe(movement.responsable)
                    val metodoTag = if (movement.metodoPago.contains(MetodoPago.EFECTIVO, ignoreCase = true)) "💵" else "💳"
                    // La fila optimista se marca: encolada pero sin confirmar. Si ya hubo
                    // intentos fallidos se dice, porque la notificación de error puede no haber
                    // llegado nunca (se descarta si el permiso de notificaciones está denegado).
                    val pendienteTag = when {
                        intentosPendientes == null -> ""
                        intentosPendientes > 0 -> "⚠️ Sin guardar, reintentando • "
                        else -> "⏳ Guardando… • "
                    }
                    val subtitulo = if (movement.descripcion.isNotEmpty()) {
                        "$pendienteTag$metodoTag $responsableTag • ${movement.descripcion}"
                    } else {
                        "$pendienteTag$metodoTag $responsableTag"
                    }
                    Text(
                        text = subtitulo,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1.5f)
                    )

                    // Icono de Ticket
                    if (movement.ticketUrl.isNotEmpty()) {
                        val context = LocalContext.current
                        Icon(
                            imageVector = Icons.Default.Receipt,
                            contentDescription = "Ver Ticket",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { abrirUrl(context, movement.ticketUrl) }
                                .padding(horizontal = 2.dp)
                        )
                    }

                    EtiquetasDelMovimiento(movement, clase, currentUserProfile, config) { showDuplicateDialog = true }
                }

                // Mostrar fecha muy minimal abajo
                Text(
                    text = Fechas.legible(movement.fecha).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                    fontSize = 10.sp,
                    color = appTextMuted,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

/** Las etiquetas del renglón de abajo de la tarjeta (y el botón de duplicar de los transportes). */
@Composable
private fun EtiquetasDelMovimiento(
    movement: Movement,
    clase: ClaseDeMovimiento,
    currentUserProfile: String,
    config: UsuariosConfig,
    onDuplicar: () -> Unit
) {
    when {
        clase == ClaseDeMovimiento.GASTO -> {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (movement.esDuplicable()) {
                    IconButton(onClick = onDuplicar, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Duplicar",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                // Gasto evitable (lujo, gusto, opcional).
                if (movement.evitable) {
                    Text(text = "🍰", fontSize = 11.sp)
                }
                if (movement.esDeOtraPersona()) {
                    // Tag que nombra al propietario: usa su color de identidad estable.
                    val propietarioColor = personaColor(movement.propietario, currentUserProfile)
                    EtiquetaChica("De ${config.nombreDe(movement.propietario)}", propietarioColor, propietarioColor.copy(alpha = 0.1f))
                }
                if (movement.esComun) {
                    EtiquetaChica("Común", MaterialTheme.colorScheme.error)
                } else {
                    EtiquetaChica(
                        "Pers.", MaterialTheme.colorScheme.onSurfaceVariant,
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                    )
                }
            }
        }
        clase == ClaseDeMovimiento.CONDONACION || clase == ClaseDeMovimiento.DEVOLUCION || clase == ClaseDeMovimiento.CAMBIO -> {
            EtiquetaChica(
                when (clase) {
                    ClaseDeMovimiento.CONDONACION -> "Perdón"
                    ClaseDeMovimiento.DEVOLUCION -> "Pago deuda"
                    else -> "Cambio"
                },
                MaterialTheme.colorScheme.tertiary
            )
        }
        movement.tipo.equals(TipoMovimiento.TRANSFERENCIA, ignoreCase = true) -> {
            val isPropia = movement.propietario.equals(movement.responsable, ignoreCase = true)
            EtiquetaChica(
                if (isPropia) "Mía" else "Transf.",
                if (isPropia) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
            )
        }
        clase == ClaseDeMovimiento.APORTE && !movement.propietario.equals(movement.responsable, ignoreCase = true) -> {
            // Tag que nombra a la cuenta destino (responsable): color de identidad estable.
            EtiquetaChica("A ${config.nombreDe(movement.responsable)}", personaColor(movement.responsable, currentUserProfile))
        }
    }
}

/**
 * Detalle completo de un movimiento en un diálogo. Se abre al tocar la tarjeta del listado; su razón
 * principal es poder leer la **descripción entera**, que no entra en la tarjeta compacta.
 */
@Composable
private fun MovementDetailDialog(
    movement: Movement,
    config: UsuariosConfig,
    currentUserProfile: String,
    formatMoney: NumberFormat,
    onDismiss: () -> Unit,
    /** Si no es null, se ofrece "Editar" a la izquierda de "Cerrar". */
    onEdit: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val clase = movement.clase()
    val isGasto = clase == ClaseDeMovimiento.GASTO
    val montoColor = when {
        clase == ClaseDeMovimiento.APORTE -> personaColor(movement.responsable, currentUserProfile)
        isGasto && movement.esComun -> MaterialTheme.colorScheme.error
        isGasto -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.tertiary
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(movement.categoria, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = movement.signo() + formatMoney.format(movement.monto),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp,
                    color = montoColor
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                DetailRow("Tipo", movement.tipoVisible())
                DetailRow("Fecha", Fechas.legible(movement.fecha))
                DetailRow("Responsable", config.nombreDe(movement.responsable))
                if (isGasto) {
                    DetailRow("Distribución", if (movement.esComun) "Común (50/50)" else "Personal")
                    DetailRow("Tipo de gasto", if (movement.evitable) "🍰 Evitable" else "🍞 Necesario")
                }
                DetailRow("Propietario", config.nombreDe(movement.propietario))
                if (clase == ClaseDeMovimiento.CAMBIO) {
                    DetailRow(
                        "Con",
                        if (AccountingEngine.isCambioPropio(movement)) "Otra persona"
                        else config.nombreDe(config.elOtro(movement.responsable).slotKey)
                    )
                    DetailRow("Entregó", if (movement.metodoPago.contains(MetodoPago.EFECTIVO, ignoreCase = true)) "💵 Efectivo" else "💳 Transferencia")
                } else {
                    DetailRow("Método de pago", movement.metodoPago)
                }
                // Descripción completa (el motivo de este popup): texto entero, con saltos de línea.
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Descripción",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = movement.descripcion.ifBlank { "Sin descripción" },
                        fontSize = 14.sp,
                        color = if (movement.descripcion.isBlank()) appTextMuted else MaterialTheme.colorScheme.onSurface
                    )
                }
                if (movement.ticketUrl.isNotEmpty()) {
                    TextButton(
                        onClick = { abrirUrl(context, movement.ticketUrl) },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ver ticket", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar", fontWeight = FontWeight.Bold) }
        },
        dismissButton = onEdit?.let { editar ->
            {
                TextButton(onClick = editar) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Editar", fontWeight = FontWeight.Bold)
                }
            }
        }
    )
}

/** Fila etiqueta/valor para el diálogo de detalle. */
@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}
