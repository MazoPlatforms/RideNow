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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.ridenow.driver.R
import com.ridenow.driver.ui.components.*
import com.ridenow.driver.ui.data.DriverStore
import com.ridenow.driver.ui.theme.RideTheme
import com.ridenow.driver.ui.util.openAppSettings
import com.ridenow.driver.ui.util.rememberBoolPref

private class NType(val key: String, val icon: List<String>, val title: Int, val sub: Int, val default: Boolean, val more: Boolean)

private val TYPES = listOf(
    NType("ride_requests", RideIcons.Car, R.string.ns_ride_requests, R.string.ns_ride_requests_sub, true, true),
    NType("trip_updates", RideIcons.Navigate, R.string.ns_trip_updates, R.string.ns_trip_updates_sub, true, true),
    NType("payments", RideIcons.Wallet, R.string.ns_payments, R.string.ns_payments_sub, true, true),
    NType("promotions", RideIcons.Megaphone, R.string.ns_promotions, R.string.ns_promotions_sub, false, false),
    NType("reminders", RideIcons.Calendar, R.string.ns_reminders, R.string.ns_reminders_sub, true, true),
    NType("safety_alerts", RideIcons.Shield, R.string.ns_safety, R.string.ns_safety_sub, true, true),
    NType("app_updates", RideIcons.Info, R.string.ns_updates, R.string.ns_updates_sub, false, false),
)

@Composable
private fun PrefToggle(icon: List<String>, title: String, sub: String?, key: String, default: Boolean, onMore: (() -> Unit)? = null) {
    val p = rememberBoolPref(key, default)
    ToggleRow(icon, title, p.value, { p.value = it }, subtitle = sub, onMore = onMore)
}

/** Notification settings: push on/off, a switch per type with sound and vibration options, and in-app notifications. */
@Composable
fun NotificationSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val push = rememberBoolPref("push_enabled", true)
    var sheet by remember { mutableStateOf<NType?>(null) }
    SubScreen(str(R.string.ns_title), onBack, modifier) {
        InfoBanner(RideIcons.Bell, str(R.string.ns_banner_title), str(R.string.ns_banner_text), Modifier.padding(top = 4.dp), Tone.Brand)
        SectionTitle(str(R.string.ns_push))
        GroupCard { ToggleRow(RideIcons.Bell, str(R.string.ns_enable), push.value, { push.value = it }, subtitle = str(R.string.ns_enable_sub)) }
        SectionTitle(str(R.string.ns_types))
        GroupCard {
            TYPES.forEachIndexed { i, t ->
                if (i > 0) RowDivider()
                PrefToggle(t.icon, str(t.title), str(t.sub), "n_" + t.key, t.default, if (t.more) ({ sheet = t }) else null)
            }
        }
        SectionTitle(str(R.string.ns_in_app))
        GroupCard(Modifier.padding(bottom = 16.dp)) { PrefToggle(RideIcons.Msg, str(R.string.ns_show_in_app), str(R.string.ns_show_in_app_sub), "n_in_app", true) }
    }
    sheet?.let { t ->
        RideSheet(str(t.title), { sheet = null }, str(t.sub)) { close ->
            GroupCard {
                PrefToggle(RideIcons.Bell, str(R.string.ns_sound), null, "n_${t.key}_sound", true); RowDivider()
                PrefToggle(RideIcons.Phone, str(R.string.ns_vibrate), null, "n_${t.key}_vibrate", true)
            }
            Spacer(Modifier.height(14.dp)); RideButton(str(R.string.dialog_ok), { close() })
        }
    }
}

