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
import com.ridenow.app.ui.components.ScreenTop
import com.ridenow.app.ui.theme.RideNowTheme
import com.ridenow.app.ui.theme.RideTheme
import com.ridenow.app.ui.theme.ThemeMode
import com.ridenow.app.ui.util.entrance
import kotlinx.coroutines.launch

/** Settings list. Appearance really switches the app theme. */
@Composable
fun SettingsScreen(
    mode: ThemeMode, onMode: (ThemeMode) -> Unit, onBack: () -> Unit, onOpen: (String) -> Unit, modifier: Modifier = Modifier,
) {
    val c = RideTheme.colors
    var sheet by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
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
                NavRow(RideIcons.Moon, stringResource(R.string.settings_appearance), { sheet = true }, subtitle = stringResource(mode.labelRes)); RowDivider()
                NavRow(RideIcons.Globe, stringResource(R.string.settings_language), { onOpen("Language") }, subtitle = nativeLanguageName); RowDivider()
                NavRow(RideIcons.Bell, stringResource(R.string.menu_notifications), { onOpen("Notification settings") }, subtitle = stringResource(R.string.settings_notifications_sub)); RowDivider()
                NavRow(RideIcons.Shield, stringResource(R.string.settings_privacy), { onOpen("Privacy & Security") }, subtitle = stringResource(R.string.settings_privacy_sub)); RowDivider()
                NavRow(RideIcons.Help, stringResource(R.string.menu_help), { onOpen("Help & Support") }, subtitle = stringResource(R.string.settings_help_sub)); RowDivider()
                NavRow(RideIcons.Info, stringResource(R.string.settings_about), { onOpen("Legal & About") }, subtitle = stringResource(R.string.settings_about_sub))
            }
        }
    }
    if (sheet) AppearanceSheet(mode, onMode) { sheet = false }
}

/** Choose System default, Light or Dark. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSheet(mode: ThemeMode, onMode: (ThemeMode) -> Unit, onDismiss: () -> Unit) {
    val c = RideTheme.colors
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = c.bg, contentColor = c.fg, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Text(stringResource(R.string.settings_appearance), color = c.fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.settings_appearance_sub), color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
            GroupCard {
                ThemeMode.values().forEachIndexed { i, m ->
                    if (i > 0) RowDivider()
                    Row(Modifier.fillMaxWidth().height(56.dp).clickable(role = Role.RadioButton) { onMode(m); scope.launch { state.hide() }.invokeOnCompletion { onDismiss() } }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(m.labelRes), color = c.fg, fontSize = 15.sp, fontWeight = if (m == mode) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
                        Box(Modifier.size(20.dp).clip(CircleShape).then(if (m == mode) Modifier.background(c.accent) else Modifier.border(1.5.dp, c.muted, CircleShape)), contentAlignment = Alignment.Center) {
                            if (m == mode) Box(Modifier.size(8.dp).clip(CircleShape).background(c.onAccent))
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SettingsPreview() = RideNowTheme(darkTheme = true) { SettingsScreen(ThemeMode.System, {}, {}, {}) }
