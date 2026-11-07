package com.ridenow.app.ui.rides

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

private val Rides = listOf(
    RideItem("Wed, 4 Nov · 8:30 AM", "Nova Terminal, Gate 4", "Orbit · Booked", "$16.80", RideStatus.Scheduled),
    RideItem("Today", "Helix Garden Plaza", "Orbit · 8.4 km · 19 min", "$16.80", RideStatus.Completed),
    RideItem("Mon, 2 Nov", "Aurora Medical Centre", "Pulse · 5.2 km · 14 min", "$12.40", RideStatus.Completed),
    RideItem("Sat, 31 Oct", "Helix Garden Plaza", "Nova XL · Cancelled", "—", RideStatus.Cancelled),
)

/** Ride history with filters. Sample data for now. [onOpen] receives the destination. */
@Composable
fun MyRidesScreen(onBack: () -> Unit, onBook: () -> Unit, onOpen: (RideItem) -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = Rides.filter { filter == null || it.status.label == filter }
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        ScreenTop("Your Rides", onBack)
        Row(Modifier.padding(top = 14.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("All", filter == null) { filter = null }
            RideStatus.values().forEach { s -> Pill(s.label, filter == s.label) { filter = s.label } }
        }
        if (shown.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("No rides yet", color = c.fg, fontSize = 24.sp, fontWeight = FontWeight.Light)
                Text("Your trips and receipts will appear here.", color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp))
                Box(Modifier.width(220.dp)) { RideButton("Book a ride", onBook) }
            }
        } else Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).entrance(60)) {
            val upcoming = shown.filter { it.status == RideStatus.Scheduled }
            val past = shown - upcoming.toSet()
            if (upcoming.isNotEmpty()) { SectionLabel("UPCOMING"); upcoming.forEach { RideCard(it) { onOpen(it) }; Spacer(Modifier.height(8.dp)) } }
            if (past.isNotEmpty()) { SectionLabel("PAST"); past.forEach { RideCard(it) { onOpen(it) }; Spacer(Modifier.height(8.dp)) } }
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
                r.status.label, color = if (on) c.accent else c.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(50)).then(if (r.status == RideStatus.Completed) Modifier.background(c.accent.copy(alpha = 0.14f)) else Modifier.border(1.dp, if (on) c.accent else c.border, RoundedCornerShape(50))).padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun RidesPreview() = RideNowTheme(darkTheme = true) { MyRidesScreen({}, {}, {}) }
