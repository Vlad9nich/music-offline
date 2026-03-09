package com.codex.signalflow.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
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
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.PushbackInputStream
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

    suspend fun importTracks(uris: List<Uri>): List<TrackRecord> = withContext(Dispatchers.IO) {
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

        resolved
    }

    suspend fun importDownloadedTrack(blueprint: DownloadBlueprint): TrackRecord = withContext(Dispatchers.IO) {
        logDebug(
            stage = "import",
            message = "start source=${blueprint.url} title=${blueprint.title} artist=${blueprint.artist}",
        )

        val existing = snapshot.value.tracks.firstOrNull { it.sourceUri == blueprint.url }
        if (existing != null) {
            logDebug(stage = "import", message = "existing track reused id=${existing.id} uri=${existing.uri}")
            return@withContext existing
        }

        val stored = downloadToDevice(blueprint)
        val track = registerDownloadedTrack(
            blueprint = blueprint,
            stored = stored,
        )

        val updated = mutate { current ->
            val duplicate = current.tracks.firstOrNull { it.sourceUri == blueprint.url }
            if (duplicate != null) {
                current
            } else {
                current.copy(tracks = current.tracks + track)
            }
        }

        val finalTrack = updated.tracks.firstOrNull { it.sourceUri == blueprint.url } ?: track
        logDebug(
            stage = "library",
            message = "registered track id=${finalTrack.id} uri=${finalTrack.uri} source=${finalTrack.sourceUri} librarySize=${updated.tracks.size}",
        )
        finalTrack
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
        logDebug(stage = "library", message = "snapshot persisted path=${storageFile.absolutePath} tracks=${updated.tracks.size} playlists=${updated.playlists.size}")
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
                    output.flush()
                } ?: error("Не удалось открыть поток записи")
            } ?: error("Не удалось открыть поток чтения")
            finalizeMediaStoreAudio(targetUri)
            targetUri.toString()
        }.getOrElse {
            resolver.delete(targetUri, null, null)
            null
        }
    }

    private suspend fun registerDownloadedTrack(
        blueprint: DownloadBlueprint,
        stored: StoredDownload,
    ): TrackRecord {
        logDebug(
            stage = "register",
            message = "start uri=${stored.persistedUri} finalUrl=${stored.finalUrl} bytes=${stored.bytesWritten} contentType=${stored.contentType}",
        )

        val metadataTrack = runCatching {
            AudioMetadataReader.read(
                context = context,
                playbackUri = stored.persistedUri,
                sourceUri = blueprint.url,
                fallbackTitle = blueprint.title,
                fallbackArtist = blueprint.artist,
            )
        }.onFailure { error ->
            logError(stage = "register", message = "metadata reader threw for uri=${stored.persistedUri}", error = error)
        }.getOrNull()

        val track = metadataTrack ?: buildFallbackTrackRecord(
            playbackUri = stored.persistedUri,
            sourceUri = blueprint.url,
            title = blueprint.title,
            artist = blueprint.artist,
        )

        logDebug(
            stage = "register",
            message = "completed trackId=${track.id} title=${track.title} artist=${track.artist} duration=${track.durationMs} usedFallback=${metadataTrack == null}",
        )
        return track
    }

    private suspend fun downloadToDevice(blueprint: DownloadBlueprint): StoredDownload {
        val guessedExtension = guessAudioExtension(
            suggestedFilename = blueprint.suggestedFilename,
            contentType = null,
        )
        val initialMimeType = guessMimeType(guessedExtension.removePrefix("."))
        val displayName = buildDisplayName(
            title = blueprint.title,
            artist = blueprint.artist,
            extension = guessedExtension.removePrefix("."),
        )
        val resolver = context.contentResolver
        val targetUri = createMediaStoreAudioUri(displayName = displayName, mimeType = initialMimeType)
            ?: error("Не удалось создать файл в памяти устройства.")

        logDebug(
            stage = "download",
            message = "start target=$targetUri displayName=$displayName mime=$initialMimeType source=${blueprint.url} referer=${blueprint.refererUrl}",
        )

        var bytesWritten = 0L
        var published = false
        var finalUrl = blueprint.url
        var finalContentType = initialMimeType

        try {
            val session = openDownloadSessionWithRetry(blueprint)
            finalUrl = session.finalUrl
            finalContentType = session.contentType.ifBlank { initialMimeType }

            try {
                resolver.openOutputStream(targetUri)?.use { output ->
                    session.stream.use { input ->
                        bytesWritten = input.copyTo(output)
                        output.flush()
                    }
                } ?: error("Не удалось открыть поток записи")
            } finally {
                runCatching { session.stream.close() }
                session.connection.disconnect()
            }

            logDebug(
                stage = "download",
                message = "written target=$targetUri bytes=$bytesWritten finalUrl=$finalUrl contentType=$finalContentType",
            )

            finalizeMediaStoreAudio(targetUri)
            published = true
            logDebug(stage = "download", message = "published target=$targetUri bytes=$bytesWritten")

            return StoredDownload(
                persistedUri = targetUri.toString(),
                finalUrl = finalUrl,
                contentType = finalContentType,
                bytesWritten = bytesWritten,
            )
        } catch (error: Throwable) {
            logError(
                stage = "download",
                message = "failed target=$targetUri bytes=$bytesWritten published=$published finalUrl=$finalUrl",
                error = error,
            )
            if (!published && bytesWritten <= 0L) {
                resolver.delete(targetUri, null, null)
                logDebug(stage = "download", message = "deleted incomplete target=$targetUri")
            } else {
                logDebug(stage = "download", message = "kept partially saved target=$targetUri bytes=$bytesWritten published=$published")
            }
            throw IllegalStateException(toUserFacingMessage(error, "Не удалось скачать трек."), error)
        }
    }

    private suspend fun openDownloadSessionWithRetry(blueprint: DownloadBlueprint): DownloadSession {
        val delaysMs = listOf(0L, 1_500L, 3_000L, 5_000L)
        var lastError: Throwable? = null

        for ((index, delayMs) in delaysMs.withIndex()) {
            if (delayMs > 0) {
                delay(delayMs)
            }
            logDebug(
                stage = "download-attempt",
                message = "attempt=${index + 1}/${delaysMs.size} url=${blueprint.url} referer=${blueprint.refererUrl} delayMs=$delayMs",
            )
            try {
                return openDownloadSessionAttempt(blueprint)
            } catch (error: Throwable) {
                lastError = error
                logError(
                    stage = "download-attempt",
                    message = "attempt=${index + 1}/${delaysMs.size} failed url=${blueprint.url}",
                    error = error,
                )
                if (index == delaysMs.lastIndex) {
                    throw error
                }
            }
        }

        throw IllegalStateException(toUserFacingMessage(lastError, "Не удалось скачать трек."), lastError)
    }

    private fun openDownloadSessionAttempt(blueprint: DownloadBlueprint): DownloadSession {
        var currentUrl = blueprint.url
        var currentReferer = blueprint.refererUrl
        val cookies = linkedMapOf<String, String>()

        repeat(8) { redirectIndex ->
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
                    val initialStream = connection.inputStream

                    logDebug(
                        stage = "http",
                        message = "response=$responseCode url=$currentUrl finalUrl=$finalUrl contentType=$contentType contentDisposition=$contentDisposition redirectIndex=$redirectIndex",
                    )

                    val probe = inspectPayload(
                        rawStream = initialStream,
                        baseUrl = finalUrl,
                        contentType = contentType,
                        contentDisposition = contentDisposition,
                    )

                    if (probe.redirectUrl != null) {
                        runCatching { initialStream.close() }
                        connection.disconnect()
                        logDebug(
                            stage = "payload",
                            message = "resolved nested media url=${probe.redirectUrl} from finalUrl=$finalUrl previewBytes=${probe.previewBytes} preview=${probe.previewText.take(140)}",
                        )
                        currentReferer = finalUrl
                        currentUrl = probe.redirectUrl
                    } else {
                        logDebug(
                            stage = "payload",
                            message = "accepted finalUrl=$finalUrl previewBytes=${probe.previewBytes} contentType=$contentType",
                        )
                        return DownloadSession(
                            connection = connection,
                            stream = probe.stream ?: initialStream,
                            finalUrl = finalUrl,
                            contentType = contentType,
                        )
                    }
                }

                in 300..399 -> {
                    val location = connection.getHeaderField("Location")
                        ?: run {
                            connection.disconnect()
                            error("HTTP $responseCode без адреса перенаправления.")
                        }
                    val redirectUrl = resolveUrl(currentUrl, location)
                    logDebug(
                        stage = "http",
                        message = "redirect response=$responseCode from=$currentUrl to=$redirectUrl redirectIndex=$redirectIndex",
                    )
                    connection.disconnect()
                    currentReferer = currentUrl
                    currentUrl = redirectUrl
                }

                else -> {
                    val errorPreview = readBodyPreview(connection.errorStream)
                    connection.disconnect()
                    val message = if (errorPreview.isBlank()) {
                        "HTTP $responseCode"
                    } else {
                        "HTTP $responseCode: ${errorPreview.take(140)}"
                    }
                    error(message)
                }
            }
        }

        error("Слишком много перенаправлений при скачивании.")
    }

    private fun inspectPayload(
        rawStream: InputStream,
        baseUrl: String,
        contentType: String,
        contentDisposition: String,
    ): PayloadProbe {
        val stream = PushbackInputStream(rawStream, PAYLOAD_PEEK_BYTES)
        val buffer = ByteArray(PAYLOAD_PEEK_BYTES)
        val bytesRead = stream.read(buffer)
        if (bytesRead <= 0) {
            return PayloadProbe(stream = stream)
        }

        val headBytes = buffer.copyOf(bytesRead)
        val previewText = runCatching { String(headBytes, Charsets.UTF_8) }.getOrDefault("")
            .replace("\\u0000", "")
            .replace(Regex("\\s+"), " ")
            .trim()
        val loweredPreview = previewText.lowercase()

        val looksLikeMarkup = loweredPreview.startsWith("<") ||
            loweredPreview.startsWith("{") ||
            loweredPreview.startsWith("[") ||
            loweredPreview.contains("<html") ||
            loweredPreview.contains("<!doctype") ||
            loweredPreview.contains("<body")

        val headerSaysAudio = contentDisposition.contains("filename=") ||
            contentType.startsWith("audio/") ||
            contentType == "application/octet-stream" ||
            contentType == "application/download" ||
            contentType == "binary/octet-stream"

        if (looksLikeMarkup) {
            val extractedUrl = extractMediaUrlFromBody(previewText, baseUrl)
            if (!extractedUrl.isNullOrBlank() && extractedUrl != baseUrl) {
                return PayloadProbe(
                    redirectUrl = extractedUrl,
                    previewText = previewText,
                    previewBytes = bytesRead,
                )
            }
            error("Сервер вернул HTML/JSON вместо аудиофайла: ${previewText.take(140)}")
        }

        if (!headerSaysAudio && !looksLikeAudioBytes(headBytes)) {
            val extractedUrl = extractMediaUrlFromBody(previewText, baseUrl)
            if (!extractedUrl.isNullOrBlank() && extractedUrl != baseUrl) {
                return PayloadProbe(
                    redirectUrl = extractedUrl,
                    previewText = previewText,
                    previewBytes = bytesRead,
                )
            }
            if (previewText.isNotBlank()) {
                logDebug(
                    stage = "payload",
                    message = "non-audio response without redirect baseUrl=$baseUrl contentType=$contentType preview=${previewText.take(140)}",
                )
            }
        }

        stream.unread(headBytes)
        return PayloadProbe(
            stream = stream,
            previewText = previewText,
            previewBytes = bytesRead,
        )
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

    private fun looksLikeAudioBytes(bytes: ByteArray): Boolean {
        if (bytes.size >= 3) {
            val startsWithId3 = bytes[0] == 'I'.code.toByte() && bytes[1] == 'D'.code.toByte() && bytes[2] == '3'.code.toByte()
            if (startsWithId3) return true
        }
        if (bytes.isNotEmpty()) {
            val first = bytes[0].toInt() and 0xFF
            val second = bytes.getOrNull(1)?.toInt()?.and(0xE0) ?: 0
            if (first == 0xFF && second == 0xE0) return true
        }
        return false
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

    private fun logDebug(stage: String, message: String) {
        Log.d(TAG, "$stage | $message")
    }

    private fun logError(stage: String, message: String, error: Throwable?) {
        if (error == null) {
            Log.e(TAG, "$stage | $message | reason=(null)")
        } else {
            Log.e(TAG, "$stage | $message | reason=${describeThrowable(error)}", error)
        }
    }

    private data class StoredDownload(
        val persistedUri: String,
        val finalUrl: String,
        val contentType: String,
        val bytesWritten: Long,
    )

    private data class DownloadSession(
        val connection: HttpURLConnection,
        val stream: InputStream,
        val finalUrl: String,
        val contentType: String,
    )

    private data class PayloadProbe(
        val stream: InputStream? = null,
        val redirectUrl: String? = null,
        val previewText: String = "",
        val previewBytes: Int = 0,
    )

    companion object {
        private const val TAG = "YaNeoDexDownload"
        private const val PAYLOAD_PEEK_BYTES = 1024

        internal fun buildFallbackTrackRecord(
            playbackUri: String,
            sourceUri: String,
            title: String,
            artist: String,
        ): TrackRecord {
            return TrackRecord(
                id = UUID.randomUUID().toString(),
                uri = playbackUri,
                sourceUri = sourceUri,
                title = title.ifBlank { "Неизвестный трек" },
                artist = artist.ifBlank { "Неизвестный исполнитель" },
                durationMs = 0L,
                importedAtEpochMs = System.currentTimeMillis(),
            )
        }

        internal fun extractMediaUrlFromBody(body: String, baseUrl: String): String? {
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
                return resolveRelativeUrl(baseUrl, rawValue)
            }

            return null
        }

        internal fun describeThrowable(error: Throwable): String {
            val className = error::class.java.name
            val message = error.message?.trim().orEmpty().ifBlank { "без сообщения" }
            val causeMessage = error.cause?.message?.trim().orEmpty()
            return if (causeMessage.isBlank()) {
                "$className: $message"
            } else {
                "$className: $message | cause=${error.cause!!::class.java.name}: $causeMessage"
            }
        }

        internal fun toUserFacingMessage(error: Throwable?, fallback: String): String {
            if (error == null) return fallback
            val message = error.message?.trim().orEmpty()
            if (message.isNotBlank()) return message
            val causeMessage = error.cause?.message?.trim().orEmpty()
            if (causeMessage.isNotBlank()) return causeMessage
            return when (error) {
                is java.net.SocketTimeoutException -> "Сервер слишком долго отвечает. Повторите попытку."
                is java.io.FileNotFoundException -> "Файл не найден на стороне источника."
                else -> fallback
            }
        }

        private fun resolveRelativeUrl(baseUrl: String, candidate: String): String {
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
    }
}
