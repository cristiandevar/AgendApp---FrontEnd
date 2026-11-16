package com.agendapp.ui.libreta

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agendapp.BuildConfig
import com.agendapp.data.session.Usuario
import com.agendapp.dominio.ArmadoHoja
import com.agendapp.dominio.Renglon
import com.agendapp.ui.AppViewModelFactory
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** Página del pager que corresponde a hoy; cada página es un día (anterior = -1). */
private const val PAGINA_HOY = 50_000
private const val TOTAL_PAGINAS = 100_000

/**
 * La libreta del empleado: una hoja por día. Deslizar a la izquierda → días siguientes;
 * a la derecha → días anteriores. Calendario para saltar a una fecha.
 * (Entrega 3a: solo lectura. El botón (+) para agregar eventos llega en la 3b.)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibretaScreen(usuario: Usuario, vm: LibretaViewModel = viewModel(factory = AppViewModelFactory)) {
    val datos by vm.datos.collectAsStateWithLifecycle()
    val descarga by vm.descarga.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val alcance = rememberCoroutineScope()
    val hoy = remember { LocalDate.now() }
    val pager = rememberPagerState(initialPage = PAGINA_HOY) { TOTAL_PAGINAS }

    var menuAbierto by remember { mutableStateOf(false) }
    var calendarioAbierto by rememberSaveable { mutableStateOf(false) }
    var confirmarSalida by rememberSaveable { mutableStateOf(false) }
    var pendientesAlSalir by remember { mutableStateOf(0) }
    var renglonElegido by remember { mutableStateOf<Renglon?>(null) }

    fun fechaDePagina(pagina: Int): LocalDate = hoy.plusDays((pagina - PAGINA_HOY).toLong())
    fun paginaDeFecha(fecha: LocalDate): Int = PAGINA_HOY + ChronoUnit.DAYS.between(hoy, fecha).toInt()

    LaunchedEffect(usuario.id) { vm.iniciar(usuario) }
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
                        Text("AgendApp")
                        Text(
                            usuario.nombreCompleto,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { calendarioAbierto = true }) {
                        Icon(Icons.Filled.DateRange, contentDescription = "Ir a una fecha")
                    }
                    if (descarga.descargando) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(12.dp).size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        IconButton(onClick = vm::actualizar) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Actualizar datos")
                        }
                    }
                    Box {
                        IconButton(onClick = { menuAbierto = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Más opciones")
                        }
                        DropdownMenu(expanded = menuAbierto, onDismissRequest = { menuAbierto = false }) {
                            if (BuildConfig.DEBUG) {
                                DropdownMenuItem(
                                    text = { Text("Cargar datos de ejemplo") },
                                    onClick = { menuAbierto = false; vm.cargarEjemplo() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Borrar datos de ejemplo") },
                                    onClick = { menuAbierto = false; vm.borrarEjemplo() },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Cerrar sesión") },
                                onClick = {
                                    menuAbierto = false
                                    alcance.launch {
                                        pendientesAlSalir = vm.pendientes()
                                        confirmarSalida = true
                                    }
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        floatingActionButton = {
            // En la hoja de hoy irá el botón (+) (entrega 3b); en los demás días, volver a hoy.
            if (pager.currentPage != PAGINA_HOY) {
                ExtendedFloatingActionButton(
                    onClick = { alcance.launch { pager.animateScrollToPage(PAGINA_HOY) } },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) { Text("Hoy") }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val d = datos
        if (d == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize().padding(padding),
                key = { it },
            ) { pagina ->
                val fecha = fechaDePagina(pagina)
                val hoja = remember(d, fecha) { ArmadoHoja.armar(d, fecha, hoy) }
                HojaLibreta(hoja, alTocarRenglon = { renglonElegido = it })
            }
        }
    }

    renglonElegido?.let { DetalleRenglon(it, alCerrar = { renglonElegido = null }) }

    if (calendarioAbierto) {
        val estado = rememberDatePickerState(
            initialSelectedDateMillis = fechaDePagina(pager.currentPage)
                .atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { calendarioAbierto = false },
            confirmButton = {
                TextButton(onClick = {
                    calendarioAbierto = false
                    estado.selectedDateMillis?.let { ms ->
                        val elegida = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        val destino = paginaDeFecha(elegida).coerceIn(0, TOTAL_PAGINAS - 1)
                        alcance.launch { pager.scrollToPage(destino) }
                    }
                }) { Text("Ir") }
            },
            dismissButton = { TextButton(onClick = { calendarioAbierto = false }) { Text("Cancelar") } },
        ) {
            DatePicker(state = estado)
        }
    }

    if (confirmarSalida) {
        AlertDialog(
            onDismissRequest = { confirmarSalida = false },
            title = { Text("Cerrar sesión") },
            text = {
                Text(
                    if (pendientesAlSalir > 0)
                        "Tenés $pendientesAlSalir movimientos sin sincronizar. Quedan guardados en este celular y se envían cuando vuelvas a ingresar. ¿Querés salir?"
                    else "¿Querés cerrar la sesión?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmarSalida = false
                    vm.cerrarSesion()
                }) { Text("Salir") }
            },
            dismissButton = { TextButton(onClick = { confirmarSalida = false }) { Text("Cancelar") } },
        )
    }
}
