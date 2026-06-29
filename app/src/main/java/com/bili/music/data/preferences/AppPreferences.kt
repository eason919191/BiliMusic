package com.bili.music.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.bili.music.data.model.PlayMode
import com.bili.music.data.model.ProgressBarStyle
import com.bili.music.data.model.ThemeMode
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
        private val KEY_SLEEP_TIMER_MILLIS = longPreferencesKey("sleep_timer_millis")
        private val KEY_SLEEP_TIMER_START = longPreferencesKey("sleep_timer_start")
        private val KEY_LAST_PLAYED_SONG_ID = stringPreferencesKey("last_played_song_id")
        private val KEY_LAST_PLAYED_POSITION = longPreferencesKey("last_played_position")
        private val KEY_LAST_PLAYLIST_ID = stringPreferencesKey("last_playlist_id")
        private val KEY_PLAYER_BG_PURE_COLOR = booleanPreferencesKey("player_bg_pure_color")
        private val KEY_DOWNLOAD_PATH = stringPreferencesKey("download_path")
        private val KEY_FIRST_LAUNCH = booleanPreferencesKey("first_launch")
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

    // ===== Account (encrypted) =====
    val encryptedCookieStore = EncryptedCookieStore(context)

    /** Bilibili cookie — stored in EncryptedSharedPreferences (AES-256 GCM). */
    val bilibiliCookie: Flow<String> = encryptedCookieStore.cookie

    /** Current cookie value for synchronous reads. */
    fun getCookieSync(): String = encryptedCookieStore.get()

    suspend fun setBilibiliCookie(cookie: String) {
        encryptedCookieStore.set(cookie)
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

    // ===== Download =====
    val downloadPath: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_DOWNLOAD_PATH] ?: ""
    }

    suspend fun setDownloadPath(path: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DOWNLOAD_PATH] = path
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
}
