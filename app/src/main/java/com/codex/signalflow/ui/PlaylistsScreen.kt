package com.codex.signalflow.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.signalflow.MainUiState
import com.codex.signalflow.data.PlaylistRecord

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
    onSetYandexToken: (String?) -> Unit = {},
    onToggleYandexDirectSource: () -> Unit = {},
    onStartYandexLikesSync: () -> Unit = {},
    onStartYandexDeviceAuth: () -> Unit = {},
    onCancelYandexDeviceAuth: () -> Unit = {},
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
        if (!isDetailOpen && importSession == null) {
            // System Playlist Hub Hero Panel
            item {
                HeroPanel(
                    title = "СИСТЕМА ПЛЕЙЛИСТОВ",
                    subtitle = "БАЗА: ${uiState.playlists.size} СПИСКОВ • В ЭФИРЕ: ${uiState.selectedPlaylistName.uppercase()}",
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PrimaryActionButton(
                            label = "СЛУШАТЬ",
                            icon = Icons.Rounded.PlayArrow,
                            onClick = onPlayPlaylist,
                            compact = true,
                        )
                        SecondaryActionButton(
                            label = "МИКС",
                            icon = Icons.Rounded.Shuffle,
                            onClick = onShufflePlaylist,
                            compact = true,
                        )
                        SecondaryActionButton(
                            label = "СКАНЕР OCR",
                            icon = Icons.Rounded.DocumentScanner,
                            onClick = onImportPlaylistScreenshots,
                            compact = true,
                        )
                    }
                }
            }

            // Yandex Music Liked Tracks Sync Card (Способ Б + Загрузчик llistochek/yandex-music-downloader)
            item {
                val context = LocalContext.current
                var yandexTokenInput by rememberSaveable(uiState.yandexToken) {
                    mutableStateOf(uiState.yandexToken.orEmpty())
                }
                val syncProgress = uiState.yandexSyncProgress

                PanelCard(
                    title = "СИНХРОНИЗАЦИЯ: ЯНДЕКС МУЗЫКА",
                    accent = Color.White,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (uiState.yandexToken.isNullOrBlank()) "ТОКЕН: НЕ ЗАДАН" else "ТОКЕН: АКТИВЕН",
                            color = if (uiState.yandexToken.isNullOrBlank()) Color(0xFFE28B8B) else Color(0xFF00FF66),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                        )
                        SecondaryActionButton(
                            label = if (uiState.isYandexDirectSourceEnabled) "РЕЗЕРВ ЯНДЕКС: ВКЛ" else "РЕЗЕРВ ЯНДЕКС: ВЫКЛ",
                            icon = Icons.Rounded.Sync,
                            onClick = onToggleYandexDirectSource,
                            compact = true,
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // Device Auth Box if currently active
                    if (uiState.yandexDeviceCode != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF040A10), CutSmallShape)
                                .border(1.dp, Color(0xFF00FF66), CutSmallShape)
                                .padding(12.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "● АВТОРИЗАЦИЯ YANDEX DEVICE FLOW",
                                    color = Color(0xFF00FF66),
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                )
                                Text(
                                    text = "Код подтверждения: ${uiState.yandexDeviceCode.userCode.uppercase()}\nПерейдите на ya.ru/device и подтвердите вход.",
                                    color = Color(0xFFD6E2F0),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    PrimaryActionButton(
                                        modifier = Modifier.weight(1f),
                                        label = "ОТКРЫТЬ YA.RU/DEVICE",
                                        icon = Icons.Rounded.OpenInBrowser,
                                        onClick = {
                                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uiState.yandexDeviceCode.verificationUrl))
                                            context.startActivity(browserIntent)
                                        },
                                        compact = true,
                                    )
                                    SecondaryActionButton(
                                        label = "ОТМЕНА",
                                        icon = Icons.Rounded.Clear,
                                        onClick = onCancelYandexDeviceAuth,
                                        compact = true,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    } else {
                        // Quick Action Buttons: Device Flow vs Start Sync
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SecondaryActionButton(
                                modifier = Modifier.weight(1f),
                                label = "ВОЙТИ ПО КОДУ (YA.RU)",
                                icon = Icons.Rounded.Key,
                                onClick = onStartYandexDeviceAuth,
                                compact = true,
                            )
                            PrimaryActionButton(
                                modifier = Modifier.weight(1f),
                                label = if (syncProgress.isRunning) "КОНВЕЙЕР..." else "СИНХР. ЛАЙКИ",
                                icon = Icons.Rounded.Sync,
                                onClick = onStartYandexLikesSync,
                                compact = true,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    // Manual Token Input Row (if user already has token)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            CyberTextField(
                                value = yandexTokenInput,
                                onValueChange = { yandexTokenInput = it },
                                placeholder = "ИЛИ ВСТАВЬТЕ ТОКЕН ВРУЧНУЮ...",
                                imeAction = ImeAction.Done,
                            )
                        }
                        SecondaryActionButton(
                            label = "СОХРАНИТЬ",
                            icon = Icons.Rounded.Check,
                            onClick = { onSetYandexToken(yandexTokenInput) },
                            compact = true,
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    // Implicit OAuth Link
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "ИНСТРУКЦИЯ / ПОЛУЧИТЬ ТОКЕН:",
                            color = Color(0xFF6E8096),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                        )
                        SecondaryActionButton(
                            label = "ССЫЛКА НА ТОКЕН",
                            icon = Icons.Rounded.OpenInBrowser,
                            onClick = {
                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://oauth.yandex.ru/authorize?response_type=token&client_id=23cabbbdc6cd418abb4b39c32c41195d"))
                                context.startActivity(browserIntent)
                            },
                            compact = true,
                        )
                    }

                    // Active progress display
                    if (syncProgress.isRunning || syncProgress.message.isNotBlank() || syncProgress.error != null) {
                        Spacer(Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF080D14), CutSmallShape)
                                .border(1.dp, Color(0xFF202A38), CutSmallShape)
                                .padding(10.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (syncProgress.error != null) {
                                    Text(
                                        text = "[!] ОШИБКА: ${syncProgress.error}",
                                        color = Color(0xFFFF5555),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                    )
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = if (syncProgress.total > 0) "[ ${syncProgress.current} / ${syncProgress.total} ]" else "ПОДГОТОВКА...",
                                            color = Color.White,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                        )
                                        Text(
                                            text = if (syncProgress.isRunning) "В ПРОЦЕССЕ" else "ГОТОВО",
                                            color = if (syncProgress.isRunning) Color(0xFF00FF66) else Color(0xFF8E9BAE),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                        )
                                    }
                                    if (syncProgress.currentTrackTitle.isNotBlank()) {
                                        Text(
                                            text = syncProgress.currentTrackTitle,
                                            color = Color.White,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Text(
                                        text = syncProgress.message,
                                        color = Color(0xFF8E9BAE),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                    )
                                    if (syncProgress.total > 0) {
                                        Spacer(Modifier.height(4.dp))
                                        val fraction = (syncProgress.current.toFloat() / syncProgress.total.toFloat()).coerceIn(0f, 1f)
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(3.dp)
                                                .background(Color(0xFF1B2330)),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(fraction)
                                                    .fillMaxHeight()
                                                    .background(Color.White),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Quick Create Bar
            item {
                PanelCard(title = "СОЗДАТЬ ПЛЕЙЛИСТ", accent = Color.White) {
                    CyberTextField(
                        value = draftName,
                        onValueChange = { draftName = it },
                        placeholder = "ВВЕДИТЕ НАЗВАНИЕ ПЛЕЙЛИСТА...",
                        imeAction = ImeAction.Done,
                    )
                    Spacer(Modifier.height(8.dp))
                    PrimaryActionButton(
                        modifier = Modifier.fillMaxWidth(),
                        label = "СОЗДАТЬ ПЛЕЙЛИСТ",
                        icon = Icons.Rounded.Add,
                        onClick = {
                            if (draftName.isNotBlank()) {
                                onCreatePlaylist(draftName.trim())
                                draftName = ""
                            }
                        },
                    )
                }
            }

            // Playlist Cards List Header
            item {
                SectionLabel(title = "ДОСТУПНЫЕ ПЛЕЙЛИСТЫ")
            }

            if (uiState.playlists.isEmpty()) {
                item {
                    EmptyBlock(
                        title = "ПЛЕЙЛИСТОВ НЕТ",
                        body = "НЕТ СОХРАНЁННЫХ ПЛЕЙЛИСТОВ. СОЗДАЙТЕ ПЕРВЫЙ ВЫШЕ ИЛИ ИСПОЛЬЗУЙТЕ СКАНЕР OCR",
                    )
                }
            } else {
                items(uiState.playlists, key = { it.id }) { playlist ->
                    val isSelected = playlist.id == uiState.selectedPlaylistId
                    PlaylistCard(
                        playlist = playlist,
                        isSelected = isSelected,
                        trackCount = playlist.trackIds.size,
                        onClick = {
                            onSelectPlaylist(playlist.id)
                            isDetailOpen = true
                            playlistQuery = ""
                            selectedArtist = null
                        },
                        onPlay = {
                            onSelectPlaylist(playlist.id)
                            onPlayPlaylist()
                        },
                        onShuffle = {
                            onSelectPlaylist(playlist.id)
                            onShufflePlaylist()
                        },
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
            // Playlist Detailed Deck View
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
                                fontSize = 14.sp,
                            )
                            Text(
                                text = "ТРЕКОВ: ${uiState.selectedPlaylistTracks.size}",
                                color = Color(0xFF7A8498),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                            )
                        }
                        PrimaryActionButton(
                            label = "ПУСК",
                            icon = Icons.Rounded.PlayArrow,
                            onClick = onPlayPlaylist,
                            compact = true,
                        )
                        SecondaryActionButton(
                            label = "МИКС",
                            icon = Icons.Rounded.Shuffle,
                            onClick = onShufflePlaylist,
                            compact = true,
                        )
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

@Composable
private fun PlaylistCard(
    playlist: PlaylistRecord,
    isSelected: Boolean,
    trackCount: Int,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .tactileClickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF101622) else Color(0xFF0A0D14),
        ),
        shape = CutPanelShape,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) Color.White else Color(0xFF202A38),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // DedSec Playlist Cassette Badge
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CutSmallShape)
                    .background(if (isSelected) Color.White else Color(0xFF141A26))
                    .border(1.dp, if (isSelected) Color.White else Color(0xFF283446), CutSmallShape),
                contentAlignment = Alignment.Center,
            ) {
                DedSecPlaylistsNavIcon(
                    tint = if (isSelected) Color.Black else Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }

            // Playlist info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name.uppercase(),
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "$trackCount ТРЕКОВ",
                        color = Color(0xFF8E9BAE),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    )
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .background(Color.White, CutSmallShape)
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = "В ЭФИРЕ",
                                color = Color.Black,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            // Quick Play Actions
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SecondaryActionButton(
                    label = "МИКС",
                    icon = Icons.Rounded.Shuffle,
                    onClick = onShuffle,
                    compact = true,
                )
                PrimaryActionButton(
                    label = "ПУСК",
                    icon = Icons.Rounded.PlayArrow,
                    onClick = onPlay,
                    compact = true,
                )
            }
        }
    }
}

internal fun playlistTrackItemKey(index: Int, track: com.codex.signalflow.data.TrackRecord): String {
    return "${track.id}:$index"
}
