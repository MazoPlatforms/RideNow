package com.ridenow.app.ui.booking

import com.ridenow.app.ui.components.offsetByStartDirection

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.res.stringResource
import com.ridenow.app.R
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
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
import com.ridenow.app.ui.more.passengerOptions
import com.ridenow.app.ui.more.repeatOptions
import com.ridenow.app.ui.more.reminderOptions
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

/** Pick a pickup date and time. [onContinue] receives a label such as "Wed, 4 Nov, 8:30 AM". */
@Composable
fun ScheduleScreen(onBack: () -> Unit, onContinue: (String) -> Unit, modifier: Modifier = Modifier) {
    val textAm = stringResource(R.string.text_am)
    val textPm = stringResource(R.string.text_pm)

    val c = RideTheme.colors
    val backLabel = stringResource(R.string.search_back)
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
    var sheet by remember { mutableStateOf("") }

    val date = LocalDate.ofEpochDay(dateDay)
    val base = (0..4).map { today.plusDays(it.toLong()) }
    val chips = if (date in base) base else base.take(4) + date
    val picked = date.atTime((hour + 1) % 12 + if (pm == 1) 12 else 0, minute * 15)
    val valid = picked.isAfter(LocalDateTime.now().plusMinutes(14))
    val label = picked.format(DateTimeFormatter.ofPattern("EEE, d MMM, h:mm a", Locale.getDefault()))

    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().height(48.dp).entrance(0), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.offsetByStartDirection((-12).dp).size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = backLabel }, contentAlignment = Alignment.Center) {
                RideIcon(RideIcons.Back, c.fg, size = 22.dp)
            }
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.schedule_title), color = c.fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.weight(1f)); Spacer(Modifier.width(36.dp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).entrance(80)) {
            Text(stringResource(R.string.schedule_sub), color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
            Label(stringResource(R.string.schedule_date))
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chips.forEach { d ->
                    DateChip(
                        on = d == date,
                        dayLabel = if (d == today) stringResource(R.string.schedule_today) else d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        dayOfMonth = d.dayOfMonth,
                        modifier = Modifier.weight(1f),
                    ) { dateDay = d.toEpochDay() }
                }
            }
            Row(
                Modifier.height(48.dp).clickable(role = Role.Button) { sheet = "date" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RideIcon(listOf("M3,4.5h14v12.5h-14z", "M3 8.5h14M7 3v3M13 3v3"), c.accent, size = 18.dp); Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.schedule_other_date), color = c.accent, fontSize = 14.sp)
            }

            Label(stringResource(R.string.schedule_time))
            Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(132.dp).clip(RoundedCornerShape(20.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(20.dp))) {
                Box(Modifier.align(Alignment.Center).padding(horizontal = 10.dp).fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(c.accent.copy(alpha = 0.14f)))
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Wheel((1..12).map { "$it" }, hour, Modifier.weight(1f)) { hour = it }
                    Text(":", color = c.fg, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                    Wheel(listOf("00", "15", "30", "45"), minute, Modifier.weight(1f)) { minute = it }
                    Wheel(listOf(textAm, textPm), pm, Modifier.weight(1f)) { pm = it }
                }
            }
            AnimatedVisibility(
                visible = !valid,
                enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { -it / 2 },
                exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { -it / 2 },
            ) {
                Text(stringResource(R.string.schedule_min), color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
            }

            Label(stringResource(R.string.schedule_options))
            Column(Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(20.dp))) {
                OptionRow(RideIcons.Person, stringResource(R.string.passenger_title), passengerOptions()[passenger]) { sheet = "Passenger" }
                HorizontalDivider(color = c.line)
                OptionRow(listOf("M4 9V8a3 3 0 0 1 3-3h8l-2-2M16 11v1a3 3 0 0 1-3 3H5l2 2"), stringResource(R.string.repeat_title), repeatOptions()[repeat]) { sheet = "Repeat" }
                HorizontalDivider(color = c.line)
                OptionRow(RideIcons.Bell, stringResource(R.string.reminder_title), reminderOptions()[reminder]) { sheet = "Reminder" }
            }
            if (sheet.isNotEmpty()) androidx.compose.ui.window.Dialog({ }, androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false)) {
                when (sheet) {
                    "date" -> com.ridenow.app.ui.more.CalendarSheet(date, { dateDay = it.toEpochDay(); sheet = "" }, { sheet = "" })
                    "Passenger" -> com.ridenow.app.ui.more.ChoiceSheet(stringResource(R.string.passenger_title), passengerOptions(), passenger, { passenger = it; sheet = "" }, { sheet = "" }, icons = listOf(RideIcons.Person, RideIcons.Group))
                    "Repeat" -> com.ridenow.app.ui.more.ChoiceSheet(stringResource(R.string.repeat_title), repeatOptions(), repeat, { repeat = it; sheet = "" }, { sheet = "" }, icons = List(5) { RideIcons.Calendar })
                    else -> com.ridenow.app.ui.more.ChoiceSheet(stringResource(R.string.reminder_title), reminderOptions(), reminder, { reminder = it; sheet = "" }, { sheet = "" }, icons = List(5) { RideIcons.Clock })
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        AnimatedContent(targetState = label, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "pickupLabel", modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)) { l ->
            Text(stringResource(R.string.home_pickup, l), color = c.muted, fontSize = 13.sp)
        }
        RideButton(stringResource(R.string.ride_continue), { onContinue(label) }, enabled = valid, modifier = Modifier.padding(top = 10.dp, bottom = 12.dp))
    }
}

/** Date chip that eases its colours and gently scales up when chosen. */
@Composable
private fun DateChip(on: Boolean, dayLabel: String, dayOfMonth: Int, modifier: Modifier, onClick: () -> Unit) {
    val c = RideTheme.colors
    val onAccent = if (c.bg.luminance() > 0.5f) Color.White else Ink
    val bg by animateColorAsState(if (on) c.accent else c.field, tween(260), label = "chipBg")
    val border by animateColorAsState(if (on) c.accent else c.border, tween(260), label = "chipBorder")
    val fg by animateColorAsState(if (on) onAccent else c.fg, tween(260), label = "chipFg")
    val scale by animateFloatAsState(if (on) 1f else 0.96f, spring(dampingRatio = 0.6f, stiffness = 400f), label = "chipScale")
    Column(
        modifier.graphicsLayer { scaleX = scale; scaleY = scale }.height(76.dp).clip(RoundedCornerShape(16.dp)).background(bg)
            .border(1.dp, border, RoundedCornerShape(16.dp)).clickable(role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text(dayLabel, color = fg, fontSize = 12.sp)
        Text("$dayOfMonth", color = fg, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
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
        AnimatedContent(targetState = value, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, label = "optValue") { v ->
            Text(v, color = c.muted, fontSize = 14.sp)
        }
        Spacer(Modifier.width(8.dp))
        RideIcon(listOf("M7 4l6 6-6 6"), c.muted, size = 18.dp)
    }
}

/** Snapping wheel: the item nearest the vertical centre is selected; labels ease in size and colour. */
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
            val on = i == sel
            val fontSp by animateFloatAsState(if (on) 24f else 18f, tween(200), label = "wheelSize")
            val col by animateColorAsState(if (on) c.fg else c.muted, tween(200), label = "wheelColor")
            Box(Modifier.height(44.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(t, color = col, fontSize = fontSp.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Light)
            }
        }
        item { Spacer(Modifier.height(44.dp)) }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SchedulePreview() = RideNowTheme(darkTheme = true) { ScheduleScreen({}, {}) }
