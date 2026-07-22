package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import com.example.ui.AhorroViewModel
import com.example.ui.components.AddMovementScreen
import com.example.ui.components.CuotasScreen
import com.example.ui.components.DashboardScreen
import com.example.ui.components.ReportsScreen
import com.example.ui.components.SettingsScreen
import com.example.ui.theme.MyApplicationTheme

enum class ScreenTab {
    INICIO, NUEVO, CUOTAS, METRICAS, AJUSTES
}

class MainActivity : ComponentActivity() {

    private val viewModel: AhorroViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            val userProfile by viewModel.currentUserProfile.collectAsState()
            val isDarkMode by viewModel.isDarkMode.collectAsState()
            
            MyApplicationTheme(darkTheme = isDarkMode, isRocio = userProfile == "Rocío") {
                var currentTab by remember { mutableStateOf(ScreenTab.INICIO) }
                
                val movements by viewModel.movements.collectAsState()
                val balance by viewModel.balance.collectAsState()
                val isLoading by viewModel.isLoading.collectAsState()
                val useLocalDemo by viewModel.useLocalDemo.collectAsState()
                val isCurrentMonth by viewModel.isCurrentMonth.collectAsState()
                val plans by viewModel.plans.collectAsState()
                val allMovements by viewModel.allMovements.collectAsState()
                val selectedMonth by viewModel.selectedMonth.collectAsState()

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
                                onClick = { currentTab = ScreenTab.INICIO },
                                icon = { Icon(Icons.Default.Home, contentDescription = "Inicio") },
                                label = { Text("Inicio", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            )
                            if (isCurrentMonth) {
                                NavigationBarItem(
                                    selected = currentTab == ScreenTab.NUEVO,
                                    onClick = { currentTab = ScreenTab.NUEVO },
                                    icon = { Icon(Icons.Default.Add, contentDescription = "Nuevo") },
                                    label = { Text("Nuevo", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                )
                            }
                            NavigationBarItem(
                                selected = currentTab == ScreenTab.CUOTAS,
                                onClick = { currentTab = ScreenTab.CUOTAS },
                                icon = { Icon(Icons.Default.CreditCard, contentDescription = "Cuotas") },
                                label = { Text("Cuotas", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            )
                            NavigationBarItem(
                                selected = currentTab == ScreenTab.METRICAS,
                                onClick = { currentTab = ScreenTab.METRICAS },
                                icon = { Icon(Icons.Default.Info, contentDescription = "Métricas") },
                                label = { Text("Métricas", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            )
                            NavigationBarItem(
                                selected = currentTab == ScreenTab.AJUSTES,
                                onClick = { currentTab = ScreenTab.AJUSTES },
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
                                    }
                                )
                            }
                            ScreenTab.NUEVO -> {
                                AddMovementScreen(
                                    viewModel = viewModel,
                                    onSuccess = {
                                        currentTab = ScreenTab.INICIO
                                    }
                                )
                            }
                            ScreenTab.CUOTAS -> {
                                CuotasScreen(viewModel = viewModel)
                            }
                            ScreenTab.METRICAS -> {
                                ReportsScreen(
                                    userProfile = userProfile,
                                    balance = balance,
                                    movements = movements,
                                    availableMonths = viewModel.availableMonths.collectAsState().value,
                                    selectedMonth = selectedMonth,
                                    onMonthSelected = { viewModel.setSelectedMonth(it) },
                                    plans = plans,
                                    allMovements = allMovements
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
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}
