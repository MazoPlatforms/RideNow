package com.ridenow.driver.ui.support

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.ridenow.driver.R
import com.ridenow.driver.ui.components.*
import com.ridenow.driver.ui.data.*
import com.ridenow.driver.ui.home.MainTab
import com.ridenow.driver.ui.theme.RideTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun ticketIcon(key: String): List<String> = when (key) {
    "wallet" -> RideIcons.Wallet; "car" -> RideIcons.Car; "alert" -> RideIcons.Alert; "doc" -> RideIcons.Doc; "map" -> RideIcons.Book
    "shield" -> RideIcons.Shield; "person" -> RideIcons.Person; "gear" -> RideIcons.Gear; else -> RideIcons.Msg
}

@Composable
fun statusLabel(s: TicketStatus) = str(when (s) { TicketStatus.Open -> R.string.tk_open; TicketStatus.InProgress -> R.string.tk_progress; TicketStatus.Resolved -> R.string.tk_resolved })
private fun statusTone(s: TicketStatus) = when (s) { TicketStatus.Open -> Tone.Good; TicketStatus.InProgress -> Tone.Info; TicketStatus.Resolved -> Tone.Neutral }
private fun nowMinute() = java.util.Calendar.getInstance().let { it.get(java.util.Calendar.HOUR_OF_DAY) * 60 + it.get(java.util.Calendar.MINUTE) }

