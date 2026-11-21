package com.ridenow.driver.ui.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.util.UUID
import javax.net.ssl.HttpsURLConnection

/** Authenticated RideNow requests use the Keystore-backed session from WhatsApp verification. */
object PhoneAuthApi {
    private const val BASE_URL = "https://mzn-api.onrender.com"
    private const val PREFS = "ridenow_driver_auth_session"
    private const val PENDING = "pending_driver_application"
    private val lock = Mutex()

    private fun prefs(context: Context) = EncryptedSharedPreferences.create(
        context, PREFS, MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    data class SignedInDevice(
        val id: String, val name: String?, val app: String?, val updatedAt: String, val current: Boolean,
    )

    @Synchronized
    private fun deviceMetadata(context: Context): JSONObject {
        val storage = context.getSharedPreferences("ridenow_installation", Context.MODE_PRIVATE)
        val id = storage.getString("id", null) ?: UUID.randomUUID().toString().also {
            check(storage.edit().putString("id", it).commit())
        }
        val systemName = runCatching {
            android.provider.Settings.Global.getString(context.contentResolver, "device_name")
        }.getOrNull()
        val cleanName = Regex("[\\p{C}]")
        val name = sequenceOf(systemName, android.os.Build.MODEL, "Android")
            .map { cleanName.replace(it.orEmpty().take(80), "").trim() }
            .first { it.isNotEmpty() }
        return JSONObject().put("installationId", id).put("name", name).put("app", "DRIVER")
    }

    suspend fun listDevices(context: Context, register: Boolean = false): List<SignedInDevice> {
        val response = if (register) {
            val metadata = withContext(Dispatchers.IO) { deviceMetadata(context) }
            authorized(context, "PUT", "/v1/auth/devices/current", metadata)
        } else authorized(context, "GET", "/v1/auth/devices")
        val items = response.getJSONArray("items")
        return List(items.length()) { index ->
            val item = items.getJSONObject(index)
            SignedInDevice(item.getString("id"), item.opt("name") as? String, item.opt("app") as? String,
                item.getString("updatedAt"), item.getBoolean("current"))
        }
    }

    suspend fun revokeDevice(context: Context, id: String?) {
        if (id == null) authorized(context, "POST", "/v1/auth/devices/revoke-others")
        else {
            val family = UUID.fromString(id)
            authorized(context, "DELETE", "/v1/auth/devices/$family")
        }
    }

    suspend fun requestAccountDeletion(context: Context): JSONObject =
        authorized(context, "POST", "/v1/auth/account-deletion/request")

    suspend fun confirmAccountDeletion(context: Context, id: String, code: String, factor: String?) {
        val body = JSONObject().put("challengeId", id).put("code", code)
        factor?.let { body.put("factor", it) }
        check(authorized(context, "POST", "/v1/auth/account-deletion/confirm", body,
            clearSession = true).getBoolean("deleted"))
    }

    suspend fun twoStepSettings(context: Context, action: String = "", body: JSONObject? = null): JSONObject {
        require(action in setOf("", "/setup", "/enable", "/disable", "/recovery"))
        return authorized(context, if (action.isEmpty()) "GET" else "POST", "/v1/auth/two-step" + action, body)
    }

    suspend fun requestCode(context: Context, phone: String): String = withContext(Dispatchers.IO) {
        request("POST", "/v1/auth/otp/request", JSONObject().put("phone", phone)).getString("challengeId")
    }

    suspend fun verifyCode(context: Context, phone: String, challengeId: String, code: String): String = withContext(Dispatchers.IO) {
        lock.withLock {
            val result = request("POST", "/v1/auth/otp/verify", JSONObject().put("challengeId", challengeId).put("code", code)
                .put("device", deviceMetadata(context)))
            completeLogin(context, phone, result)
        }
    }

    suspend fun verifySecondStep(context: Context, phone: String, token: String, code: String): String = withContext(Dispatchers.IO) {
        lock.withLock {
            val result = request("POST", "/v1/auth/two-step/verify", JSONObject().put("token", token).put("code", code))
            completeLogin(context, phone, result)
        }
    }

    private fun completeLogin(context: Context, phone: String, result: JSONObject): String {
        (result.opt("twoStepToken") as? String)?.let { throw TwoStepRequired(it) }
        val storage = prefs(context)
        val verifiedPhone = result.optJSONObject("user")?.optString("phone")?.ifBlank { null } ?: phone
        val edit = storage.edit().putString("phone", verifiedPhone)
        if (storage.getString("phone", null) != verifiedPhone) edit.remove(PENDING)
        writeTokens(edit, result)
        return result.optJSONObject("user")?.optString("name").orEmpty()
    }

    suspend fun restoreSession(context: Context): String? = withContext(Dispatchers.IO) {
        lock.withLock {
            val storage = prefs(context)
            val phone = storage.getString("phone", null) ?: return@withLock null
            if (storage.getString("access_token", null).isNullOrBlank()) null else phone
        }
    }

    suspend fun authorized(
        context: Context, method: String, path: String, body: JSONObject? = null, key: String? = null,
        replaceSession: Boolean = false,
        clearSession: Boolean = false,
    ): JSONObject = withContext(Dispatchers.IO) {
        lock.withLock {
            val storage = prefs(context)
            val access = storage.getString("access_token", null) ?: throw PhoneAuthException("AUTH_REQUIRED", 401)
            val result = try {
                request(method, path, body, access, key)
            } catch (error: PhoneAuthException) {
                if (error.status != 401) throw error
                request(method, path, body, rotate(storage), key)
            }
            if (clearSession && result.optBoolean("deleted")) storage.edit().clear().commit()
            if (replaceSession) {
                val verifiedPhone = result.getJSONObject("user").getString("phone")
                writeTokens(storage.edit().putString("phone", verifiedPhone), result)
            }
            result
        }
    }

    suspend fun requestPhoneChange(context: Context, target: String): JSONObject =
        authorized(context, "POST", "/v1/auth/phone-change/request", JSONObject().put("phone", target))

    suspend fun verifyPhoneChange(context: Context, id: String, currentCode: String, newCode: String): String =
        authorized(context, "POST", "/v1/auth/phone-change/verify",
            JSONObject().put("changeId", id).put("currentCode", currentCode).put("newCode", newCode),
            replaceSession = true).getJSONObject("user").getString("phone")

    suspend fun pendingApplication(context: Context): JSONObject? = withContext(Dispatchers.IO) {
        lock.withLock { prefs(context).getString(PENDING, null)?.let { JSONObject(it) } }
    }

    suspend fun savePendingApplication(context: Context, value: JSONObject?) = withContext(Dispatchers.IO) {
        lock.withLock {
            val storage = prefs(context)
            if (value != null && storage.getString("access_token", null).isNullOrBlank())
                throw PhoneAuthException("AUTH_REQUIRED", 401)
            val edit = storage.edit()
            if (value == null) edit.remove(PENDING) else edit.putString(PENDING, value.toString())
            check(edit.commit())
        }
    }

    suspend fun signOut(context: Context) = withContext(Dispatchers.IO) {
        lock.withLock {
            val storage = prefs(context)
            try {
                val access = storage.getString("access_token", null) ?: return@withLock
                try {
                    request("POST", "/v1/auth/logout", JSONObject(), access)
                } catch (error: PhoneAuthException) {
                    if (error.status == 401) request("POST", "/v1/auth/logout", JSONObject(), rotate(storage))
                }
            } finally {
                storage.edit().clear().commit()
            }
        }
    }

    private fun rotate(storage: SharedPreferences): String {
        val refresh = storage.getString("refresh_token", null) ?: throw PhoneAuthException("AUTH_REQUIRED", 401)
        val result = try {
            request("POST", "/v1/auth/refresh", JSONObject().put("refreshToken", refresh))
        } catch (error: PhoneAuthException) {
            if (error.status == 401) storage.edit().clear().commit()
            throw error
        }
        return writeTokens(storage.edit(), result)
    }

    private fun writeTokens(edit: SharedPreferences.Editor, result: JSONObject): String {
        val access = result.getString("accessToken")
        val refresh = result.getString("refreshToken")
        check(access.isNotBlank() && refresh.isNotBlank())
        check(edit.putString("access_token", access).putString("refresh_token", refresh).commit())
        return access
    }

    private fun request(method: String, path: String, body: JSONObject?, bearer: String? = null, key: String? = null): JSONObject {
        require(path.startsWith("/v1/"))
        val connection = (URL(BASE_URL + path).openConnection() as HttpsURLConnection).apply {
            requestMethod = method
            instanceFollowRedirects = false
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            if (bearer != null) setRequestProperty("Authorization", "Bearer $bearer")
            if (key != null) setRequestProperty("Idempotency-Key", key)
        }
        return try {
            if (body != null) connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val response = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
            if (status !in 200..299) throw PhoneAuthException(response.optString("code", "NETWORK_ERROR"), status)
            response
        } finally {
            connection.disconnect()
        }
    }
}

class PhoneAuthException(val code: String, val status: Int) : Exception(code)

internal class TwoStepRequired(val token: String) : Exception("TWO_STEP_REQUIRED")
