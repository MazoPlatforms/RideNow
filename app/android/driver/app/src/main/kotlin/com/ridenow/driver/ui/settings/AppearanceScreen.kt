package com.ridenow.driver.ui.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.ridenow.driver.R
import com.ridenow.driver.ui.components.*
import com.ridenow.driver.ui.theme.*
import com.ridenow.driver.ui.util.AppIconChoice
import com.ridenow.driver.ui.util.currentAppIcon
import com.ridenow.driver.ui.util.setAppIcon

@Composable
private fun Heading(title: String, sub: String) {
    val c = RideTheme.colors
    Column(Modifier.padding(top = 20.dp, bottom = 10.dp)) {
        Text(title, color = c.fg, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        Text(sub, color = c.muted, fontSize = 13.sp)
    }
}

@Composable
private fun SelectCard(selected: Boolean, label: String, onClick: () -> Unit, modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = RideTheme.colors
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.clip(shape).background(c.field).border(if (selected) 2.dp else 1.dp, if (selected) c.fill else c.border, shape).selectable(selected, role = Role.RadioButton, onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(92.dp), content = content)
        Row(Modifier.padding(top = 8.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = if (selected) c.accent else c.fg, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
        }
    }
}

/** Appearance: theme, map style, accent colour, text size and app icon. Every choice applies at once and is remembered. */
@Composable
fun AppearanceScreen(state: AppearanceState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val context = LocalContext.current
    var textSheet by remember { mutableStateOf(false) }
    var iconSheet by remember { mutableStateOf(false) }
    var icon by remember { mutableStateOf(currentAppIcon(context)) }
    SubScreen(str(R.string.acct_appearance), onBack, modifier) {
        Heading(str(R.string.ap_theme), str(R.string.ap_theme_sub))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ThemeMode.entries.forEach { m ->
                SelectCard(state.mode == m, str(m.labelRes), { state.setMode(m) }, Modifier.weight(1f)) {
                    ThemePreview(m, Modifier.fillMaxSize())
                    if (state.mode == m) CheckBadge(Modifier.align(Alignment.TopEnd), 20.dp)
                }
            }
        }
        Heading(str(R.string.ap_map), str(R.string.ap_map_sub))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MapStyle.entries.forEach { s ->
                SelectCard(state.mapStyle == s, str(s.labelRes), { state.setMapStyle(s) }, Modifier.weight(1f)) {
                    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))) { MapStyleTile(s) }
                    if (state.mapStyle == s) CheckBadge(Modifier.align(Alignment.TopEnd).padding(4.dp), 20.dp)
                }
            }
        }
        Heading(str(R.string.ap_accent), str(R.string.ap_accent_sub))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AccentChoice.entries.forEach { a ->
                val on = state.accent == a
                val label = str(a.labelRes)
                Box(
                    Modifier.size(48.dp).clip(CircleShape).then(if (on) Modifier.border(2.dp, c.fg, CircleShape).padding(4.dp) else Modifier).clip(CircleShape).background(a.fill)
                        .selectable(on, role = Role.RadioButton) { state.setAccent(a) }.semantics { contentDescription = label },
                    contentAlignment = Alignment.Center,
                ) { if (on) RideIcon(RideIcons.Check, a.onFill, size = 22.dp) }
            }
        }
        Heading(str(R.string.ap_display), "")
        GroupCard {
            ToggleRow(RideIcons.TextSize, str(R.string.ap_system_font), state.systemFont, { state.setSystemFont(it) }); RowDivider()
            ValueRow(RideIcons.TextSize, str(R.string.ap_text_size), str(state.textSize.labelRes)) { textSheet = true }
        }
        Heading(str(R.string.ap_icon), "")
        GroupCard(Modifier.padding(bottom = 16.dp)) { ValueRow(RideIcons.Grid, str(R.string.ap_change_icon), str(icon.labelRes)) { iconSheet = true } }
    }
    if (textSheet) RideSheet(str(R.string.ap_text_size), { textSheet = false }, str(R.string.ap_text_size_sub)) { close ->
        GroupCard {
            TextSize.entries.forEachIndexed { i, t ->
                if (i > 0) RowDivider()
                val on = !state.systemFont && state.textSize == t
                Row(Modifier.fillMaxWidth().height(56.dp).selectable(on, role = Role.RadioButton) { state.setTextSize(t); state.setSystemFont(false); close() }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(str(t.labelRes), color = c.fg, fontSize = (15 * t.scale).sp, modifier = Modifier.weight(1f)); RadioDot(on)
                }
            }
        }
    }
    if (iconSheet) RideSheet(str(R.string.ap_change_icon), { iconSheet = false }, str(R.string.ap_icon_sub)) { close ->
        GroupCard {
            AppIconChoice.entries.forEachIndexed { i, ch ->
                if (i > 0) RowDivider()
                Row(Modifier.fillMaxWidth().height(64.dp).selectable(icon == ch, role = Role.RadioButton) { setAppIcon(context, ch); icon = ch; close() }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(androidx.compose.ui.res.colorResource(ch.colorRes)), contentAlignment = Alignment.Center) { LogoMark(30.dp) }
                    Text(str(ch.labelRes), color = c.fg, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(start = 14.dp)); RadioDot(icon == ch)
                }
            }
        }
    }
}
