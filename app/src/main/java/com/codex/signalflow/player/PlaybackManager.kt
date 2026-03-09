package com.codex.signalflow.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.codex.signalflow.data.TrackRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlayerUiState(
    val queueLabel: String = "Медиатека",
    val currentTrack: TrackRecord? = null,
    val isPlaying: Boolean = false,
    val isShuffleEnabled: Boolean = false,
    val queueSize: Int = 0,
    val currentIndex: Int = 0,
)

class PlaybackManager(
    context: Context,
) {
    private val player = ExoPlayer.Builder(context).build()
    private val _playerState = MutableStateFlow(PlayerUiState())
    val playerState: StateFlow<PlayerUiState> = _playerState.asStateFlow()
    private var currentQueue: List<TrackRecord> = emptyList()
    private var currentQueueLabel: String = "Медиатека"

    init {
        player.addListener(
            object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    updateState()
                }
            },
        )
    }

    fun playQueue(
        label: String,
        tracks: List<TrackRecord>,
        startTrackId: String,
        shuffle: Boolean = false,
    ) {
        if (tracks.isEmpty()) return
        currentQueue = tracks
        currentQueueLabel = label
        val startIndex = tracks.indexOfFirst { it.id == startTrackId }.coerceAtLeast(0)
        val items = tracks.map { track ->
            MediaItem.Builder()
                .setMediaId(track.id)
                .setUri(track.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .build(),
                )
                .build()
        }
        player.setMediaItems(items, startIndex, 0L)
        player.prepare()
        player.shuffleModeEnabled = shuffle
        player.playWhenReady = true
        updateState()
    }

    fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
        updateState()
    }

    fun playNext() {
        player.seekToNextMediaItem()
        updateState()
    }

    fun playPrevious() {
        player.seekToPreviousMediaItem()
        updateState()
    }

    fun toggleShuffle() {
        player.shuffleModeEnabled = !player.shuffleModeEnabled
        updateState()
    }

    fun release() {
        player.release()
    }

    private fun updateState() {
        val currentIndex = player.currentMediaItemIndex.coerceAtLeast(0)
        _playerState.value = PlayerUiState(
            queueLabel = currentQueueLabel,
            currentTrack = currentQueue.getOrNull(currentIndex),
            isPlaying = player.isPlaying,
            isShuffleEnabled = player.shuffleModeEnabled,
            queueSize = currentQueue.size,
            currentIndex = currentIndex,
        )
    }
}
