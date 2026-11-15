package com.agendapp.ui.inicio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agendapp.data.local.HorarioEntity
import com.agendapp.data.session.Usuario
import com.agendapp.ui.AppViewModelFactory
import com.agendapp.util.comoPesos
import com.agendapp.util.fechaIsoCorta
import com.agendapp.util.fechaIsoLarga
import com.agendapp.util.horaCorta
import com.agendapp.util.momentoIso
import com.agendapp.util.sinCeros
import java.time.LocalDate

/**
 * Inicio provisorio de la Fase 2. Muestra lo descargado al celular (funciona sin
 * conexión). Empleado: en la Fase 3 se reemplaza por la libreta. Encargado: en la
 * Fase 5 se reemplaza por el backoffice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InicioScreen(usuario: Usuario, vm: InicioViewModel = viewModel(factory = AppViewModelFactory)) {
    val datos by vm.datos.collectAsStateWithLifecycle()
    val ultimaDescarga by vm.ultimaDescarga.collectAsStateWithLifecycle()
    val descarga by vm.descarga.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmarSalida by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(usuario.id) { vm.alIniciar(usuario) }
    LaunchedEffect(descarga.mensaje) {
        descarga.mensaje?.let {
            snackbar.showSnackbar(it)
            vm.mensajeVisto()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (usuario.esEncargado) "AgendApp - Backoffice" else "AgendApp")
                        Text(
                            usuario.nombreCompleto,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                        )
                    }
                },
                actions = {
                    if (descarga.descargando) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(12.dp).size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        IconButton(onClick = { vm.actualizar(usuario) }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Actualizar datos")
                        }
                    }
                    IconButton(onClick = { confirmarSalida = true }) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Cerrar sesión")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val hoy = LocalDate.now().toString()
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Tarjeta {
                    Text(
                        if (usuario.esEncargado) "Backoffice en construcción" else "Tu libreta llega en la próxima fase",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (usuario.esEncargado)
                            "Por ahora podés ver los datos que la app descargó del servidor. La gestión de empleados, productos, horarios y liquidaciones se suma en la Fase 5."
                        else
                            "Por ahora podés ver los datos que la app guardó en tu celular para funcionar sin internet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                Tarjeta {
                    Titulo("Datos en el celular")
                    Fila("Última actualización", ultimaDescarga?.let { momentoIso(it) } ?: "Nunca")
                    Fila("Productos", datos.productos.size.toString())
                    Fila("Feriados", datos.feriados.size.toString())
                    Fila("Parámetros", datos.parametros.size.toString())
                    if (usuario.esEncargado) {
                        Fila("Tarifas cargadas", datos.tarifas.size.toString())
                        Fila("Horarios asignados", datos.horarios.size.toString())
                    }
                }
            }

            if (!usuario.esEncargado) {
                item {
                    val tarifa = datos.tarifas
                        .filter { it.empleadoId == usuario.id && it.vigenteDesde <= hoy }
                        .maxByOrNull { it.vigenteDesde }
                    Tarjeta {
                        Titulo("Mi tarifa por hora")
                        if (tarifa == null) {
                            Text("Todavía no tenés una tarifa cargada.", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Fila("Vigente desde ${fechaIsoCorta(tarifa.vigenteDesde)}", tarifa.monto.comoPesos())
                        }
                    }
                }
                item {
                    val proximos = datos.horarios
                        .filter { it.empleadoId == usuario.id && it.fecha >= hoy }
                        .groupBy { it.fecha }
                        .toSortedMap()
                        .entries
                        .take(3)
                    Tarjeta {
                        Titulo("Mis próximos horarios")
                        if (proximos.isEmpty()) {
                            Text("No tenés horarios asignados próximamente.", style = MaterialTheme.typography.bodyMedium)
                        }
                        proximos.forEach { (fecha, tramos) ->
                            Fila(
                                fechaIsoLarga(fecha).replaceFirstChar { it.uppercase() },
                                tramos.joinToString("  ·  ") { it.rango() },
                            )
                        }
                    }
                }
            }

            if (usuario.esEncargado) {
                item {
                    Tarjeta {
                        Titulo("Parámetros")
                        datos.parametros.forEach { Fila(it.descripcion, it.valor.sinCeros()) }
                    }
                }
            }

            item { Titulo("Productos (${datos.productos.size})", Modifier.padding(top = 4.dp)) }
            if (datos.productos.isEmpty()) {
                item {
                    Text(
                        "No hay productos descargados todavía.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(datos.productos, key = { it.id }) { p ->
                Tarjeta {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.nombre, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (p.maxDiario > 0) "Máximo por día: ${p.maxDiario}" else "Sin límite diario",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(p.precioCosto.comoPesos(), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }

    if (confirmarSalida) {
        AlertDialog(
            onDismissRequest = { confirmarSalida = false },
            title = { Text("Cerrar sesión") },
            text = { Text("Se van a borrar del celular los datos descargados. ¿Querés salir?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmarSalida = false
                    vm.cerrarSesion()
                }) { Text("Salir") }
            },
            dismissButton = {
                TextButton(onClick = { confirmarSalida = false }) { Text("Cancelar") }
            },
        )
    }
}

private fun HorarioEntity.rango() = "${horaCorta(horaInicio)} a ${horaCorta(horaFin)}"

@Composable
private fun Tarjeta(contenido: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) { contenido() }
    }
}

@Composable
private fun Titulo(texto: String, modifier: Modifier = Modifier) {
    Text(
        texto,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun Fila(etiqueta: String, valor: String) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(etiqueta, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Spacer(Modifier.size(12.dp))
            Text(valor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}
