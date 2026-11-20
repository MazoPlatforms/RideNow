package com.ridenow.app.ui.auth

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** Rider authentication and profile API. OTP delivery is WhatsApp, not SMS. */
object PhoneAuthApi {
    private const val BASE_URL = "https://mzn-api.onrender.com"
    private const val PREFS = "ridenow_auth_session"
    private const val MAX_RESPONSE_BYTES = 256 * 1024
    private val sessionMutex = Mutex()

    data class RiderProfile(
        val id: String,
        val phone: String,
        val name: String,
        val email: String,
        val profileComplete: Boolean,
        val profilePhotoUrl: String?,
    )

    private fun prefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREFS,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    suspend fun requestCode(context: Context, phone: String): String = withContext(Dispatchers.IO) {
        val response = request("POST", "/v1/auth/otp/request", JSONObject().put("phone", phone))
        response.optString("challengeId").also {
            check(it.isNotBlank()) { "OTP response did not contain a challenge ID" }
        }
    }

    suspend fun verifyCode(context: Context, challengeId: String, code: String): RiderProfile = withContext(Dispatchers.IO) {
        val result = request("POST", "/v1/auth/otp/verify", JSONObject().put("challengeId", challengeId).put("code", code))
        sessionMutex.withLock { saveTokens(context, result) }
        val issued = parseAuthenticatedProfile(result)
        try { authorized(context, "GET", "/v1/me") { parseProfile(it) } }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (failure: PhoneAuthException) { if (failure.status == 401) throw failure else issued }
        catch (_: Exception) { issued }
    }

    /** Rotates the refresh token and returns the current server profile for session restoration. */
    suspend fun restoreSession(context: Context): RiderProfile? = withContext(Dispatchers.IO) {
        sessionMutex.withLock {
            val storage = prefs(context)
            if (storage.getString("refresh_token", null).isNullOrBlank()) return@withLock null
            try {
                refreshLocked(context)
                val access = storage.getString("access_token", null)
                    ?: throw PhoneAuthException("INVALID_REFRESH", "Session expired", 401)
                parseProfile(request("GET", "/v1/me", bearer = access))
            } catch (failure: PhoneAuthException) {
                if (failure.status == 401) {
                    storage.edit().clear().commit()
                    null
                } else throw failure
            }
        }
    }

