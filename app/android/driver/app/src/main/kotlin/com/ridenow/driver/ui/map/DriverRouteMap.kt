package com.ridenow.driver.ui.map

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.location.LocationManagerCompat
import com.ridenow.driver.ui.system.rememberLocationEnabled
import com.ridenow.driver.ui.util.hasLocationPermission
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.android.geometry.LatLngBounds
import kotlin.coroutines.resume

internal val defaultMapCenter get() = LatLng(7.6842, 81.7335)

@SuppressLint("MissingPermission")
private fun lastFix(context: Context): LatLng? {
    val manager = context.getSystemService<LocationManager>() ?: return null
    if (!hasLocationPermission(context) || !LocationManagerCompat.isLocationEnabled(manager)) return null
    return runCatching {
        manager.getProviders(true).mapNotNull { manager.getLastKnownLocation(it) }
            .filter { fix -> fix.elapsedRealtimeNanos > 0 && android.os.SystemClock.elapsedRealtimeNanos() - fix.elapsedRealtimeNanos < 120_000_000_000 }
            .maxByOrNull { it.elapsedRealtimeNanos }?.let { LatLng(it.latitude, it.longitude) }
    }.getOrNull()
}

@SuppressLint("MissingPermission")
private suspend fun currentFix(context: Context): LatLng? = withTimeoutOrNull(10_000) {
    val manager = context.getSystemService<LocationManager>() ?: return@withTimeoutOrNull null
    if (!hasLocationPermission(context) || !LocationManagerCompat.isLocationEnabled(manager)) return@withTimeoutOrNull null
    val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        .firstOrNull { manager.isProviderEnabled(it) } ?: return@withTimeoutOrNull null
    suspendCancellableCoroutine { continuation ->
        val signal = CancellationSignal()
        continuation.invokeOnCancellation { signal.cancel() }
        try {
            LocationManagerCompat.getCurrentLocation(manager, provider, signal, ContextCompat.getMainExecutor(context)) { fix ->
                if (continuation.isActive) continuation.resume(fix?.let { LatLng(it.latitude, it.longitude) })
            }
        } catch (_: RuntimeException) {
            if (continuation.isActive) continuation.resume(null)
        }
    }
}

@Composable
internal fun rememberDriverMapLocation(): LatLng? {
    val context = LocalContext.current
    val enabled = rememberLocationEnabled()
    var point by remember(enabled) { mutableStateOf<LatLng?>(null) }
    LaunchedEffect(context, enabled) {
        while (enabled) {
            point = withContext(Dispatchers.IO) { lastFix(context) ?: currentFix(context) }
            delay(10_000)
        }
    }
    return point
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
