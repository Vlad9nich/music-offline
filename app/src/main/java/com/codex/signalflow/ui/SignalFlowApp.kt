package com.codex.signalflow.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
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

internal val CyberWhite = Color(0xFFFFFFFF)
internal val CyberSilver = Color(0xFFD6DBE5)
internal val CyberGrey = Color(0xFF8A93A6)
internal val CyberDark = Color(0xFF141822)
internal val PanelFill = Color(0xFF090B0F)
internal val PanelFillAlt = Color(0xFF10131A)
internal val InkText = Color(0xFFF2F4F8)
internal val MutedText = Color(0xFF8690A2)

// Compatibility aliases for pure Black & White aesthetic
internal val NeonCyan = CyberWhite
internal val NeonPink = CyberSilver
internal val AcidLime = CyberWhite

internal val PanelShape = CutCornerShape(8.dp)
internal val InputShape = CutCornerShape(6.dp)
internal val TrackRowShape = CutCornerShape(6.dp)

private enum class AppTab(
    val label: String,
    val icon: ImageVector,
) {
    Home("Главная", Icons.Rounded.Home),
    Search("Поиск", Icons.Rounded.Search),
    Local("Библиотека", Icons.Rounded.LibraryMusic),
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
    onSetYandexToken: (String?) -> Unit = {},
    onToggleYandexDirectSource: () -> Unit = {},
    onStartYandexLikesSync: () -> Unit = {},
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
            .background(Color(0xFF040609)),
    ) {
        // 1. Isolated Background Layer (Runs in its own hardware RenderNode)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer()
                .dedsecNoiseBackground(),
        )

        // 2. Main Application Scaffold
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
                        (slideInHorizontally(animationSpec = tween(260)) { it / 12 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(animationSpec = tween(220)) { -it / 14 } + fadeOut(tween(180)))
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
                            onPlayTrack = onPlayTrack,
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
                            onSetYandexToken = onSetYandexToken,
                            onToggleYandexDirectSource = onToggleYandexDirectSource,
                            onStartYandexLikesSync = onStartYandexLikesSync,
                        )
                    }
                }
            }
        }

        // 3. Ultra-fast hardware CRT scanline and raster overlay (1 draw call, non-blocking)
        CrtScanlineOverlay(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(),
        )

        AnimatedVisibility(
            visible = isExpandedPlayerOpen,
            enter = slideInVertically(animationSpec = tween(320)) { it / 3 } + fadeIn(tween(240)),
            exit = slideOutVertically(animationSpec = tween(240)) { it / 4 } + fadeOut(tween(180)),
        ) {
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(Color.White, CutSmallShape),
            )
            Text(
                text = "YaNeoDex • Терминал",
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                letterSpacing = 0.5.sp,
            )
        }
        Box(
            modifier = Modifier
                .clip(CutCornerShape(3.dp))
                .border(1.dp, Color(0xFF283446), CutCornerShape(3.dp))
                .background(Color(0xFF0F141E))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text(
                text = "В СЕТИ",
                color = Color(0xFFA5B4C7),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun BottomNavigation(currentTab: AppTab, onSelect: (AppTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF080B10))
            .border(width = 1.dp, color = Color(0xFF1E2634))
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppTab.entries.forEach { tab ->
            val isSelected = tab == currentTab
            val scale by animateFloatAsState(
                targetValue = if (isSelected) 1.02f else 0.98f,
                animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                label = "tab_scale",
            )
            val bgColor = if (isSelected) Color.White else Color(0xFF10151E)
            val contentColor = if (isSelected) Color.Black else Color(0xFF8E9BAE)
            val borderColor = if (isSelected) Color.White else Color(0xFF222C3A)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(CutButtonShape)
                    .background(bgColor)
                    .border(1.dp, borderColor, CutButtonShape)
                    .tactileClickable(onClick = { onSelect(tab) })
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    when (tab) {
                        AppTab.Home -> DedSecTerminalNavIcon(tint = contentColor)
                        AppTab.Search -> DedSecSearchNavIcon(tint = contentColor)
                        AppTab.Local -> DedSecLibraryNavIcon(tint = contentColor)
                        AppTab.Playlists -> DedSecPlaylistsNavIcon(tint = contentColor)
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = tab.label,
                        color = contentColor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
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
            .background(Color(0xFF080A0F))
            .border(1.dp, Color(0xFF2A3240), InputShape)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = TextStyle(
                color = Color.White,
                fontSize = 15.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
            ),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(imeAction = imeAction),
            keyboardActions = KeyboardActions(
                onSearch = { onImeAction() },
                onDone = { onImeAction() },
            ),
            decorationBox = { innerTextField ->
                if (value.isBlank()) {
                    Text(
                        text = placeholder,
                        color = Color(0xFF5E6779),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 13.sp,
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TrackRowShape)
            .background(Color(0xFF090D14))
            .border(1.dp, Color(0xFF1E2736), TrackRowShape)
            .tactileClickable(onClick = onPrimary)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TrackArtwork(
            track = track,
            size = 40.dp,
            shape = CutSmallShape,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
            )
            Text(
                text = "${track.artist} • ${formatDuration(track.durationMs)}",
                color = Color(0xFF808D9F),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ActionIconButton(
            icon = Icons.Rounded.PlayArrow,
            contentDescription = primaryLabel,
            enabled = true,
            active = false,
            accent = Color.White,
            onClick = onPrimary,
        )
        ActionIconButton(
            icon = Icons.Rounded.PlaylistPlay,
            contentDescription = secondaryLabel,
            enabled = true,
            active = false,
            accent = Color(0xFFD6DBE5),
            onClick = onSecondary,
        )
        if (tertiaryIcon != null && tertiaryLabel != null && onTertiary != null) {
            ActionIconButton(
                icon = tertiaryIcon,
                contentDescription = tertiaryLabel,
                enabled = true,
                active = false,
                accent = Color(0xFFA0AAB8),
                onClick = onTertiary,
            )
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
            .width(180.dp)
            .tactileClickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF090D14)),
        shape = PanelShape,
        border = BorderStroke(1.dp, Color(0xFF222B38)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title.uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            )
            Text(
                text = caption,
                color = Color(0xFF8B97AB),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                maxLines = 2,
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
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.tactileClickable(onClick = onClick)
                } else {
                    Modifier
                },
            ),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF090D14)),
        shape = TrackRowShape,
        border = BorderStroke(1.dp, Color(0xFF1E2634)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TrackArtwork(
                track = track,
                size = 36.dp,
                shape = CutSmallShape,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                )
                Text(
                    text = "${track.artist} • $meta",
                    color = Color(0xFF808D9F),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
    PanelCard(title = "ИСТОЧНИК • ${result.sourceId.uppercase()}", accent = Color.White) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrackArtwork(
                remoteTrack = result,
                size = 40.dp,
                shape = CutSmallShape,
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = result.artist,
                    color = Color(0xFF828FA2),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                )
            }
            Spacer(Modifier.width(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                ActionIconButton(
                    icon = Icons.Rounded.PlayCircleOutline,
                    contentDescription = "Предпрослушивание",
                    enabled = !isDownloading,
                    active = isPreviewing,
                    accent = Color.White,
                    onClick = onPreview,
                )
                ActionIconButton(
                    icon = Icons.Rounded.FileDownload,
                    contentDescription = "Скачать",
                    enabled = result.downloadUrl != null && !isDownloading,
                    active = false,
                    accent = Color.White,
                    onClick = onDownload,
                )
            }
        }
    }
}

@Composable
internal fun EmptyBlock(title: String, body: String) {
    PanelCard(title = title, accent = Color.White) {
        Text(
            text = body,
            color = Color(0xFF7A8498),
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontSize = 12.sp,
        )
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF090B0F)),
        shape = PanelShape,
        border = BorderStroke(1.dp, Color(0xFF262E3D)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF090B0F))
                .techCornerMarks()
                .padding(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title.uppercase(),
                    color = Color.White,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                )
                Text(
                    text = "ГОТОВО",
                    color = Color(0xFF7E8C9E),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontSize = 10.sp,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                color = Color(0xFF7E889B),
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(10.dp))
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
    compact: Boolean = false,
) {
    TactileButton(
        onClick = onClick,
        modifier = modifier,
        inverted = true,
        icon = icon,
        text = label,
    )
}

