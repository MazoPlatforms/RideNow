package com.ridenow.app.ui.booking

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridenow.app.ui.components.RideButton
import com.ridenow.app.ui.components.RideIcon
import com.ridenow.app.ui.components.RideIcons
import com.ridenow.app.ui.theme.BrandYellow
import com.ridenow.app.ui.theme.Ink
import com.ridenow.app.ui.theme.RideNowTheme
import com.ridenow.app.ui.theme.RideTheme
import com.ridenow.app.ui.util.entrance
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

private val Repeats = listOf("Does not repeat", "Daily", "Weekdays", "Weekly")
private val Reminders = listOf("None", "5 min before", "15 min before", "30 min before", "1 hour before")
private val Passengers = listOf("Me", "Someone else")

/** Pick a pickup date and time. [onContinue] receives a label such as "Wed, 4 Nov, 8:30 AM". */
@Composable
fun ScheduleScreen(onBack: () -> Unit, onContinue: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val context = LocalContext.current
    val today = remember { LocalDate.now() }
    val startTime = remember { LocalDateTime.now().plusMinutes(30).let { it.plusMinutes(((15 - it.minute % 15) % 15).toLong()) } }
    var dateDay by rememberSaveable { mutableLongStateOf(startTime.toLocalDate().toEpochDay()) }
    var hour by rememberSaveable { mutableIntStateOf((startTime.hour + 11) % 12) }   // 0..11 = 1..12
    var minute by rememberSaveable { mutableIntStateOf(startTime.minute / 15) }       // 0..3
    var pm by rememberSaveable { mutableIntStateOf(if (startTime.hour >= 12) 1 else 0) }
    var repeat by rememberSaveable { mutableIntStateOf(0) }
    var reminder by rememberSaveable { mutableIntStateOf(2) }
    var passenger by rememberSaveable { mutableIntStateOf(0) }

    val date = LocalDate.ofEpochDay(dateDay)
    val base = (0..4).map { today.plusDays(it.toLong()) }
    val chips = if (date in base) base else base.take(4) + date
    val picked = date.atTime((hour + 1) % 12 + if (pm == 1) 12 else 0, minute * 15)
    val valid = picked.isAfter(LocalDateTime.now().plusMinutes(14))
    val label = picked.format(DateTimeFormatter.ofPattern("EEE, d MMM, h:mm a", Locale.getDefault()))

    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().height(48.dp).entrance(0), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.offset(x = (-12).dp).size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = "Back" }, contentAlignment = Alignment.Center) {
                RideIcon(RideIcons.Back, c.fg, size = 22.dp)
            }
            Spacer(Modifier.weight(1f))
            Text("Schedule a ride", color = c.fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.weight(1f)); Spacer(Modifier.width(36.dp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).entrance(80)) {
            Text("Pick a day and time for your pickup.", color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
            Label("DATE")
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chips.forEach { d ->
                    val on = d == date
                    Column(
                        Modifier.weight(1f).height(76.dp).clip(RoundedCornerShape(16.dp)).background(if (on) BrandYellow else c.field)
                            .border(1.dp, if (on) BrandYellow else c.border, RoundedCornerShape(16.dp)).clickable(role = Role.RadioButton) { dateDay = d.toEpochDay() },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Text(if (d == today) "Today" else d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()), color = if (on) Ink else c.fg, fontSize = 12.sp)
                        Text("${d.dayOfMonth}", color = if (on) Ink else c.fg, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Row(
                Modifier.height(48.dp).clickable(role = Role.Button) {
                    DatePickerDialog(context, { _, y, m, d -> dateDay = LocalDate.of(y, m + 1, d).toEpochDay() }, date.year, date.monthValue - 1, date.dayOfMonth)
                        .apply { datePicker.minDate = System.currentTimeMillis() - 1000 }.show()
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RideIcon(listOf("M3,4.5h14v12.5h-14z", "M3 8.5h14M7 3v3M13 3v3"), c.accent, size = 18.dp); Spacer(Modifier.width(8.dp))
                Text("Pick another date", color = c.accent, fontSize = 14.sp)
            }

            Label("TIME")
            Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(132.dp).clip(RoundedCornerShape(20.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(20.dp))) {
                Box(Modifier.align(Alignment.Center).padding(horizontal = 10.dp).fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(c.accent.copy(alpha = 0.14f)))
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Wheel((1..12).map { "$it" }, hour, Modifier.weight(1f)) { hour = it }
                    Text(":", color = c.fg, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                    Wheel(listOf("00", "15", "30", "45"), minute, Modifier.weight(1f)) { minute = it }
                    Wheel(listOf("AM", "PM"), pm, Modifier.weight(1f)) { pm = it }
                }
            }
            if (!valid) Text("Pick a time at least 15 minutes from now.", color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))

            Label("OPTIONS")
            Column(Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(20.dp))) {
                OptionRow(RideIcons.Person, "Passenger", Passengers[passenger]) { passenger = (passenger + 1) % Passengers.size }
                HorizontalDivider(color = c.line)
                OptionRow(listOf("M4 9V8a3 3 0 0 1 3-3h8l-2-2M16 11v1a3 3 0 0 1-3 3H5l2 2"), "Repeat", Repeats[repeat]) { repeat = (repeat + 1) % Repeats.size }
                HorizontalDivider(color = c.line)
                OptionRow(RideIcons.Bell, "Reminder", Reminders[reminder]) { reminder = (reminder + 1) % Reminders.size }
            }
            Spacer(Modifier.height(16.dp))
        }
        Text("Pickup · $label", color = c.muted, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp))
        RideButton("Continue", { onContinue(label) }, enabled = valid, modifier = Modifier.padding(top = 10.dp, bottom = 12.dp))
    }
}

