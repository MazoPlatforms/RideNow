package com.ridenow.app.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ridenow.app.maps.MazoMapsClient
import com.ridenow.app.ui.theme.RideTheme
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory as LibreCameraUpdateFactory
import org.maplibre.android.geometry.LatLng as LibreLatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

private const val RouteSource = "ridenow-osm-route"
private const val PickupSource = "ridenow-osm-pickup"
private const val DropSource = "ridenow-osm-drop"
private const val VehicleSource = "ridenow-osm-vehicles"

/** OpenStreetMap-backed vector map rendered with MapLibre and RideNow route/pin overlays. */
@Composable
fun RideMap(
    modifier: Modifier = Modifier,
    camera: CameraPositionState,
    pins: List<MapPin> = emptyList(),
    route: List<LatLng> = emptyList(),
    myLocation: Boolean = false,
    padding: PaddingValues = PaddingValues(),
    onLoaded: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val colors = RideTheme.colors
    val dark = colors.isDark
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }

    DisposableEffect(lifecycle, mapView) {
        val view = mapView
        if (view == null) return@DisposableEffect onDispose { }
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
            lifecycle.removeObserver(observer)
            view.onPause()
            view.onStop()
            view.onDestroy()
        }
    }

    LaunchedEffect(map, dark) {
        val active = map ?: return@LaunchedEffect
        styleReady = false
        val styleUrl = if (dark) "${MazoMapsClient.baseUrl}/style/dark.json" else "${MazoMapsClient.baseUrl}/style.json"
        active.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
            styleReady = true
            addRouteAndPinLayers(style, dark)
            onLoaded()
        }
    }

    val userLocation = rememberUserLocation()
    LaunchedEffect(map, styleReady, pins, route, dark, myLocation, userLocation) {
        val activeStyle = map?.style?.takeIf { styleReady } ?: return@LaunchedEffect
        addRouteAndPinLayers(activeStyle, dark)
        updateRouteAndPins(activeStyle, pins, route, myLocation, userLocation.position, userLocation.real)
    }

    DisposableEffect(camera, map) {
        camera.applyUpdate = { update ->
            map?.animateCamera(
                LibreCameraUpdateFactory.newLatLngZoom(LibreLatLng(update.target.latitude, update.target.longitude), update.zoom.toDouble()),
                420,
            )
        }
        onDispose { camera.applyUpdate = null }
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            MapLibre.getInstance(viewContext.applicationContext)
            MapView(viewContext).also { view ->
                mapView = view
                view.onCreate(null)
                view.getMapAsync { ready ->
                    map = ready
                    ready.uiSettings.isAttributionEnabled = true
                    ready.uiSettings.isCompassEnabled = false
                    ready.moveCamera(
                        LibreCameraUpdateFactory.newLatLngZoom(
                            LibreLatLng(camera.position.target.latitude, camera.position.target.longitude),
                            camera.position.zoom.toDouble(),
                        ),
                    )
                    ready.addOnCameraMoveStartedListener { camera.sync(camera.position.target, ready.cameraPosition.zoom, true) }
                    ready.addOnCameraMoveListener {
                        ready.cameraPosition.target?.let { target ->
                            camera.sync(LatLng(target.latitude, target.longitude), ready.cameraPosition.zoom, true)
                        }
                    }
                    ready.addOnCameraIdleListener {
                        ready.cameraPosition.target?.let { target ->
                            camera.sync(LatLng(target.latitude, target.longitude), ready.cameraPosition.zoom, false)
                        }
                    }
                }
            }
        },
    )
}

private fun addRouteAndPinLayers(style: Style, dark: Boolean) {
    if (style.getSourceAs<GeoJsonSource>(RouteSource) == null) {
        style.addSource(GeoJsonSource(RouteSource, FeatureCollection.fromFeatures(emptyArray())))
        style.addLayer(LineLayer("ridenow-osm-route-line", RouteSource).withProperties(
            lineColor(if (dark) "#FEC604" else "#8A5A00"), lineWidth(5f),
        ))
    }
    addPinLayer(style, PickupSource, "ridenow-osm-pickup-layer", if (dark) "#FEC604" else "#8A5A00", if (dark) "#0A0C10" else "#FFFFFF", 7f)
    addPinLayer(style, DropSource, "ridenow-osm-drop-layer", if (dark) "#F2F4F7" else "#0A0C10", if (dark) "#0A0C10" else "#FFFFFF", 8f)
    addPinLayer(style, VehicleSource, "ridenow-osm-vehicle-layer", if (dark) "#11151C" else "#FFFFFF", if (dark) "#FEC604" else "#8A5A00", 6f)
}

private fun addPinLayer(style: Style, sourceId: String, layerId: String, fill: String, stroke: String, radius: Float) {
    if (style.getSourceAs<GeoJsonSource>(sourceId) == null) {
        style.addSource(GeoJsonSource(sourceId, FeatureCollection.fromFeatures(emptyArray())))
        style.addLayer(CircleLayer(layerId, sourceId).withProperties(
            circleColor(fill), circleRadius(radius), circleStrokeColor(stroke), circleStrokeWidth(2.5f),
        ))
    }
}

private fun updateRouteAndPins(style: Style, pins: List<MapPin>, route: List<LatLng>, myLocation: Boolean, userPosition: LatLng, hasUserPosition: Boolean) {
    val routeFeatures = if (route.size > 1) arrayOf(
        Feature.fromGeometry(LineString.fromLngLats(route.map { Point.fromLngLat(it.longitude, it.latitude) })),
    ) else emptyArray()
    style.getSourceAs<GeoJsonSource>(RouteSource)?.setGeoJson(FeatureCollection.fromFeatures(routeFeatures))

    fun features(kind: PinKind): FeatureCollection {
        val list = pins.filter { it.kind == kind }.map { Feature.fromGeometry(Point.fromLngLat(it.at.longitude, it.at.latitude)) }.toMutableList()
        if (kind == PinKind.Pickup && myLocation && hasUserPosition) list += Feature.fromGeometry(Point.fromLngLat(userPosition.longitude, userPosition.latitude))
        return FeatureCollection.fromFeatures(list.toTypedArray())
    }
    style.getSourceAs<GeoJsonSource>(PickupSource)?.setGeoJson(features(PinKind.Pickup))
    style.getSourceAs<GeoJsonSource>(DropSource)?.setGeoJson(features(PinKind.Drop))
    style.getSourceAs<GeoJsonSource>(VehicleSource)?.setGeoJson(features(PinKind.Vehicle))
}

@Composable
fun rememberUserLocation(default: LatLng = LatLng(7.72, 81.72)): UserLocation {
    val context = LocalContext.current
    var loc by remember { mutableStateOf<LatLng?>(null) }
    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    LaunchedEffect(granted) {
        if (granted) loc = lastKnownLocation(context)
    }
    return UserLocation(loc ?: default, loc != null && granted)
}

@SuppressLint("MissingPermission")
private fun lastKnownLocation(context: Context): LatLng? {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    return runCatching {
        listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter(manager::isProviderEnabled)
            .firstNotNullOfOrNull { provider -> manager.getLastKnownLocation(provider)?.let { LatLng(it.latitude, it.longitude) } }
    }.getOrNull()
}

enum class PinKind { Pickup, Drop, Vehicle }
data class MapPin(val at: LatLng, val kind: PinKind)
data class UserLocation(val position: LatLng, val real: Boolean)
