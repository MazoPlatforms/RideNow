package com.ridenow.driver.ui.trip

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ridenow.driver.R
import com.ridenow.driver.ui.auth.PhoneAuthException
import com.ridenow.driver.ui.components.RideButton
import com.ridenow.driver.ui.components.RideButtonStyle
import com.ridenow.driver.ui.components.RideSheet
import com.ridenow.driver.ui.components.ScreenTop
import com.ridenow.driver.ui.map.*
import com.ridenow.driver.ui.rides.formatMinor
import com.ridenow.driver.ui.theme.RideTheme
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import android.content.Intent
import android.net.Uri

internal fun driverRideError(error: Exception): Int = when ((error as? PhoneAuthException)?.code) {
    "FRESH_LOCATION_NEAR_STOP_REQUIRED" -> R.string.live_near_stop
    "FRESH_SERVICE_AREA_LOCATION_REQUIRED" -> R.string.live_service_area
    "DRIVER_NOT_ELIGIBLE" -> R.string.live_ineligible
    "OFFER_EXPIRED", "OFFER_NOT_FOUND" -> R.string.live_offer_expired
    "INVALID_RIDE_PIN" -> R.string.live_invalid_pin
    "RIDE_PIN_LOCKED" -> R.string.live_pin_locked
    "PICKUP_TIME_NOT_REACHED" -> R.string.live_pickup_time
    "NO_SHOW_TOO_SOON" -> R.string.live_wait
    "INVALID_TRANSITION", "RIDE_STATE_CHANGED", "CANNOT_CANCEL" -> R.string.live_state_changed
    "IMPLAUSIBLE_LOCATION", "STALE_LOCATION", "STALE_DRIVER_LOCATION", "LOCATION_OUT_OF_ORDER" -> R.string.live_gps
    "DRIVER_BUSY" -> R.string.live_busy
    "RIDE_CASH_PENDING" -> R.string.live_pending_cash
    else -> R.string.live_error
}

@Composable
internal fun LiveOfferSheet(offer: DispatchOffer, busy: Boolean, respond: (Boolean) -> Unit) {
    val c = RideTheme.colors
    var remaining by remember(offer.id) { mutableLongStateOf(1) }
    LaunchedEffect(offer.id) {
        while (true) {
            remaining = maxOf(0, offer.expires.epochSecond - Instant.now().epochSecond)
            if (remaining == 0L) break
            delay(500)
        }
    }
    RideSheet(stringResource(R.string.live_offer), { if (!busy && remaining > 0) respond(false) }) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(offer.pickup + " → " + offer.destination, color = c.fg)
            Text(stringResource(R.string.live_offer_price, offer.distanceKm, formatMinor(offer.fareMinor)), color = c.muted)
            if (offer.returnTrip) Text(stringResource(R.string.live_return_trip), color = c.muted)
            if (remaining > 0) Text(stringResource(R.string.live_offer_seconds, remaining), color = c.muted)
            else Text(stringResource(R.string.live_offer_expired), color = c.muted)
            RideButton(stringResource(R.string.live_accept), { respond(true) }, enabled = !busy && remaining > 0, loading = busy)
            RideButton(stringResource(R.string.live_decline), { respond(false) }, enabled = !busy && remaining > 0, style = RideButtonStyle.Text)
        }
    }
}

