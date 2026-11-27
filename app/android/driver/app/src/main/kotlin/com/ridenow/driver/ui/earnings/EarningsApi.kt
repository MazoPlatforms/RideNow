package com.ridenow.driver.ui.earnings

import android.content.Context
import com.ridenow.driver.ui.auth.PhoneAuthApi
import java.time.LocalDateTime

internal data class EarningsPeriod(
    val rideCount: Int, val total: Long, val previous: Long, val points: List<EarningsPoint>,
)

internal data class EarningsPoint(val at: LocalDateTime, val minor: Long)

internal object EarningsApi {
    suspend fun summary(context: Context): List<EarningsPeriod> {
        val periods = PhoneAuthApi.authorized(context, "GET", "/v1/earnings/summary").getJSONObject("periods")
        return listOf("today", "week", "month", "all").map { name ->
            val item = periods.getJSONObject(name)
            val series = item.getJSONArray("series")
            EarningsPeriod(
                item.getInt("rides"), item.getString("totalMinor").toLong(), item.getString("previousMinor").toLong(),
                List(series.length()) { index ->
                    val point = series.getJSONObject(index)
                    EarningsPoint(LocalDateTime.parse(point.getString("start")), point.getString("minor").toLong())
                },
            )
        }
    }
}
