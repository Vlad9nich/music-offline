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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 140.dp, top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SearchFieldCard(
                value = uiState.libraryQuery,
                onValueChange = onLibraryQueryChange,
                title = "LOCAL_LIBRARY // FILTER",
                placeholder = "ENTER TITLE OR ARTIST...",
                buttonLabel = "IMPORT",
                buttonIcon = Icons.Rounded.FolderOpen,
                onSearch = { onImportTracks() },
            )
        }
        item {
            Text(
                text = "[ INDEX // ${uiState.filteredTracks.size} TRACKS • QUEUE: ${uiState.selectedPlaylistName.uppercase()} ]",
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 0.6.sp,
            )
        }
        if (uiState.filteredTracks.isEmpty()) {
            item {
                EmptyBlock(
                    title = "EMPTY_INDEX",
                    body = "// NO LOCAL AUDIO MATCHED. CLICK [IMPORT] TO LOAD MP3/FLAC FILES",
                )
            }
        } else {
            items(uiState.filteredTracks, key = { it.id }) { track ->
                TrackRow(
                    track = track,
                    primaryLabel = "PLAY",
                    secondaryLabel = "QUEUE",
                    onPrimary = { onPlayTrack(track.id) },
                    onSecondary = { onAddTrackToPlaylist(track.id) },
                    tertiaryIcon = Icons.Rounded.Delete,
                    tertiaryLabel = "DELETE",
                    onTertiary = { onDeleteTrack(track.id) },
                )
            }
        }
    }
}
