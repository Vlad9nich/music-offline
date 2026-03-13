package com.codex.signalflow.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.signalflow.MainUiState
import com.codex.signalflow.data.PlaylistRecord
import com.codex.signalflow.data.TrackRecord
import com.codex.signalflow.importer.MatchedTrackCandidate
import com.codex.signalflow.importer.ScreenshotImportItemStatus
import com.codex.signalflow.importer.ScreenshotImportPhase
import com.codex.signalflow.importer.ScreenshotImportSession
import com.codex.signalflow.player.PlayerUiState
import com.codex.signalflow.player.VisualizerUiState
import com.codex.signalflow.source.RemoteTrackCandidate
import kotlin.math.roundToInt

internal val NeonCyan = Color(0xFF42F5FF)
internal val NeonPink = Color(0xFFFF4D8D)
internal val AcidLime = Color(0xFF74FF8B)
internal val PanelFill = Color(0xFF0A111A)
internal val PanelFillAlt = Color(0xFF0F1825)
internal val InkText = Color(0xFFF4F7FF)
internal val MutedText = Color(0xFF9BA9C4)
internal val PanelShape = CutCornerShape(topStart = 24.dp, topEnd = 8.dp, bottomEnd = 24.dp, bottomStart = 8.dp)
internal val InputShape = CutCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomEnd = 18.dp, bottomStart = 4.dp)

private enum class AppTab(
    val label: String,
    val icon: ImageVector,
) {
    Home("Главная", Icons.Rounded.Home),
    Search("Парсер", Icons.Rounded.Search),
    Local("Локальные", Icons.Rounded.LibraryMusic),
    Playlists("Плейлисты", Icons.Rounded.QueueMusic),
}

