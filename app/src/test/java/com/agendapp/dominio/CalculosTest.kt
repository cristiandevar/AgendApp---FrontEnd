package com.agendapp.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** Los mismos casos que agenda/tests.py del backend: el celular tiene que dar igual. */
class CalculosTest {

    private val dia1 = LocalDate.of(2026, 10, 5) // lunes
    private val dia2 = LocalDate.of(2026, 10, 6)
    private val tarifa2000 = listOf(Tarifa(LocalDate.of(2026, 1, 1), BigDecimal("2000")))
    private val cafe = ProductoDisponible("cafe", "Café", BigDecimal("800"), maxDiario = 2)
    private val sandwich = ProductoDisponible("sandwich", "Sándwich", BigDecimal("1600"), maxDiario = 0)
    private var n = 0

    private fun h(hh: Int, mm: Int) = LocalTime.of(hh, mm)
    private fun d(s: String) = BigDecimal(s)

    private fun fichaje(fecha: LocalDate, tipo: TipoFichaje, hh: Int, mm: Int, anulado: Boolean = false) =
        Fichaje(id = "f${n++}", fecha = fecha, hora = h(hh, mm), tipo = tipo, anulado = anulado)

    private fun jornada(fecha: LocalDate, vararg marcas: Pair<Int, Int>) = marcas.mapIndexed { i, (hh, mm) ->
        fichaje(fecha, if (i % 2 == 0) TipoFichaje.ENTRADA else TipoFichaje.SALIDA, hh, mm)
    }

    private fun retiro(fecha: LocalDate, p: ProductoDisponible, cantidad: Int, anulado: Boolean = false) =
        Retiro("r${n++}", fecha, h(10, 0), p.id, p.nombre, cantidad, p.precioCosto, anulado = anulado)

    @Test
    fun ejemploDelDisenio() {
        // Día 1: 08:00-12:00 y 13:00-17:30 = 8,5 h x $2000 = $17000; retiros $20000 -> saldo -3000
        val dia1Fichajes = jornada(dia1, 8 to 0, 12 to 0, 13 to 0, 17 to 30)
        var datos = DatosEmpleado(
            fichajes = dia1Fichajes,
            retiros = listOf(retiro(dia1, sandwich, 12), retiro(dia1, cafe, 1)),
            tarifas = tarifa2000,
        )
        val r1 = ArmadoHoja.armar(datos, dia1, dia1).resumen
        assertEquals("8:30", Calculos.formatoHoras(r1.minutos))
        assertEquals(d("17000.00"), r1.ganado)
        assertEquals(d("20000.00"), r1.retiros.setScale(2))
        assertEquals(d("-3000.00"), r1.saldo.setScale(2))

        // Día 2: arrastra -3000, gana 17000, retira 2000, cobra 10000 -> saldo 2000
        val medialuna = ProductoDisponible("medialuna", "Medialuna", BigDecimal("400"), 0)
        datos = datos.copy(
            fichajes = dia1Fichajes + jornada(dia2, 8 to 0, 12 to 0, 13 to 0, 17 to 30),
            retiros = datos.retiros + retiro(dia2, cafe, 2) + retiro(dia2, medialuna, 1),
            pagos = listOf(Pago("p1", dia2, h(18, 0), d("10000"), "Adelanto")),
        )
        val r2 = ArmadoHoja.armar(datos, dia2, dia2).resumen
        assertEquals(d("-3000.00"), r2.saldoAnterior)
        assertEquals(d("2000.00"), r2.retiros.setScale(2))
        assertEquals(d("10000.00"), r2.recibido.setScale(2))
        assertEquals(d("2000.00"), r2.saldo.setScale(2))
    }

    @Test
    fun turnoAbiertoYAnuladosNoSuman() {
        val datos = DatosEmpleado(
            fichajes = listOf(
                fichaje(dia1, TipoFichaje.ENTRADA, 8, 0),
                fichaje(dia1, TipoFichaje.SALIDA, 10, 0),
                fichaje(dia1, TipoFichaje.SALIDA, 9, 0, anulado = true),
                fichaje(dia1, TipoFichaje.ENTRADA, 11, 0), // sin salida todavía
            ),
            tarifas = tarifa2000,
        )
        val hoja = ArmadoHoja.armar(datos, dia1, dia1)
        assertEquals(120, hoja.resumen.minutos)
        assertTrue(hoja.resumen.turnoAbierto)
        assertEquals(h(11, 0), hoja.trabajandoDesde)
    }

