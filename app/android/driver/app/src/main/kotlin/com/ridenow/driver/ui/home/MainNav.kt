package com.ridenow.driver.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridenow.driver.R
import com.ridenow.driver.ui.components.RideIcon
import com.ridenow.driver.ui.components.RideIcons
import com.ridenow.driver.ui.theme.RideTheme

enum class MainTab(val icon: List<String>, val labelRes: Int) {
    Home(RideIcons.Home, R.string.nav_home),
    Earnings(RideIcons.Chart, R.string.nav_earnings),
    Inbox(RideIcons.Msg, R.string.nav_inbox),
    Account(RideIcons.Person, R.string.nav_account),
}

/** Tabs that show an unread dot. Provided once at the app root so every bar reads the same state. */
val LocalNavBadges = staticCompositionLocalOf<Set<MainTab>> { emptySet() }

/**
 * The four tabs. The selected tab gets a tinted pill behind its icon, bold accent label, and an unread
 * dot appears on tabs in [LocalNavBadges]. The same bar is used on Home and on every other tab.
 */
@Composable
fun MainNavBar(selected: MainTab, onTab: (MainTab) -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val badges = LocalNavBadges.current
    val unread = stringResource(R.string.nav_unread_desc)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        MainTab.entries.forEach { tab ->
            val active = tab == selected
            val pill by animateColorAsState(if (active) c.accent.copy(alpha = 0.16f) else Color.Transparent, label = "pill")
            val hasBadge = tab in badges
            val label = stringResource(tab.labelRes)
            Column(
                Modifier.weight(1f).heightIn(min = 60.dp)
                    .selectable(active, role = Role.Tab) { onTab(tab) }
                    .semantics(mergeDescendants = true) {
                        selected = active
                        contentDescription = label
                        if (hasBadge) stateDescription = unread
                    }
                    .padding(top = 6.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(Modifier.size(width = 56.dp, height = 30.dp).clip(RoundedCornerShape(15.dp)).background(pill), contentAlignment = Alignment.Center) {
                        RideIcon(tab.icon, if (active) c.accent else c.muted, size = 22.dp)
                    }
                    if (hasBadge) Box(
                        Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 10.dp).size(10.dp).clip(CircleShape)
                            .background(c.danger).border(1.5.dp, c.field, CircleShape),
                    )
                }
                Text(
                    label, color = if (active) c.accent else c.muted, fontSize = 11.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, softWrap = false,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp),
                )
            }
        }
    }
}

/** Bar with a top divider and the system inset. Used under every tab, including inside the Home sheet. */
@Composable
fun MainBottomBar(selected: MainTab, onTab: (MainTab) -> Unit) {
    val c = RideTheme.colors
    Column(Modifier.fillMaxWidth().background(c.field)) {
        HorizontalDivider(color = c.line)
        MainNavBar(selected, onTab, Modifier.navigationBarsPadding().padding(horizontal = 8.dp))
    }
}
