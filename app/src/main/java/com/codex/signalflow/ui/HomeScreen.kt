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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    val localPreview = uiState.tracks.take(4)
    val playlistPreview = uiState.selectedPlaylistTracks.take(4)

    ScreenColumn(
        state = listState,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 160.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            HeroPanel(
                title = "Система готова",
                subtitle = "Плеер и очередь держат выбранный плейлист под постоянным контролем.",
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PrimaryActionButton(label = "Импорт", icon = Icons.Rounded.FolderOpen, onClick = onImportTracks)
                    SecondaryActionButton(label = "Пуск", icon = Icons.Rounded.PlayArrow, onClick = onPlayPlaylist, compact = true)
                    SecondaryActionButton(label = "Шафл", icon = Icons.Rounded.Shuffle, onClick = onShufflePlaylist, compact = true)
                }
            }
        }
        item {
            SectionLabel("Быстрый доступ")
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    PortalCard(
                        title = "Парсер",
                        caption = if (uiState.sourceResults.isEmpty()) uiState.sourceStatus else "Найдено ${uiState.sourceResults.size} треков",
                        accent = NeonCyan,
                        onClick = onOpenParser,
                    )
                }
                item {
                    PortalCard(
                        title = "Локальные",
                        caption = "Треков: ${uiState.tracks.size}",
                        accent = NeonPink,
                        onClick = onOpenLocal,
                    )
                }
                item {
                    PortalCard(
                        title = "Плейлисты",
                        caption = uiState.selectedPlaylistName,
                        accent = AcidLime,
                        onClick = onOpenPlaylists,
                    )
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Треки",
                    value = uiState.tracks.size.toString(),
                    note = "в офлайне",
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Очередь",
                    value = uiState.selectedPlaylistTracks.size.toString(),
                    note = uiState.selectedPlaylistName,
                )
            }
        }
        item {
            PanelCard(title = "Сейчас играет", accent = NeonCyan) {
                Text(
                    text = playerState.currentTrack?.title ?: "Ожидание запуска",
                    color = InkText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = playerState.currentTrack?.artist ?: "Откройте медиатеку, парсер или плейлист для старта.",
                    color = MutedText,
                )
            }
        }
        item {
            PanelCard(title = "Навигация по библиотеке", accent = NeonPink) {
                Text(
                    text = "Главная собирает срезы всех вкладок: локальные треки, активный плейлист и статус поиска.",
                    color = MutedText,
                )
            }
        }
        item {
            SectionLabel("Активный плейлист")
        }
        if (playlistPreview.isEmpty()) {
            item {
                EmptyBlock(
                    title = "Плейлист пуст",
                    body = "Откройте вкладку плейлистов и выберите или создайте подборку.",
                )
            }
        } else {
            items(playlistPreview, key = { "playlist_preview_${it.id}" }) { track ->
                CompactTrackCard(
                    track = track,
                    accent = NeonPink,
                    meta = uiState.selectedPlaylistName,
                    onClick = { onPlayTrack(track.id) },
                )
            }
        }
        item {
            SectionLabel("Локальные треки")
        }
        if (localPreview.isEmpty()) {
            item {
                EmptyBlock(
                    title = "Медиатека пуста",
                    body = "Импортируйте локальные аудиофайлы, чтобы заполнить домашний экран.",
                )
            }
        } else {
            items(localPreview, key = { "local_preview_${it.id}" }) { track ->
                CompactTrackCard(
                    track = track,
                    accent = NeonCyan,
                    meta = "Офлайн-библиотека",
                    onClick = { onPlayTrack(track.id) },
                )
            }
        }
    }
}
