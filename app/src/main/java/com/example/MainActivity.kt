package com.example

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.data.PreferencesHelper
import com.example.data.notifications.BackgroundSyncScheduler
import com.example.data.notifications.BilleterasNotificationListener
import com.example.ui.AhorroViewModel
import com.example.ui.components.AddMovementScreen
import com.example.ui.components.CuotasScreen
import com.example.ui.components.DashboardScreen
import com.example.ui.components.IdentityPickerScreen
import com.example.ui.components.ReportsScreen
import com.example.ui.components.SettingsScreen
import com.example.ui.theme.MyApplicationTheme

enum class ScreenTab {
    INICIO, NUEVO, CUOTAS, METRICAS, AJUSTES
}

class MainActivity : ComponentActivity() {

    private val viewModel: AhorroViewModel by viewModels()

    // Pestaña pedida desde afuera (p. ej. al tocar la notificación de cuotas). `onNewIntent` la
    // actualiza si la Activity ya estaba viva; el `LaunchedEffect` de más abajo la consume.
    private var pendingOpenTab by mutableStateOf<ScreenTab?>(null)

    private val pedirPermisoNotificaciones =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* sin acción */ }

    /**
     * Pide POST_NOTIFICATIONS en el primer arranque. Sin este permiso los recordatorios de cuotas
     * se descartan **en silencio** (ver `CuotasNotifier.show`), y antes el único lugar que lo pedía
     * era un botón escondido en Ajustes: si nadie entraba ahí, las notificaciones no llegaban nunca.
     *
     * Se pide una sola vez. Android deja de mostrar el diálogo tras dos rechazos, así que insistir
     * en cada arranque solo serviría para quemar el pedido; después queda el botón de Ajustes.
     */
    private fun pedirPermisoDeNotificacionesUnaVez() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val prefs = PreferencesHelper(applicationContext)
        if (prefs.notifPermisoPedido) return
        prefs.notifPermisoPedido = true
        pedirPermisoNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /**
     * Estilo de las barras del sistema (estado y navegación).
     *
     * El tema claro/oscuro de la app lo elige el usuario en Ajustes y **no** sigue al del sistema,
     * así que `enableEdgeToEdge()` sin argumentos era el bug: dejaba que Android decidiera el color
     * de sus íconos por su cuenta. Con el teléfono en modo oscuro y la app en modo claro, los
     * íconos de arriba quedaban blancos sobre el fondo blanco de la app: invisibles.
     *
     * Se vuelve a llamar cada vez que cambia el tema (el efecto de más abajo), que es la forma
     * soportada de actualizar el estilo en caliente.
     */
    private fun aplicarEstiloDeBarras(darkTheme: Boolean) {
        val transparente = android.graphics.Color.TRANSPARENT
        // Velo que Android pone detrás de sus íconos oscuros en las APIs que no saben pintarlos
        // sobre un fondo claro (< 29 en la barra de navegación).
        val velo = android.graphics.Color.argb(0x40, 0, 0, 0)
        val estilo =
            if (darkTheme) SystemBarStyle.dark(transparente)
            else SystemBarStyle.light(transparente, velo)
        enableEdgeToEdge(statusBarStyle = estilo, navigationBarStyle = estilo)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        aplicarEstiloDeBarras(PreferencesHelper(applicationContext).isDarkMode)
        BackgroundSyncScheduler.schedule(applicationContext)
        pedirPermisoDeNotificacionesUnaVez()
        // El listener de billeteras puede haber quedado muerto (actualización del APK, optimización
        // de batería del fabricante). Abrir la app es el momento natural para resucitarlo.
        if (PreferencesHelper(applicationContext).deteccionMontosActiva) {
            BilleterasNotificationListener.pedirRebind(applicationContext)
        }
        consumirIntent(intent)

        setContent {
            val userProfile by viewModel.currentUserProfile.collectAsState()
            val isDarkMode by viewModel.isDarkMode.collectAsState()

            // El tema es del usuario, no del sistema: las barras lo tienen que seguir a él.
            LaunchedEffect(isDarkMode) { aplicarEstiloDeBarras(isDarkMode) }
            val usuarios by viewModel.usuarios.collectAsState()
            val activeUser by viewModel.activeUser.collectAsState()
            val otherUser by viewModel.otherUser.collectAsState()
            val hasChosenIdentity by viewModel.hasChosenIdentity.collectAsState()

            MyApplicationTheme(darkTheme = isDarkMode, activeUser = activeUser, otherUser = otherUser) {
                // Gate de identidad: en el primer arranque (o tras cerrar sesión) se elige quién sos.
                if (!hasChosenIdentity) {
                    IdentityPickerScreen(
                        usuarios = usuarios,
                        onPick = { viewModel.setIdentity(it) }
                    )
                    return@MyApplicationTheme
                }

                var currentTab by remember { mutableStateOf(pendingOpenTab ?: ScreenTab.INICIO) }

                // Pila de pestañas visitadas, para que el gesto de retroceso de Android vuelva a la
                // anterior en vez de cerrar la app. La app no usa Navigation-Compose (las pantallas
                // son un `when` sobre la pestaña actual), así que el back stack se lleva a mano.
                val tabBackStack = remember { mutableStateListOf<ScreenTab>() }

                /** Cambia de pestaña apilando la actual. Es el único camino de navegación. */
                fun irA(tab: ScreenTab) {
                    if (tab == currentTab) return
                    tabBackStack.add(currentTab)
                    currentTab = tab
                }

                // Deshabilitado en la raíz (Inicio recién abierto, sin historial): ahí el retroceso
                // tiene que cerrar la app como siempre, no quedar atrapado.
                BackHandler(enabled = tabBackStack.isNotEmpty()) {
                    currentTab = tabBackStack.removeAt(tabBackStack.lastIndex)
                }

                // Si se toca una notificación con la Activity ya viva, `onNewIntent` actualiza
                // `pendingOpenTab` y este efecto salta de pestaña.
                LaunchedEffect(pendingOpenTab) {
                    pendingOpenTab?.let {
                        irA(it)
                        pendingOpenTab = null
                    }
                }

                val movements by viewModel.movements.collectAsState()
                val balance by viewModel.balance.collectAsState()
                val isLoading by viewModel.isLoading.collectAsState()
                val useLocalDemo by viewModel.useLocalDemo.collectAsState()
                val isCurrentMonth by viewModel.isCurrentMonth.collectAsState()
                val plans by viewModel.plans.collectAsState()
                val allMovements by viewModel.allMovements.collectAsState()
                val selectedMonth by viewModel.selectedMonth.collectAsState()
                val dashboardFilters by viewModel.dashboardFilters.collectAsState()
                val pendingStates by viewModel.pendingStates.collectAsState()
                val scrollAFiltros by viewModel.scrollAFiltros.collectAsState()

                // Expulsión de "Nuevo" al mirar un mes cerrado. No es navegación del usuario, así
                // que no se apila: retroceder desde acá tiene que llevar a donde estaba antes, no
                // devolverlo a una pestaña que ya no existe.
                LaunchedEffect(isCurrentMonth) {
                    if (!isCurrentMonth && currentTab == ScreenTab.NUEVO) {
                        currentTab = ScreenTab.METRICAS
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp
                        ) {
                            NavigationBarItem(
                                selected = currentTab == ScreenTab.INICIO,
                                onClick = { irA(ScreenTab.INICIO) },
                                icon = { Icon(Icons.Default.Home, contentDescription = "Inicio") },
                                label = { Text("Inicio", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            )
                            if (isCurrentMonth) {
                                NavigationBarItem(
                                    selected = currentTab == ScreenTab.NUEVO,
                                    onClick = { irA(ScreenTab.NUEVO) },
                                    icon = { Icon(Icons.Default.Add, contentDescription = "Nuevo") },
                                    label = { Text("Nuevo", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                )
                            }
                            NavigationBarItem(
                                selected = currentTab == ScreenTab.CUOTAS,
                                onClick = { irA(ScreenTab.CUOTAS) },
                                icon = { Icon(Icons.Default.CreditCard, contentDescription = "Cuotas") },
                                label = { Text("Cuotas", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            )
                            NavigationBarItem(
                                selected = currentTab == ScreenTab.METRICAS,
                                onClick = { irA(ScreenTab.METRICAS) },
                                icon = { Icon(Icons.Default.Info, contentDescription = "Métricas") },
                                label = { Text("Métricas", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            )
                            NavigationBarItem(
                                selected = currentTab == ScreenTab.AJUSTES,
                                onClick = { irA(ScreenTab.AJUSTES) },
                                icon = { Icon(Icons.Default.Settings, contentDescription = "Ajustes") },
                                label = { Text("Ajustes", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (currentTab) {
                            ScreenTab.INICIO -> {
                                DashboardScreen(
                                    userProfile = userProfile,
                                    config = usuarios,
                                    balance = balance,
                                    movements = movements,
                                    isLoading = isLoading,
                                    useLocalDemo = useLocalDemo,
                                    onRefresh = { viewModel.refreshData() },
                                    onDeleteMovement = { viewModel.deleteMovement(it) },
                                    onDuplicateMovement = { viewModel.duplicateMovement(it) {} },
                                    availableMonths = viewModel.availableMonths.collectAsState().value,
                                    selectedMonth = selectedMonth,
                                    isCurrentMonth = isCurrentMonth,
                                    onMonthSelected = { viewModel.setSelectedMonth(it) },
                                    plans = plans,
                                    allMovements = allMovements,
                                    onConfirmCuota = { plan, numero, fecha, metodo, monto, onSuccess ->
                                        viewModel.confirmarCuota(plan, numero, fecha, metodo, monto, onSuccess = onSuccess)
                                    },
                                    filters = dashboardFilters,
                                    onFiltersChange = { viewModel.setDashboardFilters(it) },
                                    pendingStates = pendingStates,
                                    scrollAFiltros = scrollAFiltros,
                                    onScrollAFiltrosConsumido = { viewModel.consumirScrollAFiltros() }
                                )
                            }
                            ScreenTab.NUEVO -> {
                                AddMovementScreen(
                                    viewModel = viewModel,
                                    onSuccess = {
                                        irA(ScreenTab.INICIO)
                                    }
                                )
                            }
                            ScreenTab.CUOTAS -> {
                                CuotasScreen(viewModel = viewModel)
                            }
                            ScreenTab.METRICAS -> {
                                ReportsScreen(
                                    userProfile = userProfile,
                                    config = usuarios,
                                    balance = balance,
                                    movements = movements,
                                    availableMonths = viewModel.availableMonths.collectAsState().value,
                                    selectedMonth = selectedMonth,
                                    onMonthSelected = { viewModel.setSelectedMonth(it) },
                                    plans = plans,
                                    allMovements = allMovements,
                                    // Tocar una categoría en Métricas = "quiero ver el detalle":
                                    // deja el filtro puesto y salta a Inicio. Sin cambios de UI.
                                    onVerDetalle = { persona, categoria ->
                                        viewModel.verDetalleDeGastos(persona, categoria)
                                        irA(ScreenTab.INICIO)
                                    }
                                )
                            }
                            ScreenTab.AJUSTES -> {
                                SettingsScreen(
                                    viewModel = viewModel
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumirIntent(intent)
    }

    /**
     * Lee lo que trae un intent externo: a qué pestaña saltar y, si viene de la detección de montos
     * (ver [com.example.data.notifications.MontoDetectadoNotifier]), con qué importe sembrar el alta.
     *
     * El monto se siembra en el borrador del ViewModel y no se pasa por parámetro a la pantalla: es
     * el mismo canal que ya usaba el formulario para sobrevivir al cambio de pestaña, así que el
     * alta no necesita ninguna UI nueva.
     */
    private fun consumirIntent(intent: Intent?) {
        intent?.getStringExtra(EXTRA_MONTO_SUGERIDO)?.takeIf { it.isNotBlank() }?.let { monto ->
            viewModel.sugerirMonto(monto)
        }
        pendingOpenTab = tabFromIntent(intent)
    }

    companion object {
        const val EXTRA_OPEN_TAB = "open_tab"

        /**
         * Importe detectado en la notificación de una billetera, listo para precargar en el alta.
         * Viaja como texto con punto decimal (el formato que espera el campo del formulario).
         */
        const val EXTRA_MONTO_SUGERIDO = "monto_sugerido"

        private fun tabFromIntent(intent: Intent?): ScreenTab? =
            intent?.getStringExtra(EXTRA_OPEN_TAB)?.let { name ->
                runCatching { ScreenTab.valueOf(name) }.getOrNull()
            }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}
