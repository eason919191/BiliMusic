package com.bilimusic.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bilimusic.data.model.*
import com.bilimusic.data.preferences.AppPreferences
import com.bilimusic.player.MusicPlayer
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
    val progressBarStyle: ProgressBarStyle = ProgressBarStyle.ROUNDED,
    val playerBgPureColor: Boolean = false,
    val isMinimized: Boolean = false,
    val playerError: String? = null,
    val lyrics: List<com.bilimusic.data.api.LyricLine> = emptyList(),
    val showLyrics: Boolean = false,
    val currentLyricIndex: Int = -1
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

    init {
        // 歌曲切换时加载歌词（取消上一次加载避免竞态）
        viewModelScope.launch {
            musicPlayer.currentSong.collect { song ->
                lyricsLoadJob?.cancel()
                _uiState.update { it.copy(currentSong = song, lyrics = emptyList(), currentLyricIndex = -1) }
                if (song != null && song.bvid != null) {
                    android.util.Log.d("PlayerVM", "Loading lyrics for bvid=${song.bvid}, title=${song.title}")
                    lyricsLoadJob = loadLyrics(song.bvid, song.page.toLong(), song.title)
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

    fun clearError() {
        musicPlayer.clearError()
    }

    fun toggleLyrics() {
        _uiState.update { it.copy(showLyrics = !it.showLyrics) }
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

                // 直接用player/v2 API获取字幕（带cookie可获取AI字幕）
                val subtitles = com.bilimusic.data.api.BilibiliApiClient.getVideoSubtitles(bvid, cid, detail.aid)
                if (subtitles.isEmpty()) return@launch

                // 优先中文字幕（含AI字幕）
                val zhSub = subtitles.find { it.lan.contains("zh", ignoreCase = true) || it.lan.contains("ai-zh", ignoreCase = true) }
                    ?: subtitles.firstOrNull()

                if (zhSub != null && zhSub.subtitleUrl.isNotBlank()) {
                    val lines = com.bilimusic.data.api.BilibiliApiClient.fetchSubtitleContent(zhSub.subtitleUrl)
                    android.util.Log.d("PlayerVM", "Got ${lines.size} lines, first: ${lines.firstOrNull()?.text?.take(50)}")
                    if (lines.isNotEmpty()) {
                        // 确认这歌词不是上一次未取消请求的结果
                        if (_uiState.value.currentSong?.bvid == bvid) {
                            _uiState.update { it.copy(lyrics = lines) }
                        } else {
                            android.util.Log.d("PlayerVM", "Lyrics arrived for wrong song, discarding")
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("PlayerVM", "loadLyrics failed", e)
            }
        }
    }
}
