package com.bilimusic.data.api.netease

import android.util.Log
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.TimeUnit

object NeteaseApiClient {
    private const val TAG = "NeteaseApi"
    private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()
    private val cookieLock = Any()

    @Volatile
    var persistedCookies: Map<String, String> = emptyMap()
        private set

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .cookieJar(object : CookieJar {
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    val host = url.host
                    synchronized(cookieLock) {
                        val list = cookieStore.getOrPut(host) { mutableListOf() }
                        list.removeAll { c -> cookies.any { it.name == c.name } }
                        list.addAll(cookies)
                    }
                }
                override fun loadForRequest(url: HttpUrl): List<Cookie> {
                    return synchronized(cookieLock) {
                        cookieStore[url.host]?.toList() ?: emptyList()
                    }
                }
            })
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; BiliMusic) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                    .addHeader("Referer", "https://music.163.com")
                    .addHeader("Accept", "*/*")
                    .addHeader("Accept-Language", "zh-CN,zh-Hans;q=0.9")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    fun hasLogin(): Boolean = !persistedCookies["MUSIC_U"].isNullOrBlank()

    fun setPersistedCookies(cookies: Map<String, String>) {
        val m = cookies.toMutableMap()
        m.putIfAbsent("os", "pc")
        m.putIfAbsent("appver", "8.10.35")
        persistedCookies = m.toMap()
        seedCookieJarFromPersisted("music.163.com")
        seedCookieJarFromPersisted("interface.music.163.com")
    }

    fun getPersistedCookieHeader(): String? {
        if (persistedCookies.isEmpty()) return null
        return persistedCookies.entries.joinToString("; ") { (k, v) -> "$k=$v" }
    }

    private fun seedCookieJarFromPersisted(host: String) {
        val snapshot = persistedCookies
        synchronized(cookieLock) {
            val list = cookieStore.getOrPut(host) { mutableListOf() }
            snapshot.forEach { (name, value) ->
                val c = Cookie.Builder()
                    .name(name).value(value).domain(host).path("/").build()
                list.removeAll { it.name == name }
                list.add(c)
            }
        }
    }

    fun getCookies(): Map<String, String> {
        return synchronized(cookieLock) {
            val result = LinkedHashMap<String, String>()
            cookieStore.values.forEach { list -> list.forEach { result[it.name] = it.value } }
            result
        }
    }

    fun logout() {
        synchronized(cookieLock) { cookieStore.clear() }
        persistedCookies = emptyMap()
    }

    private fun getCsrfCookie(): String? = synchronized(cookieLock) {
        cookieStore.values.asSequence().flatMap { it.asSequence() }
            .firstOrNull { it.name == "__csrf" }?.value
    }

    fun ensureWeapiSession() {
        try {
            request("https://music.163.com/", emptyMap(), CryptoMode.API, "GET")
        } catch (_: Exception) {}
    }

    fun request(
        url: String,
        params: Map<String, Any>,
        mode: CryptoMode = CryptoMode.WEAPI,
        method: String = "POST",
        usePersistedCookies: Boolean = true
    ): String {
        val requestUrl = url.toHttpUrl()
        Log.d(TAG, "call $url $method $mode")

        val bodyParams: Map<String, String> = when (mode) {
            CryptoMode.WEAPI -> NeteaseCrypto.weApiEncrypt(params)
            CryptoMode.EAPI -> NeteaseCrypto.eApiEncrypt(requestUrl.encodedPath, params)
            CryptoMode.LINUX -> NeteaseCrypto.linuxApiEncrypt(params)
            CryptoMode.API -> params.mapValues { it.value.toString() }
        }

        var reqUrl = requestUrl
        val builder = Request.Builder()

        if (usePersistedCookies) {
            getPersistedCookieHeader()?.let { builder.header("Cookie", it) }
        }

        if (mode == CryptoMode.WEAPI) {
            val csrf = if (usePersistedCookies) {
                persistedCookies["__csrf"] ?: getCsrfCookie() ?: ""
            } else getCsrfCookie() ?: ""
            reqUrl = requestUrl.newBuilder()
                .setQueryParameter("csrf_token", csrf).build()
        }

        when (method.uppercase(Locale.getDefault())) {
            "POST" -> {
                val formBody = FormBody.Builder(StandardCharsets.UTF_8).apply {
                    bodyParams.forEach { (k, v) -> add(k, v) }
                }.build()
                builder.post(formBody).url(reqUrl)
            }
            else -> {
                val urlB = reqUrl.newBuilder()
                bodyParams.forEach { (k, v) -> urlB.addQueryParameter(k, v) }
                builder.url(urlB.build())
            }
        }

        val resp = okHttpClient.newCall(builder.build()).execute()
        val responseBody = resp.body ?: throw IOException("Empty response body")
        val bytes = responseBody.bytes()
        if (!resp.isSuccessful) {
            throw IOException("HTTP ${resp.code}: ${String(bytes, StandardCharsets.UTF_8)}")
        }
        return String(bytes, StandardCharsets.UTF_8)
    }

    fun callWeApi(path: String, params: Map<String, Any> = emptyMap()): String {
        val p = if (path.startsWith("/")) path else "/$path"
        return request("https://music.163.com/weapi$p", params, CryptoMode.WEAPI)
    }

    fun callEApi(path: String, params: Map<String, Any> = emptyMap()): String {
        val p = if (path.startsWith("/")) path else "/$path"
        return request("https://interface.music.163.com/eapi$p", params, CryptoMode.EAPI)
    }

    fun callApi(path: String, params: Map<String, Any> = emptyMap()): String {
        val p = if (path.startsWith("/")) path else "/$path"
        return request("https://music.163.com/api$p", params, CryptoMode.API)
    }

    // == Daily Recommendation ==
    fun getDailyRecommendSongs(): String {
        return callWeApi("/v1/discovery/recommend/songs")
    }

    fun getPersonalizedPlaylists(limit: Int = 30): String {
        return callWeApi("/personalized/playlist", mapOf("limit" to limit.toString()))
    }

    fun getPersonalFM(): String {
        return callWeApi("/personal_fm")
    }

    fun getToplist(): String {
        return callWeApi("/toplist")
    }

    fun getPlaylistHighQuality(cat: String = "全部", limit: Int = 10): String {
        return callWeApi("/playlist/highquality", mapOf("cat" to cat, "limit" to limit.toString()))
    }

    // == Auth ==
    fun loginByPhone(phone: String, password: String, countryCode: Int = 86): String {
        return callEApi("/w/login/cellphone", mapOf(
            "phone" to phone, "countrycode" to countryCode, "remember" to "true",
            "password" to NeteaseCrypto.md5Hex(password), "type" to "1"
        ))
    }

    fun sendCaptcha(phone: String, ctcode: Int = 86): String {
        return request("https://interface.music.163.com/weapi/sms/captcha/sent",
            mapOf("cellphone" to phone, "ctcode" to ctcode.toString()), CryptoMode.WEAPI, usePersistedCookies = false)
    }

    fun verifyCaptcha(phone: String, captcha: String, ctcode: Int = 86): String {
        return request("https://interface.music.163.com/weapi/sms/captcha/verify",
            mapOf("cellphone" to phone, "captcha" to captcha, "ctcode" to ctcode.toString()), CryptoMode.WEAPI, usePersistedCookies = false)
    }

    fun loginByCaptcha(phone: String, captcha: String, ctcode: Int = 86): String {
        return callEApi("/w/login/cellphone", mapOf(
            "phone" to phone, "countrycode" to ctcode.toString(), "remember" to "true",
            "type" to "1", "captcha" to captcha
        ))
    }

    // == Search ==
    fun searchSongs(keyword: String, limit: Int = 30, offset: Int = 0): String {
        return callWeApi("/cloudsearch/get/web", mapOf(
            "s" to keyword, "type" to "1", "limit" to limit.toString(), "offset" to offset.toString(), "total" to "true"
        ))
    }

    fun searchPlaylists(keyword: String, limit: Int = 30, offset: Int = 0): String {
        return callWeApi("/cloudsearch/get/web", mapOf(
            "s" to keyword, "type" to "1000", "limit" to limit.toString(), "offset" to offset.toString(), "total" to "true"
        ))
    }

    fun searchArtists(keyword: String, limit: Int = 30, offset: Int = 0): String {
        return callWeApi("/cloudsearch/get/web", mapOf(
            "s" to keyword, "type" to "100", "limit" to limit.toString(), "offset" to offset.toString(), "total" to "true"
        ))
    }

    // == Song URL ==
    fun getSongUrl(songId: Long, level: String = "lossless"): String {
        return try {
            val resp = callEApi("/song/enhance/player/url/v1", mapOf(
                "ids" to "[$songId]", "level" to level, "encodeType" to "flac"
            ))
            val code = JSONObject(resp).optInt("code", -1)
            if (code == 301 && hasLogin()) {
                ensureWeapiSession()
                callEApi("/song/enhance/player/url/v1", mapOf(
                    "ids" to "[$songId]", "level" to level, "encodeType" to "flac"
                ))
            } else resp
        } catch (e: Exception) { throw e }
    }

    // == Song Detail ==
    fun getSongDetail(ids: List<Long>): String {
        val idsParam = ids.joinToString(",")
        val detailParam = ids.joinToString(",", "[", "]") { """{"id":$it}""" }
        return callWeApi("/v3/song/detail", mapOf("c" to detailParam, "ids" to "[$idsParam]"))
    }

    // == Lyrics ==
    fun getLyricNew(songId: Long): String {
        fun call(): String = callEApi("/song/lyric/v1", mapOf(
            "id" to songId.toString(), "cp" to "false", "lv" to "0", "tv" to "1",
            "rv" to "0", "yv" to "1", "ytv" to "1", "yrv" to "0"
        ))
        var resp = call()
        try {
            val code = JSONObject(resp).optInt("code", 200)
            if (code == 301 && hasLogin()) { ensureWeapiSession(); resp = call() }
        } catch (_: Exception) {}
        return resp
    }

    // == Playlist ==
    fun getUserPlaylists(userId: Long, offset: Int = 0, limit: Int = 30): String {
        return callWeApi("/user/playlist", mapOf(
            "uid" to userId.toString(), "offset" to offset.toString(),
            "limit" to limit.toString(), "includeVideo" to "true"
        ))
    }

    fun getPlaylistDetail(playlistId: Long): String {
        return callApi("/v6/playlist/detail", mapOf(
            "id" to playlistId.toString(), "n" to "100000", "s" to "8"
        ))
    }

    fun getCurrentUserAccount(): String {
        return callWeApi("/w/nuser/account/get")
    }

    fun getCurrentUserId(): Long {
        val raw = getCurrentUserAccount()
        val root = JSONObject(raw)
        if (root.optInt("code", -1) != 200) throw IllegalStateException("Failed to get user info: $raw")
        val profile = root.optJSONObject("profile")
        return profile?.optLong("userId")
            ?: throw IllegalStateException("userId not found: $raw")
    }

    // == Album ==
    fun getAlbumDetail(albumId: Long): String {
        return callWeApi("/v1/album/$albumId", mapOf("n" to "100000", "s" to "8"))
    }

    // == Artist ==
    fun getArtistSongs(artistId: Long, limit: Int = 50, offset: Int = 0): String {
        return callWeApi("/v1/artist/songs", mapOf(
            "id" to artistId.toString(), "offset" to offset.toString(), "limit" to limit.toString(), "order" to "hot"
        ))
    }
}
