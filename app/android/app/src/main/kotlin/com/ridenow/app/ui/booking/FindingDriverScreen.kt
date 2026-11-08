package com.ridenow.app.ui.booking

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridenow.app.ui.components.RideButton
import com.ridenow.app.ui.components.RideButtonStyle
import com.ridenow.app.ui.map.MazoMap
import com.ridenow.app.ui.map.MapCoordinate
import com.ridenow.app.ui.map.MazoRoute
import com.ridenow.app.ui.theme.RideNowTheme
import com.ridenow.app.ui.theme.RideTheme
import com.ridenow.app.ui.util.entrance
import com.ridenow.app.ui.util.rememberReducedMotion
import kotlinx.coroutines.delay

/** Radar-style search for a driver. Demo: finds one after about 6 seconds and calls [onFound]. */
@Composable
fun FindingDriverScreen(destination: String, vehicleIndex: Int, returnTrip: Boolean, onFound: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier,
    mapStart: MapCoordinate? = null, mapEnd: MapCoordinate? = null, mapRoute: MazoRoute? = null) {
    val c = RideTheme.colors
    val v = Vehicles[vehicleIndex]
    val reduce = rememberReducedMotion()
    LaunchedEffect(Unit) { delay(6000); onFound() }
    val loop = rememberInfiniteTransition(label = "finding")
    val tRaw by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearOutSlowInEasing)), label = "ping")
    val barRaw by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing)), label = "bar")
    val t = if (reduce) 0.4f else tRaw
    val bar = if (reduce) 0.5f else barRaw

    Box(modifier.fillMaxSize().background(c.bg)) {
        MazoMap(Modifier.fillMaxSize(), initialCenter = mapEnd ?: mapStart,
            start = mapStart, selected = mapEnd, route = mapRoute)
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp).height(48.dp).clip(RoundedCornerShape(24.dp)).background(c.bg)
                .border(1.dp, c.line, RoundedCornerShape(24.dp)).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(c.accent)); Spacer(Modifier.width(8.dp))
            Text("Current location  →  $destination", color = c.fg, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().shadow(16.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), ambientColor = Color.Black, spotColor = Color.Black)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(c.bg).border(1.dp, c.line, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .navigationBarsPadding().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 14.dp).entrance(100, 40),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(width = 40.dp, height = 4.dp).clip(CircleShape).background(c.border))
            Text("Finding your driver…", color = c.fg, fontSize = 26.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(top = 16.dp).semantics { heading() })
            Text("Matching you with the nearest ${v.name}.", color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            BoxWithConstraints(Modifier.padding(top = 18.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.line)) {
                Box(Modifier.fillMaxHeight().width(maxWidth * 0.4f).offset(x = maxWidth * (-0.4f + 1.4f * bar)).background(c.accent))
            }
            Row(
                Modifier.padding(top = 18.dp).fillMaxWidth().height(72.dp).clip(RoundedCornerShape(20.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(20.dp)).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VehicleArt(v.style, 64.dp); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("${v.name} · ${v.seats} seats", color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("Pickup in about ${v.etaMin} min", color = c.muted, fontSize = 12.sp)
                }
                Text(money(v.fareFor(returnTrip)), color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            RideButton("Cancel request", onCancel, style = RideButtonStyle.Text, modifier = Modifier.padding(top = 14.dp).border(1.dp, c.border, RoundedCornerShape(28.dp)))
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun FindingPreview() = RideNowTheme(darkTheme = true) { FindingDriverScreen("Helix Garden Plaza", 1, false, {}, {}) }
