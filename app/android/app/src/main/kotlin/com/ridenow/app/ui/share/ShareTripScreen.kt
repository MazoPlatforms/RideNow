package com.ridenow.app.ui.share

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
import android.content.Intent
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import com.ridenow.app.ui.components.RideButton
import com.ridenow.app.ui.components.RideIcon
import com.ridenow.app.ui.components.RideIcons
import com.ridenow.app.ui.components.RideSwitch
import com.ridenow.app.ui.components.ScreenTop
import com.ridenow.app.ui.safety.Contact
import com.ridenow.app.ui.theme.BrandYellow
import com.ridenow.app.ui.theme.Ink
import com.ridenow.app.ui.theme.RideNowTheme
import com.ridenow.app.ui.theme.RideTheme
import com.ridenow.app.ui.util.entrance

private const val TripLink = "https://ridenow.app/t/8F3K2Q"   // placeholder link

/** Pick trusted contacts, copy the live-trip link, or send it with the phone's share sheet. */
@Composable
fun ShareTripScreen(contacts: List<Contact>, onBack: () -> Unit, onManage: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var picked by remember { mutableStateOf(contacts.map { it.name }.toSet()) }
    var copied by remember { mutableStateOf(false) }
    var auto by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        ScreenTop("Share live trip", onBack, subtitle = "Trusted contacts can follow your route in real time.")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).entrance(60)) {
            Row(Modifier.padding(top = 20.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                contacts.forEach { k ->
                    val on = k.name in picked
                    Column(Modifier.width(64.dp).clickable(role = Role.Checkbox) { picked = if (on) picked - k.name else picked + k.name }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(56.dp)) {
                            Box(Modifier.fillMaxSize().clip(CircleShape).background(if (on) c.accent.copy(alpha = 0.14f) else c.field).border(if (on) 1.5.dp else 1.dp, if (on) c.accent else c.border, CircleShape), contentAlignment = Alignment.Center) {
                                Text(k.name.take(2).uppercase(), color = if (on) c.accent else c.muted, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            }
                            if (on) Box(Modifier.align(Alignment.BottomEnd).size(20.dp).clip(CircleShape).background(c.accent).border(2.dp, c.bg, CircleShape), contentAlignment = Alignment.Center) { RideIcon(RideIcons.Check, c.onAccent, size = 11.dp) }
                        }
                        Text(k.name, color = c.fg, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                Column(Modifier.width(64.dp).clickable(role = Role.Button, onClick = onManage), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(56.dp).clip(CircleShape).border(1.5.dp, c.border, CircleShape), contentAlignment = Alignment.Center) { RideIcon(RideIcons.Plus, c.accent) }
                    Text("Add", color = c.fg, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
            Row(Modifier.padding(top = 20.dp).fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                RideIcon(listOf("M8.5 11.5a3 3 0 0 0 4.2 0l2.5-2.5a3 3 0 0 0-4.2-4.2L10 5.8", "M11.5 8.5a3 3 0 0 0-4.2 0L4.8 11A3 3 0 0 0 9 15.2l1-1"), c.accent); Spacer(Modifier.width(12.dp))
                Text("ridenow.app/t/8F3K2Q", color = c.fg, fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f))
                Box(Modifier.heightIn(min = 48.dp).clickable(role = Role.Button) { clipboard.setText(AnnotatedString(TripLink)); copied = true }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    Text(if (copied) "Copied" else "Copy", color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Row(Modifier.padding(top = 8.dp).fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Share automatically on every ride", color = c.fg, fontSize = 14.sp, modifier = Modifier.weight(1f))
                RideSwitch(auto, "Share automatically on every ride") { auto = it }
            }
        }
        RideButton(
            if (picked.isEmpty()) "Share link" else "Share with ${picked.size} contact${if (picked.size == 1) "" else "s"}",
            {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Follow my RideNow trip: $TripLink")
                runCatching { context.startActivity(Intent.createChooser(send, "Share live trip").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            },
            modifier = Modifier.padding(vertical = 12.dp),
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SharePreview() = RideNowTheme(darkTheme = true) { ShareTripScreen(listOf(Contact("Ana", "1"), Contact("Ben", "2")), {}, {}) }
