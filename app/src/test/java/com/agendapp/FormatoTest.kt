package com.agendapp

import com.agendapp.util.comoPesos
import com.agendapp.util.horaCorta
import com.agendapp.util.sinCeros
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class FormatoTest {
    @Test
    fun pesos() {
        assertEquals("$1.600,00", BigDecimal("1600").comoPesos())
        assertEquals("$17.208,33", BigDecimal("17208.333").comoPesos())
        assertEquals("$0,50", BigDecimal("0.5").comoPesos())
        assertEquals("-$3.000,00", BigDecimal("-3000").comoPesos())
        assertEquals("$1.234.567,89", BigDecimal("1234567.89").comoPesos())
    }

    @Test
    fun otros() {
        assertEquals("08:00", horaCorta("08:00:00"))
        assertEquals("10", BigDecimal("10.00").sinCeros())
        assertEquals("1.5", BigDecimal("1.50").sinCeros())
    }
}
