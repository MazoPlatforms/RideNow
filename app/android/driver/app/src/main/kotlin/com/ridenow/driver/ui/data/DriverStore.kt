package com.ridenow.driver.ui.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ridenow.driver.ui.preview.PreviewConfig
import com.ridenow.driver.ui.trip.CompletedTrip

/**
 * Everything the new driver screens show: rides, transactions, notifications, tickets, documents, bank accounts
 * and vehicle details. It is filled with sample data while [PreviewConfig.SAMPLE_DATA] is true and starts empty
 * otherwise, so a backend can replace the loaders without touching the screens.
 */
class DriverStore {
    val sample = PreviewConfig.SAMPLE_DATA
    val rides = mutableStateListOf<RideRecord>()
    val txns = mutableStateListOf<Txn>()
    val notices = mutableStateListOf<Notice>()
    val tickets = mutableStateListOf<Ticket>()
    val docs = mutableStateListOf<VehicleDoc>()
    val banks = mutableStateListOf<BankAccountInfo>()
    var vehicle by mutableStateOf(VehicleInfo("", "", "", "", "", ""))

    var balance by mutableIntStateOf(0)
    var rating by mutableStateOf(0.0)
    var totalRides by mutableIntStateOf(0)
    var memberSince by mutableStateOf("")
    var dob by mutableStateOf("")
    var nic by mutableStateOf("")
    var prefLanguage by mutableStateOf("English")
    var photo by mutableStateOf<android.graphics.Bitmap?>(null)
    var sessionEarned by mutableIntStateOf(0)
    private var counter = 1000
    private var noticeId = 100
    private var ticketNo = 790_000

    // Payment methods the driver accepts for fares.
    var acceptCash by mutableStateOf(true)
    var acceptCard by mutableStateOf(true)
    var acceptWallet by mutableStateOf(false)

    init { reset() }

    fun reset() {
        rides.clear(); txns.clear(); notices.clear(); tickets.clear(); docs.clear(); banks.clear()
        sessionEarned = 0; balance = 0; rating = 0.0; totalRides = 0; memberSince = ""; dob = ""; nic = ""; prefLanguage = "English"; photo = null
        vehicle = VehicleInfo("", "", "", "", "", "")
        acceptCash = true; acceptCard = true; acceptWallet = false
        if (sample) loadSample()
    }

    fun addBank(holder: String, bank: String, branch: String, number: String, type: String = "Savings") {
        banks.add(BankAccountInfo(counter++, holder, bank, number, branch, type))
    }

    fun nextId(prefix: String) = "$prefix${counter++}"

    // ----- Derived figures -----
    private fun done(maxAge: Int) = rides.filter { it.status == RideStatus.Completed && it.ageDays <= maxAge }
    fun earned(maxAge: Int) = done(maxAge).sumOf { it.earned }
    fun ridesCount(maxAge: Int) = done(maxAge).size
    fun onlineMinutes(maxAge: Int) = done(maxAge).sumOf { it.durationMin } + done(maxAge).size * 6
    val weekTotal: Int get() = earned(6)
    val monthTotal: Int get() = earned(29)
    /** Lifetime earnings. The sample figure stands in for history that is not listed as rides. */
    val allTotal: Int get() = if (sample) 482_150 + sessionEarned else earned(Int.MAX_VALUE)

    // ----- Actions -----
    fun markAllNoticesRead() = notices.forEach { it.unread = false }
    val unreadCount: Int get() = notices.count { it.unread }
    fun clearNotices() = notices.clear()

    /** Adds a trip finished in the app (cash fare) to rides, transactions and today's totals. */
    fun addFinished(trip: CompletedTrip, startMin: Int) {
        val id = nextId("r")
        val km = (Math.round(((trip.fare - RideRecord.BASE_FARE) / RideRecord.PER_KM).coerceAtLeast(1.0) * 10) / 10.0)
        val minutes = (km * 3.5).toInt().coerceAtLeast(8)
        val ride = RideRecord(
            id, "#RN" + (700000 + counter++), 0, startMin, minutes, km, trip.pickup, trip.pickup, trip.drop, trip.drop,
            trip.rider, "+94 77 000 0000", RideStatus.Completed,
        )
        rides.add(0, ride)
        sessionEarned += ride.fare
        txns.add(0, Txn(nextId("t"), TxnKind.Cash, "Cash Collected (COD)", "Ride \u2022 ${trip.drop}", 0, startMin + minutes, ride.fare))
        notices.add(0, Notice(noticeId++, NoticeKind.Rides, NoticeTone.Green, "check", "Ride completed", "You've dropped off the rider.", "Just now", true))
        totalRides += 1
    }

