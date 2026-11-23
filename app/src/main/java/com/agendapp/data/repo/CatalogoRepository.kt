package com.agendapp.data.repo

import androidx.room.withTransaction
import com.agendapp.data.local.AppDatabase
import com.agendapp.data.local.FeriadoEntity
import com.agendapp.data.local.HorarioEntity
import com.agendapp.data.local.ParametroEntity
import com.agendapp.data.local.ProductoEntity
import com.agendapp.data.local.TarifaEntity
import com.agendapp.data.remote.ApiService
import com.agendapp.data.remote.Resultado
import com.agendapp.data.remote.llamarApi
import com.agendapp.data.session.SessionStore
import com.agendapp.data.session.Usuario
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * Descarga del servidor el catálogo y la configuración que la app necesita para
 * funcionar sin conexión, y los guarda en Room reemplazando lo anterior.
 */
class CatalogoRepository(
    private val api: ApiService,
    private val db: AppDatabase,
    private val sesion: SessionStore,
) {
    val productos = db.productoDao().observarActivos()
    val parametros = db.parametroDao().observarTodos()
    val feriados = db.feriadoDao().observarTodos()
    val tarifas = db.tarifaDao().observarTodas()
    val horarios = db.horarioDao().observarTodos()
    val ultimaDescarga = sesion.ultimaDescarga

    suspend fun descargar(usuario: Usuario, hoy: LocalDate = LocalDate.now()): Resultado<Unit> {
        // Feriados desde el alta del empleado (para calcular días pasados) hasta un año adelante.
        val desdeFeriados = usuario.fechaAlta?.let { LocalDate.parse(it) }?.let { if (it.isAfter(hoy)) hoy else it } ?: hoy.minusYears(1)
        val hastaFeriados = hoy.plusYears(1)
        // Horarios asignados: la semana pasada y los próximos dos meses.
        val desdeHorarios = hoy.minusDays(7)
        val hastaHorarios = hoy.plusDays(60)

        val resultado = llamarApi {
            Descarga(
                productos = api.productos(),
                parametros = api.parametros(),
                feriados = api.feriados(desdeFeriados.toString(), hastaFeriados.toString()),
                tarifas = api.tarifas(),
                horarios = api.horarios(desdeHorarios.toString(), hastaHorarios.toString()),
            )
        }
        if (resultado is Resultado.Error) return resultado
        val d = (resultado as Resultado.Exito).valor

        db.withTransaction {
            db.productoDao().borrarTodo()
            db.productoDao().insertar(d.productos.map {
                ProductoEntity(it.id, it.nombre, it.precioCosto, it.maxDiario, it.activo, it.actualizado, it.codigoBarra)
            })
            db.parametroDao().borrarTodo()
            db.parametroDao().insertar(d.parametros.map {
                ParametroEntity(it.clave, it.descripcion, it.valor, it.actualizado)
            })
            db.feriadoDao().borrarTodo()
            db.feriadoDao().insertar(d.feriados.map {
                FeriadoEntity(it.id, it.fecha, it.descripcion, it.multiplicador)
            })
            db.tarifaDao().borrarTodo()
            db.tarifaDao().insertar(d.tarifas.map {
                TarifaEntity(it.id, it.empleado, it.empleadoNombre, it.monto, it.vigenteDesde)
            })
            db.horarioDao().borrarTodo()
            db.horarioDao().insertar(d.horarios.map {
                HorarioEntity(it.id, it.empleado, it.empleadoNombre, it.fecha, it.horaInicio, it.horaFin)
            })
        }
        sesion.marcarDescarga(OffsetDateTime.now().toString())
        return Resultado.Exito(Unit)
    }

    private data class Descarga(
        val productos: List<com.agendapp.data.remote.ProductoDto>,
        val parametros: List<com.agendapp.data.remote.ParametroDto>,
        val feriados: List<com.agendapp.data.remote.FeriadoDto>,
        val tarifas: List<com.agendapp.data.remote.TarifaDto>,
        val horarios: List<com.agendapp.data.remote.HorarioDto>,
    )
}
