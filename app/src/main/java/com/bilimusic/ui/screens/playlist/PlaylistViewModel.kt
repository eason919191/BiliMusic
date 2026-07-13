package com.bilimusic.ui.screens.playlist

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bilimusic.data.api.BilibiliApiClient
import com.bilimusic.data.model.*
import com.bilimusic.data.repository.MusicRepository
import com.bilimusic.player.MusicPlayer
import com.bilimusic.data.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistUiState(
    val playlists: List<Playlist> = emptyList(),
    val currentPlaylistSongs: List<Music> = emptyList(),
    val selectedPlaylistId: String? = null,
    val isShowingDetail: Boolean = false,
    val isCreatingPlaylist: Boolean = false,
    val newPlaylistName: String = "",
    val newPlaylistDescription: String = "",
    val error: String? = null,
    val errorTitle: String? = null,
    // Batch selection
    val isSelecting: Boolean = false,
    val selectedSongIds: Set<String> = emptySet(),
    // Copy/Move target
    val showTargetPlaylistDialog: Boolean = false,
    var isCopyMode: Boolean = true,
    // Tab
    val selectedTab: Int = 0,
    // 视图模式
    val isGridView: Boolean = true,
    // 最近播放
    val recentSongs: List<Music> = emptyList(),
    val isShowingRecent: Boolean = false,
    // 同步 哔哩哔哩收藏夹
    val isSyncing: Boolean = false,
    val showSyncDialog: Boolean = false
)

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val repository: MusicRepository,
    private val musicPlayer: MusicPlayer,
    private val preferences: AppPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaylistUiState())
    val uiState: StateFlow<PlaylistUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                repository.getAllPlaylists().collect { playlists ->
                    _uiState.update { it.copy(playlists = playlists) }
                }
            } catch (_: Exception) {}
        }
        viewModelScope.launch {
            try {
                repository.getRecentPlays().collect { recent ->
                    _uiState.update { it.copy(recentSongs = recent.map { play ->
                        Music(id = play.id, title = play.title, artist = play.artist,
                            coverUrl = play.coverUrl, duration = play.duration, source = play.source,
                            bvid = play.bvid, neteaseId = play.neteaseId)
                    }) }
                }
            } catch (_: Exception) {}
        }
    }

    fun setPlaylistTab(tab: Int) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun toggleGridView() {
        _uiState.update { it.copy(isGridView = !it.isGridView) }
    }

    fun showRecentPlaylist() {
        val recent = _uiState.value.recentSongs
        if (recent.isEmpty()) return
        _uiState.update {
            it.copy(
                isShowingDetail = true,
                selectedPlaylistId = "__recent__",
                currentPlaylistSongs = recent
            )
        }
    }

    fun hideRecentPlaylist() {
        _uiState.update { it.copy(isShowingDetail = false, selectedPlaylistId = null) }
    }

    fun clearRecentPlaylist() {
        viewModelScope.launch { repository.clearRecentPlays() }
    }

    fun playRecentSong(index: Int) {
        val songs = _uiState.value.recentSongs
        val song = songs.getOrNull(index) ?: return
        if (song.url != null && song.url.isNotBlank()) {
            // 有缓存的URL直接播放
            musicPlayer.playSongList(songs, index)
            return
        }
        // 没有URL需要获取
        viewModelScope.launch {
            try {
                var target = song
                val bvid = target.bvid
                if (bvid != null) {
                    val detail = com.bilimusic.data.api.BilibiliApiClient.getVideoDetail(bvid)
                    if (detail != null) {
                        val url = com.bilimusic.data.api.BilibiliApiClient.getAudioUrl(bvid, detail.cid)
                        if (url != null) target = target.copy(url = url)
                    }
                } else if (target.source == "NETEASE" && target.neteaseId > 0) {
                    val nid = target.neteaseId
                    val resp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        com.bilimusic.data.api.netease.NeteaseApiClient.getSongUrl(nid)
                    }
                    val url = com.bilimusic.data.api.netease.NeteaseSongParser.parseSongUrlRaw(resp)
                    if (url != null && url != "__VIP_ONLY__") target = target.copy(url = url)
                }
                val updatedSongs = songs.toMutableList().also { it[index] = target }
                musicPlayer.playSongList(updatedSongs, index)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "无法播放: ${e.message}") }
            }
        }
    }

    fun playAllRecent() {
        if (_uiState.value.recentSongs.isNotEmpty()) playRecentSong(0)
    }

    fun showPlaylistDetail(playlistId: String) {
        _uiState.update {
            it.copy(selectedPlaylistId = playlistId, isShowingDetail = true)
        }
        viewModelScope.launch {
            try {
                repository.getSongsInPlaylist(playlistId).collect { songs ->
                    _uiState.update { it.copy(currentPlaylistSongs = songs) }
                    // 自动用第一首歌封面更新歌单封面
                    val firstCover = songs.firstOrNull()?.coverUrl
                    if (firstCover != null) {
                        val pl = _uiState.value.playlists.find { it.id == playlistId }
                        if (pl != null && pl.coverUrl != firstCover) {
                            repository.updatePlaylist(pl.copy(coverUrl = firstCover))
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun hidePlaylistDetail() {
        _uiState.update { it.copy(isShowingDetail = false, selectedPlaylistId = null) }
    }

    fun showCreatePlaylist() {
        _uiState.update { it.copy(isCreatingPlaylist = true, newPlaylistName = "", newPlaylistDescription = "") }
    }

    fun hideCreatePlaylist() {
        _uiState.update { it.copy(isCreatingPlaylist = false) }
    }

    fun setNewPlaylistName(name: String) {
        _uiState.update { it.copy(newPlaylistName = name) }
    }

    fun setNewPlaylistDescription(desc: String) {
        _uiState.update { it.copy(newPlaylistDescription = desc) }
    }

    fun createPlaylist() {
        val name = _uiState.value.newPlaylistName.trim()
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                repository.createPlaylist(name, _uiState.value.newPlaylistDescription.trim())
                _uiState.update { it.copy(isCreatingPlaylist = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "创建失败: ${e.localizedMessage}", errorTitle = "错误") }
            }
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            try {
                val playlist = repository.getPlaylistById(playlistId) ?: return@launch
                repository.deletePlaylist(playlist)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "删除失败: ${e.localizedMessage}", errorTitle = "错误") }
            }
        }
    }

    fun removeSongFromPlaylist(playlistId: String, songId: String) {
        viewModelScope.launch {
            try {
                repository.removeSongFromPlaylist(playlistId, songId)
            } catch (_: Exception) {}
        }
    }

    fun playPlaylist(playlistId: String, startIndex: Int = 0) {
        viewModelScope.launch {
            try {
                var songs = repository.getSongsInPlaylistOnce(playlistId)
                if (songs.isEmpty()) return@launch
                val idx = startIndex.coerceIn(0, songs.lastIndex)
                val target = songs[idx]
                Log.i("PlaylistVM", "playPlaylist: idx=$idx, title=${target.title}, bvid=${target.bvid}, hasUrl=${target.url != null}, hasLocal=${target.localPath != null}")
                // 确保当前歌曲有音频URL
                var resolvedSongs = songs
                val resolvedTarget = resolvedSongs[idx]

                // 1) 哔哩哔哩: 通过bvid获取音频URL
                if (resolvedTarget.url == null && resolvedTarget.localPath == null && resolvedTarget.bvid != null) {
                    try {
                        Log.i("PlaylistVM", "No URL, fetching from 哔哩哔哩 API: bvid=${resolvedTarget.bvid}")
                        val detail = BilibiliApiClient.getVideoDetail(resolvedTarget.bvid)
                        Log.i("PlaylistVM", "getVideoDetail result: ${detail != null}")
                        if (detail != null) {
                            val url = BilibiliApiClient.getAudioUrl(resolvedTarget.bvid, detail.cid)
                            Log.i("PlaylistVM", "getAudioUrl result: ${url != null}  url=${url?.take(60)}")
                            if (url != null) {
                                val updated = resolvedTarget.copy(url = url)
                                repository.saveMusic(updated)
                                resolvedSongs = resolvedSongs.toMutableList().also { it[idx] = updated }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("PlaylistVM", "Failed to get audio URL for ${resolvedTarget.bvid}", e)
                    }
                }

                // 2) 网易云歌曲：获取音源
                val afterBili = resolvedSongs[idx]
                if (afterBili.url == null && afterBili.localPath == null && afterBili.source == "NETEASE" && afterBili.neteaseId > 0) {
                    try {
                        val resp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            com.bilimusic.data.api.netease.NeteaseApiClient.getSongUrl(afterBili.neteaseId)
                        }
                        val url = com.bilimusic.data.api.netease.NeteaseSongParser.parseSongUrlRaw(resp)
                        if (url != null && url != "__VIP_ONLY__") {
                            val updated = afterBili.copy(url = url)
                            repository.saveMusic(updated)
                            resolvedSongs = resolvedSongs.toMutableList().also { it[idx] = updated }
                        } else {
                            // VIP only or no URL - fallback to 哔哩哔哩
                            val mutableSongs = resolvedSongs.toMutableList()
                            fallbackNeteaseSong(afterBili, mutableSongs, idx)
                            resolvedSongs = repository.getSongsInPlaylistOnce(playlistId)
                        }
                    } catch (_: Exception) {
                        val mutableSongs = resolvedSongs.toMutableList()
                        fallbackNeteaseSong(afterBili, mutableSongs, idx)
                        resolvedSongs = repository.getSongsInPlaylistOnce(playlistId)
                    }
                }

                // 3) 仍然没有URL → 跳过
                if (resolvedSongs[idx].url == null && resolvedSongs[idx].localPath == null) {
                    _uiState.update { it.copy(error = "此歌曲暂时无法播放，正在跳过...") }
                    if (startIndex + 1 < resolvedSongs.size) {
                        playPlaylist(playlistId, startIndex + 1)
                    }
                    return@launch
                }
                musicPlayer.playSongList(resolvedSongs, idx)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "播放失败: ${e.message}", errorTitle = "错误") }
            }
        }
    }

    private suspend fun fallbackNeteaseSong(song: com.bilimusic.data.model.Music, songs: MutableList<com.bilimusic.data.model.Music>, idx: Int) {
        try {
            val keyword = "${song.title} ${song.artist}"
            val results = com.bilimusic.data.api.BilibiliApiClient.searchVideos(keyword, 1, "totalrank")
            val best = results.firstOrNull() ?: return
            val detail = com.bilimusic.data.api.BilibiliApiClient.getVideoDetail(best.bvid) ?: return
            val streamUrl = com.bilimusic.data.api.BilibiliApiClient.getAudioUrl(best.bvid, detail.cid) ?: return
            val updated = song.copy(url = streamUrl, bvid = best.bvid)
            repository.saveMusic(updated)
            songs[idx] = updated
        } catch (_: Exception) {}
    }

    fun pausePlayback() {
        if (musicPlayer.isPlaying.value) {
            musicPlayer.togglePlayPause()
        }
    }

    fun downloadSong(songId: String) {
        val songs = _uiState.value.currentPlaylistSongs
        val recent = _uiState.value.recentSongs
        val song = songs.find { it.id == songId } ?: recent.find { it.id == songId } ?: return
        downloadMusic(song)
    }

    private fun downloadMusic(song: Music) {
        viewModelScope.launch {
            try {
                var audioUrl = song.url
                if (song.bvid != null && song.bvid.isNotBlank()) {
                    val detail = com.bilimusic.data.api.BilibiliApiClient.getVideoDetail(song.bvid)
                    val cid = detail?.cid ?: 0L
                    if (cid > 0) {
                        val dashUrl = com.bilimusic.data.api.BilibiliApiClient.getDownloadAudioUrl(song.bvid, cid)
                        if (dashUrl != null) audioUrl = dashUrl
                    }
                } else if (song.neteaseId > 0) {
                    val resp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        com.bilimusic.data.api.netease.NeteaseApiClient.getSongUrl(song.neteaseId)
                    }
                    val url = com.bilimusic.data.api.netease.NeteaseSongParser.parseSongUrlRaw(resp)
                    if (url != null && url != "__VIP_ONLY__") audioUrl = url
                }
                val finalUrl = audioUrl?.takeIf { it.isNotBlank() }
                    ?: song.localPath?.takeIf { it.isNotBlank() }
                    ?: return@launch
                val task = DownloadTask(
                    id = song.id, musicId = song.id,
                    title = song.title, artist = song.artist,
                    coverUrl = song.coverUrl,
                    url = finalUrl
                )
                repository.addDownloadTask(task)
                repository.updateDownloadTaskStatus(task.id, DownloadStatus.PENDING)
            } catch (_: Exception) {}
        }
    }

    // ===== Batch Selection =====
    fun toggleSongSelection(songId: String) {
        _uiState.update {
            val newSet = it.selectedSongIds.toMutableSet()
            if (newSet.contains(songId)) newSet.remove(songId) else newSet.add(songId)
            it.copy(selectedSongIds = newSet, isSelecting = newSet.isNotEmpty())
        }
    }

    fun enterSelectionMode(firstId: String) {
        _uiState.update { it.copy(isSelecting = true, selectedSongIds = setOf(firstId)) }
    }

    fun exitSelectionMode() {
        _uiState.update { it.copy(isSelecting = false, selectedSongIds = emptySet()) }
    }

    fun selectAllSongs() {
        _uiState.update { it.copy(selectedSongIds = it.currentPlaylistSongs.map { m -> m.id }.toSet()) }
    }

    fun showCopyDialog(isCopy: Boolean) {
        _uiState.update { it.copy(showTargetPlaylistDialog = true, isCopyMode = isCopy) }
    }

    fun hideTargetDialog() {
        _uiState.update { it.copy(showTargetPlaylistDialog = false) }
    }

    fun batchCopyOrMoveToPlaylist(targetPlaylistId: String) {
        val state = _uiState.value
        val fromPlaylistId = state.selectedPlaylistId ?: return
        viewModelScope.launch {
            state.selectedSongIds.forEach { songId ->
                if (state.isCopyMode) repository.copySongToPlaylist(fromPlaylistId, targetPlaylistId, songId)
                else repository.moveSongToPlaylist(fromPlaylistId, targetPlaylistId, songId)
            }
            _uiState.update {
                it.copy(error = if (state.isCopyMode) "已复制到歌单" else "已移动到歌单",
                    isSelecting = false, selectedSongIds = emptySet(), showTargetPlaylistDialog = false)
            }
        }
    }

    // ===== Reorder =====
    fun moveSong(fromIndex: Int, toIndex: Int) {
        val songs = _uiState.value.currentPlaylistSongs.toMutableList()
        if (fromIndex !in songs.indices || toIndex !in songs.indices) return
        val song = songs.removeAt(fromIndex)
        songs.add(toIndex, song)
        _uiState.update { it.copy(currentPlaylistSongs = songs) }
        // Persist new order
        val playlistId = _uiState.value.selectedPlaylistId ?: return
        viewModelScope.launch {
            songs.forEachIndexed { index, music ->
                repository.reorderSong(playlistId, music.id, index)
            }
            repository.apply {
                val playlist = getPlaylistById(playlistId) ?: return@launch
                updatePlaylist(playlist.copy(updatedAt = System.currentTimeMillis()))
            }
        }
    }

    fun importBilibiliFavorites(cookie: String, folderId: Long) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(error = null) }
                Log.i("PlaylistVM", "开始导入收藏夹: folderId=$folderId cookieLen=${cookie.length}")
                val videos = BilibiliApiClient.getFavoriteResources(cookie, folderId)
                Log.i("PlaylistVM", "API返回 ${videos.size} 个视频")
                if (videos.isEmpty()) {
                    _uiState.update { it.copy(error = "收藏夹为空或获取失败", errorTitle = "导入失败") }
                    return@launch
                }
                val folderName = "哔哩哔哩收藏"
                val existing = repository.getAllPlaylistsOnce().find { it.favoriteFolderId == folderId }
                val playlist = if (existing != null) existing else {
                    Playlist(name = folderName, favoriteFolderId = folderId, favoriteFolderName = folderName)
                }
                if (existing == null) repository.insertPlaylist(playlist)
                Log.i("PlaylistVM", "歌单: ${playlist.id} (${playlist.name}) folderId=$folderId")
                val musics = videos.map { video ->
                    Music(
                        id = video.bvid,
                        title = video.title,
                        artist = video.author,
                        coverUrl = video.coverUrl,
                        duration = video.duration * 1000,
                        bvid = video.bvid
                    )
                }
                repository.batchAddSongsToPlaylist(playlist.id, musics)
                Log.i("PlaylistVM", "入库完成: ${musics.size} 首")
                val msg = "成功导入 ${musics.size} 首歌曲到歌单"
                _uiState.update { it.copy(error = msg) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = "导入失败: ${e.localizedMessage ?: "未知错误"}", errorTitle = "导入失败")
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null, errorTitle = null) }
    }

    // ===== Batch Operations =====
    fun batchDownloadSongs() {
        val state = _uiState.value
        val ids = state.selectedSongIds.toList()
        if (ids.isEmpty()) return
        ids.forEach { id -> downloadSong(id) }
        _uiState.update {
            it.copy(error = "已添加 ${ids.size} 首到下载", isSelecting = false, selectedSongIds = emptySet())
        }
    }

    fun batchRemoveSongs() {
        val state = _uiState.value
        val playlistId = state.selectedPlaylistId ?: return
        val ids = state.selectedSongIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            if (playlistId == "__recent__") {
                ids.forEach { repository.removeRecentPlayById(it) }
                val updated = state.recentSongs.filter { it.id !in ids }
                _uiState.update {
                    it.copy(currentPlaylistSongs = updated, error = "已移除 ${ids.size} 首", isSelecting = false, selectedSongIds = emptySet())
                }
            } else {
                ids.forEach { repository.removeSongFromPlaylist(playlistId, it) }
                val updated = state.currentPlaylistSongs.filter { it.id !in ids }
                _uiState.update {
                    it.copy(currentPlaylistSongs = updated, error = "已移除 ${ids.size} 首", isSelecting = false, selectedSongIds = emptySet())
                }
            }
        }
    }

    fun commitSongOrder(playlistId: String, orderedSongIds: List<String>) {
        viewModelScope.launch {
            orderedSongIds.forEachIndexed { index, songId ->
                repository.reorderSong(playlistId, songId, index)
            }
        }
    }

    fun showSyncDialog() {
        _uiState.update { it.copy(showSyncDialog = true) }
    }

    fun hideSyncDialog() {
        _uiState.update { it.copy(showSyncDialog = false) }
    }

    fun syncPlaylistWithBilibili(playlistId: String) {
        viewModelScope.launch {
            try {
                val playlist = repository.getPlaylistById(playlistId) ?: run {
                    _uiState.update { it.copy(error = "歌单不存在", errorTitle = "同步失败") }
                    return@launch
                }
                val folderId = playlist.favoriteFolderId ?: run {
                    _uiState.update { it.copy(error = "该歌单未关联哔哩哔哩收藏夹", errorTitle = "无法同步") }
                    return@launch
                }
                val cookie = preferences.bilibiliCookie.first() ?: run {
                    _uiState.update { it.copy(error = "请先在设置中登录哔哩哔哩账号", errorTitle = "未登录") }
                    return@launch
                }
                _uiState.update { it.copy(isSyncing = true) }
                val videos = BilibiliApiClient.getFavoriteResources(cookie, folderId)
                val existingIds = repository.getSongsInPlaylistOnce(playlistId).map { it.id }.toSet()
                var addedCount = 0
                var skippedCount = 0
                videos.forEach { video ->
                    if (video.bvid in existingIds) {
                        skippedCount++
                    } else {
                        val music = Music(id = video.bvid, title = video.title, artist = video.author,
                            coverUrl = video.coverUrl, duration = video.duration * 1000, bvid = video.bvid)
                        repository.addSongToPlaylist(playlistId, music)
                        addedCount++
                    }
                }
                repository.updatePlaylistSongCount(playlistId)
                _uiState.update {
                    it.copy(isSyncing = false,
                        error = "同步完成：API返回${videos.size}首，新增$addedCount 跳过$skippedCount")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSyncing = false, error = "同步失败: ${e.message}") }
            }
        }
    }

    fun pushToBilibili(playlistId: String) {
        viewModelScope.launch {
            try {
                val playlist = repository.getPlaylistById(playlistId) ?: run {
                    _uiState.update { it.copy(error = "歌单不存在") }
                    return@launch
                }
                val folderId = playlist.favoriteFolderId ?: run {
                    _uiState.update { it.copy(error = "该歌单未关联哔哩哔哩收藏夹") }
                    return@launch
                }
                val cookie = preferences.bilibiliCookie.first() ?: run {
                    _uiState.update { it.copy(error = "请先登录哔哩哔哩账号") }
                    return@launch
                }
                _uiState.update { it.copy(isSyncing = true) }
                val playlistSongs = repository.getSongsInPlaylistOnce(playlistId)
                val songsWithBvid = playlistSongs.filter { it.bvid != null && it.bvid.isNotBlank() }
                val favVideos = BilibiliApiClient.getFavoriteResources(cookie, folderId)
                val favBvids = favVideos.map { it.bvid }.toSet()
                var pushed = 0; var skipped = 0; var failed = 0
                songsWithBvid.forEach { song ->
                    if (song.bvid in favBvids) {
                        skipped++
                    } else {
                        if (BilibiliApiClient.addVideoToFavorite(cookie, folderId, song.bvid!!)) pushed++ else failed++
                    }
                }
                _uiState.update {
                    it.copy(isSyncing = false,
                        error = "推送完成：新增$pushed 跳过$skipped${if (failed > 0) " 失败$failed" else ""}")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSyncing = false, error = "推送失败: ${e.message}") }
            }
        }
    }
}
