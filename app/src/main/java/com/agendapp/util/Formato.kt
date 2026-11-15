package com.agendapp.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val localeAr = Locale("es", "AR")
private val fechaCorta = DateTimeFormatter.ofPattern("dd/MM/yyyy", localeAr)
private val fechaLarga = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", localeAr)
private val fechaHora = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", localeAr)

/** $1.600,00 — mismo formato que usa el backend en las notificaciones. */
fun BigDecimal.comoPesos(): String {
    val texto = setScale(2, RoundingMode.HALF_UP).abs().toPlainString()
    val (entero, decimales) = texto.split(".").let { it[0] to it.getOrElse(1) { "00" } }
    val conPuntos = entero.reversed().chunked(3).joinToString(".").reversed()
    val signo = if (signum() < 0) "-" else ""
    return "${signo}\$${conPuntos},$decimales"
}

/** "2026-11-16" → "16/11/2026" */
fun fechaIsoCorta(iso: String): String = LocalDate.parse(iso).format(fechaCorta)

/** "2026-11-16" → "lunes 16 de noviembre" */
fun fechaIsoLarga(iso: String): String = LocalDate.parse(iso).format(fechaLarga)

/** "08:00:00" → "08:00" */
fun horaCorta(hora: String): String = hora.take(5)

/** Fecha y hora ISO con zona → "15/11/2026 01:07" en la hora del celular. */
fun momentoIso(iso: String): String =
    OffsetDateTime.parse(iso).atZoneSameInstant(java.time.ZoneId.systemDefault()).format(fechaHora)

/** Número sin decimales innecesarios: 10.00 → "10", 1.50 → "1.5" */
fun BigDecimal.sinCeros(): String = stripTrailingZeros().toPlainString()
