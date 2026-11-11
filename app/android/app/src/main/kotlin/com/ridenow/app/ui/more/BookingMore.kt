package com.ridenow.app.ui.more

import com.ridenow.app.R
import androidx.compose.ui.res.stringResource
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
    val textNovaTerminalGate4 = stringResource(R.string.text_nova_terminal_gate_4)
    val textYourRoute = stringResource(R.string.text_your_route)
    val textAddStopsAlongTheWay = stringResource(R.string.text_add_stops_along_the_way)
    val textDone = stringResource(R.string.text_done)
    val textConfirmPickup = stringResource(R.string.confirm_pickup)
    val textConfirmDrop = stringResource(R.string.confirm_drop)
    val textStopAddress = stringResource(R.string.text_stop_address)
    val textAddStop = stringResource(R.string.text_add_stop)
    val textPhotoCancel = stringResource(R.string.photo_cancel)
    val textAddAnotherStop = stringResource(R.string.text_add_another_stop)
    val textYouCanAddUpTo3Stops = stringResource(R.string.text_you_can_add_up_to_3_stops)

    val c = RideTheme.colors
    val stops = remember { mutableStateListOf(textNovaTerminalGate4) }
    var adding by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    MoreScaffold(textYourRoute, onBack, subtitle = textAddStopsAlongTheWay, bottom = { RideButton(textDone, onBack) }) {
        Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FixedStop(textConfirmPickup, pickup)
            stops.forEachIndexed { i, s -> StopRow(i, s, stops) }
            FixedStop(textConfirmDrop, drop)
        }
        if (adding) {
            RideTextField(draft, { draft = it.take(80) }, textStopAddress, RideIcons.Pin, Modifier.padding(top = 14.dp))
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RideButton(textAddStop, { if (draft.isNotBlank()) { stops.add(draft.trim()); draft = ""; adding = false } }, Modifier.weight(1f), enabled = draft.isNotBlank())
                RideButton(textPhotoCancel, { draft = ""; adding = false }, Modifier.weight(1f), style = RideButtonStyle.Text)
            }
        } else if (stops.size < 3) {
            Row(
                Modifier.padding(top = 14.dp).heightIn(min = 48.dp).clickable(role = Role.Button) { adding = true },
                verticalAlignment = Alignment.CenterVertically,
            ) { RideIcon(RideIcons.Plus, c.accent); Text(textAddAnotherStop, color = c.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
        } else Text(textYouCanAddUpTo3Stops, color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp))
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
    val textStopValue = stringResource(R.string.text_stop_value)
    val textMoveStopValueUp = stringResource(R.string.text_move_stop_value_up)
    val textMoveStopValueDown = stringResource(R.string.text_move_stop_value_down)
    val textRemoveStopValue = stringResource(R.string.text_remove_stop_value)

    val c = RideTheme.colors
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.field).border(1.dp, c.accent.copy(alpha = 0.5f), RoundedCornerShape(16.dp)).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
            Text(textStopValue.format(i + 1), color = c.muted, fontSize = 11.sp, letterSpacing = 1.1.sp)
            Text(text, color = c.fg, fontSize = 15.sp, modifier = Modifier.padding(top = 2.dp))
        }
        SmallAction(RideIcons.ChevronDown, textMoveStopValueUp.format(i + 1), 180f, i > 0) { val t = stops[i]; stops[i] = stops[i - 1]; stops[i - 1] = t }
        SmallAction(RideIcons.ChevronDown, textMoveStopValueDown.format(i + 1), 0f, i < stops.lastIndex) { val t = stops[i]; stops[i] = stops[i + 1]; stops[i + 1] = t }
        SmallAction(RideIcons.Close, textRemoveStopValue.format(i + 1), 0f, true) { stops.removeAt(i) }
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
    val textMeridianWalk = stringResource(R.string.text_meridian_walk)
    val textHelixAvenue = stringResource(R.string.text_helix_avenue)
    val textNovaMarket = stringResource(R.string.text_nova_market)
    val textZenithCafe = stringResource(R.string.text_zenith_cafe)
    val textSearchReturn = stringResource(R.string.search_return)
    val textChooseWhereYouStopBeforeHeadingBack = stringResource(R.string.text_choose_where_you_stop_before_heading_back)
    val textSetStopLocation = stringResource(R.string.text_set_stop_location)
    val textConfirmPickup = stringResource(R.string.confirm_pickup)
    val textSearchStop = stringResource(R.string.search_stop)
    val textConfirmDrop = stringResource(R.string.confirm_drop)
    val textSearchSame = stringResource(R.string.search_same)
    val textCountrySuggested = stringResource(R.string.country_suggested)
    val textSearchChooseMap = stringResource(R.string.search_choose_map)

    val c = RideTheme.colors
    var stop by rememberSaveable { mutableStateOf(drop) }
    val places = listOf(textMeridianWalk, textHelixAvenue, textNovaMarket, textZenithCafe)
    MoreScaffold(textSearchReturn, onBack, subtitle = textChooseWhereYouStopBeforeHeadingBack, bottom = { RideButton(textSetStopLocation, { onDone(stop) }) }) {
        Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FixedStop(textConfirmPickup, pickup)
            FixedStop(textSearchStop, stop)
            FixedStop(textConfirmDrop, textSearchSame)
        }
        SectionLabel(textCountrySuggested)
        GroupCard {
            places.forEachIndexed { i, p ->
                if (i > 0) RowDivider()
                NavRow(RideIcons.Pin, p, { stop = p }, trailing = { if (stop == p) RideIcon(RideIcons.Check, c.accent) else Spacer(Modifier.size(20.dp)) })
            }
            RowDivider()
            NavRow(RideIcons.Map, textSearchChooseMap, onMap)
        }
    }
}

