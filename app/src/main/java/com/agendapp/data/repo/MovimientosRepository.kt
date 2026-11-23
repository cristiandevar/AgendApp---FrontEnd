package com.agendapp.data.repo

import com.agendapp.data.local.AppDatabase
import com.agendapp.data.local.FeriadoEntity
import com.agendapp.data.local.FichajeEntity
import com.agendapp.data.local.HorarioEntity
import com.agendapp.data.local.PagoEntity
import com.agendapp.data.local.ParametroEntity
import com.agendapp.data.local.ProductoEntity
import com.agendapp.data.local.RetiroEntity
import com.agendapp.data.local.TarifaEntity
import com.agendapp.dominio.DatosEmpleado
import com.agendapp.dominio.Feriado
import com.agendapp.dominio.Fichaje
import com.agendapp.dominio.Horario
import com.agendapp.dominio.ORIGEN_ENCARGADO
import com.agendapp.dominio.Pago
import com.agendapp.dominio.ProductoDisponible
import com.agendapp.dominio.Reglas
import com.agendapp.dominio.Retiro
import com.agendapp.dominio.Tarifa
import com.agendapp.dominio.TipoFichaje
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Movimientos del empleado guardados en el celular y armado de sus datos para la libreta. */
class MovimientosRepository(private val db: AppDatabase) {

    private data class Movimientos(val fichajes: List<Fichaje>, val retiros: List<Retiro>, val pagos: List<Pago>)

    /** Todo lo que necesita la libreta de un empleado, actualizado ante cualquier cambio local. */
    fun datosEmpleado(empleadoId: Int): Flow<DatosEmpleado> {
        val movimientos = combine(
            db.fichajeDao().observarDe(empleadoId),
            db.retiroDao().observarDe(empleadoId),
            db.pagoDao().observarDe(empleadoId),
        ) { f, r, p -> Movimientos(f.map { it.aDominio() }, r.map { it.aDominio() }, p.map { it.aDominio() }) }

        return combine(
            movimientos,
            db.tarifaDao().observarTodas(),
            db.feriadoDao().observarTodos(),
            db.horarioDao().observarTodos(),
            combine(db.productoDao().observarActivos(), db.parametroDao().observarTodos()) { p, par -> p to par },
        ) { m, tarifas, feriados, horarios, (productos, parametros) ->
            DatosEmpleado(
                fichajes = m.fichajes,
                retiros = m.retiros,
                pagos = m.pagos,
                tarifas = tarifas.filter { it.empleadoId == empleadoId }.map { it.aDominio() },
                feriados = feriados.map { it.aDominio() },
                horarios = horarios.filter { it.empleadoId == empleadoId }.map { it.aDominio() },
                productos = productos.map { it.aDominio() },
                toleranciaMinutos = parametros.entero("tolerancia_minutos", 15),
                plazoMinutos = parametros.entero("anulacion_fichaje_minutos", 10),
            )
        }
    }

    suspend fun pendientes(empleadoId: Int): Int =
        db.fichajeDao().contarPendientes(empleadoId) + db.retiroDao().contarPendientes(empleadoId)

    // ---------- Fichajes del empleado ----------

    /** Fichajes del día leídos directo de la base (no del flujo), para controlar al tocar Aceptar. */
    suspend fun fichajesDelDia(empleadoId: Int, fecha: LocalDate): List<Fichaje> =
        db.fichajeDao().delDia(empleadoId, fecha.toString()).map { it.aDominio() }

    suspend fun fichaje(id: String): Fichaje? = db.fichajeDao().porId(id)?.aDominio()

    /** Guarda un fichaje ya controlado. Queda pendiente de sincronizar. */
    suspend fun registrarFichaje(
        empleadoId: Int,
        fecha: LocalDate,
        valido: Reglas.ControlFichaje.Valido,
        registradoEn: OffsetDateTime,
    ): Fichaje {
        val entidad = FichajeEntity(
            id = UUID.randomUUID().toString(),
            empleadoId = empleadoId,
            fecha = fecha.toString(),
            hora = valido.hora.format(HORA_ISO),
            tipo = valido.tipo.name,
            motivo = valido.motivo,
            fueraDeTolerancia = valido.fueraDeTolerancia,
            registradoEn = registradoEn.toString(),
            pendiente = true,
        )
        db.fichajeDao().guardar(entidad)
        return entidad.aDominio()
    }

