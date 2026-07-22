package com.example.ui.components

import android.app.DatePickerDialog
import android.widget.DatePicker
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.ui.AhorroViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class NumberCommaVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val original = text.text
        if (original.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        
        val parts = original.split(".")
        val intPart = parts[0]
        val fracPart = if (parts.size > 1) parts[1] else null
        
        val formattedInt = java.lang.StringBuilder()
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
                if (offset <= intLen) {
                    val dotsPassed = (intLen - 1) / 3 - (intLen - offset) / 3
                    return offset + dotsPassed
                } else {
                    val dotsInTotal = (intLen - 1) / 3
                    return offset + dotsInTotal
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun AddMovementScreen(
    viewModel: AhorroViewModel,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollState = rememberScrollState()

    val currentUserProfile by viewModel.currentUserProfile.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    // Estados locales del formulario
    var monto by remember { mutableStateOf("") }
    var tipo by remember { mutableStateOf("Gasto") } // "Gasto", "Aporte", "Transferencia"
    var esComun by remember { mutableStateOf(false) } // personal por defecto
    val responsable = currentUserProfile // Siempre el usuario actual
    var metodoPago by remember { mutableStateOf("Billetera Virtual") } // "Efectivo", "Billetera Virtual"
    var propietario by remember { mutableStateOf(currentUserProfile) }
    var descripcion by remember { mutableStateOf("") }
    var ticketUri by remember { mutableStateOf<Uri?>(null) }
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }

    // Fecha por defecto: hoy en formato YYYY-MM-DD HH:mm
    val currentCalendar = remember { Calendar.getInstance() }
    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.US) }
    var fecha by remember { mutableStateOf(dateFormatter.format(currentCalendar.time)) }
    var hora by remember { mutableStateOf(timeFormatter.format(currentCalendar.time)) }

    // Categorías basadas en Tipo y Comunalidad
    val listAportes = listOf("Sueldo", "Transferencias", "Otros")
    val listGastos = listOf(
        "Transporte", "Servicios", "Animales", "Supermercado", "Verdulería",
        "Farmacia", "Cuidado personal", "Salidas", "Gustos", "Utilería", "Otros"
    )
    val listTransferencias = listOf("Ajuste", "Reembolso", "Otros")

    var categoria by remember { mutableStateOf("") }

    val cameraPermissionState = rememberPermissionState(
        android.Manifest.permission.CAMERA
    )

    // Launchers para Cámara y Galería
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            ticketUri = tempPhotoUri
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            ticketUri = uri
        }
    }

    // Resetear categoría cuando cambia tipo o esComun
    LaunchedEffect(tipo, esComun, currentUserProfile) {
        categoria = when (tipo) {
            "Aporte" -> "Sueldo"
            "Transferencia" -> listTransferencias.first()
            "Gasto" -> listGastos.first()
            else -> "Otros"
        }
        
        // Predeterminar propietario
        propietario = when {
            tipo == "Gasto" && esComun -> "Ambos"
            else -> currentUserProfile // Aporte, Transferencia (sigue siendo mía) y Gasto personal
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Registrar Movimiento", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
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
            // Monto (Tipo Cajero)
            val isDark = MaterialTheme.colorScheme.background == com.example.ui.theme.DarkBackground
            val cardBg = if (isDark) MaterialTheme.colorScheme.surface else Color.White
            val cardBorder = if (isDark) Color(0xFF333833) else Color(0xFFE2E8F0)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "MONTO DEL MOVIMIENTO",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    
                    TextField(
                        value = monto,
                        onValueChange = { input ->
                            val normalized = input.replace(',', '.')
                            if (normalized.all { it.isDigit() || it == '.' } && normalized.count { it == '.' } <= 1) {
                                val parts = normalized.split(".")
                                if (parts.size <= 1 || parts[1].length <= 2) {
                                    monto = normalized
                                }
                            }
                        },
                        visualTransformation = NumberCommaVisualTransformation(),
                        textStyle = MaterialTheme.typography.headlineLarge.copy(
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 32.sp
                        ),
                        placeholder = {
                            Text(
                                "0",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { keyboardController?.hide() }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Tipo de Movimiento (Fila de Botones)
            Column {
                Text(
                    text = "Tipo de Movimiento",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Gasto", "Aporte", "Transferencia").forEach { item ->
                        val isSelected = tipo == item
                        val label = if (item == "Transferencia") "Transf." else item
                        Button(
                            onClick = { tipo = item },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }

            // Método de Pago (Efectivo o Billetera Virtual)
            Column {
                Text(
                    text = "Método de Pago",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Billetera Virtual", "Efectivo").forEach { item ->
                        val isSelected = metodoPago == item
                        Button(
                            onClick = { metodoPago = item },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Text(item, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Si es Gasto -> Compartido vs Personal
            if (tipo == "Gasto") {
                Column {
                    Text(
                        text = "Distribución del Gasto",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { 
                                esComun = true
                                propietario = "Ambos"
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (esComun) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                contentColor = if (esComun) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(12.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text("Común (50/50)", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text("Afecta a ambos", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), maxLines = 1)
                            }
                        }

                        Button(
                            onClick = { 
                                esComun = false
                                propietario = currentUserProfile
                            },
                            colors = ButtonDefaults.buttonColors(
                                // "Personal" = paga el usuario activo → su propio color, que en el
                                // tema activo siempre es `primary` (no hace falta invertir a mano).
                                containerColor = if (!esComun) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                contentColor = if (!esComun) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(12.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text("Personal", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text("Paga solo $responsable", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), maxLines = 1)
                            }
                        }
                    }
                }
            }

            // Propiedad del Dinero (Solo si no es Común)
            if (!(tipo == "Gasto" && esComun)) {
                Column {
                    val label = when(tipo) {
                        "Aporte" -> "¿En qué cuenta entra?"
                        "Transferencia" -> "¿La plata sigue siendo de $currentUserProfile?"
                        else -> "¿Quién debe pagar realmente?"
                    }
                    Text(
                        text = label,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val otherUser = if (currentUserProfile == "Santiago") "Rocío" else "Santiago"
                        
                        // Opción 1: Mío (o sigue siendo mío)
                        val isMine = propietario == currentUserProfile
                        Button(
                            onClick = { propietario = currentUserProfile },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isMine) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                contentColor = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(12.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Text(if (tipo == "Transferencia") "Sí, es mía" else "Mío", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        // Opción 2: Del otro (o cambia de dueño)
                        val isOthers = propietario == otherUser
                        Button(
                            onClick = { propietario = otherUser },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isOthers) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                contentColor = if (isOthers) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(12.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            val text = when(tipo) {
                                "Transferencia" -> "No, es de $otherUser"
                                "Aporte" -> "De $otherUser"
                                else -> "De $otherUser"
                            }
                            Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Categoría (Chips seleccionables dinámicos)
            Column {
                Text(
                    text = "Categoría",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                val items = when (tipo) {
                    "Aporte" -> listAportes
                    "Transferencia" -> listTransferencias
                    else -> listGastos
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items.forEach { cat ->
                        val isSelected = categoria == cat
                        FilterChip(
                            selected = isSelected,
                            onClick = { categoria = cat },
                            label = { Text(cat, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)
                            )
                        )
                    }
                }
            }

            // Fecha con Atajos (Hoy / Ayer / Lanzar dialog)
            Column {
                Text(
                    text = "Fecha del Movimiento",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Botón para hoy
                    val hoyString = remember { dateFormatter.format(Date()) }
                    val calAyer = remember { Calendar.getInstance().apply { add(Calendar.DATE, -1) } }
                    val ayerString = remember { dateFormatter.format(calAyer.time) }

                    Button(
                        onClick = { fecha = hoyString },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (fecha == hoyString) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                            contentColor = if (fecha == hoyString) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(10.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                    ) {
                        Text("Hoy", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { fecha = ayerString },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (fecha == ayerString) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                            contentColor = if (fecha == ayerString) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(10.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                    ) {
                        Text("Ayer", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // Selector manual de Fecha
                    OutlinedButton(
                        onClick = {
                            val parts = fecha.split("-")
                            val y = parts.getOrNull(0)?.toIntOrNull() ?: currentCalendar.get(Calendar.YEAR)
                            val m = (parts.getOrNull(1)?.toIntOrNull() ?: (currentCalendar.get(Calendar.MONTH) + 1)) - 1
                            val d = parts.getOrNull(2)?.toIntOrNull() ?: currentCalendar.get(Calendar.DAY_OF_MONTH)

                            DatePickerDialog(context, { _: DatePicker, year: Int, month: Int, dayOfMonth: Int ->
                                val newCal = Calendar.getInstance().apply {
                                    set(Calendar.YEAR, year)
                                    set(Calendar.MONTH, month)
                                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                }
                                fecha = dateFormatter.format(newCal.time)
                            }, y, m, d).show()
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1.5f)
                            .height(38.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(fecha, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Descripción (Opcional)
            Column {
                Text(
                    text = "Descripción (Opcional)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                OutlinedTextField(
                    value = descripcion,
                    onValueChange = { descripcion = it },
                    placeholder = { Text("Ej. Supermercado mensual, compra abrigo...", fontSize = 13.sp) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)
                    ),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { keyboardController?.hide() }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Adjuntar Ticket
            Column {
                Text(
                    text = "Adjuntar Ticket (Opcional)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Botón Cámara
                    OutlinedButton(
                        onClick = {
                            if (cameraPermissionState.status.isGranted) {
                                val imagesDir = File(context.cacheDir, "images")
                                if (!imagesDir.exists()) imagesDir.mkdirs()
                                val tempFile = File(imagesDir, "temp_ticket_${System.currentTimeMillis()}.jpg")
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    tempFile
                                )
                                tempPhotoUri = uri
                                cameraLauncher.launch(uri)
                            } else {
                                cameraPermissionState.launchPermissionRequest()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cámara", fontSize = 12.sp)
                    }

                    // Botón Galería
                    OutlinedButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.Collections, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Galería", fontSize = 12.sp)
                    }
                }

                if (ticketUri != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = ticketUri,
                            contentDescription = "Ticket seleccionado",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                        IconButton(
                            onClick = { ticketUri = null },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Eliminar", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Botón de Registro de un solo tap
            val useLocalDemo by viewModel.useLocalDemo.collectAsState()
            Button(
                onClick = {
                    if (isLoading) return@Button
                    val doubleMonto = monto.toDoubleOrNull() ?: 0.0
                    if (doubleMonto <= 0.0) {
                        return@Button
                    }

                    viewModel.addMovement(
                        fecha = "$fecha $hora",
                        monto = doubleMonto,
                        tipo = tipo,
                        categoria = categoria,
                        responsable = responsable,
                        esComun = if (tipo == "Gasto") esComun else false,
                        propietario = propietario,
                        descripcion = descripcion,
                        metodoPago = metodoPago,
                        ticketUri = ticketUri,
                        onSuccess = {
                            monto = ""
                            descripcion = ""
                            ticketUri = null
                            onSuccess()
                        }
                    )
                },
                enabled = monto.isNotEmpty() && monto.toDoubleOrNull() != null && (monto.toDoubleOrNull() ?: 0.0) > 0 && !isLoading,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Registrar en " + if (useLocalDemo) "Base Local" else "Nube", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                }
            }
        }
    }
}

// Un simple FlowRow para organizar dinámicamente los chips sin librerías externas
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    androidx.compose.ui.layout.Layout(
        modifier = modifier,
        content = content
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
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
                currentY += maxRowHeight + verticalArrangement.let { 6.dp.roundToPx() } // Valor aproximado
                maxRowHeight = 0
            }
            currentRow.add(placeable)
            currentX += placeable.width + horizontalArrangement.let { 6.dp.roundToPx() } // Valor aproximado
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
                    xOffset += placeable.width + 6.dp.roundToPx()
                    rowMaxHeight = maxOf(rowMaxHeight, placeable.height)
                }
                yOffset += rowMaxHeight + 6.dp.roundToPx()
            }
        }
    }
}
