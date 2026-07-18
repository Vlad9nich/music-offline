package com.codex.signalflow.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
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

/** Single source of truth for YaNeoDex Android dark theme. */
private val SignalFlowColors = darkColorScheme(
    primary = Color(0xFF38F2FA),
    onPrimary = Color(0xFF031217),
    secondary = Color(0xFFE24872),
    onSecondary = Color(0xFF17030A),
    tertiary = Color(0xFF74FF8B),
    background = Color(0xFF05070B),
    surface = Color(0xFF0D1119),
    surfaceVariant = Color(0xFF121A26),
    onSurface = Color(0xFFE9EEF9),
    onBackground = Color(0xFFE9EEF9),
    outline = Color(0xFF2A3548),
)

private val SignalFlowTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 26.sp,
        letterSpacing = 0.4.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.4.sp,
    ),
)

/** Soft product shapes — less "топорно" than full cut-corners everywhere. */
private val SignalFlowShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
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
