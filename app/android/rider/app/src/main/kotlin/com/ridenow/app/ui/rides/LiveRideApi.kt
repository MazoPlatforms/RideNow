package com.ridenow.app.ui.rides

import android.content.Context
import com.ridenow.app.ui.auth.PhoneAuthApi
import com.ridenow.app.ui.map.LatLng
import com.ridenow.app.ui.map.RouteInfo
import com.ridenow.app.ui.search.RidePassenger
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

internal val rideCategories = listOf("TUK", "CAR", "VAN")

internal data class FareQuote(val id: String, val minor: Long, val expiresAt: Instant, val route: RouteInfo?, val reminderMinutes: Int)

internal data class LiveRide(
    val id: String, val state: String, val category: String, val pickup: LatLng, val destination: LatLng,
    val pickupLabel: String, val destinationLabel: String, val returnTrip: Boolean, val schedule: Instant?,
    val route: RouteInfo?, val estimate: Long, val total: Long?, val payment: String?, val pin: String?,
    val driver: String?, val vehicle: String, val driverLocation: LatLng?, val fee: Long, val charged: Long?,
) {
    val terminal get() = state in setOf("COMPLETED", "CANCELLED", "NO_DRIVER", "NO_SHOW")
    val canCancel get() = state in setOf("SCHEDULED", "SEARCHING", "DRIVER_ASSIGNED", "DRIVER_ARRIVING", "DRIVER_ARRIVED")
}

internal object LiveRideApi {
    suspend fun quote(
        context: Context, pickup: LatLng, destination: LatLng, pickupLabel: String, destinationLabel: String,
        category: String, returnTrip: Boolean, scheduledAt: String?, repeatAt: List<String> = emptyList(), repeatRule: String = "NONE", reminderMinutes: Int = 0,
    ): FareQuote {
        val data = JSONObject()
            .put("waypoints", JSONArray().put(pickup.coordinate()).put(destination.coordinate()))
            .put("waypointLabels", JSONArray().put(pickupLabel.trim().take(150).ifBlank { pickup.label() })
                .put(destinationLabel.trim().take(150).ifBlank { destination.label() }))
            .put("category", category)
            .put("tripType", if (returnTrip) "RETURN" else "ONE_WAY")
        if (scheduledAt != null) data.put("scheduledAt", scheduledAt)
        data.put("repeatAt", JSONArray(repeatAt)).put("repeatRule", repeatRule).put("reminderMinutes", reminderMinutes)
        return PhoneAuthApi.authorized(context, "POST", "/v1/fare-quotes", data) {
            FareQuote(it.getString("id"), it.getLong("estimated_minor"), Instant.parse(it.getString("expires_at")), route(it.optJSONObject("route")), it.optInt("reminder_minutes", 0))
        }
    }

    suspend fun book(context: Context, quote: FareQuote, key: String, rescheduleId: String?, passenger: RidePassenger? = null): String {
        val path = if (rescheduleId == null) "/v1/rides" else "/v1/rides/$rescheduleId/reschedule"
        val body = JSONObject().put("quoteId", quote.id)
        if (rescheduleId == null && passenger != null) body.put("passenger", JSONObject().put("name", passenger.name).put("phone", passenger.phone))
        return PhoneAuthApi.authorized(context, "POST", path, body, key) {
            it.getString("rideId")
        }
    }

    suspend fun get(context: Context, id: String? = null): LiveRide? {
        val path = "/v1/mobile/ride?app=RIDER" + (id?.let { "&id=$it" } ?: "")
        return PhoneAuthApi.authorized(context, "GET", path) { result -> result.optJSONObject("ride")?.let(::parse) }
    }

    suspend fun cancel(context: Context, ride: LiveRide, key: String = UUID.randomUUID().toString()) {
        PhoneAuthApi.authorized(context, "POST", "/v1/rides/${ride.id}/cancel",
            JSONObject().put("reason", "Cancelled by rider").put("expectedState", ride.state), key) { Unit }
    }

    private fun parse(data: JSONObject): LiveRide {
        val points = data.getJSONArray("waypoints")
        val pickup = points.getJSONArray(0).point()
        val destination = points.getJSONArray(1).point()
        val labels = data.optJSONArray("waypoint_labels")
        return LiveRide(
            data.getString("id"), data.getString("state"), data.getString("category"), pickup, destination,
            labels?.optString(0)?.takeIf(String::isNotBlank) ?: pickup.label(),
            labels?.optString(1)?.takeIf(String::isNotBlank) ?: destination.label(),
            data.getString("trip_type") == "RETURN", data.value("scheduled_at")?.let(Instant::parse),
            route(data.optJSONObject("route")), data.getLong("estimated_minor"), data.value("final_minor")?.toLong(),
            data.value("payment_status"), data.value("ridePin"), data.value("counterpart")?.takeIf(String::isNotBlank),
            listOfNotNull(data.value("color"), data.value("make"), data.value("model"), data.value("registration")).joinToString(" · "),
            data.optJSONArray("driverLocation")?.point(),
            data.optLong("cancellation_minor"), data.value("charged_minor")?.toLong(),
        )
    }

    private fun route(data: JSONObject?): RouteInfo? {
        val legs = data?.optJSONArray("legs") ?: return null
        val points = buildList {
            for (index in 0 until legs.length()) {
                val coordinates = legs.getJSONObject(index).optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
                for (i in 0 until coordinates.length()) add(coordinates.getJSONArray(i).point())
            }
        }
        return RouteInfo(points, data.optDouble("distanceMeters", 0.0).toInt(), data.optDouble("durationSeconds", 0.0).toInt())
    }

    private fun LatLng.coordinate() = JSONArray().put(longitude).put(latitude)
    private fun LatLng.label() = String.format(java.util.Locale.ROOT, "%.5f, %.5f", latitude, longitude)
    private fun JSONArray.point() = LatLng(getDouble(1), getDouble(0))
    private fun JSONObject.value(key: String): String? = if (isNull(key)) null else getString(key)
}
