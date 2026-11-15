package com.ridenow.driver.ui.trip

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import com.ridenow.driver.R
import com.ridenow.driver.ui.components.*
import com.ridenow.driver.ui.theme.RideNowTheme
import com.ridenow.driver.ui.theme.RideTheme
import com.ridenow.driver.ui.util.entrance

/** After the trip: collect the cash fare and record the trip. */
@Composable
fun TripCompleteScreen(trip: TripRequest, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val money = remember(trip.fare) { com.ridenow.driver.ui.util.Money.fmt(trip.fare) }
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.padding(top = 40.dp).entrance(0)) { Orb(88.dp) { RideIcon(RideIcons.Check, c.onFill, size = 40.dp) } }
            Text(stringResource(R.string.done_title), color = c.fg, fontSize = 30.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(top = 20.dp).semantics { heading() })
            Text(stringResource(R.string.done_collect), color = c.muted, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Column(
                Modifier.padding(top = 24.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.accent.copy(alpha = 0.10f)).border(1.dp, c.accent, RoundedCornerShape(20.dp)).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.done_fare_label), color = c.muted, fontSize = 13.sp)
                Text(money, color = c.fg, fontSize = 44.sp, fontWeight = FontWeight.Light)
            }
        }
        RideButton(stringResource(R.string.done_button), onDone, modifier = Modifier.padding(vertical = 12.dp))
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun TripCompletePreview() = RideNowTheme(darkTheme = false) { TripCompleteScreen(SampleTrips.all[0], {}) }
