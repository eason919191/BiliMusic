package com.bilimusic.ui.screens.netease

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.bilimusic.data.api.netease.NeteaseApiClient

class NeteaseWebLoginActivity : ComponentActivity() {
    companion object {
        const val RESULT_COOKIE = "result_cookie_map_json"
        private const val TARGET_URL = "https://music.163.com/"
    }

    private lateinit var webView: WebView
    private var hasReturned = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        webView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                allowFileAccess = false
                userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
            }
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    checkLoginAndReturn()
                }
                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                    super.doUpdateVisitedHistory(view, url, isReload)
                    checkLoginAndReturn()
                }
            }
        }

        setContentView(webView)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })

        webView.loadUrl(TARGET_URL)
    }

    private fun checkLoginAndReturn() {
        if (hasReturned) return
        CookieManager.getInstance().flush()
        val cookies = readCookieMap()
        if (cookies.containsKey("MUSIC_U") && cookies["MUSIC_U"]!!.isNotBlank()) {
            hasReturned = true
            val json = org.json.JSONObject(cookies as Map<*, *>).toString()
            setResult(RESULT_OK, Intent().putExtra(RESULT_COOKIE, json))
            finish()
        }
    }

    private fun readCookieMap(): Map<String, String> {
        val cm = CookieManager.getInstance()
        val main = cm.getCookie("https://music.163.com").orEmpty()
        val api = cm.getCookie("https://interface.music.163.com").orEmpty()
        val merged = listOf(main, api).filter { it.isNotBlank() }.joinToString("; ")
        if (merged.isBlank()) return emptyMap()

        val map = linkedMapOf<String, String>()
        merged.split(';').map { it.trim() }.filter { it.contains('=') }.forEach { part ->
            val idx = part.indexOf('=')
            val key = part.substring(0, idx).trim()
            val value = part.substring(idx + 1).trim()
            if (key.isNotEmpty()) map[key] = value
        }
        return map
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
        super.onDestroy()
    }
}
