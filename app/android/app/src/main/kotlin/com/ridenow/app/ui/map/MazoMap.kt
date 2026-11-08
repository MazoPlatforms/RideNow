package com.ridenow.app.ui.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ridenow.app.ui.theme.RideTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
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

/**
 * Map-only surface; never performs a RideNow backend request.
 * The published style currently contains places only, not full road/building basemap layers.
 */
@Composable
fun MazoMap(
    modifier: Modifier = Modifier,
    initialCenter: MapCoordinate? = null,
    start: MapCoordinate? = null,
    selected: MapCoordinate? = null,
    route: MazoRoute? = null,
    interactive: Boolean = true,
    showLocate: Boolean = false,
    onCenterChanged: (MapCoordinate) -> Unit = {},
    onGpsLocation: (MapCoordinate) -> Unit = {},
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val colors = RideTheme.colors
    val dark = colors.isDark
    val onCenter by rememberUpdatedState(onCenterChanged)
    val onGps by rememberUpdatedState(onGpsLocation)
    val view = remember(context) {
        MapLibre.getInstance(context.applicationContext)
        MapView(context).apply { onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var styleRevision by remember { mutableIntStateOf(0) }
    var hasLocationPermission by remember {
        mutableStateOf(context.hasMapLocationPermission())
    }
    var currentGps by remember { mutableStateOf<MapCoordinate?>(null) }
    var locateWhenReady by remember { mutableStateOf(false) }
    var centeredOnGps by remember { mutableStateOf(false) }
    var isOnline by remember { mutableStateOf(context.mapNetworkAvailable()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasLocationPermission = result.values.any { it }
        if (hasLocationPermission) locateWhenReady = true
    }

    DisposableEffect(view, owner) {
        var started = false
        var resumed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> { view.onStart(); started = true }
                Lifecycle.Event.ON_RESUME -> { view.onResume(); resumed = true }
                Lifecycle.Event.ON_PAUSE -> { view.onPause(); resumed = false }
                Lifecycle.Event.ON_STOP -> { view.onStop(); started = false }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            if (resumed) view.onPause()
            if (started) view.onStop()
            view.onDestroy()
        }
    }

    DisposableEffect(view) {
        view.getMapAsync { ready ->
            map = ready
            ready.uiSettings.isRotateGesturesEnabled = false
            ready.uiSettings.isTiltGesturesEnabled = false
            ready.uiSettings.isCompassEnabled = false
            ready.uiSettings.isScrollGesturesEnabled = interactive
            ready.uiSettings.isZoomGesturesEnabled = interactive
        }
        onDispose { map = null }
    }

    DisposableEffect(map) {
        val current = map
        if (current == null) onDispose {}
        else {
            val idle = MapLibreMap.OnCameraIdleListener {
                val pos = current.cameraPosition.target
                MapCoordinate(pos.longitude, pos.latitude).takeIf { it.valid }?.let(onCenter)
            }
            current.addOnCameraIdleListener(idle)
            onDispose { current.removeOnCameraIdleListener(idle) }
        }
    }

    LaunchedEffect(map, initialCenter) {
        val target = initialCenter ?: KATTANKUDY_MAP_CENTER
        if (target.valid) map?.moveCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(target.latitude, target.longitude), 14.0)
        )
    }

    // Reload the full public style on theme or revision change, then restore route/point overlays.
    LaunchedEffect(map, dark, styleRevision) {
        style = null
        map?.setStyle(Style.Builder().fromUri(
            if (dark) "$MAZO_MAPS_BASE/style/dark.json" else "$MAZO_MAPS_BASE/style.json"
        )) { loaded -> style = loaded }
    }

    LaunchedEffect(style, start, selected, route, currentGps, dark) {
        style?.let { active ->
            setPoint(active, "ridenow-start", start, "#FEC604")
            setPoint(active, "ridenow-selected", selected, if (dark) "#62DAC6" else "#087F8C")
            setPoint(active, "ridenow-gps", if (showLocate) currentGps else null, "#338BFF")
            setRoute(active, route)
        }
    }

    // The version check is scoped to this visible map Composable; ETag avoids reloading unchanged data.
    LaunchedEffect(Unit) {
        var etag: String? = null
        while (true) {
            delay(15_000)
            try {
                val (nextTag, changed) = MazoMapsApi.revision(etag)
                etag = nextTag
                if (changed) styleRevision++
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keep the previously drawn map if connectivity or the version endpoint fails.
            }
            isOnline = context.mapNetworkAvailable()
        }
    }

    // Use Android system location only while a visible map offers the Locate control.
    // No rider location is sent to Mazo Maps until a user selects a destination/requests a route.
    DisposableEffect(context, hasLocationPermission, showLocate) {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val point = MapCoordinate(location.longitude, location.latitude)
                if (point.valid) {
                    currentGps = point
                    onGps(point)
                }
            }
        }
        if (showLocate && hasLocationPermission) {
            try {
                listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).forEach { provider ->
                    if (manager.isProviderEnabled(provider)) {
                        manager.getLastKnownLocation(provider)?.let(listener::onLocationChanged)
                        manager.requestLocationUpdates(provider, 5_000L, 5f, listener, Looper.getMainLooper())
                    }
                }
            } catch (_: SecurityException) {
                hasLocationPermission = false
            } catch (_: IllegalArgumentException) {
                // A provider can disappear while the screen is open.
            }
        }
        onDispose { manager.removeUpdates(listener) }
    }

    LaunchedEffect(currentGps, map, locateWhenReady) {
        val point = currentGps ?: return@LaunchedEffect
        if ((locateWhenReady || (!centeredOnGps && initialCenter == null)) && showLocate) {
            map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(point.latitude, point.longitude), 15.0))
            centeredOnGps = true
            locateWhenReady = false
        }
    }

    Box(modifier.background(colors.bg)) {
        AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
        if (!isOnline) {
            Text(
                "Offline · cached map data only",
                color = colors.fg,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 62.dp)
                    .background(colors.field, CircleShape).padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
        if (showLocate) {
            Text(
                "Locate",
                color = colors.fg,
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding()
                    .padding(end = 18.dp, bottom = 246.dp).size(width = 76.dp, height = 44.dp)
                    .clip(CircleShape).background(colors.field).border(1.dp, colors.border, CircleShape)
                    .clickable {
                        if (!context.hasMapLocationPermission()) {
                            permissionLauncher.launch(arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
                            ))
                        } else {
                            hasLocationPermission = true
                            locateWhenReady = true
                        }
                    }
                    .padding(top = 12.dp),
            )
        }
    }
}

