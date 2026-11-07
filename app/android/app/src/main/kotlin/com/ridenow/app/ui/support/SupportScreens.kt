package com.ridenow.app.ui.support

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import com.ridenow.app.ui.components.GroupCard
import com.ridenow.app.ui.components.NavRow
import com.ridenow.app.ui.components.RideButton
import com.ridenow.app.ui.components.RideButtonStyle
import com.ridenow.app.ui.components.RideIcon
import com.ridenow.app.ui.components.RideIcons
import com.ridenow.app.ui.components.RowDivider
import com.ridenow.app.ui.components.ScreenTop
import com.ridenow.app.ui.components.SectionLabel
import com.ridenow.app.ui.theme.BrandYellow
import com.ridenow.app.ui.theme.Ink
import com.ridenow.app.ui.theme.RideNowTheme
import com.ridenow.app.ui.theme.RideTheme
import com.ridenow.app.ui.util.entrance
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SupportPhone = "+15550100"      // placeholder number
private const val SupportEmail = "support@ridenow.app"

/** Ways to reach support. Call and email open the phone's own apps. */
@Composable
fun ContactSupportScreen(onBack: () -> Unit, onChat: () -> Unit, onReport: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val context = LocalContext.current
    fun launch(i: Intent) { runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        ScreenTop("Contact support", onBack, subtitle = "Choose how you would like to reach us.")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).entrance(60)) {
            GroupCard(Modifier.padding(top = 16.dp)) {
                NavRow(RideIcons.Message, "Chat with support", onChat, subtitle = "Send a message from the app"); RowDivider()
                NavRow(RideIcons.Call, "Call support", { launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$SupportPhone"))) }, subtitle = "Speak with someone"); RowDivider()
                NavRow(RideIcons.Mail, "Email us", { launch(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$SupportEmail"))) }, subtitle = SupportEmail)
            }
            SectionLabel("ABOUT A RECENT TRIP")
            GroupCard { NavRow(RideIcons.Car, "Helix Garden Plaza", onReport, subtitle = "Today · $16.80") }
        }
        RideButton("Report an issue", onReport, style = RideButtonStyle.Text, modifier = Modifier.padding(vertical = 12.dp).border(1.dp, c.border, RoundedCornerShape(28.dp)).height(52.dp))
    }
}

private class Msg(val mine: Boolean, val text: String)
private val Replies = listOf(
    "Thanks for telling us. Can you confirm which trip this was?",
    "I am sorry about that. I have noted it on your trip and a specialist will follow up here.",
    "Is there anything else we can help with?",
)

/** Demo support chat: the agent replies with canned answers after a short delay. */
@Composable
fun SupportChatScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val messages = remember { mutableStateListOf(Msg(false, "Hi Alex, how can we help with your trip today?")) }
    var draft by remember { mutableStateOf("") }
    var replies by remember { mutableIntStateOf(0) }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(messages.size) { list.animateScrollToItem(messages.lastIndex) }
    fun send(t: String) {
        val text = t.trim(); if (text.isEmpty()) return
        messages += Msg(true, text); draft = ""
        scope.launch { delay(1200); messages += Msg(false, Replies[replies % Replies.size]); replies++ }
    }
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.offset(x = (-12).dp).size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = "Back" }, contentAlignment = Alignment.Center) { RideIcon(RideIcons.Back, c.fg, size = 22.dp) }
            Box(Modifier.size(40.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.14f)).border(1.dp, c.accent, CircleShape), contentAlignment = Alignment.Center) { RideIcon(RideIcons.Message, c.accent) }
            Spacer(Modifier.width(12.dp))
            Column { Text("RideNow Support", color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold); Text("Online", color = c.accent, fontSize = 12.sp) }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
            items(messages) { m ->
                Box(Modifier.fillMaxWidth(), contentAlignment = if (m.mine) Alignment.CenterEnd else Alignment.CenterStart) {
                    Text(
                        m.text, color = if (m.mine) Ink else c.fg, fontSize = 14.sp, lineHeight = 20.sp,
                        modifier = Modifier.widthIn(max = 270.dp).clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = if (m.mine) 18.dp else 4.dp, bottomEnd = if (m.mine) 4.dp else 18.dp))
                            .background(if (m.mine) BrandYellow else c.field).padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }
        if (messages.size == 1) Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("My driver took a different route", "I was charged more", "I lost an item").forEach { q ->
                Text(q, color = c.fg, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(19.dp)).border(1.dp, c.border, RoundedCornerShape(19.dp)).clickable(role = Role.Button) { send(q) }.padding(horizontal = 14.dp, vertical = 10.dp))
            }
        }
        Row(Modifier.padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                draft, { draft = it.take(500) }, singleLine = true, textStyle = TextStyle(color = c.fg, fontSize = 15.sp), cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { send(draft) }),
                modifier = Modifier.weight(1f).height(52.dp),
                decorationBox = { inner -> Box(Modifier.fillMaxSize().clip(RoundedCornerShape(26.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(26.dp)).padding(horizontal = 18.dp), contentAlignment = Alignment.CenterStart) { if (draft.isEmpty()) Text("Type a message", color = c.muted, fontSize = 15.sp); inner() } },
            )
            Spacer(Modifier.width(10.dp))
            Box(Modifier.size(52.dp).clip(CircleShape).background(BrandYellow).clickable(role = Role.Button) { send(draft) }.semantics { contentDescription = "Send" }, contentAlignment = Alignment.Center) {
                RideIcon(listOf("M3 10l14-6-5 14-2-6z"), Ink, size = 20.dp)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ChatPreview() = RideNowTheme(darkTheme = true) { SupportChatScreen({}) }
