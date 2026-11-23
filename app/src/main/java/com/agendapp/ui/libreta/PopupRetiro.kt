package com.agendapp.ui.libreta

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.agendapp.dominio.Busqueda
import com.agendapp.dominio.DatosEmpleado
import com.agendapp.dominio.ProductoDisponible
import com.agendapp.dominio.Reglas
import com.agendapp.dominio.Retiro
import com.agendapp.ui.theme.Margen
import com.agendapp.ui.theme.Tinta
import com.agendapp.util.comoPesos
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Popup para retirar un producto, o para modificar [original] (mismo popup con los datos
 * cargados). Búsqueda incremental por nombre (o código), escáner de código de barras/QR,
 * cantidad con − y + hasta el máximo diario, y subtotal en vivo. Al tocar Aceptar el
 * ViewModel vuelve a controlar todo.
 */
@Composable
fun PopupRetiro(
    datos: DatosEmpleado,
    original: Retiro?,
    buscarPorCodigo: suspend (String) -> ProductoDisponible?,
    alAceptar: suspend (productoId: String?, cantidad: Int) -> ResultadoRetiro,
    alTerminar: (mensaje: String?) -> Unit,
    alCancelar: () -> Unit,
) {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()

    var productoId by rememberSaveable { mutableStateOf(original?.productoId) }
    var cantidad by rememberSaveable { mutableStateOf(original?.cantidad ?: 1) }
    var busqueda by rememberSaveable { mutableStateOf("") }
    var buscando by rememberSaveable { mutableStateOf(original == null) }
    var error by remember { mutableStateOf<String?>(null) }
    var guardando by remember { mutableStateOf(false) }

    val fecha = original?.fecha ?: LocalDate.now()
    // Al modificar, el plazo se vigila con el popup abierto: al vencer, no deja aceptar.
    val ahora = rememberAhora()
    val vencido = original?.let { Reglas.impedimentoParaCambiarRetiro(it, ahora, datos.plazoMinutos) }
    val mismoQueElOriginal = original != null && original.productoId == productoId
    // El producto elegido; al modificar, el original puede no estar más en el catálogo.
    val producto = datos.productos.firstOrNull { it.id == productoId }
        ?: original?.takeIf { mismoQueElOriginal }
            ?.let { ProductoDisponible(it.productoId, it.productoNombre, it.precioCosto, maxDiario = 0) }
    // Precio congelado: al modificar sin cambiar de producto se mantiene el del retiro.
    val precio = if (mismoQueElOriginal) original!!.precioCosto else producto?.precioCosto
    val tope = producto?.let { Reglas.restanteDiario(it, datos.retiros, fecha, original?.id) }

    fun elegir(p: ProductoDisponible) {
        productoId = p.id
        buscando = false
        busqueda = ""
        error = null
        val restante = Reglas.restanteDiario(p, datos.retiros, fecha, original?.id)
        cantidad = if (original != null && p.id == original.productoId) original.cantidad else 1
        if (restante != null && restante >= 1) cantidad = cantidad.coerceAtMost(restante)
    }

    fun escanear() {
        error = null
        Escaner.escanear(
            contexto,
            alLeer = { codigo ->
                alcance.launch {
                    val p = buscarPorCodigo(codigo)
                    if (p == null) error = "Producto no encontrado (código $codigo)." else elegir(p)
                }
            },
            alFallar = { error = it },
        )
    }

    AlertDialog(
        onDismissRequest = { if (!guardando) alCancelar() },
        title = { Text(if (original == null) "Retiro de producto" else "Modificar retiro") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (original != null) {
                    if (vencido == null) {
                        Text(
                            "Tiempo para modificarlo: " +
                                Reglas.formatoCuentaRegresiva(Reglas.segundosRestantes(original.registradoEn, ahora, datos.plazoMinutos) ?: 0L),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(vencido, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                }
                if (buscando || producto == null) {
                    OutlinedTextField(
                        value = busqueda,
                        onValueChange = { busqueda = it; error = null },
                        label = { Text("Buscar producto") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = ::escanear, enabled = !guardando) {
                                Icon(IconoCodigo, contentDescription = "Escanear código de barras o QR")
                            }
                        },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ListaProductos(Busqueda.filtrar(datos.productos, busqueda), hayCatalogo = datos.productos.isNotEmpty(), alElegir = ::elegir)
                    if (producto != null) {
                        TextButton(onClick = { buscando = false }) { Text("Volver a ${producto.nombre}") }
                    }
                } else {
                    ProductoElegido(producto, precio ?: producto.precioCosto, alCambiar = { buscando = true; error = null })
                    Cantidad(
                        cantidad = cantidad,
                        puedeRestar = cantidad > 1 && !guardando,
                        puedeSumar = (tope == null || cantidad < tope) && !guardando,
                        alCambiar = { cantidad = it; error = null },
                    )
                    Text(
                        when {
                            tope == null -> "Sin límite diario"
                            tope == 0 -> "Ya retiraste el máximo de hoy (${producto.maxDiario})."
                            original != null -> "Podés dejar hasta $tope (máximo ${producto.maxDiario} por día)."
                            else -> "Te quedan $tope de hoy"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (tope == 0) Margen else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Subtotal", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        // En positivo: el signo negativo solo tiene sentido en la hoja (estado de cuenta).
                        Text(
                            (precio ?: producto.precioCosto).multiply(BigDecimal(cantidad)).comoPesos(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Tinta,
                        )
                    }
                }
                error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !guardando && producto != null && !buscando && tope != 0 && vencido == null,
                onClick = {
                    guardando = true
                    error = null
                    alcance.launch {
                        when (val r = alAceptar(productoId, cantidad)) {
                            is ResultadoRetiro.Guardado -> alTerminar(r.mensaje)
                            ResultadoRetiro.SinCambios -> alTerminar(null)
                            is ResultadoRetiro.Rechazado -> {
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

@Composable
private fun ListaProductos(productos: List<ProductoDisponible>, hayCatalogo: Boolean, alElegir: (ProductoDisponible) -> Unit) {
    if (productos.isEmpty()) {
        Text(
            if (hayCatalogo) "No hay productos que coincidan."
            else "No hay productos descargados. Tocá actualizar cuando tengas conexión.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    // Lista con scroll propio (una LazyColumn no se puede usar dentro de un AlertDialog).
    Column(Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
        productos.forEach { p ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { alElegir(p) }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(p.nombre, style = MaterialTheme.typography.bodyLarge)
                    if (p.maxDiario > 0) {
                        Text(
                            "Máximo ${p.maxDiario} por día",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(p.precioCosto.comoPesos(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun ProductoElegido(producto: ProductoDisponible, precio: BigDecimal, alCambiar: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(producto.nombre, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "${precio.comoPesos()} c/u",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = alCambiar) { Text("Cambiar") }
    }
}

@Composable
private fun Cantidad(cantidad: Int, puedeRestar: Boolean, puedeSumar: Boolean, alCambiar: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconButton(onClick = { alCambiar(cantidad - 1) }, enabled = puedeRestar) {
            Text("−", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            cantidad.toString(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(72.dp),
        )
        FilledTonalIconButton(onClick = { alCambiar(cantidad + 1) }, enabled = puedeSumar) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
    }
}
