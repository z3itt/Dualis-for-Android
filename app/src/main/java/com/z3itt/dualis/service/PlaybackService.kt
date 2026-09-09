package com.z3itt.dualis.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.z3itt.dualis.DualisApplication
import com.z3itt.dualis.R

class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                "dualis_playback",
                getString(R.string.channel_playback),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        val player: Player = (application as DualisApplication).container.player.vocals
        session = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        pauseAll()
        stopSelf()
    }

    private fun pauseAll() {
        (application as DualisApplication).container.player.pause()
    }
}
