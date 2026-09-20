package br.com.ricardobandeira.alcada.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Positive = Color(0xFF32D583)
val Negative = Color(0xFFFF6673)
val Pending = Color(0xFFFDB022)
val Analytic = Color(0xFF56C8F5)

private val Graphite = Color(0xFF080B10)
private val Panel = Color(0xFF10151D)
private val Raised = Color(0xFF171E28)
private val Border = Color(0xFF283341)
private val TextPrimary = Color(0xFFF2F5F8)
private val TextSecondary = Color(0xFF9CAABC)

private val colors = darkColorScheme(
    primary = Positive,
    onPrimary = Color(0xFF03140C),
    primaryContainer = Color(0xFF123426),
    onPrimaryContainer = Color(0xFFB9F6D5),
    secondary = Analytic,
    onSecondary = Color(0xFF04151D),
    secondaryContainer = Color(0xFF12303D),
    onSecondaryContainer = Color(0xFFC4ECFB),
    tertiary = Pending,
    error = Negative,
    background = Graphite,
    surface = Panel,
    surfaceVariant = Raised,
    surfaceContainer = Panel,
    surfaceContainerHigh = Raised,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline = Border,
    outlineVariant = Color(0xFF202A36)
)

private val typography = Typography(
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, letterSpacing = 0.35.sp)
)

private val shapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16),
    large = androidx.compose.foundation.shape.RoundedCornerShape(22),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28)
)

@Composable
fun AlcadaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes, content = content)
}