/** Support tickets: search, status pills, the list, and a Create New Ticket button. */
@Composable
fun TicketsScreen(
    store: DriverStore, unread: Int, initials: String, onTab: (MainTab) -> Unit, onBell: () -> Unit, onProfile: () -> Unit,
    onOpen: (Ticket) -> Unit, onCreate: () -> Unit, modifier: Modifier = Modifier,
) {
    val c = RideTheme.colors
    var filter by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim().lowercase()
    val list = store.tickets.filter {
        (filter == 0 || (filter == 1 && it.status == TicketStatus.Open) || (filter == 2 && it.status == TicketStatus.InProgress) || (filter == 3 && it.status == TicketStatus.Resolved)) &&
            (q.isEmpty() || it.title.lowercase().contains(q) || it.id.lowercase().contains(q))
    }
    TabScaffold(MainTab.Support, onTab, unread, initials, onBell, onProfile, modifier, bottom = { RideButton(str(R.string.tk_create), onCreate) }) {
        PageTitle(str(R.string.tk_support), subtitle = str(R.string.tk_support_sub))
        SearchField(query, { query = it }, str(R.string.tk_search), Modifier.padding(top = 14.dp))
        FilterPills(listOf(str(R.string.filter_all), str(R.string.tk_open), str(R.string.tk_progress), str(R.string.tk_resolved)), filter, { filter = it }, Modifier.padding(top = 12.dp, bottom = 12.dp))
        if (list.isEmpty()) EmptyState(RideIcons.Doc, str(R.string.tk_empty_title), str(R.string.tk_empty_text))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
            list.forEach { t ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(16.dp)).clickable(role = Role.Button) { onOpen(t) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RideIcon(ticketIcon(t.icon), c.fg, size = 24.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(t.title, color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("${t.id} \u2022 ${t.dateLabel}", color = c.muted, fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(t.time, color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                        StatusChip(statusLabel(t.status), statusTone(t.status))
                    }
                    RideIcon(RideIcons.Chevron, c.muted, size = 16.dp, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

private fun nameAndSize(context: android.content.Context, uri: Uri): Pair<String, String> {
    var name = "photo.jpg"; var size = ""
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cur ->
            if (cur.moveToFirst()) {
                val n = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME); val s = cur.getColumnIndex(OpenableColumns.SIZE)
                if (n >= 0) name = cur.getString(n); if (s >= 0) size = (cur.getLong(s) / 1024).toString() + " KB"
            }
        }
    }
    return name to size
}

/** A ticket conversation: ticket summary, bubbles with attachments, and a message box with an attach button. */
@Composable
fun TicketConversationScreen(ticket: Ticket, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    var text by rememberSaveable { mutableStateOf("") }
    var pending by remember { mutableStateOf<Pair<String, String>?>(null) }
    val reply = str(R.string.tk_auto_reply)
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) pending = nameAndSize(context, uri) }
    LaunchedEffect(ticket.messages.size) { scroll.animateScrollTo(scroll.maxValue) }

    fun send() {
        if (text.isBlank() && pending == null) return
        val att = pending
        ticket.messages.add(ChatMessage(false, text.trim(), Calendars.timeLabel(nowMinute()), att?.first, att?.second))
        text = ""; pending = null
        if (ticket.status == TicketStatus.Resolved) ticket.status = TicketStatus.Open
        scope.launch { delay(1600); ticket.messages.add(ChatMessage(true, reply, Calendars.timeLabel(nowMinute()))) }
    }

    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(36.dp)) { BackButton(onBack) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(ticket.title, color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(ticket.id, color = c.muted, fontSize = 12.sp)
            }
            Box(Modifier.width(36.dp))
        }
        Column(Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GroupCard {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(c.line), contentAlignment = Alignment.Center) { RideIcon(ticketIcon(ticket.icon), c.fg, size = 24.dp) }
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(ticket.title, color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) { Text(ticket.id + "  ", color = c.muted, fontSize = 12.sp); StatusChip(statusLabel(ticket.status), statusTone(ticket.status), dot = true) }
                        Text(str(R.string.tk_created, ticket.createdLabel), color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
            ticket.messages.forEach { m -> Bubble(m) }
            Spacer(Modifier.height(4.dp))
        }
        pending?.let { (n, s) ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).clip(RoundedCornerShape(14.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(14.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                RideIcon(RideIcons.Image, c.fg, size = 20.dp)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) { Text(n, color = c.fg, fontSize = 13.sp, maxLines = 1); Text(s, color = c.muted, fontSize = 11.sp) }
                CircleButton(RideIcons.Close, str(R.string.att_remove), { pending = null }, size = 36.dp, filled = false)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleButton(RideIcons.Paperclip, str(R.string.tk_attach), { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, size = 48.dp, filled = false)
            Box(Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(24.dp)).padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.CenterStart) {
                if (text.isEmpty()) Text(str(R.string.tk_type), color = c.muted, fontSize = 14.sp)
                BasicTextField(text, { text = it }, textStyle = TextStyle(color = c.fg, fontSize = 14.sp), cursorBrush = SolidColor(c.accent), maxLines = 4, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(48.dp).clip(CircleShape).background(c.fill).clickable(role = Role.Button, onClick = ::send).semantics { contentDescription = "Send" }, contentAlignment = Alignment.Center) { RideIcon(RideIcons.Send, c.onFill, size = 22.dp) }
        }
    }
}

@Composable
private fun Bubble(m: ChatMessage) {
    val c = RideTheme.colors
    val mine = !m.fromSupport
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        if (m.fromSupport) Text(str(R.string.tk_support_name) + "  " + m.time, color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(start = 46.dp, bottom = 3.dp))
        else Text(m.time, color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 3.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            if (m.fromSupport) { Box(Modifier.size(34.dp).clip(CircleShape).background(c.fill.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) { LogoMark(24.dp) }; Spacer(Modifier.width(8.dp)) }
            Column(
                Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(18.dp)).background(if (mine) c.fill else c.field).then(if (mine) Modifier else Modifier.border(1.dp, c.border, RoundedCornerShape(18.dp))).padding(12.dp),
            ) {
                if (m.text.isNotEmpty()) Text(m.text, color = if (mine) c.onFill else c.fg, fontSize = 14.sp, lineHeight = 20.sp)
                if (m.attachment != null) Row(Modifier.padding(top = if (m.text.isNotEmpty()) 8.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
                    RideIcon(RideIcons.Image, if (mine) c.onFill else c.fg, size = 18.dp)
                    Column(Modifier.padding(start = 8.dp)) { Text(m.attachment, color = if (mine) c.onFill else c.fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold); if (m.attachmentSize != null) Text(m.attachmentSize, color = if (mine) c.onFill.copy(alpha = 0.7f) else c.muted, fontSize = 11.sp) }
                }
            }
        }
    }
}

/** Create a new support ticket: category, subject and what happened. Opens the conversation when sent. */
@Composable
fun NewTicketScreen(store: DriverStore, onBack: () -> Unit, onCreated: (Ticket) -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val cats = listOf(
        Triple("wallet", R.string.cat_payments, RideIcons.Wallet), Triple("car", R.string.cat_rides, RideIcons.Car), Triple("doc", R.string.cat_docs, RideIcons.Doc),
        Triple("alert", R.string.cat_app, RideIcons.Gear), Triple("person", R.string.cat_account, RideIcons.Person), Triple("map", R.string.cat_maps, RideIcons.Pin),
    )
    var cat by rememberSaveable { mutableStateOf(0) }
    var subject by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var tried by rememberSaveable { mutableStateOf(false) }
    SubScreen(str(R.string.tk_create), onBack, modifier, bottom = {
        RideButton(str(R.string.tk_send), { tried = true; if (subject.isNotBlank() && body.isNotBlank()) onCreated(store.addTicket(subject.trim(), cats[cat].first, body.trim())) })
    }) {
        Text(str(R.string.rep_category), color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
        cats.chunked(2).forEachIndexed { r, pair ->
            Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEachIndexed { i, t -> RideChip(str(t.second), cat == r * 2 + i, { cat = r * 2 + i }, Modifier.weight(1f)) }
            }
        }
        RideTextField(subject, { subject = it }, str(R.string.tk_subject), RideIcons.Doc, Modifier.padding(top = 8.dp), isError = tried && subject.isBlank())
        Text(str(R.string.rep_describe), color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
        Box(Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(16.dp)).background(c.field).border(1.dp, if (tried && body.isBlank()) c.danger else c.border, RoundedCornerShape(16.dp)).padding(14.dp)) {
            if (body.isEmpty()) Text(str(R.string.rep_hint), color = c.muted, fontSize = 14.sp)
            BasicTextField(body, { body = it.take(500) }, textStyle = TextStyle(color = c.fg, fontSize = 14.sp), cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxSize())
        }
    }
}
