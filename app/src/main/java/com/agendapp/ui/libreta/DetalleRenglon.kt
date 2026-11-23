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
import com.agendapp.dominio.Retiro
import com.agendapp.dominio.TipoFichaje
import com.agendapp.dominio.TipoRenglon
import com.agendapp.ui.theme.Margen
import com.agendapp.util.comoPesos
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val hhmm = DateTimeFormatter.ofPattern("HH:mm")
private val hhmmss = DateTimeFormatter.ofPattern("HH:mm:ss")

/**
 * Popup con el detalle completo de un renglón, con las acciones del empleado dentro del plazo:
 * - Fichaje: Anular, solo si es el último vigente del día.
 * - Retiro: Modificar y Anular.
 * La disponibilidad se recalcula cada segundo: al vencer el plazo, las acciones desaparecen solas.
 */
@Composable
fun DetalleRenglon(
    renglon: Renglon,
    datos: DatosEmpleado,
    alAnularFichaje: (Fichaje) -> Unit,
    alModificarRetiro: (Retiro) -> Unit,
    alAnularRetiro: (Retiro) -> Unit,
    alCerrar: () -> Unit,
) {
    val evento = renglon as? Renglon.Evento
    val fichaje = evento?.fichaje
    val retiro = evento?.retiro
    when {
        fichaje != null -> DetalleFichaje(renglon, fichaje, datos, alAnularFichaje, alCerrar)
        retiro != null -> DetalleRetiro(renglon, retiro, datos, alModificarRetiro, alAnularRetiro, alCerrar)
        else -> DetalleSimple(renglon, alCerrar)
    }
}

@Composable
private fun DetalleFichaje(
    renglon: Renglon,
    fichaje: Fichaje,
    datos: DatosEmpleado,
    alAnularFichaje: (Fichaje) -> Unit,
    alCerrar: () -> Unit,
) {
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
                    impedimento == null -> AvisoPlazo(actual.registradoEn, datos.plazoMinutos, ahora, "anularlo")
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
private fun DetalleRetiro(
    renglon: Renglon,
    retiro: Retiro,
    datos: DatosEmpleado,
    alModificar: (Retiro) -> Unit,
    alAnular: (Retiro) -> Unit,
    alCerrar: () -> Unit,
) {
    val ahora = rememberAhora()
    // Se usa la versión actual del retiro (pudo cambiar con el popup abierto).
    val actual = datos.retiros.firstOrNull { it.id == retiro.id } ?: retiro
    val impedimento = Reglas.impedimentoParaCambiarRetiro(actual, ahora, datos.plazoMinutos)
    var confirmando by remember { mutableStateOf(false) }

    if (confirmando && impedimento == null) {
        AlertDialog(
            onDismissRequest = { confirmando = false },
            title = { Text("¿Anular el retiro de ${actual.cantidad} × ${actual.productoNombre}?") },
            text = { Text("Queda tachado en la hoja y no se descuenta. No se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = { alAnular(actual) },
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
                if (impedimento == null) {
                    AvisoPlazo(actual.registradoEn, datos.plazoMinutos, ahora, "modificarlo o anularlo")
                }
            }
        },
        confirmButton = { TextButton(onClick = alCerrar) { Text("Cerrar") } },
        dismissButton = {
            if (impedimento == null) {
                Row {
                    TextButton(
                        onClick = { confirmando = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = Margen),
                    ) { Text("Anular") }
                    TextButton(onClick = { alModificar(actual) }) { Text("Modificar") }
                }
            }
        },
    )
}

/**
 * "Podés anularlo hasta las 22:48:45 (quedan 3:12)". El plazo corre desde el momento exacto
 * de registro (con segundos, igual que en el servidor), no desde la hora del renglón.
 */
@Composable
fun AvisoPlazo(registradoEn: OffsetDateTime?, plazoMinutos: Int, ahora: OffsetDateTime, acciones: String) {
    val limite = Reglas.limiteDelPlazo(registradoEn, plazoMinutos)?.aHoraLocal()
    val quedan = Reglas.segundosRestantes(registradoEn, ahora, plazoMinutos) ?: 0L
    Spacer(Modifier.height(4.dp))
    Text(
        "Podés $acciones hasta las ${limite?.format(hhmmss) ?: "--:--:--"} " +
            "(quedan ${Reglas.formatoCuentaRegresiva(quedan)}).",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun OffsetDateTime.aHoraLocal(): LocalTime = atZoneSameInstant(ZoneId.systemDefault()).toLocalTime()

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
                add("Subtotal" to ret.subtotal.comoPesos()) // en positivo: el negativo es de la hoja
            }
            TipoRenglon.RECIBIDO -> {
                add("Detalle" to r.detalle)
                r.monto?.let { add("Monto" to it.abs().comoPesos()) }
            }
        }
        // Momento exacto en que se registró (el plazo corre desde acá, con segundos).
        (r.fichaje?.registradoEn ?: r.retiro?.registradoEn)?.let {
            add("Registrado a las" to it.aHoraLocal().format(hhmmss))
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
