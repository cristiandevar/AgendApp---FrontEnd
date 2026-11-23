package com.agendapp.dominio

import java.text.Normalizer
import java.util.Locale

/** Búsqueda incremental de productos en el popup de retiro. */
object Busqueda {

    private val MARCAS = Regex("\\p{M}+")
    private val ESPACIOS = Regex("\\s+")

    /** Minúsculas y sin acentos: "Café" y "cafe" son lo mismo. */
    fun normalizar(texto: String): String =
        Normalizer.normalize(texto, Normalizer.Form.NFD).replace(MARCAS, "").lowercase(Locale.ROOT).trim()

    /**
     * Productos cuyo nombre contiene todas las palabras escritas, en cualquier orden y sin
     * importar acentos ni mayúsculas. También encuentra un producto por su código exacto,
     * por si hay que tipearlo a mano. Sin texto, devuelve todos.
     */
    fun filtrar(productos: List<ProductoDisponible>, texto: String): List<ProductoDisponible> {
        val buscado = normalizar(texto)
        if (buscado.isEmpty()) return productos
        val palabras = buscado.split(ESPACIOS)
        return productos.filter { p ->
            val nombre = normalizar(p.nombre)
            palabras.all { nombre.contains(it) } || p.codigoBarra?.equals(texto.trim(), ignoreCase = true) == true
        }
    }
}
