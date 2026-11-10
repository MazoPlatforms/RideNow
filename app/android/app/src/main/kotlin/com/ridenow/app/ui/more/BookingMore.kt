package com.ridenow.app.ui.more

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import com.ridenow.app.ui.booking.Vehicles
import com.ridenow.app.ui.booking.money
import com.ridenow.app.ui.components.*
import com.ridenow.app.ui.theme.RideTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---------- Your route ----------

@Composable
fun YourRouteScreen(pickup: String, drop: String, onBack: () -> Unit) {
    val c = RideTheme.colors
    val stops = remember { mutableStateListOf("Nova Terminal, Gate 4") }
    var adding by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    MoreScaffold("Your route", onBack, subtitle = "Add stops along the way.", bottom = { RideButton("Done", onBack) }) {
        Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FixedStop("PICKUP", pickup)
            stops.forEachIndexed { i, s -> StopRow(i, s, stops) }
            FixedStop("DROP", drop)
        }
        if (adding) {
            RideTextField(draft, { draft = it.take(80) }, "Stop address", RideIcons.Pin, Modifier.padding(top = 14.dp))
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RideButton("Add stop", { if (draft.isNotBlank()) { stops.add(draft.trim()); draft = ""; adding = false } }, Modifier.weight(1f), enabled = draft.isNotBlank())
                RideButton("Cancel", { draft = ""; adding = false }, Modifier.weight(1f), style = RideButtonStyle.Text)
            }
        } else if (stops.size < 3) {
            Row(
                Modifier.padding(top = 14.dp).heightIn(min = 48.dp).clickable(role = Role.Button) { adding = true },
                verticalAlignment = Alignment.CenterVertically,
            ) { RideIcon(RideIcons.Plus, c.accent); Text("  Add another stop", color = c.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
        } else Text("You can add up to 3 stops.", color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp))
    }
}

@Composable
private fun FixedStop(label: String, text: String) {
    val c = RideTheme.colors
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(label, color = c.muted, fontSize = 11.sp, letterSpacing = 1.1.sp)
        Text(text, color = c.fg, fontSize = 15.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun StopRow(i: Int, text: String, stops: SnapshotStateList<String>) {
    val c = RideTheme.colors
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.field).border(1.dp, c.accent.copy(alpha = 0.5f), RoundedCornerShape(16.dp)).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
            Text("STOP ${i + 1}", color = c.muted, fontSize = 11.sp, letterSpacing = 1.1.sp)
            Text(text, color = c.fg, fontSize = 15.sp, modifier = Modifier.padding(top = 2.dp))
        }
        SmallAction(RideIcons.ChevronDown, "Move stop ${i + 1} up", 180f, i > 0) { val t = stops[i]; stops[i] = stops[i - 1]; stops[i - 1] = t }
        SmallAction(RideIcons.ChevronDown, "Move stop ${i + 1} down", 0f, i < stops.lastIndex) { val t = stops[i]; stops[i] = stops[i + 1]; stops[i + 1] = t }
        SmallAction(RideIcons.Close, "Remove stop ${i + 1}", 0f, true) { stops.removeAt(i) }
    }
}

