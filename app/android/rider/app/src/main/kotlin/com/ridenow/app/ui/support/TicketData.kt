package com.ridenow.app.ui.support

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class TicketStatus { Open, InProgress, Resolved }

class SupportMessage(val fromSupport: Boolean, val text: String, val time: String, val attachment: String? = null, val attachmentSize: String? = null)

class SupportTicket(val id: String, val title: String, val icon: String, val dateLabel: String, val time: String, status: TicketStatus) {
    var status by mutableStateOf(status)
    val messages = mutableStateListOf<SupportMessage>()
    val createdLabel: String get() = "$dateLabel, $time"
}

/** The rider's support tickets for this session, with a few examples to start. */
object TicketStore {
    val tickets = mutableStateListOf<SupportTicket>()
    /** The ticket whose conversation is open. */
    var selectedId by mutableStateOf<String?>(null)
    private var ticketNo = 790_000
    private val dayFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val timeFmt = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)

    const val GREETING = "Hi! \uD83D\uDC4B\nThank you for contacting RideNow Support. We've received your request and will check this for you. Please share any additional details or screenshots if available."

    fun dayLabel(ageDays: Int): String = LocalDate.now().minusDays(ageDays.toLong()).format(dayFmt)
    fun timeLabel(minute: Int): String = LocalTime.of((minute / 60) % 24, minute % 60).format(timeFmt)
    fun nowMinute(): Int = LocalTime.now().let { it.hour * 60 + it.minute }

    fun addTicket(title: String, icon: String, first: String): SupportTicket {
        val t = SupportTicket("#RN" + ticketNo++, title, icon, dayFmt.format(LocalDate.now()), timeLabel(nowMinute()), TicketStatus.Open)
        t.messages.add(SupportMessage(false, first, t.time))
        t.messages.add(SupportMessage(true, GREETING, t.time))
        tickets.add(0, t)
        return t
    }

    init {
        fun t(id: String, title: String, icon: String, age: Int, time: String, st: TicketStatus) = SupportTicket(id, title, icon, dayLabel(age), time, st)
        val t1 = t("#RN784532", "Refund not received", "wallet", 2, "09:12 AM", TicketStatus.Open)
        t1.messages.addAll(listOf(
            SupportMessage(true, GREETING, "09:15 AM"),
            SupportMessage(false, "Hi, I was charged for a ride that was cancelled. Here is a screenshot of the charge.", "09:18 AM", "screenshot_charge.jpg", "245 KB"),
            SupportMessage(true, "Thanks for the screenshot. We're checking the charge and will update you shortly. \uD83D\uDE4F", "09:25 AM"),
        ))
        val others = listOf(
            t("#RN784120", "Driver took a different route", "car", 4, "04:36 PM", TicketStatus.InProgress),
            t("#RN783908", "App not working", "alert", 7, "11:20 AM", TicketStatus.Resolved),
            t("#RN783441", "Cannot log in", "person", 10, "02:18 PM", TicketStatus.Open),
            t("#RN782990", "Wrong pickup location", "map", 12, "09:05 AM", TicketStatus.Resolved),
        )
        others.forEach { it.messages.add(SupportMessage(true, GREETING, it.time)) }
        tickets.add(t1); tickets.addAll(others)
    }
}
