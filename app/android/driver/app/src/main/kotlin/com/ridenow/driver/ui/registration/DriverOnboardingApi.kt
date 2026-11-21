package com.ridenow.driver.ui.registration

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.staticCompositionLocalOf
import com.ridenow.driver.ui.auth.PhoneAuthApi
import com.ridenow.driver.ui.auth.PhoneAuthException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URL
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import java.util.UUID
import javax.net.ssl.HttpsURLConnection

internal val LocalDriverDraft = staticCompositionLocalOf<DriverDraft?> { null }

internal data class DriverFile(val photo: Bitmap, val expiresOn: String? = null)

internal class DriverDraft {
    val fields = JSONObject()
    var vehicle: JSONObject? = null
    val files = mutableMapOf<String, DriverFile>()
    var acceptedTerms = false
    private val staged = mutableMapOf<Triple<String, String?, DriverFile>, String>()

    fun photo(kind: String) = files[kind]?.photo

    fun setPhoto(kind: String, photo: Bitmap?, expiresOn: String? = null) {
        if (photo == null) files.remove(kind) else files[kind] = DriverFile(photo, expiresOn)
    }

    fun uploaded(kind: String, vehicleId: String?, file: DriverFile) = staged[Triple(kind, vehicleId, file)]

    fun markUploaded(kind: String, vehicleId: String?, file: DriverFile, documentId: String) {
        staged.keys.removeAll { it.first == kind }
        staged[Triple(kind, vehicleId, file)] = documentId
    }

    fun clearUploads() = staged.clear()
}

internal fun pickedDay(millis: Long): String =
    Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC).toLocalDate().toString()

internal fun clockTime(minutes: Int): String =
    String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60)

internal object DriverOnboardingApi {
    private const val MIME = "image/jpeg"
    private val required = setOf(
        "PROFILE_PHOTO", "IDENTITY_FRONT", "IDENTITY_BACK", "LICENSE_FRONT", "LICENSE_BACK",
        "REGISTRATION", "REVENUE_LICENSE", "INSURANCE",
        "VEHICLE_FRONT", "VEHICLE_REAR", "VEHICLE_LEFT", "VEHICLE_RIGHT",
    )

    suspend fun status(context: Context): JSONObject? = try {
        PhoneAuthApi.authorized(context, "GET", "/v1/drivers/me")
    } catch (error: PhoneAuthException) {
        if (error.code == "DRIVER_REQUIRED") null else throw error
    }

    suspend fun prefillPersonalDetails(context: Context, draft: DriverDraft) {
        val profile = try {
            PhoneAuthApi.authorized(context, "GET", "/v1/me")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: PhoneAuthException) {
            if (error.status == 401) throw error
            return
        } catch (_: Exception) {
            return
        }
        for (field in listOf("name", "email", "dateOfBirth")) {
            val value = profile.opt(field) as? String
            if (!draft.fields.has(field) && !value.isNullOrBlank()) draft.fields.put(field, value)
        }
        if (!draft.fields.has("gender")) {
            val gender = when (profile.optString("gender")) {
                "MALE" -> "MALE"
                "FEMALE" -> "FEMALE"
                "NON_BINARY" -> "OTHER"
                else -> null
            }
            if (gender != null) draft.fields.put("gender", gender)
        }
        val photoUrl = profile.opt("profilePhotoUrl") as? String
        if (photoUrl != null && draft.photo("PROFILE_PHOTO") == null) {
            val photo = try {
                downloadProfilePhoto(photoUrl)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (photo != null) draft.setPhoto("PROFILE_PHOTO", photo)
        }
    }

    suspend fun downloadProfilePhoto(value: String): Bitmap? = withContext(Dispatchers.IO) {
        val url = URL(value)
        if (url.protocol != "https" || url.userInfo != null || url.host.isBlank()) return@withContext null
        val connection = (url.openConnection() as HttpsURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 5_000
            readTimeout = 5_000
            useCaches = false
        }
        try {
            if (connection.responseCode !in 200..299) return@withContext null
            val data = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count == -1) break
                    if (output.size() + count > 3 * 1024 * 1024) return@withContext null
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
            if (bounds.outWidth !in 1..2048 || bounds.outHeight !in 1..2048) return@withContext null
            BitmapFactory.decodeByteArray(data, 0, data.size)
        } finally {
            connection.disconnect()
        }
    }

    suspend fun updateContact(context: Context, payload: JSONObject): JSONObject =
        PhoneAuthApi.authorized(context, "PATCH", "/v1/drivers/profile", payload)

