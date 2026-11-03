package com.codex.signalflow.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.codex.signalflow.data.TrackRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

@Immutable
data class PlayerUiState(
    val queueLabel: String = "Медиатека",
    val currentTrack: TrackRecord? = null,
    val isPlaying: Boolean = false,
    val isShuffleEnabled: Boolean = false,
    val queueSize: Int = 0,
    val currentIndex: Int = 0,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val progressFraction: Float = 0f,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val canSeek: Boolean = false,
    val audioSessionId: Int = 0,
    val isNotificationVisible: Boolean = false,
)

class PlaybackManager(
    context: Context,
) {
    private val exoPlayer = ExoPlayer.Builder(context).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _playerState = MutableStateFlow(PlayerUiState())
    val playerState: StateFlow<PlayerUiState> = _playerState.asStateFlow()
    private var baseQueue: List<TrackRecord> = emptyList()
    private var playbackQueue: List<TrackRecord> = emptyList()
    private var currentQueueLabel: String = "Медиатека"
    private var shuffleEnabled: Boolean = false
    private var notificationVisible: Boolean = false
    /** Last emitted identity (track/play/shuffle/queue) for cheap equality checks. */
    private var lastIdentityKey: String = ""
    private var lastEmittedPositionMs: Long = -1L

    val player: ExoPlayer get() = exoPlayer

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) &&
                    player.playbackState == Player.STATE_ENDED &&
                    shuffleEnabled &&
                    baseQueue.isNotEmpty()
                ) {
                    rebuildPlaybackQueue(
                        currentTrackId = playbackQueue.lastOrNull()?.id,
                        currentPositionMs = 0L,
                        playWhenReady = false,
                    )
                }
                // Identity-level events force a full publish immediately.
                updateState(force = true)
            }
        })
        scope.launch {
            while (true) {
                updateState(force = false)
                delay(if (exoPlayer.isPlaying) 500L else 1_200L)
            }
        }
    }

    fun playQueue(label: String, tracks: List<TrackRecord>, startTrackId: String, shuffle: Boolean = false) {
        if (tracks.isEmpty()) return
        baseQueue = tracks
        currentQueueLabel = label
        shuffleEnabled = shuffle
        rebuildPlaybackQueue(
            currentTrackId = startTrackId,
            currentPositionMs = 0L,
            playWhenReady = true,
        )
        updateState()
    }

    fun togglePlayPause() {
        if (exoPlayer.playbackState == Player.STATE_ENDED && playbackQueue.isNotEmpty()) {
            rebuildPlaybackQueue(
                currentTrackId = if (shuffleEnabled) null else playbackQueue.firstOrNull()?.id,
                currentPositionMs = 0L,
                playWhenReady = true,
            )
        } else if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
        updateState()
    }

    fun playNext() { exoPlayer.seekToNextMediaItem(); updateState() }
    fun playPrevious() { exoPlayer.seekToPreviousMediaItem(); updateState() }
    fun toggleShuffle() {
        if (baseQueue.isEmpty()) return
        val activeTrackId = exoPlayer.currentMediaItem?.mediaId ?: playbackQueue.getOrNull(exoPlayer.currentMediaItemIndex)?.id
        val activePositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
        val shouldResume = exoPlayer.isPlaying || exoPlayer.playWhenReady
        shuffleEnabled = !shuffleEnabled
        rebuildPlaybackQueue(
            currentTrackId = activeTrackId,
            currentPositionMs = activePositionMs,
            playWhenReady = shouldResume,
        )
        updateState()
    }
    fun seekTo(positionMs: Long) { exoPlayer.seekTo(positionMs.coerceAtLeast(0L)); updateState() }
    fun playPreview(track: TrackRecord) {
        playQueue(
            label = "Предпрослушивание",
            tracks = listOf(track),
            startTrackId = track.id,
            shuffle = false,
        )
    }

    fun removeTrack(trackId: String) {
        if (baseQueue.none { it.id == trackId } && playbackQueue.none { it.id == trackId }) return

        val currentTrackId = exoPlayer.currentMediaItem?.mediaId
        val currentPlaybackIndex = exoPlayer.currentMediaItemIndex.coerceAtLeast(0)
        val playWhenReady = exoPlayer.isPlaying || exoPlayer.playWhenReady
        val nextTrackId = if (currentTrackId == trackId) {
            playbackQueue.getOrNull(currentPlaybackIndex + 1)?.id
                ?: playbackQueue.getOrNull((currentPlaybackIndex - 1).coerceAtLeast(0))?.id
        } else {
            currentTrackId
        }

        baseQueue = baseQueue.filterNot { it.id == trackId }
        if (baseQueue.isEmpty()) {
            playbackQueue = emptyList()
            currentQueueLabel = "Медиатека"
            shuffleEnabled = false
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            updateState()
            return
        }

        rebuildPlaybackQueue(
            currentTrackId = nextTrackId,
            currentPositionMs = if (currentTrackId == trackId) 0L else exoPlayer.currentPosition.coerceAtLeast(0L),
            playWhenReady = playWhenReady,
        )
        updateState()
    }

    fun release() {
        scope.cancel()
        exoPlayer.release()
    }

    fun setNotificationVisible(visible: Boolean) {
        notificationVisible = visible
        updateState()
    }

    private fun updateState(force: Boolean = true) {
        val currentIndex = exoPlayer.currentMediaItemIndex.coerceAtLeast(0)
        val duration = exoPlayer.duration.takeIf { it > 0 } ?: playbackQueue.getOrNull(currentIndex)?.durationMs ?: 0L
        val position = exoPlayer.currentPosition.coerceAtLeast(0L)
        val progressFraction = if (duration > 0L) {
            (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        val track = playbackQueue.getOrNull(currentIndex)
        val isPlaying = exoPlayer.isPlaying
        val identityKey = listOf(
            track?.id.orEmpty(),
            isPlaying.toString(),
            shuffleEnabled.toString(),
            playbackQueue.size.toString(),
            currentIndex.toString(),
            notificationVisible.toString(),
            currentQueueLabel,
        ).joinToString("|")

        val identityChanged = identityKey != lastIdentityKey
        val positionDelta = kotlin.math.abs(position - lastEmittedPositionMs)
        // Skip no-op timeline ticks unless identity changed or position moved enough.
        if (!force && !identityChanged && positionDelta < 400L) {
            return
        }

        lastIdentityKey = identityKey
        lastEmittedPositionMs = position
        _playerState.value = PlayerUiState(
            queueLabel = currentQueueLabel,
            currentTrack = track,
            isPlaying = isPlaying,
            isShuffleEnabled = shuffleEnabled,
            queueSize = playbackQueue.size,
            currentIndex = currentIndex,
            currentPositionMs = position,
            durationMs = duration,
            progressFraction = progressFraction,
            hasPrevious = currentIndex > 0,
            hasNext = currentIndex in 0 until (playbackQueue.lastIndex),
            canSeek = duration > 0L,
            audioSessionId = exoPlayer.audioSessionId,
            isNotificationVisible = notificationVisible,
        )
    }

    private fun applyQueue(
        queue: List<TrackRecord>,
        startIndex: Int,
        startPositionMs: Long,
        playWhenReady: Boolean,
    ) {
        val items = queue.map { track ->
            MediaItem.Builder()
                .setMediaId(track.id)
                .setUri(track.uri)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).setArtist(track.artist).build())
                .build()
        }
        exoPlayer.setMediaItems(items, startIndex.coerceAtLeast(0), startPositionMs.coerceAtLeast(0L))
        exoPlayer.prepare()
        exoPlayer.shuffleModeEnabled = false
        exoPlayer.playWhenReady = playWhenReady
    }

    private fun rebuildPlaybackQueue(
        currentTrackId: String?,
        currentPositionMs: Long,
        playWhenReady: Boolean,
    ) {
        if (baseQueue.isEmpty()) return
        playbackQueue = buildPlaybackQueue(baseQueue, currentTrackId, shuffleEnabled)
        val fallbackTrackId = playbackQueue.firstOrNull()?.id
        val startIndex = playbackQueue.indexOfFirst { it.id == (currentTrackId ?: fallbackTrackId) }.coerceAtLeast(0)
        applyQueue(
            queue = playbackQueue,
            startIndex = startIndex,
            startPositionMs = currentPositionMs,
            playWhenReady = playWhenReady,
        )
    }
}

