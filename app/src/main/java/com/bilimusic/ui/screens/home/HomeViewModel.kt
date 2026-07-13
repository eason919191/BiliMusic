package com.bilimusic.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bilimusic.data.api.netease.NeteaseApiClient
import com.bilimusic.data.api.netease.NeteaseSongParser
import com.bilimusic.data.api.netease.NeteaseSong
import com.bilimusic.data.api.netease.NeteaseArtist
import com.bilimusic.data.api.netease.NeteaseAlbum
import com.bilimusic.data.api.netease.NeteasePlaylist
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

data class HomeUiState(
    val isNeteaseLoggedIn: Boolean = false,
    val dailyRecommendSongs: List<NeteaseSong> = emptyList(),
    val personalizedSongs: List<NeteaseSong> = emptyList(),
    val personalizedPlaylists: List<NeteasePlaylist> = emptyList(),
    val hotSongs: List<NeteaseSong> = emptyList(),
    val radarSongs: List<NeteaseSong> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        checkLoginAndLoad()
    }

    private fun checkLoginAndLoad() {
        viewModelScope.launch {
            val loggedIn = NeteaseApiClient.hasLogin()
            _uiState.value = _uiState.value.copy(isNeteaseLoggedIn = loggedIn)
            if (loggedIn) {
                loadRecommendations()
            }
        }
    }

    fun loadRecommendations() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                withContext(Dispatchers.IO) {
                    val dailySongs = loadDailyRecommend()
                    val playlists = loadPersonalizedPlaylists()
                    val songs = loadPersonalizedSongs()
                    val hotSongs = loadHotSongs()
                    val radarSongs = loadRadarSongs()

                    _uiState.value = _uiState.value.copy(
                        dailyRecommendSongs = dailySongs,
                        personalizedPlaylists = playlists,
                        personalizedSongs = songs,
                        hotSongs = hotSongs,
                        radarSongs = radarSongs,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "加载失败"
                )
            }
        }
    }

    private fun loadDailyRecommend(): List<NeteaseSong> {
        return try {
            val json = NeteaseApiClient.getDailyRecommendSongs()
            val obj = JSONObject(json)
            if (obj.optInt("code") == 200) {
                val data = obj.optJSONObject("data") ?: return emptyList()
                val arr = data.optJSONArray("dailySongs") ?: return emptyList()
                NeteaseSongParser.parseTracksArray(arr)
            } else emptyList()
        } catch (_: Exception) { emptyList() }
    }

    private fun loadPersonalizedPlaylists(): List<NeteasePlaylist> {
        return try {
            val json = NeteaseApiClient.getPersonalizedPlaylists(6)
            val obj = JSONObject(json)
            if (obj.optInt("code") == 200) {
                val arr = obj.optJSONArray("result") ?: return emptyList()
                val list = mutableListOf<NeteasePlaylist>()
                for (i in 0 until arr.length()) {
                    val p = arr.optJSONObject(i) ?: continue
                    list.add(NeteasePlaylist(
                        id = p.optLong("id"),
                        name = p.optString("name"),
                        coverUrl = p.optString("picUrl"),
                        songCount = p.optInt("trackCount"),
                        userId = 0,
                        nickname = "",
                        playCount = p.optLong("playCount")
                    ))
                }
                list
            } else emptyList()
        } catch (_: Exception) { emptyList() }
    }

    private fun loadPersonalizedSongs(): List<NeteaseSong> {
        return try {
            val json = NeteaseApiClient.callWeApi("/personalized/newsong")
            val obj = JSONObject(json)
            if (obj.optInt("code") == 200) {
                val arr = obj.optJSONArray("result") ?: return emptyList()
                val songs = mutableListOf<NeteaseSong>()
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val song = item.optJSONObject("song") ?: continue
                    val id = song.optLong("id", 0)
                    val name = song.optString("name", "")
                    val ar = song.optJSONArray("ar") ?: JSONArray()
                    val artistList = mutableListOf<NeteaseArtist>()
                    for (j in 0 until ar.length()) {
                        val a = ar.optJSONObject(j) ?: continue
                        artistList.add(NeteaseArtist(id = a.optLong("id", 0), name = a.optString("name", "")))
                    }
                    val al = song.optJSONObject("al")
                    val album = if (al != null) NeteaseAlbum(
                        id = al.optLong("id", 0),
                        name = al.optString("name", ""),
                        picUrl = al.optString("picUrl", "")
                    ) else null
                    songs.add(NeteaseSong(
                        id = id,
                        name = name,
                        artists = artistList,
                        album = album,
                        duration = song.optLong("dt", 0),
                        coverUrl = al?.optString("picUrl"),
                        genre = "",
                        language = "",
                        pop = song.optDouble("pop", 0.0).toFloat()
                    ))
                }
                songs
            } else emptyList()
        } catch (_: Exception) { emptyList() }
    }

    private fun loadHotSongs(): List<NeteaseSong> {
        return try {
            val json = NeteaseApiClient.searchSongs("热歌", limit = 30)
            val obj = JSONObject(json)
            if (obj.optInt("code") == 200) {
                val result = obj.optJSONObject("result") ?: return emptyList()
                val arr = result.optJSONArray("songs") ?: return emptyList()
                NeteaseSongParser.parseTracksArray(arr)
            } else emptyList()
        } catch (_: Exception) { emptyList() }
    }

    private fun loadRadarSongs(): List<NeteaseSong> {
        return try {
            val json = NeteaseApiClient.searchSongs("私人雷达", limit = 30)
            val obj = JSONObject(json)
            if (obj.optInt("code") == 200) {
                val result = obj.optJSONObject("result") ?: return emptyList()
                val arr = result.optJSONArray("songs") ?: return emptyList()
                NeteaseSongParser.parseTracksArray(arr)
            } else emptyList()
        } catch (_: Exception) { emptyList() }
    }
}
