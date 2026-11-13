package com.ridenow.driver.ui.inbox

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import com.ridenow.driver.R
import com.ridenow.driver.ui.components.*
import com.ridenow.driver.ui.home.MainBottomBar
import com.ridenow.driver.ui.home.MainTab
import com.ridenow.driver.ui.theme.RideNowTheme
import com.ridenow.driver.ui.theme.RideTheme
import com.ridenow.driver.ui.util.entrance
import androidx.compose.ui.text.style.TextAlign

/** Inbox tab: messages from RideNow. Only the welcome message exists until trips and support replies do. */
@Composable
fun InboxScreen(onTab: (MainTab) -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text(stringResource(R.string.inbox_title), color = c.fg, fontSize = 30.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(top = 16.dp).semantics { heading() })
            GroupCard(Modifier.padding(top = 16.dp).entrance(60)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) { LogoMark(28.dp) }
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.inbox_from), color = c.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(stringResource(R.string.inbox_today), color = c.muted, fontSize = 12.sp)
                            Box(Modifier.padding(start = 8.dp).size(8.dp).clip(CircleShape).background(c.accent))
                        }
                        Text(stringResource(R.string.inbox_welcome_title), color = c.fg, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                        Text(stringResource(R.string.inbox_welcome_text), color = c.muted, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
            Text(
                stringResource(R.string.inbox_empty), color = c.muted, fontSize = 13.sp, lineHeight = 19.sp,
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 12.dp), textAlign = TextAlign.Center,
            )
        }
        MainBottomBar(MainTab.Inbox, onTab)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun InboxDarkPreview() = RideNowTheme(darkTheme = true) { InboxScreen({}) }

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun InboxLightPreview() = RideNowTheme(darkTheme = false) { InboxScreen({}) }
