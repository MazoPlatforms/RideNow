package com.ridenow.app.auth

import android.content.Context
import android.util.Base64
import com.ridenow.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

data class AuthSession(val accessToken: String, val refreshToken: String, val userId: String)

class AuthApiException(val code: String, message: String, val status: Int? = null) : Exception(message)

class AuthRepository(context: Context) {
    private val sessions = EncryptedSessionStore(context.applicationContext)

    suspend fun requestOtp(phone: String): String = withContext(Dispatchers.IO) {
        val response = request("/v1/auth/otp/request", JSONObject().put("phone", phone), expected = 202)
        response.getString("challengeId")
    }

    suspend fun verifyOtp(challengeId: String, code: String): AuthSession = withContext(Dispatchers.IO) {
        val response = request(
            "/v1/auth/otp/verify",
            JSONObject().put("challengeId", challengeId).put("code", code),
        )
        val user = response.getJSONObject("user")
        AuthSession(response.getString("accessToken"), response.getString("refreshToken"), user.getString("id"))
            .also(sessions::save)
    }

    suspend fun restoreSession(): AuthSession? = withContext(Dispatchers.IO) {
        val stored = sessions.read() ?: return@withContext null
        try {
            val response = request("/v1/auth/refresh", JSONObject().put("refreshToken", stored.refreshToken))
            AuthSession(response.getString("accessToken"), response.getString("refreshToken"), response.getJSONObject("user").getString("id"))
                .also(sessions::save)
        } catch (failure: AuthApiException) {
            if (failure.status == 401 || failure.code in setOf("INVALID_REFRESH", "REFRESH_REUSE", "SESSION_REVOKED", "INVALID_TOKEN")) {
                sessions.clear()
                null
            } else throw failure
        }
    }

    suspend fun logout() = withContext(Dispatchers.IO) { sessions.clear() }

    private fun request(path: String, body: JSONObject, expected: Int = 200): JSONObject {
        val connection = (URL(BuildConfig.API_BASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val payload = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = try { JSONObject(payload) } catch (_: Exception) { JSONObject() }
            if (status != expected) {
                val code = json.optString("code", "NETWORK_ERROR")
                throw AuthApiException(code, friendlyMessage(code), status)
            }
            return json
        } catch (error: AuthApiException) {
            throw error
        } catch (error: IOException) {
            throw AuthApiException("NETWORK_ERROR", "Could not reach RideNow. Check your internet connection and try again.")
        } finally {
            connection.disconnect()
        }
    }

    private fun friendlyMessage(code: String): String = when (code) {
        "INVALID_SRI_LANKAN_MOBILE" -> "Enter a valid Sri Lankan mobile number beginning with 7."
        "OTP_RATE_LIMIT", "RATE_LIMITED" -> "Please wait before requesting another code."
        "OTP_PROVIDER_UNAVAILABLE" -> "WhatsApp verification is temporarily unavailable. Try again shortly."
        "OTP_INVALID" -> "That code is incorrect, expired, or already used. Request a new code."
        "STAFF_LOGIN_REQUIRED" -> "This number belongs to a staff account. Use the staff sign-in portal."
        "ACCOUNT_UNAVAILABLE" -> "This account is currently unavailable. Contact RideNow support."
        "NETWORK_ERROR" -> "Could not reach RideNow. Check your internet connection and try again."
        else -> "Could not complete verification. Please try again."
    }
}

/** Stores bearer and refresh credentials encrypted with an Android Keystore AES-GCM key. */
private class EncryptedSessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("auth_session", Context.MODE_PRIVATE)
    private val alias = "ridenow.auth.session.v1"

    @Synchronized
    fun save(session: AuthSession) {
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key())
        val plaintext = JSONObject()
            .put("accessToken", session.accessToken)
            .put("refreshToken", session.refreshToken)
            .put("userId", session.userId)
            .toString()
            .toByteArray(StandardCharsets.UTF_8)
        val encoded = Base64.encodeToString(cipher.iv + cipher.doFinal(plaintext), Base64.NO_WRAP)
        check(prefs.edit().putString("encrypted", encoded).commit()) { "Could not save the sign-in session" }
    }

    @Synchronized
    fun read(): AuthSession? {
        val encoded = prefs.getString("encrypted", null) ?: return null
        return try {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            require(bytes.size > 12)
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                javax.crypto.Cipher.DECRYPT_MODE,
                key(),
                javax.crypto.spec.GCMParameterSpec(128, bytes.copyOfRange(0, 12)),
            )
            val json = JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), StandardCharsets.UTF_8))
            AuthSession(json.getString("accessToken"), json.getString("refreshToken"), json.getString("userId"))
        } catch (_: Exception) {
            clear()
            null
        }
    }

    @Synchronized
    fun clear() {
        prefs.edit().clear().commit()
    }

    private fun key(): java.security.Key {
        val store = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        store.getKey(alias, null)?.let { return it }
        val generator = javax.crypto.KeyGenerator.getInstance("AES", "AndroidKeyStore")
        generator.init(
            android.security.keystore.KeyGenParameterSpec.Builder(
                alias,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
            ).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }
}
