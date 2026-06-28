package com.bilimusic.data.api

import android.util.Log
import com.bilimusic.data.model.BilibiliFavoriteFolder
import com.bilimusic.data.model.BilibiliVideo
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

private const val TAG = "BilibiliApi"

/**
 * B站API接口
 */
interface BilibiliApiService {

    // 视频详情（不需要WBI签名）
    @GET("x/web-interface/view")
    suspend fun getVideoDetail(@Query("bvid") bvid: String): BilibiliDetailResponse

    // 视频分P列表
    @GET("x/player/pagelist")
    suspend fun getVideoPages(@Query("bvid") bvid: String): BilibiliPageListResponse

    // 收藏夹列表
    @GET("x/v3/fav/folder/created/list")
    suspend fun getFavoriteFolders(
        @Header("Cookie") cookie: String,
        @Query("pn") pageNum: Int = 1,
        @Query("ps") pageSize: Int = 30
    ): BilibiliFavFolderResponse

    // 收藏夹内容
    @GET("x/v3/fav/resource/list")
    suspend fun getFavoriteResources(
        @Header("Cookie") cookie: String,
        @Query("media_id") mediaId: Long,
        @Query("pn") pageNum: Int = 1,
        @Query("ps") pageSize: Int = 20,
        @Query("platform") platform: String = "web"
    ): BilibiliFavResourceResponse
}

// ===== Search (使用OkHttp直接调用，因为需要WBI签名) =====
data class BilibiliSearchResponse(
    val code: Int = -1,
    val message: String = "",
    val data: SearchData? = null
)

data class SearchData(
    val result: List<SearchResultItem>? = null,
    val page: Int = 1,
    val pagesize: Int = 20,
    @SerializedName("numResults") val numResults: Int = 0,
    @SerializedName("numPages") val numPages: Int = 1
)

data class SearchResultItem(
    val bvid: String = "",
    val title: String = "",
    val author: String = "",
    val pic: String = "",
    val duration: String = "0:00",
    @SerializedName("playback_count") val play: Long = 0
) {
    fun durationToSeconds(): Long {
        if (duration.isBlank()) return 0
        return try {
            val parts = duration.split(":")
            when (parts.size) {
                3 -> (parts[0].toLongOrNull() ?: 0) * 3600 +
                     (parts[1].toLongOrNull() ?: 0) * 60 +
                     (parts[2].toLongOrNull() ?: 0)
                2 -> (parts[0].toLongOrNull() ?: 0) * 60 +
                     (parts[1].toLongOrNull() ?: 0)
                else -> duration.toLongOrNull() ?: 0
            }
        } catch (e: Exception) {
            Log.e(TAG, "parse duration error: $duration", e)
            0
        }
    }
}

// ===== Video Detail Response =====
data class BilibiliDetailResponse(
    val code: Int = -1,
    val message: String = "",
    val data: VideoDetailData? = null
)

data class VideoDetailData(
    val aid: Long = 0,
    val bvid: String = "",
    val title: String = "",
    val owner: VideoOwner? = null,
    val pic: String = "",
    val duration: Long = 0,
    val cid: Long = 0,
    val pages: List<VideoPage>? = null,
    val stat: VideoStat? = null,
    val subtitle: VideoSubtitleData? = null
)

data class VideoSubtitleData(
    @SerializedName("allow_submit") val allowSubmit: Boolean = false,
    val list: List<SubtitleItem>? = null
)

data class SubtitleItem(
    val id: Long = 0,
    val lan: String = "",
    @SerializedName("lan_doc") val lanDoc: String = "",
    @SerializedName("subtitle_url") val subtitleUrl: String = "",
    @SerializedName("type") val type: Int = 0
) {
    val isAi: Boolean get() = type == 1
    val displayName: String get() = if (isAi) "$lanDoc（AI）" else lanDoc
}

data class VideoOwner(
    val mid: Long = 0,
    val name: String = "",
    val face: String = ""
)

data class VideoStat(
    val view: Long = 0,
    val like: Long = 0,
    val coin: Long = 0,
    val favorite: Long = 0,
    val share: Long = 0
)

data class VideoPage(
    val cid: Long = 0,
    val page: Int = 1,
    val part: String = "",
    val duration: Long = 0
)