@Composable
fun SignalFlowApp(
    uiState: MainUiState,
    playerState: PlayerUiState,
    visualizerState: VisualizerUiState,
    visualizerPermissionGranted: Boolean,
    snackbarMessage: String?,
    onImportTracks: () -> Unit,
    onImportPlaylistScreenshots: () -> Unit,
    onDismissSnackbar: () -> Unit,
    onLibraryQueryChange: (String) -> Unit,
    onSourceQueryChange: (String) -> Unit,
    onOcrServerUrlChange: (String) -> Unit,
    onOcrAuthTokenChange: (String) -> Unit,
    onSelectPlaylist: (String) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onAddTrackToPlaylist: (String) -> Unit,
    onRemoveTrackFromPlaylist: (String) -> Unit,
    onPlayTrack: (String) -> Unit,
    onPlayPlaylist: () -> Unit,
    onShufflePlaylist: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onExpandedPlayerVisibilityChanged: (Boolean) -> Unit,
    onRequestVisualizerPermission: () -> Unit,
    onSearchSources: (String) -> Unit,
    onPlayPlaylistTrack: (String) -> Unit,
    onPreviewRemoteTrack: (RemoteTrackCandidate) -> Unit,
    onDownloadRemoteTrack: (RemoteTrackCandidate) -> Unit,
    onDeleteLocalTrack: (String) -> Unit,
    onDismissScreenshotImport: () -> Unit,
    onAddScreenshotImportMatch: (String) -> Unit,
) {
    var currentTab by rememberSaveable { mutableStateOf(AppTab.Home) }
    var isExpandedPlayerOpen by rememberSaveable { mutableStateOf(false) }
    val snackbars = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbars.showSnackbar(it)
            onDismissSnackbar()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF02040A), Color(0xFF070B12), Color(0xFF05070B)),
                ),
            ),
    ) {
        GlowOrb(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .align(Alignment.TopCenter),
            color = NeonPink.copy(alpha = 0.22f),
            secondaryColor = NeonCyan.copy(alpha = 0.12f),
        )
        GlowOrb(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .align(Alignment.BottomCenter),
            color = NeonCyan.copy(alpha = 0.14f),
            secondaryColor = Color.Transparent,
        )

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(hostState = snackbars) },
            bottomBar = {
                Column {
                    PlayerDock(
                        state = playerState,
                        onTogglePlayPause = onTogglePlayPause,
                        onPlayPrevious = onPlayPrevious,
                        onPlayNext = onPlayNext,
                        onToggleShuffle = onToggleShuffle,
                        onSeekTo = onSeekTo,
                        onExpand = {
                            isExpandedPlayerOpen = true
                            onExpandedPlayerVisibilityChanged(true)
                            if (!visualizerPermissionGranted) onRequestVisualizerPermission()
                        },
                    )
                    BottomNavigation(currentTab = currentTab, onSelect = { currentTab = it })
                }
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                AppHeader()
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        (slideInHorizontally { it / 7 } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it / 7 } + fadeOut())
                    },
                    label = "app_tab_transition",
                ) { tab ->
                    when (tab) {
                        AppTab.Home -> HomeScreen(
                            uiState = uiState,
                            playerState = playerState,
                            onImportTracks = onImportTracks,
                            onPlayPlaylist = onPlayPlaylist,
                            onShufflePlaylist = onShufflePlaylist,
                            onOpenParser = { currentTab = AppTab.Search },
                            onOpenLocal = { currentTab = AppTab.Local },
                            onOpenPlaylists = { currentTab = AppTab.Playlists },
                        )

                        AppTab.Search -> ParserSearchScreen(
                            uiState = uiState,
                            playerState = playerState,
                            onSourceQueryChange = onSourceQueryChange,
                            onSearchSources = onSearchSources,
                            onPreviewRemoteTrack = onPreviewRemoteTrack,
                            onDownloadRemoteTrack = onDownloadRemoteTrack,
                        )

                        AppTab.Local -> LocalLibraryScreen(
                            uiState = uiState,
                            onImportTracks = onImportTracks,
                            onLibraryQueryChange = onLibraryQueryChange,
                            onAddTrackToPlaylist = onAddTrackToPlaylist,
                            onPlayTrack = onPlayTrack,
                            onDeleteTrack = onDeleteLocalTrack,
                        )

                        AppTab.Playlists -> PlaylistsScreen(
                            uiState = uiState,
                            onSelectPlaylist = onSelectPlaylist,
                            onCreatePlaylist = onCreatePlaylist,
                            onPlayPlaylist = onPlayPlaylist,
                            onShufflePlaylist = onShufflePlaylist,
                            onRemoveTrackFromPlaylist = onRemoveTrackFromPlaylist,
                            onPlayTrack = onPlayPlaylistTrack,
                            onImportPlaylistScreenshots = onImportPlaylistScreenshots,
                            onOcrServerUrlChange = onOcrServerUrlChange,
                            onOcrAuthTokenChange = onOcrAuthTokenChange,
                            onDismissScreenshotImport = onDismissScreenshotImport,
                            onAddScreenshotImportMatch = onAddScreenshotImportMatch,
                        )
                    }
                }
            }
        }
        if (isExpandedPlayerOpen) {
            ExpandedPlayerOverlay(
                state = playerState,
                visualizerState = visualizerState,
                onDismiss = {
                    isExpandedPlayerOpen = false
                    onExpandedPlayerVisibilityChanged(false)
                },
                onTogglePlayPause = onTogglePlayPause,
                onPlayPrevious = onPlayPrevious,
                onPlayNext = onPlayNext,
                onToggleShuffle = onToggleShuffle,
                onSeekTo = onSeekTo,
            )
        }
    }
}

