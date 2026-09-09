package com.remixtv.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import com.remixtv.R
import com.remixtv.RemixTVApplication
import com.remixtv.data.database.ActivePlaylistEntry
import com.remixtv.presentation.ui.MainActivity
import com.remixtv.utils.AppDebugLog
import com.remixtv.utils.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

/**
 * Фоновый сервис воспроизведения: ExoPlayer + MediaSession + уведомление с управлением.
 */
@UnstableApi
class PlaybackService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)

    private val binder = LocalBinder()

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private val prefs by lazy { PreferencesManager(applicationContext) }

    override fun onCreate() {
        super.onCreate()
        AppDebugLog.log("PlaybackService", "onCreate")
        createChannel()

        player = ExoPlayer.Builder(this).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
            playWhenReady = true
        }

        mediaSession = MediaSession.Builder(this, player).build()

        val app = application as RemixTVApplication
        serviceScope.launch {
            app.playlistRepository.observePlayableEntries()
                .map { entries -> entries.toQueueSignature() to entries.toMediaItems() }
                .distinctUntilChanged { old, new -> old.first == new.first }
                .collect { (_, items) ->
                    AppDebugLog.log("PlaybackService", "очередь обновлена: mediaItems=${items.size}")
                    if (items.isEmpty()) {
                        AppDebugLog.log("PlaybackService", "очередь пуста — stop")
                        player.stop()
                        player.clearMediaItems()
                    } else {
                        val currentId = player.currentMediaItem?.mediaId
                        player.setMediaItems(items)
                        player.prepare()
                        val index = items.indexOfFirst { it.mediaId == currentId }
                        if (index >= 0) {
                            player.seekToDefaultPosition(index)
                            AppDebugLog.log("PlaybackService", "prepare: сохранён индекс=$index id=$currentId")
                        } else {
                            AppDebugLog.log("PlaybackService", "prepare: с начала (новая очередь)")
                        }
                        player.playWhenReady = true
                    }
                    startForeground(NOTIFICATION_ID, buildNotification())
                }
        }

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                AppDebugLog.log("PlaybackService", "onIsPlayingChanged playing=$isPlaying")
                val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, buildNotification())
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                AppDebugLog.log(
                    "PlaybackService",
                    "onMediaItemTransition id=${mediaItem?.mediaId} reason=$reason"
                )
                val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, buildNotification())
            }
        })
    }

    override fun onBind(intent: Intent?): IBinder {
        AppDebugLog.log("PlaybackService", "onBind")
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppDebugLog.log("PlaybackService", "onStartCommand action=${intent?.action} flags=$flags startId=$startId")
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> {
                if (player.isPlaying) {
                    AppDebugLog.log("PlaybackService", "pause()")
                    player.pause()
                } else {
                    AppDebugLog.log("PlaybackService", "play()")
                    player.play()
                }
            }
            ACTION_NEXT -> {
                AppDebugLog.log("PlaybackService", "seekToNextMediaItem()")
                player.seekToNextMediaItem()
            }
            ACTION_PREV -> {
                AppDebugLog.log("PlaybackService", "seekToPreviousMediaItem()")
                player.seekToPreviousMediaItem()
            }
            else -> {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        AppDebugLog.log("PlaybackService", "onDestroy")
        serviceScope.cancel()
        mediaSession.release()
        player.release()
        super.onDestroy()
    }

    fun getPlayer(): ExoPlayer = player

    /** Обновить уведомление (например, после смены имени проигрывателя в настройках). */
    fun refreshMediaNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.playback_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            mgr.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )

        val playPause = PendingIntent.getService(
            this,
            1,
            Intent(this, PlaybackService::class.java).setAction(ACTION_PLAY_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )
        val next = PendingIntent.getService(
            this,
            2,
            Intent(this, PlaybackService::class.java).setAction(ACTION_NEXT),
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )
        val prev = PendingIntent.getService(
            this,
            3,
            Intent(this, PlaybackService::class.java).setAction(ACTION_PREV),
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )

        val name = prefs.playerName
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(name)
            .setContentText(getString(R.string.notification_playing))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(player.isPlaying)
            .addAction(
                android.R.drawable.ic_media_previous,
                getString(R.string.action_previous),
                prev
            )
            .addAction(
                if (player.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                getString(R.string.action_play_pause),
                playPause
            )
            .addAction(
                android.R.drawable.ic_media_next,
                getString(R.string.action_next),
                next
            )
            .setStyle(
                MediaStyleNotificationHelper.MediaStyle(mediaSession)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }

    private fun immutableFlag(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
    }

    private fun List<ActivePlaylistEntry>.toQueueSignature(): String {
        return joinToString("|") { e ->
            val v = e.video
            "${e.item.videoId}:${v?.localPath}:${v?.fileHash}:${v?.isDownloaded}"
        }
    }

    private fun List<ActivePlaylistEntry>.toMediaItems(): List<MediaItem> {
        val displayName = prefs.playerName
        return mapNotNull { e ->
            val v = e.video ?: return@mapNotNull null
            val path = v.localPath ?: return@mapNotNull null
            val f = File(path)
            if (!f.exists()) return@mapNotNull null
            MediaItem.Builder()
                .setUri(android.net.Uri.fromFile(f))
                .setMediaId(v.id.toString())
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(displayName)
                        .build()
                )
                .build()
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): PlaybackService = this@PlaybackService
    }

    companion object {
        private const val CHANNEL_ID = "remixtv_playback"
        private const val NOTIFICATION_ID = 42

        const val ACTION_PLAY_PAUSE = "com.remixtv.PLAY_PAUSE"
        const val ACTION_NEXT = "com.remixtv.NEXT"
        const val ACTION_PREV = "com.remixtv.PREV"

        fun start(context: Context) {
            AppDebugLog.log("PlaybackService", "start() foreground=${Build.VERSION.SDK_INT >= Build.VERSION_CODES.O}")
            val i = Intent(context, PlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }
    }
}
