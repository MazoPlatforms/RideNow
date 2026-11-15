package com.ridenow.driver.ui.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.ridenow.driver.R
import com.ridenow.driver.getAppLanguage
import com.ridenow.driver.ui.components.*
import com.ridenow.driver.ui.data.DriverStore
import com.ridenow.driver.ui.profile.ProfilePhoto
import com.ridenow.driver.ui.theme.RideTheme
import com.ridenow.driver.ui.theme.ThemeMode
import com.ridenow.driver.ui.util.openAppSettings
import com.ridenow.driver.ui.util.rememberStringPref
import com.ridenow.driver.ui.util.rememberBoolPref
import java.util.Locale

/** A settings row: icon, title, current value and a chevron. */
@Composable
fun ValueRow(icon: List<String>, title: String, value: String?, onClick: () -> Unit) = NavRow(
    icon, title, onClick,
    trailing = {
        if (value != null) Text(value, color = RideTheme.colors.muted, fontSize = 13.sp, modifier = Modifier.padding(end = 6.dp), maxLines = 1)
        RideIcon(RideIcons.Chevron, RideTheme.colors.muted, size = 18.dp)
    },
)

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    SectionTitle(title)
    GroupCard(content = content)
}

/** Settings: profile shortcut, app preferences, privacy, account and payments, support, about and sign out. */
@Composable
fun SettingsScreen(
    store: DriverStore, name: String, phone: String, mode: ThemeMode, onBack: () -> Unit, onProfile: () -> Unit, onAppearance: () -> Unit, onLanguage: () -> Unit,
    onNotifications: () -> Unit, onPrivacy: () -> Unit, onPaymentMethods: () -> Unit, onBank: () -> Unit, onPayouts: () -> Unit, onHelp: () -> Unit,
    onTickets: () -> Unit, onAbout: () -> Unit, onSignOut: () -> Unit, modifier: Modifier = Modifier,
) {
    val c = RideTheme.colors
    val context = LocalContext.current
    val unit = rememberStringPref("distance_unit", "km")
    val appLock = rememberBoolPref("app_lock", false)
    var unitSheet by remember { mutableStateOf(false) }
    var confirmOut by rememberSaveable { mutableStateOf(false) }
    val lang = getAppLanguage(context).let { code -> Locale.forLanguageTag(code).let { l -> l.getDisplayLanguage(l).replaceFirstChar { it.titlecaseChar() } } }
    val needLock = str(R.string.lock_need_screen_lock)

    SubScreen(str(R.string.settings_title), onBack, modifier, bottom = { SoftButton(str(R.string.acct_signout), { confirmOut = true }, icon = RideIcons.Logout, danger = true) }) {
        GroupCard(Modifier.padding(top = 4.dp)) {
            Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onProfile).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                ProfilePhoto(store, name, 56.dp)
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(name.ifBlank { str(R.string.profile_none) }, color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(phone, color = c.muted, fontSize = 13.sp)
                    Text(str(R.string.settings_view_profile), color = c.muted, fontSize = 13.sp)
                }
                RideIcon(RideIcons.Chevron, c.muted, size = 18.dp)
            }
        }
        Section(str(R.string.settings_prefs)) {
            ValueRow(RideIcons.Sun, str(R.string.acct_appearance), str(mode.labelRes), onAppearance); RowDivider()
            ValueRow(RideIcons.Globe, str(R.string.acct_language), lang, onLanguage); RowDivider()
            ValueRow(RideIcons.Navigate, str(R.string.settings_distance), str(if (unit.value == "mi") R.string.unit_mi else R.string.unit_km), { unitSheet = true }); RowDivider()
            ValueRow(RideIcons.Bell, str(R.string.settings_notifications), null, onNotifications)
        }
        Section(str(R.string.privacy_title)) {
            ValueRow(RideIcons.Shield, str(R.string.privacy_title), null, onPrivacy); RowDivider()
            NavRow(RideIcons.Lock, str(R.string.lock_title), { }, trailing = {
                RideSwitch(appLock.value, str(R.string.lock_title)) { on ->
                    if (on && !deviceHasLock(context)) toast(context, needLock) else appLock.value = on
                }
            }); RowDivider()
            ValueRow(RideIcons.Pin, str(R.string.settings_location), null) { openAppSettings(context) }
        }
        Section(str(R.string.settings_account)) {
            ValueRow(RideIcons.Wallet, str(R.string.pm_title), null, onPaymentMethods); RowDivider()
            ValueRow(RideIcons.Bank, str(R.string.bank_acc_title), null, onBank); RowDivider()
            ValueRow(RideIcons.Receipt, str(R.string.pay_title), null, onPayouts)
        }
        Section(str(R.string.settings_support)) {
            ValueRow(RideIcons.Help, str(R.string.settings_help), null, onHelp); RowDivider()
            ValueRow(RideIcons.Msg, str(R.string.tickets_title), null, onTickets)
        }
        Section(str(R.string.settings_about)) { ValueRow(RideIcons.Info, str(R.string.settings_about_ridenow), null, onAbout) }
        Spacer(Modifier.height(16.dp))
    }
    if (unitSheet) RideSheet(str(R.string.settings_distance), { unitSheet = false }) { close ->
        GroupCard {
            listOf("km" to R.string.unit_km, "mi" to R.string.unit_mi).forEachIndexed { i, (key, res) ->
                if (i > 0) RowDivider()
                Row(Modifier.fillMaxWidth().height(52.dp).selectable(unit.value == key, role = Role.RadioButton) { unit.value = key; close() }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(str(res), color = c.fg, fontSize = 15.sp, modifier = Modifier.weight(1f)); RadioDot(unit.value == key)
                }
            }
        }
    }
    if (confirmOut) AlertDialog(
        onDismissRequest = { confirmOut = false }, containerColor = c.bg,
        title = { Text(str(R.string.acct_signout_title), color = c.fg) }, text = { Text(str(R.string.acct_signout_text), color = c.muted) },
        confirmButton = { TextButton(onClick = { confirmOut = false; onSignOut() }) { Text(str(R.string.acct_signout), color = c.danger) } },
        dismissButton = { TextButton(onClick = { confirmOut = false }) { Text(str(R.string.dialog_cancel), color = c.fg) } },
    )
}

fun deviceHasLock(context: android.content.Context): Boolean =
    (context.getSystemService(android.content.Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager)?.isDeviceSecure == true
