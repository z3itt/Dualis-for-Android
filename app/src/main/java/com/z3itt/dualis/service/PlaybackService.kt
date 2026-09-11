package com.z3itt.dualis.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.z3itt.dualis.DualisApplication
import com.z3itt.dualis.MainActivity
import com.z3itt.dualis.R
import com.z3itt.dualis.audio.SessionPlayer
import com.z3itt.dualis.domain.model.LoopMode

class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var sessionPlayer: SessionPlayer? = null
    private var placedForeground = false
    private val main = Handler(Looper.getMainLooper())
    private val stopIfIdle = Runnable { stopIfIdle() }
    private val hubListener: () -> Unit = { refreshControls() }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        val app = application as DualisApplication
        val hub = app.container.playback
        val notifications = DefaultMediaNotificationProvider.Builder(this)
            .setNotificationId(NOTIF_ID)
            .setChannelId(CHANNEL)
            .setChannelName(R.string.channel_playback)
            .build()
        notifications.setSmallIcon(R.drawable.ic_stat_playback)
        setMediaNotificationProvider(notifications)
        val wrapped = SessionPlayer(app.container.player.vocals, hub)
        sessionPlayer = wrapped
        val launch = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val built = MediaSession.Builder(this, wrapped)
            .setId("dualis-playback")
            .setSessionActivity(launch)
            .setCallback(SessionCallback())
            .build()
        session = built
        built.setMediaButtonPreferences(controlButtons())
        addSession(built)
        hub.addListener(hubListener)
        enterForeground()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        enterForeground()
        main.removeCallbacks(stopIfIdle)
        main.postDelayed(stopIfIdle, IDLE_STOP_MS)
        return result
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!shouldKeepPlaying()) {
            pauseAll()
            stopSelf()
        }
    }

    override fun onDestroy() {
        main.removeCallbacks(stopIfIdle)
        (application as DualisApplication).container.playback.removeListener(hubListener)
        session?.let { open ->
            removeSession(open)
            open.release()
        }
        session = null
        sessionPlayer = null
        placedForeground = false
        super.onDestroy()
    }

    @OptIn(UnstableApi::class)
    private fun enterForeground() {
        if (placedForeground) return
        val notification = mediaNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, notification)
        }
        placedForeground = true
    }

    @OptIn(UnstableApi::class)
    private fun mediaNotification(): Notification {
        val player = session?.player
        val title = player?.mediaMetadata?.title?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: getString(R.string.playback_notification_title)
        val text = player?.mediaMetadata?.artist?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: getString(R.string.app_name)
        val launch = session?.sessionActivity ?: PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_playback)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(launch)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        session?.let { open ->
            builder.setStyle(MediaStyleNotificationHelper.MediaStyle(open))
        }
        return builder.build()
    }

    @OptIn(UnstableApi::class)
    private fun refreshControls() {
        val update = {
            session?.setMediaButtonPreferences(controlButtons())
            sessionPlayer?.dispatchSessionChanged()
            Unit
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            update()
        } else {
            main.post { update() }
        }
    }

    @OptIn(UnstableApi::class)
    private fun controlButtons(): List<CommandButton> {
        val hub = (application as DualisApplication).container.playback
        val shuffleName = getString(if (hub.shuffle) R.string.shuffle_on else R.string.shuffle_off)
        val loopName = getString(
            when (hub.loopMode) {
                LoopMode.SONG -> R.string.repeat_song
                LoopMode.QUEUE -> R.string.repeat_queue
                LoopMode.OFF -> R.string.repeat_off
            },
        )
        val shuffleIcon = if (hub.shuffle) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_UNDEFINED
        val loopIcon = when (hub.loopMode) {
            LoopMode.SONG -> CommandButton.ICON_REPEAT_ONE
            LoopMode.QUEUE -> CommandButton.ICON_REPEAT_ALL
            LoopMode.OFF -> CommandButton.ICON_UNDEFINED
        }
        val shuffleDrawable = if (hub.shuffle) R.drawable.ic_stat_shuffle_on else R.drawable.ic_stat_shuffle
        val loopDrawable = when (hub.loopMode) {
            LoopMode.SONG -> R.drawable.ic_stat_repeat_one
            LoopMode.QUEUE -> R.drawable.ic_stat_repeat_on
            LoopMode.OFF -> R.drawable.ic_stat_repeat
        }
        return listOf(
            CommandButton.Builder(shuffleIcon)
                .setSessionCommand(SessionCommand(ACTION_SHUFFLE, Bundle.EMPTY))
                .setDisplayName(shuffleName)
                .setCustomIconResId(shuffleDrawable)
                .setSlots(CommandButton.SLOT_BACK_SECONDARY, CommandButton.SLOT_OVERFLOW)
                .build(),
            CommandButton.Builder(loopIcon)
                .setSessionCommand(SessionCommand(ACTION_LOOP, Bundle.EMPTY))
                .setDisplayName(loopName)
                .setCustomIconResId(loopDrawable)
                .setSlots(CommandButton.SLOT_FORWARD_SECONDARY, CommandButton.SLOT_OVERFLOW)
                .build(),
        )
    }

    private fun stopIfIdle() {
        val player = session?.player
        val hasMedia = player != null &&
            player.mediaItemCount > 0 &&
            player.playbackState != Player.STATE_IDLE
        if (hasMedia || shouldKeepPlaying()) return
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun shouldKeepPlaying(): Boolean {
        val player = session?.player ?: return false
        return player.playWhenReady &&
            player.mediaItemCount > 0 &&
            player.playbackState != Player.STATE_IDLE &&
            player.playbackState != Player.STATE_ENDED
    }

    private fun pauseAll() {
        (application as DualisApplication).container.player.pause()
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL,
            getString(R.string.channel_playback),
            NotificationManager.IMPORTANCE_LOW,
        )
        channel.setShowBadge(false)
        channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        manager.createNotificationChannel(channel)
    }

    @OptIn(UnstableApi::class)
    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(ACTION_SHUFFLE, Bundle.EMPTY))
                .add(SessionCommand(ACTION_LOOP, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .setMediaButtonPreferences(controlButtons())
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            val hub = (application as DualisApplication).container.playback
            when (customCommand.customAction) {
                ACTION_SHUFFLE -> hub.toggleShuffle()
                ACTION_LOOP -> hub.cycleLoop()
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    companion object {
        const val CHANNEL = "dualis_playback"
        const val NOTIF_ID = 1001
        const val ACTION_SHUFFLE = "com.z3itt.dualis.SHUFFLE"
        const val ACTION_LOOP = "com.z3itt.dualis.LOOP"
        private const val IDLE_STOP_MS = 8_000L

        fun start(context: Context) {
            val intent = Intent(context, PlaybackService::class.java)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: IllegalStateException) {
                context.startService(intent)
            } catch (_: SecurityException) {
                context.startService(intent)
            }
        }
    }
}
