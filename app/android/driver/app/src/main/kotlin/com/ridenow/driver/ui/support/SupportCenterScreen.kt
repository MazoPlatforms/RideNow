package com.ridenow.driver.ui.support

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.ridenow.driver.R
import com.ridenow.driver.ui.components.*
import com.ridenow.driver.ui.home.MainTab
import com.ridenow.driver.ui.theme.RideTheme

/** One question that opens to show its answer. */
@Composable
fun FaqItem(question: String, answer: String) {
    val c = RideTheme.colors
    var open by rememberSaveable(question) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { open = !open }.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(question, color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            RideIcon(if (open) listOf("M5 12.5l5-5 5 5") else RideIcons.ChevronDown, c.muted, size = 18.dp)
        }
        if (open) Text(answer, color = c.muted, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

/** Support tab: search, six topic cards, and ways to reach support. */
@Composable
fun SupportCenterScreen(
    unread: Int, initials: String, onTab: (MainTab) -> Unit, onBell: () -> Unit, onProfile: () -> Unit,
    onTopic: (Int) -> Unit, onContact: () -> Unit, onTickets: () -> Unit, onFaqs: () -> Unit, modifier: Modifier = Modifier,
) {
    val c = RideTheme.colors
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim().lowercase()
    TabScaffold(MainTab.Support, onTab, unread, initials, onBell, onProfile, modifier) {
        PageTitle(str(R.string.sc_title), subtitle = str(R.string.sc_sub))
        SearchField(query, { query = it }, str(R.string.sc_search), Modifier.padding(top = 14.dp))
        if (q.isNotEmpty()) {
            val hits = HelpContent.topics.flatMap { t -> t.faqs }.filter { (qs, a) -> str(qs).lowercase().contains(q) || str(a).lowercase().contains(q) }
            Spacer(Modifier.height(14.dp))
            if (hits.isEmpty()) EmptyState(RideIcons.Search, str(R.string.sc_no_results), str(R.string.sc_no_results_text))
            else GroupCard { hits.forEachIndexed { i, (qs, a) -> if (i > 0) RowDivider(); FaqItem(str(qs), str(a)) } }
            return@TabScaffold
        }
        Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            HelpContent.topics.chunked(2).forEachIndexed { row, pair ->
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEachIndexed { col, t ->
                        Column(
                            Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(18.dp)).clickable(role = Role.Button) { onTopic(row * 2 + col) }.padding(14.dp),
                        ) {
                            Row(verticalAlignment = Alignment.Top) { RideIcon(t.icon, c.accent, size = 26.dp); Spacer(Modifier.weight(1f)); RideIcon(RideIcons.Chevron, c.fg, size = 16.dp) }
                            Text(str(t.title), color = c.fg, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
                            Text(str(t.sub), color = c.muted, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                    }
                }
            }
        }
        SectionTitle(str(R.string.sc_more_help))
        GroupCard(Modifier.padding(bottom = 16.dp)) {
            NavRow(RideIcons.Msg, str(R.string.sc_contact), onContact, subtitle = str(R.string.sc_contact_sub)); RowDivider()
            NavRow(RideIcons.Doc, str(R.string.sc_tickets), onTickets, subtitle = str(R.string.sc_tickets_sub)); RowDivider()
            NavRow(RideIcons.Help, str(R.string.sc_faqs), onFaqs, subtitle = str(R.string.sc_faqs_sub))
        }
    }
}

/** Questions for one topic, or for every topic when [topic] is -1. */
@Composable
fun FaqScreen(topic: Int, onBack: () -> Unit, onContact: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val topics = if (topic in HelpContent.topics.indices) listOf(HelpContent.topics[topic]) else HelpContent.topics
    SubScreen(
        if (topic in HelpContent.topics.indices) str(HelpContent.topics[topic].title) else str(R.string.sc_faqs), onBack, modifier,
        bottom = { RideButton(str(R.string.sc_still_help), onContact) },
    ) {
        topics.forEach { t ->
            if (topics.size > 1) Text(str(t.title), color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
            else Spacer(Modifier.height(8.dp))
            GroupCard { t.faqs.forEachIndexed { i, (qs, a) -> if (i > 0) RowDivider(); FaqItem(str(qs), str(a)) } }
        }
        Spacer(Modifier.height(16.dp))
    }
}
