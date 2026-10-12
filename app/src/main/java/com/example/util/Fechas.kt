package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Fechas de la app. En la planilla y en el modelo viajan como texto: "yyyy-MM-dd HH:mm" los
 * movimientos y "yyyy-MM" los meses. Acá están el "ahora" en esos formatos y la lectura de las fechas
 * que Sheets devuelve como `Date.toString()` ("Wed Jul 01 2026 …") cuando guardó la celda como fecha.
 *
 * El "ahora" de la UI usa el huso del teléfono; el de los workers, el argentino ([ARGENTINA]), igual
 * que antes de centralizarlo.
 */
object Fechas {

    /** Huso de los recordatorios y del corte de mes en segundo plano (Argentina no tiene DST). */
    val ARGENTINA: TimeZone = TimeZone.getTimeZone("America/Argentina/Buenos_Aires")

    private fun formato(patron: String, tz: TimeZone?): SimpleDateFormat =
        SimpleDateFormat(patron, Locale.US).apply { if (tz != null) timeZone = tz }

    /** "yyyy-MM" de hoy. */
    fun mesActual(tz: TimeZone? = null): String = formato("yyyy-MM", tz).format(Date())

    /** "yyyy-MM-dd" de hoy. */
    fun hoy(tz: TimeZone? = null): String = formato("yyyy-MM-dd", tz).format(Date())

    /** "yyyy-MM-dd" de ayer. */
    fun ayer(): String = formato("yyyy-MM-dd", null).format(Calendar.getInstance().apply { add(Calendar.DATE, -1) }.time)

    /** "HH:mm" de ahora. */
    fun horaActual(): String = formato("HH:mm", null).format(Date())

    /** "yyyy-MM-dd HH:mm" de ahora: la fecha con que se guarda un movimiento nuevo. */
    fun ahora(): String = formato("yyyy-MM-dd HH:mm", null).format(Date())

    /** "yyyy-MM-dd" de una fecha cualquiera (lo que devuelve el selector de fecha). */
    fun dia(fecha: Date): String = formato("yyyy-MM-dd", null).format(fecha)