@Composable
private fun SmallAction(icon: List<String>, label: String, rotation: Float, enabled: Boolean, onClick: () -> Unit) {
    val c = RideTheme.colors
    Box(
        Modifier.size(48.dp).clip(CircleShape).alpha(if (enabled) 1f else 0.3f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { RideIcon(icon, c.muted, Modifier.rotate(rotation), size = 20.dp) }
}

// ---------- Stop location for return trips ----------

@Composable
fun StopLocationScreen(pickup: String, drop: String, onBack: () -> Unit, onMap: () -> Unit, onDone: (String) -> Unit) {
    val c = RideTheme.colors
    var stop by rememberSaveable { mutableStateOf(drop) }
    val places = listOf("Meridian Walk", "Helix Avenue", "Nova Market", "Zenith Cafe")
    MoreScaffold("Return trip", onBack, subtitle = "Choose where you stop before heading back.", bottom = { RideButton("Set stop location", { onDone(stop) }) }) {
        Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FixedStop("PICKUP", pickup)
            FixedStop("STOP", stop)
            FixedStop("DROP", "Same as pickup")
        }
        SectionLabel("SUGGESTED")
        GroupCard {
            places.forEachIndexed { i, p ->
                if (i > 0) RowDivider()
                NavRow(RideIcons.Pin, p, { stop = p }, trailing = { if (stop == p) RideIcon(RideIcons.Check, c.accent) else Spacer(Modifier.size(20.dp)) })
            }
            RowDivider()
            NavRow(RideIcons.Map, "Choose on map", onMap)
        }
    }
}

// ---------- Vehicle info sheet ----------

@Composable
fun VehicleInfoSheet(index: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    val c = RideTheme.colors
    val i = index.coerceIn(0, Vehicles.lastIndex)
    val v = Vehicles[i]
    val blurb = when (v.name) { "Pulse" -> "Compact electric ride for up to ${v.seats}."; "Orbit" -> "Comfortable electric ride for up to ${v.seats}."; else -> "Spacious electric ride for up to ${v.seats}." }
    SheetScreen(onDismiss, v.name, blurb) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp)) {
            listOf("Seats" to "${v.seats}", "Luggage" to "${v.luggage} bags", "Pickup" to "${v.etaMin} min", "Fare" to money(v.fare)).forEach { (k, value) ->
                Column(Modifier.weight(1f)) { Text(k, color = c.muted, fontSize = 12.sp); Text(value, color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
        SectionLabel("WHAT YOU GET")
        listOf("Quiet electric drive", "Air purified cabin", "Charging port for every seat", "Child seat on request").forEach {
            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) { RideIcon(RideIcons.Check, c.accent, size = 18.dp); Text("  $it", color = c.fg, fontSize = 14.sp) }
        }
        Text("Estimated fare. Pay your driver in cash.", color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 12.dp))
        RideButton("Select ${v.name}", { onSelect(i) })
    }
}

// ---------- High demand notice ----------

@Composable
fun HighDemandSheet(vehicle: Int, onContinue: () -> Unit, onSchedule: () -> Unit, onDismiss: () -> Unit) {
    val c = RideTheme.colors
    val v = Vehicles[vehicle.coerceIn(0, Vehicles.lastIndex)]
    var notify by rememberSaveable { mutableStateOf(false) }
    SheetScreen(onDismiss) {
        Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(RideIcons.Alert, c.accent, 44.dp)
            Text("HIGH DEMAND", color = c.accent, fontSize = 12.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp))
        }
        Text("Fares are higher than usual.", color = c.fg, fontSize = 24.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(top = 14.dp).semantics { heading() })
        Text("More riders than drivers nearby. Fares update as demand changes.", color = c.muted, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 6.dp))
        Column(Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 8.dp)) {
            InfoRow("Usual fare", money(v.fare / 1.4))
            InfoRow("1.4× right now", money(v.fare), strong = true, valueColor = c.accent)
        }
        RideButton("Continue at ${money(v.fare)}", onContinue, Modifier.padding(top = 16.dp))
        RideButton(if (notify) "We will notify you" else "Notify me when fares drop", { notify = !notify }, style = RideButtonStyle.Text)
        RideButton("Schedule for later", onSchedule, style = RideButtonStyle.Text)
    }
}

// ---------- Date and time pieces shared by the pickers ----------

fun pickLabel(date: LocalDate, hour: Int, minute: Int, pm: Boolean): String =
    date.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())) + ", %d:%02d %s".format(hour, minute, if (pm) "PM" else "AM")

@Composable
fun DateTimePicker(
    date: LocalDate, onDate: (LocalDate) -> Unit, hour: Int, onHour: (Int) -> Unit, minute: Int, onMinute: (Int) -> Unit,
    pm: Boolean, onPm: (Boolean) -> Unit, onMoreDates: (() -> Unit)? = null,
) {
    val c = RideTheme.colors
    val today = remember { LocalDate.now() }
    val days = remember(date) { val base = (0..4).map { today.plusDays(it.toLong()) }; if (date in base) base else base.take(4) + date }
    SectionLabel("DATE")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        days.forEach { d ->
            val name = if (d == today) "Today" else d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault())
            Chip("$name ${d.dayOfMonth}", d == date, { onDate(d) })
        }
    }
    if (onMoreDates != null) Row(Modifier.padding(top = 4.dp).heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onMoreDates), verticalAlignment = Alignment.CenterVertically) {
        RideIcon(RideIcons.Calendar, c.accent, size = 18.dp); Text("  Pick another date", color = c.accent, fontSize = 14.sp)
    }
    SectionLabel("TIME")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { (1..12).forEach { h -> Chip("$h", h == hour, { onHour(h) }, Modifier.widthIn(min = 48.dp)) } }
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0, 15, 30, 45).forEach { m -> Chip("%02d".format(m), m == minute, { onMinute(m) }) }
        Spacer(Modifier.width(8.dp))
        Chip("AM", !pm, { onPm(false) }); Chip("PM", pm, { onPm(true) })
    }
}