@Composable
private fun Label(t: String) = Text(t, color = RideTheme.colors.muted, fontSize = 11.sp, letterSpacing = 1.1.sp, modifier = Modifier.padding(top = 20.dp))

@Composable
private fun OptionRow(icon: List<String>, title: String, value: String, onClick: () -> Unit) {
    val c = RideTheme.colors
    Row(Modifier.fillMaxWidth().height(56.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        RideIcon(icon, c.accent); Spacer(Modifier.width(14.dp))
        Text(title, color = c.fg, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(value, color = c.muted, fontSize = 14.sp); Spacer(Modifier.width(8.dp))
        RideIcon(listOf("M7 4l6 6-6 6"), c.muted, size = 18.dp)
    }
}

/** Snapping wheel: the item nearest the vertical centre is selected. */
@Composable
private fun Wheel(items: List<String>, initial: Int, modifier: Modifier, onSelected: (Int) -> Unit) {
    val c = RideTheme.colors
    val state = rememberLazyListState()
    LaunchedEffect(Unit) { state.scrollToItem(initial) }
    val sel by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val center = info.viewportSize.height / 2
            val best = info.visibleItemsInfo.filter { it.index in 1..items.size }.minByOrNull { abs(it.offset + it.size / 2 - center) }
            (best?.index ?: (initial + 1)) - 1
        }
    }
    LaunchedEffect(sel) { onSelected(sel) }
    LazyColumn(state = state, modifier = modifier.height(132.dp), flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Center)) {
        item { Spacer(Modifier.height(44.dp)) }
        itemsIndexed(items) { i, t ->
            Box(Modifier.height(44.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(t, color = if (i == sel) c.fg else c.muted, fontSize = if (i == sel) 24.sp else 18.sp, fontWeight = if (i == sel) FontWeight.SemiBold else FontWeight.Light)
            }
        }
        item { Spacer(Modifier.height(44.dp)) }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SchedulePreview() = RideNowTheme(darkTheme = true) { ScheduleScreen({}, {}) }
