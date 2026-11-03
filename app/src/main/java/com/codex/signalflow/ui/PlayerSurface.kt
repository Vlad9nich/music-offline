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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CutCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .glitchClickable(onClick = onExpand),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0C10)),
        shape = CutPanelShape,
        border = BorderStroke(1.dp, Color(0xFF2E3544)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Status indicator mark
                Text(
                    text = if (state.isPlaying) "▶ " else "■ ",
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.currentTrack?.title ?: "STANDBY // NO_TRACK",
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                    )
                    Text(
                        text = state.currentTrack?.artist ?: state.queueLabel,
                        color = Color(0xFF8A93A6),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
                        primary = true,
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
    val cursor = rememberBlinkingCursor()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF040507))
            .scanlineOverlay(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top Navigation / Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "[ DEDSEC_AUDIO ]",
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 1.sp,
                    )
                    Text(
                        text = if (state.isPlaying) "ONLINE" else "PAUSED",
                        color = Color(0xFFA0AAB8),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    )
                }
                MiniControl(icon = Icons.Rounded.KeyboardArrowDown, active = false, onClick = onDismiss)
            }

            Spacer(modifier = Modifier.weight(0.1f))

            // Central Visualizer & Track Info
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CyberVisualizer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    bars = visualizerState.bars,
                    isPlaying = state.isPlaying,
                    isUsingFallback = visualizerState.isUsingFallback,
                    peakIntensity = visualizerState.peakIntensity,
                    bassIntensity = visualizerState.bassIntensity,
                    glowPhase = visualizerState.glowPhase,
                )

                // Track Title with Terminal Blinking Cursor
                Text(
                    text = (state.currentTrack?.title ?: "NO TRACK LOADED") + cursor,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                // Artist & Queue
                Text(
                    text = "// " + (state.currentTrack?.artist ?: state.queueLabel).uppercase(),
                    color = Color(0xFF8A93A6),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                PlaybackTimeline(
                    state = state,
                    onSeekTo = onSeekTo,
                    compact = false,
                )
            }

            Spacer(modifier = Modifier.weight(0.2f))

            // Large Controls Bar (Cut Corners, Black & White Inverted)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LargePlayerControl(
                    icon = Icons.Rounded.Shuffle,
                    active = state.isShuffleEnabled,
                    enabled = state.queueSize > 1,
                    onClick = onToggleShuffle,
                    size = 50,
                )
                LargePlayerControl(
                    icon = Icons.Rounded.FastRewind,
                    enabled = state.hasPrevious,
                    onClick = onPlayPrevious,
                    size = 58,
                )
                LargePlayerControl(
                    icon = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    onClick = onTogglePlayPause,
                    primary = true,
                    size = 72,
                )
                LargePlayerControl(
                    icon = Icons.Rounded.FastForward,
                    enabled = state.hasNext,
                    onClick = onPlayNext,
                    size = 58,
                )
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
        verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp),
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
                .height(if (compact) 16.dp else 22.dp),
            thumb = {
                if (!compact || isDragging) {
                    Box(
                        modifier = Modifier
                            .size(width = 6.dp, height = 14.dp)
                            .background(Color.White),
                    )
                }
            },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color(0xFF222834),
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatDuration(position),
                color = Color(0xFFA0AAB8),
                fontFamily = FontFamily.Monospace,
                fontSize = if (compact) 10.sp else 12.sp,
            )
            Text(
                text = formatDuration(duration),
                color = Color(0xFFA0AAB8),
                fontFamily = FontFamily.Monospace,
                fontSize = if (compact) 10.sp else 12.sp,
            )
        }
    }
}

/**
 * High-contrast digital equalizer bars with Watch Dogs 2 terminal aesthetic.
 */
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
    val normalizedBars = if (bars.isEmpty()) List(VisualizerUiState.BAR_COUNT) { 0.14f } else bars

    Box(
        modifier = modifier
            .clip(CutCornerShape(8.dp))
            .background(Color(0xFF090B0F))
            .border(1.dp, Color(0xFF252B37), CutCornerShape(8.dp))
            .techCornerMarks(),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 14.dp),
        ) {
            val width = size.width
            val height = size.height
            val centerY = height / 2f
            val count = normalizedBars.size.coerceAtLeast(1)
            val step = width / count
            val barWidth = (step * 0.72f).coerceAtLeast(3f)

            // Center zero-baseline
            drawLine(
                color = Color(0x33FFFFFF),
                start = Offset(0f, centerY),
                end = Offset(width, centerY),
                strokeWidth = 1f,
                cap = StrokeCap.Square,
            )

            normalizedBars.forEachIndexed { index, sample ->
                val amp = sample.coerceIn(0.06f, 1f)
                val reactiveBoost = if (isPlaying) (1f + bassIntensity * 0.25f + peakIntensity * 0.2f) else 0.5f
                val barH = (height * 0.44f * amp * reactiveBoost).coerceAtLeast(3f)
                val x = index * step + (step - barWidth) / 2f

                // Draw digital segments (3px blocks with 1.5px gaps)
                val segments = (barH / 5f).toInt().coerceAtLeast(1)
                for (s in 0 until segments) {
                    val segYTop = centerY - (s + 1) * 5f
                    val segYBottom = centerY + s * 5f

                    val color = when {
                        s > segments - 2 && isPlaying -> Color.White
                        s > segments - 5 -> Color(0xFFD6DBE5)
                        else -> Color(0xFF707A8C)
                    }

                    // Upper bar
                    drawRect(
                        color = color,
                        topLeft = Offset(x, segYTop),
                        size = Size(barWidth, 3.5f),
                    )
                    // Lower mirrored bar
                    drawRect(
                        color = color.copy(alpha = 0.65f),
                        topLeft = Offset(x, segYBottom),
                        size = Size(barWidth, 3.5f),
                    )
                }
            }
        }

        // Subtitle indicator
        Text(
            text = if (isPlaying) "SPECTRUM // ACTIVE" else "SPECTRUM // IDLE",
            color = Color(0xFF5A6273),
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            letterSpacing = 1.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp),
        )
    }
}

@Composable
internal fun LargePlayerControl(
    icon: ImageVector,
    onClick: () -> Unit,
    primary: Boolean = false,
    active: Boolean = false,
    enabled: Boolean = true,
    size: Int = 64,
) {
    val shape = CutCornerShape((size / 5).dp)
    val bgColor = when {
        primary && enabled -> Color.White
        active -> Color(0xFF222836)
        else -> Color(0xFF0F1218)
    }
    val iconColor = when {
        primary && enabled -> Color.Black
        active -> Color.White
        !enabled -> Color(0xFF3E4656)
        else -> Color(0xFFD0D6E2)
    }
    val borderColor = when {
        primary -> Color.White
        active -> Color.White
        !enabled -> Color(0xFF1C212B)
        else -> Color(0xFF2E3646)
    }

    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(shape)
            .background(bgColor)
            .border(1.dp, borderColor, shape)
            .glitchClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size((size * 0.44f).dp),
        )
    }
}
