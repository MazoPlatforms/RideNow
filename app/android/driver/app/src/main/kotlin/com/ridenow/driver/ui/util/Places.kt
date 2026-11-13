package com.ridenow.driver.ui.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/** "City, Country" for the last known device location, or null when permission or a fix is missing. */
@SuppressLint("MissingPermission")
suspend fun currentCity(context: Context): String? = withContext(Dispatchers.IO) {
    runCatching {
        if (!hasLocationPermission(context)) return@runCatching null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val fix = lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time } ?: return@runCatching null
        @Suppress("DEPRECATION")
        val a = Geocoder(context, Locale.getDefault()).getFromLocation(fix.latitude, fix.longitude, 1)?.firstOrNull() ?: return@runCatching null
        listOfNotNull(a.locality ?: a.subAdminArea, a.countryName).joinToString(", ").ifBlank { null }
    }.getOrNull()
}

/** True when the driver allowed location "all the time" (always true below Android 10, where it is one permission). */
fun hasBackgroundLocation(context: Context): Boolean =
    hasLocationPermission(context) && (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED)
