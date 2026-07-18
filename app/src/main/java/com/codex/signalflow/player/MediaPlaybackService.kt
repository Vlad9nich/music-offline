package com.codex.signalflow.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.codex.signalflow.MainActivity
import com.codex.signalflow.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@UnstableApi
class MediaPlaybackService : MediaSessionService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var playbackManager: PlaybackManager
    private var mediaSession: MediaSession? = null
    private var foregroundActive = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        playbackManager = PlaybackRuntime.getManager(this)
        mediaSession = MediaSession.Builder(this, playbackManager.player)
            .setSessionActivity(createContentIntent())
            .build()

        serviceScope.launch {
            // Rebuild notification only when identity-relevant fields change (not every position tick).
            playbackManager.playerState
                .map { state ->
                    NotificationIdentity(
                        trackId = state.currentTrack?.id,
                        title = state.currentTrack?.title,
                        artist = state.currentTrack?.artist,
                        isPlaying = state.isPlaying,
                        shuffle = state.isShuffleEnabled,
                        queueSize = state.queueSize,
                    ) to state
                }
                .distinctUntilChanged { a, b -> a.first == b.first }
                .collectLatest { (_, state) ->
                    val shouldShow = state.currentTrack != null && (state.queueSize > 0 || state.isPlaying)
                    playbackManager.setNotificationVisible(shouldShow)
                    if (!shouldShow) {
                        hideNotification()
                    } else {
                        showNotification(state)
                    }
                }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_PLAYBACK -> playbackManager.togglePlayPause()
            ACTION_NEXT -> playbackManager.playNext()
            ACTION_PREVIOUS -> playbackManager.playPrevious()
            ACTION_TOGGLE_SHUFFLE -> playbackManager.toggleShuffle()
            ACTION_STOP_SURFACE -> {
                hideNotification()
                stopSelf()
            }
            ACTION_START_SURFACE -> Unit
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!playbackManager.player.isPlaying) {
            hideNotification()
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    private fun showNotification(state: PlayerUiState) {
        val session = mediaSession ?: return
        val notification = buildNotification(state, session)
        if (state.isPlaying) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                } else {
                    0
                },
            )
            foregroundActive = true
        } else {
            if (foregroundActive) {
                stopForeground(STOP_FOREGROUND_DETACH)
                foregroundActive = false
            }
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun hideNotification() {
        if (foregroundActive) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foregroundActive = false
        }
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        playbackManager.setNotificationVisible(false)
    }

    private fun buildNotification(
        state: PlayerUiState,
        session: MediaSession,
    ): Notification {
        val currentTrack = state.currentTrack
        val title = currentTrack?.title ?: getString(R.string.app_name)
        val artist = currentTrack?.artist ?: "Ожидание запуска"
        val previousAction = NotificationCompat.Action(
            android.R.drawable.ic_media_previous,
            "Назад",
            createServicePendingIntent(ACTION_PREVIOUS, 1),
        )
        val playPauseAction = NotificationCompat.Action(
            if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            if (state.isPlaying) "Пауза" else "Пуск",
            createServicePendingIntent(ACTION_TOGGLE_PLAYBACK, 2),
        )
        val nextAction = NotificationCompat.Action(
            android.R.drawable.ic_media_next,
            "Далее",
            createServicePendingIntent(ACTION_NEXT, 3),
        )
        val shuffleAction = NotificationCompat.Action(
            android.R.drawable.ic_menu_rotate,
            if (state.isShuffleEnabled) "Шафл: вкл" else "Шафл: выкл",
            createServicePendingIntent(ACTION_TOGGLE_SHUFFLE, 4),
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(artist)
            .setContentIntent(createContentIntent())
            .setDeleteIntent(createServicePendingIntent(ACTION_STOP_SURFACE, 5))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(state.isPlaying)
            .setSilent(true)
            .setLargeIcon(createPlaceholderArtwork())
            .setStyle(
                MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
                    .setMediaSession(session.sessionCompatToken),
            )
            .addAction(previousAction)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .addAction(shuffleAction)
            .build()
    }

    private fun createContentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            100,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createServicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, MediaPlaybackService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "YaNeoDex Playback",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Управление воспроизведением YaNeoDex"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    private fun createPlaceholderArtwork(): Bitmap {
        val size = 256
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#09111A")
        }
        val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF4D8D")
            alpha = 230
        }
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#42F5FF")
            style = Paint.Style.STROKE
            strokeWidth = 10f
        }

        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), basePaint)
        canvas.drawCircle(size / 2f, size / 2f, size * 0.23f, pulsePaint)
        canvas.drawCircle(size / 2f, size / 2f, size * 0.34f, ringPaint)
        return bitmap
    }

    private data class NotificationIdentity(
        val trackId: String?,
        val title: String?,
        val artist: String?,
        val isPlaying: Boolean,
        val shuffle: Boolean,
        val queueSize: Int,
    )

    companion object {
        private const val CHANNEL_ID = "yaneodex_playback"
        private const val NOTIFICATION_ID = 42

        const val ACTION_START_SURFACE = "com.codex.signalflow.action.START_SURFACE"
        private const val ACTION_TOGGLE_PLAYBACK = "com.codex.signalflow.action.TOGGLE_PLAYBACK"
        private const val ACTION_NEXT = "com.codex.signalflow.action.NEXT"
        private const val ACTION_PREVIOUS = "com.codex.signalflow.action.PREVIOUS"
        private const val ACTION_TOGGLE_SHUFFLE = "com.codex.signalflow.action.TOGGLE_SHUFFLE"
        private const val ACTION_STOP_SURFACE = "com.codex.signalflow.action.STOP_SURFACE"

        fun ensureStarted(context: Context) {
            val intent = Intent(context, MediaPlaybackService::class.java).setAction(ACTION_START_SURFACE)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun sessionComponent(context: Context): ComponentName = ComponentName(context, MediaPlaybackService::class.java)
    }
}