    @Test
    fun feriadoPagaConSuMultiplicador() {
        val base = DatosEmpleado(fichajes = jornada(dia1, 9 to 0, 13 to 0), tarifas = tarifa2000)
        val doble = base.copy(feriados = listOf(Feriado(dia1, "Feriado", d("2"))))
        assertEquals(d("16000.00"), Calculos.dia(doble, dia1).ganado)
        val unoYMedio = base.copy(feriados = listOf(Feriado(dia1, "Feriado", d("1.5"))))
        assertEquals(d("12000.00"), Calculos.dia(unoYMedio, dia1).ganado)
    }

    @Test
    fun tarifaSegunFecha() {
        val datos = DatosEmpleado(
            fichajes = jornada(dia1, 9 to 0, 10 to 0) + jornada(dia2, 9 to 0, 10 to 0),
            tarifas = tarifa2000 + Tarifa(dia2, d("3000")),
        )
        assertEquals(d("2000.00"), Calculos.dia(datos, dia1).ganado)
        assertEquals(d("3000.00"), Calculos.dia(datos, dia2).ganado)
    }

    @Test
    fun minutosSueltos() {
        val datos = DatosEmpleado(fichajes = jornada(dia1, 9 to 0, 9 to 20), tarifas = tarifa2000)
        assertEquals(d("666.67"), Calculos.dia(datos, dia1).ganado) // 20 min = 1/3 h
    }

    @Test
    fun montosDeSalidaSumanElGanadoDelDia() {
        // Dos tramos de 20 min: por separado darían 666.67 + 666.67, pero el día vale 1333.33.
        val fichajes = jornada(dia1, 9 to 0, 9 to 20, 10 to 0, 10 to 20)
        val datos = DatosEmpleado(fichajes = fichajes, tarifas = tarifa2000)
        val porSalida = Calculos.ganadoPorSalida(datos, dia1)
        assertEquals(d("666.67"), porSalida[fichajes[1].id])
        assertEquals(d("666.66"), porSalida[fichajes[3].id])
        assertEquals(Calculos.dia(datos, dia1).ganado, porSalida.values.reduce(BigDecimal::add))
    }

    @Test
    fun hojaOrdenadaConSaldosPrimeroYUltimo() {
        val fichajes = jornada(dia1, 8 to 0, 12 to 0)
        val datos = DatosEmpleado(
            fichajes = fichajes,
            retiros = listOf(Retiro("r", dia1, h(10, 15), cafe.id, cafe.nombre, 2, cafe.precioCosto)),
            pagos = listOf(Pago("p", dia1, h(12, 0), d("1000"), "Adelanto")),
            tarifas = tarifa2000,
        )
        val renglones = ArmadoHoja.armar(datos, dia1, dia1).renglones
        assertTrue(renglones.first() is Renglon.SaldoAnterior)
        assertTrue(renglones.last() is Renglon.SaldoALaFecha)
        val eventos = renglones.filterIsInstance<Renglon.Evento>()
        assertEquals(
            listOf(TipoRenglon.ENTRADA, TipoRenglon.RETIRO, TipoRenglon.SALIDA, TipoRenglon.RECIBIDO),
            eventos.map { it.tipo },
        )
        assertEquals("2 × Café", eventos[1].detalle)
        assertEquals(d("-1600"), eventos[1].monto)
        assertEquals(d("8000.00"), eventos[2].monto)
        assertNull(eventos[0].monto)
        val saldo = renglones.last() as Renglon.SaldoALaFecha
        assertEquals(d("5400.00"), saldo.monto.setScale(2)) // 8000 - 1600 - 1000
    }
}

class ReglasTest {

    private val dia = LocalDate.of(2026, 10, 5)
    private fun h(hh: Int, mm: Int) = LocalTime.of(hh, mm)
    private val horarios = listOf(Horario(dia, h(8, 0), h(12, 0)), Horario(dia, h(16, 0), h(20, 0)))

