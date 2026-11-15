package com.agendapp.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal

// Modelos tal cual los devuelve la API de AgendApp - BackEnd (ver README del backend).

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class LoginResponse(
    val access: String,
    val refresh: String,
    val usuario: UsuarioDto,
)

@Serializable
data class RefreshRequest(val refresh: String)

@Serializable
data class TokensDto(val access: String, val refresh: String? = null)

@Serializable
data class UsuarioDto(
    val id: Int,
    val username: String,
    @SerialName("first_name") val nombre: String = "",
    @SerialName("last_name") val apellido: String = "",
    @SerialName("nombre_completo") val nombreCompleto: String,
    val rol: String,
    @SerialName("modalidad_liquidacion") val modalidadLiquidacion: String = "DIARIA",
    @SerialName("fecha_alta") val fechaAlta: String? = null,
    @SerialName("is_active") val activo: Boolean = true,
)

@Serializable
data class ProductoDto(
    val id: String,
    val nombre: String,
    @SerialName("precio_costo") @Serializable(with = BigDecimalSerializer::class) val precioCosto: BigDecimal,
    @SerialName("max_diario") val maxDiario: Int,
    val activo: Boolean,
    val actualizado: String,
)

@Serializable
data class ParametroDto(
    val clave: String,
    val descripcion: String,
    @Serializable(with = BigDecimalSerializer::class) val valor: BigDecimal,
    val actualizado: String,
)

@Serializable
data class FeriadoDto(
    val id: String,
    val fecha: String,
    val descripcion: String,
    @Serializable(with = BigDecimalSerializer::class) val multiplicador: BigDecimal,
    val actualizado: String,
)

@Serializable
data class TarifaDto(
    val id: String,
    val empleado: Int,
    @SerialName("empleado_nombre") val empleadoNombre: String,
    @Serializable(with = BigDecimalSerializer::class) val monto: BigDecimal,
    @SerialName("vigente_desde") val vigenteDesde: String,
    val actualizado: String,
)

@Serializable
data class HorarioDto(
    val id: String,
    val empleado: Int,
    @SerialName("empleado_nombre") val empleadoNombre: String,
    val fecha: String,
    @SerialName("hora_inicio") val horaInicio: String,
    @SerialName("hora_fin") val horaFin: String,
    val actualizado: String,
)
