package com.ridenow.driver.ui.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class RideStatus { Completed, Cancelled }

/** One finished or cancelled ride. Fare = base + distance - promo + surge, so the breakdown always adds up. */
class RideRecord(
    val id: String, val ref: String, val ageDays: Int, val startMin: Int, val durationMin: Int, val km: Double,
    val pickup: String, val pickupSub: String, val drop: String, val dropSub: String,
    val rider: String, val riderRating: Double, val riderRides: Int, val riderPhone: String,
    val status: RideStatus, val surge: Int = 0, val promo: Int = 0,
) {
    val base: Int get() = BASE_FARE
    val distanceFare: Int get() = Math.round(km * PER_KM).toInt()
    val fare: Int get() = base + distanceFare + surge - promo
    val earned: Int get() = if (status == RideStatus.Completed) fare else 0
    val endMin: Int get() = startMin + durationMin
    companion object { const val BASE_FARE = 300; const val PER_KM = 40 }
}

enum class TxnKind { Trip, Cash, Bonus, Payout, Cancellation, Adjustment }

class Txn(val id: String, val kind: TxnKind, val title: String, val sub: String, val ageDays: Int, val minute: Int, val amount: Int)

enum class NoticeKind { Rides, System }
enum class NoticeTone { Blue, Green, Yellow, Neutral }

class Notice(val id: Int, val kind: NoticeKind, val tone: NoticeTone, val icon: String, val title: String, val body: String, val age: String, unread: Boolean) {
    var unread by mutableStateOf(unread)
}

enum class TicketStatus { Open, InProgress, Resolved }

class ChatMessage(val fromSupport: Boolean, val text: String, val time: String, val attachment: String? = null, val attachmentSize: String? = null)

class Ticket(val id: String, val title: String, val icon: String, val dateLabel: String, val time: String, status: TicketStatus) {
    var status by mutableStateOf(status)
    val messages = mutableStateListOf<ChatMessage>()
    val createdLabel: String get() = "$dateLabel, $time"
}

enum class DocType { License, Registration, Insurance, Emission, Photos }
enum class DocStatus { Verified, Pending, Expired }

class VehicleDoc(val type: DocType, expiry: String?, status: DocStatus, val photos: Int = 0) {
    var expiry by mutableStateOf(expiry)
    var status by mutableStateOf(status)
}

class BankAccountInfo(val id: Int, val holder: String, val bank: String, val number: String, val branch: String, val type: String, val verified: Boolean = true)

class VehicleInfo(
    type: String, number: String, color: String, model: String, year: String, fuel: String,
) {
    var type by mutableStateOf(type)
    var number by mutableStateOf(number)
    var color by mutableStateOf(color)
    var model by mutableStateOf(model)
    var year by mutableStateOf(year)
    var fuel by mutableStateOf(fuel)
}

/** Day labels for sample data are measured from a fixed date so the preview looks the same every time. */
object Calendars {
    val SAMPLE_TODAY: LocalDate = LocalDate.of(2026, 10, 20)
    fun today(sample: Boolean): LocalDate = if (sample) SAMPLE_TODAY else LocalDate.now()
    private val dayFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val timeFmt = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)
    fun dayLabel(sample: Boolean, ageDays: Int): String = today(sample).minusDays(ageDays.toLong()).format(dayFmt)
    fun timeLabel(minute: Int): String = LocalTime.of((minute / 60) % 24, minute % 60).format(timeFmt)
    fun dateTime(sample: Boolean, ageDays: Int, minute: Int): String = "${dayLabel(sample, ageDays)}, ${timeLabel(minute)}"
}
