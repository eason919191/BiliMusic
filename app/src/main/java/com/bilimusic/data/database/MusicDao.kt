package com.bilimusic.data.database

import androidx.room.*
import com.bilimusic.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {

    // ===== Music =====
    @Query("SELECT * FROM music ORDER BY addedAt DESC")
    fun getAllMusic(): Flow<List<Music>>

    @Query("SELECT * FROM music WHERE id = :id")
    suspend fun getMusicById(id: String): Music?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMusic(music: Music)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMusicList(musicList: List<Music>)

    @Delete
    suspend fun deleteMusic(music: Music)

    @Query("DELETE FROM music WHERE id = :id")
    suspend fun deleteMusicById(id: String)

    @Query("SELECT * FROM music WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%'")
    fun searchMusic(query: String): Flow<List<Music>>

    // ===== Playlist =====
    @Query("SELECT * FROM playlist ORDER BY updatedAt DESC")
    fun getAllPlaylists(): Flow<List<Playlist>>

    @Query("SELECT * FROM playlist WHERE id = :id")
    suspend fun getPlaylistById(id: String): Playlist?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist)

    @Update
    suspend fun updatePlaylist(playlist: Playlist)

    @Delete
    suspend fun deletePlaylist(playlist: Playlist)

    @Query("DELETE FROM playlist WHERE id = :id")
    suspend fun deletePlaylistById(id: String)

    @Query("UPDATE playlist SET songCount = (SELECT COUNT(*) FROM playlist_song WHERE playlistId = :playlistId), updatedAt = :updatedAt WHERE id = :playlistId")
    suspend fun updatePlaylistSongCount(playlistId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE playlist SET songCount = (SELECT COUNT(*) FROM playlist_song WHERE playlistId = playlist.id)")
    suspend fun fixAllPlaylistCounts()

    // ===== Playlist-Song =====
    @Query("SELECT m.* FROM music m INNER JOIN playlist_song ps ON m.id = ps.songId WHERE ps.playlistId = :playlistId ORDER BY ps.`order` ASC")
    fun getSongsInPlaylist(playlistId: String): Flow<List<Music>>

    @Query("SELECT m.* FROM music m INNER JOIN playlist_song ps ON m.id = ps.songId WHERE ps.playlistId = :playlistId ORDER BY ps.`order` ASC")
    suspend fun getSongsInPlaylistOnce(playlistId: String): List<Music>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSongToPlaylist(playlistSong: PlaylistSong)

    @Delete
    suspend fun removeSongFromPlaylist(playlistSong: PlaylistSong)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSongsToPlaylist(songs: List<PlaylistSong>)

    @Query("DELETE FROM playlist_song WHERE playlistId = :playlistId AND songId IN (:songIds)")
    suspend fun removeSongsFromPlaylistByIds(playlistId: String, songIds: List<String>)

    @Query("DELETE FROM playlist_song WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylistById(playlistId: String, songId: String)

    @Query("SELECT COUNT(*) FROM playlist_song WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun isSongInPlaylist(playlistId: String, songId: String): Int

    // ===== Search History =====
    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT 20")
    fun getSearchHistory(): Flow<List<SearchHistory>>

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun deleteSearchHistoryByQuery(query: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSearchHistory(history: SearchHistory)

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()

    @Delete
    suspend fun deleteSearchHistoryItem(history: SearchHistory)

    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun deleteSearchHistoryById(id: Long)

    // ===== Download Task =====
    @Query("SELECT * FROM download_task ORDER BY createdAt DESC")
    fun getAllDownloadTasks(): Flow<List<DownloadTask>>

    @Query("SELECT * FROM download_task WHERE status = :status ORDER BY createdAt ASC")
    fun getDownloadTasksByStatus(status: DownloadStatus): Flow<List<DownloadTask>>

    @Query("SELECT * FROM download_task WHERE id = :id")
    suspend fun getDownloadTaskById(id: String): DownloadTask?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownloadTask(task: DownloadTask)

    @Update
    suspend fun updateDownloadTask(task: DownloadTask)

    @Delete
    suspend fun deleteDownloadTask(task: DownloadTask)

    @Query("DELETE FROM download_task WHERE id = :id")
    suspend fun deleteDownloadTaskById(id: String)

    @Query("DELETE FROM download_task")
    suspend fun clearAllDownloadTasks()

    @Query("UPDATE download_task SET status = :status WHERE id = :id")
    suspend fun updateDownloadTaskStatus(id: String, status: DownloadStatus)

    @Query("SELECT COUNT(*) FROM download_task WHERE status = :status")
    fun getDownloadTaskCount(status: DownloadStatus): Flow<Int>

    @Query("SELECT COUNT(*) FROM download_task WHERE status = 'DOWNLOADING' OR status = 'PENDING'")
    fun getActiveDownloadCount(): Flow<Int>

    // ===== Local Music =====
    @Query("SELECT * FROM music WHERE isLocal = 1 ORDER BY addedAt DESC")
    fun getLocalMusic(): Flow<List<Music>>
}
