package com.agendapp

import com.agendapp.data.remote.FeriadoDto
import com.agendapp.data.remote.HorarioDto
import com.agendapp.data.remote.LoginResponse
import com.agendapp.data.remote.ParametroDto
import com.agendapp.data.remote.ProductoDto
import com.agendapp.data.remote.TarifaDto
import com.agendapp.data.remote.jsonApi
import com.agendapp.data.repo.aUsuario
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/** Respuestas reales del backend (capturadas con los datos de seed_agenda). */
class DtosTest {

    @Test
    fun login() {
        val json = """{"refresh":"r","access":"a","usuario":{"id":2,"username":"ana","first_name":"Ana","last_name":"Pérez","nombre_completo":"Ana Pérez","rol":"EMPLEADO","modalidad_liquidacion":"DIARIA","fecha_alta":"2026-11-02","is_active":true}}"""
        val r = jsonApi.decodeFromString<LoginResponse>(json)
        val u = r.usuario.aUsuario()
        assertEquals("a", r.access)
        assertEquals(2, u.id)
        assertEquals("Ana Pérez", u.nombreCompleto)
        assertFalse(u.esEncargado)
        assertEquals("2026-11-02", u.fechaAlta)
    }

    @Test
    fun productos_conMontoSinPerderPrecision() {
        val json = """[{"id":"aca79544-0f52-4636-8c06-4d34ddaaf61a","nombre":"Agua mineral 500 ml","precio_costo":600.0,"max_diario":2,"activo":true,"actualizado":"2026-11-15T01:07:26.752213-03:00"},{"id":"x","nombre":"Café","precio_costo":666.67,"max_diario":0,"activo":true,"actualizado":"2026-11-15T01:07:26-03:00"}]"""
        val lista = jsonApi.decodeFromString<List<ProductoDto>>(json)
        assertEquals(2, lista.size)
        assertEquals(0, BigDecimal("600").compareTo(lista[0].precioCosto))
        assertEquals(BigDecimal("666.67"), lista[1].precioCosto)
    }

    @Test
    fun parametrosFeriadosTarifasHorarios() {
        val p = jsonApi.decodeFromString<List<ParametroDto>>(
            """[{"clave":"anulacion_fichaje_minutos","descripcion":"Plazo","valor":10.0,"actualizado":"2026-11-15T01:07:26.738046-03:00"}]"""
        )
        assertEquals(0, BigDecimal.TEN.compareTo(p[0].valor))

        val f = jsonApi.decodeFromString<List<FeriadoDto>>(
            """[{"id":"f5","fecha":"2026-12-08","descripcion":"Inmaculada Concepción de María","multiplicador":2.0,"es_modificable":true,"actualizado":"2026-11-15T01:07:26.745278-03:00"}]"""
        )
        assertEquals("2026-12-08", f[0].fecha)

        val t = jsonApi.decodeFromString<List<TarifaDto>>(
            """[{"id":"24","empleado":2,"empleado_nombre":"Ana Pérez","monto":2500.0,"vigente_desde":"2026-01-01","es_modificable":false,"actualizado":"2026-11-15T01:07:26.756071-03:00"}]"""
        )
        assertEquals(2, t[0].empleado)
        assertEquals(0, BigDecimal("2500").compareTo(t[0].monto))

        val h = jsonApi.decodeFromString<List<HorarioDto>>(
            """[{"id":"7a","empleado":2,"empleado_nombre":"Ana Pérez","fecha":"2026-11-16","hora_inicio":"08:00:00","hora_fin":"12:00:00","actualizado":"2026-11-15T01:07:26.772976-03:00"}]"""
        )
        assertEquals("08:00:00", h[0].horaInicio)
        assertTrue(h[0].fecha.startsWith("2026"))
    }
}
