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
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class BiliMusicApp : Application() {

    companion object {
        const val CHANNEL_ID = "bilimusic_playback"
        const val CHANNEL_NAME = "音乐播放"
        const val DOWNLOAD_CHANNEL_ID = "bilimusic_downloads"
        const val DOWNLOAD_CHANNEL_NAME = "下载"
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
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val original = chain.request()
                val newRequest = original.newBuilder()
                    .addHeader("Referer", "https://www.bilibili.com/")
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .build()
                Log.d(TAG, "Coil loading: ${original.url}")
                val response = chain.proceed(newRequest)
                if (!response.isSuccessful) {
                    Log.w(TAG, "Coil failed: ${response.code} for ${original.url}")
                }
                response
            }
            .build()

        val imageLoader = ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
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
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "音乐播放控制通知"
                setShowBadge(false)
            }
            val downloadChannel = NotificationChannel(
                DOWNLOAD_CHANNEL_ID, DOWNLOAD_CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "下载进度通知"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(playbackChannel)
            manager.createNotificationChannel(downloadChannel)
        }
    }
}
