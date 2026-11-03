package com.codex.signalflow.ui.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Pure Black & White Glitch / Watch Dogs 2 (DedSec) terminal color scheme.
 * High contrast, minimal, no distracting neon pink or acid greens.
 */
private val SignalFlowColors = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF000000),
    secondary = Color(0xFFD6DBE5),
    onSecondary = Color(0xFF000000),
    tertiary = Color(0xFF8A94A6),
    background = Color(0xFF040507),
    surface = Color(0xFF0A0C10),
    surfaceVariant = Color(0xFF12151D),
    onSurface = Color(0xFFF2F4F8),
    onBackground = Color(0xFFF2F4F8),
    outline = Color(0xFF262D3C),
    outlineVariant = Color(0xFF191D26),
)

private val SignalFlowTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        letterSpacing = 1.2.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        letterSpacing = 0.8.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.4.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 19.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        letterSpacing = 0.8.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.6.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 0.4.sp,
    ),
)

/** Watch Dogs 2 style: Chamfered / Cut-corners (45° angles) instead of rounded bubbles. */
private val SignalFlowShapes = Shapes(
    extraSmall = CutCornerShape(3.dp),
    small = CutCornerShape(6.dp),
    medium = CutCornerShape(10.dp),
    large = CutCornerShape(14.dp),
    extraLarge = CutCornerShape(18.dp),
)

@Composable
fun SignalFlowTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SignalFlowColors,
        typography = SignalFlowTypography,
        shapes = SignalFlowShapes,
        content = content,
    )
}
