package com.ridenow.app.ui.booking

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridenow.app.R
import com.ridenow.app.ui.components.RideButton
import com.ridenow.app.ui.components.RideButtonStyle
import com.ridenow.app.ui.components.RideIcon
import com.ridenow.app.ui.components.RideIcons
import com.ridenow.app.ui.map.MazoMap
import com.ridenow.app.ui.map.MapCoordinate
import com.ridenow.app.ui.map.MazoRoute
import com.ridenow.app.ui.theme.RideNowTheme
import com.ridenow.app.ui.theme.RideTheme
import com.ridenow.app.ui.util.entrance

/** Choose a vehicle. [onContinue] gets the vehicle index; [onLater] opens scheduling. */
@Composable
fun RideOptionsScreen(
    destination: String, returnTrip: Boolean, initial: Int, onBack: () -> Unit,
    onContinue: (Int) -> Unit, onLater: (Int) -> Unit, modifier: Modifier = Modifier,
    mapStart: MapCoordinate? = null, mapEnd: MapCoordinate? = null, mapRoute: MazoRoute? = null,
    mapError: String? = null,
) {
    val c = RideTheme.colors
    var selected by rememberSaveable { mutableIntStateOf(initial) }
    val v = Vehicles[selected]
    Box(modifier.fillMaxSize().background(c.bg)) {
        MazoMap(Modifier.fillMaxSize(), initialCenter = mapEnd ?: mapStart,
            start = mapStart, selected = mapEnd, route = mapRoute)

        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp).entrance(0), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(c.bg).border(1.dp, c.line, CircleShape).clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = "Back" },
                contentAlignment = Alignment.Center,
            ) { RideIcon(RideIcons.Back, c.fg, size = 22.dp) }
            Spacer(Modifier.width(10.dp))
            Row(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp)).background(c.bg).border(1.dp, c.line, RoundedCornerShape(24.dp)).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(c.accent)); Spacer(Modifier.width(8.dp))
                Text("Current location", color = c.fg, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("  →  ", color = c.muted, fontSize = 13.sp)
                Text(destination, color = c.fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().shadow(16.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), ambientColor = Color.Black, spotColor = Color.Black)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(c.bg).border(1.dp, c.line, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .navigationBarsPadding().padding(horizontal = 20.dp).padding(top = 12.dp).entrance(120, 40),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(width = 40.dp, height = 4.dp).clip(CircleShape).background(c.border))
            Text(stringResource(R.string.ride_choose), color = c.fg, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp))
            Text(mapError ?: if (mapRoute == null) "Calculating a local route…" else
                "Mazo Maps route · local road estimate",
                color = if (mapError == null) c.muted else c.danger,
                fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
            if (returnTrip) Text(stringResource(R.string.ride_return_note), color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Vehicles.forEachIndexed { i, veh -> VehicleRow(veh, returnTrip, i == selected) { selected = i } }
            }
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat(stringResource(R.string.ride_distance), mapRoute?.let { "%.1f km".format(it.distanceMeters / 1000) } ?: "—", Modifier.weight(1f))
                Stat(stringResource(R.string.ride_time), mapRoute?.let { "${(it.durationSeconds / 60).toInt()} min" } ?: "—", Modifier.weight(1f))
                Stat(stringResource(R.string.ride_seats), "${v.seats}", Modifier.weight(1f))
                Stat(stringResource(R.string.ride_luggage), "${v.luggage} bags", Modifier.weight(1f))
            }
            Row(Modifier.padding(top = 14.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { RideButton(stringResource(R.string.ride_continue), { onContinue(selected) }) }
                Box(Modifier.width(112.dp)) { RideButton(stringResource(R.string.ride_later), { onLater(selected) }, style = RideButtonStyle.Text, modifier = Modifier.border(1.dp, c.border, RoundedCornerShape(28.dp)).height(56.dp)) }
            }
        }
    }
}

@Composable
private fun VehicleRow(v: Vehicle, returnTrip: Boolean, on: Boolean, onClick: () -> Unit) {
    val c = RideTheme.colors
    Row(
        Modifier.fillMaxWidth().height(76.dp).clip(RoundedCornerShape(16.dp)).background(if (on) c.accent.copy(alpha = 0.14f) else c.field)
            .border(if (on) 1.5.dp else 1.dp, if (on) c.accent else c.border, RoundedCornerShape(16.dp)).clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp).semantics { contentDescription = "${v.name}, ${v.seats} seats, ${money(v.fareFor(returnTrip))}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VehicleArt(v.style, 76.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(v.name, color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RideIcon(RideIcons.Person, c.muted, size = 14.dp); Spacer(Modifier.width(5.dp))
                Text("${v.seats} seats · ${v.etaMin} min away", color = c.muted, fontSize = 12.sp)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(money(v.fareFor(returnTrip)), color = c.fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text("Est. fare", color = c.muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    val c = RideTheme.colors
    Column(modifier.clip(ChipShape).background(c.field).border(1.dp, c.border, ChipShape).padding(horizontal = 10.dp, vertical = 9.dp)) {
        Text(label, color = c.muted, fontSize = 11.sp, maxLines = 1)
        Text(value, color = c.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun RideOptionsDarkPreview() = RideNowTheme(darkTheme = true) { RideOptionsScreen("Helix Garden Plaza", false, 1, {}, {}, {}) }

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun RideOptionsLightPreview() = RideNowTheme(darkTheme = false) { RideOptionsScreen("Helix Garden Plaza", true, 1, {}, {}, {}) }
