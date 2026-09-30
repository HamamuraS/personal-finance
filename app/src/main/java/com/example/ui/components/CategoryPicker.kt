package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Categorias
import com.example.data.Rubro

/**
 * Selector de categoría compartido por el alta de movimientos y el alta de compras en cuotas.
 *
 * Los gastos ya pasaban las 15 categorías y el FlowRow plano ocupaba media pantalla, con las de todos
 * los días mezcladas con las de dos veces por año. Para gastos hay dos niveles:
 *
 *  1. **Frecuentes**: las que la pareja más viene usando ([Categorias.frecuentes]). En la práctica
 *     casi toda alta se resuelve acá, con un toque.
 *  2. **Rubros** ([Categorias.RUBROS_GASTOS]): un rubro de una sola categoría se elige directo; uno
 *     de varias despliega las suyas justo debajo de su fila (uno abierto por vez).
 *
 * Todo va en una **grilla de celdas iguales** (ver [columnasPara]): 3 columnas en un teléfono común,
 * 4 en uno ancho. Chips de ancho variable quedaban desparejos y cada fila cortaba en otro lugar.
 *
 * La categoría elegida **siempre está visible**: si no está entre las frecuentes se antepone a esa
 * fila, y su rubro queda marcado. Sin eso, elegir algo dentro de un rubro y cerrarlo dejaba el
 * formulario sin señal de qué se había elegido.
 *
 * Los tipos con listas cortas (aportes, transferencias…) se muestran en la misma grilla, sin niveles.
 *
 * @param historial categorías de los movimientos, de más nuevo a más viejo.
 * @param incluirCorreccion ofrecer "Corrección" (un ajuste técnico, va al final y atenuado). Las
 *        cuotas no la ofrecen.
 */
