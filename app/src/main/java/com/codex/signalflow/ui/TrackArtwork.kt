package com.codex.signalflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.codex.signalflow.R
import com.codex.signalflow.data.TrackRecord
import com.codex.signalflow.source.RemoteTrackCandidate

/**
 * High-performance Track Cover component with Coil AsyncImage,
 * optimized for smooth 120 FPS list scrolling with hardware caching.
 */
@Composable
fun TrackArtwork(
    modifier: Modifier = Modifier,
    track: TrackRecord? = null,
    remoteTrack: RemoteTrackCandidate? = null,
    size: Dp = 48.dp,
    shape: Shape = CutSmallShape,
    showCornerTicks: Boolean = false,
) {
    // Only pass actual image URIs (not raw MP3 file paths)
    val coverUrl = track?.coverUri ?: remoteTrack?.coverUrl
    val title = track?.title ?: remoteTrack?.title.orEmpty()
    val initial = title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "♪"

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(Color(0xFF0C1017))
            .border(1.dp, Color(0xFF263040), shape)
            .then(if (showCornerTicks) Modifier.techCornerMarks() else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (!coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(coverUrl)
                    .size(128, 128)
                    .crossfade(false)
                    .build(),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            DedSecArtworkFallback(initial = initial, size = size)
        }
    }
}

@Composable
private fun DedSecArtworkFallback(
    initial: String,
    size: Dp,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070A0F))
            .scanlineOverlay(),
        contentAlignment = Alignment.Center,
    ) {
        if (size >= 80.dp) {
            AsyncImage(
                model = R.drawable.dedsec_emblem,
                contentDescription = "DedSec Emblem",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = Color(0xFFB0BDD0),
                modifier = Modifier.size((size * 0.48f).coerceAtLeast(16.dp)),
            )
        }
    }
}
