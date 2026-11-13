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
import java.text.NumberFormat
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.graphics.drawscope.Stroke

/** After the trip: collect the cash fare, rate the rider, and record the trip. */
@Composable
fun TripCompleteScreen(trip: TripRequest, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    var stars by rememberSaveable { mutableIntStateOf(5) }
    val money = remember(trip.fare) { NumberFormat.getCurrencyInstance().format(trip.fare) }
    val first = trip.rider.substringBefore(" ")
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
            Text(stringResource(R.string.done_rate, first), color = c.fg, fontSize = 15.sp, modifier = Modifier.padding(top = 28.dp))
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 1..5) {
                    val on = i <= stars
                    val label = stringResource(R.string.done_star, i)
                    Box(Modifier.size(52.dp).selectable(i == stars, role = androidx.compose.ui.semantics.Role.RadioButton) { stars = i }.semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
                        Text("\u2605", color = if (on) c.fill else c.line, fontSize = 34.sp)
                    }
                }
            }
        }
        RideButton(stringResource(R.string.done_button), onDone, modifier = Modifier.padding(vertical = 12.dp))
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun TripCompletePreview() = RideNowTheme(darkTheme = false) { TripCompleteScreen(SampleTrips.all[0], {}) }
