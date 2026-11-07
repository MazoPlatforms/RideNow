package com.ridenow.app.ui.saved

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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.draw.drawBehind
import com.ridenow.app.ui.components.RideButton
import com.ridenow.app.ui.components.RideIcon
import com.ridenow.app.ui.components.RideIcons
import com.ridenow.app.ui.components.ScreenTop
import com.ridenow.app.ui.components.SectionLabel
import com.ridenow.app.ui.theme.RideNowTheme
import com.ridenow.app.ui.theme.RideTheme
import com.ridenow.app.ui.util.entrance

/** Home and Work are always shown; other places can be removed. Edits and adds open placeholders for now. */
@Composable
fun SavedAddressesScreen(onBack: () -> Unit, onEdit: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val others = remember { mutableStateListOf("Nova Terminal, Gate 4" to "Sector 12, Orbit Ring", "Helix Garden Plaza" to "Sector 9, Garden Loop", "Aurora Medical Centre" to "Sector 3, Skyline Way") }
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        ScreenTop("Saved addresses", onBack, subtitle = "Home and Work are always one tap away.")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).entrance(60)) {
            Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Slot(RideIcons.Home, "Home", "128 Meridian Walk, Sector 7", set = true) { onEdit("Home address") }
                Slot(RideIcons.Work, "Work", "Not set yet", set = false) { onEdit("Work address") }
            }
            SectionLabel("OTHER PLACES")
            if (others.isEmpty()) Text("No favorites yet. Save places you visit often.", color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp))
            others.forEachIndexed { i, (title, sub) ->
                var menu by remember { mutableStateOf(false) }
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).clip(CircleShape).border(1.dp, c.border, CircleShape), contentAlignment = Alignment.Center) { RideIcon(RideIcons.Heart, c.accent, size = 18.dp) }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) { Text(title, color = c.fg, fontSize = 15.sp); Text(sub, color = c.muted, fontSize = 12.sp) }
                        Box {
                            Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button) { menu = true }.semantics { contentDescription = "More options for $title" }, contentAlignment = Alignment.Center) { RideIcon(RideIcons.More, c.muted) }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit(title) })
                                DropdownMenuItem(text = { Text("Remove") }, onClick = { menu = false; others.removeAt(i) })
                            }
                        }
                    }
                    HorizontalDivider(color = c.line)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        RideButton("Add new address", { onEdit("Add address") }, modifier = Modifier.padding(vertical = 12.dp))
    }
}

@Composable
private fun Slot(icon: List<String>, title: String, sub: String, set: Boolean, onClick: () -> Unit) {
    val c = RideTheme.colors
    val dash = c.border
    Row(
        Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(20.dp)).background(if (set) c.field else androidx.compose.ui.graphics.Color.Transparent)
            .then(if (set) Modifier.border(1.dp, c.border, RoundedCornerShape(20.dp)) else Modifier.drawBehind { drawRoundRect(dash, cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))) })
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) { RideIcon(icon, c.accent) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) { Text(title, color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold); Text(sub, color = c.muted, fontSize = 13.sp) }
        RideIcon(if (set) RideIcons.Pencil else RideIcons.Plus, c.accent)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SavedPreview() = RideNowTheme(darkTheme = true) { SavedAddressesScreen({}, {}) }