/** Privacy and security: privacy controls, app lock, sessions and data. Smaller choices open in sheets. */
@Composable
fun PrivacySecurityScreen(onBack: () -> Unit, onPersonalData: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val context = LocalContext.current
    val appLock = rememberBoolPref("app_lock", false)
    val twoFactor = rememberBoolPref("two_factor", false)
    var sheet by remember { mutableStateOf("") }
    var download by remember { mutableStateOf(false) }
    val needLock = str(R.string.lock_need_screen_lock)
    val requested = str(R.string.priv_download_done)

    @Composable fun Item(icon: List<String>, title: Int, sub: Int, onClick: () -> Unit) = NavRow(icon, str(title), onClick, subtitle = str(sub))

    SubScreen(str(R.string.privacy_title), onBack, modifier) {
        InfoBanner(RideIcons.Shield, str(R.string.priv_banner_title), str(R.string.priv_banner_text), Modifier.padding(top = 4.dp), Tone.Brand)
        SectionTitle(str(R.string.priv_controls))
        GroupCard {
            Item(RideIcons.Pin, R.string.settings_location, R.string.priv_location_sub) { openAppSettings(context) }; RowDivider()
            Item(RideIcons.Eye, R.string.priv_sharing, R.string.priv_sharing_sub) { sheet = "sharing" }; RowDivider()
            Item(RideIcons.Lock, R.string.priv_visibility, R.string.priv_visibility_sub) { sheet = "visibility" }; RowDivider()
            Item(RideIcons.Person, R.string.priv_personal, R.string.priv_personal_sub, onPersonalData)
        }
        SectionTitle(str(R.string.priv_security))
        GroupCard {
            ToggleRow(RideIcons.Lock, str(R.string.lock_title), appLock.value, { on -> if (on && !deviceHasLock(context)) toast(context, needLock) else appLock.value = on }, subtitle = str(R.string.lock_sub)); RowDivider()
            Item(RideIcons.Shield, R.string.priv_2fa, R.string.priv_2fa_sub) { sheet = "2fa" }; RowDivider()
            Item(RideIcons.Key, R.string.priv_sessions, R.string.priv_sessions_sub) { sheet = "sessions" }; RowDivider()
            Item(RideIcons.Phone, R.string.priv_trusted, R.string.priv_trusted_sub) { sheet = "trusted" }
        }
        SectionTitle(str(R.string.priv_data))
        GroupCard(Modifier.padding(bottom = 16.dp)) {
            Item(RideIcons.Download, R.string.priv_download, R.string.priv_download_sub) { download = true }; RowDivider()
            Item(RideIcons.Trash, R.string.priv_delete, R.string.priv_delete_sub, onDelete)
        }
    }
    when (sheet) {
        "sharing" -> RideSheet(str(R.string.priv_sharing), { sheet = "" }, str(R.string.priv_sharing_sub)) { close ->
            GroupCard {
                PrefToggle(RideIcons.Chart, str(R.string.priv_analytics), str(R.string.priv_analytics_sub), "p_analytics", true); RowDivider()
                PrefToggle(RideIcons.Megaphone, str(R.string.priv_offers), str(R.string.priv_offers_sub), "p_offers", false)
            }
            Spacer(Modifier.height(14.dp)); RideButton(str(R.string.dialog_ok), { close() })
        }
        "visibility" -> RideSheet(str(R.string.priv_visibility), { sheet = "" }, str(R.string.priv_visibility_sub)) { close ->
            GroupCard {
                PrefToggle(RideIcons.Star, str(R.string.priv_show_rating), str(R.string.priv_show_rating_sub), "p_show_rating", true); RowDivider()
                PrefToggle(RideIcons.Person, str(R.string.priv_show_photo), str(R.string.priv_show_photo_sub), "p_show_photo", true)
            }
            Spacer(Modifier.height(14.dp)); RideButton(str(R.string.dialog_ok), { close() })
        }
        "2fa" -> RideSheet(str(R.string.priv_2fa), { sheet = "" }, str(R.string.priv_2fa_text)) { close ->
            GroupCard { ToggleRow(RideIcons.Shield, str(R.string.priv_2fa_toggle), twoFactor.value, { twoFactor.value = it }) }
            Spacer(Modifier.height(14.dp)); RideButton(str(R.string.dialog_ok), { close() })
        }
        "sessions" -> RideSheet(str(R.string.priv_sessions), { sheet = "" }, str(R.string.priv_sessions_text)) { close ->
            GroupCard {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RideIcon(RideIcons.Phone, c.fg); Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(android.os.Build.MODEL, color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold); Text(str(R.string.priv_this_device), color = c.muted, fontSize = 12.sp) }
                    StatusChip(str(R.string.priv_active_now), Tone.Good, dot = true)
                }
            }
            Spacer(Modifier.height(14.dp)); SoftButton(str(R.string.priv_signout_others), { toast(context, requested); close() })
        }
        "trusted" -> RideSheet(str(R.string.priv_trusted), { sheet = "" }, str(R.string.priv_trusted_text)) { close ->
            GroupCard {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RideIcon(RideIcons.Phone, c.fg); Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(android.os.Build.MODEL, color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold); Text(str(R.string.priv_this_device), color = c.muted, fontSize = 12.sp) }
                    StatusChip(str(R.string.priv_trusted_chip), Tone.Info)
                }
            }
            Spacer(Modifier.height(14.dp)); RideButton(str(R.string.dialog_ok), { close() })
        }
    }
    if (download) AlertDialog(
        onDismissRequest = { download = false }, containerColor = c.bg,
        title = { Text(str(R.string.priv_download), color = c.fg) }, text = { Text(str(R.string.priv_download_text), color = c.muted) },
        confirmButton = { TextButton(onClick = { download = false; toast(context, requested) }) { Text(str(R.string.priv_download_confirm), color = c.accent) } },
        dismissButton = { TextButton(onClick = { download = false }) { Text(str(R.string.dialog_cancel), color = c.fg) } },
    )
}

