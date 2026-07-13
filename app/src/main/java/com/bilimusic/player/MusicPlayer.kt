package com.bilimusic.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.session.MediaSession
import android.net.Uri
import android.util.Log
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.bilimusic.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import com.bilimusic.data.model.*
import com.bilimusic.data.repository.MusicRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MusicRepository
) {
    companion object {
        @Volatile
        private var _instance: MusicPlayer? = null
        val instance: MusicPlayer? get() = _instance
        private const val TAG = "MusicPlayer"
    }

    init {
        _instance = this
    }

    private var exoPlayer: ExoPlayer? = null

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

    private val _playbackSpeed = MutableStateFlow(1f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()
    private val _pitch = MutableStateFlow(1f)
    val pitch: StateFlow<Float> = _pitch.asStateFlow()

    // 当前歌词行（供系统通知使用）
    private val _currentLyricLine = MutableStateFlow("")
    val currentLyricLine: StateFlow<String> = _currentLyricLine.asStateFlow()

    /**
     * 由 PlayerViewModel 调用，更新系统通知中的歌词行
     */
    fun updateCurrentLyricLine(line: String) {
        _currentLyricLine.value = line
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var mediaSession: MediaSessionCompat? = null
    private var notificationManager: NotificationManager? = null
    private val NOTIFICATION_ID = 1
    private var notificationJob: Job? = null
    private var equalizer: Equalizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    private var retryCount = 0
    private val maxRetries = 3

    init {
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel("playback", "音乐播放", NotificationManager.IMPORTANCE_LOW).apply {
                description = "播放控制"
                setShowBadge(false)
            }.let { notificationManager?.createNotificationChannel(it) }
        }

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

        startNotificationUpdates()
        // 定期轮询位置更新（ExoPlayer 不会自动通知位置变化）
        scope.launch {
            while (isActive) {
                delay(250)
                val player = exoPlayer
                if (player != null && player.isPlaying) {
                    _currentPosition.value = player.currentPosition
                    _duration.value = player.duration.coerceAtLeast(0)
                }
            }
        }
    }

    private fun createExoPlayer() {
        releaseExoPlayer()
        exoPlayer = ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_READY -> {
                            _duration.value = duration.coerceAtLeast(0)
                            _isPlaying.value = playWhenReady
                            // Record each song in recent plays
                            _currentSong.value?.let { s ->
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        repository.addRecentPlay(RecentPlay(
                                            id = s.id, title = s.title, artist = s.artist,
                                            coverUrl = s.coverUrl, duration = s.duration,
                                            source = s.source, url = s.url, bvid = s.bvid, neteaseId = s.neteaseId
                                        ))
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                        Player.STATE_ENDED -> {
                            _isPlaying.value = false
                            if (_playMode.value == PlayMode.SINGLE) seekTo(0)
                            else playNext()
                        }
                        Player.STATE_BUFFERING -> {}
                        Player.STATE_IDLE -> {}
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.e("MusicPlayer", "ExoPlayer error: ${error.errorCodeName} ${error.message}")
                    // Source error (HTTP 403/404 etc) → the URL is stale, try to fetch fresh one then retry
                    if (error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) {
                        val failedSong = _currentSong.value
                        retryCount++
                        if (failedSong != null && retryCount <= maxRetries && failedSong.bvid != null) {
                            // Try to get fresh URL
                            scope.launch {
                                try {
                                    val detail = com.bilimusic.data.api.BilibiliApiClient.getVideoDetail(failedSong.bvid!!)
                                    if (detail != null) {
                                        val newUrl = com.bilimusic.data.api.BilibiliApiClient.getAudioUrl(failedSong.bvid!!, detail.cid)
                                        if (newUrl != null) {
                                            val updated = failedSong.copy(url = newUrl)
                                            playSong(updated, _playlist.value)
                                            return@launch
                                        }
                                    }
                                } catch (_: Exception) {}
                                playNext()
                            }
                        } else {
                            playNext()
                        }
                        return
                    }
                    if (retryCount < maxRetries && (
                        error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                        error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
                        error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)) {
                        retryCount++
                        playNext()
                    } else {
                        _error.value = "播放错误: ${error.localizedMessage ?: error.errorCodeName}"
                        retryCount = 0
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                }

                override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                    _currentPosition.value = newPosition.positionMs
                }
            })
            playWhenReady = true
        }
    }

    private fun buildMediaSource(url: String): MediaSource {
        // 本地文件：直接传 file:// URI
        val uri = if (url.startsWith("/") || url.startsWith("file://") || url.startsWith("content://")) {
            android.net.Uri.parse(url)
        } else {
            android.net.Uri.parse("file://$url")
        }
        if (url.startsWith("/") || url.startsWith("file://") || url.startsWith("content://")) {
            return ProgressiveMediaSource.Factory(androidx.media3.datasource.DefaultDataSource.Factory(context))
                .createMediaSource(MediaItem.fromUri(uri))
        }
        val headers = mutableMapOf<String, String>()
        headers["Referer"] = "https://www.bilibili.com/"
        headers["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        val dataSourceFactory = DefaultHttpDataSource.Factory().setDefaultRequestProperties(headers)
        return ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(Uri.parse(url)))
    }

    fun playSong(song: Music, songList: List<Music>? = null) {
        retryCount = 0
        // 切歌时清除旧封面缓存
        coverBitmap = null
        try {
            if (songList != null) _playlist.value = songList
            _currentSong.value = song
            // 更新桌面小组件
            com.bilimusic.widget.MusicWidgetProvider.updateAllWidgets(context)
            val url = song.url ?: song.localPath ?: ""
            if (url.isBlank()) {
                _error.value = "没有可播放的音频"
                return
            }

            createExoPlayer()
            val player = exoPlayer ?: return
            player.setMediaSource(buildMediaSource(url))
            player.prepare()
            // 试着attach均衡器
            try { attachEqualizer() } catch (_: Exception) {}
        } catch (e: Exception) {
            Log.e(TAG, "playSong error", e)
            _error.value = "播放出错: ${e.localizedMessage}"
        }
    }

    fun playSongList(songs: List<Music>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        _playlist.value = songs
        playSong(songs[startIndex.coerceIn(0, songs.lastIndex)])
    }

    fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) { player.pause(); _isPlaying.value = false }
        else { player.play(); _isPlaying.value = true }
        // 更新桌面小组件
        com.bilimusic.widget.MusicWidgetProvider.updateAllWidgets(context)
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
        if (nextSong.url == null && nextSong.localPath == null) {
            scope.launch {
                try {
                    var resolvedSong = nextSong
                    // 1) 哔哩哔哩: 通过bvid获取音频URL
                    if (nextSong.bvid != null) {
                        val detail = com.bilimusic.data.api.BilibiliApiClient.getVideoDetail(nextSong.bvid!!)
                        if (detail != null) {
                            val url = com.bilimusic.data.api.BilibiliApiClient.getAudioUrl(nextSong.bvid!!, detail.cid)
                            if (url != null) {
                                resolvedSong = nextSong.copy(url = url)
                            }
                        }
                    }
                    // 2) 网易云: 通过neteaseId获取
                    if (resolvedSong.url == null && nextSong.source == "NETEASE" && nextSong.neteaseId > 0) {
                        val resp = withContext(Dispatchers.IO) {
                            com.bilimusic.data.api.netease.NeteaseApiClient.getSongUrl(nextSong.neteaseId)
                        }
                        val url = com.bilimusic.data.api.netease.NeteaseSongParser.parseSongUrlRaw(resp)
                        if (url != null && url != "__VIP_ONLY__") {
                            resolvedSong = nextSong.copy(url = url)
                        }
                    }
                    // 3) 兜底: 用标题+艺人搜索哔哩哔哩
                    if (resolvedSong.url == null && nextSong.bvid == null) {
                        try {
                            val keyword = "${nextSong.title} ${nextSong.artist}"
                            val results = com.bilimusic.data.api.BilibiliApiClient.searchVideos(keyword, 1, "totalrank")
                            val best = results.firstOrNull()
                            if (best != null) {
                                val detail = com.bilimusic.data.api.BilibiliApiClient.getVideoDetail(best.bvid) ?: null
                                if (detail != null) {
                                    val streamUrl = com.bilimusic.data.api.BilibiliApiClient.getAudioUrl(best.bvid, detail.cid)
                                    if (streamUrl != null) {
                                        resolvedSong = nextSong.copy(url = streamUrl, bvid = best.bvid)
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                    if (resolvedSong.url != null || resolvedSong.localPath != null) {
                        val newList = _playlist.value.toMutableList()
                        val realIdx = newList.indexOfFirst { it.id == nextSong.id }
                        if (realIdx >= 0) newList[realIdx] = resolvedSong
                        _playlist.value = newList
                        playSong(resolvedSong)
                    } else {
                        _error.value = "没有可播放的音频"
                    }
                } catch (_: Exception) {
                    _error.value = "没有可播放的音频"
                }
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
        try { exoPlayer?.seekTo(pos) } catch (_: Exception) {}
        _currentPosition.value = pos
    }

    fun setPlayMode(mode: PlayMode) { _playMode.value = mode }
    fun stop() { releaseExoPlayer(); _isPlaying.value = false; _currentPosition.value = 0L }
    fun release() { scope.cancel(); releaseExoPlayer(); releaseEqualizer(); releaseLoudnessEnhancer(); mediaSession?.release() }
    fun addToPlaylist(songs: List<Music>) { _playlist.value = _playlist.value + songs }
    fun removeFromPlaylist(index: Int) {
        val list = _playlist.value.toMutableList()
        if (index in list.indices) { list.removeAt(index); _playlist.value = list }
    }
    fun clearPlaylist() { _playlist.value = emptyList(); stop() }
    fun clearError() { _error.value = null }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed.coerceIn(0.1f, 5.0f)
        exoPlayer?.playbackParameters = PlaybackParameters(_playbackSpeed.value, _pitch.value)
    }

    fun setPitch(pitch: Float) {
        _pitch.value = pitch.coerceIn(0.1f, 5.0f)
        exoPlayer?.playbackParameters = PlaybackParameters(_playbackSpeed.value, _pitch.value)
    }

    // ========== Equalizer & Loudness ==========

    fun attachEqualizer(): Boolean {
        val sessionId = exoPlayer?.audioSessionId ?: return false
        if (sessionId <= 0) return false
        return try {
            releaseEqualizer()
            val eq = Equalizer(0, sessionId).apply { enabled = true }
            equalizer = eq
            true
        } catch (_: Exception) { false }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        try { equalizer?.enabled = enabled } catch (_: Exception) {}
    }

    fun setEqualizerBandLevel(band: Int, levelMb: Int) {
        try { equalizer?.setBandLevel(band.toShort(), levelMb.toShort()) } catch (_: Exception) {}
    }

    fun getEqualizerBands(): List<Triple<Int, Int, Int>> {
        val eq = equalizer ?: return emptyList()
        return try {
            (0 until eq.numberOfBands).map { i ->
                Triple(i, eq.getCenterFreq(i.toShort()) / 1000, eq.getBandLevel(i.toShort()).toInt())
            }
        } catch (_: Exception) { emptyList() }
    }

    fun setLoudnessGain(gainMb: Int) {
        try {
            val sessionId = exoPlayer?.audioSessionId ?: return
            if (sessionId <= 0) return
            if (loudnessEnhancer == null) loudnessEnhancer = LoudnessEnhancer(sessionId)
            loudnessEnhancer?.setTargetGain(gainMb)
            loudnessEnhancer?.enabled = gainMb > 0
        } catch (_: Exception) {}
    }

    private fun releaseEqualizer() {
        try { equalizer?.enabled = false; equalizer?.release() } catch (_: Exception) {}
        equalizer = null
    }

    private fun releaseLoudnessEnhancer() {
        try { loudnessEnhancer?.enabled = false; loudnessEnhancer?.release() } catch (_: Exception) {}
        loudnessEnhancer = null
    }

    // ========== Notification & MediaSession ==========

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
        // 如果当前有歌词行，用歌词作为标题；否则用歌曲名
        val lyricLine = _currentLyricLine.value
        val displayTitle = if (lyricLine.isNotBlank()) lyricLine else (song?.title ?: "BiliMusic")
        val metaBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, displayTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song?.artist ?: "")
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, _duration.value)
        // 设置封面图
        if (coverBitmap != null) {
            metaBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, coverBitmap)
        }
        session.setMetadata(metaBuilder.build())
        session.setPlaybackState(PlaybackStateCompat.Builder()
            .setState(if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED, position, _playbackSpeed.value)
            .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_STOP or PlaybackStateCompat.ACTION_SEEK_TO)
            .build())
        updateNotification(song, playing)
    }

    private var coverBitmap: android.graphics.Bitmap? = null

    // 缓存 PendingIntents 避免 FLAG_IMMUTABLE 创建多次
    private val playIntent by lazy { PendingIntent.getActivity(context, 1001,
        Intent(context, MainActivity::class.java).apply { action = "com.bilimusic.PLAY"; flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE) }
    private val pauseIntent by lazy { PendingIntent.getActivity(context, 1002,
        Intent(context, MainActivity::class.java).apply { action = "com.bilimusic.PAUSE"; flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE) }
    private val nextIntent by lazy { PendingIntent.getActivity(context, 1003,
        Intent(context, MainActivity::class.java).apply { action = "com.bilimusic.NEXT"; flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE) }
    private val prevIntent by lazy { PendingIntent.getActivity(context, 1004,
        Intent(context, MainActivity::class.java).apply { action = "com.bilimusic.PREV"; flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE) }

    private fun updateNotification(song: Music?, playing: Boolean) {
        val session = mediaSession ?: return
        val lyricLine = _currentLyricLine.value
        val displayTitle = if (lyricLine.isNotBlank()) lyricLine else (song?.title ?: "BiliMusic")
        val contentIntent = PendingIntent.getActivity(context, 0,
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        if (song?.coverUrl != null && coverBitmap == null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val conn = java.net.URL(song.coverUrl).openConnection() as java.net.HttpURLConnection
                    conn.setRequestProperty("Referer", "https://www.bilibili.com/")
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                    conn.connectTimeout = 5000; conn.readTimeout = 5000
                    val input = conn.inputStream
                    coverBitmap = android.graphics.BitmapFactory.decodeStream(input)
                    input.close(); conn.disconnect()
                    withContext(Dispatchers.Main) { updateNotification(song, playing) }
                } catch (_: Exception) { coverBitmap = null }
            }
        }

        val notification = NotificationCompat.Builder(context, "playback")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(displayTitle)
            .setContentText(song?.artist ?: "")
            .setContentIntent(contentIntent)
            .setOngoing(true).setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(MediaStyle()
                .setMediaSession(session.sessionToken)
                .setShowActionsInCompactView(0, 1, 2)
                .setShowCancelButton(true))
            .addAction(android.R.drawable.ic_media_previous, "上一首", prevIntent)
            .addAction(if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (playing) "暂停" else "播放", if (playing) pauseIntent else playIntent)
            .addAction(android.R.drawable.ic_media_next, "下一首", nextIntent)
            .apply { if (coverBitmap != null) setLargeIcon(coverBitmap) }
            .build()

        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    private fun releaseExoPlayer() {
        try { exoPlayer?.stop(); exoPlayer?.release() } catch (_: Exception) {}
        exoPlayer = null
    }

    fun getMediaSessionToken(): MediaSessionCompat.Token? = mediaSession?.sessionToken
}
