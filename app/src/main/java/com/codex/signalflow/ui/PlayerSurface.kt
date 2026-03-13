package com.codex.signalflow.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codex.signalflow.player.PlayerUiState
import com.codex.signalflow.player.VisualizerUiState

@Composable
internal fun PlayerDock(
    state: PlayerUiState,
    onTogglePlayPause: () -> Unit,
    onPlayPrevious: () -> Unit,
    onPlayNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onExpand: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onExpand),
        colors = CardDefaults.cardColors(containerColor = PanelFillAlt),
        shape = PanelShape,
        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.currentTrack?.title ?: "Линия ожидания",
                        color = InkText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = state.currentTrack?.artist ?: state.queueLabel,
                        color = MutedText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    MiniControl(
                        icon = Icons.Rounded.Shuffle,
                        active = state.isShuffleEnabled,
                        enabled = state.queueSize > 1,
                        onClick = onToggleShuffle,
                    )
                    MiniControl(
                        icon = Icons.Rounded.FastRewind,
                        active = state.hasPrevious,
                        enabled = state.hasPrevious,
                        onClick = onPlayPrevious,
                    )
                    MiniControl(
                        icon = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        active = true,
                        onClick = onTogglePlayPause,
                    )
                    MiniControl(
                        icon = Icons.Rounded.FastForward,
                        active = state.hasNext,
                        enabled = state.hasNext,
                        onClick = onPlayNext,
                    )
                }
            }
            PlaybackTimeline(
                state = state,
                onSeekTo = onSeekTo,
                compact = true,
            )
        }
    }
}

