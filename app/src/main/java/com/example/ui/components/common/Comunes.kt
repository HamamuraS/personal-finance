package com.example.ui.components.common

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MetodoPago
import com.example.data.UsuariosConfig
import com.example.ui.DashboardFilters
import com.example.ui.theme.personaColor
import com.example.util.Fechas
import com.example.util.Formato
import java.text.NumberFormat
import java.util.Calendar

// =================================================================================================
// Piezas visuales compartidas por las pantallas. Cada una reproduce exactamente el estilo que antes
// estaba copiado en cada lugar; los parámetros son las diferencias que había entre las copias.
// =================================================================================================

/** El formateador de montos de la app ("$ 1.234,56"), recordado por la composición. */
@Composable
fun rememberDinero(): NumberFormat = remember { Formato.dinero() }

/**
 * La tarjeta de la app: fondo `surface`, borde fino `outlineVariant` y sin elevación, salvo que se
 * pida otra cosa. [modifier] va tal cual a la `Card` (los `clickable` se pasan por acá).
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier.fillMaxWidth(),
    radio: Dp = 20.dp,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    elevacion: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(radio),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = border,
        elevation = CardDefaults.cardElevation(defaultElevation = elevacion),
        content = content
    )
}

/** Ícono centrado en un círculo con el mismo color de fondo atenuado. */
@Composable
fun IconoEnCirculo(
    icono: ImageVector,
    color: Color,
    tamano: Dp,
    alphaFondo: Float,
    tamanoIcono: Dp? = null
) {
    Box(
        modifier = Modifier.size(tamano).clip(CircleShape).background(color.copy(alpha = alphaFondo)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icono,
            contentDescription = null,
            tint = color,
            modifier = if (tamanoIcono != null) Modifier.size(tamanoIcono) else Modifier
        )
    }
}

/** Etiqueta chiquita de las tarjetas de movimientos ("Común", "De Rocío", "Mía"…). */
@Composable
fun EtiquetaChica(texto: String, color: Color, fondo: Color = color.copy(alpha = 0.08f)) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(fondo)
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(text = texto, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

/** Diálogo de confirmación de dos botones. [destructivo] pinta la confirmación en rojo. */
@Composable
fun ConfirmDialog(
    titulo: String,
    texto: String,
    confirmar: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructivo: Boolean = false,
    cancelar: String = "Cancelar"
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titulo) },
        text = { Text(texto) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                if (destructivo) Text(confirmar, color = MaterialTheme.colorScheme.error) else Text(confirmar)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(cancelar) } }
    )
}

/** Botón de recargar del TopAppBar: spinner mientras carga. */
@Composable
fun BotonRecargar(isLoading: Boolean, onRefresh: () -> Unit) {
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
}

/** Título de un campo de formulario. */
@Composable
fun FormLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

