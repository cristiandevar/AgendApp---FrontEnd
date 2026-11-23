package com.agendapp.ui.libreta

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agendapp.data.remote.Resultado
import com.agendapp.data.repo.AuthRepository
import com.agendapp.data.repo.CatalogoRepository
import com.agendapp.data.repo.MovimientosRepository
import com.agendapp.data.session.Usuario
import com.agendapp.dominio.DatosEmpleado
import com.agendapp.dominio.ProductoDisponible
import com.agendapp.dominio.Reglas
import com.agendapp.dominio.TipoFichaje
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.agendapp.util.comoPesos
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val HH_MM = DateTimeFormatter.ofPattern("HH:mm")

/** Lo que pasó al tocar Aceptar en el popup de fichaje. */
sealed interface ResultadoFichaje {
    data class Registrado(val mensaje: String) : ResultadoFichaje
    data object FaltaMotivo : ResultadoFichaje
    data class Rechazado(val mensaje: String) : ResultadoFichaje
}

/** Lo que pasó al tocar Aceptar en el popup de retiro. */
sealed interface ResultadoRetiro {
    data class Guardado(val mensaje: String) : ResultadoRetiro
    data object SinCambios : ResultadoRetiro
    data class Rechazado(val mensaje: String) : ResultadoRetiro
}

