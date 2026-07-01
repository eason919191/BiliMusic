package com.bilimusic.ui.screens.netease

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bilimusic.data.api.BilibiliApiClient
import com.bilimusic.data.api.netease.NeteaseApiClient
import com.bilimusic.data.api.netease.NeteaseSong
import com.bilimusic.data.api.netease.NeteasePlaylist
import com.bilimusic.data.api.netease.NeteaseSongParser
import com.bilimusic.data.model.Music
import com.bilimusic.data.preferences.AppPreferences
import com.bilimusic.player.MusicPlayer
import com.bilimusic.ui.components.BiliAsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun NeteaseScreen(
    viewModel: NeteaseViewModel = hiltViewModel(),
    musicPlayer: MusicPlayer? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val webLoginLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == -1) {
            val cookieJson = result.data?.getStringExtra(NeteaseWebLoginActivity.RESULT_COOKIE)
            if (cookieJson != null) {
                scope.launch { AppPreferences(context).setNeteaseCookie(cookieJson) }
            }
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); viewModel.clearError() }
    }

    val playSong: (NeteaseSong) -> Unit = { song ->
        if (musicPlayer != null) {
            scope.launch {
                try {
                    val resp = withContext(Dispatchers.IO) { NeteaseApiClient.getSongUrl(song.id) }
                    val url = NeteaseSongParser.parseSongUrlRaw(resp)
                    if (url == "__VIP_ONLY__" || url == null || url.isBlank()) {
                        val msg = if (url == "__VIP_ONLY__") "网易云无完整音源" else "网易云无音源"
                        Toast.makeText(context, "$msg，自动切换到B站", Toast.LENGTH_SHORT).show()
                        fallbackToBilibili(song, musicPlayer, context)
                    } else {
                        val music = Music(
                            id = "netease_${song.id}", title = song.name, artist = song.artistName,
                            coverUrl = song.coverUrl, duration = song.duration / 1000L, url = url,
                            source = "NETEASE", neteaseId = song.id
                        )
                        musicPlayer.playSongList(listOf(music), 0)
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "播放失败，自动切换到B站", Toast.LENGTH_SHORT).show()
                    fallbackToBilibili(song, musicPlayer, context)
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = uiState.isLoggedIn,
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) },
            label = "netease_login"
        ) { loggedIn ->
            if (!loggedIn) {
                NeteaseLoginContent(uiState, viewModel, webLoginLauncher)
            } else {
                NeteaseMainContent(uiState, viewModel, playSong)
            }
        }
    }
}

