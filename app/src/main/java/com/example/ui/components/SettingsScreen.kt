package com.example.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CuotaPlan
import com.example.data.Movement
import com.example.data.Usuario
import com.example.data.notifications.CuotasNotifier
import com.example.domain.CuotasEngine
import com.example.ui.AhorroViewModel
import com.example.ui.components.common.AppCard
import com.example.ui.components.common.FlowRow
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.USER_COLOR_PRESETS
import com.example.ui.theme.presetOf
import com.example.util.Fechas
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: AhorroViewModel
) {
    val scrollState = rememberScrollState()

    val useLocalDemo by viewModel.useLocalDemo.collectAsState()
    val currentUserProfile by viewModel.currentUserProfile.collectAsState()
    val config by viewModel.usuarios.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val plans by viewModel.plans.collectAsState()
    val allMovements by viewModel.allMovements.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val tieneSaldoInicial by viewModel.tieneSaldoInicial.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuración", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Tarjeta de Perfil (self): "Sos {nombre}", editar nombre, elegir color, cerrar sesión.
            val me = config.byKey(currentUserProfile) ?: config.primario
            PerfilCard(
                me = me,
                coloresDeLosDemas = config.otros(me.slotKey).map { it.colorId }.toSet(),
                onNombreChange = { viewModel.updateMiNombre(it) },
                onColorChange = { viewModel.updateMiColor(it) },
                onLogout = { viewModel.logout() }
            )

            TarjetaConSwitch(
                titulo = "Modo Oscuro",
                descripcion = "Cambiar entre paleta oscura y clara",
                activado = isDarkMode,
                onCambio = { viewModel.setIsDarkMode(it) }
            )

            TarjetaConSwitch(
                titulo = "Modo Local (Demo)",
                descripcion = "Usa la memoria interna sin conexión de forma totalmente funcional.",
                activado = useLocalDemo,
                onCambio = { viewModel.setUseLocalDemo(it) }
            )

            // Tarjeta de Saldo inicial del mes (arrastre materializado)
            SaldoInicialCard(
                mes = selectedMonth,
                tieneSaldoInicial = tieneSaldoInicial,
                isLoading = isLoading,
                onRecalcular = { onDone -> viewModel.recalcularSaldoInicial(onDone) }
            )

            // Un error de red igual tiene que ser visible.
            errorMessage?.let { msg ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                        .padding(12.dp)
                ) {
                    Text(
                        text = msg,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }

            // Tarjeta de prueba de Notificaciones de Cuotas (ver features/notificaciones-cuotas.md)
            CuotasNotificationTestCard(
                plans = plans,
                allMovements = allMovements,
                currentUserProfile = currentUserProfile
            )

            // Información sobre reglas
            AppCard(
                radio = 24.dp,
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Los aportes aumentan el saldo del aportante. Los gastos personales reducen solo su saldo. Los gastos compartidos se dividen 50% cada uno.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

/** Tarjeta de Ajustes con un título, una descripción y un switch a la derecha. */
@Composable
private fun TarjetaConSwitch(
    titulo: String,
    descripcion: String,
    activado: Boolean,
    onCambio: (Boolean) -> Unit
) {
    AppCard(radio = 24.dp, elevacion = 1.dp) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = titulo,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = descripcion,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = activado,
                    onCheckedChange = onCambio,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
                )
            }
        }
    }
}

/**
 * Saldo inicial del mes. Cuando el mes tiene sus filas de apertura escritas, su cálculo deja de
 * depender de las hojas anteriores (y esas hojas se pueden archivar). Recalcular vuelve a derivar
 * el arrastre de los meses previos: hay que hacerlo si se corrigió un movimiento viejo.
 */
