package com.ridenow.app.ui.support

import com.ridenow.app.R
import androidx.compose.ui.res.stringResource

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
    val textContactSupport = stringResource(R.string.text_contact_support)
    val textChooseHowYouWouldLikeToReachUs = stringResource(R.string.text_choose_how_you_would_like_to_reach_us)
    val textChatWithSupport = stringResource(R.string.text_chat_with_support)
    val textSendAMessageFromTheApp = stringResource(R.string.text_send_a_message_from_the_app)
    val textCallSupport = stringResource(R.string.text_call_support)
    val textSpeakWithSomeone = stringResource(R.string.text_speak_with_someone)
    val textEmailUs = stringResource(R.string.text_email_us)
    val textAboutARecentTrip = stringResource(R.string.text_about_a_recent_trip)
    val textHelixGardenPlaza = stringResource(R.string.text_helix_garden_plaza)
    val textToday1680 = stringResource(R.string.text_today_16_80)
    val textReportAnIssue = stringResource(R.string.text_report_an_issue)

    val c = RideTheme.colors
    val context = LocalContext.current
    fun launch(i: Intent) { runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        ScreenTop(textContactSupport, onBack, subtitle = textChooseHowYouWouldLikeToReachUs)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).entrance(60)) {
            GroupCard(Modifier.padding(top = 16.dp)) {
                NavRow(RideIcons.Message, textChatWithSupport, onChat, subtitle = textSendAMessageFromTheApp); RowDivider()
                NavRow(RideIcons.Call, textCallSupport, { launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$SupportPhone"))) }, subtitle = textSpeakWithSomeone); RowDivider()
                NavRow(RideIcons.Mail, textEmailUs, { launch(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$SupportEmail"))) }, subtitle = SupportEmail)
            }
            SectionLabel(textAboutARecentTrip)
            GroupCard { NavRow(RideIcons.Car, textHelixGardenPlaza, onReport, subtitle = textToday1680) }
        }
        RideButton(textReportAnIssue, onReport, style = RideButtonStyle.Text, modifier = Modifier.padding(vertical = 12.dp).border(1.dp, c.border, RoundedCornerShape(28.dp)).height(52.dp))
    }
}

private class Msg(val mine: Boolean, val text: String)


/** Demo support chat: the agent replies with canned answers after a short delay. */
@Composable
fun SupportChatScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val Replies = listOf(
        stringResource(R.string.extra_thanks_for_telling_us_can_you_confirm_which_trip_this_was),
        stringResource(R.string.extra_i_am_sorry_about_that_i_have_noted_it_on_your_trip_and_a_specialist_wi),
        stringResource(R.string.extra_is_there_anything_else_we_can_help_with),
    )
    val textHiAlexHowCanWeHelpWithYourTripToday = stringResource(R.string.text_hi_alex_how_can_we_help_with_your_trip_today)
    val textSearchBack = stringResource(R.string.search_back)
    val textRidenowSupport = stringResource(R.string.text_ridenow_support)
    val textOnline = stringResource(R.string.text_online)
    val textMyDriverTookADifferentRoute = stringResource(R.string.text_my_driver_took_a_different_route)
    val textIWasChargedMore = stringResource(R.string.text_i_was_charged_more)
    val textILostAnItem = stringResource(R.string.text_i_lost_an_item)
    val textTypeAMessage = stringResource(R.string.text_type_a_message)
    val textSend = stringResource(R.string.text_send)

    val c = RideTheme.colors
    val messages = remember { mutableStateListOf(Msg(false, textHiAlexHowCanWeHelpWithYourTripToday)) }
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
            Box(Modifier.offset(x = (-12).dp).size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = textSearchBack }, contentAlignment = Alignment.Center) { RideIcon(RideIcons.Back, c.fg, size = 22.dp) }
            Box(Modifier.size(40.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.14f)).border(1.dp, c.accent, CircleShape), contentAlignment = Alignment.Center) { RideIcon(RideIcons.Message, c.accent) }
            Spacer(Modifier.width(12.dp))
            Column { Text(textRidenowSupport, color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold); Text(textOnline, color = c.accent, fontSize = 12.sp) }
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
            listOf(textMyDriverTookADifferentRoute, textIWasChargedMore, textILostAnItem).forEach { q ->
                Text(q, color = c.fg, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(19.dp)).border(1.dp, c.border, RoundedCornerShape(19.dp)).clickable(role = Role.Button) { send(q) }.padding(horizontal = 14.dp, vertical = 10.dp))
            }
        }
        Row(Modifier.padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                draft, { draft = it.take(500) }, singleLine = true, textStyle = TextStyle(color = c.fg, fontSize = 15.sp), cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { send(draft) }),
                modifier = Modifier.weight(1f).height(52.dp),
                decorationBox = { inner -> Box(Modifier.fillMaxSize().clip(RoundedCornerShape(26.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(26.dp)).padding(horizontal = 18.dp), contentAlignment = Alignment.CenterStart) { if (draft.isEmpty()) Text(textTypeAMessage, color = c.muted, fontSize = 15.sp); inner() } },
            )
            Spacer(Modifier.width(10.dp))
            Box(Modifier.size(52.dp).clip(CircleShape).background(BrandYellow).clickable(role = Role.Button) { send(draft) }.semantics { contentDescription = textSend }, contentAlignment = Alignment.Center) {
                RideIcon(listOf("M3 10l14-6-5 14-2-6z"), Ink, size = 20.dp)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ChatPreview() = RideNowTheme(darkTheme = true) { SupportChatScreen({}) }
