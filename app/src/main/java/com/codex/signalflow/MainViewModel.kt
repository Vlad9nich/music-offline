package com.codex.signalflow

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codex.signalflow.data.LibraryRepository
import com.codex.signalflow.data.PlaylistRecord
import androidx.compose.runtime.Immutable
import com.codex.signalflow.data.TrackRecord
import com.codex.signalflow.importer.MatchedTrackCandidate
import com.codex.signalflow.importer.OcrJobResponse
import com.codex.signalflow.importer.ScreenshotImportItemStatus
import com.codex.signalflow.importer.ScreenshotImportMatcher
import com.codex.signalflow.importer.ScreenshotImportPhase
import com.codex.signalflow.importer.ScreenshotImportRepository
import com.codex.signalflow.importer.ScreenshotImportSession
import com.codex.signalflow.importer.toRecognizedCandidate
import com.codex.signalflow.player.PlaybackController
import com.codex.signalflow.player.PlayerUiState
import com.codex.signalflow.source.MusicSourceCatalog
import com.codex.signalflow.source.RemoteTrackCandidate
import com.codex.signalflow.source.SourceDescriptor
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class MainUiState(
    val tracks: List<TrackRecord> = emptyList(),
    val filteredTracks: List<TrackRecord> = emptyList(),
    val playlists: List<PlaylistRecord> = emptyList(),
    val selectedPlaylistId: String? = null,
    val selectedPlaylistTracks: List<TrackRecord> = emptyList(),
    val selectedPlaylistName: String = "Плейлист не выбран",
    val libraryQuery: String = "",
    val sourceQuery: String = "",
    val sourceResults: List<RemoteTrackCandidate> = emptyList(),
    val downloadingTrackKeys: Set<String> = emptySet(),
    val sourceDescriptors: List<SourceDescriptor> = emptyList(),
    val sourceStatus: String = "Источник Ligaudio активен.",
    val ocrServerUrl: String = "",
    val ocrAuthToken: String = "",
    val screenshotImportSession: ScreenshotImportSession? = null,
    val yandexToken: String? = null,
    val isYandexDirectSourceEnabled: Boolean = false,
    val yandexSyncProgress: com.codex.signalflow.yandex.YandexSyncProgress = com.codex.signalflow.yandex.YandexSyncProgress(),
    val yandexDeviceCode: com.codex.signalflow.yandex.YandexDeviceCode? = null,
    val selectedSourceFilter: String = "all",
)

internal fun buildSelectedPlaylistTracks(
    trackIds: List<String>,
    allTracks: List<TrackRecord>,
): List<TrackRecord> {
    val tracksById = allTracks.associateBy { it.id }
    // Preserve playlist order (trackIds), only dedupe consecutive/legacy duplicates.
    return trackIds
        .asSequence()
        .distinct()
        .mapNotNull { trackId -> tracksById[trackId] }
        .toList()
}

class MainViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository = LibraryRepository(application)
    private val screenshotImportRepository = ScreenshotImportRepository(application)
    private val playbackController = PlaybackController(application)
    private val yandexSyncManager = com.codex.signalflow.yandex.YandexSyncManager(application)
    private val sourceCatalog = MusicSourceCatalog(
        sources = listOf(
            com.codex.signalflow.source.LigaudioSource(),
            com.codex.signalflow.source.YandexDirectSource(
                client = yandexSyncManager.client,
                tokenProvider = { yandexSyncManager.token.value },
                isEnabledProvider = { yandexSyncManager.isDirectSourceEnabled.value },
            ),
        ),
    )
    private val selectedPlaylistId = MutableStateFlow<String?>(null)
    private val libraryQuery = MutableStateFlow("")
    private val sourceQuery = MutableStateFlow("")
    private val sourceResults = MutableStateFlow<List<RemoteTrackCandidate>>(emptyList())
    private val downloadingTrackKeys = MutableStateFlow<Set<String>>(emptySet())
    private val selectedSourceFilter = MutableStateFlow("all")
    private val sourceStatus = MutableStateFlow("Источник Ligaudio активен.")
    private val ocrServerUrl = MutableStateFlow(screenshotImportRepository.getServerUrl())
    private val ocrAuthToken = MutableStateFlow(screenshotImportRepository.getAuthToken())
    private val screenshotImportSession = MutableStateFlow<ScreenshotImportSession?>(null)
    private val snackbar = MutableStateFlow<String?>(null)

    val snackbarMessage: StateFlow<String?> = snackbar
    val playerState: StateFlow<PlayerUiState> = playbackController.playerState

    private val librarySelection = combine(repository.snapshot, selectedPlaylistId) { snapshot, playlistId ->
        val activePlaylist = snapshot.playlists.firstOrNull { it.id == playlistId }
            ?: snapshot.playlists.sortedBy { it.name.lowercase() }.firstOrNull()
        snapshot to activePlaylist
    }

    @OptIn(FlowPreview::class)
    private val debouncedLibraryQuery = libraryQuery.debounce(200)

    private val libraryFilterState = combine(librarySelection, debouncedLibraryQuery) { selection, debouncedQuery ->
        selection to debouncedQuery
    }

    private val coreUiState = combine(
        libraryFilterState,
        libraryQuery,
        sourceQuery,
        sourceResults,
        sourceStatus,
    ) { selectionAndFilter, rawQuery, remoteQuery, results, sourceMessage ->
        val selection = selectionAndFilter.first
        val debouncedQuery = selectionAndFilter.second
        val snapshot = selection.first
        val activePlaylist = selection.second
        val normalizedQuery = debouncedQuery.trim()
        // Sort library once per snapshot emission, reuse for filter.
        val sortedTracks = snapshot.tracks
            .sortedWith(compareBy<TrackRecord> { it.title.lowercase() }.thenBy { it.artist.lowercase() })
        val filteredTracks = if (normalizedQuery.isBlank()) {
            sortedTracks
        } else {
            sortedTracks.filter {
                it.title.contains(normalizedQuery, ignoreCase = true) ||
                    it.artist.contains(normalizedQuery, ignoreCase = true)
            }
        }
        val selectedTracks = buildSelectedPlaylistTracks(
            trackIds = activePlaylist?.trackIds.orEmpty(),
            allTracks = snapshot.tracks,
        )

        MainUiState(
            tracks = sortedTracks,
            filteredTracks = filteredTracks,
            playlists = snapshot.playlists.sortedBy { it.name.lowercase() },
            selectedPlaylistId = activePlaylist?.id,
            selectedPlaylistTracks = selectedTracks,
            selectedPlaylistName = activePlaylist?.name ?: "Плейлист не выбран",
            libraryQuery = rawQuery,
            sourceQuery = remoteQuery,
            sourceResults = results,
            sourceDescriptors = sourceCatalog.descriptors,
            sourceStatus = sourceMessage,
        )
    }

    private val baseUiState = combine(coreUiState, ocrServerUrl, ocrAuthToken, screenshotImportSession) { state, currentOcrServerUrl, currentOcrAuthToken, importSession ->
        state.copy(
            ocrServerUrl = currentOcrServerUrl,
            ocrAuthToken = currentOcrAuthToken,
            screenshotImportSession = importSession,
        )
    }

    private data class YandexCombinedState(
        val token: String?,
        val isDirectEnabled: Boolean,
        val progress: com.codex.signalflow.yandex.YandexSyncProgress,
        val deviceCode: com.codex.signalflow.yandex.YandexDeviceCode?,
    )

    private val yandexStateFlow = combine(
        yandexSyncManager.token,
        yandexSyncManager.isDirectSourceEnabled,
        yandexSyncManager.progress,
        yandexSyncManager.deviceAuthCode,
    ) { token, directEnabled, progress, deviceCode ->
        YandexCombinedState(token, directEnabled, progress, deviceCode)
    }

    val uiState: StateFlow<MainUiState> = combine(
        baseUiState,
        downloadingTrackKeys,
        selectedSourceFilter,
        yandexStateFlow,
    ) { state, downloadingKeys, srcFilter, yState ->
        state.copy(
            downloadingTrackKeys = downloadingKeys,
            selectedSourceFilter = srcFilter,
            yandexToken = yState.token,
            isYandexDirectSourceEnabled = yState.isDirectEnabled,
            yandexSyncProgress = yState.progress,
            yandexDeviceCode = yState.deviceCode,
            sourceDescriptors = sourceCatalog.descriptors,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState(sourceDescriptors = sourceCatalog.descriptors))

    init {
        viewModelScope.launch {
            repository.snapshot.collect { snapshot ->
                val current = selectedPlaylistId.value
                if (snapshot.playlists.isNotEmpty() && current !in snapshot.playlists.map { it.id }) {
                    selectedPlaylistId.value = snapshot.playlists.sortedBy { it.name.lowercase() }.first().id
                }
            }
        }
    }

    fun setLibraryQuery(value: String) { libraryQuery.value = value }
    fun setSourceQuery(value: String) { sourceQuery.value = value }
    fun setOcrServerUrl(value: String) {
        ocrServerUrl.value = value
        screenshotImportRepository.setServerUrl(value)
    }
    fun setOcrAuthToken(value: String) {
        ocrAuthToken.value = value
        screenshotImportRepository.setAuthToken(value)
    }
    fun clearSnackbar() { snackbar.value = null }
    fun dismissScreenshotImportSession() { screenshotImportSession.value = null }

    fun importTracks(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val importedTracks = repository.importTracks(uris)
            val selectedPlaylist = selectedPlaylistId.value
            if (selectedPlaylist != null) importedTracks.forEach { track -> repository.addTrackToPlaylist(track.id, selectedPlaylist) }
            snackbar.value = when {
                importedTracks.isEmpty() -> "Новые треки не были добавлены."
                selectedPlaylist != null -> "Импортировано ${importedTracks.size} треков и добавлено в текущий плейлист."
                else -> "Импортировано ${importedTracks.size} офлайн-треков."
            }
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            val updated = repository.createPlaylist(name)
            selectedPlaylistId.value = updated.playlists.lastOrNull()?.id
            snackbar.value = if (name.trim().isBlank()) "Название плейлиста пустое." else "Плейлист создан."
        }
    }

    fun selectPlaylist(playlistId: String) { selectedPlaylistId.value = playlistId }

    fun importPlaylistScreenshots(uris: List<Uri>) {
        val playlistId = selectedPlaylistId.value
        if (playlistId == null) {
            snackbar.value = "Сначала выберите плейлист."
            return
        }
        val serverUrl = ocrServerUrl.value.trim()
        if (serverUrl.isBlank()) {
            snackbar.value = "Укажите адрес OCR сервера перед импортом."
            return
        }
        if (uris.isEmpty()) return

        val playlistName = uiState.value.selectedPlaylistName
        screenshotImportSession.value = ScreenshotImportSession(
            playlistId = playlistId,
            playlistName = playlistName,
            phase = ScreenshotImportPhase.UPLOADING,
            totalScreenshots = uris.size,
            statusMessage = "Подготавливаем и отправляем скриншоты...",
        )

        viewModelScope.launch {
            try {
                val normalizedResponse = if (uris.size == 1) {
                    screenshotImportSession.update { session ->
                        session?.copy(
                            phase = ScreenshotImportPhase.RECOGNIZING,
                            processedScreenshots = 0,
                            totalScreenshots = 1,
                            statusMessage = "Распознаем скриншот через vision-LLM...",
                        )
                    }
                    val single = screenshotImportRepository.submitSingleImage(serverUrl, uris.first())
                    OcrJobResponse(
                        jobId = "single-image",
                        status = "completed",
                        processedFiles = 1,
                        totalFiles = 1,
                        candidates = single.candidates,
                    )
                } else {
                    val job = screenshotImportRepository.submitJob(serverUrl, uris)
                    screenshotImportSession.update { session ->
                        session?.copy(
                            phase = ScreenshotImportPhase.RECOGNIZING,
                            statusMessage = "Скриншоты отправлены. Идет распознавание...",
                        )
                    }
                    val response = screenshotImportRepository.pollJob(serverUrl, job.jobId) { progress ->
                        updateImportProgress(progress, fallbackTotal = uris.size)
                    }
                    runCatching {
                        screenshotImportSession.update { session ->
                            session?.copy(
                                phase = ScreenshotImportPhase.MATCHING,
                                statusMessage = "Нормализуем названия и исполнителей...",
                            )
                        }
                        val normalized = screenshotImportRepository.normalizeCandidates(serverUrl, response.candidates)
                        response.copy(candidates = normalized.candidates)
                    }.getOrElse { error ->
                        Log.w(TAG, "normalizeCandidates failed reason=${LibraryRepository.describeThrowable(error)}", error)
                        response
                    }
                }
                completeImportMatching(normalizedResponse, fallbackTotal = uris.size)
            } catch (error: Throwable) {
                val message = LibraryRepository.toUserFacingMessage(error, "Сервис распознавания недоступен.")
                screenshotImportSession.update { session ->
                    session?.copy(
                        phase = ScreenshotImportPhase.FAILED,
                        errorMessage = message,
                        statusMessage = message,
                    )
                }
                snackbar.value = message
                Log.e(TAG, "importPlaylistScreenshots failed reason=${LibraryRepository.describeThrowable(error)}", error)
            }
        }
    }

    fun addScreenshotImportMatch(candidateId: String) {
        val session = screenshotImportSession.value ?: return
        val item = session.items.firstOrNull { it.recognized.candidateId == candidateId } ?: return
        val track = item.bestMatch ?: return
        if (item.status == ScreenshotImportItemStatus.ALREADY_IN_PLAYLIST || item.status == ScreenshotImportItemStatus.ADDED) {
            return
        }

        screenshotImportSession.update { current ->
            current?.copy(
                items = current.items.map { existing ->
                    if (existing.recognized.candidateId == candidateId) {
                        existing.copy(status = ScreenshotImportItemStatus.ADDING, message = "Добавляем в плейлист...")
                    } else {
                        existing
                    }
                },
            )
        }

        viewModelScope.launch {
            try {
                val blueprint = sourceCatalog.resolve(track)
                val importedTrack = repository.importDownloadedTrack(blueprint)
                repository.addTrackToPlaylist(importedTrack.id, session.playlistId)
                screenshotImportSession.update { current ->
                    current?.copy(
                        items = current.items.map { existing ->
                            if (existing.recognized.candidateId == candidateId) {
                                existing.copy(status = ScreenshotImportItemStatus.ADDED, message = "Трек добавлен в плейлист.")
                            } else {
                                existing
                            }
                        },
                    )
                }
                snackbar.value = "Трек добавлен в ${session.playlistName}."
            } catch (error: Throwable) {
                val message = LibraryRepository.toUserFacingMessage(error, "Не удалось добавить трек.")
                screenshotImportSession.update { current ->
                    current?.copy(
                        items = current.items.map { existing ->
                            if (existing.recognized.candidateId == candidateId) {
                                existing.copy(status = ScreenshotImportItemStatus.ADD_FAILED, message = message)
                            } else {
                                existing
                            }
                        },
                    )
                }
                snackbar.value = message
                Log.e(TAG, "addScreenshotImportMatch failed id=$candidateId reason=${LibraryRepository.describeThrowable(error)}", error)
            }
        }
    }

    fun addTrackToSelectedPlaylist(trackId: String) {
        val playlistId = selectedPlaylistId.value
        if (playlistId == null) {
            snackbar.value = "Сначала создайте плейлист."
            return
        }
        viewModelScope.launch {
            repository.addTrackToPlaylist(trackId, playlistId)
            snackbar.value = "Трек добавлен в плейлист."
        }
    }

    fun removeTrackFromSelectedPlaylist(trackId: String) {
        val playlistId = selectedPlaylistId.value ?: return
        viewModelScope.launch {
            repository.removeTrackFromPlaylist(trackId, playlistId)
            snackbar.value = "Трек удален из плейлиста."
        }
    }

    fun playTrackFromLibrary(trackId: String) {
        val state = uiState.value
        val queue = state.filteredTracks.ifEmpty { state.tracks }
        if (queue.isEmpty()) {
            snackbar.value = "Сначала импортируйте локальные аудиофайлы."
            return
        }
        playbackController.playQueue("Локальная медиатека", queue, trackId, shuffle = false)
    }

    fun playTrackFromSelectedPlaylist(trackId: String) {
        val state = uiState.value
        if (state.selectedPlaylistTracks.isEmpty()) {
            snackbar.value = "Плейлист пуст."
            return
        }
        playbackController.playQueue(state.selectedPlaylistName, state.selectedPlaylistTracks, trackId, shuffle = false)
    }

    fun playSelectedPlaylist() {
        val state = uiState.value
        val firstTrack = state.selectedPlaylistTracks.firstOrNull()
        if (firstTrack == null) {
            snackbar.value = "Плейлист пуст."
            return
        }
        playbackController.playQueue(state.selectedPlaylistName, state.selectedPlaylistTracks, firstTrack.id, shuffle = false)
    }

    fun shuffleSelectedPlaylist() {
        val state = uiState.value
        val randomTrack = state.selectedPlaylistTracks.shuffled().firstOrNull()
        if (randomTrack == null) {
            snackbar.value = "Плейлист пуст."
            return
        }
        playbackController.playQueue(state.selectedPlaylistName, state.selectedPlaylistTracks, randomTrack.id, shuffle = true)
    }

    fun togglePlayPause() = playbackController.togglePlayPause()
    fun playNext() = playbackController.playNext()
    fun playPrevious() = playbackController.playPrevious()
    fun toggleShuffle() = playbackController.toggleShuffle()
    fun seekTo(positionMs: Long) = playbackController.seekTo(positionMs)
    fun deleteLocalTrack(trackId: String) {
        viewModelScope.launch {
            val deleted = repository.deleteTrack(trackId)
            playbackController.removeTrack(trackId)
            snackbar.value = if (deleted) "Трек удален с устройства." else "Трек уже отсутствует."
        }
    }

    fun previewRemoteTrack(track: RemoteTrackCandidate) {
        viewModelScope.launch {
            try {
                val blueprint = sourceCatalog.resolve(track)
                playbackController.playPreview(
                    TrackRecord(
                        id = "preview:${track.stableKey()}",
                        uri = blueprint.url,
                        sourceUri = blueprint.url,
                        title = blueprint.title,
                        artist = blueprint.artist,
                        durationMs = 0L,
                        importedAtEpochMs = System.currentTimeMillis(),
                    ),
                )
            } catch (error: Throwable) {
                snackbar.value = LibraryRepository.toUserFacingMessage(error, "Не удалось открыть предпрослушивание.")
                Log.e(TAG, "previewRemoteTrack failed key=${track.stableKey()} reason=${LibraryRepository.describeThrowable(error)}", error)
            }
        }
    }

    fun setSelectedSourceFilter(filter: String) {
        selectedSourceFilter.value = filter
        if (sourceQuery.value.isNotBlank()) {
            searchSources(sourceQuery.value)
        }
    }

    fun searchSources(rawQuery: String) {
        viewModelScope.launch {
            val query = rawQuery.trim()
            sourceQuery.value = rawQuery
            Log.d(TAG, "searchSources start query=$query filter=${selectedSourceFilter.value}")
            if (query.isBlank()) {
                sourceResults.value = emptyList()
                sourceStatus.value = "Введите запрос для поиска."
                return@launch
            }
            try {
                val results = sourceCatalog.search(query, selectedSourceFilter.value)
                sourceResults.value = results
                sourceStatus.value = if (results.isEmpty()) "Ничего не найдено." else "Найдено: ${results.size}."
                Log.d(TAG, "searchSources done query=$query results=${results.size} status=${sourceStatus.value}")
            } catch (error: Throwable) {
                sourceResults.value = emptyList()
                sourceStatus.value = LibraryRepository.toUserFacingMessage(error, "Ошибка поиска источника.")
                snackbar.value = sourceStatus.value
                Log.e(TAG, "searchSources failed query=$query reason=${LibraryRepository.describeThrowable(error)}", error)
            }
        }
    }

    fun downloadRemoteTrack(track: RemoteTrackCandidate) {
        val key = track.stableKey()
        if (key in downloadingTrackKeys.value) return
        viewModelScope.launch {
            downloadingTrackKeys.value = downloadingTrackKeys.value + key
            Log.d(TAG, "downloadRemoteTrack start key=$key detail=${track.detailUrl} direct=${track.downloadUrl}")
            try {
                val blueprint = sourceCatalog.resolve(track)
                val importedTrack = repository.importDownloadedTrack(blueprint)
                val selectedPlaylist = selectedPlaylistId.value
                if (selectedPlaylist != null) repository.addTrackToPlaylist(importedTrack.id, selectedPlaylist)
                snackbar.value = if (selectedPlaylist != null) "Трек скачан и добавлен в текущий плейлист." else "Трек скачан в Музыка/YaNeoDex."
            } catch (error: Throwable) {
                snackbar.value = LibraryRepository.toUserFacingMessage(error, "Не удалось скачать трек.")
                Log.e(TAG, "downloadRemoteTrack failed key=$key reason=${LibraryRepository.describeThrowable(error)}", error)
            } finally {
                downloadingTrackKeys.value = downloadingTrackKeys.value - key
            }
        }
    }

    fun setYandexToken(token: String?) {
        yandexSyncManager.setToken(token)
        snackbar.value = if (!token.isNullOrBlank()) "Токен Яндекс Музыки сохранён." else "Токен сброшен."
    }

    fun toggleYandexDirectSource() {
        val current = yandexSyncManager.isDirectSourceEnabled.value
        yandexSyncManager.setDirectSourceEnabled(!current)
        snackbar.value = if (!current) "Прямой источник Яндекс Музыка включен." else "Прямой источник Яндекс отключен."
    }

    fun startYandexLikesSync() {
        viewModelScope.launch {
            yandexSyncManager.runLikesImportPipeline(sourceCatalog, repository)
        }
    }

    fun startYandexDeviceAuth() {
        viewModelScope.launch {
            yandexSyncManager.startDeviceAuth()
                .onSuccess { code ->
                    snackbar.value = "Код: ${code.userCode}. Подтвердите вход на ${code.verificationUrl}"
                    pollDeviceAuthLoop(code.deviceCode, code.interval, code.expiresIn)
                }
                .onFailure {
                    snackbar.value = "Ошибка запроса кода: ${it.message}"
                }
        }
    }

    private fun pollDeviceAuthLoop(deviceCode: String, intervalSeconds: Int, expiresInSeconds: Int) {
        viewModelScope.launch {
            val deadline = System.currentTimeMillis() + (expiresInSeconds * 1000L)
            while (System.currentTimeMillis() < deadline) {
                delay((intervalSeconds * 1000L).coerceAtLeast(4000L))
                val token = yandexSyncManager.pollDeviceAuthOnce()
                if (token != null) {
                    snackbar.value = "Успешная авторизация в Яндекс Музыке!"
                    break
                }
            }
        }
    }

    fun cancelYandexDeviceAuth() {
        yandexSyncManager.cancelDeviceAuth()
    }

    fun release() = Unit

    private suspend fun completeImportMatching(
        response: OcrJobResponse,
        fallbackTotal: Int,
    ) {
        val recognized = ScreenshotImportMatcher.deduplicate(response.candidates.map { it.toRecognizedCandidate() })
        if (recognized.isEmpty()) {
            screenshotImportSession.update { session ->
                session?.copy(
                    phase = ScreenshotImportPhase.COMPLETED,
                    processedScreenshots = response.totalFiles.takeIf { it > 0 } ?: fallbackTotal,
                    items = emptyList(),
                    statusMessage = "OCR завершен, но треки не распознаны.",
                )
            }
            return
        }

        screenshotImportSession.update { session ->
            session?.copy(
                phase = ScreenshotImportPhase.MATCHING,
                processedScreenshots = response.totalFiles.takeIf { it > 0 } ?: fallbackTotal,
                statusMessage = "Подбираем совпадения через текущий источник...",
                items = recognized.map { candidate ->
                    MatchedTrackCandidate(
                        recognized = candidate,
                        bestMatch = null,
                        matchScore = 0f,
                        status = ScreenshotImportItemStatus.RECOGNIZED,
                    )
                },
            )
        }

        val playlistTracks = uiState.value.selectedPlaylistTracks
        val matchedItems = recognized.mapIndexed { index, candidate ->
            screenshotImportSession.update { session ->
                session?.copy(
                    statusMessage = "Матчинг треков: ${index + 1} / ${recognized.size}",
                )
            }
            val results = searchCandidateMatches(candidate)
            ScreenshotImportMatcher.pickBestMatch(candidate, results, playlistTracks)
        }

        screenshotImportSession.update { session ->
            session?.copy(
                phase = ScreenshotImportPhase.COMPLETED,
                processedScreenshots = response.totalFiles.takeIf { it > 0 } ?: fallbackTotal,
                items = matchedItems,
                statusMessage = "Импорт завершен. Найдено совпадений: ${matchedItems.count { it.bestMatch != null }}.",
            )
        }
    }

    private fun updateImportProgress(
        response: OcrJobResponse,
        fallbackTotal: Int,
    ) {
        screenshotImportSession.update { session ->
            session?.copy(
                phase = ScreenshotImportPhase.RECOGNIZING,
                processedScreenshots = response.processedFiles,
                totalScreenshots = response.totalFiles.takeIf { it > 0 } ?: fallbackTotal,
                statusMessage = when (response.status.lowercase()) {
                    "queued" -> "Задача поставлена в очередь распознавания..."
                    "processing" -> "Распознаем ${response.processedFiles} из ${response.totalFiles.takeIf { it > 0 } ?: fallbackTotal} скриншотов..."
                    else -> "Идет распознавание..."
                },
            )
        }
    }

    private suspend fun searchCandidateMatches(candidate: com.codex.signalflow.importer.RecognizedTrackCandidate): List<RemoteTrackCandidate> {
        val allResults = mutableListOf<RemoteTrackCandidate>()
        ScreenshotImportMatcher.buildSearchQueries(candidate).forEach { query ->
            runCatching {
                allResults += sourceCatalog.searchAll(query)
            }.onFailure { error ->
                Log.w(TAG, "searchCandidateMatches query=$query failed reason=${LibraryRepository.describeThrowable(error)}")
            }
        }
        return allResults.distinctBy { it.stableKey() }
    }

    private fun RemoteTrackCandidate.stableKey(): String = "$sourceId|$title|$artist|${downloadUrl ?: detailUrl}"

    companion object {
        private const val TAG = "YaNeoDexVm"
    }
}