    suspend fun replaceProfilePhoto(context: Context, image: Bitmap): String? {
        val bytes = withContext(Dispatchers.IO) {
            val maxSide = maxOf(image.width, image.height)
            val scaled = if (maxSide <= 512) image else {
                val ratio = 512f / maxSide
                Bitmap.createScaledBitmap(image, (image.width * ratio).toInt().coerceAtLeast(1),
                    (image.height * ratio).toInt().coerceAtLeast(1), true)
            }
            ByteArrayOutputStream().use { output ->
                check(scaled.compress(Bitmap.CompressFormat.JPEG, 86, output))
                output.toByteArray()
            }
        }
        val staged = PhoneAuthApi.authorized(context, "POST", "/v1/drivers/profile-photo",
            JSONObject().put("mime", MIME).put("bytes", bytes.size), UUID.randomUUID().toString())
        putImage(staged.getString("uploadUrl"), bytes)
        val id = staged.getString("photoId")
        val profile = PhoneAuthApi.authorized(context, "POST", "/v1/drivers/profile-photo/$id/complete", JSONObject())
        return profile.opt("profilePhotoUrl") as? String
    }

    suspend fun beginReapplication(context: Context) {
        PhoneAuthApi.authorized(context, "POST", "/v1/drivers/reapplication",
            JSONObject().put("confirmed", true), UUID.randomUUID().toString())
    }

    suspend fun queue(context: Context, draft: DriverDraft, progress: (Int, Int) -> Unit): JSONObject {
        val vehicle = draft.vehicle ?: throw IllegalStateException("INCOMPLETE_DRIVER_FORM")
        check(draft.acceptedTerms && draft.files.keys.containsAll(required) &&
            listOf("name","dateOfBirth","gender","identity","license","schedule","serviceArea").all(draft.fields::has)) {
            "INCOMPLETE_DRIVER_FORM"
        }
        val application = JSONObject(draft.fields.toString()).put("consent", JSONObject()
            .put("termsVersion", "1").put("termsAccepted", true).put("locationAccepted", true))
        PhoneAuthApi.authorized(context, "POST", "/v1/drivers/onboarding",
            JSONObject().put("name", application.getString("name")), UUID.randomUUID().toString())
        PhoneAuthApi.authorized(context, "PUT", "/v1/drivers/application", application)
        val vehicleId = PhoneAuthApi.authorized(context, "POST", "/v1/drivers/vehicles",
            vehicle, UUID.randomUUID().toString()).getString("id")
        val files = draft.files.toList()
        val documentIds = JSONArray()
        for ((index, entry) in files.withIndex()) {
            val (kind, file) = entry
            progress(index + 1, files.size)
            val owner = vehicleId.takeIf { kind.startsWith("VEHICLE_") || kind in setOf("REGISTRATION", "REVENUE_LICENSE", "INSURANCE") }
            val documentId = draft.uploaded(kind, owner, file) ?: upload(context, kind, owner, file).also {
                draft.markUploaded(kind, owner, file, it)
            }
            documentIds.put(documentId)
        }
        val pending = JSONObject().put("vehicleId", vehicleId).put("documentIds", documentIds)
            .put("informationConfirmed", true)
        PhoneAuthApi.savePendingApplication(context, pending)
        return pending
    }

    private suspend fun upload(context: Context, kind: String, vehicleId: String?, file: DriverFile): String {
        val bytes = withContext(Dispatchers.IO) {
            val output = ByteArrayOutputStream()
            check(file.photo.compress(Bitmap.CompressFormat.JPEG, 88, output)) { "INVALID_DRIVER_PHOTO" }
            output.toByteArray().also { check(it.isNotEmpty() && it.size <= 10 * 1024 * 1024) { "INVALID_DRIVER_PHOTO" } }
        }
        val payload = JSONObject().put("kind", kind).put("mime", MIME).put("bytes", bytes.size)
        vehicleId?.let { payload.put("vehicleId", it) }
        file.expiresOn?.let { payload.put("expiresAt", "${it}T23:59:59Z") }
        val upload = PhoneAuthApi.authorized(context, "POST", "/v1/drivers/documents", payload, UUID.randomUUID().toString())
        val id = upload.getString("documentId")
        putImage(upload.getString("uploadUrl"), bytes)
        PhoneAuthApi.authorized(context, "POST", "/v1/drivers/documents/$id/submit", JSONObject(), id)
        return id
    }

    private suspend fun putImage(value: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val url = URL(value)
        check(url.protocol == "https" && url.userInfo == null && url.host.isNotBlank())
        val connection = (url.openConnection() as HttpsURLConnection).apply {
            requestMethod = "PUT"
            instanceFollowRedirects = false
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 45_000
            setFixedLengthStreamingMode(bytes.size)
            setRequestProperty("Content-Type", MIME)
        }
        try {
            connection.outputStream.use { it.write(bytes) }
            check(connection.responseCode in 200..299) { "DOCUMENT_UPLOAD_FAILED" }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun submit(context: Context, pending: JSONObject): JSONObject =
        PhoneAuthApi.authorized(context, "POST", "/v1/drivers/submit", pending, UUID.randomUUID().toString())
}
