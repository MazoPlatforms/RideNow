package com.ridenow.app.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.ridenow.app.ui.permissions.rememberDeviceLocationEnabled
import kotlin.coroutines.resume

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ridenow.app.R
import com.ridenow.app.ui.theme.LocalMapStyle
import com.ridenow.app.ui.theme.MapStyle
import com.ridenow.app.ui.theme.RideTheme
import org.maplibre.android.camera.CameraUpdate
import org.maplibre.android.camera.CameraUpdateFactory as NativeCameraUpdates
import org.maplibre.android.camera.CameraPosition as NativeCameraPosition
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.OnMapReadyCallback
import org.sifric.maps.SifricMapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

typealias LatLng = org.maplibre.android.geometry.LatLng

object CameraPosition {
    fun fromLatLngZoom(point: LatLng, zoom: Float): NativeCameraPosition =
        NativeCameraPosition.Builder().target(point).zoom(zoom.toDouble()).build()
}

object CameraUpdateFactory {
    fun newLatLngZoom(point: LatLng, zoom: Float): CameraUpdate = NativeCameraUpdates.newLatLngZoom(point, zoom.toDouble())
    fun newLatLngBounds(bounds: LatLngBounds, padding: Int): CameraUpdate = NativeCameraUpdates.newLatLngBounds(bounds, padding)
}

class CameraPositionState {
    var position by mutableStateOf(CameraPosition.fromLatLngZoom(LatLng(7.6842, 81.7335), 14f))
    var isMoving by mutableStateOf(false)
        internal set
    internal var map: MapLibreMap? = null
    private var pending: CameraUpdate? = null

    fun animate(update: CameraUpdate) {
        val native = map
        if (native == null) pending = update else native.animateCamera(update)
    }

    internal fun attach(native: MapLibreMap) {
        native.cameraPosition = position
        map = native
        pending?.let { animate(it) }
        pending = null
    }
}

@Composable
fun rememberCameraPositionState(setup: CameraPositionState.() -> Unit): CameraPositionState =
    remember { CameraPositionState().apply(setup) }

enum class PinKind { Pickup, Drop, Vehicle, Location }
data class MapPin(val at: LatLng, val kind: PinKind)

private val emptyFeatures = FeatureCollection.fromFeatures(emptyArray<Feature>())
private fun LatLng.point(): Point = Point.fromLngLat(longitude, latitude)

private fun Style.rideLayers(color: Int) {
    addSource(GeoJsonSource("ride-route", emptyFeatures))
    addSource(GeoJsonSource("ride-pins", emptyFeatures))
    addLayer(LineLayer("ride-route-shadow", "ride-route").withProperties(lineColor(color), lineWidth(11f), lineOpacity(0.3f), lineCap("round")))
    addLayer(LineLayer("ride-route-line", "ride-route").withProperties(lineColor(color), lineWidth(5f), lineCap("round"), lineJoin("round")))
    for (kind in PinKind.entries) {
        val fill = when (kind) {
            PinKind.Location -> 0xff1877f2.toInt()
            PinKind.Vehicle -> 0xff0a0c10.toInt()
            else -> color
        }
        addLayer(CircleLayer("ride-pin-${kind.name}", "ride-pins")
            .withFilter(Expression.eq(Expression.get("kind"), Expression.literal(kind.name)))
            .withProperties(circleColor(fill), circleRadius(if (kind == PinKind.Drop) 9f else 7f),
                circleStrokeColor(0xffffffff.toInt()), circleStrokeWidth(2f)))
    }
}

