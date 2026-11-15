package com.agendapp.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sesionDataStore: DataStore<Preferences> by preferencesDataStore(name = "sesion")

/** Usuario logueado, guardado en el celular para poder abrir la app sin conexión. */
data class Usuario(
    val id: Int,
    val username: String,
    val nombreCompleto: String,
    val rol: String,
    val modalidadLiquidacion: String,
    val fechaAlta: String?,
) {
    val esEncargado: Boolean get() = rol == ROL_ENCARGADO

    companion object {
        const val ROL_ENCARGADO = "ENCARGADO"
        const val ROL_EMPLEADO = "EMPLEADO"
    }
}

data class Tokens(val access: String, val refresh: String)

/**
 * Sesión persistida con DataStore: tokens JWT, datos del usuario y momento de la
 * última descarga de datos. Si no hay sesión, [usuario] emite null.
 */
class SessionStore(private val context: Context) {

    private object Claves {
        val ACCESS = stringPreferencesKey("access")
        val REFRESH = stringPreferencesKey("refresh")
        val ID = intPreferencesKey("usuario_id")
        val USERNAME = stringPreferencesKey("username")
        val NOMBRE = stringPreferencesKey("nombre_completo")
        val ROL = stringPreferencesKey("rol")
        val MODALIDAD = stringPreferencesKey("modalidad_liquidacion")
        val FECHA_ALTA = stringPreferencesKey("fecha_alta")
        val ULTIMA_DESCARGA = stringPreferencesKey("ultima_descarga")
    }

    private val datos get() = context.sesionDataStore.data

    val usuario: Flow<Usuario?> = datos.map { p ->
        val id = p[Claves.ID]
        val access = p[Claves.ACCESS]
        if (id == null || access == null) null
        else Usuario(
            id = id,
            username = p[Claves.USERNAME].orEmpty(),
            nombreCompleto = p[Claves.NOMBRE].orEmpty(),
            rol = p[Claves.ROL] ?: Usuario.ROL_EMPLEADO,
            modalidadLiquidacion = p[Claves.MODALIDAD] ?: "DIARIA",
            fechaAlta = p[Claves.FECHA_ALTA],
        )
    }

    /** Fecha y hora (ISO) de la última descarga correcta, o null si nunca se descargó. */
    val ultimaDescarga: Flow<String?> = datos.map { it[Claves.ULTIMA_DESCARGA] }

    suspend fun tokens(): Tokens? {
        val p = datos.first()
        val access = p[Claves.ACCESS] ?: return null
        val refresh = p[Claves.REFRESH] ?: return null
        return Tokens(access, refresh)
    }

    suspend fun guardarSesion(tokens: Tokens, usuario: Usuario) {
        context.sesionDataStore.edit { p ->
            p[Claves.ACCESS] = tokens.access
            p[Claves.REFRESH] = tokens.refresh
            p[Claves.ID] = usuario.id
            p[Claves.USERNAME] = usuario.username
            p[Claves.NOMBRE] = usuario.nombreCompleto
            p[Claves.ROL] = usuario.rol
            p[Claves.MODALIDAD] = usuario.modalidadLiquidacion
            if (usuario.fechaAlta != null) p[Claves.FECHA_ALTA] = usuario.fechaAlta else p.remove(Claves.FECHA_ALTA)
        }
    }

    suspend fun guardarTokens(tokens: Tokens) {
        context.sesionDataStore.edit { p ->
            p[Claves.ACCESS] = tokens.access
            p[Claves.REFRESH] = tokens.refresh
        }
    }

    suspend fun marcarDescarga(momentoIso: String) {
        context.sesionDataStore.edit { it[Claves.ULTIMA_DESCARGA] = momentoIso }
    }

    suspend fun cerrarSesion() {
        context.sesionDataStore.edit { it.clear() }
    }
}
