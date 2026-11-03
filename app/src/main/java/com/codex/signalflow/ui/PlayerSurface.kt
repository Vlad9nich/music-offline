package com.codex.signalflow.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.signalflow.player.PlayerUiState

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
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .tactileClickable(onClick = onExpand),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0C1017)),
        shape = CutPanelShape,
        border = BorderStroke(1.dp, Color(0xFF263140)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Real Track Cover Artwork
                TrackArtwork(
                    track = state.currentTrack,
                    size = 42.dp,
                    shape = CutSmallShape,
                )

                // Track Title and Artist
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.currentTrack?.title ?: "Трек не выбран",
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                    )
                    Text(
                        text = state.currentTrack?.artist ?: state.queueLabel,
                        color = Color(0xFF8B97AB),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    )
                }

                // Tactile Playback Controls
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
            .dedsecNoiseBackground(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top Navigation / Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TactileButton(
                    onClick = onDismiss,
                    icon = Icons.Rounded.KeyboardArrowDown,
                    text = "СВЕРНУТЬ",
                )
                Text(
                    text = "YaNeoDex • Плеер",
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 0.5.sp,
                )
                Box(
                    modifier = Modifier
                        .background(Color(0xFF131822), CutSmallShape)
                        .border(1.dp, Color(0xFF283446), CutSmallShape)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = if (state.isPlaying) "В СЕТИ" else "ПАУЗА",
                        color = if (state.isPlaying) Color.White else Color(0xFF909EB0),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Central Cover & Track Details
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Large Track Cover with DedSec Viewfinder Corner Marks
                TrackArtwork(
                    track = state.currentTrack,
                    size = 250.dp,
                    shape = CutPanelShape,
                    showCornerTicks = true,
                )

                // Track Title with Blinking Terminal Block
                Text(
                    text = (state.currentTrack?.title ?: "Трек не выбран") + cursor,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                // Artist & Source Badge
                Text(
                    text = state.currentTrack?.artist ?: state.queueLabel,
                    color = Color(0xFFA2AFC2),
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

            // Large Tactile Playback Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LargePlayerControl(
                    icon = Icons.Rounded.Shuffle,
                    active = state.isShuffleEnabled,
                    enabled = state.queueSize > 1,
                    onClick = onToggleShuffle,
                    size = 52,
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
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember(duration) { mutableStateOf(activeFraction) }

    LaunchedEffect(activeFraction, isDragging) {
        if (!isDragging) dragFraction = activeFraction
    }

    val currentFraction = if (isDragging) dragFraction else activeFraction
    val displayPosition = if (isDragging && duration > 0L) (duration * dragFraction).toLong() else position

    val thumbSize by animateDpAsState(
        targetValue = when {
            isDragging -> if (compact) 10.dp else 15.dp
            compact -> 0.dp
            else -> 9.dp
        },
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 450f),
        label = "scrubber_thumb_size",
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 18.dp else 34.dp)
                .pointerInput(state.canSeek, duration) {
                    if (!state.canSeek || duration <= 0L) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        isDragging = true
                        dragFraction = (down.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.changedToUp()) {
                                change.consume()
                                onSeekTo((duration * dragFraction).toLong())
                                break
                            }
                            dragFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                            change.consume()
                        }
                        isDragging = false
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            val trackHeight = if (compact) 2.dp else 3.dp
            val widthPx = constraints.maxWidth.toFloat()

            // Inactive track groove
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .clip(RoundedCornerShape(trackHeight / 2))
                    .background(Color(0xFF1E2636)),
            )

            // Active track (played progress)
            Box(
                modifier = Modifier
                    .fillMaxWidth(currentFraction)
                    .height(trackHeight)
                    .clip(RoundedCornerShape(trackHeight / 2))
                    .background(Color.White),
            )

            // Sleek user-friendly scrubber thumb
            if (thumbSize > 0.dp) {
                val density = LocalDensity.current
                val thumbPx = with(density) { thumbSize.toPx() }
                val thumbOffset = ((widthPx * currentFraction) - (thumbPx / 2f))
                    .coerceIn(0f, (widthPx - thumbPx).coerceAtLeast(0f))
                Box(
                    modifier = Modifier
                        .offset { IntOffset(thumbOffset.toInt(), 0) }
                        .size(thumbSize)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(
                            width = if (isDragging) 2.dp else 1.5.dp,
                            color = Color(0xFF0C1017),
                            shape = CircleShape,
                        ),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatDuration(displayPosition),
                color = if (isDragging) Color.White else Color(0xFFA0AAB8),
                fontFamily = FontFamily.Monospace,
                fontSize = if (compact) 10.sp else 12.sp,
                fontWeight = if (isDragging) FontWeight.Bold else FontWeight.Normal,
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
        active -> Color(0xFF222938)
        else -> Color(0xFF10141C)
    }
    val iconColor = when {
        primary && enabled -> Color.Black
        active -> Color.White
        !enabled -> Color(0xFF3E4758)
        else -> Color(0xFFD0D7E4)
    }
    val borderColor = when {
        primary -> Color.White
        active -> Color.White
        !enabled -> Color(0xFF1B222E)
        else -> Color(0xFF2E384A)
    }

    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(shape)
            .background(bgColor)
            .border(1.dp, borderColor, shape)
            .tactileClickable(enabled = enabled, onClick = onClick),
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
