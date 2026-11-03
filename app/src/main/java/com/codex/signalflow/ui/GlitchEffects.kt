package com.codex.signalflow.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Cut-Corner Chamfered shapes for KAIRSEC / DedSec style.
 */
internal val CutButtonShape = CutCornerShape(4.dp)
internal val CutPanelShape = CutCornerShape(8.dp)
internal val CutTrackShape = CutCornerShape(4.dp)
internal val CutSmallShape = CutCornerShape(2.dp)

/**
 * Tactile spring click with an instant glitch jitter & inverted flash on tap.
 */
@Composable
fun Modifier.tactileClickable(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "tactile_scale",
    )
    var flashActive by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .drawWithContent {
            drawContent()
            if (flashActive) {
                drawRect(Color(0x44FFFFFF))
            }
        }
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = null,
            onClick = {
                scope.launch {
                    flashActive = true
                    delay(50)
                    flashActive = false
                }
                onClick()
            },
        )
}

@Composable
fun Modifier.glitchClickable(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = this.tactileClickable(enabled = enabled, onClick = onClick)

/**
 * Prominent animated background with monochrome CRT scanlines, sweeping radar beam,
 * cyber mesh, and silver glitch bands.
 */
@Composable
fun Modifier.dedsecNoiseBackground(): Modifier {
    val transition = rememberInfiniteTransition(label = "dedsec_noise")
    val sweepProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sweep_progress",
    )
    val glitchPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "glitch_phase",
    )
    val flickerAlpha by transition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(120, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "flicker_alpha",
    )

    return this
        .background(Color(0xFF040609))
        .drawWithContent {
            // 1. Procedural cyber mesh grid (24dp)
            val step = 24.dp.toPx()
            val cols = (size.width / step).toInt()
            val rows = (size.height / step).toInt()
            for (c in 0..cols) {
                val x = c * step
                drawLine(Color(0x15FFFFFF), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
            }
            for (r in 0..rows) {
                val y = r * step
                drawLine(Color(0x15FFFFFF), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }

            // 2. Sweeping CRT radar beam (underneath)
            val sweepY = sweepProgress * size.height
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFFD0DCF0).copy(alpha = flickerAlpha * 0.7f),
                        Color(0xFFFFFFFF).copy(alpha = flickerAlpha),
                        Color(0xFFD0DCF0).copy(alpha = flickerAlpha * 0.7f),
                        Color.Transparent,
                    ),
                    startY = sweepY - 18.dp.toPx(),
                    endY = sweepY + 18.dp.toPx(),
                ),
                topLeft = Offset(0f, sweepY - 18.dp.toPx()),
                size = Size(size.width, 36.dp.toPx()),
            )

            // 3. Dynamic silver glitch bands
            val band1 = ((glitchPhase * 19.3f) % size.height)
            val band2 = ((glitchPhase * 43.7f) % size.height)
            drawRect(
                color = Color(0xFFD8E4F2).copy(alpha = flickerAlpha * 0.9f),
                topLeft = Offset(0f, band1),
                size = Size(size.width, 3.dp.toPx()),
            )
            drawRect(
                color = Color(0xFFB5C8DE).copy(alpha = flickerAlpha * 0.7f),
                topLeft = Offset(0f, band2),
                size = Size(size.width, 2.dp.toPx()),
            )

            // 4. Render child content
            drawContent()

            // 5. Overlay fine CRT scanlines (over foreground for authentic CRT glass look)
            val scanStep = 4.dp.toPx()
            val scanCount = (size.height / scanStep).toInt()
            for (s in 0..scanCount) {
                val sy = s * scanStep
                drawLine(Color(0x18FFFFFF), Offset(0f, sy), Offset(size.width, sy), strokeWidth = 1f)
            }

            // 6. Sweeping phosphor raster line (over foreground)
            drawLine(
                color = Color(0x35FFFFFF),
                start = Offset(0f, sweepY),
                end = Offset(size.width, sweepY),
                strokeWidth = 1.5f,
            )

            // 7. Dynamic VHS glitch flicker jump line
            val vhsJump = ((glitchPhase * 53.9f) % size.height)
            drawLine(
                color = Color(0x28FFFFFF),
                start = Offset(0f, vhsJump),
                end = Offset(size.width, vhsJump),
                strokeWidth = 2f,
            )
        }
}

/**
 * Scanline overlay helper.
 */
