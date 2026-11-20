package com.ridenow.driver.ui.support

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.ridenow.driver.R
import com.ridenow.driver.ui.components.*
import com.ridenow.driver.ui.data.DriverStore
import com.ridenow.driver.ui.theme.RideTheme
import com.ridenow.driver.ui.util.openDial
import com.ridenow.driver.ui.util.shareText

/** Help & Safety: search, popular topics, safety tools and a Contact Support card. */
@Composable
fun HelpSafetyScreen(
    onBack: () -> Unit, onTopic: (Int) -> Unit, onSafetyCenter: () -> Unit, onEmergency: () -> Unit, onShareTrip: () -> Unit, onReportSafety: () -> Unit,
    onContact: () -> Unit, modifier: Modifier = Modifier,
) {
    val c = RideTheme.colors
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim().lowercase()
    val popular = listOf(
        Triple(RideIcons.Car, R.string.hs_booking to R.string.hs_booking_sub, HelpContent.booking), Triple(RideIcons.Wallet, R.string.hs_payments to R.string.hs_payments_sub, HelpContent.payments),
        Triple(RideIcons.Person, R.string.hs_account to R.string.hs_account_sub, HelpContent.account), Triple(RideIcons.Pin, R.string.hs_maps to R.string.hs_maps_sub, HelpContent.maps),
        Triple(RideIcons.Gear, R.string.hs_app to R.string.hs_app_sub, HelpContent.appIssues),
    )
    SubScreen(str(R.string.hs_title), onBack, modifier) {
        SearchField(query, { query = it }, str(R.string.sc_search), Modifier.padding(top = 4.dp))
        if (q.isNotEmpty()) {
            val hits = HelpContent.topics.flatMap { it.faqs }.filter { (qs, a) -> str(qs).lowercase().contains(q) || str(a).lowercase().contains(q) }
            Spacer(Modifier.height(14.dp))
            if (hits.isEmpty()) EmptyState(RideIcons.Search, str(R.string.sc_no_results), str(R.string.sc_no_results_text))
            else GroupCard { hits.forEachIndexed { i, (qs, a) -> if (i > 0) RowDivider(); FaqItem(str(qs), str(a)) } }
            return@SubScreen
        }
        SectionTitle(str(R.string.hs_popular))
        GroupCard { popular.forEachIndexed { i, (icon, t, topic) -> if (i > 0) RowDivider(); TileRow(icon, Tone.Neutral, str(t.first), str(t.second), { onTopic(topic) }) } }
        SectionTitle(str(R.string.hs_tools))
        GroupCard {
            TileRow(RideIcons.Shield, Tone.Info, str(R.string.hs_center), str(R.string.hs_center_sub), onSafetyCenter, trailing = null); RowDivider()
            TileRow(RideIcons.Siren, Tone.Danger, str(R.string.hs_emergency), str(R.string.hs_emergency_sub), onEmergency); RowDivider()
            TileRow(RideIcons.Users, Tone.Good, str(R.string.hs_share), str(R.string.hs_share_sub), onShareTrip); RowDivider()
            TileRow(RideIcons.Flag, Tone.Warn, str(R.string.hs_report), str(R.string.hs_report_sub), onReportSafety)
        }
        GroupCard(Modifier.padding(top = 14.dp, bottom = 16.dp)) { TileRow(RideIcons.Msg, Tone.Info, str(R.string.sc_contact), str(R.string.hs_contact_sub), onContact) }
    }
}