// ---------- Pick up later quick sheet ----------

@Composable
fun PickUpLaterSheet(onConfirm: (String) -> Unit, onCancel: () -> Unit, onMoreDates: () -> Unit) {
    val now = remember { java.time.LocalDateTime.now().plusMinutes(30) }
    var dateDay by rememberSaveable { mutableLongStateOf(now.toLocalDate().toEpochDay()) }
    var hour by rememberSaveable { mutableIntStateOf(if (now.hour % 12 == 0) 12 else now.hour % 12) }
    var minute by rememberSaveable { mutableIntStateOf(((now.minute + 14) / 15 * 15) % 60) }
    var pm by rememberSaveable { mutableStateOf(now.hour >= 12) }
    val date = LocalDate.ofEpochDay(dateDay)
    SheetScreen(onCancel, "Pick up later", "Choose when you want to be picked up.") {
        DateTimePicker(date, { dateDay = it.toEpochDay() }, hour, { hour = it }, minute, { minute = it }, pm, { pm = it }, onMoreDates)
        RideButton("Confirm · ${pickLabel(date, hour, minute, pm)}", { onConfirm(pickLabel(date, hour, minute, pm)) }, Modifier.padding(top = 16.dp))
        RideButton("Cancel", onCancel, style = RideButtonStyle.Text)
    }
}

// ---------- Full calendar ----------

@Composable
fun CalendarSheet(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val c = RideTheme.colors
    val today = remember { LocalDate.now() }
    var month by remember { mutableStateOf(YearMonth.from(initial)) }
    var chosen by remember { mutableStateOf(initial) }
    SheetScreen(onDismiss, "Pick another date") {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())), color = c.fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).semantics { heading() })
            SmallAction(RideIcons.Back, "Previous month", 0f, month.isAfter(YearMonth.from(today))) { month = month.minusMonths(1) }
            SmallAction(RideIcons.Back, "Next month", 180f, true) { month = month.plusMonths(1) }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach { Text(it, color = c.muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
        }
        val lead = month.atDay(1).dayOfWeek.value % 7
        val cells = (0 until 42).map { it - lead + 1 }
        for (row in 0 until 6) {
            if (cells[row * 7] > month.lengthOfMonth()) break
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val day = cells[row * 7 + col]
                    Box(Modifier.weight(1f).height(48.dp), contentAlignment = Alignment.Center) {
                        if (day in 1..month.lengthOfMonth()) {
                            val d = month.atDay(day)
                            val past = d.isBefore(today)
                            val on = d == chosen
                            Box(
                                Modifier.size(44.dp).clip(CircleShape).background(if (on) c.accent else androidx.compose.ui.graphics.Color.Transparent)
                                    .then(if (d == today && !on) Modifier.border(1.dp, c.accent, CircleShape) else Modifier)
                                    .clickable(enabled = !past, role = Role.Button) { chosen = d }
                                    .semantics { contentDescription = d.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())); selected = on },
                                contentAlignment = Alignment.Center,
                            ) { Text("$day", color = if (on) c.onAccent else if (past) c.muted.copy(alpha = 0.5f) else c.fg, fontSize = 15.sp) }
                        }
                    }
                }
            }
        }
        RideButton("Confirm date", { onPick(chosen) }, Modifier.padding(top = 12.dp))
    }
}

// ---------- Passenger, Repeat and Reminder sheets ----------

@Composable
fun ChoiceSheet(title: String, options: List<String>, initial: Int, onSave: (Int) -> Unit, onDismiss: () -> Unit, subs: List<String?> = emptyList()) {
    var pick by rememberSaveable { mutableIntStateOf(initial) }
    SheetScreen(onDismiss, title) {
        Column(Modifier.padding(top = 8.dp)) {
            options.forEachIndexed { i, o ->
                if (i > 0) RowDivider()
                RadioRow(o, pick == i, { pick = i }, subs.getOrNull(i))
            }
        }
        val close = LocalSheetClose.current
        RideButton("Save", { if (close != null) close { onSave(pick) } else onSave(pick) }, Modifier.padding(top = 14.dp))
    }
}

val PassengerOptions = listOf("Me", "Someone else")
val RepeatOptions = listOf("Does not repeat", "Daily", "Weekdays", "Weekly", "Monthly")
val ReminderOptions = listOf("None", "5 min before", "15 min before", "30 min before", "1 hour before")
