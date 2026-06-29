package com.bili.music.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Wraps EncryptedSharedPreferences to securely store the Bilibili session cookie.
 * Uses Android Keystore for key management and AES-256 GCM for encryption.
 */
class EncryptedCookieStore(context: Context) {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "bilimusic_encrypted_cookie",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val COOKIE_KEY = "bilibili_cookie"

    /** Flow of the current cookie value (in-memory, updates on write). */
    private val _cookieFlow = MutableStateFlow(prefs.getString(COOKIE_KEY, "") ?: "")

    /** Observe the cookie value reactively. */
    val cookie: Flow<String> = _cookieFlow

    /** Get current cookie synchronously. */
    fun get(): String = _cookieFlow.value

    /** Store a new cookie value (encrypted at rest). */
    fun set(cookie: String) {
        prefs.edit().putString(COOKIE_KEY, cookie).apply()
        _cookieFlow.value = cookie
    }

    /** Clear the stored cookie. */
    fun clear() {
        prefs.edit().remove(COOKIE_KEY).apply()
        _cookieFlow.value = ""
    }
}
