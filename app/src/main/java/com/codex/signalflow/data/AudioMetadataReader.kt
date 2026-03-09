package com.codex.signalflow.data

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

object AudioMetadataReader {
    suspend fun read(
        context: Context,
        playbackUri: String,
        sourceUri: String = playbackUri,
        fallbackTitle: String? = null,
        fallbackArtist: String? = null,
    ): TrackRecord? = withContext(Dispatchers.IO) {
        val parsedUri = playbackUri.toUri()
        val retriever = MediaMetadataRetriever()
        runCatching {
            retriever.setDataSource(context, parsedUri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }
                ?: fallbackTitle?.takeIf { it.isNotBlank() }
                ?: DocumentFile.fromSingleUri(context, parsedUri)?.name?.substringBeforeLast('.')
                ?: parsedUri.path?.let { File(it).nameWithoutExtension }?.takeIf { it.isNotBlank() }
                ?: "Неизвестный трек"
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() }
                ?: fallbackArtist?.takeIf { it.isNotBlank() }
                ?: "Неизвестный исполнитель"
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
            TrackRecord(
                id = UUID.randomUUID().toString(),
                uri = playbackUri,
                sourceUri = sourceUri,
                title = title,
                artist = artist,
                durationMs = durationMs,
                importedAtEpochMs = System.currentTimeMillis(),
            )
        }.getOrNull().also {
            runCatching { retriever.release() }
        }
    }
}
