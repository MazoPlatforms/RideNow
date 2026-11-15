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
import com.ridenow.driver.ui.theme.RideTheme
import com.ridenow.driver.ui.util.Money
import com.ridenow.driver.ui.util.openMaps

/** Ride details from Ride History: map, summary, stops, rider with contact buttons, earnings and a share button. */
@Composable
fun RideDetailsScreen(ride: RideRecord, sample: Boolean, onBack: () -> Unit, onReport: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val context = LocalContext.current
    SubScreen(
        str(R.string.ride_details), onBack, modifier, trailing = { RideMenu({ shareRide(context, ride, sample, "RideNow") }, onReport) },
        bottom = { SoftButton(str(R.string.ride_share_details), { shareRide(context, ride, sample, "RideNow") }, icon = RideIcons.Share) },
    ) {
        RouteMap(
            ride.pickup.substringBefore(","), ride.drop.substringBefore(","), Modifier.padding(top = 4.dp).height(150.dp),
            corner = { CircleButton(RideIcons.Navigate, str(R.string.trip_navigate), { openMaps(context, ride.drop) }, size = 40.dp) },
        )
        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Calendars.dateTime(sample, ride.ageDays, ride.startMin), color = c.muted, fontSize = 13.sp)
                Text(Money.fmt(ride.fare), color = c.fg, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
                Text("${km(ride.km)} \u2022 ${ride.durationMin} min", color = c.muted, fontSize = 13.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(ride.ref, color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                StatusFor(ride)
            }
        }
        RideStops(ride, Modifier.padding(top = 18.dp))
        HorizontalDivider(Modifier.padding(vertical = 16.dp), color = c.line)
        Text(str(R.string.rider_info), color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 10.dp))
        RiderRow(ride, extra = { CircleButton(RideIcons.Navigate, str(R.string.trip_navigate), { openMaps(context, ride.pickup) }, size = 42.dp) })
        SectionTitle(str(R.string.earn_breakdown))
        BreakdownCard(ride, Modifier.padding(bottom = 8.dp))
    }
}
