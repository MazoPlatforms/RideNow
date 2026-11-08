package com.ridenow.app.ui.home

import androidx.compose.foundation.layout.PaddingValues
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.rememberCameraPositionState
import com.ridenow.app.ui.map.MapPin
import com.ridenow.app.ui.map.PinKind
import com.ridenow.app.ui.map.RideMap
import com.ridenow.app.ui.map.rememberUserLocation
import androidx.compose.runtime.LaunchedEffect

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridenow.app.R
import com.ridenow.app.ui.components.RideIcon
import com.ridenow.app.ui.components.RideIcons
import com.ridenow.app.ui.theme.BrandYellow
import com.ridenow.app.ui.theme.RideColors
import com.ridenow.app.ui.theme.RideNowTheme
import com.ridenow.app.ui.theme.RideTheme
import com.ridenow.app.ui.util.entrance
import com.ridenow.app.ui.util.rememberReducedMotion

/**
 * Home ("Where to?"): full-screen map, menu and notification buttons, and a bottom sheet.
 * The map is a drawn placeholder with moving vehicles; swap [CityMap] for a Google Maps view later.
 */
@Composable
fun HomeScreen(
    onMenu: () -> Unit, onNotifications: () -> Unit, onSearch: () -> Unit, onLater: () -> Unit,
    onEditPickup: () -> Unit, onSaved: (String) -> Unit, onRecent: (String) -> Unit,
    modifier: Modifier = Modifier, hasUnread: Boolean = true, pickup: String = "128 Meridian Walk",
) {
    val c = RideTheme.colors
    var expanded by rememberSaveable { mutableStateOf(true) }
    val me = rememberUserLocation()
    val cam = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(me.position, 15f) }
    LaunchedEffect(me.position) { cam.animate(CameraUpdateFactory.newLatLngZoom(me.position, 15.5f)) }
    val cars = remember(me.position) { listOf(0.003 to 0.004, -0.004 to 0.002, 0.002 to -0.005, -0.003 to -0.003).map { (a, b) -> MapPin(LatLng(me.position.latitude + a, me.position.longitude + b), PinKind.Vehicle) } }
    Box(modifier.fillMaxSize().background(c.bg)) {
        RideMap(Modifier.fillMaxSize(), cam, pins = cars, myLocation = me.real, padding = PaddingValues(bottom = 300.dp))

        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp).entrance(0), horizontalArrangement = Arrangement.SpaceBetween) {
            RoundButton(RideIcons.Menu, stringResource(R.string.home_menu), onMenu)
            Box {
                RoundButton(RideIcons.Bell, stringResource(R.string.home_notifications), onNotifications)
                if (hasUnread) Box(Modifier.align(Alignment.TopEnd).padding(top = 11.dp, end = 11.dp).size(12.dp).clip(CircleShape).background(BrandYellow).border(2.dp, c.bg, CircleShape))
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().shadow(16.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), ambientColor = Color.Black, spotColor = Color.Black)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(c.bg)
                .border(1.dp, c.line, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).navigationBarsPadding()
                .padding(horizontal = 24.dp).entrance(150, 40),
        ) {
            // Drag or tap the handle to expand and collapse the sheet.
            Box(
                Modifier.fillMaxWidth().height(28.dp).clickable { expanded = !expanded }.pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(onDragStart = { total = 0f }, onDragEnd = { if (total < -30f) expanded = true else if (total > 30f) expanded = false }) { _, dy -> total += dy }
                }.semantics { contentDescription = if (expanded) "Collapse" else "Expand" },
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size(width = 40.dp, height = 4.dp).clip(CircleShape).background(c.border)) }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.home_title), color = c.fg, fontSize = 28.sp, fontWeight = FontWeight.Light, modifier = Modifier.weight(1f).semantics { heading() })
                Row(
                    Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(20.dp))
                        .clickable(role = Role.Button, onClick = onLater).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RideIcon(RideIcons.Clock, c.accent, size = 18.dp); Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.home_later), color = c.fg, fontSize = 14.sp)
                }
            }

            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(c.accent)); Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_pickup, pickup), color = c.muted, fontSize = 13.sp)
                Box(Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onEditPickup).semantics { contentDescription = "Edit pickup location" }, contentAlignment = Alignment.Center) {
                    RideIcon(RideIcons.Pencil, c.accent, size = 18.dp)
                }
            }

            Row(
                Modifier.padding(top = 4.dp).fillMaxWidth().height(60.dp).clip(RoundedCornerShape(16.dp)).background(c.field)
                    .border(1.dp, c.border, RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClick = onSearch).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RideIcon(RideIcons.Search, c.accent); Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.home_destination), color = c.muted, fontSize = 17.sp)
            }

            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SavedButton(RideIcons.Home, stringResource(R.string.home_saved_home), Modifier.weight(1f)) { onSaved("Home") }
                SavedButton(RideIcons.Work, stringResource(R.string.home_saved_work), Modifier.weight(1f)) { onSaved("Work") }
            }

            AnimatedVisibility(expanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column(Modifier.padding(top = 8.dp)) {
                    RecentRow("Nova Terminal, Gate 4", "6.2 km") { onRecent("Nova Terminal, Gate 4") }
                    RecentRow("Helix Garden Plaza", "3.8 km") { onRecent("Helix Garden Plaza") }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun RoundButton(icon: List<String>, label: String, onClick: () -> Unit) {
    val c = RideTheme.colors
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(c.bg).border(1.dp, c.line, CircleShape)
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { RideIcon(icon, c.fg, size = 22.dp) }
}

@Composable
private fun SavedButton(icon: List<String>, label: String, modifier: Modifier, onClick: () -> Unit) {
    val c = RideTheme.colors
    Row(
        modifier.height(52.dp).clip(RoundedCornerShape(16.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) { RideIcon(icon, c.accent); Spacer(Modifier.width(10.dp)); Text(label, color = c.fg, fontSize = 15.sp) }
}

@Composable
private fun RecentRow(title: String, distance: String, onClick: () -> Unit) {
    val c = RideTheme.colors
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)) {
        Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).border(1.dp, c.border, CircleShape), contentAlignment = Alignment.Center) { RideIcon(RideIcons.Clock, c.accent, size = 18.dp) }
            Spacer(Modifier.width(14.dp))
            Text(title, color = c.fg, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(distance, color = c.muted, fontSize = 12.sp)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
    }
}

/** Placeholder city map: faint streets, a park, vehicles driving along streets and the rider's pulsing dot. */
@Composable
private fun CityMap(modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val reduce = rememberReducedMotion()
    val loop = rememberInfiniteTransition(label = "map")
    val driveRaw by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(60000, easing = LinearEasing)), label = "drive")
    val pulseRaw by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearOutSlowInEasing)), label = "pulse")
    val drive = if (reduce) 0.3f else driveRaw
    val pulse = if (reduce) 0f else pulseRaw
    Canvas(modifier) {
        drawRect(c.bg)
        drawRoundRect(c.accent.copy(alpha = 0.07f), Offset(size.width * 0.6f, 110.dp.toPx()), Size(104.dp.toPx(), 84.dp.toPx()), CornerRadius(14.dp.toPx()))
        val minor = c.street.copy(alpha = 0.9f)
        var y = 110.dp.toPx(); while (y < size.height) { drawLine(minor, Offset(0f, y), Offset(size.width, y), 5.dp.toPx(), StrokeCap.Round); y += 80.dp.toPx() }
        var x = 50.dp.toPx(); while (x < size.width) { drawLine(minor, Offset(x, 0f), Offset(x, size.height), 5.dp.toPx(), StrokeCap.Round); x += 70.dp.toPx() }
        drawLine(c.street2, Offset(-10f, size.height * 0.64f), Offset(size.width + 10f, size.height * 0.07f), 9.dp.toPx(), StrokeCap.Round)
        drawLine(c.street2, Offset(0f, size.height * 0.31f), Offset(size.width, size.height * 0.31f), 9.dp.toPx(), StrokeCap.Round)
        drawLine(c.street2, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 9.dp.toPx(), StrokeCap.Round)

        // Vehicles on three streets
        listOf(Triple(110f + 80f, 1f, 0.1f), Triple(110f + 240f, -0.8f, 0.6f), Triple(110f + 400f, 0.6f, 0.35f)).forEach { (yDp, speed, off) ->
            val t = (((drive * 12 * speed + off) % 1f) + 1f) % 1f
            vehicle(c, Offset(t * size.width, yDp.dp.toPx()), speed > 0)
        }

        // Rider location
        val me = Offset(size.width / 2, size.height * 0.3f)
        drawCircle(c.accent.copy(alpha = (1f - pulse) * 0.3f), 20.dp.toPx() + pulse * 60.dp.toPx(), me, style = Stroke(2.dp.toPx()))
        drawCircle(c.accent.copy(alpha = 0.2f), 22.dp.toPx(), me)
        drawCircle(c.accent, 7.dp.toPx(), me); drawCircle(c.bg, 7.dp.toPx(), me, style = Stroke(3.dp.toPx()))
    }
}

private fun DrawScope.vehicle(c: RideColors, at: Offset, forward: Boolean) {
    drawCircle(c.accent.copy(alpha = 0.16f), 14.dp.toPx(), at)
    drawRoundRect(c.fg, Offset(at.x - 11.dp.toPx(), at.y - 6.dp.toPx()), Size(22.dp.toPx(), 12.dp.toPx()), CornerRadius(5.dp.toPx()))
    drawRoundRect(c.accent, Offset(at.x - 11.dp.toPx(), at.y - 1.dp.toPx()), Size(22.dp.toPx(), 2.dp.toPx()), CornerRadius(1.dp.toPx()))
    drawCircle(c.bg, 2.dp.toPx(), Offset(at.x + (if (forward) 6 else -6).dp.toPx(), at.y - 2.5.dp.toPx()))
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun HomeDarkPreview() = RideNowTheme(darkTheme = true) { HomeScreen({}, {}, {}, {}, {}, {}, {}) }

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun HomeLightPreview() = RideNowTheme(darkTheme = false) { HomeScreen({}, {}, {}, {}, {}, {}, {}) }
