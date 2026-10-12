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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppConfig
import com.example.data.CuotaPlan
import com.example.data.Movement
import com.example.data.UsuariosConfig
import com.example.data.mensajeDelDiaVisible
import com.example.domain.BalanceBreakdown
import com.example.domain.CuotasEngine
import com.example.ui.DashboardFilters
import com.example.ui.components.common.BotonRecargar
import com.example.ui.components.common.alternar
import com.example.ui.components.common.rememberDinero
import com.example.ui.theme.appTextMuted
import com.example.ui.theme.personaColor
import com.example.util.Fechas

/**
 * Posición del panel de filtros dentro del `LazyColumn` de Inicio.
 *
 * `LazyListState` solo entiende de índices, así que hay que contar los `item` que van antes:
 * pozo común, [opcional] recordatorio de cuotas, título "Saldos Individuales", fila de socios,
 * gastos comunes, dinero cruzado y el título "Movimientos Recientes".
 *
 * Está separado y con nombre para que se vea que existe: agregar o sacar una tarjeta del
 * encabezado obliga a tocar esta cuenta.
 */
private fun indiceDeFiltros(hayRecordatorioDeCuotas: Boolean): Int =
    if (hayRecordatorioDeCuotas) 7 else 6

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    userProfile: String,
    config: UsuariosConfig,
    balance: BalanceBreakdown,
    movements: List<Movement>,
    isLoading: Boolean,
    useLocalDemo: Boolean,
    onRefresh: () -> Unit,
    onDeleteMovement: (Movement) -> Unit,
    onDuplicateMovement: (Movement) -> Unit,
    availableMonths: List<String>,
    selectedMonth: String,
    onMonthSelected: (String) -> Unit,
    plans: List<CuotaPlan> = emptyList(),
    allMovements: List<Movement> = emptyList(),
    onConfirmCuota: (CuotaPlan, Int, String, String, Double?, () -> Unit) -> Unit = { _, _, _, _, _, _ -> },
    // Filtros: búsqueda por descripción + persona + tipo + categorías. Viven en el ViewModel (ver
    // [DashboardFilters]) para sobrevivir el cambio de pestaña y para que Métricas pueda aplicarlos.
    filters: DashboardFilters = DashboardFilters(),
    onFiltersChange: (DashboardFilters) -> Unit = {},
    // Altas encoladas que la planilla todavía no confirmó, con su cantidad de intentos fallidos:
    // se listan igual (fila optimista) pero marcadas, para que se vea que el guardado sigue en
    // curso — o que falló y se está reintentando.
    pendingStates: Map<String, Int> = emptyMap(),
    /**
     * Señal de un solo uso: al llegar desde Métricas hay que abrir la pantalla directo a la altura
     * de los filtros, porque lo que se vino a ver es el listado filtrado y no los saldos. Se
     * consume con [onScrollAFiltrosConsumido] para no volver a saltar cada vez que se entra a
     * Inicio a mano.
     */
    scrollAFiltros: Boolean = false,
    onScrollAFiltrosConsumido: () -> Unit = {},
    /** Editar un movimiento desde su detalle (v7.7). [puedeEditar] decide si se ofrece el botón. */
    onEditMovement: (Movement) -> Unit = {},
    puedeEditar: (Movement) -> Boolean = { false },
    /** Deslizar para borrar: solo los movimientos que el usuario puede borrar (los propios). */
    puedeBorrar: (Movement) -> Boolean = { false }
) {
    // Se calcula acá arriba, y no dentro del LazyColumn, porque de esta tarjeta depende el índice
    // del panel de filtros (ver [indiceDeFiltros]). Se corta en el último resumen CERRADO, no en el
    // mes en curso: la tarjeta cierra a fin de mes, así que en agosto se paga julio.
    val mesActualReal = Fechas.mesActual()
    val recordatoriosCuotas = remember(plans, allMovements, userProfile, mesActualReal) {
        CuotasEngine
            .recordatoriosDelMes(CuotasEngine.mesAPagar(mesActualReal), plans, allMovements)
            .filter { it.plan.propietario.equals(userProfile, ignoreCase = true) }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(scrollAFiltros) {
        if (!scrollAFiltros) return@LaunchedEffect
        listState.scrollToItem(indiceDeFiltros(hayRecordatorioDeCuotas = recordatoriosCuotas.isNotEmpty()))
        onScrollAFiltrosConsumido()
    }

    val formatMoney = rememberDinero()

    // Mensaje del día (lo genera el Apps Script con Gemini). null = encabezado de siempre: función
    // apagada en el `.env`, sin mensaje, o uno que no es de hoy.
    val mensajeDelDia = mensajeDelDiaVisible(config.byKey(userProfile), Fechas.hoy(), AppConfig.MENSAJE_DEL_DIA_ACTIVO)

    // Filtrado: descripción -> persona (propietario) -> tipo -> categorías (multi). Ver
    // [DashboardFilters.aplicar].
    val filtrado = remember(movements, filters) { filters.aplicar(movements) }

    Scaffold(
        topBar = {
            val titulo: @Composable () -> Unit = {
                TituloInicio(
                    mensajeDelDia = mensajeDelDia,
                    selectedMonth = selectedMonth,
                    useLocalDemo = useLocalDemo,
                    availableMonths = availableMonths,
                    onMonthSelected = onMonthSelected
                )
            }
            if (mensajeDelDia == null) {
                TopAppBar(
                    title = titulo,
                    actions = { BotonRecargar(isLoading, onRefresh) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            } else {
                // Con mensaje son tres niveles y la frase puede ocupar dos renglones: no entra en la
                // altura fija del TopAppBar. Mismas medidas (16dp a la izquierda, botón a la derecha)
                // pero la altura la define el contenido.
                Surface(color = MaterialTheme.colorScheme.background) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(TopAppBarDefaults.windowInsets)
                            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) { titulo() }
                        BotonRecargar(isLoading, onRefresh)
                    }
                }
            }
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ¡OJO! Si se agrega o saca un `item` de acá arriba, hay que actualizar
                // [indiceDeFiltros] o el salto desde Métricas cae en la tarjeta equivocada.

                item {
                    PozoComunCard(userProfile = userProfile, config = config, balance = balance, formatMoney = formatMoney)
                }

                // Recordatorio: cuotas a pagar del usuario activo (incluye atrasadas), independiente
                // del mes que se esté visualizando.
                if (recordatoriosCuotas.isNotEmpty()) {
                    item {
                        CuotasRecordatorioCard(
                            recordatorios = recordatoriosCuotas,
                            formatMoney = formatMoney,
                            isLoading = isLoading,
                            onConfirmCuota = onConfirmCuota
                        )
                    }
                }

                // Desglose de Saldos Individuales
                item {
                    Text(
                        text = "Saldos Individuales",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        config.todos.forEach { usuario ->
                            DesgloseSocioCard(
                                modifier = Modifier.weight(1f),
                                nombre = usuario.nombre,
                                saldo = balance.de(usuario.slotKey),
                                formatMoney = formatMoney,
                                avatarColor = personaColor(usuario.slotKey, userProfile)
                            )
                        }
                    }
                }

                // Gasto común total (una sola vez; antes se repetía simétrico en rojo en cada tarjeta)
                item {
                    GastosComunesCard(total = balance.gastosComunesReales, cantidadDePersonas = config.todos.size, formatMoney = formatMoney)
                }

                // Relación de propiedad cruzada (una sola vez, no redundante por tarjeta)
                item {
                    DineroCruzadoCard(balance = balance, currentUserProfile = userProfile, config = config, formatMoney = formatMoney)
                }

                // Historial Reciente de Movimientos
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Movimientos Recientes",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        if (useLocalDemo) {
                            Text(
                                text = "Mantén presionado para borrar",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item {
                    MovementFilters(
                        person = filters.persona,
                        currentUser = userProfile,
                        config = config,
                        searchQuery = filters.query,
                        onSearchChange = { onFiltersChange(filters.copy(query = it)) },
                        onPersonChange = { onFiltersChange(filters.copy(persona = it)) },
                        tipo = filters.tipo,
                        onTipoChange = { onFiltersChange(filters.copy(tipo = it)) },
                        availableCategories = filtrado.categoriasDisponibles,
                        selectedCategories = filtrado.categoriasEfectivas,
                        onToggleCategory = { cat -> onFiltersChange(filters.copy(categorias = filters.categorias.alternar(cat))) },
                        onClearCategories = { onFiltersChange(filters.copy(categorias = emptySet())) }
                    )
                }

                if (filtrado.movimientos.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = appTextMuted
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Sin movimientos registrados para el filtro",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                } else {
                    items(filtrado.movimientos, key = { it.id }) { mov ->
                        SwipeableMovementItem(
                            movement = mov,
                            formatMoney = formatMoney,
                            currentUserProfile = userProfile,
                            config = config,
                            puedeBorrar = puedeBorrar(mov),
                            onDelete = { onDeleteMovement(mov) },
                            onDuplicate = { onDuplicateMovement(mov) },
                            intentosPendientes = pendingStates[mov.id],
                            onEdit = if (puedeEditar(mov)) ({ onEditMovement(mov) }) else null
                        )
                    }
                }
            }
        }
    }
}

