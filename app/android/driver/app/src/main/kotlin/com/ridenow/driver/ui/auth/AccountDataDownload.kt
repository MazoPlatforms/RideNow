package com.ridenow.driver.ui.auth

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.ridenow.driver.R
import java.io.File
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal class AccountDataDownload(
    val busy: Boolean,
    val feedback: Int?,
    val saved: Boolean,
    val start: (List<String>) -> Unit,
)

@Composable
internal fun rememberAccountDataDownload(): AccountDataDownload {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var pending by rememberSaveable { mutableStateOf<ArrayList<String>?>(null) }
    var writing by remember { mutableStateOf(false) }
    var feedback by rememberSaveable { mutableStateOf<Int?>(null) }
    var saved by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val sections = pending
        pending = null
        if (uri != null && !sections.isNullOrEmpty()) {
            writing = true
            scope.launch {
                try {
                    saveAccountData(context, uri, sections)
                    saved = true
                    feedback = R.string.data_export_saved
                } catch (failure: Exception) {
                    withContext(NonCancellable + Dispatchers.IO) {
                        // The picker created this document for this export.
                        runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
                    }
                    if (failure is CancellationException) throw failure
                    feedback = when {
                        failure is PhoneAuthException && failure.status == 401 -> R.string.data_export_sign_in
                        failure is PhoneAuthException && failure.status == 429 -> R.string.data_export_rate_limit
                        else -> R.string.data_export_failed
                    }
                } finally {
                    writing = false
                }
            }
        }
    }
    return AccountDataDownload(writing || pending != null, feedback, saved) { sections ->
        if (sections.isNotEmpty() && !writing && pending == null) {
            pending = ArrayList(sections)
            feedback = null
            saved = false
            try {
                picker.launch("RideNow-driver-data.zip")
            } catch (_: Exception) {
                pending = null
                feedback = R.string.data_export_failed
            }
        }
    }
}

private suspend fun saveAccountData(context: Context, destination: Uri, sections: List<String>) =
    withContext(Dispatchers.IO) {
        val app = "DRIVER"
        val path = "/v1/me/data-export"
        val body = JSONObject().put("app", app).put("sections", JSONArray(sections))
        val report = PhoneAuthApi.authorized(context, "POST", path, body)
        val files = report.getJSONArray("files")
        val temporary = File.createTempFile("ridenow-export-", ".zip", context.cacheDir)
        try {
            ZipOutputStream(temporary.outputStream().buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("account-data.json"))
                zip.write(report.toString(2).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                for (index in 0 until files.length()) {
                    currentCoroutineContext().ensureActive()
                    val file = files.getJSONObject(index)
                    val id = file.getString("id")
                    val type = file.getString("type")
                    val archivePath = file.getString("archivePath")
                    require(id.matches(Regex("[0-9a-f-]{36}")))
                    require(type in setOf("avatar", "document", "support"))
                    require(archivePath.matches(Regex("(profile|identity-documents|vehicle-documents|support-attachments)/[0-9a-f-]{36}\\.(jpg|png|pdf)")))
                    val filePath = "$path/files/$id?app=$app&type=$type"
                    val link = PhoneAuthApi.authorized(context, "GET", filePath).getString("downloadUrl")
                    val url = URL(link)
                    require(url.protocol == "https" && url.host.isNotBlank() && url.userInfo == null)
                    val connection = (url.openConnection() as HttpsURLConnection).apply {
                        instanceFollowRedirects = false
                        connectTimeout = 15_000
                        readTimeout = 30_000
                    }
                    try {
                        check(connection.responseCode == 200)
                        zip.putNextEntry(ZipEntry(archivePath))
                        connection.inputStream.use { stream ->
                            val buffer = ByteArray(16 * 1024)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = stream.read(buffer)
                                if (count < 0) break
                                zip.write(buffer, 0, count)
                            }
                        }
                        zip.closeEntry()
                    } finally {
                        connection.disconnect()
                    }
                }
            }
            currentCoroutineContext().ensureActive()
            val output = context.contentResolver.openOutputStream(destination, "wt") ?: error("Cannot open export")
            output.use { sink ->
                temporary.inputStream().use { source ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = source.read(buffer)
                        if (count < 0) break
                        sink.write(buffer, 0, count)
                    }
                }
            }
        } finally {
            temporary.delete()
        }
    }
