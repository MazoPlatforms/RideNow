package com.ridenow.app.ui.support

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridenow.app.R
import com.ridenow.app.ui.auth.PhoneAuthException
import com.ridenow.app.ui.components.RideButton
import com.ridenow.app.ui.theme.RideTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal enum class SupportEntry { LIST, TICKET, CHAT, COMPLAINT, SAFETY }

private class DraftFile(val uri: Uri, val name: String) {
    var uploadKey = supportKey()
    var submitKey = supportKey()
    var id: String? = null
    fun reset() {
        uploadKey = supportKey()
        submitKey = supportKey()
        id = null
    }
}

@Composable
internal fun SupportWorkspaceScreen(
    entry: SupportEntry,
    onBack: () -> Unit,
    categories: List<Int> = emptyList(),
    initialCategory: Int = -1,
    chrome: (@Composable (Boolean) -> Unit)? = null,
    initialSubject: String = "",
    initialMessage: String = "",
    onSafety: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val api = remember(context) { SupportApi(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val colors = RideTheme.colors
    val panel = RoundedCornerShape(18.dp)
    val chatSubject = stringResource(R.string.support_live_chat)
    val kind = when (entry) {
        SupportEntry.CHAT -> SupportKind.CHAT
        SupportEntry.COMPLAINT -> SupportKind.COMPLAINT
        SupportEntry.SAFETY -> SupportKind.SAFETY
        else -> SupportKind.TICKET
    }
    var ticketId by rememberSaveable(entry) { mutableStateOf<String?>(null) }
    var composing by rememberSaveable(entry) { mutableStateOf(entry != SupportEntry.LIST) }
    var batch by remember { mutableStateOf<List<RemoteSupportTicket>>(emptyList()) }
    var listNext by remember { mutableStateOf<SupportCursor?>(null) }
    var thread by remember(ticketId) { mutableStateOf<SupportThread?>(null) }
    var older by remember(ticketId) { mutableStateOf<SupportCursor?>(null) }
    var selection by rememberSaveable(entry) { mutableIntStateOf(initialCategory) }
    var statusFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var text by rememberSaveable(entry, ticketId) { mutableStateOf(if (ticketId == null) initialMessage else "") }
    var subject by rememberSaveable(entry, ticketId) { mutableStateOf(if (ticketId == null) initialSubject else "") }
    var sendKey by rememberSaveable(entry, ticketId) { mutableStateOf(supportKey()) }
    val files = remember(ticketId) { mutableStateListOf<DraftFile>() }
    var loading by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var sendJob by remember { mutableStateOf<Job?>(null) }
    var error by remember { mutableStateOf<Int?>(null) }
    val scroll = rememberLazyListState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { chosen ->
        val additions = chosen.distinct().filter { uri -> files.none { it.uri == uri } }
        if (additions.size + files.size > 5) error = R.string.support_live_limit_files
        else {
            try {
                files.addAll(additions.map { DraftFile(it, api.fileName(it)) })
                if (additions.isNotEmpty()) sendKey = supportKey()
                error = null
            } catch (_: Exception) { error = R.string.support_live_bad_file }
        }
    }

    fun showError(failure: Exception) {
        error = when {
            failure is SupportFileException -> R.string.support_live_bad_file
            failure is PhoneAuthException && failure.status == 401 -> R.string.support_live_sign_in
            failure is PhoneAuthException && failure.code == "SUPPORT_TICKET_RESOLVED" -> R.string.support_live_resolved
            failure is PhoneAuthException && failure.code == "SUPPORT_ATTACHMENT_UNAVAILABLE" -> R.string.support_live_file_failed
            failure is PhoneAuthException && failure.status == 429 -> R.string.support_live_rate_limit
            else -> R.string.support_live_error
        }
    }

    fun guarded(action: suspend () -> Unit) {
        if (loading || sending) return
        loading = true
        error = null
        scope.launch {
            try { action() }
            catch (failure: CancellationException) { throw failure }
            catch (failure: Exception) { showError(failure) }
            finally { loading = false }
        }
    }

    suspend fun loadList(cursor: SupportCursor? = null) {
        val response = api.list(null, cursor, statusFilter)
        batch = ((if (cursor == null) emptyList() else batch) + response.tickets).distinctBy { it.id }
        listNext = response.next
    }

    fun updateThread(id: String, response: SupportThread, cursor: SupportCursor? = null) {
        if (ticketId != id) return
        val previous = thread
        val messages = (previous?.messages.orEmpty() + response.messages)
            .associateBy { it.id }.values.sortedBy { it.time }
        if (previous == null || cursor != null) older = response.next
        thread = response.copy(ticket = response.ticket, messages = messages)
    }

    fun leave() {
        if (entry == SupportEntry.LIST && (ticketId != null || composing)) {
            sendJob?.cancel()
            ticketId = null
            composing = false
            subject = ""
            text = ""
            files.clear()
            sendKey = supportKey()
            error = null
            return
        }
        onBack()
    }
    BackHandler { leave() }

    LaunchedEffect(ticketId, composing, statusFilter) {
        if (ticketId == null && !composing) {
            try { loadList() }
            catch (failure: CancellationException) { throw failure }
            catch (failure: Exception) { showError(failure) }
        }
    }
    LaunchedEffect(entry) {
        if (entry == SupportEntry.CHAT && ticketId == null) guarded {
            ticketId = api.list(SupportKind.CHAT, null, active = true).tickets.firstOrNull()?.id
        }
    }
    LaunchedEffect(ticketId) {
        val id = ticketId ?: return@LaunchedEffect
        while (true) {
            try {
                val firstLoad = thread == null
                val response = api.thread(id)
                updateThread(id, response)
                if (firstLoad && response.messages.isNotEmpty())
                    scroll.scrollToItem(response.messages.size + if (older != null) 1 else 0)
            }
            catch (failure: CancellationException) { throw failure }
            catch (failure: Exception) { if (ticketId == id && thread == null) showError(failure) }
            delay(5_000)
        }
    }

    val resolved = thread?.ticket?.status == "RESOLVED"
    val title = thread?.ticket?.subject ?: stringResource(
        if (ticketId != null || !composing) R.string.support_live_tickets else when (kind) {
            SupportKind.TICKET -> R.string.support_live_new
            SupportKind.CHAT -> R.string.support_live_chat
            SupportKind.COMPLAINT -> R.string.support_live_complaint
            SupportKind.SAFETY -> R.string.support_live_safety
        }
    )
    val showEditor = !resolved && (thread != null || (ticketId == null && composing))
    val create = ticketId == null && composing
    val showSubject = create && kind != SupportKind.CHAT && categories.isEmpty()
    val category = categories.getOrNull(selection)?.let { stringResource(it) }
    val showingList = ticketId == null && !composing
    val backLabel = stringResource(R.string.support_live_back)
    val statuses = listOf(null, "OPEN", "IN_PROGRESS", "RESOLVED")

    Column(Modifier.fillMaxSize().background(colors.bg).statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 20.dp)) {
        if (showingList) chrome?.invoke(true)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = ::leave, modifier = Modifier.semantics { contentDescription = backLabel }) {
                Text("‹", color = colors.fg, fontSize = 28.sp, modifier = Modifier.padding(end = 4.dp))
            }
            Text(title, Modifier.weight(1f), color = colors.fg, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { guarded {
                val id = ticketId
                if (id != null) updateThread(id, api.thread(id))
                else if (!composing) loadList()
                else if (entry == SupportEntry.CHAT) ticketId = api.list(kind, null, active = true).tickets.firstOrNull()?.id
            } }, enabled = !loading && !sending && (ticketId != null || !composing || entry == SupportEntry.CHAT)) {
                Text(stringResource(R.string.support_live_refresh), color = colors.accent)
            }
        }
        error?.let { Text(stringResource(it), color = colors.fg, modifier = Modifier.padding(vertical = 8.dp)) }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = scroll, verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 8.dp)) {
            if (!composing && ticketId == null) {
                item { SupportField(search, { search = it }, R.string.support_live_search, false, true) }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        statuses.forEach { status ->
                            val label = if (status == null) R.string.support_live_all else status.label()
                            if (status == statusFilter) FilterChip(true, {}, { Text(stringResource(label)) })
                            else TextButton(onClick = { statusFilter = status; batch = emptyList(); listNext = null }) {
                                Text(stringResource(label), color = colors.fg, fontSize = 12.sp)
                            }
                        }
                    }
                }
                if (batch.none { it.subject.contains(search.trim(), ignoreCase = true) }) item { Text(stringResource(if (loading) R.string.support_live_loading else R.string.support_live_empty), color = colors.muted) }
                items(batch.filter { it.subject.contains(search.trim(), ignoreCase = true) }, key = { it.id }) { ticket ->
                    Column(Modifier.fillMaxWidth().border(1.dp, colors.border, panel).clickable {
                        error = null
                        ticketId = ticket.id
                    }.padding(14.dp)) {
                        Text(ticket.subject, color = colors.fg, fontWeight = FontWeight.Medium)
                        Text(stringResource(ticket.kind.label()) + " · " + stringResource(ticket.status.label()), color = colors.muted, fontSize = 13.sp)
                    }
                }
                listNext?.let { cursor -> item { TextButton(onClick = { guarded { loadList(cursor) } }) {
                    Text(stringResource(R.string.support_live_more), color = colors.accent)
                } } }
            }
            if (ticketId != null) {
                val current = thread
                if (current == null) item { Text(stringResource(R.string.support_live_loading), color = colors.muted) }
                else {
                    item { Text(stringResource(current.ticket.status.label()), color = colors.muted) }
                    older?.let { cursor -> item { TextButton(onClick = { guarded {
                        val id = current.ticket.id
                        updateThread(id, api.thread(id, cursor), cursor)
                    } }) { Text(stringResource(R.string.support_live_older), color = colors.accent) } } }
                    items(current.messages, key = { it.id }) { message ->
                        Column(Modifier.fillMaxWidth().border(1.dp, colors.border, panel).padding(14.dp)) {
                            Text(stringResource(if (message.sender == "SUPPORT") R.string.support_live_agent else R.string.support_live_you),
                                color = colors.accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            if (message.body.isNotBlank()) Text(message.body, color = colors.fg, modifier = Modifier.padding(top = 4.dp))
                            message.files.forEach { file ->
                                if (file.status == "CLEAN") TextButton(onClick = { guarded {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, api.download(file.id)))
                                } }) { Text(file.name, color = colors.accent) }
                                else Text(file.name + " · " + stringResource(if (file.status == "PENDING") R.string.support_live_scanning else R.string.support_live_unavailable),
                                    color = colors.muted, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
            if (showEditor) {
                if (create && kind == SupportKind.SAFETY) item {
                    Text(stringResource(R.string.support_live_emergency_notice), color = colors.muted)
                    if (onSafety != null) TextButton(onClick = onSafety) {
                        Text(stringResource(R.string.support_live_safety_center), color = colors.accent)
                    }
                }
                if (create && categories.isNotEmpty()) item {
                    Text(stringResource(R.string.support_live_category), color = colors.muted)
                    categories.chunked(2).forEachIndexed { row, pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEachIndexed { column, label ->
                                val index = row * 2 + column
                                FilterChip(selection == index, { selection = index; sendKey = supportKey() },
                                    { Text(stringResource(label)) }, enabled = !sending, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                if (showSubject) item {
                    SupportField(subject, { if (it.length <= 140) { subject = it; sendKey = supportKey() } }, R.string.support_live_subject, sending, true)
                }
                item {
                    SupportField(text, { if (it.length <= 4000) { text = it; sendKey = supportKey() } }, R.string.support_live_message, sending, false)
                }
                items(files, key = { it.uri.toString() }) { file ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(file.name, Modifier.weight(1f), color = colors.fg)
                        TextButton(enabled = !sending, onClick = { files.remove(file); sendKey = supportKey() }) {
                            Text(stringResource(R.string.support_live_remove), color = colors.accent)
                        }
                    }
                }
                item { TextButton(enabled = !sending && files.size < 5, onClick = {
                    picker.launch(arrayOf("image/jpeg", "image/png", "application/pdf"))
                }) { Text(stringResource(R.string.support_live_attach), color = colors.accent) } }
            }
        }
        if (!composing && ticketId == null) RideButton(stringResource(R.string.support_live_new), { composing = true; error = null },
            Modifier.padding(vertical = 10.dp))
        if (showEditor) RideButton(stringResource(if (create) R.string.support_live_submit else R.string.support_live_send), {
            if (sending) return@RideButton
            sending = true
            error = null
            sendJob = scope.launch {
                try {
                    val ids = files.map { file ->
                        file.id ?: api.upload(file.uri, file.uploadKey, file.submitKey).also { file.id = it }
                    }
                    val id = api.send(ticketId, kind, if (kind == SupportKind.CHAT) chatSubject else category ?: subject, text, ids, sendKey)
                    text = ""
                    subject = ""
                    files.clear()
                    sendKey = supportKey()
                    if (ticketId != id) ticketId = id
                    else {
                        runCatching { api.thread(id) }.onSuccess { response ->
                            updateThread(id, response)
                            scroll.animateScrollToItem((thread?.messages?.size ?: 0) + if (older != null) 1 else 0)
                        }.onFailure { if (it is CancellationException) throw it }
                    }
                } catch (failure: CancellationException) { throw failure }
                catch (failure: Exception) {
                    if (failure is PhoneAuthException && failure.code == "SUPPORT_ATTACHMENT_UNAVAILABLE") {
                        files.forEach { it.reset() }
                    }
                    showError(failure)
                } finally { sending = false }
            }
        }, Modifier.padding(vertical = 10.dp), enabled = (!showSubject || subject.trim().length >= 2) &&
            (!create || categories.isEmpty() || category != null) &&
            (text.isNotBlank() || files.isNotEmpty()) && !loading, loading = sending)
        if (showingList) chrome?.invoke(false)
    }
}

@Composable
private fun SupportField(value: String, update: (String) -> Unit, label: Int, sending: Boolean, singleLine: Boolean) {
    val c = RideTheme.colors
    OutlinedTextField(value, update, modifier = Modifier.fillMaxWidth(), enabled = !sending, singleLine = singleLine,
        minLines = if (singleLine) 1 else 3, maxLines = if (singleLine) 1 else 6,
        label = { Text(stringResource(label)) }, shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = c.fg, unfocusedTextColor = c.fg, disabledTextColor = c.muted, cursorColor = c.accent,
            focusedLabelColor = c.accent, unfocusedLabelColor = c.muted, focusedBorderColor = c.accent, unfocusedBorderColor = c.border,
        ))
}

private fun SupportKind.label() = when (this) {
    SupportKind.TICKET -> R.string.support_live_ticket
    SupportKind.CHAT -> R.string.support_live_chat
    SupportKind.COMPLAINT -> R.string.support_live_complaint
    SupportKind.SAFETY -> R.string.support_live_safety
}

private fun String.label() = when (this) {
    "RESOLVED" -> R.string.support_live_resolved
    "IN_PROGRESS" -> R.string.support_live_progress
    else -> R.string.support_live_open
}
