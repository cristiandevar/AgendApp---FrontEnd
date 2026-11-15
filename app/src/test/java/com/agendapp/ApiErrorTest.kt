package com.agendapp

import com.agendapp.data.remote.ApiError
import org.junit.Assert.assertEquals
import org.junit.Test

/** Formatos de error reales del backend de AgendApp. */
class ApiErrorTest {

    @Test
    fun loginIncorrecto_seTraduce() {
        val cuerpo = """{"detail":"No active account found with the given credentials"}"""
        assertEquals("Usuario o contraseña incorrectos.", ApiError.mensajeHttp(401, cuerpo))
    }

    @Test
    fun tokenInvalido_seTraduce() {
        val cuerpo = """{"detail":"Given token not valid for any token type","code":"token_not_valid","messages":[{"token_class":"AccessToken","token_type":"access","message":"Token is invalid"}]}"""
        assertEquals("La sesión expiró. Ingresá de nuevo.", ApiError.mensajeHttp(401, cuerpo))
    }

    @Test
    fun detalleDeNegocio_enLista() {
        val cuerpo = """{"detalle":["La fecha de vigencia no puede ser anterior a hoy."]}"""
        assertEquals("La fecha de vigencia no puede ser anterior a hoy.", ApiError.mensajeHttp(400, cuerpo))
    }

    @Test
    fun detalleDeNegocio_texto() {
        val cuerpo = """{"detalle":"No podés desactivar tu propio usuario."}"""
        assertEquals("No podés desactivar tu propio usuario.", ApiError.mensajeHttp(409, cuerpo))
    }

    @Test
    fun erroresPorCampo() {
        val cuerpo = """{"nombre":["Ya existe producto con este nombre."]}"""
        assertEquals("nombre: Ya existe producto con este nombre.", ApiError.mensajeHttp(400, cuerpo))
    }

    @Test
    fun sinCuerpo_usaElCodigo() {
        assertEquals("Error del servidor (500). Probá de nuevo más tarde.", ApiError.mensajeHttp(500, null))
        assertEquals("Error del servidor (502). Probá de nuevo más tarde.", ApiError.mensajeHttp(502, "<html>Bad gateway</html>"))
    }
}
