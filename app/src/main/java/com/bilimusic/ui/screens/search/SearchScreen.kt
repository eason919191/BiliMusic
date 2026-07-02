package com.bilimusic.ui.screens.search

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bilimusic.data.api.netease.NeteaseSong
import com.bilimusic.data.api.netease.NeteaseSongParser
import com.bilimusic.data.model.Music
import com.bilimusic.data.model.DownloadTask
import com.bilimusic.data.model.DownloadStatus
import com.bilimusic.ui.components.BiliAsyncImage
import com.bilimusic.data.model.BilibiliVideo
import com.bilimusic.player.MusicPlayer

@Composable
fun SearchScreen(
    viewModel: SearchViewModel = hiltViewModel(),
    onPlaySong: (BilibiliVideo) -> Unit = {},
    musicPlayer: MusicPlayer? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val playlistViewModel: com.bilimusic.ui.screens.playlist.PlaylistViewModel = hiltViewModel()

    val playlistUiState by playlistViewModel.uiState.collectAsState()
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var pendingVideo by remember { mutableStateOf<BilibiliVideo?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    var showArtistDetail by remember { mutableStateOf<com.bilimusic.data.api.netease.NeteaseSong?>(null) }

    // Toast for non-dialog messages (not covered by bottom bars)
    LaunchedEffect(uiState.error) {
        uiState.error?.let { msg ->
            if (uiState.errorTitle == null) {
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        }
    }

    // Playlist selection dialog (single or batch)
    if (showPlaylistDialog) {
        val isBatch = uiState.isSelecting
        AlertDialog(
            onDismissRequest = { showPlaylistDialog = false; pendingVideo = null },
            title = { Text("添加到歌单") },
            text = {
                if (playlistUiState.playlists.isEmpty()) {
                    Text("还没有歌单，请先去歌单页面创建")
                } else {
                    LazyColumn {
                        items(playlistUiState.playlists) { playlist ->
                            ListItem(
                                headlineContent = { Text(playlist.name) },
                                supportingContent = { Text("${playlist.songCount ?: ""}首") },
                                modifier = Modifier.clickable {
                                    if (isBatch) viewModel.batchAddToPlaylist(playlist.id)
                                    else if (pendingVideo != null) viewModel.addToPlaylist(pendingVideo!!, playlist.id)
                                    showPlaylistDialog = false
                                    pendingVideo = null
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPlaylistDialog = false; pendingVideo = null }) { Text("取消") } }
        )
    }

    // Error dialog
    if (uiState.errorTitle != null && uiState.error != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text(uiState.errorTitle ?: "错误") },
            text = { Text(uiState.error ?: "错误") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("确定")
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
        // Batch action bar
        if (uiState.isSelecting) {
            Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)) {
                Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("已选", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                    TextButton(onClick = { viewModel.selectAll() }) { Text("全选") }
                    IconButton(onClick = { showPlaylistDialog = true }) { Icon(Icons.Filled.PlaylistAdd, "添加到歌单") }
                    IconButton(onClick = { viewModel.batchDownload() }) { Icon(Icons.Filled.Download, "下载") }
                    IconButton(onClick = { viewModel.exitSelectionMode() }) { Icon(Icons.Outlined.Close, "取消") }
                }
            }
        }

        // Search Bar (hide in selection mode)
        if (!uiState.isSelecting) {
            // Source toggle chip row
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = uiState.searchSource == 0,
                    onClick = { viewModel.setSearchSource(0); if (uiState.isShowingResults && uiState.searchSource != 0) viewModel.onSearch(uiState.query) },
                    label = { Text("B站") },
                    leadingIcon = { Icon(Icons.Filled.PlayArrow, null, Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = uiState.searchSource == 1,
                    onClick = { viewModel.setSearchSource(1); if (uiState.isShowingResults && uiState.searchSource != 1) viewModel.onSearch(uiState.query) },
                    label = { Text("网易云") },
                    leadingIcon = { Icon(Icons.Filled.MusicNote, null, Modifier.size(16.dp)) }
                )
            }

            SearchBar(
                query = uiState.query,
                onQueryChange = { viewModel.onQueryChange(it) },
                onSearch = { viewModel.onSearch(it) },
                onClear = { viewModel.onQueryChange("") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
            // Netease search type chips
            if (uiState.searchSource == 1 && !uiState.isSelecting) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SuggestionChip(
                        onClick = {
                            viewModel.setNeteaseSearchType(0)
                            if (uiState.isShowingResults) viewModel.onSearch(uiState.query)
                        },
                        label = { Text("歌曲") }
                    )
                    SuggestionChip(
                        onClick = {
                            viewModel.setNeteaseSearchType(1)
                            if (uiState.isShowingResults) viewModel.onSearch(uiState.query)
                        },
                        label = { Text("歌单") }
                    )
                    SuggestionChip(
                        onClick = {
                            viewModel.setNeteaseSearchType(2)
                            if (uiState.isShowingResults) viewModel.onSearch(uiState.query)
                        },
                        label = { Text("歌手") }
                    )
                }
            }
            // 搜索建议
            if (suggestions.isNotEmpty() && !uiState.isShowingResults) {
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(suggestions.size) { i ->
                        SuggestionChip(
                            onClick = {
                                viewModel.onQueryChange(suggestions[i])
                                viewModel.onSearch(suggestions[i])
                            },
                            label = { Text(suggestions[i], maxLines = 1) }
                        )
                    }
                }
            }
        }

        // 获取搜索建议
        LaunchedEffect(uiState.query) {
            if (!uiState.isShowingResults) viewModel.fetchSuggestions(uiState.query)
        }

        // Content
        Box(modifier = Modifier.fillMaxSize()) {
            if (uiState.isShowingResults) {
                AnimatedContent(
                    targetState = uiState.searchSource,
                    transitionSpec = {
                        fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                    },
                    label = "search_source_transition"
                ) { source ->
                    if (source == 0) {
                    SearchResults(
                        results = uiState.searchResults,
                        isSearching = uiState.isSearching,
                        error = uiState.error,
                        isSelecting = uiState.isSelecting,
                        selectedIds = uiState.selectedIds,
                        onPlay = { video ->
                            if (uiState.isSelecting) viewModel.toggleSelection(video.bvid)
                            else { viewModel.playSong(video); onPlaySong(video) }
                        },
                        onDownload = { viewModel.downloadSong(it) },
                        onShare = { video ->
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, "https://www.bilibili.com/video/${video.bvid}")
                                putExtra(android.content.Intent.EXTRA_SUBJECT, video.title)
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, "分享到"))
                        },
                        onAddToPlaylist = { video -> pendingVideo = video; showPlaylistDialog = true },
                        onLongPress = { viewModel.enterSelectionMode(it.bvid) },
                        onLoadMore = { viewModel.loadMore() },
                        onClearError = { viewModel.clearError() }
                    )
                } else {
                    if (uiState.neteaseSearchType == 1) {
                        var showImportDialog by remember { mutableStateOf<com.bilimusic.data.api.netease.NeteasePlaylist?>(null) }
                        NeteasePlaylistResults(
                            playlists = uiState.neteasePlaylists,
                            isSearching = uiState.isSearching,
                            onPlaylistClick = { pl ->
                                val found = uiState.neteasePlaylists.find { it.id == pl }
                                if (found != null) showImportDialog = found
                            }
                        )
                        showImportDialog?.let { pl ->
                            ImportNeteasePlaylistDialog(
                                playlist = pl,
                                onDismiss = { showImportDialog = null },
                                onConfirm = {
                                    showImportDialog = null
                                    viewModel.importNeteasePlaylist(pl.id, pl.name)
                                }
                            )
                        }
                    } else if (uiState.neteaseSearchType == 2) {
                        ArtistResultList(
                            results = uiState.neteaseResults,
                            isSearching = uiState.isSearching,
                            onArtistClick = { artist ->
                                showArtistDetail = artist
                            }
                        )
                    } else {
                        NeteaseSearchResults(
                            results = uiState.neteaseResults,
                            isSearching = uiState.isSearching,
                            onPlay = { song -> viewModel.playSongFromSearch(song) },
                            onDownload = { song -> viewModel.downloadNeteaseSong(song) },
                            onAddToPlaylist = { song ->
                                pendingVideo = null
                                showPlaylistDialog = true
                            }
                        )
                    }
                }
                }
            } else {
                SearchHistory(
                    history = uiState.searchHistory,
                    onItemClick = { viewModel.onSearch(it.query) },
                    onDeleteItem = { viewModel.deleteHistoryItem(it.id) },
                    onClearAll = { viewModel.clearHistory() }
                )
            }
        }
    }

    SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }

    // Artist detail overlay (full screen, outside main Box)
    showArtistDetail?.let { artist ->
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            NeteaseArtistDetailScreen(
                artistName = artist.name,
                artistId = artist.id,
                artistCover = artist.coverUrl,
                musicPlayer = musicPlayer,
                onBack = { showArtistDetail = null }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("搜索音乐、UP主、视频…") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "搜索") },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Outlined.Close, contentDescription = "取消")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        ),
        modifier = modifier,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            imeAction = androidx.compose.ui.text.input.ImeAction.Search
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onSearch = { onSearch(query) }
        )
    )
}

