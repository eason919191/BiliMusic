package com.bilimusic.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.viewinterop.AndroidView
import com.bilimusic.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCookieDialog by remember { mutableStateOf(false) }
    var showSearchSortDialog by remember { mutableStateOf(false) }
    var showBiliLoginWebView by remember { mutableStateOf(false) }
    // Geetest状态（全屏，不在弹窗内）
    var showCaptcha by remember { mutableStateOf(false) }
    var captchaUrl by remember { mutableStateOf("") }
    var captchaPhone by remember { mutableStateOf("") }
    var showTimerDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showProgressStyleDialog by remember { mutableStateOf(false) }
    var showPageTransitionDialog by remember { mutableStateOf(false) }
    var showFolderSelectDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "设置",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // ===== 播放设置 =====
        item {
            SettingsGroupHeader("播放设置")
        }

        item {
            SettingsSliderItem(
                icon = Icons.Outlined.BlurOn,
                title = "模糊程度",
                subtitle = "${uiState.blurDegree.toInt()}%",
                value = uiState.blurDegree / 100f,
                onValueChange = { viewModel.setBlurDegree(it * 100f) }
            )
        }

        item {
            SettingsItem(
                icon = Icons.Outlined.ShowChart,
                title = "进度条样式",
                subtitle = uiState.progressBarStyle.displayName,
                onClick = { showProgressStyleDialog = true }
            )
            val pageTransitions = mapOf(
                "slide" to "滑动", "fade" to "淡入淡出",
                "scale" to "缩放", "default" to "默认"
            )
            SettingsItem(
                icon = Icons.Outlined.Animation,
                title = "页面过渡动画",
                subtitle = pageTransitions[uiState.pageTransition] ?: "滑动",
                onClick = { showPageTransitionDialog = true }
            )
        }

        item {
            SettingsSwitchItem(
                icon = Icons.Outlined.ColorLens,
                title = "纯色背景",
                subtitle = "关闭后使用模糊背景",
                checked = uiState.playerBgPureColor,
                onCheckedChange = { viewModel.setPlayerBgPureColor(it) }
            )
        }

        // ===== 外观 =====
        item {
            SettingsGroupHeader("外观")
        }

        item {
            SettingsItem(
                icon = Icons.Outlined.Palette,
                title = "主题色",
                subtitle = when {
                    uiState.useDynamicColor -> "动态取色"
                    else -> "自定义"
                },
                onClick = { showThemeDialog = true }
            )
        }

        item {
            SettingsClickableItem(
                icon = Icons.Outlined.DarkMode,
                title = "深色模式",
                subtitle = when (uiState.themeMode) {
                    ThemeMode.SYSTEM -> "跟随系统"
                    ThemeMode.LIGHT -> "浅色"
                    ThemeMode.DARK -> "深色"
                }
            ) {
                // Quick toggle cycling through modes
                when (uiState.themeMode) {
                    ThemeMode.SYSTEM -> viewModel.setThemeMode(ThemeMode.LIGHT)
                    ThemeMode.LIGHT -> viewModel.setThemeMode(ThemeMode.DARK)
                    ThemeMode.DARK -> viewModel.setThemeMode(ThemeMode.SYSTEM)
                }
            }
        }

        // ===== 账号管理 =====
        item {
            SettingsGroupHeader("账号管理")
        }

        item {
            SettingsItem(
                icon = Icons.Outlined.AccountCircle,
                title = "哔哩哔哩账号",
                subtitle = if (uiState.isLoggedIn) {
                    uiState.userInfo?.let { "${it.nickname} (Lv.${it.level})" } ?: "已登录"
                } else "未登录",
                onClick = { showCookieDialog = true }
            )
        }

        // 已登录显示用户信息
        if (uiState.isLoggedIn && uiState.userInfo != null) {
            item {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth()) {
                    // Avatar
                    if (uiState.userInfo!!.avatar.isNotBlank()) {
                        com.bilimusic.ui.components.BiliAsyncImage(
                            model = uiState.userInfo!!.avatar,
                            contentDescription = "头像",
                            modifier = Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(uiState.userInfo!!.nickname, style = MaterialTheme.typography.titleSmall)
                        Text("UID: ${uiState.userInfo!!.uid}  Lv.${uiState.userInfo!!.level}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (uiState.isLoggedIn) {
            item {
                SettingsItem(
                    icon = Icons.Outlined.Bookmark,
                    title = "导入收藏夹",
                    subtitle = "将B站收藏夹导入为歌单",
                    onClick = {
                        viewModel.loadFavoriteFolders()
                        showFolderSelectDialog = true
                    }
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Outlined.Logout,
                    title = "退出登录",
                    subtitle = "清除Cookie",
                    onClick = { viewModel.logoutBilibili() }
                )
            }
        }

        // ===== 音乐管理 =====
        item {
            SettingsGroupHeader("音乐管理")
        }

        item {
            SettingsItem(
                icon = Icons.Outlined.LibraryMusic,
                title = "导入本地音乐",
                subtitle = if (uiState.isImportingLocal) "导入中..." else "扫描设备中的音乐文件",
                onClick = { viewModel.importLocalMusic() },
                enabled = !uiState.isImportingLocal
            )
        }

        item {
            val launcher = rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
            ) { uri ->
                if (uri != null) {
                    // Convert content URI to file path
                    val path = uri.path?.let { p ->
                        // SAF returns content:// URIs, extract the real path
                        p.replace("/tree/primary:", "/storage/emulated/0/")
                            ?: ""
                    } ?: ""
                    viewModel.setDownloadPath(path)
                }
            }
            SettingsItem(
                icon = Icons.Outlined.Folder,
                title = "下载位置",
                subtitle = uiState.downloadPath.ifBlank { "Music/BiliMusic" },
                onClick = { launcher.launch(null) }
            )
            if (uiState.downloadPath.isNotBlank()) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = uiState.downloadPath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { viewModel.setDownloadPath("") }) {
                        Text("恢复默认", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // ===== 定时关闭 =====
        item {
            SettingsGroupHeader("定时关闭")
        }

        item {
            SettingsItem(
                icon = Icons.Outlined.Timer,
                title = "定时关闭",
                subtitle = if (uiState.isTimerActive) {
                    val min = uiState.sleepTimerRemaining / 60000
                    val sec = (uiState.sleepTimerRemaining % 60000) / 1000
                    "剩余 ${String.format("%02d:%02d", min, sec)}"
                } else "关闭",
                onClick = { showTimerDialog = true }
            )
        }

        // ===== 搜索 =====
        item { SettingsGroupHeader("搜索") }
        item {
            SettingsItem(
                icon = Icons.Outlined.Sort,
                title = "排序方式",
                subtitle = searchSortDisplay(uiState.searchSort),
                onClick = { showSearchSortDialog = true }
            )
        }
        item { SettingsGroupHeader("搜索筛选") }
        item {
            SettingsSwitchItem(
                icon = Icons.Outlined.FilterList,
                title = "过滤超长视频",
                subtitle = "默认隐藏10分钟以上的视频",
                checked = uiState.filterLongVideos,
                onCheckedChange = { viewModel.setFilterLongVideos(it) }
            )
        }
        item {
            SettingsSwitchItem(
                icon = Icons.Outlined.Loop,
                title = "过滤\"循环\"标题",
                subtitle = "默认隐藏标题含\"循环\"的视频",
                checked = uiState.filterLoopTitle,
                onCheckedChange = { viewModel.setFilterLoopTitle(it) }
            )
        }
        item {
            var showKwDialog by remember { mutableStateOf(false) }
            var kwText by remember { mutableStateOf(uiState.filterKeywords) }
            SettingsItem(
                icon = Icons.Outlined.Block,
                title = "自定义过滤词",
                subtitle = if (uiState.filterKeywords.isBlank()) "点击设置，用|分隔多个词" else uiState.filterKeywords,
                onClick = { kwText = uiState.filterKeywords; showKwDialog = true }
            )
            if (showKwDialog) {
                AlertDialog(onDismissRequest = { showKwDialog = false }, title = { Text("自定义过滤词") },
                    text = { OutlinedTextField(value = kwText, onValueChange = { kwText = it }, label = { Text("过滤词（|分隔）") }, modifier = Modifier.fillMaxWidth()) },
                    confirmButton = { TextButton(onClick = { viewModel.setFilterKeywords(kwText); showKwDialog = false }) { Text("确定") } },
                    dismissButton = { TextButton(onClick = { showKwDialog = false }) { Text("取消") } })
            }
        }

        // ===== 关于 =====
        item {
            SettingsGroupHeader("关于")
        }

        item {
            SettingsItem(
                icon = Icons.Outlined.Info,
                title = "关于 BiliMusic",
                subtitle = "v1.0.0",
                onClick = {}
            )
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // ===== Dialogs =====

    if (showCookieDialog) {
        CookieDialog(
            currentCookie = uiState.bilibiliCookie,
            onConfirm = { cookie ->
                viewModel.loginWithCookie(cookie)
                showCookieDialog = false
            },
            onDismiss = { showCookieDialog = false },
            // SMS captcha callbacks
            onCaptchaNeeded = { url, phone ->
                showCookieDialog = false
                captchaUrl = url; captchaPhone = phone
                showCaptcha = true
            },
            onWebLogin = {
                showBiliLoginWebView = true
            }
        )
    }

    // 全屏滑块验证（根层级，不被弹窗限制）
    if (showCaptcha) {
        val scope = rememberCoroutineScope()
        SimpleCaptchaWebView(
            url = captchaUrl, phone = captchaPhone,
            onDismiss = { showCaptcha = false },
            onSmsSent = {
                showCaptcha = false
                scope.launch {
                    val (ok, msg) = com.bilimusic.data.api.BilibiliLoginClient.sendSmsCode(captchaPhone)
                    android.util.Log.d("SMS", "After captcha: ok=$ok msg=$msg")
                    if (ok) {
                        showCookieDialog = true  // 重新打开登录弹窗
                    }
                }
            }
        )
    }

    if (showSearchSortDialog) {
        SimpleSelectDialog("排序方式", listOf("totalrank" to "综合", "click" to "播放量", "stow" to "收藏数", "dm" to "弹幕数", "pubdate" to "发布日期"), uiState.searchSort,
            onSelect = { viewModel.setSearchSort(it); showSearchSortDialog = false },
            onDismiss = { showSearchSortDialog = false })
    }

    if (showBiliLoginWebView) {
        BiliLoginWebView(
            onDismiss = { showBiliLoginWebView = false },
            onLoginSuccess = { cookie ->
                showBiliLoginWebView = false
                viewModel.loginWithCookie(cookie)
            }
        )
    }

    if (showTimerDialog) {
        TimerDialog(
            onSelect = { millis ->
                viewModel.setSleepTimer(millis)
                showTimerDialog = false
            },
            onClear = {
                viewModel.clearSleepTimer()
                showTimerDialog = false
            },
            onDismiss = { showTimerDialog = false }
        )
    }

    if (showThemeDialog) {
        ThemeDialog(
            useDynamicColor = uiState.useDynamicColor,
            onToggleDynamic = { viewModel.setUseDynamicColor(it) },
            onSelectColor = { viewModel.setSeedColor(it) },
            onDismiss = { showThemeDialog = false }
        )
    }

    if (showProgressStyleDialog) {
        ProgressStyleDialog(
            currentStyle = uiState.progressBarStyle,
            onSelect = {
                viewModel.setProgressBarStyle(it)
                showProgressStyleDialog = false
            },
            onDismiss = { showProgressStyleDialog = false }
        )
    }

    if (showPageTransitionDialog) {
        SimpleSelectDialog(
            title = "页面过渡动画",
            options = listOf("slide" to "滑动", "fade" to "淡入淡出", "scale" to "缩放", "default" to "默认"),
            current = uiState.pageTransition,
            onSelect = {
                viewModel.setPageTransition(it)
                showPageTransitionDialog = false
            },
            onDismiss = { showPageTransitionDialog = false }
        )
    }

    if (showFolderSelectDialog) {
        FolderSelectDialog(
            folders = uiState.favoriteFolders,
            isLoading = uiState.isLoadingFolders,
            onSelect = { folderId ->
                viewModel.importFavoriteFolder(folderId)
                showFolderSelectDialog = false
            },
            onDismiss = { showFolderSelectDialog = false }
        )
    }

    // Error snackbar
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearError()
        }
    }
}

// ===== Settings Components =====

@Composable
private fun SettingsGroupHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 12.dp)
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        modifier = Modifier.clickable(enabled = enabled) { onClick() }
    )
}

