package com.bilimusic.data.api.netease

import org.json.JSONArray
import org.json.JSONObject

data class NeteaseSong(
    val id: Long,
    val name: String,
    val artists: List<NeteaseArtist> = emptyList(),
    val album: NeteaseAlbum? = null,
    val duration: Long = 0L,
    val coverUrl: String? = null
) {
    val artistName: String get() = artists.joinToString("/") { it.name }
}

data class NeteaseArtist(
    val id: Long,
    val name: String
)

data class NeteaseAlbum(
    val id: Long,
    val name: String,
    val picUrl: String? = null
)

data class NeteasePlaylist(
    val id: Long,
    val name: String,
    val coverUrl: String? = null,
    val songCount: Int = 0,
    val userId: Long = 0,
    val nickname: String = ""
)

data class NeteaseLyricLine(
    val timeMs: Long,
    val text: String,
    val endTimeMs: Long = 0L,
    val words: List<NeteaseWordTiming>? = null
)

data class NeteaseWordTiming(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val charCount: Int = 0
)

object NeteaseSongParser {
    private fun parseDuration(s: org.json.JSONObject): Long {
        val d = s.optLong("duration")
        if (d > 0) return d
        val dt = s.optLong("dt")
        if (dt > 0) return dt
        val dur = s.optInt("durationTime")
        if (dur > 0) return dur.toLong() * 1000
        return 0L
    }

    fun parseSearchResult(json: String): List<NeteaseSong> {
        val root = org.json.JSONObject(json)
        if (root.optInt("code") != 200) return emptyList()
        val result = root.optJSONObject("result") ?: return emptyList()
        val songs = result.optJSONArray("songs") ?: return emptyList()
        val list = mutableListOf<NeteaseSong>()
        for (i in 0 until songs.length()) {
            val s = songs.getJSONObject(i)
            val artists = mutableListOf<NeteaseArtist>()
            val arr = s.optJSONArray("artists") ?: s.optJSONArray("ar")
            if (arr != null) {
                for (j in 0 until arr.length()) {
                    val a = arr.getJSONObject(j)
                    artists.add(NeteaseArtist(a.optLong("id"), a.optString("name")))
                }
            }
            val album = s.optJSONObject("album") ?: s.optJSONObject("al")
            val albumObj = if (album != null) NeteaseAlbum(
                album.optLong("id"),
                album.optString("name"),
                album.optString("picUrl") ?: album.optString("pic")
            ) else null

            list.add(NeteaseSong(
                id = s.optLong("id"),
                name = s.optString("name"),
                artists = artists,
                album = albumObj,
                duration = parseDuration(s),
                coverUrl = albumObj?.picUrl
            ))
        }
        return list
    }

    fun parseSongDetail(json: String): List<NeteaseSong> {
        val root = org.json.JSONObject(json)
        if (root.optInt("code") != 200) return emptyList()
        val songs = root.optJSONArray("songs") ?: return emptyList()
        val list = mutableListOf<NeteaseSong>()
        for (i in 0 until songs.length()) {
            val s = songs.getJSONObject(i)
            val artists = mutableListOf<NeteaseArtist>()
            val arr = s.optJSONArray("ar")
            if (arr != null) {
                for (j in 0 until arr.length()) {
                    val a = arr.getJSONObject(j)
                    artists.add(NeteaseArtist(a.optLong("id"), a.optString("name")))
                }
            }
            val album = s.optJSONObject("al")
            val albumObj = if (album != null) NeteaseAlbum(
                album.optLong("id"),
                album.optString("name"),
                album.optString("picUrl")
            ) else null

            list.add(NeteaseSong(
                id = s.optLong("id"),
                name = s.optString("name"),
                artists = artists,
                album = albumObj,
                duration = parseDuration(s),
                coverUrl = albumObj?.picUrl
            ))
        }
        return list
    }

    fun parseSongUrlRaw(json: String): String? {
        val root = org.json.JSONObject(json)
        if (root.optInt("code") != 200) return null
        val data = root.optJSONArray("data") ?: return null
        if (data.length() == 0) return null
        val first = data.getJSONObject(0)
        if (first.optInt("code") != 200) return null
        val url = first.optString("url")
        if (url.isBlank()) return null
        // Check if it's a trial clip
        val freeTrialInfo = first.optJSONObject("freeTrialInfo")
        val endType = first.optInt("endType", 0)
        // endType=1 or freeTrialInfo != null means only preview available (no VIP)
        if (freeTrialInfo != null || endType == 1) {
            return "__VIP_ONLY__"
        }
        return url
    }

    fun parsePlaylists(json: String): List<NeteasePlaylist> {
        val root = org.json.JSONObject(json)
        if (root.optInt("code") != 200) return emptyList()
        val playlist = root.optJSONArray("playlist") ?: return emptyList()
        val list = mutableListOf<NeteasePlaylist>()
        for (i in 0 until playlist.length()) {
            val p = playlist.getJSONObject(i)
            val creator = p.optJSONObject("creator")
            list.add(NeteasePlaylist(
                id = p.optLong("id"),
                name = p.optString("name"),
                coverUrl = p.optString("coverImgUrl"),
                songCount = p.optInt("trackCount"),
                userId = creator?.optLong("userId") ?: 0,
                nickname = creator?.optString("nickname") ?: ""
            ))
        }
        return list
    }