@Composable
private fun SaldoInicialCard(
    mes: String,
    tieneSaldoInicial: Boolean,
    isLoading: Boolean,
    onRecalcular: ((Boolean, String) -> Unit) -> Unit
) {
    var mensaje by remember { mutableStateOf<String?>(null) }

    AppCard(radio = 24.dp, elevacion = 1.dp) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Saldo inicial del mes",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (tieneSaldoInicial) {
                    "$mes tiene su saldo inicial escrito en la hoja. El cálculo de este mes no " +
                        "depende de las hojas anteriores."
                } else {
                    "$mes todavía no tiene saldo inicial: el arrastre se está derivando de los " +
                        "meses anteriores, así que esas hojas no se pueden archivar."
                },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Recalculalo si corregiste un movimiento de un mes anterior.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = {
                    mensaje = null
                    onRecalcular { _, msg -> mensaje = msg }
                },
                enabled = !isLoading && mes.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (tieneSaldoInicial) "Recalcular saldo inicial" else "Escribir saldo inicial")
            }
            mensaje?.let {
                Text(
                    text = it,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Tarjeta de prueba de las notificaciones de cuotas (ver `features/notificaciones-cuotas.md`).
 * Dispara [CuotasNotifier] directo con los datos YA CARGADOS en el ViewModel — sin el gating de
 * fecha (último día del mes / lunes) ni la deduplicación que sí aplica el Worker real — para poder
 * validar canal, permiso y texto sin esperar al horario programado.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun CuotasNotificationTestCard(
    plans: List<CuotaPlan>,
    allMovements: List<Movement>,
    currentUserProfile: String
) {
    val context = LocalContext.current
    val notifPermissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(android.Manifest.permission.POST_NOTIFICATIONS)
    } else null
    val permisoConcedido = notifPermissionState == null || notifPermissionState.status.isGranted

    val mesActual = Fechas.mesActual()
    val misPlanes = remember(plans, currentUserProfile) {
        plans.filter { it.propietario.equals(currentUserProfile, ignoreCase = true) }
    }

    AppCard(radio = 24.dp, elevacion = 1.dp) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Notificaciones de cuotas (prueba)",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Usan los datos ya cargados, no reflejan el horario real. El recordatorio automático corre solo (último día del mes y todos los lunes, 9am hora Argentina).",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!permisoConcedido) {
                Button(
                    onClick = { notifPermissionState?.launchPermissionRequest() },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                ) {
                    Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Habilitar notificaciones", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedButton(
                    onClick = {
                        val total = CuotasEngine.cuotasImpagasDelMes(mesActual, misPlanes, allMovements)
                            .sumOf { (_, cuota) -> cuota.monto }
                        CuotasNotifier.notificarCierreDeMes(context, total, mesActual)
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                ) {
                    Text("Probar recordatorio de cierre de mes", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        val total = CuotasEngine.recordatoriosDelMes(mesActual, misPlanes, allMovements)
                            .filter { it.atrasada }
                            .sumOf { it.cuota.monto }
                        CuotasNotifier.notificarAtrasos(context, total)
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                ) {
                    Text("Probar recordatorio de atrasos", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Tarjeta de perfil propio: muestra quién sos, deja editar tu nombre, elegir tu color (grilla de
 * presets, deshabilitando los que ya usan los demás para no perder la distinción visual) y cerrar
 * sesión.
 */
@Composable
private fun PerfilCard(
    me: Usuario,
    coloresDeLosDemas: Set<String>,
    onNombreChange: (String) -> Unit,
    onColorChange: (String) -> Unit,
    onLogout: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    // El input se re-sincroniza si el nombre cambia desde afuera (p. ej. tras guardar / refrescar).
    var nombreInput by remember(me.nombre) { mutableStateOf(me.nombre) }
    val nombreCambiado = nombreInput.isNotBlank() && nombreInput.trim() != me.nombre
    val miColor = presetOf(me.colorId).resolve(isDark)

    AppCard(radio = 24.dp, elevacion = 1.dp) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: "Sos {nombre}"
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(miColor.brand),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = me.nombre.take(1).uppercase(),
                        color = miColor.on,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp
                    )
                }
                Column {
                    Text(
                        text = "Tu perfil",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Sos ${me.nombre}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Editar nombre
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Tu nombre",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = nombreInput,
                        onValueChange = { nombreInput = it },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { onNombreChange(nombreInput) },
                        enabled = nombreCambiado,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Guardar nombre", modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Elegir color (grilla de presets; los que usan los demás quedan deshabilitados)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Tu color",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    USER_COLOR_PRESETS.forEach { preset ->
                        val selected = preset.id == me.colorId
                        // El color de otro usuario no se puede elegir (mantiene la distinción visual).
                        val disabled = !selected && preset.id in coloresDeLosDemas
                        val resuelto = preset.resolve(isDark)
                        ColorSwatch(
                            fill = resuelto.brand,
                            // El "on" del propio preset: los rellenos del tema oscuro son pasteles
                            // y un tilde blanco encima no se veía.
                            onFill = resuelto.on,
                            selected = selected,
                            disabled = disabled,
                            onClick = { if (!disabled && !selected) onColorChange(preset.id) }
                        )
                    }
                }
                Text(
                    text = "El color del otro usuario aparece deshabilitado para no repetirlo.",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Cerrar sesión
            OutlinedButton(
                onClick = onLogout,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cerrar sesión", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

/** Muestra circular de color para la grilla de selección. */
@Composable
private fun ColorSwatch(
    fill: Color,
    onFill: Color,
    selected: Boolean,
    disabled: Boolean,
    onClick: () -> Unit
) {
    val ring = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (disabled) fill.copy(alpha = 0.25f) else fill)
            .then(
                if (selected) Modifier.border(3.dp, ring, CircleShape)
                else Modifier
            )
            .clickable(enabled = !disabled && !selected) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Color seleccionado",
                tint = onFill
            )
        } else if (disabled) {
            Icon(
                imageVector = Icons.Default.Block,
                contentDescription = "No disponible",
                tint = onFill.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
