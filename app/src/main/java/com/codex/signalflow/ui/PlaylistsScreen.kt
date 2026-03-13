package com.codex.signalflow.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.codex.signalflow.MainUiState

@Composable
internal fun PlaylistsScreen(
    uiState: MainUiState,
    onSelectPlaylist: (String) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onPlayPlaylist: () -> Unit,
    onShufflePlaylist: () -> Unit,
    onRemoveTrackFromPlaylist: (String) -> Unit,
    onPlayTrack: (String) -> Unit,
    onImportPlaylistScreenshots: () -> Unit,
    onOcrServerUrlChange: (String) -> Unit,
    onOcrAuthTokenChange: (String) -> Unit,
    onDismissScreenshotImport: () -> Unit,
    onAddScreenshotImportMatch: (String) -> Unit,
) {
    var draftName by rememberSaveable { mutableStateOf("") }
    var isDetailOpen by rememberSaveable(uiState.selectedPlaylistId) { mutableStateOf(false) }
    var playlistQuery by rememberSaveable(uiState.selectedPlaylistId) { mutableStateOf("") }
    var selectedArtist by rememberSaveable(uiState.selectedPlaylistId) { mutableStateOf<String?>(null) }
    var showMatchedOnly by rememberSaveable(uiState.screenshotImportSession?.playlistId) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val authorFilters = remember(uiState.selectedPlaylistTracks) {
        uiState.selectedPlaylistTracks.map { it.artist }.distinct().sorted()
    }
    val filteredPlaylistTracks = remember(uiState.selectedPlaylistTracks, playlistQuery, selectedArtist) {
        uiState.selectedPlaylistTracks.filter { track ->
            val matchesQuery = playlistQuery.isBlank() ||
                track.title.contains(playlistQuery, ignoreCase = true) ||
                track.artist.contains(playlistQuery, ignoreCase = true)
            val matchesArtist = selectedArtist == null || track.artist == selectedArtist
            matchesQuery && matchesArtist
        }
    }
    val importSession = uiState.screenshotImportSession
    val visibleImportItems = remember(importSession, showMatchedOnly) {
        val items = importSession?.items.orEmpty()
        if (showMatchedOnly) {
            items.filter { it.bestMatch != null }
        } else {
            items
        }
    }

    BackHandler(enabled = importSession != null || isDetailOpen) {
        if (importSession != null) {
            onDismissScreenshotImport()
        } else {
            isDetailOpen = false
        }
    }

    ScreenColumn(
        state = listState,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 160.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!isDetailOpen) {
            item {
                PanelCard(title = "Новый плейлист", accent = NeonCyan) {
                    CyberTextField(
                        value = draftName,
                        onValueChange = { draftName = it },
                        placeholder = "Название плейлиста",
                        imeAction = ImeAction.Done,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        PrimaryActionButton(
                            modifier = Modifier.weight(1f),
                            label = "Создать",
                            icon = Icons.Rounded.Add,
                            onClick = {
                                onCreatePlaylist(draftName)
                                draftName = ""
                            },
                        )
                        SecondaryActionButton(label = "Пуск", icon = Icons.Rounded.PlayArrow, onClick = onPlayPlaylist, compact = true)
                        SecondaryActionButton(label = "Шафл", icon = Icons.Rounded.Shuffle, onClick = onShufflePlaylist, compact = true)
                    }
                }
            }
        }
        item {
            if (uiState.playlists.isEmpty()) {
                EmptyBlock(
                    title = "Плейлистов пока нет",
                    body = "Создайте плейлист, затем импортируйте треки или добавьте их с вкладки локальной медиатеки.",
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.playlists, key = { it.id }) { playlist ->
                        PlaylistChip(
                            playlist = playlist,
                            selected = playlist.id == uiState.selectedPlaylistId,
                            onClick = {
                                onSelectPlaylist(playlist.id)
                                isDetailOpen = true
                                playlistQuery = ""
                                selectedArtist = null
                            },
                        )
                    }
                }
            }
        }
        if (!isDetailOpen) {
            item {
                PanelCard(title = "Активный плейлист", accent = NeonPink) {
                    Text(uiState.selectedPlaylistName, color = InkText, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Треков: ${uiState.selectedPlaylistTracks.size}",
                        color = MutedText,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Нажмите на плейлист выше, чтобы открыть отдельный экран с поиском и фильтром по авторам.",
                        color = MutedText,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        } else if (importSession != null) {
            item {
                PlaylistScreenshotImportPanel(
                    session = importSession,
                    serverUrl = uiState.ocrServerUrl,
                    showMatchedOnly = showMatchedOnly,
                    onServerUrlChange = onOcrServerUrlChange,
                    onBack = onDismissScreenshotImport,
                    onToggleMatchedOnly = { showMatchedOnly = !showMatchedOnly },
                    onPickScreenshots = onImportPlaylistScreenshots,
                )
            }
            if (visibleImportItems.isEmpty()) {
                item {
                    EmptyBlock(
                        title = "Пока пусто",
                        body = importSession.errorMessage ?: "После распознавания здесь появятся треки для переноса.",
                    )
                }
            } else {
                items(visibleImportItems, key = { it.recognized.candidateId }) { item ->
                    ScreenshotImportRow(
                        item = item,
                        onAdd = { onAddScreenshotImportMatch(item.recognized.candidateId) },
                    )
                }
            }
        } else {
            item {
                PanelCard(title = "Плейлист", accent = NeonPink) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SecondaryActionButton(
                            label = "К списку",
                            icon = Icons.Rounded.ArrowBack,
                            onClick = { isDetailOpen = false },
                            compact = true,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(uiState.selectedPlaylistName, color = InkText, fontWeight = FontWeight.Bold)
                            Text("Треков: ${uiState.selectedPlaylistTracks.size}", color = MutedText, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Адрес OCR сервера",
                        color = MutedText,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    CyberTextField(
                        value = uiState.ocrServerUrl,
                        onValueChange = onOcrServerUrlChange,
                        placeholder = "https://your-vps.example.com",
                        imeAction = ImeAction.Done,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Bearer token OCR сервера",
                        color = MutedText,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    CyberTextField(
                        value = uiState.ocrAuthToken,
                        onValueChange = onOcrAuthTokenChange,
                        placeholder = "OCR_API_TOKEN",
                        imeAction = ImeAction.Done,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SecondaryActionButton(
                            label = "Импорт со скриншотов",
                            icon = Icons.Rounded.Add,
                            onClick = onImportPlaylistScreenshots,
                            compact = true,
                        )
                        Text(
                            text = "Скриншоты распознаются на VPS и затем матчатся через текущий источник.",
                            color = MutedText,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    CyberTextField(
                        value = playlistQuery,
                        onValueChange = { playlistQuery = it },
                        placeholder = "Поиск внутри плейлиста",
                        imeAction = ImeAction.Search,
                    )
                    if (authorFilters.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    label = "Все",
                                    selected = selectedArtist == null,
                                    onClick = { selectedArtist = null },
                                )
                            }
                            items(authorFilters, key = { it }) { artist ->
                                FilterChip(
                                    label = artist,
                                    selected = selectedArtist == artist,
                                    onClick = { selectedArtist = if (selectedArtist == artist) null else artist },
                                )
                            }
                        }
                    }
                }
            }
            if (filteredPlaylistTracks.isEmpty()) {
                item {
                    EmptyBlock(
                        title = "Ничего не найдено",
                        body = "Измените поиск или снимите фильтр по автору.",
                    )
                }
            } else {
                items(filteredPlaylistTracks, key = { it.id }) { track ->
                    TrackRow(
                        track = track,
                        primaryLabel = "Пуск",
                        secondaryLabel = "Убрать",
                        onPrimary = { onPlayTrack(track.id) },
                        onSecondary = { onRemoveTrackFromPlaylist(track.id) },
                    )
                }
            }
        }
    }
}
