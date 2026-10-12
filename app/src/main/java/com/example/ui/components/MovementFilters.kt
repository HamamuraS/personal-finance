package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.UsuariosConfig
import com.example.ui.DashboardFilters
import com.example.ui.components.common.AppCard
import com.example.ui.components.common.FiltroDeCategorias
import com.example.ui.components.common.FiltroDePersona

/**
 * Panel de filtros del historial de movimientos: búsqueda por descripción, persona, tipo y
 * categorías (multi-selección). Las categorías mostradas se derivan de los movimientos ya filtrados
 * por lo anterior, de modo que solo aparecen opciones que realmente existen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MovementFilters(
    person: String,
    currentUser: String,
    config: UsuariosConfig,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onPersonChange: (String) -> Unit,
    tipo: String,
    onTipoChange: (String) -> Unit,
    availableCategories: List<String>,
    selectedCategories: Set<String>,
    onToggleCategory: (String) -> Unit,
    onClearCategories: () -> Unit
) {
    AppCard {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 0) Búsqueda por descripción
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Buscar por descripción", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Limpiar búsqueda", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                )
            )

            // 1) Persona (control segmentado)
            FiltroDePersona("Persona", person, config, currentUser, onPersonChange)

            // 2) Tipo de movimiento (selección única)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DashboardFilters.TIPOS.forEach { t ->
                    val selected = tipo == t
                    FilterChip(
                        selected = selected,
                        onClick = { onTipoChange(t) },
                        label = {
                            Text(
                                text = t,
                                fontSize = 11.sp,
                                maxLines = 1,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        },
                        modifier = Modifier.weight(1f),
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

            // 3) Categorías (multi-selección) - desplegable, contraído por defecto.
            FiltroDeCategorias(
                disponibles = availableCategories,
                seleccionadas = selectedCategories,
                onToggle = onToggleCategory,
                onLimpiar = onClearCategories,
                describirIcono = true
            )
        }
    }
}
