package com.ridenow.app.ui.menu

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
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.ui.graphics.Color
import com.ridenow.app.ui.components.RideIcon
import com.ridenow.app.ui.components.RideIcons
import com.ridenow.app.ui.profile.initialsOf
import com.ridenow.app.ui.theme.RideTheme

private class Item(val icon: List<String>, val title: String, val sub: String, val badge: String? = null)

private val Main = listOf(
    Item(RideIcons.Home, "Home", "Find a ride"),
    Item(RideIcons.Clock, "Your Rides", "History and receipts"),
    Item(RideIcons.Card, "Payments", "Payment methods"),
    Item(RideIcons.Pin, "Saved Places", "Home, work and favorites"),
    Item(RideIcons.Bell, "Notifications", "Rides, offers and updates", "1"),
)
private val More = listOf(
    Item(RideIcons.Shield, "Safety center", "Contacts and emergency tools"),
    Item(RideIcons.Help, "Help & Support", "FAQs and contact us"),
    Item(RideIcons.Sliders, "Settings", "Account and preferences"),
    Item(RideIcons.Info, "About RideNow", "Version, terms and privacy"),
)

/** Left drawer over Home. [onItem] receives the tapped title. */
@Composable
fun MenuDrawer(open: Boolean, name: String, phone: String, onClose: () -> Unit, onProfile: () -> Unit, onItem: (String) -> Unit, onSignOut: () -> Unit) {
    val c = RideTheme.colors
    BackHandler(enabled = open) { onClose() }
    AnimatedVisibility(open, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (c.isDark) 0.62f else 0.45f)).clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClick = onClose))
    }
    AnimatedVisibility(open, enter = slideInHorizontally { -it }, exit = slideOutHorizontally { -it }) {
        Column(
            Modifier.fillMaxHeight().width(320.dp).background(c.bg).border(1.dp, c.line).statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 16.dp)
                .semantics { contentDescription = "Main menu" },
        ) {
            Row(
                Modifier.fillMaxWidth().height(88.dp).clip(RoundedCornerShape(20.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(20.dp)).clickable(role = Role.Button, onClick = onProfile).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.14f)).border(1.5.dp, c.accent, CircleShape), contentAlignment = Alignment.Center) { Text(initialsOf(name), color = c.accent, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) { Text(name, color = c.fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1); Text(phone, color = c.muted, fontSize = 13.sp, maxLines = 1) }
                RideIcon(listOf("M7 4l6 6-6 6"), c.muted, size = 18.dp)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 16.dp)) {
                Main.forEachIndexed { i, m -> DrawerRow(m, i == 0) { if (i == 0) onClose() else onItem(m.title) } }
                HorizontalDivider(color = c.line, modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp))
                More.forEach { m -> DrawerRow(m, false) { onItem(m.title) } }
            }
            HorizontalDivider(color = c.line)
            Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onSignOut), verticalAlignment = Alignment.CenterVertically) {
                    RideIcon(RideIcons.Logout, c.muted); Spacer(Modifier.width(12.dp)); Text("Sign out", color = c.fg, fontSize = 15.sp)
                }
                Spacer(Modifier.weight(1f)); Text("RideNow 2100.1", color = c.muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DrawerRow(item: Item, selected: Boolean, onClick: () -> Unit) {
    val c = RideTheme.colors
    Row(
        Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(14.dp)).background(if (selected) c.accent.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RideIcon(item.icon, if (selected) c.accent else c.muted); Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, color = c.fg, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            Text(item.sub, color = c.muted, fontSize = 12.sp, maxLines = 1)
        }
        if (item.badge != null) Box(
            Modifier.defaultMinSize(minWidth = 22.dp, minHeight = 22.dp).clip(CircleShape).background(c.accent).padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) { Text(item.badge, color = c.onAccent, fontSize = 12.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold) }
    }
}
