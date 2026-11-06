package com.ridenow.app.maps

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.geojson.LineString
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class MapPlace(
    val id: String,
    val name: String,
    val address: String?,
    val longitude: Double,
    val latitude: Double,
    val category: String?,
)

data class MapStyleRevision(val revision: String, val etag: String, val styleJson: String)
data class MapRoute(val distanceMeters: Double, val durationSeconds: Double, val geometry: LineString)

class MazoMapsException(
    val status: Int,
    val retryAfter: String?,
    val apiCode: String?,
    message: String,
) : IOException(message)

object MazoMapsClient {
    const val baseUrl = "https://maps.mazo.workers.dev/api/v1"
    private val http = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun searchPlaces(query: String): List<MapPlace> {
        val normalized = query.trim()
        require(normalized.isNotEmpty()) { "Enter a place to search." }
        require(normalized.length <= 120) { "Search text must be 120 characters or fewer." }
        val encoded = URLEncoder.encode(normalized, "UTF-8")
        val response = execute(
            Request.Builder().url("$baseUrl/places/search?q=$encoded&limit=10")
                .header("Accept", "application/json")
                .get()
                .build(),
        )
        requireSuccess(response)
        val results: JSONArray = JSONObject(response.body).optJSONArray("results") ?: JSONArray()
        return (0 until results.length()).mapNotNull { index ->
            placeFrom(results.optJSONObject(index) ?: return@mapNotNull null)
        }
    }

    /** The version route in the guide is not yet deployed; style.json currently exposes the same revision and ETag. */
    suspend fun checkStyleRevision(etag: String?): MapStyleRevision? {
        val builder = Request.Builder().url("$baseUrl/style.json").header("Accept", "application/json").get()
        if (!etag.isNullOrBlank()) builder.header("If-None-Match", etag)
        val response = execute(builder.build())
        if (response.status == 304) return null
        requireSuccess(response)
        val style = JSONObject(response.body)
        val revision = response.revision
            ?: style.optJSONObject("metadata")?.optString("mazo:data-revision")?.takeIf(String::isNotBlank)
            ?: throw IOException("Map style did not include a data revision.")
        return MapStyleRevision(revision, response.etag.orEmpty(), response.body)
    }

    suspend fun requestRoute(startLongitude: Double, startLatitude: Double, destination: MapPlace, profile: String): MapRoute {
        require(profile in setOf("driving", "walking", "cycling")) { "Choose a supported route type." }
        val body = JSONObject()
            .put("start_lon", startLongitude)
            .put("start_lat", startLatitude)
            .put("end_lon", destination.longitude)
            .put("end_lat", destination.latitude)
            .put("profile", profile)
            .toString()
        val response = execute(
            Request.Builder().url("$baseUrl/route")
                .header("Accept", "application/json")
                .post(body.toRequestBody(jsonMedia))
                .build(),
        )
        requireSuccess(response)
        val routes = JSONObject(response.body).optJSONArray("routes") ?: JSONArray()
        val first = routes.optJSONObject(0) ?: throw IOException("No route alternatives were returned.")
        val geometry = first.optJSONObject("geometry") ?: throw IOException("The route response did not include its line geometry.")
        return MapRoute(
            distanceMeters = first.optDouble("distance", Double.NaN).takeIf(Double::isFinite)
                ?: throw IOException("The route response did not include its distance."),
            durationSeconds = first.optDouble("duration", Double.NaN).takeIf(Double::isFinite)
                ?: throw IOException("The route response did not include its estimated time."),
            geometry = LineString.fromJson(geometry.toString()),
        )
    }

    private fun placeFrom(json: JSONObject): MapPlace? {
        val coordinates = json.optJSONArray("coordinates") ?: return null
        if (coordinates.length() < 2) return null
        val lon = coordinates.optDouble(0, Double.NaN)
        val lat = coordinates.optDouble(1, Double.NaN)
        val id = json.optString("id")
        val name = json.optString("name")
        if (id.isBlank() || name.isBlank() || !lon.isFinite() || !lat.isFinite()) return null
        return MapPlace(
            id = id,
            name = name,
            address = json.optString("address").takeIf(String::isNotBlank),
            longitude = lon,
            latitude = lat,
            category = json.optString("discoveryCategory").takeIf(String::isNotBlank),
        )
    }

    private suspend fun execute(request: Request): ApiResponse = suspendCancellableCoroutine { continuation ->
        val call = http.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val result = ApiResponse(
                        status = it.code,
                        body = it.body?.string().orEmpty(),
                        etag = it.header("ETag"),
                        revision = it.header("X-Mazo-Data-Revision"),
                        retryAfter = it.header("Retry-After"),
                    )
                    if (continuation.isActive) continuation.resume(result)
                }
            }
        })
    }

    private fun requireSuccess(response: ApiResponse) {
        if (response.status in 200..299) return
        val code = runCatching { JSONObject(response.body).optString("error").takeIf(String::isNotBlank) }.getOrNull()
        val message = when (response.status) {
            400 -> "Map request was not valid."
            404 -> "Map service endpoint was not found."
            422 -> when (code) {
                "snap_too_far" -> "Route point is outside the local road network. Verified routing is currently limited to Kattankudy."
                "no_connected_route" -> "These points are not connected by the local road network. Try another destination."
                else -> "A route is not available for this location."
            }
            429 -> "Map service is busy. Try again in ${response.retryAfter ?: "a little while"}."
            503 -> "Map service is temporarily unavailable."
            else -> "Map service request failed (HTTP ${response.status})."
        }
        throw MazoMapsException(response.status, response.retryAfter, code, message)
    }

    private data class ApiResponse(
        val status: Int,
        val body: String,
        val etag: String?,
        val revision: String?,
        val retryAfter: String?,
    )
}
