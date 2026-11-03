package com.codex.signalflow.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class TrackRecord(
    val id: String,
    val uri: String,
    val sourceUri: String = uri,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val importedAtEpochMs: Long,
    val coverUri: String? = null,
)

@Immutable
@Serializable
data class PlaylistRecord(
    val id: String,
    val name: String,
    val trackIds: List<String>,
    val createdAtEpochMs: Long,
)

@Immutable
@Serializable
data class LibrarySnapshot(
    val tracks: List<TrackRecord> = emptyList(),
    val playlists: List<PlaylistRecord> = emptyList(),
)