@Composable
fun CategoryPicker(
    tipo: String,
    seleccionada: String,
    historial: List<String>,
    onSelect: (String) -> Unit,
    incluirCorreccion: Boolean = true
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columnas = columnasPara(maxWidth)

        if (!Categorias.usaRubros(tipo)) {
            Grilla(columnas) {
                Categorias.deTipo(tipo).forEach { cat ->
                    CategoriaChip(texto = cat, seleccionado = seleccionada == cat, onClick = { onSelect(cat) })
                }
            }
            return@BoxWithConstraints
        }

        // Dos filas completas de frecuentes, sea cual sea el número de columnas.
        val maxFrecuentes = columnas * 2
        val frecuentes = remember(historial, tipo, maxFrecuentes) {
            Categorias.frecuentes(historial, tipo, max = maxFrecuentes)
        }
        val filaFrecuentes = if (seleccionada.isNotEmpty() && frecuentes.none { it == seleccionada }) {
            listOf(seleccionada) + frecuentes.take(maxFrecuentes - 1)
        } else frecuentes
        val rubroElegido = if (seleccionada.isEmpty()) null else Categorias.rubroDe(seleccionada)
        var abierto by remember { mutableStateOf<Rubro?>(null) }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (filaFrecuentes.isNotEmpty()) {
                SubtituloPicker("Frecuentes")
                Grilla(columnas) {
                    filaFrecuentes.forEach { cat ->
                        CategoriaChip(texto = cat, seleccionado = seleccionada == cat, onClick = { onSelect(cat) })
                    }
                }
            }

            SubtituloPicker("Todas")
            // Orden: primero los rubros que se despliegan, después los de una sola categoría (se
            // eligen directo) y al final "Corrección". `sortedBy` es estable, así que dentro de cada
            // grupo se respeta el orden del catálogo.
            val rubros = Categorias.RUBROS_GASTOS.sortedBy { it.categorias.size == 1 }
            val indiceAbierto = rubros.indexOf(abierto).takeIf { it >= 0 }
            Grilla(columnas, indiceAbierto = indiceAbierto) {
                rubros.forEach { rubro ->
                    val unica = rubro.categorias.singleOrNull()
                    val marcado = rubroElegido == rubro && !Categorias.esCorreccion(seleccionada)
                    CategoriaChip(
                        texto = if (unica == null) "${rubro.etiqueta} ${if (abierto == rubro) "▴" else "▾"}" else rubro.etiqueta,
                        seleccionado = marcado,
                        onClick = {
                            if (unica != null) {
                                onSelect(unica)
                                abierto = null
                            } else {
                                abierto = if (abierto == rubro) null else rubro
                            }
                        }
                    )
                }
                if (incluirCorreccion) {
                    CategoriaChip(
                        texto = Categorias.CORRECCION,
                        seleccionado = Categorias.esCorreccion(seleccionada),
                        atenuado = true,
                        onClick = { onSelect(Categorias.CORRECCION); abierto = null }
                    )
                }
                // Categorías del rubro abierto: [Grilla] lo ubica justo debajo de la fila del rubro
                // tocado (no al final, que obligaba a scrollear para ver qué se desplegó).
                abierto?.let { rubro ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .padding(8.dp)
                    ) {
                        // Mismas columnas que afuera, para que las celdas se lean como de la misma grilla.
                        Grilla(columnas) {
                            rubro.categorias.forEach { cat ->
                                CategoriaChip(
                                    texto = cat,
                                    seleccionado = seleccionada == cat,
                                    onClick = { onSelect(cat); abierto = null }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Ancho mínimo de una celda: por debajo, los nombres largos ya no entran en dos renglones. */
private val ANCHO_MIN_CELDA = 92.dp

/**
 * Columnas de la grilla según el ancho disponible: 3 en un teléfono común (~330-360 dp de contenido),
 * 4 desde ~370 dp (S25 Ultra y similares), más en tablets. Nunca menos de 3.
 */
private fun columnasPara(ancho: Dp): Int = (ancho / ANCHO_MIN_CELDA).toInt().coerceIn(3, 6)

/**
 * Grilla de [columnas] celdas **del mismo ancho**, con un panel opcional que va debajo de la fila que
 * contiene a la celda [indiceAbierto]. El panel, si hay, es el **último** hijo del contenido y ocupa
 * todo el ancho. Todas las celdas de una fila toman la altura de la más alta.
 */
@Composable
private fun Grilla(columnas: Int, indiceAbierto: Int? = null, content: @Composable () -> Unit) {
    Layout(content = content, modifier = Modifier.fillMaxWidth()) { measurables, constraints ->
        val espacio = 6.dp.roundToPx()
        val ancho = constraints.maxWidth
        val anchoCelda = ((ancho - espacio * (columnas - 1)) / columnas).coerceAtLeast(0)
        val hayPanel = indiceAbierto != null && measurables.isNotEmpty()
        val celdasMedibles = if (hayPanel) measurables.dropLast(1) else measurables

        val filas = celdasMedibles.chunked(columnas)
        // Primero la altura natural de cada fila; después se miden todas con esa altura fija para que
        // las celdas de una fila queden parejas aunque un nombre ocupe dos renglones.
        val altoFilas = filas.map { fila -> fila.maxOf { it.minIntrinsicHeight(anchoCelda) } }
        val placeables = filas.mapIndexed { f, fila ->
            fila.map { it.measure(Constraints.fixed(anchoCelda, altoFilas[f])) }
        }
        val panel = if (hayPanel) {
            measurables.last().measure(Constraints(minWidth = ancho, maxWidth = ancho))
        } else null
        val filaDelPanel = indiceAbierto?.let { it / columnas }

        val alto = altoFilas.sum() + espacio * (filas.size - 1).coerceAtLeast(0) +
            (panel?.let { it.height + espacio } ?: 0)

        layout(ancho, alto) {
            var y = 0
            placeables.forEachIndexed { f, fila ->
                fila.forEachIndexed { c, celda -> celda.placeRelative(c * (anchoCelda + espacio), y) }
                y += altoFilas[f] + espacio
                if (f == filaDelPanel && panel != null) {
                    panel.placeRelative(0, y)
                    y += panel.height + espacio
                }
            }
        }
    }
}

@Composable
private fun SubtituloPicker(texto: String) {
    Text(
        text = texto,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * Celda de la grilla. Mismo tamaño de letra en todas (frecuentes, rubros, categorías de un rubro):
 * el texto va centrado y puede partirse en dos renglones en vez de achicarse.
 */
@Composable
private fun CategoriaChip(
    texto: String,
    seleccionado: Boolean,
    onClick: () -> Unit,
    atenuado: Boolean = false
) {
    FilterChip(
        selected = seleccionado,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(CHIP_ALTO),
        label = {
            Text(
                texto,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = if (atenuado && !seleccionado) com.example.ui.theme.appTextMuted else Color.Unspecified,
                modifier = Modifier.fillMaxWidth()
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = seleccionado,
            borderColor = MaterialTheme.colorScheme.outlineVariant
        )
    )
}

/** Alto fijo de las celdas: entra un nombre en dos renglones ("Alimentos frescos") sin que crezca. */
private val CHIP_ALTO = 40.dp
