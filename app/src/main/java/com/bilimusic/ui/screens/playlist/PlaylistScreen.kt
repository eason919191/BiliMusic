package com.bilimusic.ui.screens.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.hilt.navigation.compose.hiltViewModel
import com.bilimusic.ui.components.BiliAsyncImage
import com.bilimusic.data.model.Music
import com.bilimusic.data.model.Playlist

@Composable
fun PlaylistScreen(
    viewModel: PlaylistViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val selectedPlaylistId = uiState.selectedPlaylistId

    // Back from detail to playlist list
    BackHandler(enabled = uiState.isShowingDetail) {
        viewModel.hidePlaylistDetail()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Main list
        if (!uiState.isShowingDetail) {
            PlaylistListScreen(
                playlists = uiState.playlists,
                onCreatePlaylist = { viewModel.showCreatePlaylist() },
                onPlaylistClick = { viewModel.showPlaylistDetail(it.id) },
                onDeletePlaylist = { viewModel.deletePlaylist(it) }
            )
        }

        // Detail content
        if (uiState.isShowingDetail && selectedPlaylistId != null) {
            val pid = selectedPlaylistId ?: return@Box
            val playlist = uiState.playlists.find { it.id == pid }
            PlaylistDetailScreen(
                playlistId = pid,
                playlistName = playlist?.name ?: "",
                songs = uiState.currentPlaylistSongs,
                isSelecting = uiState.isSelecting,
                selectedSongIds = uiState.selectedSongIds,
                onBack = { viewModel.hidePlaylistDetail() },
                onPlayAll = { viewModel.playPlaylist(pid) },
                onPlaySong = { index -> viewModel.playPlaylist(pid, index) },
                onRemoveSong = { songId -> viewModel.removeSongFromPlaylist(pid, songId) },
                onDeletePlaylist = { viewModel.deletePlaylist(pid); viewModel.hidePlaylistDetail() },
                onToggleSelect = { viewModel.toggleSongSelection(it) },
                onLongPress = { viewModel.enterSelectionMode(it) },
                onSelectAll = { viewModel.selectAllSongs() },
                onExitSelect = { viewModel.exitSelectionMode() },
                onCopyToPlaylist = { viewModel.showCopyDialog(true) },
                onMoveToPlaylist = { viewModel.showCopyDialog(false) },
                onMoveUp = { viewModel.moveSong(it, it - 1) },
                onMoveDown = { viewModel.moveSong(it, it + 1) },
                onDownloadSong = { songId -> viewModel.downloadSong(songId) }
            )
        }
    }

    // Create playlist dialog
    if (uiState.isCreatingPlaylist) {
        AlertDialog(
            onDismissRequest = { viewModel.hideCreatePlaylist() },
            title = { Text("创建歌单") },
            text = {
                Column {
                    OutlinedTextField(
                        value = uiState.newPlaylistName,
                        onValueChange = { viewModel.setNewPlaylistName(it) },
                        label = { Text("歌单名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uiState.newPlaylistDescription,
                        onValueChange = { viewModel.setNewPlaylistDescription(it) },
                        label = { Text("歌单描述（可选）") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.createPlaylist() },
                    enabled = uiState.newPlaylistName.isNotBlank()
                ) {
                    Text("创建")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideCreatePlaylist() }) {
                    Text("取消")
                }
            }
        )
    }

    // Target playlist picker dialog
    if (uiState.showTargetPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideTargetDialog() },
            title = { Text(if (uiState.isCopyMode) "复制到歌单" else "移动到歌单") },
            text = {
                val available = uiState.playlists.filter { it.id != uiState.selectedPlaylistId }
                if (available.isEmpty()) {
                    Text("没有可用的歌单")
                } else {
                    LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        items(available) { target ->
                            ListItem(
                                headlineContent = { Text(target.name) },
                                supportingContent = { Text("${target.songCount} 首") },
                                modifier = Modifier.clickable { viewModel.batchCopyOrMoveToPlaylist(target.id) }
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.hideTargetDialog() }) { Text("取消") } }
        )
    }
}

@Composable
private fun PlaylistListScreen(
    playlists: List<Playlist>,
    onCreatePlaylist: () -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onPlaylistCardCover: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "我的歌单",
                style = MaterialTheme.typography.titleLarge
            )
            FilledTonalIconButton(onClick = onCreatePlaylist) {
                Icon(Icons.Filled.Add, contentDescription = "创建歌单")
            }
        }

        if (playlists.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.QueueMusic,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "还没有歌单",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        "点击右上角按钮创建",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    PlaylistCard(
                        playlist = playlist,
                        onClick = {
                            onPlaylistClick(playlist)
                        },
                        onCoverPositioned = { rect -> onPlaylistCardCover?.invoke(rect) },
                        onDelete = { onDeletePlaylist(playlist.id) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onCoverPositioned: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box {
            Column {
                // Cover
                if (playlist.coverUrl != null) {
                    BiliAsyncImage(
                        model = playlist.coverUrl,
                        contentDescription = "歌单封面",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .then(if (onCoverPositioned != null) Modifier.onGloballyPositioned { coords ->
                                val pos = coords.positionInWindow()
                                val size = coords.size
                                onCoverPositioned?.invoke(Rect(pos.x, pos.y, pos.x + size.width, pos.y + size.height))
                            } else Modifier)
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }

                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${playlist.songCount} 首",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Context menu
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("删除歌单") },
                    onClick = {
                        showMenu = false
                        onDelete()
                    },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun PlaylistDetailScreen(
    playlistId: String,
    playlistName: String,
    songs: List<Music>,
    isSelecting: Boolean = false,
    selectedSongIds: Set<String> = emptySet(),
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onPlaySong: (Int) -> Unit,
    onRemoveSong: (String) -> Unit,
    onDeletePlaylist: () -> Unit,
    onToggleSelect: (String) -> Unit = {},
    onLongPress: (String) -> Unit = {},
    onSelectAll: () -> Unit = {},
    onExitSelect: () -> Unit = {},
    onCopyToPlaylist: () -> Unit = {},
    onMoveToPlaylist: () -> Unit = {},
    onMoveUp: (Int) -> Unit = {},
    onMoveDown: (Int) -> Unit = {},
    onDownloadSong: (String) -> Unit = {}
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    // 获取第一首歌曲封面作为歌单封面
    val playlistCover = songs.firstOrNull()?.coverUrl

    Column(modifier = Modifier.fillMaxSize()) {
        if (isSelecting) {
            Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)) {
                Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("已选 ${selectedSongIds.size} 项", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                    TextButton(onClick = onSelectAll) { Text("全选") }
                    IconButton(onClick = onCopyToPlaylist) { Icon(Icons.Filled.ContentCopy, "复制到") }
                    IconButton(onClick = onMoveToPlaylist) { Icon(Icons.Filled.ContentPasteGo, "移动到") }
                    IconButton(onClick = onExitSelect) { Icon(Icons.Filled.Close, "取消") }
                }
            }
        }

        if (!isSelecting) {
            Box(modifier = Modifier.fillMaxWidth().height(250.dp).offset(y = (-50).dp)) {
                if (playlistCover != null) {
                    BiliAsyncImage(model = playlistCover, null, Modifier.fillMaxSize().blur(30.dp).alpha(0.7f), ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f)))
                } else Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)))
                Row(Modifier.fillMaxSize().padding(16.dp).statusBarsPadding(), verticalAlignment = Alignment.Bottom) {
                    Card(Modifier.size(100.dp), RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(6.dp)) {
                        if (playlistCover != null) BiliAsyncImage(playlistCover, "封面", Modifier.fillMaxSize(), ContentScale.Crop)
                        else Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Icon(Icons.Filled.QueueMusic, null, Modifier.size(40.dp)) }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(playlistName, style = MaterialTheme.typography.titleLarge, color = androidx.compose.ui.graphics.Color.White)
                        val totalMin = songs.sumOf { it.duration } / 60000
                        Text("${songs.size}首·${totalMin}分钟", style = MaterialTheme.typography.bodyMedium, color = androidx.compose.ui.graphics.Color.White.copy(0.8f))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(4.dp).statusBarsPadding(), horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "返回", tint = androidx.compose.ui.graphics.Color.White) }
                    Row {
                        IconButton(onClick = onPlayAll) { Icon(Icons.Filled.PlayArrow, "播放全部", tint = androidx.compose.ui.graphics.Color.White) }
                        IconButton(onClick = { showDeleteConfirm = true }) { Icon(Icons.Filled.Delete, "删除", tint = androidx.compose.ui.graphics.Color.White) }
                    }
                }
            }
        } else {
            // 选择模式下的简单顶栏
            TopAppBar(
                title = { Text("") },
                navigationIcon = { IconButton(onClick = onExitSelect) { Icon(Icons.Filled.Close, "返回") } }
            )
        }

        if (songs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("歌单为空", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
                    val isChecked = selectedSongIds.contains(song.id)
                    ListItem(
                        headlineContent = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = {
                            if (isSelecting) Checkbox(checked = isChecked, onCheckedChange = { onToggleSelect(song.id) })
                            else if (song.coverUrl != null) BiliAsyncImage(model = song.coverUrl, null, Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)), ContentScale.Crop)
                            else Icon(Icons.Filled.MusicNote, null)
                        },
                        trailingContent = {
                            if (isSelecting) {
                                Row {
                                    if (index > 0) IconButton(onClick = { onMoveUp(index) }) { Icon(Icons.Filled.KeyboardArrowUp, "上移") }
                                    if (index < songs.lastIndex) IconButton(onClick = { onMoveDown(index) }) { Icon(Icons.Filled.KeyboardArrowDown, "下移") }
                                }
                            } else {
                                var showMenu by remember { mutableStateOf(false) }
                                Row {
                                    IconButton(onClick = { onPlaySong(index) }) { Icon(Icons.Filled.PlayArrow, "播放", Modifier.size(18.dp)) }
                                    IconButton(onClick = { onDownloadSong(song.id) }) { Icon(Icons.Filled.Download, "下载", Modifier.size(18.dp)) }
                                    Box {
                                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Filled.MoreVert, "更多", Modifier.size(18.dp)) }
                                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                            DropdownMenuItem(text = { Text("添加到歌单") }, onClick = { showMenu = false; onCopyToPlaylist() }, leadingIcon = { Icon(Icons.Filled.PlaylistAdd, null) })
                                            DropdownMenuItem(text = { Text("在B站App中打开") }, onClick = {
                                                showMenu = false
                                                try {
                                                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                                        data = android.net.Uri.parse("bilibili://video/${song.bvid}")
                                                        setPackage("tv.danmaku.bili")
                                                    })
                                                } catch (_: Exception) {}
                                            }, leadingIcon = { Icon(Icons.Filled.OpenInBrowser, null) })
                                            DropdownMenuItem(text = { Text("分享") }, onClick = {
                                                showMenu = false
                                                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(android.content.Intent.EXTRA_TEXT, "【${song.title}】- ${song.artist}\nhttps://www.bilibili.com/video/${song.bvid}")
                                                }
                                                context.startActivity(android.content.Intent.createChooser(intent, "分享"))
                                            }, leadingIcon = { Icon(Icons.Filled.Share, null) })
                                            DropdownMenuItem(text = { Text("移除") }, onClick = { showMenu = false; onRemoveSong(song.id) }, leadingIcon = { Icon(Icons.Filled.Delete, null) })
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.clickable(
                            onClick = { if (isSelecting) onToggleSelect(song.id) else onPlaySong(index) }
                        )
                    )
                }
            }
        }
    }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除歌单") },
            text = { Text("确定要删除「$playlistName」吗？歌曲不会被删除。") },
            confirmButton = { TextButton(onClick = { showDeleteConfirm = false; onDeletePlaylist() }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") } }
        )
    }
}