internal fun buildPlaybackQueue(
    tracks: List<TrackRecord>,
    currentTrackId: String?,
    shuffleEnabled: Boolean,
    random: Random = Random.Default,
): List<TrackRecord> {
    if (tracks.isEmpty()) return emptyList()
    if (!shuffleEnabled) return tracks
    val pinnedTrack = tracks.firstOrNull { it.id == currentTrackId } ?: tracks.random(random)
    val remainder = buildArtistAwareShuffle(
        tracks = tracks.filterNot { it.id == pinnedTrack.id },
        previousArtist = pinnedTrack.artist,
        random = random,
    )
    return listOfNotNull(pinnedTrack) + remainder
}

private fun normalizePrimaryArtist(artist: String): String {
    val clean = artist.trim().lowercase()
    if (clean.isBlank()) return "unknown"
    return clean
        .split(",", ";", "/", "\\", " feat.", " ft.", " feat ", " ft ", " Feat.", " Ft.", " with ", " & ", " vs ", " x ", " X ")
        .firstOrNull()?.trim() ?: clean
}

private fun buildArtistAwareShuffle(
    tracks: List<TrackRecord>,
    previousArtist: String?,
    random: Random,
): List<TrackRecord> {
    if (tracks.isEmpty()) return emptyList()
    if (tracks.size <= 2) return tracks.shuffled(random)

    val normalizedPrevArtist = previousArtist?.let { normalizePrimaryArtist(it) }

    // 1. Group tracks by normalized primary artist and shuffle within artist
    val artistGroups = tracks
        .groupBy { normalizePrimaryArtist(it.artist) }
        .mapValues { (_, group) -> group.shuffled(random) }

    // 2. Assign spaced relative target position (0.0 .. 1.0) to every track of each artist (Spotify balanced shuffle)
    data class ScheduledTrack(val track: TrackRecord, val normalizedArtist: String, val targetPosition: Double)

    val scheduled = mutableListOf<ScheduledTrack>()
    for ((artist, group) in artistGroups) {
        val count = group.size
        val interval = 1.0 / count
        val phaseOffset = random.nextDouble() * interval
        val extraSpacing = if (artist == normalizedPrevArtist) interval * 0.5 else 0.0

        for (i in 0 until count) {
            val basePos = (i * interval + phaseOffset + extraSpacing) % 1.0
            val jitter = (random.nextDouble() - 0.5) * (interval * 0.15)
            val finalPos = (basePos + jitter).coerceIn(0.0, 1.0)
            scheduled.add(ScheduledTrack(group[i], artist, finalPos))
        }
    }

    // 3. Sort by target position
    scheduled.sortBy { it.targetPosition }

    // 4. Secondary pass: Strict anti-clustering swap
    // Guarantee that no two tracks by the same primary artist appear consecutively
    val result = scheduled.map { it.track }.toMutableList()
    var i = 0
    while (i < result.size - 1) {
        val currentArtist = normalizePrimaryArtist(result[i].artist)
        val nextArtist = normalizePrimaryArtist(result[i + 1].artist)

        if (currentArtist == nextArtist) {
            var swapIndex = -1
            for (j in (i + 2) until result.size) {
                val candidateArtist = normalizePrimaryArtist(result[j].artist)
                if (candidateArtist != currentArtist) {
                    swapIndex = j
                    break
                }
            }
            if (swapIndex != -1) {
                val temp = result[i + 1]
                result[i + 1] = result[swapIndex]
                result[swapIndex] = temp
            }
        }
        i++
    }

    return result
}
