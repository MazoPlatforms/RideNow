package com.ridenow.driver.ui.trip

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import com.ridenow.driver.R
import com.ridenow.driver.ui.components.*
import com.ridenow.driver.ui.theme.RideNowTheme
import com.ridenow.driver.ui.theme.RideTheme
import com.ridenow.driver.ui.util.entrance
import java.text.NumberFormat
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate

/** Active trip: map with the route and a sheet whose main button moves through arrived, start and complete. */
@Composable
fun TripScreen(trip: TripRequest, phase: TripPhase, onAdvance: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    var confirmCancel by rememberSaveable { mutableStateOf(false) }
    val money = remember(trip.fare) { NumberFormat.getCurrencyInstance().format(trip.fare) }
    val title = stringResource(when (phase) { TripPhase.ToPickup -> R.string.trip_phase_pickup; TripPhase.Arrived -> R.string.trip_phase_arrived; TripPhase.InTrip -> R.string.trip_phase_trip })
    val sub = when (phase) {
        TripPhase.ToPickup -> stringResource(R.string.trip_phase_pickup_sub, trip.rider.substringBefore(" "), trip.pickup)
        TripPhase.Arrived -> stringResource(R.string.trip_phase_arrived_sub, trip.rider.substringBefore(" "))
        TripPhase.InTrip -> stringResource(R.string.trip_phase_trip_sub, trip.drop)
    }
    val button = stringResource(when (phase) { TripPhase.ToPickup -> R.string.trip_btn_arrived; TripPhase.Arrived -> R.string.trip_btn_start; TripPhase.InTrip -> R.string.trip_btn_complete })

    Box(modifier.fillMaxSize().background(c.bg)) {
        Canvas(Modifier.fillMaxSize()) {
            drawCityStreets(c, 0.9f)
            val start = Offset(size.width * 0.2f, size.height * 0.40f)
            val pickup = Offset(size.width * 0.58f, size.height * 0.30f)
            val drop = Offset(size.width * 0.82f, size.height * 0.12f)
            val route = Path().apply {
                moveTo(start.x, start.y); cubicTo(size.width * 0.32f, size.height * 0.30f, size.width * 0.45f, size.height * 0.36f, pickup.x, pickup.y)
                cubicTo(size.width * 0.66f, size.height * 0.24f, size.width * 0.72f, size.height * 0.20f, drop.x, drop.y)
            }
            drawPath(route, c.accent.copy(alpha = 0.2f), style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
            drawPath(route, c.accent, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 16f))))
            drawCircle(c.fill, 9.dp.toPx(), pickup); drawCircle(c.fg, 9.dp.toPx(), pickup, style = Stroke(3.dp.toPx()))
            drawRoundRect(c.fg, Offset(drop.x - 9.dp.toPx(), drop.y - 9.dp.toPx()), Size(18.dp.toPx(), 18.dp.toPx()), CornerRadius(4.dp.toPx()))
            val car = when (phase) { TripPhase.ToPickup -> start; TripPhase.Arrived -> pickup; TripPhase.InTrip -> Offset((pickup.x + drop.x) / 2f, (pickup.y + drop.y) / 2f) }
            drawCircle(c.accent.copy(alpha = 0.2f), 22.dp.toPx(), car)
            rotate(-25f, car) {
                drawRoundRect(c.fg, Offset(car.x - 9.dp.toPx(), car.y - 16.dp.toPx()), Size(18.dp.toPx(), 32.dp.toPx()), CornerRadius(7.dp.toPx()))
                drawRoundRect(c.bg, Offset(car.x - 6.dp.toPx(), car.y - 8.dp.toPx()), Size(12.dp.toPx(), 9.dp.toPx()), CornerRadius(3.dp.toPx()))
            }
        }
        val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(c.field, shape).border(1.dp, c.border, shape).navigationBarsPadding().padding(20.dp)) {
            Text(title, color = c.fg, fontSize = 22.sp, fontWeight = FontWeight.Light, modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite })
            Text(sub, color = c.muted, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                RiderAvatar(trip.initials)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(trip.rider, color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("\u2605 " + String.format("%.1f", trip.rating), color = c.muted, fontSize = 12.sp)
                }
                CircleAction(RideIcons.Call, stringResource(R.string.trip_call))
                Spacer(Modifier.width(8.dp))
                CircleAction(RideIcons.Msg, stringResource(R.string.trip_message))
            }
            Row(Modifier.padding(top = 14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RideIcon(if (phase == TripPhase.InTrip) RideIcons.Flag else RideIcons.Pin, c.accent, size = 18.dp)
                Text(if (phase == TripPhase.InTrip) trip.drop else trip.pickup, color = c.fg, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(start = 10.dp))
                Text(money, color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            RideButton(button, onAdvance, modifier = Modifier.padding(top = 16.dp))
            if (phase != TripPhase.InTrip) RideButton(stringResource(R.string.trip_cancel), { confirmCancel = true }, style = RideButtonStyle.Text, modifier = Modifier.padding(top = 4.dp))
        }
    }
    if (confirmCancel) AlertDialog(
        onDismissRequest = { confirmCancel = false }, containerColor = c.bg,
        title = { Text(stringResource(R.string.trip_cancel_title), color = c.fg) }, text = { Text(stringResource(R.string.trip_cancel_text), color = c.muted) },
        confirmButton = { TextButton(onClick = { confirmCancel = false; onCancel() }) { Text(stringResource(R.string.trip_cancel_confirm), color = c.danger) } },
        dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text(stringResource(R.string.trip_keep), color = c.fg) } },
    )
}

@Composable
private fun CircleAction(icon: List<String>, description: String) {
    val c = RideTheme.colors
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.10f)).border(1.dp, c.accent, CircleShape)
            .clickable(role = androidx.compose.ui.semantics.Role.Button) { }.semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { RideIcon(icon, c.accent, size = 20.dp) }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun TripPreview() = RideNowTheme(darkTheme = true) { TripScreen(SampleTrips.all[0], TripPhase.ToPickup, {}, {}) }