@Composable
private fun SettingsClickableItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle, color = MaterialTheme.colorScheme.primary) },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        modifier = Modifier.clickable { onClick() }
    )
}

@Composable
private fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    )
}

@Composable
private fun SettingsSliderItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.padding(start = 40.dp)
        )
    }
}

// ===== Dialogs =====

@Composable
private fun CookieDialog(
    currentCookie: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    onCaptchaNeeded: ((String, String) -> Unit)? = null,
    onWebLogin: (() -> Unit)? = null
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("扫码", "短信")
    val scope = rememberCoroutineScope()

    // Password state
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // SMS state
    var phone by remember { mutableStateOf("") }
    var smsCode by remember { mutableStateOf("") }
    var smsCaptchaKey by remember { mutableStateOf("") }
    var smsCountdown by remember { mutableIntStateOf(0) }
    var smsMsg by remember { mutableStateOf("") }
    var smsLoading by remember { mutableStateOf(false) }
    var showQR by remember { mutableStateOf(false) }
    var showGeetest by remember { mutableStateOf(false) }
    var captchaUrl by remember { mutableStateOf("") }
    var geetestGt by remember { mutableStateOf("") }
    var geetestChallenge by remember { mutableStateOf("") }
    var geetestToken by remember { mutableStateOf("") }

    LaunchedEffect(smsCountdown) {
        if (smsCountdown > 0) { kotlinx.coroutines.delay(1000); smsCountdown-- }
    }

    if (showQR) {
        QRLoginDialog(onDismiss = { showQR = false }, onLoginSuccess = { cookie -> onConfirm(cookie) })
        return
    }
    if (showGeetest) {
        PiliPlusGeetestDialog(
            gt = geetestGt, challenge = geetestChallenge,
            onDismiss = { showGeetest = false; smsMsg = "已取消" },
            onSuccess = { gc, gv, gs ->
                showGeetest = false
                scope.launch {
                    smsLoading = true; smsMsg = "正在发送..."
                    val (ok, msg) = com.bilimusic.data.api.BilibiliLoginClient.sendSmsWithGeetest(phone, geetestToken, gc, gv, gs)
                    if (ok) { smsCaptchaKey = msg; smsCountdown = 60; smsMsg = "已发送" }
                    else smsMsg = msg
                    smsLoading = false
                }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.AccountCircle, null) },
        title = { Text("B站账号登录") },
        text = {
            Column {
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
                    }
                }
                Spacer(Modifier.height(12.dp))

                when (selectedTab) {
                    0 -> {
                        // 扫码登录
                        val context = androidx.compose.ui.platform.LocalContext.current
                        val vm: com.bilimusic.ui.screens.settings.SettingsViewModel = hiltViewModel()
                        val state by vm.uiState.collectAsState()
                        LaunchedEffect(Unit) { vm.startQRLogin() }
                        val qrUrl = state.qrCodeUrl
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Button(onClick = {
                                if (qrUrl != null) {
                                    try {
                                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                            data = android.net.Uri.parse("bilibili://browser?url=${java.net.URLEncoder.encode(qrUrl!!, "UTF-8")}")
                                            setPackage("tv.danmaku.bili")
                                        })
                                    } catch (_: Exception) {}
                                }
                            }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Filled.OpenInBrowser, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("打开B站App登录")
                            }
                            Spacer(Modifier.height(12.dp))
                            if (state.qrCodeBitmap != null) Image(bitmap = state.qrCodeBitmap!!.asImageBitmap(), contentDescription = "QR", modifier = Modifier.size(180.dp))
                            else CircularProgressIndicator(Modifier.size(180.dp))
                            Text(state.qrCodeMessage.ifBlank { "获取二维码..." }, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    1 -> {
                        Button(onClick = { onDismiss(); onWebLogin?.invoke() }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Language, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("打开B站网页登录")
                        }
                    }
                }
            }
        },
        confirmButton = {
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
private fun BiliLoginWebView(
    onDismiss: () -> Unit,
    onLoginSuccess: (String) -> Unit
) {
    var loading by remember { mutableStateOf(true) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "关闭") }
                Text("B站登录", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = {
                    val cm = android.webkit.CookieManager.getInstance()
                    val cookies = cm.getCookie("https://passport.bilibili.com") ?: ""
                    if (cookies.contains("SESSDATA")) onLoginSuccess(cookies)
                }) { Text("已登录") }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            AndroidView(factory = { ctx ->
                android.webkit.WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                    webViewClient = object : android.webkit.WebViewClient() {
                        override fun onPageFinished(v: android.webkit.WebView?, u: String?) { loading = false }
                    }
                    loadUrl("https://passport.bilibili.com/login?goto=https%3A%2F%2Fwww.bilibili.com%2F")
                }
            }, modifier = Modifier.weight(1f).fillMaxWidth())
        }
    }
}

