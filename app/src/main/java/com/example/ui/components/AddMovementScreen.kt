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
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.data.Categorias
import com.example.ui.AccountingEngine
import com.example.ui.AhorroViewModel
import com.example.ui.MovementDraft
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
    val usuarios by viewModel.usuarios.collectAsState()

    // Nombre visible del usuario activo (para textos); la lógica sigue usando el slotKey.
    val miNombre = usuarios.nombreDe(currentUserProfile)

    // Historial de categorías de más nueva a más vieja (`allMovements` ya viene ordenado así).
    // Alimenta la fila "Frecuentes" del selector de categorías: con el catálogo plegado en
    // carpetas, es lo que evita el tap extra en el caso de todos los días.
    val allMovements by viewModel.allMovements.collectAsState()
    val historialCategorias = remember(allMovements) {
        allMovements.filterNot { it.eliminado }.map { it.categoria }
    }

    // Posición cruzada vigente: es lo que habilita el atajo "perdonar todo" del modo condonación.
    val balance by viewModel.balance.collectAsState()
    val formatMoney = remember {
        java.text.DecimalFormat("#,##0.00").apply {
            val symbols = java.text.DecimalFormatSymbols()
            symbols.groupingSeparator = '.'
            symbols.decimalSeparator = ','
            decimalFormatSymbols = symbols
            positivePrefix = "$ "
            negativePrefix = "-$ "
        }
    }

    // Categorías basadas en Tipo (declaradas antes para derivar defaults del borrador).
    // El catálogo vive en [Categorias]: es el mismo que usa el alta de cuotas.
    fun categoriaDefault(t: String): String = Categorias.defaultDeTipo(t)

    // Fechas base
    val currentCalendar = remember { Calendar.getInstance() }
    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.US) }
    val hoyIso = remember { dateFormatter.format(currentCalendar.time) }
    val horaActual = remember { timeFormatter.format(currentCalendar.time) }

    // --- Borrador persistente: el estado local se SIEMBRA del borrador del VM (que sobrevive al
    // cambio de pestaña) y se VUELCA a él en cada cambio. Campos vacíos usan el default. ---
    val savedDraft = remember { viewModel.movementDraft.value }
    var monto by remember { mutableStateOf(savedDraft.monto) }
    var tipo by remember { mutableStateOf(savedDraft.tipo) } // "Gasto", "Aporte", "Transferencia"
    var esComun by remember { mutableStateOf(savedDraft.esComun) }
    val responsable = currentUserProfile // Siempre el usuario actual (slotKey)
    var metodoPago by remember { mutableStateOf(savedDraft.metodoPago) }
    var propietario by remember { mutableStateOf(savedDraft.propietario.ifEmpty { currentUserProfile }) }
    var descripcion by remember { mutableStateOf(savedDraft.descripcion) }
    var categoria by remember { mutableStateOf(savedDraft.categoria) }
    var fecha by remember { mutableStateOf(savedDraft.fecha.ifEmpty { hoyIso }) }
    var hora by remember { mutableStateOf(savedDraft.hora.ifEmpty { horaActual }) }
    var ticketUri by remember { mutableStateOf(savedDraft.ticketUriString?.let { Uri.parse(it) }) }
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }

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

    // "Perdonar deuda" es un submodo de Transferencia en la UI, pero un tipo propio en la planilla
    // (`AccountingEngine.TIPO_CONDONACION`). Vive en `tipo`, así que el borrador lo arrastra solo.
    val esCondonacion = tipo == AccountingEngine.TIPO_CONDONACION
    val otroSlot = usuarios.elOtro(currentUserProfile).slotKey
    val otroNombre = usuarios.nombreDe(otroSlot)
    // Lo que el otro tiene tuyo hoy. El motor guarda un único neto con signo desde el punto de
    // vista del slot primario, así que hay que leerlo del lado que corresponde al usuario activo.
    val deudaAFavor = if (currentUserProfile == usuarios.primario.slotKey) balance.santiagoExterno
                      else balance.rocioExterno
    val hayDeudaAFavor = deudaAFavor >= 1.0

    // Reset de categoría/propietario SOLO cuando el usuario cambia tipo/comunalidad (no en la primera
    // composición, para no pisar el borrador restaurado).
    var resetInicializado by remember { mutableStateOf(false) }
    LaunchedEffect(tipo, esComun) {
        if (!resetInicializado) {
            resetInicializado = true
            if (categoria.isEmpty()) categoria = categoriaDefault(tipo)
            return@LaunchedEffect
        }
        categoria = categoriaDefault(tipo)
        propietario = when {
            tipo == "Gasto" && esComun -> "Ambos"
            // En una condonación el propietario es el beneficiario: a quién se le perdona.
            tipo == AccountingEngine.TIPO_CONDONACION -> otroSlot
            else -> currentUserProfile
        }
    }

    // Importe llegado desde la notificación de una billetera (ver MontoDetectadoNotifier): se
    // vuelca al campo y se consume, para que volver a esta pestaña más tarde no lo re-imponga
    // encima de lo que el usuario haya escrito. Solo pisa el monto: el resto del alta es manual.
    val montoSugerido by viewModel.montoSugerido.collectAsState()
    LaunchedEffect(montoSugerido) {
        montoSugerido?.let {
            monto = it
            viewModel.consumirMontoSugerido()
        }
    }

    // Volcar el estado al borrador del VM para que sobreviva el cambio de pestaña.
    LaunchedEffect(monto, tipo, esComun, metodoPago, propietario, descripcion, categoria, fecha, ticketUri) {
        viewModel.setMovementDraft(
            MovementDraft(
                monto = monto, tipo = tipo, esComun = esComun, metodoPago = metodoPago,
                propietario = propietario, descripcion = descripcion, fecha = fecha, hora = hora,
                categoria = categoria, ticketUriString = ticketUri?.toString()
            )
        )
    }

    // Limpia el formulario (botón "Limpiar" del top bar).
    fun limpiarFormulario() {
        monto = ""; tipo = "Gasto"; esComun = false; metodoPago = "Billetera Virtual"
        descripcion = ""; ticketUri = null; tempPhotoUri = null
        fecha = hoyIso; hora = horaActual
        propietario = currentUserProfile
        categoria = categoriaDefault("Gasto")
        viewModel.clearMovementDraft()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Registrar Movimiento", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                actions = {
                    TextButton(onClick = { limpiarFormulario() }) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Limpiar", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                },
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
            val cardBg = MaterialTheme.colorScheme.surface
            val cardBorder = MaterialTheme.colorScheme.outlineVariant

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
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Gasto", "Aporte", "Transferencia").forEach { item ->
                        // La condonación se elige en el submodo de abajo, no acá: mientras se carga
                        // una, la pestaña "Transf." se queda marcada y volver a tocarla no la anula.
                        val isSelected = tipo == item || (item == "Transferencia" && esCondonacion)
                        val label = if (item == "Transferencia") "Transf." else item
                        Button(
                            onClick = { if (!isSelected) tipo = item },
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

            // Submodo de la transferencia: mover plata (lo de siempre) o perdonar deuda. Se guarda
            // directamente en `tipo`, así que no hace falta un campo nuevo en el borrador.
            if (tipo == "Transferencia" || esCondonacion) {
                Column {
                    Text(
                        text = "¿Qué estás haciendo?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ModoTransferenciaBoton(
                            titulo = "Mover plata",
                            bajada = "Sale de tu cuenta",
                            seleccionado = !esCondonacion,
                            modifier = Modifier.weight(1f),
                            onClick = { tipo = "Transferencia" }
                        )
                        ModoTransferenciaBoton(
                            titulo = "Perdonar deuda",
                            bajada = "No mueve plata",
                            seleccionado = esCondonacion,
                            modifier = Modifier.weight(1f),
                            onClick = { tipo = AccountingEngine.TIPO_CONDONACION }
                        )
                    }
                }
            }

            // Contexto del perdón: cuánto tiene el otro que es tuyo, con el atajo para perdonarlo
            // todo (el caso de uso real) sin perder la opción de perdonar solo una parte.
            if (esCondonacion) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolunteerActivism,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (hayDeudaAFavor) "$otroNombre tiene ${formatMoney.format(deudaAFavor)} tuyos"
                                else "No hay deuda a tu favor",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (hayDeudaAFavor) "No se mueve plata: deja de debértelos."
                                else "$otroNombre no tiene plata tuya para perdonar.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (hayDeudaAFavor) {
                            TextButton(onClick = { monto = montoParaInput(deudaAFavor) }) {
                                Text("Todo", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Perdonar de más no puede generar deuda en el sentido contrario, así que se capa.
                val excedido = hayDeudaAFavor && (monto.toDoubleOrNull() ?: 0.0) > deudaAFavor
                if (excedido) {
                    Text(
                        text = "Es más de lo que te debe: se van a perdonar ${formatMoney.format(deudaAFavor)}.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Método de Pago (Efectivo o Billetera Virtual). Una condonación no mueve plata de una
            // cuenta a la otra, así que no hay medio de pago que elegir.
            if (!esCondonacion) Column {
                Text(
                    text = "Método de Pago",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                Text("Afecta a ambos", fontSize = 9.sp, color = com.example.ui.theme.appTextMuted, maxLines = 1)
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
                                Text("Paga solo $miNombre", fontSize = 9.sp, color = com.example.ui.theme.appTextMuted, maxLines = 1)
                            }
                        }
                    }
                }
            }

            // Propiedad del Dinero (Solo si no es Común). En una condonación tampoco hay nada que
            // elegir: el propietario es el beneficiario (el otro) y lo fija el reset de tipo.
            if (!esCondonacion && !(tipo == "Gasto" && esComun)) {
                Column {
                    val label = when(tipo) {
                        "Aporte" -> "¿En qué cuenta entra?"
                        "Transferencia" -> "¿La plata sigue siendo de $miNombre?"
                        else -> "¿Quién debe pagar realmente?"
                    }
                    Text(
                        text = label,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val otherUser = usuarios.elOtro(currentUserProfile).slotKey
                        val otherNombre = usuarios.nombreDe(otherUser)

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
                                "Transferencia" -> "No, es de $otherNombre"
                                "Aporte" -> "De $otherNombre"
                                else -> "De $otherNombre"
                            }
                            Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Categoría: frecuentes + carpetas plegables (ver CategoryPicker)
            Column {
                Text(
                    text = "Categoría",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                CategoryPicker(
                    tipo = tipo,
                    seleccionada = categoria,
                    historialCategorias = historialCategorias,
                    onSelect = { categoria = it }
                )
            }

            // Fecha con Atajos (Hoy / Ayer / Lanzar dialog)
            Column {
                Text(
                    text = "Fecha del Movimiento",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                OutlinedTextField(
                    value = descripcion,
                    onValueChange = { descripcion = it },
                    placeholder = { Text("Ej. Supermercado mensual, compra abrigo...", fontSize = 13.sp) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    val ingresado = monto.toDoubleOrNull() ?: 0.0
                    // El motor también clampea, pero se capa acá para que la planilla guarde el
                    // monto que realmente se aplicó y no uno mayor que nunca llegó a perdonarse.
                    val doubleMonto = if (esCondonacion) minOf(ingresado, deudaAFavor) else ingresado
                    if (doubleMonto <= 0.0) {
                        return@Button
                    }

                    // El alta es asincrónica: se encola, aparece al instante en Inicio como fila
                    // pendiente y se escribe en la planilla en segundo plano (con una notificación
                    // al terminar). Por eso el botón ya no espera ni muestra spinner: `onEncolado`
                    // se ejecuta en el acto.
                    viewModel.encolarMovimiento(
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
                        onEncolado = {
                            limpiarFormulario()
                            onSuccess()
                        }
                    )
                },
                enabled = monto.isNotEmpty() && monto.toDoubleOrNull() != null &&
                        (monto.toDoubleOrNull() ?: 0.0) > 0 && (!esCondonacion || hayDeudaAFavor),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Registrar en " + if (useLocalDemo) "Base Local" else "Nube", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            }
        }
    }
}

/**
 * Botón del submodo de transferencia ("Mover plata" / "Perdonar deuda"): mismo formato de dos
 * líneas que los de "Distribución del Gasto".
 */
@Composable
private fun ModoTransferenciaBoton(
    titulo: String,
    bajada: String,
    seleccionado: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (seleccionado) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
            contentColor = if (seleccionado) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        modifier = modifier.height(56.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(titulo, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(
                bajada,
                fontSize = 9.sp,
                color = com.example.ui.theme.appTextMuted,
                maxLines = 1
            )
        }
    }
}

/**
 * Formatea un monto para el campo de texto del alta, que espera dígitos con punto decimal y a lo
 * sumo dos decimales (lo mismo que acepta su `onValueChange`).
 */
private fun montoParaInput(valor: Double): String =
    java.math.BigDecimal.valueOf(valor)
        .setScale(2, java.math.RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()

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