    suspend fun downloadProfilePhoto(url: String): Bitmap? = withContext(Dispatchers.IO) {
        val target = URL(url)
        if (target.protocol != "https") return@withContext null
        val connection = (target.openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", "image/jpeg")
            setRequestProperty("Cache-Control", "no-store")
        }
        try {
            if (connection.responseCode !in 200..299) return@withContext null
            val bytes = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > 3 * 1024 * 1024) return@withContext null
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth !in 1..512 || bounds.outHeight !in 1..512) return@withContext null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } finally {
            connection.disconnect()
        }
    }

    /** Saves onboarding/edit-profile fields and uploads an optional profile image through the backend's private staging flow. */
    suspend fun saveProfile(context: Context, name: String, email: String, photo: Bitmap? = null): RiderProfile = withContext(Dispatchers.IO) {
        if (photo != null) uploadProfilePhoto(context, photo)
        authorized(context, "PATCH", "/v1/me", JSONObject().put("name", name).put("email", email)) { parseProfile(it) }
    }

    private suspend fun uploadProfilePhoto(context: Context, source: Bitmap): RiderProfile = withContext(Dispatchers.IO) {
        val scaled = scaleForUpload(source)
        val bytes = ByteArrayOutputStream().use { output ->
            check(scaled.compress(Bitmap.CompressFormat.JPEG, 86, output)) { "Could not encode profile photo" }
            output.toByteArray()
        }
        val upload = authorized(context, "POST", "/v1/me/profile-photo", JSONObject()
            .put("mime", "image/jpeg").put("bytes", bytes.size), UUID.randomUUID().toString()) { it }
        val uploadUrl = upload.optString("uploadUrl")
        val photoId = upload.optString("photoId")
        val target = URL(uploadUrl)
        check(target.protocol == "https" && !photoId.isBlank()) { "Invalid profile photo upload response" }
        putPhoto(target, bytes)
        authorized(context, "POST", "/v1/me/profile-photo/$photoId/complete", JSONObject()) { parseProfile(it) }
    }

    private fun scaleForUpload(bitmap: Bitmap): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= 512) return bitmap
        val ratio = 512f / maxSide
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1), true)
    }

    private suspend fun putPhoto(url: URL, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            instanceFollowRedirects = false
            connectTimeout = 15_000
            readTimeout = 60_000
            doOutput = true
            setFixedLengthStreamingMode(bytes.size)
            setRequestProperty("Content-Type", "image/jpeg")
        }
        try {
            connection.outputStream.use { it.write(bytes) }
            val status = connection.responseCode
            if (status !in 200..299) throw PhoneAuthException("PROFILE_PHOTO_UPLOAD_FAILED", "Could not upload profile photo", status)
        } finally {
            connection.disconnect()
        }
    }

    suspend fun signOut(context: Context) = withContext(Dispatchers.IO) {
        sessionMutex.withLock {
            val storage = prefs(context)
            val access = storage.getString("access_token", null)
            val refresh = storage.getString("refresh_token", null)
            check(storage.edit().clear().commit()) { "Could not clear the local session" }
            if (!access.isNullOrBlank()) {
                try {
                    request("POST", "/v1/auth/logout", JSONObject(), access)
                } catch (failure: PhoneAuthException) {
                    if (failure.status != 401 || refresh.isNullOrBlank()) throw failure
                    val rotated = request("POST", "/v1/auth/refresh", JSONObject().put("refreshToken", refresh))
                    val freshAccess = rotated.optString("accessToken")
                    if (freshAccess.isNotBlank()) request("POST", "/v1/auth/logout", JSONObject(), freshAccess)
                }
            }
        }
    }

    private suspend fun <T> authorized(context: Context, method: String, path: String, payload: JSONObject? = null,
        idempotencyKey: String? = null, transform: (JSONObject) -> T): T = withContext(Dispatchers.IO) {
        sessionMutex.withLock {
            val storage = prefs(context)
            var access = storage.getString("access_token", null)
                ?: throw PhoneAuthException("AUTH_REQUIRED", "Please verify your phone number", 401)
            val result = try {
                request(method, path, payload, access, idempotencyKey)
            } catch (failure: PhoneAuthException) {
                if (failure.status != 401) throw failure
                refreshLocked(context)
                access = storage.getString("access_token", null)
                    ?: throw PhoneAuthException("INVALID_REFRESH", "Session expired", 401)
                request(method, path, payload, access, idempotencyKey)
            }
            transform(result)
        }
    }

    private fun refreshLocked(context: Context): JSONObject {
        val storage = prefs(context)
        val refresh = storage.getString("refresh_token", null)
            ?: throw PhoneAuthException("INVALID_REFRESH", "Session expired", 401)
        val result = try {
            request("POST", "/v1/auth/refresh", JSONObject().put("refreshToken", refresh))
        } catch (failure: PhoneAuthException) {
            if (failure.status == 401) storage.edit().clear().commit()
            throw failure
        }
        saveTokens(context, result)
        return result
    }

    private fun saveTokens(context: Context, response: JSONObject) {
        val access = response.optString("accessToken")
        val refresh = response.optString("refreshToken")
        check(access.isNotBlank() && refresh.isNotBlank()) { "Authentication response did not contain session tokens" }
        check(prefs(context).edit().putString("access_token", access).putString("refresh_token", refresh).commit()) {
            "Could not securely save the session"
        }
    }

    private fun parseProfile(value: JSONObject) = RiderProfile(
        id = value.optString("id"),
        phone = value.optString("phone"),
        name = value.optString("name"),
        email = value.optString("email"),
        profileComplete = value.optBoolean("profileComplete"),
        profilePhotoUrl = value.optString("profilePhotoUrl").takeIf { it.startsWith("https://") },
    )

    private fun parseAuthenticatedProfile(value: JSONObject): RiderProfile {
        val user = value.optJSONObject("user") ?: throw PhoneAuthException("INVALID_RESPONSE", "Unexpected server response", 502)
        return parseProfile(user)
    }

    private fun request(method: String, path: String, payload: JSONObject? = null, bearer: String? = null,
        idempotencyKey: String? = null): JSONObject {
        require(path.startsWith("/v1/") && !path.startsWith("//"))
        val connection = (URL(BASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            instanceFollowRedirects = false
            connectTimeout = 15_000
            readTimeout = 60_000 // Render's free web service can take time to wake from idle.
            setRequestProperty("Accept", "application/json")
            if (payload != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            if (bearer != null) setRequestProperty("Authorization", "Bearer $bearer")
            if (idempotencyKey != null) setRequestProperty("Idempotency-Key", idempotencyKey)
        }
        return try {
            if (payload != null) connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_RESPONSE_BYTES) throw PhoneAuthException("RESPONSE_TOO_LARGE", "Unexpected server response", 502)
                    output.write(buffer, 0, count)
                }
                output.toString(Charsets.UTF_8.name())
            }.orEmpty()
            val json = runCatching { JSONObject(body) }.getOrElse { JSONObject() }
            if (status !in 200..299) throw PhoneAuthException(
                json.optString("code", "NETWORK_ERROR"), json.optString("message", "Could not complete the request"), status,
            )
            json
        } finally {
            connection.disconnect()
        }
    }
}

class PhoneAuthException(val code: String, message: String, val status: Int) : Exception(message)
