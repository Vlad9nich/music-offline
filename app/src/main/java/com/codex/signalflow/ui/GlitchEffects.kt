package com.codex.signalflow.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Cut-Corner Chamfered shapes for Watch Dogs 2 style.
 */
internal val CutButtonShape = CutCornerShape(6.dp)
internal val CutPanelShape = CutCornerShape(10.dp)
internal val CutTrackShape = CutCornerShape(5.dp)
internal val CutSmallShape = CutCornerShape(3.dp)

/**
 * Clickable modifier with an instant glitch jitter & inverted flash on tap.
 */
@Composable
fun Modifier.glitchClickable(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val scope = rememberCoroutineScope()
    var isGlitching by remember { mutableStateOf(false) }
    var glitchOffset by remember { mutableStateOf(0f) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    return this
        .graphicsLayer {
            translationX = if (isGlitching) glitchOffset else 0f
            alpha = if (isPressed) 0.85f else 1f
        }
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = null,
            onClick = {
                scope.launch {
                    isGlitching = true
                    glitchOffset = -3f
                    delay(35)
                    glitchOffset = 3f
                    delay(35)
                    glitchOffset = 0f
                    isGlitching = false
                }
                onClick()
            },
        )
}

/**
 * CRT scanline overlay with faint horizontal stripes.
 */
fun Modifier.scanlineOverlay(
    lineColor: Color = Color(0x18FFFFFF),
    stepPx: Float = 6f,
): Modifier = this.drawWithContent {
    drawContent()
    val count = (size.height / stepPx).toInt()
    for (i in 0..count) {
        val y = i * stepPx
        drawLine(
            color = lineColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f,
        )
    }
}

/**
 * Subtle noise / corner tick mark painter for technical cards.
 */
fun Modifier.techCornerMarks(
    markColor: Color = Color(0x66FFFFFF),
    markLength: Float = 8f,
): Modifier = this.drawWithContent {
    drawContent()
    // Top-left cross
    drawLine(markColor, Offset(0f, 0f), Offset(markLength, 0f), strokeWidth = 1.5f)
    drawLine(markColor, Offset(0f, 0f), Offset(0f, markLength), strokeWidth = 1.5f)
    // Top-right cross
    drawLine(markColor, Offset(size.width - markLength, 0f), Offset(size.width, 0f), strokeWidth = 1.5f)
    drawLine(markColor, Offset(size.width, 0f), Offset(size.width, markLength), strokeWidth = 1.5f)
    // Bottom-left cross
    drawLine(markColor, Offset(0f, size.height - markLength), Offset(0f, size.height), strokeWidth = 1.5f)
    drawLine(markColor, Offset(0f, size.height), Offset(markLength, size.height), strokeWidth = 1.5f)
    // Bottom-right cross
    drawLine(markColor, Offset(size.width - markLength, size.height), Offset(size.width, size.height), strokeWidth = 1.5f)
    drawLine(markColor, Offset(size.width, size.height - markLength), Offset(size.width, size.height), strokeWidth = 1.5f)
}

/**
 * Blinking terminal block cursor: "█"
 */
@Composable
fun rememberBlinkingCursor(): String {
    val transition = rememberInfiniteTransition(label = "terminal_cursor")
    val alpha by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cursor_blink",
    )
    return if (alpha > 0.45f) "█" else " "
}
