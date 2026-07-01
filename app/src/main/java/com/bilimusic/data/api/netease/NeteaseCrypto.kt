package com.bilimusic.data.api.netease

import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object NeteaseCrypto {
    private const val BASE62 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    private const val PRESET_KEY = "0CoJUm6Qyw8W8jud"
    private const val IV = "0102030405060708"
    private const val LINUX_KEY = "rFgB&h#%2?^eDg:Q"
    private const val EAPI_KEY = "e82ckenh8dichen8"
    private const val EAPI_FORMAT = "%s-36cd479b6b5-%s-36cd479b6b5-%s"
    private const val EAPI_SALT = "nobody%suse%smd5forencrypt"
    private const val PUBLIC_KEY_PEM = """
        -----BEGIN PUBLIC KEY-----
        MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDgtQn2JZ34ZC28NWYpAUd98iZ37BUrX/aKzmFb
        t7clFSs6sXqHauqKWqdtLkF2KexO40H1YTX8z2lSgBBOAxLsvaklV8k4cBFK9snQXE9/DDaFt6Rr7iVZ
        MldczhC0JNgTz+SHXT6CBHuX3e9SdB1Ua44oncaTWz7OBGLbCiK45wIDAQAB
        -----END PUBLIC KEY-----
    """

    private val secureRandom = SecureRandom()

    fun randomKey(): String {
        val sb = StringBuilder()
        repeat(16) { sb.append(BASE62[secureRandom.nextInt(BASE62.length)]) }
        return sb.toString()
    }

    private fun aesEncrypt(text: String, key: String, ivStr: String, mode: String, format: String): String {
        val secretKey = SecretKeySpec(key.toByteArray(StandardCharsets.UTF_8), "AES")
        val cipher: Cipher = when (mode.lowercase()) {
            "cbc" -> {
                val ci = Cipher.getInstance("AES/CBC/PKCS5Padding")
                val ivSpec = IvParameterSpec(ivStr.toByteArray(StandardCharsets.UTF_8))
                ci.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
                ci
            }
            "ecb" -> {
                val ci = Cipher.getInstance("AES/ECB/PKCS5Padding")
                ci.init(Cipher.ENCRYPT_MODE, secretKey)
                ci
            }
            else -> throw IllegalArgumentException("Unknown AES mode: $mode")
        }
        val encrypted = cipher.doFinal(text.toByteArray(StandardCharsets.UTF_8))
        return when (format.lowercase()) {
            "base64" -> Base64.getEncoder().encodeToString(encrypted)
            "hex" -> encrypted.joinToString("") { "%02x".format(it) }
            else -> throw IllegalArgumentException("Unknown format: $format")
        }
    }

    private fun rsaEncrypt(text: String): String {
        val cleanedKey = PUBLIC_KEY_PEM
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s".toRegex(), "")
        val keyBytes = Base64.getDecoder().decode(cleanedKey)
        val keySpec = X509EncodedKeySpec(keyBytes)
        val pubKey = KeyFactory.getInstance("RSA").generatePublic(keySpec) as java.security.interfaces.RSAPublicKey

        val message = java.math.BigInteger(1, text.toByteArray(StandardCharsets.UTF_8))
        val result = message.modPow(pubKey.publicExponent, pubKey.modulus)

        val keySize = (pubKey.modulus.bitLength() + 7) / 8
        var bytes = result.toByteArray()
        if (bytes.size > keySize) {
            bytes = bytes.copyOfRange(bytes.size - keySize, bytes.size)
        } else if (bytes.size < keySize) {
            val padded = ByteArray(keySize)
            System.arraycopy(bytes, 0, padded, keySize - bytes.size, bytes.size)
            bytes = padded
        }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun md5Hex(data: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(data.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun weApiEncrypt(payload: Map<String, Any>): Map<String, String> {
        val jsonMap = mutableMapOf<String, String>()
        payload.forEach { (k, v) -> jsonMap[k] = v.toString() }
        val jsonStr = org.json.JSONObject(jsonMap as Map<*, *>).toString()
        val secretKey = randomKey()
        val enc1 = aesEncrypt(jsonStr, PRESET_KEY, IV, "cbc", "base64")
        val params = aesEncrypt(enc1, secretKey, IV, "cbc", "base64")
        val encSecKey = rsaEncrypt(secretKey.reversed())
        return mapOf("params" to params, "encSecKey" to encSecKey)
    }

    fun linuxApiEncrypt(payload: Map<String, Any>): Map<String, String> {
        val jsonMap = mutableMapOf<String, String>()
        payload.forEach { (k, v) -> jsonMap[k] = v.toString() }
        val jsonStr = org.json.JSONObject(jsonMap as Map<*, *>).toString()
        return mapOf("eparams" to aesEncrypt(jsonStr, LINUX_KEY, "", "ecb", "hex"))
    }

    fun eApiEncrypt(url: String, payload: Map<String, Any>): Map<String, String> {
        val jsonMap = mutableMapOf<String, String>()
        payload.forEach { (k, v) -> jsonMap[k] = v.toString() }
        val data = org.json.JSONObject(jsonMap as Map<*, *>).toString()
        val apiPath = url.replace("/eapi", "/api")
        val message = String.format(EAPI_FORMAT, apiPath, data,
            md5Hex(String.format(EAPI_SALT, apiPath, data)))
        val cipher = aesEncrypt(message, EAPI_KEY, "", "ecb", "hex").uppercase()
        return mapOf("params" to cipher)
    }
}
