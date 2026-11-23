package com.agendapp.dominio

import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
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

    private val HH_MM = DateTimeFormatter.ofPattern("HH:mm")

    /**
     * Igual que validar_secuencia: ordenados por hora, los fichajes vigentes deben alternar
     * entrada, salida, entrada... empezando por entrada y sin horas repetidas.
     * Devuelve el mensaje de error, o null si la secuencia es válida.
     */
    fun errorDeSecuencia(marcas: List<Pair<LocalTime, TipoFichaje>>): String? {
        val ordenadas = marcas.sortedBy { it.first }
        ordenadas.forEachIndexed { i, (hora, tipo) ->
            val esperado = if (i % 2 == 0) TipoFichaje.ENTRADA else TipoFichaje.SALIDA
            if (tipo != esperado) {
                val texto = if (esperado == TipoFichaje.ENTRADA) "entrada" else "salida"
                return "Secuencia inválida a las ${hora.format(HH_MM)}: se esperaba una $texto."
            }
            if (i > 0 && minutos(hora) == minutos(ordenadas[i - 1].first)) {
                return "Hay dos fichajes a la misma hora (${hora.format(HH_MM)})."
            }
        }
        return null
    }

    /** Resultado de controlar un fichaje nuevo del empleado al tocar Aceptar. */
    sealed interface ControlFichaje {
        /** Se puede registrar tal cual. */
        data class Valido(
            val tipo: TipoFichaje,
            val hora: LocalTime,
            val fueraDeTolerancia: Boolean,
            val referencia: LocalTime?,
            val motivo: String,
        ) : ControlFichaje

        /** La hora quedó fuera de la tolerancia (o no hay horario) y falta el motivo. */
        data class FaltaMotivo(val referencia: LocalTime?) : ControlFichaje

        /** No se puede registrar (secuencia u hora del celular). */
        data class Rechazado(val mensaje: String) : ControlFichaje
    }

    /**
     * Controles de registrar_fichaje aplicados en el celular: el tipo sale del último
     * fichaje vigente, la hora se registra al minuto y debe ser posterior al último fichaje
     * (el servidor rechaza dos fichajes a la misma hora), y fuera de tolerancia el motivo es
     * obligatorio.
     */
    fun controlarFichaje(
        fichajesDelDia: List<Fichaje>,
        horariosDelDia: List<Horario>,
        toleranciaMinutos: Int,
        ahora: LocalTime,
        motivo: String,
    ): ControlFichaje {
        val hora = ahora.truncatedTo(ChronoUnit.MINUTES)
        val ultimo = ultimoFichajeVigente(fichajesDelDia)
        if (ultimo != null) {
            val ultimaHora = ultimo.hora.format(HH_MM)
            if (minutos(hora) == minutos(ultimo.hora)) {
                return ControlFichaje.Rechazado("Ya fichaste a las $ultimaHora. Esperá a que pase el minuto para volver a fichar.")
            }
            if (hora.isBefore(ultimo.hora)) {
                return ControlFichaje.Rechazado(
                    "La hora del celular (${hora.format(HH_MM)}) es anterior a tu último fichaje ($ultimaHora). " +
                        "Revisá la fecha y hora del teléfono."
                )
            }
        }
        val tipo = siguienteTipo(fichajesDelDia)
        val vigentes = fichajesDelDia.filter { !it.anulado }.map { it.hora to it.tipo }
        errorDeSecuencia(vigentes + (hora to tipo))?.let { return ControlFichaje.Rechazado(it) }

        val tolerancia = evaluarTolerancia(horariosDelDia, tipo, hora, toleranciaMinutos)
        val motivoLimpio = motivo.trim()
        if (tolerancia.fuera && motivoLimpio.isEmpty()) return ControlFichaje.FaltaMotivo(tolerancia.referencia)
        return ControlFichaje.Valido(tipo, hora, tolerancia.fuera, tolerancia.referencia, motivoLimpio)
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

    /** Segundos que faltan para que venza el plazo (0 si ya venció; null sin momento de registro). */
    fun segundosRestantes(registradoEn: OffsetDateTime?, ahora: OffsetDateTime, plazoMinutos: Int): Long? {
        val limite = limiteDelPlazo(registradoEn, plazoMinutos) ?: return null
        return maxOf(Duration.between(ahora, limite).seconds, 0L)
    }

    /** Cuenta regresiva "m:ss" (125 → "2:05"). */
    fun formatoCuentaRegresiva(segundos: Long): String = "%d:%02d".format(segundos / 60, segundos % 60)

    /**
     * Un fichaje solo se puede anular si es el último vigente del día: anular uno anterior
     * dejaría la secuencia entrada/salida rota (por ejemplo, una salida sin entrada).
     */
    fun puedeAnularFichaje(f: Fichaje, fichajesDelDia: List<Fichaje>, ahora: OffsetDateTime, plazoMinutos: Int): Boolean =
        impedimentoParaAnular(f, fichajesDelDia, ahora, plazoMinutos) == null

    /** Por qué el empleado no puede anular el fichaje, o null si puede. */
    fun impedimentoParaAnular(f: Fichaje, fichajesDelDia: List<Fichaje>, ahora: OffsetDateTime, plazoMinutos: Int): String? =
        when {
            f.anulado -> "El fichaje ya está anulado."
            f.delEncargado -> "Lo cargó el encargado: solo él puede corregirlo."
            !dentroDelPlazo(f.registradoEn, f.anulado, ahora, plazoMinutos) ->
                "Pasó el plazo de $plazoMinutos minutos para anularlo."
            ultimoFichajeVigente(fichajesDelDia)?.id != f.id -> "Solo se puede anular el último fichaje del día."
            else -> null
        }

    fun puedeCambiarRetiro(r: Retiro, ahora: OffsetDateTime, plazoMinutos: Int): Boolean =
        impedimentoParaCambiarRetiro(r, ahora, plazoMinutos) == null

    /** Por qué el empleado no puede modificar ni anular el retiro, o null si puede. */
    fun impedimentoParaCambiarRetiro(r: Retiro, ahora: OffsetDateTime, plazoMinutos: Int): String? = when {
        r.anulado -> "El retiro ya está anulado."
        r.delEncargado -> "Lo cargó el encargado: solo él puede corregirlo."
        !dentroDelPlazo(r.registradoEn, r.anulado, ahora, plazoMinutos) ->
            "Pasó el plazo de $plazoMinutos minutos para cambiarlo. Pedile al encargado que lo corrija."
        else -> null
    }

    /** Unidades vigentes del producto ya retiradas en la fecha (sin contar [excluirRetiroId]). */
    fun yaRetirado(productoId: String, retiros: List<Retiro>, fecha: LocalDate, excluirRetiroId: String? = null): Int =
        retiros
            .filter { it.fecha == fecha && it.productoId == productoId && !it.anulado && it.id != excluirRetiroId }
            .sumOf { it.cantidad }

    /** Resultado de controlar un retiro nuevo o modificado al tocar Aceptar. */
    sealed interface ControlRetiro {
        /** Se puede guardar: [precioCosto] es el que queda congelado en el retiro. */
        data class Valido(val producto: ProductoDisponible, val cantidad: Int, val precioCosto: BigDecimal) : ControlRetiro

        /** Al modificar, no cambió ni el producto ni la cantidad. */
        data object SinCambios : ControlRetiro

        data class Rechazado(val mensaje: String) : ControlRetiro
    }

    /**
     * Controles de registrar_retiro y modificar_retiro_empleado aplicados en el celular:
     * producto activo (al modificar se puede dejar el mismo aunque ya no esté activo),
     * cantidad de al menos 1 y máximo diario por empleado. El precio queda congelado:
     * al modificar se conserva el original si no cambia el producto, y si cambia se toma
     * el precio actual del producto nuevo.
     */
    fun controlarRetiro(
        productoId: String?,
        cantidad: Int,
        productosActivos: List<ProductoDisponible>,
        retiros: List<Retiro>,
        fecha: LocalDate,
        original: Retiro? = null,
    ): ControlRetiro {
        if (productoId == null) return ControlRetiro.Rechazado("Elegí un producto.")
        val mismoProducto = original != null && original.productoId == productoId
        val producto = productosActivos.firstOrNull { it.id == productoId }
            ?: if (mismoProducto) ProductoDisponible(original!!.productoId, original.productoNombre, original.precioCosto, maxDiario = 0)
            else return ControlRetiro.Rechazado("El producto ya no está disponible.")
        if (cantidad < 1) return ControlRetiro.Rechazado("La cantidad tiene que ser al menos 1.")
        if (mismoProducto && cantidad == original!!.cantidad) return ControlRetiro.SinCambios

        if (producto.maxDiario > 0) {
            val ya = yaRetirado(producto.id, retiros, fecha, original?.id)
            if (ya + cantidad > producto.maxDiario) {
                val disponible = maxOf(producto.maxDiario - ya, 0)
                return ControlRetiro.Rechazado(
                    "Supera el máximo diario de ${producto.nombre} (${producto.maxDiario}). " +
                        "Ya retiraste $ya hoy; podés retirar hasta $disponible más."
                )
            }
        }
        val precio = if (mismoProducto) original!!.precioCosto else producto.precioCosto
        return ControlRetiro.Valido(producto, cantidad, precio)
    }

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
        return maxOf(producto.maxDiario - yaRetirado(producto.id, retiros, fecha, excluirRetiroId), 0)
    }
}
