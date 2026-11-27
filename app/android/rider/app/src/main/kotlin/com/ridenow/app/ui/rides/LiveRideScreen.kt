package com.ridenow.app.ui.rides

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ridenow.app.R
import com.ridenow.app.ui.auth.PhoneAuthException
import com.ridenow.app.ui.components.RideButton
import com.ridenow.app.ui.components.RideButtonStyle
import com.ridenow.app.ui.components.ScreenTop
import com.ridenow.app.ui.map.*
import com.ridenow.app.ui.theme.RideTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.UUID

internal fun rideFare(minor: Long): String = "LKR " + String.format(Locale.getDefault(), "%,.2f", minor.toBigDecimal().movePointLeft(2))

internal fun rideTime(time: Instant): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withZone(ZoneId.systemDefault()).format(time)

internal fun rideError(error: Exception): Int = when ((error as? PhoneAuthException)?.code) {
    "OUTSIDE_SERVICE_AREA" -> R.string.live_outside
    "FARE_PLAN_UNAVAILABLE" -> R.string.live_vehicle_unavailable
    "ROUTE_UNAVAILABLE" -> R.string.live_no_route
    "SCHEDULE_OUT_OF_RANGE", "SCHEDULE_CONFLICT" -> R.string.live_schedule_invalid
    "QUOTE_EXPIRED", "QUOTE_NOT_FOUND", "QUOTE_USED", "QUOTE_UNAVAILABLE" -> R.string.live_quote_expired
    "ACTIVE_RIDE_EXISTS" -> R.string.live_active_exists
    "RIDE_STATE_CHANGED", "CANNOT_CANCEL", "CANNOT_RESCHEDULE" -> R.string.live_state_changed
    else -> R.string.live_error
}

