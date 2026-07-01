package com.bilimusic.ui.screens.search

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bilimusic.data.api.BilibiliApiClient
import com.bilimusic.data.api.netease.NeteaseApiClient
import com.bilimusic.data.api.netease.NeteaseSong
import com.bilimusic.data.api.netease.NeteaseSongParser
import com.bilimusic.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.bilimusic.data.repository.MusicRepository
import com.bilimusic.player.MusicPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val searchResults: List<BilibiliVideo> = emptyList(),
    val neteaseResults: List<com.bilimusic.data.api.netease.NeteaseSong> = emptyList(),
    val neteasePlaylists: List<com.bilimusic.data.api.netease.NeteasePlaylist> = emptyList(),
    val neteaseSearchType: Int = 0, // 0=歌曲, 1=歌单, 2=歌手
    val searchHistory: List<SearchHistory> = emptyList(),
    val isSearching: Boolean = false,
    val isShowingResults: Boolean = false,
    val error: String? = null,
    val errorTitle: String? = null,
    val currentPage: Int = 1,
    val hasMore: Boolean = false,
    val searchSource: Int = 0,
    val isSelecting: Boolean = false,
    val selectedIds: Set<String> = emptySet()
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: MusicRepository,
    private val musicPlayer: MusicPlayer,
    private val preferences: com.bilimusic.data.preferences.AppPreferences,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getSearchHistory().collect { history ->
                _uiState.update { it.copy(searchHistory = history) }
            }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun onSearch(query: String) {
        try {
            if (query.isBlank()) return
            val source = _uiState.value.searchSource
            _uiState.update {
                it.copy(query = query, isShowingResults = true, isSearching = true, currentPage = 1, error = null, errorTitle = null)
            }

            viewModelScope.launch {
                try {
                    try { repository.addSearchHistory(query) } catch (_: Exception) {}

                    if (source == 0) {
                        // B站搜索
                        val results = try {
                            BilibiliApiClient.searchVideos(query, 1, searchSort)
                        } catch (e: Exception) {
                            Log.e("SearchVM", "searchVideos threw", e)
                            emptyList()
                        }
                        _uiState.update {
                            it.copy(
                                searchResults = results.distinctBy { it.bvid }.filter { !shouldFilter(it) },
                                isSearching = false,
                                hasMore = results.size >= 20,
                                error = if (results.isEmpty()) "没有找到相关结果" else null
                            )
                        }
                    } else {
                        // 网易云搜索
                        _uiState.update { it.copy(neteaseResults = emptyList(), neteasePlaylists = emptyList()) }
                        val searchType = _uiState.value.neteaseSearchType
                        when (searchType) {
                            0 -> {
                                val resp = withContext(Dispatchers.IO) { NeteaseApiClient.searchSongs(query) }
                                val results = withContext(Dispatchers.IO) { NeteaseSongParser.parseSearchResult(resp) }
                                _uiState.update { it.copy(neteaseResults = results, isSearching = false,
                                    error = if (results.isEmpty()) "没有找到相关结果" else null) }
                            }
                            1 -> {
                                val resp = withContext(Dispatchers.IO) { com.bilimusic.data.api.netease.NeteaseApiClient.searchPlaylists(query) }
                                val results = withContext(Dispatchers.IO) { com.bilimusic.data.api.netease.parseSearchPlaylists(resp) }
                                _uiState.update { it.copy(neteasePlaylists = results, isSearching = false,
                                    error = if (results.isEmpty()) "没有找到相关结果" else null) }
                            }
                            2 -> {
                                val resp = withContext(Dispatchers.IO) { com.bilimusic.data.api.netease.NeteaseApiClient.searchArtists(query) }
                                val results = withContext(Dispatchers.IO) { com.bilimusic.data.api.netease.parseSearchArtists(resp) }
                                _uiState.update { it.copy(neteaseResults = results, isSearching = false,
                                    error = if (results.isEmpty()) "没有找到相关结果" else null) }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SearchVM", "onSearch failed", e)
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            error = "搜索失败: ${e.localizedMessage ?: "未知错误"}",
                            errorTitle = "搜索失败"
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SearchVM", "onSearch OUTER failed", e)
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isSearching || !state.hasMore) return

        val nextPage = state.currentPage + 1
        _uiState.update { it.copy(isSearching = true) }

        viewModelScope.launch {
            runCatching {
                val results = BilibiliApiClient.searchVideos(state.query, nextPage, searchSort)
                _uiState.update {
                    it.copy(
                        searchResults = it.searchResults + results.filter { !shouldFilter(it) },
                        isSearching = false,
                        currentPage = nextPage,
                        hasMore = results.size >= 20
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isSearching = false) }
            }
        }
    }

    fun hideResults() {
        _uiState.update { it.copy(isShowingResults = false, query = "", error = null) }
    }

    fun setSearchSource(source: Int) {
        _uiState.update { it.copy(searchSource = source) }
    }

    fun setNeteaseSearchType(type: Int) {
        _uiState.update { it.copy(neteaseSearchType = type) }
    }

    fun importNeteasePlaylist(playlistId: Long) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isSearching = true) }
                val resp = withContext(Dispatchers.IO) { NeteaseApiClient.getPlaylistDetail(playlistId) }
                val songs = withContext(Dispatchers.IO) { NeteaseSongParser.parsePlaylistSongs(resp) }
                var added = 0
                for (song in songs) {
                    try {
                        val urlResp = withContext(Dispatchers.IO) { NeteaseApiClient.getSongUrl(song.id) }
                        val url = NeteaseSongParser.parseSongUrlRaw(urlResp)
                        if (url != null && url != "__VIP_ONLY__") {
                            val music = Music(
                                id = "netease_${song.id}",
                                title = song.name,
                                artist = song.artistName,
                                coverUrl = song.coverUrl,
                                duration = song.duration / 1000L,
                                url = url,
                                source = "NETEASE",
                                neteaseId = song.id
                            )
                            repository.addDownloadTask(DownloadTask(
                                id = "netease_${song.id}",
                                musicId = "netease_${song.id}",
                                title = song.name,
                                artist = song.artistName,
                                coverUrl = song.coverUrl,
                                url = url,
                                status = DownloadStatus.PENDING
                            ))
                            added++
                        }
                    } catch (_: Exception) {}
                }
                _uiState.update { it.copy(isSearching = false,
                    error = "导入完成，成功导入 $added 首歌曲") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSearching = false,
                    error = "导入失败: ${e.localizedMessage}") }
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            runCatching { repository.clearSearchHistory() }
        }
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch {
            runCatching { repository.deleteSearchHistoryItem(id) }
        }
    }

    private var playJob: kotlinx.coroutines.Job? = null

    fun playNeteaseSong(song: NeteaseSong) {
        playJob?.cancel()
        playJob = viewModelScope.launch {
            try {
                _uiState.update { it.copy(isSearching = true, error = null) }
                val resp = withContext(Dispatchers.IO) {
                    NeteaseApiClient.getSongUrl(song.id)
                }
                val url = NeteaseSongParser.parseSongUrlRaw(resp)

                if (url != null && url != "__VIP_ONLY__") {
                    val music = Music(
                        id = "netease_${song.id}",
                        title = song.name,
                        artist = song.artistName,
                        coverUrl = song.coverUrl,
                        duration = song.duration / 1000L,
                        url = url,
                        source = "NETEASE",
                        neteaseId = song.id
                    )
                    musicPlayer.playSongList(listOf(music), 0)
                    _uiState.update { it.copy(isSearching = false) }
                } else {
                    // NetEase doesn't have playable URL (VIP trial) → fallback to B站
                    _uiState.update { it.copy(isSearching = false, error = null) }
                    android.widget.Toast.makeText(context, "网易云无完整音源，自动切换到B站播放", android.widget.Toast.LENGTH_SHORT).show()
                    fallbackToBilibili(song)
                }
            } catch (e: Exception) {
                Log.e("SearchVM", "playNeteaseSong failed", e)
                android.widget.Toast.makeText(context, "网易云播放失败，自动切换到B站", android.widget.Toast.LENGTH_SHORT).show()
                fallbackToBilibili(song)
            }
        }
    }

    private suspend fun fallbackToBilibili(song: NeteaseSong) {
        try {
            val keyword = "${song.name} ${song.artistName}"
            val results = BilibiliApiClient.searchVideos(keyword, 1, "totalrank")
            val best = results.firstOrNull() ?: return
            val detail = BilibiliApiClient.getVideoDetail(best.bvid) ?: return
            val streamUrl = BilibiliApiClient.getAudioUrl(best.bvid, detail.cid) ?: return

            val music = Music(
                id = best.bvid,
                title = song.name,
                artist = song.artistName,
                coverUrl = song.coverUrl ?: best.coverUrl,
                duration = song.duration,
                url = streamUrl,
                bvid = best.bvid,
                source = "NETEASE",
                neteaseId = song.id
            )
            musicPlayer.playSongList(listOf(music), 0)
        } catch (e: Exception) {
            Log.e("SearchVM", "fallbackToBilibili failed", e)
            _uiState.update { it.copy(error = "播放失败: 所有音源均不可用", errorTitle = "播放失败") }
        }
    }

    fun playSongFromSearch(song: NeteaseSong) {
        playNeteaseSong(song)
    }

    fun downloadNeteaseSong(song: NeteaseSong) {
        viewModelScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) { NeteaseApiClient.getSongUrl(song.id) }
                val url = NeteaseSongParser.parseSongUrlRaw(resp)
                if (url != null && url != "__VIP_ONLY__") {
                    val task = DownloadTask(
                        id = "netease_${song.id}",
                        musicId = "netease_${song.id}",
                        title = song.name,
                        artist = song.artistName,
                        coverUrl = song.coverUrl,
                        url = url,
                        status = DownloadStatus.PENDING
                    )
                    repository.addDownloadTask(task)
                    _uiState.update { it.copy(error = "已添加到下载列表") }
                } else {
                    Toast.makeText(context, "该歌曲无可用下载源", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("SearchVM", "downloadNeteaseSong failed", e)
            }
        }
    }
    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    fun fetchSuggestions(keyword: String) {
        if (keyword.isBlank()) { _suggestions.value = emptyList(); return }
        viewModelScope.launch {
            try {
                val tags = com.bilimusic.data.api.BilibiliApiClient.fetchSearchSuggestions(keyword)
                if (_suggestions.value != tags) _suggestions.value = tags
            } catch (_: Exception) {}
        }
    }

    var filterLongVideos = true
    var filterLoopTitle = true
    var searchSort = "totalrank"
    var filterKeywords = ""

    init {
        viewModelScope.launch { preferences.searchSort.collect { searchSort = it } }
        viewModelScope.launch { preferences.filterLongVideos.collect { filterLongVideos = it } }
        viewModelScope.launch { preferences.filterLoopTitle.collect { filterLoopTitle = it } }
        viewModelScope.launch { preferences.filterKeywords.collect { filterKeywords = it } }
    }

    private fun shouldFilter(video: BilibiliVideo): Boolean {
        if (filterLongVideos && video.duration > 600) return true
        if (filterLoopTitle && video.title.contains("循环")) return true
        if (filterKeywords.isNotBlank()) {
            filterKeywords.split("|").forEach { kw ->
                if (kw.isNotBlank() && video.title.contains(kw.trim(), ignoreCase = true)) return true
            }
        }
        return false
    }

    fun playSong(video: BilibiliVideo) {
        if (video.bvid.isBlank()) return
        // 取消上一次播放任务
        playJob?.cancel()
        playJob = viewModelScope.launch {
            try {
                _uiState.update { it.copy(isSearching = true, error = null) }
                val detail = BilibiliApiClient.getVideoDetail(video.bvid)
                if (detail != null) {
                    // 直接获取流式URL（不等待完整下载）
                    val streamUrl = BilibiliApiClient.getAudioUrl(video.bvid, detail.cid)
                    if (streamUrl != null) {
                        val music = Music(
                            id = video.bvid,
                            title = video.title.ifBlank { detail.title },
                            artist = video.author.ifBlank { detail.owner?.name ?: "未知" },
                            coverUrl = video.coverUrl ?: detail.pic,
                            duration = (video.duration.coerceAtLeast(detail.duration)) * 1000,
                            url = streamUrl,
                            bvid = video.bvid
                        )
                        repository.saveMusic(music)
                        musicPlayer.playSong(music, listOf(music))
                        _uiState.update { it.copy(isSearching = false) }
                        return@launch
                    }
                }
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        error = "无法获取音频源 (可能: 版权限制 / 接口限制 / 网络问题)\nBVID: ${video.bvid}",
                        errorTitle = "播放失败"
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 被取消，忽略
            } catch (e: Exception) {
                Log.e("SearchVM", "playSong failed", e)
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        error = "播放失败: ${e.localizedMessage ?: "未知网络错误"}",
                        errorTitle = "播放失败"
                    )
                }
            }
        }
    }

    fun downloadSong(video: BilibiliVideo) {
        if (video.bvid.isBlank()) return
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(error = null) }
                val detail = BilibiliApiClient.getVideoDetail(video.bvid)
                if (detail != null) {
                    val audioUrl = BilibiliApiClient.getAudioUrl(video.bvid, detail.cid)
                    if (audioUrl != null) {
                        val task = DownloadTask(
                            id = video.bvid,
                            musicId = video.bvid,
                            title = video.title.ifBlank { detail.title },
                            artist = video.author.ifBlank { detail.owner?.name ?: "未知" },
                            coverUrl = video.coverUrl ?: detail.pic,
                            url = audioUrl,
                            status = DownloadStatus.PENDING
                        )
                        repository.addDownloadTask(task)
                        _uiState.update { it.copy(error = "已添加到下载列表") }
                    } else {
                        _uiState.update { it.copy(error = "获取音频链接失败", errorTitle = "下载失败") }
                    }
                }
            } catch (e: Exception) {
                Log.e("SearchVM", "download failed", e)
                _uiState.update {
                    it.copy(error = "下载失败: ${e.localizedMessage ?: "未知错误"}", errorTitle = "下载失败")
                }
            }
        }
    }

    // ===== Batch Selection =====
    fun toggleSelection(id: String) {
        _uiState.update {
            val newSet = it.selectedIds.toMutableSet()
            if (newSet.contains(id)) newSet.remove(id) else newSet.add(id)
            it.copy(selectedIds = newSet, isSelecting = newSet.isNotEmpty())
        }
    }

    fun enterSelectionMode(firstId: String) {
        _uiState.update { it.copy(isSelecting = true, selectedIds = setOf(firstId)) }
    }

    fun exitSelectionMode() {
        _uiState.update { it.copy(isSelecting = false, selectedIds = emptySet()) }
    }

    fun selectAll() {
        _uiState.update { it.copy(selectedIds = it.searchResults.map { v -> v.bvid }.toSet()) }
    }

    fun batchAddToPlaylist(playlistId: String) {
        val selected = _uiState.value.selectedIds
        viewModelScope.launch {
            selected.forEach { bvid ->
                val video = _uiState.value.searchResults.find { it.bvid == bvid } ?: return@forEach
                try {
                    val detail = BilibiliApiClient.getVideoDetail(bvid)
                    if (detail != null) {
                        val audioUrl = BilibiliApiClient.getAudioUrl(bvid, detail.cid)
                        val music = Music(
                            id = bvid, title = video.title, artist = video.author,
                            coverUrl = video.coverUrl, duration = video.duration * 1000,
                            url = audioUrl, bvid = bvid
                        )
                        repository.addSongToPlaylist(playlistId, music)
                    }
                } catch (_: Exception) {}
            }
            _uiState.update { it.copy(error = "已添加到歌单", isSelecting = false, selectedIds = emptySet()) }
        }
    }

    fun batchDownload() {
        val selected = _uiState.value.selectedIds
        selected.forEach { bvid ->
            val video = _uiState.value.searchResults.find { it.bvid == bvid } ?: return@forEach
            downloadSong(video)
        }
        _uiState.update { it.copy(error = "已添加到下载列表", isSelecting = false, selectedIds = emptySet()) }
    }

    fun addToPlaylist(video: BilibiliVideo, playlistId: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(error = null) }
                val detail = BilibiliApiClient.getVideoDetail(video.bvid)
                if (detail != null) {
                    val audioUrl = BilibiliApiClient.getAudioUrl(video.bvid, detail.cid)
                    val music = Music(
                        id = video.bvid,
                        title = video.title.ifBlank { detail.title },
                        artist = video.author.ifBlank { detail.owner?.name ?: "未知" },
                        coverUrl = video.coverUrl ?: detail.pic,
                        duration = video.duration * 1000,
                        url = audioUrl,
                        bvid = video.bvid
                    )
                    repository.addSongToPlaylist(playlistId, music)
                    _uiState.update { it.copy(error = "已添加到歌单") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "添加失败: ${e.localizedMessage}", errorTitle = "错误") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null, errorTitle = null) }
    }
}
