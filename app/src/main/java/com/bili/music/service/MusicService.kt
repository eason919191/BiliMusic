package com.bili.music.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * Foreground service for media playback.
 * Required by Android 13+ for any app that plays audio in background.
 * MusicPlayer starts this service and provides the notification via [notifyForeground].
 */
class MusicService : Service() {

    companion object {
        private const val TAG = "MusicService"
        private var pendingNotification: Notification? = null

        /**
         * Set the notification that [MusicService] should use on next [onStartCommand].
         * Called by [com.bili.music.player.MusicPlayer] before starting the service.
         */
        fun setNotification(notification: Notification) {
            pendingNotification = notification
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = pendingNotification
        if (notification != null) {
            pendingNotification = null
            try {
                startForeground(com.bili.music.player.MusicPlayer.NOTIFICATION_ID, notification)
                Log.d(TAG, "startForeground called")
            } catch (e: Exception) {
                Log.e(TAG, "startForeground failed", e)
            }
        } else {
            // No notification yet — stop service to avoid ANR or crash
            Log.w(TAG, "startForegroundService called without notification, stopping")
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSelf()
    }
}