data class EstadoDescargaLibreta(
    val descargando: Boolean = false,
    val mensaje: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class LibretaViewModel(
    private val catalogo: CatalogoRepository,
    private val movimientos: MovimientosRepository,
    private val auth: AuthRepository,
) : ViewModel() {

    private val usuario = MutableStateFlow<Usuario?>(null)

    /** Datos del empleado (de Room): se recalculan solos ante cualquier cambio local. */
    val datos: StateFlow<DatosEmpleado?> = usuario
        .flatMapLatest { u -> if (u == null) emptyFlow() else movimientos.datosEmpleado(u.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _descarga = MutableStateFlow(EstadoDescargaLibreta())
    val descarga: StateFlow<EstadoDescargaLibreta> = _descarga.asStateFlow()

    fun iniciar(u: Usuario) {
        usuario.value = u
        viewModelScope.launch {
            // Recién logueado (o base local recreada): descarga el catálogo automáticamente.
            if (catalogo.ultimaDescarga.first() == null || catalogo.productos.first().isEmpty()) actualizar()
        }
    }

    fun actualizar() {
        val u = usuario.value ?: return
        if (_descarga.value.descargando) return
        _descarga.update { EstadoDescargaLibreta(descargando = true) }
        viewModelScope.launch {
            _descarga.value = when (val r = catalogo.descargar(u)) {
                is Resultado.Exito -> EstadoDescargaLibreta(mensaje = "Datos actualizados.")
                is Resultado.Error -> EstadoDescargaLibreta(
                    mensaje = if (r.sinConexion) "Sin conexión: se usan los últimos datos descargados." else r.mensaje,
                )
            }
        }
    }

    fun mensajeVisto() = _descarga.update { it.copy(mensaje = null) }

    /** Evita registrar dos veces si se toca Aceptar repetido. */
    private val escritura = Mutex()

    /**
     * Registra el fichaje con la fecha y hora de este momento (el de tocar Aceptar).
     * Los controles se hacen otra vez acá, con los fichajes leídos de la base.
     */
    suspend fun fichar(motivo: String): ResultadoFichaje = escritura.withLock {
        val u = usuario.value ?: return ResultadoFichaje.Rechazado("No hay una sesión iniciada.")
        val d = datos.value ?: return ResultadoFichaje.Rechazado("Todavía se están cargando los datos.")
        val ahora = OffsetDateTime.now()
        val fecha = ahora.toLocalDate()
        val control = Reglas.controlarFichaje(
            fichajesDelDia = movimientos.fichajesDelDia(u.id, fecha),
            horariosDelDia = d.horarios.filter { it.fecha == fecha },
            toleranciaMinutos = d.toleranciaMinutos,
            ahora = ahora.toLocalTime(),
            motivo = motivo,
        )
        when (control) {
            is Reglas.ControlFichaje.Valido -> {
                movimientos.registrarFichaje(u.id, fecha, control, ahora)
                val tipo = if (control.tipo == TipoFichaje.ENTRADA) "Entrada" else "Salida"
                ResultadoFichaje.Registrado("$tipo registrada a las ${control.hora.format(HH_MM)}.")
            }
            is Reglas.ControlFichaje.FaltaMotivo -> ResultadoFichaje.FaltaMotivo
            is Reglas.ControlFichaje.Rechazado -> ResultadoFichaje.Rechazado(control.mensaje)
        }
    }

    /** Anula un fichaje propio si todavía se puede. Devuelve el mensaje para mostrar. */
    suspend fun anularFichaje(id: String): String = escritura.withLock {
        val u = usuario.value ?: return "No hay una sesión iniciada."
        val f = movimientos.fichaje(id) ?: return "No se encontró el fichaje."
        val ahora = OffsetDateTime.now()
        val plazo = datos.value?.plazoMinutos ?: 10
        Reglas.impedimentoParaAnular(f, movimientos.fichajesDelDia(u.id, f.fecha), ahora, plazo)?.let { return it }
        movimientos.anularFichaje(id, ahora)
        val tipo = if (f.tipo == TipoFichaje.ENTRADA) "Entrada" else "Salida"
        "$tipo de las ${f.hora.format(HH_MM)} anulada."
    }

    /**
     * Registra un retiro nuevo (fecha y hora de este momento) o modifica [retiroId].
     * Los controles se repiten acá con los retiros leídos de la base.
     */
    suspend fun guardarRetiro(productoId: String?, cantidad: Int, retiroId: String? = null): ResultadoRetiro =
        escritura.withLock {
            val u = usuario.value ?: return ResultadoRetiro.Rechazado("No hay una sesión iniciada.")
            val d = datos.value ?: return ResultadoRetiro.Rechazado("Todavía se están cargando los datos.")
            val ahora = OffsetDateTime.now()
            val original = retiroId?.let { movimientos.retiro(it) ?: return ResultadoRetiro.Rechazado("No se encontró el retiro.") }
            if (original != null) {
                Reglas.impedimentoParaCambiarRetiro(original, ahora, d.plazoMinutos)?.let { return ResultadoRetiro.Rechazado(it) }
            }
            val fecha = original?.fecha ?: ahora.toLocalDate()
            val control = Reglas.controlarRetiro(
                productoId = productoId,
                cantidad = cantidad,
                productosActivos = d.productos,
                retiros = movimientos.retirosDelDia(u.id, fecha),
                fecha = fecha,
                original = original,
            )
            when (control) {
                is Reglas.ControlRetiro.Valido -> {
                    val texto = "${control.cantidad} × ${control.producto.nombre} " +
                        "(${control.precioCosto.multiply(BigDecimal(control.cantidad)).comoPesos()})"
                    if (original == null) {
                        movimientos.registrarRetiro(u.id, fecha, ahora.toLocalTime().truncatedTo(ChronoUnit.MINUTES), control, ahora)
                        ResultadoRetiro.Guardado("Retiro registrado: $texto.")
                    } else {
                        movimientos.modificarRetiro(original.id, control)
                        ResultadoRetiro.Guardado("Retiro modificado: $texto.")
                    }
                }
                Reglas.ControlRetiro.SinCambios -> ResultadoRetiro.SinCambios
                is Reglas.ControlRetiro.Rechazado -> ResultadoRetiro.Rechazado(control.mensaje)
            }
        }

    /** Anula un retiro propio si todavía está en plazo. Devuelve el mensaje para mostrar. */
    suspend fun anularRetiro(id: String): String = escritura.withLock {
        val r = movimientos.retiro(id) ?: return "No se encontró el retiro."
        val ahora = OffsetDateTime.now()
        Reglas.impedimentoParaCambiarRetiro(r, ahora, datos.value?.plazoMinutos ?: 10)?.let { return it }
        movimientos.anularRetiro(id, ahora)
        "Retiro anulado: ${r.cantidad} × ${r.productoNombre}."
    }

    /** Producto activo con ese código (lo que leyó el escáner), o null si no hay. */
    suspend fun productoPorCodigo(codigo: String): ProductoDisponible? = movimientos.productoPorCodigo(codigo)

    /** Cantidad de movimientos sin sincronizar (se avisa antes de cerrar sesión). */
    suspend fun pendientes(): Int = usuario.value?.let { movimientos.pendientes(it.id) } ?: 0

    fun cargarEjemplo() {
        val u = usuario.value ?: return
        viewModelScope.launch { movimientos.cargarEjemplo(u.id) }
    }

    fun borrarEjemplo() {
        viewModelScope.launch { movimientos.borrarEjemplo() }
    }

    fun cerrarSesion() {
        viewModelScope.launch { auth.cerrarSesion() }
    }
}
