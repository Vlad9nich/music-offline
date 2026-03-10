package com.codex.signalflow.player

import android.content.Context

object PlaybackRuntime {
    @Volatile
    private var playbackManager: PlaybackManager? = null

    fun getManager(context: Context): PlaybackManager {
        val existing = playbackManager
        if (existing != null) return existing

        return synchronized(this) {
            playbackManager ?: PlaybackManager(context.applicationContext).also {
                playbackManager = it
            }
        }
    }
}
