package com.ridenow.app.ui.menu

import androidx.compose.ui.res.stringResource
import com.ridenow.app.R
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.ridenow.app.ui.components.RideIcon
import com.ridenow.app.ui.components.RideIcons
import com.ridenow.app.ui.profile.initialsOf
import com.ridenow.app.ui.theme.RideTheme

private class Item(val icon: List<String>, val route: String, val title: Int, val sub: Int, val badge: String? = null)

private val Main = listOf(
    Item(RideIcons.Home, "Home", R.string.menu_home, R.string.menu_home_sub),
    Item(RideIcons.Clock, "Your Rides", R.string.menu_rides, R.string.menu_rides_sub),
    Item(RideIcons.Card, "Payments", R.string.menu_payments, R.string.menu_payments_sub),
    Item(RideIcons.Pin, "Saved Places", R.string.menu_saved, R.string.menu_saved_sub),
    Item(RideIcons.Bell, "Notifications", R.string.menu_notifications, R.string.menu_notifications_sub, "1"),
)
private val More = listOf(
    Item(RideIcons.Shield, "Safety center", R.string.menu_safety, R.string.menu_safety_sub),
    Item(RideIcons.Help, "Help & Support", R.string.menu_help, R.string.menu_help_sub),
    Item(RideIcons.Sliders, "Settings", R.string.menu_settings, R.string.menu_settings_sub),
    Item(RideIcons.Info, "About RideNow", R.string.menu_about, R.string.menu_about_sub),
)

/** Left drawer over Home. [onItem] receives the tapped item's route id, which does not change with language. */
@Composable
fun MenuDrawer(open: Boolean, name: String, phone: String, onClose: () -> Unit, onProfile: () -> Unit, onItem: (String) -> Unit, onSignOut: () -> Unit) {
    val mainMenuLabel = stringResource(R.string.menu_main_menu)
    val c = RideTheme.colors
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    BackHandler(enabled = open) { onClose() }
    AnimatedVisibility(open, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (c.isDark) 0.62f else 0.45f)).clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClick = onClose))
    }
    AnimatedVisibility(open, enter = slideInHorizontally { if (isRtl) it else -it }, exit = slideOutHorizontally { if (isRtl) it else -it }) {
        Column(
            Modifier.fillMaxHeight().width(320.dp).background(c.bg).border(1.dp, c.line).statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 16.dp)
                .semantics { contentDescription = mainMenuLabel },
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
                Main.forEachIndexed { i, m -> DrawerRow(m, i == 0) { if (i == 0) onClose() else onItem(m.route) } }
                HorizontalDivider(color = c.line, modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp))
                More.forEach { m -> DrawerRow(m, false) { onItem(m.route) } }
            }
            HorizontalDivider(color = c.line)
            Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onSignOut), verticalAlignment = Alignment.CenterVertically) {
                    RideIcon(RideIcons.Logout, c.muted); Spacer(Modifier.width(12.dp)); Text(stringResource(R.string.menu_sign_out), color = c.fg, fontSize = 15.sp)
                }
                Spacer(Modifier.weight(1f)); Text(stringResource(R.string.menu_version, com.ridenow.app.BuildConfig.VERSION_NAME), color = c.muted, fontSize = 12.sp)
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
            Text(stringResource(item.title), color = c.fg, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            Text(stringResource(item.sub), color = c.muted, fontSize = 12.sp, maxLines = 1)
        }
        if (item.badge != null) Box(
            Modifier.defaultMinSize(minWidth = 22.dp, minHeight = 22.dp).clip(CircleShape).background(c.accent).padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) { Text(item.badge, color = c.onAccent, fontSize = 12.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold) }
    }
}
