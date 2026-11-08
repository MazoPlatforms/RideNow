package com.ridenow.app.ui.map

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import com.ridenow.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.roundToInt

data class Suggestion(val placeId: String, val name: String, val area: String)
data class RouteInfo(val points: List<LatLng>, val meters: Int, val seconds: Int) {
    val km: String get() = String.format(Locale.US, "%.1f", meters / 1000.0)
    val minutes: Int get() = (seconds / 60.0).roundToInt().coerceAtLeast(1)
}

/** Thin wrappers over Places (New), Geocoding and Routes. Every call returns null on failure. */
object GoogleApi {
    private val key = BuildConfig.MAPS_API_KEY

    private suspend fun call(url: String, body: JSONObject? = null, headers: Map<String, String> = emptyMap()): JSONObject? = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 8000; c.readTimeout = 8000
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (body != null) {
                c.requestMethod = "POST"; c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val stream = if (c.responseCode in 200..299) c.inputStream else c.errorStream
            JSONObject(stream.bufferedReader().use { it.readText() })
        }.getOrNull()
    }

    private fun ll(o: JSONObject) = LatLng(o.getDouble("latitude"), o.getDouble("longitude"))

    /** Places API (New) autocomplete, biased to [near]. */
    suspend fun autocomplete(query: String, near: LatLng?): List<Suggestion>? {
        val body = JSONObject().put("input", query)
        if (near != null) body.put("locationBias", JSONObject().put("circle", JSONObject()
            .put("center", JSONObject().put("latitude", near.latitude).put("longitude", near.longitude)).put("radius", 30000.0)))
        val r = call("https://places.googleapis.com/v1/places:autocomplete", body, mapOf("X-Goog-Api-Key" to key)) ?: return null
        val arr = r.optJSONArray("suggestions") ?: return if (r.has("error")) null else emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val p = arr.getJSONObject(i).optJSONObject("placePrediction") ?: return@mapNotNull null
            val f = p.optJSONObject("structuredFormat")
            Suggestion(
                p.getString("placeId"),
                f?.optJSONObject("mainText")?.optString("text") ?: p.optJSONObject("text")?.optString("text") ?: "",
                f?.optJSONObject("secondaryText")?.optString("text") ?: "",
            )
        }
    }

    suspend fun placeLatLng(placeId: String): LatLng? =
        call("https://places.googleapis.com/v1/places/$placeId", headers = mapOf("X-Goog-Api-Key" to key, "X-Goog-FieldMask" to "location"))
            ?.optJSONObject("location")?.let { runCatching { ll(it) }.getOrNull() }

    suspend fun geocode(address: String): LatLng? =
        call("https://maps.googleapis.com/maps/api/geocode/json?address=${URLEncoder.encode(address, "UTF-8")}&key=$key")
            ?.optJSONArray("results")?.optJSONObject(0)?.optJSONObject("geometry")?.optJSONObject("location")
            ?.let { LatLng(it.getDouble("lat"), it.getDouble("lng")) }

    suspend fun reverse(at: LatLng): String? =
        call("https://maps.googleapis.com/maps/api/geocode/json?latlng=${at.latitude},${at.longitude}&key=$key")
            ?.optJSONArray("results")?.optJSONObject(0)?.optString("formatted_address")?.split(",")?.take(2)?.joinToString(",")?.trim()

    /** Routes API: driving route with live traffic. */
    suspend fun route(from: LatLng, to: LatLng): RouteInfo? {
        fun loc(p: LatLng) = JSONObject().put("location", JSONObject().put("latLng", JSONObject().put("latitude", p.latitude).put("longitude", p.longitude)))
        val body = JSONObject().put("origin", loc(from)).put("destination", loc(to)).put("travelMode", "DRIVE").put("routingPreference", "TRAFFIC_AWARE")
        val r = call(
            "https://routes.googleapis.com/directions/v2:computeRoutes", body,
            mapOf("X-Goog-Api-Key" to key, "X-Goog-FieldMask" to "routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline"),
        )?.optJSONArray("routes")?.optJSONObject(0) ?: return null
        val pts = PolyUtil.decode(r.getJSONObject("polyline").getString("encodedPolyline"))
        return RouteInfo(pts, r.optInt("distanceMeters"), r.optString("duration", "0s").removeSuffix("s").toDoubleOrNull()?.toInt() ?: 0)
    }
}
