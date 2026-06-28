package com.bilimusic.ui.screens.playlist

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
    // 同步 B站收藏夹
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
                // 确保当前歌曲有音频URL
                if (target.url == null && target.localPath == null && target.bvid != null) {
                    val detail = BilibiliApiClient.getVideoDetail(target.bvid)
                    if (detail != null) {
                        val url = BilibiliApiClient.getAudioUrl(target.bvid, detail.cid)
                        if (url != null) {
                            val updated = target.copy(url = url)
                            repository.saveMusic(updated)
                            songs = songs.toMutableList().also { it[idx] = updated }
                        }
                    }
                }
                if (songs[idx].url != null || songs[idx].localPath != null) {
                    musicPlayer.playSongList(songs, idx)
                } else {
                    _uiState.update { it.copy(error = "没有可播放的音频", errorTitle = "播放失败") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "播放失败: ${e.message}", errorTitle = "错误") }
            }
        }
    }

    fun downloadSong(songId: String) {
        viewModelScope.launch {
            try {
                val songs = uiState.value.currentPlaylistSongs
                val song = songs.find { it.id == songId } ?: return@launch
                // 获取音频专用URL（DASH音频流，不是完整视频）
                var audioUrl = song.url
                if (song.bvid != null && song.bvid.isNotBlank()) {
                    val detail = com.bilimusic.data.api.BilibiliApiClient.getVideoDetail(song.bvid)
                    val cid = detail?.cid ?: 0L
                    if (cid > 0) {
                        // 下载专用：获取DASH纯音频流（文件小，3-8MB）
                        val dashUrl = com.bilimusic.data.api.BilibiliApiClient.getDownloadAudioUrl(song.bvid, cid)
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
                // 自动开始下载
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
                val videos = BilibiliApiClient.getFavoriteResources(cookie, folderId)
                if (videos.isEmpty()) {
                    _uiState.update { it.copy(error = "收藏夹为空或获取失败", errorTitle = "导入失败") }
                    return@launch
                }
                val playlist = Playlist(name = "B站收藏".take(50))
                repository.insertPlaylist(playlist)
                var count = 0
                videos.forEach { video ->
                    try {
                        val music = Music(
                            id = video.bvid,
                            title = video.title,
                            artist = video.author,
                            coverUrl = video.coverUrl,
                            duration = video.duration * 1000,
                            bvid = video.bvid
                        )
                        repository.addSongToPlaylist(playlist.id, music)
                        count++
                    } catch (_: Exception) {}
                }
                _uiState.update { it.copy(error = "成功导入 $count 首歌曲到歌单") }
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
        viewModelScope.launch {
            state.selectedSongIds.forEach { songId -> downloadSong(songId) }
            _uiState.update {
                it.copy(error = "已添加 ${state.selectedSongIds.size} 首到下载", isSelecting = false, selectedSongIds = emptySet())
            }
        }
    }

    fun batchRemoveSongs() {
        val state = _uiState.value
        val playlistId = state.selectedPlaylistId ?: return
        val ids = state.selectedSongIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { repository.removeSongFromPlaylist(playlistId, it) }
            _uiState.update {
                it.copy(error = "已移除 ${ids.size} 首", isSelecting = false, selectedSongIds = emptySet())
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
                    _uiState.update { it.copy(error = "该歌单未关联B站收藏夹", errorTitle = "无法同步") }
                    return@launch
                }
                val cookie = preferences.bilibiliCookie.first() ?: run {
                    _uiState.update { it.copy(error = "请先在设置中登录B站账号", errorTitle = "未登录") }
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
                    _uiState.update { it.copy(error = "该歌单未关联B站收藏夹") }
                    return@launch
                }
                val cookie = preferences.bilibiliCookie.first() ?: run {
                    _uiState.update { it.copy(error = "请先登录B站账号") }
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
