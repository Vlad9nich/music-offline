package com.codex.signalflow.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SignalFlowColors = darkColorScheme(
    primary = Color(0xFF38F2FA),
    onPrimary = Color(0xFF031217),
    secondary = Color(0xFFE24872),
    onSecondary = Color(0xFF17030A),
    background = Color(0xFF05070B),
    surface = Color(0xFF0D1119),
    onSurface = Color(0xFFE9EEF9),
    onBackground = Color(0xFFE9EEF9),
)

@Composable
fun SignalFlowTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SignalFlowColors,
        content = content,
    )
}
