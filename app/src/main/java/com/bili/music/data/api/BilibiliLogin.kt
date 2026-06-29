package com.bili.music.data.api

import android.graphics.Bitmap
import android.util.Log
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "BilibiliLogin"

data class QRCodeData(val url: String, val qrcodeKey: String)
data class UserInfo(val uid: Long = 0, val nickname: String = "", val avatar: String = "", val level: Int = 0, val isLogin: Boolean = false)

object BilibiliLoginClient {
    val client: OkHttpClient by lazy { BilibiliApiClient.okHttpClient() }

    private const val APP_KEY = "dfca71928277209b"
    private const val APP_SEC = "b5475a8825547a4fc26c7d518eaaa02e"

    private val buvid: String by lazy {
        val sb = StringBuilder("XX")
        val chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
        repeat(35) { sb.append(chars[(Math.random() * chars.length).toInt()]) }
        sb.toString()
    }

    /** App API 签名 */
    private fun appSign(params: MutableMap<String, String>) {
        params["appkey"] = APP_KEY
        params["ts"] = (System.currentTimeMillis() / 1000).toString()
        val sorted = params.entries.sortedBy { it.key }
        val queryStr = sorted.joinToString("&") { "${it.key}=${java.net.URLEncoder.encode(it.value, "UTF-8")}" }
        params["sign"] = md5(queryStr + APP_SEC)
    }

    /** 发送短信验证码 */
    suspend fun sendSmsCode(phone: String): Pair<Boolean, String> {
        return withContext(Dispatchers.IO) {
            try {
                // 第一次：不带recaptcha_token
                val (ok, msg) = doSendSms(phone, "", null)
                if (ok) return@withContext Pair(true, msg)
                // 如果需要recaptcha验证
                if (msg.startsWith("captcha:")) {
                    // 需要WebView滑块验证，不能自动重试
                    return@withContext Pair(false, msg)
                }
                return@withContext Pair(false, msg)
            } catch (e: Exception) {
                Log.e(TAG, "sendSmsCode error", e)
                return@withContext Pair(false, "网络错误")
            }
        }
    }