private fun setPoint(style: Style, id: String, point: MapCoordinate?, hex: String) {
    val features = if (point == null || !point.valid) emptyArray() else
        arrayOf(Feature.fromGeometry(Point.fromLngLat(point.longitude, point.latitude)))
    val collection = FeatureCollection.fromFeatures(features)
    val existing = style.getSourceAs<GeoJsonSource>(id)
    if (existing == null) {
        style.addSource(GeoJsonSource(id, collection))
        style.addLayer(CircleLayer("$id-circle", id).withProperties(
            circleColor(hex), circleRadius(8f), circleStrokeColor("#FFFFFF"), circleStrokeWidth(2f)
        ))
    } else existing.setGeoJson(collection)
}

private fun setRoute(style: Style, route: MazoRoute?) {
    val features = if (route == null || route.coordinates.size < 2) emptyArray() else arrayOf(
        Feature.fromGeometry(LineString.fromLngLats(route.coordinates.map {
            Point.fromLngLat(it.longitude, it.latitude)
        }))
    )
    val collection = FeatureCollection.fromFeatures(features)
    val existing = style.getSourceAs<GeoJsonSource>("ridenow-route")
    if (existing == null) {
        style.addSource(GeoJsonSource("ridenow-route", collection))
        style.addLayer(LineLayer("ridenow-route-line", "ridenow-route").withProperties(
            lineColor("#FEC604"), lineWidth(5f)
        ))
    } else existing.setGeoJson(collection)
}

private fun Context.hasMapLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun Context.mapNetworkAvailable(): Boolean {
    val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val active = manager.activeNetwork ?: return false
    return manager.getNetworkCapabilities(active)
        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}
