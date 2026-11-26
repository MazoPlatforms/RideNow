package com.ridenow.driver.ui.rides

import android.content.Context
import com.ridenow.driver.ui.auth.PhoneAuthApi
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Currency
import java.util.Locale

internal object RideHistoryApi {
    suspend fun list(context: Context, cursor: String?, limit: Int = 30): DriverRidePage {
        val path = "/v1/rides?app=DRIVER&limit=$limit" + (cursor?.let { "&cursor=$it" } ?: "")
        val result = PhoneAuthApi.authorized(context, "GET", path)
        val items = result.getJSONArray("items")
        return DriverRidePage(List(items.length()) { parse(items.getJSONObject(it)) }, result.optValue("nextCursor"))
    }

    private fun parse(row: JSONObject): DriverHistoryRide {
        val state = row.getString("state")
        val date = row.optValue("started_at") ?: row.optValue("scheduled_at") ?: row.getString("created_at")
        val local = Instant.parse(date).atZone(ZoneId.systemDefault())
        val points = row.getJSONArray("waypoints")
        val labels = row.optJSONArray("waypoint_labels")
        val dropIndex = points.length() - if (row.optString("trip_type") == "RETURN") 2 else 1
        return DriverHistoryRide(
            id = row.getString("id"),
            state = state,
            ageDays = ChronoUnit.DAYS.between(local.toLocalDate(), LocalDate.now()).toInt(),
            startMinute = local.hour * 60 + local.minute,
            pickup = labels?.optString(0)?.takeIf(String::isNotBlank) ?: pointLabel(points, 0),
            drop = labels?.optString(1)?.takeIf(String::isNotBlank) ?: pointLabel(points, dropIndex),
            category = row.getString("category"),
            amountMinor = (row.optValue("final_minor") ?: row.optValue("estimated_minor"))?.toLongOrNull(),
            payment = row.optValue("payment_status"),
        )
    }

    private fun pointLabel(points: JSONArray, index: Int): String {
        val point = points.optJSONArray(index) ?: return "—"
        return String.format(Locale.ROOT, "%.5f, %.5f", point.getDouble(1), point.getDouble(0))
    }

    private fun JSONObject.optValue(key: String): String? = if (isNull(key)) null else getString(key)
}

internal data class DriverRidePage(val items: List<DriverHistoryRide>, val nextCursor: String?)

internal data class DriverHistoryRide(
    val id: String, val state: String, val ageDays: Int, val startMinute: Int, val pickup: String,
    val drop: String, val category: String, val amountMinor: Long?, val payment: String?,
) {
    val completed: Boolean get() = state == "COMPLETED"
    val cancelled: Boolean get() = state in setOf("CANCELLED", "NO_DRIVER", "NO_SHOW")
    val fare: String get() = amountMinor?.let(::formatMinor) ?: "—"
}

internal fun formatMinor(minor: Long): String = NumberFormat.getCurrencyInstance()
    .apply {
        currency = Currency.getInstance("LKR")
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    .format(minor.toBigDecimal().movePointLeft(2))
