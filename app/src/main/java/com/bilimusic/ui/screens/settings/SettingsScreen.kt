package com.bilimusic.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.viewinterop.AndroidView
import com.bilimusic.data.api.netease.NeteaseApiClient
import com.bilimusic.data.api.netease.NeteaseSongParser
import com.bilimusic.data.model.*
import com.bilimusic.ui.theme.bounceSpring
import com.bilimusic.ui.theme.bounceSpringIntOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class SettingsCategory(val title: String, val icon: ImageVector) {
    PERSONALIZATION("个性化", Icons.Outlined.Tune),
    ACCOUNT("账号设置", Icons.Outlined.AccountCircle),
    DOWNLOAD("下载设置", Icons.Outlined.Download),
    SEARCH("搜索设置", Icons.Outlined.Search),
    SLEEP_TIMER("定时关闭", Icons.Outlined.Timer),
    BACKUP("备份与恢复", Icons.Outlined.Backup),
ABOUT("关于", Icons.Outlined.Info)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedCategory by remember { mutableStateOf<SettingsCategory?>(null) }

    // 修复：子页面时系统返回键应返回设置主页面，而非搜索
    BackHandler(enabled = selectedCategory != null) {
        selectedCategory = null
    }

    AnimatedContent(
        targetState = selectedCategory,
        transitionSpec = {
            val transition = uiState.pageTransition
            when (transition) {
                "fade" -> (fadeIn(animationSpec = bounceSpring()) togetherWith fadeOut(animationSpec = bounceSpring()))
                "scale" -> (scaleIn(animationSpec = bounceSpring(), initialScale = 0.9f) + fadeIn(animationSpec = bounceSpring()) togetherWith scaleOut(animationSpec = bounceSpring(), targetScale = 0.9f) + fadeOut(animationSpec = bounceSpring()))
                "default" -> (fadeIn(animationSpec = bounceSpring()) togetherWith fadeOut(animationSpec = bounceSpring()))
                else -> {
                    (slideInHorizontally(animationSpec = bounceSpringIntOffset()) { width -> width } + fadeIn(animationSpec = bounceSpring())) togetherWith
                            (slideOutHorizontally(animationSpec = bounceSpringIntOffset()) { width -> -width } + fadeOut(animationSpec = bounceSpring()))
                }
            }
        },
        label = "settings_category"
    ) { category ->
        when (category) {
            null -> SettingsMainPage(uiState = uiState, viewModel = viewModel, onSelectCategory = { selectedCategory = it })
            SettingsCategory.PERSONALIZATION -> PersonalizationPage(uiState = uiState, viewModel = viewModel, onBack = { selectedCategory = null })
            SettingsCategory.ACCOUNT -> AccountPage(uiState = uiState, viewModel = viewModel, onBack = { selectedCategory = null })
            SettingsCategory.DOWNLOAD -> DownloadPage(uiState = uiState, viewModel = viewModel, onBack = { selectedCategory = null })
            SettingsCategory.SEARCH -> SearchPage(uiState = uiState, viewModel = viewModel, onBack = { selectedCategory = null })
            SettingsCategory.SLEEP_TIMER -> SleepTimerPage(uiState = uiState, viewModel = viewModel, onBack = { selectedCategory = null })
            SettingsCategory.BACKUP -> BackupRestorePage(uiState = uiState, viewModel = viewModel, onBack = { selectedCategory = null })
SettingsCategory.ABOUT -> AboutPage(onBack = { selectedCategory = null })
        }
    }
}

// ==================== Category Sub-pages ====================

