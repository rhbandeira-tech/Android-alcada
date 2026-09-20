package br.com.ricardobandeira.alcada.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Positive = Color(0xFF35D07F); val Negative = Color(0xFFFF5C65); val Pending = Color(0xFFFFB84D); val Analytic = Color(0xFF46C7F4)
private val colors = darkColorScheme(primary = Positive, secondary = Analytic, tertiary = Pending, error = Negative, background = Color(0xFF070A0E), surface = Color(0xFF10151C), surfaceVariant = Color(0xFF18212B), onBackground = Color(0xFFEAF0F6), onSurface = Color(0xFFEAF0F6), onSurfaceVariant = Color(0xFF9BAABA), outline = Color(0xFF293442))
private val typography = Typography(
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 27.sp, letterSpacing = (-.4).sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = .5.sp)
)
@Composable fun AlcadaTheme(content: @Composable () -> Unit) { MaterialTheme(colorScheme = colors, typography = typography, content = content) }