// ---------- Vehicle info sheet ----------

@Composable
fun VehicleInfoSheet(index: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    val textCompactElectricRideForUpToValue = stringResource(R.string.text_compact_electric_ride_for_up_to_value)
    val textComfortableElectricRideForUpToValue = stringResource(R.string.text_comfortable_electric_ride_for_up_to_value)
    val textSpaciousElectricRideForUpToValue = stringResource(R.string.text_spacious_electric_ride_for_up_to_value)
    val textRideSeats = stringResource(R.string.ride_seats)
    val textRideLuggage = stringResource(R.string.ride_luggage)
    val textValueBags = stringResource(R.string.text_value_bags)
    val textPickup = stringResource(R.string.text_pickup)
    val textValueMin = stringResource(R.string.text_value_min)
    val textFare2 = stringResource(R.string.text_fare_2)
    val textWhatYouGet = stringResource(R.string.text_what_you_get)
    val textQuietElectricDrive = stringResource(R.string.text_quiet_electric_drive)
    val textAirPurifiedCabin = stringResource(R.string.text_air_purified_cabin)
    val textChargingPortForEverySeat = stringResource(R.string.text_charging_port_for_every_seat)
    val textChildSeatOnRequest = stringResource(R.string.text_child_seat_on_request)
    val textEstimatedFarePayYourDriverInCash = stringResource(R.string.text_estimated_fare_pay_your_driver_in_cash)
    val textSelectValue = stringResource(R.string.text_select_value)

    val c = RideTheme.colors
    val i = index.coerceIn(0, Vehicles.lastIndex)
    val v = Vehicles[i]
    val blurb = when (v.name) { "Pulse" -> textCompactElectricRideForUpToValue.format(v.seats); "Orbit" -> textComfortableElectricRideForUpToValue.format(v.seats); else -> textSpaciousElectricRideForUpToValue.format(v.seats) }
    SheetScreen(onDismiss, v.name, blurb) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp)) {
            listOf(textRideSeats to "${v.seats}", textRideLuggage to textValueBags.format(v.luggage), textPickup to textValueMin.format(v.etaMin), textFare2 to money(v.fare)).forEach { (k, value) ->
                Column(Modifier.weight(1f)) { Text(k, color = c.muted, fontSize = 12.sp); Text(value, color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
        SectionLabel(textWhatYouGet)
        listOf(textQuietElectricDrive, textAirPurifiedCabin, textChargingPortForEverySeat, textChildSeatOnRequest).forEach {
            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) { RideIcon(RideIcons.Check, c.accent, size = 18.dp); Text("  $it", color = c.fg, fontSize = 14.sp) }
        }
        Text(textEstimatedFarePayYourDriverInCash, color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 12.dp))
        RideButton(textSelectValue.format(v.name), { onSelect(i) })
    }
}

// ---------- High demand notice ----------

