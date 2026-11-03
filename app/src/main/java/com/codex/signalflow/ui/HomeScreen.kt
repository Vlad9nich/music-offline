package com.codex.signalflow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.signalflow.MainUiState
import com.codex.signalflow.player.PlayerUiState

@Composable
internal fun HomeScreen(
    uiState: MainUiState,
    playerState: PlayerUiState,
    onImportTracks: () -> Unit,
    onPlayPlaylist: () -> Unit,
    onShufflePlaylist: () -> Unit,
    onOpenParser: () -> Unit,
    onOpenLocal: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onPlayTrack: (String) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val localPreview = uiState.tracks.take(5)
    val playlistPreview = uiState.selectedPlaylistTracks.take(5)

    ScreenColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 140.dp, top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // System Status Hero Panel
        item {
            HeroPanel(
                title = "СТАТУС: В СЕТИ",
                subtitle = "ПАМЯТЬ: ${uiState.tracks.size} ТРЕКОВ • ОЧЕРЕДЬ: ${uiState.selectedPlaylistTracks.size}",
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PrimaryActionButton(label = "ИМПОРТ", icon = Icons.Rounded.FolderOpen, onClick = onImportTracks)
                    SecondaryActionButton(label = "СЛУШАТЬ", icon = Icons.Rounded.PlayArrow, onClick = onPlayPlaylist, compact = true)
                    SecondaryActionButton(label = "МИКС", icon = Icons.Rounded.Shuffle, onClick = onShufflePlaylist, compact = true)
                }
            }
        }

        // Fast Portal Links (Horizontal Row)
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    PortalCard(
                        title = "ПОИСК ТРЕКОВ",
                        caption = if (uiState.sourceResults.isEmpty()) "ПОТОКОВОЕ СКАЧИВАНИЕ" else "НАЙДЕНО ТРЕКОВ: ${uiState.sourceResults.size}",
                        accent = Color.White,
                        onClick = onOpenParser,
                    )
                }
                item {
                    PortalCard(
                        title = "БИБЛИОТЕКА",
                        caption = "${uiState.tracks.size} ФАЙЛОВ ГОТОВО",
                        accent = Color.White,
                        onClick = onOpenLocal,
                    )
                }
                item {
                    PortalCard(
                        title = "ПЛЕЙЛИСТЫ",
                        caption = "АКТИВНЫЙ: ${uiState.selectedPlaylistName.uppercase()}",
                        accent = Color.White,
                        onClick = onOpenPlaylists,
                    )
                }
            }
        }

        // Active Player Status Monitor
        item {
            PanelCard(title = "АКТИВНАЯ ДЕКА", accent = Color.White) {
                Text(
                    text = playerState.currentTrack?.title ?: "ОЖИДАНИЕ ВЫБОРА ТРЕКА",
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = playerState.currentTrack?.artist ?: "ВЫБЕРИТЕ ТРЕК ДЛЯ ВОСПРОИЗВЕДЕНИЯ",
                    color = Color(0xFF7A8498),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                )
            }
        }

        // Active Playlist Section
        item {
            Text(
                text = "ОЧЕРЕДЬ: ${uiState.selectedPlaylistName.uppercase()}",
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 0.5.sp,
            )
        }

        if (playlistPreview.isEmpty()) {
            item {
                EmptyBlock(
                    title = "ОЧЕРЕДЬ ПУСТА",
                    body = "В ТЕКУЩЕМ ПЛЕЙЛИСТЕ НЕТ ТРЕКОВ",
                )
            }
        } else {
            items(playlistPreview, key = { "playlist_preview_${it.id}" }) { track ->
                CompactTrackCard(
                    track = track,
                    accent = Color.White,
                    meta = uiState.selectedPlaylistName,
                    onClick = { onPlayTrack(track.id) },
                )
            }
        }

        // Local Storage Section
        item {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "ХРАНИЛИЩЕ: ЛОКАЛЬНЫЕ ТРЕКИ (${uiState.tracks.size})",
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 0.5.sp,
            )
        }

        if (localPreview.isEmpty()) {
            item {
                EmptyBlock(
                    title = "ХРАНИЛИЩЕ ПУСТО",
                    body = "ЛОКАЛЬНЫХ ФАЙЛОВ НЕ НАЙДЕНО. НАЖМИТЕ «ИМПОРТ» ВЫШЕ",
                )
            }
        } else {
            items(localPreview, key = { "local_preview_${it.id}" }) { track ->
                CompactTrackCard(
                    track = track,
                    accent = Color.White,
                    meta = "ОФФЛАЙН",
                    onClick = { onPlayTrack(track.id) },
                )
            }
        }
    }
}
