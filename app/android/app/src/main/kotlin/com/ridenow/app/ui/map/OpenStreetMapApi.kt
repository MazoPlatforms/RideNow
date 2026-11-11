package com.ridenow.app.ui.map

import com.ridenow.app.maps.MapPlace
import com.ridenow.app.maps.MazoMapsClient
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

data class Suggestion(val placeId: String, val name: String, val area: String)
data class RouteInfo(val points: List<LatLng>, val meters: Int, val seconds: Int) {
    val km: String get() = String.format(Locale.US, "%.1f", meters / 1000.0)
    val minutes: Int get() = (seconds / 60.0).roundToInt().coerceAtLeast(1)
}

/** Place search and driving routes served by Mazo Maps, using OpenStreetMap road data. */
object OpenStreetMapApi {
    private val placeCache = ConcurrentHashMap<String, MapPlace>()

    suspend fun autocomplete(query: String, near: LatLng?): List<Suggestion>? = runCatching {
        MazoMapsClient.searchPlaces(query).map { place ->
            placeCache[place.id] = place
            Suggestion(place.id, place.name, place.address.orEmpty())
        }
    }.getOrNull()

    suspend fun placeLatLng(placeId: String): LatLng? = placeCache[placeId]?.let { LatLng(it.latitude, it.longitude) }

    suspend fun geocode(address: String): LatLng? = runCatching {
        MazoMapsClient.searchPlaces(address).firstOrNull()?.let { placeCache[it.id] = it; LatLng(it.latitude, it.longitude) }
    }.getOrNull()

    suspend fun reverse(at: LatLng): String? = null

    suspend fun route(from: LatLng, to: LatLng): RouteInfo? = runCatching {
        val destination = MapPlace(
            id = "selected-${to.latitude}-${to.longitude}",
            name = "Selected destination",
            address = null,
            longitude = to.longitude,
            latitude = to.latitude,
            category = null,
        )
        val route = MazoMapsClient.requestRoute(from.longitude, from.latitude, destination, "driving")
        RouteInfo(
            points = route.geometry.coordinates().map { LatLng(it.latitude(), it.longitude()) },
            meters = route.distanceMeters.roundToInt(),
            seconds = route.durationSeconds.roundToInt(),
        )
    }.getOrNull()
}
