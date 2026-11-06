package com.ridenow.app.ui.trip

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridenow.app.ui.booking.Vehicles
import com.ridenow.app.ui.booking.VehicleArt
import com.ridenow.app.ui.components.RideButton
import com.ridenow.app.ui.components.RideButtonStyle
import com.ridenow.app.ui.components.RideIcon
import com.ridenow.app.ui.components.RideIcons
import com.ridenow.app.ui.theme.BrandYellow
import com.ridenow.app.ui.theme.Ink
import com.ridenow.app.ui.theme.RideNowTheme
import com.ridenow.app.ui.theme.RideTheme
import com.ridenow.app.ui.util.entrance
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val Far = Offset(0.82f, 0.47f)
private val Near = Offset(0.37f, 0.36f)
private val Pickup = Offset(0.3f, 0.32f)
private val Drop = Offset(0.78f, 0.14f)
private val Reasons = listOf("Driver is taking too long", "Booked by mistake", "Changed my plans", "Wrong pickup location", "Other")

/** Live ride: 0 driver assigned, 1 driver arriving, 2 on the way. Demo timers move through the phases. */
@Composable
fun TripScreen(destination: String, vehicleIndex: Int, onCancelled: () -> Unit, onComplete: () -> Unit, onShare: () -> Unit, onSafety: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val v = Vehicles[vehicleIndex]
    var phase by rememberSaveable { mutableIntStateOf(0) }
    var showCancel by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(phase) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(listOf(5000, 4000, 9000)[phase], easing = LinearEasing))
        if (phase < 2) phase++ else onComplete()
    }
    val t = progress.value
    val vehiclePos = when (phase) { 0 -> lerp(Far, Pickup, t); 1 -> lerp(Near, Pickup, t); else -> lerp(Pickup, Drop, t) }
    val target = if (phase < 2) Pickup else Drop

    Box(modifier.fillMaxSize().background(c.bg)) {
        Canvas(Modifier.fillMaxSize()) {
            val minor = c.street.copy(alpha = 0.9f)
            var y = 60.dp.toPx(); while (y < size.height) { drawLine(minor, Offset(0f, y), Offset(size.width, y), 5.dp.toPx(), StrokeCap.Round); y += 80.dp.toPx() }
            var x = 50.dp.toPx(); while (x < size.width) { drawLine(minor, Offset(x, 0f), Offset(x, size.height), 5.dp.toPx(), StrokeCap.Round); x += 70.dp.toPx() }
            drawLine(c.street2, Offset(-10f, size.height * 0.7f), Offset(size.width + 10f, size.height * 0.05f), 9.dp.toPx(), StrokeCap.Round)
            fun px(o: Offset) = Offset(size.width * o.x, size.height * o.y)
            val vp = px(vehiclePos); val tp = px(target)
            drawLine(c.accent, vp, tp, 4.dp.toPx(), StrokeCap.Round, PathEffect.dashPathEffect(floatArrayOf(2f, 18f)))
            if (phase == 2) { val pk = px(Pickup); drawLine(c.accent, pk, vp, 4.dp.toPx(), StrokeCap.Round) }
            val pk = px(Pickup); drawCircle(c.accent, 8.dp.toPx(), pk); drawCircle(c.fg, 8.dp.toPx(), pk, style = Stroke(3.dp.toPx()))
            val d = px(Drop); val r = 12.dp.toPx(); val cen = Offset(d.x, d.y - 32.dp.toPx())
            drawPath(Path().apply { moveTo(d.x, d.y); lineTo(cen.x - r * 0.8f, cen.y + r * 0.7f); lineTo(cen.x + r * 0.8f, cen.y + r * 0.7f); close() }, c.accent)
            drawCircle(c.accent, r, cen); drawCircle(Ink, 4.dp.toPx(), cen)
            drawCircle(c.accent.copy(alpha = 0.2f), 24.dp.toPx(), vp)
            drawRoundRect(c.fg, Offset(vp.x - 11.dp.toPx(), vp.y - 6.dp.toPx()), androidx.compose.ui.geometry.Size(22.dp.toPx(), 12.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()))
            drawRoundRect(c.accent, Offset(vp.x - 11.dp.toPx(), vp.y - 1.dp.toPx()), androidx.compose.ui.geometry.Size(22.dp.toPx(), 2.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()))
        }
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp).height(48.dp).clip(RoundedCornerShape(24.dp)).background(c.bg).border(1.dp, c.line, RoundedCornerShape(24.dp)).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(c.accent)); Spacer(Modifier.width(8.dp))
            Text(if (phase < 2) "Current location  →  $destination" else "On trip to  $destination", color = c.fg, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().shadow(16.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), ambientColor = Color.Black, spotColor = Color.Black)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(c.bg).border(1.dp, c.line, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .navigationBarsPadding().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 14.dp).entrance(100, 40),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(width = 40.dp, height = 4.dp).clip(CircleShape).background(c.border))
            val (title, chip, sub) = when (phase) {
                0 -> Triple("Driver assigned", "4 min", "Mira is heading to your pickup.")
                1 -> Triple("Your driver is arriving", "1 min", "Meet Mira at your pickup point.")
                else -> Triple("On your way", "12 min left", "6.1 km to go.")
            }
            Row(Modifier.padding(top = 14.dp).height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = c.fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).semantics { heading() })
                Text(chip, color = c.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(50)).background(c.accent.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 7.dp))
            }
            Text(sub, color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
            if (phase == 2) Box(Modifier.padding(top = 14.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.line)) { Box(Modifier.fillMaxWidth(t.coerceIn(0.02f, 1f)).height(4.dp).background(c.accent)) }

            Column(Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(20.dp))) {
                Row(Modifier.fillMaxWidth().height(72.dp).padding(start = 14.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.14f)).border(1.dp, c.accent, CircleShape), contentAlignment = Alignment.Center) { Text("MT", color = c.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Mira Tanaka", color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically) { RideIcon(RideIcons.Star, c.accent, size = 14.dp); Text("  4.9 · 2,310 trips", color = c.muted, fontSize = 12.sp) }
                    }
                    CircleAction(RideIcons.Call, "Call driver") {}; CircleAction(RideIcons.Message, "Message driver") {}
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    VehicleArt(v.style, 64.dp); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text("${v.name} · Silver", color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold); Text("${v.seats} seats", color = c.muted, fontSize = 12.sp) }
                    Text("RN 2100", color = c.fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, modifier = Modifier.border(1.dp, c.border, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
            if (phase == 1) Row(Modifier.padding(top = 10.dp).fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Share this PIN with your driver", color = c.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("4821", color = c.accent, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 4.sp)
            }
            Spacer(Modifier.height(14.dp))
            if (phase < 2) SwipeToCancel { showCancel = true }
            else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { RideButton("Share live trip", onShare, style = RideButtonStyle.Text, modifier = Modifier.border(1.dp, c.border, RoundedCornerShape(28.dp)).height(52.dp)) }
                Box(Modifier.weight(1f)) { RideButton("Safety", onSafety, style = RideButtonStyle.Text, modifier = Modifier.border(1.dp, c.border, RoundedCornerShape(28.dp)).height(52.dp)) }
            }
        }
    }
    if (showCancel) CancelSheet(onKeep = { showCancel = false }, onCancel = onCancelled)
}

