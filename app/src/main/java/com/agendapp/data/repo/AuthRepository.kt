package com.agendapp.data.repo

import androidx.room.withTransaction
import com.agendapp.data.local.AppDatabase
import com.agendapp.data.remote.ApiService
import com.agendapp.data.remote.LoginRequest
import com.agendapp.data.remote.Resultado
import com.agendapp.data.remote.UsuarioDto
import com.agendapp.data.remote.llamarApi
import com.agendapp.data.session.SessionStore
import com.agendapp.data.session.Tokens
import com.agendapp.data.session.Usuario
import kotlinx.coroutines.flow.Flow

class AuthRepository(
    private val api: ApiService,
    private val sesion: SessionStore,
    private val db: AppDatabase,
) {
    val usuario: Flow<Usuario?> = sesion.usuario

    suspend fun ingresar(username: String, password: String): Resultado<Usuario> {
        val resultado = llamarApi { api.login(LoginRequest(username.trim(), password)) }
        return when (resultado) {
            is Resultado.Error -> resultado
            is Resultado.Exito -> {
                val datos = resultado.valor
                val usuario = datos.usuario.aUsuario()
                sesion.guardarSesion(Tokens(datos.access, datos.refresh), usuario)
                Resultado.Exito(usuario)
            }
        }
    }

    /**
     * Cierra la sesión y borra los datos locales, para que otra persona que use el
     * celular no vea la información del usuario anterior.
     * (Desde la Fase 3: antes de cerrar habrá que avisar si quedan datos sin sincronizar.)
     */
    suspend fun cerrarSesion() {
        db.withTransaction {
            db.productoDao().borrarTodo()
            db.parametroDao().borrarTodo()
            db.feriadoDao().borrarTodo()
            db.tarifaDao().borrarTodo()
            db.horarioDao().borrarTodo()
        }
        sesion.cerrarSesion()
    }
}

fun UsuarioDto.aUsuario() = Usuario(
    id = id,
    username = username,
    nombreCompleto = nombreCompleto.ifBlank { username },
    rol = rol,
    modalidadLiquidacion = modalidadLiquidacion,
    fechaAlta = fechaAlta,
)
