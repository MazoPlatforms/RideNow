package com.ridenow.driver.ui.earnings

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
import com.ridenow.driver.ui.rides.*
import com.ridenow.driver.ui.theme.RideTheme
import com.ridenow.driver.ui.util.Money

/** Earnings details for one ride: map, total, distance and duration, breakdown, timeline and rider. */
@Composable
fun EarningsDetailsScreen(ride: RideRecord, sample: Boolean, onBack: () -> Unit, onReport: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val context = LocalContext.current
    SubScreen(str(R.string.earn_details), onBack, modifier, trailing = { RideMenu({ shareRide(context, ride, sample, "RideNow") }, onReport) }) {
        RouteMap(ride.pickup.substringBefore(","), ride.drop.substringBefore(","), Modifier.padding(top = 4.dp).height(150.dp), areaLabel = "Batticaloa", showCar = false)
        GroupCard(Modifier.padding(top = 12.dp)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(Calendars.dateTime(sample, ride.ageDays, ride.startMin), color = c.muted, fontSize = 13.sp)
                    Text(Money.fmt(ride.earned), color = c.fg, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                    Text(str(R.string.earn_total_earnings), color = c.muted, fontSize = 13.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(ride.ref, color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp))
                    StatusFor(ride)
                }
            }
        }
        GroupCard(Modifier.padding(top = 12.dp)) {
            Row(Modifier.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Stat(RideIcons.Route, km(ride.km), str(R.string.earn_distance), Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(36.dp).background(c.line))
                Stat(RideIcons.Clock, "${ride.durationMin} min", str(R.string.earn_duration), Modifier.weight(1f))
            }
        }
        SectionTitle(str(R.string.earn_breakdown))
        BreakdownCard(ride)
        SectionTitle(str(R.string.ride_details))
        GroupCard { Box(Modifier.padding(16.dp)) { RideStops(ride, subtitles = true) } }
        SectionTitle(str(R.string.rider_title))
        RiderRow(ride, Modifier.padding(bottom = 16.dp), showRating = false)
    }
}

@Composable
private fun Stat(icon: List<String>, value: String, label: String, modifier: Modifier) {
    val c = RideTheme.colors
    Row(modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(c.line), contentAlignment = Alignment.Center) { RideIcon(icon, c.fg) }
        Column(Modifier.padding(start = 10.dp)) { Text(value, color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold); Text(label, color = c.muted, fontSize = 12.sp) }
    }
}
