package com.ridenow.app.ui.rides

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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import com.ridenow.app.ui.components.RideButton
import com.ridenow.app.ui.components.ScreenTop
import com.ridenow.app.ui.components.SectionLabel
import com.ridenow.app.ui.theme.RideNowTheme
import com.ridenow.app.ui.theme.RideTheme
import com.ridenow.app.ui.util.entrance

enum class RideStatus(val label: String) { Completed("Completed"), Scheduled("Scheduled"), Cancelled("Cancelled") }
data class RideItem(val date: String, val destination: String, val meta: String, val fare: String, val status: RideStatus)



/** Ride history with filters. Sample data for now. [onOpen] receives the destination. */
@Composable
fun MyRidesScreen(onBack: () -> Unit, onBook: () -> Unit, onOpen: (RideItem) -> Unit, modifier: Modifier = Modifier) {
    val textWed4Nov830Am = stringResource(R.string.text_wed_4_nov_8_30_am)
    val textNovaTerminalGate4 = stringResource(R.string.text_nova_terminal_gate_4)
    val textOrbitBooked = stringResource(R.string.text_orbit_booked)
    val textScheduleToday = stringResource(R.string.schedule_today)
    val textHelixGardenPlaza = stringResource(R.string.text_helix_garden_plaza)
    val textOrbit84Km19Min = stringResource(R.string.text_orbit_8_4_km_19_min)
    val textMon2Nov = stringResource(R.string.text_mon_2_nov)
    val textAuroraMedicalCentre = stringResource(R.string.text_aurora_medical_centre)
    val textPulse52Km14Min = stringResource(R.string.text_pulse_5_2_km_14_min)
    val textSat31Oct = stringResource(R.string.text_sat_31_oct)
    val textNovaXlCancelled = stringResource(R.string.text_nova_xl_cancelled)
    val textMenuRides = stringResource(R.string.menu_rides)
    val textAll = stringResource(R.string.text_all)
    val textNoRidesYet = stringResource(R.string.text_no_rides_yet)
    val textYourTripsAndReceiptsWillAppearHere = stringResource(R.string.text_your_trips_and_receipts_will_appear_here)
    val textBookARide = stringResource(R.string.text_book_a_ride)
    val textUpcoming = stringResource(R.string.text_upcoming)
    val textPast = stringResource(R.string.text_past)

    val Rides = listOf(
        RideItem(textWed4Nov830Am, textNovaTerminalGate4, textOrbitBooked, "$16.80", RideStatus.Scheduled),
        RideItem(textScheduleToday, textHelixGardenPlaza, textOrbit84Km19Min, "$16.80", RideStatus.Completed),
        RideItem(textMon2Nov, textAuroraMedicalCentre, textPulse52Km14Min, "$12.40", RideStatus.Completed),
        RideItem(textSat31Oct, textHelixGardenPlaza, textNovaXlCancelled, "—", RideStatus.Cancelled),
    )

    val c = RideTheme.colors
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = Rides.filter { filter == null || it.status.label == filter }
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        ScreenTop(textMenuRides, onBack)
        Row(Modifier.padding(top = 14.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(textAll, filter == null) { filter = null }
            RideStatus.values().forEach { s -> Pill(localizedLabel(s.label), filter == s.label) { filter = s.label } }
        }
        if (shown.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(textNoRidesYet, color = c.fg, fontSize = 24.sp, fontWeight = FontWeight.Light)
                Text(textYourTripsAndReceiptsWillAppearHere, color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp))
                Box(Modifier.width(220.dp)) { RideButton(textBookARide, onBook) }
            }
        } else Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).entrance(60)) {
            val upcoming = shown.filter { it.status == RideStatus.Scheduled }
            val past = shown - upcoming.toSet()
            if (upcoming.isNotEmpty()) { SectionLabel(textUpcoming); upcoming.forEach { RideCard(it) { onOpen(it) }; Spacer(Modifier.height(8.dp)) } }
            if (past.isNotEmpty()) { SectionLabel(textPast); past.forEach { RideCard(it) { onOpen(it) }; Spacer(Modifier.height(8.dp)) } }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun Pill(text: String, on: Boolean, onClick: () -> Unit) {
    val c = RideTheme.colors
    Box(
        Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(if (on) c.accent else androidx.compose.ui.graphics.Color.Transparent)
            .border(1.dp, if (on) c.accent else c.border, RoundedCornerShape(20.dp)).clickable(role = Role.Tab, onClick = onClick).padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (on) c.onAccent else c.fg, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal) }
}

@Composable
private fun RideCard(r: RideItem, onClick: () -> Unit) {
    val c = RideTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(20.dp)).clickable(role = Role.Button, onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp))) {
            drawRect(c.bg)
            listOf(0.3f, 0.7f).forEach { f -> drawLine(c.street, Offset(0f, size.height * f), Offset(size.width, size.height * f), 3.dp.toPx()); drawLine(c.street, Offset(size.width * f, 0f), Offset(size.width * f, size.height), 3.dp.toPx()) }
            val p = Path().apply { moveTo(size.width * 0.2f, size.height * 0.8f); cubicTo(size.width * 0.35f, size.height * 0.5f, size.width * 0.55f, size.height * 0.7f, size.width * 0.8f, size.height * 0.22f) }
            drawPath(p, c.accent, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            drawCircle(c.accent, 3.5.dp.toPx(), Offset(size.width * 0.2f, size.height * 0.8f)); drawCircle(c.fg, 3.5.dp.toPx(), Offset(size.width * 0.8f, size.height * 0.22f))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(r.date, color = c.muted, fontSize = 12.sp)
            Text(r.destination, color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(r.meta, color = c.muted, fontSize = 12.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(r.fare, color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            val on = r.status != RideStatus.Cancelled
            Text(
                localizedLabel(r.status.label), color = if (on) c.accent else c.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(50)).then(if (r.status == RideStatus.Completed) Modifier.background(c.accent.copy(alpha = 0.14f)) else Modifier.border(1.dp, if (on) c.accent else c.border, RoundedCornerShape(50))).padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun RidesPreview() = RideNowTheme(darkTheme = true) { MyRidesScreen({}, {}, {}) }
