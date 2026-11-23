package com.agendapp.ui.libreta

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.agendapp.dominio.Hoja
import com.agendapp.dominio.Renglon
import com.agendapp.dominio.TipoRenglon
import com.agendapp.ui.theme.Margen
import com.agendapp.ui.theme.Papel
import com.agendapp.ui.theme.Renglon as ColorRenglon
import com.agendapp.ui.theme.Tinta
import com.agendapp.util.comoPesos
import java.math.BigDecimal
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Medidas de la hoja rayada. */
val ALTO_RENGLON: Dp = 44.dp
val ANCHO_MARGEN: Dp = 36.dp
private const val RENGLONES_EN_BLANCO = 30

val VerdeRecibido = Color(0xFF2E7D32)
private val GrisAnulado = Color(0xFF9E9A90)

private val formatoFecha = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale("es", "AR"))
private val formatoHora = DateTimeFormatter.ofPattern("HH:mm")

/** "+$9.791,67" / "-$1.800,00" */
fun BigDecimal.conSigno(): String = if (signum() > 0) "+${comoPesos()}" else comoPesos()

/**
 * Una hoja de la libreta: encabezado y renglones de 4 secciones (hora, tipo, detalle,
 * monto) sobre papel rayado con margen rojo. El primer y el último renglón (saldos) se
 * calculan cada vez que se dibuja la hoja.
 */
@Composable
fun HojaLibreta(hoja: Hoja, alTocarRenglon: (Renglon) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Papel)
            .drawBehind {
                // Margen rojo vertical (fijo, no se desplaza con los renglones).
                val x = ANCHO_MARGEN.toPx()
                drawLine(Margen.copy(alpha = 0.7f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
            },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp), // espacio para el botón flotante
        ) {
            item { Encabezado(hoja) }
            items(hoja.renglones, key = { clave(it) }) { renglon ->
                FilaRenglon(renglon, alTocar = { alTocarRenglon(renglon) })
            }
            items(RENGLONES_EN_BLANCO) { RenglonVacio() }
        }
    }
}

private fun clave(r: Renglon): String = when (r) {
    is Renglon.SaldoAnterior -> "saldo-anterior"
    is Renglon.SaldoALaFecha -> "saldo-a-la-fecha"
    is Renglon.Evento -> r.id
}

@Composable
private fun Encabezado(hoja: Hoja) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = ANCHO_MARGEN + 12.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    ) {
        Text(
            hoja.fecha.format(formatoFecha).replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = Tinta,
        )
        Text(
            if (hoja.esHoy) "Hoy · ${hoja.fecha.year}" else hoja.fecha.year.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        hoja.feriado?.let {
            Spacer(Modifier.height(6.dp))
            Etiqueta("Feriado: ${it.descripcion} · la hora vale ×${it.multiplicador.stripTrailingZeros().toPlainString()}", Margen)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (hoja.horarios.isEmpty()) "Sin horario asignado"
            else "Horario: " + hoja.horarios.joinToString(" · ") { "${it.inicio.format(formatoHora)}–${it.fin.format(formatoHora)}" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (hoja.esHoy) {
            Spacer(Modifier.height(4.dp))
            val desde = hoja.trabajandoDesde
            Etiqueta(
                if (desde != null) "Trabajando desde ${desde.format(formatoHora)}" else "Fuera de turno",
                if (desde != null) VerdeRecibido else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Etiqueta(texto: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.Medium)
    }
}

/** Renglón base: alto fijo y línea azul abajo, para que la hoja se vea rayada. */
@Composable
private fun RenglonBase(alTocar: (() -> Unit)?, contenido: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ALTO_RENGLON)
            .then(if (alTocar != null) Modifier.clickable(onClick = alTocar) else Modifier)
            .drawBehind {
                val y = size.height - 1.dp.toPx()
                drawLine(ColorRenglon, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            .padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { contenido() }
}

@Composable
private fun RenglonVacio() = RenglonBase(alTocar = null) {}

@Composable
private fun FilaRenglon(renglon: Renglon, alTocar: () -> Unit) {
    when (renglon) {
        is Renglon.SaldoAnterior -> FilaSaldo("Saldo anterior", renglon.monto, alTocar, cursiva = true)
        is Renglon.SaldoALaFecha -> FilaSaldo("Saldo a la fecha", renglon.monto, alTocar, cursiva = false)
        is Renglon.Evento -> FilaEvento(renglon, alTocar)
    }
}

@Composable
private fun FilaSaldo(texto: String, monto: BigDecimal, alTocar: () -> Unit, cursiva: Boolean) {
    RenglonBase(alTocar) {
        Spacer(Modifier.width(ANCHO_MARGEN + 12.dp))
        Text(
            texto,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            fontStyle = if (cursiva) FontStyle.Italic else FontStyle.Normal,
        )
        Text(
            monto.comoPesos(),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = if (monto.signum() < 0) Margen else Tinta,
        )
    }
}

@Composable
private fun FilaEvento(e: Renglon.Evento, alTocar: () -> Unit) {
    val anulado = e.anulado
    val decoracion = if (anulado) TextDecoration.LineThrough else TextDecoration.None
    val colorBase = if (anulado) GrisAnulado else MaterialTheme.colorScheme.onSurface
    RenglonBase(alTocar) {
        // Margen: marcas (corregido por el encargado / pendiente de sincronizar).
        Box(Modifier.width(ANCHO_MARGEN), contentAlignment = Alignment.Center) {
            when {
                e.corregido -> Icon(
                    Icons.Filled.Edit, contentDescription = "Cargado o corregido por el encargado",
                    tint = Margen, modifier = Modifier.size(14.dp),
                )
                e.pendiente -> Box(Modifier.size(6.dp).background(GrisAnulado, CircleShape))
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            e.hora.format(formatoHora), modifier = Modifier.width(48.dp),
            style = MaterialTheme.typography.bodyMedium, color = colorBase, textDecoration = decoracion,
        )
        Text(
            e.tipo.etiqueta, modifier = Modifier.width(72.dp),
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
            color = colorBase, textDecoration = decoracion,
        )
        Text(
            e.detalle, modifier = Modifier.weight(1f).padding(end = 6.dp),
            style = MaterialTheme.typography.bodyMedium, color = if (anulado) GrisAnulado else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textDecoration = decoracion,
        )
        e.monto?.let { monto ->
            Text(
                monto.conSigno(),
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                textDecoration = decoracion,
                color = when {
                    anulado -> GrisAnulado
                    e.tipo == TipoRenglon.RECIBIDO -> VerdeRecibido
                    monto.signum() < 0 -> Margen
                    else -> Tinta
                },
            )
        }
    }
}

/** Separación vertical entre filas de una tabla de detalle. */
val EspacioDetalle = Arrangement.spacedBy(6.dp)
