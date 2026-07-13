package com.bilimusic.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.bilimusic.data.model.PlayMode
import com.bilimusic.data.model.ProgressBarStyle
import com.bilimusic.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "bilimusic_settings")

class AppPreferences(private val context: Context) {

    companion object {
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        private val KEY_SEED_COLOR = intPreferencesKey("seed_color")
        private val KEY_BLUR_DEGREE = floatPreferencesKey("blur_degree")
        private val KEY_PROGRESS_STYLE = stringPreferencesKey("progress_style")
        private val KEY_PLAY_MODE = stringPreferencesKey("play_mode")
        private val KEY_BILIBILI_COOKIE = stringPreferencesKey("bilibili_cookie")
        private val KEY_NETEASE_COOKIE = stringPreferencesKey("netease_cookie")
        private val KEY_SLEEP_TIMER_MILLIS = longPreferencesKey("sleep_timer_millis")
        private val KEY_SLEEP_TIMER_START = longPreferencesKey("sleep_timer_start")
        private val KEY_LAST_PLAYED_SONG_ID = stringPreferencesKey("last_played_song_id")
        private val KEY_LAST_PLAYED_POSITION = longPreferencesKey("last_played_position")
        private val KEY_LAST_PLAYLIST_ID = stringPreferencesKey("last_playlist_id")
        private val KEY_PLAYER_BG_PURE_COLOR = booleanPreferencesKey("player_bg_pure_color")
        private val KEY_DOWNLOAD_PATH = stringPreferencesKey("download_path")
        private val KEY_AUDIO_QUALITY = stringPreferencesKey("audio_quality")
        private val KEY_FIRST_LAUNCH = booleanPreferencesKey("first_launch")
        private val KEY_SPRING_DAMPING = floatPreferencesKey("spring_damping")
        private val KEY_SPRING_STIFFNESS = floatPreferencesKey("spring_stiffness")
        private val KEY_SPRING_ENABLED = booleanPreferencesKey("spring_enabled")
        private val KEY_NETEASE_PLAYLIST_SORT = stringPreferencesKey("netease_playlist_sort")
        private val KEY_BACKGROUND_IMAGE_PATH = stringPreferencesKey("background_image_path")
        private val KEY_BACKGROUND_OPACITY = floatPreferencesKey("background_opacity")
        private val KEY_BACKGROUND_BLUR = floatPreferencesKey("background_blur")
        private val KEY_TEXT_COLOR = intPreferencesKey("text_color")
        private val KEY_TEXT_COLOR_ENABLED = booleanPreferencesKey("text_color_enabled")
    }

