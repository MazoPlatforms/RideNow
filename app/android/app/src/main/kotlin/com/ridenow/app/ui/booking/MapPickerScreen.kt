package com.ridenow.app.ui.booking

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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import com.ridenow.app.ui.components.RideButton
import com.ridenow.app.ui.components.RideIcon
import com.ridenow.app.ui.components.RideIcons
import com.ridenow.app.ui.theme.BrandYellow
import com.ridenow.app.ui.theme.Ink
import com.ridenow.app.ui.theme.RideNowTheme
import com.ridenow.app.ui.theme.RideTheme
import kotlin.math.abs
import kotlin.math.roundToInt

private val Addresses = listOf(
    "Helix Garden Plaza, Sector 9", "134 Meridian Walk, Sector 7", "Nova Terminal, Gate 4", "Orbit Ring Station, Sector 1",
    "Aurora Medical Centre, Sector 3", "Zenith Café, Sector 7", "Helix City Hall, Sector 2", "Skyline Way 21, Sector 5",
)

/** Drag the map under a fixed pin to choose a place. Address names are samples until real geocoding is added. */
@Composable
fun MapPickerScreen(title: String, confirmLabel: String, onBack: () -> Unit, onConfirm: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    var pan by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }
    val lift by animateDpAsState(if (dragging) 14.dp else 0.dp, label = "lift")
    val cell = 80
    val key = (pan.x / cell.toFloat()).roundToInt() * 7 + (pan.y / cell.toFloat()).roundToInt() * 13
    val address = Addresses[abs(key) % Addresses.size]
    val meters = (abs(pan.x) + abs(pan.y)).roundToInt() / 4

    Box(modifier.fillMaxSize().background(c.bg)) {
        Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
            detectDragGestures(onDragStart = { dragging = true }, onDragEnd = { dragging = false }, onDragCancel = { dragging = false }) { change, drag -> change.consume(); pan += drag }
        }) {
            val step = 80.dp.toPx(); val vstep = 70.dp.toPx()
            val ox = pan.x % step; val oy = pan.y % step; val vx = pan.x % vstep
            val minor = c.street.copy(alpha = 0.9f)
            var y = oy; while (y < size.height + step) { drawLine(minor, Offset(0f, y), Offset(size.width, y), 5.dp.toPx(), StrokeCap.Round); y += step }
            var x = vx; while (x < size.width + vstep) { drawLine(minor, Offset(x, 0f), Offset(x, size.height), 5.dp.toPx(), StrokeCap.Round); x += vstep }
            drawLine(c.street2, Offset(-10f, size.height * 0.7f + oy), Offset(size.width + 10f, size.height * 0.1f + oy), 9.dp.toPx(), StrokeCap.Round)
            val me = Offset(size.width / 2 + pan.x * 0f + 70.dp.toPx() + pan.x, size.height * 0.45f + 90.dp.toPx() + pan.y)
            drawCircle(c.accent.copy(alpha = 0.2f), 18.dp.toPx(), me); drawCircle(c.accent, 6.dp.toPx(), me); drawCircle(c.bg, 6.dp.toPx(), me, style = Stroke(2.dp.toPx()))
        }
        // Fixed pin at the middle of the map area
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val tipY = maxHeight * 0.42f
            Canvas(Modifier.fillMaxSize()) {
                drawOval(Color.Black.copy(alpha = 0.3f), Offset(size.width / 2 - 10.dp.toPx(), tipY.toPx() - 4.dp.toPx()), androidx.compose.ui.geometry.Size(20.dp.toPx(), 8.dp.toPx()))
                val tip = Offset(size.width / 2, tipY.toPx() - lift.toPx()); val r = 15.dp.toPx(); val cen = Offset(tip.x, tip.y - 36.dp.toPx())
                drawPath(Path().apply { moveTo(tip.x, tip.y); lineTo(cen.x - r * 0.8f, cen.y + r * 0.7f); lineTo(cen.x + r * 0.8f, cen.y + r * 0.7f); close() }, BrandYellow)
                drawCircle(BrandYellow, r, cen); drawCircle(Ink, 5.dp.toPx(), cen)
            }
        }
        Box(Modifier.statusBarsPadding().padding(20.dp).size(48.dp).clip(CircleShape).background(c.bg).border(1.dp, c.line, CircleShape).clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = "Back" }, contentAlignment = Alignment.Center) { RideIcon(RideIcons.Back, c.fg, size = 22.dp) }
        Text("Move the map to adjust", color = c.muted, fontSize = 13.sp, modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 28.dp).clip(RoundedCornerShape(50)).background(c.bg).border(1.dp, c.line, RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 10.dp))
        Box(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 20.dp, bottom = 196.dp).size(48.dp).clip(CircleShape).background(c.bg).border(1.dp, c.line, CircleShape).clickable(role = Role.Button) { pan = Offset.Zero }.semantics { contentDescription = "Recenter on my location" }, contentAlignment = Alignment.Center) { RideIcon(listOf("M10,10m-5,0a5,5 0 1,0 10,0a5,5 0 1,0 -10,0", "M10 1.5v3M10 15.5v3M1.5 10h3M15.5 10h3"), c.accent, size = 22.dp) }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(c.bg).border(1.dp, c.line, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).navigationBarsPadding().padding(20.dp)) {
            Text(title, color = c.fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Row(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                RideIcon(RideIcons.Pin, c.accent); Spacer(Modifier.width(12.dp))
                Column { Text(if (dragging) "Moving…" else address, color = c.fg, fontSize = 15.sp); Text("$meters m from you", color = c.muted, fontSize = 12.sp) }
            }
            RideButton(confirmLabel, { onConfirm(address) }, enabled = !dragging, modifier = Modifier.padding(top = 14.dp))
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun MapPickerPreview() = RideNowTheme(darkTheme = true) { MapPickerScreen("Set drop location", "Set drop location", {}, {}) }
