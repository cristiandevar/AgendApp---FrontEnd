package com.agendapp.ui.libreta

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agendapp.dominio.Calculos
import com.agendapp.dominio.Renglon
import com.agendapp.dominio.TipoRenglon
import com.agendapp.util.comoPesos
import java.time.format.DateTimeFormatter

private val hhmm = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Popup con el detalle completo de un renglón. En las entregas 3b y 3c suma las
 * acciones (anular / modificar) cuando el evento está dentro del plazo.
 */
@Composable
fun DetalleRenglon(renglon: Renglon, alCerrar: () -> Unit) {
    val (titulo, filas) = contenido(renglon)
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(titulo) },
        text = {
            Column(verticalArrangement = EspacioDetalle) {
                filas.forEach { (etiqueta, valor) -> FilaDetalle(etiqueta, valor) }
            }
        },
        confirmButton = { TextButton(onClick = alCerrar) { Text("Cerrar") } },
    )
}

@Composable
private fun FilaDetalle(etiqueta: String, valor: String) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(etiqueta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp).weight(1f))
            Text(valor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

private fun contenido(r: Renglon): Pair<String, List<Pair<String, String>>> = when (r) {
    is Renglon.SaldoAnterior -> "Saldo anterior" to listOf(
        "Saldo al cierre del día anterior" to r.monto.comoPesos(),
    )
    is Renglon.SaldoALaFecha -> "Resumen del día" to buildList {
        val s = r.resumen
        add("Horas trabajadas" to Calculos.formatoHoras(s.minutos))
        add("Ganado" to s.ganado.conSigno())
        add("Productos retirados" to s.retiros.negate().comoPesos())
        add("Monto recibido" to s.recibido.negate().comoPesos())
        add("Saldo anterior" to s.saldoAnterior.comoPesos())
        add("Saldo a la fecha" to s.saldo.comoPesos())
        if (s.turnoAbierto) add("Turno en curso" to "suma al fichar la salida")
    }
    is Renglon.Evento -> "${r.tipo.etiqueta} · ${r.hora.format(hhmm)}" to buildList {
        when (r.tipo) {
            TipoRenglon.ENTRADA, TipoRenglon.SALIDA -> {
                add("Horario esperado" to (r.horarioEsperado?.format(hhmm) ?: "Sin horario asignado"))
                if (r.detalle.isNotBlank()) add("Motivo" to r.detalle)
                if (r.monto != null) add("Ganado en el tramo" to r.monto.conSigno())
            }
            TipoRenglon.RETIRO -> r.retiro?.let { ret ->
                add("Producto" to ret.productoNombre)
                add("Cantidad" to ret.cantidad.toString())
                add("Precio unitario" to ret.precioCosto.comoPesos())
                add("Subtotal" to ret.subtotal.negate().comoPesos())
            }
            TipoRenglon.RECIBIDO -> {
                add("Detalle" to r.detalle)
                r.monto?.let { add("Monto" to it.comoPesos()) }
            }
        }
        val fichaje = r.fichaje
        when {
            r.anulado -> add("Estado" to "Anulado")
            fichaje != null && fichaje.delEncargado -> add("Estado" to "Cargado por el encargado")
            r.corregido -> add("Estado" to "Corregido por el encargado")
            r.pendiente -> add("Estado" to "Pendiente de sincronizar")
        }
    }
}