    /** Nombres de los meses, con mayúscula ("Enero" … "Diciembre"). */
    val MESES = listOf(
        "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    )

    /** "2026-08" -> "Agosto 2026". Si no parsea, devuelve la entrada. */
    fun etiquetaDeMes(yyyyMM: String): String {
        val parts = yyyyMM.split("-")
        val y = parts.getOrNull(0) ?: return yyyyMM
        val m = parts.getOrNull(1)?.toIntOrNull() ?: return yyyyMM
        if (m !in 1..12) return yyyyMM
        return "${MESES[m - 1]} $y"
    }

    /** "2026-07" -> "julio". Si no parsea, devuelve la entrada tal cual. */
    fun nombreDeMes(yyyyMM: String): String {
        val n = yyyyMM.split("-").getOrNull(1)?.toIntOrNull() ?: return yyyyMM
        return MESES.getOrNull(n - 1)?.lowercase() ?: yyyyMM
    }

    /** "jan"/"ene" … -> "01" … "12" (inglés y español). null si no es un mes. */
    private fun numeroDeMes(abreviatura: String): String? = when (abreviatura.lowercase()) {
        "jan", "ene" -> "01"; "feb" -> "02"; "mar" -> "03"; "apr", "abr" -> "04"
        "may" -> "05"; "jun" -> "06"; "jul" -> "07"; "aug", "ago" -> "08"
        "sep" -> "09"; "oct" -> "10"; "nov" -> "11"; "dec", "dic" -> "12"
        else -> null
    }

    private val FECHA_DE_SHEETS = Regex("^[a-zA-Z]{3}\\s[a-zA-Z]{3}\\s\\d{2}\\s\\d{4}.*")

    /**
     * Fecha de un movimiento tal como llegó del Web App, llevada a "yyyy-MM-dd[ HH:mm]". Solo toca
     * las de forma "Wed Jul 01 2026 10:30:00 …" (la medianoche se descarta: es una fecha sin hora);
     * cualquier otra cosa queda igual. Un mes que no se reconoce se lee como enero.
     */
    fun normalizarFechaDeMovimiento(input: String): String {
        try {
            if (input.matches(FECHA_DE_SHEETS)) {
                val parts = input.split(Regex("\\s+"))
                if (parts.size >= 4) {
                    val mes = numeroDeMes(parts[1]) ?: "01"
                    val dia = parts[2].padStart(2, '0')
                    val anio = parts[3]
                    var hora = if (parts.size >= 5) parts[4] else ""
                    if (hora.contains(":")) {
                        val t = hora.split(":")
                        if (t.size >= 2) hora = "${t[0]}:${t[1]}"
                    } else {
                        hora = ""
                    }
                    return "$anio-$mes-$dia${if (hora != "00:00" && hora.isNotEmpty()) " $hora" else ""}"
                }
            }
        } catch (_: Exception) {
        }
        return input
    }

    /** Extrae "yyyy-MM" de "yyyy-MM…" o de la forma Date de Sheets. Si no reconoce el formato, la deja. */
    fun aMes(raw: String): String {
        val s = raw.trim()
        if (s.isEmpty()) return s
        Regex("^(\\d{4})-(\\d{2})").find(s)?.let { return "${it.groupValues[1]}-${it.groupValues[2]}" }
        Regex("^[A-Za-z]{3}\\s([A-Za-z]{3})\\s\\d{2}\\s(\\d{4})").find(s)?.let { m ->
            val mm = numeroDeMes(m.groupValues[1]) ?: return s
            return "${m.groupValues[2]}-$mm"
        }
        return s
    }

    /** Lleva una fecha de creación a "yyyy-MM-dd HH:mm" (ordenable). Sin hora queda "00:00". */
    fun aFechaHora(raw: String): String {
        val s = raw.trim()
        if (s.isEmpty()) return s
        if (Regex("^\\d{4}-\\d{2}-\\d{2}").containsMatchIn(s)) return s
        Regex("^[A-Za-z]{3}\\s([A-Za-z]{3})\\s(\\d{2})\\s(\\d{4})(?:\\s(\\d{2}:\\d{2}))?").find(s)?.let { m ->
            val mm = numeroDeMes(m.groupValues[1]) ?: return s
            val hora = m.groupValues.getOrNull(4)?.ifEmpty { "00:00" } ?: "00:00"
            return "${m.groupValues[3]}-$mm-${m.groupValues[2]} $hora"
        }
        return s
    }

    /** "2026-10-01 08:30" -> "01 de octubre de 2026 08:30" (también la forma Date de Sheets). */
    fun legible(input: String): String {
        if (input.isBlank()) return "Fecha desconocida"
        try {
            if (input.matches(FECHA_DE_SHEETS)) {
                val parts = input.split(Regex("\\s+"))
                if (parts.size >= 4) {
                    val mes = parts[1]
                    val dia = parts[2]
                    val anio = parts[3]
                    var hora = if (parts.size >= 5) parts[4] else ""
                    if (hora.contains(":")) {
                        val t = hora.split(":")
                        if (t.size >= 2) hora = "${t[0]}:${t[1]}"
                    } else {
                        hora = ""
                    }
                    val nombre = numeroDeMes(mes)?.let { MESES[it.toInt() - 1].lowercase() } ?: mes
                    return if (hora != "00:00" && hora.isNotEmpty()) "$dia de $nombre de $anio $hora" else "$dia de $nombre de $anio"
                }
            }
            if (input.matches(Regex("\\d{4}-\\d{2}-\\d{2}.*"))) {
                val t = input.substring(0, 10).split("-")
                val nombre = t[1].toIntOrNull()?.takeIf { it in 1..12 }?.let { MESES[it - 1].lowercase() } ?: t[1]
                val hora = if (input.length > 10) input.substring(11).trim() else ""
                return if (hora.isNotEmpty()) "${t[2]} de $nombre de ${t[0]} $hora" else "${t[2]} de $nombre de ${t[0]}"
            }
        } catch (_: Exception) {
        }
        return input
    }
}