// ===== Play URL Response (使用OkHttp直接调用，WBI签名) =====
data class BilibiliPlayUrlResponse(
    val code: Int = -1,
    val message: String = "",
    val data: PlayUrlData? = null
)

data class PlayUrlData(
    val durl: List<DurlItem>? = null,
    val dash: DashData? = null,
    val quality: Int = 0,
    val format: String = "",
    val timelength: Long = 0
)

data class DurlItem(
    val order: Int = 0,
    val length: Long = 0,
    val size: Long = 0,
    val url: String = "",
    val backup_url: List<String>? = null
)

data class DashData(
    val audio: List<DashAudioItem>? = null
)

data class DashAudioItem(
    val id: Int = 0,
    val baseUrl: String = "",
    val bandwidth: Int = 0,
    val codecid: Int = 0,
    val size: Long = 0
)

// ===== Favorite Responses =====
data class BilibiliFavFolderResponse(
    val code: Int = -1,
    val message: String = "",
    val data: FavFolderData? = null
)

data class FavFolderData(
    val list: List<FavFolderItem>? = null,
    val count: Int = 0
)

data class FavFolderItem(
    val id: Long = 0,
    val title: String = "",
    @SerializedName("media_count") val mediaCount: Int = 0,
    val cover: String? = null
)

data class BilibiliFavResourceResponse(
    val code: Int = -1,
    val message: String = "",
    val data: FavResourceData? = null
)

data class FavResourceData(
    val medias: List<FavMediaItem>? = null,
    val page: FavPageInfo? = null
)

data class FavMediaItem(
    val bvid: String? = null,
    val title: String = "",
    val cover: String? = null,
    val upper: FavUpper? = null,
    val duration: Long = 0,
    val id: Long = 0,
    val type: Int = 0
)

data class FavUpper(
    val mid: Long = 0,
    val name: String = "",
    val face: String = ""
)

data class FavPageInfo(
    val pn: Int = 1,
    val ps: Int = 20,
    val total: Int = 0
)

data class BilibiliPageListResponse(
    val code: Int = -1,
    val message: String = "",
    val data: List<VideoPage>? = null
)

// ===== 字幕数据 (player API response) =====
data class BilibiliSubtitleResponse(
    val code: Int = -1,
    val message: String = "",
    val data: SubtitleData? = null
)

data class SubtitleData(
    val subtitle: PlayerSubtitleInfo? = null
)

data class PlayerSubtitleInfo(
    val subtitles: List<PlayerSubtitleItem>? = null
)

data class PlayerSubtitleItem(
    val id: Long = 0,
    val lan: String = "",
    @SerializedName("lan_doc") val lanDoc: String = "",
    @SerializedName("subtitle_url") val subtitleUrl: String = "",
    @SerializedName("type") val type: Int = 0
) {
    val isAi: Boolean get() = type == 1
}

/** 字幕行 - 解析后的时间轴文本 */
data class LyricLine(
    val timeMs: Long,
    val text: String
)

// ===== API Client =====

object BilibiliApiClient {
    private const val BASE_URL = "https://api.bilibili.com/"

