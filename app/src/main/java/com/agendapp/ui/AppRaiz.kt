package com.agendapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agendapp.ui.inicio.InicioScreen
import com.agendapp.ui.login.LoginScreen

/**
 * Raíz de la app: sin sesión muestra el login; con sesión, la pantalla del rol.
 * Al cerrar sesión (o si el servidor rechaza el token) vuelve sola al login.
 */
@Composable
fun AppRaiz(vm: RaizViewModel = viewModel(factory = AppViewModelFactory)) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val e = estado) {
            EstadoSesion.Cargando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            EstadoSesion.SinSesion -> LoginScreen()
            // La clave por usuario evita reutilizar el estado de pantalla de otra sesión.
            is EstadoSesion.ConSesion -> key(e.usuario.id) { InicioScreen(e.usuario) }
        }
    }
}
