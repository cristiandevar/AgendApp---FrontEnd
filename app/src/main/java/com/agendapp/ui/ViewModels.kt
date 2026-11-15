package com.agendapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.agendapp.AgendAppApplication
import com.agendapp.data.repo.AuthRepository
import com.agendapp.data.session.Usuario
import com.agendapp.ui.inicio.InicioViewModel
import com.agendapp.ui.login.LoginViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Fábrica única de ViewModels: toma las dependencias del AppContainer. */
val AppViewModelFactory = viewModelFactory {
    initializer { RaizViewModel(app().container.authRepository) }
    initializer { LoginViewModel(app().container.authRepository) }
    initializer {
        val c = app().container
        InicioViewModel(c.catalogoRepository, c.authRepository)
    }
}

private fun androidx.lifecycle.viewmodel.CreationExtras.app() = this[APPLICATION_KEY] as AgendAppApplication

sealed interface EstadoSesion {
    data object Cargando : EstadoSesion
    data object SinSesion : EstadoSesion
    data class ConSesion(val usuario: Usuario) : EstadoSesion
}

/** Decide qué se muestra según haya sesión guardada (funciona sin conexión). */
class RaizViewModel(auth: AuthRepository) : ViewModel() {
    val estado: StateFlow<EstadoSesion> = auth.usuario
        .map { u -> if (u == null) EstadoSesion.SinSesion else EstadoSesion.ConSesion(u) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, EstadoSesion.Cargando)
}
