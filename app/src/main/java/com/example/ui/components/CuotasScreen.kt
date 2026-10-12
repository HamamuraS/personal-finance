package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.data.TipoMovimiento
import com.example.ui.AhorroViewModel

/** Sub-pantallas de la pestaña Cuotas. Cada una tiene su `BackHandler` que vuelve al listado. */
private sealed interface CuotasRoute {
    data object List : CuotasRoute
    data class Detail(val planId: String) : CuotasRoute
    data class Form(val planId: String?) : CuotasRoute
    data class TarjetaResumen(val tarjeta: String) : CuotasRoute
}

@Composable
fun CuotasScreen(viewModel: AhorroViewModel) {
    val plans by viewModel.plans.collectAsState()
    val paidPlans by viewModel.paidPlans.collectAsState()
    val allMovements by viewModel.allMovements.collectAsState()
    val currentUser by viewModel.currentUserProfile.collectAsState()
    val config by viewModel.usuarios.collectAsState()
    val cuotaDraft by viewModel.cuotaDraft.collectAsState()
    val showPaid by viewModel.showPaidPlans.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingPaid by viewModel.isLoadingPaidPlans.collectAsState()

    // Si hay un borrador de "nueva compra" en curso, al volver a la pestaña se reabre el formulario
    // (conservación del borrador entre pestañas). Si no, arranca en el listado.
    var route by remember {
        mutableStateOf<CuotasRoute>(if (cuotaDraft.tieneContenido) CuotasRoute.Form(null) else CuotasRoute.List)
    }

    when (val r = route) {
        is CuotasRoute.List -> CuotasListContent(
            plans = plans,
            paidPlans = paidPlans,
            allMovements = allMovements,
            currentUser = currentUser,
            config = config,
            showPaid = showPaid,
            isLoading = isLoading,
            isLoadingPaid = isLoadingPaid,
            onRefresh = { viewModel.refreshData() },
            onToggleShowPaid = { viewModel.setShowPaidPlans(it) },
            onOpenPlan = { route = CuotasRoute.Detail(it.id) },
            onNewPlan = { route = CuotasRoute.Form(null) },
            onOpenTarjeta = { route = CuotasRoute.TarjetaResumen(it) }
        )

        is CuotasRoute.TarjetaResumen -> {
            BackHandler { route = CuotasRoute.List }
            TarjetaResumenContent(
                tarjeta = r.tarjeta,
                plans = plans,
                allMovements = allMovements,
                currentUser = currentUser,
                isLoading = isLoading,
                onBack = { route = CuotasRoute.List },
                onPagar = { cuotas, metodo ->
                    viewModel.pagarCuotas(cuotas, metodo) { route = CuotasRoute.List }
                }
            )
        }

        is CuotasRoute.Detail -> {
            val plan = (plans + paidPlans).firstOrNull { it.id == r.planId }
            if (plan == null) {
                LaunchedEffect(Unit) { route = CuotasRoute.List }
            } else {
                BackHandler { route = CuotasRoute.List }
                PlanDetailContent(
                    plan = plan,
                    allMovements = allMovements,
                    currentUser = currentUser,
                    config = config,
                    canManage = viewModel.puedeGestionar(plan),
                    isLoading = isLoading,
                    onBack = { route = CuotasRoute.List },
                    onConfirmCuota = { numero, fecha, metodo, monto, onSuccess ->
                        viewModel.confirmarCuota(plan, numero, fecha, metodo, monto, onSuccess = onSuccess)
                    },
                    onEdit = { route = CuotasRoute.Form(plan.id) },
                    onDelete = { viewModel.deletePlan(plan) { route = CuotasRoute.List } }
                )
            }
        }

        is CuotasRoute.Form -> {
            val existing = r.planId?.let { id -> (plans + paidPlans).firstOrNull { it.id == id } }
            BackHandler { route = CuotasRoute.List }
            PlanFormContent(
                existing = existing,
                historialCategorias = remember(allMovements) {
                    allMovements
                        .filter { !it.eliminado && it.tipo.equals(TipoMovimiento.GASTO, ignoreCase = true) }
                        .map { it.categoria }
                },
                currentUser = currentUser,
                config = config,
                isLoading = isLoading,
                draft = cuotaDraft,
                onDraftChange = { viewModel.setCuotaDraft(it) },
                onClearDraft = { viewModel.clearCuotaDraft() },
                onBack = { route = CuotasRoute.List },
                onSave = { descripcion, monto, cant, primera, propietario, categoria, tarjeta, evitable ->
                    if (existing == null) {
                        viewModel.addPlan(descripcion, monto, cant, primera, propietario, categoria, tarjeta, evitable) {
                            viewModel.clearCuotaDraft()   // borrador consumido al crear
                            route = CuotasRoute.List
                        }
                    } else {
                        viewModel.updatePlan(
                            existing.copy(
                                descripcion = descripcion,
                                montoPorCuota = monto,
                                cantidadCuotas = cant,
                                fechaPrimeraCuota = primera,
                                propietario = propietario,
                                categoria = categoria,
                                tarjeta = tarjeta,
                                // No reescribe los pagos ya hechos: cada pago copió el valor al pagarse.
                                evitable = evitable
                            )
                        ) { route = CuotasRoute.List }
                    }
                }
            )
        }
    }
}
