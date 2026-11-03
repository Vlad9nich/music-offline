package com.codex.signalflow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.codex.signalflow.MainUiState
import com.codex.signalflow.player.PlayerUiState
import com.codex.signalflow.source.RemoteTrackCandidate

@Composable
internal fun ParserSearchScreen(
    uiState: MainUiState,
    playerState: PlayerUiState,
    onSourceQueryChange: (String) -> Unit,
    onSearchSources: (String) -> Unit,
    onPreviewRemoteTrack: (RemoteTrackCandidate) -> Unit,
    onDownloadRemoteTrack: (RemoteTrackCandidate) -> Unit,
) {
    var queryDraft by rememberSaveable(uiState.sourceQuery) { mutableStateOf(uiState.sourceQuery) }
    var selectedArtist by rememberSaveable(uiState.sourceQuery, uiState.sourceResults) { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val authorFilters = remember(uiState.sourceResults) {
        uiState.sourceResults
            .map { it.artist.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }
    val filteredSourceResults = remember(uiState.sourceResults, selectedArtist) {
        uiState.sourceResults.filter { result ->
            selectedArtist == null || result.artist.equals(selectedArtist, ignoreCase = true)
        }
    }

    ScreenColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 140.dp, top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SearchFieldCard(
                value = queryDraft,
                onValueChange = {
                    queryDraft = it
                    onSourceQueryChange(it)
                },
                title = "STREAM_HUNTER // QUERY",
                placeholder = "ENTER ARTIST OR TRACK...",
                buttonLabel = "SEARCH",
                buttonIcon = Icons.Rounded.Search,
                onSearch = { onSearchSources(queryDraft) },
            )
        }
        item {
            SearchStatusStrip(
                status = if (selectedArtist == null) {
                    uiState.sourceStatus
                } else {
                    "FILTER: $selectedArtist"
                },
                resultsCount = filteredSourceResults.size,
            )
        }
        if (authorFilters.isNotEmpty()) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        FilterChip(
                            label = "ALL",
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
        if (uiState.sourceResults.isEmpty()) {
            item {
                EmptyBlock(
                    title = "STANDBY",
                    body = "// INITIATE SEARCH TO INTERCEPT REMOTE STREAM METADATA",
                )
            }
        } else if (filteredSourceResults.isEmpty()) {
            item {
                EmptyBlock(
                    title = "FILTER_EMPTY",
                    body = "// NO CANDIDATES MATCH FILTER",
                )
            }
        } else {
            items(filteredSourceResults, key = { it.stableKey() }) { result ->
                RemoteTrackRow(
                    result = result,
                    isPreviewing = playerState.currentTrack?.id == "preview:${result.stableKey()}",
                    isDownloading = result.stableKey() in uiState.downloadingTrackKeys,
                    onPreview = { onPreviewRemoteTrack(result) },
                    onDownload = { onDownloadRemoteTrack(result) },
                )
            }
        }
    }
}