    private val gson: Gson = GsonBuilder().setLenient().create()

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .addHeader("Referer", "https://www.bilibili.com/")
                    .addHeader("Origin", "https://www.bilibili.com")
                if (userCookie.isNotBlank()) builder.addHeader("Cookie", userCookie)
                chain.proceed(builder.build())
            }
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    val service: BilibiliApiService by lazy {
        retrofit.create(BilibiliApiService::class.java)
    }

    fun sharedClient(): OkHttpClient = okHttpClient

    /**
     * 搜索视频 - 使用WBI签名
     */
    suspend fun searchVideos(keyword: String, page: Int = 1, order: String = "totalrank"): List<BilibiliVideo> {
        return withContext(Dispatchers.IO) {
            try {
                runCatching { WbiSign.refreshKey() }
                val params = mutableMapOf(
                    "search_type" to "video", "keyword" to keyword,
                    "page" to page.toString(), "page_size" to "20",
                    "platform" to "pc", "web_location" to "1430654",
                    "order" to order
                )
                val signedParams = WbiSign.sign(params)

                // 构建URL
                val urlBuilder = StringBuilder("https://api.bilibili.com/x/web-interface/wbi/search/type?")
                signedParams.forEach { (key, value) ->
                    urlBuilder.append(java.net.URLEncoder.encode(key, "UTF-8"))
                    urlBuilder.append("=")
                    urlBuilder.append(java.net.URLEncoder.encode(value, "UTF-8"))
                    urlBuilder.append("&")
                }

                val request = Request.Builder()
                    .url(urlBuilder.toString().trimEnd('&'))
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .addHeader("Referer", "https://search.bilibili.com/video?keyword=${java.net.URLEncoder.encode(keyword, "UTF-8")}")
                    .addHeader("Origin", "https://search.bilibili.com")
                    .build()

                val response = okHttpClient.newCall(request).execute()
                val body = response.body?.string() ?: ""
                Log.d(TAG, "Search response code: ${response.code}")

                if (!response.isSuccessful) {
                    Log.e(TAG, "Search failed: ${response.code} $body")
                    return@withContext emptyList()
                }

                val json = GsonBuilder().setLenient().create()
                val searchResponse = try {
                    json.fromJson(body, BilibiliSearchResponse::class.java)
                } catch (e: Exception) {
                    Log.e(TAG, "Parse search response error", e)
                    Log.d(TAG, "Raw response: ${body.take(500)}")
                    return@withContext emptyList()
                }

                if (searchResponse.code == 0 && searchResponse.data?.result != null) {
                    searchResponse.data.result.mapNotNull { item ->
                        try {
                            val bvid = item.bvid
                            if (bvid.isBlank()) return@mapNotNull null
                            // Normalize cover URL
                            var coverUrl = item.pic.ifBlank { "" }
                            if (coverUrl.startsWith("//")) coverUrl = "https:$coverUrl"

                            BilibiliVideo(
                                bvid = bvid,
                                title = item.title.replace(Regex("<[^>]*>"), ""),
                                author = item.author.ifBlank { "未知" },
                                coverUrl = coverUrl,
                                duration = item.durationToSeconds()
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing search result", e)
                            null
                        }
                    }
                } else {
                    Log.w(TAG, "Search API error: code=${searchResponse.code}, msg=${searchResponse.message}")
                    emptyList()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Search exception", e)
                emptyList()
            }
        }
    }

    /**
     * 获取视频详情
     */
    /** 搜索建议 */
    suspend fun fetchSearchSuggestions(keyword: String): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                val resp = okHttpClient.newCall(Request.Builder()
                    .url("https://s.search.bilibili.com/main/suggest?term=${java.net.URLEncoder.encode(keyword, "UTF-8")}")
                    .addHeader("User-Agent", "Mozilla/5.0")
                    .addHeader("Referer", "https://www.bilibili.com/")
                    .build()).execute()
                val body = resp.body?.string() ?: ""
                val json = JSONObject(body)
                val result = json.optJSONArray("result")
                val tags = mutableListOf<String>()
                if (result != null) {
                    for (i in 0 until result.length()) {
                        val item = result.getJSONObject(i)
                        val value = item.optString("value", "").replace(Regex("<[^>]*>"), "")
                        if (value.isNotBlank()) tags.add(value)
                    }
                }
                tags
            } catch (e: Exception) { emptyList() }
        }
    }

    /** 获取视频cid（使用pagelist，避开412） */
    suspend fun getVideoCid(bvid: String): Long {
        return withContext(Dispatchers.IO) {
            try {
                val resp = service.getVideoPages(bvid)
                if (resp.code == 0 && resp.data != null) resp.data.firstOrNull()?.cid ?: 0L
                else 0L
            } catch (e: Exception) { 0L }
        }
    }

    suspend fun getVideoDetail(bvid: String): VideoDetailData? {
        return withContext(Dispatchers.IO) {
            try {
                val cid = getVideoCid(bvid)
                if (cid <= 0) return@withContext null
                // 使用player/wbi/v2（不被412限制）
                runCatching { WbiSign.refreshKey() }
                val signed = WbiSign.sign(mutableMapOf("bvid" to bvid, "cid" to cid.toString()))
                val url = buildString {
                    append("https://api.bilibili.com/x/player/wbi/v2?")
                    signed.forEach { (k, v) -> append("${java.net.URLEncoder.encode(k,"UTF-8")}=${java.net.URLEncoder.encode(v,"UTF-8")}&") }
                }.trimEnd('&')
                val resp = okHttpClient.newCall(okhttp3.Request.Builder().url(url).build()).execute()
                val body = resp.body?.string() ?: ""
                val json = JSONObject(body)
                if (json.optInt("code") == 0) {
                    val d = json.optJSONObject("data") ?: return@withContext null
                    VideoDetailData(aid = d.optLong("aid"), bvid = bvid, cid = cid, duration = d.optLong("duration"))
                } else null
            } catch (e: Exception) {
                Log.e(TAG, "getVideoDetail error", e)
                null
            }
        }
    }

    /**
     * 获取流式播放URL（用于下载列表）
     */
    suspend fun getAudioUrl(bvid: String, cid: Long): String? {
        return withContext(Dispatchers.IO) {
            try {
                tryGetAudioUrlWbi(bvid, cid) ?: tryGetAudioUrlFallback(bvid, cid)
            } catch (e: Exception) {
                Log.e(TAG, "getAudioUrl exception", e)
                null
            }
        }
    }

    /**
     * 获取可播放的音频文件路径 - 和下载使用完全相同的URL
     */
    suspend fun getPlayableAudioFile(bvid: String, cid: Long, cacheDir: java.io.File): String? {
        return withContext(Dispatchers.IO) {
            try {
                cacheDir.mkdirs()
                val cacheFile = java.io.File(cacheDir, "${bvid}.audio")
                if (cacheFile.exists() && cacheFile.length() > 10240) {
                    Log.d(TAG, "Using cached audio: ${cacheFile.absolutePath}")
                    return@withContext cacheFile.absolutePath
                }

                // 使用和下载按钮完全相同的URL
                val streamUrl = getAudioUrl(bvid, cid)
                if (streamUrl == null) {
                    val msg = "getAudioUrl returned null for bvid=$bvid cid=$cid"
                    Log.e(TAG, msg)
                    com.bilimusic.BiliMusicApp.appendLog(TAG, msg)
                    return@withContext null
                }
                Log.d(TAG, "Downloading playback cache: ${streamUrl.take(80)}")

                // 用和下载完全相同的OkHttp客户端下载
                val dlReq = okhttp3.Request.Builder().url(streamUrl)
                    .addHeader("Referer", "https://www.bilibili.com/")
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()
                val dlResp = okHttpClient.newCall(dlReq).execute()
                if (!dlResp.isSuccessful) {
                    Log.e(TAG, "Download HTTP ${dlResp.code} for $streamUrl")
                    return@withContext streamUrl // 返回原始URL，让ExoPlayer尝试
                }
                val body = dlResp.body ?: return@withContext streamUrl
                val total = body.contentLength()

                if (total < 10240) {
                    Log.w(TAG, "Download too small: $total bytes")
                    return@withContext streamUrl
                }

                java.io.FileOutputStream(cacheFile).use { fos ->
                    body.byteStream().use { input -> input.copyTo(fos) }
                }
                Log.d(TAG, "Cached to ${cacheFile.absolutePath} ($total bytes)")
                cacheFile.absolutePath
            } catch (e: Exception) {
                Log.e(TAG, "getPlayableAudioFile error", e)
                null
            }
        }
    }

    /**
     * 策略3: 直接下载音频到缓存目录
     */
    private suspend fun downloadAudioToCache(bvid: String, cid: Long, cacheDir: java.io.File): String? {
        return runCatching {
            cacheDir.mkdirs()
            val params = mutableMapOf(
                "bvid" to bvid, "cid" to cid.toString(),
                "qn" to "80", "fnval" to "0", "fourk" to "0"
            )
            runCatching { WbiSign.refreshKey() }
            val signedParams = runCatching { WbiSign.sign(params) }.getOrElse { params }

            val urlBuilder = StringBuilder("https://api.bilibili.com/x/player/wbi/playurl?")
            signedParams.forEach { (k, v) ->
                urlBuilder.append("${java.net.URLEncoder.encode(k, "UTF-8")}=${java.net.URLEncoder.encode(v, "UTF-8")}&")
            }

            val req1 = Request.Builder().url(urlBuilder.toString().trimEnd('&'))
                .addHeader("Referer", "https://www.bilibili.com/video/$bvid")
                .addHeader("User-Agent", "Mozilla/5.0")
                .build()
            val resp1 = okHttpClient.newCall(req1).execute()
            val body1 = resp1.body?.string() ?: ""
            if (!resp1.isSuccessful) return@runCatching null

            val playResp = GsonBuilder().setLenient().create().fromJson(body1, BilibiliPlayUrlResponse::class.java)
            if (playResp.code != 0) return@runCatching null
            val playData = playResp.data ?: return@runCatching null

            val dlUrl = playData.durl?.firstOrNull { it.url.isNotBlank() }?.url
                ?: playData.dash?.audio?.firstOrNull { it.baseUrl.isNotBlank() }?.baseUrl
                ?: return@runCatching null

            val cacheFile = java.io.File(cacheDir, "${bvid}.mp4")
            if (cacheFile.exists() && cacheFile.length() > 10240) return@runCatching cacheFile.absolutePath

            val dlReq2 = Request.Builder().url(dlUrl)
                .addHeader("Referer", "https://www.bilibili.com/")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            val dlResp2 = okHttpClient.newCall(dlReq2).execute()
            if (!dlResp2.isSuccessful) return@runCatching null

            val body2 = dlResp2.body ?: return@runCatching null
            java.io.FileOutputStream(cacheFile).use { fos ->
                body2.byteStream().use { input -> input.copyTo(fos) }
            }
            Log.d(TAG, "Cached audio: ${cacheFile.absolutePath} (${cacheFile.length()} bytes)")
            cacheFile.absolutePath
        }.getOrElse {
            Log.e(TAG, "Cache download error", it)
            null
        }
    }

    /**
     * 策略1: 多格式尝试，优先完整文件
     */
    private suspend fun tryGetAudioUrlWbi(bvid: String, cid: Long): String? {
        return runCatching {
            runCatching { WbiSign.refreshKey() }

            // 尝试获取完整FLV/MP4 (fnval=0)
            val flv = fetchPlayUrl(bvid, cid, "0")
            if (flv != null) return@runCatching flv

            // 尝试DASH格式 (fnval=16)
            val dash = fetchPlayUrl(bvid, cid, "16")
            if (dash != null) return@runCatching dash

            // 尝试完整DASH+ (fnval=4048)
            val dashP = fetchPlayUrl(bvid, cid, "4048")
            if (dashP != null) return@runCatching dashP

            null
        }.getOrElse { null }
    }

    /**
     * 用指定fnval获取播放URL
     */
    private suspend fun fetchPlayUrl(bvid: String, cid: Long, fnval: String): String? {
        return runCatching {
            val params = mutableMapOf(
                "bvid" to bvid, "cid" to cid.toString(),
                "qn" to "80", "fnval" to fnval, "fourk" to "1", "fnver" to "0"
            )
            val signed = WbiSign.sign(params)
            val url = buildString {
                append("https://api.bilibili.com/x/player/wbi/playurl?")
                signed.forEach { (k, v) -> append("${java.net.URLEncoder.encode(k, "UTF-8")}=${java.net.URLEncoder.encode(v, "UTF-8")}&") }
            }.trimEnd('&')

            val req = okhttp3.Request.Builder().url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .addHeader("Referer", "https://www.bilibili.com/video/$bvid")
                .build()
            val resp = okHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) return@runCatching null

            val json = GsonBuilder().setLenient().create().fromJson(body, BilibiliPlayUrlResponse::class.java)
            if (json.code != 0) return@runCatching null
            val data = json.data ?: return@runCatching null

            // durl（完整音视频文件）→ dash音频
            data.durl?.firstOrNull { it.url.isNotBlank() }?.url
                ?: data.dash?.audio?.firstOrNull { it.baseUrl.isNotBlank() }?.baseUrl
        }.getOrElse { null }
    }

    /**
     * 获取下载专用音频URL（仅音频流，DASH格式，文件小）
     */
    suspend fun getDownloadAudioUrl(bvid: String, cid: Long): String? {
        return withContext(Dispatchers.IO) {
            try {
                runCatching { WbiSign.refreshKey() }
                // 只请求DASH格式，优先音频
                val url = fetchPlayUrl(bvid, cid, "80")
                if (url != null) return@withContext url
                // 备用: fnval=16 (基础DASH)
                fetchPlayUrl(bvid, cid, "16")
            } catch (e: Exception) {
                Log.e(TAG, "getDownloadAudioUrl error", e)
                null
            }
        }
    }

    /**
     * 策略2: 备用方案 - 不带WBI签名
     */
    private suspend fun tryGetAudioUrlFallback(bvid: String, cid: Long): String? {
        return runCatching {
            val params = mutableMapOf(
                "bvid" to bvid, "cid" to cid.toString(),
                "qn" to "16", "fnval" to "16", "fourk" to "0", "fnver" to "0"
            )

            val urlBuilder = StringBuilder("https://api.bilibili.com/x/player/playurl?")
            params.forEach { (k, v) ->
                urlBuilder.append("${java.net.URLEncoder.encode(k, "UTF-8")}=${java.net.URLEncoder.encode(v, "UTF-8")}&")
            }

            val request = Request.Builder()
                .url(urlBuilder.toString().trimEnd('&'))
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .addHeader("Referer", "https://www.bilibili.com/video/$bvid")
                .build()

            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) return@runCatching null

            val json = GsonBuilder().setLenient().create().fromJson(body, BilibiliPlayUrlResponse::class.java)
            if (json.code != 0) return@runCatching null

            json.data?.dash?.audio?.firstOrNull { it.baseUrl.isNotBlank() }?.baseUrl
                ?: json.data?.durl?.firstOrNull { it.url.isNotBlank() }?.url
        }.getOrElse { null }
    }

    /**
     * 获取收藏夹列表
     */
    suspend fun getFavoriteFolders(cookie: String, upMid: Long = 0): List<BilibiliFavoriteFolder> {
        return withContext(Dispatchers.IO) {
            try {
                // Build URL with optional up_mid parameter (required by some API versions)
                val urlStr = if (upMid > 0)
                    "https://api.bilibili.com/x/v3/fav/folder/created/list?pn=1&ps=30&up_mid=$upMid"
                else
                    "https://api.bilibili.com/x/v3/fav/folder/created/list?pn=1&ps=30"

                val request = okhttp3.Request.Builder()
                    .url(urlStr)
                    .addHeader("Cookie", cookie)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .addHeader("Referer", "https://www.bilibili.com/")
                    .build()
                Log.d(TAG, "Fetching fav folders...")
                val response = okHttpClient.newCall(request).execute()
                val body = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    Log.e(TAG, "Fav folders HTTP ${response.code}: ${body.take(200)}")
                    return@withContext emptyList()
                }

                val json = GsonBuilder().setLenient().create()
                val favResp = json.fromJson(body, BilibiliFavFolderResponse::class.java)
                if (favResp.code == 0 && favResp.data?.list != null) {
                    Log.d(TAG, "Found ${favResp.data.list.size} folders")
                    favResp.data.list.mapNotNull { item ->
                        if (item.id == 0L) return@mapNotNull null
                        BilibiliFavoriteFolder(
                            id = item.id,
                            title = item.title.ifBlank { "未命名" },
                            coverUrl = item.cover,
                            songCount = item.mediaCount
                        )
                    }
                } else {
                    Log.w(TAG, "Fav folder error: code=${favResp.code}, msg=${favResp.message}")
                    Log.d(TAG, "Response body: ${body.take(200)}")
                    emptyList()
                }
            } catch (e: Exception) {
                Log.e(TAG, "getFavoriteFolders exception", e)
                emptyList()
            }
        }
    }

    /**
     * 获取收藏夹内容
     */
    suspend fun getFavoriteResources(cookie: String, mediaId: Long): List<BilibiliVideo> {
        return withContext(Dispatchers.IO) {
            try {
                val allVideos = mutableListOf<BilibiliVideo>()
                var page = 1
                var hasMore = true

                while (hasMore) {
                    val response = service.getFavoriteResources(cookie, mediaId, page, 20)
                    Log.d(TAG, "Fav resource page=$page mediaId=$mediaId code=${response.code}")
                    if (response.code == 0 && response.data?.medias != null) {
                        val videos = response.data.medias.mapNotNull { media ->
                            val bvid = media.bvid ?: return@mapNotNull null
                            BilibiliVideo(
                                bvid = bvid,
                                title = media.title.ifBlank { "未知" },
                                author = media.upper?.name ?: "未知",
                                coverUrl = media.cover ?: "",
                                duration = media.duration
                            )
                        }
                        allVideos.addAll(videos)
                        hasMore = videos.size >= 20 && page < 50 // 最多50页防止死循环
                        page++
                    } else {
                        Log.w(TAG, "Fav resource error: code=${response.code} at page=$page")
                        hasMore = false
                    }
                }
                Log.d(TAG, "Loaded ${allVideos.size} favorite videos")
                allVideos
            } catch (e: Exception) {
                Log.e(TAG, "getFavoriteResources exception", e)
                emptyList()
            }
        }
    }

    /**
     * 获取视频分P列表
     */
    suspend fun getVideoPages(bvid: String): List<VideoPage> {
        return withContext(Dispatchers.IO) {
            try {
                val response = service.getVideoPages(bvid)
                if (response.code == 0 && response.data != null) response.data
                else emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "getVideoPages exception", e)
                emptyList()
            }
        }
    }

    /**
     * 获取视频字幕列表（从x/player/v2接口，使用WBI签名）
     */
    /** 存储用户cookie，用于需要登录的API */
    @Volatile var userCookie: String = ""

    suspend fun getVideoSubtitles(bvid: String, cid: Long, aid: Long? = null): List<PlayerSubtitleItem> {
        return withContext(Dispatchers.IO) {
            try {
                // 使用WBI签名的player/wbi/v2接口（确保数据准确）
                runCatching { WbiSign.refreshKey() }
                val params = mutableMapOf("bvid" to bvid, "cid" to cid.toString())
                if (aid != null && aid > 0) params["aid"] = aid.toString()
                val signed = WbiSign.sign(params)
                val url = buildString {
                    append("https://api.bilibili.com/x/player/wbi/v2?")
                    signed.forEach { (k, v) ->
                        append("${java.net.URLEncoder.encode(k, "UTF-8")}=${java.net.URLEncoder.encode(v, "UTF-8")}&")
                    }
                }.trimEnd('&')
                val reqBuilder = Request.Builder().url(url).get()
                if (userCookie.isNotBlank()) reqBuilder.addHeader("Cookie", userCookie)
                val response = okHttpClient.newCall(reqBuilder.build()).execute()
                val body = response.body?.string() ?: return@withContext emptyList()
                val subtitleResp = gson.fromJson(body, BilibiliSubtitleResponse::class.java)
                if (subtitleResp.code == 0) {
                    val subs = subtitleResp.data?.subtitle?.subtitles
                    if (subs != null && subs.isNotEmpty()) {
                        Log.d(TAG, "Found ${subs.size} subtitle(s): ${subs.joinToString { "${it.lanDoc}(AI=${it.isAi})" }}")
                        subs
                    } else emptyList()
                } else emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "getVideoSubtitles error", e)
                emptyList()
            }
        }
    }

    /**
     * 下载并解析字幕JSON为LyricLine列表
     * B站字幕JSON格式: [{from: float, to: float, content: string}, ...]
     */
    suspend fun fetchSubtitleContent(subtitleUrl: String): List<LyricLine> {
        return withContext(Dispatchers.IO) {
            try {
                val fullUrl = if (subtitleUrl.startsWith("//")) "https:$subtitleUrl" else subtitleUrl
                val reqBuilder = Request.Builder().url(fullUrl).get()
                if (userCookie.isNotBlank()) reqBuilder.addHeader("Cookie", userCookie)
                val response = okHttpClient.newCall(reqBuilder.build()).execute()
                val body = response.body?.string() ?: return@withContext emptyList()
                // AI字幕返回 {"body": [...]}，普通字幕返回 [...]
                val jsonArray = try {
                    org.json.JSONArray(body)
                } catch (_: Exception) {
                    val jsonObj = org.json.JSONObject(body)
                    jsonObj.optJSONArray("body") ?: return@withContext emptyList()
                }
                val lines = mutableListOf<LyricLine>()
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    val from = (item.getDouble("from") * 1000).toLong()
                    val content = item.optString("content", "")
                    if (content.isNotBlank()) {
                        lines.add(LyricLine(timeMs = from, text = content.trim()))
                    }
                }
                Log.d(TAG, "fetchSubtitleContent: parsed ${lines.size} lines")
                lines
            } catch (e: Exception) {
                Log.e(TAG, "fetchSubtitleContent exception", e)
                emptyList()
            }
        }
    }
}