@Composable
fun HighDemandSheet(vehicle: Int, onContinue: () -> Unit, onSchedule: () -> Unit, onDismiss: () -> Unit) {
    val textHighDemand = stringResource(R.string.text_high_demand)
    val textFaresAreHigherThanUsual = stringResource(R.string.text_fares_are_higher_than_usual)
    val textMoreRidersThanDriversNearbyFaresUpdateAsDemandChanges = stringResource(R.string.text_more_riders_than_drivers_nearby_fares_update_as_demand_changes)
    val textUsualFare = stringResource(R.string.text_usual_fare)
    val text14RightNow = stringResource(R.string.text_text_1_4_right_now)
    val textContinueAtValue = stringResource(R.string.text_continue_at_value)
    val textWeWillNotifyYou = stringResource(R.string.text_we_will_notify_you)
    val textNotifyMeWhenFaresDrop = stringResource(R.string.text_notify_me_when_fares_drop)
    val textScheduleForLater = stringResource(R.string.text_schedule_for_later)

    val c = RideTheme.colors
    val v = Vehicles[vehicle.coerceIn(0, Vehicles.lastIndex)]
    var notify by rememberSaveable { mutableStateOf(false) }
    SheetScreen(onDismiss) {
        Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(RideIcons.Alert, c.accent, 44.dp)
            Text(textHighDemand, color = c.accent, fontSize = 12.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp))
        }
        Text(textFaresAreHigherThanUsual, color = c.fg, fontSize = 24.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(top = 14.dp).semantics { heading() })
        Text(textMoreRidersThanDriversNearbyFaresUpdateAsDemandChanges, color = c.muted, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 6.dp))
        Column(Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 8.dp)) {
            InfoRow(textUsualFare, money(v.fare / 1.4))
            InfoRow(text14RightNow, money(v.fare), strong = true, valueColor = c.accent)
        }
        RideButton(textContinueAtValue.format(money(v.fare)), onContinue, Modifier.padding(top = 16.dp))
        RideButton(if (notify) textWeWillNotifyYou else textNotifyMeWhenFaresDrop, { notify = !notify }, style = RideButtonStyle.Text)
        RideButton(textScheduleForLater, onSchedule, style = RideButtonStyle.Text)
    }
}

// ---------- Date and time pieces shared by the pickers ----------

fun pickLabel(date: LocalDate, hour: Int, minute: Int, pm: Boolean, amLabel: String, pmLabel: String): String =
    date.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())) + ", %d:%02d %s".format(hour, minute, if (pm) pmLabel else amLabel)

@Composable
fun DateTimePicker(
    date: LocalDate, onDate: (LocalDate) -> Unit, hour: Int, onHour: (Int) -> Unit, minute: Int, onMinute: (Int) -> Unit,
    pm: Boolean, onPm: (Boolean) -> Unit, onMoreDates: (() -> Unit)? = null,
) {
    val textScheduleDate = stringResource(R.string.schedule_date)
    val textScheduleToday = stringResource(R.string.schedule_today)
    val textPickAnotherDate = stringResource(R.string.text_pick_another_date)
    val textScheduleTime = stringResource(R.string.schedule_time)
    val textAm = stringResource(R.string.text_am)
    val textPm = stringResource(R.string.text_pm)

    val c = RideTheme.colors
    val today = remember { LocalDate.now() }
    val days = remember(date) { val base = (0..4).map { today.plusDays(it.toLong()) }; if (date in base) base else base.take(4) + date }
    SectionLabel(textScheduleDate)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        days.forEach { d ->
            val name = if (d == today) textScheduleToday else d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault())
            Chip("$name ${d.dayOfMonth}", d == date, { onDate(d) })
        }
    }
    if (onMoreDates != null) Row(Modifier.padding(top = 4.dp).heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onMoreDates), verticalAlignment = Alignment.CenterVertically) {
        RideIcon(RideIcons.Calendar, c.accent, size = 18.dp); Text(textPickAnotherDate, color = c.accent, fontSize = 14.sp)
    }
    SectionLabel(textScheduleTime)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { (1..12).forEach { h -> Chip("$h", h == hour, { onHour(h) }, Modifier.widthIn(min = 48.dp)) } }
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0, 15, 30, 45).forEach { m -> Chip("%02d".format(m), m == minute, { onMinute(m) }) }
        Spacer(Modifier.width(8.dp))
        Chip(textAm, !pm, { onPm(false) }); Chip(textPm, pm, { onPm(true) })
    }
}

// ---------- Pick up later quick sheet ----------

