package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.data.Categorias
import com.example.data.MetodoPago
import com.example.data.PROPIETARIO_AMBOS
import com.example.data.TipoMovimiento
import com.example.ui.AhorroViewModel
import com.example.ui.MovementDraft
import com.example.ui.components.common.AppCard
import com.example.ui.components.common.AtajosDeFecha
import com.example.ui.components.common.EvitableToggle
import com.example.ui.components.common.FormLabel
import com.example.ui.components.common.IconoEnCirculo
import com.example.ui.components.common.MontoCajeroCard
import com.example.ui.components.common.SelectorDeMetodoPago
import com.example.ui.components.common.colorDeOpcion
import com.example.ui.components.common.colorDeOpcionSuave
import com.example.ui.components.common.rememberDinero
import com.example.ui.theme.appTextMuted
import com.example.util.Fechas
import com.example.util.Formato
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.io.File

/** Por debajo de esto una deuda se considera saldada (centavos de redondeo). */
private const val UMBRAL_DEUDA = 1.0

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

    // Posición cruzada vigente: es lo que habilita el atajo "perdonar todo" del modo condonación.
    val balance by viewModel.balance.collectAsState()

    // Categorías usadas, de más nueva a más vieja (`allMovements` ya viene ordenado así): alimenta
    // la fila "Frecuentes" del selector.
    val allMovements by viewModel.allMovements.collectAsState()
    val formatMoney = rememberDinero()

    // --- Borrador persistente: el estado local se SIEMBRA del borrador del VM (que sobrevive al
    // cambio de pestaña) y se VUELCA a él en cada cambio. Campos vacíos usan el default. ---
    val savedDraft = remember { viewModel.movementDraft.value }
    var monto by remember { mutableStateOf(savedDraft.monto) }
    var tipo by remember { mutableStateOf(savedDraft.tipo) }
    var esComun by remember { mutableStateOf(savedDraft.esComun) }
    val responsable = currentUserProfile // Siempre el usuario actual (slotKey)
    var metodoPago by remember { mutableStateOf(savedDraft.metodoPago) }
    var propietario by remember { mutableStateOf(savedDraft.propietario.ifEmpty { currentUserProfile }) }
    var descripcion by remember { mutableStateOf(savedDraft.descripcion) }
    var categoria by remember { mutableStateOf(savedDraft.categoria) }
    var fecha by remember { mutableStateOf(savedDraft.fecha.ifEmpty { Fechas.hoy() }) }
    var hora by remember { mutableStateOf(savedDraft.hora.ifEmpty { Fechas.horaActual() }) }
    var ticketUri by remember { mutableStateOf(savedDraft.ticketUriString?.let { Uri.parse(it) }) }
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var evitable by remember { mutableStateOf(savedDraft.evitable) }
    // Edición (v7.7): el borrador trae el id y lo que hay que conservar del movimiento original.
    // null = alta. "Cancelar" lo vuelve a null.
    var edicion by remember { mutableStateOf(savedDraft.takeIf { it.editando }) }
    val esPagoDeCuota = edicion?.planId?.isNotEmpty() == true

    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

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

    // Los submodos de la pestaña "Transf." son tipos propios en la planilla: "Cambio" (cambio de
    // dinero) y el "Saldo externo", que es una `Condonación` (perdonar, con saldo a favor) o una
    // `Devolución` (pagar, con saldo en contra). Viven en `tipo`, así que el borrador los arrastra solo.
    val esCondonacion = tipo == TipoMovimiento.CONDONACION
    val esDevolucion = tipo == TipoMovimiento.DEVOLUCION
    val esSaldoExterno = esCondonacion || esDevolucion
    // Cambio de dinero: con el otro (`Cambio`) o con alguien de afuera (`Cambio propio`).
    val esCambioPropio = tipo == TipoMovimiento.CAMBIO_PROPIO
    val esCambio = tipo == TipoMovimiento.CAMBIO || esCambioPropio
    val esModoTransferencia = tipo == TipoMovimiento.TRANSFERENCIA || esSaldoExterno || esCambio
    val otroSlot = usuarios.elOtro(currentUserProfile).slotKey
    val otroNombre = usuarios.nombreDe(otroSlot)
    // Posición cruzada del usuario activo. Al editar se mira el mes SIN el movimiento editado: si no,
    // un perdón que canceló toda la deuda se ve a sí mismo y no deja guardar (deuda cero).
    val balanceRef = remember(balance, edicion) {
        edicion?.editandoId?.let { viewModel.balanceSin(it) } ?: balance
    }
    // + = el otro tiene plata tuya; - = vos tenés plata del otro.
    val miExterno = balanceRef.de(currentUserProfile).externo
    val deudaAFavor = maxOf(0.0, miExterno)
    val deudaEnContra = maxOf(0.0, -miExterno)
    val hayDeudaAFavor = deudaAFavor >= UMBRAL_DEUDA
    val hayDeudaEnContra = deudaEnContra >= UMBRAL_DEUDA
    // Qué tipo corresponde al "Saldo externo" según de qué lado está la deuda.
    val tipoSaldoExterno = if (hayDeudaEnContra) TipoMovimiento.DEVOLUCION else TipoMovimiento.CONDONACION
    // Máximo que se puede perdonar / pagar en el modo actual (0 si no hay deuda de ese lado).
    val topeSaldoExterno = when {
        esCondonacion -> deudaAFavor
        esDevolucion -> deudaEnContra
        else -> 0.0
    }

    // Si la deuda cambia de lado con el formulario abierto (llegó un refresh), el "Saldo externo"
    // se acomoda: perdonar solo tiene sentido con saldo a favor y pagar, con saldo en contra.
    LaunchedEffect(esSaldoExterno, hayDeudaAFavor, hayDeudaEnContra) {
        if (esSaldoExterno && (hayDeudaAFavor || hayDeudaEnContra) && tipo != tipoSaldoExterno) {
            tipo = tipoSaldoExterno
        }
    }

    // Reset de categoría/propietario SOLO cuando el usuario cambia tipo/comunalidad (no en la primera
    // composición, para no pisar el borrador restaurado).
    var resetInicializado by remember { mutableStateOf(false) }
    LaunchedEffect(tipo, esComun) {
        if (!resetInicializado) {
            resetInicializado = true
            if (categoria.isEmpty()) categoria = Categorias.defaultDeTipo(tipo)
            return@LaunchedEffect
        }
        categoria = Categorias.defaultDeTipo(tipo)
        propietario = when {
            tipo == TipoMovimiento.GASTO && esComun -> PROPIETARIO_AMBOS
            // En el saldo externo el propietario es el otro: a quién se le perdona o se le paga.
            tipo == TipoMovimiento.CONDONACION || tipo == TipoMovimiento.DEVOLUCION -> otroSlot
            else -> currentUserProfile
        }
    }

    // Volcar el estado al borrador del VM para que sobreviva el cambio de pestaña.
    LaunchedEffect(monto, tipo, esComun, metodoPago, propietario, descripcion, categoria, fecha, hora, ticketUri, evitable, edicion) {
        val base = edicion ?: MovementDraft()
        viewModel.setMovementDraft(
            base.copy(
                monto = monto, tipo = tipo, esComun = esComun, metodoPago = metodoPago,
                propietario = propietario, descripcion = descripcion, fecha = fecha, hora = hora,
                categoria = categoria, ticketUriString = ticketUri?.toString(), evitable = evitable
            )
        )
    }

    // Limpia el formulario (botón "Limpiar" del top bar). En edición es "Cancelar": descarta los
    // cambios y deja un alta vacía.
    fun limpiarFormulario() {
        monto = ""; tipo = TipoMovimiento.GASTO; esComun = false; metodoPago = MetodoPago.VIRTUAL
        descripcion = ""; ticketUri = null; tempPhotoUri = null
        fecha = Fechas.hoy(); hora = Fechas.horaActual()
        propietario = currentUserProfile
        categoria = Categorias.defaultDeTipo(TipoMovimiento.GASTO)
        evitable = false
        edicion = null
        viewModel.clearMovementDraft()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (edicion != null) "Editar Movimiento" else "Registrar Movimiento",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                actions = {
                    // Evitable / no evitable: solo para gastos, a la izquierda de "Limpiar".
                    if (tipo == TipoMovimiento.GASTO) {
                        EvitableToggle(evitable = evitable, onToggle = { evitable = !evitable })
                    }
                    TextButton(onClick = { limpiarFormulario() }) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (edicion != null) "Cancelar" else "Limpiar", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
            MontoCajeroCard(
                etiqueta = "MONTO DEL MOVIMIENTO",
                monto = monto,
                onMontoChange = { monto = it },
                keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() })
            )

            // Tipo de Movimiento (Fila de Botones)
            Column {
                FormLabel("Tipo de Movimiento", Modifier.padding(bottom = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(TipoMovimiento.GASTO, TipoMovimiento.APORTE, TipoMovimiento.TRANSFERENCIA).forEach { item ->
                        // Los submodos (cambio, saldo externo) se eligen abajo, no acá: mientras se
                        // carga uno, la pestaña "Transf." se queda marcada y volver a tocarla no lo anula.
                        val isSelected = tipo == item || (item == TipoMovimiento.TRANSFERENCIA && esModoTransferencia)
                        val label = if (item == TipoMovimiento.TRANSFERENCIA) "Transf." else item
                        Button(
                            onClick = { if (!isSelected) tipo = item },
                            // Un pago de cuota es siempre un gasto: cambiarle el tipo rompería el vínculo.
                            enabled = !esPagoDeCuota || item == TipoMovimiento.GASTO,
                            colors = colorDeOpcion(isSelected),
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

            // Avisos de la edición.
            if (edicion != null) {
                val mesActual = Fechas.mesActual()
                val avisos = buildList {
                    if (esPagoDeCuota) add("Es el pago de la cuota ${edicion?.cuotaNumero}: el tipo queda fijo en Gasto.")
                    val original = edicion?.propietarioOriginal.orEmpty()
                    if (tipo == TipoMovimiento.APORTE && original.isNotBlank() && !original.equals(currentUserProfile, ignoreCase = true)) {
                        add("Este aporte estaba a nombre de ${usuarios.nombreDe(original)}: al guardarlo pasa a ser tuyo.")
                    }
                    if (fecha.take(7) < mesActual) {
                        add("La fecha cae en un mes cerrado: después de guardar, recalculá el saldo inicial en Ajustes.")
                    }
                }
                avisos.forEach { aviso ->
                    Text(text = aviso, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            }

            // Submodo de la transferencia: mover plata, cambio de dinero o saldo externo. Se guarda
            // directamente en `tipo`, así que no hace falta un campo nuevo en el borrador.
            if (esModoTransferencia) {
                Column {
                    FormLabel("¿Qué estás haciendo?", Modifier.padding(bottom = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BotonDeDosLineas(
                            titulo = "Mover plata",
                            bajada = "No toca la deuda",
                            seleccionado = tipo == TipoMovimiento.TRANSFERENCIA,
                            modifier = Modifier.weight(1f),
                            onClick = { tipo = TipoMovimiento.TRANSFERENCIA }
                        )
                        BotonDeDosLineas(
                            titulo = "Cambio",
                            bajada = "💵 ↔ 💳",
                            seleccionado = esCambio,
                            modifier = Modifier.weight(1f),
                            onClick = { if (!esCambio) tipo = TipoMovimiento.CAMBIO }
                        )
                        BotonDeDosLineas(
                            titulo = "Saldo externo",
                            bajada = when {
                                hayDeudaAFavor -> "Perdonar deuda"
                                hayDeudaEnContra -> "Pagar deuda"
                                else -> "Sin deuda"
                            },
                            seleccionado = esSaldoExterno,
                            modifier = Modifier.weight(1f),
                            onClick = { if (!esSaldoExterno) tipo = tipoSaldoExterno }
                        )
                    }
                }
            }

            // Cambio de dinero: primero con quién (el otro integrante, o alguien de afuera: familia,
            // cajero), después qué entrega quien lo carga. Lo entregado se guarda en `metodoPago`; lo
            // que se recibe es el otro medio.
            if (esCambio) {
                Column {
                    FormLabel("¿Con quién?", Modifier.padding(bottom = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BotonDeDosLineas(
                            titulo = "Con $otroNombre",
                            bajada = "Cambian entre ustedes",
                            seleccionado = !esCambioPropio,
                            modifier = Modifier.weight(1f),
                            onClick = { tipo = TipoMovimiento.CAMBIO }
                        )
                        BotonDeDosLineas(
                            titulo = "Con otra persona",
                            bajada = "Familia, amigos o cajero",
                            seleccionado = esCambioPropio,
                            modifier = Modifier.weight(1f),
                            onClick = { tipo = TipoMovimiento.CAMBIO_PROPIO }
                        )
                    }
                }
                Column {
                    FormLabel(if (esCambioPropio) "¿Qué das?" else "¿Qué le das a $otroNombre?", Modifier.padding(bottom = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BotonDeDosLineas(
                            titulo = "💵 Efectivo",
                            bajada = "Recibís una transferencia",
                            seleccionado = metodoPago == MetodoPago.EFECTIVO,
                            modifier = Modifier.weight(1f),
                            onClick = { metodoPago = MetodoPago.EFECTIVO }
                        )
                        BotonDeDosLineas(
                            titulo = "💳 Transferencia",
                            bajada = "Recibís efectivo",
                            seleccionado = metodoPago != MetodoPago.EFECTIVO,
                            modifier = Modifier.weight(1f),
                            onClick = { metodoPago = MetodoPago.VIRTUAL }
                        )
                    }
                    Text(
                        text = if (esCambioPropio) "No es gasto ni aporte: solo cambia cómo tenés tu plata."
                        else "Nadie gana ni pierde plata y la deuda no cambia: solo cambia cómo la tiene cada uno.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            // Contexto del saldo externo: cuánto le debe uno al otro, con el atajo para saldarlo todo
            // (el caso de uso real) sin perder la opción de hacerlo solo en parte.
            if (esSaldoExterno) {
                AppCard(radio = 16.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconoEnCirculo(
                            icono = if (esDevolucion) Icons.Default.Handshake else Icons.Default.VolunteerActivism,
                            color = MaterialTheme.colorScheme.tertiary,
                            tamano = 36.dp,
                            alphaFondo = 0.12f,
                            tamanoIcono = 20.dp
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when {
                                    esCondonacion && hayDeudaAFavor -> "$otroNombre tiene ${formatMoney.format(deudaAFavor)} tuyos"
                                    esDevolucion && hayDeudaEnContra -> "Tenés ${formatMoney.format(deudaEnContra)} de $otroNombre"
                                    else -> "No hay saldo externo entre ustedes"
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = when {
                                    esCondonacion && hayDeudaAFavor -> "Perdonar: no se mueve plata, deja de debértelos."
                                    esDevolucion && hayDeudaEnContra -> "Pagar: la plata sale de tu cuenta y salda la deuda."
                                    else -> "Nadie tiene plata del otro para perdonar o devolver."
                                },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (topeSaldoExterno >= UMBRAL_DEUDA) {
                            TextButton(onClick = { monto = Formato.montoParaInput(topeSaldoExterno) }) {
                                Text("Todo", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Perdonar o pagar de más no puede generar deuda en el sentido contrario: se capa.
                val excedido = topeSaldoExterno >= UMBRAL_DEUDA && (monto.toDoubleOrNull() ?: 0.0) > topeSaldoExterno
                if (excedido) {
                    Text(
                        text = if (esCondonacion) "Es más de lo que te debe: se van a perdonar ${formatMoney.format(topeSaldoExterno)}."
                        else "Es más de lo que debés: se van a pagar ${formatMoney.format(topeSaldoExterno)}.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Método de Pago (Efectivo o Billetera Virtual). Un perdón no mueve plata de una cuenta a
            // la otra, y en el cambio de dinero el medio se elige arriba ("¿Qué le das?").
            if (!esCondonacion && !esCambio) Column {
                FormLabel("Método de Pago", Modifier.padding(bottom = 8.dp))
                SelectorDeMetodoPago(
                    seleccionado = metodoPago,
                    onSelect = { metodoPago = it },
                    alto = 44.dp,
                    radio = 12.dp,
                    fontSize = 12.sp,
                    unaLinea = false
                )
            }

            // Si es Gasto -> Compartido vs Personal
            if (tipo == TipoMovimiento.GASTO) {
                Column {
                    FormLabel("Distribución del Gasto", Modifier.padding(bottom = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BotonDeDosLineas(
                            titulo = "Común (50/50)",
                            bajada = "Afecta a ambos",
                            seleccionado = esComun,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                esComun = true
                                propietario = PROPIETARIO_AMBOS
                            }
                        )
                        // "Personal" = paga el usuario activo → su propio color, que en el tema activo
                        // siempre es `primary`.
                        BotonDeDosLineas(
                            titulo = "Personal",
                            bajada = "Paga solo $miNombre",
                            seleccionado = !esComun,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                esComun = false
                                propietario = currentUserProfile
                            }
                        )
                    }
                }
            }

            // Propiedad del Dinero: solo en transferencias ("mover plata") y gastos personales. Un
            // aporte es siempre de quien lo carga (v7.7); en el saldo externo el propietario es el
            // otro y en el cambio de dinero nadie cambia de dueño, así que no hay nada que elegir.
            if (tipo == TipoMovimiento.TRANSFERENCIA || (tipo == TipoMovimiento.GASTO && !esComun)) {
                Column {
                    val esTransferencia = tipo == TipoMovimiento.TRANSFERENCIA
                    FormLabel(
                        if (esTransferencia) "¿La plata sigue siendo de $miNombre?" else "¿Quién debe pagar realmente?",
                        Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Opción 1: Mío (o sigue siendo mío)
                        Button(
                            onClick = { propietario = currentUserProfile },
                            colors = colorDeOpcionSuave(propietario == currentUserProfile),
                            shape = RoundedCornerShape(12.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Text(if (esTransferencia) "Sí, es mía" else "Mío", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        // Opción 2: Del otro (o cambia de dueño)
                        Button(
                            onClick = { propietario = otroSlot },
                            colors = colorDeOpcionSuave(propietario == otroSlot, acento = MaterialTheme.colorScheme.tertiary),
                            shape = RoundedCornerShape(12.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Text(if (esTransferencia) "No, es de $otroNombre" else "De $otroNombre", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Categoría: frecuentes + rubros (ver CategoryPicker). El saldo externo y el cambio de
            // dinero no tienen nada que elegir: el tipo ya dice qué es, y la categoría queda en la
            // default ("Perdón de deuda" / "Pago de deuda" / "Cambio de dinero") por el reset de tipo.
            if (!esSaldoExterno && !esCambio) Column {
                FormLabel("Categoría", Modifier.padding(bottom = 8.dp))
                val historial = remember(allMovements, tipo) {
                    allMovements.filter { !it.eliminado && it.tipo.equals(tipo, ignoreCase = true) }.map { it.categoria }
                }
                CategoryPicker(
                    tipo = tipo,
                    seleccionada = categoria,
                    historial = historial,
                    onSelect = { categoria = it }
                )
            }

            // Fecha con Atajos (Hoy / Ayer / Lanzar dialog)
            Column {
                FormLabel("Fecha del Movimiento", Modifier.padding(bottom = 8.dp))
                AtajosDeFecha(
                    fecha = fecha,
                    onFecha = { fecha = it },
                    alto = 38.dp,
                    pesoSelector = 1.5f,
                    paddingSelector = 6.dp
                )
            }

            // Descripción (Opcional)
            Column {
                FormLabel("Descripción (Opcional)", Modifier.padding(bottom = 8.dp))
                OutlinedTextField(
                    value = descripcion,
                    onValueChange = { descripcion = it },
                    placeholder = { Text("Ej. Supermercado mensual, compra abrigo...", fontSize = 13.sp) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Adjuntar Ticket
            Column {
                FormLabel("Adjuntar Ticket (Opcional)", Modifier.padding(bottom = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Botón Cámara
                    OutlinedButton(
                        onClick = {
                            if (cameraPermissionState.status.isGranted) {
                                val uri = crearArchivoParaFoto(context)
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
                    // monto que realmente se aplicó y no uno mayor que nunca llegó a perdonarse/pagarse.
                    val doubleMonto = if (esSaldoExterno) minOf(ingresado, topeSaldoExterno) else ingresado
                    if (doubleMonto <= 0.0) {
                        return@Button
                    }
                    val propietarioFinal = when {
                        // Aportes y cambios son siempre de quien los carga.
                        tipo == TipoMovimiento.APORTE || esCambio -> currentUserProfile
                        esSaldoExterno -> otroSlot
                        else -> propietario
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
                        esComun = if (tipo == TipoMovimiento.GASTO) esComun else false,
                        propietario = propietarioFinal,
                        descripcion = descripcion,
                        metodoPago = metodoPago,
                        ticketUri = ticketUri,
                        evitable = evitable,
                        edicion = edicion,
                        onEncolado = {
                            limpiarFormulario()
                            onSuccess()
                        }
                    )
                },
                enabled = monto.isNotEmpty() && monto.toDoubleOrNull() != null &&
                    (monto.toDoubleOrNull() ?: 0.0) > 0 && (!esSaldoExterno || topeSaldoExterno >= UMBRAL_DEUDA),
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
                Text(
                    if (edicion != null) "Guardar cambios"
                    else "Registrar en " + if (useLocalDemo) "Base Local" else "Nube",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

/** Archivo temporal (vía FileProvider) donde la cámara deja la foto del ticket. */
private fun crearArchivoParaFoto(context: android.content.Context): Uri {
    val imagesDir = File(context.cacheDir, "images")
    if (!imagesDir.exists()) imagesDir.mkdirs()
    val tempFile = File(imagesDir, "temp_ticket_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
}

/**
 * Botón de dos renglones (título + bajada atenuada) con el estilo de opción suave: los de
 * "Distribución del Gasto" y los submodos de la transferencia.
 */
@Composable
private fun BotonDeDosLineas(
    titulo: String,
    bajada: String,
    seleccionado: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = colorDeOpcionSuave(seleccionado),
        shape = RoundedCornerShape(12.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        modifier = modifier.height(56.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(titulo, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(bajada, fontSize = 9.sp, color = appTextMuted, maxLines = 1)
        }
    }
}
