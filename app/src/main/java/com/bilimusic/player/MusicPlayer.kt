package com.bilimusic.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.AudioAttributes
import android.net.Uri
import android.util.Log
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import com.bilimusic.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import com.bilimusic.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.net.HttpURLConnection
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var mediaPlayer: MediaPlayer? = null

    private val _currentSong = MutableStateFlow<Music?>(null)
    val currentSong: StateFlow<Music?> = _currentSong.asStateFlow()
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()
    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()
    private val _playMode = MutableStateFlow(PlayMode.LOOP)
    val playMode: StateFlow<PlayMode> = _playMode.asStateFlow()
    private val _playlist = MutableStateFlow<List<Music>>(emptyList())
    val playlist: StateFlow<List<Music>> = _playlist.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var mediaSession: MediaSessionCompat? = null
    private var notificationManager: NotificationManager? = null
    private val NOTIFICATION_ID = 1
    private var notificationJob: Job? = null

    init {
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 通知渠道
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel("playback", "音乐播放", NotificationManager.IMPORTANCE_LOW).apply {
                description = "播放控制"
                setShowBadge(false)
            }.let { notificationManager?.createNotificationChannel(it) }
        }

        // 创建MediaSession（系统媒体控制）
        mediaSession = MediaSessionCompat(context, "BiliMusic").apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { togglePlayPause() }
                override fun onPause() { if (_isPlaying.value) togglePlayPause() }
                override fun onSkipToNext() { playNext() }
                override fun onSkipToPrevious() { playPrevious() }
                override fun onStop() { stop() }
                override fun onSeekTo(pos: Long) { seekTo(pos) }
            })
            isActive = true
        }

        // 位置更新
        scope.launch {
            while (isActive) {
                delay(250)
                val mp = mediaPlayer
                if (mp != null && mp.isPlaying) {
                    _currentPosition.value = mp.currentPosition.toLong().coerceAtLeast(0)
                    _duration.value = mp.duration.toLong().coerceAtLeast(0)
                }
            }
        }

        // 通知更新
        startNotificationUpdates()
    }

    private fun startNotificationUpdates() {
        notificationJob?.cancel()
        notificationJob = scope.launch {
            combine(_currentSong, _isPlaying, _currentPosition) { song, playing, pos ->
                Triple(song, playing, pos)
            }.collect { (song, playing, pos) ->
                updateMediaSession(song, playing, pos)
            }
        }
    }

    private fun updateMediaSession(song: Music?, playing: Boolean, position: Long) {
        val session = mediaSession ?: return
        session.setMetadata(android.support.v4.media.MediaMetadataCompat.Builder()
            .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE, song?.title ?: "BiliMusic")
            .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST, song?.artist ?: "")
            .putLong(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION, _duration.value)
            .build())
        session.setPlaybackState(PlaybackStateCompat.Builder()
            .setState(if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED, position, 1.0f)
            .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_STOP or PlaybackStateCompat.ACTION_SEEK_TO)
            .build())
        updateNotification(song, playing)
    }

    private var coverBitmap: android.graphics.Bitmap? = null

    private fun updateNotification(song: Music?, playing: Boolean) {
        val session = mediaSession ?: return
        val contentIntent = PendingIntent.getActivity(context, 0,
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // 异步加载封面图
        if (song?.coverUrl != null && coverBitmap == null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val url = song.coverUrl
                    val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                    conn.setRequestProperty("Referer", "https://www.bilibili.com/")
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    val input = conn.inputStream
                    coverBitmap = android.graphics.BitmapFactory.decodeStream(input)
                    input.close()
                    conn.disconnect()
                    withContext(Dispatchers.Main) { updateNotification(song, playing) }
                } catch (_: Exception) { coverBitmap = null }
            }
        }

        val notification = NotificationCompat.Builder(context, "playback")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(song?.title ?: "BiliMusic")
            .setContentText(song?.artist ?: "")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(session.sessionToken)
                .setShowActionsInCompactView(0, 1, 2))
            .addAction(android.R.drawable.ic_media_previous, "上一首", null)
            .addAction(if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (playing) "暂停" else "播放", null)
            .addAction(android.R.drawable.ic_media_next, "下一首", null)
            .apply {
                if (coverBitmap != null) setLargeIcon(coverBitmap)
            }
            .build()

        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    // ========== Playback methods ==========

    fun playSong(song: Music, songList: List<Music>? = null) {
        try {
            if (songList != null) _playlist.value = songList
            coverBitmap = null // 切歌时清除旧封面
            _currentSong.value = song
            val url = song.url ?: song.localPath ?: ""
            if (url.isBlank()) { _error.value = "没有可播放的音频"; return }

            releaseMediaPlayer()
            createMediaPlayer()
            val mp = mediaPlayer ?: return

            if (url.startsWith("/") || url.startsWith("file://")) {
                val path = if (url.startsWith("file://")) url.removePrefix("file://") else url
                mp.setDataSource(path)
            } else if (url.startsWith("content://")) {
                mp.setDataSource(context, Uri.parse(url))
            } else {
                mp.setDataSource(context, Uri.parse(url), mapOf(
                    "Referer" to "https://www.bilibili.com/",
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"))
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            Log.e("MusicPlayer", "playSong error", e)
            _error.value = "播放出错: ${e.localizedMessage}"
        }
    }

    private fun createMediaPlayer() {
        releaseMediaPlayer()
        val mp = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            setOnPreparedListener { player ->
                _duration.value = player.duration.toLong().coerceAtLeast(0)
                player.start(); _isPlaying.value = true
            }
            setOnSeekCompleteListener { player ->
                _currentPosition.value = player.currentPosition.toLong()
            }
            setOnCompletionListener {
                if (_playMode.value == PlayMode.SINGLE) {
                    it.seekTo(0); it.start()
                } else playNext()
            }
            setOnErrorListener { _, what, extra ->
                _error.value = when (extra) {
                    MediaPlayer.MEDIA_ERROR_IO -> "网络错误"
                    MediaPlayer.MEDIA_ERROR_TIMED_OUT -> "连接超时"
                    else -> "播放错误($what,$extra)"
                }
                true
            }
        }
        mediaPlayer = mp
    }

    private fun releaseMediaPlayer() {
        try { mediaPlayer?.apply { if (isPlaying) stop(); release() } } catch (_: Exception) {}
        mediaPlayer = null
    }

    fun playSongList(songs: List<Music>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        _playlist.value = songs
        playSong(songs[startIndex.coerceIn(0, songs.lastIndex)])
    }

    fun togglePlayPause() {
        val mp = mediaPlayer ?: return
        if (mp.isPlaying) { mp.pause(); _isPlaying.value = false }
        else { mp.start(); _isPlaying.value = true }
    }

    fun playNext() {
        val list = _playlist.value; if (list.isEmpty()) return
        val idx = list.indexOfFirst { it.id == _currentSong.value?.id }
        val nextIdx = when (_playMode.value) {
            PlayMode.SHUFFLE -> list.indices.random()
            PlayMode.SINGLE -> idx.coerceAtLeast(0)
            else -> (idx + 1) % list.size
        }
        val nextSong = list.getOrNull(nextIdx) ?: list.first()
        if ((nextSong.url == null && nextSong.localPath == null) && nextSong.bvid != null) {
            scope.launch {
                try {
                    val detail = com.bilimusic.data.api.BilibiliApiClient.getVideoDetail(nextSong.bvid!!)
                    if (detail != null) {
                        val url = com.bilimusic.data.api.BilibiliApiClient.getAudioUrl(nextSong.bvid!!, detail.cid)
                        if (url != null) {
                            val updated = nextSong.copy(url = url)
                            val newList = _playlist.value.toMutableList()
                            val realIdx = newList.indexOfFirst { it.id == nextSong.id }
                            if (realIdx >= 0) newList[realIdx] = updated
                            _playlist.value = newList
                            playSong(updated)
                            return@launch
                        }
                    }
                } catch (_: Exception) {}
                _error.value = "没有可播放的音频"
            }
            return
        }
        playSong(nextSong)
    }

    fun playPrevious() {
        val list = _playlist.value; if (list.isEmpty()) return
        val idx = list.indexOfFirst { it.id == _currentSong.value?.id }
        playSong(list.getOrNull(if (idx > 0) idx - 1 else list.size - 1) ?: list.last())
    }

    fun seekTo(pos: Long) {
        try {
            mediaPlayer?.seekTo(pos.toInt())
            // 立即更新以触发歌词索引重算，OnSeekCompleteListener 会校正实际位置
            _currentPosition.value = pos
        } catch (_: Exception) {}
    }

    fun setPlayMode(mode: PlayMode) { _playMode.value = mode }
    fun stop() { releaseMediaPlayer(); _isPlaying.value = false; _currentPosition.value = 0L }
    fun release() { scope.cancel(); releaseMediaPlayer(); mediaSession?.release() }
    fun addToPlaylist(songs: List<Music>) { _playlist.value = _playlist.value + songs }
    fun removeFromPlaylist(index: Int) {
        val list = _playlist.value.toMutableList()
        if (index in list.indices) { list.removeAt(index); _playlist.value = list }
    }
    fun clearPlaylist() { _playlist.value = emptyList(); stop() }
    fun clearError() { _error.value = null }

    /** Expose MediaSession token for MediaSessionService */
    fun getMediaSessionToken(): MediaSessionCompat.Token? = mediaSession?.sessionToken
}
