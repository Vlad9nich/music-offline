package com.codex.signalflow.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.codex.signalflow.source.DownloadBlueprint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class LibraryRepository(
    private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    private val storageFile = File(context.filesDir, "yaneodex_library.json")
    private val _snapshot = MutableStateFlow(LibrarySnapshot())
    val snapshot: StateFlow<LibrarySnapshot> = _snapshot.asStateFlow()

    init {
        scope.launch {
            _snapshot.value = readSnapshot()
        }
    }

    suspend fun importTracks(uris: List<Uri>): List<TrackRecord> {
        val existingBySource = snapshot.value.tracks.associateBy { it.sourceUri }
        val resolved = mutableListOf<TrackRecord>()
        val created = mutableListOf<TrackRecord>()

        uris.forEach { uri ->
            val sourceUri = uri.toString()
            val existing = existingBySource[sourceUri]
            if (existing != null) {
                resolved += existing
            } else {
                val sourceName = DocumentFile.fromSingleUri(context, uri)?.name.orEmpty()
                val fallbackTitle = sourceName.substringBeforeLast('.').takeIf { it.isNotBlank() }
                val extension = sourceName.substringAfterLast('.', missingDelimiterValue = "")
                    .takeIf { it.isNotBlank() }
                    ?.lowercase()
                    ?: "mp3"
                val mimeType = DocumentFile.fromSingleUri(context, uri)?.type ?: guessMimeType(extension)
                val persistedUri = copyAudioToDeviceStorage(
                    sourceUri = uri,
                    displayName = buildDisplayName(
                        title = fallbackTitle ?: "track-${UUID.randomUUID()}",
                        artist = null,
                        extension = extension,
                    ),
                    mimeType = mimeType,
                ) ?: return@forEach
                val track = AudioMetadataReader.read(
                    context = context,
                    playbackUri = persistedUri,
                    sourceUri = sourceUri,
                    fallbackTitle = fallbackTitle,
                ) ?: return@forEach
                created += track
                resolved += track
            }
        }

        if (created.isNotEmpty()) {
            mutate { current ->
                val currentSourceUris = current.tracks.mapTo(mutableSetOf()) { it.sourceUri }
                current.copy(
                    tracks = current.tracks + created.filter { it.sourceUri !in currentSourceUris },
                )
            }
        }

        return resolved
    }

    suspend fun importDownloadedTrack(blueprint: DownloadBlueprint): TrackRecord {
        val existing = snapshot.value.tracks.firstOrNull { it.sourceUri == blueprint.url }
        if (existing != null) {
            return existing
        }

        val persistedUri = downloadAudioToDeviceStorage(blueprint)
        val track = AudioMetadataReader.read(
            context = context,
            playbackUri = persistedUri,
            sourceUri = blueprint.url,
            fallbackTitle = blueprint.title,
            fallbackArtist = blueprint.artist,
        ) ?: error("Файл скачан, но Android не распознал его как аудио.")

        mutate { current ->
            if (current.tracks.any { it.sourceUri == blueprint.url }) {
                current
            } else {
                current.copy(tracks = current.tracks + track)
            }
        }

        return track
    }

    suspend fun createPlaylist(name: String): LibrarySnapshot = mutate { current ->
        val clean = name.trim()
        if (clean.isBlank()) {
            current
        } else {
            current.copy(
                playlists = current.playlists + PlaylistRecord(
                    id = UUID.randomUUID().toString(),
                    name = clean,
                    trackIds = emptyList(),
                    createdAtEpochMs = System.currentTimeMillis(),
                ),
            )
        }
    }

    suspend fun addTrackToPlaylist(trackId: String, playlistId: String): LibrarySnapshot = mutate { current ->
        current.copy(
            playlists = current.playlists.map { playlist ->
                if (playlist.id != playlistId || trackId in playlist.trackIds) {
                    playlist
                } else {
                    playlist.copy(trackIds = playlist.trackIds + trackId)
                }
            },
        )
    }

    suspend fun removeTrackFromPlaylist(trackId: String, playlistId: String): LibrarySnapshot = mutate { current ->
        current.copy(
            playlists = current.playlists.map { playlist ->
                if (playlist.id != playlistId) {
                    playlist
                } else {
                    playlist.copy(trackIds = playlist.trackIds.filterNot { it == trackId })
                }
            },
        )
    }

    private suspend fun mutate(transform: (LibrarySnapshot) -> LibrarySnapshot): LibrarySnapshot = mutex.withLock {
        val base = if (_snapshot.value == LibrarySnapshot() && storageFile.exists()) {
            readSnapshot()
        } else {
            _snapshot.value
        }
        val updated = transform(base)
        storageFile.parentFile?.mkdirs()
        storageFile.writeText(json.encodeToString(LibrarySnapshot.serializer(), updated))
        _snapshot.value = updated
        updated
    }

    private fun readSnapshot(): LibrarySnapshot {
        if (!storageFile.exists()) {
            return LibrarySnapshot()
        }
        return runCatching {
            json.decodeFromString(LibrarySnapshot.serializer(), storageFile.readText())
        }.getOrDefault(LibrarySnapshot())
    }

    private fun copyAudioToDeviceStorage(
        sourceUri: Uri,
        displayName: String,
        mimeType: String,
    ): String? {
        val resolver = context.contentResolver
        val targetUri = createMediaStoreAudioUri(displayName = displayName, mimeType = mimeType) ?: return null

        return runCatching {
            resolver.openInputStream(sourceUri)?.use { input ->
                resolver.openOutputStream(targetUri)?.use { output ->
                    input.copyTo(output)
                } ?: error("Не удалось открыть поток записи")
            } ?: error("Не удалось открыть поток чтения")
            finalizeMediaStoreAudio(targetUri)
            targetUri.toString()
        }.getOrElse {
            resolver.delete(targetUri, null, null)
            null
        }
    }

    private suspend fun downloadAudioToDeviceStorage(blueprint: DownloadBlueprint): String {
        val extension = guessAudioExtension(
            suggestedFilename = blueprint.suggestedFilename,
            contentType = null,
        )
        val mimeType = guessMimeType(extension.removePrefix("."))
        val displayName = buildDisplayName(
            title = blueprint.title,
            artist = blueprint.artist,
            extension = extension.removePrefix("."),
        )
        val resolver = context.contentResolver
        val targetUri = createMediaStoreAudioUri(displayName = displayName, mimeType = mimeType)
            ?: error("Не удалось создать файл в памяти устройства.")

        return try {
            val connection = openDownloadConnectionWithRetry(blueprint)
            try {
                connection.inputStream.use { input ->
                    resolver.openOutputStream(targetUri)?.use { output ->
                        input.copyTo(output)
                    } ?: error("Не удалось открыть поток записи")
                }
            } finally {
                connection.disconnect()
            }
            finalizeMediaStoreAudio(targetUri)
            targetUri.toString()
        } catch (error: Throwable) {
            resolver.delete(targetUri, null, null)
            throw error
        }
    }

    private suspend fun openDownloadConnectionWithRetry(blueprint: DownloadBlueprint): HttpURLConnection {
        val delaysMs = listOf(0L, 1_500L, 3_000L, 5_000L)
        var lastError: Throwable? = null

        for ((index, delayMs) in delaysMs.withIndex()) {
            if (delayMs > 0) {
                delay(delayMs)
            }
            try {
                return openDownloadConnectionAttempt(blueprint)
            } catch (error: Throwable) {
                lastError = error
                if (index == delaysMs.lastIndex) {
                    throw error
                }
            }
        }

        throw lastError ?: error("Не удалось скачать трек.")
    }

    private fun openDownloadConnectionAttempt(blueprint: DownloadBlueprint): HttpURLConnection {
        var currentUrl = blueprint.url
        var currentReferer = blueprint.refererUrl
        val cookies = linkedMapOf<String, String>()

        repeat(8) {
            val connection = createConnection(
                url = currentUrl,
                referer = currentReferer,
                cookies = cookies,
            )
            captureResponseCookies(connection, cookies)

            when (val responseCode = connection.responseCode) {
                in 200..299 -> {
                    val contentType = connection.contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
                    val contentDisposition = connection.getHeaderField("Content-Disposition").orEmpty().lowercase()
                    val finalUrl = connection.url?.toString().orEmpty().ifBlank { currentUrl }
                    val looksDownloadable = looksLikeDownloadResponse(
                        url = finalUrl,
                        contentType = contentType,
                        contentDisposition = contentDisposition,
                    )

                    if (looksDownloadable) {
                        return connection
                    }

                    val bodyPreview = readBodyPreview(connection.inputStream)
                    val extractedUrl = extractMediaUrl(bodyPreview, finalUrl)
                    connection.disconnect()

                    if (!extractedUrl.isNullOrBlank() && extractedUrl != finalUrl) {
                        currentReferer = finalUrl
                        currentUrl = extractedUrl
                    } else {
                        val readableType = contentType.ifBlank { "неизвестный ответ" }
                        val suffix = bodyPreview.takeIf { it.isNotBlank() }?.let { ": ${it.take(140)}" }.orEmpty()
                        error("Сервер вернул $readableType вместо аудиофайла$suffix")
                    }
                }

                in 300..399 -> {
                    val location = connection.getHeaderField("Location")
                        ?: run {
                            connection.disconnect()
                            error("HTTP $responseCode без адреса перенаправления.")
                        }
                    val redirectUrl = resolveUrl(currentUrl, location)
                    connection.disconnect()
                    currentReferer = currentUrl
                    currentUrl = redirectUrl
                }

                else -> {
                    val errorPreview = readBodyPreview(connection.errorStream)
                    connection.disconnect()
                    if (errorPreview.isBlank()) {
                        error("HTTP $responseCode")
                    } else {
                        error("HTTP $responseCode: ${errorPreview.take(140)}")
                    }
                }
            }
        }

        error("Слишком много перенаправлений при скачивании.")
    }

    private fun createConnection(
        url: String,
        referer: String?,
        cookies: Map<String, String>,
    ): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 45_000
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13; YaNeoDex) AppleWebKit/537.36 Chrome/123.0 Mobile Safari/537.36")
            setRequestProperty("Accept", "audio/*,application/octet-stream,text/plain,text/html,application/json;q=0.9,*/*;q=0.8")
            setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
            setRequestProperty("Connection", "keep-alive")
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("Pragma", "no-cache")
            referer?.let { setRequestProperty("Referer", it) }
            if (cookies.isNotEmpty()) {
                setRequestProperty("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
            }
            connect()
        }
    }

    private fun looksLikeDownloadResponse(
        url: String,
        contentType: String,
        contentDisposition: String,
    ): Boolean {
        if (contentDisposition.contains("filename=")) return true
        if (contentType.startsWith("audio/")) return true
        if (contentType == "application/octet-stream" || contentType == "application/download" || contentType == "binary/octet-stream") {
            return true
        }
        val loweredUrl = url.lowercase()
        return loweredUrl.contains(".mp3") ||
            loweredUrl.contains(".m4a") ||
            loweredUrl.contains(".aac") ||
            loweredUrl.contains(".ogg") ||
            loweredUrl.contains(".wav") ||
            loweredUrl.contains(".flac")
    }

    private fun captureResponseCookies(
        connection: HttpURLConnection,
        cookieJar: MutableMap<String, String>,
    ) {
        connection.headerFields["Set-Cookie"].orEmpty().forEach { rawCookie ->
            val pair = rawCookie.substringBefore(';')
            val delimiterIndex = pair.indexOf('=')
            if (delimiterIndex > 0) {
                val name = pair.substring(0, delimiterIndex).trim()
                val value = pair.substring(delimiterIndex + 1).trim()
                if (name.isNotBlank() && value.isNotBlank()) {
                    cookieJar[name] = value
                }
            }
        }
    }

    private fun readBodyPreview(stream: InputStream?): String {
        if (stream == null) return ""
        return runCatching {
            stream.bufferedReader().use { reader ->
                reader.readText()
                    .replace(Regex("\\s+"), " ")
                    .trim()
            }
        }.getOrDefault("")
    }

    private fun extractMediaUrl(body: String, baseUrl: String): String? {
        if (body.isBlank()) return null
        val normalized = body
            .replace("\\/", "/")
            .replace("&amp;", "&")

        val patterns = listOf(
            Regex("""https?://[^\"'\s]+?\.(mp3|m4a|aac|ogg|wav|flac)(\?[^\"'\s]*)?""", RegexOption.IGNORE_CASE),
            Regex("""[\"']((?:https?:)?//[^\"'\s]+?\.(?:mp3|m4a|aac|ogg|wav|flac)(?:\?[^\"'\s]*)?)[\"']""", RegexOption.IGNORE_CASE),
            Regex("""\"url\"\s*:\s*\"([^\"]+)\"""", RegexOption.IGNORE_CASE),
            Regex("""file\s*[:=]\s*[\"']([^\"']+)[\"']""", RegexOption.IGNORE_CASE),
            Regex("""href\s*=\s*[\"']([^\"']+\.(?:mp3|m4a|aac|ogg|wav|flac)[^\"']*)[\"']""", RegexOption.IGNORE_CASE),
            Regex("""(?:do=getById[^\"'\s<>]+)""", RegexOption.IGNORE_CASE),
        )

        patterns.forEach { pattern ->
            val match = pattern.find(normalized) ?: return@forEach
            val rawValue = match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() } ?: match.value
            return resolveUrl(baseUrl, rawValue)
        }

        return null
    }

    private fun resolveUrl(baseUrl: String, candidate: String): String {
        val normalized = candidate.trim().replace("&amp;", "&")
        return when {
            normalized.startsWith("http://") || normalized.startsWith("https://") -> normalized
            normalized.startsWith("//") -> "https:$normalized"
            normalized.startsWith("/") || normalized.startsWith("?") || normalized.startsWith("do=") -> {
                URL(URL(baseUrl), normalized).toString()
            }
            else -> URL(URL(baseUrl), normalized).toString()
        }
    }

    private fun createMediaStoreAudioUri(
        displayName: String,
        mimeType: String,
    ): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/YaNeoDex")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
    }

    private fun finalizeMediaStoreAudio(uri: Uri) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }
        context.contentResolver.update(uri, values, null, null)
    }

    private fun buildDisplayName(
        title: String,
        artist: String?,
        extension: String,
    ): String {
        val rawName = listOfNotNull(artist?.takeIf { it.isNotBlank() }, title.takeIf { it.isNotBlank() })
            .joinToString(" - ")
            .ifBlank { "track-${UUID.randomUUID()}" }
        val safeName = rawName.replace(Regex("[\\/:*?\"<>|]"), "_").trim()
        return "$safeName.${extension.ifBlank { "mp3" }}"
    }

    private fun guessAudioExtension(
        suggestedFilename: String,
        contentType: String?,
    ): String {
        val extensionFromName = suggestedFilename.substringAfterLast('.', missingDelimiterValue = "")
            .takeIf { it.isNotBlank() }
            ?.lowercase()
            ?.let { ".${it}" }
        if (extensionFromName != null) {
            return extensionFromName
        }

        return when (contentType?.substringBefore(';')?.trim()?.lowercase()) {
            "audio/mpeg", "audio/mp3" -> ".mp3"
            "audio/mp4", "audio/x-m4a" -> ".m4a"
            "audio/aac", "audio/aacp" -> ".aac"
            "audio/ogg" -> ".ogg"
            "audio/wav", "audio/x-wav" -> ".wav"
            "audio/flac", "audio/x-flac" -> ".flac"
            else -> ".mp3"
        }
    }

    private fun guessMimeType(extension: String): String {
        return when (extension.lowercase()) {
            "mp3" -> "audio/mpeg"
            "m4a", "mp4" -> "audio/mp4"
            "aac" -> "audio/aac"
            "ogg" -> "audio/ogg"
            "wav" -> "audio/wav"
            "flac" -> "audio/flac"
            else -> "audio/mpeg"
        }
    }
}
