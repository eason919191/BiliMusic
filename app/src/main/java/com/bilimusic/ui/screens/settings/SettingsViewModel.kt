package com.bilimusic.ui.screens.settings

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bilimusic.data.api.BilibiliApiClient
import com.bilimusic.data.api.BilibiliLoginClient
import com.bilimusic.data.api.QRCodeData
import com.bilimusic.data.api.UserInfo
import com.bilimusic.data.api.netease.NeteaseApiClient
import com.bilimusic.data.api.netease.NeteaseSongParser
import com.bilimusic.data.model.*
import com.bilimusic.data.preferences.AppPreferences
import com.bilimusic.data.backup.BackupManager
import com.bilimusic.data.repository.MusicRepository
import com.bilimusic.player.MusicPlayer
import com.bilimusic.ui.theme.AnimationConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = true,
    val seedColor: Int = 0xFF6750A4.toInt(),
    val blurDegree: Float = 25f,
    val progressBarStyle: ProgressBarStyle = ProgressBarStyle.LINEAR,
    val searchSort: String = "totalrank",
    val filterKeywords: String = "",
    val filterLongVideos: Boolean = true,
    val filterLoopTitle: Boolean = true,
    val pageTransition: String = "slide",
    val playerBgPureColor: Boolean = false,
    val isFirstLaunch: Boolean = true,
    val downloadPath: String = "",
    val downloadThreadCount: Int = 3,
    // Account
    val bilibiliCookie: String = "",
    val isLoggedIn: Boolean = false,
    val userInfo: UserInfo? = null,
    val favoriteFolders: List<BilibiliFavoriteFolder> = emptyList(),
    val isLoadingFolders: Boolean = false,
    // NetEase
    val netseaseLoggedIn: Boolean = false,
    val neteaseNickname: String = "",
    val neteaseAvatar: String = "",
    val showNeteaseLoginDialog: Boolean = false,
    // QR Login
    val qrCodeUrl: String? = null,
    val qrCodeBitmap: Bitmap? = null,
    val qrCodeAuthCode: String? = null,
    val qrCodeMessage: String = "",
    val isQrPolling: Boolean = false,
    // Sleep Timer
    val sleepTimerRemaining: Long = 0L,
    val isTimerActive: Boolean = false,
    // Lyrics
    val lyricBlurAmount: Float = 8f,
    val lyricBlurCurrent: Float = 0f,
    val lyricBlurNear: Float = 0f,
    val lyricBlurMid: Float = 4f,
    val lyricBlurFar: Float = 12f,
    val lyricTextAlign: String = "center",
    val miniPlayerAlpha: Float = 0f,
    val lyricFontSize: Float = 18f,
    // Spring animation
    val springDamping: Float = 0.7f,
    val springStiffness: Float = 260f,
    val springEnabled: Boolean = true,
    // Import
    val isImportingLocal: Boolean = false,
    val importedCount: Int = 0,
    val error: String? = null,
    val errorTitle: String? = null,
    // Menu
    val showMenuSubtitle: Boolean = true,
    // Lyric scale & wrap
    val lyricScaleCurrent: Float = 1.15f,
    val lyricScaleNear: Float = 1.0f,
    val lyricMaxWidth: Float = 0.9f,
    // Background image
    val backgroundImagePath: String = "",
    val backgroundImageOpacity: Float = 0.5f,
    val backgroundImageBlur: Float = 25f,
    // Text color
    val textColorEnabled: Boolean = true,
    val customTextColor: Int = 0xFFFFFFFF.toInt(),
    // Backup
    val isBackingUp: Boolean = false,
    val isRestoring: Boolean = false,
    val backupError: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val repository: MusicRepository,
    private val musicPlayer: MusicPlayer,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var sleepTimerJob: Job? = null

    init {
        viewModelScope.launch {
            preferences.themeMode.collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }
        viewModelScope.launch {
            preferences.useDynamicColor.collect { enabled ->
                _uiState.update { it.copy(useDynamicColor = enabled) }
            }
        }
        viewModelScope.launch {
            preferences.seedColor.collect { color ->
                _uiState.update { it.copy(seedColor = color) }
            }
        }
        viewModelScope.launch {
            preferences.blurDegree.collect { degree ->
                _uiState.update { it.copy(blurDegree = degree) }
            }
        }
        viewModelScope.launch {
            preferences.progressBarStyle.collect { style ->
                _uiState.update { it.copy(progressBarStyle = style) }
            }
        }
        viewModelScope.launch {
            preferences.bilibiliCookie.collect { cookie ->
                val wasLoggedIn = _uiState.value.isLoggedIn
                _uiState.update {
                    it.copy(bilibiliCookie = cookie, isLoggedIn = cookie.isNotBlank())
                }
                // 启动时自动加载用户信息
                if (cookie.isNotBlank() && !wasLoggedIn) {
                    BilibiliApiClient.userCookie = cookie
                    loadUserInfoOnStartup(cookie)
                    launch { autoImportBilibiliFavorites(cookie) }
                }
            }
        }
        viewModelScope.launch {
            preferences.searchSort.collect { v -> _uiState.update { it.copy(searchSort = v) } }
        }
        viewModelScope.launch {
            preferences.filterLongVideos.collect { v -> _uiState.update { it.copy(filterLongVideos = v) } }
        }
        viewModelScope.launch {
            preferences.filterKeywords.collect { v -> _uiState.update { it.copy(filterKeywords = v) } }
        }
        viewModelScope.launch {
            preferences.filterLoopTitle.collect { v -> _uiState.update { it.copy(filterLoopTitle = v) } }
        }
        viewModelScope.launch {
            preferences.pageTransition.collect { transition ->
                _uiState.update { it.copy(pageTransition = transition) }
            }
        }
        viewModelScope.launch {
            preferences.playerBgPureColor.collect { pure ->
                _uiState.update { it.copy(playerBgPureColor = pure) }
            }
        }
        viewModelScope.launch {
            preferences.isFirstLaunch.collect { first ->
                _uiState.update { it.copy(isFirstLaunch = first) }
            }
        }
        viewModelScope.launch {
            preferences.downloadPath.collect { path ->
                _uiState.update { it.copy(downloadPath = path) }
            }
        }
        viewModelScope.launch {
            preferences.downloadThreadCount.collect { count ->
                _uiState.update { it.copy(downloadThreadCount = count) }
            }
        }
        viewModelScope.launch {
            preferences.lyricBlurAmount.collect { amount ->
                _uiState.update { it.copy(lyricBlurAmount = amount) }
            }
        }
        viewModelScope.launch {
            preferences.lyricBlurCurrent.collect { v ->
                _uiState.update { it.copy(lyricBlurCurrent = v) }
            }
        }
        viewModelScope.launch {
            preferences.lyricBlurNear.collect { v ->
                _uiState.update { it.copy(lyricBlurNear = v) }
            }
        }
        viewModelScope.launch {
            preferences.lyricBlurMid.collect { v ->
                _uiState.update { it.copy(lyricBlurMid = v) }
            }
        }
        viewModelScope.launch {
            preferences.lyricBlurFar.collect { v ->
                _uiState.update { it.copy(lyricBlurFar = v) }
            }
        }
        viewModelScope.launch {
            preferences.lyricTextAlign.collect { align ->
                _uiState.update { it.copy(lyricTextAlign = align) }
            }
        }
        viewModelScope.launch {
            preferences.miniPlayerAlpha.collect { alpha ->
                _uiState.update { it.copy(miniPlayerAlpha = alpha) }
            }
        }
        viewModelScope.launch {
            preferences.lyricFontSize.collect { size ->
                _uiState.update { it.copy(lyricFontSize = size) }
            }
        }
        // Spring animation
        viewModelScope.launch {
            preferences.springDamping.collect { v ->
                AnimationConfig.dampingRatio.floatValue = v
                _uiState.update { it.copy(springDamping = v) }
            }
        }
        viewModelScope.launch {
            preferences.springStiffness.collect { v ->
                AnimationConfig.stiffness.floatValue = v
                _uiState.update { it.copy(springStiffness = v) }
            }
        }
        viewModelScope.launch {
            preferences.springEnabled.collect { v ->
                AnimationConfig.springEnabled.value = v
                _uiState.update { it.copy(springEnabled = v) }
            }
        }
        viewModelScope.launch {
            preferences.showMenuSubtitle.collect { v ->
                _uiState.update { it.copy(showMenuSubtitle = v) }
            }
        }
        viewModelScope.launch {
            preferences.lyricScaleCurrent.collect { v ->
                _uiState.update { it.copy(lyricScaleCurrent = v) }
            }
        }
        viewModelScope.launch {
            preferences.lyricScaleNear.collect { v ->
                _uiState.update { it.copy(lyricScaleNear = v) }
            }
        }
        viewModelScope.launch {
            preferences.lyricMaxWidth.collect { v ->
                _uiState.update { it.copy(lyricMaxWidth = v) }
            }
        }
        // Background image prefs
        viewModelScope.launch {
            preferences.backgroundImagePath.collect { path ->
                _uiState.update { it.copy(backgroundImagePath = path) }
            }
        }
        viewModelScope.launch {
            preferences.backgroundOpacity.collect { opacity ->
                _uiState.update { it.copy(backgroundImageOpacity = opacity) }
            }
        }
        viewModelScope.launch {
            preferences.backgroundBlur.collect { blur ->
                _uiState.update { it.copy(backgroundImageBlur = blur) }
            }
        }
        // Text color
        viewModelScope.launch {
            preferences.textColorEnabled.collect { enabled ->
                _uiState.update { it.copy(textColorEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            preferences.textColor.collect { color ->
                _uiState.update { it.copy(customTextColor = color) }
            }
        }
        // NetEase account state
        viewModelScope.launch {
            preferences.neteaseCookie.collect { cookie ->
                val wasLoggedIn = _uiState.value.netseaseLoggedIn
                val loggedIn = cookie.isNotBlank()
                _uiState.update { it.copy(netseaseLoggedIn = loggedIn) }
                if (loggedIn) {
                    NeteaseApiClient.setPersistedCookies(parseCookieMapSafe(cookie))
                    try {
                        val resp = NeteaseApiClient.getCurrentUserAccount()
                        val profile = org.json.JSONObject(resp).optJSONObject("profile")
                        if (profile != null) {
                            val userId = profile.optLong("userId", 0)
                            val nickname = profile.optString("nickname", "")
                            val avatar = profile.optString("avatarUrl", "")
                            _uiState.update {
                                it.copy(neteaseNickname = nickname, neteaseAvatar = avatar)
                            }
                            preferences.setNeteaseUserId(userId)
                            if (!wasLoggedIn) {
                                launch { autoImportNeteasePlaylists(userId, nickname) }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }
        // Sleep timer monitoring - actually stops playback when time runs out
        viewModelScope.launch {
            combine(
                preferences.sleepTimerMillis,
                preferences.sleepTimerStart
            ) { millis, start -> Pair(millis, start) }.collect { (millis, start) ->
                sleepTimerJob?.cancel()
                if (millis > 0 && start > 0) {
                    sleepTimerJob = viewModelScope.launch {
                        while (isActive) {
                            val elapsed = System.currentTimeMillis() - start
                            val remaining = (millis - elapsed).coerceAtLeast(0)
                            _uiState.update {
                                it.copy(sleepTimerRemaining = remaining, isTimerActive = remaining > 0)
                            }
                            if (remaining <= 0) {
                                // Time's up! Stop playback
                                musicPlayer.stop()
                                preferences.clearSleepTimer()
                                _uiState.update { it.copy(isTimerActive = false, sleepTimerRemaining = 0) }
                                break
                            }
                            delay(1000) // Check every second
                        }
                    }
                } else {
                    _uiState.update { it.copy(isTimerActive = false, sleepTimerRemaining = 0) }
                }
            }
        }
    }

    // ===== Theme =====
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun setUseDynamicColor(enabled: Boolean) {
        viewModelScope.launch { preferences.setUseDynamicColor(enabled) }
    }

    fun setSeedColor(color: Int) {
        viewModelScope.launch { preferences.setSeedColor(color) }
    }

    // ===== Player =====
    fun setBlurDegree(degree: Float) {
        viewModelScope.launch { preferences.setBlurDegree(degree) }
    }

    fun setProgressBarStyle(style: ProgressBarStyle) {
        viewModelScope.launch { preferences.setProgressBarStyle(style) }
    }

    fun setPlayerBgPureColor(pure: Boolean) {
        viewModelScope.launch { preferences.setPlayerBgPureColor(pure) }
    }

    fun setLyricBlurAmount(amount: Float) {
        viewModelScope.launch { preferences.setLyricBlurAmount(amount) }
    }

    fun setLyricBlurCurrent(v: Float) { viewModelScope.launch { preferences.setLyricBlurCurrent(v) } }
    fun setLyricBlurNear(v: Float) { viewModelScope.launch { preferences.setLyricBlurNear(v) } }
    fun setLyricBlurMid(v: Float) { viewModelScope.launch { preferences.setLyricBlurMid(v) } }
    fun setLyricBlurFar(v: Float) { viewModelScope.launch { preferences.setLyricBlurFar(v) } }

    fun setLyricTextAlign(align: String) {
        viewModelScope.launch { preferences.setLyricTextAlign(align) }
    }

    fun setMiniPlayerAlpha(alpha: Float) {
        viewModelScope.launch { preferences.setMiniPlayerAlpha(alpha) }
    }

    fun setLyricFontSize(size: Float) {
        viewModelScope.launch { preferences.setLyricFontSize(size) }
    }

    fun setSpringDamping(v: Float) {
        AnimationConfig.dampingRatio.floatValue = v
        viewModelScope.launch { preferences.setSpringDamping(v) }
    }
    fun setSpringStiffness(v: Float) {
        AnimationConfig.stiffness.floatValue = v
        viewModelScope.launch { preferences.setSpringStiffness(v) }
    }
    fun setSpringEnabled(v: Boolean) {
        AnimationConfig.springEnabled.value = v
        viewModelScope.launch { preferences.setSpringEnabled(v) }
    }
    fun resetSpringAnimationDefaults() {
        AnimationConfig.dampingRatio.floatValue = 0.7f
        AnimationConfig.stiffness.floatValue = 260f
        AnimationConfig.springEnabled.value = true
        viewModelScope.launch { preferences.resetSpringAnimationDefaults() }
    }

    fun setShowMenuSubtitle(enabled: Boolean) {
        viewModelScope.launch { preferences.setShowMenuSubtitle(enabled) }
    }

    fun setLyricScaleCurrent(v: Float) { viewModelScope.launch { preferences.setLyricScaleCurrent(v) } }
    fun setLyricScaleNear(v: Float) { viewModelScope.launch { preferences.setLyricScaleNear(v) } }
    fun setLyricMaxWidth(v: Float) { viewModelScope.launch { preferences.setLyricMaxWidth(v) } }

    // ===== Background Image =====
    fun setBackgroundImage(path: String) {
        viewModelScope.launch {
            try {
                if (path.startsWith("content://")) {
                    val input = context.contentResolver.openInputStream(Uri.parse(path))
                    if (input != null) {
                        val dir = java.io.File(context.filesDir, "backgrounds")
                        if (!dir.exists()) dir.mkdirs()
                        val file = java.io.File(dir, "bg_image.jpg")
                        file.outputStream().use { out -> input.copyTo(out) }
                        input.close()
                        preferences.setBackgroundImagePath(file.absolutePath)
                        return@launch
                    }
                }
                preferences.setBackgroundImagePath(path)
            } catch (_: Exception) {
                preferences.setBackgroundImagePath(path)
            }
        }
    }
    fun setBackgroundImageOpacity(opacity: Float) {
        viewModelScope.launch { preferences.setBackgroundOpacity(opacity) }
    }
    fun setBackgroundImageBlur(blur: Float) {
        viewModelScope.launch { preferences.setBackgroundBlur(blur) }
    }

    // ===== Text Color =====
    fun setTextColorEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setTextColorEnabled(enabled) }
    }

    fun setCustomTextColor(color: Int) {
        viewModelScope.launch { preferences.setTextColor(color) }
    }

    fun setSearchSort(sort: String) {
        viewModelScope.launch { preferences.setSearchSort(sort) }
    }
    fun setFilterLongVideos(enabled: Boolean) {
        viewModelScope.launch { preferences.setFilterLongVideos(enabled) }
    }
    fun setFilterKeywords(keywords: String) {
        viewModelScope.launch { preferences.setFilterKeywords(keywords) }
    }
    fun setFilterLoopTitle(enabled: Boolean) {
        viewModelScope.launch { preferences.setFilterLoopTitle(enabled) }
    }
    fun setPageTransition(transition: String) {
        viewModelScope.launch { preferences.setPageTransition(transition) }
    }

    private fun loadUserInfoOnStartup(cookie: String) {
        viewModelScope.launch {
            try {
                val info = BilibiliLoginClient.getUserInfo(cookie)
                if (info != null && info.isLogin) {
                    _uiState.update { it.copy(userInfo = info, isLoggedIn = true) }
                    Log.d("SettingsVM", "Auto-loaded user info: ${info.nickname}")
                } else {
                    // Cookie过期了
                    BilibiliApiClient.userCookie = ""
                    preferences.setBilibiliCookie("")
                    _uiState.update { it.copy(bilibiliCookie = "", isLoggedIn = false) }
                }
            } catch (e: Exception) {
                Log.w("SettingsVM", "Failed to load user info on startup", e)
            }
        }
    }

    // ===== Account =====
    fun loginWithCookie(cookie: String) {
        if (cookie.isBlank()) return
        _uiState.update { it.copy(error = "正在验证登录...") }
        viewModelScope.launch {
            try {
                // 同步设置到全局API客户端
                BilibiliApiClient.userCookie = cookie
                // 保存cookie
                preferences.setBilibiliCookie(cookie)
                // 获取用户信息
                val info = BilibiliLoginClient.getUserInfo(cookie)
                if (info != null && info.isLogin) {
                    _uiState.update {
                        it.copy(
                            bilibiliCookie = cookie,
                            isLoggedIn = true,
                            userInfo = info,
                            error = "登录成功！欢迎 ${info.nickname}"
                        )
                    }
                } else {
                    BilibiliApiClient.userCookie = ""
                    _uiState.update {
                        it.copy(error = "Cookie无效或已过期，请重新获取", errorTitle = "登录失败")
                    }
                    preferences.setBilibiliCookie("")
                }
            } catch (e: Exception) {
                Log.e("SettingsVM", "login failed", e)
                _uiState.update { it.copy(error = "登录失败: ${e.localizedMessage}", errorTitle = "错误") }
            }
        }
    }

    fun logoutBilibili() {
        viewModelScope.launch {
            BilibiliApiClient.userCookie = ""
            preferences.setBilibiliCookie("")
            _uiState.update {
                it.copy(
                    bilibiliCookie = "", isLoggedIn = false,
                    userInfo = null, favoriteFolders = emptyList()
                )
            }
        }
    }

    // ===== NetEase Account =====
    fun showNeteaseLogin() {
        _uiState.update { it.copy(showNeteaseLoginDialog = true) }
    }

    fun hideNeteaseLogin() {
        _uiState.update { it.copy(showNeteaseLoginDialog = false) }
    }

    fun neteaseLoginWithCookie(cookieJson: String) {
        viewModelScope.launch {
            preferences.setNeteaseCookie(cookieJson)
            _uiState.update { it.copy(showNeteaseLoginDialog = false, netseaseLoggedIn = true) }
            // Immediately load user info
            NeteaseApiClient.setPersistedCookies(parseCookieMapSafe(cookieJson))
            try {
                val resp = NeteaseApiClient.getCurrentUserAccount()
                val profile = org.json.JSONObject(resp).optJSONObject("profile")
                if (profile != null) {
                    val nickname = profile.optString("nickname", "")
                    val avatarUrl = profile.optString("avatarUrl", "")
                    val uid = profile.optLong("userId", 0L)
                    _uiState.update { it.copy(neteaseNickname = nickname, neteaseAvatar = avatarUrl) }
                    if (uid > 0) preferences.setNeteaseUserId(uid)
                }
            } catch (_: Exception) {}
        }
    }

    fun logoutNetease() {
        viewModelScope.launch {
            preferences.setNeteaseCookie("")
            preferences.setNeteaseUserId(0L)
            NeteaseApiClient.logout()
            _uiState.update { it.copy(netseaseLoggedIn = false, neteaseNickname = "", neteaseAvatar = "") }
        }
    }

    private fun parseCookieMapSafe(json: String): Map<String, String> {
        return try {
            val obj = org.json.JSONObject(json)
            val map = mutableMapOf<String, String>()
            obj.keys().forEach { key -> map[key] = obj.getString(key) }
            map
        } catch (_: Exception) { emptyMap() }
    }

    fun importNeteasePlaylist(playlistId: Long) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(error = "正在导入网易云歌单...") }
                val resp = withContext(Dispatchers.IO) { NeteaseApiClient.getPlaylistDetail(playlistId) }
                val songs = withContext(Dispatchers.IO) { com.bilimusic.data.api.netease.NeteaseSongParser.parsePlaylistSongs(resp) }
                val root = withContext(Dispatchers.IO) { org.json.JSONObject(resp).optJSONObject("playlist") }
                val plName = root?.optString("name") ?: "网易云歌单"
                val plId = root?.optLong("id") ?: playlistId
                // 如果已经导入过这个歌单就更新，否则新建
                val existingPlaylist = repository.getAllPlaylistsOnce().find {
                    it.name == plName && it.neteasePlaylistId == plId
                }
                val targetId: String
                if (existingPlaylist != null) {
                    targetId = existingPlaylist.id
                    // 清除旧歌曲
                    val oldSongs = repository.getSongsInPlaylistOnce(targetId)
                    oldSongs.forEach { repository.removeSongFromPlaylist(targetId, it.id) }
                } else {
                    val playlist = com.bilimusic.data.model.Playlist(
                        name = plName,
                        neteasePlaylistId = plId
                    )
                    repository.insertPlaylist(playlist)
                    targetId = playlist.id
                }
                songs.forEach { song ->
                    val music = com.bilimusic.data.model.Music(
                        id = "netease_${song.id}_${targetId}",
                        title = song.name,
                        artist = song.artistName,
                        coverUrl = song.coverUrl,
                        duration = song.duration / 1000L,
                        source = "NETEASE",
                        neteaseId = song.id
                    )
                    repository.addSongToPlaylist(targetId, music)
                }
                repository.updatePlaylistSongCount(targetId)
                _uiState.update { it.copy(error = "同步完成！共 ${songs.size} 首") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "同步失败: ${e.message}") }
            }
        }
    }

    // ===== QR Login =====
    fun startQRLogin() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(qrCodeUrl = null, qrCodeMessage = "获取二维码中...", isQrPolling = false) }
                val qrData = BilibiliLoginClient.getQRCode()
                if (qrData != null) {
                    val bitmap = generateQRBitmap(qrData.url)
                    _uiState.update {
                        it.copy(
                            qrCodeUrl = qrData.url,
                            qrCodeBitmap = bitmap,
                            qrCodeAuthCode = qrData.qrcodeKey,
                            qrCodeMessage = "请使用哔哩哔哩客户端扫码",
                            isQrPolling = true
                        )
                    }
                    pollQRCodeResult(qrData.qrcodeKey)
                } else {
                    _uiState.update {
                        it.copy(
                            qrCodeMessage = "获取二维码失败（哔哩哔哩接口可能返回了空数据）",
                            qrCodeUrl = null, qrCodeBitmap = null, isQrPolling = false
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("SettingsVM", "QR login error", e)
                _uiState.update {
                    it.copy(
                        qrCodeMessage = "网络错误: ${e.localizedMessage ?: "请检查网络连接"}",
                        qrCodeBitmap = null, qrCodeUrl = null, isQrPolling = false
                    )
                }
            }
        }
    }

    private suspend fun pollQRCodeResult(qrcodeKey: String) {
        var retryCount = 0
        while (retryCount < 120) { // 最多等120秒
            delay(1000)
            val (success, message) = BilibiliLoginClient.pollQRCode(qrcodeKey)
            if (success) {
                // 同步更新cookie到state再异步保存，确保UI能拿到cookie值
                _uiState.update { it.copy(
                    bilibiliCookie = message,
                    isLoggedIn = true,
                    qrCodeMessage = "登录成功！",
                    isQrPolling = false
                ) }
                loginWithCookie(message)
                return
            }
            when {
                message.contains("过期") -> {
                    _uiState.update { it.copy(qrCodeMessage = message, isQrPolling = false) }
                    return
                }
                message.contains("等待") || message.contains("已扫描") || message.contains("确认") -> {
                    _uiState.update { it.copy(qrCodeMessage = message) }
                }
                else -> {
                    _uiState.update { it.copy(qrCodeMessage = message, isQrPolling = false) }
                    return
                }
            }
            retryCount++
        }
        _uiState.update { it.copy(qrCodeMessage = "二维码已过期", isQrPolling = false) }
    }

    private fun generateQRBitmap(url: String): Bitmap? {
        return try {
            val writer = com.google.zxing.qrcode.QRCodeWriter()
            val bitMatrix = writer.encode(url, com.google.zxing.BarcodeFormat.QR_CODE, 512, 512)
            val w = bitMatrix.width
            val h = bitMatrix.height
            val pixels = IntArray(w * h)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    pixels[y * w + x] = if (bitMatrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                }
            }
            Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        } catch (e: Exception) {
            Log.e("SettingsVM", "QR generate error", e)
            null
        }
    }

    fun loadFavoriteFolders() {
        val cookie = _uiState.value.bilibiliCookie
        if (cookie.isBlank()) return
        val uid = _uiState.value.userInfo?.uid ?: 0L

        _uiState.update { it.copy(isLoadingFolders = true) }
        viewModelScope.launch {
            try {
                val folders = repository.getFavoriteFolders(cookie, uid)
                if (folders.isEmpty()) {
                    _uiState.update {
                        it.copy(isLoadingFolders = false, error = "未找到收藏夹（请确认哔哩哔哩账号有收藏的视频）")
                    }
                } else {
                    _uiState.update { it.copy(favoriteFolders = folders, isLoadingFolders = false) }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoadingFolders = false, error = "加载收藏夹失败: ${e.message}")
                }
            }
        }
    }

    fun importFavoriteFolder(folderId: Long) {
        val cookie = _uiState.value.bilibiliCookie
        viewModelScope.launch {
            try {
                val videos = BilibiliApiClient.getFavoriteResources(cookie, folderId)
                val folderName = _uiState.value.favoriteFolders.find { it.id == folderId }?.title ?: "哔哩哔哩收藏"
                val existing = repository.getAllPlaylistsOnce().find { it.favoriteFolderId == folderId }
                val playlist = if (existing != null) existing else {
                    val p = Playlist(
                        name = folderName,
                        favoriteFolderId = folderId,
                        favoriteFolderName = folderName
                    )
                    repository.insertPlaylist(p)
                    p
                }
                videos.forEach { video ->
                    val music = Music(
                        id = video.bvid,
                        title = video.title,
                        artist = video.author,
                        coverUrl = video.coverUrl,
                        duration = video.duration * 1000,
                        bvid = video.bvid
                    )
                    repository.addSongToPlaylist(playlist.id, music)
                }
                repository.updatePlaylistSongCount(playlist.id)
                _uiState.update { it.copy(error = "导入成功！共导入 ${videos.size} 首歌曲") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "导入失败: ${e.message}") }
            }
        }
    }

    fun syncBilibiliHistory(count: Int = 200) {
        val cookie = _uiState.value.bilibiliCookie
        if (cookie.isBlank()) {
            _uiState.update { it.copy(error = "请先登录B站账号") }
            return
        }
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(error = "正在同步B站播放历史...") }
                val videos = BilibiliApiClient.getPlayHistory(cookie, count)
                if (videos.isEmpty()) {
                    _uiState.update { it.copy(error = "播放历史为空，可能cookie已过期或API返回异常") }
                    return@launch
                }
                val playlistName = "B站播放历史"
                val existing = repository.getAllPlaylistsOnce().find { it.name == playlistName && it.favoriteFolderId == null && it.neteasePlaylistId == null }
                val playlist: com.bilimusic.data.model.Playlist
                if (existing != null) {
                    playlist = existing
                } else {
                    playlist = com.bilimusic.data.model.Playlist(name = playlistName)
                    repository.insertPlaylist(playlist)
                }
                videos.forEach { video ->
                    val music = com.bilimusic.data.model.Music(
                        id = video.bvid,
                        title = video.title,
                        artist = video.author,
                        coverUrl = video.coverUrl,
                        duration = video.duration,
                        bvid = video.bvid
                    )
                    try { repository.addSongToPlaylist(playlist.id, music) } catch (_: Exception) {}
                }
                repository.updatePlaylistSongCount(playlist.id)
                _uiState.update { it.copy(error = "播放历史同步完成！共 ${videos.size} 条记录") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "同步播放历史失败: ${e.message}") }
            }
        }
    }

    private suspend fun autoImportBilibiliFavorites(cookie: String) {
        try {
            val uid = _uiState.value.userInfo?.uid ?: return
            val folders = repository.getFavoriteFolders(cookie, uid)
            val existingIds = repository.getAllPlaylistsOnce().mapNotNull { it.favoriteFolderId }.toSet()
            folders.filter { it.id !in existingIds }.forEach { folder ->
                importFavoriteFolder(folder.id)
            }
        } catch (_: Exception) {}
    }

    private suspend fun autoImportNeteasePlaylists(userId: Long, nickname: String) {
        try {
            val raw = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                NeteaseApiClient.getUserPlaylists(userId)
            }
            val playlists = com.bilimusic.data.api.netease.NeteaseSongParser.parsePlaylists(raw)
            val existingIds = repository.getAllPlaylistsOnce().mapNotNull { it.neteasePlaylistId }.toSet()
            playlists.filter { it.id !in existingIds }.forEach { pl ->
                importNeteasePlaylist(pl.id)
            }
        } catch (_: Exception) {}
    }

    // ===== Sleep Timer =====
    fun setSleepTimer(millis: Long) {
        viewModelScope.launch { preferences.setSleepTimer(millis) }
    }

    fun clearSleepTimer() {
        viewModelScope.launch { preferences.clearSleepTimer() }
    }

    // ===== Download =====
    fun getDownloadPath(): String = _uiState.value.downloadPath
    fun setDownloadPath(path: String) {
        viewModelScope.launch { preferences.setDownloadPath(path) }
        _uiState.update { it.copy(downloadPath = path) }
    }

    fun setDownloadThreadCount(count: Int) {
        viewModelScope.launch { preferences.setDownloadThreadCount(count) }
        _uiState.update { it.copy(downloadThreadCount = count) }
    }

    // ===== Local Music Import =====
    fun importLocalMusic() {
        // Check permission
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (android.content.pm.PackageManager.PERMISSION_GRANTED !=
                context.checkSelfPermission(android.Manifest.permission.READ_MEDIA_AUDIO)
            ) {
                _uiState.update { it.copy(error = "需要授予「音乐和音频」权限") }
                return
            }
        }
        _uiState.update { it.copy(isImportingLocal = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val count = scanLocalAudioFiles()
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(isImportingLocal = false, importedCount = count, error = "成功导入 $count 首本地音乐")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(isImportingLocal = false, error = "导入失败: ${e.message}")
                    }
                }
            }
        }
    }

    private suspend fun scanLocalAudioFiles(): Int {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val cursor = context.contentResolver.query(collection, projection, selection, null, null)

        var count = 0
        cursor?.use { c ->
            val idCol = c.getColumnIndex(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndex(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndex(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = c.getColumnIndex(MediaStore.Audio.Media.DURATION)
            val dataCol = c.getColumnIndex(MediaStore.Audio.Media.DATA)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val title = c.getString(titleCol) ?: "未知"
                val artist = c.getString(artistCol) ?: "未知"
                val albumId = c.getLong(albumCol)
                val duration = c.getLong(durationCol)
                val data = c.getString(dataCol) ?: continue

                val albumArtUri = Uri.parse("content://media/external/audio/albumart/$albumId")

                val music = Music(
                    id = "local_$id",
                    title = title,
                    artist = artist,
                    coverUrl = albumArtUri.toString(),
                    duration = duration,
                    url = data,
                    isLocal = true,
                    localPath = data,
                    source = "LOCAL"
                )

                repository.saveLocalMusic(music)
                count++
            }
        }
        return count
    }

    // ===== First Launch =====
    fun setFirstLaunchDone() {
        viewModelScope.launch { preferences.setFirstLaunchDone() }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
 // ===== Backup & Restore =====
 fun backupData(uri: Uri) {
     viewModelScope.launch {
         _uiState.update { it.copy(isBackingUp = true, backupError = null) }
         try {
             com.bilimusic.data.backup.BackupManager(context).backupData(uri)
             _uiState.update { it.copy(
                 isBackingUp = false,
                 error = "备份完成",
                 errorTitle = "备份成功"
             ) }
         } catch (e: Exception) {
             _uiState.update { it.copy(isBackingUp = false, backupError = e.localizedMessage) }
         }
     }
 }

 fun restoreData(uri: Uri) {
     viewModelScope.launch {
         _uiState.update { it.copy(isRestoring = true, backupError = null) }
         val result = com.bilimusic.data.backup.BackupManager(context).restoreData(uri)
         _uiState.update { it.copy(isRestoring = false) }
         when (result) {
             is com.bilimusic.data.backup.BackupManager.RestoreResult.Ok -> {
                 _uiState.update { it.copy(error = "恢复完成，应用将重启", errorTitle = "恢复成功") }
                 kotlinx.coroutines.delay(1500)
                 android.os.Process.killProcess(android.os.Process.myPid())
             }
             is com.bilimusic.data.backup.BackupManager.RestoreResult.Error -> {
                 _uiState.update { it.copy(error = result.message, errorTitle = "恢复失败") }
             }
         }
     }
 }

}
