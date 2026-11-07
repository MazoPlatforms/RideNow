package com.ridenow.app.ui.settings

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
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        ScreenTop("Settings", onBack, subtitle = "Manage your account and app preferences.")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).entrance(60)) {
            GroupCard(Modifier.padding(top = 16.dp)) {
                NavRow(RideIcons.Person, "Profile", { onOpen("Profile") }, subtitle = "View and edit your profile"); RowDivider()
                NavRow(RideIcons.Card, "Payment Methods", { onOpen("Payments") }, subtitle = "Manage your payment options"); RowDivider()
                NavRow(RideIcons.Receipt, "Transaction History", { onOpen("Transaction History") }, subtitle = "Your payments and ride transactions"); RowDivider()
                NavRow(RideIcons.Pin, "Saved Places", { onOpen("Saved addresses") }, subtitle = "Manage your saved locations")
            }
            GroupCard(Modifier.padding(top = 12.dp, bottom = 16.dp)) {
                NavRow(RideIcons.Moon, "Appearance", { sheet = true }, subtitle = mode.label); RowDivider()
                NavRow(RideIcons.Globe, "Language", { onOpen("Language") }, subtitle = "English"); RowDivider()
                NavRow(RideIcons.Bell, "Notifications", { onOpen("Notification settings") }, subtitle = "Manage your notifications"); RowDivider()
                NavRow(RideIcons.Shield, "Privacy & Security", { onOpen("Privacy & Security") }, subtitle = "Account and data controls"); RowDivider()
                NavRow(RideIcons.Help, "Help & Support", { onOpen("Help & Support") }, subtitle = "Get help or contact us"); RowDivider()
                NavRow(RideIcons.Info, "About", { onOpen("Legal & About") }, subtitle = "App version, terms and policies")
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
            Text("Appearance", color = c.fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text("Choose how the app looks on your device.", color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
            GroupCard {
                ThemeMode.values().forEachIndexed { i, m ->
                    if (i > 0) RowDivider()
                    Row(Modifier.fillMaxWidth().height(56.dp).clickable(role = Role.RadioButton) { onMode(m); scope.launch { state.hide() }.invokeOnCompletion { onDismiss() } }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(m.label, color = c.fg, fontSize = 15.sp, fontWeight = if (m == mode) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
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
