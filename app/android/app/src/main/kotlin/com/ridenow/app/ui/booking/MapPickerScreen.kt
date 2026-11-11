package com.ridenow.app.ui.booking

import androidx.compose.ui.res.stringResource
import com.ridenow.app.R
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridenow.app.ui.map.CameraUpdateFactory
import com.ridenow.app.ui.map.CameraPosition
import com.ridenow.app.ui.map.LatLng
import com.ridenow.app.ui.map.rememberCameraPositionState
import com.ridenow.app.ui.components.RideButton
import com.ridenow.app.ui.components.RideIcon
import com.ridenow.app.ui.components.RideIcons
import com.ridenow.app.ui.map.OpenStreetMapApi
import com.ridenow.app.ui.map.RideMap
import com.ridenow.app.ui.map.rememberUserLocation
import com.ridenow.app.ui.theme.BrandYellow
import com.ridenow.app.ui.theme.Ink
import com.ridenow.app.ui.theme.RideNowTheme
import com.ridenow.app.ui.theme.RideTheme

/** Drag the MapLibre map under a fixed pin and select a coordinate. */
@Composable
fun MapPickerScreen(title: String, confirmLabel: String, onBack: () -> Unit, onConfirm: (String, LatLng) -> Unit, modifier: Modifier = Modifier) {
    val textMapMoving = stringResource(R.string.map_moving)

    val c = RideTheme.colors
    val locatingLabel = stringResource(R.string.map_locating)
    val selectedLabel = stringResource(R.string.map_selected)
    val recenterLabel = stringResource(R.string.map_recenter)
    val backLabel = stringResource(R.string.search_back)
    val me = rememberUserLocation()
    val cam = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(me.position, 16f) }
    var address by remember { mutableStateOf(locatingLabel) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(me.position) { if (first && me.real) { first = false; cam.animate(CameraUpdateFactory.newLatLngZoom(me.position, 16f)) } }
    val moving = cam.isMoving
    val lift by animateDpAsState(if (moving) 14.dp else 0.dp, label = "lift")
    LaunchedEffect(moving) { if (!moving) address = OpenStreetMapApi.reverse(cam.position.target) ?: selectedLabel }

    Box(modifier.fillMaxSize().background(c.bg)) {
        RideMap(Modifier.fillMaxSize(), cam, myLocation = me.real, padding = PaddingValues(bottom = 190.dp))
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val tipY = (maxHeight - 190.dp) / 2
            Canvas(Modifier.fillMaxSize()) {
                drawOval(Color.Black.copy(alpha = 0.3f), Offset(size.width / 2 - 10.dp.toPx(), tipY.toPx() - 4.dp.toPx()), Size(20.dp.toPx(), 8.dp.toPx()))
                val tip = Offset(size.width / 2, tipY.toPx() - lift.toPx()); val r = 15.dp.toPx(); val cen = Offset(tip.x, tip.y - 36.dp.toPx())
                drawPath(Path().apply { moveTo(tip.x, tip.y); lineTo(cen.x - r * 0.8f, cen.y + r * 0.7f); lineTo(cen.x + r * 0.8f, cen.y + r * 0.7f); close() }, BrandYellow)
                drawCircle(BrandYellow, r, cen); drawCircle(Ink, 5.dp.toPx(), cen)
            }
        }
        Box(Modifier.statusBarsPadding().padding(20.dp).size(48.dp).clip(CircleShape).background(c.bg).border(1.dp, c.line, CircleShape).clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = backLabel }, contentAlignment = Alignment.Center) { RideIcon(RideIcons.Back, c.fg, size = 22.dp) }
        Text(stringResource(R.string.map_move), color = c.muted, fontSize = 13.sp, modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 28.dp).clip(RoundedCornerShape(50)).background(c.bg).border(1.dp, c.line, RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 10.dp))
        Box(Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 210.dp).navigationBarsPadding().size(48.dp).clip(CircleShape).background(c.bg).border(1.dp, c.line, CircleShape).clickable(role = Role.Button) { /* recenter */ }.semantics { contentDescription = recenterLabel }, contentAlignment = Alignment.Center) { RideIcon(listOf("M10,10m-5,0a5,5 0 1,0 10,0a5,5 0 1,0 -10,0", "M10 1.5v3M10 15.5v3M1.5 10h3M15.5 10h3"), c.accent, size = 22.dp) }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(c.bg).border(1.dp, c.line, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).navigationBarsPadding().padding(20.dp)) {
            Text(title, color = c.fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Row(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                RideIcon(RideIcons.Pin, c.accent); Spacer(Modifier.width(12.dp))
                Text(if (moving) textMapMoving else address, color = c.fg, fontSize = 15.sp, maxLines = 2)
            }
            RideButton(confirmLabel, { onConfirm(address, cam.position.target) }, enabled = !moving, modifier = Modifier.padding(top = 14.dp))
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun MapPickerPreview() = RideNowTheme(darkTheme = true) { MapPickerScreen(stringResource(R.string.map_set_drop), stringResource(R.string.map_set_drop), {}, { _, _ -> }) }
