package br.com.ricardobandeira.alcada.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Positive = Color(0xFF35D07F); val Negative = Color(0xFFFF5C65); val Pending = Color(0xFFFFB84D); val Analytic = Color(0xFF46C7F4)
private val colors = darkColorScheme(primary = Positive, secondary = Analytic, tertiary = Pending, error = Negative, background = Color(0xFF080B0F), surface = Color(0xFF11161D), surfaceVariant = Color(0xFF19212B), onBackground = Color(0xFFE7EDF4), onSurface = Color(0xFFE7EDF4))
@Composable fun AlcadaTheme(content: @Composable () -> Unit) { MaterialTheme(colorScheme = colors, typography = Typography(), content = content) }

