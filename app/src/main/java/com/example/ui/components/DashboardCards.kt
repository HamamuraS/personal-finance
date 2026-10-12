package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CuotaPlan
import com.example.data.UsuariosConfig
import com.example.domain.BalanceBreakdown
import com.example.domain.CuotaRecordatorio
import com.example.domain.SaldoPersona
import com.example.ui.components.common.AppCard
import com.example.ui.components.common.IconoEnCirculo
import com.example.ui.theme.personaColor
import com.example.util.Fechas
import java.text.NumberFormat
import kotlin.math.abs

/** Por debajo de esto un saldo cruzado o un total se considera cero (centavos de redondeo). */
private const val UMBRAL_CENTAVOS = 1.0

/**
 * Tarjeta hero de Inicio: el pozo total, la barra de cuánto le corresponde a cada uno y el desglose
 * físico (💵 / 💳) más la posición cruzada (🤝) de cada persona. El primer usuario a la izquierda y
 * el segundo a la derecha, espejados.
 */
@Composable
internal fun PozoComunCard(userProfile: String, config: UsuariosConfig, balance: BalanceBreakdown, formatMoney: NumberFormat) {
    // La tarjeta hero es la única que NO va sobre `surface`: usa el contenedor tonal para
    // despegarse del resto del listado. Los tres colores salen del tema, así que el contraste del
    // label sobre ese fondo está garantizado por la paleta y no por un hex elegido a ojo.
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMainColor = MaterialTheme.colorScheme.onSurface
    val izquierda = config.primario
    val derecha = config.secundario
    val saldoIzq = balance.de(izquierda.slotKey)
    val saldoDer = balance.de(derecha.slotKey)
    val colorIzq = personaColor(izquierda.slotKey, userProfile)
    val colorDer = personaColor(derecha.slotKey, userProfile)

    AppCard(radio = 32.dp, containerColor = MaterialTheme.colorScheme.surfaceVariant) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Pozo Común Total",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = labelColor,
                letterSpacing = 0.5.sp
            )

            Text(
                text = formatMoney.format(balance.totalPozo),
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                color = textMainColor,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Barra de reparto (si se dividiera hoy).
            val ratioIzq = if (balance.totalPozo > 0) (saldoIzq.saldoFinal / balance.totalPozo).coerceIn(0.0, 1.0) else 0.5
            val ratioDer = 1.0 - ratioIzq
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(if (ratioIzq > 0) ratioIzq.toFloat() else 0.001f)
                        .background(colorIzq)
                )
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(if (ratioDer > 0) ratioDer.toFloat() else 0.001f)
                        .background(colorDer)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ColumnaDelPozo(izquierda.nombre, saldoIzq, colorIzq, alinearALaDerecha = false, labelColor, textMainColor, formatMoney)
                ColumnaDelPozo(derecha.nombre, saldoDer, colorDer, alinearALaDerecha = true, labelColor, textMainColor, formatMoney)
            }
        }
    }
}

/**
 * Una columna del pozo. 💵 y 💳 son el dinero FÍSICO (lo que se concilia contra el banco); 🤝 es la
 * posición cruzada. Las tres líneas suman el titular de arriba. La de la derecha va espejada.
 */
@Composable
private fun ColumnaDelPozo(
    nombre: String,
    saldo: SaldoPersona,
    color: Color,
    alinearALaDerecha: Boolean,
    labelColor: Color,
    textMainColor: Color,
    formatMoney: NumberFormat
) {
    @Composable
    fun Linea(emoji: String, texto: String, colorTexto: Color, modifier: Modifier = Modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
            if (!alinearALaDerecha) {
                Text(emoji, fontSize = 10.sp)
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(texto, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colorTexto)
            if (alinearALaDerecha) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(emoji, fontSize = 10.sp)
            }
        }
    }

    Column(horizontalAlignment = if (alinearALaDerecha) Alignment.End else Alignment.Start) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val punto: @Composable () -> Unit = {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
            }
            if (!alinearALaDerecha) {
                punto()
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(nombre, fontSize = 12.sp, color = labelColor, fontWeight = FontWeight.SemiBold)
            if (alinearALaDerecha) {
                Spacer(modifier = Modifier.width(6.dp))
                punto()
            }
        }
        Text(
            text = formatMoney.format(saldo.saldoFinal),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp,
            color = textMainColor
        )
        Linea("💵", formatMoney.format(saldo.efectivo), textMainColor.copy(alpha = 0.7f), Modifier.padding(top = 2.dp))
        Linea("💳", formatMoney.format(saldo.virtual), textMainColor.copy(alpha = 0.7f))
        if (abs(saldo.externo) >= UMBRAL_CENTAVOS) {
            Linea(
                "🤝",
                formatMoney.format(saldo.externo),
                if (saldo.externo < 0) MaterialTheme.colorScheme.error else color
            )
        }
    }
}

