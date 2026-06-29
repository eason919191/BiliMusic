package com.bili.music.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bili.music.data.api.BilibiliApiClient
import com.bili.music.data.api.LyricLine
import com.bili.music.data.api.PlayerSubtitleItem
import com.bili.music.data.model.*
import com.bili.music.data.preferences.AppPreferences
import com.bili.music.data.repository.MusicRepository
import com.bili.music.player.MusicPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayerUiState(
    val currentSong: Music? = null,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val playMode: PlayMode = PlayMode.LOOP,
    val playlist: List<Music> = emptyList(),
    val blurDegree: Float = 25f,
    val progressBarStyle: ProgressBarStyle = ProgressBarStyle.LINEAR,
    val playerBgPureColor: Boolean = false,
    val isMinimized: Boolean = false,
    val playerError: String? = null,
    val lyrics: List<LyricLine> = emptyList(),
    val showLyrics: Boolean = false,
    val currentLyricIndex: Int = -1,
    // 歌词多语言
    val lyricsMode: LyricsMode = LyricsMode.ZH_ONLY,
    val subtitleOptions: List<SubtitleOption> = emptyList(),
    /** 已加载的各语言字幕行 */
    val loadedLyrics: Map<String, List<LyricLine>> = emptyMap()
)

/** 字幕语言选项 */
data class SubtitleOption(
    val lang: String,
    val label: String,
    val url: String
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val musicPlayer: MusicPlayer,
    private val preferences: AppPreferences,
    private val repository: com.bili.music.data.repository.MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var lyricsLoadJob: kotlinx.coroutines.Job? = null

    init {
        // 歌词模式偏好
        viewModelScope.launch {
            preferences.lyricsMode.collect { mode: LyricsMode ->
                _uiState.update { it.copy(lyricsMode = mode) }
                // 已加载过歌词则刷新显示
                if (_uiState.value.loadedLyrics.isNotEmpty()) {
                    applyLyricsMode(mode)
                }
            }
        }

        // 歌曲切换时加载歌词（取消上一次加载避免竞态）
        viewModelScope.launch {
            musicPlayer.currentSong.collect { song ->
                lyricsLoadJob?.cancel()
                _uiState.update { it.copy(
                    currentSong = song, lyrics = emptyList(), currentLyricIndex = -1,
                    subtitleOptions = emptyList(), loadedLyrics = emptyMap()
                ) }
                if (song != null && song.bvid != null) {
                    android.util.Log.d("PlayerVM", "Loading lyrics for bvid=${song.bvid}, title=${song.title}")
                    lyricsLoadJob = loadAllLyrics(song.bvid, song.page.toLong())
                } else if (song != null) {
                    android.util.Log.d("PlayerVM", "No bvid for song: ${song.title}")
                }
            }
        }
        // 播放进度更新时跟踪当前歌词行
        viewModelScope.launch {
            musicPlayer.currentPosition.collect { pos ->
                val lyrics = _uiState.value.lyrics
                if (lyrics.isNotEmpty()) {
                    val idx = lyrics.indexOfLast { it.timeMs <= pos }
                    _uiState.update { it.copy(currentPosition = pos, currentLyricIndex = idx) }
                } else {
                    _uiState.update { it.copy(currentPosition = pos) }
                }
            }
        }
        viewModelScope.launch {
            musicPlayer.isPlaying.collect { playing ->
                _uiState.update { it.copy(isPlaying = playing) }
            }
        }
        viewModelScope.launch {
            musicPlayer.duration.collect { dur ->
                _uiState.update { it.copy(duration = dur) }
            }
        }
        viewModelScope.launch {
            musicPlayer.playMode.collect { mode ->
                _uiState.update { it.copy(playMode = mode) }
            }
        }
        viewModelScope.launch {
            musicPlayer.playlist.collect { list ->
                _uiState.update { it.copy(playlist = list) }
            }
        }
        viewModelScope.launch {
            preferences.blurDegree.collect { degree ->
                _uiState.update { it.copy(blurDegree = degree) }
            }
        }
        viewModelScope.launch {
            preferences.progressBarStyle.collect { style ->
                _uiState.update { it.copy(progressBarStyle = style) }
            }
        }
        viewModelScope.launch {
            musicPlayer.error.collect { err ->
                _uiState.update { it.copy(playerError = err) }
                if (err != null) {
                    kotlinx.coroutines.delay(5000)
                    musicPlayer.clearError()
                }
            }
        }
        viewModelScope.launch {
            preferences.playerBgPureColor.collect { pure ->
                _uiState.update { it.copy(playerBgPureColor = pure) }
            }
        }
    }

    fun togglePlayPause() = musicPlayer.togglePlayPause()
    fun playNext() = musicPlayer.playNext()
    fun playPrevious() = musicPlayer.playPrevious()

    fun seekTo(position: Long) = musicPlayer.seekTo(position)

    fun cyclePlayMode() {
        val next = when (musicPlayer.playMode.value) {
            PlayMode.LOOP -> PlayMode.SHUFFLE
            PlayMode.SHUFFLE -> PlayMode.SINGLE
            PlayMode.SINGLE -> PlayMode.LOOP
        }
        musicPlayer.setPlayMode(next)
    }

    fun minimize() {
        _uiState.update { it.copy(isMinimized = true) }
    }

    fun restore() {
        _uiState.update { it.copy(isMinimized = false) }
    }

    fun toggleMinimize() {
        _uiState.update { it.copy(isMinimized = !it.isMinimized) }
    }

    fun playSongAt(index: Int) {
        val list = _uiState.value.playlist
        if (index in list.indices) {
            musicPlayer.playSongList(list, index)
        }
    }

    fun removeFromPlaylist(index: Int) {
        musicPlayer.removeFromPlaylist(index)
    }

    fun addCurrentSongToPlaylist(song: Music, playlistId: String) {
        viewModelScope.launch {
            try {
                val musicToAdd = song.copy(url = song.url ?: song.localPath)
                repository.addSongToPlaylist(playlistId, musicToAdd)
                musicPlayer.clearError()
            } catch (e: Exception) {
                android.util.Log.e("PlayerVM", "add to playlist failed", e)
            }
        }
    }

    fun downloadCurrentSong() {
        val song = musicPlayer.currentSong.value ?: return
        viewModelScope.launch {
            try {
                var audioUrl = song.url
                if (song.bvid != null && song.bvid.isNotBlank()) {
                    val detail = BilibiliApiClient.getVideoDetail(song.bvid)
                    val cid = detail?.cid ?: 0L
                    if (cid > 0) {
                        val dashUrl = BilibiliApiClient.getDownloadAudioUrl(song.bvid, cid)
                        if (dashUrl != null) audioUrl = dashUrl
                    }
                }
                val task = DownloadTask(
                    id = song.id, musicId = song.id,
                    title = song.title, artist = song.artist,
                    coverUrl = song.coverUrl,
                    url = audioUrl ?: song.localPath ?: ""
                )
                repository.addDownloadTask(task)
                repository.updateDownloadTaskStatus(task.id, DownloadStatus.PENDING)
            } catch (_: Exception) {}
        }
    }

    fun getCurrentSongShareText(): String {
        val song = musicPlayer.currentSong.value ?: return ""
        return "【${song.title}】- ${song.artist}\nhttps://www.bilibili.com/video/${song.bvid ?: ""}"
    }

    fun clearError() {
        musicPlayer.clearError()
    }

    fun toggleLyrics() {
        _uiState.update { it.copy(showLyrics = !it.showLyrics) }
    }

    /** 切换歌词显示模式（中文 → 英文 → 双语 → 中文…） */
    fun cycleLyricsMode() {
        val next = when (_uiState.value.lyricsMode) {
            LyricsMode.ZH_ONLY -> LyricsMode.EN_ONLY
            LyricsMode.EN_ONLY -> LyricsMode.BILINGUAL
            LyricsMode.BILINGUAL -> LyricsMode.ZH_ONLY
        }
        viewModelScope.launch {
            preferences.setLyricsMode(next)
        }
    }

    /** 加载当前歌曲的所有可用字幕 */
    private fun loadAllLyrics(bvid: String, page: Long): kotlinx.coroutines.Job {
        return viewModelScope.launch {
            try {
                // 确保API客户端有cookie
                if (com.bili.music.data.api.BilibiliApiClient.userCookie.isBlank()) {
                    val savedCookie = preferences.bilibiliCookie.first()
                    if (savedCookie.isNotBlank()) {
                        com.bili.music.data.api.BilibiliApiClient.userCookie = savedCookie
                    }
                }
                val detail = com.bili.music.data.api.BilibiliApiClient.getVideoDetail(bvid)
                if (detail == null) return@launch
                val cid = detail.cid

                android.util.Log.d("PlayerVM", "Fetching all subtitles: bvid=$bvid cid=$cid")

                val subtitles = com.bili.music.data.api.BilibiliApiClient.getVideoSubtitles(bvid, cid, detail.aid)
                if (subtitles.isEmpty()) return@launch

                // 构建语言选项列表，AI字幕优先
                val options = subtitles.filter { it.subtitleUrl.isNotBlank() }.map { item ->
                    val label = if (item.lan.startsWith("ai-")) {
                        when {
                            item.lan.contains("zh") -> "中文"
                            item.lan.contains("en") -> "英文"
                            item.lan.contains("jp") -> "日文"
                            else -> item.lanDoc
                        }
                    } else item.lanDoc
                    SubtitleOption(lang = item.lan, label = label, url = item.subtitleUrl)
                }
                if (options.isEmpty()) return@launch

                // 同时加载所有语言的字幕
                val urlToLang = options.associate { it.url to it.lang }
                val linesMap = mutableMapOf<String, List<LyricLine>>()
                for (opt in options) {
                    try {
                        val lines = com.bili.music.data.api.BilibiliApiClient.fetchSubtitleContent(opt.url)
                        if (lines.isNotEmpty()) {
                            linesMap[opt.lang] = lines
                            android.util.Log.d("PlayerVM", "Loaded ${lines.size} lines for ${opt.lang}")
                        }
                    } catch (_: Exception) { }
                }

                if (_uiState.value.currentSong?.bvid != bvid) {
                    android.util.Log.d("PlayerVM", "Lyrics arrived for wrong song, discarding")
                    return@launch
                }

                _uiState.update { it.copy(
                    subtitleOptions = options,
                    loadedLyrics = linesMap
                ) }
                // 按当前模式合并显示
                applyLyricsMode(_uiState.value.lyricsMode)
            } catch (e: Exception) {
                android.util.Log.e("PlayerVM", "loadAllLyrics failed", e)
            }
        }
    }

    /** 根据模式合并歌词到显示列表 */
    private fun applyLyricsMode(mode: LyricsMode) {
        val loaded = _uiState.value.loadedLyrics
        val merged = when (mode) {
            LyricsMode.ZH_ONLY -> pickLang(loaded, "ai-zh", "zh")
            LyricsMode.EN_ONLY -> pickLang(loaded, "ai-en", "en")
            LyricsMode.BILINGUAL -> mergeBilingual(loaded)
        }
        _uiState.update { it.copy(lyrics = merged, lyricsMode = mode, currentLyricIndex = -1) }
    }

    /** 按优先级取一种语言 */
    private fun pickLang(map: Map<String, List<LyricLine>>, vararg priorities: String): List<LyricLine> {
        for (key in priorities) {
            map[key]?.let { if (it.isNotEmpty()) return it }
        }
        return map.values.firstOrNull() ?: emptyList()
    }

    /** 合并中英双语（按时间轴交织，相同时间合并成一行） */
    private fun mergeBilingual(map: Map<String, List<LyricLine>>): List<LyricLine> {
        val zh = pickLang(map, "ai-zh", "zh")
        val en = pickLang(map, "ai-en", "en")
        if (zh.isEmpty()) return en
        if (en.isEmpty()) return zh

        val result = mutableListOf<LyricLine>()
        var i = 0; var j = 0
        val tolerance = 150L // 同一句的时间容差 (ms)
        while (i < zh.size || j < en.size) {
            when {
                i >= zh.size -> { result.add(en[j]); j++ }
                j >= en.size -> { result.add(zh[i]); i++ }
                else -> {
                    val diff = zh[i].timeMs - en[j].timeMs
                    if (kotlin.math.abs(diff) <= tolerance) {
                        // 同一句，合并显示
                        result.add(LyricLine(zh[i].timeMs, "${zh[i].text}\n${en[j].text}"))
                        i++; j++
                    } else if (diff < 0) {
                        result.add(zh[i]); i++
                    } else {
                        result.add(en[j]); j++
                    }
                }
            }
        }
        return result
    }
}
