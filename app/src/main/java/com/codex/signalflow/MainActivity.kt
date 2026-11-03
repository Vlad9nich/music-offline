package com.codex.signalflow

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.codex.signalflow.ui.SignalFlowApp
import com.codex.signalflow.ui.theme.SignalFlowTheme

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Minimize to background instead of destroying process on root back press
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })

        setContent {
            SignalFlowTheme {
                val uiState by viewModel.uiState.collectAsState()
                val playerState by viewModel.playerState.collectAsState()
                val snackbarMessage by viewModel.snackbarMessage.collectAsState()
                val hasNotificationPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED
                val postNotificationsPermission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { }
                val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                    uris.forEach { uri ->
                        runCatching {
                            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    }
                    viewModel.importTracks(uris)
                }
                val screenshotPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                    uris.forEach { uri ->
                        runCatching {
                            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    }
                    viewModel.importPlaylistScreenshots(uris)
                }

                LaunchedEffect(playerState.isPlaying, hasNotificationPermission) {
                    if (playerState.isPlaying &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !hasNotificationPermission
                    ) {
                        postNotificationsPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                SignalFlowApp(
                    uiState = uiState,
                    playerState = playerState,
                    snackbarMessage = snackbarMessage,
                    onImportTracks = { picker.launch(arrayOf("audio/*")) },
                    onImportPlaylistScreenshots = { screenshotPicker.launch(arrayOf("image/*")) },
                    onDismissSnackbar = viewModel::clearSnackbar,
                    onLibraryQueryChange = viewModel::setLibraryQuery,
                    onSourceQueryChange = viewModel::setSourceQuery,
                    onOcrServerUrlChange = viewModel::setOcrServerUrl,
                    onOcrAuthTokenChange = viewModel::setOcrAuthToken,
                    onSelectPlaylist = viewModel::selectPlaylist,
                    onCreatePlaylist = viewModel::createPlaylist,
                    onAddTrackToPlaylist = viewModel::addTrackToSelectedPlaylist,
                    onRemoveTrackFromPlaylist = viewModel::removeTrackFromSelectedPlaylist,
                    onPlayTrack = viewModel::playTrackFromLibrary,
                    onPlayPlaylist = viewModel::playSelectedPlaylist,
                    onShufflePlaylist = viewModel::shuffleSelectedPlaylist,
                    onTogglePlayPause = viewModel::togglePlayPause,
                    onPlayNext = viewModel::playNext,
                    onPlayPrevious = viewModel::playPrevious,
                    onToggleShuffle = viewModel::toggleShuffle,
                    onSeekTo = viewModel::seekTo,
                    onSearchSources = { query -> viewModel.searchSources(query) },
                    onPlayPlaylistTrack = viewModel::playTrackFromSelectedPlaylist,
                    onPreviewRemoteTrack = viewModel::previewRemoteTrack,
                    onDownloadRemoteTrack = viewModel::downloadRemoteTrack,
                    onDeleteLocalTrack = viewModel::deleteLocalTrack,
                    onDeletePlaylist = viewModel::deletePlaylist,
                    onDismissScreenshotImport = viewModel::dismissScreenshotImportSession,
                    onAddScreenshotImportMatch = viewModel::addScreenshotImportMatch,
                    onSetYandexToken = viewModel::setYandexToken,
                    onToggleYandexDirectSource = viewModel::toggleYandexDirectSource,
                    onStartYandexLikesSync = viewModel::startYandexLikesSync,
                    onStartYandexDeviceAuth = viewModel::startYandexDeviceAuth,
                    onCancelYandexDeviceAuth = viewModel::cancelYandexDeviceAuth,
                    onSetSelectedSourceFilter = viewModel::setSelectedSourceFilter,
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) viewModel.release()
    }
}
