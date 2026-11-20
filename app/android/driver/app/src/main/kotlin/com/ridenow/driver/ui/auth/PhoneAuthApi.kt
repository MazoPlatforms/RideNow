package com.ridenow.driver.ui.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** RideNow's OTP API. OTP delivery is WhatsApp; Android cannot inspect WhatsApp messages. */
object PhoneAuthApi {
    private const val BASE_URL = "https://mzn-api.onrender.com"
    private const val PREFS = "ridenow_driver_auth_session"

    private fun prefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREFS,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    suspend fun requestCode(context: Context, phone: String): String = withContext(Dispatchers.IO) {
        val response = post("/v1/auth/otp/request", JSONObject().put("phone", phone))
        val challengeId = response.optString("challengeId")
        check(challengeId.isNotBlank()) { "OTP response did not contain a challenge ID" }
        challengeId
    }

    suspend fun verifyCode(context: Context, phone: String, challengeId: String, code: String): String = withContext(Dispatchers.IO) {
        val result = post("/v1/auth/otp/verify", JSONObject().put("challengeId", challengeId).put("code", code))
        val access = result.optString("accessToken")
        val refresh = result.optString("refreshToken")
        check(access.isNotBlank() && refresh.isNotBlank()) { "Authentication response did not contain session tokens" }
        prefs(context).edit().putString("access_token", access).putString("refresh_token", refresh)
            .putString("phone", phone).apply()
        result.optJSONObject("user")?.optString("name").orEmpty()
    }

    /** Rotates the refresh token and returns the saved phone only for a valid server session. */
    suspend fun restoreSession(context: Context): String? = withContext(Dispatchers.IO) {
        val storage = prefs(context)
        val refresh = storage.getString("refresh_token", null) ?: return@withContext null
        val phone = storage.getString("phone", null) ?: return@withContext null
        try {
            val result = post("/v1/auth/refresh", JSONObject().put("refreshToken", refresh))
            val access = result.optString("accessToken")
            val nextRefresh = result.optString("refreshToken")
            check(access.isNotBlank() && nextRefresh.isNotBlank())
            storage.edit().putString("access_token", access).putString("refresh_token", nextRefresh).apply()
            phone
        } catch (_: Exception) {
            storage.edit().clear().apply()
            null
        }
    }

    suspend fun signOut(context: Context) = withContext(Dispatchers.IO) {
        val storage = prefs(context)
        var access = storage.getString("access_token", null)
        try {
            if (access.isNullOrBlank()) return@withContext
            try {
                post("/v1/auth/logout", JSONObject(), access)
            } catch (_: Exception) {
                val refresh = storage.getString("refresh_token", null) ?: return@withContext
                val rotated = post("/v1/auth/refresh", JSONObject().put("refreshToken", refresh))
                access = rotated.optString("accessToken")
                if (!access.isNullOrBlank()) post("/v1/auth/logout", JSONObject(), access)
            }
        } finally {
            storage.edit().clear().apply()
        }
    }

    private fun post(path: String, payload: JSONObject, bearer: String? = null): JSONObject {
        val connection = (URL(BASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            // Render's free web service can take over 20s to wake from idle.
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (bearer != null) setRequestProperty("Authorization", "Bearer $bearer")
        }
        return try {
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(body) }.getOrElse { JSONObject() }
            if (status !in 200..299) throw PhoneAuthException(
                json.optString("code", "NETWORK_ERROR"), json.optString("message", "Could not complete verification"), status,
            )
            json
        } finally {
            connection.disconnect()
        }
    }
}

class PhoneAuthException(val code: String, message: String, val status: Int) : Exception(message)
