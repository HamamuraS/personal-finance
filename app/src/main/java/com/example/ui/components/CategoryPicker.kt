package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Categorias
import com.example.data.GrupoCategorias

/**
 * Selector de categoría compartido por el alta de movimientos y el alta de compras en cuotas.
 *
 * El problema que resuelve: la lista de gastos ya pasó las 15 categorías y sigue creciendo, y el
 * FlowRow plano que había antes ocupaba media pantalla del formulario — con la particularidad de
 * que las categorías que se usan todos los días quedaban mezcladas con las que se usan dos veces
 * por año.
 *
 * El diseño tiene dos niveles y ningún tap extra en el caso frecuente:
 *
 *  1. **Frecuentes**: las categorías que la pareja viene usando, calculadas de los propios
 *     movimientos ([Categorias.recientes]). En la práctica casi toda alta se resuelve acá sin
 *     abrir ninguna carpeta.
 *  2. **Carpetas**: el resto del catálogo, plegado en los grupos de [Categorias.GRUPOS_GASTOS]. Se
 *     abre una por vez; la que contiene a la categoría elegida queda marcada aunque esté cerrada.
 *
 * La categoría seleccionada **siempre está visible**: si no aparece entre las frecuentes, se
 * antepone a esa fila. Sin eso, elegir algo de una carpeta y cerrarla dejaba el formulario sin
 * ninguna señal de qué se había elegido.
 *
 * Para los tipos con listas cortas (aportes, transferencias, condonaciones)
 * [Categorias.gruposDeTipo] devuelve `null` y esto se degrada al FlowRow plano de siempre: armar
 * carpetas para cuatro chips sería peor que no tenerlas.
 */
@Composable
fun CategoryPicker(
    tipo: String,
    seleccionada: String,
    historialCategorias: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val grupos = Categorias.gruposDeTipo(tipo)

    if (grupos == null) {
        CategoryChipRow(
            categorias = Categorias.deTipo(tipo),
            seleccionada = seleccionada,
            onSelect = onSelect,
            modifier = modifier
        )
        return
    }

    // Una carpeta abierta por vez. El estado se resetea si cambia el tipo (cambia la key del
    // `remember`), que es lo que corresponde: las carpetas de otro tipo no son las mismas.
    var grupoAbierto by remember(tipo) { mutableStateOf<String?>(null) }

    val frecuentes = remember(historialCategorias, tipo, seleccionada) {
        val base = Categorias.recientes(historialCategorias, tipo)
        // La elegida primero y sin repetirse: es el ancla visual del selector.
        if (seleccionada.isBlank()) base
        else listOf(seleccionada) + base.filterNot { it.equals(seleccionada, ignoreCase = true) }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (frecuentes.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PickerCaption("Frecuentes")
                CategoryChipRow(
                    categorias = frecuentes,
                    seleccionada = seleccionada,
                    onSelect = onSelect
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PickerCaption("Todas las categorías")
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                grupos.forEach { grupo ->
                    FolderChip(
                        grupo = grupo,
                        abierta = grupoAbierto == grupo.nombre,
                        contieneSeleccion = grupo.categorias.any { it.equals(seleccionada, ignoreCase = true) },
                        onClick = {
                            grupoAbierto = if (grupoAbierto == grupo.nombre) null else grupo.nombre
                        }
                    )
                }
            }

            grupos.forEach { grupo ->
                AnimatedVisibility(
                    visible = grupoAbierto == grupo.nombre,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(10.dp)
                    ) {
                        CategoryChipRow(
                            categorias = grupo.categorias,
                            seleccionada = seleccionada,
                            onSelect = {
                                onSelect(it)
                                grupoAbierto = null   // elegida: la carpeta se pliega sola
                            }
                        )
                    }
                }
            }
        }
    }
}

/** Fila de chips de categoría (el render plano de siempre, ahora reutilizable). */
@Composable
private fun CategoryChipRow(
    categorias: List<String>,
    seleccionada: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        categorias.forEach { cat ->
            val isSelected = cat.equals(seleccionada, ignoreCase = true)
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(cat) },
                label = { Text(cat, fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    labelColor = MaterialTheme.colorScheme.onSurface
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
        }
    }
}

/** Chip de carpeta: emoji + nombre, y el chevron de abierta/cerrada (o el tilde si tiene la elegida). */
@Composable
private fun FolderChip(
    grupo: GrupoCategorias,
    abierta: Boolean,
    contieneSeleccion: Boolean,
    onClick: () -> Unit,
) {
    val acento = MaterialTheme.colorScheme.primary
    val resaltada = abierta || contieneSeleccion
    val borde = if (resaltada) acento else MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (abierta) acento.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface)
            .border(if (resaltada) 1.5.dp else 1.dp, borde, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(grupo.emoji, fontSize = 12.sp)
        Text(
            text = grupo.nombre,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (resaltada) acento else MaterialTheme.colorScheme.onSurface
        )
        if (contieneSeleccion && !abierta) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Contiene la categoría elegida",
                tint = acento,
                modifier = Modifier.size(14.dp)
            )
        } else {
            Icon(
                imageVector = if (abierta) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (abierta) "Cerrar carpeta" else "Abrir carpeta",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun PickerCaption(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
