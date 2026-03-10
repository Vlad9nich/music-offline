package com.codex.signalflow

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codex.signalflow.data.LibraryRepository
import com.codex.signalflow.data.PlaylistRecord
import com.codex.signalflow.data.TrackRecord
import com.codex.signalflow.player.PlaybackManager
import com.codex.signalflow.player.PlayerUiState
import com.codex.signalflow.player.VisualizerUiState
import com.codex.signalflow.source.MusicSourceCatalog
import com.codex.signalflow.source.RemoteTrackCandidate
import com.codex.signalflow.source.SourceDescriptor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
)

class MainViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository = LibraryRepository(application)
    private val playbackManager = PlaybackManager(application)
    private val sourceCatalog = MusicSourceCatalog()
    private val selectedPlaylistId = MutableStateFlow<String?>(null)
    private val libraryQuery = MutableStateFlow("")
    private val sourceQuery = MutableStateFlow("")
    private val sourceResults = MutableStateFlow<List<RemoteTrackCandidate>>(emptyList())
    private val downloadingTrackKeys = MutableStateFlow<Set<String>>(emptySet())
    private val sourceStatus = MutableStateFlow("Источник Ligaudio активен.")
    private val snackbar = MutableStateFlow<String?>(null)

    val snackbarMessage: StateFlow<String?> = snackbar
    val playerState: StateFlow<PlayerUiState> = playbackManager.playerState
    val visualizerState: StateFlow<VisualizerUiState> = playbackManager.visualizerState

    private val librarySelection = combine(repository.snapshot, selectedPlaylistId) { snapshot, playlistId ->
        val activePlaylist = snapshot.playlists.firstOrNull { it.id == playlistId }
            ?: snapshot.playlists.sortedBy { it.name.lowercase() }.firstOrNull()
        snapshot to activePlaylist
    }

    private val baseUiState = combine(librarySelection, libraryQuery, sourceQuery, sourceResults, sourceStatus) { selection, query, remoteQuery, results, sourceMessage ->
        val snapshot = selection.first
        val activePlaylist = selection.second
        val normalizedQuery = query.trim()
        val filteredTracks = snapshot.tracks
            .sortedWith(compareBy<TrackRecord> { it.title.lowercase() }.thenBy { it.artist.lowercase() })
            .filter {
                normalizedQuery.isBlank() ||
                    it.title.contains(normalizedQuery, ignoreCase = true) ||
                    it.artist.contains(normalizedQuery, ignoreCase = true)
            }
        val selectedTracks = activePlaylist?.trackIds.orEmpty().mapNotNull { trackId -> snapshot.tracks.firstOrNull { it.id == trackId } }
            .sortedWith(compareBy<TrackRecord> { it.title.lowercase() }.thenBy { it.artist.lowercase() })

        MainUiState(
            tracks = snapshot.tracks.sortedWith(compareBy<TrackRecord> { it.title.lowercase() }.thenBy { it.artist.lowercase() }),
            filteredTracks = filteredTracks,
            playlists = snapshot.playlists.sortedBy { it.name.lowercase() },
            selectedPlaylistId = activePlaylist?.id,
            selectedPlaylistTracks = selectedTracks,
            selectedPlaylistName = activePlaylist?.name ?: "Плейлист не выбран",
            libraryQuery = query,
            sourceQuery = remoteQuery,
            sourceResults = results,
            sourceDescriptors = sourceCatalog.descriptors,
            sourceStatus = sourceMessage,
        )
    }

    val uiState: StateFlow<MainUiState> = combine(baseUiState, downloadingTrackKeys) { state, downloadingKeys ->
        state.copy(downloadingTrackKeys = downloadingKeys)
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
    fun clearSnackbar() { snackbar.value = null }

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
        playbackManager.playQueue("Локальная медиатека", queue, trackId, shuffle = false)
    }

    fun playTrackFromSelectedPlaylist(trackId: String) {
        val state = uiState.value
        if (state.selectedPlaylistTracks.isEmpty()) {
            snackbar.value = "Плейлист пуст."
            return
        }
        playbackManager.playQueue(state.selectedPlaylistName, state.selectedPlaylistTracks, trackId, shuffle = false)
    }

    fun playSelectedPlaylist() {
        val state = uiState.value
        val firstTrack = state.selectedPlaylistTracks.firstOrNull()
        if (firstTrack == null) {
            snackbar.value = "Плейлист пуст."
            return
        }
        playbackManager.playQueue(state.selectedPlaylistName, state.selectedPlaylistTracks, firstTrack.id, shuffle = false)
    }

    fun shuffleSelectedPlaylist() {
        val state = uiState.value
        val randomTrack = state.selectedPlaylistTracks.shuffled().firstOrNull()
        if (randomTrack == null) {
            snackbar.value = "Плейлист пуст."
            return
        }
        playbackManager.playQueue(state.selectedPlaylistName, state.selectedPlaylistTracks, randomTrack.id, shuffle = true)
    }

    fun togglePlayPause() = playbackManager.togglePlayPause()
    fun playNext() = playbackManager.playNext()
    fun playPrevious() = playbackManager.playPrevious()
    fun toggleShuffle() = playbackManager.toggleShuffle()
    fun seekTo(positionMs: Long) = playbackManager.seekTo(positionMs)
    fun setVisualizerActive(active: Boolean) = playbackManager.setVisualizerActive(active)
    fun setVisualizerPermissionGranted(granted: Boolean) = playbackManager.setVisualizerPermissionGranted(granted)
    fun deleteLocalTrack(trackId: String) {
        viewModelScope.launch {
            val deleted = repository.deleteTrack(trackId)
            playbackManager.removeTrack(trackId)
            snackbar.value = if (deleted) "Трек удален с устройства." else "Трек уже отсутствует."
        }
    }

    fun previewRemoteTrack(track: RemoteTrackCandidate) {
        viewModelScope.launch {
            try {
                val blueprint = sourceCatalog.resolve(track)
                playbackManager.playPreview(
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

    fun searchSources(rawQuery: String) {
        viewModelScope.launch {
            if (!sourceCatalog.hasEnabledSources()) {
                sourceResults.value = emptyList()
                sourceStatus.value = "Источник не подключен."
                Log.w(TAG, "searchSources aborted: no enabled sources")
                return@launch
            }
            val query = rawQuery.trim()
            sourceQuery.value = rawQuery
            Log.d(TAG, "searchSources start query=$query")
            if (query.isBlank()) {
                sourceResults.value = emptyList()
                sourceStatus.value = "Введите запрос для поиска."
                Log.d(TAG, "searchSources aborted: blank query")
                return@launch
            }
            try {
                val results = sourceCatalog.searchAll(query)
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

    fun release() { playbackManager.release() }

    private fun RemoteTrackCandidate.stableKey(): String = "$sourceId|$title|$artist|${downloadUrl ?: detailUrl}"

    companion object {
        private const val TAG = "YaNeoDexVm"
    }
}