@Composable
internal fun SecondaryActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    TactileButton(
        onClick = onClick,
        modifier = modifier,
        inverted = false,
        icon = icon,
        text = label,
    )
}

@Composable
internal fun MiniControl(
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    primary: Boolean = false,
) {
    val shape = CutCornerShape(4.dp)
    val bg = when {
        primary && active -> Color.White
        active -> Color(0xFF1D222E)
        else -> Color(0xFF090B0F)
    }
    val tint = when {
        primary && active -> Color.Black
        active -> Color.White
        !enabled -> Color(0xFF384050)
        else -> Color(0xFF909BB0)
    }
    val border = when {
        primary -> Color.White
        active -> Color(0xFF3E485C)
        else -> Color(0xFF1E232E)
    }

    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(shape)
            .background(bg)
            .border(1.dp, border, shape)
            .glitchClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp),
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
    val shape = CutCornerShape(4.dp)
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(shape)
            .background(if (active) Color.White else Color(0xFF10141C))
            .border(1.dp, if (active) Color.White else Color(0xFF242C3B), shape)
            .glitchClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) Color.Black else if (enabled) Color.White else Color(0xFF4C5668),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
internal fun GlowOrb(
    modifier: Modifier,
    color: Color,
    secondaryColor: Color,
) {
    // Replaced with minimal empty spacer for Watch Dogs 2 high-contrast clean look
    Box(modifier = modifier)
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
