/** Safety Center: what RideNow offers, with shortcuts to the tools. */
@Composable
fun SafetyCenterScreen(onBack: () -> Unit, onEmergency: () -> Unit, onShareTrip: () -> Unit, onReport: () -> Unit, modifier: Modifier = Modifier) {
    SubScreen(str(R.string.hs_center), onBack, modifier) {
        InfoBanner(RideIcons.Shield, str(R.string.sf_banner_title), str(R.string.sf_banner_text), Modifier.padding(top = 4.dp), Tone.Info)
        SectionTitle(str(R.string.sf_tips))
        GroupCard {
            val tips = listOf(R.string.sf_tip1 to RideIcons.Person, R.string.sf_tip2 to RideIcons.Pin, R.string.sf_tip3 to RideIcons.Lock, R.string.sf_tip4 to RideIcons.Phone)
            tips.forEachIndexed { i, (t, icon) ->
                if (i > 0) RowDivider()
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) { RideIcon(icon, RideTheme.colors.accent, size = 22.dp); Text(str(t), color = RideTheme.colors.fg, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(start = 12.dp)) }
            }
        }
        SectionTitle(str(R.string.hs_tools))
        GroupCard(Modifier.padding(bottom = 16.dp)) {
            TileRow(RideIcons.Siren, Tone.Danger, str(R.string.hs_emergency), str(R.string.hs_emergency_sub), onEmergency); RowDivider()
            TileRow(RideIcons.Users, Tone.Good, str(R.string.hs_share), str(R.string.hs_share_sub), onShareTrip); RowDivider()
            TileRow(RideIcons.Flag, Tone.Warn, str(R.string.hs_report), str(R.string.hs_report_sub), onReport)
        }
    }
}

/** Emergency Assist: one big call button and a way to send a help message to someone you trust. */
@Composable
fun EmergencyAssistScreen(onBack: () -> Unit, onReport: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val context = LocalContext.current
    val number = str(R.string.emergency_number)
    val help = str(R.string.em_help_message)
    SubScreen(str(R.string.hs_emergency), onBack, modifier) {
        InfoBanner(RideIcons.Siren, str(R.string.em_banner_title), str(R.string.em_banner_text), Modifier.padding(top = 4.dp), Tone.Danger)
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(32.dp)).background(androidx.compose.ui.graphics.Color(0xFFE5282B)).clickable(role = Role.Button) { openDial(context, number) }, contentAlignment = Alignment.Center) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) { RideIcon(RideIcons.Call, androidx.compose.ui.graphics.Color.White, size = 22.dp); Text(str(R.string.em_call, number), color = androidx.compose.ui.graphics.Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f, fill = false).padding(start = 10.dp, end = 12.dp, top = 14.dp, bottom = 14.dp)) }
        }
        Spacer(Modifier.height(12.dp))
        SoftButton(str(R.string.em_send_help), { shareText(context, str(R.string.hs_emergency).let { "RideNow" }, help) }, icon = RideIcons.Share)
        Spacer(Modifier.height(10.dp))
        SoftButton(str(R.string.hs_report), onReport, icon = RideIcons.Flag)
        SectionTitle(str(R.string.em_when))
        GroupCard(Modifier.padding(bottom = 16.dp)) { Text(str(R.string.em_when_text), color = c.muted, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(16.dp)) }
    }
}

