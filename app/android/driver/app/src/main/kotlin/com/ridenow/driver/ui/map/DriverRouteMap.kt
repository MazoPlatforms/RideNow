package com.ridenow.driver.ui.map

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ridenow.driver.ui.system.rememberLocationEnabled
import com.ridenow.driver.ui.util.hasLocationPermission
import org.maplibre.android.geometry.LatLngBounds
import kotlinx.coroutines.delay

internal val defaultMapCenter get() = LatLng(7.6842, 81.7335)

@SuppressLint("MissingPermission")
@Composable
internal fun rememberDriverMapLocation(permissionGranted: Boolean? = null): LatLng? {
    val context = LocalContext.current
    val enabled = rememberLocationEnabled()
    val granted = permissionGranted ?: hasLocationPermission(context)
    var fix by remember(enabled, granted) { mutableStateOf<android.location.Location?>(null) }
    DisposableEffect(context, enabled, granted) {
        val client = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
        val cancellation = com.google.android.gms.tasks.CancellationTokenSource()
        var active = true
        fun accept(location: android.location.Location?) {
            if (!active || location == null || !location.hasAccuracy() || location.accuracy > 100f) return
            val ageNanos = android.os.SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
            if (ageNanos !in 0..30_000_000_000L || !location.latitude.isFinite() || !location.longitude.isFinite()) return
            fix = android.location.Location(location)
        }
        val callback = object : com.google.android.gms.location.LocationCallback() {
            override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                result.locations.filter { it.hasAccuracy() && it.accuracy <= 100f }.minByOrNull { it.accuracy }?.let(::accept)
            }
        }
        if (enabled && granted) {
            try {
                client.lastLocation.addOnSuccessListener(::accept)
                client.getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, cancellation.token)
                    .addOnSuccessListener(::accept)
                val request = com.google.android.gms.location.LocationRequest.Builder(
                    com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 4_000L,
                ).setMinUpdateIntervalMillis(1_500L).setMinUpdateDistanceMeters(3f).build()
                client.requestLocationUpdates(request, callback, android.os.Looper.getMainLooper())
            } catch (_: SecurityException) {
                fix = null
            }
        } else {
            fix = null
        }
        onDispose {
            active = false
            cancellation.cancel()
            client.removeLocationUpdates(callback)
        }
    }
    LaunchedEffect(fix) {
        val observed = fix ?: return@LaunchedEffect
        while (fix === observed) {
            val ageNanos = android.os.SystemClock.elapsedRealtimeNanos() - observed.elapsedRealtimeNanos
            if (ageNanos > 30_000_000_000L) { fix = null; break }
            delay(5_000L)
        }
    }
    return fix?.let { LatLng(it.latitude, it.longitude) }
}

/** An address-based preview is drawn only when the backend resolves both locations. */
@Composable
internal fun DriverRouteMap(
    pickup: String, drop: String, modifier: Modifier = Modifier,
    location: LatLng? = null, bottomPadding: PaddingValues = PaddingValues(),
) {
    val context = LocalContext.current
    var pickupPoint by remember(pickup) { mutableStateOf<LatLng?>(null) }
    var dropPoint by remember(drop) { mutableStateOf<LatLng?>(null) }
    var route by remember(pickup, drop) { mutableStateOf<RouteInfo?>(null) }
    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(defaultMapCenter, 14f) }
    LaunchedEffect(context, pickup, drop) {
        val from = SifricApi.geocode(context, pickup)
        val to = SifricApi.geocode(context, drop)
        pickupPoint = from
        dropPoint = to
        if (from != null && to != null) route = SifricApi.route(context, from, to)
    }
    LaunchedEffect(pickupPoint, dropPoint, route, location != null) {
        val points = listOfNotNull(pickupPoint, dropPoint) + route?.points.orEmpty()
        val distinct = points.distinct()
        if (distinct.size >= 2) {
            val bounds = LatLngBounds.Builder()
            distinct.forEach { bounds.include(it) }
            camera.animate(CameraUpdateFactory.newLatLngBounds(bounds.build(), 36))
        } else {
            val center = distinct.firstOrNull() ?: location ?: defaultMapCenter
            camera.animate(CameraUpdateFactory.newLatLngZoom(center, 14f))
        }
    }
    RideMap(modifier, camera, listOfNotNull(pickupPoint?.let { MapPin(it, PinKind.Pickup) },
        dropPoint?.let { MapPin(it, PinKind.Drop) }), route?.points.orEmpty(), myLocation = location,
        padding = bottomPadding)
}