/** Public Sifric basemap. Ride searches and route requests use the authenticated RideNow API. */
@Composable
fun RideMap(
    modifier: Modifier = Modifier,
    camera: CameraPositionState,
    pins: List<MapPin> = emptyList(),
    route: List<LatLng> = emptyList(),
    myLocation: LatLng? = null,
    padding: PaddingValues = PaddingValues(),
    onLoaded: () -> Unit = {},
) {
    val colors = RideTheme.colors
    if (LocalInspectionMode.current) {
        Box(modifier.background(colors.field))
        return
    }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val styleChoice = LocalMapStyle.current
    val dark = styleChoice == MapStyle.Dark || colors.isDark
    val theme = if (dark) "dark" else "light"
    val type = if (styleChoice == MapStyle.Satellite) "satellite" else "default"
    val mapType = if (type == "satellite") SifricMapView.MapType.SATELLITE else SifricMapView.MapType.DEFAULT
    val mapTheme = if (theme == "dark") SifricMapView.Theme.DARK else SifricMapView.Theme.LIGHT
    val styleUrl = SifricMapView.appearanceStyleUrl(context, mapType, mapTheme)
    val loaded by rememberUpdatedState(onLoaded)
    val view = remember(context) { SifricMapView(context) }
    var map by remember(view) { mutableStateOf<MapLibreMap?>(null) }
    var style by remember(view) { mutableStateOf<Style?>(null) }
    var appliedStyleKey by remember(view) { mutableStateOf<String?>(null) }
    var error by remember(view) { mutableStateOf(false) }
    var attempt by remember(view) { mutableIntStateOf(0) }

    DisposableEffect(view, lifecycle, camera) {
        var released = false
        val mapView = (0 until view.childCount).mapNotNull { view.getChildAt(it) as? MapView }.firstOrNull()
        val fail = MapView.OnDidFailLoadingMapListener { if (!released) error = true }
        mapView?.addOnDidFailLoadingMapListener(fail)
        view.onCreate(null)
        view.load(mapType, mapTheme, OnMapReadyCallback { native ->
            if (!released) {
                native.uiSettings.isCompassEnabled = false
                native.uiSettings.isLogoEnabled = false
                native.uiSettings.isAttributionEnabled = true
                native.addOnCameraMoveStartedListener { camera.isMoving = true }
                native.addOnCameraIdleListener {
                    camera.position = native.cameraPosition
                    camera.isMoving = false
                }
                camera.attach(native)
                map = native
                native.style?.let { ready ->
                    ready.rideLayers(colors.accent.toArgb())
                    style = ready
                    appliedStyleKey = "$styleUrl|${colors.accent.toArgb()}|$attempt"
                    loaded()
                }
            }
        })
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> view.onStart()
                Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                Lifecycle.Event.ON_STOP -> view.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            released = true
            lifecycle.removeObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) view.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) view.onStop()
            camera.map = null
            camera.isMoving = false
            mapView?.removeOnDidFailLoadingMapListener(fail)
            view.onDestroy()
        }
    }
    DisposableEffect(map, styleUrl, colors.accent, attempt) {
        var current = true
        error = false
        val native = map
        val styleKey = "$styleUrl|${colors.accent.toArgb()}|$attempt"
        if (native != null && appliedStyleKey != styleKey) {
            style = null
            native.setStyle(Style.Builder().fromUri(styleUrl)) { ready ->
                if (current) {
                    ready.rideLayers(colors.accent.toArgb())
                    style = ready
                    appliedStyleKey = styleKey
                    loaded()
                }
            }
        }
        onDispose { current = false }
    }
    LaunchedEffect(style, pins, route, myLocation) {
        val active = style ?: return@LaunchedEffect
        val line = if (route.size >= 2) listOf(Feature.fromGeometry(LineString.fromLngLats(route.map { it.point() }))) else emptyList()
        val displayedPins = pins + listOfNotNull(myLocation?.let { MapPin(it, PinKind.Location) })
        active.getSourceAs<GeoJsonSource>("ride-route")?.setGeoJson(FeatureCollection.fromFeatures(line))
        active.getSourceAs<GeoJsonSource>("ride-pins")?.setGeoJson(FeatureCollection.fromFeatures(displayedPins.map { pin ->
            Feature.fromGeometry(pin.at.point()).also { it.addStringProperty("kind", pin.kind.name) }
        }))
    }

    Box(modifier.background(colors.field)) {
        AndroidView(factory = { view }, modifier = Modifier.fillMaxSize(), update = {
            val left = padding.calculateStartPadding(direction)
            val right = padding.calculateEndPadding(direction)
            with(density) {
                val actualLeft = if (direction == androidx.compose.ui.unit.LayoutDirection.Ltr) left else right
                val actualRight = if (direction == androidx.compose.ui.unit.LayoutDirection.Ltr) right else left
                map?.setPadding(actualLeft.roundToPx(), padding.calculateTopPadding().roundToPx(),
                    actualRight.roundToPx(), padding.calculateBottomPadding().roundToPx())
            }
        })
        if (error) TextButton(onClick = { attempt++ }, modifier = Modifier.align(Alignment.Center).background(colors.field)) {
            Text(stringResource(R.string.sifric_retry_map), color = colors.fg)
        }
    }
}

class UserLocation(val position: LatLng, val real: Boolean)

/** A current, accurate location fix; the default is only a map starting point, never a confirmed GPS fix. */
@SuppressLint("MissingPermission")
@Composable
fun rememberUserLocation(
    default: LatLng = LatLng(7.6842, 81.7335),
    locationOn: Boolean = rememberDeviceLocationEnabled(),
): UserLocation {
    val context = LocalContext.current
    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    var fix by remember(locationOn, granted) { mutableStateOf<Location?>(null) }
    DisposableEffect(context, granted, locationOn) {
        val client = LocationServices.getFusedLocationProviderClient(context)
        var active = true
        val cancellation = CancellationTokenSource()
        fun accept(location: Location?) {
            if (!active || location == null || !location.hasAccuracy() || location.accuracy > 100f) return
            val ageNanos = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
            if (ageNanos !in 0..30_000_000_000L || !location.latitude.isFinite() || !location.longitude.isFinite()) return
            fix = Location(location)
        }
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.filter { it.hasAccuracy() && it.accuracy <= 100f }
                    .minByOrNull { it.accuracy }?.let(::accept)
            }
        }
        if (locationOn && granted) {
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
            val ageNanos = SystemClock.elapsedRealtimeNanos() - observed.elapsedRealtimeNanos
            if (ageNanos > 30_000_000_000L) { fix = null; break }
            delay(5_000L)
        }
    }
    val point = fix?.let { LatLng(it.latitude, it.longitude) }
    return UserLocation(point ?: default, point != null && granted && locationOn)
}

/** A fresh, bounded fix for explicit location actions. Call after checking location and permissions. */
@SuppressLint("MissingPermission")
internal suspend fun fetchRiderPosition(context: android.content.Context): LatLng? =
    kotlinx.coroutines.withTimeoutOrNull(12_000L) {
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            val request = CancellationTokenSource()
            continuation.invokeOnCancellation { request.cancel() }
            LocationServices.getFusedLocationProviderClient(context)
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, request.token)
                .addOnCompleteListener { task ->
                    val fix = if (task.isSuccessful) task.result else null
                    if (continuation.isActive) continuation.resume(fix?.takeIf { it.hasAccuracy() && it.accuracy <= 100f }?.let { LatLng(it.latitude, it.longitude) })
                }
        }
    }
