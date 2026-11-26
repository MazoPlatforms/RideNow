package com.ridenow.driver.ui.earnings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.ridenow.driver.R
import com.ridenow.driver.ui.components.*
import com.ridenow.driver.ui.data.Calendars
import com.ridenow.driver.ui.home.MainTab
import com.ridenow.driver.ui.rides.*
import com.ridenow.driver.ui.theme.RideTheme
import com.ridenow.driver.ui.util.entrance
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import kotlinx.coroutines.CancellationException
import java.util.Locale

/** Shows completed-trip earnings from the ledger. Online time is local to the current session. */
@Composable
fun EarningsScreen(
    realOnlineMinutes: Int, unread: Int, initials: String, onTab: (MainTab) -> Unit, onBell: () -> Unit,
    onProfile: () -> Unit, onSeeAll: () -> Unit, modifier: Modifier = Modifier,
) {
    val c = RideTheme.colors
    val context = LocalContext.current
    val preview = LocalInspectionMode.current
    var retry by remember { mutableIntStateOf(0) }
    var periods by remember { mutableStateOf<List<EarningsPeriod>>(emptyList()) }
    var recent by remember { mutableStateOf<List<DriverHistoryRide>>(emptyList()) }
    var failed by remember { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var period by rememberSaveable { mutableStateOf(0) }
    var range by rememberSaveable(period) { mutableStateOf(period) }
    var menu by remember { mutableStateOf(false) }
    val labels = listOf(str(R.string.earn_today), str(R.string.earn_week), str(R.string.earn_month), str(R.string.earn_all))

    LaunchedEffect(retry, preview) {
        if (!preview) {
            failed = false
            try {
                val summary = EarningsApi.summary(context)
                recent = RideHistoryApi.list(context, null, 3).items
                periods = summary
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                failed = true
            }
        }
    }

    val chosen = recent.firstOrNull { it.id == selected }
    if (chosen != null) {
        BackHandler { selected = null }
        DriverHistoryDetails(chosen) { selected = null }
        return
    }

    val slice = periods.getOrNull(period)
    val delta = slice?.takeIf { period != 3 && it.previous > 0 }?.let {
        Math.round((it.total.toDouble() - it.previous) * 100.0 / it.previous)
    }
    TabScaffold(MainTab.Earnings, onTab, unread, initials, onBell, onProfile, modifier) {
        PageTitle(str(R.string.earn_title))
        FilterPills(labels, period, { period = it }, Modifier.padding(top = 12.dp))
        if (failed) {
            Text(str(R.string.earn_live_error), color = c.muted, modifier = Modifier.padding(top = 12.dp))
            RideButton(str(R.string.sys_try_again), { retry++ }, style = RideButtonStyle.Text)
        }
        if (slice == null) {
            if (!failed && !preview) CircularProgressIndicator(color = c.accent, modifier = Modifier.padding(top = 20.dp))
            return@TabScaffold
        }
        GroupCard(Modifier.padding(top = 14.dp).entrance(40)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(str(R.string.earn_summary_title, labels[period]), color = c.muted, fontSize = 14.sp)
                    Text(formatMinor(slice.total), color = c.fg, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
                    Text(str(R.string.earn_live_calendar), color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                }
                if (delta != null) Column(horizontalAlignment = Alignment.End) {
                    StatusChip((if (delta >= 0) "\u2191 +" else "\u2193 ") + delta + "%", if (delta >= 0) Tone.Good else Tone.Danger)
                    Text(str(if (period == 0) R.string.earn_vs_yesterday else if (period == 1) R.string.earn_vs_last_week else R.string.earn_vs_last_month), color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        GroupCard(Modifier.padding(top = 12.dp).entrance(80)) {
            Row(Modifier.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Mini(RideIcons.Car, slice.rideCount.toString(), str(R.string.earn_rides), Modifier.weight(1f)); Sep()
                Mini(RideIcons.Clock, if (period == 0) str(R.string.home_time_fmt, realOnlineMinutes / 60, realOnlineMinutes % 60) else "—",
                    str(R.string.earn_live_session), Modifier.weight(1f)); Sep()
                Mini(RideIcons.Cash, if (slice.rideCount > 0) formatMinor(slice.total / slice.rideCount) else "—",
                    str(R.string.earn_avg), Modifier.weight(1f))
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
        val (values, xl) = chartFor(periods[range].points, range)
        GroupCard { Box(Modifier.padding(14.dp)) { BarChart(values, xl, description = str(R.string.earn_chart_desc)) } }
        SectionTitle(str(R.string.earn_recent), action = str(R.string.see_all), onAction = onSeeAll)
        if (recent.isEmpty()) GroupCard(Modifier.padding(bottom = 16.dp)) { EmptyState(RideIcons.Receipt, str(R.string.earn_empty_title), str(R.string.earn_empty_text)) }
        else Column(Modifier.padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            recent.forEach { r ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(16.dp)).clickable(role = Role.Button) { selected = r.id }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(r.pickup, color = c.fg, fontSize = 14.sp)
                        Text(r.drop, color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
                        Text(Calendars.timeLabel(r.startMinute), color = c.muted, fontSize = 12.sp)
                        Text(r.fare, color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
                    }
                    RideIcon(RideIcons.Chevron, c.muted, size = 16.dp, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

private fun chartFor(points: List<EarningsPoint>, range: Int): Pair<List<Float>, List<String>> {
    val locale = Locale.getDefault()
    val clock = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    val labels = points.mapIndexed { index, point ->
        when (range) {
            0 -> if (index % 4 == 0) point.at.format(clock) else ""
            1 -> point.at.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
            2 -> if (index % 5 == 0) point.at.dayOfMonth.toString() else ""
            else -> point.at.month.getDisplayName(TextStyle.SHORT, locale)
        }
    }
    return points.map { it.minor / 100f } to labels
}

@Composable
private fun Sep() = Box(Modifier.width(1.dp).height(36.dp).background(RideTheme.colors.line))

@Composable
private fun Mini(icon: List<String>, value: String, label: String, modifier: Modifier) {
    val c = RideTheme.colors
    Row(modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        RideIcon(icon, c.fg, size = 22.dp)
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(value, color = c.fg, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold)
            Text(label, color = c.muted, fontSize = 11.sp, lineHeight = 14.sp)
        }
    }
}
