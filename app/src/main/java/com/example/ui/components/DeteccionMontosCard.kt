package com.example.ui.components

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.PreferencesHelper
import com.example.data.notifications.BilleterasNotificationListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ajustes de la detección automática de montos (ver [BilleterasNotificationListener]).
 *
 * La feature es **opt-in y prescindible**: la app funciona completa sin ella, y encenderla obliga a
 * pasar antes por [DivulgacionMontosDialog]. Ese orden no es estético — es el requisito de
 * *Prominent Disclosure & Consent* de Google Play: hay que explicar qué se lee, de qué apps y para
 * qué **antes** de mandar al usuario al setting de Android, y ni la política de privacidad ni el
 * diálogo del sistema alcanzan para cumplirlo.
 *
 * La tarjeta también muestra la fecha de la última detección. Es información de diagnóstico, no
 * decoración: en varios fabricantes la optimización de batería mata al listener sin avisar, y sin
 * esa fecha la muerte del servicio es indistinguible de "no compré nada esta semana".
 */
@Composable
fun DeteccionMontosCard(
    cardBg: Color,
    cardBorder: Color,
) {
    val context = LocalContext.current
    val prefs = remember { PreferencesHelper(context.applicationContext) }

    // El permiso y el package descubierto cambian FUERA de la app (en los Ajustes de Android, o
    // desde el propio servicio), así que se releen en cada vuelta al primer plano.
    var refresco by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresco++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val tienePermiso = remember(refresco) { BilleterasNotificationListener.tienePermiso(context) }
    var activa by remember(refresco) { mutableStateOf(prefs.deteccionMontosActiva) }
    var descubriendo by remember(refresco) { mutableStateOf(prefs.descubrimientoActivo()) }
    val ultimaDeteccion = remember(refresco) { prefs.ultimaDeteccionAt }
    val ultimoPackage = remember(refresco) { prefs.ultimoPackageDetectado }
    var extras by remember(refresco) { mutableStateOf(prefs.packagesBancariosExtra) }
    var mostrarDivulgacion by remember { mutableStateOf(false) }

    fun abrirAjustesDeAndroid() {
        // No hay diálogo runtime para el acceso a notificaciones: se habilita a mano acá.
        runCatching {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    if (mostrarDivulgacion) {
        DivulgacionMontosDialog(
            onDismiss = { mostrarDivulgacion = false },
            onAceptar = {
                prefs.deteccionDivulgacionAceptada = true
                prefs.deteccionMontosActiva = true
                activa = true
                mostrarDivulgacion = false
                abrirAjustesDeAndroid()
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Detectar montos de tus billeteras",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Opcional. Si lo activás, cuando " +
                            BilleterasNotificationListener.BILLETERAS_SOPORTADAS.joinToString(", ") +
                            " te avisan de un pago, la app te ofrece el alta con el importe ya cargado.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = activa,
                    onCheckedChange = { pedido ->
                        if (!pedido) {
                            prefs.deteccionMontosActiva = false
                            prefs.descubrimientoHasta = 0L
                            activa = false
                            descubriendo = false
                        } else if (prefs.deteccionDivulgacionAceptada && tienePermiso) {
                            // Ya vio la divulgación y el permiso sigue dado: no hace falta repetirle nada.
                            prefs.deteccionMontosActiva = true
                            activa = true
                        } else {
                            mostrarDivulgacion = true
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
                )
            }

            if (activa) {
                EstadoDeteccion(
                    tienePermiso = tienePermiso,
                    ultimaDeteccion = ultimaDeteccion
                )

                if (!tienePermiso) {
                    Button(
                        onClick = { abrirAjustesDeAndroid() },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth().height(40.dp)
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Dar acceso en Ajustes de Android", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // --- Modo descubrimiento ---
                Text(
                    text = "¿Una billetera no dispara nada? Su nombre de package puede haber cambiado. " +
                        "Activá el descubrimiento por 10 minutos, hacé un movimiento y agregá el package que aparezca.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = {
                        prefs.descubrimientoHasta =
                            System.currentTimeMillis() + PreferencesHelper.DESCUBRIMIENTO_DURACION_MILLIS
                        descubriendo = true
                    },
                    enabled = !descubriendo,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (descubriendo) "Descubrimiento activo (10 min)" else "Activar descubrimiento (10 min)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (ultimoPackage.isNotBlank()) {
                    val yaEsta = ultimoPackage in BilleterasNotificationListener.PAQUETES_BANCARIOS ||
                        ultimoPackage in extras
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Última app con monto",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = ultimoPackage,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (yaEsta) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Ya está en la lista",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            TextButton(onClick = {
                                prefs.agregarPackageBancario(ultimoPackage)
                                extras = prefs.packagesBancariosExtra
                            }) {
                                Text("Agregar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (extras.isNotEmpty()) {
                    Text(
                        text = "Agregadas por vos: " + extras.joinToString(", "),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Estado en dos líneas: si Android dejó pasar el permiso y cuándo fue la última detección. */
@Composable
private fun EstadoDeteccion(tienePermiso: Boolean, ultimaDeteccion: Long) {
    val formato = remember { SimpleDateFormat("d 'de' MMMM, HH:mm", Locale("es", "AR")) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = if (tienePermiso) "✅ Acceso a notificaciones concedido"
            else "⚠️ Falta el acceso a notificaciones: sin eso no se detecta nada",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (tienePermiso) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.error
        )
        Text(
            text = if (ultimaDeteccion > 0L) {
                "Última detección: ${formato.format(Date(ultimaDeteccion))}"
            } else {
                "Todavía no se detectó ningún monto."
            },
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Divulgación previa (Prominent Disclosure & Consent de Google Play).
 *
 * Tiene que aparecer **antes** de mandar al usuario al setting de Android y decir con todas las
 * letras qué se lee, de qué apps y para qué. Está redactada como lo que el código realmente hace,
 * que es la única forma de que siga siendo cierta.
 */
@Composable
fun DivulgacionMontosDialog(
    onDismiss: () -> Unit,
    onAceptar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Antes de activarlo",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PuntoDivulgacion(
                    "Qué lee",
                    "Solo el importe. De la notificación se extrae un número y nada más: ni el " +
                        "comercio, ni con quién fue la transferencia, ni si es un gasto o un ingreso."
                )
                PuntoDivulgacion(
                    "De qué apps",
                    "Únicamente de las billeteras de una lista fija: " +
                        BilleterasNotificationListener.BILLETERAS_SOPORTADAS.joinToString(", ") +
                        ". Las notificaciones de cualquier otra app se descartan sin leerlas."
                )
                PuntoDivulgacion(
                    "Para qué",
                    "Para ofrecerte el alta con el importe ya cargado y que no tengas que tipearlo. " +
                        "El resto del movimiento lo completás vos como siempre."
                )
                PuntoDivulgacion(
                    "Dónde queda",
                    "El texto de la notificación no se guarda, no queda en registros y no sale del " +
                        "teléfono. El importe llega a la planilla solo si vos confirmás el alta."
                )
                PuntoDivulgacion(
                    "Es opcional",
                    "La app funciona igual de completa sin esto. Podés apagarlo cuando quieras desde " +
                        "acá o revocar el acceso en los Ajustes de Android."
                )
                Text(
                    text = "Al continuar se abren los Ajustes de Android, donde tenés que habilitar " +
                        "el acceso a notificaciones para esta app.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onAceptar, shape = RoundedCornerShape(10.dp)) {
                Text("Entiendo, continuar", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Ahora no", fontSize = 13.sp)
            }
        }
    )
}

@Composable
private fun PuntoDivulgacion(titulo: String, cuerpo: String) {
    Column {
        Text(
            text = titulo,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = cuerpo,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
