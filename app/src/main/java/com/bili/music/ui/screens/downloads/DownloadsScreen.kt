package com.bili.music.ui.screens.downloads

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bili.music.R
import com.bili.music.ui.components.BiliAsyncImage
import com.bili.music.data.model.DownloadStatus
import com.bili.music.data.model.DownloadTask

@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val progressMap by viewModel.downloadProgress.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar with total count and actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    stringResource(R.string.downloads_title),
                    style = MaterialTheme.typography.titleLarge
                )
                if (uiState.activeCount > 0) {
                    Text(
                        stringResource(R.string.downloads_active_count, uiState.activeCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (uiState.downloadTasks.isNotEmpty()) {
                Row {
                    SmallFloatingActionButton(
                        onClick = { viewModel.startAll() },
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.downloads_start_all), modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    SmallFloatingActionButton(
                        onClick = { viewModel.pauseAll() },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Icon(Icons.Filled.Pause, contentDescription = stringResource(R.string.downloads_pause_all), modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    SmallFloatingActionButton(
                        onClick = { viewModel.deleteAll() },
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = stringResource(R.string.downloads_delete_all), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        if (uiState.downloadTasks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.Download,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.downloads_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        stringResource(R.string.downloads_empty_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                // Group: Active downloads
                val activeTasks = uiState.downloadTasks.filter {
                    it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING
                }
                if (activeTasks.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.downloads_section_active),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    items(activeTasks, key = { it.id }) { task ->
                        DownloadTaskItem(
                            task = task,
                            progress = progressMap[task.id],
                            onPause = { viewModel.pauseDownload(task.id) },
                            onResume = { viewModel.resumeDownload(task.id) },
                            onDelete = { viewModel.deleteDownload(task.id) }
                        )
                    }
                }

                // Group: Paused
                val pausedTasks = uiState.downloadTasks.filter { it.status == DownloadStatus.PAUSED }
                if (pausedTasks.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.downloads_section_paused),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(pausedTasks, key = { it.id }) { task ->
                        DownloadTaskItem(
                            task = task,
                            onPause = { viewModel.pauseDownload(task.id) },
                            onResume = { viewModel.resumeDownload(task.id) },
                            onDelete = { viewModel.deleteDownload(task.id) }
                        )
                    }
                }

                // Group: Completed
                val completedTasks = uiState.downloadTasks.filter { it.status == DownloadStatus.COMPLETED }
                if (completedTasks.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.downloads_section_completed),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = { viewModel.clearCompleted() }) {
                                Text(stringResource(R.string.downloads_clear_completed), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    items(completedTasks, key = { it.id }) { task ->
                        DownloadTaskItem(
                            task = task,
                            onPlay = { viewModel.playDownloadedTask(task) },
                            onDelete = { viewModel.deleteDownload(task.id) }
                        )
                    }
                }

                // Group: Error
                val errorTasks = uiState.downloadTasks.filter { it.status == DownloadStatus.ERROR }
                if (errorTasks.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.downloads_section_error),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    items(errorTasks, key = { it.id }) { task ->
                        DownloadTaskItem(
                            task = task,
                            onResume = { viewModel.resumeDownload(task.id) },
                            onDelete = { viewModel.deleteDownload(task.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadTaskItem(
    task: DownloadTask,
    progress: DownloadProgress? = null,
    onPause: () -> Unit = {},
    onResume: () -> Unit = {},
    onDelete: () -> Unit = {},
    onPlay: () -> Unit = {}
) {
    val progressVal = if (progress != null) {
        if (progress.totalBytes > 0) progress.downloadedBytes.toFloat() / progress.totalBytes
        else 0f
    } else if (task.totalBytes > 0) {
        task.downloadedBytes.toFloat() / task.totalBytes
    } else 0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cover
            if (task.coverUrl != null) {
                BiliAsyncImage(
                    model = task.coverUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))

                when (task.status) {
                    DownloadStatus.DOWNLOADING -> {
                        if (progress != null) {
                            // Progress bar
                            LinearProgressIndicator(
                                progress = progressVal,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${String.format("%.2f", progressVal * 100)}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = formatSpeed(progress.speedBytesPerSec),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${formatSize(progress.downloadedBytes)} / ${formatSize(progress.totalBytes)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (progress.etaSeconds > 0) {
                                    Text(
                                        text = stringResource(R.string.downloads_eta_remaining, formatTime(progress.etaSeconds)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                    DownloadStatus.PENDING -> {
                        Text(
                            stringResource(R.string.downloads_status_pending),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DownloadStatus.PAUSED -> {
                        val pct = if (task.totalBytes > 0) task.downloadedBytes * 100 / task.totalBytes else 0
                        Text(
                            stringResource(R.string.downloads_status_paused, pct),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DownloadStatus.COMPLETED -> {
                        Text(
                            stringResource(R.string.downloads_status_completed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    DownloadStatus.ERROR -> {
                        Text(
                            task.errorMessage ?: stringResource(R.string.downloads_section_error),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Action button
            when (task.status) {
                DownloadStatus.DOWNLOADING -> {
                    IconButton(onClick = onPause) {
                        Icon(Icons.Filled.Pause, contentDescription = stringResource(R.string.downloads_pause))
                    }
                }
                DownloadStatus.PAUSED, DownloadStatus.PENDING, DownloadStatus.ERROR -> {
                    IconButton(onClick = onResume) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.downloads_resume))
                    }
                }
                DownloadStatus.COMPLETED -> {
                    Row {
                        IconButton(onClick = onPlay) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.downloads_play), tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.downloads_delete))
                        }
                    }
                }
            }
        }
    }
}

private fun formatSpeed(bytesPerSec: Double): String {
    val mbps = bytesPerSec / 1024 / 1024
    return "${String.format("%.2f", mbps)} MB/s"
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
        bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024))
        bytes >= 1024 -> String.format("%.2f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}

private fun formatTime(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
    else String.format("%02d:%02d", m, s)
}
