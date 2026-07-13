package com.bilimusic.ui.screens.netease

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bilimusic.data.api.netease.NeteaseApiClient
import com.bilimusic.data.api.netease.NeteaseSong
import com.bilimusic.data.api.netease.NeteaseSongParser
import com.bilimusic.data.api.netease.NeteasePlaylist
import com.bilimusic.data.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject

data class NeteaseUiState(
    val isLoggedIn: Boolean = false,
    val userId: Long = 0L,
    val nickname: String = "",
    val avatarUrl: String = "",
    val query: String = "",
    val searchResults: List<NeteaseSong> = emptyList(),
    val isSearching: Boolean = false,
    val playlists: List<NeteasePlaylist> = emptyList(),
    val playlistSongs: List<NeteaseSong> = emptyList(),
    val selectedPlaylistId: Long = 0L,
    val isShowingPlaylistDetail: Boolean = false,
    val isLoadingPlaylist: Boolean = false,
    val error: String? = null,
    val isLoggingIn: Boolean = false,
    val loginPhone: String = "",
    val loginPassword: String = "",
    val loginCaptcha: String = "",
    val showCaptchaInput: Boolean = false,
    val loginMode: Int = 1,  // 1=captcha (default), 0=password
    val countdown: Int = 0,
    val currentSongUrl: String? = null,
    val playlistSort: String = "playCount" // default/playCount/trackCount
)

