package com.ridenow.driver.ui.account

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
import androidx.compose.foundation.layout.Arrangement
import com.ridenow.driver.getAppLanguage
import com.ridenow.driver.ui.more.LanguageScreen
import com.ridenow.driver.ui.theme.ThemeMode
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.activity.compose.BackHandler

/** Account tab: profile, vehicle, payout, appearance, language, help and sign out. Appearance and language really switch. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    name: String, phone: String, vehicle: String, availability: String, bankSaved: Boolean,
    mode: ThemeMode, onMode: (ThemeMode) -> Unit, onAddBank: () -> Unit, onSignOut: () -> Unit, onTab: (MainTab) -> Unit,
    onProfile: () -> Unit = {}, modifier: Modifier = Modifier,
) {
    val c = RideTheme.colors
    val context = LocalContext.current
    var appearance by rememberSaveable { mutableStateOf(false) }
    var language by rememberSaveable { mutableStateOf(false) }
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    val dash = "\u2014"
    val langCode = getAppLanguage(context)
    val langName = Locale.forLanguageTag(langCode).let { loc -> loc.getDisplayLanguage(loc).replaceFirstChar { it.titlecaseChar() } }

    if (language) {
        BackHandler { language = false }
        LanguageScreen(onBack = { language = false })
        return
    }

    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text(stringResource(R.string.acct_title), color = c.fg, fontSize = 30.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(top = 16.dp).semantics { heading() })
            Row(
                Modifier.padding(top = 16.dp).fillMaxWidth().entrance(60).clip(RoundedCornerShape(20.dp))
                    .clickable(role = Role.Button, onClick = onProfile).padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.14f)).border(1.5.dp, c.accent, CircleShape), contentAlignment = Alignment.Center) {
                    Text(name.take(1).uppercase().ifEmpty { "?" }, color = c.accent, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                }
                Column(Modifier.padding(start = 16.dp)) {
                    Text(name.ifBlank { dash }, color = c.fg, fontSize = 20.sp)
                    Text(phone, color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                    Text(
                        stringResource(R.string.app_pill), color = c.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 6.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.14f)).border(1.dp, c.accent, CircleShape).padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }
            GroupCard(Modifier.padding(top = 20.dp).entrance(120)) {
                NavRow(RideIcons.Car, stringResource(R.string.review_row_vehicle), { }, subtitle = vehicle.ifBlank { dash }); RowDivider()
                NavRow(RideIcons.Calendar, stringResource(R.string.review_row_avail), { }, subtitle = availability.ifBlank { dash }); RowDivider()
                NavRow(
                    RideIcons.Card, stringResource(R.string.review_row_bank), { if (!bankSaved) onAddBank() },
                    subtitle = stringResource(if (bankSaved) R.string.review_bank_added else R.string.acct_bank_missing),
                    trailing = if (bankSaved) null else ({ Text(stringResource(R.string.home_add), color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }),
                )
            }
            GroupCard(Modifier.padding(top = 12.dp).entrance(180)) {
                NavRow(RideIcons.Moon, stringResource(R.string.acct_appearance), { appearance = true }, subtitle = stringResource(mode.labelRes)); RowDivider()
                NavRow(RideIcons.Globe, stringResource(R.string.acct_language), { language = true }, subtitle = langName); RowDivider()
                NavRow(RideIcons.Help, stringResource(R.string.acct_help), { }, subtitle = stringResource(R.string.acct_help_sub))
            }
            GroupCard(Modifier.padding(top = 12.dp, bottom = 20.dp).entrance(240)) {
                NavRow(RideIcons.Logout, stringResource(R.string.acct_signout), { confirmSignOut = true }, danger = true, trailing = { })
            }
        }
        MainBottomBar(MainTab.Account, onTab)
    }

    if (appearance) PickerSheet(
        stringResource(R.string.acct_appearance), stringResource(R.string.acct_appearance_sub),
        ThemeMode.entries.map { stringResource(it.labelRes) }, ThemeMode.entries.indexOf(mode), { onMode(ThemeMode.entries[it]) }, { appearance = false },
    )
    if (confirmSignOut) AlertDialog(
        onDismissRequest = { confirmSignOut = false }, containerColor = c.bg,
        title = { Text(stringResource(R.string.acct_signout_title), color = c.fg) },
        text = { Text(stringResource(R.string.acct_signout_text), color = c.muted) },
        confirmButton = { TextButton(onClick = { confirmSignOut = false; onSignOut() }) { Text(stringResource(R.string.acct_signout), color = c.danger) } },
        dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.dialog_cancel), color = c.fg) } },
    )
}

/** Single-choice bottom sheet used for appearance and language. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerSheet(title: String, subtitle: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    val c = RideTheme.colors
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = c.bg, contentColor = c.fg, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Text(title, color = c.fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
            GroupCard {
                options.forEachIndexed { i, label ->
                    if (i > 0) RowDivider()
                    Row(
                        Modifier.fillMaxWidth().height(56.dp).selectable(i == selected, role = Role.RadioButton) { onSelect(i); scope.launch { state.hide() }.invokeOnCompletion { onDismiss() } }.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, color = c.fg, fontSize = 15.sp, fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
                        Box(Modifier.size(20.dp).clip(CircleShape).then(if (i == selected) Modifier.background(c.accent) else Modifier.border(1.5.dp, c.muted, CircleShape)), contentAlignment = Alignment.Center) {
                            if (i == selected) Box(Modifier.size(8.dp).clip(CircleShape).background(c.onAccent))
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun AccountDarkPreview() = RideNowTheme(darkTheme = true) {
    AccountScreen("Alex Morgan", "+1 555 012 3456", "Toyota Prius", "Full time", false, ThemeMode.System, {}, {}, {}, {})
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun AccountLightPreview() = RideNowTheme(darkTheme = false) {
    AccountScreen("Alex Morgan", "+1 555 012 3456", "Toyota Prius", "Full time", true, ThemeMode.Light, {}, {}, {}, {})
}
