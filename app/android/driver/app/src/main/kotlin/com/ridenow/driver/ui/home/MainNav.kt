package com.ridenow.driver.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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

/** The four tabs. Used inside the Home sheet and, wrapped by [MainBottomBar], under the other tabs. */
@Composable
fun MainNavBar(selected: MainTab, onTab: (MainTab) -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        MainTab.entries.forEach { tab ->
            val active = tab == selected
            Column(
                Modifier.weight(1f).heightIn(min = 56.dp).selectable(active, role = Role.Tab) { onTab(tab) },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                RideIcon(tab.icon, if (active) c.accent else c.muted, size = 22.dp)
                Text(
                    stringResource(tab.labelRes), color = if (active) c.accent else c.muted, fontSize = 11.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** Bar with a top divider for Earnings, Inbox and Account. */
@Composable
fun MainBottomBar(selected: MainTab, onTab: (MainTab) -> Unit) {
    val c = RideTheme.colors
    Column(Modifier.fillMaxWidth().background(c.field)) {
        HorizontalDivider(color = c.line)
        MainNavBar(selected, onTab, Modifier.navigationBarsPadding().padding(horizontal = 12.dp))
    }
}
