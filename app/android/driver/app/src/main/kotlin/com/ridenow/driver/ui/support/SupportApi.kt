package com.ridenow.driver.ui.support

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.ridenow.driver.ui.auth.PhoneAuthApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URL
import java.util.UUID
import javax.net.ssl.HttpsURLConnection

internal enum class SupportKind { TICKET, CHAT, COMPLAINT, SAFETY }
internal data class SupportFile(val id: String, val name: String, val status: String)
internal data class RemoteSupportTicket(val id: String, val subject: String, val status: String, val kind: SupportKind)
internal data class RemoteSupportMessage(val id: String, val sender: String, val body: String, val time: String, val files: List<SupportFile>)
internal data class SupportCursor(val before: String, val id: String)
internal data class SupportList(val tickets: List<RemoteSupportTicket>, val next: SupportCursor?)
internal data class SupportThread(val ticket: RemoteSupportTicket, val messages: List<RemoteSupportMessage>, val next: SupportCursor?)
internal class SupportFileException : Exception()

internal fun supportKey(): String = UUID.randomUUID().toString()

internal class SupportApi(private val context: Context) {
    private val base = "/v1/me/support"
    private val app = "DRIVER"

    private suspend fun call(method: String, path: String, body: JSONObject? = null, key: String? = null): JSONObject =
        PhoneAuthApi.authorized(context, method, base + path, body, key)

    private fun requestBody() = JSONObject().put("app", app)
    private fun query(cursor: SupportCursor?) = "?app=$app" + if (cursor == null) "" else
        "&before=${Uri.encode(cursor.before)}&beforeId=${Uri.encode(cursor.id)}"

    suspend fun list(kind: SupportKind?, cursor: SupportCursor?, status: String? = null, active: Boolean = false): SupportList {
        val data = call("GET", "/tickets" + query(cursor) + (kind?.let { "&kind=${it.name}" } ?: "") +
            (status?.let { "&status=$it" } ?: "") + (if (active) "&active=true" else ""))
        return SupportList(data.getJSONArray("items").objects().map(::ticket), cursor(data))
    }

    suspend fun thread(id: String, cursor: SupportCursor? = null): SupportThread {
        val data = call("GET", "/tickets/$id" + query(cursor))
        val messages = data.getJSONArray("messages").objects().map { message ->
            RemoteSupportMessage(message.getString("id"), message.getString("sender"), message.getString("body"),
                message.getString("created_at"), message.getJSONArray("attachments").objects().map { file ->
                    SupportFile(file.getString("id"), file.getString("name"), file.getString("scan_status"))
                })
        }
        return SupportThread(ticket(data.getJSONObject("ticket")), messages, cursor(data))
    }

    suspend fun send(id: String?, kind: SupportKind, subject: String, text: String, files: List<String>, key: String): String {
        val body = requestBody().put("body", text.trim()).put("attachments", JSONArray(files))
        if (id != null) {
            call("POST", "/tickets/$id/messages", body, key)
            return id
        }
        return call("POST", "/tickets", body.put("kind", kind.name).put("subject", subject.trim()), key).getString("id")
    }

    fun fileName(uri: Uri): String {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }.orEmpty()
        return name.replace(Regex("[\\p{C}/\\\\]"), "_").trim().take(120).ifBlank { "attachment" }
    }

    suspend fun upload(uri: Uri, uploadKey: String, submitKey: String): String = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) throw SupportFileException()
        val mime = resolver.getType(uri)
        if (mime !in listOf("image/jpeg", "image/png", "application/pdf")) throw SupportFileException()
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() + count > 8 * 1024 * 1024) throw SupportFileException()
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: throw SupportFileException()
        if (bytes.isEmpty()) throw SupportFileException()
        val staged = call("POST", "/attachments", requestBody().put("name", fileName(uri)).put("mime", mime)
            .put("bytes", bytes.size), uploadKey)
        val id = staged.getString("id")
        val connection = (signedUrl(staged.getString("uploadUrl")).openConnection() as HttpsURLConnection).apply {
            requestMethod = "PUT"
            instanceFollowRedirects = false
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", mime)
            setFixedLengthStreamingMode(bytes.size)
        }
        try {
            connection.outputStream.use { it.write(bytes) }
            if (connection.responseCode !in 200..299) throw SupportFileException()
        } finally { connection.disconnect() }
        call("POST", "/attachments/$id/submit", requestBody(), submitKey)
        id
    }

    suspend fun download(id: String): Uri = Uri.parse(
        signedUrl(call("GET", "/attachments/$id" + query(null)).getString("downloadUrl")).toExternalForm()
    )

    private fun signedUrl(raw: String): URL = URL(raw).also {
        require(it.protocol == "https" && it.host.isNotBlank() && it.userInfo == null)
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map(::getJSONObject)
    private fun cursor(data: JSONObject) = data.optJSONObject("next")?.let { SupportCursor(it.getString("before"), it.getString("beforeId")) }
    private fun ticket(data: JSONObject) = RemoteSupportTicket(data.getString("id"), data.getString("subject"),
        data.getString("status"), SupportKind.valueOf(data.getString("kind")))
}
