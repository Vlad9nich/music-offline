package com.codex.signalflow.yandex

import android.content.Context
import android.util.Log
import com.codex.signalflow.data.LibraryRepository
import com.codex.signalflow.source.DownloadBlueprint
import com.codex.signalflow.source.MusicSourceCatalog
import com.codex.signalflow.source.RemoteTrackCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Manages Yandex Music account credentials, toggles, and the Liked Tracks import pipeline.
 */
class YandexSyncManager(
    private val context: Context,
    val client: YandexMusicClient = YandexMusicClient(),
) {
    private val prefs = context.applicationContext.getSharedPreferences("yandex_sync_prefs", Context.MODE_PRIVATE)

    private val _token = MutableStateFlow(prefs.getString(KEY_TOKEN, null))
    val token: StateFlow<String?> = _token.asStateFlow()

    private val _isDirectSourceEnabled = MutableStateFlow(prefs.getBoolean(KEY_DIRECT_ENABLED, false))
    val isDirectSourceEnabled: StateFlow<Boolean> = _isDirectSourceEnabled.asStateFlow()

    private val _progress = MutableStateFlow(YandexSyncProgress())
    val progress: StateFlow<YandexSyncProgress> = _progress.asStateFlow()

    private val _deviceAuthCode = MutableStateFlow<YandexDeviceCode?>(null)
    val deviceAuthCode: StateFlow<YandexDeviceCode?> = _deviceAuthCode.asStateFlow()

    fun setToken(newToken: String?) {
        val clean = newToken?.trim()?.takeIf { it.isNotBlank() }
        prefs.edit().putString(KEY_TOKEN, clean).apply()
        _token.value = clean
    }

    suspend fun startDeviceAuth(): Result<YandexDeviceCode> = withContext(Dispatchers.IO) {
        val res = client.requestDeviceCode()
        res.onSuccess { _deviceAuthCode.value = it }
        res
    }

    suspend fun pollDeviceAuthOnce(): String? = withContext(Dispatchers.IO) {
        val code = _deviceAuthCode.value ?: return@withContext null
        val tokenRes = client.pollDeviceToken(code.deviceCode).getOrNull()
        if (tokenRes != null) {
            setToken(tokenRes)
            _deviceAuthCode.value = null
        }
        tokenRes
    }

    fun cancelDeviceAuth() {
        _deviceAuthCode.value = null
    }

    fun setDirectSourceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DIRECT_ENABLED, enabled).apply()
        _isDirectSourceEnabled.value = enabled
    }

    /**
     * Executes the Likes Import Pipeline:
     * 1. Fetches all liked tracks using the personal token.
     * 2. Searches & downloads EACH track via the PRIMARY parser (first priority).
     * 3. If primary parser has no match, falls back to direct Yandex downloader (if enabled).
     * 4. Adds all successfully downloaded tracks to playlist "ЯНДЕКС: МНЕ НРАВИТСЯ".
     */
    suspend fun runLikesImportPipeline(
        sourceCatalog: MusicSourceCatalog,
        repository: LibraryRepository,
    ) = withContext(Dispatchers.IO) {
        val currentToken = token.value
        if (currentToken.isNullOrBlank()) {
            _progress.value = YandexSyncProgress(error = "Токен Яндекс Музыки не задан.")
            return@withContext
        }

        if (_progress.value.isRunning) return@withContext

        _progress.value = YandexSyncProgress(
            isRunning = true,
            message = "Проверка авторизации Яндекс...",
        )

        try {
            // 1. Validate Token and get Account UID
            val account = client.validateToken(currentToken).getOrThrow()
            _progress.value = _progress.value.copy(
                message = "Авторизован: ${account.displayName}. Загрузка лайков...",
            )

            // 2. Fetch Liked Tracks
            val likedTracks = client.fetchLikedTracks(currentToken, account.uid).getOrThrow()
            if (likedTracks.isEmpty()) {
                _progress.value = YandexSyncProgress(
                    isRunning = false,
                    message = "В разделе «Мне нравится» нет треков.",
                )
                return@withContext
            }

            // 3. Ensure Playlist "ЯНДЕКС: МНЕ НРАВИТСЯ" exists
            val playlistName = "ЯНДЕКС: МНЕ НРАВИТСЯ"
            var targetPlaylist = repository.snapshot.value.playlists.firstOrNull {
                it.name.equals(playlistName, ignoreCase = true)
            }
            if (targetPlaylist == null) {
                val createdSnapshot = repository.createPlaylist(playlistName)
                targetPlaylist = createdSnapshot.playlists.firstOrNull { it.name.equals(playlistName, ignoreCase = true) }
            }

            val total = likedTracks.size
            var downloadedCount = 0

            _progress.value = YandexSyncProgress(
                isRunning = true,
                total = total,
                current = 0,
                message = "Найдено $total треков. Запуск конвейера...",
            )

            // 4. Pipeline Loop over tracks
            for ((index, track) in likedTracks.withIndex()) {
                val currentNumber = index + 1
                val trackTitle = "${track.artist} - ${track.title}"

                _progress.value = YandexSyncProgress(
                    isRunning = true,
                    total = total,
                    current = currentNumber,
                    currentTrackTitle = trackTitle,
                    message = "[$currentNumber / $total] Поиск в основном парсере: $trackTitle",
                )

                var wasDownloaded = false

                // -----------------------------------------------------------
                // Step A: Primary Parser (Первоначальный источник - ПРИОРИТЕТ)
                // -----------------------------------------------------------
                val query = "${track.artist} ${track.title}"
                val primaryCandidates = runCatching {
                    sourceCatalog.searchAll(query).filter { it.sourceId != "yandex_direct" }
                }.getOrDefault(emptyList())

                val bestPrimaryMatch = findBestMatch(track, primaryCandidates)

                if (bestPrimaryMatch != null) {
                    try {
                        _progress.value = _progress.value.copy(
                            message = "[$currentNumber / $total] Скачивание из парсера (${bestPrimaryMatch.sourceId})...",
                        )
                        val blueprint = sourceCatalog.resolve(bestPrimaryMatch)
                        // Preserve high-res Yandex cover if primary has none
                        val finalBlueprint = if (blueprint.coverUrl.isNullOrBlank() && !track.coverUrl.isNullOrBlank()) {
                            blueprint.copy(coverUrl = track.coverUrl)
                        } else {
                            blueprint
                        }
                        val imported = repository.importDownloadedTrack(finalBlueprint)
                        targetPlaylist?.let { repository.addTrackToPlaylist(imported.id, it.id) }
                        downloadedCount++
                        wasDownloaded = true
                    } catch (e: Throwable) {
                        Log.w(TAG, "Primary parser download failed for $trackTitle: ${e.message}")
                    }
                }

                // -----------------------------------------------------------
                // Step B: Secondary / Fallback Source (Яндекс Музыка Direct)
                // -----------------------------------------------------------
                if (!wasDownloaded && isDirectSourceEnabled.value) {
                    try {
                        _progress.value = _progress.value.copy(
                            message = "[$currentNumber / $total] Парсер пропустил. Загрузка через Яндекс Direct...",
                        )
                        val directUrl = client.resolveDirectDownloadUrl(currentToken, track.id).getOrThrow()
                        val filename = "${track.artist} - ${track.title}.mp3".replace(Regex("[/\\\\:*?\"<>|]"), "_")
                        val blueprint = DownloadBlueprint(
                            title = track.title,
                            artist = track.artist,
                            url = directUrl,
                            suggestedFilename = filename,
                            coverUrl = track.coverUrl,
                        )
                        val imported = repository.importDownloadedTrack(blueprint)
                        targetPlaylist?.let { repository.addTrackToPlaylist(imported.id, it.id) }
                        downloadedCount++
                        wasDownloaded = true
                    } catch (e: Throwable) {
                        Log.e(TAG, "Direct Yandex download failed for $trackTitle: ${e.message}")
                    }
                }
            }

            _progress.value = YandexSyncProgress(
                isRunning = false,
                total = total,
                current = total,
                message = "Конвейер завершён: перенесено $downloadedCount из $total треков в плейлист «$playlistName».",
            )
        } catch (error: Throwable) {
            _progress.value = YandexSyncProgress(
                isRunning = false,
                error = "Ошибка синхронизации: ${error.localizedMessage ?: error.message}",
            )
        }
    }

    private fun findBestMatch(track: YandexTrackMeta, candidates: List<RemoteTrackCandidate>): RemoteTrackCandidate? {
        if (candidates.isEmpty()) return null
        val targetTitle = normalize(track.title)
        val targetArtist = normalize(track.artist)

        return candidates.firstOrNull { candidate ->
            val cTitle = normalize(candidate.title)
            val cArtist = normalize(candidate.artist)
            cTitle.contains(targetTitle) || targetTitle.contains(cTitle) &&
                (cArtist.contains(targetArtist) || targetArtist.contains(cArtist) || cArtist.isBlank())
        } ?: candidates.firstOrNull()
    }

    private fun normalize(str: String): String =
        str.replace('ё', 'е').lowercase().replace(Regex("[^a-zа-я0-9]"), "")

    companion object {
        private const val TAG = "YandexSyncManager"
        private const val KEY_TOKEN = "yandex_music_oauth_token"
        private const val KEY_DIRECT_ENABLED = "yandex_music_direct_enabled"
    }
}
