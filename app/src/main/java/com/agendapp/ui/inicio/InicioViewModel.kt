package com.agendapp.ui.inicio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agendapp.data.local.FeriadoEntity
import com.agendapp.data.local.HorarioEntity
import com.agendapp.data.local.ParametroEntity
import com.agendapp.data.local.ProductoEntity
import com.agendapp.data.local.TarifaEntity
import com.agendapp.data.remote.Resultado
import com.agendapp.data.repo.AuthRepository
import com.agendapp.data.repo.CatalogoRepository
import com.agendapp.data.session.Usuario
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Todo lo descargado en el celular (se lee de Room, también sin conexión). */
data class DatosLocales(
    val productos: List<ProductoEntity> = emptyList(),
    val parametros: List<ParametroEntity> = emptyList(),
    val feriados: List<FeriadoEntity> = emptyList(),
    val tarifas: List<TarifaEntity> = emptyList(),
    val horarios: List<HorarioEntity> = emptyList(),
)

data class EstadoDescarga(
    val descargando: Boolean = false,
    val mensaje: String? = null,
    val esError: Boolean = false,
)

/**
 * Pantalla de inicio provisoria de la Fase 2 (para ambos roles): muestra lo descargado
 * y permite actualizarlo. En la Fase 3 la del empleado pasa a ser la libreta, y en la
 * Fase 5 la del encargado pasa a ser el backoffice.
 */
class InicioViewModel(
    private val catalogo: CatalogoRepository,
    private val auth: AuthRepository,
) : ViewModel() {

    val datos: StateFlow<DatosLocales> = combine(
        catalogo.productos, catalogo.parametros, catalogo.feriados, catalogo.tarifas, catalogo.horarios,
    ) { productos, parametros, feriados, tarifas, horarios ->
        DatosLocales(productos, parametros, feriados, tarifas, horarios)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DatosLocales())

    val ultimaDescarga: StateFlow<String?> =
        catalogo.ultimaDescarga.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _descarga = MutableStateFlow(EstadoDescarga())
    val descarga: StateFlow<EstadoDescarga> = _descarga.asStateFlow()

    /**
     * Al entrar con una sesión que todavía no descargó datos (recién logueado),
     * descarga automáticamente. Cerrar sesión borra la marca de descarga.
     */
    fun alIniciar(usuario: Usuario) {
        viewModelScope.launch {
            if (catalogo.ultimaDescarga.first() == null || catalogo.productos.first().isEmpty()) actualizar(usuario)
        }
    }

    fun actualizar(usuario: Usuario) {
        if (_descarga.value.descargando) return
        _descarga.update { EstadoDescarga(descargando = true) }
        viewModelScope.launch {
            _descarga.value = when (val r = catalogo.descargar(usuario)) {
                is Resultado.Exito -> EstadoDescarga(mensaje = "Datos actualizados.")
                is Resultado.Error -> EstadoDescarga(
                    mensaje = if (r.sinConexion) "Sin conexión: se muestran los últimos datos descargados." else r.mensaje,
                    esError = true,
                )
            }
        }
    }

    fun mensajeVisto() = _descarga.update { it.copy(mensaje = null) }

    fun cerrarSesion() {
        viewModelScope.launch { auth.cerrarSesion() }
    }
}
