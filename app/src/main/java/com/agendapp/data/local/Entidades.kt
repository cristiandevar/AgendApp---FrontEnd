package com.agendapp.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal

// Copia local del catálogo y la configuración, para usar la app sin conexión.
// Fechas en formato ISO "AAAA-MM-DD" y horas "HH:MM:SS", tal como las manda la API.

@Entity(tableName = "productos")
data class ProductoEntity(
    @PrimaryKey val id: String,
    val nombre: String,
    val precioCosto: BigDecimal,
    val maxDiario: Int,
    val activo: Boolean,
    val actualizado: String,
)

@Entity(tableName = "parametros")
data class ParametroEntity(
    @PrimaryKey val clave: String,
    val descripcion: String,
    val valor: BigDecimal,
    val actualizado: String,
)

@Entity(tableName = "feriados", indices = [Index(value = ["fecha"], unique = true)])
data class FeriadoEntity(
    @PrimaryKey val id: String,
    val fecha: String,
    val descripcion: String,
    val multiplicador: BigDecimal,
)

@Entity(tableName = "tarifas", indices = [Index(value = ["empleadoId", "vigenteDesde"])])
data class TarifaEntity(
    @PrimaryKey val id: String,
    val empleadoId: Int,
    val empleadoNombre: String,
    val monto: BigDecimal,
    val vigenteDesde: String,
)

@Entity(tableName = "horarios", indices = [Index(value = ["empleadoId", "fecha"])])
data class HorarioEntity(
    @PrimaryKey val id: String,
    val empleadoId: Int,
    val empleadoNombre: String,
    val fecha: String,
    val horaInicio: String,
    val horaFin: String,
)