    // ===== Theme =====
    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        when (prefs[KEY_THEME_MODE]) {
            "light" -> ThemeMode.LIGHT
            "dark" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode.name.lowercase()
        }
    }

    val useDynamicColor: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_USE_DYNAMIC_COLOR] ?: true
    }

    suspend fun setUseDynamicColor(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USE_DYNAMIC_COLOR] = enabled
        }
    }

    val seedColor: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_SEED_COLOR] ?: 0xFF6750A4.toInt()
    }

    suspend fun setSeedColor(color: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SEED_COLOR] = color
        }
    }

    // ===== Player =====
    val blurDegree: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_BLUR_DEGREE] ?: 25f
    }

    suspend fun setBlurDegree(degree: Float) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BLUR_DEGREE] = degree
        }
    }

    val progressBarStyle: Flow<ProgressBarStyle> = context.dataStore.data.map { prefs ->
        try {
            ProgressBarStyle.valueOf(prefs[KEY_PROGRESS_STYLE] ?: "LINEAR")
        } catch (e: Exception) {
            ProgressBarStyle.LINEAR
        }
    }

    suspend fun setProgressBarStyle(style: ProgressBarStyle) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PROGRESS_STYLE] = style.name
        }
    }

    val playMode: Flow<PlayMode> = context.dataStore.data.map { prefs ->
        try {
            PlayMode.valueOf(prefs[KEY_PLAY_MODE] ?: "LOOP")
        } catch (e: Exception) {
            PlayMode.LOOP
        }
    }

    suspend fun setPlayMode(mode: PlayMode) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PLAY_MODE] = mode.name
        }
    }

    // ===== Account =====
    val bilibiliCookie: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_BILIBILI_COOKIE] ?: ""
    }

    suspend fun setBilibiliCookie(cookie: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BILIBILI_COOKIE] = cookie
        }
    }

    // ===== NetEase =====
    val neteaseCookie: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_NETEASE_COOKIE] ?: ""
    }

    suspend fun setNeteaseCookie(cookie: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_NETEASE_COOKIE] = cookie
        }
    }

    val neteaseUserId: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[longPreferencesKey("netease_user_id")] ?: 0L
    }

    suspend fun setNeteaseUserId(id: Long) {
        context.dataStore.edit { prefs ->
            prefs[longPreferencesKey("netease_user_id")] = id
        }
    }

    // ===== Search Sort =====
    private val KEY_SEARCH_SORT = stringPreferencesKey("search_sort")

    val searchSort: Flow<String> = context.dataStore.data.map { it[KEY_SEARCH_SORT] ?: "totalrank" }
    suspend fun setSearchSort(sort: String) { context.dataStore.edit { it[KEY_SEARCH_SORT] = sort } }

    // ===== Search Filters =====
    private val KEY_FILTER_LONG = booleanPreferencesKey("filter_long_videos")
    private val KEY_FILTER_LOOP = booleanPreferencesKey("filter_loop_title")

    val filterLongVideos: Flow<Boolean> = context.dataStore.data.map { it[KEY_FILTER_LONG] ?: true }
    val filterLoopTitle: Flow<Boolean> = context.dataStore.data.map { it[KEY_FILTER_LOOP] ?: true }

    suspend fun setFilterLongVideos(enabled: Boolean) { context.dataStore.edit { it[KEY_FILTER_LONG] = enabled } }
    suspend fun setFilterLoopTitle(enabled: Boolean) { context.dataStore.edit { it[KEY_FILTER_LOOP] = enabled } }

    private val KEY_FILTER_KEYWORDS = stringPreferencesKey("filter_keywords")
    val filterKeywords: Flow<String> = context.dataStore.data.map { it[KEY_FILTER_KEYWORDS] ?: "" }
    suspend fun setFilterKeywords(keywords: String) { context.dataStore.edit { it[KEY_FILTER_KEYWORDS] = keywords } }

    // ===== Page Transition =====
    private val KEY_PAGE_TRANSITION = stringPreferencesKey("page_transition")

    val pageTransition: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_PAGE_TRANSITION] ?: "slide"
    }

    suspend fun setPageTransition(transition: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PAGE_TRANSITION] = transition
        }
    }

    // ===== Sleep Timer =====
    val sleepTimerMillis: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[KEY_SLEEP_TIMER_MILLIS] ?: 0L
    }

    val sleepTimerStart: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[KEY_SLEEP_TIMER_START] ?: 0L
    }

    suspend fun setSleepTimer(millis: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SLEEP_TIMER_MILLIS] = millis
            prefs[KEY_SLEEP_TIMER_START] = System.currentTimeMillis()
        }
    }

    suspend fun clearSleepTimer() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_SLEEP_TIMER_MILLIS)
            prefs.remove(KEY_SLEEP_TIMER_START)
        }
    }

    // ===== Player State =====
    val lastPlayedSongId: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_PLAYED_SONG_ID] ?: ""
    }

    suspend fun setLastPlayedSongId(id: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_PLAYED_SONG_ID] = id
        }
    }

    val lastPlayedPosition: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_PLAYED_POSITION] ?: 0L
    }

    suspend fun setLastPlayedPosition(position: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_PLAYED_POSITION] = position
        }
    }

    val lastPlaylistId: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_PLAYLIST_ID] ?: ""
    }

    suspend fun setLastPlaylistId(id: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_PLAYLIST_ID] = id
        }
    }

    // ===== Player Background =====
    val playerBgPureColor: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_PLAYER_BG_PURE_COLOR] ?: false
    }

    suspend fun setPlayerBgPureColor(pure: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PLAYER_BG_PURE_COLOR] = pure
        }
    }

    // ===== Lyrics =====
    private val KEY_LYRIC_BLUR_AMOUNT = floatPreferencesKey("lyric_blur_amount")
    val lyricBlurAmount: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_LYRIC_BLUR_AMOUNT] ?: 8f
    }

    suspend fun setLyricBlurAmount(amount: Float) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LYRIC_BLUR_AMOUNT] = amount
        }
    }

    private val KEY_LYRIC_BLUR_CURRENT = floatPreferencesKey("lyric_blur_current")
    val lyricBlurCurrent: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_LYRIC_BLUR_CURRENT] ?: 0f
    }
    suspend fun setLyricBlurCurrent(v: Float) {
        context.dataStore.edit { prefs -> prefs[KEY_LYRIC_BLUR_CURRENT] = v }
    }
    private val KEY_LYRIC_BLUR_NEAR = floatPreferencesKey("lyric_blur_near")
    val lyricBlurNear: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_LYRIC_BLUR_NEAR] ?: 0f
    }
    suspend fun setLyricBlurNear(v: Float) {
        context.dataStore.edit { prefs -> prefs[KEY_LYRIC_BLUR_NEAR] = v }
    }
    private val KEY_LYRIC_BLUR_MID = floatPreferencesKey("lyric_blur_mid")
    val lyricBlurMid: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_LYRIC_BLUR_MID] ?: 4f
    }
    suspend fun setLyricBlurMid(v: Float) {
        context.dataStore.edit { prefs -> prefs[KEY_LYRIC_BLUR_MID] = v }
    }
    private val KEY_LYRIC_BLUR_FAR = floatPreferencesKey("lyric_blur_far")
    val lyricBlurFar: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_LYRIC_BLUR_FAR] ?: 12f
    }
    suspend fun setLyricBlurFar(v: Float) {
        context.dataStore.edit { prefs -> prefs[KEY_LYRIC_BLUR_FAR] = v }
    }

    private val KEY_LYRIC_SCALE_CURRENT = floatPreferencesKey("lyric_scale_current")
    val lyricScaleCurrent: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_LYRIC_SCALE_CURRENT] ?: 1.15f
    }
    suspend fun setLyricScaleCurrent(v: Float) {
        context.dataStore.edit { prefs -> prefs[KEY_LYRIC_SCALE_CURRENT] = v }
    }
    private val KEY_LYRIC_SCALE_NEAR = floatPreferencesKey("lyric_scale_near")
    val lyricScaleNear: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_LYRIC_SCALE_NEAR] ?: 1.0f
    }
    suspend fun setLyricScaleNear(v: Float) {
        context.dataStore.edit { prefs -> prefs[KEY_LYRIC_SCALE_NEAR] = v }
    }
    private val KEY_LYRIC_MAX_WIDTH = floatPreferencesKey("lyric_max_width")
    val lyricMaxWidth: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_LYRIC_MAX_WIDTH] ?: 0.9f
    }
    suspend fun setLyricMaxWidth(v: Float) {
        context.dataStore.edit { prefs -> prefs[KEY_LYRIC_MAX_WIDTH] = v }
    }

    private val KEY_LYRIC_TEXT_ALIGN = stringPreferencesKey("lyric_text_align")
    val lyricTextAlign: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_LYRIC_TEXT_ALIGN] ?: "center"
    }

    suspend fun setLyricTextAlign(align: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LYRIC_TEXT_ALIGN] = align
        }
    }

    // ===== Mini Player =====
    private val KEY_MINI_PLAYER_ALPHA = floatPreferencesKey("mini_player_alpha")
    val miniPlayerAlpha: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_MINI_PLAYER_ALPHA] ?: 0f
    }

    suspend fun setMiniPlayerAlpha(alpha: Float) {
        context.dataStore.edit { prefs ->
            prefs[KEY_MINI_PLAYER_ALPHA] = alpha
        }
    }

    // ===== Lyric Font Size =====
    private val KEY_LYRIC_FONT_SIZE = floatPreferencesKey("lyric_font_size")
    val lyricFontSize: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_LYRIC_FONT_SIZE] ?: 18f
    }

    suspend fun setLyricFontSize(size: Float) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LYRIC_FONT_SIZE] = size
        }
    }

    // ===== Download =====
    val downloadPath: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_DOWNLOAD_PATH] ?: "/storage/emulated/0/Music/BiliMusic"
    }

    suspend fun setDownloadPath(path: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DOWNLOAD_PATH] = path
        }
    }

    private val KEY_DOWNLOAD_THREAD_COUNT = intPreferencesKey("download_thread_count")
    val downloadThreadCount: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_DOWNLOAD_THREAD_COUNT] ?: 3
    }

    suspend fun setDownloadThreadCount(count: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DOWNLOAD_THREAD_COUNT] = count.coerceIn(1, 5)
        }
    }

    // ===== Audio Quality =====
    val audioQuality: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUDIO_QUALITY] ?: "lossless"
    }
    suspend fun setAudioQuality(quality: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUDIO_QUALITY] = quality
        }
    }

    // ===== Spring Animation =====
    val springDamping: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_SPRING_DAMPING] ?: 0.7f
    }
    suspend fun setSpringDamping(v: Float) {
        context.dataStore.edit { prefs -> prefs[KEY_SPRING_DAMPING] = v }
    }
    val springStiffness: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_SPRING_STIFFNESS] ?: 260f
    }
    suspend fun setSpringStiffness(v: Float) {
        context.dataStore.edit { prefs -> prefs[KEY_SPRING_STIFFNESS] = v }
    }
    val springEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SPRING_ENABLED] ?: true
    }
    suspend fun setSpringEnabled(v: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_SPRING_ENABLED] = v }
    }

    suspend fun resetSpringAnimationDefaults() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_SPRING_DAMPING)
            prefs.remove(KEY_SPRING_STIFFNESS)
            prefs.remove(KEY_SPRING_ENABLED)
        }
    }

    // ===== Menu Subtitle =====
    private val KEY_SHOW_MENU_SUBTITLE = booleanPreferencesKey("show_menu_subtitle")
    val showMenuSubtitle: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SHOW_MENU_SUBTITLE] ?: true
    }
    suspend fun setShowMenuSubtitle(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SHOW_MENU_SUBTITLE] = enabled
        }
    }

    // ===== First Launch =====
    val isFirstLaunch: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_FIRST_LAUNCH] ?: true
    }

    suspend fun setFirstLaunchDone() {
        context.dataStore.edit { prefs ->
            prefs[KEY_FIRST_LAUNCH] = false
        }
    }

    // ===== NetEase Playlist Sort =====
    val neteasePlaylistSort: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_NETEASE_PLAYLIST_SORT] ?: "default"
    }

    suspend fun setNeteasePlaylistSort(sort: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_NETEASE_PLAYLIST_SORT] = sort
        }
    }

    // ===== Background Image =====
    val backgroundImagePath: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_BACKGROUND_IMAGE_PATH] ?: ""
    }

    suspend fun setBackgroundImagePath(path: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BACKGROUND_IMAGE_PATH] = path
        }
    }

    val backgroundOpacity: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_BACKGROUND_OPACITY] ?: 0.5f
    }

    suspend fun setBackgroundOpacity(opacity: Float) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BACKGROUND_OPACITY] = opacity
        }
    }

    val backgroundBlur: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_BACKGROUND_BLUR] ?: 25f
    }

    suspend fun setBackgroundBlur(blur: Float) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BACKGROUND_BLUR] = blur
        }
    }

    // ===== Text Color =====
    val textColorEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_TEXT_COLOR_ENABLED] ?: true
    }

    suspend fun setTextColorEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TEXT_COLOR_ENABLED] = enabled
        }
    }

    val textColor: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_TEXT_COLOR] ?: 0xFFFFFFFF.toInt()
    }

    suspend fun setTextColor(color: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TEXT_COLOR] = color
        }
    }
}
