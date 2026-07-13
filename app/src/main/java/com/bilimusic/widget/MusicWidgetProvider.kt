package com.bilimusic.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import coil.ImageLoader
import coil.request.ImageRequest
import coil.target.Target
import com.bilimusic.MainActivity
import com.bilimusic.R
import com.bilimusic.player.MusicPlayer

class MusicWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "MusicWidget"
        const val ACTION_PLAY_PAUSE = "com.bilimusic.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.bilimusic.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.bilimusic.ACTION_PREVIOUS"

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, MusicWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
        }

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_music)

            // Set click to open app
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context, 0, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_title, openAppPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_artist, openAppPendingIntent)

            // Play/Pause button
            val playPauseIntent = Intent(context, MusicWidgetProvider::class.java).apply {
                action = ACTION_PLAY_PAUSE
            }
            val playPausePendingIntent = PendingIntent.getBroadcast(
                context, 1, playPauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_play, playPausePendingIntent)

            // Previous button
            val prevIntent = Intent(context, MusicWidgetProvider::class.java).apply {
                action = ACTION_PREVIOUS
            }
            val prevPendingIntent = PendingIntent.getBroadcast(
                context, 2, prevIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_prev, prevPendingIntent)

            // Next button
            val nextIntent = Intent(context, MusicWidgetProvider::class.java).apply {
                action = ACTION_NEXT
            }
            val nextPendingIntent = PendingIntent.getBroadcast(
                context, 3, nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_next, nextPendingIntent)

            // Update song info from MusicPlayer
            val player = MusicPlayer.instance
            if (player != null) {
                val song = player.currentSong.value
                val playing = player.isPlaying.value

                views.setTextViewText(R.id.widget_title, song?.title ?: "未在播放")
                views.setTextViewText(R.id.widget_artist, song?.artist ?: "BiliMusic")
                views.setImageViewResource(
                    R.id.widget_play,
                    if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                )

                // Load cover image
                val coverUrl = song?.coverUrl
                if (coverUrl != null) {
                    loadCoverImage(context, views, appWidgetManager, appWidgetId, coverUrl)
                } else {
                    views.setImageViewResource(R.id.widget_cover, android.R.drawable.ic_menu_gallery)
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            } else {
                views.setTextViewText(R.id.widget_title, "未在播放")
                views.setTextViewText(R.id.widget_artist, "BiliMusic")
                views.setImageViewResource(R.id.widget_play, android.R.drawable.ic_media_play)
                views.setImageViewResource(R.id.widget_cover, android.R.drawable.ic_menu_gallery)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }

        private fun loadCoverImage(
            context: Context,
            views: RemoteViews,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            url: String
        ) {
            val imageLoader = ImageLoader.Builder(context)
                .allowHardware(false)
                .build()

            val request = ImageRequest.Builder(context)
                .data(url)
                .crossfade(true)
                .target(
                    onStart = { placeholder ->
                        views.setImageViewResource(R.id.widget_cover, android.R.drawable.ic_menu_gallery)
                    },
                    onSuccess = { result ->
                        val bitmap = if (result is Bitmap) result else {
                            val b = android.graphics.Bitmap.createBitmap(
                                result.intrinsicWidth.coerceAtLeast(1),
                                result.intrinsicHeight.coerceAtLeast(1),
                                android.graphics.Bitmap.Config.ARGB_8888
                            )
                            val canvas = android.graphics.Canvas(b)
                            result.setBounds(0, 0, canvas.width, canvas.height)
                            result.draw(canvas)
                            b
                        }
                        views.setImageViewBitmap(R.id.widget_cover, bitmap)
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    },
                    onError = { error ->
                        views.setImageViewResource(R.id.widget_cover, android.R.drawable.ic_menu_gallery)
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    }
                )
                .build()

            imageLoader.enqueue(request)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.d(TAG, "Widget enabled")
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Log.d(TAG, "Widget disabled")
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_PLAY_PAUSE -> {
                Log.d(TAG, "Play/Pause clicked")
                MusicPlayer.instance?.togglePlayPause()
                updateAllWidgets(context)
            }
            ACTION_NEXT -> {
                Log.d(TAG, "Next clicked")
                MusicPlayer.instance?.playNext()
                updateAllWidgets(context)
            }
            ACTION_PREVIOUS -> {
                Log.d(TAG, "Previous clicked")
                MusicPlayer.instance?.playPrevious()
                updateAllWidgets(context)
            }
            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                // Handled by super
            }
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateAppWidget(context, appWidgetManager, appWidgetId)
    }
}
