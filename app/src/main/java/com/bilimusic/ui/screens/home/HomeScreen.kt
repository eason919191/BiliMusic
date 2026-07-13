package com.bilimusic.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bilimusic.data.api.netease.NeteaseSong
import com.bilimusic.data.api.netease.NeteasePlaylist
import com.bilimusic.ui.components.BiliAsyncImage

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onPlaySong: (NeteaseSong) -> Unit = {},
    onPlayPlaylist: (Long) -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("主页", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                Row {
                    IconButton(onClick = { viewModel.loadRecommendations() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (!uiState.isNeteaseLoggedIn) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.MusicNote, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        Spacer(Modifier.height(12.dp))
                        Text("请先登录网易云音乐", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("登录后即可使用推荐功能", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                }
                return@item
            }
        }

        // 精选推荐 cards
        item {
            SectionHeader(
                icon = Icons.Outlined.Star,
                title = "精选推荐"
            )
        }

        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 每日推荐
                item {
                    val coverUrl = uiState.dailyRecommendSongs.firstOrNull()?.coverUrl
                    RecommendCardWithCover(
                        title = "每日推荐",
                        subtitle = "符合你口味的新鲜好歌",
                        coverUrl = coverUrl,
                        gradient = listOf(Color(0xFF667eea), Color(0xFF764ba2)),
                        onClick = { if (uiState.dailyRecommendSongs.isNotEmpty()) onPlaySong(uiState.dailyRecommendSongs.first()) }
                    )
                }
                // 心动模式
                item {
                    val coverUrl = uiState.personalizedSongs.firstOrNull()?.coverUrl
                    RecommendCardWithCover(
                        title = "心动模式",
                        subtitle = "红心歌曲和相似推荐",
                        coverUrl = coverUrl,
                        gradient = listOf(Color(0xFFf093fb), Color(0xFFf5576c)),
                        onClick = { if (uiState.personalizedSongs.isNotEmpty()) onPlaySong(uiState.personalizedSongs.first()) }
                    )
                }
                // 私人FM
                item {
                    val coverUrl = uiState.personalizedSongs.getOrNull(uiState.personalizedSongs.size / 2)?.coverUrl
                    RecommendCardWithCover(
                        title = "私人FM",
                        subtitle = "根据你的口味推荐",
                        coverUrl = coverUrl,
                        gradient = listOf(Color(0xFF4facfe), Color(0xFF00f2fe)),
                        onClick = { if (uiState.personalizedSongs.isNotEmpty()) onPlaySong(uiState.personalizedSongs.last()) }
                    )
                }
            }
        }

        // 猜你喜欢的好歌
        if (uiState.personalizedSongs.isNotEmpty()) {
            item {
                SectionHeader(
                    icon = Icons.Outlined.Bolt,
                    title = "猜你喜欢的好歌"
                )
            }

            val displaySongs = uiState.personalizedSongs.take(6)
            items(displaySongs) { song ->
                SongItem(song = song, onClick = { onPlaySong(song) })
            }
        }

        // 热歌榜
        if (uiState.hotSongs.isNotEmpty()) {
            item {
                SectionHeader(
                    icon = Icons.Outlined.LocalFireDepartment,
                    title = "热歌榜"
                )
            }

            val displaySongs = uiState.hotSongs.take(10)
            itemsIndexed(displaySongs) { index, song ->
                SongItemWithIndex(index = index + 1, song = song, onClick = { onPlaySong(song) })
            }
        }

        // 每日推荐歌曲
        if (uiState.dailyRecommendSongs.isNotEmpty()) {
            item {
                SectionHeader(
                    icon = Icons.Outlined.WbSunny,
                    title = "每日推荐"
                )
            }

            val displaySongs = uiState.dailyRecommendSongs.take(6)
            items(displaySongs) { song ->
                SongItem(song = song, onClick = { onPlaySong(song) })
            }
        }

        // 私人雷达
        if (uiState.radarSongs.isNotEmpty()) {
            item {
                SectionHeader(
                    icon = Icons.Outlined.Radar,
                    title = "私人雷达"
                )
            }

            val displaySongs = uiState.radarSongs.take(10)
            itemsIndexed(displaySongs) { index, song ->
                SongItemWithIndex(index = index + 1, song = song, onClick = { onPlaySong(song) })
            }
        }

        // 雷达歌单
        if (uiState.personalizedPlaylists.isNotEmpty()) {
            item {
                SectionHeader(
                    icon = Icons.Outlined.QueueMusic,
                    title = "推荐歌单"
                )
            }

            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.personalizedPlaylists) { playlist ->
                        PlaylistCard(playlist = playlist, onClick = { onPlayPlaylist(playlist.id) })
                    }
                }
            }
        }

        // Loading
        if (uiState.isLoading) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(start = 8.dp),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun RecommendCardWithCover(
    title: String,
    subtitle: String,
    coverUrl: String?,
    gradient: List<Color>,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(160.dp)
            .height(200.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!coverUrl.isNullOrBlank()) {
                BiliAsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.1f), Color.Black.copy(alpha = 0.7f))
                        ))
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(gradient))
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White.copy(alpha = 0.25f)
                ) {
                    Text(
                        title,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Text(
                    subtitle,
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SongItem(song: NeteaseSong, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!song.coverUrl.isNullOrBlank()) {
            BiliAsyncImage(
                model = song.coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.MusicNote, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                song.artists.joinToString(" / ") { it.name },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.PlayArrow, "播放", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SongItemWithIndex(index: Int, song: NeteaseSong, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = index.toString(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(28.dp),
            maxLines = 1,
            overflow = TextOverflow.Clip
        )

        if (!song.coverUrl.isNullOrBlank()) {
            BiliAsyncImage(
                model = song.coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(10.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = song.artists.joinToString(" / ") { it.name },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlaylistCard(playlist: NeteasePlaylist, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column {
            if (!playlist.coverUrl.isNullOrBlank()) {
                BiliAsyncImage(
                    model = playlist.coverUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.QueueMusic, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    playlist.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${playlist.songCount} 首",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
