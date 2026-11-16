package com.agendapp.dominio

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalTime

/**
 * Réplica de agenda/calculos.py del backend.
 *
 *   Saldo a la fecha = Saldo anterior + Ganado - Retiros - Monto recibido
 *
 * Los redondeos imitan a Python Decimal: operaciones con 28 dígitos (HALF_EVEN) y
 * resultado final a centavos con HALF_UP, para que el celular y el servidor den
 * exactamente lo mismo.
 */
object Calculos {

    private val PYTHON = MathContext(28, RoundingMode.HALF_EVEN)
    private val SESENTA = BigDecimal(60)
    val CERO: BigDecimal = BigDecimal("0.00")

    /** Un tramo trabajado: entrada y salida vigentes, de a pares. */
    data class Tramo(val entrada: Fichaje, val salida: Fichaje) {
        val minutos: Int get() = maxOf(aMinutos(salida.hora) - aMinutos(entrada.hora), 0)
    }

    data class Tramos(val tramos: List<Tramo>, val abiertoDesde: Fichaje?) {
        val turnoAbierto get() = abiertoDesde != null
        val minutos get() = tramos.sumOf { it.minutos }
    }

    fun aMinutos(hora: LocalTime) = hora.hour * 60 + hora.minute

    /** Arma los tramos con los fichajes NO anulados del día (igual que armar_tramos). */
    fun armarTramos(fichajesDelDia: List<Fichaje>): Tramos {
        val activos = fichajesDelDia.filter { !it.anulado }.sortedBy { it.hora }
        val tramos = mutableListOf<Tramo>()
        var entrada: Fichaje? = null
        for (f in activos) {
            if (f.tipo == TipoFichaje.ENTRADA) {
                entrada = f
            } else if (entrada != null) {
                tramos += Tramo(entrada, f)
                entrada = null
            }
        }
        return Tramos(tramos, entrada)
    }

    fun calcularGanado(minutos: Int, tarifa: BigDecimal, multiplicador: BigDecimal): BigDecimal =
        BigDecimal(minutos).divide(SESENTA, PYTHON)
            .multiply(tarifa, PYTHON)
            .multiply(multiplicador, PYTHON)
            .setScale(2, RoundingMode.HALF_UP)

    fun tarifaVigente(tarifas: List<Tarifa>, fecha: LocalDate): BigDecimal =
        tarifas.filter { !it.vigenteDesde.isAfter(fecha) }.maxByOrNull { it.vigenteDesde }?.monto ?: CERO

    fun feriadoDe(feriados: List<Feriado>, fecha: LocalDate): Feriado? = feriados.firstOrNull { it.fecha == fecha }

    fun multiplicador(feriados: List<Feriado>, fecha: LocalDate): BigDecimal =
        feriadoDe(feriados, fecha)?.multiplicador ?: BigDecimal.ONE

    /** Totales de un día. */
    data class Dia(
        val minutos: Int = 0,
        val ganado: BigDecimal = CERO,
        val retiros: BigDecimal = CERO,
        val pagos: BigDecimal = CERO,
        val turnoAbierto: Boolean = false,
    ) {
        val neto: BigDecimal get() = ganado.subtract(retiros)
    }

    fun dia(datos: DatosEmpleado, fecha: LocalDate): Dia {
        val tramos = armarTramos(datos.fichajes.filter { it.fecha == fecha })
        val ganado = calcularGanado(tramos.minutos, tarifaVigente(datos.tarifas, fecha), multiplicador(datos.feriados, fecha))
        val retiros = datos.retiros.filter { it.fecha == fecha && !it.anulado }.fold(CERO) { a, r -> a.add(r.subtotal) }
        val pagos = datos.pagos.filter { it.fecha == fecha && !it.anulado }.fold(CERO) { a, p -> a.add(p.monto) }
        return Dia(tramos.minutos, ganado, retiros, pagos, tramos.turnoAbierto)
    }

    /** Saldo acumulado al cierre del día [hasta] (inclusive), como saldo_hasta. */
    fun saldoHasta(datos: DatosEmpleado, hasta: LocalDate): BigDecimal {
        val fechas = buildSet {
            datos.fichajes.forEach { if (!it.fecha.isAfter(hasta)) add(it.fecha) }
            datos.retiros.forEach { if (!it.fecha.isAfter(hasta)) add(it.fecha) }
            datos.pagos.forEach { if (!it.fecha.isAfter(hasta)) add(it.fecha) }
        }
        return fechas.fold(CERO) { saldo, fecha ->
            val d = dia(datos, fecha)
            saldo.add(d.ganado).subtract(d.retiros).subtract(d.pagos)
        }.setScale(2, RoundingMode.HALF_UP)
    }

    /**
     * Monto que se muestra en cada renglón de salida: lo ganado en ese tramo.
     * Se calcula por diferencia de acumulados, así la suma de los renglones da
     * exactamente el ganado del día (sin diferencias de un centavo por redondeo).
     */
    fun ganadoPorSalida(datos: DatosEmpleado, fecha: LocalDate): Map<String, BigDecimal> {
        val tramos = armarTramos(datos.fichajes.filter { it.fecha == fecha }).tramos
        val tarifa = tarifaVigente(datos.tarifas, fecha)
        val mult = multiplicador(datos.feriados, fecha)
        val resultado = mutableMapOf<String, BigDecimal>()
        var acumulado = 0
        var ganadoAnterior = CERO
        for (t in tramos) {
            acumulado += t.minutos
            val ganadoAcumulado = calcularGanado(acumulado, tarifa, mult)
            resultado[t.salida.id] = ganadoAcumulado.subtract(ganadoAnterior)
            ganadoAnterior = ganadoAcumulado
        }
        return resultado
    }

    fun formatoHoras(minutos: Int) = "${minutos / 60}:${(minutos % 60).toString().padStart(2, '0')}"
}
