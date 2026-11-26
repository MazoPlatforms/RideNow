package com.ridenow.app.ui.rides

import android.content.Context
import com.ridenow.app.ui.auth.PhoneAuthApi
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

internal object RideHistoryApi {
    suspend fun list(context: Context, cursor: String?): RidePage {
        val path = "/v1/rides?app=RIDER&limit=30" + (cursor?.let { "&cursor=$it" } ?: "")
        return PhoneAuthApi.authorized(context, "GET", path) { result ->
            val items = result.getJSONArray("items")
            RidePage(List(items.length()) { parse(items.getJSONObject(it)) }, result.optValue("nextCursor"))
        }
    }

    suspend fun cancelScheduled(context: Context, id: String, requestId: String) {
        PhoneAuthApi.authorized(context, "POST", "/v1/rides/$id/cancel",
            JSONObject().put("reason", "Rider cancelled scheduled ride").put("expectedState", "SCHEDULED"), requestId) { Unit }
    }

    private fun parse(item: JSONObject): RideItem {
        val state = item.getString("state")
        val status = when (state) {
            "COMPLETED" -> RideStatus.Completed
            "SCHEDULED" -> RideStatus.Scheduled
            "CANCELLED", "NO_DRIVER", "NO_SHOW" -> RideStatus.Cancelled
            else -> RideStatus.Active
        }
        val points = item.getJSONArray("waypoints")
        val isReturn = item.optString("trip_type") == "RETURN"
        val dropIndex = if (isReturn) points.length() - 2 else points.length() - 1
        val date = Instant.parse(item.optValue("scheduled_at") ?: item.getString("created_at"))
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        val amountMinor = item.optValue("final_minor") ?: item.optValue("estimated_minor")
        val fare = NumberFormat.getCurrencyInstance().apply {
            currency = Currency.getInstance("LKR")
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        return RideItem(
            id = item.getString("id"),
            date = formatter.format(date.atZone(ZoneId.systemDefault())),
            pickup = pointLabel(points, 0),
            destination = pointLabel(points, dropIndex),
            meta = item.getString("category"),
            fare = amountMinor?.toLongOrNull()?.let { fare.format(it.toBigDecimal().movePointLeft(2)) } ?: "—",
            status = status,
            payment = item.optValue("payment_status"),
            distanceMeters = item.optValue("distance_meters")?.toDoubleOrNull(),
        )
    }

    private fun pointLabel(points: JSONArray, index: Int): String {
        val point = points.optJSONArray(index) ?: return "—"
        return String.format(Locale.ROOT, "%.5f, %.5f", point.getDouble(1), point.getDouble(0))
    }

    private fun JSONObject.optValue(key: String): String? = if (isNull(key)) null else getString(key)
}

internal data class RidePage(val items: List<RideItem>, val nextCursor: String?)
