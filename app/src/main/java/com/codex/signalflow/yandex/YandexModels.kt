package com.codex.signalflow.yandex

import kotlinx.serialization.Serializable

@Serializable
data class YandexAccountInfo(
    val uid: Long,
    val login: String,
    val displayName: String = "",
)

@Serializable
data class YandexTrackMeta(
    val id: String,
    val albumId: String? = null,
    val title: String,
    val artist: String,
    val albumTitle: String? = null,
    val durationMs: Long = 0L,
    val coverUrl: String? = null,
)

data class YandexSyncProgress(
    val isRunning: Boolean = false,
    val current: Int = 0,
    val total: Int = 0,
    val currentTrackTitle: String = "",
    val message: String = "",
    val error: String? = null,
)
