package com.agendapp.ui.libreta

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agendapp.dominio.Calculos
import com.agendapp.dominio.DatosEmpleado
import com.agendapp.dominio.Fichaje
import com.agendapp.dominio.Reglas
import com.agendapp.dominio.Renglon
import com.agendapp.dominio.TipoFichaje
import com.agendapp.dominio.TipoRenglon
import com.agendapp.ui.theme.Margen
import com.agendapp.util.comoPesos
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val hhmm = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Popup con el detalle completo de un renglón. Si es un fichaje propio, dentro del plazo y
 * el último vigente del día, suma la acción Anular (con confirmación). La disponibilidad se
 * recalcula cada segundo: al vencer el plazo, la acción desaparece sola.
 * (Las acciones de los retiros llegan en la entrega 3c.)
 */
@Composable
fun DetalleRenglon(
    renglon: Renglon,
    datos: DatosEmpleado,
    alAnularFichaje: (Fichaje) -> Unit,
    alCerrar: () -> Unit,
) {
    val fichaje = (renglon as? Renglon.Evento)?.fichaje
    if (fichaje == null) {
        DetalleSimple(renglon, alCerrar)
        return
    }

    val ahora = rememberAhora()
    val delDia = datos.fichajes.filter { it.fecha == fichaje.fecha }
    // Se usa la versión actual del fichaje (pudo cambiar con el popup abierto).
    val actual = delDia.firstOrNull { it.id == fichaje.id } ?: fichaje
    val impedimento = Reglas.impedimentoParaAnular(actual, delDia, ahora, datos.plazoMinutos)
    val enPlazo = !actual.delEncargado && Reglas.dentroDelPlazo(actual.registradoEn, actual.anulado, ahora, datos.plazoMinutos)
    var confirmando by remember { mutableStateOf(false) }
    val nombre = if (actual.tipo == TipoFichaje.ENTRADA) "entrada" else "salida"

    if (confirmando && impedimento == null) {
        AlertDialog(
            onDismissRequest = { confirmando = false },
            title = { Text("¿Anular la $nombre de las ${actual.hora.format(hhmm)}?") },
            text = { Text("Queda tachada en la hoja y no se puede deshacer. Si te equivocaste, después podés volver a fichar.") },
            confirmButton = {
                TextButton(
                    onClick = { alAnularFichaje(actual) },
                    colors = ButtonDefaults.textButtonColors(contentColor = Margen),
                ) { Text("Anular") }
            },
            dismissButton = { TextButton(onClick = { confirmando = false }) { Text("Volver") } },
        )
        return
    }

    val (titulo, filas) = contenido(renglon)
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(titulo) },
        text = {
            Column(verticalArrangement = EspacioDetalle) {
                filas.forEach { (etiqueta, valor) -> FilaDetalle(etiqueta, valor) }
                when {
                    impedimento == null -> {
                        val limite = Reglas.limiteDelPlazo(actual.registradoEn, datos.plazoMinutos)
                            ?.atZoneSameInstant(ZoneId.systemDefault())?.toLocalTime()
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Podés anularlo hasta las ${limite?.format(hhmm) ?: "--:--"}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // Dentro del plazo pero no es el último: se explica por qué no se puede.
                    enPlazo -> {
                        Spacer(Modifier.height(4.dp))
                        Text(impedimento, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = alCerrar) { Text("Cerrar") } },
        dismissButton = {
            if (impedimento == null) {
                TextButton(
                    onClick = { confirmando = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = Margen),
                ) { Text("Anular") }
            }
        },
    )
}

@Composable
private fun DetalleSimple(renglon: Renglon, alCerrar: () -> Unit) {
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
