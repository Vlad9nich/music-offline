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
import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import com.codex.signalflow.player.PlayerUiState
import com.codex.signalflow.player.VisualizerUiState
import com.codex.signalflow.source.RemoteTrackCandidate
import kotlin.math.roundToInt

private val NeonCyan = Color(0xFF42F5FF)
private val NeonPink = Color(0xFFFF4D8D)
private val AcidLime = Color(0xFF74FF8B)
private val PanelFill = Color(0xFF0A111A)
private val PanelFillAlt = Color(0xFF0F1825)
private val InkText = Color(0xFFF4F7FF)
private val MutedText = Color(0xFF9BA9C4)
private val PanelShape = CutCornerShape(topStart = 24.dp, topEnd = 8.dp, bottomEnd = 24.dp, bottomStart = 8.dp)
private val InputShape = CutCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomEnd = 18.dp, bottomStart = 4.dp)

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
    onDismissSnackbar: () -> Unit,
    onLibraryQueryChange: (String) -> Unit,
    onSourceQueryChange: (String) -> Unit,
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
    onDownloadRemoteTrack: (RemoteTrackCandidate) -> Unit,
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
                        )

                        AppTab.Search -> ParserSearchScreen(
                            uiState = uiState,
                            onSourceQueryChange = onSourceQueryChange,
                            onSearchSources = onSearchSources,
                            onDownloadRemoteTrack = onDownloadRemoteTrack,
                        )

                        AppTab.Local -> LocalLibraryScreen(
                            uiState = uiState,
                            onImportTracks = onImportTracks,
                            onLibraryQueryChange = onLibraryQueryChange,
                            onAddTrackToPlaylist = onAddTrackToPlaylist,
                            onPlayTrack = onPlayTrack,
                        )

                        AppTab.Playlists -> PlaylistsScreen(
                            uiState = uiState,
                            onSelectPlaylist = onSelectPlaylist,
                            onCreatePlaylist = onCreatePlaylist,
                            onPlayPlaylist = onPlayPlaylist,
                            onShufflePlaylist = onShufflePlaylist,
                            onRemoveTrackFromPlaylist = onRemoveTrackFromPlaylist,
                            onPlayTrack = onPlayPlaylistTrack,
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
private fun ScreenColumn(
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
private fun MinimalScrollIndicator(
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
private fun HomeScreen(
    uiState: MainUiState,
    playerState: PlayerUiState,
    onImportTracks: () -> Unit,
    onPlayPlaylist: () -> Unit,
    onShufflePlaylist: () -> Unit,
) {
    val listState = rememberLazyListState()

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
                    text = playerState.currentTrack?.artist ?: "Выберите трек в локальной медиатеке или плейлисте",
                    color = MutedText,
                )
            }
        }
        item {
            PanelCard(title = "Текущий плейлист", accent = NeonPink) {
                Text(
                    text = uiState.selectedPlaylistName,
                    color = InkText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Треков в очереди: ${uiState.selectedPlaylistTracks.size}",
                    color = MutedText,
                )
            }
        }
    }
}

@Composable
private fun ParserSearchScreen(
    uiState: MainUiState,
    onSourceQueryChange: (String) -> Unit,
    onSearchSources: (String) -> Unit,
    onDownloadRemoteTrack: (RemoteTrackCandidate) -> Unit,
) {
    var queryDraft by rememberSaveable(uiState.sourceQuery) { mutableStateOf(uiState.sourceQuery) }
    val listState = rememberLazyListState()

    ScreenColumn(
        state = listState,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 160.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SearchFieldCard(
                value = queryDraft,
                onValueChange = {
                    queryDraft = it
                    onSourceQueryChange(it)
                },
                title = "Поиск по парсеру",
                placeholder = "Введите артиста или название",
                buttonLabel = "Искать",
                buttonIcon = Icons.Rounded.Search,
                onSearch = { onSearchSources(queryDraft) },
            )
        }
        item {
            SearchStatusStrip(status = uiState.sourceStatus, resultsCount = uiState.sourceResults.size)
        }
        if (uiState.sourceResults.isEmpty()) {
            item {
                EmptyBlock(
                    title = "Пока пусто",
                    body = "Запустите поиск, чтобы увидеть подходящие треки из активного источника.",
                )
            }
        } else {
            items(uiState.sourceResults, key = { it.stableKey() }) { result ->
                RemoteTrackRow(
                    result = result,
                    isDownloading = result.stableKey() in uiState.downloadingTrackKeys,
                    onDownload = { onDownloadRemoteTrack(result) },
                )
            }
        }
    }
}

@Composable
private fun LocalLibraryScreen(
    uiState: MainUiState,
    onImportTracks: () -> Unit,
    onLibraryQueryChange: (String) -> Unit,
    onAddTrackToPlaylist: (String) -> Unit,
    onPlayTrack: (String) -> Unit,
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
                )
            }
        }
    }
}

