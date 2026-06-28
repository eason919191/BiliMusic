package com.bilimusic.data.repository

import com.bilimusic.data.api.BilibiliApiClient
import com.bilimusic.data.api.VideoDetailData
import com.bilimusic.data.api.VideoPage
import com.bilimusic.data.database.MusicDao
import com.bilimusic.data.model.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    private val musicDao: MusicDao
) {
    // ===== Search =====
    suspend fun searchBilibili(keyword: String, page: Int = 1): List<BilibiliVideo> {
        return BilibiliApiClient.searchVideos(keyword, page)
    }

    suspend fun getVideoDetail(bvid: String): VideoDetailData? {
        return BilibiliApiClient.getVideoDetail(bvid)
    }

    suspend fun getAudioUrl(bvid: String, cid: Long): String? {
        return BilibiliApiClient.getAudioUrl(bvid, cid)
    }

    suspend fun getVideoPages(bvid: String): List<VideoPage> {
        return BilibiliApiClient.getVideoPages(bvid)
    }

    // ===== Music =====
    fun getAllMusic(): Flow<List<Music>> = musicDao.getAllMusic()

    suspend fun getMusicById(id: String): Music? = musicDao.getMusicById(id)

    suspend fun saveMusic(music: Music) = musicDao.insertMusic(music)

    suspend fun deleteMusic(music: Music) = musicDao.deleteMusic(music)

    fun searchMusic(query: String): Flow<List<Music>> = musicDao.searchMusic(query)

    // ===== Playlist =====
    fun getAllPlaylists(): Flow<List<Playlist>> = musicDao.getAllPlaylists()

    suspend fun getPlaylistById(id: String): Playlist? = musicDao.getPlaylistById(id)

    suspend fun createPlaylist(name: String, description: String = "", coverUrl: String? = null): String {
        val playlist = Playlist(name = name, description = description, coverUrl = coverUrl)
        musicDao.insertPlaylist(playlist)
        return playlist.id
    }

    suspend fun insertPlaylist(playlist: Playlist) = musicDao.insertPlaylist(playlist)

    suspend fun updatePlaylist(playlist: Playlist) = musicDao.updatePlaylist(playlist)
    suspend fun updatePlaylistSongCount(playlistId: String) = musicDao.updatePlaylistSongCount(playlistId)
    suspend fun fixAllPlaylistCounts() = musicDao.fixAllPlaylistCounts()

    suspend fun deletePlaylist(playlist: Playlist) {
        musicDao.deletePlaylist(playlist)
    }

    fun getSongsInPlaylist(playlistId: String): Flow<List<Music>> =
        musicDao.getSongsInPlaylist(playlistId)

    suspend fun getSongsInPlaylistOnce(playlistId: String): List<Music> =
        musicDao.getSongsInPlaylistOnce(playlistId)

    suspend fun addSongToPlaylist(playlistId: String, song: Music) {
        musicDao.insertMusic(song)
        val count = musicDao.getSongsInPlaylistOnce(playlistId).size
        musicDao.addSongToPlaylist(
            PlaylistSong(playlistId = playlistId, songId = song.id, order = count)
        )
        musicDao.updatePlaylistSongCount(playlistId)
        // 如果歌单没有封面，用第一首歌的封面
        val playlist = musicDao.getPlaylistById(playlistId)
        if (playlist != null && playlist.coverUrl == null && song.coverUrl != null) {
            musicDao.updatePlaylist(playlist.copy(coverUrl = song.coverUrl))
        }
    }

    suspend fun removeSongFromPlaylist(playlistId: String, songId: String) {
        musicDao.removeSongFromPlaylistById(playlistId, songId)
        musicDao.updatePlaylistSongCount(playlistId)
    }

    suspend fun isSongInPlaylist(playlistId: String, songId: String): Boolean {
        return musicDao.isSongInPlaylist(playlistId, songId) > 0
    }

    suspend fun copySongToPlaylist(fromPlaylistId: String, toPlaylistId: String, songId: String) {
        val song = musicDao.getMusicById(songId) ?: return
        val countInTarget = musicDao.getSongsInPlaylistOnce(toPlaylistId).size
        musicDao.addSongToPlaylist(PlaylistSong(playlistId = toPlaylistId, songId = songId, order = countInTarget))
        musicDao.updatePlaylistSongCount(toPlaylistId)
    }

    suspend fun moveSongToPlaylist(fromPlaylistId: String, toPlaylistId: String, songId: String) {
        copySongToPlaylist(fromPlaylistId, toPlaylistId, songId)
        musicDao.removeSongFromPlaylistById(fromPlaylistId, songId)
        musicDao.updatePlaylistSongCount(fromPlaylistId)
    }

    suspend fun reorderSong(playlistId: String, songId: String, newOrder: Int) {
        musicDao.removeSongFromPlaylistById(playlistId, songId)
        musicDao.addSongToPlaylist(PlaylistSong(playlistId = playlistId, songId = songId, order = newOrder))
    }

    // ===== Search History =====
    fun getSearchHistory(): Flow<List<SearchHistory>> = musicDao.getSearchHistory()

    suspend fun addSearchHistory(query: String) {
        musicDao.addSearchHistory(SearchHistory(query = query))
    }

    suspend fun clearSearchHistory() = musicDao.clearSearchHistory()

    suspend fun deleteSearchHistoryItem(id: Long) = musicDao.deleteSearchHistoryById(id)

    // ===== Download Task =====
    fun getAllDownloadTasks(): Flow<List<DownloadTask>> = musicDao.getAllDownloadTasks()

    suspend fun getDownloadTaskById(id: String): DownloadTask? = musicDao.getDownloadTaskById(id)

    suspend fun addDownloadTask(task: DownloadTask) = musicDao.insertDownloadTask(task)

    suspend fun updateDownloadTask(task: DownloadTask) = musicDao.updateDownloadTask(task)

    suspend fun deleteDownloadTask(task: DownloadTask) = musicDao.deleteDownloadTask(task)

    suspend fun deleteDownloadTaskById(id: String) = musicDao.deleteDownloadTaskById(id)

    suspend fun clearAllDownloadTasks() = musicDao.clearAllDownloadTasks()

    suspend fun updateDownloadTaskStatus(id: String, status: DownloadStatus) {
        musicDao.updateDownloadTaskStatus(id, status)
    }

    fun getActiveDownloadCount(): Flow<Int> = musicDao.getActiveDownloadCount()

    // ===== Favorite Folders (Bilibili) =====
    suspend fun getFavoriteFolders(cookie: String, upMid: Long = 0): List<BilibiliFavoriteFolder> {
        return BilibiliApiClient.getFavoriteFolders(cookie, upMid)
    }

    suspend fun getFavoriteResources(cookie: String, mediaId: Long): List<BilibiliVideo> {
        return BilibiliApiClient.getFavoriteResources(cookie, mediaId)
    }

    // ===== Local Music =====
    fun getLocalMusic(): Flow<List<Music>> = musicDao.getLocalMusic()

    suspend fun saveLocalMusic(music: Music) = musicDao.insertMusic(music)
}