    /** Anula un fichaje (nunca se borra): queda tachado y pendiente de sincronizar. */
    suspend fun anularFichaje(id: String, ahora: OffsetDateTime) {
        val actual = db.fichajeDao().porId(id) ?: return
        db.fichajeDao().guardar(actual.copy(anulado = true, anuladoEn = ahora.toString(), pendiente = true))
    }

    // ---------- Retiros del empleado ----------

    suspend fun retirosDelDia(empleadoId: Int, fecha: LocalDate): List<Retiro> =
        db.retiroDao().delDia(empleadoId, fecha.toString()).map { it.aDominio() }

    suspend fun retiro(id: String): Retiro? = db.retiroDao().porId(id)?.aDominio()

    /** Producto activo con ese código de barras o QR (el que lee el escáner). */
    suspend fun productoPorCodigo(codigo: String): ProductoDisponible? =
        db.productoDao().porCodigo(codigo.trim())?.aDominio()

    /** Guarda un retiro ya controlado, con el precio congelado. Queda pendiente de sincronizar. */
    suspend fun registrarRetiro(
        empleadoId: Int,
        fecha: LocalDate,
        hora: LocalTime,
        valido: Reglas.ControlRetiro.Valido,
        registradoEn: OffsetDateTime,
    ): Retiro {
        val entidad = RetiroEntity(
            id = UUID.randomUUID().toString(),
            empleadoId = empleadoId,
            fecha = fecha.toString(),
            hora = hora.format(HORA_ISO),
            productoId = valido.producto.id,
            productoNombre = valido.producto.nombre,
            cantidad = valido.cantidad,
            precioCosto = valido.precioCosto,
            registradoEn = registradoEn.toString(),
            pendiente = true,
        )
        db.retiroDao().guardar(entidad)
        return entidad.aDominio()
    }

    /**
     * Cambia producto y/o cantidad de un retiro (mismo id, misma fecha, hora y momento de
     * registro: el plazo se sigue contando desde el alta). Queda pendiente de sincronizar.
     */
    suspend fun modificarRetiro(id: String, valido: Reglas.ControlRetiro.Valido) {
        val actual = db.retiroDao().porId(id) ?: return
        db.retiroDao().guardar(
            actual.copy(
                productoId = valido.producto.id,
                productoNombre = valido.producto.nombre,
                cantidad = valido.cantidad,
                precioCosto = valido.precioCosto,
                pendiente = true,
            )
        )
    }

    /** Anula un retiro (nunca se borra): queda tachado y pendiente de sincronizar. */
    suspend fun anularRetiro(id: String, ahora: OffsetDateTime) {
        val actual = db.retiroDao().porId(id) ?: return
        db.retiroDao().guardar(actual.copy(anulado = true, anuladoEn = ahora.toString(), pendiente = true))
    }

    // ---------- Datos de ejemplo (solo para probar en debug) ----------
    // Usan ids "demo-..." y no quedan pendientes: la sincronización (Fase 4) no los envía.

