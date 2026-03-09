package com.codex.signalflow

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.codex.signalflow.ui.SignalFlowApp
import com.codex.signalflow.ui.theme.SignalFlowTheme

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SignalFlowTheme {
                val uiState by viewModel.uiState.collectAsState()
                val playerState by viewModel.playerState.collectAsState()
                val snackbarMessage by viewModel.snackbarMessage.collectAsState()
                val picker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenMultipleDocuments(),
                ) { uris ->
                    uris.forEach { uri ->
                        runCatching {
                            contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION,
                            )
                        }
                    }
                    viewModel.importTracks(uris)
                }

                SignalFlowApp(
                    uiState = uiState,
                    playerState = playerState,
                    snackbarMessage = snackbarMessage,
                    onImportTracks = { picker.launch(arrayOf("audio/*")) },
                    onDismissSnackbar = viewModel::clearSnackbar,
                    onLibraryQueryChange = viewModel::setLibraryQuery,
                    onSourceQueryChange = viewModel::setSourceQuery,
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
                    onSearchSources = viewModel::searchSources,
                    onPlayPlaylistTrack = viewModel::playTrackFromSelectedPlaylist,
                    onDownloadRemoteTrack = viewModel::downloadRemoteTrack,
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            viewModel.release()
        }
    }
}
