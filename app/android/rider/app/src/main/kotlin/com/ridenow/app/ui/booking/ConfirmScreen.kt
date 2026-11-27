package com.ridenow.app.ui.booking

import com.ridenow.app.ui.components.offsetByStartDirection

import com.ridenow.app.ui.map.LatLng
import com.ridenow.app.ui.map.RouteInfo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridenow.app.R
import com.ridenow.app.ui.rides.rideFare
import com.ridenow.app.ui.components.RideButton
import com.ridenow.app.ui.components.RideIcon
import com.ridenow.app.ui.components.RideIcons
import com.ridenow.app.ui.theme.BrandYellow
import com.ridenow.app.ui.theme.RideNowTheme
import com.ridenow.app.ui.theme.RideTheme
import com.ridenow.app.ui.util.entrance

/** Final read-only review with fare breakdown. Pass [whenLabel] for scheduled rides. */
@Composable
fun ConfirmScreen(
    destination: String, vehicleIndex: Int, returnTrip: Boolean, whenLabel: String?, onBack: () -> Unit, onBook: () -> Unit,
    route: RouteInfo?, from: LatLng, to: LatLng?, modifier: Modifier = Modifier,
    quotedMinor: Long? = null, busy: Boolean = false, error: Int? = null, onRefreshQuote: () -> Unit = {},
    pickupLabel: String? = null, repeatedRideCount: Int = 1, reminderMinutes: Int = 0,
) {
    val chipText = route?.let { stringResource(R.string.text_value_km_value_min).format(it.km, it.minutes) }
    val textValueValueSeats = stringResource(R.string.text_value_value_seats)
    val c = RideTheme.colors
    val backLabel = stringResource(R.string.search_back)
    val v = Vehicles[vehicleIndex]

    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().height(48.dp).entrance(0), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.offsetByStartDirection((-12).dp).size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = backLabel }, contentAlignment = Alignment.Center) {
                RideIcon(RideIcons.Back, c.fg, size = 22.dp)
            }
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.confirm_title), color = c.fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.weight(1f)); Spacer(Modifier.width(36.dp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).entrance(80), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(130.dp).clip(RoundedCornerShape(20.dp)).border(1.dp, c.line, RoundedCornerShape(20.dp))) {
                RouteMap(Modifier.fillMaxSize(), from, to, route, chipText, topPadding = 0.dp)
            }
            Card {
                Row2(stringResource(R.string.confirm_pickup), pickupLabel ?: stringResource(R.string.search_current), dot = Dot.Pickup)
                Divider(); Row2(stringResource(R.string.confirm_drop), destination, dot = Dot.Drop)
                Divider(); Row2(stringResource(R.string.confirm_when), whenLabel ?: stringResource(R.string.live_now), dot = Dot.Clock)
                Divider()
                Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.confirm_vehicle), color = c.muted, fontSize = 11.sp, letterSpacing = 1.1.sp)
                        Text(textValueValueSeats.format(v.name, v.seats), color = c.fg, fontSize = 15.sp)
                    }
                    VehicleArt(v.style, 64.dp)
                }
            }
            Text(stringResource(R.string.confirm_fare), color = c.muted, fontSize = 11.sp, letterSpacing = 1.1.sp, modifier = Modifier.padding(top = 4.dp))
            Card {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.confirm_total), color = c.fg, modifier = Modifier.weight(1f))
                        Text(quotedMinor?.let(::rideFare) ?: "—", color = c.fg, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (repeatedRideCount > 1) Text(stringResource(R.string.schedule_repeat_summary, repeatedRideCount), color = c.muted, fontSize = 13.sp)
            if (reminderMinutes > 0 && whenLabel != null) Text(stringResource(R.string.schedule_reminder_summary, reminderMinutes), color = c.muted, fontSize = 13.sp)
            if (returnTrip) Text(stringResource(R.string.ride_return_note), color = c.muted)
            error?.let { Text(stringResource(it), color = c.fg) }
        }
        if (quotedMinor == null && !busy && error != null) {
            RideButton(stringResource(R.string.text_try_again), onRefreshQuote)
        } else {
            val label = if (whenLabel == null) R.string.live_book else R.string.live_book_scheduled
            RideButton(stringResource(label), onBook, enabled = quotedMinor != null && !busy, loading = busy,
                modifier = Modifier.padding(vertical = 12.dp).entrance(200))
        }
    }
}

private val Offset0 = androidx.compose.ui.geometry.Offset(0.14f, 0.72f)
private val Offset1 = androidx.compose.ui.geometry.Offset(0.84f, 0.45f)
private enum class Dot { Pickup, Drop, Clock }

@Composable
private fun Card(content: @Composable () -> Unit) {
    val c = RideTheme.colors
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(20.dp))) { content() }
}

@Composable
private fun Divider() = HorizontalDivider(color = RideTheme.colors.line)

@Composable
private fun Row2(label: String, value: String, dot: Dot) {
    val c = RideTheme.colors
    Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(18.dp), contentAlignment = Alignment.CenterStart) {
            when (dot) {
                Dot.Pickup -> Box(Modifier.size(10.dp).clip(CircleShape).background(BrandYellow))
                Dot.Drop -> Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(c.fg))
                Dot.Clock -> RideIcon(RideIcons.Clock, c.accent, size = 16.dp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, color = c.muted, fontSize = 11.sp, letterSpacing = 1.1.sp)
            Text(value, color = c.fg, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ConfirmDarkPreview() = RideNowTheme(darkTheme = true) { ConfirmScreen(stringResource(R.string.text_helix_garden_plaza), 1, false, null, {}, {}, null, LatLng(0.0, 0.0), null) }

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ConfirmLightPreview() = RideNowTheme(darkTheme = false) { ConfirmScreen(stringResource(R.string.text_helix_garden_plaza), 1, false, stringResource(R.string.text_wed_4_nov_8_30_am), {}, {}, null, LatLng(0.0, 0.0), null) }