@Composable
internal fun LiveDriverTrip(
    ride: DriverRide, busy: Boolean, stale: Boolean, error: Int?, onBack: () -> Unit, onAction: (String, String?) -> Unit,
) {
    val c = RideTheme.colors
    val context = LocalContext.current
    val location = rememberDriverMapLocation()
    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(ride.pickup, 14f) }
    var pin by remember(ride.id, ride.state) { mutableStateOf("") }
    var pending by rememberSaveable(ride.id, ride.version) { mutableStateOf<String?>(null) }
    var now by remember { mutableStateOf(Instant.now()) }
    val scheduled = ride.scheduled
    val arrived = ride.arrived
    val showNoShow = arrived != null && now >= maxOf(arrived, scheduled ?: arrived).plusSeconds(300)
    LaunchedEffect(ride.state) {
        if (ride.state == "DRIVER_ARRIVED") while (true) {
            now = Instant.now()
            delay(1_000)
        }
    }
    LaunchedEffect(ride.pickup, ride.destination, location != null) {
        val points = (listOf(ride.pickup, ride.destination) + listOfNotNull(location)).distinct()
        if (points.size >= 2) {
            val bounds = org.maplibre.android.geometry.LatLngBounds.Builder()
            points.forEach { bounds.include(it) }
            camera.animate(CameraUpdateFactory.newLatLngBounds(bounds.build(), 42))
        }
    }
    val action = when (ride.state) {
        "DRIVER_ASSIGNED" -> "EN_ROUTE" to R.string.live_en_route
        "DRIVER_ARRIVING" -> "ARRIVE" to R.string.live_arrive
        "DRIVER_ARRIVED" -> "START" to R.string.live_start
        "IN_PROGRESS" -> if (ride.returnTrip) "AT_STOP" to R.string.live_at_stop else "COMPLETE" to R.string.live_finish
        "AT_STOP", "WAITING" -> "RETURN" to R.string.live_return
        "RETURNING" -> "COMPLETE" to R.string.live_finish
        "COMPLETED" -> if (ride.payment == "DUE") "CASH" to R.string.live_cash else null
        else -> null
    }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 20.dp)) {
        ScreenTop(stringResource(R.string.ride_details), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            RideMap(Modifier.fillMaxWidth().height(232.dp), camera,
                listOf(MapPin(ride.pickup, PinKind.Pickup), MapPin(ride.destination, PinKind.Drop)),
                ride.path, myLocation = location)
            Text(ride.pickupLabel + " → " + ride.destinationLabel, color = c.fg)
            if (ride.returnTrip) Text(stringResource(R.string.live_return_trip), color = c.muted)
            ride.riderName?.let { Text(it, color = c.fg) }
            ride.passengerPhone?.let { passengerPhone ->
                RideButton(stringResource(R.string.live_call_passenger), {
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(passengerPhone)}")))
                }, enabled = !stale, style = RideButtonStyle.Outline)
            }
            if (scheduled != null) {
                val time = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                    .withZone(ZoneId.systemDefault()).format(scheduled)
                Text(time, color = c.muted)
            }
            Text(stringResource(if (ride.total == null) R.string.ride_history_estimate else R.string.ride_history_total,
                formatMinor(ride.total ?: ride.estimate)), color = c.fg)
            if (ride.payment == "DUE") Text(stringResource(R.string.ride_history_cash_due), color = c.muted)
            if (ride.state == "DRIVER_ARRIVED") {
                OutlinedTextField(pin, { pin = it.filter { ch -> ch in '0'..'9' }.take(4) },
                    modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.live_pin)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !busy)
                if (scheduled != null && now < scheduled) Text(stringResource(R.string.live_pickup_time), color = c.muted)
                if (showNoShow) RideButton(stringResource(R.string.live_no_show), { pending = "NO_SHOW" },
                    enabled = !busy && !stale, style = RideButtonStyle.Text)
            }
            if (ride.state == "AT_STOP") RideButton(stringResource(R.string.live_waiting), { onAction("WAIT", null) },
                enabled = !busy && !stale, style = RideButtonStyle.Text)
            if (stale) Text(stringResource(R.string.live_connection), color = c.muted)
            error?.let { Text(stringResource(it), color = c.fg) }
        }
        action?.let { (event, label) ->
            val pinReady = event != "START" || (pin.length == 4 && (scheduled == null || now >= scheduled))
            RideButton(stringResource(label), {
                if (event == "CASH") pending = event
                else onAction(event, if (event == "START") pin else null)
            }, enabled = !stale && !busy && pinReady, loading = busy)
        }
        if (ride.canCancel) RideButton(stringResource(R.string.live_cancel), { pending = "CANCEL" },
            enabled = !busy && !stale, style = RideButtonStyle.Text)
    }
    pending?.let { event ->
        val label = when (event) { "CASH" -> R.string.live_cash; "NO_SHOW" -> R.string.live_no_show; else -> R.string.live_cancel }
        AlertDialog(onDismissRequest = { pending = null },
            title = { Text(stringResource(label)) },
            text = { if (event == "CASH") Text(stringResource(R.string.live_confirm_cash, formatMinor(ride.total ?: ride.estimate))) },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.dialog_cancel)) } },
            confirmButton = { TextButton(enabled = !busy && !stale, onClick = { pending = null; onAction(event, null) }) {
                Text(stringResource(label))
            } })
    }
}
