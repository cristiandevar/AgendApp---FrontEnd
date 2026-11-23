package com.agendapp.dominio

import com.agendapp.dominio.Reglas.ControlFichaje
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** Controles del fichaje nuevo (entrega 3b), con los mismos criterios que registrar_fichaje. */
class FichajeTest {

    private val dia = LocalDate.of(2026, 10, 5)
    private fun h(hh: Int, mm: Int, ss: Int = 0) = LocalTime.of(hh, mm, ss)
    private val horarios = listOf(Horario(dia, h(8, 0), h(12, 0)), Horario(dia, h(16, 0), h(20, 0)))
    private fun entrada(id: String, hh: Int, mm: Int, anulado: Boolean = false) =
        Fichaje(id, dia, h(hh, mm), TipoFichaje.ENTRADA, anulado = anulado)
    private fun salida(id: String, hh: Int, mm: Int) = Fichaje(id, dia, h(hh, mm), TipoFichaje.SALIDA)

    private fun controlar(fichajes: List<Fichaje>, ahora: LocalTime, motivo: String = "", hs: List<Horario> = horarios) =
        Reglas.controlarFichaje(fichajes, hs, 15, ahora, motivo)

    @Test
    fun primeraEntradaDentroDeToleranciaSeRegistraAlMinuto() {
        val r = controlar(emptyList(), h(8, 5, 42))
        assertTrue(r is ControlFichaje.Valido)
        r as ControlFichaje.Valido
        assertEquals(TipoFichaje.ENTRADA, r.tipo)
        assertEquals(h(8, 5), r.hora) // se descartan los segundos
        assertEquals(false, r.fueraDeTolerancia)
        assertEquals(h(8, 0), r.referencia)
    }

    @Test
    fun despuesDeUnaEntradaTocaSalida() {
        val r = controlar(listOf(entrada("a", 8, 0)), h(12, 3)) as ControlFichaje.Valido
        assertEquals(TipoFichaje.SALIDA, r.tipo)
        assertEquals(h(12, 0), r.referencia)
    }

    @Test
    fun fueraDeToleranciaPideMotivo() {
        val r = controlar(emptyList(), h(8, 16))
        assertTrue(r is ControlFichaje.FaltaMotivo)
        assertEquals(h(8, 0), (r as ControlFichaje.FaltaMotivo).referencia)
        // Un motivo en blanco no cuenta.
        assertTrue(controlar(emptyList(), h(8, 16), "   ") is ControlFichaje.FaltaMotivo)
    }

    @Test
    fun fueraDeToleranciaConMotivoSeRegistra() {
        val r = controlar(emptyList(), h(8, 40), "  Se cortó el colectivo ") as ControlFichaje.Valido
        assertTrue(r.fueraDeTolerancia)
        assertEquals("Se cortó el colectivo", r.motivo)
    }

    @Test
    fun sinHorarioAsignadoPideMotivo() {
        val r = controlar(emptyList(), h(8, 0), hs = emptyList())
        assertTrue(r is ControlFichaje.FaltaMotivo)
        assertNull((r as ControlFichaje.FaltaMotivo).referencia)
    }

    @Test
    fun noSePuedeFicharDosVecesEnElMismoMinuto() {
        val r = controlar(listOf(entrada("a", 8, 5)), h(8, 5, 50))
        assertTrue(r is ControlFichaje.Rechazado)
        assertTrue((r as ControlFichaje.Rechazado).mensaje.startsWith("Ya fichaste a las 08:05"))
    }

    @Test
    fun horaDelCelularAnteriorAlUltimoFichajeSeRechaza() {
        val r = controlar(listOf(entrada("a", 8, 0), salida("b", 12, 0)), h(11, 30))
        assertTrue(r is ControlFichaje.Rechazado)
        assertTrue((r as ControlFichaje.Rechazado).mensaje.contains("anterior a tu último fichaje (12:00)"))
    }

    @Test
    fun losAnuladosNoCuentan() {
        // La entrada de las 08:05 se anuló: se puede volver a fichar entrada, incluso más temprano.
        val r = controlar(listOf(entrada("a", 8, 5, anulado = true)), h(8, 3)) as ControlFichaje.Valido
        assertEquals(TipoFichaje.ENTRADA, r.tipo)
    }

    @Test
    fun variosTramosEnElDia() {
        val f = listOf(entrada("a", 8, 0), salida("b", 12, 0))
        val r = controlar(f, h(15, 55)) as ControlFichaje.Valido
        assertEquals(TipoFichaje.ENTRADA, r.tipo)
        assertEquals(h(16, 0), r.referencia)
    }

    @Test
    fun secuenciaIgualQueElBackend() {
        assertNull(Reglas.errorDeSecuencia(listOf(h(8, 0) to TipoFichaje.ENTRADA, h(12, 0) to TipoFichaje.SALIDA)))
        assertNull(Reglas.errorDeSecuencia(listOf(h(8, 0) to TipoFichaje.ENTRADA))) // turno abierto
        assertEquals(
            "Secuencia inválida a las 08:00: se esperaba una entrada.",
            Reglas.errorDeSecuencia(listOf(h(8, 0) to TipoFichaje.SALIDA)),
        )
        assertEquals(
            "Hay dos fichajes a la misma hora (08:00).",
            Reglas.errorDeSecuencia(listOf(h(8, 0) to TipoFichaje.ENTRADA, h(8, 0) to TipoFichaje.SALIDA)),
        )
    }

    @Test
    fun motivosPorLosQueNoSePuedeAnular() {
        val t0 = OffsetDateTime.of(2026, 10, 5, 8, 0, 0, 0, ZoneOffset.ofHours(-3))
        val e = entrada("a", 8, 0).copy(registradoEn = t0)
        val s = salida("b", 12, 0).copy(registradoEn = t0.plusHours(4))
        val delDia = listOf(e, s)
        assertNull(Reglas.impedimentoParaAnular(s, delDia, t0.plusHours(4).plusMinutes(10), 10))
        assertEquals("Pasó el plazo de 10 minutos para anularlo.",
            Reglas.impedimentoParaAnular(s, delDia, t0.plusHours(4).plusMinutes(11), 10))
        assertEquals("Solo se puede anular el último fichaje del día.",
            Reglas.impedimentoParaAnular(e, listOf(e.copy(registradoEn = t0), s), t0.plusMinutes(5), 10))
        assertEquals("Lo cargó el encargado: solo él puede corregirlo.",
            Reglas.impedimentoParaAnular(s.copy(origen = ORIGEN_ENCARGADO), delDia, t0.plusHours(4), 10))
        assertEquals("El fichaje ya está anulado.",
            Reglas.impedimentoParaAnular(s.copy(anulado = true), delDia, t0.plusHours(4), 10))
    }
}