/** Share Live Trip: explains sharing and opens the system share sheet. */
@Composable
fun ShareTripScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val context = LocalContext.current
    val msg = str(R.string.sl_message)
    SubScreen(str(R.string.hs_share), onBack, modifier, bottom = { RideButton(str(R.string.sl_share_button), { shareText(context, "RideNow", msg) }) }) {
        InfoBanner(RideIcons.Users, str(R.string.sl_banner_title), str(R.string.sl_banner_text), Modifier.padding(top = 4.dp), Tone.Good)
        SectionTitle(str(R.string.sl_how))
        GroupCard {
            listOf(R.string.sl_step1, R.string.sl_step2, R.string.sl_step3).forEachIndexed { i, t ->
                if (i > 0) RowDivider()
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(c.fill), contentAlignment = Alignment.Center) { Text("${i + 1}", color = c.onFill, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    Text(str(t), color = c.fg, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
    }
}

/** Report an issue: pick a category, describe it (up to 500 characters) and add up to five screenshots. */
@Composable
fun ReportIssueScreen(store: DriverStore, initialCategory: Int, onBack: () -> Unit, onSent: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val cats = listOf(
        R.string.cat_ride to RideIcons.Car, R.string.cat_payments to RideIcons.Wallet, R.string.cat_account to RideIcons.Person, R.string.cat_maps to RideIcons.Pin,
        R.string.cat_bug to RideIcons.Gear, R.string.cat_safety to RideIcons.Shield, R.string.cat_service to RideIcons.Msg, R.string.cat_other to RideIcons.More,
    )
    var cat by rememberSaveable { mutableStateOf(initialCategory) }
    var body by rememberSaveable { mutableStateOf("") }
    var shots by remember { mutableStateOf(listOf<android.net.Uri>()) }
    var tried by rememberSaveable { mutableStateOf(false) }
    var done by rememberSaveable { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { uris -> shots = (shots + uris).distinct().take(5) }
    SubScreen(str(R.string.report_title), onBack, modifier, bottom = {
        RideButton(str(R.string.rep_submit), {
            tried = true
            if (body.isNotBlank()) { store.addTicket(str0(cats[cat].first, store), if (cat == 5) "shield" else "alert", body.trim(), shots.firstOrNull()?.lastPathSegment); done = true }
        })
    }) {
        InfoBanner(RideIcons.Alert, str(R.string.rep_banner_title), str(R.string.rep_banner_text), Modifier.padding(top = 4.dp), Tone.Brand)
        SectionTitle(str(R.string.rep_category))
        cats.chunked(3).forEachIndexed { r, row ->
            Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEachIndexed { i, (label, icon) ->
                    val idx = r * 3 + i; val on = cat == idx
                    Column(
                        Modifier.weight(1f).heightIn(min = 84.dp).clip(RoundedCornerShape(16.dp)).background(c.field).border(if (on) 2.dp else 1.dp, if (on) c.fill else c.border, RoundedCornerShape(16.dp)).selectable(on, role = Role.RadioButton) { cat = idx }.padding(10.dp),
                    ) {
                        Row { RideIcon(icon, c.fg, size = 22.dp); Spacer(Modifier.weight(1f)); if (on) CheckBadge(size = 20.dp) else RadioDot(false, Modifier.size(20.dp)) }
                        Text(str(label), color = c.fg, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        SectionTitle(str(R.string.rep_describe))
        Box(Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(16.dp)).background(c.field).border(1.dp, if (tried && body.isBlank()) c.danger else c.border, RoundedCornerShape(16.dp)).padding(14.dp)) {
            if (body.isEmpty()) Text(str(R.string.rep_hint), color = c.muted, fontSize = 14.sp)
            BasicTextField(body, { body = it.take(500) }, textStyle = TextStyle(color = c.fg, fontSize = 14.sp), cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxSize())
        }
        Text("${body.length}/500", color = c.muted, fontSize = 12.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        SectionTitle(str(R.string.rep_shots))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, c.border, RoundedCornerShape(16.dp)).clickable(role = Role.Button, enabled = shots.size < 5) { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(c.line), contentAlignment = Alignment.Center) { RideIcon(RideIcons.Image, c.fg, size = 22.dp) }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(str(R.string.rep_tap_add), color = c.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(if (shots.isEmpty()) str(R.string.rep_up_to) else str(R.string.rep_added, shots.size), color = c.muted, fontSize = 12.sp)
            }
            RideIcon(RideIcons.Chevron, c.muted, size = 16.dp)
        }
        shots.forEach { u ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                RideIcon(RideIcons.Image, c.muted, size = 18.dp)
                Text(u.lastPathSegment ?: "photo", color = c.fg, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
                CircleButton(RideIcons.Close, str(R.string.att_remove), { shots = shots - u }, size = 36.dp, filled = false)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
    if (done) AlertDialog(
        onDismissRequest = { }, containerColor = c.bg,
        title = { Text(str(R.string.rep_done_title), color = c.fg) }, text = { Text(str(R.string.rep_done_text), color = c.muted) },
        confirmButton = { TextButton(onClick = { done = false; onSent() }) { Text(str(R.string.dialog_ok), color = c.accent) } },
    )
}

/** Plain category name for a ticket title. Resource strings need a composable, so the Report screen passes through the store language-neutral. */
@Suppress("UNUSED_PARAMETER")
private fun str0(id: Int, store: DriverStore): String = REPORT_TITLES[id] ?: "Issue report"

private val REPORT_TITLES = mapOf(
    R.string.cat_ride to "Ride issue", R.string.cat_payments to "Payment issue", R.string.cat_account to "Account issue", R.string.cat_maps to "Map & Navigation issue",
    R.string.cat_bug to "App bug", R.string.cat_safety to "Safety issue", R.string.cat_service to "Customer service", R.string.cat_other to "Issue report",
)
