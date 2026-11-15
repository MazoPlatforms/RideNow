package com.ridenow.driver.ui.earnings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.ridenow.driver.R
import com.ridenow.driver.ui.components.*
import com.ridenow.driver.ui.data.*
import com.ridenow.driver.ui.home.MainTab
import com.ridenow.driver.ui.rides.RideStops
import com.ridenow.driver.ui.theme.RideTheme
import com.ridenow.driver.ui.util.Money
import com.ridenow.driver.ui.util.entrance
import java.time.format.TextStyle
import java.util.Locale

private val MAX_AGE = intArrayOf(0, 6, 29, Int.MAX_VALUE)

/**
 * Earnings tab: period pills, summary with change, three stats, a chart with its own range menu, and recent rides.
 * [realOnlineMinutes] is the time online today measured by the app.
 */
@Composable
fun EarningsScreen(
    store: DriverStore, realOnlineMinutes: Int, unread: Int, initials: String, onTab: (MainTab) -> Unit, onBell: () -> Unit, onProfile: () -> Unit,
    onSeeAll: () -> Unit, onOpenRide: (RideRecord) -> Unit, modifier: Modifier = Modifier,
) {
    val c = RideTheme.colors
    var period by rememberSaveable { mutableStateOf(0) }
    var range by rememberSaveable(period) { mutableStateOf(period) }
    var menu by remember { mutableStateOf(false) }
    val labels = listOf(str(R.string.earn_today), str(R.string.earn_week), str(R.string.earn_month), str(R.string.earn_all))
    val completed = store.rides.filter { it.status == RideStatus.Completed }
    val inPeriod = completed.filter { it.ageDays <= MAX_AGE[period] }
    val total = inPeriod.sumOf { it.earned }
    val minutes = if (period == 0) maxOf(realOnlineMinutes, inPeriod.sumOf { it.durationMin + 6 }) else inPeriod.sumOf { it.durationMin + 6 }
    val prev: Int? = when (period) {
        0 -> completed.filter { it.ageDays == 1 }.sumOf { it.earned }
        1 -> completed.filter { it.ageDays in 7..13 }.sumOf { it.earned }
        2 -> completed.filter { it.ageDays in 30..59 }.sumOf { it.earned }
        else -> null
    }
    val delta = if (prev != null && prev > 0) Math.round((total - prev) * 100f / prev) else null
    val recent = store.rides.sortedWith(compareBy<RideRecord> { it.ageDays }.thenByDescending { it.startMin }).take(3)

    TabScaffold(MainTab.Earnings, onTab, unread, initials, onBell, onProfile, modifier) {
        PageTitle(str(R.string.earn_title))
        FilterPills(labels, period, { period = it }, Modifier.padding(top = 12.dp))
        GroupCard(Modifier.padding(top = 14.dp).entrance(40)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(str(R.string.earn_summary_title, labels[period]), color = c.muted, fontSize = 14.sp)
                    Text(Money.fmt(total), color = c.fg, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
                    Text(str(R.string.earn_rides_online, inPeriod.size, minutes / 60, minutes % 60), color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                }
                if (delta != null) Column(horizontalAlignment = Alignment.End) {
                    StatusChip((if (delta >= 0) "\u2191 +" else "\u2193 ") + delta + "%", if (delta >= 0) Tone.Good else Tone.Danger)
                    Text(str(if (period == 0) R.string.earn_vs_yesterday else if (period == 1) R.string.earn_vs_last_week else R.string.earn_vs_last_month), color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        GroupCard(Modifier.padding(top = 12.dp).entrance(80)) {
            Row(Modifier.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Mini(RideIcons.Car, inPeriod.size.toString(), str(R.string.earn_rides), Modifier.weight(1f)); Sep()
                Mini(RideIcons.Clock, str(R.string.home_time_fmt, minutes / 60, minutes % 60), str(R.string.earn_online), Modifier.weight(1f)); Sep()
                Mini(RideIcons.Cash, Money.fmt(if (inPeriod.isEmpty()) 0 else total / inPeriod.size), str(R.string.earn_avg), Modifier.weight(1f))
            }
        }
        Row(Modifier.padding(top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(str(R.string.earn_overview), color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).semantics { heading() })
            Box {
                Row(
                    Modifier.clip(RoundedCornerShape(18.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(18.dp)).clickable(role = Role.Button) { menu = true }.heightIn(min = 36.dp).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) { Text(labels[range], color = c.fg, fontSize = 13.sp); Spacer(Modifier.width(6.dp)); RideIcon(RideIcons.ChevronDown, c.fg, size = 14.dp) }
                DropdownMenu(menu, { menu = false }, containerColor = c.field) {
                    labels.forEachIndexed { i, l -> DropdownMenuItem(text = { Text(l, color = c.fg) }, onClick = { range = i; menu = false }) }
                }
            }
        }
        val (values, xl) = chartFor(completed, range)
        GroupCard { Box(Modifier.padding(14.dp)) { BarChart(values, xl, description = str(R.string.earn_chart_desc)) } }
        SectionTitle(str(R.string.earn_recent), action = str(R.string.see_all), onAction = onSeeAll)
        if (recent.isEmpty()) GroupCard(Modifier.padding(bottom = 16.dp)) { EmptyState(RideIcons.Receipt, str(R.string.earn_empty_title), str(R.string.earn_empty_text)) }
        else Column(Modifier.padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            recent.forEach { r ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(16.dp)).clickable(role = Role.Button) { onOpenRide(r) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) { RideStops(r, withTimes = false, subtitles = false) }
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
                        Text(Calendars.timeLabel(r.startMin), color = c.muted, fontSize = 12.sp)
                        Text(Money.fmt(r.fare), color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
                    }
                    RideIcon(RideIcons.Chevron, c.muted, size = 16.dp, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

/** Bars and x labels for a range: 0 hours today, 1 last 7 days, 2 last 30 days, 3 last 6 months. */
@Composable
private fun chartFor(rides: List<RideRecord>, range: Int): Pair<List<Float>, List<String>> {
    val hours = listOf("12 AM", "4 AM", "8 AM", "12 PM", "4 PM", "8 PM")
    return when (range) {
        0 -> List(24) { h -> rides.filter { it.ageDays == 0 && it.startMin / 60 == h }.sumOf { it.earned }.toFloat() } to List(24) { if (it % 4 == 0) hours[it / 4] else "" }
        1 -> {
            val today = java.time.LocalDate.now()
            List(7) { i -> rides.filter { it.ageDays == 6 - i }.sumOf { it.earned }.toFloat() } to List(7) { i -> today.minusDays((6 - i).toLong()).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
        }
        2 -> List(30) { i -> rides.filter { it.ageDays == 29 - i }.sumOf { it.earned }.toFloat() } to List(30) { i -> if (i % 5 == 0) java.time.LocalDate.now().minusDays((29 - i).toLong()).dayOfMonth.toString() else "" }
        else -> List(6) { i -> rides.filter { it.ageDays / 30 == 5 - i }.sumOf { it.earned }.toFloat() } to List(6) { i -> java.time.LocalDate.now().minusMonths((5 - i).toLong()).month.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
    }
}

@Composable
private fun Sep() = Box(Modifier.width(1.dp).height(36.dp).background(RideTheme.colors.line))

@Composable
private fun Mini(icon: List<String>, value: String, label: String, modifier: Modifier) {
    val c = RideTheme.colors
    Row(modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        RideIcon(icon, c.fg, size = 22.dp)
        Column(Modifier.padding(start = 8.dp)) {
            Text(value, color = c.fg, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(label, color = c.muted, fontSize = 11.sp, maxLines = 1)
        }
    }
}
