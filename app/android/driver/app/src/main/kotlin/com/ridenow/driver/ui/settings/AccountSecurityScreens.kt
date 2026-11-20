package com.ridenow.driver.ui.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridenow.driver.R
import com.ridenow.driver.ui.components.*
import com.ridenow.driver.ui.theme.RideTheme
import com.ridenow.driver.ui.util.rememberBoolPref

@Composable
private fun Intro(text: String) {
    Text(text, color = RideTheme.colors.muted, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 4.dp))
}

/** Devices where the driver is signed in, with a way to log out of the others. */
@Composable
fun ManageDevicesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val context = LocalContext.current
    val tablet = str(R.string.sec_tablet)
    val lastActive = str(R.string.sec_last_active)
    val others = remember { mutableStateListOf(tablet to lastActive) }
    val loggedOut = str(R.string.sec_logged_out_others)
    val logOutValue = str(R.string.sec_log_out_value)
    SubScreen(str(R.string.sec_devices_title), onBack, modifier, bottom = {
        if (others.isNotEmpty()) SoftButton(str(R.string.sec_log_out_all), { others.clear(); toast(context, loggedOut) }, danger = true)
    }) {
        Intro(str(R.string.sec_devices_sub))
        SectionTitle(str(R.string.sec_this_device))
        GroupCard {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                RideIcon(RideIcons.Phone, c.accent)
                Column(Modifier.padding(start = 14.dp).weight(1f)) { Text(android.os.Build.MODEL, color = c.fg, fontSize = 15.sp); Text(str(R.string.priv_active_now), color = c.muted, fontSize = 12.sp) }
                Text(str(R.string.sec_current), color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        SectionTitle(str(R.string.sec_other_devices))
        if (others.isEmpty()) Text(str(R.string.sec_no_other_devices), color = c.muted, fontSize = 14.sp)
        else GroupCard {
            others.forEachIndexed { i, (n, s) ->
                if (i > 0) RowDivider()
                Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RideIcon(RideIcons.Phone, c.muted)
                    Column(Modifier.padding(start = 14.dp).weight(1f).padding(vertical = 12.dp)) { Text(n, color = c.fg, fontSize = 15.sp); Text(s, color = c.muted, fontSize = 12.sp) }
                    Box(
                        Modifier.heightIn(min = 48.dp).clickable(role = Role.Button) { others.removeAt(i) }.padding(horizontal = 16.dp).semantics { contentDescription = logOutValue.format(n) },
                        contentAlignment = Alignment.Center,
                    ) { Text(str(R.string.sec_log_out), color = c.danger, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun MethodRow(title: String, sub: String, selected: Boolean, onClick: () -> Unit) {
    val c = RideTheme.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).selectable(selected, role = Role.RadioButton, onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, color = c.fg, fontSize = 15.sp); Text(sub, color = c.muted, fontSize = 12.sp) }
        RadioDot(selected)
    }
}

/** Two-step verification: switch, method and recovery codes. Uses the same setting as before, so the choice carries over. */
@Composable
fun TwoStepScreen(phone: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val context = LocalContext.current
    val on = rememberBoolPref("two_factor", false)
    var method by rememberSaveable { mutableIntStateOf(0) }
    var codes by remember { mutableStateOf<List<String>?>(null) }
    val clip = LocalClipboardManager.current
    val copied = str(R.string.sec_copied)
    SubScreen(str(R.string.sec_2fa_title), onBack, modifier) {
        Intro(str(R.string.sec_2fa_sub))
        GroupCard(Modifier.padding(top = 16.dp)) { ToggleRow(RideIcons.Shield, str(R.string.priv_2fa_toggle), on.value, { on.value = it }, subtitle = str(R.string.sec_2fa_toggle_sub)) }
        SectionTitle(str(R.string.sec_method))
        GroupCard {
            MethodRow(str(R.string.sec_sms), str(R.string.sec_sms_sub, phone), method == 0) { method = 0 }; RowDivider()
            MethodRow(str(R.string.sec_auth_app), str(R.string.sec_auth_app_sub), method == 1) { method = 1 }
        }
        SectionTitle(str(R.string.sec_backup))
        GroupCard { NavRow(RideIcons.Doc, str(R.string.sec_recovery), { codes = List(8) { "%04d-%04d".format((0..9999).random(), (0..9999).random()) } }, subtitle = str(R.string.sec_recovery_sub)) }
        Spacer(Modifier.height(16.dp))
    }
    codes?.let { list ->
        AlertDialog(
            onDismissRequest = { codes = null }, containerColor = c.bg,
            title = { Text(str(R.string.sec_recovery), color = c.fg) },
            text = { Text(str(R.string.sec_recovery_text) + "\n\n" + list.joinToString("\n"), color = c.muted) },
            confirmButton = { TextButton({ clip.setText(AnnotatedString(list.joinToString("\n"))); toast(context, copied); codes = null }) { Text(str(R.string.sec_copy), color = c.accent) } },
            dismissButton = { TextButton({ codes = null }) { Text(str(R.string.sec_close), color = c.fg) } },
        )
    }
}

/** Choose what to include and request a copy of the driver's data. */
@Composable
fun DownloadDataScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val items = listOf(
        Triple(RideIcons.Person, R.string.settings_profile, R.string.sec_dl_profile_sub), Triple(RideIcons.Clock, R.string.sec_dl_rides, R.string.sec_dl_rides_sub),
        Triple(RideIcons.Wallet, R.string.sec_dl_earnings, R.string.sec_dl_earnings_sub), Triple(RideIcons.Car, R.string.sec_dl_vehicle, R.string.sec_dl_vehicle_sub),
    )
    val on = remember { mutableStateListOf(true, true, true, true) }
    var requested by rememberSaveable { mutableStateOf(false) }
    if (requested) {
        SubScreen(str(R.string.priv_download), onBack, modifier, bottom = { RideButton(str(R.string.sec_done), onBack) }) {
            InfoBanner(RideIcons.Check, str(R.string.sec_request_received), str(R.string.sec_notify_ready), Modifier.padding(top = 8.dp), Tone.Good)
        }
        return
    }
    SubScreen(str(R.string.priv_download), onBack, modifier, bottom = {
        RideButton(str(R.string.sec_request_copy), { requested = true }, enabled = on.any { it })
        Text(str(R.string.sec_notify_ready), color = c.muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
    }) {
        Intro(str(R.string.sec_dl_sub))
        SectionTitle(str(R.string.sec_include))
        GroupCard(Modifier.padding(bottom = 16.dp)) {
            items.forEachIndexed { i, (icon, t, s) -> if (i > 0) RowDivider(); ToggleRow(icon, str(t), on[i], { on[i] = it }, subtitle = str(s)) }
        }
    }
}
