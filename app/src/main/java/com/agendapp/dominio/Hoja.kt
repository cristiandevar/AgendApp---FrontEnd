package com.agendapp.dominio

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

/** Tipo de cada renglón de evento, en el orden en que se muestran a igual hora. */
enum class TipoRenglon(val etiqueta: String, val orden: Int) {
    ENTRADA("Entrada", 0),
    RETIRO("Retiro", 1),
    SALIDA("Salida", 2),
    RECIBIDO("Recibido", 3),
}

/** Resumen del día (lo que se ve al tocar "Saldo a la fecha"). */
data class ResumenDia(
    val minutos: Int,
    val ganado: BigDecimal,
    val retiros: BigDecimal,
    val recibido: BigDecimal,
    val saldoAnterior: BigDecimal,
    val saldo: BigDecimal,
    val turnoAbierto: Boolean,
)

sealed interface Renglon {
    /** Primer renglón: calculado, no es un evento guardado. */
    data class SaldoAnterior(val monto: BigDecimal) : Renglon

    /** Un evento con sus 4 secciones: hora, tipo, detalle y monto. */
    data class Evento(
        val id: String,
        val hora: LocalTime,
        val tipo: TipoRenglon,
        val detalle: String,
        val monto: BigDecimal?,
        val anulado: Boolean,
        val corregido: Boolean,
        val pendiente: Boolean,
        val fichaje: Fichaje? = null,
        val retiro: Retiro? = null,
        val pago: Pago? = null,
        val horarioEsperado: LocalTime? = null,
    ) : Renglon

    /** Último renglón: calculado, siempre al final de la hoja. */
    data class SaldoALaFecha(val monto: BigDecimal, val resumen: ResumenDia) : Renglon
}

data class Hoja(
    val fecha: LocalDate,
    val esHoy: Boolean,
    val feriado: Feriado?,
    val horarios: List<Horario>,
    /** Solo en la hoja de hoy: entrada del turno en curso, si lo hay. */
    val trabajandoDesde: LocalTime?,
    val renglones: List<Renglon>,
    val resumen: ResumenDia,
)

object ArmadoHoja {

    fun armar(datos: DatosEmpleado, fecha: LocalDate, hoy: LocalDate): Hoja {
        val fichajesDia = datos.fichajes.filter { it.fecha == fecha }
        val retirosDia = datos.retiros.filter { it.fecha == fecha }
        val pagosDia = datos.pagos.filter { it.fecha == fecha }
        val horariosDia = datos.horarios.filter { it.fecha == fecha }.sortedBy { it.inicio }

        val saldoAnterior = Calculos.saldoHasta(datos, fecha.minusDays(1))
        val dia = Calculos.dia(datos, fecha)
        val ganadoSalida = Calculos.ganadoPorSalida(datos, fecha)
        val tramos = Calculos.armarTramos(fichajesDia)

        val eventos = buildList {
            fichajesDia.forEach { f ->
                val tipo = if (f.tipo == TipoFichaje.ENTRADA) TipoRenglon.ENTRADA else TipoRenglon.SALIDA
                val referencia = Reglas.evaluarTolerancia(horariosDia, f.tipo, f.hora, datos.toleranciaMinutos).referencia
                add(
                    Renglon.Evento(
                        id = f.id, hora = f.hora, tipo = tipo, detalle = f.motivo,
                        monto = if (f.anulado) null else ganadoSalida[f.id],
                        anulado = f.anulado, corregido = f.corregido || f.delEncargado, pendiente = f.pendiente,
                        fichaje = f, horarioEsperado = referencia,
                    )
                )
            }
            retirosDia.forEach { r ->
                add(
                    Renglon.Evento(
                        id = r.id, hora = r.hora, tipo = TipoRenglon.RETIRO,
                        detalle = "${r.cantidad} × ${r.productoNombre}",
                        monto = r.subtotal.negate(),
                        anulado = r.anulado, corregido = r.corregido || r.delEncargado, pendiente = r.pendiente,
                        retiro = r,
                    )
                )
            }
            pagosDia.forEach { p ->
                add(
                    Renglon.Evento(
                        id = p.id, hora = p.hora, tipo = TipoRenglon.RECIBIDO, detalle = p.descripcion,
                        monto = p.monto.negate(), anulado = p.anulado, corregido = false, pendiente = false, pago = p,
                    )
                )
            }
        }.sortedWith(compareBy<Renglon.Evento>({ it.hora }, { it.tipo.orden }))

        val saldo = saldoAnterior.add(dia.ganado).subtract(dia.retiros).subtract(dia.pagos)
        val resumen = ResumenDia(
            minutos = dia.minutos,
            ganado = dia.ganado,
            retiros = dia.retiros,
            recibido = dia.pagos,
            saldoAnterior = saldoAnterior,
            saldo = saldo,
            turnoAbierto = dia.turnoAbierto,
        )
        return Hoja(
            fecha = fecha,
            esHoy = fecha == hoy,
            feriado = Calculos.feriadoDe(datos.feriados, fecha),
            horarios = horariosDia,
            trabajandoDesde = if (fecha == hoy) tramos.abiertoDesde?.hora else null,
            renglones = listOf<Renglon>(Renglon.SaldoAnterior(saldoAnterior)) + eventos + Renglon.SaldoALaFecha(saldo, resumen),
            resumen = resumen,
        )
    }
}
