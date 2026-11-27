package com.ridenow.driver.ui.trip

import android.content.Context
import android.location.Location
import com.ridenow.driver.ui.auth.PhoneAuthApi
import com.ridenow.driver.ui.map.LatLng
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.Locale

internal data class DispatchOffer(
    val id: String, val expires: Instant, val pickup: String, val destination: String,
    val distanceKm: Double, val fareMinor: Long, val returnTrip: Boolean,
)

internal data class DriverRide(
    val id: String, val state: String, val version: Int, val pickup: LatLng, val destination: LatLng,
    val pickupLabel: String, val destinationLabel: String, val returnTrip: Boolean, val path: List<LatLng>,
    val riderName: String?, val scheduled: Instant?, val arrived: Instant?, val estimate: Long,
    val total: Long?, val payment: String?, val passengerPhone: String?,
) {
    val canCancel get() = state in setOf("DRIVER_ASSIGNED", "DRIVER_ARRIVING", "DRIVER_ARRIVED")
    val needsLocation get() = state !in setOf("COMPLETED", "CANCELLED", "NO_DRIVER", "NO_SHOW")
}

internal data class DriverRideStatus(val availability: String, val ride: DriverRide?)

internal object DriverRideApi {
    suspend fun current(context: Context): DriverRideStatus {
        val data = PhoneAuthApi.authorized(context, "GET", "/v1/mobile/ride?app=DRIVER")
        return DriverRideStatus(data.getString("availability"), data.optJSONObject("ride")?.let(::ride))
    }

    suspend fun offer(context: Context): DispatchOffer? {
        val data = PhoneAuthApi.authorized(context, "GET", "/v1/mobile/ride-offers").getJSONArray("items").optJSONObject(0)
            ?: return null
        val points = data.getJSONArray("waypoints")
        return DispatchOffer(data.getString("id"), Instant.parse(data.getString("expires_at")),
            label(data, 0, points.getJSONArray(0).point()), label(data, 1, points.getJSONArray(1).point()),
            data.optDouble("distance_meters", 0.0) / 1000.0, data.getLong("estimated_minor"),
            data.getString("trip_type") == "RETURN")
    }

    suspend fun availability(context: Context, online: Boolean) {
        PhoneAuthApi.authorized(context, "PUT", "/v1/drivers/availability",
            JSONObject().put("availability", if (online) "AVAILABLE" else "OFFLINE"))
    }

    suspend fun location(context: Context, fix: Location) {
        PhoneAuthApi.authorized(context, "POST", "/v1/locations", JSONObject()
            .put("coordinates", JSONArray().put(fix.longitude).put(fix.latitude))
            .put("accuracy", fix.accuracy.toDouble())
            .put("recordedAt", Instant.ofEpochMilli(fix.time).toString())
            .put("sequence", fix.time))
    }

    suspend fun respond(context: Context, offer: DispatchOffer, accept: Boolean, key: String) {
        PhoneAuthApi.authorized(context, "POST", "/v1/driver-offers/${offer.id}/respond", JSONObject().put("accept", accept), key)
    }

    suspend fun action(context: Context, ride: DriverRide, event: String, key: String, pin: String?) {
        val payload = JSONObject().put("action", event)
        if (pin != null) payload.put("pin", pin)
        val path = if (event == "CASH") "/v1/rides/${ride.id}/cash" else "/v1/rides/${ride.id}/actions"
        PhoneAuthApi.authorized(context, "POST", path, if (event == "CASH") null else payload, key)
    }

    suspend fun cancel(context: Context, ride: DriverRide, key: String) {
        PhoneAuthApi.authorized(context, "POST", "/v1/rides/${ride.id}/cancel",
            JSONObject().put("reason", "Cancelled by driver").put("expectedState", ride.state), key)
    }

    private fun ride(data: JSONObject): DriverRide {
        val points = data.getJSONArray("waypoints")
        val pickup = points.getJSONArray(0).point()
        val destination = points.getJSONArray(1).point()
        val tripPath = buildList {
            val legs = data.optJSONObject("route")?.optJSONArray("legs") ?: return@buildList
            for (index in 0 until legs.length()) {
                val geometry = legs.getJSONObject(index).optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
                for (i in 0 until geometry.length()) add(geometry.getJSONArray(i).point())
            }
        }
        return DriverRide(data.getString("id"), data.getString("state"), data.getInt("version"), pickup, destination,
            label(data, 0, pickup), label(data, 1, destination), data.getString("trip_type") == "RETURN", tripPath,
            data.value("counterpart")?.takeIf(String::isNotBlank), data.value("scheduled_at")?.let(Instant::parse),
            data.value("arrived_at")?.let(Instant::parse), data.getLong("estimated_minor"),
            data.value("final_minor")?.toLong(), data.value("payment_status"), data.value("passengerPhone"))
    }

    private fun label(row: JSONObject, index: Int, point: LatLng): String =
        row.optJSONArray("waypoint_labels")?.optString(index)?.takeIf(String::isNotBlank)
            ?: String.format(Locale.ROOT, "%.5f, %.5f", point.latitude, point.longitude)

    private fun JSONArray.point() = LatLng(getDouble(1), getDouble(0))
    private fun JSONObject.value(key: String): String? = if (isNull(key)) null else getString(key)
}
