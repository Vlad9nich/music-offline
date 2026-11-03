package com.codex.signalflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.codex.signalflow.data.TrackRecord
import com.codex.signalflow.source.RemoteTrackCandidate

/**
 * Universal Track Cover component using Coil for online & local artwork,
 * falling back to a sharp DedSec technical emblem when no image is available.
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
    val model = track?.coverUri ?: track?.uri ?: remoteTrack?.downloadUrl
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
        if (model != null) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(model)
                    .crossfade(true)
                    .build(),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    DedSecArtworkFallback(initial = initial, size = size)
                },
                error = {
                    DedSecArtworkFallback(initial = initial, size = size)
                },
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
            .background(Color(0xFF0F141C))
            .scanlineOverlay(lineColor = Color(0x15FFFFFF), stepPx = 4f),
        contentAlignment = Alignment.Center,
    ) {
        if (size >= 80.dp) {
            Text(
                text = initial,
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.35f).sp,
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
