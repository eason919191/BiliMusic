package com.bili.music.ui.screens.downloads

import android.content.Context
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bili.music.data.model.*
import com.bili.music.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.Request
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

data class DownloadsUiState(
    val downloadTasks: List<DownloadTask> = emptyList(),
    val activeCount: Int = 0,
    val isProcessing: Boolean = false,
    val error: String? = null
)

data class DownloadProgress(
    val taskId: String,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val speedBytesPerSec: Double = 0.0,
    val etaSeconds: Long = 0
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val repository: MusicRepository,
    private val musicPlayer: com.bili.music.player.MusicPlayer,
    private val preferences: com.bili.music.data.preferences.AppPreferences,
    private val client: OkHttpClient,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, DownloadProgress>> = _downloadProgress.asStateFlow()

    private var downloadPath: String = ""

    private val activeDownloads = mutableMapOf<String, Job>()
    private val downloadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        viewModelScope.launch {
            repository.getAllDownloadTasks().collect { tasks ->
                _uiState.update { it.copy(downloadTasks = tasks) }
            }
        }
        viewModelScope.launch {
            repository.getActiveDownloadCount().collect { count ->
                _uiState.update { it.copy(activeCount = count) }
            }
        }
        viewModelScope.launch {
            preferences.downloadPath.collect { path ->
                downloadPath = path
            }
        }
        // Auto-start pending downloads (initial + new ones)
        viewModelScope.launch {
            delay(500)
            startPendingDownloads()
        }
        viewModelScope.launch {
            repository.getAllDownloadTasks().collect { tasks ->
                // 检测新添加的PENDING任务并自动开始
                tasks.filter { it.status == DownloadStatus.PENDING }
                    .forEach { startDownload(it) }
            }
        }
    }

    private suspend fun startPendingDownloads() {
        val tasks = _uiState.value.downloadTasks
        tasks.filter { it.status == DownloadStatus.PENDING || it.status == DownloadStatus.ERROR }
            .forEach { startDownload(it) }
    }

    fun playDownloadedTask(task: DownloadTask) {
        if (task.status != DownloadStatus.COMPLETED) return
        viewModelScope.launch {
            try {
                val filePath = task.localFilePath
                if (filePath != null && File(filePath).exists()) {
                    val music = Music(
                        id = task.musicId,
                        title = task.title,
                        artist = task.artist,
                        coverUrl = task.coverUrl,
                        url = filePath,
                        isLocal = true,
                        localPath = filePath
                    )
                    musicPlayer.playSong(music, listOf(music))
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "播放失败: ${e.localizedMessage}") }
            }
        }
    }

    fun startDownload(task: DownloadTask) {
        if (activeDownloads.containsKey(task.id)) return

        val job = downloadScope.launch {
            try {
                _uiState.update { it.copy(isProcessing = true) }
                repository.updateDownloadTaskStatus(task.id, DownloadStatus.DOWNLOADING)

                var currentTask = task
                if (task.totalBytes == 0L) {
                    val headRequest = Request.Builder().url(task.url).head().build()
                    val headResponse = client.newCall(headRequest).execute()
                    val contentLength = headResponse.body?.contentLength() ?: 0L
                    if (contentLength > 0) {
                        currentTask = task.copy(totalBytes = contentLength)
                        repository.updateDownloadTask(currentTask)
                    }
                    headResponse.close()
                }

                // 强制获取纯音频DASH URL（fnval=80，仅下载音频流）
                var downloadUrl = task.url
                if (task.musicId.startsWith("BV") || task.musicId.length >= 10) {
                    try {
                        val bvid = task.musicId
                        val detail = com.bili.music.data.api.BilibiliApiClient.getVideoDetail(bvid)
                        if (detail != null && detail.cid > 0) {
                            val audioOnly = com.bili.music.data.api.BilibiliApiClient.getDownloadAudioUrl(bvid, detail.cid)
                            if (audioOnly != null) {
                                downloadUrl = audioOnly
                                Log.d("DownloadVM", "Got DASH audio URL for download")
                            }
                        }
                    } catch (_: Exception) {}
                }

                // Download directory
                val privateMusicDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "BiliMusic").apply { mkdirs() }
                val downloadDir = if (downloadPath.isNotBlank()) {
                    File(downloadPath).apply { mkdirs() }
                } else {
                    privateMusicDir
                }

                val safeName = task.title.replace(Regex("[/\\\\:*?\"<>|]"), "_").take(100)
                val file = File(downloadDir, "${safeName}.m4a")

                Log.d("DownloadVM", "Download URL: ${downloadUrl.take(80)}...")
                val request = Request.Builder().url(downloadUrl).build()
                val response = client.newCall(request).execute()
                val body = response.body ?: throw Exception("下载响应为空")

                val totalBytes = body.contentLength().coerceAtLeast(1)
                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(file)

                val buffer = ByteArray(65536) // 64KB 缓冲区，减少系统调用
                var downloaded = 0L
                var lastUpdateTime = System.currentTimeMillis()
                var lastDownloaded = 0L
                var bytesSinceLastUpdate = 0L

                inputStream.use { input ->
                    outputStream.use { output ->
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            // Check if paused
                            val currentTaskState = repository.getDownloadTaskById(task.id) ?: break
                            if (currentTaskState.status == DownloadStatus.PAUSED) break
                            if (!isActive) break

                            output.write(buffer, 0, bytesRead)
                            downloaded += bytesRead
                            bytesSinceLastUpdate += bytesRead

                            val now = System.currentTimeMillis()
                            val elapsed = now - lastUpdateTime

                            // Update progress every 500ms
                            if (elapsed >= 500) {
                                val speed = if (elapsed > 0) {
                                    bytesSinceLastUpdate.toDouble() / elapsed * 1000
                                } else 0.0

                                val remaining = if (speed > 0) {
                                    ((totalBytes - downloaded) / speed).toLong()
                                } else 0L

                                _downloadProgress.update {
                                    it + (task.id to DownloadProgress(
                                        taskId = task.id,
                                        downloadedBytes = downloaded,
                                        totalBytes = totalBytes,
                                        speedBytesPerSec = speed,
                                        etaSeconds = remaining
                                    ))
                                }

                                // Update database every 1 second
                                if (now - lastUpdateTime >= 1000) {
                                    repository.updateDownloadTask(
                                        currentTask.copy(
                                            downloadedBytes = downloaded,
                                            totalBytes = totalBytes,
                                            status = DownloadStatus.DOWNLOADING
                                        )
                                    )
                                    lastUpdateTime = now
                                    bytesSinceLastUpdate = 0
                                }
                            }
                        }
                    }
                }

                response.close()

                if (downloaded >= totalBytes) {
                    // Download complete
                    // Scan to MediaStore so the file appears in file manager
                    try {
                        val values = android.content.ContentValues().apply {
                            put(android.provider.MediaStore.Audio.Media.IS_MUSIC, "1")
                            put(android.provider.MediaStore.Audio.Media.TITLE, task.title)
                            put(android.provider.MediaStore.Audio.Media.ARTIST, task.artist)
                            put(android.provider.MediaStore.Audio.Media.DATA, file.absolutePath)
                        }
                        context.contentResolver.insert(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                    } catch (_: Exception) {}
                    // Also broadcast for older devices
                    try {
                        val intent = android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                        intent.data = android.net.Uri.fromFile(file)
                        context.sendBroadcast(intent)
                    } catch (_: Exception) {}

                    repository.updateDownloadTask(
                        currentTask.copy(
                            downloadedBytes = downloaded,
                            totalBytes = totalBytes,
                            status = DownloadStatus.COMPLETED,
                            completedAt = System.currentTimeMillis(),
                            localFilePath = file.absolutePath
                        )
                    )
                    _downloadProgress.update { it + (task.id to DownloadProgress(
                        taskId = task.id,
                        downloadedBytes = totalBytes,
                        totalBytes = totalBytes,
                        speedBytesPerSec = 0.0
                    )) }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                repository.updateDownloadTaskStatus(task.id, DownloadStatus.ERROR)
                repository.updateDownloadTask(
                    task.copy(
                        status = DownloadStatus.ERROR,
                        errorMessage = e.message
                    )
                )
            } finally {
                activeDownloads.remove(task.id)
                _uiState.update { it.copy(isProcessing = false) }
            }
        }

        activeDownloads[task.id] = job
    }

    fun pauseDownload(taskId: String) {
        viewModelScope.launch {
            activeDownloads[taskId]?.cancel()
            activeDownloads.remove(taskId)
            repository.updateDownloadTaskStatus(taskId, DownloadStatus.PAUSED)
        }
    }

    fun resumeDownload(taskId: String) {
        viewModelScope.launch {
            val task = repository.getDownloadTaskById(taskId) ?: return@launch
            startDownload(task)
        }
    }

    fun deleteDownload(taskId: String) {
        viewModelScope.launch {
            activeDownloads[taskId]?.cancel()
            activeDownloads.remove(taskId)
            repository.deleteDownloadTaskById(taskId)
            _downloadProgress.update { it - taskId }
        }
    }

    fun startAll() {
        viewModelScope.launch {
            _uiState.value.downloadTasks
                .filter { it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.PENDING || it.status == DownloadStatus.ERROR }
                .forEach { startDownload(it) }
        }
    }

    fun pauseAll() {
        activeDownloads.keys.toList().forEach { taskId ->
            activeDownloads[taskId]?.cancel()
            activeDownloads.remove(taskId)
        }
        viewModelScope.launch {
            _uiState.value.downloadTasks
                .filter { it.status == DownloadStatus.DOWNLOADING }
                .forEach { repository.updateDownloadTaskStatus(it.id, DownloadStatus.PAUSED) }
        }
    }

    fun deleteAll() {
        activeDownloads.keys.toList().forEach { taskId ->
            activeDownloads[taskId]?.cancel()
            activeDownloads.remove(taskId)
        }
        viewModelScope.launch {
            repository.clearAllDownloadTasks()
            _downloadProgress.update { emptyMap() }
        }
    }

    fun clearCompleted() {
        viewModelScope.launch {
            _uiState.value.downloadTasks
                .filter { it.status == DownloadStatus.COMPLETED }
                .forEach { repository.deleteDownloadTask(it) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        downloadScope.cancel()
    }
}
