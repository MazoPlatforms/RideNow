package com.ridenow.driver.ui.rides

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
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
import com.ridenow.driver.ui.theme.RideTheme
import com.ridenow.driver.ui.util.Money

/** Ride History tab: status pills, search, a filter sheet, and rides grouped by day. */
@Composable
fun RideHistoryScreen(
    store: DriverStore, unread: Int, initials: String, onTab: (MainTab) -> Unit, onBell: () -> Unit, onProfile: () -> Unit, onOpenRide: (RideRecord) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = RideTheme.colors
    var status by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var sheet by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf(0) }
    var span by rememberSaveable { mutableStateOf(0) }
    val maxAge = intArrayOf(Int.MAX_VALUE, 0, 6, 29)[span]
    val q = query.trim().lowercase()
    var list = store.rides.filter {
        (status == 0 || (status == 1 && it.status == RideStatus.Completed) || (status == 2 && it.status == RideStatus.Cancelled)) && it.ageDays <= maxAge &&
            (q.isEmpty() || listOf(it.pickup, it.drop, it.rider, it.ref, Calendars.dayLabel(store.sample, it.ageDays)).any { s -> s.lowercase().contains(q) })
    }
    list = when (sort) {
        1 -> list.sortedWith(compareByDescending<RideRecord> { it.ageDays }.thenBy { it.startMin })
        2 -> list.sortedByDescending { it.fare }
        else -> list.sortedWith(compareBy<RideRecord> { it.ageDays }.thenByDescending { it.startMin })
    }
    val grouped = if (sort == 2) listOf("" to list) else list.groupBy { it.ageDays }.map { (age, rs) -> Calendars.dayLabel(store.sample, age) to rs }

    TabScaffold(MainTab.Rides, onTab, unread, initials, onBell, onProfile, modifier) {
        PageTitle(str(R.string.hist_title))
        FilterPills(listOf(str(R.string.filter_all), str(R.string.ride_completed), str(R.string.ride_cancelled)), status, { status = it }, Modifier.padding(top = 12.dp))
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            SearchField(query, { query = it }, str(R.string.hist_search), Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            CircleButton(RideIcons.Filter, str(R.string.hist_filter), { sheet = true }, size = 52.dp)
        }
        if (grouped.all { it.second.isEmpty() }) EmptyState(RideIcons.Receipt, str(R.string.hist_empty_title), str(R.string.hist_empty_text))
        grouped.forEach { (label, rides) ->
            if (label.isNotEmpty()) Text(label, color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp).semantics { heading() })
            else Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { rides.forEach { RideCard(it, store.sample, onOpenRide) } }
        }
        Spacer(Modifier.height(16.dp))
    }
    if (sheet) RideSheet(str(R.string.hist_filter), { sheet = false }) { close ->
        Text(str(R.string.hist_sort), color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
        Options(listOf(R.string.sort_newest, R.string.sort_oldest, R.string.sort_fare), sort) { sort = it }
        Text(str(R.string.hist_range), color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
        Options(listOf(R.string.range_all, R.string.earn_today, R.string.range_7, R.string.range_30), span) { span = it }
        Spacer(Modifier.height(14.dp))
        RideButton(str(R.string.dialog_ok), { close() })
    }
}

@Composable
private fun Options(ids: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    GroupCard {
        ids.forEachIndexed { i, id ->
            if (i > 0) RowDivider()
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).selectable(i == selected, role = Role.RadioButton) { onSelect(i) }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(str(id), color = RideTheme.colors.fg, fontSize = 15.sp, modifier = Modifier.weight(1f)); RadioDot(i == selected)
            }
        }
    }
}

@Composable
private fun RideCard(r: RideRecord, sample: Boolean, onOpen: (RideRecord) -> Unit) {
    val c = RideTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(16.dp)).clickable(role = Role.Button) { onOpen(r) }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RouteDots(58.dp, Modifier.padding(end = 12.dp))
        Column(Modifier.weight(1f)) {
            Text(Calendars.timeLabel(r.startMin), color = c.muted, fontSize = 12.sp)
            Text(r.pickup, color = c.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, modifier = Modifier.padding(top = 2.dp))
            Text(r.drop, color = c.muted, fontSize = 13.sp, maxLines = 2, modifier = Modifier.padding(top = 10.dp))
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
            Text(Money.fmt(r.fare), color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            StatusFor(r, Modifier.padding(top = 8.dp))
        }
        RideIcon(RideIcons.Chevron, c.muted, size = 16.dp, modifier = Modifier.padding(start = 6.dp))
    }
}