private fun searchSortDisplay(sort: String) = when(sort) { "click"->"播放量"; "stow"->"收藏数"; "dm"->"弹幕数"; "pubdate"->"发布日期"; else->"综合" }

@Composable
private fun SimpleCaptchaWebView(
    url: String, phone: String, onDismiss: () -> Unit, onSmsSent: () -> Unit
) {
    var webView by remember { mutableStateOf<android.webkit.WebView?>(null) }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.5f)).clickable(
        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
        indication = null, onClick = {}
    )) {
        Column(Modifier.fillMaxSize().padding(32.dp).align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("请完成滑块验证", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(factory = { ctx ->
                    android.webkit.WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        addJavascriptInterface(object {
                            @android.webkit.JavascriptInterface
                            fun onSmsResult(jsonStr: String) {
                                try {
                                    val json = org.json.JSONObject(jsonStr)
                                    val code = json.optInt("code", -1)
                                    android.util.Log.d("CaptchaJS", "SMS via JS: code=$code")
                                    if (code == 0) {
                                        (context as? android.app.Activity)?.runOnUiThread { onSmsSent() }
                                    }
                                } catch (_: Exception) {}
                            }
                        }, "SmsBridge")
                        webViewClient = object : android.webkit.WebViewClient() {}
                        webView = this
                        loadUrl(url)
                    }
                }, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                // 同步WebView cookies后重发短信
                val cm = android.webkit.CookieManager.getInstance()
                val cookies = listOf("https://passport.bilibili.com", "https://bilibili.com", "https://www.bilibili.com", "https://api.bilibili.com")
                    .mapNotNull { cm.getCookie(it) }.joinToString("; ")
                if (cookies.isNotBlank()) {
                    com.bilimusic.data.api.BilibiliApiClient.userCookie = cookies
                }
                onSmsSent()
            }, modifier = Modifier.fillMaxWidth()) { Text("验证完成，发短信") }
        }
        IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
            Icon(Icons.Filled.Close, "关闭", tint = Color.White, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun PiliPlusGeetestDialog(
    gt: String, challenge: String,
    onDismiss: () -> Unit,
    onSuccess: (gc: String, gv: String, gs: String) -> Unit
) {
    var jsCode by remember { mutableStateOf("") }
    var jsUrl by remember { mutableStateOf("https://static.geetest.com/static/js/fullpage.9.2.0-guwyxh.js") }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(gt) {
        withContext(Dispatchers.IO) {
            try {
                val client = com.bilimusic.data.api.BilibiliApiClient.sharedClient()
                val resp = client.newCall(okhttp3.Request.Builder()
                    .url("https://api.geetest.com/gettype.php?gt=$gt")
                    .addHeader("User-Agent", "Mozilla/5.0")
                    .build()).execute()
                val body = resp.body?.string() ?: ""
                if (body.contains("\"status\":\"success\"")) {
                    val jsonStr = body.substringAfter("(").substringBeforeLast(")")
                    val json = org.json.JSONObject(jsonStr)
                    val data = json.getJSONObject("data")
                    // 使用API返回的正确的JS文件路径
                    // 和PiliPlus一样的静态URL（0.0.0会重定向到最新版）
                    jsUrl = "https://static.geetest.com/static/js/fullpage.0.0.0.js"
                    data.put("gt", gt)
                    data.put("challenge", challenge)
                    data.put("offline", false)
                    data.put("new_captcha", true)
                    data.put("product", "bind")
                    data.put("width", "100%")
                    data.put("https", true)
                    data.put("protocol", "https://")
                    jsCode = "new Geetest($data).onSuccess(function(){R('success',this.getValidate())}).onError(function(e){R('error',e)}).onClose(function(){R('close',{})});this.onReady(function(){this.verify()})"
                }
            } catch (_: Exception) {}
        }
        isLoading = false
    }

    // 全屏Dialog（和PiliPlus一样）
    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.5f)).clickable(
        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
        indication = null, onClick = {}
    )) {
        if (isLoading) CircularProgressIndicator(Modifier.align(Alignment.Center).size(48.dp))
        else AndroidView(factory = { ctx ->
            android.webkit.WebView(ctx).apply {
                settings.javaScriptEnabled = true
                setWebChromeClient(object : android.webkit.WebChromeClient() {
                    override fun onConsoleMessage(msg: android.webkit.ConsoleMessage?): Boolean {
                        android.util.Log.d("GeetestJS", msg?.message() ?: ""); return true
                    }
                })
                addJavascriptInterface(object {
                    @android.webkit.JavascriptInterface
                    fun onResult(jsonStr: String) {
                        try {
                            val j = org.json.JSONObject(jsonStr)
                            val gv = j.optString("geetest_validate", "")
                            if (gv.isNotBlank()) {
                                (context as? android.app.Activity)?.runOnUiThread {
                                    onSuccess(j.optString("geetest_challenge", challenge), gv, j.optString("geetest_seccode", ""))
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }, "GeetestBridge")
                webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(v: android.webkit.WebView?, u: String?) {
                        android.util.Log.d("GeetestJS", "Page loaded, injecting: ${jsCode.take(80)}")
                        v?.evaluateJavascript(jsCode, null)
                    }
                }
                loadDataWithBaseURL(null, """
                    <!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
                    <script src="$jsUrl"></script></head><body style="margin:0;padding:0">
                    <script>function R(type,data){if(type==='success')GeetestBridge.onResult(JSON.stringify(data));}</script>
                    </body></html>
                """.trimIndent(), "text/html", "UTF-8", null)
            }
        }, modifier = Modifier.fillMaxSize().padding(32.dp).align(Alignment.Center))
        // 关闭按钮
        IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
            Icon(Icons.Filled.Close, "关闭", tint = Color.White, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun GeetestWebView(
    url: String, onDismiss: () -> Unit, onVerified: () -> Unit
) {
    var loading by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("请完成滑块验证") },
        text = {
            Box(Modifier.fillMaxWidth().height(300.dp)) {
                if (loading) CircularProgressIndicator(Modifier.align(Alignment.Center))
                AndroidView(factory = { ctx ->
                    android.webkit.WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = object : android.webkit.WebViewClient() {
                            override fun onPageFinished(v: android.webkit.WebView?, u: String?) { loading = false }
                        }
                        loadUrl(url)
                    }
                }, modifier = Modifier.fillMaxSize())
            }
        },
        confirmButton = { TextButton(onClick = onVerified) { Text("验证完成") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun QRLoginDialog(
    onDismiss: () -> Unit,
    onLoginSuccess: (String) -> Unit
) {
    val vm: com.bilimusic.ui.screens.settings.SettingsViewModel = hiltViewModel()
    val state by vm.uiState.collectAsState()

    LaunchedEffect(Unit) {
        vm.startQRLogin()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("扫码登录") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (state.qrCodeBitmap != null) {
                    val bitmap = state.qrCodeBitmap!!
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "QR Code",
                        modifier = Modifier.size(256.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                } else {
                    Box(Modifier.size(256.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                Text(
                    state.qrCodeMessage.ifBlank { "获取二维码中..." },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun TimerDialog(
    onSelect: (Long) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val timerOptions = listOf(
        15L * 60 * 1000 to "15分钟",
        30L * 60 * 1000 to "30分钟",
        45L * 60 * 1000 to "45分钟",
        60L * 60 * 1000 to "60分钟",
        90L * 60 * 1000 to "90分钟",
        120L * 60 * 1000 to "120分钟"
    )
    var showCustomDialog by remember { mutableStateOf(false) }
    var customMins by remember { mutableStateOf("") }

    if (showCustomDialog) {
        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            title = { Text("自定义时间") },
            text = {
                OutlinedTextField(
                    value = customMins,
                    onValueChange = { customMins = it.filter { c -> c.isDigit() } },
                    label = { Text("分钟") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val mins = customMins.toLongOrNull()
                    if (mins != null && mins > 0) {
                        onSelect(mins * 60 * 1000)
                        showCustomDialog = false
                    }
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showCustomDialog = false }) { Text("取消") } }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("定时关闭") },
        text = {
            Column {
                TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Close, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("关闭定时", Modifier.weight(1f))
                }
                Divider()
                timerOptions.forEach { (millis, label) ->
                    TextButton(onClick = { onSelect(millis) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Timer, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(label, Modifier.weight(1f))
                    }
                }
                Divider()
                TextButton(onClick = { showCustomDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Edit, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("自定义…", Modifier.weight(1f))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun ThemeDialog(
    useDynamicColor: Boolean,
    onToggleDynamic: (Boolean) -> Unit,
    onSelectColor: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val presetColors = listOf(
        0xFF6750A4.toInt() to "默认紫",
        0xFFE91E63.toInt() to "粉色",
        0xFFFF5722.toInt() to "橙色",
        0xFFFFEB3B.toInt() to "黄色",
        0xFF4CAF50.toInt() to "绿色",
        0xFF2196F3.toInt() to "蓝色",
        0xFF00BCD4.toInt() to "青色",
        0xFF9C27B0.toInt() to "紫色",
        0xFF607D8B.toInt() to "灰蓝",
        0xFF000000.toInt() to "黑白"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("主题色设置") },
        text = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("动态取色（壁纸）", modifier = Modifier.weight(1f))
                    Switch(checked = useDynamicColor, onCheckedChange = onToggleDynamic)
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (!useDynamicColor) {
                    Text(
                        "选择主题色",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // Color grid
                    presetColors.chunked(5).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            row.forEach { (color, name) ->
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(color))
                                        .clickable { onSelectColor(color) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (color == 0xFF000000.toInt()) {
                                        // Show black/white combo
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        }
    )
}

@Composable
private fun SimpleSelectDialog(
    title: String,
    options: List<Pair<String, String>>,
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = current == value,
                            onClick = { onSelect(value) }
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun ProgressStyleDialog(
    currentStyle: ProgressBarStyle,
    onSelect: (ProgressBarStyle) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("进度条样式") },
        text = {
            Column {
                ProgressBarStyle.values().forEach { style ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(style) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = style == currentStyle,
                            onClick = { onSelect(style) }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(style.displayName)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun FolderSelectDialog(
    folders: List<BilibiliFavoriteFolder>,
    isLoading: Boolean,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择收藏夹") },
        text = {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (folders.isEmpty()) {
                Text("未找到收藏夹")
            } else {
                LazyColumn {
                    items(folders) { folder ->
                        ListItem(
                            headlineContent = { Text(folder.title) },
                            supportingContent = { Text("${folder.songCount} 个视频") },
                            modifier = Modifier.clickable { onSelect(folder.id) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