    suspend fun cargarEjemplo(empleadoId: Int, hoy: LocalDate = LocalDate.now()) {
        borrarEjemplo()
        val productos = db.productoDao().todosActivos()
        val p1 = productos.getOrNull(0)
        val p2 = productos.getOrNull(1) ?: p1
        val d3 = hoy.minusDays(3)
        val d2 = hoy.minusDays(2)
        val d1 = hoy.minusDays(1)
        var n = 0
        fun momento(fecha: LocalDate, hora: String) = OffsetDateTime.of(fecha, LocalTime.parse(hora), OffsetDateTime.now().offset).toString()
        fun fichaje(fecha: LocalDate, hora: String, tipo: String, motivo: String = "", fuera: Boolean = false,
                    encargado: Boolean = false) = FichajeEntity(
            id = "demo-f${n++}", empleadoId = empleadoId, fecha = fecha.toString(), hora = "$hora:00", tipo = tipo,
            motivo = motivo, fueraDeTolerancia = fuera, origen = if (encargado) ORIGEN_ENCARGADO else "EMPLEADO",
            corregido = encargado, registradoEn = momento(fecha, hora), pendiente = false,
        )
        db.fichajeDao().guardarTodos(
            listOf(
                fichaje(d3, "08:05", "ENTRADA"), fichaje(d3, "12:00", "SALIDA"),
                fichaje(d3, "16:00", "ENTRADA"), fichaje(d3, "20:10", "SALIDA"),
                fichaje(d2, "08:40", "ENTRADA", "Se cortó el colectivo", fuera = true), fichaje(d2, "12:00", "SALIDA"),
                fichaje(d1, "08:00", "ENTRADA", encargado = true),
                fichaje(d1, "12:30", "SALIDA", "Me pidieron quedarme a recibir mercadería", fuera = true),
            )
        )
        if (p1 != null && p2 != null) {
            fun retiro(fecha: LocalDate, hora: String, p: ProductoEntity, cantidad: Int, anulado: Boolean = false) =
                RetiroEntity(
                    id = "demo-r${n++}", empleadoId = empleadoId, fecha = fecha.toString(), hora = "$hora:00",
                    productoId = p.id, productoNombre = p.nombre, cantidad = cantidad, precioCosto = p.precioCosto,
                    anulado = anulado, registradoEn = momento(fecha, hora), pendiente = false,
                )
            db.retiroDao().guardarTodos(
                listOf(
                    retiro(d3, "10:15", p1, 2),
                    retiro(d2, "11:00", p2, 1),
                    retiro(d2, "11:05", p2, 1, anulado = true),
                    retiro(d1, "09:30", p1, 1),
                )
            )
        }
        db.pagoDao().guardarTodos(
            listOf(
                PagoEntity("demo-p${n++}", empleadoId, d1.toString(), "20:05:00", BigDecimal("5000"), "ADELANTO", "Adelanto"),
            )
        )
    }

    suspend fun borrarEjemplo() {
        db.fichajeDao().borrarEjemplo()
        db.retiroDao().borrarEjemplo()
        db.pagoDao().borrarEjemplo()
    }
}

// ---------- Conversión de Room al dominio ----------

/** Horas guardadas como "HH:mm:ss", igual que las devuelve la API. */
private val HORA_ISO = DateTimeFormatter.ofPattern("HH:mm:ss")

private fun List<ParametroEntity>.entero(clave: String, porDefecto: Int) =
    firstOrNull { it.clave == clave }?.valor?.toInt() ?: porDefecto

private fun momento(iso: String?): OffsetDateTime? = iso?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }

fun FichajeEntity.aDominio() = Fichaje(
    id = id, fecha = LocalDate.parse(fecha), hora = LocalTime.parse(hora),
    tipo = if (tipo == "SALIDA") TipoFichaje.SALIDA else TipoFichaje.ENTRADA,
    motivo = motivo, fueraDeTolerancia = fueraDeTolerancia, anulado = anulado, corregido = corregido,
    origen = origen, registradoEn = momento(registradoEn), pendiente = pendiente,
)

fun RetiroEntity.aDominio() = Retiro(
    id = id, fecha = LocalDate.parse(fecha), hora = LocalTime.parse(hora), productoId = productoId,
    productoNombre = productoNombre, cantidad = cantidad, precioCosto = precioCosto, anulado = anulado,
    corregido = corregido, origen = origen, registradoEn = momento(registradoEn), pendiente = pendiente,
)

fun PagoEntity.aDominio() = Pago(
    id = id, fecha = LocalDate.parse(fecha), hora = LocalTime.parse(hora), monto = monto,
    descripcion = descripcion, anulado = anulado,
)

fun TarifaEntity.aDominio() = Tarifa(LocalDate.parse(vigenteDesde), monto)

fun FeriadoEntity.aDominio() = Feriado(LocalDate.parse(fecha), descripcion, multiplicador)

fun HorarioEntity.aDominio() = Horario(LocalDate.parse(fecha), LocalTime.parse(horaInicio), LocalTime.parse(horaFin))

fun ProductoEntity.aDominio() = ProductoDisponible(id, nombre, precioCosto, maxDiario, codigoBarra)
