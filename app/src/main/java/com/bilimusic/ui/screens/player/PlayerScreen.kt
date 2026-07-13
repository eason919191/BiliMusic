package com.bilimusic.ui.screens.player

import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bilimusic.ui.components.BiliAsyncImage
import coil.compose.rememberAsyncImagePainter
import com.bilimusic.ui.components.PixelProgressBar
import com.bilimusic.data.model.PlayMode
import com.bilimusic.data.model.BilibiliVideo
import com.bilimusic.data.api.netease.NeteaseSong
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel = hiltViewModel(),
    onMinimize: () -> Unit = {},
    coverSharedModifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val playlistViewModel: com.bilimusic.ui.screens.playlist.PlaylistViewModel = hiltViewModel()
    val playlistUiState by playlistViewModel.uiState.collectAsState()
    var showPlaylistDialog by remember { mutableStateOf(false) }
    // song 变化时强制重置 sharedElement 状态，避免跨歌曲状态残留
    var currentSongId by remember { mutableStateOf("") }

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
                onToggleLyrics = { viewModel.toggleLyrics() },
                onCycleLyricsMode = { viewModel.cycleLyricsMode() },
                onDownload = { viewModel.downloadCurrentSong() },
                onEditLyrics = { viewModel.updateLyrics(it); android.widget.Toast.makeText(context, "歌词已更新", android.widget.Toast.LENGTH_SHORT).show() },
                onSetSleepTimer = { viewModel.setSleepTimer(it) },
                onClearSleepTimer = { viewModel.clearSleepTimer() },
                onSetPlaybackSpeed = { viewModel.setPlaybackSpeed(it) },
                onSetPitch = { viewModel.setPitch(it) },
                onSetAudioQuality = { viewModel.setAudioQuality(it); android.widget.Toast.makeText(context, "音质已切换，下一首歌生效", android.widget.Toast.LENGTH_SHORT).show() },
                onImportLyrics = { viewModel.importLyricsFromFile(it) },
                onSwitchAudioSource = { url, bvid, neteaseId, source, isLocal, localPath ->
                    viewModel.switchAudioSource(url, bvid, neteaseId, source, isLocal, localPath)
                },
                coverSharedModifier = coverSharedModifier,
                onShare = {
                    val text = viewModel.getCurrentSongShareText()
                    if (text.isNotBlank()) {
                        context.startActivity(
                            android.content.Intent.createChooser(
                                android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, text)
                                },
                                "分享"

                            )
                        )
                    }
                },
                onOpenInBilibili = {
                    if (viewModel.uiState.value.currentSong?.bvid != null) {
                        val bvid = viewModel.uiState.value.currentSong!!.bvid!!
                        viewModel.togglePlayPause()
                        try {
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                data = android.net.Uri.parse("bilibili://video/$bvid")
                                setPackage("tv.danmaku.bili")
                            })
                        } catch (_: Exception) {
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                data = android.net.Uri.parse("https://www.bilibili.com/video/$bvid")
                            })
                        }
                    }
                }
            )

        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
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
    onToggleLyrics: () -> Unit = {},
    onCycleLyricsMode: () -> Unit = {},
    onDownload: () -> Unit = {},
    onShare: () -> Unit = {},
    onEditLyrics: (List<com.bilimusic.data.api.LyricLine>) -> Unit = {},
    onSetSleepTimer: (Long) -> Unit = {},
    onClearSleepTimer: () -> Unit = {},
    onSetPlaybackSpeed: (Float) -> Unit = {},
    onSetPitch: (Float) -> Unit = {},
    onSetAudioQuality: (String) -> Unit = {},
    onImportLyrics: (String) -> Unit = {},
    onSwitchAudioSource: (url: String?, bvid: String?, neteaseId: Long, source: String, isLocal: Boolean, localPath: String?) -> Unit = { _, _, _, _, _, _ -> },
    onOpenInBilibili: () -> Unit = {},
    coverSharedModifier: Modifier = Modifier
) {
    val song = uiState.currentSong ?: return
    var showPlaylist by remember { mutableStateOf(false) }

    // Dialog states (shared across bottom-bar interactions)
    var showSongDetailDialog by remember { mutableStateOf(false) }
    var showLyricEditDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showTimerDialog by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showPitchSheet by remember { mutableStateOf(false) }
    var showEqualizerSheet by remember { mutableStateOf(false) }
    var showSourceSwitchSheet by remember { mutableStateOf(false) }

    // Import lyrics file picker
    val context = androidx.compose.ui.platform.LocalContext.current
    val lyricImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val text = inputStream?.bufferedReader()?.readText() ?: ""
                inputStream?.close()
                if (text.isNotBlank()) onImportLyrics(text)
            } catch (_: Exception) {}
        }
    }

    // Track song switch direction for cover animation (from NeriPlayer)
    var prevSongId by remember { mutableStateOf(song.id) }
    var songDirection by remember { mutableIntStateOf(0) } // -1 left (prev), 1 right (next)
    LaunchedEffect(song.id) {
        songDirection = if (prevSongId.isNotEmpty() && song.id > prevSongId) -1 else 1
        prevSongId = song.id
    }

    // Animated progress bar (skip animation during drag)
    var isDragging by remember { mutableStateOf(false) }
    val progress = if (uiState.duration > 0) {
        (uiState.currentPosition.toFloat() / uiState.duration).coerceIn(0f, 1f)
    } else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = if (isDragging) snap() else tween(durationMillis = 150),
        label = "progress_anim"
    )

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

        // ==================== MAIN CONTENT ====================
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ===== ANIMATED LAYOUT: Normal ↔ Lyrics =====
            // Buttons are inside AnimatedContent in both branches at the same position,
            // so sharedElement animates A/B/C to the correct top-left location.
            // Both Row heights are matched via IntrinsicSize.Min so buttons don't shift.
            Box(Modifier.weight(1f).fillMaxWidth()) {
                SharedTransitionLayout {
                    val coverState = rememberSharedContentState(key = "cover")
                    val titleState = rememberSharedContentState(key = "title")
                    val artistState = rememberSharedContentState(key = "artist")

                    AnimatedContent(
                        targetState = uiState.showLyrics,
                        transitionSpec = {
                            fadeIn(tween(300, easing = FastOutSlowInEasing)) togetherWith
                                    fadeOut(tween(200, easing = FastOutSlowInEasing))
                        },
                        label = "player_layout"
                    ) { isLyrics ->
                        if (isLyrics) {
                            // ========== LYRICS MODE ==========
                            Column(Modifier.fillMaxSize()) {
                                // Top row: mini cover + title/artist (buttons moved to bottom)
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .height(IntrinsicSize.Min),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    // Element A: Mini cover (sharedElement from 260dp center)
                                    Card(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .sharedElement(
                                                state = coverState,
                                                animatedVisibilityScope = this@AnimatedContent
                                            )
                                            .clip(RoundedCornerShape(8.dp)),
                                        shape = RoundedCornerShape(8.dp),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                                    ) {
                                        AnimatedContent(
                                            targetState = song.coverUrl ?: "",
                                            transitionSpec = {
                                                (slideInHorizontally { width -> width } + fadeIn()) togetherWith
                                                        (slideOutHorizontally { width -> -width } + fadeOut())
                                            },
                                            label = "cover_slide_compact"
                                        ) { url ->
                                            if (url.isNotEmpty()) {
                                                BiliAsyncImage(model = url, contentDescription = "专辑封面", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                            } else {
                                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Filled.MusicNote, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                                }
                                            }
                                        }
                                    }

                                    Spacer(Modifier.width(6.dp))

                                    // Element B + C
                                    Column(Modifier.weight(1f).widthIn(max = 200.dp), verticalArrangement = Arrangement.Center) {
                                        Text(
                                            text = song.title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = song.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                                maxLines = 1, overflow = TextOverflow.Ellipsis
                                            )
                                            if (song.source == "NETEASE") { Spacer(Modifier.width(4.dp)); Text("网易云", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }; if (song.isLocal || song.source == "LOCAL") { Spacer(Modifier.width(4.dp)); Text("本地", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) } else if (song.source != "NETEASE") { Spacer(Modifier.width(4.dp)); Text("哔哩哔哩", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                                        }
                                    }
                                    // 收起在右上角
                                    IconButton(onClick = onMinimize) {
                                        Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "收起", tint = MaterialTheme.colorScheme.onSurface)
                                    }
                                }

                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    if (uiState.lyrics.isNotEmpty()) {
                                        LyricsView(
                                            lyrics = uiState.lyrics, currentIndex = uiState.currentLyricIndex, currentPosition = uiState.currentPosition,
                                            lyricBlurEnabled = uiState.lyricBlurEnabled, lyricBlurAmount = uiState.lyricBlurAmount,
                                            lyricBlurCurrent = uiState.lyricBlurCurrent, lyricBlurNear = uiState.lyricBlurNear,
                                            lyricBlurMid = uiState.lyricBlurMid, lyricBlurFar = uiState.lyricBlurFar,
                                            lyricScaleCurrent = uiState.lyricScaleCurrent, lyricScaleNear = uiState.lyricScaleNear,
                                            lyricMaxWidth = uiState.lyricMaxWidth,
                                            textAlign = if (uiState.lyricTextAlign == "left") androidx.compose.ui.text.style.TextAlign.Start else androidx.compose.ui.text.style.TextAlign.Center,
                                            fontSize = uiState.lyricFontSize.sp, translatedLyrics = if (uiState.showCombinedLyrics) uiState.translatedLyrics else emptyList(),
                                            onLyricClick = { position -> onSeek(position + 200L) }, modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Text(if (song.bvid != null) "暂无可用歌词\n（登录哔哩哔哩后可获取AI字幕）" else "当前歌曲无字幕",
                                            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), textAlign = TextAlign.Center)
                                    }
                                }
                            }
                        } else {
                            // ========== NORMAL MODE ==========
                            Column(Modifier.fillMaxSize()) {
                                // Top row: "正在播放" (buttons moved to bottom)
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .height(IntrinsicSize.Min),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("正在播放", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                                    Spacer(Modifier.weight(1f))
                                    // 收起在右上角
                                    IconButton(onClick = onMinimize) {
                                        Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "收起", tint = MaterialTheme.colorScheme.onSurface)
                                    }
                                }

                                // Centered content (cover, title, artist)
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Card(
                                                modifier = Modifier.size(260.dp)
                                                    .sharedElement(state = coverState, animatedVisibilityScope = this@AnimatedContent)
                                                    .clip(RoundedCornerShape(16.dp)),
                                                shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                                            ) {
                                                AnimatedContent(
                                                    targetState = song.coverUrl ?: "",
                                                    transitionSpec = { (slideInHorizontally { width -> width } + fadeIn()) togetherWith (slideOutHorizontally { width -> -width } + fadeOut()) },
                                                    label = "cover_slide_full"
                                                ) { url ->
                                                    if (url.isNotEmpty()) {
                                                        BiliAsyncImage(model = url, contentDescription = "专辑封面", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                                    } else {
                                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                            Icon(Icons.Filled.MusicNote, null, Modifier.size(80.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = song.title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
                                        ) {
                                            Text(text = song.artist, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            if (song.source == "NETEASE") { Spacer(Modifier.width(4.dp)); Text("网易云", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }; if (song.isLocal || song.source == "LOCAL") { Spacer(Modifier.width(4.dp)); Text("本地", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) } else if (song.source != "NETEASE") { Spacer(Modifier.width(4.dp)); Text("哔哩哔哩", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ===== ALWAYS VISIBLE: Progress bar + Controls (two rows) =====
            Spacer(modifier = Modifier.height(16.dp))

            PixelProgressBar(
                progress = if (isDragging) progress else animatedProgress,
                onProgressChange = { isDragging = true; onSeek((it * uiState.duration).toLong()) },
                onProgressChangeFinished = { isDragging = false },
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

            Spacer(modifier = Modifier.height(8.dp))

            // Row 1: Playback controls (prev/play/next) — centered
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Filled.SkipPrevious, "上一首", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(40.dp))
                }
                Spacer(Modifier.width(24.dp))
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
                Spacer(Modifier.width(24.dp))
                IconButton(onClick = onNext) {
                    Icon(Icons.Filled.SkipNext, "下一首", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(40.dp))
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Row 2: Utility buttons (play mode, lyrics, download, more) — spread evenly
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 循环模式
                IconButton(onClick = onCyclePlayMode) {
                    Icon(
                        when (uiState.playMode) { PlayMode.LOOP -> Icons.Outlined.Loop; PlayMode.SHUFFLE -> Icons.Outlined.Shuffle; PlayMode.SINGLE -> Icons.Outlined.RepeatOne },
                        "播放模式",
                        tint = if (uiState.playMode != PlayMode.LOOP) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(24.dp)
                    )
                }
                // 歌词
                IconButton(onClick = { onToggleLyrics() }) {
                    Icon(
                        if (uiState.showLyrics) Icons.Filled.Article else Icons.Outlined.Article,
                        contentDescription = "歌词",
                        tint = when {
                            uiState.showLyrics -> MaterialTheme.colorScheme.primary
                            uiState.lyrics.isNotEmpty() -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        },
                        modifier = Modifier.size(24.dp)
                    )
                }
                // 歌词语言切换
                if (uiState.lyrics.isNotEmpty()) {
                    val modeLabel = when (uiState.lyricsMode) { 0 -> "中"; 1 -> "EN"; else -> "中EN" }
                    val modeTint = when (uiState.lyricsMode) { 2 -> MaterialTheme.colorScheme.primary; else -> MaterialTheme.colorScheme.onSurface }
                    TextButton(onClick = { onCycleLyricsMode() }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                        Text(text = modeLabel, style = MaterialTheme.typography.labelMedium, color = modeTint)
                    }
                }
                // 下载（本地歌曲不显示）
                if (!song.isLocal && song.source != "LOCAL") {
                    IconButton(onClick = onDownload) {
                        Icon(Icons.Filled.Download, "下载", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.size(24.dp))
                    }
                }
                // 更多
                var showMoreMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showMoreMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "更多", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
                    }
                    if (showMoreMenu) {
                        ModalBottomSheet(
                            onDismissRequest = { showMoreMenu = false },
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            Column(Modifier.fillMaxWidth().padding(bottom = 32.dp).verticalScroll(rememberScrollState())) {
                                Text("更多操作", style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
                                HorizontalDivider()
                                ListItem(headlineContent = { Text("在哔哩哔哩打开") }, supportingContent = if (uiState.showMenuSubtitle) {{ Text("在哔哩哔哩App或网页中查看原视频") }} else null, leadingContent = { Icon(Icons.Filled.OpenInBrowser, contentDescription = null) }, modifier = Modifier.clickable { showMoreMenu = false; onOpenInBilibili() })
                                ListItem(headlineContent = { Text("分享") }, leadingContent = { Icon(Icons.Filled.Share, contentDescription = null) }, modifier = Modifier.clickable { showMoreMenu = false; onShare() })
                                ListItem(headlineContent = { Text("添加到歌单") }, leadingContent = { Icon(Icons.Filled.PlaylistAdd, contentDescription = null) }, modifier = Modifier.clickable { showMoreMenu = false; onAddToPlaylist() })
                                ListItem(headlineContent = { Text("播放列表") }, leadingContent = { Icon(Icons.Outlined.QueueMusic, contentDescription = null) }, modifier = Modifier.clickable { showMoreMenu = false; showPlaylist = true })
                                HorizontalDivider()
                                ListItem(headlineContent = { Text("歌曲详情") }, supportingContent = if (uiState.showMenuSubtitle) {{ Text("查看歌手、专辑、曲风等信息") }} else null, leadingContent = { Icon(Icons.Outlined.Info, contentDescription = null) }, modifier = Modifier.clickable { showMoreMenu = false; showSongDetailDialog = true })
                                ListItem(headlineContent = { Text("歌词编辑") }, supportingContent = if (uiState.showMenuSubtitle) {{ Text("修改当前歌词内容") }} else null, leadingContent = { Icon(Icons.Outlined.Edit, contentDescription = null) }, modifier = Modifier.clickable { showMoreMenu = false; showLyricEditDialog = true })
                                ListItem(headlineContent = { Text("导入歌词文件") }, supportingContent = if (uiState.showMenuSubtitle) {{ Text("从本地选择 .lrc 文件") }} else null, leadingContent = { Icon(Icons.Outlined.FileUpload, contentDescription = null) }, modifier = Modifier.clickable { showMoreMenu = false; lyricImportLauncher.launch(arrayOf("text/*", "application/octet-stream")) })
                                ListItem(headlineContent = { Text("音质选项") }, supportingContent = if (uiState.showMenuSubtitle) {{ Text("当前: " + uiState.audioQuality) }} else null, leadingContent = { Icon(Icons.Outlined.Tune, contentDescription = null) }, modifier = Modifier.clickable { showMoreMenu = false; showQualityDialog = true })
                                ListItem(headlineContent = { Text("更换音源") }, supportingContent = if (uiState.showMenuSubtitle) {{ Text("哔哩哔哩/网易云/本地音频") }} else null, leadingContent = { Icon(Icons.Outlined.SwapHoriz, contentDescription = null) }, modifier = Modifier.clickable { showMoreMenu = false; showSourceSwitchSheet = true })
                                ListItem(headlineContent = { Text("定时关闭") }, supportingContent = if (uiState.showMenuSubtitle) {{ Text(if (uiState.isTimerActive) { val m = uiState.sleepTimerRemaining / 60000; val s = (uiState.sleepTimerRemaining % 60000) / 1000; "剩余 ${String.format("%02d:%02d", m, s)}" } else "关闭") }} else null, leadingContent = { Icon(Icons.Outlined.Timer, contentDescription = null) }, modifier = Modifier.clickable { showMoreMenu = false; showTimerDialog = true })
                                ListItem(headlineContent = { Text("倍速播放") }, supportingContent = if (uiState.showMenuSubtitle) {{ Text(String.format("%.1f", uiState.playbackSpeed) + "x") }} else null, leadingContent = { Icon(Icons.Outlined.Speed, contentDescription = null) }, modifier = Modifier.clickable { showMoreMenu = false; showSpeedSheet = true })
                                ListItem(headlineContent = { Text("音调调节") }, supportingContent = if (uiState.showMenuSubtitle) {{ Text(String.format("%.1f", uiState.pitch)) }} else null, leadingContent = { Icon(Icons.Outlined.MusicNote, contentDescription = null) }, modifier = Modifier.clickable { showMoreMenu = false; showPitchSheet = true })
                                ListItem(headlineContent = { Text("均衡器") }, supportingContent = if (uiState.showMenuSubtitle) {{ Text("调节音效") }} else null, leadingContent = { Icon(Icons.Outlined.Tune, contentDescription = null) }, modifier = Modifier.clickable { showMoreMenu = false; showEqualizerSheet = true })
                            }
                        }
                    }
                }
                // 收起
            }

            // 更多功能弹窗（移到底部后用同一个状态）
            TopButtonsRowDialogs(
                uiState = uiState,
                showSongDetailDialog = showSongDetailDialog, onDismissSongDetail = { showSongDetailDialog = false },
                showLyricEditDialog = showLyricEditDialog, lyrics = uiState.lyrics, onSaveLyrics = { onEditLyrics(it); showLyricEditDialog = false }, onDismissLyricEdit = { showLyricEditDialog = false },
                showQualityDialog = showQualityDialog, qualityCurrent = uiState.audioQuality, onSelectQuality = { onSetAudioQuality(it); showQualityDialog = false }, onDismissQuality = { showQualityDialog = false },
                showTimerDialog = showTimerDialog, timerActive = uiState.isTimerActive, timerRemaining = uiState.sleepTimerRemaining, onSelectTimer = { onSetSleepTimer(it); showTimerDialog = false }, onClearTimer = { onClearSleepTimer(); showTimerDialog = false }, onDismissTimer = { showTimerDialog = false },
                showSpeedSheet = showSpeedSheet, speedCurrent = uiState.playbackSpeed, onSetSpeed = { onSetPlaybackSpeed(it) }, onDismissSpeed = { showSpeedSheet = false },
                showPitchSheet = showPitchSheet, pitchCurrent = uiState.pitch, onSetPitch = { onSetPitch(it) }, onDismissPitch = { showPitchSheet = false },
                showEqualizerSheet = showEqualizerSheet, onDismissEqualizer = { showEqualizerSheet = false },
            )

            if (showSourceSwitchSheet) {
                SourceSwitchSheet(
                    songTitle = song.title,
                    songArtist = song.artist,
                    onSelect = { url, bvid, neteaseId, source, isLocal, localPath ->
                        showSourceSwitchSheet = false
                        onSwitchAudioSource(url, bvid, neteaseId, source, isLocal, localPath)
                    },
                    onDismiss = { showSourceSwitchSheet = false }
                )
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

        // Playlist drawer overlay (fills entire player, on top)
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

/**
 * Top-right horizontal button row: only lyrics toggle + 收起全屏 are directly visible.
 * Other actions (分享, 添加到歌单, 播放列表) are inside a "更多" DropdownMenu.
 */
@Composable
private fun TopButtonsRow(
    uiState: PlayerUiState,
    onToggleLyrics: () -> Unit,
    onCycleLyricsMode: () -> Unit,
    onMinimize: () -> Unit,
    onShare: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onShowPlaylist: () -> Unit,
    onOpenInBilibili: () -> Unit = {},
    onEditLyrics: (List<com.bilimusic.data.api.LyricLine>) -> Unit = {},
    onSetSleepTimer: (Long) -> Unit = {},
    onClearSleepTimer: () -> Unit = {},
    onSetPlaybackSpeed: (Float) -> Unit = {},
    onSetPitch: (Float) -> Unit = {},
    onSetAudioQuality: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showMoreMenu by remember { mutableStateOf(false) }
    var showSongDetailDialog by remember { mutableStateOf(false) }
    var showLyricEditDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showTimerDialog by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showPitchSheet by remember { mutableStateOf(false) }
    var showEqualizerSheet by remember { mutableStateOf(false) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 歌词按钮 (always visible)
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
        // 歌词语言切换
        if (uiState.lyrics.isNotEmpty()) {
            val modeLabel = when (uiState.lyricsMode) {
                0 -> "中"
                1 -> "EN"
                2 -> "中EN"
                else -> "中"
            }
            val modeTint = when (uiState.lyricsMode) {
                2 -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            }
            TextButton(
                onClick = { onCycleLyricsMode() },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
            ) {
                Text(
                    text = modeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = modeTint
                )
            }
        }

        // 更多按钮 → ModalBottomSheet
        Box {
            IconButton(onClick = { showMoreMenu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "更多", tint = MaterialTheme.colorScheme.onSurface)
            }
            if (showMoreMenu) {
                ModalBottomSheet(
                    onDismissRequest = { showMoreMenu = false },
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Column(Modifier.fillMaxWidth().padding(bottom = 32.dp).verticalScroll(rememberScrollState())) {
                        Text("更多操作", style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
                        HorizontalDivider()

                        // 顶部：哔哩哔哩打开 + 分享 + 添加到歌单 + 播放列表
                        ListItem(headlineContent = { Text("在哔哩哔哩打开") }, supportingContent = if (uiState.showMenuSubtitle) {{ Text("在哔哩哔哩App或网页中查看原视频") }} else null, leadingContent = { Icon(Icons.Filled.OpenInBrowser, contentDescription = null) }, modifier = Modifier.clickable { showMoreMenu = false; onOpenInBilibili() })
                        ListItem(headlineContent = { Text("分享") }, leadingContent = { Icon(Icons.Filled.Share, contentDescription = null) }, modifier = Modifier.clickable { showMoreMenu = false; onShare() })
                        ListItem(headlineContent = { Text("添加到歌单") }, leadingContent = { Icon(Icons.Filled.PlaylistAdd, contentDescription = null) }, modifier = Modifier.clickable { showMoreMenu = false; onAddToPlaylist() })
                        ListItem(headlineContent = { Text("播放列表") }, leadingContent = { Icon(Icons.Outlined.QueueMusic, contentDescription = null) }, modifier = Modifier.clickable { showMoreMenu = false; onShowPlaylist() })
                        HorizontalDivider()
                        // 歌曲详情
                        ListItem(headlineContent = { Text("歌曲详情") }, supportingContent = if (uiState.showMenuSubtitle) {{ Text("查看歌手、专辑、曲风等信息") }} else null, leadingContent = { Icon(Icons.Outlined.Info, contentDescription = null) }, modifier = Modifier.clickable { showMoreMenu = false; showSongDetailDialog = true })
                        // 歌词编辑
                        ListItem(headlineContent = { Text("歌词编辑") }, supportingContent = if (uiState.showMenuSubtitle) {{ Text("修改当前歌词内容") }} else null, leadingContent = { Icon(Icons.Outlined.Edit, contentDescription = null) }, modifier = Modifier.clickable { showMoreMenu = false; showLyricEditDialog = true })
                        // 音质选项
                        ListItem(headlineContent = { Text("音质选项") }, supportingContent = if (uiState.showMenuSubtitle) {{ Text("当前: " + uiState.audioQuality) }} else null, leadingContent = { Icon(Icons.Outlined.Tune, contentDescription = null) }, modifier = Modifier.clickable { showMoreMenu = false; showQualityDialog = true })
                        // 定时关闭
                        ListItem(headlineContent = { Text("定时关闭") }, supportingContent = if (uiState.showMenuSubtitle) {{ Text(if (uiState.isTimerActive) { val m = uiState.sleepTimerRemaining / 60000; val s = (uiState.sleepTimerRemaining % 60000) / 1000; "剩余 ${String.format("%02d:%02d", m, s)}" } else "关闭") }} else null, leadingContent = { Icon(Icons.Outlined.Timer, contentDescription = null) }, modifier = Modifier.clickable { showMoreMenu = false; showTimerDialog = true })
                        // 倍速播放
                        ListItem(headlineContent = { Text("倍速播放") }, supportingContent = if (uiState.showMenuSubtitle) {{ Text(String.format("%.1f", uiState.playbackSpeed) + "x") }} else null, leadingContent = { Icon(Icons.Outlined.Speed, contentDescription = null) }, modifier = Modifier.clickable { showMoreMenu = false; showSpeedSheet = true })
                        // 音调调节
                        ListItem(headlineContent = { Text("音调调节") }, supportingContent = if (uiState.showMenuSubtitle) {{ Text(String.format("%.1f", uiState.pitch)) }} else null, leadingContent = { Icon(Icons.Outlined.MusicNote, contentDescription = null) }, modifier = Modifier.clickable { showMoreMenu = false; showPitchSheet = true })
                        // 均衡器
                        ListItem(headlineContent = { Text("均衡器") }, supportingContent = if (uiState.showMenuSubtitle) {{ Text("调节音效") }} else null, leadingContent = { Icon(Icons.Outlined.Tune, contentDescription = null) }, modifier = Modifier.clickable { showMoreMenu = false; showEqualizerSheet = true })
                    }
                }
            }
        }

        // 最小化/收起全屏 (always visible)
        IconButton(onClick = onMinimize) {
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "收起", tint = MaterialTheme.colorScheme.onSurface)
        }
    }
    // Render all BottomSheets
    TopButtonsRowDialogs(
        uiState = uiState,
        showSongDetailDialog = showSongDetailDialog, onDismissSongDetail = { showSongDetailDialog = false },
        showLyricEditDialog = showLyricEditDialog, lyrics = uiState.lyrics, onSaveLyrics = { onEditLyrics(it); showLyricEditDialog = false }, onDismissLyricEdit = { showLyricEditDialog = false },
        showQualityDialog = showQualityDialog, qualityCurrent = uiState.audioQuality, onSelectQuality = { onSetAudioQuality(it); showQualityDialog = false }, onDismissQuality = { showQualityDialog = false },
        showTimerDialog = showTimerDialog, timerActive = uiState.isTimerActive, timerRemaining = uiState.sleepTimerRemaining, onSelectTimer = { onSetSleepTimer(it); showTimerDialog = false }, onClearTimer = { onClearSleepTimer(); showTimerDialog = false }, onDismissTimer = { showTimerDialog = false },
        showSpeedSheet = showSpeedSheet, speedCurrent = uiState.playbackSpeed, onSetSpeed = { onSetPlaybackSpeed(it) }, onDismissSpeed = { showSpeedSheet = false },
        showPitchSheet = showPitchSheet, pitchCurrent = uiState.pitch, onSetPitch = { onSetPitch(it) }, onDismissPitch = { showPitchSheet = false },
        showEqualizerSheet = showEqualizerSheet, onDismissEqualizer = { showEqualizerSheet = false },
    )
}

// ===== Dialog states rendered inside TopButtonsRow =====
@Composable
private fun TopButtonsRowDialogs(
    uiState: PlayerUiState,
    showSongDetailDialog: Boolean,
    onDismissSongDetail: () -> Unit,
    showLyricEditDialog: Boolean,
    lyrics: List<com.bilimusic.data.api.LyricLine>,
    onSaveLyrics: (List<com.bilimusic.data.api.LyricLine>) -> Unit,
    onDismissLyricEdit: () -> Unit,
    showQualityDialog: Boolean,
    qualityCurrent: String,
    onSelectQuality: (String) -> Unit,
    onDismissQuality: () -> Unit,
    showTimerDialog: Boolean,
    timerActive: Boolean,
    timerRemaining: Long,
    onSelectTimer: (Long) -> Unit,
    onClearTimer: () -> Unit,
    onDismissTimer: () -> Unit,
    showSpeedSheet: Boolean,
    speedCurrent: Float,
    onSetSpeed: (Float) -> Unit,
    onDismissSpeed: () -> Unit,
    showPitchSheet: Boolean,
    pitchCurrent: Float,
    onSetPitch: (Float) -> Unit,
    onDismissPitch: () -> Unit,
    showEqualizerSheet: Boolean,
    onDismissEqualizer: () -> Unit,
) {
    // Note: SpeedSheet and PitchSheet are already ModalBottomSheet instances.
    // SongDetail, LyricEdit, Quality, Timer are AlertDialog — they can be wrapped
    // in ModalBottomSheet but for simplicity keep them as AlertDialog with BottomSheet for speed/pitch.
    // The user wants all 6 as BottomSheet, so wrap them all:

    if (showSongDetailDialog) {
        SongDetailBottomSheet(uiState = uiState, onDismiss = onDismissSongDetail)
    }
    if (showLyricEditDialog) {
        LyricEditBottomSheet(lyrics = lyrics, onSave = onSaveLyrics, onDismiss = onDismissLyricEdit)
    }
    if (showQualityDialog) {
        QualityBottomSheet(current = qualityCurrent, onSelect = onSelectQuality, onDismiss = onDismissQuality)
    }
    if (showTimerDialog) {
        TimerBottomSheet(isActive = timerActive, remaining = timerRemaining, onSelect = onSelectTimer, onClear = onClearTimer, onDismiss = onDismissTimer)
    }
    if (showSpeedSheet) {
        SpeedSliderSheet(currentSpeed = speedCurrent, onSetSpeed = onSetSpeed, onDismiss = onDismissSpeed)
    }
    if (showPitchSheet) {
        PitchSliderSheet(currentPitch = pitchCurrent, onSetPitch = onSetPitch, onDismiss = onDismissPitch)
    }
    if (showEqualizerSheet) {
        EqualizerSheet(onDismiss = onDismissEqualizer)
    }
}

// ===== BottomSheet-based Dialogs =====

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongDetailBottomSheet(uiState: PlayerUiState, onDismiss: () -> Unit) {
    val song = uiState.currentSong ?: return
    var info by remember { mutableStateOf<List<Pair<String,String>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(song.id) {
        try {
            if (song.neteaseId > 0) {
                val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.bilimusic.data.api.netease.NeteaseApiClient.getSongDetail(listOf(song.neteaseId)) }
                val s = com.bilimusic.data.api.netease.NeteaseSongParser.parseSongDetail(r).firstOrNull()
                if (s != null) {
                    val items = mutableListOf("歌曲" to s.name, "歌手" to s.artistName)
                    if (s.album != null) items.add("专辑" to s.album.name)
                    if (s.genre.isNotBlank()) items.add("曲风" to s.genre)
                    if (s.language.isNotBlank()) items.add("语种" to s.language)
                    if (s.pop > 0) items.add("热度" to "${s.pop.toInt()}%")
                    info = items
                }
            }
        } catch (_: Exception) { info = listOf("歌曲" to song.title) }
        loading = false
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(24.dp).padding(bottom = 32.dp)) {
            Text("歌曲详情", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            if (loading) Box(Modifier.fillMaxWidth().height(100.dp)) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
            else Column { info.forEach { (l, v) -> Text("$l: $v", modifier = Modifier.padding(vertical = 4.dp)) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LyricEditBottomSheet(lyrics: List<com.bilimusic.data.api.LyricLine>, onSave: (List<com.bilimusic.data.api.LyricLine>) -> Unit, onDismiss: () -> Unit) {
    var txt by remember(lyrics) { mutableStateOf(lyrics.joinToString("\n") { "${it.timeMs}:${it.text}" }) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(24.dp).padding(bottom = 32.dp)) {
            Text("歌词编辑", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text("格式: 时间戳:文本 每行一句", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = txt, onValueChange = { txt = it }, modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp), maxLines = 15)
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                val nl = txt.lines().filter { it.isNotBlank() }.mapNotNull { ln -> val ci = ln.indexOf(':'); if (ci > 0) { val t = ln.substring(0, ci).toLongOrNull(); val text = ln.substring(ci + 1); if (t != null) com.bilimusic.data.api.LyricLine(timeMs = t, text = text) else null } else null }
                if (nl.isNotEmpty()) onSave(nl)
            }, modifier = Modifier.fillMaxWidth()) { Text("保存") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QualityBottomSheet(current: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val opts = listOf(
        "standard" to "标准",
        "higher" to "较高",
        "exhigh" to "极高",
        "lossless" to "无损",
        "hires" to "Hi-Res",
        "jyeffect" to "高清环绕声",
        "sky" to "沉浸环绕声",
        "jymaster" to "超清母带"
    )
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(24.dp).padding(bottom = 32.dp)) {
            Text("音质选项", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            opts.forEach { (v, l) ->
                Row(Modifier.fillMaxWidth().clickable { onSelect(v); onDismiss() }.padding(vertical = 12.dp)) {
                    RadioButton(selected = current == v, onClick = { onSelect(v); onDismiss() })
                    Spacer(Modifier.width(12.dp))
                    Text(l, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimerBottomSheet(isActive: Boolean, remaining: Long, onSelect: (Long) -> Unit, onClear: () -> Unit, onDismiss: () -> Unit) {
    val opts = listOf(15L*60*1000 to "15分钟", 30L*60*1000 to "30分钟", 45L*60*1000 to "45分钟", 60L*60*1000 to "60分钟", 90L*60*1000 to "90分钟", 120L*60*1000 to "120分钟")
    val context = androidx.compose.ui.platform.LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(24.dp).padding(bottom = 32.dp)) {
            Text("定时关闭", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (isActive) { Text("剩余 ${String.format("%02d:%02d", remaining/60000, (remaining%60000)/1000)}", style = MaterialTheme.typography.bodyLarge); Spacer(Modifier.height(8.dp)) }
            if (isActive) { TextButton(onClick = { onClear(); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text("关闭定时", Modifier.weight(1f)) }; HorizontalDivider() }
            opts.forEach { (m, l) -> TextButton(onClick = { onSelect(m); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text(l, Modifier.weight(1f)) } }
            HorizontalDivider()
            TextButton(onClick = {
                android.app.TimePickerDialog(context, { _, h, min ->
                    val millis = (h * 60L + min) * 60 * 1000L
                    if (millis > 0) { onSelect(millis); onDismiss() }
                }, 0, 0, true).show()
            }, modifier = Modifier.fillMaxWidth()) { Text("自定义...", Modifier.weight(1f)) }
        }
    }
}

// ===== Equalizer Sheet =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EqualizerSheet(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var bands by remember { mutableStateOf<List<Triple<Int,Int,Int>>>(emptyList()) }
    var enabled by remember { mutableStateOf(true) }
    var selectedPreset by remember { mutableStateOf(com.bilimusic.player.model.EqualizerPresetId.FLAT) }

    LaunchedEffect(Unit) {
        // Get eq from our player singleton - simplified
        bands = listOf(
            Triple(0, 60, 0), Triple(1, 230, 0), Triple(2, 910, 0),
            Triple(3, 3600, 0), Triple(4, 14000, 0)
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(24.dp).padding(bottom = 32.dp)) {
            Text("均衡器", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            // Presets row
            Text("预设", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            val rows = com.bilimusic.player.model.EqualizerPresets.chunked(5)
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { preset ->
                        FilterChip(
                            selected = selectedPreset == preset.id,
                            onClick = { selectedPreset = preset.id },
                            label = { Text(preset.label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("频段", style = MaterialTheme.typography.titleSmall)
            bands.forEach { (index, freq, level) ->
                val freqLabel = if (freq < 1000) "${freq}Hz" else "${freq/1000}kHz"
                var sliderValue by remember { mutableFloatStateOf(0f) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(freqLabel, Modifier.width(52.dp), style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = -1f..1f,
                        modifier = Modifier.weight(1f)
                    )
                    Text("%+ddB".format((sliderValue * 15).toInt()), Modifier.width(40.dp),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

// ===== Speed/Pitch sheets =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeedSliderSheet(currentSpeed: Float, onSetSpeed: (Float) -> Unit, onDismiss: () -> Unit) {
    var s by remember { mutableFloatStateOf(currentSpeed) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(24.dp).padding(bottom = 32.dp)) {
            Text("倍速播放", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Text(String.format("%.1f", s) + "x", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Slider(value = s, onValueChange = { s = kotlin.math.round(it * 10f) / 10f; onSetSpeed(s) }, valueRange = 0.1f..5.0f, steps = 49, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("0.1x"); Text("5.0x") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PitchSliderSheet(currentPitch: Float, onSetPitch: (Float) -> Unit, onDismiss: () -> Unit) {
    var p by remember { mutableFloatStateOf(currentPitch) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(24.dp).padding(bottom = 32.dp)) {
            Text("音调调节", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Text(String.format("%.1f", p), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Slider(value = p, onValueChange = { p = kotlin.math.round(it * 10f) / 10f; onSetPitch(p) }, valueRange = 0.1f..5.0f, steps = 49, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("0.1"); Text("5.0") }
        }
    }
}

@Composable
private fun LyricsView(
    lyrics: List<com.bilimusic.data.api.LyricLine>,
    currentIndex: Int,
    currentPosition: Long = 0L,
    lyricBlurEnabled: Boolean = true,
    lyricBlurAmount: Float = 8f,
    lyricBlurCurrent: Float = 0f,
    lyricBlurNear: Float = 0f,
    lyricBlurMid: Float = 4f,
    lyricBlurFar: Float = 12f,
    lyricScaleCurrent: Float = 1.15f,
    lyricScaleNear: Float = 1.0f,
    lyricMaxWidth: Float = 0.9f,
    translatedLyrics: List<com.bilimusic.data.api.LyricLine> = emptyList(),
    onLyricClick: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier,
    textAlign: androidx.compose.ui.text.style.TextAlign = androidx.compose.ui.text.style.TextAlign.Center,
    fontSize: androidx.compose.ui.unit.TextUnit = 18.sp
) {
    val listState = rememberLazyListState()
    var isAutoScrolling by remember { mutableStateOf(false) }
    // Track whether user has manually scrolled
    var hasManualScrolled by remember { mutableStateOf(false) }

    // Smooth animated scroll to current lyric (from NeriPlayer AppleMusicLyric)
    LaunchedEffect(currentIndex, lyrics.size) {
        if (currentIndex >= 0 && currentIndex < lyrics.size && !listState.isScrollInProgress) {
            isAutoScrolling = true
            hasManualScrolled = false // auto-recover resets manual flag
            try {
                listState.animateScrollToItem(currentIndex)
            } finally {
                isAutoScrolling = false
            }
        }
    }

    // Detect manual scroll start: once user drags, set flag until auto-scroll recovers
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress && !isAutoScrolling) {
            hasManualScrolled = true
        }
    }

    val isUserInteracting by remember {
        derivedStateOf { listState.isScrollInProgress && !isAutoScrolling }
    }
    // Clear text ONLY while actively dragging
    val shouldUseClearText = isUserInteracting

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopStart
    ) {
        val centerPad = maxHeight / 2.5f

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.Start,
            contentPadding = PaddingValues(top = centerPad, bottom = centerPad)
        ) {
        itemsIndexed(lyrics) { index, line ->
            val isCurrent = index == currentIndex
            val distance = kotlin.math.abs(index - currentIndex)

            // Smooth scale animation for active/inactive lines (from NeriPlayer)
            val targetScale = if (isCurrent) lyricScaleCurrent else {
                when {
                    distance <= 1 -> lyricScaleNear
                    distance <= 2 -> (lyricScaleNear * 0.95f).coerceAtMost(lyricScaleNear)
                    distance <= 3 -> 0.90f
                    else -> 0.85f
                }
            }
            val animScale by animateFloatAsState(
                targetValue = targetScale,
                animationSpec = tween(300, easing = FastOutSlowInEasing),
                label = "lyric_scale"
            )

            // Smooth alpha animation
            val targetAlpha = when {
                currentIndex < 0 -> if (index == 0) 1f else 0.3f
                isCurrent -> 1f
                distance <= 2 -> 0.6f
                distance <= 4 -> 0.4f
                else -> 0.25f
            }
            val animAlpha by animateFloatAsState(
                targetValue = targetAlpha,
                animationSpec = tween(350),
                label = "lyric_alpha"
            )

            // Blur:
            // - When auto-scrolling/playing: blur based on distance from current line
            // - When user manually scrolls (isUserInteracting): NO blur
            // - After release until auto-recover: NO blur (hasManualScrolled)
            val targetBlurRadius = if (!lyricBlurEnabled || isUserInteracting || hasManualScrolled) 0f else {
                if (lyricBlurAmount <= 0f) 0f else {
                    when {
                        distance == 0 -> lyricBlurCurrent
                        distance <= 2 -> lyricBlurNear
                        distance <= 4 -> lyricBlurMid
                        else -> lyricBlurFar
                    }
                }
            }
            val blurRadiusPx by animateFloatAsState(
                targetValue = targetBlurRadius,
                animationSpec = tween(durationMillis = 400),
                label = "lyric_blur"
            )

            val renderEffect = remember(blurRadiusPx) {
                if (blurRadiusPx > 0.1f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    BlurEffect(blurRadiusPx, blurRadiusPx, TileMode.Clamp)
                } else null
            }

            val textColor = MaterialTheme.colorScheme.onSurface
            val columnWidth = if (lyricMaxWidth > 0f) maxWidth * lyricMaxWidth else maxWidth

            Column(
                modifier = Modifier
                    .width(columnWidth)
                    .graphicsLayer {
                        scaleX = animScale
                        scaleY = animScale
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                        this.alpha = animAlpha
                        if (renderEffect != null) {
                            this.renderEffect = renderEffect
                        }
                    }
                    .padding(vertical = if (isCurrent) 10.dp else 6.dp)
                    .padding(start = 8.dp)
                    .clickable { onLyricClick?.invoke(line.timeMs) },
                horizontalAlignment = Alignment.Start
            ) {
                // Primary lyric text
                Text(
                    text = line.text,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                        fontSize = fontSize
                    ),
                    color = textColor,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                )

                // Translated lyric (combined mode)
                if (translatedLyrics.isNotEmpty() && index < translatedLyrics.size) {
                    val tl = translatedLyrics[index]
                    val isTranslationMatch = kotlin.math.abs(tl.timeMs - line.timeMs) < 2000
                    if (isTranslationMatch && tl.text.isNotBlank()) {
                        Text(
                            text = tl.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor.copy(alpha = (animAlpha * 0.7f).coerceIn(0f, 1f)),
                            textAlign = textAlign,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
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

// ===== 更换音源 BottomSheet =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceSwitchSheet(
    songTitle: String,
    songArtist: String,
    onSelect: (url: String?, bvid: String?, neteaseId: Long, source: String, isLocal: Boolean, localPath: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) } // 0=哔哩哔哩, 1=网易云, 2=本地
    var biliResults by remember { mutableStateOf<List<com.bilimusic.data.model.BilibiliVideo>?>(null) }
    var neteaseResults by remember { mutableStateOf<List<com.bilimusic.data.api.netease.NeteaseSong>?>(null) }
    var loadingBili by remember { mutableStateOf(false) }
    var loadingNetease by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Local audio picker
    val context = androidx.compose.ui.platform.LocalContext.current
    val localAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                val name = cursor?.use {
                    val nameIdx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0 && it.moveToFirst()) it.getString(nameIdx) else "本地音频"
                } ?: "本地音频"
                cursor?.close()
                // Copy to app cache so Media3 can play it
                val cacheFile = java.io.File(context.cacheDir, "source_switch_${System.currentTimeMillis()}_$name")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    cacheFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                onSelect(cacheFile.absolutePath, null, 0L, "LOCAL", true, cacheFile.absolutePath)
            } catch (_: Exception) {}
        }
    }

    // Search Bilibili when tab switches to 0
    LaunchedEffect(tab) {
        if (tab == 0 && biliResults == null) {
            loadingBili = true
            try {
                val results = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.bilimusic.data.api.BilibiliApiClient.searchVideos("$songTitle $songArtist", 1, "totalrank")
                }
                biliResults = results
            } catch (_: Exception) { biliResults = emptyList() }
            loadingBili = false
        }
        if (tab == 1 && neteaseResults == null) {
            loadingNetease = true
            try {
                val resp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.bilimusic.data.api.netease.NeteaseApiClient.searchSongs("$songTitle $songArtist")
                }
                val results = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.bilimusic.data.api.netease.NeteaseSongParser.parseSearchResult(resp)
                }
                neteaseResults = results
            } catch (_: Exception) { neteaseResults = emptyList() }
            loadingNetease = false
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
            Text("更换音源", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
            HorizontalDivider()
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("哔哩哔哩") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("网易云") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("本地") })
            }
            when (tab) {
                0 -> {
                    if (loadingBili) {
                        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    } else if (biliResults.isNullOrEmpty()) {
                        Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { Text("未找到结果") }
                    } else {
                        LazyColumn(Modifier.heightIn(max = 300.dp)) {
                            val videos: List<com.bilimusic.data.model.BilibiliVideo> = biliResults?.take(20) ?: emptyList()
                            items(videos, key = { it.bvid }) { video ->
                                ListItem(
                                    headlineContent = { Text(video.title, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                                    supportingContent = { Text(video.author) },
                                    leadingContent = { Icon(Icons.Filled.PlayArrow, null) },
                                    modifier = Modifier.clickable {
                                        coroutineScope.launch {
                                            try {
                                                val detail = com.bilimusic.data.api.BilibiliApiClient.getVideoDetail(video.bvid)
                                                if (detail != null) {
                                                    val url = com.bilimusic.data.api.BilibiliApiClient.getAudioUrl(video.bvid, detail.cid)
                                                    if (url != null) onSelect(url, video.bvid, 0L, "BILIBILI", false, null)
                                                }
                                            } catch (_: Exception) {}
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                1 -> {
                    if (loadingNetease) {
                        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    } else if (neteaseResults.isNullOrEmpty()) {
                        Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { Text("未找到结果") }
                    } else {
                        LazyColumn(Modifier.heightIn(max = 300.dp)) {
                            val songs: List<com.bilimusic.data.api.netease.NeteaseSong> = neteaseResults?.take(20) ?: emptyList()
                            items(songs, key = { it.id }) { song ->
                                ListItem(
                                    headlineContent = { Text(song.name, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                                    supportingContent = { Text(song.artistName) },
                                    leadingContent = { Icon(Icons.Filled.MusicNote, null) },
                                    modifier = Modifier.clickable {
                                        coroutineScope.launch {
                                            try {
                                                val resp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                    com.bilimusic.data.api.netease.NeteaseApiClient.getSongUrl(song.id)
                                                }
                                                val url = com.bilimusic.data.api.netease.NeteaseSongParser.parseSongUrlRaw(resp)
                                                if (url != null && url != "__VIP_ONLY__") onSelect(url, null, song.id, "NETEASE", false, null)
                                            } catch (_: Exception) {}
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                2 -> {
                    Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        Button(onClick = { localAudioLauncher.launch(arrayOf("audio/*")) }) {
                            Icon(Icons.Filled.FolderOpen, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("选择本地音频文件")
                        }
                    }
                }
            }
        }
    }
}
