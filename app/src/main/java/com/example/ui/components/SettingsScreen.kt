package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Usuario
import com.example.ui.AhorroViewModel
import com.example.ui.theme.USER_COLOR_PRESETS
import com.example.ui.theme.presetOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: AhorroViewModel
) {
    val scrollState = rememberScrollState()

    // Configuración del Sheets
    val scriptUrlState = viewModel.spreadsheetId.collectAsState()
    val folderIdState = viewModel.folderId.collectAsState()
    val useLocalDemoState = viewModel.useLocalDemo.collectAsState()
    val currentUserProfileState = viewModel.currentUserProfile.collectAsState()
    val usuariosState = viewModel.usuarios.collectAsState()
    val isDarkModeState = viewModel.isDarkMode.collectAsState()
    val isLoadingState = viewModel.isLoading.collectAsState()
    val errorMessageState = viewModel.errorMessage.collectAsState()

    var scriptInput by remember { mutableStateOf(scriptUrlState.value) }
    var folderInput by remember { mutableStateOf(folderIdState.value) }
    var demoToggle by remember { mutableStateOf(useLocalDemoState.value) }

    // Sincronizar inputs si cambian de afuera
    LaunchedEffect(scriptUrlState.value) {
        if (scriptInput.isEmpty()) {
            scriptInput = scriptUrlState.value
        }
    }
    LaunchedEffect(folderIdState.value) {
        if (folderInput.isEmpty()) {
            folderInput = folderIdState.value
        }
    }
    LaunchedEffect(useLocalDemoState.value) {
        demoToggle = useLocalDemoState.value
    }

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
            val isDark = MaterialTheme.colorScheme.background == com.example.ui.theme.DarkBackground
            val cardBg = if (isDark) MaterialTheme.colorScheme.surface else Color.White
            val cardBorder = if (isDark) Color(0xFF333833) else Color(0xFFE2E8F0)

            // Tarjeta de Perfil (self): "Sos {nombre}", editar nombre, elegir color, cerrar sesión.
            val config = usuariosState.value
            val me = config.byKey(currentUserProfileState.value) ?: config.primario
            val otherColorId = config.elOtro(me.slotKey).colorId
            PerfilCard(
                me = me,
                otherColorId = otherColorId,
                isDark = isDark,
                cardBg = cardBg,
                cardBorder = cardBorder,
                onNombreChange = { viewModel.updateMiNombre(it) },
                onColorChange = { viewModel.updateMiColor(it) },
                onLogout = { viewModel.logout() }
            )

            // Tarjeta de Modo Oscuro / Claro
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Modo Oscuro",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Cambiar entre paleta oscura y clara",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                        Switch(
                            checked = isDarkModeState.value,
                            onCheckedChange = {
                                viewModel.setIsDarkMode(it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            )
                        )
                    }
                }
            }

            // Tarjeta de Modo de Operación (Demo vs Hojas)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Modo Local (Demo)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Usa la memoria interna sin conexión de forma totalmente funcional.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                        Switch(
                            checked = demoToggle,
                            onCheckedChange = {
                                demoToggle = it
                                viewModel.saveSheetsConfig(scriptInput, folderInput, it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            )
                        )
                    }
                }
            }

            // Tarjeta de Sincronización Web App
            AnimatedVisibility(
                visible = !demoToggle,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Sincronización Permanente en la Nube",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // URL de Sincronización
                        OutlinedTextField(
                            value = scriptInput,
                            onValueChange = { scriptInput = it },
                            label = { Text("URL del Web App (Google Apps Script)", fontSize = 12.sp) },
                            placeholder = { Text("https://script.google.com/macros/s/.../exec", fontSize = 12.sp) },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // ID Carpeta Drive
                        OutlinedTextField(
                            value = folderInput,
                            onValueChange = { folderInput = it },
                            label = { Text("ID Carpeta Google Drive (Tickets)", fontSize = 12.sp) },
                            placeholder = { Text("ID de la carpeta donde se guardan los tickets", fontSize = 12.sp) },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            text = "Para no tener que renovar autenticación jamás, esta app se conecta de forma directa a un Web App. Pega arriba el enlace generado y el ID de la carpeta de Drive.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            lineHeight = 15.sp
                        )

                        // Mensajes de error o éxito de comunicación
                        if (errorMessageState.value != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = errorMessageState.value ?: "",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }

                        Button(
                            onClick = {
                                viewModel.saveSheetsConfig(scriptInput, folderInput, demoToggle)
                            },
                            enabled = scriptInput.isNotEmpty() && folderInput.isNotEmpty() && !isLoadingState.value,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        ) {
                            if (isLoadingState.value) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                            } else {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Guardar y Sincronizar", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Información sobre reglas
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg.copy(alpha = 0.85f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        lineHeight = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

/**
 * Tarjeta de perfil propio (reemplaza al viejo selector "¿Quién está usando la app?"): muestra
 * quién sos, deja editar tu nombre, elegir tu color (grilla de presets, deshabilitando el que usa
 * el otro para no perder la distinción visual) y cerrar sesión.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PerfilCard(
    me: Usuario,
    otherColorId: String,
    isDark: Boolean,
    cardBg: Color,
    cardBorder: Color,
    onNombreChange: (String) -> Unit,
    onColorChange: (String) -> Unit,
    onLogout: () -> Unit
) {
    // El input se re-sincroniza si el nombre cambia desde afuera (p. ej. tras guardar / refrescar).
    var nombreInput by remember(me.nombre) { mutableStateOf(me.nombre) }
    val nombreCambiado = nombreInput.isNotBlank() && nombreInput.trim() != me.nombre
    val miColor = presetOf(me.colorId).resolve(isDark).brand

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
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
                        .background(miColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = me.nombre.take(1).uppercase(),
                        color = presetOf(me.colorId).resolve(isDark).on,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp
                    )
                }
                Column {
                    Text(
                        text = "Tu perfil",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
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
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
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

            // Elegir color (grilla de presets; el que usa el otro queda deshabilitado)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Tu color",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    USER_COLOR_PRESETS.forEach { preset ->
                        val selected = preset.id == me.colorId
                        // El color del otro no se puede elegir (mantiene la distinción visual).
                        val disabled = !selected && preset.id == otherColorId
                        ColorSwatch(
                            fill = preset.resolve(isDark).brand,
                            selected = selected,
                            disabled = disabled,
                            onClick = { if (!disabled && !selected) onColorChange(preset.id) }
                        )
                    }
                }
                Text(
                    text = "El color del otro usuario aparece deshabilitado para no repetirlo.",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            HorizontalDivider(color = if (isDark) Color(0xFF333833) else Color(0xFFF1F5F9))

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
                tint = Color.White
            )
        } else if (disabled) {
            Icon(
                imageVector = Icons.Default.Block,
                contentDescription = "No disponible",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}