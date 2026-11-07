package com.ridenow.app.ui.help

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

private val Quick = listOf(
    Topic(RideIcons.Book, "Help Center", "Guides and FAQs"),
    Topic(RideIcons.Message, "Contact support", "Chat or call us"),
    Topic(RideIcons.Flag, "Report an issue", "Tell us what happened"),
    Topic(RideIcons.Doc, "Terms & Policies", "Legal and privacy"),
)
private val Topics = listOf(
    Topic(RideIcons.Person, "Account & profile", "Login, phone number and settings"),
    Topic(RideIcons.Card, "Payments & wallet", "Fares, receipts and payment methods"),
    Topic(RideIcons.Car, "Ride bookings", "Book, schedule and cancel rides"),
    Topic(RideIcons.Shield, "Safety & security", "Trusted contacts and emergency help"),
)
private val Articles = listOf("How to book a ride", "How to schedule a ride", "How to cancel a ride", "Change your drop-off", "Book for a friend", "Find a lost item")

/** Help & Support: search, quick help cards and popular topics. [onOpen] receives the tapped title. */
@Composable
fun HelpScreen(onBack: () -> Unit, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim()
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 20.dp)) {
        ScreenTop("Help & Support", onBack)
        BasicTextField(
            query, { query = it }, singleLine = true, textStyle = TextStyle(color = c.fg, fontSize = 16.sp), cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), modifier = Modifier.padding(top = 14.dp).fillMaxWidth().height(56.dp),
            decorationBox = { inner ->
                Row(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RideIcon(RideIcons.Search, c.accent); Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f)) { if (query.isEmpty()) Text("Search for help", color = c.muted, fontSize = 16.sp); inner() }
                }
            },
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).entrance(60)) {
            if (q.isEmpty()) {
                SectionLabel("QUICK HELP")
                Quick.chunked(2).forEach { pair ->
                    Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { t ->
                            Column(
                                Modifier.weight(1f).height(96.dp).clip(RoundedCornerShape(20.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(20.dp)).clickable(role = Role.Button) { onOpen(t.title) }.padding(14.dp),
                                verticalArrangement = Arrangement.SpaceBetween,
                            ) { RideIcon(t.icon, c.accent, size = 24.dp); Column { Text(t.title, color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold); Text(t.sub, color = c.muted, fontSize = 12.sp) } }
                        }
                    }
                }
                SectionLabel("POPULAR TOPICS")
                GroupCard { Topics.forEachIndexed { i, t -> if (i > 0) RowDivider(); NavRow(t.icon, t.title, { onOpen(t.title) }, subtitle = t.sub) } }
            } else {
                val hits = (Topics.map { it.title } + Articles).filter { it.contains(q, ignoreCase = true) }
                SectionLabel("RESULTS")
                if (hits.isEmpty()) Text("No help articles found for “$q”. Try different words or contact support.", color = c.muted, fontSize = 14.sp, lineHeight = 21.sp)
                else GroupCard { hits.forEachIndexed { i, t -> if (i > 0) RowDivider(); NavRow(RideIcons.Doc, t, { onOpen(t) }) } }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun HelpPreview() = RideNowTheme(darkTheme = true) { HelpScreen({}, {}) }
