package com.codex.signalflow.player

import android.content.Context
import com.codex.signalflow.data.TrackRecord

class PlaybackController(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val playbackManager = PlaybackRuntime.getManager(appContext)

    val playerState = playbackManager.playerState
    val visualizerState = playbackManager.visualizerState

    fun playQueue(label: String, tracks: List<TrackRecord>, startTrackId: String, shuffle: Boolean = false) {
        playbackManager.playQueue(label, tracks, startTrackId, shuffle)
        MediaPlaybackService.ensureStarted(appContext)
    }

    fun togglePlayPause() {
        playbackManager.togglePlayPause()
        if (playbackManager.playerState.value.currentTrack != null) MediaPlaybackService.ensureStarted(appContext)
    }

    fun playNext() {
        playbackManager.playNext()
        MediaPlaybackService.ensureStarted(appContext)
    }

    fun playPrevious() {
        playbackManager.playPrevious()
        MediaPlaybackService.ensureStarted(appContext)
    }

    fun toggleShuffle() {
        playbackManager.toggleShuffle()
        if (playbackManager.playerState.value.currentTrack != null) MediaPlaybackService.ensureStarted(appContext)
    }

    fun seekTo(positionMs: Long) {
        playbackManager.seekTo(positionMs)
    }

    fun playPreview(track: TrackRecord) {
        playbackManager.playPreview(track)
        MediaPlaybackService.ensureStarted(appContext)
    }

    fun removeTrack(trackId: String) {
        playbackManager.removeTrack(trackId)
    }

    fun setVisualizerActive(active: Boolean) {
        playbackManager.setVisualizerActive(active)
    }

    fun setVisualizerPermissionGranted(granted: Boolean) {
        playbackManager.setVisualizerPermissionGranted(granted)
    }
}
