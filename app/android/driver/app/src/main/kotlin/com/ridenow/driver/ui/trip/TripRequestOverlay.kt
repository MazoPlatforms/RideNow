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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import com.ridenow.driver.ui.preview.PreviewConfig
import kotlinx.coroutines.delay

/** Initials in a ring, used for riders. */
@Composable
fun RiderAvatar(initials: String, size: Dp = 44.dp) {
    val c = RideTheme.colors
    Box(Modifier.size(size).clip(CircleShape).background(c.accent.copy(alpha = 0.14f)).border(1.dp, c.accent, CircleShape), contentAlignment = Alignment.Center) {
        Text(initials, color = c.accent, fontSize = (size.value * 0.3f).sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Incoming request over the Home screen. The ring counts down [PreviewConfig.REQUEST_TIMEOUT_SECONDS];
 * running out counts as a decline.
 */
@Composable
fun TripRequestOverlay(request: TripRequest, onAccept: () -> Unit, onDecline: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val total = PreviewConfig.REQUEST_TIMEOUT_SECONDS
    var left by remember(request.id) { mutableIntStateOf(total) }
    LaunchedEffect(request.id) {
        while (left > 0) { delay(1000); left-- }
        onDecline()
    }
    val money = remember(request.fare) { NumberFormat.getCurrencyInstance().format(request.fare) }
    val countdownDesc = stringResource(R.string.trip_req_seconds_desc, left)
    val ratingText = String.format("%.1f", request.rating)

    Box(modifier.fillMaxSize().background(c.bg.copy(alpha = 0.6f)).pointerInput(Unit) { detectTapGestures { } }, contentAlignment = Alignment.BottomCenter) {
        val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        Column(Modifier.fillMaxWidth().background(c.field, shape).border(1.dp, c.border, shape).navigationBarsPadding().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.trip_req_title), color = c.fg, fontSize = 22.sp, fontWeight = FontWeight.Light, modifier = Modifier.weight(1f)
                        .semantics { heading(); liveRegion = LiveRegionMode.Assertive },
                )
                Box(Modifier.size(48.dp).semantics { contentDescription = countdownDesc }, contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        drawCircle(c.line, size.minDimension / 2 - 2.dp.toPx(), style = Stroke(4.dp.toPx()))
                        drawArc(c.fill, -90f, 360f * left / total, false, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round), topLeft = androidx.compose.ui.geometry.Offset(2.dp.toPx(), 2.dp.toPx()), size = androidx.compose.ui.geometry.Size(size.width - 4.dp.toPx(), size.height - 4.dp.toPx()))
                    }
                    Text("$left", color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                RiderAvatar(request.initials)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(request.rider, color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("\u2605 $ratingText", color = c.muted, fontSize = 12.sp)
                }
                Text(money, color = c.fg, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.accent.copy(alpha = 0.10f)).border(1.dp, c.accent, RoundedCornerShape(16.dp)).padding(14.dp)) {
                Text(stringResource(R.string.trip_req_meta, request.etaMin, String.format("%.1f", request.distanceKm)), color = c.muted, fontSize = 12.sp)
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    RideIcon(RideIcons.Pin, c.accent, size = 18.dp); Text(request.pickup, color = c.fg, fontSize = 14.sp, modifier = Modifier.padding(start = 10.dp))
                }
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    RideIcon(RideIcons.Flag, c.accent, size = 18.dp); Text(request.drop, color = c.fg, fontSize = 14.sp, modifier = Modifier.padding(start = 10.dp))
                }
            }
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RideButton(stringResource(R.string.trip_decline), onDecline, style = RideButtonStyle.Outline, modifier = Modifier.weight(1f))
                RideButton(stringResource(R.string.trip_accept), onAccept, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun RequestPreview() = RideNowTheme(darkTheme = true) { TripRequestOverlay(SampleTrips.all[0], {}, {}) }