/**
 * Encabezado de Inicio: el saludo del día como título, el mensaje del día (si hay) como subtítulo y el
 * selector de mes debajo. Sin mensaje queda en dos niveles, con el selector en tipografía secundaria;
 * con mensaje el selector pasa a terciario (más chico y más tenue) para que la jerarquía se lea sola.
 */
@Composable
private fun TituloInicio(
    mensajeDelDia: String?,
    selectedMonth: String,
    useLocalDemo: Boolean,
    availableMonths: List<String>,
    onMonthSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        // Siempre el día real, aunque se esté mirando un mes viejo: es un saludo, no un dato del periodo.
        Text(
            text = saludoDeHoy(),
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )
        if (mensajeDelDia != null) {
            // Alineado a la izquierda; si no entra en un renglón baja al siguiente con el mismo
            // aire arriba y abajo.
            Text(
                text = mensajeDelDia,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
            )
        }
        val colorSelector = if (mensajeDelDia != null) appTextMuted else MaterialTheme.colorScheme.onSurfaceVariant
        Box {
            Row(
                modifier = Modifier.clickable { expanded = true },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = buildString {
                        append(if (selectedMonth.isNotEmpty()) Fechas.etiquetaDeMes(selectedMonth) else "Elegí un mes")
                        if (useLocalDemo) append(" · Modo local (demo)")
                    },
                    fontSize = if (mensajeDelDia != null) 11.sp else 12.sp,
                    color = colorSelector,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = "Cambiar de mes",
                    tint = colorSelector,
                    modifier = Modifier.size(if (mensajeDelDia != null) 16.dp else 18.dp)
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                availableMonths.forEach { month ->
                    DropdownMenuItem(
                        text = { Text(Fechas.etiquetaDeMes(month)) },
                        onClick = {
                            onMonthSelected(month)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

/**
 * "Hoy es lunes ☕": el título de Inicio. Un emoji distinto por día, de lunes a domingo.
 * [diaDeLaSemana] es la constante de [java.util.Calendar] (`Calendar.SUNDAY` = 1 … `SATURDAY` = 7).
 */
internal fun saludoDeHoy(
    diaDeLaSemana: Int = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
): String = when (diaDeLaSemana) {
    java.util.Calendar.MONDAY -> "Hoy es lunes ☕"
    java.util.Calendar.TUESDAY -> "Hoy es martes 💪"
    java.util.Calendar.WEDNESDAY -> "Hoy es miércoles 🐫"
    java.util.Calendar.THURSDAY -> "Hoy es jueves 🌮"
    java.util.Calendar.FRIDAY -> "Hoy es viernes 🎉"
    java.util.Calendar.SATURDAY -> "Hoy es sábado 😎"
    else -> "Hoy es domingo 🛋️"
}
