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
    val codigoBarra: String? = null,
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

// ---------- Movimientos del empleado (Fase 3) ----------
// Se generan en el celular con UUID propio. "pendiente" = todavía no se envió al
// servidor (la sincronización llega en la Fase 4). Fecha "AAAA-MM-DD", hora "HH:MM:SS",
// momentos (registradoEn, anuladoEn) en ISO con zona horaria.

@Entity(tableName = "fichajes", indices = [Index(value = ["empleadoId", "fecha"])])
data class FichajeEntity(
    @PrimaryKey val id: String,
    val empleadoId: Int,
    val fecha: String,
    val hora: String,
    val tipo: String,                 // ENTRADA | SALIDA
    val motivo: String = "",
    val fueraDeTolerancia: Boolean = false,
    val anulado: Boolean = false,
    val anuladoEn: String? = null,
    val origen: String = "EMPLEADO",  // EMPLEADO | ENCARGADO
    val corregido: Boolean = false,
    val registradoEn: String,
    val pendiente: Boolean = true,
)

@Entity(tableName = "retiros", indices = [Index(value = ["empleadoId", "fecha"])])
data class RetiroEntity(
    @PrimaryKey val id: String,
    val empleadoId: Int,
    val fecha: String,
    val hora: String,
    val productoId: String,
    val productoNombre: String,
    val cantidad: Int,
    val precioCosto: BigDecimal,      // congelado al momento del retiro
    val anulado: Boolean = false,
    val anuladoEn: String? = null,
    val origen: String = "EMPLEADO",
    val corregido: Boolean = false,
    val registradoEn: String,
    val pendiente: Boolean = true,
)

/** Montos recibidos (los registra el encargado; llegan con la sincronización). */
@Entity(tableName = "pagos", indices = [Index(value = ["empleadoId", "fecha"])])
data class PagoEntity(
    @PrimaryKey val id: String,
    val empleadoId: Int,
    val fecha: String,
    val hora: String,
    val monto: BigDecimal,
    val tipo: String,                 // LIQUIDACION | ADELANTO
    val descripcion: String,          // "Liquidación del 01/11 al 15/11" / "Adelanto"
    val anulado: Boolean = false,
)
