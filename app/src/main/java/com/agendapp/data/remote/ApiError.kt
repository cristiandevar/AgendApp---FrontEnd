package com.agendapp.data.remote

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.HttpException
import java.io.IOException

/** Resultado de una operación contra la API, con un mensaje listo para mostrar. */
sealed interface Resultado<out T> {
    data class Exito<T>(val valor: T) : Resultado<T>
    data class Error(val mensaje: String, val sinConexion: Boolean = false) : Resultado<Nothing>
}

object ApiError {

    const val SIN_CONEXION = "No se pudo conectar con el servidor. Revisá la conexión a internet."

    private val json = Json { ignoreUnknownKeys = true }

    // Mensajes en inglés que manda Django REST Framework / SimpleJWT.
    private val traducciones = mapOf(
        "No active account found with the given credentials" to "Usuario o contraseña incorrectos.",
        "Given token not valid for any token type" to "La sesión expiró. Ingresá de nuevo.",
        "Token is invalid or expired" to "La sesión expiró. Ingresá de nuevo.",
        "Authentication credentials were not provided." to "La sesión expiró. Ingresá de nuevo.",
        "You do not have permission to perform this action." to "No tenés permiso para realizar esta acción.",
    )

    /** Convierte cualquier excepción de red/API en un mensaje entendible. */
    fun desde(e: Throwable): Resultado.Error = when (e) {
        is HttpException -> Resultado.Error(mensajeHttp(e.code(), e.response()?.errorBody()?.string()))
        is IOException -> Resultado.Error(SIN_CONEXION, sinConexion = true)
        is SerializationException -> Resultado.Error("El servidor devolvió una respuesta inesperada.")
        else -> Resultado.Error(e.message ?: "Ocurrió un error inesperado.")
    }

    /**
     * Formatos que puede devolver el backend:
     * - {"detalle": "..."} o {"detalle": ["...", "..."]}  (reglas de negocio de AgendApp)
     * - {"detail": "..."}                                   (DRF / SimpleJWT)
     * - {"campo": ["..."]}                                  (validación de campos de DRF)
     */
    fun mensajeHttp(codigo: Int, cuerpo: String?): String {
        val desdeCuerpo = cuerpo?.takeIf { it.isNotBlank() }?.let { leerCuerpo(it) }
        if (!desdeCuerpo.isNullOrBlank()) return desdeCuerpo
        return when (codigo) {
            400 -> "Los datos enviados no son válidos."
            401 -> "La sesión expiró. Ingresá de nuevo."
            403 -> "No tenés permiso para realizar esta acción."
            404 -> "No se encontró lo solicitado en el servidor."
            409 -> "La operación no se puede realizar en el estado actual."
            in 500..599 -> "Error del servidor ($codigo). Probá de nuevo más tarde."
            else -> "Error inesperado del servidor ($codigo)."
        }
    }

    private fun leerCuerpo(cuerpo: String): String? {
        val elemento = try {
            json.parseToJsonElement(cuerpo)
        } catch (e: SerializationException) {
            return null
        }
        val objeto = elemento as? JsonObject ?: return textos(elemento).joinToString(" ").ifBlank { null }
        for (clave in listOf("detalle", "detail")) {
            objeto[clave]?.let { valor ->
                return textos(valor).joinToString(" ") { traducir(it) }.ifBlank { null }
            }
        }
        // Errores por campo: "campo: mensaje"
        return objeto.entries
            .filter { it.key != "code" && it.key != "messages" }
            .flatMap { (campo, valor) ->
                textos(valor).map { if (campo == "non_field_errors") traducir(it) else "$campo: ${traducir(it)}" }
            }
            .joinToString("\n")
            .ifBlank { null }
    }

    private fun textos(elemento: JsonElement): List<String> = when (elemento) {
        is JsonPrimitive -> listOf(elemento.content)
        is JsonArray -> elemento.flatMap { textos(it) }
        is JsonObject -> elemento.values.flatMap { textos(it) }
    }

    private fun traducir(texto: String) = traducciones[texto] ?: texto
}

/** Ejecuta una llamada a la API y la envuelve en un [Resultado]. */
suspend fun <T> llamarApi(bloque: suspend () -> T): Resultado<T> =
    try {
        Resultado.Exito(bloque())
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        ApiError.desde(e)
    }