private fun lerp(a: Offset, b: Offset, t: Float) = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

@Composable
private fun CircleAction(icon: List<String>, label: String, onClick: () -> Unit) {
    val c = RideTheme.colors
    Box(Modifier.padding(end = 6.dp).size(44.dp).clip(CircleShape).border(1.dp, c.border, CircleShape).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label }, contentAlignment = Alignment.Center) { RideIcon(icon, c.accent) }
}

/** Drag the red knob right to cancel. Tap works too, for accessibility. */
@Composable
private fun SwipeToCancel(onCancel: () -> Unit) {
    val c = RideTheme.colors
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    BoxWithConstraints(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(28.dp))) {
        val maxPx = with(LocalDensity.current) { (maxWidth - 54.dp).toPx() }
        val pad = with(LocalDensity.current) { 4.dp.roundToPx() }
        Text("Swipe to cancel  ››", color = c.muted, fontSize = 15.sp, modifier = Modifier.align(Alignment.Center))
        Box(
            Modifier.offset { IntOffset(offset.value.roundToInt() + pad, pad) }.size(46.dp).clip(CircleShape).background(c.danger)
                .clickable(role = Role.Button) { onCancel() }.semantics { contentDescription = "Cancel ride. Swipe right or double tap." }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(onDragEnd = { scope.launch { if (offset.value > maxPx * 0.7f) onCancel(); offset.animateTo(0f) } }) { change, dx ->
                        change.consume(); scope.launch { offset.snapTo((offset.value + dx).coerceIn(0f, maxPx)) }
                    }
                },
            contentAlignment = Alignment.Center,
        ) { RideIcon(RideIcons.Close, if (c.isDark) Ink else Color.White, size = 18.dp) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CancelSheet(onKeep: () -> Unit, onCancel: () -> Unit) {
    val c = RideTheme.colors
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var reason by rememberSaveable { mutableIntStateOf(2) }
    ModalBottomSheet(onDismissRequest = onKeep, sheetState = state, containerColor = c.bg, contentColor = c.fg, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 12.dp)) {
            Text("Cancel this ride?", color = c.fg, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text("Tell us why. It helps us improve.", color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
            Reasons.forEachIndexed { i, r ->
                Row(Modifier.fillMaxWidth().height(52.dp).clickable(role = Role.RadioButton) { reason = i }, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(20.dp).clip(CircleShape).then(if (i == reason) Modifier.background(BrandYellow) else Modifier.border(1.5.dp, c.muted, CircleShape)), contentAlignment = Alignment.Center) { if (i == reason) Box(Modifier.size(8.dp).clip(CircleShape).background(Ink)) }
                    Spacer(Modifier.width(14.dp)); Text(r, color = c.fg, fontSize = 15.sp, fontWeight = if (i == reason) FontWeight.SemiBold else FontWeight.Normal)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
            }
            RideButton("Keep my ride", onKeep, modifier = Modifier.padding(top = 16.dp))
            Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).border(1.dp, c.danger, RoundedCornerShape(26.dp)).clickable(role = Role.Button, onClick = onCancel), contentAlignment = Alignment.Center) {
                Text("Cancel ride", color = c.danger, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun TripPreview() = RideNowTheme(darkTheme = true) { TripScreen("Helix Garden Plaza", 1, {}, {}, {}, {}) }
