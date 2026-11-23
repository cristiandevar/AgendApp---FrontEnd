package com.agendapp.ui.libreta

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Lector de códigos de barras y QR de Google Play Services. Abre su propia pantalla de
 * cámara y no pide permiso de cámara a la app. El módulo se descarga una sola vez (hace
 * falta internet esa vez); después funciona sin conexión.
 */
object Escaner {

    private fun cliente(contexto: Context) = GmsBarcodeScanning.getClient(
        contexto,
        GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .enableAutoZoom()
            .build(),
    )

    /** Pide a Play Services que descargue el módulo del lector si todavía no lo tiene. */
    fun prepararModulo(contexto: Context) {
        runCatching {
            ModuleInstall.getClient(contexto)
                .installModules(ModuleInstallRequest.newBuilder().addApi(cliente(contexto)).build())
        }
    }

    /** Abre el lector. [alLeer] recibe el contenido del código; [alFallar], el mensaje para mostrar. */
    fun escanear(contexto: Context, alLeer: (String) -> Unit, alFallar: (String) -> Unit) {
        try {
            cliente(contexto).startScan()
                .addOnSuccessListener { codigo ->
                    val texto = codigo.rawValue?.trim().orEmpty()
                    if (texto.isEmpty()) alFallar("No se pudo leer el código. Probá de nuevo.") else alLeer(texto)
                }
                .addOnFailureListener {
                    alFallar(
                        "No se pudo abrir el lector de códigos. La primera vez necesita internet para " +
                            "descargarse; mientras tanto buscá el producto por nombre."
                    )
                }
        } catch (e: Exception) {
            alFallar("Este celular no tiene el lector de códigos de Google disponible. Buscá el producto por nombre.")
        }
    }
}

/** Ícono de código de barras (el set básico de íconos de Material no lo trae). */
val IconoCodigo: ImageVector by lazy {
    ImageVector.Builder(name = "Codigo", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .apply {
            path(fill = SolidColor(Color.Black)) {
                // Barras verticales de distinto ancho.
                listOf(3f to 2f, 6f to 1f, 8.5f to 2f, 12f to 1f, 14f to 2.5f, 18f to 1f, 20f to 1f).forEach { (x, ancho) ->
                    moveTo(x, 5f)
                    lineTo(x + ancho, 5f)
                    lineTo(x + ancho, 19f)
                    lineTo(x, 19f)
                    close()
                }
            }
        }
        .build()
}
