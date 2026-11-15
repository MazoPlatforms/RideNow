package com.ridenow.driver.ui.rides

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.ridenow.driver.R
import com.ridenow.driver.ui.components.*
import com.ridenow.driver.ui.data.Calendars
import com.ridenow.driver.ui.data.RideRecord
import com.ridenow.driver.ui.data.RideStatus
import com.ridenow.driver.ui.theme.RideTheme
import com.ridenow.driver.ui.util.Money
import com.ridenow.driver.ui.util.openDial
import com.ridenow.driver.ui.util.openSms
import com.ridenow.driver.ui.util.shareText

fun km(v: Double) = String.format(java.util.Locale.US, "%.1f km", v)

@Composable
fun StatusFor(r: RideRecord, modifier: Modifier = Modifier) {
    if (r.status == RideStatus.Completed) StatusChip(str(R.string.ride_completed), Tone.Good, modifier, dot = false)
    else StatusChip(str(R.string.ride_cancelled), Tone.Danger, modifier)
}

/** Two-line pickup and drop block with route dots. */
@Composable
fun RideStops(r: RideRecord, modifier: Modifier = Modifier, withTimes: Boolean = true, subtitles: Boolean = true) {
    val c = RideTheme.colors
    Row(modifier.fillMaxWidth()) {
        RouteDots(if (subtitles) 96.dp else 70.dp, Modifier.padding(top = 4.dp, bottom = 4.dp, end = 12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.SpaceBetween) {
            StopLine(r.pickup, r.pickupSub.takeIf { subtitles }, if (withTimes) Calendars.timeLabel(r.startMin) else null)
            Spacer(Modifier.height(18.dp))
            StopLine(r.drop, r.dropSub.takeIf { subtitles }, if (withTimes) Calendars.timeLabel(r.endMin) else null)
        }
    }
}

@Composable
private fun StopLine(title: String, sub: String?, time: String?) {
    val c = RideTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
            if (sub != null) Text(sub, color = c.muted, fontSize = 12.sp, maxLines = 1)
        }
        if (time != null) Text(time, color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

/** Base fare, distance, surge, promo and the total. */
@Composable
fun BreakdownCard(r: RideRecord, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    GroupCard(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Line(str(R.string.brk_base), Money.fmt(r.base))
            Line(str(R.string.brk_distance, km(r.km), Money.fmt(RideRecord.PER_KM)), Money.fmt(r.distanceFare))
            Line(str(R.string.brk_surge), Money.fmt(r.surge))
            Line(str(R.string.brk_promo), "- " + Money.fmt(r.promo))
            HorizontalDivider(color = c.line)
            Row {
                Text(str(R.string.brk_total), color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(Money.fmt(r.earned), color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            if (r.status == RideStatus.Cancelled) Text(str(R.string.brk_cancelled_note), color = c.muted, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    val c = RideTheme.colors
    Row { Text(label, color = c.muted, fontSize = 13.sp, modifier = Modifier.weight(1f)); Text(value, color = c.muted, fontSize = 13.sp) }
}

/** Rider avatar, name, rating and call / message buttons. */
@Composable
fun RiderRow(r: RideRecord, modifier: Modifier = Modifier, showRating: Boolean = true, extra: (@Composable RowScope.() -> Unit)? = null) {
    val c = RideTheme.colors
    val context = LocalContext.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Avatar(initialsOf(r.rider), 48.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(r.rider, color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (showRating) Row(verticalAlignment = Alignment.CenterVertically) {
                RideIcon(RideIcons.Star, c.fill, size = 13.dp)
                Text(" ${r.riderRating}  " + str(R.string.rider_rides, r.riderRides), color = c.muted, fontSize = 12.sp)
            }
        }
        CircleButton(RideIcons.Call, str(R.string.trip_call), { openDial(context, r.riderPhone) }, size = 42.dp)
        Spacer(Modifier.width(8.dp))
        CircleButton(RideIcons.Message, str(R.string.trip_message), { openSms(context, r.riderPhone) }, size = 42.dp)
        if (extra != null) { Spacer(Modifier.width(8.dp)); extra() }
    }
}

fun shareRide(context: android.content.Context, r: RideRecord, sample: Boolean, title: String) {
    val text = "RideNow ${r.ref}\n${Calendars.dateTime(sample, r.ageDays, r.startMin)}\n${r.pickup} \u2192 ${r.drop}\n${km(r.km)} \u2022 ${r.durationMin} min\n${Money.fmt(r.earned)}"
    shareText(context, title, text)
}

/** Three-dot menu with Share and Report an issue. */
@Composable
fun RideMenu(onShare: () -> Unit, onReport: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val c = RideTheme.colors
    Box {
        CircleButton(RideIcons.More, str(R.string.menu_more), { open = true }, filled = false)
        DropdownMenu(open, { open = false }, containerColor = c.field) {
            DropdownMenuItem(text = { Text(str(R.string.ride_share), color = c.fg) }, onClick = { open = false; onShare() })
            DropdownMenuItem(text = { Text(str(R.string.report_title), color = c.fg) }, onClick = { open = false; onReport() })
        }
    }
}
