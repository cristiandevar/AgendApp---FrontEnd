package com.agendapp.dominio

import com.agendapp.dominio.Reglas.ControlRetiro
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** Controles de retiros (entrega 3c), con los mismos criterios que registrar_retiro y modificar_retiro_empleado. */
class RetiroTest {

    private val dia = LocalDate.of(2026, 10, 5)
    private val cafe = ProductoDisponible("cafe", "Café", BigDecimal("800"), maxDiario = 2, codigoBarra = "AGENDAPP-0001")
    private val sandwich = ProductoDisponible("sandwich", "Sándwich de miga", BigDecimal("1600"), maxDiario = 0)
    private val agua = ProductoDisponible("agua", "Agua mineral", BigDecimal("500"), maxDiario = 3)
    private val activos = listOf(agua, cafe, sandwich)

    private fun retiro(id: String, p: ProductoDisponible, cantidad: Int, anulado: Boolean = false, precio: BigDecimal = p.precioCosto) =
        Retiro(id, dia, LocalTime.of(10, 0), p.id, p.nombre, cantidad, precio, anulado = anulado)

    private fun controlar(productoId: String?, cantidad: Int, retiros: List<Retiro> = emptyList(), original: Retiro? = null) =
        Reglas.controlarRetiro(productoId, cantidad, activos, retiros, dia, original)

    @Test
    fun retiroNuevoValidoCongelaElPrecioActual() {
        val r = controlar("cafe", 2) as ControlRetiro.Valido
        assertEquals(cafe, r.producto)
        assertEquals(2, r.cantidad)
        assertEquals(BigDecimal("800"), r.precioCosto)
    }

    @Test
    fun sinProductoOCantidadInvalida() {
        assertEquals(ControlRetiro.Rechazado("Elegí un producto."), controlar(null, 1))
        assertEquals(ControlRetiro.Rechazado("La cantidad tiene que ser al menos 1."), controlar("cafe", 0))
        assertEquals(ControlRetiro.Rechazado("El producto ya no está disponible."), controlar("inexistente", 1))
    }

    @Test
    fun maximoDiarioConElMensajeDelBackend() {
        val hoy = listOf(retiro("a", cafe, 1), retiro("b", cafe, 1, anulado = true))
        assertTrue(controlar("cafe", 1, hoy) is ControlRetiro.Valido) // el anulado no cuenta
        assertEquals(
            ControlRetiro.Rechazado("Supera el máximo diario de Café (2). Ya retiraste 1 hoy; podés retirar hasta 1 más."),
            controlar("cafe", 2, hoy),
        )
        // Otro día no cuenta.
        val ayer = listOf(retiro("c", cafe, 2).copy(fecha = dia.minusDays(1)))
        assertTrue(controlar("cafe", 2, ayer) is ControlRetiro.Valido)
    }

    @Test
    fun sinMaximoDiarioNoHayLimite() {
        assertTrue(controlar("sandwich", 50) is ControlRetiro.Valido)
        assertNull(Reglas.restanteDiario(sandwich, emptyList(), dia))
    }

    @Test
    fun modificarCantidadExcluyeElPropioRetiro() {
        val original = retiro("a", cafe, 1)
        // Pasar de 1 a 2 cafés: lo ya retirado sin contar este es 0, así que entra.
        val r = controlar("cafe", 2, listOf(original), original) as ControlRetiro.Valido
        assertEquals(2, r.cantidad)
        assertEquals(1, Reglas.restanteDiario(cafe, listOf(original), dia)) // retiro nuevo: queda 1
        assertEquals(2, Reglas.restanteDiario(cafe, listOf(original), dia, excluirRetiroId = "a")) // modificándolo: hasta 2
    }

    @Test
    fun modificarSinCambios() {
        val original = retiro("a", cafe, 1)
        assertEquals(ControlRetiro.SinCambios, controlar("cafe", 1, listOf(original), original))
    }

    @Test
    fun modificarConservaElPrecioOriginalSiNoCambiaElProducto() {
        // El café se retiró a $700; hoy cuesta $800. Al cambiar la cantidad sigue a $700.
        val original = retiro("a", cafe, 1, precio = BigDecimal("700"))
        val r = controlar("cafe", 2, listOf(original), original) as ControlRetiro.Valido
        assertEquals(BigDecimal("700"), r.precioCosto)
    }

