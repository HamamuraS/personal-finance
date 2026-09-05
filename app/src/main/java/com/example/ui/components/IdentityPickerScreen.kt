package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Usuario
import com.example.data.UsuariosConfig
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.presetOf

/**
 * Pantalla "¿Quién sos?" del primer arranque (y tras cerrar sesión). No es autenticación: solo
 * recuerda cuál de los 2 slots sos en este dispositivo. Cada tarjeta muestra el nombre y el color
 * **propio** de ese usuario (resuelto directo del preset: acá todavía no hay usuario "activo", así
 * que no se puede depender de primary/tertiary).
 */
@Composable
fun IdentityPickerScreen(
    usuarios: UsuariosConfig,
    onPick: (slotKey: String) -> Unit
) {
    val isDark = LocalIsDarkTheme.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "¿Quién sos?",
                fontWeight = FontWeight.Black,
                fontSize = 28.sp,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Elegí tu perfil para entrar. Lo vamos a recordar en este dispositivo.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            usuarios.todos.forEach { usuario ->
                IdentityCard(usuario = usuario, isDark = isDark, onClick = { onPick(usuario.slotKey) })
            }
        }
    }
}

@Composable
private fun IdentityCard(
    usuario: Usuario,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val resolved = presetOf(usuario.colorId).resolve(isDark)
    val cardBg = MaterialTheme.colorScheme.surface
    val cardBorder = MaterialTheme.colorScheme.outlineVariant

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(resolved.brand),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = usuario.nombre.take(1).uppercase(),
                    color = resolved.on,
                    fontWeight = FontWeight.Black,
                    fontSize = 24.sp
                )
            }
            Text(
                text = usuario.nombre,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = resolved.brand
            )
        }
    }
}
