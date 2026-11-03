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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 140.dp, top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!isDetailOpen) {
            item {
                PanelCard(title = "СОЗДАТЬ ПЛЕЙЛИСТ", accent = Color.White) {
                    CyberTextField(
                        value = draftName,
                        onValueChange = { draftName = it },
                        placeholder = "ВВЕДИТЕ НАЗВАНИЕ ПЛЕЙЛИСТА...",
                        imeAction = ImeAction.Done,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PrimaryActionButton(
                            modifier = Modifier.weight(1f),
                            label = "СОЗДАТЬ",
                            icon = Icons.Rounded.Add,
                            onClick = {
                                onCreatePlaylist(draftName)
                                draftName = ""
                            },
                        )
                        SecondaryActionButton(label = "СЛУШАТЬ", icon = Icons.Rounded.PlayArrow, onClick = onPlayPlaylist, compact = true)
                        SecondaryActionButton(label = "МИКС", icon = Icons.Rounded.Shuffle, onClick = onShufflePlaylist, compact = true)
                    }
                }
            }
        }
        item {
            if (uiState.playlists.isEmpty()) {
                EmptyBlock(
                    title = "ПЛЕЙЛИСТОВ НЕТ",
                    body = "НЕТ СОХРАНЁННЫХ ПЛЕЙЛИСТОВ. СОЗДАЙТЕ ПЕРВЫЙ ВЫШЕ",
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
                PanelCard(title = "АКТИВНЫЙ: ${uiState.selectedPlaylistName.uppercase()}", accent = Color.White) {
                    Text(
                        text = "ВСЕГО ТРЕКОВ: ${uiState.selectedPlaylistTracks.size}",
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "НАЖМИТЕ НА ПЛЕЙЛИСТ ВЫШЕ ДЛЯ ОТКРЫТИЯ СПИСКА",
                        color = Color(0xFF7A8498),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
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
                        title = "СКАНИРОВАНИЕ",
                        body = importSession.errorMessage ?: "ОЖИДАНИЕ ОБРАБОТКИ СКРИНШОТОВ",
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
                PanelCard(title = "ДЕКА: ${uiState.selectedPlaylistName.uppercase()}", accent = Color.White) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SecondaryActionButton(
                            label = "НАЗАД",
                            icon = Icons.AutoMirrored.Rounded.ArrowBack,
                            onClick = { isDetailOpen = false },
                            compact = true,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = uiState.selectedPlaylistName.uppercase(),
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                            )
                            Text(
                                text = "ВСЕГО ТРЕКОВ: ${uiState.selectedPlaylistTracks.size}",
                                color = Color(0xFF7A8498),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    CyberTextField(
                        value = playlistQuery,
                        onValueChange = { playlistQuery = it },
                        placeholder = "ПОИСК ТРЕКА В ПЛЕЙЛИСТЕ...",
                        imeAction = ImeAction.Search,
                    )
                    if (authorFilters.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item {
                                FilterChip(
                                    label = "ВСЕ",
                                    selected = selectedArtist == null,
                                    onClick = { selectedArtist = null },
                                )
                            }
                            items(authorFilters, key = { it }) { artist ->
                                FilterChip(
                                    label = artist.uppercase(),
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
                        title = "НЕТ СОВПАДЕНИЙ",
                        body = "ПО ТЕКУЩЕМУ ЗАПРОСУ ТРЕКИ НЕ НАЙДЕНЫ",
                    )
                }
            } else {
                itemsIndexed(
                    items = filteredPlaylistTracks,
                    key = { index, track -> playlistTrackItemKey(index, track) },
                ) { _, track ->
                    TrackRow(
                        track = track,
                        primaryLabel = "СЛУШАТЬ",
                        secondaryLabel = "УДАЛИТЬ",
                        onPrimary = { onPlayTrack(track.id) },
                        onSecondary = { onRemoveTrackFromPlaylist(track.id) },
                    )
                }
            }
        }
    }
}

internal fun playlistTrackItemKey(index: Int, track: com.codex.signalflow.data.TrackRecord): String {
    return "${track.id}:$index"
}
