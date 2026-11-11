package com.ridenow.app.ui.help

import com.ridenow.app.ui.components.localizedLabel

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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.tooling.preview.Preview
import com.ridenow.app.ui.components.GroupCard
import com.ridenow.app.ui.components.NavRow
import com.ridenow.app.ui.components.RideIcon
import com.ridenow.app.ui.components.RideIcons
import com.ridenow.app.ui.components.RowDivider
import com.ridenow.app.ui.components.ScreenTop
import com.ridenow.app.ui.components.SectionLabel
import com.ridenow.app.ui.theme.RideNowTheme
import com.ridenow.app.ui.theme.RideTheme
import com.ridenow.app.ui.util.entrance

private class Topic(val icon: List<String>, val title: String, val sub: String)





/** Help & Support: search, quick help cards and popular topics. [onOpen] receives the tapped title. */
@Composable
fun HelpScreen(onBack: () -> Unit, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val textMenuHelp = stringResource(R.string.menu_help)
    val textSearchForHelp = stringResource(R.string.text_search_for_help)
    val textQuickHelp = stringResource(R.string.text_quick_help)
    val textPopularTopics = stringResource(R.string.text_popular_topics)
    val textResults = stringResource(R.string.text_results)
    val textNoHelpArticlesFoundForValueTryDifferentWordsOrContactSupport = stringResource(R.string.text_no_help_articles_found_for_value_try_different_words_or_contact_support)

    val Quick = listOf(
        Topic(RideIcons.Book, "Help Center", stringResource(R.string.label_guides_and_faqs)),
        Topic(RideIcons.Message, "Contact support", stringResource(R.string.label_chat_or_call_us)),
        Topic(RideIcons.Flag, "Report an issue", stringResource(R.string.label_tell_us_what_happened)),
        Topic(RideIcons.Doc, "Terms & Policies", stringResource(R.string.label_legal_and_privacy)),
    )
    val Topics = listOf(
        Topic(RideIcons.Person, "Account & profile", stringResource(R.string.label_login_phone_number_and_settings)),
        Topic(RideIcons.Card, "Payments & wallet", stringResource(R.string.label_fares_receipts_and_payment_methods)),
        Topic(RideIcons.Car, "Ride bookings", stringResource(R.string.label_book_schedule_and_cancel_rides)),
        Topic(RideIcons.Shield, "Safety & security", stringResource(R.string.label_trusted_contacts_and_emergency_help)),
    )
    val Articles = listOf("How to book a ride", "How to schedule a ride", "How to cancel a ride", "Change your drop-off", "Book for a friend", "Report a lost item")

    val c = RideTheme.colors
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim()
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 20.dp)) {
        ScreenTop(textMenuHelp, onBack)
        BasicTextField(
            query, { query = it }, singleLine = true, textStyle = TextStyle(color = c.fg, fontSize = 16.sp), cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), modifier = Modifier.padding(top = 14.dp).fillMaxWidth().height(56.dp),
            decorationBox = { inner ->
                Row(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RideIcon(RideIcons.Search, c.accent); Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f)) { if (query.isEmpty()) Text(textSearchForHelp, color = c.muted, fontSize = 16.sp); inner() }
                }
            },
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).entrance(60)) {
            if (q.isEmpty()) {
                SectionLabel(textQuickHelp)
                Quick.chunked(2).forEach { pair ->
                    Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { t ->
                            Column(
                                Modifier.weight(1f).height(96.dp).clip(RoundedCornerShape(20.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(20.dp)).clickable(role = Role.Button) { onOpen(t.title) }.padding(14.dp),
                                verticalArrangement = Arrangement.SpaceBetween,
                            ) { RideIcon(t.icon, c.accent, size = 24.dp); Column { Text(localizedLabel(t.title), color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold); Text(t.sub, color = c.muted, fontSize = 12.sp) } }
                        }
                    }
                }
                SectionLabel(textPopularTopics)
                GroupCard { Topics.forEachIndexed { i, t -> if (i > 0) RowDivider(); NavRow(t.icon, localizedLabel(t.title), { onOpen("Help topic:${t.title}") }, subtitle = t.sub) } }
            } else {
                val hits = (Topics.map { it.title to "Help topic:${it.title}" } + Articles.map { it to "Help article:$it" }).filter { localizedLabel(it.first).contains(q, ignoreCase = true) }
                SectionLabel(textResults)
                if (hits.isEmpty()) Text(textNoHelpArticlesFoundForValueTryDifferentWordsOrContactSupport.format(q), color = c.muted, fontSize = 14.sp, lineHeight = 21.sp)
                else GroupCard { hits.forEachIndexed { i, h -> if (i > 0) RowDivider(); NavRow(RideIcons.Doc, localizedLabel(h.first), { onOpen(h.second) }) } }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun HelpPreview() = RideNowTheme(darkTheme = true) { HelpScreen({}, {}) }
