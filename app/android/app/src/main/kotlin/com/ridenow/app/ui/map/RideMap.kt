package com.ridenow.app.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberMarkerState
import com.ridenow.app.ui.theme.RideTheme

enum class PinKind { Pickup, Drop, Vehicle }
data class MapPin(val at: LatLng, val kind: PinKind)
class UserLocation(val position: LatLng, val real: Boolean)

private const val DarkStyle = """[{"elementType":"geometry","stylers":[{"color":"#0e1218"}]},{"elementType":"labels.text.fill","stylers":[{"color":"#9aa3ae"}]},{"elementType":"labels.text.stroke","stylers":[{"color":"#0a0c10"}]},{"featureType":"road","elementType":"geometry","stylers":[{"color":"#1a202a"}]},{"featureType":"road.arterial","elementType":"geometry","stylers":[{"color":"#252d3a"}]},{"featureType":"road.highway","elementType":"geometry","stylers":[{"color":"#2c3442"}]},{"featureType":"water","elementType":"geometry","stylers":[{"color":"#0a1520"}]},{"featureType":"poi","stylers":[{"visibility":"off"}]},{"featureType":"transit","stylers":[{"visibility":"off"}]}]"""
private const val LightStyle = """[{"featureType":"poi","stylers":[{"visibility":"off"}]},{"featureType":"transit","stylers":[{"visibility":"off"}]}]"""

private fun dot(fill: Int, ring: Int, px: Int, center: Int? = null): BitmapDescriptor {
    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp); val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = ring; c.drawCircle(px / 2f, px / 2f, px / 2f, p)
    p.color = fill; c.drawCircle(px / 2f, px / 2f, px / 2f - px * 0.14f, p)
    if (center != null) { p.color = center; c.drawCircle(px / 2f, px / 2f, px * 0.14f, p) }
    return BitmapDescriptorFactory.fromBitmap(bmp)
}

/** Google map themed for RideNow. Pins: yellow dot (pickup), yellow marker (drop), dark dot (vehicle). */
@Composable
fun RideMap(
    modifier: Modifier = Modifier, camera: CameraPositionState, pins: List<MapPin> = emptyList(), route: List<LatLng> = emptyList(),
    myLocation: Boolean = false, padding: PaddingValues = PaddingValues(), onLoaded: () -> Unit = {},
) {
    val c = RideTheme.colors
    val mapKind = androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("ridenow", 0).getString("map_style", "Default")
    val style = remember(c.isDark) { MapStyleOptions(if (c.isDark) DarkStyle else LightStyle) }
    GoogleMap(
        modifier, cameraPositionState = camera, contentPadding = padding, onMapLoaded = onLoaded,
        properties = MapProperties(isMyLocationEnabled = myLocation, mapStyleOptions = style.takeIf { mapKind != "Satellite" }, mapType = if (mapKind == "Satellite") com.google.maps.android.compose.MapType.SATELLITE else com.google.maps.android.compose.MapType.NORMAL),
        uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false, compassEnabled = false, mapToolbarEnabled = false),
    ) {
        val d = LocalDensity.current.density
        val pickupIcon = remember(d) { dot(0xFFFEC604.toInt(), 0xFFFFFFFF.toInt(), (24 * d).toInt()) }
        val carIcon = remember(d) { dot(0xFF0A0C10.toInt(), 0xFFFFFFFF.toInt(), (30 * d).toInt(), 0xFFFEC604.toInt()) }
        val dropIcon = remember { BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW) }
        if (route.size > 1) {
            Polyline(route, color = c.accent.copy(alpha = 0.25f), width = 24f)
            Polyline(route, color = c.accent, width = 10f)
        }
        pins.forEachIndexed { i, p ->
            key(i, p.kind) {
                val st = rememberMarkerState(position = p.at)
                SideEffect { st.position = p.at }
                when (p.kind) {
                    PinKind.Drop -> Marker(state = st, icon = dropIcon)
                    PinKind.Pickup -> Marker(state = st, icon = pickupIcon, anchor = Offset(0.5f, 0.5f))
                    PinKind.Vehicle -> Marker(state = st, icon = carIcon, anchor = Offset(0.5f, 0.5f), zIndex = 2f)
                }
            }
        }
    }
}

/** The rider's current position, or a default city until permission and a fix are available. */
@SuppressLint("MissingPermission")
@Composable
fun rememberUserLocation(default: LatLng = LatLng(37.7749, -122.4194)): UserLocation {
    val context = LocalContext.current
    var loc by remember { mutableStateOf<LatLng?>(null) }
    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    LaunchedEffect(granted) {
        if (granted) LocationServices.getFusedLocationProviderClient(context).getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            .addOnSuccessListener { it?.let { l -> loc = LatLng(l.latitude, l.longitude) } }
    }
    return UserLocation(loc ?: default, loc != null && granted)
}
