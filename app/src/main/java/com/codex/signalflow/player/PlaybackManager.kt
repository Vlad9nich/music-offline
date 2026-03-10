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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

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
)

class PlaybackManager(
    context: Context,
) {
    private val player = ExoPlayer.Builder(context).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _playerState = MutableStateFlow(PlayerUiState())
    val playerState: StateFlow<PlayerUiState> = _playerState.asStateFlow()
    private val audioVisualizer = AudioVisualizerController()
    private var baseQueue: List<TrackRecord> = emptyList()
    private var playbackQueue: List<TrackRecord> = emptyList()
    private var currentQueueLabel: String = "Медиатека"
    private var shuffleEnabled: Boolean = false
    private var visualizerActive: Boolean = false
    private var visualizerPermissionGranted: Boolean = false

    val visualizerState: StateFlow<VisualizerUiState> = audioVisualizer.state

    init {
        player.addListener(object : Player.Listener {
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
                updateState()
            }
        })
        scope.launch {
            while (true) {
                updateState()
                delay(
                    when {
                        visualizerActive -> 220L
                        player.isPlaying -> 900L
                        else -> 1_200L
                    },
                )
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
        if (player.playbackState == Player.STATE_ENDED && playbackQueue.isNotEmpty()) {
            rebuildPlaybackQueue(
                currentTrackId = if (shuffleEnabled) null else playbackQueue.firstOrNull()?.id,
                currentPositionMs = 0L,
                playWhenReady = true,
            )
        } else if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
        updateState()
    }

    fun playNext() { player.seekToNextMediaItem(); updateState() }
    fun playPrevious() { player.seekToPreviousMediaItem(); updateState() }
    fun toggleShuffle() {
        if (baseQueue.isEmpty()) return
        val activeTrackId = player.currentMediaItem?.mediaId ?: playbackQueue.getOrNull(player.currentMediaItemIndex)?.id
        val activePositionMs = player.currentPosition.coerceAtLeast(0L)
        val shouldResume = player.isPlaying || player.playWhenReady
        shuffleEnabled = !shuffleEnabled
        rebuildPlaybackQueue(
            currentTrackId = activeTrackId,
            currentPositionMs = activePositionMs,
            playWhenReady = shouldResume,
        )
        updateState()
    }
    fun seekTo(positionMs: Long) { player.seekTo(positionMs.coerceAtLeast(0L)); updateState() }
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

        val currentTrackId = player.currentMediaItem?.mediaId
        val currentPlaybackIndex = player.currentMediaItemIndex.coerceAtLeast(0)
        val playWhenReady = player.isPlaying || player.playWhenReady
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
            player.stop()
            player.clearMediaItems()
            updateState()
            return
        }

        rebuildPlaybackQueue(
            currentTrackId = nextTrackId,
            currentPositionMs = if (currentTrackId == trackId) 0L else player.currentPosition.coerceAtLeast(0L),
            playWhenReady = playWhenReady,
        )
        updateState()
    }

    fun setVisualizerActive(active: Boolean) {
        visualizerActive = active
        updateState()
    }

    fun setVisualizerPermissionGranted(granted: Boolean) {
        visualizerPermissionGranted = granted
        updateState()
    }

    fun release() {
        scope.cancel()
        audioVisualizer.release()
        player.release()
    }

    private fun updateState() {
        val currentIndex = player.currentMediaItemIndex.coerceAtLeast(0)
        val duration = player.duration.takeIf { it > 0 } ?: playbackQueue.getOrNull(currentIndex)?.durationMs ?: 0L
        val position = player.currentPosition.coerceAtLeast(0L)
        val progressFraction = if (duration > 0L) {
            (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        _playerState.value = PlayerUiState(
            queueLabel = currentQueueLabel,
            currentTrack = playbackQueue.getOrNull(currentIndex),
            isPlaying = player.isPlaying,
            isShuffleEnabled = shuffleEnabled,
            queueSize = playbackQueue.size,
            currentIndex = currentIndex,
            currentPositionMs = position,
            durationMs = duration,
            progressFraction = progressFraction,
            hasPrevious = currentIndex > 0,
            hasNext = currentIndex in 0 until (playbackQueue.lastIndex),
            canSeek = duration > 0L,
            audioSessionId = player.audioSessionId,
        )
        audioVisualizer.update(
            audioSessionId = player.audioSessionId,
            isPlaying = player.isPlaying,
            isActive = visualizerActive,
            permissionGranted = visualizerPermissionGranted,
            progressFraction = progressFraction,
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
        player.setMediaItems(items, startIndex.coerceAtLeast(0), startPositionMs.coerceAtLeast(0L))
        player.prepare()
        player.shuffleModeEnabled = false
        player.playWhenReady = playWhenReady
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
    val remainder = tracks.filterNot { it.id == pinnedTrack?.id }.shuffled(random)
    return listOfNotNull(pinnedTrack) + remainder
}