    fun parsePlaylistSongs(json: String): List<NeteaseSong> {
        val root = org.json.JSONObject(json)
        if (root.optInt("code") != 200) return emptyList()
        val pl = root.optJSONObject("playlist") ?: return emptyList()
        val trackIds = pl.optJSONArray("trackIds") ?: return emptyList()
        // Try trackIds first
        val ids = mutableListOf<Long>()
        for (i in 0 until trackIds.length()) {
            ids.add(trackIds.getJSONObject(i).optLong("id"))
        }
        // Also try tracks directly
        val tracks = pl.optJSONArray("tracks") ?: return emptyList()
        return parseTracksArray(tracks)
    }

    fun parseTracksArray(tracks: JSONArray): List<NeteaseSong> {
        val list = mutableListOf<NeteaseSong>()
        for (i in 0 until tracks.length()) {
            val s = tracks.getJSONObject(i)
            val artists = mutableListOf<NeteaseArtist>()
            val arr = s.optJSONArray("ar")
            if (arr != null) {
                for (j in 0 until arr.length()) {
                    val a = arr.getJSONObject(j)
                    artists.add(NeteaseArtist(a.optLong("id"), a.optString("name")))
                }
            }
            val album = s.optJSONObject("al")
            val albumObj = if (album != null) NeteaseAlbum(
                album.optLong("id"), album.optString("name"), album.optString("picUrl")
            ) else null

            list.add(NeteaseSong(
                id = s.optLong("id"),
                name = s.optString("name"),
                artists = artists,
                album = albumObj,
                duration = parseDuration(s),
                coverUrl = albumObj?.picUrl
            ))
        }
        return list
    }

    private fun parseUserId(json: String): Long {
        val root = org.json.JSONObject(json)
        if (root.optInt("code") != 200) return 0L
        val profile = root.optJSONObject("profile")
        return profile?.optLong("userId") ?: 0L
    }
}

private fun SongObj_getDuration(s: org.json.JSONObject): Long {
    val d = s.optLong("duration")
    if (d > 0) return d
    val dt = s.optLong("dt")
    if (dt > 0) return dt
    val dur = s.optInt("durationTime")
    if (dur > 0) return dur.toLong() * 1000
    return 0L
}

fun parseArtistSongs(json: String): List<NeteaseSong> {
    val root = org.json.JSONObject(json)
    if (root.optInt("code") != 200) return emptyList()
    val songs = root.optJSONArray("songs") ?: return emptyList()
    val list = mutableListOf<NeteaseSong>()
    for (i in 0 until songs.length()) {
        val s = songs.getJSONObject(i)
        val artists = mutableListOf<NeteaseArtist>()
        val arr = s.optJSONArray("ar")
        if (arr != null) {
            for (j in 0 until arr.length()) {
                val a = arr.getJSONObject(j)
                artists.add(NeteaseArtist(a.optLong("id"), a.optString("name")))
            }
        }
        val album = s.optJSONObject("al")
        val albumObj = if (album != null) NeteaseAlbum(
            album.optLong("id"), album.optString("name"), album.optString("picUrl")
        ) else null
        val dur = SongObj_getDuration(s)
        list.add(NeteaseSong(
            id = s.optLong("id"),
            name = s.optString("name"),
            artists = artists,
            album = albumObj,
            duration = dur,
            coverUrl = albumObj?.picUrl
        ))
    }
    return list
}

fun parseSearchPlaylists(json: String): List<NeteasePlaylist> {
    val root = org.json.JSONObject(json)
    if (root.optInt("code") != 200) return emptyList()
    val result = root.optJSONObject("result") ?: return emptyList()
    val playlists = result.optJSONArray("playlists") ?: return emptyList()
    val list = mutableListOf<NeteasePlaylist>()
    for (i in 0 until playlists.length()) {
        val p = playlists.getJSONObject(i)
        val creator = p.optJSONObject("creator")
        list.add(NeteasePlaylist(
            id = p.optLong("id"),
            name = p.optString("name"),
            coverUrl = p.optString("coverImgUrl"),
            songCount = p.optInt("trackCount"),
            userId = creator?.optLong("userId") ?: 0,
            nickname = creator?.optString("nickname") ?: ""
        ))
    }
    return list
}

fun parseSearchArtists(json: String): List<NeteaseSong> {
    val root = org.json.JSONObject(json)
    if (root.optInt("code") != 200) return emptyList()
    val result = root.optJSONObject("result") ?: return emptyList()
    val artists = result.optJSONArray("artists") ?: return emptyList()
    val list = mutableListOf<NeteaseSong>()
    for (i in 0 until artists.length()) {
        val a = artists.getJSONObject(i)
        val name = a.optString("name")
        val id = a.optLong("id")
        val picUrl = a.optString("picUrl").ifEmpty { a.optString("img1v1Url").ifEmpty { "" } }
        val alias = a.optJSONArray("alias")
        val aliasStr = if (alias != null && alias.length() > 0) alias.optString(0) else ""
        val nameDisplay = if (aliasStr.isNotBlank()) "$name ($aliasStr)" else name
        list.add(NeteaseSong(id = id, name = nameDisplay, artists = listOf(NeteaseArtist(id, nameDisplay)),
            album = NeteaseAlbum(0, "歌手"), coverUrl = picUrl, duration = 0L))
    }
    return list
}