@Composable
private fun PlaylistsScreen(
    uiState: MainUiState,
    onSelectPlaylist: (String) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onPlayPlaylist: () -> Unit,
    onShufflePlaylist: () -> Unit,
    onRemoveTrackFromPlaylist: (String) -> Unit,
    onPlayTrack: (String) -> Unit,
) {
    var draftName by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    ScreenColumn(
        state = listState,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 160.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
                            onClick = { onSelectPlaylist(playlist.id) },
                        )
                    }
                }
            }
        }
        item {
            PanelCard(title = "Активный плейлист", accent = NeonPink) {
                Text(uiState.selectedPlaylistName, color = InkText, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Треков: ${uiState.selectedPlaylistTracks.size}",
                    color = MutedText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (uiState.selectedPlaylistTracks.isEmpty()) {
            item {
                EmptyBlock(
                    title = "Плейлист пуст",
                    body = "Добавьте треки из локальной медиатеки или через импорт файлов.",
                )
            }
        } else {
            items(uiState.selectedPlaylistTracks, key = { it.id }) { track ->
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

@Composable
private fun PlayerDock(
    state: PlayerUiState,
    onTogglePlayPause: () -> Unit,
    onPlayPrevious: () -> Unit,
    onPlayNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onExpand: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onExpand),
        colors = CardDefaults.cardColors(containerColor = PanelFillAlt),
        shape = PanelShape,
        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.currentTrack?.title ?: "Линия ожидания",
                        color = InkText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = state.currentTrack?.artist ?: state.queueLabel,
                        color = MutedText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    MiniControl(
                        icon = Icons.Rounded.Shuffle,
                        active = state.isShuffleEnabled,
                        enabled = state.queueSize > 1,
                        onClick = onToggleShuffle,
                    )
                    MiniControl(
                        icon = Icons.Rounded.FastRewind,
                        active = state.hasPrevious,
                        enabled = state.hasPrevious,
                        onClick = onPlayPrevious,
                    )
                    MiniControl(
                        icon = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        active = true,
                        onClick = onTogglePlayPause,
                    )
                    MiniControl(
                        icon = Icons.Rounded.FastForward,
                        active = state.hasNext,
                        enabled = state.hasNext,
                        onClick = onPlayNext,
                    )
                }
            }
            PlaybackTimeline(
                state = state,
                onSeekTo = onSeekTo,
                compact = true,
            )
        }
    }
}

