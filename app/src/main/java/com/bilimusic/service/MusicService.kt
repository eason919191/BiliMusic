package com.bilimusic.service

import android.content.Intent
import androidx.media3.session.MediaSessionService

class MusicService : MediaSessionService() {

    // This service is kept minimal - actual playback is in MusicPlayer
    // The MediaSession is managed by MusicPlayer directly

    override fun onCreate() {
        super.onCreate()
    }

    override fun onGetSession(controllerInfo: androidx.media3.session.MediaSession.ControllerInfo): androidx.media3.session.MediaSession? {
        return null // Session is handled by MusicPlayer's MediaSession (legacy API)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
