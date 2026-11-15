package com.agendapp.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agendapp.data.remote.Resultado
import com.agendapp.data.repo.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val usuario: String = "",
    val password: String = "",
    val cargando: Boolean = false,
    val error: String? = null,
) {
    val puedeIngresar get() = usuario.isNotBlank() && password.isNotEmpty() && !cargando
}

class LoginViewModel(private val auth: AuthRepository) : ViewModel() {

    private val _estado = MutableStateFlow(LoginUiState())
    val estado: StateFlow<LoginUiState> = _estado.asStateFlow()

    fun cambiarUsuario(valor: String) = _estado.update { it.copy(usuario = valor, error = null) }

    fun cambiarPassword(valor: String) = _estado.update { it.copy(password = valor, error = null) }

    /** Si sale bien, la sesión queda guardada y la pantalla raíz cambia sola a la del rol. */
    fun ingresar() {
        val actual = _estado.value
        if (!actual.puedeIngresar) return
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            when (val r = auth.ingresar(actual.usuario, actual.password)) {
                is Resultado.Exito -> _estado.update { it.copy(cargando = false, password = "") }
                is Resultado.Error -> _estado.update { it.copy(cargando = false, error = r.mensaje) }
            }
        }
    }
}
