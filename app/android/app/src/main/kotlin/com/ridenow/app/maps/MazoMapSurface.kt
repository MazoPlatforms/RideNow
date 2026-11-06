package com.ridenow.app.maps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.components.LocationComponentActivationOptions
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.modes.CameraMode
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
import org.maplibre.geojson.Point
import org.maplibre.geojson.Feature as MapFeature
import org.maplibre.geojson.FeatureCollection as MapFeatureCollection
import org.maplibre.geojson.LineString
import com.ridenow.app.ui.theme.RideTheme
import java.util.Locale

private val KattankudyCenter = LatLng(7.720, 81.720)

@Composable
fun MazoMapSurface(modifier: Modifier = Modifier, selectedPlace: MapPlace? = null) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val colors = RideTheme.colors
    val dark = colors.isDark
    val currentDark = rememberUpdatedState(dark)
    val currentPlace = rememberUpdatedState(selectedPlace)
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }
    var online by remember { mutableStateOf(isOnline(context)) }
    var mapStatus by remember { mutableStateOf("Connecting to Mazo Maps…") }
    var route by remember { mutableStateOf<MapRoute?>(null) }
    var routeError by remember { mutableStateOf<String?>(null) }
    var routeLoading by remember { mutableStateOf(false) }
    var selectedProfile by remember { mutableStateOf("driving") }
    var routeJob by remember { mutableStateOf<Job?>(null) }
    val currentRoute = rememberUpdatedState(route)
    val scope = rememberCoroutineScope()

    fun showCurrentLocation() {
        val currentMap = map ?: run {
            mapStatus = "The map is still loading. Try again shortly."
            return
        }
        val style = currentMap.style ?: run {
            mapStatus = "The map is still loading. Try again shortly."
            return
        }
        try {
            val component = currentMap.locationComponent
            if (!component.isLocationComponentActivated) {
                component.activateLocationComponent(LocationComponentActivationOptions.builder(context, style).build())
            }
            component.isLocationComponentEnabled = true
            component.cameraMode = CameraMode.TRACKING
            mapStatus = "Your location stays on this device."
        } catch (_: SecurityException) {
            mapStatus = "Location permission is needed to show your position."
        } catch (_: Exception) {
            mapStatus = "Your current location is not available yet."
        }
    }

    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) showCurrentLocation()
        else mapStatus = "Location permission was denied. Place search remains available."
    }

    LaunchedEffect(map, dark) {
        val currentMap = map ?: return@LaunchedEffect
        styleReady = false
        val url = if (dark) "${MazoMapsClient.baseUrl}/style/dark.json" else "${MazoMapsClient.baseUrl}/style.json"
        currentMap.setStyle(Style.Builder().fromUri(url)) { style ->
            styleReady = true
            mapStatus = "Mazo Maps · place coverage"
            applySelectedPlace(style, currentPlace.value, currentDark.value)
            applyRoute(style, currentRoute.value, currentDark.value)
            val hasLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (hasLocation) {
                runCatching {
                    val component = currentMap.locationComponent
                    if (!component.isLocationComponentActivated) {
                        component.activateLocationComponent(LocationComponentActivationOptions.builder(context, style).build())
                    }
                    component.isLocationComponentEnabled = true
                }
            }
        }
    }

    LaunchedEffect(map, selectedPlace) {
        routeJob?.cancel()
        route = null
        routeError = null
        val currentMap = map ?: return@LaunchedEffect
        val style = currentMap.style ?: return@LaunchedEffect
        applySelectedPlace(style, selectedPlace, dark)
        applyRoute(style, route, dark)
        selectedPlace?.let {
            currentMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 15.0))
        }
    }

    // The guide's /version route currently returns 404 in production. style.json exposes an ETag and
    // X-Mazo-Data-Revision today, so revalidate that small resource and refresh only on revision changes.
    LaunchedEffect(map) {
        var etag: String? = null
        var revision: String? = null
        while (isActive) {
            try {
                val latest = MazoMapsClient.checkStyleRevision(etag)
                if (latest != null) {
                    etag = latest.etag.ifBlank { etag.orEmpty() }
                    if (revision != null && latest.revision != revision) {
                        map?.setStyle(Style.Builder().fromJson(latest.styleJson)) { style ->
                            styleReady = true
                            mapStatus = "Mazo Maps · map data updated"
                            applySelectedPlace(style, currentPlace.value, currentDark.value)
                            applyRoute(style, currentRoute.value, currentDark.value)
                        }
                    }
                    revision = latest.revision
                }
            } catch (_: Exception) {
                // Keep the current style available; the next conditional check will retry in 15 seconds.
            }
            delay(15_000)
        }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            online = isOnline(context)
            delay(5_000)
        }
    }

    Box(modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                MapLibre.getInstance(viewContext.applicationContext)
                MapView(viewContext).also { view ->
                    mapView = view
                    view.onCreate(null)
                    view.getMapAsync { ready ->
                        map = ready
                        ready.uiSettings.isAttributionEnabled = true
                        ready.uiSettings.isCompassEnabled = true
                        ready.moveCamera(CameraUpdateFactory.newLatLngZoom(KattankudyCenter, 13.2))
                    }
                }
            },
        )

        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 68.dp).clip(RoundedCornerShape(12.dp))
                .background(colors.bg.copy(alpha = 0.92f)).border(1.dp, colors.border, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text("MAZO MAPS · KATTANKUDY", color = colors.fg, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp)
            Text("Place preview · roads and buildings not included", color = colors.muted, fontSize = 9.sp)
        }

        Box(
            Modifier.align(Alignment.CenterEnd).padding(end = 16.dp).size(48.dp).clip(CircleShape)
                .background(colors.bg).border(1.dp, colors.border, CircleShape)
                .clickable(role = Role.Button) {
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (granted) showCurrentLocation()
                    else locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }.semantics { contentDescription = "Show my current location" },
            contentAlignment = Alignment.Center,
        ) { Text("◎", color = colors.accent, fontSize = 25.sp, fontWeight = FontWeight.Medium) }

        if (!online || !styleReady) {
            Text(
                text = if (!online) "Offline · only cached map tiles may appear" else mapStatus,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
                    .clip(RoundedCornerShape(10.dp)).background(colors.bg.copy(alpha = 0.94f))
                    .border(1.dp, colors.border, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                color = colors.muted,
                fontSize = 10.sp,
            )
        }

        if (selectedPlace != null) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxSize().padding(start = 16.dp, end = 16.dp, bottom = 330.dp),
                verticalArrangement = Arrangement.Bottom,
            ) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.bg.copy(alpha = 0.96f))
                        .border(1.dp, colors.border, RoundedCornerShape(18.dp)).padding(14.dp),
                ) {
                    Text(selectedPlace.name, color = colors.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(selectedPlace.address ?: selectedPlace.category.orEmpty(), color = colors.muted, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("driving" to "Drive", "walking" to "Walk", "cycling" to "Cycle").forEach { (profile, label) ->
                            val selected = selectedProfile == profile
                            Box(
                                Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) colors.accent.copy(alpha = 0.18f) else colors.field)
                                    .border(1.dp, if (selected) colors.accent else colors.border, RoundedCornerShape(10.dp))
                                    .clickable { selectedProfile = profile; routeJob?.cancel(); route = null; routeError = null }
                                    .padding(vertical = 9.dp), contentAlignment = Alignment.Center,
                            ) { Text(label, color = if (selected) colors.fg else colors.muted, fontSize = 11.sp, fontWeight = FontWeight.Medium) }
                        }
                    }
                    Box(
                        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(11.dp))
                            .background(colors.accent).clickable(enabled = !routeLoading) {
                                val hasLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                if (!hasLocation) {
                                    routeError = "Allow location, then tap Preview route again. Your position stays on this device until you request a route."
                                    locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                                } else {
                                    val location = runCatching {
                                        val currentMap = map ?: return@runCatching null
                                        if (!currentMap.locationComponent.isLocationComponentActivated) {
                                            currentMap.locationComponent.activateLocationComponent(LocationComponentActivationOptions.builder(context, currentMap.style ?: return@runCatching null).build())
                                        }
                                        currentMap.locationComponent.lastKnownLocation
                                    }.getOrNull()
                                    if (location == null) {
                                        routeError = "Your current location is not available. Turn on device location and try again."
                                    } else {
                                        routeJob?.cancel()
                                        routeLoading = true
                                        routeError = null
                                        route = null
                                        routeJob = scope.launch {
                                            try {
                                                val response = MazoMapsClient.requestRoute(location.longitude, location.latitude, selectedPlace, selectedProfile)
                                                route = response
                                                map?.style?.let { applyRoute(it, response, currentDark.value) }
                                            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                                throw cancelled
                                            } catch (failure: Exception) {
                                                routeError = failure.message ?: "Route preview is unavailable."
                                            } finally {
                                                routeLoading = false
                                            }
                                        }
                                    }
                                }
                            }.padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(if (routeLoading) "Finding a route…" else "Preview route", color = colors.onAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    route?.let { result ->
                        Text("${formatDistance(result.distanceMeters)} · about ${maxOf(1, (result.durationSeconds / 60).toInt())} min · local estimate", color = colors.fg, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                    routeError?.let { Text(it, color = colors.danger, fontSize = 10.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 8.dp)) }
                }
            }
        }
    }

    DisposableEffect(mapView, lifecycle) {
        val view = mapView
        if (view == null) return@DisposableEffect onDispose { }
        var started = false
        var resumed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (!started) { view.onStart(); started = true }
                Lifecycle.Event.ON_RESUME -> if (!resumed) { view.onResume(); resumed = true }
                Lifecycle.Event.ON_PAUSE -> if (resumed) { view.onPause(); resumed = false }
                Lifecycle.Event.ON_STOP -> if (started) { view.onStop(); started = false }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) { view.onStart(); started = true }
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) { view.onResume(); resumed = true }
        onDispose {
            lifecycle.removeObserver(observer)
            if (resumed) view.onPause()
            if (started) view.onStop()
            view.onDestroy()
        }
    }
}

