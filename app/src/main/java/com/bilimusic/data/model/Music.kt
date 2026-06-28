package com.bilimusic.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 音乐实体 - 表示一首歌曲/音频
 */
@Entity(tableName = "music")
data class Music(
    @PrimaryKey
    val id: String,
    val title: String,
    val artist: String = "",
    val coverUrl: String? = null,
    val duration: Long = 0L,
    val url: String? = null,
    val bvid: String? = null,
    val page: Int = 1,
    val isLocal: Boolean = false,
    val localPath: String? = null,
    val addedAt: Long = System.currentTimeMillis()
)

/**
 * 歌单实体
 */
@Entity(tableName = "playlist")
data class Playlist(
    @PrimaryKey
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val coverUrl: String? = null,
    val coverColor: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val songCount: Int = 0
)

/**
 * 歌单-歌曲关联
 */
@Entity(
    tableName = "playlist_song",
    primaryKeys = ["playlistId", "songId"]
)
data class PlaylistSong(
    val playlistId: String,
    val songId: String,
    val order: Int = 0,
    val addedAt: Long = System.currentTimeMillis()
)

/**
 * 搜索历史
 */
@Entity(tableName = "search_history")
data class SearchHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val query: String,
    val searchedAt: Long = System.currentTimeMillis()
)

/**
 * 下载任务状态
 */
enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    ERROR
}

/**
 * 下载任务
 */
@Entity(tableName = "download_task")
data class DownloadTask(
    @PrimaryKey
    val id: String,
    val musicId: String,
    val title: String,
    val artist: String,
    val coverUrl: String?,
    val url: String,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val localFilePath: String? = null
)

/**
 * B站视频信息（API响应模型）
 */
data class BilibiliVideo(
    val bvid: String,
    val title: String,
    val author: String,
    val coverUrl: String,
    val duration: Long,
    val playUrl: String? = null
)

/**
 * B站收藏夹
 */
data class BilibiliFavoriteFolder(
    val id: Long,
    val title: String,
    val coverUrl: String?,
    val songCount: Int
)

/**
 * 播放模式
 */
enum class PlayMode {
    LOOP,
    SHUFFLE,
    SINGLE
}

/**
 * 主题色模式
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

/**
 * 进度条样式
 */
enum class ProgressBarStyle(val displayName: String) {
    ROUNDED("圆角"),
    DOT("圆点")
}

/**
 * 定时关闭选项
 */
data class SleepTimer(
    val remainingMillis: Long,
    val startTime: Long = System.currentTimeMillis()
)
