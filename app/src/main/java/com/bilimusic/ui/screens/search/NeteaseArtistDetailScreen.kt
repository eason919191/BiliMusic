package com.bilimusic.ui.screens.search

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bilimusic.data.api.BilibiliApiClient
import com.bilimusic.data.api.netease.NeteaseApiClient
import com.bilimusic.data.api.netease.NeteaseSong
import com.bilimusic.data.api.netease.NeteaseSongParser
import com.bilimusic.ui.theme.bounceGentle
import com.bilimusic.data.database.AppDatabase
import com.bilimusic.data.model.DownloadTask
import com.bilimusic.data.model.DownloadStatus
import com.bilimusic.data.model.Music
import com.bilimusic.player.MusicPlayer
import com.bilimusic.ui.components.BiliAsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return ""
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    if (h > 0) return String.format("%d:%02d:%02d", h, m, s)
    return String.format("%02d:%02d", m, s)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NeteaseArtistDetailScreen(
    artistName: String,
    artistId: Long,
    artistCover: String?,
    musicPlayer: MusicPlayer?,
    onBack: () -> Unit
) {
    var songs by remember { mutableStateOf<List<NeteaseSong>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(artistId) {
        try {
            val resp = withContext(Dispatchers.IO) { NeteaseApiClient.getArtistSongs(artistId) }
            songs = withContext(Dispatchers.IO) { com.bilimusic.data.api.netease.parseArtistSongs(resp) }
        } catch (_: Exception) {}
        isLoading = false
    }

    fun playSong(song: NeteaseSong) {
        scope.launch {
            try {
                val resp = withContext(Dispatchers.IO) { NeteaseApiClient.getSongUrl(song.id) }
                val url = NeteaseSongParser.parseSongUrlRaw(resp)
                if (url != null && url != "__VIP_ONLY__") {
                    val music = Music(
                        id = "netease_${song.id}", title = song.name, artist = song.artistName,
                        coverUrl = song.coverUrl, duration = song.duration / 1000L, url = url,
                        source = "NETEASE", neteaseId = song.id
                    )
                    musicPlayer?.playSongList(listOf(music), 0)
                    return@launch
                }
                val keyword = "${song.name} ${song.artistName}"
                val biliResults = withContext(Dispatchers.IO) { BilibiliApiClient.searchVideos(keyword, 1, "totalrank") }
                val best = biliResults.firstOrNull() ?: return@launch
                val detail = withContext(Dispatchers.IO) { BilibiliApiClient.getVideoDetail(best.bvid) } ?: return@launch
                val streamUrl = withContext(Dispatchers.IO) { BilibiliApiClient.getAudioUrl(best.bvid, detail.cid) } ?: return@launch
                val fallback = Music(
                    id = best.bvid, title = song.name, artist = song.artistName,
                    coverUrl = song.coverUrl ?: best.coverUrl, duration = song.duration,
                    url = streamUrl, bvid = best.bvid, source = "NETEASE", neteaseId = song.id
                )
                musicPlayer?.playSongList(listOf(fallback), 0)
            } catch (_: Exception) {}
        }
    }

    fun downloadSong(song: NeteaseSong) {
        scope.launch {
            try {
                val resp = withContext(Dispatchers.IO) { NeteaseApiClient.getSongUrl(song.id) }
                val url = NeteaseSongParser.parseSongUrlRaw(resp)
                if (url != null && url != "__VIP_ONLY__") {
                    try {
                        val task = DownloadTask(
                            id = "netease_${song.id}_${System.currentTimeMillis()}",
                            musicId = "netease_${song.id}",
                            title = song.name,
                            artist = song.artistName,
                            coverUrl = song.coverUrl,
                            url = url,
                            status = DownloadStatus.PENDING
                        )
                        AppDatabase.getInstance(context).musicDao().insertDownloadTask(task)
                    } catch (_: Exception) {}
                    Toast.makeText(context, "已添加到下载列表", Toast.LENGTH_SHORT).show()
                } else Toast.makeText(context, "该歌曲无可用下载源", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {}
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(artistName) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (songs.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("该歌手暂无歌曲", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (artistCover != null) {
                                BiliAsyncImage(model = artistCover, contentDescription = "头像",
                                    modifier = Modifier.size(80.dp).clip(androidx.compose.foundation.shape.CircleShape),
                                    contentScale = ContentScale.Crop)
                            }
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(artistName, style = MaterialTheme.typography.headlineSmall)
                                Text("${songs.size} 首歌曲", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider()
                    }

                    itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
                        val alpha by animateFloatAsState(
                            targetValue = 1f,
                            animationSpec = bounceGentle(),
                            label = "fade"
                        )
                        var showMenu by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.graphicsLayer(alpha = alpha)) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).combinedClickable(onClick = { playSong(song) }),
                                colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (song.coverUrl != null) {
                                        BiliAsyncImage(model = song.coverUrl, contentDescription = "封面",
                                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                                    } else Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(Color.Transparent), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.MusicNote, null, Modifier.size(28.dp))
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(song.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Spacer(Modifier.height(4.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(song.artistName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                            Spacer(Modifier.width(4.dp))
                                            Text(formatDuration(song.duration / 1000), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    IconButton(onClick = { playSong(song) }) { Icon(Icons.Filled.PlayArrow, "播放", tint = MaterialTheme.colorScheme.primary) }
                                    IconButton(onClick = { downloadSong(song) }) { Icon(Icons.Filled.Download, "下载", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    Box {
                                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Filled.MoreVert, "更多", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                            DropdownMenuItem(text = { Text("分享") }, onClick = {
                                                showMenu = false
                                                try {
                                                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra(android.content.Intent.EXTRA_TEXT, "【${song.name}】- ${song.artistName}")
                                                    }.let { android.content.Intent.createChooser(it, "分享") })
                                                } catch (_: Exception) {}
                                            }, leadingIcon = { Icon(Icons.Filled.Share, null) })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
