package com.ridenow.app.ui.support

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.ridenow.app.R
import com.ridenow.app.ui.components.*
import com.ridenow.app.ui.more.Chip
import com.ridenow.app.ui.more.MoreScaffold
import com.ridenow.app.ui.theme.RideTheme
import com.ridenow.app.ui.util.entrance
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun ticketIcon(key: String): List<String> = when (key) {
    "wallet" -> RideIcons.Wallet; "car" -> RideIcons.Car; "alert" -> RideIcons.Alert; "doc" -> RideIcons.Doc; "map" -> RideIcons.Map
    "shield" -> RideIcons.Shield; "person" -> RideIcons.Person; "gear" -> RideIcons.Sliders; else -> RideIcons.Message
}

@Composable
private fun statusLabel(s: TicketStatus) = stringResource(when (s) { TicketStatus.Open -> R.string.tk_open; TicketStatus.InProgress -> R.string.tk_progress; TicketStatus.Resolved -> R.string.tk_resolved })

@Composable
private fun StatusChip(status: TicketStatus) {
    val c = RideTheme.colors
    val tone = when (status) { TicketStatus.Open -> Color(0xFF22B35A); TicketStatus.InProgress -> Color(0xFF3B8BEB); TicketStatus.Resolved -> c.muted }
    Row(Modifier.clip(RoundedCornerShape(50)).background(tone.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(tone))
        Text(statusLabel(status), color = tone, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun RoundIconButton(icon: List<String>, label: String, onClick: () -> Unit, filled: Boolean = false) {
    val c = RideTheme.colors
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(if (filled) c.accent else Color.Transparent).border(1.dp, if (filled) c.accent else c.border, CircleShape)
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { RideIcon(icon, if (filled) c.onAccent else c.fg, size = 22.dp) }
}

/** Support tickets: search, status pills, the list, and a Create New Ticket button. */
@Composable
fun TicketsScreen(onBack: () -> Unit, onOpen: (SupportTicket) -> Unit, onCreate: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim().lowercase()
    val list = TicketStore.tickets.filter {
        (filter == 0 || (filter == 1 && it.status == TicketStatus.Open) || (filter == 2 && it.status == TicketStatus.InProgress) || (filter == 3 && it.status == TicketStatus.Resolved)) &&
            (q.isEmpty() || it.title.lowercase().contains(q) || it.id.lowercase().contains(q))
    }
    MoreScaffold(stringResource(R.string.tk_support), onBack, modifier, subtitle = stringResource(R.string.tk_support_sub), bottom = { RideButton(stringResource(R.string.tk_create), onCreate, Modifier.fillMaxWidth()) }) {
        RideTextField(query, { query = it }, stringResource(R.string.tk_search), RideIcons.Search, Modifier.padding(top = 14.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(R.string.filter_all, R.string.tk_open, R.string.tk_progress, R.string.tk_resolved).forEachIndexed { i, r -> Chip(stringResource(r), filter == i, { filter = i }) }
        }
        if (list.isEmpty()) Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            RideIcon(RideIcons.Doc, c.muted, size = 40.dp)
            Text(stringResource(R.string.tk_empty_title), color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
            Text(stringResource(R.string.tk_empty_text), color = c.muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
        }
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
                        StatusChip(t.status)
                    }
                    RideIcon(listOf("M7 4l6 6-6 6"), c.muted, size = 16.dp, modifier = Modifier.padding(start = 6.dp))
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
fun TicketConversationScreen(ticket: SupportTicket, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    var text by rememberSaveable { mutableStateOf("") }
    var pending by remember { mutableStateOf<Pair<String, String>?>(null) }
    val reply = stringResource(R.string.tk_auto_reply)
    val sendLabel = stringResource(R.string.tk_send)
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) pending = nameAndSize(context, uri) }
    LaunchedEffect(ticket.messages.size) { scroll.animateScrollTo(scroll.maxValue) }

    fun send() {
        if (text.isBlank() && pending == null) return
        val att = pending
        ticket.messages.add(SupportMessage(false, text.trim(), TicketStore.timeLabel(TicketStore.nowMinute()), att?.first, att?.second))
        text = ""; pending = null
        if (ticket.status == TicketStatus.Resolved) ticket.status = TicketStatus.Open
        scope.launch { delay(1600); ticket.messages.add(SupportMessage(true, reply, TicketStore.timeLabel(TicketStore.nowMinute()))) }
    }

    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Column(Modifier.padding(horizontal = 20.dp)) { ScreenTop(ticket.title, onBack, subtitle = ticket.id) }
        Column(Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 20.dp).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GroupCard {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(c.line), contentAlignment = Alignment.Center) { RideIcon(ticketIcon(ticket.icon), c.fg, size = 24.dp) }
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(ticket.title, color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) { Text(ticket.id + "  ", color = c.muted, fontSize = 12.sp); StatusChip(ticket.status) }
                        Text(stringResource(R.string.tk_created, ticket.createdLabel), color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
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
                Box(Modifier.size(36.dp).clip(CircleShape).clickable(role = Role.Button) { pending = null }.semantics { contentDescription = "Remove" }, contentAlignment = Alignment.Center) { RideIcon(RideIcons.Close, c.fg, size = 18.dp) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(RideIcons.Paperclip, stringResource(R.string.tk_attach), { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(24.dp)).padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.CenterStart) {
                if (text.isEmpty()) Text(stringResource(R.string.tk_type), color = c.muted, fontSize = 14.sp)
                BasicTextField(text, { text = it }, textStyle = TextStyle(color = c.fg, fontSize = 14.sp), cursorBrush = SolidColor(c.accent), maxLines = 4, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.width(8.dp))
            RoundIconButton(RideIcons.Send, sendLabel, ::send, filled = true)
        }
    }
}

@Composable
private fun Bubble(m: SupportMessage) {
    val c = RideTheme.colors
    val mine = !m.fromSupport
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        if (m.fromSupport) Text(stringResource(R.string.tk_support_name) + "  " + m.time, color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(start = 46.dp, bottom = 3.dp))
        else Text(m.time, color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 3.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            if (m.fromSupport) { Box(Modifier.size(34.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) { LogoMark(24.dp) }; Spacer(Modifier.width(8.dp)) }
            Column(
                Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(18.dp)).background(if (mine) c.accent else c.field).then(if (mine) Modifier else Modifier.border(1.dp, c.border, RoundedCornerShape(18.dp))).padding(12.dp),
            ) {
                if (m.text.isNotEmpty()) Text(m.text, color = if (mine) c.onAccent else c.fg, fontSize = 14.sp, lineHeight = 20.sp)
                if (m.attachment != null) Row(Modifier.padding(top = if (m.text.isNotEmpty()) 8.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
                    RideIcon(RideIcons.Image, if (mine) c.onAccent else c.fg, size = 18.dp)
                    Column(Modifier.padding(start = 8.dp)) { Text(m.attachment, color = if (mine) c.onAccent else c.fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold); if (m.attachmentSize != null) Text(m.attachmentSize, color = if (mine) c.onAccent.copy(alpha = 0.7f) else c.muted, fontSize = 11.sp) }
                }
            }
        }
    }
}

/** Create a new support ticket: category, subject and what happened. Opens the conversation when sent. */
@Composable
fun NewTicketScreen(onBack: () -> Unit, onCreated: (SupportTicket) -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val cats = listOf("wallet" to R.string.cat_payments, "car" to R.string.cat_rides, "alert" to R.string.cat_app, "person" to R.string.cat_account, "map" to R.string.cat_maps, "shield" to R.string.cat_safety)
    var cat by rememberSaveable { mutableIntStateOf(0) }
    var subject by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var tried by rememberSaveable { mutableStateOf(false) }
    MoreScaffold(stringResource(R.string.tk_create), onBack, modifier, bottom = {
        RideButton(stringResource(R.string.tk_send), { tried = true; if (subject.isNotBlank() && body.isNotBlank()) onCreated(TicketStore.addTicket(subject.trim(), cats[cat].first, body.trim())) }, Modifier.fillMaxWidth())
    }) {
        Text(stringResource(R.string.tkt_category), color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
        cats.chunked(2).forEachIndexed { r, pair ->
            Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEachIndexed { i, t -> Chip(stringResource(t.second), cat == r * 2 + i, { cat = r * 2 + i }, Modifier.weight(1f)) }
            }
        }
        RideTextField(subject, { subject = it }, stringResource(R.string.tk_subject), RideIcons.Doc, Modifier.padding(top = 8.dp), isError = tried && subject.isBlank())
        Text(stringResource(R.string.tkt_describe), color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
        Box(Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(16.dp)).background(c.field).border(1.dp, if (tried && body.isBlank()) c.danger else c.border, RoundedCornerShape(16.dp)).padding(14.dp)) {
            if (body.isEmpty()) Text(stringResource(R.string.tkt_hint), color = c.muted, fontSize = 14.sp)
            BasicTextField(body, { body = it.take(500) }, textStyle = TextStyle(color = c.fg, fontSize = 14.sp), cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(16.dp))
    }
}
