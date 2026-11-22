package com.agendapp.ui.libreta

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.agendapp.dominio.DatosEmpleado
import com.agendapp.dominio.Reglas
import com.agendapp.dominio.TipoFichaje
import com.agendapp.ui.theme.Margen
import com.agendapp.ui.theme.Tinta
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val HH_MM = DateTimeFormatter.ofPattern("HH:mm")
private val HH_MM_SS = DateTimeFormatter.ofPattern("HH:mm:ss")
private val FECHA_LARGA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale("es", "AR"))

/** La fecha y hora actuales, actualizadas en cada cambio de segundo (para relojes en vivo). */
@Composable
fun rememberAhora(): OffsetDateTime {
    val ahora by produceState(OffsetDateTime.now()) {
        while (true) {
            delay(1_000L - System.currentTimeMillis() % 1_000L)
            value = OffsetDateTime.now()
        }
    }
    return ahora
}

/**
 * Popup para fichar. El tipo (entrada/salida) sale del último fichaje vigente del día;
 * la fecha y la hora son de solo lectura (reloj en vivo) y se registra la hora del momento
 * de tocar Aceptar. Si la hora queda fuera de la tolerancia, o el día no tiene horario,
 * aparece el motivo, obligatorio. Al tocar Aceptar el ViewModel vuelve a controlar todo.
 */
@Composable
fun PopupFichaje(
    datos: DatosEmpleado,
    alAceptar: suspend (motivo: String) -> ResultadoFichaje,
    alRegistrar: (mensaje: String) -> Unit,
    alCancelar: () -> Unit,
) {
    val ahora = rememberAhora()
    val fecha = ahora.toLocalDate()
    val hora = ahora.toLocalTime()
    val tipo = Reglas.siguienteTipo(datos.fichajes.filter { it.fecha == fecha })
    val tolerancia = Reglas.evaluarTolerancia(
        datos.horarios.filter { it.fecha == fecha },
        tipo,
        hora.truncatedTo(ChronoUnit.MINUTES),
        datos.toleranciaMinutos,
    )

    var motivo by rememberSaveable { mutableStateOf("") }
    // El ViewModel pidió el motivo al tocar Aceptar: el campo queda visible y marcado.
    var exigirMotivo by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var guardando by remember { mutableStateOf(false) }
    val alcance = rememberCoroutineScope()

    val pideMotivo = tolerancia.fuera || exigirMotivo
    val faltaMotivo = exigirMotivo && motivo.isBlank()

    AlertDialog(
        onDismissRequest = { if (!guardando) alCancelar() },
        title = { Text(if (tipo == TipoFichaje.ENTRADA) "Fichar entrada" else "Fichar salida") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        hora.format(HH_MM_SS),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Tinta,
                    )
                    Text(
                        fecha.format(FECHA_LARGA).replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    tolerancia.referencia?.let { "Esperado: ${it.format(HH_MM)}" } ?: "Sin horario asignado",
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (pideMotivo) {
                    Text(
                        if (tolerancia.referencia == null) "Hoy no tenés horario asignado: indicá el motivo."
                        else "Fuera del horario (más de ${datos.toleranciaMinutos} min): indicá el motivo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Margen,
                    )
                    OutlinedTextField(
                        value = motivo,
                        onValueChange = { motivo = it; error = null },
                        label = { Text("Motivo") },
                        isError = faltaMotivo,
                        supportingText = { if (faltaMotivo) Text("El motivo es obligatorio.") },
                        enabled = !guardando,
                        minLines = 2,
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text("Dentro del horario", style = MaterialTheme.typography.bodyMedium, color = VerdeRecibido)
                }
                error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !guardando,
                onClick = {
                    guardando = true
                    error = null
                    alcance.launch {
                        when (val r = alAceptar(motivo)) {
                            is ResultadoFichaje.Registrado -> alRegistrar(r.mensaje)
                            ResultadoFichaje.FaltaMotivo -> {
                                exigirMotivo = true
                                guardando = false
                            }
                            is ResultadoFichaje.Rechazado -> {
                                error = r.mensaje
                                guardando = false
                            }
                        }
                    }
                },
            ) { Text("Aceptar") }
        },
        dismissButton = { TextButton(enabled = !guardando, onClick = alCancelar) { Text("Cancelar") } },
    )
}
