package com.bilimusic.ui.screens.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bilimusic.data.api.BilibiliApiClient
import com.bilimusic.data.model.*
import com.bilimusic.data.repository.MusicRepository
import com.bilimusic.player.MusicPlayer
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
    var isCopyMode: Boolean = true
)

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val repository: MusicRepository,
    private val musicPlayer: MusicPlayer
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaylistUiState())
    val uiState: StateFlow<PlaylistUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                // 一次性修复所有歌单计数
                repository.fixAllPlaylistCounts()
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
                    val detail = com.bilimusic.data.api.BilibiliApiClient.getVideoDetail(target.bvid)
                    if (detail != null) {
                        val url = com.bilimusic.data.api.BilibiliApiClient.getAudioUrl(target.bvid, detail.cid)
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
}