@Composable
private fun SettingsMainPage(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    onSelectCategory: (SettingsCategory) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text("设置", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Category items
        SettingsCategory.values().forEach { cat ->
            item {
                SettingsItem(icon = cat.icon, title = cat.title, subtitle = categorySubtitle(cat, uiState), onClick = { onSelectCategory(cat) })
            }
        }
        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

private fun categorySubtitle(cat: SettingsCategory, uiState: SettingsUiState): String = when (cat) {
    SettingsCategory.PERSONALIZATION -> "播放、歌词、外观设置"
    SettingsCategory.ACCOUNT -> if (uiState.isLoggedIn || uiState.netseaseLoggedIn) "已登录" else "未登录"
    SettingsCategory.DOWNLOAD -> "下载目录、下载线程数"
    SettingsCategory.SEARCH -> "排序、过滤筛选"
    SettingsCategory.SLEEP_TIMER -> if (uiState.isTimerActive) {
        val m = uiState.sleepTimerRemaining / 60000; val s = (uiState.sleepTimerRemaining % 60000) / 1000
        "剩余 ${String.format("%02d:%02d", m, s)}"
    } else "关闭"
SettingsCategory.BACKUP -> "备份歌单、设置与音乐文件"
    SettingsCategory.ABOUT -> "v2.0.0"
}

// ===== 个性化 =====
@Composable
private fun PersonalizationPage(uiState: SettingsUiState, viewModel: SettingsViewModel, onBack: () -> Unit) {
    var showPageTransitionDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showBgDialog by remember { mutableStateOf(false) }
    var bgImagePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) viewModel.setBackgroundImage(uri.toString())
    }

    Column(Modifier.fillMaxSize()) {
        // Header
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "返回") }
            Text("个性化", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        LazyColumn(Modifier.weight(1f)) {
            // 播放设置
            item { SettingsGroupHeader("播放设置") }
            item { SettingsSliderItem(icon = Icons.Outlined.BlurOn, title = "播放器背景模糊程度", subtitle = "${uiState.blurDegree.toInt()}%", value = uiState.blurDegree / 100f, onValueChange = { viewModel.setBlurDegree(it * 100f) }) }
            item {
                val pt = mapOf("slide" to "滑动", "fade" to "淡入淡出", "scale" to "缩放", "default" to "默认")
                SettingsItem(icon = Icons.Outlined.Animation, title = "页面过渡动画", subtitle = pt[uiState.pageTransition] ?: "滑动", onClick = { showPageTransitionDialog = true })
            }
            item { SettingsSwitchItem(icon = Icons.Outlined.ColorLens, title = "纯色背景", subtitle = "关闭后使用模糊背景", checked = uiState.playerBgPureColor, onCheckedChange = { viewModel.setPlayerBgPureColor(it) }) }
            if (!uiState.playerBgPureColor) {
                item { SettingsSliderItem(icon = Icons.Outlined.Opacity, title = "迷你播放器透明度", subtitle = "${(uiState.miniPlayerAlpha * 100).toInt()}%", value = uiState.miniPlayerAlpha, onValueChange = { viewModel.setMiniPlayerAlpha(it) }) }
            }
            item { SettingsSwitchItem(icon = Icons.Outlined.TextSnippet, title = "菜单选项说明文字", subtitle = "显示/隐藏播放器更多菜单中的说明", checked = uiState.showMenuSubtitle, onCheckedChange = { viewModel.setShowMenuSubtitle(it) }) }

            // 歌词设置
            item { SettingsGroupHeader("歌词设置") }
            item { SettingsSliderItem(icon = Icons.Outlined.BlurOn, title = "歌词模糊总体强度", subtitle = "${uiState.lyricBlurAmount.toInt()}", value = uiState.lyricBlurAmount, valueRange = 0f..30f, onValueChange = { viewModel.setLyricBlurAmount(it) }) }
            item { SettingsSliderItem(icon = Icons.Outlined.BlurOn, title = "当前句模糊", subtitle = "${uiState.lyricBlurCurrent.toInt()}", value = uiState.lyricBlurCurrent, valueRange = 0f..30f, onValueChange = { viewModel.setLyricBlurCurrent(it) }) }
            item { SettingsSliderItem(icon = Icons.Outlined.BlurOn, title = "隔1句模糊", subtitle = "${uiState.lyricBlurNear.toInt()}", value = uiState.lyricBlurNear, valueRange = 0f..30f, onValueChange = { viewModel.setLyricBlurNear(it) }) }
            item { SettingsSliderItem(icon = Icons.Outlined.BlurOn, title = "隔2句模糊", subtitle = "${uiState.lyricBlurMid.toInt()}", value = uiState.lyricBlurMid, valueRange = 0f..30f, onValueChange = { viewModel.setLyricBlurMid(it) }) }
            item { SettingsSliderItem(icon = Icons.Outlined.BlurOn, title = "其余歌词模糊", subtitle = "${uiState.lyricBlurFar.toInt()}", value = uiState.lyricBlurFar, valueRange = 0f..30f, onValueChange = { viewModel.setLyricBlurFar(it) }) }
            item { SettingsSliderItem(icon = Icons.Outlined.TextFields, title = "歌词字体大小", subtitle = "${uiState.lyricFontSize.toInt()}sp", value = uiState.lyricFontSize, valueRange = 12f..40f, onValueChange = { viewModel.setLyricFontSize(it) }) }
            item { SettingsSliderItem(icon = Icons.Outlined.ZoomIn, title = "当前句放大", subtitle = String.format("%.2fx", uiState.lyricScaleCurrent), value = uiState.lyricScaleCurrent, valueRange = 0.8f..1.5f, onValueChange = { viewModel.setLyricScaleCurrent(it) }) }
            item { SettingsSliderItem(icon = Icons.Outlined.ZoomIn, title = "附近句放大", subtitle = String.format("%.2fx", uiState.lyricScaleNear), value = uiState.lyricScaleNear, valueRange = 0.8f..1.3f, onValueChange = { viewModel.setLyricScaleNear(it) }) }
            item { SettingsSliderItem(icon = Icons.Outlined.ViewColumn, title = "歌词最大宽度", subtitle = "${(uiState.lyricMaxWidth * 100).toInt()}%", value = uiState.lyricMaxWidth, valueRange = 0.5f..1.0f, onValueChange = { viewModel.setLyricMaxWidth(it) }) }
            item {
                val alignOpts = mapOf("center" to "居中", "left" to "左对齐")
                SettingsItem(icon = Icons.Outlined.FormatAlignLeft, title = "歌词对齐方式", subtitle = alignOpts[uiState.lyricTextAlign] ?: "居中", onClick = { viewModel.setLyricTextAlign(if (uiState.lyricTextAlign == "center") "left" else "center") })
            }

            // 动画设置
            item { SettingsGroupHeader("动画设置") }
            item { SettingsSwitchItem(icon = Icons.Outlined.SportsBaseball, title = "启用回弹动画", subtitle = "关闭后所有动画使用线性过渡", checked = uiState.springEnabled, onCheckedChange = { viewModel.setSpringEnabled(it) }) }
            if (uiState.springEnabled) {
                item { SettingsSliderItem(icon = Icons.Outlined.Timeline, title = "回弹阻尼", subtitle = String.format("%.1f", uiState.springDamping), value = uiState.springDamping, valueRange = 0.3f..2f, onValueChange = { viewModel.setSpringDamping(it) }) }
                item { SettingsSliderItem(icon = Icons.Outlined.Tune, title = "回弹刚度", subtitle = String.format("%.0f", uiState.springStiffness), value = uiState.springStiffness, valueRange = 50f..500f, onValueChange = { viewModel.setSpringStiffness(it) }) }
                item {
                    TextButton(onClick = { viewModel.resetSpringAnimationDefaults() }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Icon(Icons.Filled.Restore, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("恢复动画默认值", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // 外观设置
            item { SettingsGroupHeader("外观设置") }
            item { SettingsItem(icon = Icons.Outlined.Palette, title = "主题色", subtitle = if (uiState.useDynamicColor) "动态取色" else "自定义", onClick = { showThemeDialog = true }) }
            item {
                SettingsClickableItem(icon = Icons.Outlined.DarkMode, title = "深色模式",
                    subtitle = when (uiState.themeMode) { ThemeMode.SYSTEM -> "跟随系统"; ThemeMode.LIGHT -> "浅色"; ThemeMode.DARK -> "深色" }) {
                    when (uiState.themeMode) { ThemeMode.SYSTEM -> viewModel.setThemeMode(ThemeMode.LIGHT); ThemeMode.LIGHT -> viewModel.setThemeMode(ThemeMode.DARK); ThemeMode.DARK -> viewModel.setThemeMode(ThemeMode.SYSTEM) }
                }
            }
            item { SettingsItem(icon = Icons.Outlined.Image, title = "播放器背景图", subtitle = if (uiState.backgroundImagePath.isNotBlank()) "已设置" else "未设置", onClick = { showBgDialog = true }) }
            // 字体颜色
            item { SettingsGroupHeader("字体颜色") }
            item { SettingsSwitchItem(icon = Icons.Outlined.TextFields, title = "自定义字体颜色", subtitle = "开启后所有文字使用指定颜色", checked = uiState.textColorEnabled, onCheckedChange = { viewModel.setTextColorEnabled(it) }) }
            if (uiState.textColorEnabled) {
                item {
                val presetTextColors = listOf(
                    0xFFFFFFFF.toInt() to "白色",
                    0xFF000000.toInt() to "黑色",
                    0xFFE0E0E0.toInt() to "浅灰",
                    0xFFB0B0B0.toInt() to "灰色",
                    0xFFFFD700.toInt() to "金色",
                    0xFF4FC3F7.toInt() to "天蓝",
                    0xFF81C784.toInt() to "浅绿",
                    0xFFF06292.toInt() to "粉色",
                    0xFFCE93D8.toInt() to "淡紫"
                )
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("文字颜色", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            presetTextColors.forEach { (color, name) ->
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(androidx.compose.ui.graphics.Color(color))
                                        .then(
                                            if (uiState.customTextColor == color)
                                                Modifier.border(3.dp, MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape)
                                            else Modifier
                                        )
                                        .clickable { viewModel.setCustomTextColor(color) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (color == 0xFFFFFFFF.toInt() || color == 0xFFE0E0E0.toInt()) {
                                        Text("A", style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color.Black)
                                    } else if (color == 0xFF000000.toInt()) {
                                        Text("A", style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    if (showPageTransitionDialog) SimpleSelectDialog("页面过渡动画", listOf("slide" to "滑动", "fade" to "淡入淡出", "scale" to "缩放", "default" to "默认"), uiState.pageTransition, onSelect = { viewModel.setPageTransition(it); showPageTransitionDialog = false }, onDismiss = { showPageTransitionDialog = false })
    if (showThemeDialog) ThemeDialog(useDynamicColor = uiState.useDynamicColor, onToggleDynamic = { viewModel.setUseDynamicColor(it) }, onSelectColor = { viewModel.setSeedColor(it) }, onDismiss = { showThemeDialog = false })
    if (showBgDialog) BackgroundImageDialog(
        currentPath = uiState.backgroundImagePath,
        currentOpacity = uiState.backgroundImageOpacity,
        currentBlur = uiState.backgroundImageBlur,
        onSelectImage = { bgImagePickerLauncher.launch("image/*") },
        onSetOpacity = { viewModel.setBackgroundImageOpacity(it) },
        onSetBlur = { viewModel.setBackgroundImageBlur(it) },
        onClear = { viewModel.setBackgroundImage("") },
        onDismiss = { showBgDialog = false }
    )
}

// ===== 账号设置 =====
@Composable
private fun AccountPage(uiState: SettingsUiState, viewModel: SettingsViewModel, onBack: () -> Unit) {
    var showCookieDialog by remember { mutableStateOf(false) }
    var showBiliLoginWebView by remember { mutableStateOf(false) }
    var showCaptcha by remember { mutableStateOf(false) }
    var captchaUrl by remember { mutableStateOf("") }
    var captchaPhone by remember { mutableStateOf("") }
    var showFolderSelectDialog by remember { mutableStateOf(false) }
    var showHistoryCountDialog by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "返回") }
            Text("账号设置", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        LazyColumn(Modifier.weight(1f)) {
            item { SettingsGroupHeader("账号管理") }
            item { SettingsItem(icon = Icons.Outlined.AccountCircle, title = "哔哩哔哩账号", subtitle = if (uiState.isLoggedIn) uiState.userInfo?.let { "${it.nickname} (Lv.${it.level})" } ?: "已登录" else "未登录", onClick = { showCookieDialog = true }) }
            item { SettingsItem(icon = Icons.Outlined.MusicNote, title = "网易云音乐", subtitle = if (uiState.netseaseLoggedIn) uiState.neteaseNickname.ifBlank { "已登录" } else "未登录", onClick = { viewModel.showNeteaseLogin() }) }

            if (uiState.netseaseLoggedIn) {
                item {
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth()) {
                        if (uiState.neteaseAvatar.isNotBlank()) com.bilimusic.ui.components.BiliAsyncImage(model = uiState.neteaseAvatar, contentDescription = "头像", modifier = Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape))
                        else Box(Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { Icon(Icons.Filled.MusicNote, null, Modifier.size(20.dp)) }
                        Spacer(Modifier.width(12.dp))
                        Column { Text(uiState.neteaseNickname, style = MaterialTheme.typography.titleSmall); Text("网易云音乐", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                item {
                    var showNeteasePlaylistImport by remember { mutableStateOf(false) }
                    SettingsItem(icon = Icons.Outlined.QueueMusic, title = "导入网易云歌单", subtitle = "将网易云歌单导入为本地歌单", onClick = { showNeteasePlaylistImport = true })
                    if (showNeteasePlaylistImport) NeteasePlaylistImportDialog(onDismiss = { showNeteasePlaylistImport = false }, viewModel = viewModel)
                }
            }

            if (uiState.isLoggedIn && uiState.userInfo != null) {
                item {
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth()) {
                        if (uiState.userInfo!!.avatar.isNotBlank()) com.bilimusic.ui.components.BiliAsyncImage(model = uiState.userInfo!!.avatar, contentDescription = "头像", modifier = Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape))
                        Spacer(Modifier.width(12.dp))
                        Column { Text(uiState.userInfo!!.nickname, style = MaterialTheme.typography.titleSmall); Text("UID: ${uiState.userInfo!!.uid}  Lv.${uiState.userInfo!!.level}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
            if (uiState.isLoggedIn) {
                item { SettingsItem(icon = Icons.Outlined.Bookmark, title = "导入收藏夹", subtitle = "将哔哩哔哩收藏夹导入为歌单", onClick = { viewModel.loadFavoriteFolders(); showFolderSelectDialog = true }) }
                item { SettingsItem(icon = Icons.Outlined.History, title = "同步B站播放历史", subtitle = "将哔哩哔哩观看/收听记录导入为本地歌单", onClick = { showHistoryCountDialog = true }) }
                item { SettingsItem(icon = Icons.Outlined.Logout, title = "退出登录", subtitle = "清除Cookie", onClick = { viewModel.logoutBilibili() }) }
            }

            // 导入本地音乐（也放在账号这边，或者也可以放在下载设置）
            item { SettingsGroupHeader("音乐管理") }
            item { SettingsItem(icon = Icons.Outlined.LibraryMusic, title = "导入本地音乐", subtitle = if (uiState.isImportingLocal) "导入中..." else "扫描设备中的音乐文件", onClick = { viewModel.importLocalMusic() }, enabled = !uiState.isImportingLocal) }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    if (showCookieDialog) {
        CookieDialog(currentCookie = uiState.bilibiliCookie, onConfirm = { viewModel.loginWithCookie(it); showCookieDialog = false }, onDismiss = { showCookieDialog = false },
            onCaptchaNeeded = { url, phone -> showCookieDialog = false; captchaUrl = url; captchaPhone = phone; showCaptcha = true },
            onWebLogin = { showBiliLoginWebView = true })
    }
    if (showCaptcha) {
        val scope = rememberCoroutineScope()
        SimpleCaptchaWebView(url = captchaUrl, phone = captchaPhone, onDismiss = { showCaptcha = false }, onSmsSent = { showCaptcha = false; scope.launch { val (ok, msg) = com.bilimusic.data.api.BilibiliLoginClient.sendSmsCode(captchaPhone); if (ok) showCookieDialog = true } })
    }
    if (showBiliLoginWebView) BiliLoginWebView(onDismiss = { showBiliLoginWebView = false }, onLoginSuccess = { cookie -> showBiliLoginWebView = false; viewModel.loginWithCookie(cookie) })
    if (showFolderSelectDialog) FolderSelectDialog(folders = uiState.favoriteFolders, isLoading = uiState.isLoadingFolders, onSelect = { viewModel.importFavoriteFolder(it); showFolderSelectDialog = false }, onDismiss = { showFolderSelectDialog = false })
    if (showHistoryCountDialog) {
        var countText by remember { mutableStateOf("200") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showHistoryCountDialog = false },
            title = { Text("同步播放历史") },
            text = {
                Column {
                    Text("请输入要同步的记录数量（最多50条/次）", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = countText,
                        onValueChange = { countText = it.filter { c -> c.isDigit() } },
                        label = { Text("数量") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val count = countText.toIntOrNull() ?: 200
                    viewModel.syncBilibiliHistory(count)
                    showHistoryCountDialog = false
                }) { Text("确定") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showHistoryCountDialog = false }) { Text("取消") }
            }
        )
    }
    if (uiState.showNeteaseLoginDialog) NeteaseLoginDialog(onDismiss = { viewModel.hideNeteaseLogin() }, onLoginSuccess = { viewModel.neteaseLoginWithCookie(it) }, onLogout = { viewModel.logoutNetease() }, isLoggedIn = uiState.netseaseLoggedIn, nickname = uiState.neteaseNickname)
}

// ===== 下载设置 =====
@Composable
private fun DownloadPage(uiState: SettingsUiState, viewModel: SettingsViewModel, onBack: () -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) { viewModel.setDownloadPath(uri.path?.replace("/tree/primary:", "/storage/emulated/0/") ?: "") }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "返回") }
            Text("下载设置", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        LazyColumn(Modifier.weight(1f)) {
            item { SettingsGroupHeader("下载设置") }
            item { SettingsItem(icon = Icons.Outlined.Folder, title = "下载位置", subtitle = uiState.downloadPath.ifBlank { "Music/BiliMusic" }, onClick = { launcher.launch(null) }) }
            if (uiState.downloadPath.isNotBlank()) {
                item {
                    Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(uiState.downloadPath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.setDownloadPath("") }) { Text("恢复默认", style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
            item { SettingsSliderItem(icon = Icons.Outlined.Speed, title = "下载线程数", subtitle = "${uiState.downloadThreadCount}", value = uiState.downloadThreadCount.toFloat(), valueRange = 1f..5f, onValueChange = { viewModel.setDownloadThreadCount(it.toInt()) }) }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

// ===== 搜索设置 =====
@Composable
private fun SearchPage(uiState: SettingsUiState, viewModel: SettingsViewModel, onBack: () -> Unit) {
    var showSearchSortDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "返回") }
            Text("搜索设置", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        LazyColumn(Modifier.weight(1f)) {
            item { SettingsGroupHeader("搜索") }
            item { SettingsItem(icon = Icons.Outlined.Sort, title = "排序方式", subtitle = searchSortDisplay(uiState.searchSort), onClick = { showSearchSortDialog = true }) }
            item { SettingsGroupHeader("搜索筛选") }
            item { SettingsSwitchItem(icon = Icons.Outlined.FilterList, title = "过滤超长视频", subtitle = "默认隐藏10分钟以上的视频", checked = uiState.filterLongVideos, onCheckedChange = { viewModel.setFilterLongVideos(it) }) }
            item { SettingsSwitchItem(icon = Icons.Outlined.Loop, title = "过滤\"循环\"标题", subtitle = "默认隐藏标题含\"循环\"的视频", checked = uiState.filterLoopTitle, onCheckedChange = { viewModel.setFilterLoopTitle(it) }) }
            item {
                var showKwDialog by remember { mutableStateOf(false) }
                var kwText by remember { mutableStateOf(uiState.filterKeywords) }
                SettingsItem(icon = Icons.Outlined.Block, title = "自定义过滤词", subtitle = if (uiState.filterKeywords.isBlank()) "点击设置，用|分隔多个词" else uiState.filterKeywords, onClick = { kwText = uiState.filterKeywords; showKwDialog = true })
                if (showKwDialog) AlertDialog(onDismissRequest = { showKwDialog = false }, title = { Text("自定义过滤词") }, text = { OutlinedTextField(value = kwText, onValueChange = { kwText = it }, label = { Text("过滤词（|分隔）") }, modifier = Modifier.fillMaxWidth()) }, confirmButton = { TextButton(onClick = { viewModel.setFilterKeywords(kwText); showKwDialog = false }) { Text("确定") } }, dismissButton = { TextButton(onClick = { showKwDialog = false }) { Text("取消") } })
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    if (showSearchSortDialog) SimpleSelectDialog("排序方式", listOf("totalrank" to "综合", "click" to "播放量", "stow" to "收藏数", "dm" to "弹幕数", "pubdate" to "发布日期"), uiState.searchSort, onSelect = { viewModel.setSearchSort(it); showSearchSortDialog = false }, onDismiss = { showSearchSortDialog = false })
}

// ===== 定时关闭 =====
@Composable
private fun SleepTimerPage(uiState: SettingsUiState, viewModel: SettingsViewModel, onBack: () -> Unit) {
    var showTimerDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "返回") }
            Text("定时关闭", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        LazyColumn(Modifier.weight(1f)) {
            item { SettingsGroupHeader("定时关闭") }
            item {
                SettingsItem(icon = Icons.Outlined.Timer, title = "定时关闭", subtitle = if (uiState.isTimerActive) { val m = uiState.sleepTimerRemaining / 60000; val s = (uiState.sleepTimerRemaining % 60000) / 1000; "剩余 ${String.format("%02d:%02d", m, s)}" } else "关闭", onClick = { showTimerDialog = true })
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    if (showTimerDialog) TimerDialog(onSelect = { viewModel.setSleepTimer(it); showTimerDialog = false }, onClear = { viewModel.clearSleepTimer(); showTimerDialog = false }, onDismiss = { showTimerDialog = false })
}

// ===== 关于 =====
@Composable
private fun AboutPage(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "返回") }
            Text("关于", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        LazyColumn(Modifier.weight(1f)) {
            item { SettingsGroupHeader("关于") }
            item { SettingsItem(icon = Icons.Outlined.Info, title = "关于 BiliMusic", subtitle = "v1.0.0", onClick = {}) }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

// ==================== Shared Dialogs & Components (moved up, unchanged) ====================

@Composable
private fun NeteasePlaylistImportDialog(onDismiss: () -> Unit, viewModel: SettingsViewModel) {
    var playlists by remember { mutableStateOf<List<com.bilimusic.data.api.netease.NeteasePlaylist>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            val resp = withContext(Dispatchers.IO) { NeteaseApiClient.getUserPlaylists(0) }
            playlists = withContext(Dispatchers.IO) { com.bilimusic.data.api.netease.NeteaseSongParser.parsePlaylists(resp) }
        } catch (_: Exception) {}
        isLoading = false
    }

    AlertDialog(onDismissRequest = onDismiss, title = { Text("导入网易云歌单") }, text = {
        if (isLoading) Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else if (playlists.isEmpty()) Text("没有找到歌单")
        else LazyColumn(Modifier.heightIn(max = 300.dp)) {
            items(playlists) { pl ->
                ListItem(headlineContent = { Text(pl.name) }, supportingContent = { Text("${pl.songCount} 首") },
                    leadingContent = { if (pl.coverUrl != null) com.bilimusic.ui.components.BiliAsyncImage(model = pl.coverUrl, contentDescription = "封面", modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop) else Icon(Icons.Filled.QueueMusic, null) },
                    modifier = Modifier.clickable { onDismiss(); viewModel.importNeteasePlaylist(pl.id) })
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
private fun NeteaseLoginDialog(onDismiss: () -> Unit, onLoginSuccess: (String) -> Unit, onLogout: () -> Unit, isLoggedIn: Boolean, nickname: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == -1) { val cookieJson = result.data?.getStringExtra(com.bilimusic.ui.screens.netease.NeteaseWebLoginActivity.RESULT_COOKIE); if (cookieJson != null) onLoginSuccess(cookieJson) }
    }

    if (isLoggedIn) {
        AlertDialog(onDismissRequest = onDismiss, title = { Text("网易云音乐") }, text = { Column { Text("已登录: $nickname"); Spacer(Modifier.height(12.dp)); Button(onClick = onLogout, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Filled.Logout, null); Spacer(Modifier.width(8.dp)); Text("退出登录") } } }, confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } })
    } else {
        var loginTab by remember { mutableIntStateOf(0) }; var phone by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }; var captcha by remember { mutableStateOf("") }; var countdown by remember { mutableIntStateOf(0) }; var isLoggingIn by remember { mutableStateOf(false) }
        LaunchedEffect(countdown) { if (countdown > 0) { kotlinx.coroutines.delay(1000); countdown-- } }
        AlertDialog(onDismissRequest = onDismiss, title = { Text("登录网易云音乐") }, text = {
            Column {
                TabRow(selectedTabIndex = loginTab) { Tab(selected = loginTab == 0, onClick = { loginTab = 0 }, text = { Text("验证码") }); Tab(selected = loginTab == 1, onClick = { loginTab = 1 }, text = { Text("密码") }); Tab(selected = loginTab == 2, onClick = { loginTab = 2 }, text = { Text("网页") }) }
                Spacer(Modifier.height(12.dp))
                when (loginTab) {
                    0 -> {
                        OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("手机号") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth()) {
                            OutlinedTextField(value = captcha, onValueChange = { captcha = it }, label = { Text("验证码") }, singleLine = true, modifier = Modifier.weight(1f)); Spacer(Modifier.width(8.dp))
                            Button(onClick = { scope.launch { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.bilimusic.data.api.netease.NeteaseApiClient.sendCaptcha(phone) }; countdown = 60 } }, enabled = phone.isNotBlank() && countdown == 0, modifier = Modifier.height(56.dp)) { Text(if (countdown > 0) "${countdown}s" else "发送") }
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { isLoggingIn = true; scope.launch { try { val resp = withContext(Dispatchers.IO) { com.bilimusic.data.api.netease.NeteaseApiClient.loginByCaptcha(phone, captcha) }; val code = org.json.JSONObject(resp).optInt("code"); if (code == 200) { val cookies = com.bilimusic.data.api.netease.NeteaseApiClient.getCookies(); onLoginSuccess(org.json.JSONObject(cookies as Map<*, *>).toString()) } else android.widget.Toast.makeText(context, org.json.JSONObject(resp).optString("message", "登录失败"), android.widget.Toast.LENGTH_SHORT).show() } catch (e: Exception) { android.widget.Toast.makeText(context, e.message, android.widget.Toast.LENGTH_SHORT).show() }; isLoggingIn = false } }, enabled = !isLoggingIn, modifier = Modifier.fillMaxWidth()) { if (isLoggingIn) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("登录") }
                    }
                    1 -> {
                        OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("手机号") }, singleLine = true, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("密码") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(12.dp))
                        Button(onClick = { isLoggingIn = true; scope.launch { try { val resp = withContext(Dispatchers.IO) { com.bilimusic.data.api.netease.NeteaseApiClient.loginByPhone(phone, password) }; val code = org.json.JSONObject(resp).optInt("code"); if (code == 200) { val cookies = com.bilimusic.data.api.netease.NeteaseApiClient.getCookies(); onLoginSuccess(org.json.JSONObject(cookies as Map<*, *>).toString()) } else { val msg = org.json.JSONObject(resp).optString("message", ""); android.widget.Toast.makeText(context, if (msg.contains("确认")) "需要安全验证，请使用验证码或网页登录" else msg.ifBlank { "登录失败($code)" }, android.widget.Toast.LENGTH_LONG).show() } } catch (e: Exception) { android.widget.Toast.makeText(context, e.message, android.widget.Toast.LENGTH_SHORT).show() }; isLoggingIn = false } }, enabled = !isLoggingIn, modifier = Modifier.fillMaxWidth()) { if (isLoggingIn) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("登录") }
                    }
                    2 -> { Text("打开浏览器登录网易云音乐", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(12.dp)); Button(onClick = { val intent = android.content.Intent(context, com.bilimusic.ui.screens.netease.NeteaseWebLoginActivity::class.java); launcher.launch(intent) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Language, null); Spacer(Modifier.width(8.dp)); Text("打开网页扫码登录") } }
                }
            }
        }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
    }
}

// ---- Settings Components (unchanged) ----

@Composable
private fun SettingsGroupHeader(title: String) {
    Text(text = title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 12.dp))
}


@Composable
private fun SettingsClickableItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(headlineContent = { Text(title) }, supportingContent = { Text(subtitle, color = MaterialTheme.colorScheme.primary) }, leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }, modifier = Modifier.clickable { onClick() }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
}

@Composable
private fun SettingsSwitchItem(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(headlineContent = { Text(title) }, supportingContent = { Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant) }, leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }, trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
}

@Composable
private fun SettingsSliderItem(icon: ImageVector, title: String, subtitle: String, value: Float, valueRange: ClosedFloatingPointRange<Float> = 0f..1f, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange, modifier = Modifier.padding(start = 40.dp))
    }
}

// ---- Dialogs (unchanged from original, key ones kept) ----

@Composable
private fun CookieDialog(currentCookie: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit, onCaptchaNeeded: ((String, String) -> Unit)? = null, onWebLogin: (() -> Unit)? = null) {
    var selectedTab by remember { mutableIntStateOf(0) }; val tabs = listOf("扫码", "短信"); val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }; var phone by remember { mutableStateOf("") }; var smsCode by remember { mutableStateOf("") }; var smsCaptchaKey by remember { mutableStateOf("") }; var smsCountdown by remember { mutableIntStateOf(0) }; var smsMsg by remember { mutableStateOf("") }; var smsLoading by remember { mutableStateOf(false) }
    var showQR by remember { mutableStateOf(false) }; var showGeetest by remember { mutableStateOf(false) }; var captchaUrl by remember { mutableStateOf("") }; var geetestGt by remember { mutableStateOf("") }; var geetestChallenge by remember { mutableStateOf("") }; var geetestToken by remember { mutableStateOf("") }

    LaunchedEffect(smsCountdown) { if (smsCountdown > 0) { kotlinx.coroutines.delay(1000); smsCountdown-- } }
    if (showQR) { QRLoginDialog(onDismiss = { showQR = false }, onLoginSuccess = { cookie -> onConfirm(cookie) }); return }
    if (showGeetest) { PiliPlusGeetestDialog(gt = geetestGt, challenge = geetestChallenge, onDismiss = { showGeetest = false; smsMsg = "已取消" }, onSuccess = { gc, gv, gs -> showGeetest = false; scope.launch { smsLoading = true; smsMsg = "正在发送..."; val (ok, msg) = com.bilimusic.data.api.BilibiliLoginClient.sendSmsWithGeetest(phone, geetestToken, gc, gv, gs); if (ok) { smsCaptchaKey = msg; smsCountdown = 60; smsMsg = "已发送" } else smsMsg = msg; smsLoading = false } }); return }

    AlertDialog(onDismissRequest = onDismiss, icon = { Icon(Icons.Outlined.AccountCircle, null) }, title = { Text("哔哩哔哩账号登录") }, text = {
        Column {
            TabRow(selectedTabIndex = selectedTab) { tabs.forEachIndexed { i, t -> Tab(selected = selectedTab == i, onClick = { selectedTab = i }, text = { Text(t) }) } }; Spacer(Modifier.height(12.dp))
            when (selectedTab) {
                0 -> {
                    val context = androidx.compose.ui.platform.LocalContext.current; val vm: SettingsViewModel = hiltViewModel(); val state by vm.uiState.collectAsState()
                    LaunchedEffect(Unit) { vm.startQRLogin() }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = { try { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).apply { data = android.net.Uri.parse("bilibili://browser?url=${java.net.URLEncoder.encode(state.qrCodeUrl ?: "", "UTF-8")}"); setPackage("tv.danmaku.bili") }) } catch (_: Exception) {} }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.OpenInBrowser, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("打开哔哩哔哩App登录") }
                        Spacer(Modifier.height(12.dp))
                        if (state.qrCodeBitmap != null) Image(bitmap = state.qrCodeBitmap!!.asImageBitmap(), contentDescription = "QR", modifier = Modifier.size(180.dp)) else CircularProgressIndicator(Modifier.size(180.dp))
                        Text(state.qrCodeMessage.ifBlank { "获取二维码..." }, style = MaterialTheme.typography.bodySmall)
                    }
                }
                1 -> { Button(onClick = { onDismiss(); onWebLogin?.invoke() }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Language, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("打开哔哩哔哩网页登录") } }
            }
        }
    }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } })
}

@Composable
private fun BiliLoginWebView(onDismiss: () -> Unit, onLoginSuccess: (String) -> Unit) {
    var loading by remember { mutableStateOf(true) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "关闭") }; Text("哔哩哔哩登录", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { val cookies = android.webkit.CookieManager.getInstance().getCookie("https://passport.bilibili.com") ?: ""; if (cookies.contains("SESSDATA")) onLoginSuccess(cookies) }) { Text("已登录") }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            AndroidView(factory = { ctx -> android.webkit.WebView(ctx).apply { settings.javaScriptEnabled = true; settings.domStorageEnabled = true; settings.userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"; webViewClient = object : android.webkit.WebViewClient() { override fun onPageFinished(v: android.webkit.WebView?, u: String?) { loading = false; if (android.webkit.CookieManager.getInstance().getCookie("https://passport.bilibili.com")?.contains("SESSDATA") == true) onLoginSuccess(android.webkit.CookieManager.getInstance().getCookie("https://passport.bilibili.com") ?: "") } }; loadUrl("https://passport.bilibili.com/login?goto=https%3A%2F%2Fwww.bilibili.com%2F") } }, modifier = Modifier.weight(1f).fillMaxWidth())
        }
    }
}

private fun searchSortDisplay(sort: String) = when(sort) { "click"->"播放量"; "stow"->"收藏数"; "dm"->"弹幕数"; "pubdate"->"发布日期"; else->"综合" }

@Composable
private fun SimpleCaptchaWebView(url: String, phone: String, onDismiss: () -> Unit, onSmsSent: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.5f)).clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClick = {})) {
        Column(Modifier.fillMaxSize().padding(32.dp).align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("请完成滑块验证", color = Color.White, style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(8.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) { AndroidView(factory = { ctx -> android.webkit.WebView(ctx).apply { settings.javaScriptEnabled = true; settings.domStorageEnabled = true; addJavascriptInterface(object { @android.webkit.JavascriptInterface fun onSmsResult(jsonStr: String) { try { val json = org.json.JSONObject(jsonStr); if (json.optInt("code", -1) == 0) (context as? android.app.Activity)?.runOnUiThread { onSmsSent() } } catch (_: Exception) {} } }, "SmsBridge"); webViewClient = object : android.webkit.WebViewClient() {}; loadUrl(url) } }, modifier = Modifier.fillMaxSize()) }
            Spacer(Modifier.height(8.dp)); Button(onClick = { onSmsSent() }, modifier = Modifier.fillMaxWidth()) { Text("验证完成，发短信") }
        }
        IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) { Icon(Icons.Filled.Close, "关闭", tint = Color.White, modifier = Modifier.size(28.dp)) }
    }
}

@Composable
private fun PiliPlusGeetestDialog(gt: String, challenge: String, onDismiss: () -> Unit, onSuccess: (gc: String, gv: String, gs: String) -> Unit) {
    var jsCode by remember { mutableStateOf("") }; var jsUrl by remember { mutableStateOf("https://static.geetest.com/static/js/fullpage.9.2.0-guwyxh.js") }; var isLoading by remember { mutableStateOf(true) }
    LaunchedEffect(gt) {
        withContext(Dispatchers.IO) {
            try { val client = com.bilimusic.data.api.BilibiliApiClient.sharedClient(); val resp = client.newCall(okhttp3.Request.Builder().url("https://api.geetest.com/gettype.php?gt=$gt").addHeader("User-Agent", "Mozilla/5.0").build()).execute(); val body = resp.body?.string() ?: ""; if (body.contains("\"status\":\"success\"")) { val jsonStr = body.substringAfter("(").substringBeforeLast(")"); val json = org.json.JSONObject(jsonStr); val data = json.getJSONObject("data"); jsUrl = "https://static.geetest.com/static/js/fullpage.0.0.0.js"; data.put("gt", gt); data.put("challenge", challenge); data.put("offline", false); data.put("new_captcha", true); data.put("product", "bind"); data.put("width", "100%"); data.put("https", true); data.put("protocol", "https://"); jsCode = "new Geetest($data).onSuccess(function(){R('success',this.getValidate())}).onError(function(e){R('error',e)}).onClose(function(){R('close',{})});this.onReady(function(){this.verify()})" } } catch (_: Exception) {}
        }; isLoading = false
    }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.5f)).clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClick = {})) {
        if (isLoading) CircularProgressIndicator(Modifier.align(Alignment.Center).size(48.dp))
        else AndroidView(factory = { ctx -> android.webkit.WebView(ctx).apply { settings.javaScriptEnabled = true; addJavascriptInterface(object { @android.webkit.JavascriptInterface fun onResult(jsonStr: String) { try { val j = org.json.JSONObject(jsonStr); if (j.optString("geetest_validate", "").isNotBlank()) (context as? android.app.Activity)?.runOnUiThread { onSuccess(j.optString("geetest_challenge", challenge), j.optString("geetest_validate", ""), j.optString("geetest_seccode", "")) } } catch (_: Exception) {} } }, "GeetestBridge"); webViewClient = object : android.webkit.WebViewClient() { override fun onPageFinished(v: android.webkit.WebView?, u: String?) { v?.evaluateJavascript(jsCode, null) } }; loadDataWithBaseURL(null, "<!DOCTYPE html><html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=1\"><script src=\"$jsUrl\"></script></head><body style=\"margin:0;padding:0\"><script>function R(type,data){if(type==='success')GeetestBridge.onResult(JSON.stringify(data));}</script></body></html>", "text/html", "UTF-8", null) } }, modifier = Modifier.fillMaxSize().padding(32.dp).align(Alignment.Center))
        IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) { Icon(Icons.Filled.Close, "关闭", tint = Color.White, modifier = Modifier.size(28.dp)) }
    }
}