    private suspend fun doSendSms(phone: String, captchaKey: String, recaptchaToken: String?): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        // APP API - 返回recaptcha_url用于WebView验证
        val ts = System.currentTimeMillis() / 1000
        val params = mutableMapOf(
            "tel" to phone, "cid" to "86",
            "mobi_app" to "android_hd", "platform" to "android",
            "build" to "2001100", "channel" to "master",
            "c_locale" to "zh_CN", "s_locale" to "zh_CN", "disable_rcmd" to "0",
            "buvid" to buvid, "local_id" to buvid,
            "login_session_id" to md5(buvid + ts.toString()),
            "statistics" to "{\"appId\":1,\"platform\":3,\"version\":\"7.78.0\",\"abtest\":\"\"}"
        )
        if (captchaKey.isNotBlank()) params["captcha_key"] = captchaKey
        if (recaptchaToken != null) params["recaptcha_token"] = recaptchaToken
        appSign(params)
        val fb = FormBody.Builder()
        params.forEach { (k, v) -> fb.add(k, v) }
        val resp = client.newCall(Request.Builder()
            .url("https://passport.bilibili.com/x/passport-login/sms/send")
            .post(fb.build())
            .addHeader("User-Agent", "Mozilla/5.0")
            .addHeader("Cookie", "buvid3=$buvid")
            .build()).execute()
        val body = resp.body?.string() ?: ""
        Log.i(TAG, "doSendSms: $body")
        val json = JSONObject(body)
        val code = json.optInt("code", -1)
        if (code == 0) {
            val data = json.optJSONObject("data")
            val key = data?.optString("captcha_key") ?: ""
            val url = data?.optString("recaptcha_url") ?: ""
            if (url.isNotBlank() && recaptchaToken == null) {
                val token = url.substringAfter("recaptcha_token=").substringBefore("&")
                Pair(false, "captcha:$url|$token")
            } else if (url.isNotBlank()) {
                Pair(false, "需要滑块验证")
            } else {
                Pair(true, key)
            }
        } else {
            Pair(false, json.optString("message", "请求错误"))
        }
    }

    /** 从WebView同步cookies到API client */
    /** Geetest验证完成后，用验证数据发送短信 */
    suspend fun sendSmsWithGeetest(
        phone: String, recaptchaToken: String,
        geeChallenge: String, geeValidate: String, geeSeccode: String
    ): Pair<Boolean, String> {
        return withContext(Dispatchers.IO) {
            try {
                val ts = System.currentTimeMillis() / 1000
                val loginSessionId = md5(buvid + ts.toString())
                val params = mutableMapOf(
                    "tel" to phone, "cid" to "86",
                    "mobi_app" to "android_hd", "platform" to "android",
                    "build" to "2001100", "channel" to "master",
                    "c_locale" to "zh_CN", "s_locale" to "zh_CN",
                    "disable_rcmd" to "0",
                    "buvid" to buvid, "local_id" to buvid,
                    "login_session_id" to loginSessionId,
                    "recaptcha_token" to recaptchaToken,
                    "gee_challenge" to geeChallenge,
                    "gee_validate" to geeValidate,
                    "gee_seccode" to geeSeccode,
                    "statistics" to "{\"appId\":1,\"platform\":3,\"version\":\"7.78.0\",\"abtest\":\"\"}"
                )
                appSign(params)
                val fb = FormBody.Builder()
                params.forEach { (k, v) -> fb.add(k, v) }
                val resp = client.newCall(Request.Builder()
                    .url("https://passport.bilibili.com/x/passport-login/sms/send")
                    .post(fb.build())
                    .addHeader("User-Agent", "Mozilla/5.0")
                    .addHeader("Cookie", buildString {
                        append("buvid3=$buvid")
                            })
                    .build()).execute()
                val body = resp.body?.string() ?: ""
                Log.d(TAG, "sendSmsWithGeetest: $body")
                val json = JSONObject(body)
                if (json.optInt("code") == 0) {
                    val key = json.optJSONObject("data")?.optString("captcha_key") ?: ""
                    Pair(true, key)
                } else {
                    Pair(false, json.optString("message", "请求错误"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "sendSmsWithGeetest error", e)
                Pair(false, "网络错误")
            }
        }
    }

    /** WebView验证完成后，用token发送短信 */
    suspend fun sendSmsWithToken(phone: String, token: String): Pair<Boolean, String> {
        return withContext(Dispatchers.IO) {
            try {
                doSendSms(phone, "", token)
            } catch (e: Exception) {
                Pair(false, "网络错误")
            }
        }
    }

    /** 获取Web登录Key（RSA公钥和hash） */
    suspend fun getWebKey(): Triple<Boolean, String, String> {
        return withContext(Dispatchers.IO) {
            try {
                val resp = client.newCall(Request.Builder()
                    .url("https://passport.bilibili.com/x/passport-login/web/key")
                    .addHeader("User-Agent", "Mozilla/5.0")
                    .build()).execute()
                val json = JSONObject(resp.body?.string() ?: "")
                if (json.optInt("code") == 0) {
                    val data = json.optJSONObject("data") ?: JSONObject()
                    Triple(true, data.optString("hash", ""), data.optString("key", ""))
                } else Triple(false, "", "")
            } catch (e: Exception) {
                Triple(false, "", "")
            }
        }
    }

    /** RSA加密密码 */
    fun rsaEncrypt(password: String, publicKeyStr: String): String {
        return try {
            val keyBytes = android.util.Base64.decode(publicKeyStr, android.util.Base64.DEFAULT)
            val spec = java.security.spec.X509EncodedKeySpec(keyBytes)
            val key = java.security.KeyFactory.getInstance("RSA").generatePublic(spec)
            val cipher = javax.crypto.Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key)
            android.util.Base64.encodeToString(cipher.doFinal(password.toByteArray(Charsets.UTF_8)), android.util.Base64.NO_WRAP)
        } catch (e: Exception) { "" }
    }

    /** 密码登录 */
    suspend fun loginByPassword(username: String, password: String): Pair<Boolean, String> {
        return withContext(Dispatchers.IO) {
            try {
                val (ok, hash, pubKey) = getWebKey()
                if (!ok) return@withContext Pair(false, "获取密钥失败")
                val encrypted = rsaEncrypt("$hash$password", pubKey)
                val form = FormBody.Builder()
                    .add("username", username)
                    .add("password", encrypted)
                    .add("source", "main_web")
                    .add("go_url", "https://www.bilibili.com/")
                    .build()
                val resp = client.newCall(Request.Builder()
                    .url("https://passport.bilibili.com/x/passport-login/web/login")
                    .post(form)
                    .addHeader("User-Agent", "Mozilla/5.0")
                    .addHeader("Referer", "https://passport.bilibili.com/")
                    .build()).execute()
                val body = resp.body?.string() ?: ""
                val cookieStr = buildCookieString(resp.headers("set-cookie"))
                val json = JSONObject(body)
                val code = json.optInt("code", -1)
                if (code == 0) {
                    val data = json.optJSONObject("data")
                    if (data?.optInt("status") == 2) {
                        // 需要风控验证
                        Pair(false, "需要手机验证（风险环境）")
                    } else if (cookieStr.contains("SESSDATA")) {
                        Pair(true, cookieStr)
                    } else {
                        Pair(false, "登录失败")
                    }
                } else {
                    Pair(false, json.optString("message", "账号或密码错误"))
                }
            } catch (e: Exception) {
                Pair(false, "网络错误")
            }
        }
    }

    /** 短信验证码登录 */
    suspend fun loginBySms(phone: String, code: String, captchaKey: String = ""): Pair<Boolean, String> {
        return withContext(Dispatchers.IO) {
            try {
                val ts = System.currentTimeMillis() / 1000
                val params = mutableMapOf(
                    "tel" to phone, "cid" to "86", "code" to code,
                    "mobi_app" to "android_hd", "platform" to "android",
                    "build" to "2001100", "channel" to "master",
                    "buvid" to buvid, "local_id" to buvid,
                    "c_locale" to "zh_CN", "s_locale" to "zh_CN",
                    "disable_rcmd" to "0", "ts" to ts.toString(),
                    "statistics" to "{\"appId\":1,\"platform\":3,\"version\":\"7.78.0\",\"abtest\":\"\"}"
                )
                if (captchaKey.isNotBlank()) params["captcha_key"] = captchaKey
                appSign(params)

                val fb = FormBody.Builder()
                params.forEach { (k, v) -> fb.add(k, v) }
                val resp = client.newCall(Request.Builder()
                    .url("https://passport.bilibili.com/x/passport-login/login/sms")
                    .post(fb.build())
                    .addHeader("User-Agent", "Mozilla/5.0")
                    .addHeader("Cookie", buildString {
                        append("buvid3=$buvid")
                            })
                    .build()).execute()
                val body = resp.body?.string() ?: ""
                Log.d(TAG, "loginBySms: ${body.take(300)}")
                val cookieStr = buildCookieString(resp.headers("set-cookie"))
                val json = JSONObject(body)
                if (json.optInt("code") == 0) {
                    val data = json.optJSONObject("data")
                    val cookies = data?.optJSONObject("cookie_info")?.optJSONArray("cookies")
                    var finalCookie = cookieStr
                    if (cookies != null) {
                        val sb = StringBuilder()
                        for (i in 0 until cookies.length()) {
                            val c = cookies.getJSONObject(i)
                            sb.append("${c.optString("name")}=${c.optString("value")}; ")
                        }
                        if (sb.isNotBlank()) finalCookie = sb.toString().trimEnd(';', ' ')
                    }
                    if (finalCookie.isNotBlank()) Pair(true, finalCookie)
                    else Pair(false, "未获取到登录凭证")
                } else {
                    Pair(false, json.optString("message", "登录失败"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "loginBySms error", e)
                Pair(false, "网络错误")
            }
        }
    }

    // ===== QR Login =====
    suspend fun getQRCode(): QRCodeData? { /* ... existing code unchanged ... */
        return withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder().url("https://passport.bilibili.com/x/passport-login/web/qrcode/generate?source=main-fe-header")
                    .addHeader("User-Agent", "Mozilla/5.0").build()
                val resp = client.newCall(req).execute()
                val json = JSONObject(resp.body?.string() ?: "")
                if (json.optInt("code") == 0) {
                    val d = json.optJSONObject("data") ?: return@withContext null
                    QRCodeData(d.optString("url"), d.optString("qrcode_key"))
                } else null
            } catch (e: Exception) { Log.e(TAG, "QR error", e); null }
        }
    }

    suspend fun pollQRCode(qrcodeKey: String): Pair<Boolean, String> { /* ... existing code unchanged ... */
        return withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder().url("https://passport.bilibili.com/x/passport-login/web/qrcode/poll?qrcode_key=$qrcodeKey&source=main-fe-header")
                    .addHeader("User-Agent", "Mozilla/5.0").build()
                val resp = client.newCall(req).execute()
                val json = JSONObject(resp.body?.string() ?: "")
                if (json.optInt("code") != 0) return@withContext Pair(false, json.optString("message"))
                val data = json.optJSONObject("data") ?: return@withContext Pair(false, "无数据")
                when (data.optInt("code")) {
                    0 -> {
                        val url = data.optString("url", "")
                        var cookie = buildCookieString(resp.headers("set-cookie"))
                        if (url.isNotBlank()) {
                            try {
                                val ssoResp = client.newCall(Request.Builder().url(url)
                                    .addHeader("User-Agent", "Mozilla/5.0").build()).execute()
                                ssoResp.body?.close()
                                val c = buildCookieString(ssoResp.headers("set-cookie"))
                                if (c.isNotBlank()) cookie = c
                            } catch (_: Exception) {}
                        }
                        if (!cookie.contains("SESSDATA") && url.contains("SESSDATA=")) {
                            cookie = url.substringAfter("?").split("&").filter { it.contains("=") }
                                .joinToString("; ") { java.net.URLDecoder.decode(it, "UTF-8") }
                        }
                        Pair(true, cookie)
                    }
                    86090 -> Pair(false, "已扫描，请在手机上确认")
                    86101 -> Pair(false, "等待扫码...")
                    86038 -> Pair(false, "二维码已过期")
                    else -> Pair(false, "状态: ${data.optInt("code")}")
                }
            } catch (e: Exception) { Log.e(TAG, "pollQR error", e); Pair(false, "网络错误") }
        }
    }

    suspend fun getUserInfo(cookie: String): UserInfo? { /* ... existing code unchanged ... */
        return withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder().url("https://api.bilibili.com/x/web-interface/nav")
                    .addHeader("Cookie", cookie).addHeader("User-Agent", "Mozilla/5.0").build()
                val resp = client.newCall(req).execute()
                val json = JSONObject(resp.body?.string() ?: "")
                if (json.optInt("code") == 0) {
                    val data = json.optJSONObject("data") ?: return@withContext null
                    if (data.optBoolean("isLogin")) {
                        val user = data.optJSONObject("user_info") ?: data
                        val level = data.optJSONObject("level_info")
                        UserInfo(user.optLong("mid"), user.optString("uname"),
                            user.optString("face"), level?.optInt("current_level") ?: 0, true)
                    } else null
                } else null
            } catch (e: Exception) { Log.e(TAG, "getUserInfo error", e); null }
        }
    }

    private fun md5(input: String): String {
        val digest = java.security.MessageDigest.getInstance("MD5")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun buildCookieString(setCookies: List<String>): String {
        val sb = StringBuilder()
        setCookies.forEach { cookie ->
            val parts = cookie.split(";")
            if (parts.isNotEmpty()) {
                val nv = parts[0].trim()
                if (nv.contains("=")) sb.append(nv).append("; ")
            }
        }
        return sb.toString().trimEnd(';', ' ')
    }
}
