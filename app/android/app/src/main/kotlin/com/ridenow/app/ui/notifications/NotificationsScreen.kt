package com.ridenow.app.ui.notifications

import com.ridenow.app.ui.components.localizedLabel

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
import com.ridenow.app.ui.components.RideIcon
import com.ridenow.app.ui.components.RideIcons
import com.ridenow.app.ui.components.SectionLabel
import com.ridenow.app.ui.theme.RideNowTheme
import com.ridenow.app.ui.theme.RideTheme
import com.ridenow.app.ui.util.entrance

private enum class Kind(val label: String) { Rides("Rides"), Promotions("Promotions"), Updates("Updates"), Safety("Safety"), Account("Account") }
private class Notice(val id: Int, val kind: Kind, val icon: List<String>, val title: String, val body: String, val time: String, val today: Boolean)



/** Notification list with filter pills. Sample data; tapping marks an item as read. */
@Composable
fun NotificationsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val textTripArrivingTitle = stringResource(R.string.trip_arriving_title)
    val textMiraIs1MinAwayInOrbitRn2100 = stringResource(R.string.text_mira_is_1_min_away_in_orbit_rn_2100)
    val textNow = stringResource(R.string.text_now)
    val textTripCompleted = stringResource(R.string.text_trip_completed)
    val textYour1680ReceiptIsReadyToDownload = stringResource(R.string.text_your_16_80_receipt_is_ready_to_download)
    val text2h = stringResource(R.string.text_text_2h)
    val text20OffYourNext3Rides = stringResource(R.string.text_text_20_off_your_next_3_rides)
    val textUseCodeNova20BeforeSunday = stringResource(R.string.text_use_code_nova20_before_sunday)
    val text5h = stringResource(R.string.text_text_5h)
    val textNewRideLater = stringResource(R.string.text_new_ride_later)
    val textPlanYourPickupAheadWithTheLaterButton = stringResource(R.string.text_plan_your_pickup_ahead_with_the_later_button)
    val textYesterday = stringResource(R.string.text_yesterday)
    val textAddYourWorkAddress = stringResource(R.string.text_add_your_work_address)
    val textSaveItOnceForOneTapRides = stringResource(R.string.text_save_it_once_for_one_tap_rides)
    val textMon = stringResource(R.string.text_mon)
    val textAddATrustedContact = stringResource(R.string.text_add_a_trusted_contact)
    val textLetSomeoneFollowYourTripsInRealTime = stringResource(R.string.text_let_someone_follow_your_trips_in_real_time)
    val textSun = stringResource(R.string.text_sun)
    val textSearchBack = stringResource(R.string.search_back)
    val textMenuNotifications = stringResource(R.string.menu_notifications)
    val textMarkAllRead = stringResource(R.string.text_mark_all_read)
    val textAll = stringResource(R.string.text_all)
    val textYouAreAllCaughtUp = stringResource(R.string.text_you_are_all_caught_up)
    val textNothingNewRightNow = stringResource(R.string.text_nothing_new_right_now)
    val textToday = stringResource(R.string.text_today)
    val textEarlier = stringResource(R.string.text_earlier)

    val All = listOf(
        Notice(1, Kind.Rides, RideIcons.Car, textTripArrivingTitle, textMiraIs1MinAwayInOrbitRn2100, textNow, true),
        Notice(2, Kind.Rides, RideIcons.Receipt, textTripCompleted, textYour1680ReceiptIsReadyToDownload, text2h, true),
        Notice(3, Kind.Promotions, RideIcons.Tag, text20OffYourNext3Rides, textUseCodeNova20BeforeSunday, text5h, true),
        Notice(4, Kind.Updates, RideIcons.Clock, textNewRideLater, textPlanYourPickupAheadWithTheLaterButton, textYesterday, false),
        Notice(5, Kind.Account, RideIcons.Person, textAddYourWorkAddress, textSaveItOnceForOneTapRides, textMon, false),
        Notice(6, Kind.Safety, RideIcons.Shield, textAddATrustedContact, textLetSomeoneFollowYourTripsInRealTime, textSun, false),
    )

    val c = RideTheme.colors
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var unread by remember { mutableStateOf(setOf(1, 2, 3).minus(2)) }
    val shown = All.filter { filter == null || it.kind.label == filter }
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.offset(x = (-12).dp).size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = textSearchBack }, contentAlignment = Alignment.Center) { RideIcon(RideIcons.Back, c.fg, size = 22.dp) }
            Spacer(Modifier.weight(1f))
            Text(textMenuNotifications, color = c.fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.weight(1f))
            Box(Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, enabled = unread.isNotEmpty()) { unread = emptySet() }.padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                Text(textMarkAllRead, color = if (unread.isEmpty()) c.muted else c.accent, fontSize = 13.sp)
            }
        }
        Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(textAll, filter == null) { filter = null }
            Kind.values().forEach { k -> Pill(localizedLabel(k.label), filter == k.label) { filter = k.label } }
        }
        if (shown.isEmpty()) Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(textYouAreAllCaughtUp, color = c.fg, fontSize = 24.sp, fontWeight = FontWeight.Light)
            Text(textNothingNewRightNow, color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        } else Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).entrance(60)) {
            val today = shown.filter { it.today }; val earlier = shown.filter { !it.today }
            if (today.isNotEmpty()) { SectionLabel(textToday); today.forEach { NoticeRow(it, it.id in unread) { unread = unread - it.id } } }
            if (earlier.isNotEmpty()) { SectionLabel(textEarlier); earlier.forEach { NoticeRow(it, it.id in unread) { unread = unread - it.id } } }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Pill(text: String, on: Boolean, onClick: () -> Unit) {
    val c = RideTheme.colors
    Box(
        Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(if (on) c.accent else androidx.compose.ui.graphics.Color.Transparent).border(1.dp, if (on) c.accent else c.border, RoundedCornerShape(20.dp))
            .clickable(role = Role.Tab, onClick = onClick).padding(horizontal = 18.dp), contentAlignment = Alignment.Center,
    ) { Text(text, color = if (on) c.onAccent else c.fg, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal) }
}

@Composable
private fun NoticeRow(n: Notice, unread: Boolean, onClick: () -> Unit) {
    val textUnread = stringResource(R.string.text_unread)

    val c = RideTheme.colors
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(if (unread) c.accent.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent).border(1.dp, c.border, CircleShape), contentAlignment = Alignment.Center) { RideIcon(n.icon, c.accent) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(n.title, color = c.fg, fontSize = 15.sp, fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal)
                Text(n.body, color = c.muted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(n.time, color = c.muted, fontSize = 12.sp)
                if (unread) Box(Modifier.padding(top = 8.dp).size(8.dp).clip(CircleShape).background(c.accent).semantics { contentDescription = textUnread })
            }
        }
        HorizontalDivider(color = c.line)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun NotificationsPreview() = RideNowTheme(darkTheme = true) { NotificationsScreen({}) }