@Composable
private fun AppHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "YaNeoDex",
            color = InkText,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 0.8.sp,
            ),
        )
        Text(
            text = "Офлайн-плеер, плейлисты и поиск по внешнему источнику",
            color = MutedText,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun BottomNavigation(currentTab: AppTab, onSelect: (AppTab) -> Unit) {
    NavigationBar(
        containerColor = Color(0xFF081018),
        contentColor = InkText,
    ) {
        AppTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == currentTab,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = NeonCyan,
                    selectedTextColor = NeonCyan,
                    indicatorColor = NeonPink.copy(alpha = 0.18f),
                    unselectedIconColor = MutedText,
                    unselectedTextColor = MutedText,
                ),
            )
        }
    }
}

@Composable
internal fun ScreenColumn(
    state: LazyListState,
    contentPadding: PaddingValues,
    verticalArrangement: Arrangement.Vertical,
    content: LazyListScope.() -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = verticalArrangement,
            content = content,
        )
        MinimalScrollIndicator(
            state = state,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(top = 20.dp, end = 6.dp, bottom = 164.dp),
        )
    }
}

@Composable
internal fun MinimalScrollIndicator(
    state: LazyListState,
    modifier: Modifier = Modifier,
) {
    val layoutInfo = state.layoutInfo
    val visibleItems = layoutInfo.visibleItemsInfo
    if (visibleItems.isEmpty() || layoutInfo.totalItemsCount <= visibleItems.size) return

    val firstVisible = visibleItems.first()
    val thumbFraction by remember(state, layoutInfo) {
        derivedStateOf {
            (visibleItems.size.toFloat() / layoutInfo.totalItemsCount.toFloat()).coerceIn(0.14f, 0.36f)
        }
    }
    val progress by remember(state, layoutInfo) {
        derivedStateOf {
            val totalScrollableItems = (layoutInfo.totalItemsCount - visibleItems.size).coerceAtLeast(1)
            val firstItemFraction = if (firstVisible.size > 0) {
                state.firstVisibleItemScrollOffset.toFloat() / firstVisible.size.toFloat()
            } else {
                0f
            }
            ((state.firstVisibleItemIndex + firstItemFraction) / totalScrollableItems.toFloat()).coerceIn(0f, 1f)
        }
    }

    AnimatedVisibility(
        visible = state.isScrollInProgress,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .height(220.dp)
                .width(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .align(Alignment.Center)
                    .background(NeonCyan.copy(alpha = 0.12f)),
            )
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(220.dp * thumbFraction)
                    .offset(y = (220.dp - (220.dp * thumbFraction)) * progress)
                    .clip(RoundedCornerShape(99.dp))
                    .background(NeonCyan.copy(alpha = 0.84f))
                    .align(Alignment.TopCenter),
            )
        }
    }
}


@Composable
internal fun SearchFieldCard(
    value: String,
    onValueChange: (String) -> Unit,
    title: String,
    placeholder: String,
    buttonLabel: String,
    buttonIcon: ImageVector,
    onSearch: (String) -> Unit,
) {
    PanelCard(title = title, accent = NeonCyan) {
        CyberTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            imeAction = ImeAction.Search,
            onImeAction = { onSearch(value) },
        )
        Spacer(Modifier.height(12.dp))
        PrimaryActionButton(label = buttonLabel, icon = buttonIcon, onClick = { onSearch(value) })
    }
}

