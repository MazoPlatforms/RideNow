package com.ridenow.driver.ui.map

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
import com.ridenow.driver.R
import com.ridenow.driver.ui.theme.LocalMapStyle
import com.ridenow.driver.ui.theme.MapStyle
import com.ridenow.driver.ui.theme.RideTheme
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdate
import org.maplibre.android.camera.CameraUpdateFactory as NativeCameraUpdates
import org.maplibre.android.camera.CameraPosition as NativeCameraPosition
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import kotlin.math.*

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
data class ServiceRadius(val center: LatLng, val kilometers: Double) {
    fun polygon(): Polygon {
        val latitude = Math.toRadians(center.latitude)
        val longitude = Math.toRadians(center.longitude)
        val distance = kilometers / 6371.0
        val ring = (0 until 64).map { vertex ->
            val bearing = 2 * PI * vertex / 64
            val lat = asin(sin(latitude) * cos(distance) + cos(latitude) * sin(distance) * cos(bearing))
            val lon = longitude + atan2(sin(bearing) * sin(distance) * cos(latitude), cos(distance) - sin(latitude) * sin(lat))
            Point.fromLngLat(Math.toDegrees(lon), Math.toDegrees(lat))
        }
        return Polygon.fromLngLats(listOf(ring + ring.first()))
    }
}

private const val STYLE = "https://maps.sifric.workers.dev/api/v1/native-style.json"
private val emptyFeatures = FeatureCollection.fromFeatures(emptyArray<Feature>())
private fun LatLng.point(): Point = Point.fromLngLat(longitude, latitude)

private fun Style.rideLayers(color: Int) {
    addSource(GeoJsonSource("ride-area", emptyFeatures))
    addLayer(FillLayer("ride-area-fill", "ride-area").withProperties(fillColor(color), fillOpacity(0.18f), fillOutlineColor(color)))
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
    area: ServiceRadius? = null,
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
    val styleUrl = "$STYLE?type=$type&theme=$theme"
    val loaded by rememberUpdatedState(onLoaded)
    val view = remember(context) {
        MapLibre.getInstance(context.applicationContext)
        MapView(context, MapLibreMapOptions.createFromAttributes(context).textureMode(true))
    }
    var map by remember(view) { mutableStateOf<MapLibreMap?>(null) }
    var style by remember(view) { mutableStateOf<Style?>(null) }
    var error by remember(view) { mutableStateOf(false) }
    var attempt by remember(view) { mutableIntStateOf(0) }

    DisposableEffect(view, lifecycle, camera) {
        var released = false
        val fail = MapView.OnDidFailLoadingMapListener { if (!released) error = true }
        view.addOnDidFailLoadingMapListener(fail)
        view.onCreate(null)
        view.getMapAsync { native ->
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
            }
        }
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
            view.removeOnDidFailLoadingMapListener(fail)
            view.onDestroy()
        }
    }
    DisposableEffect(map, styleUrl, colors.accent, attempt) {
        var current = true
        style = null
        error = false
        val native = map
        native?.setStyle(Style.Builder().fromUri(styleUrl)) { ready ->
            if (current) {
                ready.rideLayers(colors.accent.toArgb())
                style = ready
                loaded()
            }
        }
        onDispose { current = false }
    }
    LaunchedEffect(style, pins, route, myLocation, area) {
        val active = style ?: return@LaunchedEffect
        active.getSourceAs<GeoJsonSource>("ride-area")?.setGeoJson(area?.let {
            FeatureCollection.fromFeature(Feature.fromGeometry(it.polygon()))
        } ?: emptyFeatures)
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
