package com.agendapp.dominio

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import kotlin.math.abs

/** Réplica de las reglas de agenda/reglas.py que el celular necesita para funcionar sin conexión. */
object Reglas {

    private fun minutos(hora: LocalTime) = hora.hour * 60 + hora.minute

    /** Último fichaje vigente del día (por hora), o null si no hay. */
    fun ultimoFichajeVigente(fichajesDelDia: List<Fichaje>): Fichaje? =
        fichajesDelDia.filter { !it.anulado }.maxByOrNull { it.hora }

    /** Si el último fichaje vigente es una entrada, toca salida; si no, entrada. */
    fun siguienteTipo(fichajesDelDia: List<Fichaje>): TipoFichaje =
        if (ultimoFichajeVigente(fichajesDelDia)?.tipo == TipoFichaje.ENTRADA) TipoFichaje.SALIDA
        else TipoFichaje.ENTRADA

    data class Tolerancia(val fuera: Boolean, val referencia: LocalTime?)

    /**
     * Igual que evaluar_tolerancia: la entrada se compara con el inicio de horario más
     * cercano y la salida con el fin más cercano. Sin horario asignado → fuera.
     */
    fun evaluarTolerancia(
        horariosDelDia: List<Horario>,
        tipo: TipoFichaje,
        hora: LocalTime,
        toleranciaMinutos: Int,
    ): Tolerancia {
        val referencias = horariosDelDia.map { if (tipo == TipoFichaje.ENTRADA) it.inicio else it.fin }
        if (referencias.isEmpty()) return Tolerancia(true, null)
        val referencia = referencias.minBy { abs(minutos(it) - minutos(hora)) }
        return Tolerancia(abs(minutos(referencia) - minutos(hora)) > toleranciaMinutos, referencia)
    }

    /** Igual que dentro_del_plazo: el empleado puede cambiar su registro solo dentro del plazo. */
    fun dentroDelPlazo(registradoEn: OffsetDateTime?, anulado: Boolean, ahora: OffsetDateTime, plazoMinutos: Int): Boolean {
        if (anulado || registradoEn == null) return false
        return Duration.between(registradoEn, ahora).toMillis() <= plazoMinutos * 60_000L
    }

    /**
     * Momento hasta el que el empleado puede cambiar el registro, o null si ya no puede.
     */
    fun limiteDelPlazo(registradoEn: OffsetDateTime?, plazoMinutos: Int): OffsetDateTime? =
        registradoEn?.plusMinutes(plazoMinutos.toLong())

    /**
     * Un fichaje solo se puede anular si es el último vigente del día: anular uno anterior
     * dejaría la secuencia entrada/salida rota (por ejemplo, una salida sin entrada).
     */
    fun puedeAnularFichaje(f: Fichaje, fichajesDelDia: List<Fichaje>, ahora: OffsetDateTime, plazoMinutos: Int): Boolean =
        !f.delEncargado &&
            dentroDelPlazo(f.registradoEn, f.anulado, ahora, plazoMinutos) &&
            ultimoFichajeVigente(fichajesDelDia)?.id == f.id

    fun puedeCambiarRetiro(r: Retiro, ahora: OffsetDateTime, plazoMinutos: Int): Boolean =
        !r.delEncargado && dentroDelPlazo(r.registradoEn, r.anulado, ahora, plazoMinutos)

    /**
     * Unidades que el empleado todavía puede retirar hoy de un producto
     * (null = sin límite). [excluirRetiroId] sirve al modificar un retiro existente.
     */
    fun restanteDiario(
        producto: ProductoDisponible,
        retiros: List<Retiro>,
        fecha: LocalDate,
        excluirRetiroId: String? = null,
    ): Int? {
        if (producto.maxDiario <= 0) return null
        val yaRetirado = retiros
            .filter { it.fecha == fecha && it.productoId == producto.id && !it.anulado && it.id != excluirRetiroId }
            .sumOf { it.cantidad }
        return maxOf(producto.maxDiario - yaRetirado, 0)
    }
}
