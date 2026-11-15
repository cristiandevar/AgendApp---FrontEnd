package com.agendapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Paleta de libreta: papel claro, tinta azul y el rojo del margen.
val Papel = Color(0xFFFBF8EF)
val PapelOscuro = Color(0xFFF1ECDD)
val Tinta = Color(0xFF2B4C9B)
val TintaClara = Color(0xFFDCE4F5)
val Margen = Color(0xFFC0392B)
val Renglon = Color(0xFFC9D6EA)
val Grafito = Color(0xFF2E2E33)

private val esquemaLibreta = lightColorScheme(
    primary = Tinta,
    onPrimary = Color.White,
    primaryContainer = TintaClara,
    onPrimaryContainer = Color(0xFF14254F),
    secondary = Color(0xFF5B6472),
    onSecondary = Color.White,
    tertiary = Margen,
    onTertiary = Color.White,
    error = Margen,
    onError = Color.White,
    background = Papel,
    onBackground = Grafito,
    surface = Papel,
    onSurface = Grafito,
    surfaceVariant = PapelOscuro,
    onSurfaceVariant = Color(0xFF4E4A42),
    outline = Renglon,
)

@Composable
fun AgendAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = esquemaLibreta,
        typography = Typography(),
        content = content,
    )
}