    fun withdraw(amount: Int, bank: BankAccountInfo, nowMin: Int) {
        balance -= amount
        txns.add(0, Txn(nextId("t"), TxnKind.Payout, "Payout to Bank", "${bank.bank} \u2022 ****${bank.number.takeLast(4)}", 0, nowMin, -amount))
    }

    fun addTicket(title: String, icon: String, first: String, attachment: String? = null): Ticket {
        val now = java.util.Calendar.getInstance().let { it.get(java.util.Calendar.HOUR_OF_DAY) * 60 + it.get(java.util.Calendar.MINUTE) }
        val t = Ticket("#RN" + ticketNo++, title, icon, Calendars.dayLabel(sample, 0), Calendars.timeLabel(now), TicketStatus.Open)
        t.messages.add(ChatMessage(false, first, t.time, attachment, if (attachment != null) "245 KB" else null))
        t.messages.add(ChatMessage(true, GREETING, t.time))
        tickets.add(0, t)
        return t
    }

    private fun loadSample() {
        rating = 4.8; totalRides = 128; memberSince = "Oct 2026"; dob = "23 Apr 1995"; nic = "950423678V"; balance = 12_450
        val riders = listOf("Ahmed R.", "Nimal S.", "Priya K.", "Fathima A.", "Kumar T.", "Sara M.")
        val places = listOf(
            "Main Street, Kallady" to "Main Street, Kallady, Batticaloa", "Batticaloa Town, Bus Stand" to "Bus Stand, Batticaloa Town",
            "Kattankudy Junction" to "Kattankudy Junction, Kattankudy", "Eravur" to "Eravur Town, Batticaloa",
            "Kallady Beach" to "Kallady Beach Road, Batticaloa", "Sinna Kallady" to "Sinna Kallady, Batticaloa",
            "Oddamavadi" to "Oddamavadi, Batticaloa", "Kaluwanchikudy" to "Kaluwanchikudy, Batticaloa",
        )
        // age, start minute, duration, km, from, to, cancelled
        data class S(val age: Int, val start: Int, val dur: Int, val km: Double, val a: Int, val b: Int, val cancelled: Boolean = false, val surge: Int = 0, val promo: Int = 0)
        val list = listOf(
            S(0, 551, 26, 8.4, 0, 1), S(0, 432, 22, 5.1, 2, 3), S(0, 725, 12, 2.0, 4, 5), S(0, 480, 18, 4.2, 6, 0), S(0, 610, 33, 12.6, 6, 7, surge = 40),
            S(0, 665, 20, 6.8, 1, 2), S(0, 800, 15, 3.4, 3, 1), S(0, 870, 28, 9.1, 7, 0), S(0, 940, 17, 4.8, 5, 4, promo = 30), S(0, 1010, 24, 7.3, 0, 3),
            S(0, 1075, 14, 3.0, 2, 4), S(0, 1130, 21, 5.9, 1, 6),
            S(1, 1282, 19, 5.5, 2, 4, cancelled = true), S(1, 1038, 25, 8.0, 3, 1), S(1, 706, 16, 3.9, 0, 2), S(1, 585, 22, 6.2, 5, 7),
            S(2, 1180, 30, 10.4, 7, 1, surge = 60), S(2, 940, 18, 4.4, 4, 0), S(2, 760, 26, 8.8, 6, 2), S(3, 1210, 20, 5.0, 1, 3),
            S(3, 905, 15, 3.6, 5, 0), S(4, 1100, 28, 9.5, 2, 7), S(5, 840, 17, 4.1, 3, 6), S(6, 990, 23, 7.7, 0, 5), S(7, 770, 21, 6.0, 4, 2),
            S(9, 1060, 19, 5.2, 1, 3), S(12, 880, 27, 9.0, 7, 4), S(18, 930, 22, 6.6, 6, 0), S(25, 1000, 16, 3.7, 2, 5), S(40, 820, 25, 8.2, 3, 7),
        )
        list.forEachIndexed { i, s ->
            val rider = riders[i % riders.size]
            rides.add(RideRecord(
                "r$i", "#RN" + (728364 - i * 137), s.age, s.start, s.dur, s.km, places[s.a].first, places[s.a].second, places[s.b].first, places[s.b].second,
                rider, "+94 77 000 0000", if (s.cancelled) RideStatus.Cancelled else RideStatus.Completed, s.surge, s.promo,
            ))
        }
        txns.addAll(listOf(
            Txn("t1", TxnKind.Trip, "Trip Earnings", "Ride \u2022 Kattankudy \u2192 Batticaloa", 0, 554, 780),
            Txn("t2", TxnKind.Cash, "Cash Collected (COD)", "Ride \u2022 Oddamavadi", 0, 512, 1250),
            Txn("t3", TxnKind.Bonus, "Incentive Bonus", "10 rides completed", 0, 430, 500),
            Txn("t4", TxnKind.Payout, "Payout to Bank", "Bank Account \u2022 ****4567", 0, 135, -15000),
            Txn("t5", TxnKind.Trip, "Trip Earnings", "Ride \u2022 Kattankudy \u2192 Kaluwanchikudy", 1, 1308, 920),
            Txn("t6", TxnKind.Cancellation, "Cancellation Fee", "Driver cancelled by rider", 1, 1162, 150),
            Txn("t7", TxnKind.Trip, "Trip Earnings", "Ride \u2022 Eravur \u2192 Batticaloa Town", 1, 1038, 730),
            Txn("t8", TxnKind.Adjustment, "Fare Adjustment", "Toll correction \u2022 #RN728227", 1, 640, 100),
            Txn("t9", TxnKind.Trip, "Trip Earnings", "Ride \u2022 Kallady \u2192 Kattankudy", 2, 1180, 860),
            Txn("t10", TxnKind.Payout, "Payout to Bank", "Bank Account \u2022 ****4567", 3, 140, -9800),
        ))
        notices.addAll(listOf(
            Notice(1, NoticeKind.Rides, NoticeTone.Blue, "person", "New ride request", "Pickup: Main Street, Kallady\nDrop: Batticaloa Town", "2 min ago", true),
            Notice(2, NoticeKind.Rides, NoticeTone.Green, "check", "Ride accepted", "You've accepted the ride.", "6 min ago", true),
            Notice(3, NoticeKind.Rides, NoticeTone.Blue, "navigate", "En route to pickup", "Head to Main Street, Kallady", "8 min ago", false),
            Notice(4, NoticeKind.Rides, NoticeTone.Yellow, "pin", "Arrived at pickup", "Waiting for rider to board.", "12 min ago", false),
            Notice(5, NoticeKind.Rides, NoticeTone.Green, "play", "Ride started", "On the way to Batticaloa Town", "28 min ago", false),
            Notice(6, NoticeKind.Rides, NoticeTone.Green, "check", "Ride completed", "You've dropped off the rider.\nEarned LKR 636", "45 min ago", false),
            Notice(7, NoticeKind.System, NoticeTone.Neutral, "wallet", "Earnings added", "LKR 636 added to your balance", "45 min ago", false),
            Notice(8, NoticeKind.System, NoticeTone.Neutral, "bell", "System update", "App updated to v1.0.0", "1 day ago", false),
        ))
        fun t(id: String, title: String, icon: String, date: String, time: String, st: TicketStatus) = Ticket(id, title, icon, date, time, st)
        val t1 = t("#RN784532", "Payout not received", "wallet", "20 Oct 2026", "09:12 AM", TicketStatus.Open)
        t1.messages.addAll(listOf(
            ChatMessage(true, GREETING, "09:15 AM"),
            ChatMessage(false, "Hi, I didn't receive my payout for last week. Here is a screenshot of my earnings.", "09:18 AM", "screenshot_20261020.jpg", "245 KB"),
            ChatMessage(true, "Thanks for the screenshot. We're checking your payout details and will update you shortly. \uD83D\uDE4F", "09:25 AM"),
        ))
        val others = listOf(
            t("#RN784120", "Ride cancellation issue", "car", "18 Oct 2026", "04:36 PM", TicketStatus.InProgress),
            t("#RN783908", "App not working", "alert", "15 Oct 2026", "11:20 AM", TicketStatus.Resolved),
            t("#RN783441", "Document verification", "doc", "12 Oct 2026", "02:18 PM", TicketStatus.Open),
            t("#RN782990", "Map & Navigation issue", "map", "10 Oct 2026", "09:05 AM", TicketStatus.Resolved),
        )
        others.forEach { it.messages.add(ChatMessage(true, GREETING, it.time)) }
        tickets.add(t1); tickets.addAll(others)
    }

    companion object {
        const val GREETING = "Hi! \uD83D\uDC4B\nThank you for contacting RideNow Support. We've received your request and will check this for you. Please share any additional details or screenshots if available."
    }
}