@Composable
private fun ExpandedPlayerOverlay(
    state: PlayerUiState,
    visualizerState: VisualizerUiState,
    onDismiss: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPlayPrevious: () -> Unit,
    onPlayNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onSeekTo: (Long) -> Unit,
) {
    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF03060D), Color(0xFF07111B), Color(0xFF02040A)),
                ),
            ),
    ) {
        GlowOrb(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .align(Alignment.TopCenter),
            color = NeonPink.copy(alpha = 0.18f),
            secondaryColor = NeonCyan.copy(alpha = 0.12f),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Плеер",
                        color = NeonCyan,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = state.queueLabel,
                        color = MutedText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                MiniControl(icon = Icons.Rounded.KeyboardArrowDown, active = true, onClick = onDismiss)
            }

            Spacer(modifier = Modifier.weight(0.2f))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                CyberVisualizer(
                    modifier = Modifier.size(width = 300.dp, height = 220.dp),
                    bars = visualizerState.bars,
                    isPlaying = state.isPlaying,
                    isUsingFallback = visualizerState.isUsingFallback,
                )
                Text(
                    text = state.currentTrack?.title ?: "Линия ожидания",
                    color = InkText,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = state.currentTrack?.artist ?: state.queueLabel,
                    color = MutedText,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                PlaybackTimeline(
                    state = state,
                    onSeekTo = onSeekTo,
                    compact = false,
                )
            }

            Spacer(modifier = Modifier.weight(0.3f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiniControl(
                    icon = Icons.Rounded.Shuffle,
                    active = state.isShuffleEnabled,
                    enabled = state.queueSize > 1,
                    onClick = onToggleShuffle,
                )
                LargePlayerControl(icon = Icons.Rounded.FastRewind, enabled = state.hasPrevious, onClick = onPlayPrevious)
                LargePlayerControl(
                    icon = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    onClick = onTogglePlayPause,
                    primary = true,
                )
                LargePlayerControl(icon = Icons.Rounded.FastForward, enabled = state.hasNext, onClick = onPlayNext)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PlaybackTimeline(
    state: PlayerUiState,
    onSeekTo: (Long) -> Unit,
    compact: Boolean,
) {
    val duration = state.durationMs.coerceAtLeast(0L)
    val position = state.currentPositionMs.coerceIn(0L, duration.takeIf { it > 0L } ?: 0L)
    val activeFraction = state.progressFraction.takeIf { it > 0f } ?: if (duration > 0L) {
        position.toFloat() / duration.toFloat()
    } else {
        0f
    }
    val interactionSource = remember { MutableInteractionSource() }
    val isDragging by interactionSource.collectIsDraggedAsState()
    var sliderValue by remember(duration) { mutableStateOf(activeFraction) }

    LaunchedEffect(activeFraction, isDragging) {
        if (!isDragging) sliderValue = activeFraction
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp),
    ) {
        Slider(
            value = if (isDragging) sliderValue else activeFraction,
            onValueChange = { fraction -> sliderValue = fraction },
            onValueChangeFinished = {
                if (duration > 0L) onSeekTo((duration * sliderValue).toLong())
            },
            valueRange = 0f..1f,
            enabled = state.canSeek,
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 18.dp else 24.dp),
            thumb = {
                if (!compact || isDragging) {
                    SliderDefaults.Thumb(
                        interactionSource = interactionSource,
                        colors = SliderDefaults.colors(thumbColor = NeonPink),
                    )
                }
            },
            colors = SliderDefaults.colors(
                thumbColor = NeonPink,
                activeTrackColor = NeonCyan,
                inactiveTrackColor = NeonCyan.copy(alpha = 0.16f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatDuration(position),
                color = MutedText,
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
            )
            Text(
                text = formatDuration(duration),
                color = MutedText,
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun CyberVisualizer(
    modifier: Modifier = Modifier,
    bars: List<Float>,
    isPlaying: Boolean,
    isUsingFallback: Boolean,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(36.dp))
            .background(PanelFillAlt)
            .border(1.dp, NeonCyan.copy(alpha = 0.32f), RoundedCornerShape(36.dp)),
        contentAlignment = Alignment.Center,
    ) {
        GlowOrb(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            color = NeonPink.copy(alpha = if (isPlaying) 0.18f else 0.1f),
            secondaryColor = NeonCyan.copy(alpha = 0.16f),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.Bottom,
        ) {
            bars.forEachIndexed { index, baseHeight ->
                Box(
                    modifier = Modifier
                        .width(10.dp)
                        .height((160f * baseHeight.coerceIn(0.12f, 1f)).dp)
                        .offset(y = if (index % 2 == 0) 8.dp else 0.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(NeonCyan, NeonPink, Color(0xFFFFB36A)),
                            ),
                        ),
                )
            }
        }
        Text(
            text = if (isUsingFallback) "Локальный импульс" else "Аудио-сигнал",
            color = if (isUsingFallback) MutedText else AcidLime,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp),
        )
    }
}

@Composable
private fun LargePlayerControl(
    icon: ImageVector,
    onClick: () -> Unit,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .size(if (primary) 92.dp else 76.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(if (primary && enabled) NeonPink.copy(alpha = 0.18f) else PanelFillAlt)
            .border(
                1.dp,
                if (!enabled) Color(0xFF1B2433) else if (primary) NeonPink.copy(alpha = 0.72f) else NeonCyan.copy(alpha = 0.32f),
                RoundedCornerShape(26.dp),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (!enabled) MutedText.copy(alpha = 0.46f) else if (primary) InkText else NeonCyan,
            modifier = Modifier.size(if (primary) 38.dp else 30.dp),
        )
    }
}
@Composable
private fun SearchFieldCard(
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
private fun SearchStatusStrip(
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
private fun CyberTextField(
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
private fun TrackRow(
    track: TrackRecord,
    primaryLabel: String,
    secondaryLabel: String,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
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
            }
        }
    }
}

@Composable
private fun PlaylistChip(
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
private fun RemoteTrackRow(
    result: RemoteTrackCandidate,
    isDownloading: Boolean,
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
            DownloadIconButton(
                enabled = result.downloadUrl != null && !isDownloading,
                active = result.downloadUrl != null,
                onClick = onDownload,
            )
        }
    }
}

@Composable
private fun EmptyBlock(title: String, body: String) {
    PanelCard(title = title, accent = NeonCyan) {
        Text(body, color = MutedText)
    }
}

@Composable
private fun HeroPanel(
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
private fun MetricCard(
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
private fun PanelCard(
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
private fun PrimaryActionButton(
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
private fun SecondaryActionButton(
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
private fun MiniControl(
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
private fun DownloadIconButton(
    enabled: Boolean,
    active: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(InputShape)
            .background(if (enabled) NeonPink.copy(alpha = 0.18f) else PanelFillAlt)
            .border(1.dp, if (active) NeonPink.copy(alpha = 0.7f) else Color(0xFF1B2433), InputShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.FileDownload,
            contentDescription = "Скачать",
            tint = if (enabled) NeonPink else MutedText,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun GlowOrb(
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

private fun RemoteTrackCandidate.stableKey(): String {
    return "$sourceId|$title|$artist|${downloadUrl ?: detailUrl}"
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000.0).roundToInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
