fun Modifier.scanlineOverlay(
    lineColor: Color = Color(0x14FFFFFF),
    stepPx: Float = 4f,
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
 * Corner tick mark painter.
 */
fun Modifier.techCornerMarks(
    markColor: Color = Color(0x66FFFFFF),
    markLength: Float = 10f,
): Modifier = this.drawWithContent {
    drawContent()
    val w = size.width
    val h = size.height
    // Top-left
    drawLine(markColor, Offset(0f, 0f), Offset(markLength, 0f), strokeWidth = 1.5f)
    drawLine(markColor, Offset(0f, 0f), Offset(0f, markLength), strokeWidth = 1.5f)
    // Top-right
    drawLine(markColor, Offset(w - markLength, 0f), Offset(w, 0f), strokeWidth = 1.5f)
    drawLine(markColor, Offset(w, 0f), Offset(w, markLength), strokeWidth = 1.5f)
    // Bottom-left
    drawLine(markColor, Offset(0f, h - markLength), Offset(0f, h), strokeWidth = 1.5f)
    drawLine(markColor, Offset(0f, h), Offset(markLength, h), strokeWidth = 1.5f)
    // Bottom-right
    drawLine(markColor, Offset(w - markLength, h), Offset(w, h), strokeWidth = 1.5f)
    drawLine(markColor, Offset(w, h - markLength), Offset(w, h), strokeWidth = 1.5f)
}

/**
 * Tactile Button with solid podlozhka container and spring physics.
 */
@Composable
fun TactileButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    inverted: Boolean = false,
    danger: Boolean = false,
    icon: ImageVector? = null,
    text: String? = null,
    badge: String? = null,
) {
    val bgColor = when {
        !enabled -> Color(0xFF10141A)
        inverted -> Color(0xFFFFFFFF)
        danger -> Color(0xFF2B0E12)
        else -> Color(0xFF141922)
    }
    val contentColor = when {
        !enabled -> Color(0xFF556070)
        inverted -> Color(0xFF000000)
        danger -> Color(0xFFFF5252)
        else -> Color(0xFFFFFFFF)
    }
    val borderColor = when {
        !enabled -> Color(0xFF222B38)
        inverted -> Color(0xFFFFFFFF)
        danger -> Color(0xFF88202A)
        else -> Color(0xFF2D3848)
    }

    Box(
        modifier = modifier
            .tactileClickable(enabled = enabled, onClick = onClick)
            .background(bgColor, CutButtonShape)
            .border(1.dp, borderColor, CutButtonShape)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = text,
                    tint = contentColor,
                    modifier = Modifier.size(17.dp),
                )
                if (text != null) {
                    Spacer(Modifier.width(6.dp))
                }
            }
            if (text != null) {
                Text(
                    text = text,
                    color = contentColor,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = if (inverted) FontWeight.Bold else FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                )
            }
            if (badge != null) {
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .background(if (inverted) Color.Black else Color(0xFF222C3A), CutSmallShape)
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = badge,
                        color = if (inverted) Color.White else Color(0xFFA5B4C7),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
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

// ==========================================
// Custom DedSec Cyber Navigation Vector Icons
// ==========================================

@Composable
fun DedSecTerminalNavIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        // Terminal Prompt ">"
        drawLine(tint, Offset(w * 0.18f, h * 0.28f), Offset(w * 0.48f, h * 0.50f), strokeWidth = 2.2f, cap = StrokeCap.Square)
        drawLine(tint, Offset(w * 0.48f, h * 0.50f), Offset(w * 0.18f, h * 0.72f), strokeWidth = 2.2f, cap = StrokeCap.Square)
        // Cursor Block "_"
        drawLine(tint, Offset(w * 0.56f, h * 0.72f), Offset(w * 0.88f, h * 0.72f), strokeWidth = 2.5f, cap = StrokeCap.Square)
    }
}

@Composable
fun DedSecSearchNavIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val c = Offset(w * 0.46f, h * 0.46f)
        val r = w * 0.32f
        // Reticle circle
        drawCircle(color = tint, radius = r, center = c, style = Stroke(width = 1.8f))
        // Center crosshair
        drawLine(tint, Offset(c.x - r * 0.45f, c.y), Offset(c.x + r * 0.45f, c.y), strokeWidth = 1.8f)
        drawLine(tint, Offset(c.x, c.y - r * 0.45f), Offset(c.x, c.y + r * 0.45f), strokeWidth = 1.8f)
        // Scanner handle
        drawLine(tint, Offset(c.x + r * 0.75f, c.y + r * 0.75f), Offset(w * 0.90f, h * 0.90f), strokeWidth = 2.4f, cap = StrokeCap.Square)
    }
}

@Composable
fun DedSecLibraryNavIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        // Audio cassette / drive frame
        drawRect(
            color = tint,
            topLeft = Offset(w * 0.12f, h * 0.22f),
            size = Size(w * 0.76f, h * 0.56f),
            style = Stroke(width = 1.8f),
        )
        // Dual audio reels
        drawCircle(color = tint, radius = w * 0.08f, center = Offset(w * 0.34f, h * 0.50f))
        drawCircle(color = tint, radius = w * 0.08f, center = Offset(w * 0.66f, h * 0.50f))
        // Center tape bridge
        drawLine(tint, Offset(w * 0.34f, h * 0.64f), Offset(w * 0.66f, h * 0.64f), strokeWidth = 1.5f)
    }
}

@Composable
fun DedSecPlaylistsNavIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        // Stacked queue decks
        drawLine(tint, Offset(w * 0.15f, h * 0.28f), Offset(w * 0.85f, h * 0.28f), strokeWidth = 2.2f, cap = StrokeCap.Square)
        drawLine(tint, Offset(w * 0.15f, h * 0.50f), Offset(w * 0.65f, h * 0.50f), strokeWidth = 2.2f, cap = StrokeCap.Square)
        drawLine(tint, Offset(w * 0.15f, h * 0.72f), Offset(w * 0.85f, h * 0.72f), strokeWidth = 2.2f, cap = StrokeCap.Square)
        // Play indicator triangle on the second line
        drawCircle(color = tint, radius = w * 0.07f, center = Offset(w * 0.80f, h * 0.50f))
    }
}
