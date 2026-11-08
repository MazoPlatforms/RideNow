package com.ridenow.app.ui.map

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Public, read-only Mazo Maps data. This is NOT a RideNow booking/driver backend. */
const val MAZO_MAPS_BASE = "https://maps.mazo.workers.dev/api/v1"

data class MapCoordinate(val longitude: Double, val latitude: Double) {
    val valid: Boolean get() = longitude.isFinite() && latitude.isFinite() &&
        longitude in -180.0..180.0 && latitude in -90.0..90.0
    fun label(): String = "%.5f, %.5f".format(java.util.Locale.US, latitude, longitude)
}

val KATTANKUDY_MAP_CENTER = MapCoordinate(81.727, 7.703)

data class MazoPlace(
    val id: String, val name: String, val address: String?,
    val point: MapCoordinate,
)

data class MazoRoute(
    val coordinates: List<MapCoordinate>,
    val distanceMeters: Double,
    val durationSeconds: Double,
)

class MazoMapException(message: String) : IOException(message)

object MazoMapsApi {
    private val client = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS).build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private data class Result(val status: Int, val body: String, val retryAfter: String?, val etag: String?)

    private suspend fun request(request: Request): Result = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val value = Result(it.code, it.body?.string().orEmpty(),
                        it.header("Retry-After"), it.header("ETag"))
                    if (continuation.isActive) continuation.resume(value)
                }
            }
        })
    }

    private fun requireSuccess(result: Result, action: String) {
        when (result.status) {
            in 200..299 -> return
            400 -> throw MazoMapException("$action: invalid map request.")
            409 -> throw MazoMapException("Map data changed. Please retry your selection.")
            422 -> throw MazoMapException("Outside the supported Kattankudy routing area, or roads are not connected.")
            429 -> throw MazoMapException("Map service is busy. Retry after ${result.retryAfter ?: "a short while"} seconds.")
            503 -> throw MazoMapException("Mazo Maps is temporarily unavailable.")
            else -> throw MazoMapException("$action failed (HTTP ${result.status}).")
        }
    }

    private fun coordinates(json: JSONArray?): MapCoordinate? {
        if (json == null || json.length() < 2) return null
        return MapCoordinate(json.optDouble(0, Double.NaN), json.optDouble(1, Double.NaN))
            .takeIf { it.valid }
    }

    /** Search is cancellable (including the in-flight HTTP call) when Compose query changes. */
    suspend fun searchPlaces(query: String): List<MazoPlace> {
        val q = query.trim().take(120)
        if (q.isEmpty()) return emptyList()
        val url = "$MAZO_MAPS_BASE/places/search?q=${URLEncoder.encode(q, "UTF-8")}&limit=15"
        val result = request(Request.Builder().url(url).get().header("Accept", "application/json").build())
        requireSuccess(result, "Place search")
        val items = JSONObject(result.body).optJSONArray("results") ?: JSONArray()
        return (0 until items.length()).mapNotNull { index ->
            val item = items.optJSONObject(index) ?: return@mapNotNull null
            val point = coordinates(item.optJSONArray("coordinates")) ?: return@mapNotNull null
            val name = item.optString("name")
            if (name.isBlank()) return@mapNotNull null
            MazoPlace(item.optString("id"), name, item.optString("address").ifBlank { null }, point)
        }
    }

    /** Route endpoints must have real GPS/map coordinates; never invent road geometry. */
    suspend fun route(start: MapCoordinate, end: MapCoordinate): MazoRoute {
        if (!start.valid || !end.valid) throw MazoMapException("Choose valid pickup and destination coordinates.")
        val input = JSONObject()
            .put("start_lon", start.longitude).put("start_lat", start.latitude)
            .put("end_lon", end.longitude).put("end_lat", end.latitude)
            .put("profile", "driving")
        val result = request(Request.Builder().url("$MAZO_MAPS_BASE/route")
            .header("Accept", "application/json").post(input.toString().toRequestBody(jsonType)).build())
        requireSuccess(result, "Route")
        val routes = JSONObject(result.body).optJSONArray("routes")
        val first = routes?.optJSONObject(0) ?: throw MazoMapException("No connected driving route in the local map data.")
        val line = first.optJSONObject("geometry")?.optJSONArray("coordinates")
            ?: throw MazoMapException("Map service returned no route geometry.")
        val points = (0 until line.length()).mapNotNull { coordinates(line.optJSONArray(it)) }
        if (points.size < 2) throw MazoMapException("Map service returned an incomplete route.")
        return MazoRoute(points, first.optDouble("distance"), first.optDouble("duration"))
    }

    /** Revision polling while the map is visible. A 304 leaves the existing style intact. */
    suspend fun revision(previousEtag: String?): Pair<String?, Boolean> {
        val versionRequest = Request.Builder().url("$MAZO_MAPS_BASE/version").get()
            .apply { if (previousEtag != null) header("If-None-Match", previousEtag) }
            .build()
        val result = request(versionRequest)
        if (result.status == 304) return previousEtag to false
        requireSuccess(result, "Map revision")
        return result.etag to (previousEtag != null && result.etag != previousEtag)
    }
}