@Composable
internal fun LiveRideScreen(id: String, onClose: () -> Unit, onReschedule: (LiveRide) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val c = RideTheme.colors
    val scope = rememberCoroutineScope()
    var ride by remember(id) { mutableStateOf<LiveRide?>(null) }
    var stale by remember(id) { mutableStateOf(false) }
    var error by remember(id) { mutableStateOf<Int?>(null) }
    var busy by remember(id) { mutableStateOf(false) }
    var confirmState by rememberSaveable(id) { mutableStateOf<String?>(null) }
    val receipt = rememberRideReceiptDownload(id)

    suspend fun refresh() {
        try {
            ride = LiveRideApi.get(context, id)
            stale = false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            stale = true
        }
    }

    LaunchedEffect(id, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                refresh()
                delay(if (ride?.terminal == true || ride?.state == "SCHEDULED") 8_000L else 2_500L)
            }
        }
    }

    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        ScreenTop(stringResource(R.string.text_ride_details), onClose)
        val current = ride
        if (current == null) {
            if (stale) {
                Text(stringResource(R.string.live_connection), color = c.muted)
                RideButton(stringResource(R.string.text_try_again), { scope.launch { refresh() } })
            } else CircularProgressIndicator(color = c.accent)
            return@Column
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(current.pickup, 14f) }
            LaunchedEffect(current.driverLocation, current.pickup, current.destination) {
                val points = listOf(current.pickup, current.destination) + listOfNotNull(current.driverLocation)
                val distinct = points.distinct()
                if (distinct.size >= 2) {
                    val bounds = org.maplibre.android.geometry.LatLngBounds.Builder()
                    distinct.forEach { bounds.include(it) }
                    camera.animate(CameraUpdateFactory.newLatLngBounds(bounds.build(), 46))
                }
            }
            RideMap(Modifier.fillMaxWidth().height(240.dp), camera,
                listOf(MapPin(current.pickup, PinKind.Pickup), MapPin(current.destination, PinKind.Drop)) +
                    listOfNotNull(current.driverLocation?.let { MapPin(it, PinKind.Vehicle) }),
                current.route?.points.orEmpty())
            Text(stringResource(rideStatus(current.state)), color = c.fg, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            if (stale) Text(stringResource(R.string.live_connection), color = c.muted)
            Text(current.pickupLabel + " → " + current.destinationLabel, color = c.fg)
            if (current.returnTrip) Text(stringResource(R.string.ride_return_note), color = c.muted)
            current.schedule?.let { Text(rideTime(it), color = c.muted) }
            current.driver?.let { Text(it, color = c.fg, fontWeight = FontWeight.SemiBold) }
            if (current.vehicle.isNotBlank()) Text(current.vehicle, color = c.muted)
            if (current.pin != null && !stale) {
                Text(stringResource(R.string.live_pin), color = c.muted)
                Text(current.pin, color = c.accent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            if (current.total != null) Text(stringResource(R.string.ride_history_total, rideFare(current.total)), color = c.fg)
            else Text(stringResource(R.string.ride_history_estimate, rideFare(current.estimate)), color = c.fg)
            when (current.payment) {
                "DUE" -> Text(stringResource(R.string.ride_history_cash_due), color = c.muted)
                "COLLECTED" -> Text(stringResource(R.string.ride_history_cash_collected), color = c.muted)
                "DISPUTED" -> Text(stringResource(R.string.ride_history_cash_disputed), color = c.muted)
            }
            current.charged?.let { Text(stringResource(R.string.live_fee_charged, rideFare(it)), color = c.muted) }
            error?.let { Text(stringResource(it), color = c.fg) }
            receipt.feedback?.let { Text(stringResource(it), color = c.fg) }
        }
        if (current.payment == "COLLECTED") {
            RideButton(stringResource(R.string.ride_history_receipt), receipt.start, enabled = !receipt.busy, loading = receipt.busy)
        }
        if (current.state == "SCHEDULED") {
            RideButton(stringResource(R.string.live_reschedule), { onReschedule(current) }, enabled = !busy && !stale)
        }
        if (current.canCancel) {
            RideButton(stringResource(R.string.live_cancel), { confirmState = current.state }, enabled = !busy && !stale,
                loading = busy, style = RideButtonStyle.Text)
        }
        if (confirmState != null) {
            AlertDialog(
                onDismissRequest = { confirmState = null },
                title = { Text(stringResource(R.string.live_cancel)) },
                text = {
                    if (current.fee > 0) Text(stringResource(R.string.live_fee_confirm, rideFare(current.fee)))
                    else Text(stringResource(R.string.live_cancel_confirm))
                },
                dismissButton = { TextButton(onClick = { confirmState = null }) { Text(stringResource(R.string.text_keep_ride)) } },
                confirmButton = { TextButton(onClick = {
                    val expected = confirmState
                    confirmState = null
                    if (expected != current.state || stale) {
                        error = R.string.live_state_changed
                    } else {
                        busy = true
                        error = null
                        val key = UUID.randomUUID().toString()
                        scope.launch {
                            try { LiveRideApi.cancel(context, current, key) }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (failure: Exception) { error = rideError(failure) }
                            finally { busy = false }
                            refresh()
                        }
                    }
                }) { Text(stringResource(R.string.live_cancel)) } },
            )
        }
    }
}

private fun rideStatus(state: String): Int = when (state) {
    "SCHEDULED" -> R.string.live_scheduled
    "SEARCHING" -> R.string.live_searching
    "DRIVER_ASSIGNED", "DRIVER_ARRIVING" -> R.string.live_arriving
    "DRIVER_ARRIVED" -> R.string.live_arrived
    "IN_PROGRESS" -> R.string.live_started
    "AT_STOP", "WAITING" -> R.string.live_stop
    "RETURNING" -> R.string.live_returning
    "COMPLETED" -> R.string.live_completed
    "CANCELLED" -> R.string.live_cancelled
    "NO_SHOW" -> R.string.live_no_show
    "NO_DRIVER" -> R.string.live_no_driver
    else -> R.string.text_ride_details
}