@Composable
internal fun SearchStatusStrip(
    status: String,
    resultsCount: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = status,
            color = MutedText,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        if (resultsCount > 0) {
            Text(
                text = "Найдено: $resultsCount",
                color = AcidLime,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
internal fun CyberTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    imeAction: ImeAction,
    onImeAction: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(InputShape)
            .background(Color(0xFF07111B))
            .border(1.dp, NeonCyan.copy(alpha = 0.9f), InputShape)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = TextStyle(
                color = InkText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
            ),
            cursorBrush = SolidColor(NeonPink),
            keyboardOptions = KeyboardOptions(imeAction = imeAction),
            keyboardActions = KeyboardActions(
                onSearch = { onImeAction() },
                onDone = { onImeAction() },
            ),
            decorationBox = { innerTextField ->
                if (value.isBlank()) {
                    Text(
                        text = placeholder,
                        color = MutedText,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                innerTextField()
            },
        )
    }
}

@Composable
internal fun TrackRow(
    track: TrackRecord,
    primaryLabel: String,
    secondaryLabel: String,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
    tertiaryIcon: ImageVector? = null,
    tertiaryLabel: String? = null,
    onTertiary: (() -> Unit)? = null,
) {
    PanelCard(title = "Трек", accent = NeonPink) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = track.title,
                color = InkText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "${track.artist} • ${formatDuration(track.durationMs)}",
                color = MutedText,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SecondaryActionButton(
                    label = primaryLabel,
                    icon = Icons.Rounded.PlayArrow,
                    onClick = onPrimary,
                    compact = true,
                )
                SecondaryActionButton(
                    label = secondaryLabel,
                    icon = Icons.Rounded.PlaylistPlay,
                    onClick = onSecondary,
                    compact = true,
                )
                if (tertiaryIcon != null && tertiaryLabel != null && onTertiary != null) {
                    ActionIconButton(
                        icon = tertiaryIcon,
                        contentDescription = tertiaryLabel,
                        enabled = true,
                        active = false,
                        accent = NeonPink,
                        onClick = onTertiary,
                    )
                }
            }
        }
    }
}

