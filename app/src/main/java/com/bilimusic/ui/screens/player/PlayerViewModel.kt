package com.bilimusic.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bilimusic.data.api.BilibiliApiClient
import com.bilimusic.data.model.*
import com.bilimusic.data.preferences.AppPreferences
import com.bilimusic.data.repository.MusicRepository
import com.bilimusic.player.MusicPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val lyrics: List<com.bilimusic.data.api.LyricLine> = emptyList(),
    val showLyrics: Boolean = false,
    val currentLyricIndex: Int = -1,
    val lyricBlurEnabled: Boolean = true,
    val lyricBlurAmount: Float = 8f,
    val lyricBlurCurrent: Float = 0f,
    val lyricBlurNear: Float = 0f,
    val lyricBlurMid: Float = 4f,
    val lyricBlurFar: Float = 12f,
    val lyricTextAlign: String = "center",
    val lyricFontSize: Float = 18f,
    val lyricsLanguages: List<Pair<String, String>> = emptyList(),
    val selectedLyricsLanguage: String = "",
    val translatedLyrics: List<com.bilimusic.data.api.LyricLine> = emptyList(),
    val showCombinedLyrics: Boolean = true,
    val lyricsMode: Int = 0, // 0=中文, 1=EN, 2=中EN组合
    val sleepTimerRemaining: Long = 0L,
    val isTimerActive: Boolean = false,
    val playbackSpeed: Float = 1f,
    val pitch: Float = 1f,
    val audioQuality: String = "lossless",
    val showMenuSubtitle: Boolean = true,
    val lyricScaleCurrent: Float = 1.15f,
    val lyricScaleNear: Float = 1.0f,
    val lyricMaxWidth: Float = 0.9f
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val musicPlayer: MusicPlayer,
    private val preferences: AppPreferences,
    private val repository: com.bilimusic.data.repository.MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var lyricsLoadJob: kotlinx.coroutines.Job? = null
    private var sleepTimerJob: kotlinx.coroutines.Job? = null

    // 存储所有语言的歌词 Map<lanCode, List<LyricLine>>
    private val allLyricsMap = mutableMapOf<String, List<com.bilimusic.data.api.LyricLine>>()

    init {
        // 歌曲切换时加载歌词（取消上一次加载避免竞态）
        viewModelScope.launch {
            musicPlayer.currentSong.collect { song ->
                lyricsLoadJob?.cancel()
                allLyricsMap.clear()
                _uiState.update { it.copy(
                    currentSong = song,
                    lyrics = emptyList(),
                    currentLyricIndex = -1,
                    lyricsLanguages = emptyList(),
                    selectedLyricsLanguage = ""
                ) }
                if (song != null && song.neteaseId > 0) {
                    android.util.Log.d("PlayerVM", "Loading NetEase lyrics for id=${song.neteaseId}, title=${song.title}")
                    lyricsLoadJob = loadNeteaseLyrics(song.neteaseId)
                } else if (song != null && song.bvid != null) {
                    android.util.Log.d("PlayerVM", "Loading Bilibili subtitles for bvid=${song.bvid}, title=${song.title}, neteaseId=${song.neteaseId}")
                    if (song.neteaseId > 0) {
                        android.util.Log.d("PlayerVM", "Song has neteaseId=${song.neteaseId}, trying NetEase lyrics first")
                        lyricsLoadJob = loadNeteaseLyrics(song.neteaseId)
                    } else {
                        lyricsLoadJob = loadLyrics(song.bvid, 0L, song.title)
                    }
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
        viewModelScope.launch {
            preferences.lyricBlurAmount.collect { amount ->
                _uiState.update { it.copy(lyricBlurAmount = amount) }
            }
        }
        viewModelScope.launch {
            preferences.lyricBlurCurrent.collect { v ->
                _uiState.update { it.copy(lyricBlurCurrent = v) }
            }
        }
        viewModelScope.launch {
            preferences.lyricBlurNear.collect { v ->
                _uiState.update { it.copy(lyricBlurNear = v) }
            }
        }
        viewModelScope.launch {
            preferences.lyricBlurMid.collect { v ->
                _uiState.update { it.copy(lyricBlurMid = v) }
            }
        }
        viewModelScope.launch {
            preferences.lyricBlurFar.collect { v ->
                _uiState.update { it.copy(lyricBlurFar = v) }
            }
        }
        viewModelScope.launch {
            preferences.lyricTextAlign.collect { align ->
                _uiState.update { it.copy(lyricTextAlign = align) }
            }
        }
        viewModelScope.launch {
            preferences.lyricFontSize.collect { size ->
                _uiState.update { it.copy(lyricFontSize = size) }
            }
        }
        // Speed & pitch
        viewModelScope.launch {
            musicPlayer.playbackSpeed.collect { speed ->
                _uiState.update { it.copy(playbackSpeed = speed) }
            }
        }
        viewModelScope.launch {
            musicPlayer.pitch.collect { p ->
                _uiState.update { it.copy(pitch = p) }
            }
        }
        // Audio quality
        viewModelScope.launch {
            preferences.audioQuality.collect { quality ->
                _uiState.update { it.copy(audioQuality = quality) }
            }
        }
        // Sleep timer monitoring (移植自SettingsViewModel)
        viewModelScope.launch {
            combine(
                preferences.sleepTimerMillis,
                preferences.sleepTimerStart
            ) { millis, start -> Pair(millis, start) }.collect { (millis, start) ->
                sleepTimerJob?.cancel()
                if (millis > 0 && start > 0) {
                    sleepTimerJob = viewModelScope.launch {
                        while (isActive) {
                            val elapsed = System.currentTimeMillis() - start
                            val remaining = (millis - elapsed).coerceAtLeast(0)
                            _uiState.update { it.copy(sleepTimerRemaining = remaining, isTimerActive = remaining > 0) }
                            if (remaining <= 0) {
                                musicPlayer.stop()
                                preferences.clearSleepTimer()
                                _uiState.update { it.copy(isTimerActive = false, sleepTimerRemaining = 0) }
                                break
                            }
                            delay(1000)
                        }
                    }
                } else {
                    _uiState.update { it.copy(isTimerActive = false, sleepTimerRemaining = 0) }
                }
            }
        }
        viewModelScope.launch {
            preferences.showMenuSubtitle.collect { v ->
                _uiState.update { it.copy(showMenuSubtitle = v) }
            }
        }
        viewModelScope.launch {
            preferences.lyricScaleCurrent.collect { v ->
                _uiState.update { it.copy(lyricScaleCurrent = v) }
            }
        }
        viewModelScope.launch {
            preferences.lyricScaleNear.collect { v ->
                _uiState.update { it.copy(lyricScaleNear = v) }
            }
        }
        viewModelScope.launch {
            preferences.lyricMaxWidth.collect { v ->
                _uiState.update { it.copy(lyricMaxWidth = v) }
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

    // ===== Sleep timer =====
    fun setSleepTimer(millis: Long) {
        viewModelScope.launch { preferences.setSleepTimer(millis) }
    }
    fun clearSleepTimer() {
        viewModelScope.launch { preferences.clearSleepTimer() }
    }

    // ===== Speed & pitch =====
    fun setPlaybackSpeed(speed: Float) = musicPlayer.setPlaybackSpeed(speed)
    fun setPitch(pitch: Float) = musicPlayer.setPitch(pitch)

    // ===== Audio quality =====
    fun setAudioQuality(quality: String) {
        viewModelScope.launch { preferences.setAudioQuality(quality) }
    }

    // ===== Lyric editing (in-memory only) =====
    fun updateLyrics(newLyrics: List<com.bilimusic.data.api.LyricLine>) {
        _uiState.update { it.copy(lyrics = newLyrics) }
    }

    fun importLyricsFromFile(content: String) {
        val lines = parseNeteaseLyricLines(content)
        if (lines.isNotEmpty()) {
            _uiState.update { it.copy(lyrics = lines, currentLyricIndex = -1) }
        }
    }

    fun switchAudioSource(
        url: String? = null,
        bvid: String? = null,
        neteaseId: Long = 0L,
        source: String = "BILIBILI",
        isLocal: Boolean = false,
        localPath: String? = null
    ) {
        val current = musicPlayer.currentSong.value ?: return
        val updated = current.copy(
            url = url ?: current.url,
            bvid = bvid ?: current.bvid,
            neteaseId = if (neteaseId > 0) neteaseId else current.neteaseId,
            source = source,
            isLocal = isLocal,
            localPath = localPath ?: current.localPath
        )
        val list = _uiState.value.playlist.toMutableList()
        val idx = list.indexOfFirst { it.id == current.id }
        if (idx >= 0) {
            list[idx] = updated
            musicPlayer.playSongList(list, idx)
        } else {
            musicPlayer.playSong(updated, listOf(updated))
        }
    }

    fun toggleLyrics() {
        _uiState.update { it.copy(showLyrics = !it.showLyrics) }
    }

    /**
     * 循环歌词模式: 中文 → English → 中EN组合 → 中文 ...
     */
    fun cycleLyricsMode() {
        val state = _uiState.value
        val languages = state.lyricsLanguages
        if (languages.isEmpty()) return

        val zhLang = languages.firstOrNull { it.first.contains("zh", ignoreCase = true) || it.first.contains("ai-zh", ignoreCase = true) }
        val enLang = languages.firstOrNull { it.first.contains("en", ignoreCase = true) }
        val zhLyrics = zhLang?.let { allLyricsMap[it.first] } ?: emptyList()
        val enLyrics = enLang?.let { allLyricsMap[it.first] } ?: emptyList()
        val hasZh = zhLyrics.isNotEmpty()
        val hasEn = enLyrics.isNotEmpty()

        // Netease: check if original + translation available
        val neteaseOrig = allLyricsMap["netease"].orEmpty()
        val neteaseTrans = allLyricsMap["netease_en"].orEmpty()
        val hasNeteaseTrans = neteaseTrans.isNotEmpty()

        val nextMode = (state.lyricsMode + 1) % 3

        when (nextMode) {
            0 -> {
                val targetLyrics = if (zhLyrics.isNotEmpty()) zhLyrics else neteaseOrig
                _uiState.update { it.copy(
                    lyricsMode = 0,
                    selectedLyricsLanguage = zhLang?.first ?: languages.first().first,
                    lyrics = targetLyrics,
                    translatedLyrics = emptyList(),
                    showCombinedLyrics = false,
                    currentLyricIndex = if (targetLyrics.isNotEmpty()) {
                        targetLyrics.indexOfLast { l -> l.timeMs <= state.currentPosition }
                    } else -1
                ) }
            }
            1 -> {
                val targetLyrics = if (enLyrics.isNotEmpty()) enLyrics else neteaseTrans.ifEmpty { zhLyrics.ifEmpty { neteaseOrig } }
                val targetLang = if (enLang != null) enLang.first else if (hasNeteaseTrans) "netease_en" else languages.first().first
                _uiState.update { it.copy(
                    lyricsMode = 1,
                    selectedLyricsLanguage = targetLang,
                    lyrics = targetLyrics,
                    translatedLyrics = emptyList(),
                    showCombinedLyrics = false,
                    currentLyricIndex = if (targetLyrics.isNotEmpty()) {
                        targetLyrics.indexOfLast { l -> l.timeMs <= state.currentPosition }
                    } else -1
                ) }
            }
            2 -> {
                val primaryLyrics = (zhLyrics.ifEmpty { neteaseOrig }).ifEmpty { enLyrics.ifEmpty { neteaseOrig } }
                val secondaryLyrics = if (hasZh && hasEn) enLyrics else if (hasNeteaseTrans) neteaseTrans else emptyList()
                _uiState.update { it.copy(
                    lyricsMode = 2,
                    selectedLyricsLanguage = zhLang?.first ?: languages.first().first,
                    lyrics = primaryLyrics,
                    translatedLyrics = secondaryLyrics,
                    showCombinedLyrics = true,
                    currentLyricIndex = if (primaryLyrics.isNotEmpty()) {
                        primaryLyrics.indexOfLast { l -> l.timeMs <= state.currentPosition }
                    } else -1
                ) }
            }
        }
    }

    private fun loadLyrics(bvid: String, page: Long, title: String): kotlinx.coroutines.Job {
        return viewModelScope.launch {
            try {
                // 确保API客户端有cookie（从DataStore同步）
                if (com.bilimusic.data.api.BilibiliApiClient.userCookie.isBlank()) {
                    val savedCookie = preferences.bilibiliCookie.first()
                    if (savedCookie.isNotBlank()) {
                        com.bilimusic.data.api.BilibiliApiClient.userCookie = savedCookie
                    }
                }
                // 获取视频详情 - 使用与音频播放相同的cid
                val detail = com.bilimusic.data.api.BilibiliApiClient.getVideoDetail(bvid)
                if (detail == null) return@launch
                val cid = detail.cid

                android.util.Log.d("PlayerVM", "Fetching subtitles: bvid=$bvid cid=$cid title=$title")

                // 获取所有可用字幕
                val subtitles = com.bilimusic.data.api.BilibiliApiClient.getVideoSubtitles(bvid, cid, detail.aid)
                if (subtitles.isEmpty()) return@launch

                // 按优先级排序：中文字幕优先，英文其次，其它排最后
                val sorted = subtitles.sortedByDescending {
                    when {
                        it.lan.contains("zh", ignoreCase = true) || it.lan.contains("ai-zh", ignoreCase = true) -> 2
                        it.lan.contains("en", ignoreCase = true) || it.lanDoc.contains("en", ignoreCase = true) -> 1
                        else -> 0
                    }
                }

                // 加载所有语言的字幕
                val loadedMap = mutableMapOf<String, List<com.bilimusic.data.api.LyricLine>>()
                val languages = mutableListOf<Pair<String, String>>()

                for (sub in sorted) {
                    if (sub.subtitleUrl.isBlank()) continue
                    val lines = try {
                        com.bilimusic.data.api.BilibiliApiClient.fetchSubtitleContent(sub.subtitleUrl)
                    } catch (e: Exception) {
                        android.util.Log.e("PlayerVM", "Failed to load ${sub.lanDoc}", e)
                        emptyList()
                    }
                    if (lines.isNotEmpty()) {
                        loadedMap[sub.lan] = lines
                        languages.add(sub.lan to sub.lanDoc.ifBlank { sub.lan })
                        android.util.Log.d("PlayerVM", "Loaded ${lines.size} lines for ${sub.lanDoc}(${sub.lan})")
                    }
                }

                if (loadedMap.isEmpty()) return@launch

                allLyricsMap.clear()
                allLyricsMap.putAll(loadedMap)

                val defaultLang = sorted.first().lan
                val defaultLines = loadedMap[defaultLang] ?: emptyList()

                // 确认这不是上一次未取消请求的结果
                // 自动设置翻译歌词（优先英文）
                val translationLang = languages.firstOrNull { it.first.contains("en", ignoreCase = true) && it.first != defaultLang }
                    ?: languages.firstOrNull { it.first != defaultLang }
                val translationLines = translationLang?.let { loadedMap[it.first] } ?: emptyList()
                android.util.Log.d("PlayerVM", "Translation: ${translationLang?.second ?: "none"} (${translationLines.size} lines)")

                if (_uiState.value.currentSong?.bvid == bvid) {
                    _uiState.update { it.copy(
                        lyrics = defaultLines,
                        lyricsLanguages = languages,
                        selectedLyricsLanguage = defaultLang,
                        translatedLyrics = translationLines.takeIf { it.isNotEmpty() } ?: emptyList()
                    ) }
                } else {
                    android.util.Log.d("PlayerVM", "Lyrics arrived for wrong song, discarding")
                }
            } catch (e: Exception) {
                android.util.Log.e("PlayerVM", "loadLyrics failed", e)
            }
        }
    }

    private fun loadNeteaseLyrics(neteaseId: Long): kotlinx.coroutines.Job {
        return viewModelScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    com.bilimusic.data.api.netease.NeteaseApiClient.getLyricNew(neteaseId)
                }
                val root = org.json.JSONObject(resp)
                val lyricStr = root.optJSONObject("lrc")?.optString("lyric", "") ?: ""
                val tlyricStr = root.optJSONObject("tlyric")?.optString("lyric", "") ?: ""
                val klyricStr = root.optJSONObject("klyric")?.optString("lyric", "") ?: ""
                val yrcStr = root.optJSONObject("yrc")?.optString("lyric", "") ?: ""
                val ytlrcStr = root.optJSONObject("ytlrc")?.optString("lyric", "") ?: ""

                android.util.Log.d("PlayerVM", "loadNeteaseLyrics: id=$neteaseId lrc.len=${lyricStr.length} tlyric.len=${tlyricStr.length} yrc.len=${yrcStr.length} ytlrc.len=${ytlrcStr.length}")

                val hasKaraoke = yrcStr.isNotBlank() || klyricStr.isNotBlank()
                val lyricContent = if (hasKaraoke) (yrcStr.ifBlank { klyricStr }) else lyricStr
                if (lyricContent.isBlank()) {
                    android.util.Log.w("PlayerVM", "loadNeteaseLyrics: blank content, returning")
                    return@launch
                }
                val translation = tlyricStr.ifBlank { ytlrcStr }

                // Auto-detect YRC vs LRC
                val lines = parseNeteaseLyricLines(lyricContent)
                if (lines.isEmpty()) {
                    android.util.Log.w("PlayerVM", "loadNeteaseLyrics: parsed 0 lines, returning")
                    return@launch
                }

                val transLines = if (translation.isNotBlank()) {
                    parseNeteaseLyricLines(translation)
                } else emptyList()
                android.util.Log.d("PlayerVM", "loadNeteaseLyrics: ${lines.size} lines, ${transLines.size} trans lines")

                allLyricsMap.clear()
                allLyricsMap["netease"] = lines
                if (transLines.isNotEmpty()) {
                    allLyricsMap["netease_en"] = transLines
                }

                val hasTrans = transLines.isNotEmpty()
                _uiState.update { it.copy(
                    lyrics = lines,
                    lyricsLanguages = if (hasTrans) {
                        listOf("netease" to "原文", "netease_en" to "译文")
                    } else {
                        listOf("netease" to "歌词")
                    },
                    selectedLyricsLanguage = "netease",
                    translatedLyrics = if (hasTrans) transLines else emptyList(),
                    lyricsMode = 0,
                    showCombinedLyrics = false
                ) }
                android.util.Log.d("PlayerVM", "loadNeteaseLyrics: updated UI state, hasTrans=$hasTrans")
            } catch (e: Exception) {
                android.util.Log.e("PlayerVM", "loadNeteaseLyrics failed", e)
            }
        }
    }

    private fun parseNeteaseLyricLines(content: String): List<com.bilimusic.data.api.LyricLine> {
        // Try parsing LRC format
        val tag = Regex("""\[(\d{2}):(\d{2})(?:\.(\d{2,3}))?]""")
        val timeline = mutableListOf<Pair<Long, String>>()
        content.lines().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            val m = tag.find(line) ?: return@forEach
            val mm = m.groupValues[1].toInt()
            val ss = m.groupValues[2].toInt()
            val msStr = m.groupValues.getOrNull(3).orEmpty()
            val ms = when (msStr.length) { 0 -> 0; 2 -> msStr.toInt() * 10; else -> msStr.toInt() }
            val time = mm * 60_000L + ss * 1_000L + ms
            val text = line.substring(m.range.last + 1).trim()
            if (text.isNotEmpty()) timeline.add(time to text)
        }
        timeline.sortBy { it.first }
        val out = mutableListOf<com.bilimusic.data.api.LyricLine>()
        for (i in timeline.indices) {
            val (start, text) = timeline[i]
            out.add(com.bilimusic.data.api.LyricLine(timeMs = start, text = text))
        }
        return out
    }
}
