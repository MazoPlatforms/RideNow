package com.ridenow.app.ui.booking

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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

/** Placeholder map with streets and the route from pickup to destination, plus a distance chip. */
@Composable
fun RouteMap(modifier: Modifier = Modifier, pickupAt: Offset = Offset(0.2f, 0.33f), dropAt: Offset = Offset(0.78f, 0.15f), chip: String? = "$TripKm km · $TripMinutes min") {
    val c = RideTheme.colors
    BoxWithConstraints(modifier) {
        Canvas(Modifier.matchParentSize()) {
            drawRect(c.bg)
            val minor = c.street.copy(alpha = 0.9f)
            var y = 60.dp.toPx(); while (y < size.height) { drawLine(minor, Offset(0f, y), Offset(size.width, y), 5.dp.toPx(), StrokeCap.Round); y += 80.dp.toPx() }
            var x = 50.dp.toPx(); while (x < size.width) { drawLine(minor, Offset(x, 0f), Offset(x, size.height), 5.dp.toPx(), StrokeCap.Round); x += 70.dp.toPx() }
            drawLine(c.street2, Offset(-10f, size.height * 0.7f), Offset(size.width + 10f, size.height * 0.05f), 9.dp.toPx(), StrokeCap.Round)
            val a = Offset(size.width * pickupAt.x, size.height * pickupAt.y)
            val b = Offset(size.width * dropAt.x, size.height * dropAt.y)
            val route = Path().apply {
                moveTo(a.x, a.y)
                cubicTo(a.x + (b.x - a.x) * 0.2f, a.y - (a.y - b.y) * 0.7f, a.x + (b.x - a.x) * 0.55f, b.y + (a.y - b.y) * 0.45f, b.x, b.y)
            }
            drawPath(route, c.accent.copy(alpha = 0.2f), style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
            drawPath(route, c.accent, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
            drawCircle(c.accent, 9.dp.toPx(), a); drawCircle(c.fg, 9.dp.toPx(), a, style = Stroke(3.dp.toPx()))
            drawPin(b, c.accent)
        }
        if (chip != null) Box(
            Modifier.offset(maxWidth * ((pickupAt.x + dropAt.x) / 2f) - 50.dp, maxHeight * ((pickupAt.y + dropAt.y) / 2f) + 8.dp)
                .background(c.fg, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 7.dp),
        ) { Text(chip, color = c.bg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
    }
}

private fun DrawScope.drawPin(tip: Offset, color: Color) {
    val r = 14.dp.toPx(); val center = Offset(tip.x, tip.y - 36.dp.toPx())
    val p = Path().apply { moveTo(tip.x, tip.y); lineTo(center.x - r * 0.8f, center.y + r * 0.7f); lineTo(center.x + r * 0.8f, center.y + r * 0.7f); close() }
    drawPath(p, color); drawCircle(color, r, center); drawCircle(Ink, 5.dp.toPx(), center)
}

val ChipShape = RoundedCornerShape(14.dp)
