package com.ridenow.app.ui.booking

import com.google.android.gms.maps.model.LatLng
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
) {
    val c = RideTheme.colors
    val v = Vehicles[vehicleIndex]
    val total = v.fareFor(returnTrip)
    val taxes = Math.round(total * 0.065 * 100) / 100.0
    val base = Math.round(total * 0.21 * 100) / 100.0
    val dist = Math.round(total * 0.50 * 100) / 100.0
    val time = Math.round((total - taxes - base - dist) * 100) / 100.0

    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().height(48.dp).entrance(0), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.offset(x = (-12).dp).size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = "Back" }, contentAlignment = Alignment.Center) {
                RideIcon(RideIcons.Back, c.fg, size = 22.dp)
            }
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.confirm_title), color = c.fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.weight(1f)); Spacer(Modifier.width(36.dp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).entrance(80), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(130.dp).clip(RoundedCornerShape(20.dp)).border(1.dp, c.line, RoundedCornerShape(20.dp))) {
                RouteMap(Modifier.fillMaxSize(), from, to, route, "${route?.km ?: TripKm} km · ${route?.minutes ?: TripMinutes} min", topPadding = 0.dp)
            }
            Card {
                Row2(stringResource(R.string.confirm_pickup), "Current location", dot = Dot.Pickup)
                Divider(); Row2(stringResource(R.string.confirm_drop), destination, dot = Dot.Drop)
                Divider(); Row2(stringResource(R.string.confirm_when), whenLabel ?: "Now · about ${v.etaMin} min away", dot = Dot.Clock)
                Divider()
                Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.confirm_vehicle), color = c.muted, fontSize = 11.sp, letterSpacing = 1.1.sp)
                        Text("${v.name} · ${v.seats} seats", color = c.fg, fontSize = 15.sp)
                    }
                    VehicleArt(v.style, 64.dp)
                }
            }
            Text(stringResource(R.string.confirm_fare), color = c.muted, fontSize = 11.sp, letterSpacing = 1.1.sp, modifier = Modifier.padding(top = 4.dp))
            Card {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    FareLine(stringResource(R.string.confirm_base), base)
                    FareLine(stringResource(R.string.confirm_distance, route?.km ?: TripKm), dist)
                    FareLine(stringResource(R.string.confirm_duration, route?.minutes ?: TripMinutes), time)
                    FareLine(stringResource(R.string.confirm_taxes), taxes)
                    HorizontalDivider(color = c.line)
                    FareLine(stringResource(R.string.confirm_total), total, bold = true)
                }
            }
        }
        RideButton(
            (if (whenLabel != null) "Schedule " else "Book ") + "${v.name} · ${money(total)}", onBook,
            modifier = Modifier.padding(vertical = 12.dp).entrance(200),
        )
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

@Composable
private fun FareLine(label: String, amount: Double, bold: Boolean = false) {
    val c = RideTheme.colors
    Row(Modifier.fillMaxWidth().height(if (bold) 42.dp else 34.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = if (bold) c.fg else c.muted, fontSize = if (bold) 16.sp else 14.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
        Text(money(amount), color = c.fg, fontSize = if (bold) 16.sp else 14.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ConfirmDarkPreview() = RideNowTheme(darkTheme = true) { ConfirmScreen("Helix Garden Plaza", 1, false, null, {}, {}, null, LatLng(0.0, 0.0), null) }

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ConfirmLightPreview() = RideNowTheme(darkTheme = false) { ConfirmScreen("Helix Garden Plaza", 1, false, "Wed, 4 Nov · 8:30 AM", {}, {}, null, LatLng(0.0, 0.0), null) }