@Composable
private fun NeteaseLoginContent(
    uiState: NeteaseUiState,
    viewModel: NeteaseViewModel,
    webLoginLauncher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.MusicNote, null, Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("网易云音乐", style = MaterialTheme.typography.headlineMedium)
        Text("登录以同步歌单和获取高品质音源", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = uiState.loginMode == 1, onClick = { viewModel.setLoginMode(1) }, label = { Text("验证码登录") })
            FilterChip(selected = uiState.loginMode == 0, onClick = { viewModel.setLoginMode(0) }, label = { Text("密码登录") })
        }
        Spacer(Modifier.height(16.dp))

        if (uiState.loginMode == 1) {
            OutlinedTextField(value = uiState.loginPhone, onValueChange = { viewModel.setLoginPhone(it) }, label = { Text("手机号") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = uiState.loginCaptcha, onValueChange = { viewModel.setLoginCaptcha(it) },
                    label = { Text("验证码") }, singleLine = true, modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { viewModel.sendCaptcha() }, enabled = uiState.loginPhone.isNotBlank() && uiState.countdown == 0, modifier = Modifier.height(56.dp)) {
                    Text(if (uiState.countdown > 0) "${uiState.countdown}s" else "发送")
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = { viewModel.loginWithCaptcha() }, enabled = !uiState.isLoggingIn, modifier = Modifier.fillMaxWidth()) {
                if (uiState.isLoggingIn) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("登录")
            }
        } else {
            OutlinedTextField(value = uiState.loginPhone, onValueChange = { viewModel.setLoginPhone(it) }, label = { Text("手机号") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = uiState.loginPassword, onValueChange = { viewModel.setLoginPassword(it) }, label = { Text("密码") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            Button(onClick = { viewModel.loginWithPhonePassword() }, enabled = !uiState.isLoggingIn, modifier = Modifier.fillMaxWidth()) {
                if (uiState.isLoggingIn) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("登录")
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("或", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = {
            val intent = android.content.Intent(context, NeteaseWebLoginActivity::class.java)
            webLoginLauncher.launch(intent)
        }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Language, null)
            Spacer(Modifier.width(8.dp))
            Text("网页扫码登录（备选）")
        }
    }
}

@Composable
private fun NeteaseMainContent(
    uiState: NeteaseUiState,
    viewModel: NeteaseViewModel,
    onPlaySong: (NeteaseSong) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("网易云音乐", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (uiState.nickname.isNotBlank()) {
                Text(uiState.nickname, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
            }
            IconButton(onClick = { viewModel.logout() }) { Icon(Icons.Outlined.Logout, "退出") }
        }

        OutlinedTextField(
            value = uiState.query,
            onValueChange = { viewModel.onQueryChange(it) },
            placeholder = { Text("搜索网易云音乐、歌手、专辑") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (uiState.isSearching) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else IconButton(onClick = { viewModel.search() }) { Icon(Icons.Filled.Send, "搜索") }
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )

        if (uiState.isShowingPlaylistDetail) {
            PlaylistDetailContent(
                playlistName = uiState.playlists.find { it.id == uiState.selectedPlaylistId }?.name ?: "",
                songs = uiState.playlistSongs,
                isLoading = uiState.isLoadingPlaylist,
                onBack = { viewModel.hidePlaylistDetail() },
                onPlaySong = onPlaySong
            )
        } else {
            var tab by remember { mutableIntStateOf(0) }
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("搜索结果") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("我的歌单") })
            }
            when (tab) {
                0 -> SearchResultList(uiState.searchResults, onPlaySong)
                1 -> PlaylistList(uiState.playlists) { viewModel.showPlaylistDetail(it) }
            }
        }
    }
}

@Composable
private fun SearchResultList(results: List<NeteaseSong>, onPlaySong: (NeteaseSong) -> Unit) {
    if (results.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Search, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                Spacer(Modifier.height(8.dp))
                Text("搜索歌曲、歌手、专辑", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(results, key = { _, s -> s.id }) { _, song ->
            ListItem(
                headlineContent = {
                    Column {
                        Text(song.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                        Text(song.artistName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (song.album != null) {
                            Text(song.album.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                    }
                },
                leadingContent = {
                    if (song.coverUrl != null) {
                        BiliAsyncImage(model = song.coverUrl, contentDescription = "封面",
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop)
                    } else Box(Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.MusicNote, null, Modifier.size(24.dp))
                    }
                },
                trailingContent = { IconButton(onClick = { onPlaySong(song) }) { Icon(Icons.Filled.PlayArrow, "播放", tint = MaterialTheme.colorScheme.primary) } },
                modifier = Modifier.clickable { onPlaySong(song) }
            )
        }
    }
}

@Composable
private fun PlaylistList(playlists: List<NeteasePlaylist>, onClick: (Long) -> Unit) {
    if (playlists.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("没有歌单", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(playlists, key = { it.id }) { pl ->
            ListItem(
                headlineContent = { Text(pl.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text("${pl.songCount} 首 · ${pl.nickname}") },
                leadingContent = {
                    if (pl.coverUrl != null) BiliAsyncImage(model = pl.coverUrl, contentDescription = "封面",
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                    else Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.QueueMusic, null, Modifier.size(28.dp))
                    }
                },
                modifier = Modifier.clickable { onClick(pl.id) }
            )
        }
    }
}

@Composable
private fun PlaylistDetailContent(
    playlistName: String, songs: List<NeteaseSong>, isLoading: Boolean,
    onBack: () -> Unit, onPlaySong: (NeteaseSong) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "返回") }
            Text(playlistName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("${songs.size} 首", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (isLoading) {
            Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
        } else if (songs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("歌单为空") }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
                    ListItem(
                        headlineContent = {
                            Column {
                                Text(song.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(song.artistName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        leadingContent = {
                            if (song.coverUrl != null) BiliAsyncImage(model = song.coverUrl, contentDescription = "封面",
                                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop)
                            else Icon(Icons.Filled.MusicNote, null)
                        },
                        trailingContent = { IconButton(onClick = { onPlaySong(song) }) { Icon(Icons.Filled.PlayArrow, "播放", Modifier.size(20.dp)) } },
                        modifier = Modifier.clickable { onPlaySong(song) }
                    )
                }
            }
        }
    }
}

private suspend fun fallbackToBilibili(song: NeteaseSong, musicPlayer: MusicPlayer, context: android.content.Context) {
    val keyword = "${song.name} ${song.artistName}"
    val results = withContext(Dispatchers.IO) { BilibiliApiClient.searchVideos(keyword, 1, "totalrank") }
    val best = results.firstOrNull() ?: return
    val detail = withContext(Dispatchers.IO) { BilibiliApiClient.getVideoDetail(best.bvid) } ?: return
    val streamUrl = withContext(Dispatchers.IO) { BilibiliApiClient.getAudioUrl(best.bvid, detail.cid) } ?: return
    val music = Music(
        id = best.bvid, title = song.name, artist = song.artistName,
        coverUrl = song.coverUrl ?: best.coverUrl, duration = song.duration,
        url = streamUrl, bvid = best.bvid, source = "NETEASE", neteaseId = song.id
    )
    musicPlayer.playSongList(listOf(music), 0)
}
