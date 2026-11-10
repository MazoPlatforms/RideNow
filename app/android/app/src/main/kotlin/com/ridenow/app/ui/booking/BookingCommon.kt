package com.ridenow.app.ui.booking

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.rememberCameraPositionState
import com.ridenow.app.ui.map.MapPin
import com.ridenow.app.ui.map.PinKind
import com.ridenow.app.ui.map.RideMap
import com.ridenow.app.ui.map.RouteInfo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridenow.app.ui.theme.BrandYellow
import com.ridenow.app.ui.theme.Ink
import com.ridenow.app.ui.theme.RideTheme
import java.util.Locale

data class Vehicle(val name: String, val seats: Int, val luggage: Int, val etaMin: Int, val fare: Double, val style: Int)

val Vehicles = listOf(
    Vehicle("Pulse", 3, 1, 3, 12.40, 0),
    Vehicle("Orbit", 4, 2, 3, 16.80, 1),
    Vehicle("Nova XL", 6, 4, 5, 24.50, 2),
)
const val ReturnFactor = 1.8
const val TripKm = "8.4"
const val TripMinutes = 19

fun money(v: Double): String = String.format(Locale.US, "$%.2f", v)
fun Vehicle.fareFor(returnTrip: Boolean) = if (returnTrip) Math.round(fare * ReturnFactor * 100) / 100.0 else fare

/** Side-view RideNow vehicle drawn on a 100 x 52 grid. [style] 0 Pulse, 1 Orbit, 2 Nova XL (three windows). */
@Composable
fun VehicleArt(style: Int, width: Dp, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val body = if (c.isDark) Color(0xFFDCE4EC) else Color.White
    val glass = if (c.isDark) Color(0xFF0B1118) else Color(0xFF26384A)
    val edge = if (c.isDark) Color(0xFF2C3442) else Color(0xFF9BA8B5)
    Canvas(modifier.size(width, width * 0.52f)) {
        scale(size.width / 100f, Offset.Zero) {
            drawOval(c.accent.copy(alpha = 0.25f), Offset(14f, 43.5f), Size(72f, 7f))
            val p = Path().apply {
                moveTo(6f, 37f); quadraticTo(6f, 31f, 12f, 29f); lineTo(26f, 25f); quadraticTo(34f, 13f, 50f, 13f)
                lineTo(66f, 13f); quadraticTo(78f, 13f, 84f, 25f); lineTo(90f, 28f); quadraticTo(94f, 30f, 94f, 37f); lineTo(94f, 40f); lineTo(6f, 40f); close()
            }
            drawPath(p, body); drawPath(p, edge, style = Stroke(1.5f))
            when (style) {
                2 -> listOf(30f, 46f, 62f).forEach { x -> drawRoundRect(glass, Offset(x, 17f), Size(13f, 9f), CornerRadius(2f)) }
                else -> {
                    val w = Path().apply { moveTo(33f, 25f); quadraticTo(39f, 17f, 50f, 17f); lineTo(64f, 17f); quadraticTo(72f, 17f, 77f, 25f); close() }
                    drawPath(w, glass)
                    if (style == 1) drawLine(body, Offset(56f, 17f), Offset(56f, 25f), 2f)
                }
            }
            drawLine(c.accent, Offset(12f, 34f), Offset(88f, 34f), 2f, StrokeCap.Round)
            listOf(26f, 76f).forEach { x -> drawCircle(glass, 6f, Offset(x, 41f)); drawCircle(body, 6f, Offset(x, 41f), style = Stroke(2f)) }
        }
    }
}

/** Google map showing the route between [from] and [to], fitted to the visible area, with an optional distance chip. */
@Composable
fun RouteMap(modifier: Modifier = Modifier, from: LatLng, to: LatLng?, route: RouteInfo?, chip: String?, bottomPadding: Dp = 0.dp, topPadding: Dp = 70.dp) {
    val c = RideTheme.colors
    val cam = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(from, 14f) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(loaded, to, route) {
        if (loaded && to != null) {
            val b = LatLngBounds.Builder().include(from).include(to); route?.points?.forEach { b.include(it) }
            runCatching { cam.animate(CameraUpdateFactory.newLatLngBounds(b.build(), 90)) }
        }
    }
    Box(modifier) {
        RideMap(Modifier.fillMaxSize(), cam, pins = listOfNotNull(MapPin(from, PinKind.Pickup), to?.let { MapPin(it, PinKind.Drop) }), route = route?.points ?: emptyList(), padding = PaddingValues(top = topPadding, bottom = bottomPadding), onLoaded = { loaded = true })
        if (chip != null) Text(chip, color = c.bg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.TopCenter).padding(top = topPadding + 8.dp).background(c.fg, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 7.dp))
    }
}

val ChipShape = RoundedCornerShape(14.dp)
