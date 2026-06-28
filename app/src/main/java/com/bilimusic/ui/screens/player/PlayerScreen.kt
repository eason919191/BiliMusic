package com.bilimusic.ui.screens.player

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bilimusic.ui.components.BiliAsyncImage
import coil.compose.rememberAsyncImagePainter
import com.bilimusic.ui.components.PixelProgressBar
import com.bilimusic.data.model.PlayMode
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel = hiltViewModel(),
    onMinimize: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val playlistViewModel: com.bilimusic.ui.screens.playlist.PlaylistViewModel = hiltViewModel()
    val playlistUiState by playlistViewModel.uiState.collectAsState()
    var showPlaylistDialog by remember { mutableStateOf(false) }

    // Playlist selection dialog
    if (showPlaylistDialog && uiState.currentSong != null) {
        val song = uiState.currentSong!!
        AlertDialog(
            onDismissRequest = { showPlaylistDialog = false },
            title = { Text("添加到歌单") },
            text = {
                if (playlistUiState.playlists.isEmpty()) {
                    Text("还没有歌单，请先去歌单页面创建")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(playlistUiState.playlists) { playlist ->
                            ListItem(
                                headlineContent = { Text(playlist.name) },
                                supportingContent = { Text("${playlist.songCount} 首") },
                                modifier = Modifier.clickable {
                                    viewModel.addCurrentSongToPlaylist(song, playlist.id)
                                    showPlaylistDialog = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlaylistDialog = false }) { Text("取消") }
            }
        )
    }

    // Animated visibility
    AnimatedVisibility(
        visible = uiState.currentSong != null && !uiState.isMinimized,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = Modifier.fillMaxSize()
    ) {
        if (uiState.currentSong != null) {
            PlayerContent(
                uiState = uiState,
                onPlayPause = { viewModel.togglePlayPause() },
                onNext = { viewModel.playNext() },
                onPrevious = { viewModel.playPrevious() },
                onSeek = { viewModel.seekTo(it) },
                onCyclePlayMode = { viewModel.cyclePlayMode() },
                onMinimize = {
                    viewModel.minimize()
                    onMinimize()
                },
                onPlaySongAt = { viewModel.playSongAt(it) },
                onRemoveFromPlaylist = { viewModel.removeFromPlaylist(it) },
                onAddToPlaylist = { showPlaylistDialog = true },
                onToggleLyrics = { viewModel.toggleLyrics() }
            )
        }
    }
}

@Composable
private fun PlayerContent(
    uiState: PlayerUiState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onCyclePlayMode: () -> Unit,
    onMinimize: () -> Unit,
    onPlaySongAt: (Int) -> Unit,
    onRemoveFromPlaylist: (Int) -> Unit,
    onAddToPlaylist: () -> Unit = {},
    onToggleLyrics: () -> Unit = {}
) {
    val song = uiState.currentSong ?: return
    var showPlaylist by remember { mutableStateOf(false) }
    val progress = if (uiState.duration > 0) {
        (uiState.currentPosition.toFloat() / uiState.duration).coerceIn(0f, 1f)
    } else 0f

    // Background fills entire screen including behind system bars
    val bgModifier = Modifier.fillMaxSize()
        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f))
    Box(modifier = bgModifier) {
        // Background: blurred cover on the entire screen
        if (!uiState.playerBgPureColor && song.coverUrl != null) {
            coil.compose.AsyncImage(
                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(song.coverUrl)
                    .crossfade(true)
                    .addHeader("Referer", "https://www.bilibili.com/")
                    .addHeader("User-Agent", "Mozilla/5.0")
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(uiState.blurDegree.dp)
                    .alpha(0.5f),
                contentScale = ContentScale.Crop
            )
        }

        // Dark scrim
        Box(modifier = Modifier.fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f)))

        // Main content - only content area is padded for status bar
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "正在播放",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Row {
                    // 歌词按钮
                    IconButton(onClick = { onToggleLyrics() }) {
                        Icon(
                            if (uiState.showLyrics) Icons.Filled.Article else Icons.Outlined.Article,
                            contentDescription = "歌词",
                            tint = when {
                                uiState.showLyrics -> MaterialTheme.colorScheme.primary
                                uiState.lyrics.isNotEmpty() -> MaterialTheme.colorScheme.onSurface
                                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            }
                        )
                    }
                    IconButton(onClick = onAddToPlaylist) {
                        Icon(Icons.Filled.PlaylistAdd, contentDescription = "添加到歌单", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { showPlaylist = !showPlaylist }) {
                        Icon(Icons.Outlined.QueueMusic, contentDescription = "播放列表", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = onMinimize) {
                        Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "最小化", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Cover art / Lyrics
            Box(modifier = Modifier.weight(0.8f), contentAlignment = Alignment.Center) {
                if (uiState.showLyrics) {
                        if (uiState.lyrics.isNotEmpty()) {
                            LyricsView(lyrics = uiState.lyrics, currentIndex = uiState.currentLyricIndex, modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp))
                        } else {
                            Text(if (song.bvid != null) "暂无可用歌词\n（登录B站后可获取AI字幕）" else "当前歌曲无字幕", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    } else {
                Card(
                        modifier = Modifier.size(260.dp),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        if (song.coverUrl != null) {
                            BiliAsyncImage(
                                model = song.coverUrl,
                                contentDescription = "专辑封面",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.MusicNote, null, Modifier.size(80.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            }
                        }
                    }
                } // end else
            }

            // Song info
            Text(
                text = song.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Progress bar
            PixelProgressBar(
                progress = progress,
                onProgressChange = { onSeek((it * uiState.duration).toLong()) },
                onProgressChangeFinished = {},
                style = uiState.progressBarStyle,
                activeColor = MaterialTheme.colorScheme.primary,
                inactiveColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            )

            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDuration(uiState.currentPosition), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Text("-${formatDuration((uiState.duration - uiState.currentPosition).coerceAtLeast(0))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCyclePlayMode) {
                    Icon(
                        when (uiState.playMode) { PlayMode.LOOP -> Icons.Outlined.Loop; PlayMode.SHUFFLE -> Icons.Outlined.Shuffle; PlayMode.SINGLE -> Icons.Outlined.RepeatOne },
                        "播放模式",
                        tint = if (uiState.playMode != PlayMode.LOOP) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(28.dp)
                    )
                }
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Filled.SkipPrevious, "上一首", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(40.dp))
                }
                Box(
                    modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).clickable { onPlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (uiState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        if (uiState.isPlaying) "暂停" else "播放",
                        tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(32.dp)
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Filled.SkipNext, "下一首", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(40.dp))
                }
                IconButton(onClick = {}) {
                    Icon(Icons.Outlined.VolumeUp, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.size(28.dp))
                }
            }

            // Error message display
            if (uiState.playerError != null) {
                Text(
                    text = uiState.playerError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }

        // Playlist drawer overlay
        AnimatedVisibility(
            visible = showPlaylist,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.fillMaxSize()
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Playlist header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .statusBarsPadding(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "当前播放 (${uiState.playlist.size})",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(onClick = { showPlaylist = false }) {
                            Icon(Icons.Filled.Close, contentDescription = "关闭")
                        }
                    }

                    Divider()

                    // Playlist items
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        itemsIndexed(uiState.playlist) { index, songItem ->
                            val isCurrent = songItem.id == uiState.currentSong?.id
                            ListItem(
                                headlineContent = {
                                    Text(
                                        songItem.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (isCurrent) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        songItem.artist,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                leadingContent = {
                                    if (isCurrent) {
                                        Icon(
                                            Icons.Filled.PlayArrow,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        Text(
                                            "${index + 1}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                trailingContent = {
                                    IconButton(onClick = { onRemoveFromPlaylist(index) }) {
                                        Icon(
                                            Icons.Outlined.Close,
                                            contentDescription = "移除",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                modifier = Modifier.clickable { onPlaySongAt(index) }
                            )
                            if (isCurrent) {
                                Divider(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsView(lyrics: List<com.bilimusic.data.api.LyricLine>, currentIndex: Int, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0 && currentIndex < lyrics.size)
            listState.animateScrollToItem((currentIndex - 3).coerceAtLeast(0))
    }
    LazyColumn(state = listState, modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, contentPadding = PaddingValues(vertical = 100.dp)) {
        itemsIndexed(lyrics) { index, line ->
            val isCurrent = index == currentIndex
            Text(
                text = line.text, style = MaterialTheme.typography.titleMedium,
                color = when {
                    isCurrent -> MaterialTheme.colorScheme.onSurface
                    kotlin.math.abs(index - currentIndex) <= 2 -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = if (isCurrent) 6.dp else 3.dp)
            )
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
    else String.format("%02d:%02d", m, s)
}
