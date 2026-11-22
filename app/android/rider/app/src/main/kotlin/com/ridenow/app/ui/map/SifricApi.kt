package com.ridenow.app.ui.map

import android.content.Context
import android.net.Uri
import com.ridenow.app.ui.auth.PhoneAuthApi
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.roundToInt

data class Suggestion(val placeId: String, val name: String, val area: String, val point: LatLng)
data class RouteInfo(val points: List<LatLng>, val meters: Int, val seconds: Int) {
    val km: String get() = String.format(Locale.US, "%.1f", meters / 1000.0)
    val minutes: Int get() = (seconds / 60.0).roundToInt().coerceAtLeast(1)
}

object SifricApi {
    private fun JSONArray.point() = LatLng(getDouble(1), getDouble(0))
    private fun LatLng.coordinates() = JSONArray().put(longitude).put(latitude)
    private fun JSONObject.suggestion() = Suggestion(getString("id"), getString("name"),
        optString("address").takeUnless { it == "null" }.orEmpty(), getJSONArray("coordinates").point())

    private suspend fun reply(context: Context, path: String, body: JSONObject? = null): JSONObject? = try {
        PhoneAuthApi.authorized(context, if (body == null) "GET" else "POST", "/v1/maps$path", body) { it }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    suspend fun autocomplete(context: Context, query: String, near: LatLng? = null): List<Suggestion>? {
        val bias = near?.let { "&lon=${it.longitude}&lat=${it.latitude}" }.orEmpty()
        val data = reply(context, "/places?q=${Uri.encode(query.trim())}$bias") ?: return null
        val items = data.optJSONArray("items") ?: return null
        return (0 until items.length()).mapNotNull { index ->
            runCatching { items.getJSONObject(index).suggestion() }.getOrNull()
        }
    }

    suspend fun placeLatLng(context: Context, placeId: String): LatLng? =
        reply(context, "/place?id=${Uri.encode(placeId)}")?.optJSONObject("item")
            ?.optJSONArray("coordinates")?.point()

    suspend fun geocode(context: Context, address: String): LatLng? =
        address.trim().takeIf { it.length >= 2 }?.let { query ->
            val name = query.substringBefore(',').trim()
            val results = autocomplete(context, name).orEmpty()
            results.firstOrNull { it.name.equals(name, ignoreCase = true) }?.point
        }

    suspend fun reverse(context: Context, at: LatLng): String? =
        reply(context, "/reverse?lon=${at.longitude}&lat=${at.latitude}")
            ?.optJSONObject("item")?.optString("name")?.takeIf { it.isNotBlank() }

    suspend fun route(context: Context, from: LatLng, to: LatLng, category: String = "CAR", roundTrip: Boolean = false): RouteInfo? {
        val waypoints = JSONArray().put(from.coordinates()).put(to.coordinates())
        if (roundTrip) waypoints.put(from.coordinates())
        val data = reply(context, "/route", JSONObject().put("waypoints", waypoints).put("category", category)) ?: return null
        val legs = data.optJSONArray("legs") ?: return null
        val points = buildList {
            for (index in 0 until legs.length()) {
                val coords = legs.optJSONObject(index)?.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
                for (position in 0 until coords.length()) {
                    val point = coords.optJSONArray(position)?.point() ?: continue
                    if (lastOrNull() != point) add(point)
                }
            }
        }
        if (points.size < 2) return null
        return RouteInfo(points, data.getDouble("distanceMeters").roundToInt(), data.getDouble("durationSeconds").roundToInt())
    }
}