@Composable
private fun GeetestWebView(url: String, onDismiss: () -> Unit, onVerified: () -> Unit) {
    var loading by remember { mutableStateOf(true) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("请完成滑块验证") }, text = { Box(Modifier.fillMaxWidth().height(300.dp)) { if (loading) CircularProgressIndicator(Modifier.align(Alignment.Center)); AndroidView(factory = { ctx -> android.webkit.WebView(ctx).apply { settings.javaScriptEnabled = true; settings.domStorageEnabled = true; webViewClient = object : android.webkit.WebViewClient() { override fun onPageFinished(v: android.webkit.WebView?, u: String?) { loading = false } }; loadUrl(url) } }, modifier = Modifier.fillMaxSize()) } }, confirmButton = { TextButton(onClick = onVerified) { Text("验证完成") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
private fun QRLoginDialog(onDismiss: () -> Unit, onLoginSuccess: (String) -> Unit) {
    val vm: SettingsViewModel = hiltViewModel(); val state by vm.uiState.collectAsState()
    LaunchedEffect(Unit) { vm.startQRLogin() }
    LaunchedEffect(state.isQrPolling) { if (!state.isQrPolling && state.qrCodeMessage == "登录成功！") { val cookie = state.bilibiliCookie; if (cookie.isNotBlank()) onLoginSuccess(cookie) } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("扫码登录") }, text = {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            if (state.qrCodeBitmap != null) Image(bitmap = state.qrCodeBitmap!!.asImageBitmap(), contentDescription = "QR Code", modifier = Modifier.size(256.dp))
            else Box(Modifier.size(256.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            Text(state.qrCodeMessage.ifBlank { "获取二维码中..." }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
private fun TimerDialog(onSelect: (Long) -> Unit, onClear: () -> Unit, onDismiss: () -> Unit) {
    val timerOptions = listOf(15L*60*1000 to "15分钟", 30L*60*1000 to "30分钟", 45L*60*1000 to "45分钟", 60L*60*1000 to "60分钟", 90L*60*1000 to "90分钟", 120L*60*1000 to "120分钟")
    var showCustomDialog by remember { mutableStateOf(false) }; var customMins by remember { mutableStateOf("") }
    if (showCustomDialog) AlertDialog(onDismissRequest = { showCustomDialog = false }, title = { Text("自定义时间") }, text = { OutlinedTextField(value = customMins, onValueChange = { customMins = it.filter { c -> c.isDigit() } }, label = { Text("分钟") }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth()) }, confirmButton = { TextButton(onClick = { val mins = customMins.toLongOrNull(); if (mins != null && mins > 0) { onSelect(mins * 60 * 1000); showCustomDialog = false } }) { Text("确定") } }, dismissButton = { TextButton(onClick = { showCustomDialog = false }) { Text("取消") } })
    AlertDialog(onDismissRequest = onDismiss, title = { Text("定时关闭") }, text = {
        Column {
            TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Close, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("关闭定时", Modifier.weight(1f)) }; Divider()
            timerOptions.forEach { (millis, label) -> TextButton(onClick = { onSelect(millis) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Timer, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(label, Modifier.weight(1f)) } }; Divider()
            TextButton(onClick = { showCustomDialog = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("自定义…", Modifier.weight(1f)) }
        }
    }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
private fun ThemeDialog(useDynamicColor: Boolean, onToggleDynamic: (Boolean) -> Unit, onSelectColor: (Int) -> Unit, onDismiss: () -> Unit) {
    val presetColors = listOf(0xFF6750A4.toInt() to "默认紫", 0xFFE91E63.toInt() to "粉色", 0xFFFF5722.toInt() to "橙色", 0xFFFFEB3B.toInt() to "黄色", 0xFF4CAF50.toInt() to "绿色", 0xFF2196F3.toInt() to "蓝色", 0xFF00BCD4.toInt() to "青色", 0xFF9C27B0.toInt() to "紫色", 0xFF607D8B.toInt() to "灰蓝", 0xFF000000.toInt() to "黑白")
    AlertDialog(onDismissRequest = onDismiss, title = { Text("主题色设置") }, text = {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) { Text("动态取色（壁纸）", modifier = Modifier.weight(1f)); Switch(checked = useDynamicColor, onCheckedChange = onToggleDynamic) }
            Spacer(Modifier.height(16.dp))
            if (!useDynamicColor) { Text("选择主题色", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(8.dp)); presetColors.chunked(5).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { row.forEach { (color, _) -> Box(Modifier.size(40.dp).clip(CircleShape).background(Color(color)).clickable { onSelectColor(color) }, contentAlignment = Alignment.Center) {} } }; Spacer(Modifier.height(8.dp)) } }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } })
}

@Composable
private fun SimpleSelectDialog(title: String, options: List<Pair<String, String>>, current: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Column { options.forEach { (value, label) -> Row(Modifier.fillMaxWidth().clickable { onSelect(value) }.padding(vertical = 12.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = current == value, onClick = { onSelect(value) }); Spacer(Modifier.width(12.dp)); Text(label, style = MaterialTheme.typography.bodyLarge) } } } }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
private fun ProgressStyleDialog(currentStyle: ProgressBarStyle, onSelect: (ProgressBarStyle) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("进度条样式") }, text = { Column { ProgressBarStyle.values().forEach { style -> Row(Modifier.fillMaxWidth().clickable { onSelect(style) }.padding(vertical = 12.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = style == currentStyle, onClick = { onSelect(style) }); Spacer(Modifier.width(12.dp)); Text(style.displayName) } } } }, confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
private fun FolderSelectDialog(folders: List<BilibiliFavoriteFolder>, isLoading: Boolean, onSelect: (Long) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("选择收藏夹") }, text = { if (isLoading) Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } else if (folders.isEmpty()) Text("未找到收藏夹") else LazyColumn { items(folders) { folder -> ListItem(headlineContent = { Text(folder.title) }, supportingContent = { Text("${folder.songCount} 个视频") }, modifier = Modifier.clickable { onSelect(folder.id) }) } } }, confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
// ===== 备份与恢复 =====
@Composable
private fun BackupRestorePage(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val backupCreateLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) viewModel.backupData(uri)
    }
    val backupRestoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (uri != null) viewModel.restoreData(uri)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "返回") }
            Text("备份与恢复", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        LazyColumn(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
            item {
                SettingsItem(
                    icon = Icons.Outlined.Backup,
                    title = "备份所有数据",
                    subtitle = "歌单、设置、登录信息、下载记录",
                    onClick = {
                        val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.ROOT).format(java.util.Date())
                        backupCreateLauncher.launch("bilimusic_backup_$ts.zip")
                    }
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Outlined.Restore,
                    title = "从备份恢复",
                    subtitle = "还原之前备份的zip文件",
                    onClick = {
                        val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                            type = "application/zip"
                            addCategory(android.content.Intent.CATEGORY_OPENABLE)
                        }
                        backupRestoreLauncher.launch(intent)
                    }
                )
            }
            if (uiState.backupError != null) {
                item {
                    Text(uiState.backupError, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        leadingContent = { Icon(icon, contentDescription = null, tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

// ===== 背景图设置弹窗 =====
@Composable
private fun BackgroundImageDialog(
    currentPath: String,
    currentOpacity: Float,
    currentBlur: Float,
    onSelectImage: () -> Unit,
    onSetOpacity: (Float) -> Unit,
    onSetBlur: (Float) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("背景图设置") },
        text = {
            Column {
                Button(
                    onClick = onSelectImage,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Filled.FolderOpen, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (currentPath.isNotBlank()) "更换图片" else "选择图片")
                }
                Spacer(Modifier.height(12.dp))
                if (currentPath.isNotBlank()) {
                    com.bilimusic.ui.components.BiliAsyncImage(
                        model = currentPath,
                        contentDescription = "背景图预览",
                        modifier = Modifier.fillMaxWidth().height(120.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("不透明度: ${(currentOpacity * 100).toInt()}%", modifier = Modifier.width(110.dp), style = MaterialTheme.typography.bodySmall)
                        Slider(value = currentOpacity, onValueChange = onSetOpacity, valueRange = 0.1f..1f, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("模糊度: ${currentBlur.toInt()}", modifier = Modifier.width(110.dp), style = MaterialTheme.typography.bodySmall)
                        Slider(value = currentBlur, onValueChange = onSetBlur, valueRange = 0f..50f, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Icon(Icons.Filled.Delete, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("清除背景图")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )
}