private fun applyRoute(style: Style, route: MapRoute?, dark: Boolean) {
    val collection = route?.let {
        MapFeatureCollection.fromFeatures(arrayOf(MapFeature.fromGeometry(it.geometry)))
    } ?: MapFeatureCollection.fromFeatures(emptyArray())
    val source = style.getSourceAs<GeoJsonSource>("ridenow-route")
    if (source == null) {
        style.addSource(GeoJsonSource("ridenow-route", collection))
        style.addLayer(LineLayer("ridenow-route-line", "ridenow-route").withProperties(
            lineColor(if (dark) "#FEC604" else "#8A5A00"), lineWidth(5f),
        ))
    } else source.setGeoJson(collection)
}

private fun formatDistance(meters: Double): String = if (meters < 1000) "${meters.toInt()} m" else String.format(Locale.US, "%.1f km", meters / 1000)

private fun applySelectedPlace(style: Style, place: MapPlace?, dark: Boolean) {
    val feature = place?.let {
        Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude)).apply {
            addStringProperty("id", it.id)
            addStringProperty("name", it.name)
        }
    }
    val collection = if (feature == null) FeatureCollection.fromFeatures(emptyArray()) else FeatureCollection.fromFeatures(arrayOf(feature))
    val source = style.getSourceAs<GeoJsonSource>("ridenow-selected-place")
    if (source == null) {
        style.addSource(GeoJsonSource("ridenow-selected-place", collection))
        style.addLayer(
            CircleLayer("ridenow-selected-place-circle", "ridenow-selected-place").withProperties(
                circleRadius(9f),
                circleColor(if (dark) "#FEC604" else "#8A5A00"),
                circleStrokeColor(if (dark) "#0A0C10" else "#FFFFFF"),
                circleStrokeWidth(3f),
            ),
        )
    } else source.setGeoJson(collection)
}

private fun isOnline(context: Context): Boolean {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val network = manager.activeNetwork ?: return false
    return manager.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}