    @Test
    fun tolerancia() {
        fun fuera(tipo: TipoFichaje, hh: Int, mm: Int) = Reglas.evaluarTolerancia(horarios, tipo, h(hh, mm), 15).fuera
        assertFalse(fuera(TipoFichaje.ENTRADA, 8, 15))
        assertTrue(fuera(TipoFichaje.ENTRADA, 8, 16))
        assertFalse(fuera(TipoFichaje.ENTRADA, 15, 50)) // turno tarde
        assertFalse(fuera(TipoFichaje.SALIDA, 20, 10))
        assertTrue(fuera(TipoFichaje.SALIDA, 12, 30))
        assertEquals(h(16, 0), Reglas.evaluarTolerancia(horarios, TipoFichaje.ENTRADA, h(15, 50), 15).referencia)
    }

    @Test
    fun sinHorarioAsignadoEsFueraDeTolerancia() {
        val t = Reglas.evaluarTolerancia(emptyList(), TipoFichaje.ENTRADA, h(8, 0), 15)
        assertTrue(t.fuera)
        assertNull(t.referencia)
    }

    @Test
    fun siguienteTipo() {
        assertEquals(TipoFichaje.ENTRADA, Reglas.siguienteTipo(emptyList()))
        val entrada = Fichaje("a", dia, h(8, 0), TipoFichaje.ENTRADA)
        assertEquals(TipoFichaje.SALIDA, Reglas.siguienteTipo(listOf(entrada)))
        val salida = Fichaje("b", dia, h(12, 0), TipoFichaje.SALIDA)
        assertEquals(TipoFichaje.ENTRADA, Reglas.siguienteTipo(listOf(entrada, salida)))
        // Si la salida se anuló, vuelve a tocar salida.
        assertEquals(TipoFichaje.SALIDA, Reglas.siguienteTipo(listOf(entrada, salida.copy(anulado = true))))
    }

    @Test
    fun plazoDeDiezMinutos() {
        val t0 = OffsetDateTime.of(2026, 10, 5, 8, 0, 0, 0, ZoneOffset.ofHours(-3))
        assertTrue(Reglas.dentroDelPlazo(t0, false, t0.plusMinutes(10), 10))
        assertFalse(Reglas.dentroDelPlazo(t0, false, t0.plusMinutes(11), 10))
        assertFalse(Reglas.dentroDelPlazo(t0, true, t0, 10)) // ya anulado
        assertFalse(Reglas.dentroDelPlazo(null, false, t0, 10))
    }

    @Test
    fun soloSePuedeAnularElUltimoFichajeVigente() {
        val t0 = OffsetDateTime.of(2026, 10, 5, 8, 0, 0, 0, ZoneOffset.ofHours(-3))
        val entrada = Fichaje("a", dia, h(8, 0), TipoFichaje.ENTRADA, registradoEn = t0)
        val salida = Fichaje("b", dia, h(8, 5), TipoFichaje.SALIDA, registradoEn = t0.plusMinutes(5))
        val delDia = listOf(entrada, salida)
        val ahora = t0.plusMinutes(6)
        assertFalse(Reglas.puedeAnularFichaje(entrada, delDia, ahora, 10))
        assertTrue(Reglas.puedeAnularFichaje(salida, delDia, ahora, 10))
        val delEncargado = salida.copy(origen = ORIGEN_ENCARGADO)
        assertFalse(Reglas.puedeAnularFichaje(delEncargado, listOf(entrada, delEncargado), ahora, 10))
    }

    @Test
    fun restanteDiario() {
        val cafe = ProductoDisponible("cafe", "Café", BigDecimal("800"), maxDiario = 2)
        val libre = ProductoDisponible("x", "Sin límite", BigDecimal("1"), maxDiario = 0)
        val r1 = Retiro("r1", dia, h(9, 0), "cafe", "Café", 1, BigDecimal("800"))
        val anulado = Retiro("r2", dia, h(9, 5), "cafe", "Café", 1, BigDecimal("800"), anulado = true)
        assertEquals(1, Reglas.restanteDiario(cafe, listOf(r1, anulado), dia))
        assertEquals(2, Reglas.restanteDiario(cafe, listOf(r1), dia, excluirRetiroId = "r1"))
        assertEquals(2, Reglas.restanteDiario(cafe, listOf(r1), dia.plusDays(1)))
        assertNull(Reglas.restanteDiario(libre, listOf(r1), dia))
    }
}