/** Accepted payment methods for fares. */
@Composable
fun PaymentMethodsScreen(store: DriverStore, onBack: () -> Unit, onBank: () -> Unit, modifier: Modifier = Modifier) {
    SubScreen(str(R.string.pm_title), onBack, modifier) {
        InfoBanner(RideIcons.Wallet, str(R.string.pm_banner_title), str(R.string.pm_banner_text), Modifier.padding(top = 4.dp), Tone.Brand)
        SectionTitle(str(R.string.pm_accept))
        GroupCard {
            ToggleRow(RideIcons.Cash, str(R.string.pm_cash), store.acceptCash, { store.acceptCash = it }, subtitle = str(R.string.pm_cash_sub)); RowDivider()
            ToggleRow(RideIcons.Card, str(R.string.pm_card), store.acceptCard, { store.acceptCard = it }, subtitle = str(R.string.pm_card_sub)); RowDivider()
            ToggleRow(RideIcons.Wallet, str(R.string.pm_wallet), store.acceptWallet, { store.acceptWallet = it }, subtitle = str(R.string.pm_wallet_sub))
        }
        SectionTitle(str(R.string.pm_payouts))
        GroupCard(Modifier.padding(bottom = 16.dp)) { NavRow(RideIcons.Bank, str(R.string.bank_acc_title), onBank, subtitle = str(R.string.pm_payouts_sub)) }
    }
}

/** Delete driver account: what goes, alternatives, and a confirmation before the red button does anything. */
@Composable
fun DeleteAccountScreen(onBack: () -> Unit, onDownload: () -> Unit, onGoOffline: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    var confirm by rememberSaveable { mutableStateOf(false) }
    val items = listOf(
        Triple(RideIcons.Person, R.string.del_personal, R.string.del_personal_sub), Triple(RideIcons.Car, R.string.del_docs, R.string.del_docs_sub),
        Triple(RideIcons.Wallet, R.string.del_earnings, R.string.del_earnings_sub), Triple(RideIcons.Chart, R.string.del_ratings, R.string.del_ratings_sub),
        Triple(RideIcons.Clock, R.string.del_history, R.string.del_history_sub), Triple(RideIcons.Gear, R.string.del_settings, R.string.del_settings_sub),
    )
    SubScreen(
        str(R.string.del_title), onBack, modifier,
        bottom = {
            Box(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp)).background(Color_Red).clickable(role = androidx.compose.ui.semantics.Role.Button) { confirm = true }, contentAlignment = Alignment.Center) {
                Text(str(R.string.del_button), color = androidx.compose.ui.graphics.Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Text(str(R.string.del_irreversible), color = c.muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        },
    ) {
        InfoBanner(RideIcons.Trash, str(R.string.del_sorry), str(R.string.del_sorry_text), Modifier.padding(top = 4.dp), Tone.Danger)
        SectionTitle(str(R.string.del_what))
        GroupCard { items.forEachIndexed { i, (icon, t, s) -> if (i > 0) RowDivider(); NavRow(icon, str(t), { }, subtitle = str(s), trailing = { }) } }
        SectionTitle(str(R.string.del_before))
        GroupCard(Modifier.padding(bottom = 16.dp)) {
            NavRow(RideIcons.Download, str(R.string.priv_download), onDownload, subtitle = str(R.string.del_download_sub)); RowDivider()
            NavRow(RideIcons.Pause, str(R.string.del_offline), onGoOffline, subtitle = str(R.string.del_offline_sub))
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false }, containerColor = c.bg,
        title = { Text(str(R.string.del_confirm_title), color = c.fg) }, text = { Text(str(R.string.del_confirm_text), color = c.muted) },
        confirmButton = { TextButton(onClick = { confirm = false; onDelete() }) { Text(str(R.string.del_button), color = c.danger) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(str(R.string.dialog_cancel), color = c.fg) } },
    )
}

private val Color_Red = androidx.compose.ui.graphics.Color(0xFFE5282B)
