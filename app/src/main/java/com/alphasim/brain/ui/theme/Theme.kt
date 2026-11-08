package com.alphasim.brain.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Semantic colours for metrics and statuses, tuned separately for dark and light. */
@Immutable
data class SignalColors(
    val good: Color,
    val bad: Color,
    val warn: Color,
    val info: Color,
    val muted: Color,
    val chartLine: Color,
    val chartFill: Color,
    val grid: Color,
)

private val DarkSignals = SignalColors(
    good = Color(0xFF5BD68A),
    bad = Color(0xFFFF6B6B),
    warn = Color(0xFFFFB547),
    info = Color(0xFF6CB4FF),
    muted = Color(0xFF7D8796),
    chartLine = Color(0xFF4FD1C5),
    chartFill = Color(0x334FD1C5),
    grid = Color(0xFF232A35),
)

private val LightSignals = SignalColors(
    good = Color(0xFF1E8E4E),
    bad = Color(0xFFC62828),
    warn = Color(0xFFB26A00),
    info = Color(0xFF1565C0),
    muted = Color(0xFF6B7380),
    chartLine = Color(0xFF00897B),
    chartFill = Color(0x2600897B),
    grid = Color(0xFFE3E7EC),
)

val LocalSignals = staticCompositionLocalOf { DarkSignals }

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF4FD1C5),
    onPrimary = Color(0xFF00201D),
    primaryContainer = Color(0xFF0F3B37),
    onPrimaryContainer = Color(0xFFB8F2EB),
    secondary = Color(0xFFFFB547),
    onSecondary = Color(0xFF2B1A00),
    secondaryContainer = Color(0xFF3D2B0A),
    onSecondaryContainer = Color(0xFFFFDDB0),
    tertiary = Color(0xFF6CB4FF),
    background = Color(0xFF0B0E13),
    onBackground = Color(0xFFE6E9EF),
    surface = Color(0xFF0B0E13),
    onSurface = Color(0xFFE6E9EF),
    surfaceVariant = Color(0xFF1A202B),
    onSurfaceVariant = Color(0xFF9AA4B2),
    surfaceContainerLowest = Color(0xFF07090D),
    surfaceContainerLow = Color(0xFF10141B),
    surfaceContainer = Color(0xFF131821),
    surfaceContainerHigh = Color(0xFF181E28),
    surfaceContainerHighest = Color(0xFF1E2530),
    surfaceBright = Color(0xFF242C38),
    surfaceDim = Color(0xFF0B0E13),
    outline = Color(0xFF3A4352),
    outlineVariant = Color(0xFF262D39),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF3A0000),
    errorContainer = Color(0xFF4A1414),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF00796B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8F2EB),
    onPrimaryContainer = Color(0xFF00201D),
    secondary = Color(0xFF9A5B00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDDB0),
    onSecondaryContainer = Color(0xFF2B1A00),
    tertiary = Color(0xFF1565C0),
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF14181F),
    surface = Color(0xFFF7F8FA),
    onSurface = Color(0xFF14181F),
    surfaceVariant = Color(0xFFE6EAF0),
    onSurfaceVariant = Color(0xFF4F5866),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF2F4F7),
    surfaceContainer = Color(0xFFEDF0F4),
    surfaceContainerHigh = Color(0xFFE7EBF0),
    surfaceContainerHighest = Color(0xFFE1E6EC),
    outline = Color(0xFFB4BCC8),
    outlineVariant = Color(0xFFD6DCE4),
)

val Mono = FontFamily.Monospace

private val AppTypography = Typography().let { t ->
    t.copy(
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = t.labelSmall.copy(letterSpacing = 0.4.sp),
    )
}

/** Monospace style used for expressions, ids and numbers. */
val CodeStyle = TextStyle(fontFamily = Mono, fontSize = 12.5.sp, lineHeight = 18.sp)

@Composable
fun AlphaSimTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalSignals provides if (darkTheme) DarkSignals else LightSignals) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = AppTypography,
            content = content,
        )
    }
}

object Signals {
    val current: SignalColors
        @Composable get() = LocalSignals.current
}
