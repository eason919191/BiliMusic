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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.bilimusic.ui.components.BiliAsyncImage
import com.bilimusic.data.model.Music
import android.widget.Toast
import com.bilimusic.data.model.Playlist

@Composable
fun PlaylistScreen(
    viewModel: PlaylistViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    val selectedPlaylistId = uiState.selectedPlaylistId

    // 显示错误/同步结果
    LaunchedEffect(uiState.error) {
        uiState.error?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

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
                onBatchRemove = { viewModel.batchRemoveSongs() },
                onBatchDownload = { viewModel.batchDownloadSongs() },
                onMoveUp = { viewModel.moveSong(it, it - 1) },
                onMoveDown = { viewModel.moveSong(it, it + 1) },
                onDownloadSong = { songId -> viewModel.downloadSong(songId) },
                onCommitOrder = { orderedIds -> viewModel.commitSongOrder(pid, orderedIds) },
                onSync = { viewModel.showSyncDialog() },
                isSyncing = uiState.isSyncing,
                playlistFavoriteFolderId = playlist?.favoriteFolderId
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

    // 同步方向选择对话框
    if (uiState.showSyncDialog && selectedPlaylistId != null) {
        val pid = selectedPlaylistId!!
        AlertDialog(
            onDismissRequest = { viewModel.hideSyncDialog() },
            title = { Text("同步B站收藏夹") },
            text = {
                Column {
                    ListItem(
                        headlineContent = { Text("从B站拉取") },
                        supportingContent = { Text("将收藏夹中的新视频添加到本歌单") },
                        leadingContent = { Icon(Icons.Filled.CloudDownload, null) },
                        modifier = Modifier.clickable {
                            viewModel.hideSyncDialog()
                            viewModel.syncPlaylistWithBilibili(pid)
                        }
                    )
                    ListItem(
                        headlineContent = { Text("推送到B站") },
                        supportingContent = { Text("将歌单中有BV号的新歌曲添加到收藏夹") },
                        leadingContent = { Icon(Icons.Filled.CloudUpload, null) },
                        modifier = Modifier.clickable {
                            viewModel.hideSyncDialog()
                            viewModel.pushToBilibili(pid)
                        }
                    )
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.hideSyncDialog() }) { Text("取消") } }
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
    onEnterSelect: () -> Unit = {},
    onSelectAll: () -> Unit = {},
    onExitSelect: () -> Unit = {},
    onCopyToPlaylist: () -> Unit = {},
    onMoveToPlaylist: () -> Unit = {},
    onBatchRemove: () -> Unit = {},
    onBatchDownload: () -> Unit = {},
    onMoveUp: (Int) -> Unit = {},
    onMoveDown: (Int) -> Unit = {},
    onDownloadSong: (String) -> Unit = {},
    onCommitOrder: (List<String>) -> Unit = {},
    onSync: () -> Unit = {},
    isSyncing: Boolean = false,
    playlistFavoriteFolderId: Long? = null
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    // 本地排序缓存：用 songId 列表存储顺序，在 composition 中同步初始化，消除 LaunchedEffect 异步滞后导致的闪动
    var localOrderById by remember { mutableStateOf<List<String>?>(null) }

    // 同步初始化：进入选择模式时，同帧从 songs 读取并初始化排序（无 LaunchedEffect 的 1 帧滞后）
    if (isSelecting && localOrderById == null) {
        localOrderById = songs.map { it.id }
    }
    // 退出选择模式时清空
    if (!isSelecting && localOrderById != null) {
        localOrderById = null
    }

    // 退出选择模式时提交本地排序
    val handleExitSelection = {
        localOrderById?.let { onCommitOrder(it) }
        onExitSelect()
    }

    // ===== 拖拽排序状态 =====
    var draggedSongId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    val lazyListState = rememberLazyListState()
    // rememberUpdatedState：pointerInput 内部读到的始终是最新值（因为 pointerInput 在首次 composition 时捕获引用）
    val latestLocalOrderById by rememberUpdatedState(localOrderById)
    val latestLazyListState by rememberUpdatedState(lazyListState)

    // 拖拽靠近列表边缘时自动滚动
    LaunchedEffect(draggedSongId, dragOffsetY) {
        val id = draggedSongId ?: return@LaunchedEffect
        val info = latestLazyListState.layoutInfo
        val item = info.visibleItemsInfo.find { it.key == id } ?: return@LaunchedEffect
        val draggedTop = item.offset + dragOffsetY.roundToInt()
        val threshold = item.size
        if (draggedTop < info.viewportStartOffset + threshold) {
            latestLazyListState.dispatchRawDelta(-item.size.toFloat())
        } else if (draggedTop + item.size > info.viewportEndOffset - threshold) {
            latestLazyListState.dispatchRawDelta(item.size.toFloat())
        }
    }

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
                    IconButton(onClick = onBatchDownload) { Icon(Icons.Filled.Download, "下载") }
                    IconButton(onClick = onBatchRemove) { Icon(Icons.Filled.Delete, "移除", tint = MaterialTheme.colorScheme.error) }
                    IconButton(onClick = handleExitSelection) { Icon(Icons.Filled.Close, "取消") }
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
                        if (playlistFavoriteFolderId != null) {
                            IconButton(
                                onClick = onSync,
                                enabled = !isSyncing
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = androidx.compose.ui.graphics.Color.White
                                    )
                                } else {
                                    Icon(Icons.Filled.Sync, "同步收藏夹", tint = androidx.compose.ui.graphics.Color.White)
                                }
                            }
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) { Icon(Icons.Filled.Delete, "删除", tint = androidx.compose.ui.graphics.Color.White) }
                    }
                }
            }
        } else {
            // 选择模式下的简单顶栏
            TopAppBar(
                title = { Text("") },
                navigationIcon = { IconButton(onClick = handleExitSelection) { Icon(Icons.Filled.Close, "返回") } }
            )
        }

        if (songs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("歌单为空", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            // 从 localOrderById 和 songs 派生显示列表：用 songMap 做映射，保证 songs 数据变更时也能正确反映
            val displaySongs: List<Music> = if (isSelecting && localOrderById != null) {
                val songMap = songs.associateBy { it.id }
                localOrderById!!.mapNotNull { songMap[it] }
            } else {
                songs
            }
            LazyColumn(modifier = Modifier.fillMaxSize(), state = lazyListState) {
                itemsIndexed(displaySongs, key = { _, s -> s.id }) { index, song ->
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
                                Icon(
                                    Icons.Filled.DragHandle,
                                    contentDescription = "拖动排序",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            } else {
                                var showMenu by remember { mutableStateOf(false) }
                                Row {
                                    IconButton(onClick = { onPlaySong(index) }) { Icon(Icons.Filled.PlayArrow, "播放", Modifier.size(18.dp)) }
                                    IconButton(onClick = { onDownloadSong(song.id) }) { Icon(Icons.Filled.Download, "下载", Modifier.size(18.dp)) }
                                    Box {
                                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Filled.MoreVert, "更多", Modifier.size(18.dp)) }
                                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                            DropdownMenuItem(text = { Text("添加到歌单") }, onClick = {
                                                showMenu = false
                                                onToggleSelect(song.id)
                                                onCopyToPlaylist()
                                            }, leadingIcon = { Icon(Icons.Filled.PlaylistAdd, null) })
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
                        modifier = if (isSelecting) {
                                // 选择模式：长按拖拽排序 + 点击勾选
                                Modifier
                                    .zIndex(if (draggedSongId == song.id) 1f else 0f)
                                    .offset { IntOffset(0, if (draggedSongId == song.id) dragOffsetY.roundToInt() else 0) }
                                    .pointerInput(song.id) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                draggedSongId = song.id
                                                dragOffsetY = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragOffsetY += dragAmount.y

                                                val order = latestLocalOrderById ?: return@detectDragGesturesAfterLongPress
                                                val curIdx = order.indexOf(song.id)
                                                if (curIdx < 0) return@detectDragGesturesAfterLongPress

                                                val itemInfo = latestLazyListState.layoutInfo
                                                    .visibleItemsInfo.find { it.key == song.id }
                                                val itemH = itemInfo?.size?.toFloat() ?: return@detectDragGesturesAfterLongPress
                                                if (itemH <= 0f) return@detectDragGesturesAfterLongPress

                                                val delta = (dragOffsetY / itemH).roundToInt()
                                                val target = (curIdx + delta).coerceIn(0, order.lastIndex)

                                                if (target != curIdx) {
                                                    val newOrder = order.toMutableList()
                                                    val moved = newOrder.removeAt(curIdx)
                                                    newOrder.add(target, moved)
                                                    localOrderById = newOrder
                                                    dragOffsetY -= delta * itemH
                                                }
                                            },
                                            onDragEnd = {
                                                draggedSongId = null
                                                dragOffsetY = 0f
                                            },
                                            onDragCancel = {
                                                draggedSongId = null
                                                dragOffsetY = 0f
                                            }
                                        )
                                    }
                                    .clickable { onToggleSelect(song.id) }
                            } else {
                                // 普通模式：点击播放 + 长按进入多选
                                Modifier
                                    .combinedClickable(
                                        onClick = { onPlaySong(index) },
                                        onLongClick = { onLongPress(song.id) }
                                    )
                            }
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
