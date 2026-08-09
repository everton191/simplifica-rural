package br.com.simplificarural.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val RuralDarkGreen = Color(0xFF166534)
val RuralGreen = Color(0xFF15803D)
val RuralLightGreen = Color(0xFFDCFCE7)
val RuralBackground = Color(0xFFF7F9F7)
val RuralText = Color(0xFF17201A)
val RuralSecondaryText = Color(0xFF647067)
val RuralSuccess = Color(0xFF16A34A)
val RuralWarning = Color(0xFFF59E0B)
val RuralDanger = Color(0xFFDC2626)
val RuralInfo = Color(0xFF2563EB)
val RuralInactive = Color(0xFF94A3B8)

private val RuralColors = lightColorScheme(
    primary = RuralGreen,
    onPrimary = Color.White,
    primaryContainer = RuralLightGreen,
    onPrimaryContainer = RuralDarkGreen,
    background = RuralBackground,
    onBackground = RuralText,
    surface = Color.White,
    onSurface = RuralText,
    outline = Color(0xFFE2E8E3),
    error = RuralDanger
)

@Composable fun SimplificaRuralTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = RuralColors, content = content)
}