@Composable
internal fun PlaylistScreenshotImportPanel(
    session: ScreenshotImportSession,
    serverUrl: String,
    showMatchedOnly: Boolean,
    onServerUrlChange: (String) -> Unit,
    onBack: () -> Unit,
    onToggleMatchedOnly: () -> Unit,
    onPickScreenshots: () -> Unit,
) {
    PanelCard(title = "Импорт из скриншотов", accent = NeonCyan) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SecondaryActionButton(
                label = "К плейлисту",
                icon = Icons.Rounded.ArrowBack,
                onClick = onBack,
                compact = true,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(session.playlistName, color = InkText, fontWeight = FontWeight.Bold)
                Text(
                    text = session.statusMessage.ifBlank { "Импорт в выбранный плейлист." },
                    color = MutedText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        CyberTextField(
            value = serverUrl,
            onValueChange = onServerUrlChange,
            placeholder = "https://your-vps.example.com",
            imeAction = ImeAction.Done,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "Всего",
                value = session.totalScreenshots.toString(),
                note = "скриншотов",
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "Распознано",
                value = session.processedScreenshots.toString(),
                note = "обработано",
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "Добавлено",
                value = session.addedCount.toString(),
                note = "в плейлист",
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                label = if (showMatchedOnly) "Показ: найденные" else "Показ: все",
                selected = showMatchedOnly,
                onClick = onToggleMatchedOnly,
            )
            SecondaryActionButton(
                label = "Добавить еще",
                icon = Icons.Rounded.FolderOpen,
                onClick = onPickScreenshots,
                compact = true,
            )
        }
        if (session.errorMessage != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = session.errorMessage,
                color = NeonPink,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
internal fun ScreenshotImportRow(
    item: MatchedTrackCandidate,
    onAdd: () -> Unit,
) {
    val accent = when (item.status) {
        ScreenshotImportItemStatus.MATCHED,
        ScreenshotImportItemStatus.ADDED,
        ScreenshotImportItemStatus.ALREADY_IN_PLAYLIST,
        ScreenshotImportItemStatus.ADDING,
        -> AcidLime

        ScreenshotImportItemStatus.LOW_CONFIDENCE_MATCH -> NeonPink
        else -> NeonCyan
    }
    PanelCard(title = "OCR трек", accent = accent) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = item.recognized.rawText.ifBlank { listOf(item.recognized.artistGuess, item.recognized.titleGuess).joinToString(" - ") },
                color = InkText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildImportStatusText(item),
                color = accent,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
            )
            if (item.recognized.artistGuess.isNotBlank() || item.recognized.titleGuess.isNotBlank()) {
                Text(
                    text = "OCR: ${item.recognized.artistGuess.ifBlank { "?" }} — ${item.recognized.titleGuess.ifBlank { "?" }}",
                    color = MutedText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            item.bestMatch?.let { bestMatch ->
                Text(
                    text = "Совпадение: ${bestMatch.artist} — ${bestMatch.title}",
                    color = InkText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (item.status in setOf(ScreenshotImportItemStatus.MATCHED, ScreenshotImportItemStatus.LOW_CONFIDENCE_MATCH, ScreenshotImportItemStatus.ADD_FAILED)) {
                    SecondaryActionButton(
                        label = if (item.status == ScreenshotImportItemStatus.ADD_FAILED) "Повторить" else "Добавить",
                        icon = Icons.Rounded.PlaylistPlay,
                        onClick = onAdd,
                        compact = true,
                    )
                }
                Text(
                    text = item.message ?: "Уверенность OCR: ${(item.recognized.confidence * 100).roundToInt()}% • match ${(item.matchScore * 100).roundToInt()}%",
                    color = MutedText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

internal fun buildImportStatusText(item: MatchedTrackCandidate): String {
    return when (item.status) {
        ScreenshotImportItemStatus.RECOGNIZED -> "Распознано"
        ScreenshotImportItemStatus.MATCHED -> "Совпадение найдено"
        ScreenshotImportItemStatus.LOW_CONFIDENCE_MATCH -> "Совпадение с низкой уверенностью"
        ScreenshotImportItemStatus.OCR_ONLY -> "Нужна ручная проверка"
        ScreenshotImportItemStatus.NOT_FOUND -> "Не найдено"
        ScreenshotImportItemStatus.ALREADY_IN_PLAYLIST -> "Уже в плейлисте"
        ScreenshotImportItemStatus.ADDING -> "Добавляем..."
        ScreenshotImportItemStatus.ADDED -> "Уже добавлено"
        ScreenshotImportItemStatus.ADD_FAILED -> "Ошибка добавления"
    }
}

@Composable
internal fun PlaylistChip(
    playlist: PlaylistRecord,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(InputShape)
            .background(if (selected) NeonPink.copy(alpha = 0.16f) else PanelFillAlt)
            .border(1.dp, if (selected) NeonPink else NeonCyan.copy(alpha = 0.28f), InputShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = playlist.name,
            color = if (selected) InkText else MutedText,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
internal fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(InputShape)
            .background(if (selected) NeonCyan.copy(alpha = 0.16f) else PanelFillAlt)
            .border(1.dp, if (selected) NeonCyan else NeonPink.copy(alpha = 0.24f), InputShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(
            text = label,
            color = if (selected) InkText else MutedText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
internal fun SectionLabel(title: String) {
    Text(
        text = title,
        color = InkText,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 2.dp),
    )
}

@Composable
internal fun PortalCard(
    title: String,
    caption: String,
    accent: Color,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .width(228.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = PanelFill),
        shape = PanelShape,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.32f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, color = accent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            Text(
                text = caption,
                color = InkText,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun CompactTrackCard(
    track: TrackRecord,
    accent: Color,
    meta: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PanelFill),
        shape = PanelShape,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.24f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = track.title,
                color = InkText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = track.artist,
                color = MutedText,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = meta,
                color = accent,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun RemoteTrackRow(
    result: RemoteTrackCandidate,
    isPreviewing: Boolean,
    isDownloading: Boolean,
    onPreview: () -> Unit,
    onDownload: () -> Unit,
) {
    PanelCard(title = "Найденный трек", accent = NeonCyan) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.title,
                    color = InkText,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(result.artist, color = MutedText, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp))
                Text("Источник: ${result.sourceId}", color = NeonCyan, style = MaterialTheme.typography.bodySmall)
                if (!result.downloadUrl.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (isDownloading) "Скачивание..." else "Готов к загрузке",
                        color = if (isDownloading) NeonPink else AcidLime,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ActionIconButton(
                    icon = Icons.Rounded.PlayCircleOutline,
                    contentDescription = "Предпрослушивание",
                    enabled = !isDownloading,
                    active = isPreviewing,
                    accent = NeonCyan,
                    onClick = onPreview,
                )
                ActionIconButton(
                    icon = Icons.Rounded.FileDownload,
                    contentDescription = "Скачать",
                    enabled = result.downloadUrl != null && !isDownloading,
                    active = result.downloadUrl != null,
                    accent = NeonPink,
                    onClick = onDownload,
                )
            }
        }
    }
}

@Composable
internal fun EmptyBlock(title: String, body: String) {
    PanelCard(title = title, accent = NeonCyan) {
        Text(body, color = MutedText)
    }
}

@Composable
internal fun HeroPanel(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = PanelShape,
        border = BorderStroke(1.dp, NeonPink.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(NeonPink.copy(alpha = 0.12f), NeonCyan.copy(alpha = 0.08f), PanelFillAlt),
                    ),
                )
                .padding(18.dp),
        ) {
            Text(title, color = InkText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(subtitle, color = MutedText, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
internal fun MetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    note: String,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = PanelFill),
        shape = PanelShape,
        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.28f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(title, color = MutedText, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Text(value, color = InkText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(note, color = NeonCyan, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun PanelCard(
    title: String,
    accent: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PanelFill),
        shape = PanelShape,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.36f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = title.uppercase(),
                color = accent,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
internal fun PrimaryActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(InputShape)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(NeonPink, Color(0xFFFF8E6F)),
                ),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = label, tint = Color.Black, modifier = Modifier.size(18.dp))
            Text(label, color = Color.Black, fontWeight = FontWeight.Black, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
internal fun SecondaryActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    compact: Boolean = false,
) {
    Box(
        modifier = Modifier
            .clip(InputShape)
            .background(PanelFillAlt)
            .border(1.dp, NeonCyan.copy(alpha = 0.3f), InputShape)
            .clickable(onClick = onClick)
            .padding(
                horizontal = if (compact) 10.dp else 12.dp,
                vertical = if (compact) 8.dp else 10.dp,
            ),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = label,
                tint = InkText,
                modifier = Modifier.size(if (compact) 14.dp else 16.dp),
            )
            Text(
                label,
                color = InkText,
                fontWeight = FontWeight.Medium,
                style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
internal fun MiniControl(
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                when {
                    !enabled -> Color(0xFF05070B)
                    active -> PanelFill
                    else -> Color(0xFF05070B)
                },
            )
            .border(
                1.dp,
                when {
                    !enabled -> Color(0xFF1B2433)
                    active -> NeonPink.copy(alpha = 0.44f)
                    else -> Color(0xFF1B2433)
                },
                RoundedCornerShape(14.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(10.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = when {
                !enabled -> MutedText.copy(alpha = 0.46f)
                active -> InkText
                else -> MutedText
            },
        )
    }
}

@Composable
internal fun ActionIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    active: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(InputShape)
            .background(if (enabled) accent.copy(alpha = 0.16f) else PanelFillAlt)
            .border(1.dp, if (active) accent.copy(alpha = 0.7f) else Color(0xFF1B2433), InputShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) accent else MutedText,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
internal fun GlowOrb(
    modifier: Modifier,
    color: Color,
    secondaryColor: Color,
) {
    Box(
        modifier = modifier.background(
            Brush.radialGradient(
                colors = listOf(color, secondaryColor, Color.Transparent),
                radius = 820f,
            ),
        ),
    )
}

internal fun RemoteTrackCandidate.stableKey(): String {
    return "$sourceId|$title|$artist|${downloadUrl ?: detailUrl}"
}

internal fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000.0).roundToInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
















