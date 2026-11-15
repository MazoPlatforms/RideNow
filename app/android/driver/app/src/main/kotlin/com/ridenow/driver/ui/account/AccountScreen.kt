package com.ridenow.driver.ui.account

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.ridenow.driver.R
import com.ridenow.driver.ui.components.*
import com.ridenow.driver.ui.data.DriverStore
import com.ridenow.driver.ui.home.MainTab
import com.ridenow.driver.ui.profile.ProfilePhoto
import com.ridenow.driver.ui.theme.RideTheme
import com.ridenow.driver.ui.util.entrance

/**
 * Account tab. With no side drawer this is where everything the old menu held lives: profile, vehicle, documents,
 * payouts, bank account, settings, help and safety, legal, and going offline.
 */
@Composable
fun AccountScreen(
    store: DriverStore, name: String, phone: String, online: Boolean, bankSaved: Boolean, unread: Int, onTab: (MainTab) -> Unit, onBell: () -> Unit,
    onProfile: () -> Unit, onVehicle: () -> Unit, onDocuments: () -> Unit, onPayouts: () -> Unit, onBank: () -> Unit, onSettings: () -> Unit,
    onNotificationSettings: () -> Unit, onHelpSafety: () -> Unit, onLegal: () -> Unit, onGoOffline: () -> Unit, onSignOut: () -> Unit, modifier: Modifier = Modifier,
) {
    val c = RideTheme.colors
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    TabScaffold(MainTab.Account, onTab, unread, initialsOf(name), onBell, onProfile, modifier) {
        PageTitle(str(R.string.acct_title))
        Row(
            Modifier.padding(top = 14.dp).fillMaxWidth().entrance(60).clip(RoundedCornerShape(20.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(20.dp))
                .clickable(role = Role.Button, onClick = onProfile).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProfilePhoto(store, name, 64.dp)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(name.ifBlank { "\u2014" }, color = c.fg, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(phone, color = c.muted, fontSize = 13.sp)
                if (store.rating > 0) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    RideIcon(RideIcons.Star, c.fill, size = 13.dp); Text(" ${store.rating}  " + str(R.string.rider_rides, store.totalRides), color = c.muted, fontSize = 12.sp)
                }
            }
            RideIcon(RideIcons.Chevron, c.muted, size = 18.dp)
        }
        SectionTitle(str(R.string.acct_sec_vehicle))
        GroupCard(Modifier.entrance(120)) {
            NavRow(RideIcons.Car, str(R.string.vd_title), onVehicle, subtitle = store.vehicle.model.ifBlank { str(R.string.profile_none) }); RowDivider()
            NavRow(RideIcons.IdCard, str(R.string.docs_title), onDocuments, subtitle = str(R.string.acct_docs_sub))
        }
        SectionTitle(str(R.string.acct_sec_money))
        GroupCard(Modifier.entrance(160)) {
            NavRow(RideIcons.Wallet, str(R.string.pay_title), onPayouts, subtitle = str(R.string.acct_payouts_sub)); RowDivider()
            NavRow(
                RideIcons.Bank, str(R.string.bank_acc_title), onBank,
                subtitle = str(if (bankSaved) R.string.review_bank_added else R.string.acct_bank_missing),
                trailing = if (bankSaved) null else ({ Text(str(R.string.home_add), color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }),
            )
        }
        SectionTitle(str(R.string.acct_sec_app))
        GroupCard(Modifier.entrance(200)) {
            NavRow(RideIcons.Gear, str(R.string.acct_app_settings), onSettings, subtitle = str(R.string.acct_app_settings_sub)); RowDivider()
            NavRow(RideIcons.Bell, str(R.string.settings_notifications), onNotificationSettings); RowDivider()
            NavRow(RideIcons.Shield, str(R.string.hs_title), onHelpSafety, subtitle = str(R.string.acct_help_sub)); RowDivider()
            NavRow(RideIcons.Info, str(R.string.lg_title), onLegal)
        }
        GroupCard(Modifier.padding(top = 18.dp, bottom = 20.dp).entrance(240)) {
            if (online) { NavRow(RideIcons.Power, str(R.string.acct_go_offline), onGoOffline, danger = true, trailing = { }); RowDivider() }
            NavRow(RideIcons.Logout, str(R.string.acct_signout), { confirmSignOut = true }, danger = true, trailing = { })
        }
    }
    if (confirmSignOut) AlertDialog(
        onDismissRequest = { confirmSignOut = false }, containerColor = c.bg,
        title = { Text(str(R.string.acct_signout_title), color = c.fg) }, text = { Text(str(R.string.acct_signout_text), color = c.muted) },
        confirmButton = { TextButton(onClick = { confirmSignOut = false; onSignOut() }) { Text(str(R.string.acct_signout), color = c.danger) } },
        dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text(str(R.string.dialog_cancel), color = c.fg) } },
    )
}
