package com.agendapp.dominio

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

// Modelo del dominio en Kotlin puro (sin Android), para poder testearlo igual que el backend.

enum class TipoFichaje { ENTRADA, SALIDA }

data class Fichaje(
    val id: String,
    val fecha: LocalDate,
    val hora: LocalTime,
    val tipo: TipoFichaje,
    val motivo: String = "",
    val fueraDeTolerancia: Boolean = false,
    val anulado: Boolean = false,
    val corregido: Boolean = false,
    val origen: String = ORIGEN_EMPLEADO,
    val registradoEn: OffsetDateTime? = null,
    val pendiente: Boolean = false,
) {
    val delEncargado get() = origen == ORIGEN_ENCARGADO
}

data class Retiro(
    val id: String,
    val fecha: LocalDate,
    val hora: LocalTime,
    val productoId: String,
    val productoNombre: String,
    val cantidad: Int,
    val precioCosto: BigDecimal,
    val anulado: Boolean = false,
    val corregido: Boolean = false,
    val origen: String = ORIGEN_EMPLEADO,
    val registradoEn: OffsetDateTime? = null,
    val pendiente: Boolean = false,
) {
    val subtotal: BigDecimal get() = precioCosto.multiply(BigDecimal(cantidad))
    val delEncargado get() = origen == ORIGEN_ENCARGADO
}

data class Pago(
    val id: String,
    val fecha: LocalDate,
    val hora: LocalTime,
    val monto: BigDecimal,
    val descripcion: String,
    val anulado: Boolean = false,
)

data class Tarifa(val vigenteDesde: LocalDate, val monto: BigDecimal)

data class Feriado(val fecha: LocalDate, val descripcion: String, val multiplicador: BigDecimal)

data class Horario(val fecha: LocalDate, val inicio: LocalTime, val fin: LocalTime)

data class ProductoDisponible(
    val id: String,
    val nombre: String,
    val precioCosto: BigDecimal,
    val maxDiario: Int,
    val codigoBarra: String? = null,
)

/** Todo lo que el celular sabe de un empleado, para armar sus hojas. */
data class DatosEmpleado(
    val fichajes: List<Fichaje> = emptyList(),
    val retiros: List<Retiro> = emptyList(),
    val pagos: List<Pago> = emptyList(),
    val tarifas: List<Tarifa> = emptyList(),
    val feriados: List<Feriado> = emptyList(),
    val horarios: List<Horario> = emptyList(),
    val productos: List<ProductoDisponible> = emptyList(),
    val toleranciaMinutos: Int = 15,
    val plazoMinutos: Int = 10,
)

const val ORIGEN_EMPLEADO = "EMPLEADO"
const val ORIGEN_ENCARGADO = "ENCARGADO"