/** Subtítulo chico de una sección de un formulario o diálogo ("Método de pago", "Persona"…). */
@Composable
fun Subtitulo(text: String, fontSize: TextUnit = 12.sp) {
    Text(text, fontSize = fontSize, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Colores de un botón de opción: relleno de marca si está elegido, gris tenue si no. */
@Composable
fun colorDeOpcion(seleccionado: Boolean): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = if (seleccionado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
    contentColor = if (seleccionado) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
)

/** Colores de un botón de opción "suave": el elegido va con el [acento] atenuado de fondo. */
@Composable
fun colorDeOpcionSuave(
    seleccionado: Boolean,
    acento: Color = MaterialTheme.colorScheme.primary,
    alphaFondo: Float = 0.15f
): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = if (seleccionado) acento.copy(alpha = alphaFondo) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
    contentColor = if (seleccionado) acento else MaterialTheme.colorScheme.onSurface
)

/**
 * Los dos medios de pago como botones de igual ancho. Las tres pantallas que lo usan difieren solo
 * en las medidas, que se pasan tal cual.
 */
@Composable
fun SelectorDeMetodoPago(
    seleccionado: String,
    onSelect: (String) -> Unit,
    alto: Dp,
    radio: Dp,
    fontSize: TextUnit,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    unaLinea: Boolean = true
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(MetodoPago.VIRTUAL, MetodoPago.EFECTIVO).forEach { item ->
            Button(
                onClick = { onSelect(item) },
                colors = colorDeOpcion(seleccionado == item),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                shape = RoundedCornerShape(radio),
                contentPadding = contentPadding,
                modifier = Modifier.weight(1f).height(alto)
            ) {
                if (unaLinea) Text(item, fontSize = fontSize, fontWeight = FontWeight.Bold, maxLines = 1)
                else Text(item, fontSize = fontSize, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Abre el selector de fecha nativo parado en [fecha] ("yyyy-MM-dd"; si no parsea, en hoy). */
fun mostrarSelectorDeFecha(context: Context, fecha: String, onFecha: (String) -> Unit) {
    val hoy = Calendar.getInstance()
    val partes = fecha.split("-")
    val y = partes.getOrNull(0)?.toIntOrNull() ?: hoy.get(Calendar.YEAR)
    val m = (partes.getOrNull(1)?.toIntOrNull() ?: (hoy.get(Calendar.MONTH) + 1)) - 1
    val d = partes.getOrNull(2)?.toIntOrNull() ?: hoy.get(Calendar.DAY_OF_MONTH)
    DatePickerDialog(context, { _, year, month, day ->
        val elegida = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
        }
        onFecha(Fechas.dia(elegida.time))
    }, y, m, d).show()
}

/**
 * "Hoy" / "Ayer" / selector de fecha. "Hoy" y "Ayer" se calculan en cada composición: con el
 * formulario abierto pasada la medianoche, recordarlos los dejaba apuntando al día anterior.
 */
@Composable
fun AtajosDeFecha(
    fecha: String,
    onFecha: (String) -> Unit,
    alto: Dp,
    pesoSelector: Float,
    paddingSelector: Dp,
    paddingAtajo: PaddingValues = ButtonDefaults.ContentPadding,
    selectorUnaLinea: Boolean = false
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val hoy = Fechas.hoy()
    val ayer = Fechas.ayer()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf("Hoy" to hoy, "Ayer" to ayer).forEach { (etiqueta, dia) ->
            Button(
                onClick = { onFecha(dia) },
                colors = colorDeOpcionSuave(fecha == dia, alphaFondo = 0.1f),
                shape = RoundedCornerShape(10.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                contentPadding = paddingAtajo,
                modifier = Modifier.weight(1f).height(alto)
            ) {
                Text(etiqueta, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        OutlinedButton(
            onClick = { mostrarSelectorDeFecha(context, fecha, onFecha) },
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = paddingSelector),
            modifier = Modifier.weight(pesoSelector).height(alto)
        ) {
            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            if (selectorUnaLinea) Text(fecha, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            else Text(fecha, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Tarjeta del monto "tipo cajero": un número grande, centrado, con separador de miles mientras se
 * escribe. Acepta coma o punto decimal y a lo sumo dos decimales.
 */
@Composable
fun MontoCajeroCard(
    etiqueta: String,
    monto: String,
    onMontoChange: (String) -> Unit,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    AppCard(radio = 24.dp) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = etiqueta,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextField(
                value = monto,
                onValueChange = { input -> Formato.filtrarMonto(input)?.let(onMontoChange) },
                visualTransformation = NumberCommaVisualTransformation(),
                textStyle = MaterialTheme.typography.headlineLarge.copy(
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 32.sp
                ),
                placeholder = {
                    Text(
                        "0",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                            fontSize = 32.sp
                        )
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = keyboardActions,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Switch de gasto evitable / no evitable del TopAppBar. Emoji + palabra para que no haya que adivinar
 * qué significa cada estado: 🍞 Necesario (default) ↔ 🍰 Evitable.
 */
@Composable
fun EvitableToggle(evitable: Boolean, onToggle: () -> Unit) {
    val color = if (evitable) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    TextButton(
        onClick = onToggle,
        colors = ButtonDefaults.textButtonColors(
            containerColor = color.copy(alpha = 0.1f),
            contentColor = color
        ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
        modifier = Modifier.padding(end = 4.dp)
    ) {
        Text(if (evitable) "🍰" else "🍞", fontSize = 15.sp)
        Spacer(modifier = Modifier.width(4.dp))
        Text(if (evitable) "Evitable" else "Necesario", fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

/**
 * Filtro segmentado por persona ("Todos" + cada usuario). El valor es [DashboardFilters.TODOS] o un
 * slotKey; la etiqueta visible es el nombre. Recorre todos los usuarios de la config.
 */
@Composable
fun FiltroDePersona(
    titulo: String,
    valor: String,
    config: UsuariosConfig,
    currentUser: String,
    onChange: (String) -> Unit
) {
    val trackBg = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = titulo,
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
            (listOf(DashboardFilters.TODOS) + config.todos.map { it.slotKey }).forEach { key ->
                val selected = valor == key
                val esTodos = key == DashboardFilters.TODOS
                val accent = if (esTodos) MaterialTheme.colorScheme.onSurface else personaColor(key, currentUser)
                val label = if (esTodos) DashboardFilters.TODOS else config.nombreDe(key)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (selected) accent.copy(alpha = 0.15f) else Color.Transparent)
                        .clickable { onChange(key) }
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
}

/**
 * Filtro de categorías (multi-selección) desplegable, contraído por defecto. El estado del filtro
 * vive afuera, así que contraer/expandir nunca deshace la selección. Con la selección puesta y el
 * panel cerrado, se resume en una línea.
 */
@Composable
fun FiltroDeCategorias(
    disponibles: List<String>,
    seleccionadas: Set<String>,
    onToggle: (String) -> Unit,
    onLimpiar: () -> Unit,
    describirIcono: Boolean
) {
    if (disponibles.isEmpty()) return
    var expandido by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { expandido = !expandido }
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (expandido) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (!describirIcono) null else if (expandido) "Contraer" else "Expandir",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (seleccionadas.isEmpty()) "Categorías" else "Categorías (${seleccionadas.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (seleccionadas.isNotEmpty()) {
                Text(
                    text = "Limpiar",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onLimpiar() }
                )
            }
        }
        if (!expandido && seleccionadas.isNotEmpty()) {
            Text(
                text = disponibles.filter { seleccionadas.contains(it) }.joinToString(" · "),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        AnimatedVisibility(visible = expandido) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                disponibles.forEach { cat ->
                    val selected = seleccionadas.contains(cat)
                    FilterChip(
                        selected = selected,
                        onClick = { onToggle(cat) },
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

/** El conjunto con [item] agregado si no estaba, o sacado si estaba. */
fun <T> Set<T>.alternar(item: T): Set<T> = if (contains(item)) this - item else this + item

/** Abre una URL (el ticket de un movimiento) con la app que corresponda. Sin visor, no hace nada. */
fun abrirUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
        // Sin visor disponible.
    }
}

/**
 * Separador de miles mientras se escribe un monto: el texto guardado es "1234.5" y se muestra
 * "1.234,5". El mapeo de offsets mantiene el cursor en su lugar.
 */
class NumberCommaVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val original = text.text
        if (original.isEmpty()) return TransformedText(text, OffsetMapping.Identity)

        val parts = original.split(".")
        val intPart = parts[0]
        val fracPart = if (parts.size > 1) parts[1] else null

        val formattedInt = StringBuilder()
        val totalLength = intPart.length
        for (i in 0 until totalLength) {
            formattedInt.append(intPart[i])
            val remaining = totalLength - i - 1
            if (remaining > 0 && remaining % 3 == 0) {
                formattedInt.append(".")
            }
        }

        val formatted = if (fracPart != null) {
            "${formattedInt},${fracPart}"
        } else if (original.endsWith(".")) {
            "${formattedInt},"
        } else {
            formattedInt.toString()
        }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 0) return 0
                val intLen = intPart.length
                return if (offset <= intLen) {
                    val dotsPassed = (intLen - 1) / 3 - (intLen - offset) / 3
                    offset + dotsPassed
                } else {
                    val dotsInTotal = (intLen - 1) / 3
                    offset + dotsInTotal
                }
            }

            override fun transformedToOriginal(offset: Int): Int {
                var oOffset = 0
                for (i in 0 until offset) {
                    if (i < formatted.length && formatted[i] != '.') {
                        oOffset++
                    }
                }
                return minOf(oOffset, original.length)
            }
        }
        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}

/**
 * Fila que reparte los chips en renglones. Respeta el espaciado de [horizontalArrangement] y
 * [verticalArrangement] (por defecto 6 dp); antes lo ignoraba y siempre usaba 6 dp, y además
 * estiraba cada chip a todo el ancho.
 */
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(6.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(6.dp),
    content: @Composable () -> Unit
) {
    androidx.compose.ui.layout.Layout(
        modifier = modifier,
        content = content
    ) { measurables, constraints ->
        val espacioH = horizontalArrangement.spacing.roundToPx()
        val espacioV = verticalArrangement.spacing.roundToPx()
        // Cada hijo se mide con su tamaño natural. Con las restricciones del padre tal cual, un
        // `fillMaxWidth` del FlowRow le imponía a cada chip el ancho mínimo de toda la fila, y
        // terminaban uno por renglón, estirados de lado a lado.
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val layoutWidth = constraints.maxWidth

        var currentX = 0
        var currentY = 0
        var maxRowHeight = 0
        val rowData = mutableListOf<List<androidx.compose.ui.layout.Placeable>>()
        var currentRow = mutableListOf<androidx.compose.ui.layout.Placeable>()

        placeables.forEach { placeable ->
            if (currentX + placeable.width > layoutWidth) {
                rowData.add(currentRow)
                currentRow = mutableListOf()
                currentX = 0
                currentY += maxRowHeight + espacioV
                maxRowHeight = 0
            }
            currentRow.add(placeable)
            currentX += placeable.width + espacioH
            maxRowHeight = maxOf(maxRowHeight, placeable.height)
        }
        if (currentRow.isNotEmpty()) {
            rowData.add(currentRow)
        }

        val finalHeight = currentY + maxRowHeight
        layout(layoutWidth, finalHeight) {
            var yOffset = 0
            rowData.forEach { row ->
                var xOffset = 0
                var rowMaxHeight = 0
                row.forEach { placeable ->
                    placeable.placeRelative(x = xOffset, y = yOffset)
                    xOffset += placeable.width + espacioH
                    rowMaxHeight = maxOf(rowMaxHeight, placeable.height)
                }
                yOffset += rowMaxHeight + espacioV
            }
        }
    }
}
