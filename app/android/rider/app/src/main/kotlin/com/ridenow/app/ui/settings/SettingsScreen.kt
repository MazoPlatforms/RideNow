package com.ridenow.app.ui.settings

import com.ridenow.app.getAppLanguage

import java.util.Locale
import androidx.compose.ui.platform.LocalContext
import com.ridenow.app.R
import androidx.compose.ui.res.stringResource
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
import androidx.compose.ui.tooling.preview.Preview
import com.ridenow.app.ui.components.GroupCard
import com.ridenow.app.ui.components.NavRow
import com.ridenow.app.ui.components.RideIcons
import com.ridenow.app.ui.components.RowDivider
import com.ridenow.app.ui.components.RideSwitch
import com.ridenow.app.ui.components.ScreenTop
import com.ridenow.app.ui.theme.RideNowTheme
import com.ridenow.app.ui.theme.RideTheme
import com.ridenow.app.ui.theme.ThemeMode
import com.ridenow.app.ui.util.entrance
import com.ridenow.app.ui.util.rememberBoolPref
import com.ridenow.app.ui.util.rememberStringPref
import android.widget.Toast
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import kotlinx.coroutines.launch

/** Settings list. Appearance really switches the app theme. */
@Composable
fun SettingsScreen(
    mode: ThemeMode, onMode: (ThemeMode) -> Unit, onBack: () -> Unit, onOpen: (String) -> Unit, modifier: Modifier = Modifier,
) {
    val c = RideTheme.colors
    val context = LocalContext.current
    val unit = rememberStringPref("distance_unit", "km")
    val appLock = rememberBoolPref("app_lock", false)
    var unitSheet by remember { mutableStateOf(false) }
    val needLock = stringResource(R.string.lock_need_screen_lock)
    val langCode = getAppLanguage(context)
    val nativeLanguageName = Locale.forLanguageTag(langCode).let { loc -> loc.getDisplayLanguage(loc).replaceFirstChar { it.titlecaseChar() } }
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        ScreenTop(stringResource(R.string.settings_title), onBack, subtitle = stringResource(R.string.settings_subtitle))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).entrance(60)) {
            GroupCard(Modifier.padding(top = 16.dp)) {
                NavRow(RideIcons.Person, stringResource(R.string.settings_profile), { onOpen("Profile") }, subtitle = stringResource(R.string.settings_profile_sub)); RowDivider()
                NavRow(RideIcons.Card, stringResource(R.string.settings_payments), { onOpen("Payments") }, subtitle = stringResource(R.string.settings_payments_sub)); RowDivider()
                NavRow(RideIcons.Receipt, stringResource(R.string.settings_transactions), { onOpen("Transaction History") }, subtitle = stringResource(R.string.settings_transactions_sub)); RowDivider()
                NavRow(RideIcons.Pin, stringResource(R.string.menu_saved), { onOpen("Saved addresses") }, subtitle = stringResource(R.string.settings_saved_sub))
            }
            GroupCard(Modifier.padding(top = 12.dp, bottom = 16.dp)) {
                NavRow(RideIcons.Moon, stringResource(R.string.settings_appearance), { onOpen("Appearance") }, subtitle = stringResource(mode.labelRes)); RowDivider()
                NavRow(RideIcons.Globe, stringResource(R.string.settings_language), { onOpen("Language") }, subtitle = nativeLanguageName); RowDivider()
                NavRow(RideIcons.Map, stringResource(R.string.settings_distance), { unitSheet = true }, subtitle = stringResource(if (unit.value == "mi") R.string.unit_mi else R.string.unit_km)); RowDivider()
                NavRow(RideIcons.Bell, stringResource(R.string.menu_notifications), { onOpen("Notification settings") }, subtitle = stringResource(R.string.settings_notifications_sub)); RowDivider()
                NavRow(RideIcons.Shield, stringResource(R.string.settings_privacy), { onOpen("Privacy & Security") }, subtitle = stringResource(R.string.settings_privacy_sub)); RowDivider()
                NavRow(RideIcons.Lock, stringResource(R.string.lock_title), { }, subtitle = stringResource(R.string.lock_sub), trailing = {
                    RideSwitch(appLock.value, stringResource(R.string.lock_title)) { on ->
                        if (on && !deviceHasLock(context)) Toast.makeText(context, needLock, Toast.LENGTH_SHORT).show() else appLock.value = on
                    }
                }); RowDivider()
                NavRow(RideIcons.Help, stringResource(R.string.menu_help), { onOpen("Help & Support") }, subtitle = stringResource(R.string.settings_help_sub)); RowDivider()
                NavRow(RideIcons.Message, stringResource(R.string.settings_tickets), { onOpen("Support tickets") }, subtitle = stringResource(R.string.settings_tickets_sub)); RowDivider()
                NavRow(RideIcons.Info, stringResource(R.string.settings_about), { onOpen("Legal & About") }, subtitle = stringResource(R.string.settings_about_sub))
            }
        }
    }
    if (unitSheet) AppearanceSheet(stringResource(R.string.settings_distance), stringResource(R.string.settings_distance_sub), { unitSheet = false }) { close ->
        GroupCard {
            listOf("km" to R.string.unit_km, "mi" to R.string.unit_mi).forEachIndexed { i, (key, res) ->
                if (i > 0) RowDivider()
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).selectable(unit.value == key, role = Role.RadioButton) { unit.value = key; close() }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(res), color = c.fg, fontSize = 15.sp, modifier = Modifier.weight(1f)); RadioDot(unit.value == key)
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SettingsPreview() = RideNowTheme(darkTheme = true) { SettingsScreen(ThemeMode.System, {}, {}, {}) }