@Composable
fun PickUpLaterSheet(onConfirm: (String) -> Unit, onCancel: () -> Unit, onMoreDates: () -> Unit) {
    val amLabel = stringResource(R.string.text_am)
    val pmLabel = stringResource(R.string.text_pm)
    val textPickUpLater = stringResource(R.string.text_pick_up_later)
    val textChooseWhenYouWantToBePickedUp = stringResource(R.string.text_choose_when_you_want_to_be_picked_up)
    val textConfirmValue = stringResource(R.string.text_confirm_value)
    val textPhotoCancel = stringResource(R.string.photo_cancel)

    val now = remember { java.time.LocalDateTime.now().plusMinutes(30) }
    var dateDay by rememberSaveable { mutableLongStateOf(now.toLocalDate().toEpochDay()) }
    var hour by rememberSaveable { mutableIntStateOf(if (now.hour % 12 == 0) 12 else now.hour % 12) }
    var minute by rememberSaveable { mutableIntStateOf(((now.minute + 14) / 15 * 15) % 60) }
    var pm by rememberSaveable { mutableStateOf(now.hour >= 12) }
    val date = LocalDate.ofEpochDay(dateDay)
    SheetScreen(onCancel, textPickUpLater, textChooseWhenYouWantToBePickedUp) {
        DateTimePicker(date, { dateDay = it.toEpochDay() }, hour, { hour = it }, minute, { minute = it }, pm, { pm = it }, onMoreDates)
        RideButton(textConfirmValue.format(pickLabel(date, hour, minute, pm, amLabel, pmLabel)), { onConfirm(pickLabel(date, hour, minute, pm, amLabel, pmLabel)) }, Modifier.padding(top = 16.dp))
        RideButton(textPhotoCancel, onCancel, style = RideButtonStyle.Text)
    }
}

// ---------- Full calendar ----------

@Composable
fun CalendarSheet(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val textScheduleOtherDate = stringResource(R.string.schedule_other_date)
    val textPreviousMonth = stringResource(R.string.text_previous_month)
    val textNextMonth = stringResource(R.string.text_next_month)
    val textConfirmDate = stringResource(R.string.text_confirm_date)

    val c = RideTheme.colors
    val today = remember { LocalDate.now() }
    var month by remember { mutableStateOf(YearMonth.from(initial)) }
    var chosen by remember { mutableStateOf(initial) }
    SheetScreen(onDismiss, textScheduleOtherDate) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())), color = c.fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).semantics { heading() })
            SmallAction(RideIcons.Back, textPreviousMonth, 0f, month.isAfter(YearMonth.from(today))) { month = month.minusMonths(1) }
            SmallAction(RideIcons.Back, textNextMonth, 180f, true) { month = month.plusMonths(1) }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf(stringResource(R.string.weekday_sunday), stringResource(R.string.weekday_monday), stringResource(R.string.weekday_tuesday), stringResource(R.string.weekday_wednesday), stringResource(R.string.weekday_thursday), stringResource(R.string.weekday_friday), stringResource(R.string.weekday_saturday)).forEach { Text(it, color = c.muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
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
        RideButton(textConfirmDate, { onPick(chosen) }, Modifier.padding(top = 12.dp))
    }
}

// ---------- Passenger, Repeat and Reminder sheets ----------

@Composable
fun ChoiceSheet(title: String, options: List<String>, initial: Int, onSave: (Int) -> Unit, onDismiss: () -> Unit, subs: List<String?> = emptyList(), icons: List<List<String>> = emptyList()) {
    val textSave = stringResource(R.string.text_save)

    var pick by rememberSaveable { mutableIntStateOf(initial) }
    SheetScreen(onDismiss, title) {
        Column(Modifier.padding(top = 8.dp)) {
            options.forEachIndexed { i, o ->
                if (i > 0) RowDivider()
                RadioRow(o, pick == i, { pick = i }, subs.getOrNull(i), icons.getOrNull(i), highlight = true)
            }
        }
        val close = LocalSheetClose.current
        RideButton(textSave, { if (close != null) close { onSave(pick) } else onSave(pick) }, Modifier.padding(top = 14.dp))
    }
}

@Composable
fun passengerOptions(): List<String> = listOf(stringResource(R.string.passenger_me), stringResource(R.string.passenger_other))

@Composable
fun repeatOptions(): List<String> = listOf(
    stringResource(R.string.repeat_none), stringResource(R.string.repeat_daily), stringResource(R.string.repeat_weekdays),
    stringResource(R.string.repeat_weekly), stringResource(R.string.repeat_monthly),
)

@Composable
fun reminderOptions(): List<String> = listOf(
    stringResource(R.string.reminder_none), stringResource(R.string.reminder_5), stringResource(R.string.reminder_15),
    stringResource(R.string.reminder_30), stringResource(R.string.reminder_60),
)