    @Test
    fun modificarAOtroProductoTomaSuPrecioActual() {
        val original = retiro("a", cafe, 1, precio = BigDecimal("700"))
        val r = controlar("agua", 1, listOf(original), original) as ControlRetiro.Valido
        assertEquals(agua, r.producto)
        assertEquals(BigDecimal("500"), r.precioCosto)
    }

    @Test
    fun modificarElMismoProductoAunqueYaNoEsteActivo() {
        val descontinuado = ProductoDisponible("te", "Té", BigDecimal("400"), maxDiario = 1)
        val original = retiro("a", descontinuado, 1)
        val r = controlar("te", 3, listOf(original), original) as ControlRetiro.Valido
        assertEquals("Té", r.producto.nombre)
        assertEquals(BigDecimal("400"), r.precioCosto)
        // Pero no se puede pasar a un producto inactivo distinto del original.
        assertEquals(ControlRetiro.Rechazado("El producto ya no está disponible."), controlar("te", 1))
    }

    @Test
    fun plazoParaCambiarRetiro() {
        val t0 = OffsetDateTime.of(2026, 10, 5, 10, 0, 0, 0, ZoneOffset.ofHours(-3))
        val r = retiro("a", cafe, 1).copy(registradoEn = t0)
        assertNull(Reglas.impedimentoParaCambiarRetiro(r, t0.plusMinutes(10), 10))
        assertEquals(
            "Pasó el plazo de 10 minutos para cambiarlo. Pedile al encargado que lo corrija.",
            Reglas.impedimentoParaCambiarRetiro(r, t0.plusMinutes(11), 10),
        )
        assertEquals("El retiro ya está anulado.", Reglas.impedimentoParaCambiarRetiro(r.copy(anulado = true), t0, 10))
        assertEquals(
            "Lo cargó el encargado: solo él puede corregirlo.",
            Reglas.impedimentoParaCambiarRetiro(r.copy(origen = ORIGEN_ENCARGADO), t0, 10),
        )
    }

    @Test
    fun busquedaIncremental() {
        fun nombres(texto: String) = Busqueda.filtrar(activos, texto).map { it.id }
        assertEquals(listOf("agua", "cafe", "sandwich"), nombres(""))
        assertEquals(listOf("cafe"), nombres("cafe")) // sin acento
        assertEquals(listOf("cafe"), nombres("CAF"))
        assertEquals(listOf("sandwich"), nombres("miga sand")) // palabras en cualquier orden
        assertEquals(listOf("agua", "sandwich"), nombres("a m"))
        assertEquals(listOf("cafe"), nombres(" agendapp-0001 ")) // código tipeado a mano
        assertEquals(emptyList<String>(), nombres("yerba"))
    }

    @Test
    fun elPlazoCuentaDesdeElSegundoDeRegistro() {
        // Caso reportado: registrado a las 22:38:45 (el renglón dice 22:38). A las 22:48:30 todavía
        // se puede cambiar; vence a las 22:48:45.
        val registro = OffsetDateTime.of(2026, 11, 22, 22, 38, 45, 0, ZoneOffset.ofHours(-3))
        val r = retiro("a", cafe, 1).copy(registradoEn = registro)
        val a2248y30 = registro.withMinute(48).withSecond(30)
        assertNull(Reglas.impedimentoParaCambiarRetiro(r, a2248y30, 10))
        assertEquals(15L, Reglas.segundosRestantes(registro, a2248y30, 10))
        assertNull(Reglas.impedimentoParaCambiarRetiro(r, registro.plusMinutes(10), 10)) // justo en el límite
        assertTrue(Reglas.impedimentoParaCambiarRetiro(r, registro.plusMinutes(10).plusSeconds(1), 10) != null)
        assertEquals(0L, Reglas.segundosRestantes(registro, registro.plusMinutes(11), 10))
        assertNull(Reglas.segundosRestantes(null, registro, 10))
        assertEquals("2:05", Reglas.formatoCuentaRegresiva(125))
        assertEquals("10:00", Reglas.formatoCuentaRegresiva(600))
        assertEquals("0:00", Reglas.formatoCuentaRegresiva(0))
    }
}
