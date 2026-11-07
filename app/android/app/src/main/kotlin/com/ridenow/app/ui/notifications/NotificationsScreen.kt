package com.ridenow.app.ui.notifications

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
import androidx.compose.ui.tooling.preview.Preview
import com.ridenow.app.ui.components.RideIcon
import com.ridenow.app.ui.components.RideIcons
import com.ridenow.app.ui.components.SectionLabel
import com.ridenow.app.ui.theme.RideNowTheme
import com.ridenow.app.ui.theme.RideTheme
import com.ridenow.app.ui.util.entrance

private enum class Kind(val label: String) { Rides("Rides"), Promotions("Promotions"), Updates("Updates"), Safety("Safety"), Account("Account") }
private class Notice(val id: Int, val kind: Kind, val icon: List<String>, val title: String, val body: String, val time: String, val today: Boolean)

private val All = listOf(
    Notice(1, Kind.Rides, RideIcons.Car, "Your driver is arriving", "Mira is 1 min away in Orbit · RN 2100.", "Now", true),
    Notice(2, Kind.Rides, RideIcons.Receipt, "Trip completed", "Your $16.80 receipt is ready to download.", "2h", true),
    Notice(3, Kind.Promotions, RideIcons.Tag, "20% off your next 3 rides", "Use code NOVA20 before Sunday.", "5h", true),
    Notice(4, Kind.Updates, RideIcons.Clock, "New: ride later", "Plan your pickup ahead with the Later button.", "Yesterday", false),
    Notice(5, Kind.Account, RideIcons.Person, "Add your work address", "Save it once for one-tap rides.", "Mon", false),
    Notice(6, Kind.Safety, RideIcons.Shield, "Add a trusted contact", "Let someone follow your trips in real time.", "Sun", false),
)

/** Notification list with filter pills. Sample data; tapping marks an item as read. */
@Composable
fun NotificationsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var unread by remember { mutableStateOf(setOf(1, 2, 3).minus(2)) }
    val shown = All.filter { filter == null || it.kind.label == filter }
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.offset(x = (-12).dp).size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = "Back" }, contentAlignment = Alignment.Center) { RideIcon(RideIcons.Back, c.fg, size = 22.dp) }
            Spacer(Modifier.weight(1f))
            Text("Notifications", color = c.fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.weight(1f))
            Box(Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, enabled = unread.isNotEmpty()) { unread = emptySet() }.padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                Text("Mark all read", color = if (unread.isEmpty()) c.muted else c.accent, fontSize = 13.sp)
            }
        }
        Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("All", filter == null) { filter = null }
            Kind.values().forEach { k -> Pill(k.label, filter == k.label) { filter = k.label } }
        }
        if (shown.isEmpty()) Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("You are all caught up.", color = c.fg, fontSize = 24.sp, fontWeight = FontWeight.Light)
            Text("Nothing new right now.", color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        } else Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).entrance(60)) {
            val today = shown.filter { it.today }; val earlier = shown.filter { !it.today }
            if (today.isNotEmpty()) { SectionLabel("TODAY"); today.forEach { NoticeRow(it, it.id in unread) { unread = unread - it.id } } }
            if (earlier.isNotEmpty()) { SectionLabel("EARLIER"); earlier.forEach { NoticeRow(it, it.id in unread) { unread = unread - it.id } } }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Pill(text: String, on: Boolean, onClick: () -> Unit) {
    val c = RideTheme.colors
    Box(
        Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(if (on) c.accent else androidx.compose.ui.graphics.Color.Transparent).border(1.dp, if (on) c.accent else c.border, RoundedCornerShape(20.dp))
            .clickable(role = Role.Tab, onClick = onClick).padding(horizontal = 18.dp), contentAlignment = Alignment.Center,
    ) { Text(text, color = if (on) c.onAccent else c.fg, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal) }
}

@Composable
private fun NoticeRow(n: Notice, unread: Boolean, onClick: () -> Unit) {
    val c = RideTheme.colors
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(if (unread) c.accent.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent).border(1.dp, c.border, CircleShape), contentAlignment = Alignment.Center) { RideIcon(n.icon, c.accent) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(n.title, color = c.fg, fontSize = 15.sp, fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal)
                Text(n.body, color = c.muted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(n.time, color = c.muted, fontSize = 12.sp)
                if (unread) Box(Modifier.padding(top = 8.dp).size(8.dp).clip(CircleShape).background(c.accent).semantics { contentDescription = "Unread" })
            }
        }
        HorizontalDivider(color = c.line)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun NotificationsPreview() = RideNowTheme(darkTheme = true) { NotificationsScreen({}) }
