package com.agendapp.ui.libreta

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agendapp.data.remote.Resultado
import com.agendapp.data.repo.AuthRepository
import com.agendapp.data.repo.CatalogoRepository
import com.agendapp.data.repo.MovimientosRepository
import com.agendapp.data.session.Usuario
import com.agendapp.dominio.DatosEmpleado
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