/**
 * Tarjeta de una línea: ícono en círculo, una etiqueta chica, un texto y un monto a la derecha. Es
 * el formato de "Gastos comunes" y "Dinero cruzado".
 */
@Composable
private fun TarjetaDeResumen(
    icono: ImageVector,
    acento: Color,
    alphaIcono: Float,
    etiqueta: String,
    texto: String,
    monto: String,
    colorMonto: Color
) {
    val textMainColor = MaterialTheme.colorScheme.onSurface
    AppCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconoEnCirculo(icono, acento, tamano = 36.dp, alphaFondo = alphaIcono)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = etiqueta,
                    fontSize = 11.sp,
                    color = textMainColor.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = texto,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textMainColor
                )
            }
            Text(
                text = monto,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = colorMonto
            )
        }
    }
}

/**
 * Muestra, una sola vez, la posición de propiedad cruzada entre los dos: quién tiene plata en la
 * cuenta de quién. Como es simétrica, no tiene sentido repetirla en cada tarjeta.
 */
@Composable
internal fun DineroCruzadoCard(balance: BalanceBreakdown, currentUserProfile: String, config: UsuariosConfig, formatMoney: NumberFormat) {
    // Posición externa del primer usuario: + = tiene plata en la cuenta del otro.
    val externo = balance.de(config.primario.slotKey).externo
    if (abs(externo) < UMBRAL_CENTAVOS) return

    // El acento representa al dueño (persona concreta), así que usa su color de identidad estable.
    val duenoKey = if (externo > 0) config.primario.slotKey else config.secundario.slotKey
    val cuentaKey = if (externo > 0) config.secundario.slotKey else config.primario.slotKey
    val acento = personaColor(duenoKey, currentUserProfile)
    TarjetaDeResumen(
        icono = Icons.Default.SwapHoriz,
        acento = acento,
        alphaIcono = 0.12f,
        etiqueta = "Dinero cruzado",
        texto = "${config.nombreDe(duenoKey)} tiene plata en la cuenta de ${config.nombreDe(cuentaKey)}",
        monto = formatMoney.format(abs(externo)),
        colorMonto = acento
    )
}

/**
 * Tarjeta de saldo individual.
 *
 *  - "Le corresponde" = lo que realmente es suyo (patrimonial).
 *  - "En su poder"    = el dinero físico en sus cuentas, conciliable contra el homebanking.
 *
 * La diferencia entre ambas es la posición cruzada, que se explica una sola vez en
 * [DineroCruzadoCard] (y línea 🤝 del pozo) en vez de repetirse por tarjeta.
 */
