package com.bilimusic

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.bilimusic.data.api.BilibiliApiClient
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class BiliMusicApp : Application() {

    @Inject
    lateinit var sharedHttpClient: OkHttpClient

    companion object {
        const val CHANNEL_ID = "bilimusic_playback"
        const val DOWNLOAD_CHANNEL_ID = "bilimusic_downloads"
        private const val TAG = "BiliMusicApp"
        var logFile: File? = null
            private set

        fun appendLog(tag: String, msg: String) {
            try {
                logFile?.appendText("${SimpleDateFormat("HH:mm:ss.SSS").format(Date())} $tag: $msg\n")
            } catch (_: Exception) {}
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Share the DI-provided OkHttpClient across all components
        BilibiliApiClient.setHttpClient(sharedHttpClient)
        createNotificationChannels()
        setupGlobalCrashHandler()
        setupImageLoader()
        setupFileLogging()
    }

    private fun setupFileLogging() {
        try {
            // 使用 Download 目录，方便用户访问
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!dir.exists()) dir.mkdirs()
            logFile = File(dir, "BiliMusic_debug.log")
            if (logFile?.exists() == true) logFile?.delete()
            logFile?.createNewFile()
            val msg = "Log started at ${Date()}\n"
            logFile?.writeText(msg)
            Log.d(TAG, "Log file created: ${logFile?.absolutePath} exists=${logFile?.exists()}")
        } catch (e: Exception) {
            Log.e(TAG, "setupFileLogging failed", e)
        }
    }

    private fun setupImageLoader() {
        val imageLoader = ImageLoader.Builder(this)
            .okHttpClient(sharedHttpClient)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_cache"))
                    .maxSizeBytes(100 * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()

        Coil.setImageLoader(imageLoader)
        Log.d(TAG, "Coil ImageLoader initialized")
    }

    private fun setupGlobalCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "=== UNCAUGHT EXCEPTION ===", throwable)
            Log.e(TAG, "Thread: ${thread.name}")
            try {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(this, "错误: ${throwable.localizedMessage ?: "未知错误"}", Toast.LENGTH_LONG).show()
                }
            } catch (_: Exception) {}
            // 让默认处理器处理（通常会杀死进程）
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackChannel = NotificationChannel(
                CHANNEL_ID, getString(R.string.notif_channel_playback_name), NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_playback_desc)
                setShowBadge(false)
            }
            val downloadChannel = NotificationChannel(
                DOWNLOAD_CHANNEL_ID, getString(R.string.notif_channel_download_name), NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_download_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(playbackChannel)
            manager.createNotificationChannel(downloadChannel)
        }
    }
}