@HiltViewModel
class NeteaseViewModel @Inject constructor(
    private val preferences: AppPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(NeteaseUiState())
    val uiState: StateFlow<NeteaseUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.neteaseCookie.collect { cookie ->
                if (cookie.isNotBlank()) {
                    val kv = parseCookieMap(cookie)
                    NeteaseApiClient.setPersistedCookies(kv)
                    _uiState.update { it.copy(isLoggedIn = true) }
                    loadUserInfo()
                }
            }
        }
        viewModelScope.launch {
            preferences.neteaseUserId.collect { uid ->
                _uiState.update { it.copy(userId = uid) }
                if (uid > 0) loadPlaylists()
            }
        }
        viewModelScope.launch {
            preferences.neteasePlaylistSort.collect { sort ->
                _uiState.update { it.copy(playlistSort = sort) }
                // 如果有歌单，重新排序
                val current = _uiState.value.playlists
                if (current.isNotEmpty()) {
                    _uiState.update { it.copy(playlists = sortPlaylists(current, sort)) }
                }
            }
        }
    }

    private fun parseCookieMap(json: String): Map<String, String> {
        return try {
            val obj = JSONObject(json)
            val map = mutableMapOf<String, String>()
            obj.keys().forEach { key -> map[key] = obj.getString(key) }
            map
        } catch (_: Exception) { emptyMap() }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun search() {
        val q = _uiState.value.query.trim()
        if (q.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, error = null) }
            try {
                val resp = withContext(Dispatchers.IO) {
                    NeteaseApiClient.searchSongs(q)
                }
                val results = withContext(Dispatchers.IO) {
                    NeteaseSongParser.parseSearchResult(resp)
                }
                _uiState.update { it.copy(searchResults = results, isSearching = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isSearching = false) }
            }
        }
    }

    private fun loadUserInfo() {
        viewModelScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    NeteaseApiClient.getCurrentUserAccount()
                }
                val profile = JSONObject(resp).optJSONObject("profile")
                if (profile != null) {
                    val nickname = profile.optString("nickname", "")
                    val avatarUrl = profile.optString("avatarUrl", "")
                    val uid = profile.optLong("userId", 0L)
                    _uiState.update { it.copy(nickname = nickname, avatarUrl = avatarUrl, userId = uid) }
                    if (uid > 0) preferences.setNeteaseUserId(uid)
                }
            } catch (_: Exception) {}
        }
    }

    private fun loadPlaylists() {
        val uid = _uiState.value.userId
        if (uid <= 0) return
        viewModelScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    NeteaseApiClient.getUserPlaylists(uid)
                }
                val rawPlaylists = NeteaseSongParser.parsePlaylists(resp)
                val sorted = sortPlaylists(rawPlaylists, _uiState.value.playlistSort)
                _uiState.update { it.copy(playlists = sorted) }
            } catch (_: Exception) {}
        }
    }

    fun setPlaylistSort(sort: String) {
        viewModelScope.launch { preferences.setNeteasePlaylistSort(sort) }
    }

    private fun sortPlaylists(playlists: List<NeteasePlaylist>, sort: String): List<NeteasePlaylist> {
        return when (sort) {
            "playCount" -> playlists.sortedByDescending { it.playCount }
            "trackCount" -> playlists.sortedByDescending { it.songCount }
            else -> playlists
        }
    }

    fun showPlaylistDetail(playlistId: Long) {
        _uiState.update { it.copy(selectedPlaylistId = playlistId, isLoadingPlaylist = true) }
        viewModelScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    NeteaseApiClient.getPlaylistDetail(playlistId)
                }
                val songs = NeteaseSongParser.parsePlaylistSongs(resp)
                _uiState.update {
                    it.copy(playlistSongs = songs, isShowingPlaylistDetail = true, isLoadingPlaylist = false)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoadingPlaylist = false) }
            }
        }
    }

    fun hidePlaylistDetail() {
        _uiState.update { it.copy(isShowingPlaylistDetail = false, playlistSongs = emptyList()) }
    }

    fun loginWithPhonePassword() {
        val state = _uiState.value
        if (state.loginPhone.isBlank() || state.loginPassword.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingIn = true, error = null) }
            try {
                val resp = withContext(Dispatchers.IO) {
                    NeteaseApiClient.loginByPhone(state.loginPhone.trim(), state.loginPassword)
                }
                val code = JSONObject(resp).optInt("code")
                if (code == 200) {
                    val cookies = NeteaseApiClient.getCookies()
                    val cookieJson = JSONObject(cookies as Map<*, *>).toString()
                    preferences.setNeteaseCookie(cookieJson)
                    _uiState.update { it.copy(isLoggingIn = false, isLoggedIn = true) }
                } else {
                    val msg = JSONObject(resp).optString("message", "登录失败")
                    _uiState.update { it.copy(isLoggingIn = false, error = msg) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoggingIn = false, error = e.message) }
            }
        }
    }

    fun sendCaptcha() {
        val phone = _uiState.value.loginPhone.trim()
        if (phone.isBlank()) return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { NeteaseApiClient.sendCaptcha(phone) }
                _uiState.update { it.copy(showCaptchaInput = true, error = null) }
                // Start countdown
                for (i in 60 downTo 1) {
                    _uiState.update { it.copy(countdown = i) }
                    kotlinx.coroutines.delay(1000)
                }
                _uiState.update { it.copy(countdown = 0) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun loginWithCaptcha() {
        val state = _uiState.value
        if (state.loginPhone.isBlank() || state.loginCaptcha.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingIn = true, error = null) }
            try {
                val resp = withContext(Dispatchers.IO) {
                    NeteaseApiClient.loginByCaptcha(state.loginPhone.trim(), state.loginCaptcha.trim())
                }
                val code = JSONObject(resp).optInt("code")
                if (code == 200) {
                    val cookies = NeteaseApiClient.getCookies()
                    val cookieJson = JSONObject(cookies as Map<*, *>).toString()
                    preferences.setNeteaseCookie(cookieJson)
                    _uiState.update { it.copy(isLoggingIn = false, isLoggedIn = true) }
                } else {
                    val msg = JSONObject(resp).optString("message", "登录失败")
                    _uiState.update { it.copy(isLoggingIn = false, error = msg) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoggingIn = false, error = e.message) }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            preferences.setNeteaseCookie("")
            preferences.setNeteaseUserId(0L)
            NeteaseApiClient.logout()
            _uiState.update { NeteaseUiState() }
        }
    }

    fun setLoginPhone(phone: String) { _uiState.update { it.copy(loginPhone = phone) } }
    fun setLoginPassword(pw: String) { _uiState.update { it.copy(loginPassword = pw) } }
    fun setLoginCaptcha(code: String) { _uiState.update { it.copy(loginCaptcha = code) } }
    fun setLoginMode(mode: Int) { _uiState.update { it.copy(loginMode = mode) } }
    fun clearError() { _uiState.update { it.copy(error = null) } }
}