@Composable
internal fun DesgloseSocioCard(
    modifier: Modifier,
    nombre: String,
    saldo: SaldoPersona,
    formatMoney: NumberFormat,
    avatarColor: Color
) {
    val textMainColor = MaterialTheme.colorScheme.onSurface
    val rojo = MaterialTheme.colorScheme.error

    AppCard(modifier = modifier, radio = 24.dp, elevacion = 1.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header del socio
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(avatarColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = nombre.take(1),
                        color = avatarColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Text(
                    text = nombre,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = textMainColor
                )
            }

            // Lo que realmente es suyo (físico ± lo cruzado). Es el número que importa.
            Column {
                Text(
                    text = "Le corresponde",
                    fontSize = 11.sp,
                    color = textMainColor.copy(alpha = 0.5f)
                )
                Text(
                    text = formatMoney.format(saldo.saldoFinal),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (saldo.saldoFinal < 0) rojo else textMainColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Saldo físico en sus propias cuentas (efectivo + virtual). Es el que se concilia
            // contra el homebanking / la billetera real.
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "En su poder",
                    fontSize = 11.sp,
                    color = textMainColor.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = formatMoney.format(saldo.enMano),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = textMainColor.copy(alpha = 0.9f)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Ingresos (+)
            Column {
                Text(
                    text = "Ingresos (+)",
                    fontSize = 10.sp,
                    color = textMainColor.copy(alpha = 0.5f)
                )
                Text(
                    text = formatMoney.format(saldo.ingresos),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = avatarColor,
                    maxLines = 1
                )
            }

            // Gastos personales (-)
            Column {
                Text(
                    text = "Personales (-)",
                    fontSize = 10.sp,
                    color = textMainColor.copy(alpha = 0.5f)
                )
                Text(
                    text = formatMoney.format(saldo.gastosPersonalesReales),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textMainColor.copy(alpha = 0.7f),
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Muestra, una sola vez, el total de gastos comunes del periodo (se dividen en partes iguales).
 * Antes se repetía como "Comunes (-)" en rojo en ambas tarjetas, siempre con el mismo valor.
 */
@Composable
internal fun GastosComunesCard(total: Double, cantidadDePersonas: Int, formatMoney: NumberFormat) {
    if (total < UMBRAL_CENTAVOS) return
    val rojo = MaterialTheme.colorScheme.error
    TarjetaDeResumen(
        icono = Icons.Default.Groups,
        acento = rojo,
        alphaIcono = 0.1f,
        etiqueta = "Gastos comunes",
        texto = "${formatMoney.format(total / cantidadDePersonas)} cada uno",
        monto = formatMoney.format(total),
        colorMonto = rojo
    )
}

/**
 * Tarjeta recordatorio del Dashboard: lista las cuotas impagas del usuario activo (incluidas las
 * atrasadas) con un botón de pago rápido que reutiliza el mismo diálogo que el detalle del plan.
 */
@Composable
internal fun CuotasRecordatorioCard(
    recordatorios: List<CuotaRecordatorio>,
    formatMoney: NumberFormat,
    isLoading: Boolean,
    onConfirmCuota: (CuotaPlan, Int, String, String, Double?, () -> Unit) -> Unit
) {
    val textMain = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary

    var target by remember { mutableStateOf<CuotaRecordatorio?>(null) }
    target?.let { rec ->
        ConfirmarCuotaDialog(
            planDescripcion = rec.plan.descripcion,
            numero = rec.cuota.numero,
            cantidadCuotas = rec.plan.cantidadCuotas,
            esUltima = rec.cuota.numero == rec.plan.cantidadCuotas,
            esAtrasada = rec.atrasada,
            mesVencimiento = rec.cuota.mesVencimiento,
            montoSugerido = rec.plan.montoPorCuota,
            formatMoney = formatMoney,
            isLoading = isLoading,
            onDismiss = { target = null },
            onConfirm = { fecha, metodo, monto ->
                onConfirmCuota(rec.plan, rec.cuota.numero, fecha, metodo, monto) { target = null }
            }
        )
    }

    val total = recordatorios.sumOf { it.cuota.monto }
    val hayAtrasadas = recordatorios.any { it.atrasada }

    AppCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconoEnCirculo(Icons.Default.CreditCard, accent, tamano = 32.dp, alphaFondo = 0.12f, tamanoIcono = 18.dp)
                    Column {
                        Text("Cuotas a pagar", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textMain)
                        if (hayAtrasadas) {
                            Text("Tenés cuotas atrasadas", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                Text(formatMoney.format(total), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = accent)
            }

            recordatorios.forEach { rec ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Cuota ${rec.cuota.numero}/${rec.plan.cantidadCuotas} — ${rec.plan.descripcion}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textMain,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (rec.atrasada) "Venció en ${Fechas.etiquetaDeMes(rec.cuota.mesVencimiento)}" else Fechas.etiquetaDeMes(rec.cuota.mesVencimiento),
                            fontSize = 10.sp,
                            color = if (rec.atrasada) MaterialTheme.colorScheme.error else textMain.copy(alpha = 0.5f)
                        )
                    }
                    Text(formatMoney.format(rec.cuota.monto), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textMain.copy(alpha = 0.8f))
                    Button(
                        onClick = { target = rec },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = MaterialTheme.colorScheme.onPrimary),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Text("Pagar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
