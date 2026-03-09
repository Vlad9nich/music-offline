package com.codex.signalflow

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codex.signalflow.data.LibraryRepository
import com.codex.signalflow.data.PlaylistRecord
import com.codex.signalflow.data.TrackRecord
import com.codex.signalflow.player.PlaybackManager
import com.codex.signalflow.player.PlayerUiState
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
    val sourceStatus: String = "Источник poiskm.net активен.",
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
    private val sourceStatus = MutableStateFlow("Источник poiskm.net активен.")
    private val snackbar = MutableStateFlow<String?>(null)

    val snackbarMessage: StateFlow<String?> = snackbar
    val playerState: StateFlow<PlayerUiState> = playbackManager.playerState

    private val librarySelection = combine(repository.snapshot, selectedPlaylistId) { snapshot, playlistId ->
        val activePlaylist = snapshot.playlists.firstOrNull { it.id == playlistId }
            ?: snapshot.playlists.sortedBy { it.name.lowercase() }.firstOrNull()
        snapshot to activePlaylist
    }

    private val baseUiState = combine(
        librarySelection,
        libraryQuery,
        sourceQuery,
        sourceResults,
        sourceStatus,
    ) { selection, query, remoteQuery, results, sourceMessage ->
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
        val selectedTracks = activePlaylist?.trackIds.orEmpty().mapNotNull { trackId ->
            snapshot.tracks.firstOrNull { it.id == trackId }
        }.sortedWith(compareBy<TrackRecord> { it.title.lowercase() }.thenBy { it.artist.lowercase() })

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
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(sourceDescriptors = sourceCatalog.descriptors),
    )

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

    fun setLibraryQuery(value: String) {
        libraryQuery.value = value
    }

    fun setSourceQuery(value: String) {
        sourceQuery.value = value
    }

    fun clearSnackbar() {
        snackbar.value = null
    }

    fun importTracks(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val importedTracks = repository.importTracks(uris)
            val selectedPlaylist = selectedPlaylistId.value
            if (selectedPlaylist != null) {
                importedTracks.forEach { track ->
                    repository.addTrackToPlaylist(track.id, selectedPlaylist)
                }
            }
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
            snackbar.value = if (name.trim().isBlank()) {
                "Название плейлиста пустое."
            } else {
                "Плейлист создан."
            }
        }
    }

    fun selectPlaylist(playlistId: String) {
        selectedPlaylistId.value = playlistId
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
        playbackManager.playQueue(
            label = "Локальная медиатека",
            tracks = queue,
            startTrackId = trackId,
            shuffle = false,
        )
    }

    fun playTrackFromSelectedPlaylist(trackId: String) {
        val state = uiState.value
        if (state.selectedPlaylistTracks.isEmpty()) {
            snackbar.value = "Плейлист пуст."
            return
        }
        playbackManager.playQueue(
            label = state.selectedPlaylistName,
            tracks = state.selectedPlaylistTracks,
            startTrackId = trackId,
            shuffle = false,
        )
    }

    fun playSelectedPlaylist() {
        val state = uiState.value
        val firstTrack = state.selectedPlaylistTracks.firstOrNull()
        if (firstTrack == null) {
            snackbar.value = "Плейлист пуст."
            return
        }
        playbackManager.playQueue(
            label = state.selectedPlaylistName,
            tracks = state.selectedPlaylistTracks,
            startTrackId = firstTrack.id,
            shuffle = false,
        )
    }

    fun shuffleSelectedPlaylist() {
        val state = uiState.value
        val firstTrack = state.selectedPlaylistTracks.firstOrNull()
        if (firstTrack == null) {
            snackbar.value = "Плейлист пуст."
            return
        }
        playbackManager.playQueue(
            label = state.selectedPlaylistName,
            tracks = state.selectedPlaylistTracks,
            startTrackId = firstTrack.id,
            shuffle = true,
        )
    }

    fun togglePlayPause() = playbackManager.togglePlayPause()

    fun playNext() = playbackManager.playNext()

    fun playPrevious() = playbackManager.playPrevious()

    fun toggleShuffle() = playbackManager.toggleShuffle()

    fun searchSources() {
        viewModelScope.launch {
            if (!sourceCatalog.hasEnabledSources()) {
                sourceResults.value = emptyList()
                sourceStatus.value = "Каркас парсера готов, но активный источник не подключен."
                snackbar.value = "Шаблон парсера готов."
                return@launch
            }

            val query = sourceQuery.value.trim()
            if (query.isBlank()) {
                sourceResults.value = emptyList()
                sourceStatus.value = "Введите запрос для поиска по внешнему источнику."
                return@launch
            }

            val results = sourceCatalog.searchAll(query)
            sourceResults.value = results
            sourceStatus.value = "Найдено совпадений: ${results.size}."
        }
    }

    fun downloadRemoteTrack(track: RemoteTrackCandidate) {
        val key = track.stableKey()
        if (key in downloadingTrackKeys.value) {
            return
        }

        viewModelScope.launch {
            downloadingTrackKeys.value = downloadingTrackKeys.value + key
            try {
                val blueprint = sourceCatalog.resolve(track)
                val importedTrack = repository.importDownloadedTrack(blueprint)
                val selectedPlaylist = selectedPlaylistId.value
                if (selectedPlaylist != null) {
                    repository.addTrackToPlaylist(importedTrack.id, selectedPlaylist)
                }
                snackbar.value = if (selectedPlaylist != null) {
                    "Трек скачан на устройство и добавлен в текущий плейлист."
                } else {
                    "Трек скачан на устройство в Музыка/YaNeoDex."
                }
            } catch (error: Throwable) {
                val reason = error.message?.trim().orEmpty().ifBlank { "Не удалось скачать трек." }
                snackbar.value = reason
            } finally {
                downloadingTrackKeys.value = downloadingTrackKeys.value - key
            }
        }
    }

    fun release() {
        playbackManager.release()
    }

    private fun RemoteTrackCandidate.stableKey(): String {
        return "$sourceId|$title|$artist|${downloadUrl ?: detailUrl}"
    }
}


