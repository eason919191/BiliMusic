package com.bilimusic.data.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Bilibili WBI签名工具
 * 参考：https://github.com/SocialSisterYi/bilibili-API-collect/blob/master/docs/misc/sign/wbi.md
 * 实现参考PiliPlus的 wbi_sign.dart
 */
object WbiSign {
    private const val TAG = "WbiSign"

    // 字符重排表（固定）
    private val MIXIN_KEY_ENC_TAB = intArrayOf(
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50,
        10, 31, 58, 3, 45, 35, 27, 43, 5, 49,
        33, 9, 42, 19, 29, 28, 14, 39, 12, 38,
        41, 13
    )

    // 缓存的mixinKey
    private var cachedMixinKey: String? = null
    private var cacheDay = -1

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .addHeader("Referer", "https://www.bilibili.com/")
                .build()
            chain.proceed(request)
        }
        .build()

    /**
     * 对字符串进行字符重排（根据固定编码表）
     */
    private fun getMixinKey(orig: String): String {
        val chars = orig.toCharArray()
        return MIXIN_KEY_ENC_TAB.map { index ->
            if (index < chars.size) chars[index] else ' '
        }.joinToString("")
    }

    /**
     * MD5哈希
     */
    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val hash = digest.digest(input.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * URL编码参数值
     */
    private fun urlEncode(value: String): String {
        return URLEncoder.encode(value, "UTF-8")
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~")
    }

    /**
     * 为请求参数添加WBI签名
     * @param params 原始参数Map
     * @return 添加了wts和w_rid后的参数Map
     */
    suspend fun sign(params: MutableMap<String, String>): Map<String, String> {
        val mixinKey = getMixinKeyFromServer()

        // 添加时间戳
        params["wts"] = (System.currentTimeMillis() / 1000).toString()

        // 按照key排序
        val sortedKeys = params.keys.sorted()

        // 构建待签名字符串（过滤特殊字符 !'()* ）
        val queryString = sortedKeys.joinToString("&") { key ->
            val value = params[key] ?: ""
            val filteredValue = value.replace(Regex("[!'()*]"), "")
            "${urlEncode(key)}=${urlEncode(filteredValue)}"
        }

        // 计算w_rid = md5(queryString + mixinKey)
        val wRid = md5(queryString + mixinKey)
        params["w_rid"] = wRid

        return params
    }

    /**
     * 从服务器获取mixinKey（从nav接口获取img_key和sub_key）
     */
    private suspend fun getMixinKeyFromServer(): String {
        val today = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)

        // 缓存检查 - 同一天使用缓存的key
        if (cachedMixinKey != null && cacheDay == today) {
            return cachedMixinKey!!
        }

        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://api.bilibili.com/x/web-interface/nav")
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .addHeader("Referer", "https://www.bilibili.com/")
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""

                val json = JSONObject(body)
                if (json.optInt("code") == 0) {
                    val data = json.optJSONObject("data") ?: JSONObject()
                    val wbiImg = data.optJSONObject("wbi_img") ?: JSONObject()

                    val imgUrl = wbiImg.optString("img_url", "")
                    val subUrl = wbiImg.optString("sub_url", "")

                    // 从URL中提取文件名（不含扩展名）
                    val imgKey = extractFileName(imgUrl)
                    val subKey = extractFileName(subUrl)

                    val mixinKey = getMixinKey(imgKey + subKey)
                    cachedMixinKey = mixinKey
                    cacheDay = today
                    Log.d(TAG, "WBI Key refreshed. mixinKey: $mixinKey")
                    return@withContext mixinKey
                } else {
                    Log.w(TAG, "Failed to get WBI keys, code: ${json.optInt("code")}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting WBI keys", e)
            }

            // Fallback: use default key if API fails
            val fallbackKey = "9b288b8f10ef90b6a4e5e7a1e5e7a1e5"
            cachedMixinKey = fallbackKey
            cacheDay = today
            fallbackKey
        }
    }

    /**
     * 从URL路径中提取文件名（不含扩展名）
     * 例如: "https://i0.hdslb.com/bfs/wbi/7cd0849413381a26.png" -> "7cd0849413381a26"
     */
    private fun extractFileName(url: String): String {
        val segments = url.split("/")
        val last = segments.lastOrNull() ?: return ""
        return last.substringBeforeLast(".")
    }

    /**
     * 强制刷新WBI key
     */
    suspend fun refreshKey() {
        cachedMixinKey = null
        cacheDay = -1
        getMixinKeyFromServer()
    }
}
