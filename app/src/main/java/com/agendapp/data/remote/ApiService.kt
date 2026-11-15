package com.agendapp.data.remote

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ApiService {

    @POST("auth/login/")
    suspend fun login(@Body datos: LoginRequest): LoginResponse

    @GET("auth/yo/")
    suspend fun yo(): UsuarioDto

    @GET("productos/")
    suspend fun productos(@Query("activos") activos: Int = 1): List<ProductoDto>

    @GET("parametros/")
    suspend fun parametros(): List<ParametroDto>

    @GET("feriados/")
    suspend fun feriados(@Query("desde") desde: String, @Query("hasta") hasta: String): List<FeriadoDto>

    /** El empleado recibe solo las suyas; el encargado, las de todos. */
    @GET("tarifas/")
    suspend fun tarifas(): List<TarifaDto>

    /** El empleado recibe solo los suyos; el encargado, los de todos. */
    @GET("horarios/")
    suspend fun horarios(@Query("desde") desde: String, @Query("hasta") hasta: String): List<HorarioDto>
}

/** Se usa aparte (sin autenticador) para renovar el token de acceso. */
interface RefreshApi {
    @POST("auth/refresh/")
    fun refresh(@Body datos: RefreshRequest): Call<TokensDto>
}
