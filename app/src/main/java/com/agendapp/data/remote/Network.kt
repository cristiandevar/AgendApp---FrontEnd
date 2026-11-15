package com.agendapp.data.remote

import com.agendapp.BuildConfig
import com.agendapp.data.session.SessionStore
import com.agendapp.data.session.Tokens
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

val jsonApi = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

private val RUTAS_SIN_TOKEN = listOf("auth/login/", "auth/refresh/")

private fun Request.esSinToken() = RUTAS_SIN_TOKEN.any { url.encodedPath.endsWith(it) }

/** Agrega "Authorization: Bearer <access>" a cada pedido, salvo login y refresh. */
class AuthInterceptor(private val sesion: SessionStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val pedido = chain.request()
        if (pedido.esSinToken()) return chain.proceed(pedido)
        val access = runBlocking { sesion.tokens()?.access } ?: return chain.proceed(pedido)
        return chain.proceed(pedido.newBuilder().header("Authorization", "Bearer $access").build())
    }
}

/**
 * Ante un 401, renueva el token con el refresh y reintenta una vez.
 * Si el refresh también es rechazado, cierra la sesión (la app vuelve al login).
 */
class TokenAuthenticator(
    private val sesion: SessionStore,
    private val refreshApi: () -> RefreshApi,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.esSinToken() || intentos(response) >= 2) return null
        synchronized(this) {
            val actuales = runBlocking { sesion.tokens() } ?: return null
            val usado = response.request.header("Authorization")?.removePrefix("Bearer ")
            // Otro pedido ya renovó el token mientras este esperaba.
            if (usado != null && usado != actuales.access) {
                return response.request.conToken(actuales.access)
            }
            val respuesta = try {
                refreshApi().refresh(RefreshRequest(actuales.refresh)).execute()
            } catch (e: java.io.IOException) {
                return null // sin conexión: no se cierra la sesión
            }
            val nuevos = respuesta.body()
            if (!respuesta.isSuccessful || nuevos == null) {
                if (respuesta.code() == 401) runBlocking { sesion.cerrarSesion() }
                return null
            }
            val tokens = Tokens(nuevos.access, nuevos.refresh ?: actuales.refresh)
            runBlocking { sesion.guardarTokens(tokens) }
            return response.request.conToken(tokens.access)
        }
    }

    private fun Request.conToken(access: String) =
        newBuilder().header("Authorization", "Bearer $access").build()

    private fun intentos(response: Response): Int {
        var r: Response? = response
        var n = 1
        while (r?.priorResponse != null) {
            n++
            r = r.priorResponse
        }
        return n
    }
}

object Network {

    private val tipoJson = "application/json".toMediaType()

    private fun clienteBase(): OkHttpClient.Builder = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            }
        }

    private fun retrofit(cliente: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(cliente)
        .addConverterFactory(jsonApi.asConverterFactory(tipoJson))
        .build()

    fun crearApi(sesion: SessionStore): ApiService {
        val refreshApi by lazy { retrofit(clienteBase().build()).create(RefreshApi::class.java) }
        val cliente = clienteBase()
            .addInterceptor(AuthInterceptor(sesion))
            .authenticator(TokenAuthenticator(sesion) { refreshApi })
            .build()
        return retrofit(cliente).create(ApiService::class.java)
    }
}
