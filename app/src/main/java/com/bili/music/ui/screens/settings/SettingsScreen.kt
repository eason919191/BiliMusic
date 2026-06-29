package com.bili.music.ui.screens.settings

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.viewinterop.AndroidView
import com.bili.music.data.model.*
import com.bili.music.R
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
    var showAboutDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // ===== 播放设置 =====
        item {
            SettingsGroupHeader(stringResource(R.string.settings_group_playback))
        }

        item {
            SettingsSliderItem(
                icon = Icons.Outlined.BlurOn,
                title = stringResource(R.string.settings_blur_title),
                subtitle = "${uiState.blurDegree.toInt()}%",
                value = uiState.blurDegree / 100f,
                onValueChange = { viewModel.setBlurDegree(it * 100f) }
            )
        }

        item {
            SettingsItem(
                icon = Icons.Outlined.ShowChart,
                title = stringResource(R.string.settings_progress_style),
                subtitle = uiState.progressBarStyle.displayName,
                onClick = { showProgressStyleDialog = true }
            )
            val pageTransitions = mapOf(
                "slide" to stringResource(R.string.transition_slide), "fade" to stringResource(R.string.transition_fade),
                "scale" to stringResource(R.string.transition_scale), "default" to stringResource(R.string.transition_default)
            )
            SettingsItem(
                icon = Icons.Outlined.Animation,
                title = stringResource(R.string.settings_transition),
                subtitle = pageTransitions[uiState.pageTransition] ?: stringResource(R.string.transition_slide),
                onClick = { showPageTransitionDialog = true }
            )
        }

        item {
            SettingsSwitchItem(
                icon = Icons.Outlined.ColorLens,
                title = stringResource(R.string.settings_pure_bg),
                subtitle = stringResource(R.string.settings_pure_bg_desc),
                checked = uiState.playerBgPureColor,
                onCheckedChange = { viewModel.setPlayerBgPureColor(it) }
            )
        }

        // ===== 外观 =====
        item {
            SettingsGroupHeader(stringResource(R.string.settings_group_appearance))
        }

        item {
            SettingsItem(
                icon = Icons.Outlined.Palette,
                title = stringResource(R.string.settings_theme_color),
                subtitle = when {
                    uiState.useDynamicColor -> stringResource(R.string.settings_dynamic_color)
                    else -> stringResource(R.string.settings_theme_custom)
                },
                onClick = { showThemeDialog = true }
            )
        }

        item {
            SettingsClickableItem(
                icon = Icons.Outlined.DarkMode,
                title = stringResource(R.string.settings_dark_mode),
                subtitle = when (uiState.themeMode) {
                    ThemeMode.SYSTEM -> stringResource(R.string.settings_dark_follow)
                    ThemeMode.LIGHT -> stringResource(R.string.settings_dark_light)
                    ThemeMode.DARK -> stringResource(R.string.settings_dark_dark)
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
            SettingsGroupHeader(stringResource(R.string.settings_group_account))
        }

        item {
            SettingsItem(
                icon = Icons.Outlined.AccountCircle,
                title = stringResource(R.string.settings_bili_account),
                subtitle = if (uiState.isLoggedIn) {
                    uiState.userInfo?.let { "${it.nickname} (Lv.${it.level})" } ?: stringResource(R.string.settings_bili_logged_in)
                } else stringResource(R.string.settings_bili_not_logged_in),
                onClick = { showCookieDialog = true }
            )
        }

        // 已登录显示用户信息
        if (uiState.isLoggedIn && uiState.userInfo != null) {
            item {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth()) {
                    // Avatar
                    if (uiState.userInfo!!.avatar.isNotBlank()) {
                        com.bili.music.ui.components.BiliAsyncImage(
                            model = uiState.userInfo!!.avatar,
                            contentDescription = stringResource(R.string.settings_avatar),
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
                    title = stringResource(R.string.settings_import_fav),
                    subtitle = stringResource(R.string.settings_import_fav_desc),
                    onClick = {
                        viewModel.loadFavoriteFolders()
                        showFolderSelectDialog = true
                    }
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Outlined.Logout,
                    title = stringResource(R.string.settings_logout),
                    subtitle = stringResource(R.string.settings_logout_desc),
                    onClick = { viewModel.logoutBilibili() }
                )
            }
        }

        // ===== 音乐管理 =====
        item {
            SettingsGroupHeader(stringResource(R.string.settings_group_music))
        }

        item {
            SettingsItem(
                icon = Icons.Outlined.LibraryMusic,
                title = stringResource(R.string.settings_import_local),
                subtitle = if (uiState.isImportingLocal) stringResource(R.string.settings_import_local_progress) else stringResource(R.string.settings_import_local_desc),
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
                title = stringResource(R.string.settings_download_path),
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
                        Text(stringResource(R.string.settings_download_path_reset), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // ===== 定时关闭 =====
        item {
            SettingsGroupHeader(stringResource(R.string.settings_group_timer))
        }

        item {
            SettingsItem(
                icon = Icons.Outlined.Timer,
                title = stringResource(R.string.settings_timer),
                subtitle = if (uiState.isTimerActive) {
                    val min = uiState.sleepTimerRemaining / 60000
                    val sec = (uiState.sleepTimerRemaining % 60000) / 1000
                    stringResource(R.string.settings_timer_remaining, String.format("%02d:%02d", min, sec))
                } else stringResource(R.string.settings_timer_off),
                onClick = { showTimerDialog = true }
            )
        }

        // ===== 搜索 =====
        item { SettingsGroupHeader(stringResource(R.string.settings_group_search)) }
        item {
            SettingsItem(
                icon = Icons.Outlined.Sort,
                title = stringResource(R.string.settings_search_sort),
                subtitle = when (uiState.searchSort) {
                    "click" -> stringResource(R.string.settings_sort_click)
                    "stow" -> stringResource(R.string.settings_sort_stow)
                    "dm" -> stringResource(R.string.settings_sort_dm)
                    "pubdate" -> stringResource(R.string.settings_sort_pubdate)
                    else -> stringResource(R.string.settings_sort_totalrank)
                },
                onClick = { showSearchSortDialog = true }
            )
        }
        item { SettingsGroupHeader(stringResource(R.string.settings_search_filters)) }
        item {
            SettingsSwitchItem(
                icon = Icons.Outlined.FilterList,
                title = stringResource(R.string.search_filter_long),
                subtitle = stringResource(R.string.search_filter_long_desc),
                checked = uiState.filterLongVideos,
                onCheckedChange = { viewModel.setFilterLongVideos(it) }
            )
        }
        item {
            SettingsSwitchItem(
                icon = Icons.Outlined.Loop,
                title = stringResource(R.string.search_filter_loop),
                subtitle = stringResource(R.string.search_filter_loop_desc),
                checked = uiState.filterLoopTitle,
                onCheckedChange = { viewModel.setFilterLoopTitle(it) }
            )
        }
        item {
            var showKwDialog by remember { mutableStateOf(false) }
            var kwText by remember { mutableStateOf(uiState.filterKeywords) }
            SettingsItem(
                icon = Icons.Outlined.Block,
                title = stringResource(R.string.search_filter_keywords),
                subtitle = if (uiState.filterKeywords.isBlank()) stringResource(R.string.search_filter_keywords_hint) else uiState.filterKeywords,
                onClick = { kwText = uiState.filterKeywords; showKwDialog = true }
            )
            if (showKwDialog) {
                AlertDialog(onDismissRequest = { showKwDialog = false }, title = { Text(stringResource(R.string.search_filter_keywords)) },
                    text = { OutlinedTextField(value = kwText, onValueChange = { kwText = it }, label = { Text(stringResource(R.string.search_filter_keywords_label)) }, modifier = Modifier.fillMaxWidth()) },
                    confirmButton = { TextButton(onClick = { viewModel.setFilterKeywords(kwText); showKwDialog = false }) { Text(stringResource(R.string.common_confirm)) } },
                    dismissButton = { TextButton(onClick = { showKwDialog = false }) { Text(stringResource(R.string.common_cancel)) } })
            }
        }

        // ===== 关于 =====
        item {
            SettingsGroupHeader(stringResource(R.string.settings_group_about))
        }

        item {
            SettingsItem(
                icon = Icons.Outlined.Info,
                title = stringResource(R.string.settings_about),
                subtitle = stringResource(R.string.settings_version),
                onClick = { showAboutDialog = true }
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
                    val (ok, msg) = com.bili.music.data.api.BilibiliLoginClient.sendSmsCode(captchaPhone)
                    android.util.Log.d("SMS", "After captcha: ok=$ok msg=$msg")
                    if (ok) {
                        showCookieDialog = true  // 重新打开登录弹窗
                    }
                }
            }
        )
    }

    if (showSearchSortDialog) {
        SimpleSelectDialog(
            stringResource(R.string.settings_search_sort),
            listOf(
                "totalrank" to stringResource(R.string.settings_sort_totalrank),
                "click" to stringResource(R.string.settings_sort_click),
                "stow" to stringResource(R.string.settings_sort_stow),
                "dm" to stringResource(R.string.settings_sort_dm),
                "pubdate" to stringResource(R.string.settings_sort_pubdate)
            ),
            uiState.searchSort,
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
            onSelectColor = { color ->
                viewModel.setSeedColor(color)
                viewModel.setUseDynamicColor(true) // 选色后启用自定义主题
                showThemeDialog = false
            },
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
            title = stringResource(R.string.settings_transition),
            options = listOf(
                "slide" to stringResource(R.string.transition_slide),
                "fade" to stringResource(R.string.transition_fade),
                "scale" to stringResource(R.string.transition_scale),
                "default" to stringResource(R.string.transition_default)
            ),
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

    if (showAboutDialog) {
        AboutDialog(
            version = stringResource(R.string.settings_version),
            onDismiss = { showAboutDialog = false }
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
    val tabs = listOf(stringResource(R.string.login_tab_qr), stringResource(R.string.login_tab_sms))
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
    // 预解析字符串（供非 Composable lambda 中使用）
    val strLoginCancel = stringResource(R.string.login_cancel)
    val strSmsSending = stringResource(R.string.login_sms_sending)
    val strSmsSent = stringResource(R.string.login_sms_sent)

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
            onDismiss = { showGeetest = false; smsMsg = strLoginCancel },
            onSuccess = { gc, gv, gs ->
                showGeetest = false
                scope.launch {
                    smsLoading = true; smsMsg = strSmsSending
                    val (ok, msg) = com.bili.music.data.api.BilibiliLoginClient.sendSmsWithGeetest(phone, geetestToken, gc, gv, gs)
                    if (ok) { smsCaptchaKey = msg; smsCountdown = 60; smsMsg = strSmsSent }
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
        title = { Text(stringResource(R.string.login_title)) },
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
                        val vm: com.bili.music.ui.screens.settings.SettingsViewModel = hiltViewModel()
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
                                Text(stringResource(R.string.login_open_app))
                            }
                            Spacer(Modifier.height(12.dp))
                            if (state.qrCodeBitmap != null) Image(bitmap = state.qrCodeBitmap!!.asImageBitmap(), contentDescription = "QR", modifier = Modifier.size(180.dp))
                            else CircularProgressIndicator(Modifier.size(180.dp))
                            Text(state.qrCodeMessage.ifBlank { stringResource(R.string.login_qr_fetching) }, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    1 -> {
                        Button(onClick = { onDismiss(); onWebLogin?.invoke() }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Language, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.login_open_web))
                        }
                    }
                }
            }
        },
        confirmButton = {
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.login_close)) } }
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
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, stringResource(R.string.login_close)) }
                Text(stringResource(R.string.login_web_title), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = {
                    val cm = android.webkit.CookieManager.getInstance()
                    val cookies = cm.getCookie("https://passport.bilibili.com") ?: ""
                    if (cookies.contains("SESSDATA")) onLoginSuccess(cookies)
                }) { Text(stringResource(R.string.login_done)) }
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
            Text(stringResource(R.string.login_captcha_hint), color = Color.White, style = MaterialTheme.typography.titleMedium)
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
                    com.bili.music.data.api.BilibiliApiClient.userCookie = cookies
                }
                onSmsSent()
            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.login_captcha_done)) }
        }
        IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
            Icon(Icons.Filled.Close, stringResource(R.string.login_close), tint = Color.White, modifier = Modifier.size(28.dp))
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
                val client = com.bili.music.data.api.BilibiliApiClient.okHttpClient()
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
            Icon(Icons.Filled.Close, stringResource(R.string.login_close), tint = Color.White, modifier = Modifier.size(28.dp))
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
        title = { Text(stringResource(R.string.login_captcha_hint)) },
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
        confirmButton = { TextButton(onClick = onVerified) { Text(stringResource(R.string.login_captcha_done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

@Composable
private fun QRLoginDialog(
    onDismiss: () -> Unit,
    onLoginSuccess: (String) -> Unit
) {
    val vm: com.bili.music.ui.screens.settings.SettingsViewModel = hiltViewModel()
    val state by vm.uiState.collectAsState()

    LaunchedEffect(Unit) {
        vm.startQRLogin()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.login_qr_title)) },
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
                    state.qrCodeMessage.ifBlank { stringResource(R.string.login_qr_fetching) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

@Composable
private fun TimerDialog(
    onSelect: (Long) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val timerOptions = listOf(
        15L * 60 * 1000 to stringResource(R.string.timer_15min),
        30L * 60 * 1000 to stringResource(R.string.timer_30min),
        45L * 60 * 1000 to stringResource(R.string.timer_45min),
        60L * 60 * 1000 to stringResource(R.string.timer_60min),
        90L * 60 * 1000 to stringResource(R.string.timer_90min),
        120L * 60 * 1000 to stringResource(R.string.timer_120min)
    )
    var showCustomDialog by remember { mutableStateOf(false) }
    var customMins by remember { mutableStateOf("") }

    if (showCustomDialog) {
        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            title = { Text(stringResource(R.string.settings_timer_custom_title)) },
            text = {
                OutlinedTextField(
                    value = customMins,
                    onValueChange = { customMins = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.settings_timer_minutes)) },
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
                }) { Text(stringResource(R.string.common_confirm)) }
            },
            dismissButton = { TextButton(onClick = { showCustomDialog = false }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_timer)) },
        text = {
            Column {
                TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Close, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_timer_turn_off), Modifier.weight(1f))
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
                    Text(stringResource(R.string.settings_timer_custom), Modifier.weight(1f))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
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
        0xFF6750A4.toInt() to stringResource(R.string.color_default_purple),
        0xFFE91E63.toInt() to stringResource(R.string.color_pink),
        0xFFFF5722.toInt() to stringResource(R.string.color_orange),
        0xFFFFEB3B.toInt() to stringResource(R.string.color_yellow),
        0xFF4CAF50.toInt() to stringResource(R.string.color_green),
        0xFF2196F3.toInt() to stringResource(R.string.color_blue),
        0xFF00BCD4.toInt() to stringResource(R.string.color_cyan),
        0xFF9C27B0.toInt() to stringResource(R.string.color_purple),
        0xFF607D8B.toInt() to stringResource(R.string.color_grey_blue),
        0xFF000000.toInt() to stringResource(R.string.color_black_white)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_color_dialog_title)) },
        text = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_color_dynamic_wallpaper), modifier = Modifier.weight(1f))
                    Switch(checked = useDynamicColor, onCheckedChange = onToggleDynamic)
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (!useDynamicColor) {
                    Text(
                        stringResource(R.string.settings_color_choose),
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
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_color_done)) }
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
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
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
        title = { Text(stringResource(R.string.settings_progress_style)) },
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
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
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
        title = { Text(stringResource(R.string.folder_select_title)) },
        text = {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (folders.isEmpty()) {
                Text(stringResource(R.string.folder_no_folders))
            } else {
                LazyColumn {
                    items(folders) { folder ->
                        ListItem(
                            headlineContent = { Text(folder.title) },
                            supportingContent = { Text(stringResource(R.string.folder_video_count, folder.songCount)) },
                            modifier = Modifier.clickable { onSelect(folder.id) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

@Composable
private fun AboutDialog(
    version: String,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val githubUri = android.net.Uri.parse("https://github.com/eason919191/BiliMusic")
    val bilibiliUri = android.net.Uri.parse("https://space.bilibili.com/88981336")
    val bilibiliAppUri = android.net.Uri.parse("bilibili://space/88981336")

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("BiliMusic $version") },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.about_description),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                // GitHub 链接
                Surface(
                    onClick = { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, githubUri)) },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Code, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.about_github), style = MaterialTheme.typography.titleSmall)
                            Text("github.com/eason919191/BiliMusic", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                // B站链接
                Surface(
                    onClick = {
                        // 优先打开B站App，失败则跳转浏览器
                        val appIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, bilibiliAppUri).apply {
                            setPackage("tv.danmaku.bili")
                        }
                        try { context.startActivity(appIntent) }
                        catch (_: Exception) { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, bilibiliUri)) }
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.SmartDisplay, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.about_bilibili), style = MaterialTheme.typography.titleSmall)
                            Text("space.bilibili.com/88981336", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_confirm)) }
        }
    )
}