@Composable
private fun SearchHistory(
    history: List<com.bilimusic.data.model.SearchHistory>,
    onItemClick: (com.bilimusic.data.model.SearchHistory) -> Unit,
    onDeleteItem: (com.bilimusic.data.model.SearchHistory) -> Unit,
    onClearAll: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (history.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.History,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "暂无搜索记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "搜索历史",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onClearAll) {
                    Text("清空", style = MaterialTheme.typography.labelMedium)
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(history, key = { it.id }) { item ->
                    ListItem(
                        headlineContent = { Text(item.query) },
                        leadingContent = {
                            Icon(
                                Icons.Outlined.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { onDeleteItem(item) }) {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = "删除",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        modifier = Modifier.combinedClickable(
                            onClick = { onItemClick(item) }
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResults(
    results: List<BilibiliVideo>,
    isSearching: Boolean,
    error: String? = null,
    isSelecting: Boolean = false,
    selectedIds: Set<String> = emptySet(),
    onPlay: (BilibiliVideo) -> Unit,
    onDownload: (BilibiliVideo) -> Unit,
    onShare: (BilibiliVideo) -> Unit = {},
    onAddToPlaylist: (BilibiliVideo) -> Unit = {},
    onLongPress: (BilibiliVideo) -> Unit = {},
    onLoadMore: () -> Unit,
    onClearError: () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (results.isEmpty() && isSearching) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("搜索中...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (results.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("未找到结果", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(results, key = { idx, item -> "${item.bvid}_$idx" }) { index, video ->
                    val alpha by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = 1f,
                        animationSpec = androidx.compose.animation.core.tween(
                            durationMillis = 500,
                            delayMillis = (index * 100).coerceAtMost(800),
                            easing = androidx.compose.animation.core.FastOutSlowInEasing
                        ),
                        label = "item_fade"
                    )
                    Box(modifier = Modifier.graphicsLayer(alpha = alpha)) {
                        SearchResultItem(
                            video = video,
                            isSelecting = isSelecting,
                            isChecked = selectedIds.contains(video.bvid),
                            onPlay = { onPlay(video) },
                            onDownload = { onDownload(video) },
                            onShare = { onShare(video) },
                            onAddToPlaylist = { onAddToPlaylist(video) },
                            onLongPress = { onLongPress(video) }
                        )
                    }
                }

                if (isSearching) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }

                // Load more trigger
                item {
                    LaunchedEffect(Unit) {
                        onLoadMore()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchResultItem(
    video: BilibiliVideo,
    isSelecting: Boolean = false,
    isChecked: Boolean = false,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    onLongPress: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .combinedClickable(
                onClick = { if (isSelecting) onPlay() else onPlay() },
                onLongClick = if (!isSelecting) onLongPress else null
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelecting) {
                Checkbox(checked = isChecked, onCheckedChange = { onPlay() }, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
            }

            BiliAsyncImage(
                model = video.coverUrl, contentDescription = "封面",
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(video.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(video.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(4.dp))
                    Text(formatDuration(video.duration), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (!isSelecting) {
                IconButton(onClick = onPlay) { Icon(Icons.Filled.PlayArrow, "播放", tint = MaterialTheme.colorScheme.primary) }
                IconButton(onClick = onDownload) { Icon(Icons.Filled.Download, "下载", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                Box {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Filled.MoreVert, "更多", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        val ctx = androidx.compose.ui.platform.LocalContext.current
                        DropdownMenuItem(text = { Text("在B站App中打开") }, onClick = {
                            showMenu = false
                            try {
                                ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                    data = android.net.Uri.parse("bilibili://video/${video.bvid}")
                                    setPackage("tv.danmaku.bili")
                                })
                            } catch (_: Exception) {}
                        }, leadingIcon = { Icon(Icons.Filled.OpenInBrowser, null) })
                        DropdownMenuItem(text = { Text("分享") }, onClick = { showMenu = false; onShare() }, leadingIcon = { Icon(Icons.Filled.Share, null) })
                        DropdownMenuItem(text = { Text("添加到歌单") }, onClick = { showMenu = false; onAddToPlaylist() }, leadingIcon = { Icon(Icons.Filled.PlaylistAdd, null) })
                    }
                }
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
    else String.format("%02d:%02d", m, s)
}

@Composable
private fun NeteaseSearchResults(
    results: List<com.bilimusic.data.api.netease.NeteaseSong>,
    isSearching: Boolean,
    onPlay: (com.bilimusic.data.api.netease.NeteaseSong) -> Unit,
    onDownload: (com.bilimusic.data.api.netease.NeteaseSong) -> Unit = {},
    onAddToPlaylist: (com.bilimusic.data.api.netease.NeteaseSong) -> Unit = {}
) {
    if (results.isEmpty() && isSearching) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
                Text("搜索中...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else if (results.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Search, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                Spacer(Modifier.height(8.dp))
                Text("未找到结果", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(results, key = { _, s -> s.id }) { index, song ->
                val alpha by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = 1f,
                    animationSpec = androidx.compose.animation.core.tween(
                        durationMillis = 500, delayMillis = (index * 100).coerceAtMost(800),
                        easing = androidx.compose.animation.core.FastOutSlowInEasing
                    ), label = "fade"
                )
                Box(modifier = Modifier.graphicsLayer(alpha = alpha)) {
                    NeteaseSongItem(
                        song = song,
                        onPlay = { onPlay(song) },
                        onDownload = { onDownload(song) },
                        onAddToPlaylist = { onAddToPlaylist(song) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NeteaseSongItem(
    song: com.bilimusic.data.api.netease.NeteaseSong,
    onPlay: () -> Unit,
    onDownload: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).combinedClickable(onClick = onPlay),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (song.coverUrl != null) {
                BiliAsyncImage(model = song.coverUrl, contentDescription = "封面",
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
            } else Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
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
            IconButton(onClick = onPlay) { Icon(Icons.Filled.PlayArrow, "播放", tint = MaterialTheme.colorScheme.primary) }
            IconButton(onClick = onDownload) { Icon(Icons.Filled.Download, "下载", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            Box {
                IconButton(onClick = { showMenu = true }) { Icon(Icons.Filled.MoreVert, "更多", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    DropdownMenuItem(text = { Text("分享") }, onClick = {
                        showMenu = false
                        try {
                            ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, "【${song.name}】- ${song.artistName}\n来自网易云音乐")
                                putExtra(android.content.Intent.EXTRA_SUBJECT, song.name)
                            }.let { android.content.Intent.createChooser(it, "分享") })
                        } catch (_: Exception) {}
                    }, leadingIcon = { Icon(Icons.Filled.Share, null) })
                    DropdownMenuItem(text = { Text("在B站搜索") }, onClick = {
                        showMenu = false
                        try {
                            ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                data = android.net.Uri.parse("https://search.bilibili.com/all?keyword=${java.net.URLEncoder.encode(song.name + " " + song.artistName, "UTF-8")}")
                            })
                        } catch (_: Exception) {}
                    }, leadingIcon = { Icon(Icons.Filled.OpenInBrowser, null) })
                    DropdownMenuItem(text = { Text("添加到歌单") }, onClick = {
                        showMenu = false; onAddToPlaylist()
                    }, leadingIcon = { Icon(Icons.Filled.PlaylistAdd, null) })
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NeteasePlaylistResults(
    playlists: List<com.bilimusic.data.api.netease.NeteasePlaylist>,
    isSearching: Boolean,
    onPlaylistClick: (Long) -> Unit
) {
    if (isSearching && playlists.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else if (playlists.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Search, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                Spacer(Modifier.height(8.dp))
                Text("未找到歌单", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            items(playlists, key = { it.id }) { pl ->
                ListItem(
                    headlineContent = { Text(pl.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text("${pl.songCount} 首 · ${pl.nickname}") },
                    leadingContent = {
                        if (pl.coverUrl != null) {
                            BiliAsyncImage(model = pl.coverUrl, contentDescription = "封面",
                                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                        } else Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.QueueMusic, null, Modifier.size(28.dp))
                        }
                    },
                    modifier = Modifier.combinedClickable(onClick = { onPlaylistClick(pl.id) })
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArtistResultList(
    results: List<com.bilimusic.data.api.netease.NeteaseSong>,
    isSearching: Boolean,
    onArtistClick: (com.bilimusic.data.api.netease.NeteaseSong) -> Unit
) {
    if (isSearching && results.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else if (results.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Search, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                Spacer(Modifier.height(8.dp))
                Text("未找到艺人", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(results, key = { _, s -> s.id }) { index, artist ->
                val alpha by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = 1f,
                    animationSpec = androidx.compose.animation.core.tween(500, delayMillis = (index * 100).coerceAtMost(800)),
                    label = "fade"
                )
                Box(modifier = Modifier.graphicsLayer(alpha = alpha)) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).combinedClickable(onClick = { onArtistClick(artist) }),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (artist.coverUrl != null) {
                                BiliAsyncImage(model = artist.coverUrl, contentDescription = "头像",
                                    modifier = Modifier.size(56.dp).clip(androidx.compose.foundation.shape.CircleShape), contentScale = ContentScale.Crop)
                            } else Box(Modifier.size(56.dp).clip(androidx.compose.foundation.shape.CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Person, null, Modifier.size(28.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(artist.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("歌手", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