@Composable
internal fun ExpandedPlayerOverlay(
    state: PlayerUiState,
    visualizerState: VisualizerUiState,
    onDismiss: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPlayPrevious: () -> Unit,
    onPlayNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onSeekTo: (Long) -> Unit,
) {
    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF03060D), Color(0xFF07111B), Color(0xFF02040A)),
                ),
            ),
    ) {
        GlowOrb(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .align(Alignment.TopCenter),
            color = NeonPink.copy(alpha = 0.18f),
            secondaryColor = NeonCyan.copy(alpha = 0.12f),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Плеер",
                        color = NeonCyan,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = state.queueLabel,
                        color = MutedText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                MiniControl(icon = Icons.Rounded.KeyboardArrowDown, active = true, onClick = onDismiss)
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(0.2f))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                CyberVisualizer(
                    modifier = Modifier.size(width = 300.dp, height = 220.dp),
                    bars = visualizerState.bars,
                    isPlaying = state.isPlaying,
                    isUsingFallback = visualizerState.isUsingFallback,
                    peakIntensity = visualizerState.peakIntensity,
                    bassIntensity = visualizerState.bassIntensity,
                    glowPhase = visualizerState.glowPhase,
                )
                Text(
                    text = state.currentTrack?.title ?: "Линия ожидания",
                    color = InkText,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = state.currentTrack?.artist ?: state.queueLabel,
                    color = MutedText,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                PlaybackTimeline(
                    state = state,
                    onSeekTo = onSeekTo,
                    compact = false,
                )
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(0.3f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 48.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiniControl(
                    icon = Icons.Rounded.Shuffle,
                    active = state.isShuffleEnabled,
                    enabled = state.queueSize > 1,
                    onClick = onToggleShuffle,
                )
                LargePlayerControl(icon = Icons.Rounded.FastRewind, enabled = state.hasPrevious, onClick = onPlayPrevious)
                LargePlayerControl(
                    icon = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    onClick = onTogglePlayPause,
                    primary = true,
                )
                LargePlayerControl(icon = Icons.Rounded.FastForward, enabled = state.hasNext, onClick = onPlayNext)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun PlaybackTimeline(
    state: PlayerUiState,
    onSeekTo: (Long) -> Unit,
    compact: Boolean,
) {
    val duration = state.durationMs.coerceAtLeast(0L)
    val position = state.currentPositionMs.coerceIn(0L, duration.takeIf { it > 0L } ?: 0L)
    val activeFraction = state.progressFraction.takeIf { it > 0f } ?: if (duration > 0L) {
        position.toFloat() / duration.toFloat()
    } else {
        0f
    }
    val interactionSource = remember { MutableInteractionSource() }
    val isDragging by interactionSource.collectIsDraggedAsState()
    var sliderValue by remember(duration) { mutableStateOf(activeFraction) }

    LaunchedEffect(activeFraction, isDragging) {
        if (!isDragging) sliderValue = activeFraction
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp),
    ) {
        Slider(
            value = if (isDragging) sliderValue else activeFraction,
            onValueChange = { fraction -> sliderValue = fraction },
            onValueChangeFinished = {
                if (duration > 0L) onSeekTo((duration * sliderValue).toLong())
            },
            valueRange = 0f..1f,
            enabled = state.canSeek,
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 18.dp else 24.dp),
            thumb = {
                if (!compact || isDragging) {
                    SliderDefaults.Thumb(
                        interactionSource = interactionSource,
                        colors = SliderDefaults.colors(thumbColor = NeonPink),
                    )
                }
            },
            colors = SliderDefaults.colors(
                thumbColor = NeonPink,
                activeTrackColor = NeonCyan,
                inactiveTrackColor = NeonCyan.copy(alpha = 0.16f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatDuration(position),
                color = MutedText,
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
            )
            Text(
                text = formatDuration(duration),
                color = MutedText,
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
internal fun CyberVisualizer(
    modifier: Modifier = Modifier,
    bars: List<Float>,
    isPlaying: Boolean,
    isUsingFallback: Boolean,
    peakIntensity: Float,
    bassIntensity: Float,
    glowPhase: Float,
) {
    val accent = if (isUsingFallback) Color(0xFFFF7AA7) else Color(0xFFFF5C84)
    val highlight = if (isUsingFallback) Color(0xFFFFD6E5) else Color(0xFFFFF3F7)
    val frameGlow = if (isUsingFallback) NeonPink else NeonCyan
    val normalizedBars = if (bars.isEmpty()) List(VisualizerUiState.BAR_COUNT) { 0.16f } else bars
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(36.dp))
            .background(PanelFillAlt)
            .border(1.dp, frameGlow.copy(alpha = 0.32f), RoundedCornerShape(36.dp)),
        contentAlignment = Alignment.Center,
    ) {
        GlowOrb(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            color = accent.copy(alpha = if (isPlaying) 0.2f + peakIntensity * 0.16f else 0.12f),
            secondaryColor = NeonCyan.copy(alpha = 0.14f),
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .padding(horizontal = 18.dp, vertical = 18.dp),
        ) {
            val width = size.width
            val height = size.height
            val centerY = height / 2f
            val leftEdge = width * 0.06f
            val usableWidth = width * 0.88f
            val step = usableWidth / normalizedBars.size.coerceAtLeast(1)
            val barWidth = (step * 0.68f).coerceAtLeast(6f)
            val waveformPath = Path()
            val mirroredPath = Path()
            val centerX = width / 2f
            val fanfareReach = width * (0.18f + peakIntensity * 0.2f)
            val pulseAlpha = (0.22f + peakIntensity * 0.48f).coerceIn(0f, 0.8f)

            drawLine(
                color = NeonCyan.copy(alpha = 0.16f),
                start = Offset(leftEdge, centerY),
                end = Offset(leftEdge + usableWidth, centerY),
                strokeWidth = 2f,
                cap = StrokeCap.Round,
            )

            repeat(4) { streakIndex ->
                val phaseOffset = ((glowPhase + streakIndex * 0.12f) % 1f)
                val streakShift = (phaseOffset - 0.5f) * 42f
                val streakAlpha = (pulseAlpha - streakIndex * 0.08f).coerceAtLeast(0.06f)
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            highlight.copy(alpha = streakAlpha),
                            accent.copy(alpha = streakAlpha * 0.86f),
                            Color.Transparent,
                        ),
                    ),
                    start = Offset(centerX - fanfareReach, centerY - 34f - streakIndex * 10f + streakShift),
                    end = Offset(centerX + fanfareReach, centerY - 4f - streakIndex * 6f - streakShift),
                    strokeWidth = 3.2f - streakIndex * 0.4f,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            accent.copy(alpha = streakAlpha * 0.76f),
                            highlight.copy(alpha = streakAlpha),
                            Color.Transparent,
                        ),
                    ),
                    start = Offset(centerX - fanfareReach, centerY + 34f + streakIndex * 10f - streakShift),
                    end = Offset(centerX + fanfareReach, centerY + 4f + streakIndex * 6f + streakShift),
                    strokeWidth = 3.2f - streakIndex * 0.4f,
                    cap = StrokeCap.Round,
                )
            }

            normalizedBars.forEachIndexed { index, sample ->
                val amplitude = sample.coerceIn(0.08f, 1f)
                val x = leftEdge + index * step + (step - barWidth) / 2f
                val distanceFromCenter = kotlin.math.abs(index - (normalizedBars.lastIndex / 2f)) / (normalizedBars.size / 2f).coerceAtLeast(1f)
                val envelope = 1f - distanceFromCenter * 0.42f
                val reactiveBoost = 1f + bassIntensity * 0.34f + peakIntensity * 0.22f
                val barHeight = (height * 0.36f * amplitude * envelope.coerceAtLeast(0.58f) * reactiveBoost).coerceAtLeast(10f)
                val glowHeight = barHeight * (if (isPlaying) 1.18f else 1.02f) * (1f + peakIntensity * 0.26f)

                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            accent.copy(alpha = 0.22f),
                            Color(0xFFFF344E).copy(alpha = 0.14f),
                        ),
                    ),
                    topLeft = Offset(x, centerY - glowHeight),
                    size = Size(barWidth, glowHeight * 2f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth, barWidth),
                )
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFF3047),
                            accent,
                            highlight,
                            accent,
                            Color(0xFFFF3047),
                        ),
                    ),
                    topLeft = Offset(x, centerY - barHeight),
                    size = Size(barWidth, barHeight * 2f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth, barWidth),
                )

                val anchorX = x + barWidth / 2f
                val topY = centerY - barHeight * 0.58f
                val bottomY = centerY + barHeight * 0.58f
                if (index == 0) {
                    waveformPath.moveTo(anchorX, topY)
                    mirroredPath.moveTo(anchorX, bottomY)
                } else {
                    waveformPath.lineTo(anchorX, topY)
                    mirroredPath.lineTo(anchorX, bottomY)
                }
            }

            drawPath(
                path = waveformPath,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0x44FF5C84),
                        highlight,
                        Color(0x66FF5C84),
                    ),
                ),
                style = Stroke(width = 3f, cap = StrokeCap.Round),
            )
            drawPath(
                path = mirroredPath,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0x33FF5C84),
                        accent.copy(alpha = 0.84f),
                        Color(0x22FF5C84),
                    ),
                ),
                style = Stroke(width = 2f, cap = StrokeCap.Round),
            )

            val haloRadius = if (isPlaying) height * 0.18f else height * 0.12f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        highlight.copy(alpha = 0.24f + peakIntensity * 0.28f),
                        accent.copy(alpha = 0.14f + bassIntensity * 0.18f),
                        Color.Transparent,
                    ),
                    center = Offset(width / 2f, centerY),
                    radius = haloRadius * (1f + peakIntensity * 0.34f),
                ),
                radius = haloRadius * (1f + peakIntensity * 0.34f),
                center = Offset(width / 2f, centerY),
            )
        }
        Text(
            text = if (isUsingFallback) "Локальный импульс" else "Спектр потока",
            color = if (isUsingFallback) MutedText else highlight,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp),
        )
    }
}

@Composable
internal fun LargePlayerControl(
    icon: ImageVector,
    onClick: () -> Unit,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .size(if (primary) 92.dp else 76.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(if (primary && enabled) NeonPink.copy(alpha = 0.18f) else PanelFillAlt)
            .border(
                1.dp,
                if (!enabled) Color(0xFF1B2433) else if (primary) NeonPink.copy(alpha = 0.72f) else NeonCyan.copy(alpha = 0.32f),
                RoundedCornerShape(26.dp),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (!enabled) MutedText.copy(alpha = 0.46f) else if (primary) InkText else NeonCyan,
            modifier = Modifier.size(if (primary) 38.dp else 30.dp),
        )
    }
}
