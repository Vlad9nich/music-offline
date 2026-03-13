package com.codex.signalflow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.codex.signalflow.MainUiState

@Composable
internal fun LocalLibraryScreen(
    uiState: MainUiState,
    onImportTracks: () -> Unit,
    onLibraryQueryChange: (String) -> Unit,
    onAddTrackToPlaylist: (String) -> Unit,
    onPlayTrack: (String) -> Unit,
    onDeleteTrack: (String) -> Unit,
) {
    val listState = rememberLazyListState()

    ScreenColumn(
        state = listState,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 160.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SearchFieldCard(
                value = uiState.libraryQuery,
                onValueChange = onLibraryQueryChange,
                title = "Поиск по локальной медиатеке",
                placeholder = "Фильтр по названию или исполнителю",
                buttonLabel = "Импорт",
                buttonIcon = Icons.Rounded.FolderOpen,
                onSearch = { onImportTracks() },
            )
        }
        item {
            PanelCard(title = "Точка добавления", accent = NeonPink) {
                Text(uiState.selectedPlaylistName, color = InkText, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Импортированные файлы сохраняются на устройстве и автоматически добавляются в выбранный плейлист.",
                    color = MutedText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (uiState.filteredTracks.isEmpty()) {
            item {
                EmptyBlock(
                    title = "Локальных треков нет",
                    body = "Импортируйте аудиофайлы с устройства. Они будут сохранены в папку Музыка/YaNeoDex для офлайн-плеера.",
                )
            }
        } else {
            items(uiState.filteredTracks, key = { it.id }) { track ->
                TrackRow(
                    track = track,
                    primaryLabel = "Пуск",
                    secondaryLabel = "В плейлист",
                    onPrimary = { onPlayTrack(track.id) },
                    onSecondary = { onAddTrackToPlaylist(track.id) },
                    tertiaryIcon = Icons.Rounded.Delete,
                    tertiaryLabel = "Удалить",
                    onTertiary = { onDeleteTrack(track.id) },
                )
            }
        }
    }
}
