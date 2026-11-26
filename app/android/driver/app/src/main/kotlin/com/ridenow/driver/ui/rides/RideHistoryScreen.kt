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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.ridenow.driver.R
import com.ridenow.driver.ui.components.*
import com.ridenow.driver.ui.data.*
import com.ridenow.driver.ui.home.MainTab
import com.ridenow.driver.ui.theme.RideTheme

/** Ride History tab: status pills, search, a filter sheet, and rides grouped by day. */
@Composable
fun RideHistoryScreen(
    unread: Int, initials: String, onTab: (MainTab) -> Unit, onBell: () -> Unit, onProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val preview = LocalInspectionMode.current
    val scope = rememberCoroutineScope()
    var rides by remember { mutableStateOf<List<DriverHistoryRide>>(emptyList()) }
    var cursor by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(!preview) }
    var failed by remember { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }

    suspend fun fetch(next: String? = null) {
        loading = true
        failed = false
        try {
            val page = RideHistoryApi.list(context, next)
            rides = if (next == null) page.items else (rides + page.items).distinctBy { it.id }
            cursor = page.nextCursor
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            failed = true
        } finally {
            loading = false
        }
    }
    LaunchedEffect(preview) { if (!preview) fetch() }

    val chosen = rides.firstOrNull { it.id == selected }
    if (chosen != null) {
        BackHandler { selected = null }
        HistoryDetails(chosen) { selected = null }
        return
    }

    val c = RideTheme.colors
    var status by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var sheet by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf(0) }
    var span by rememberSaveable { mutableStateOf(0) }
    val maxAge = intArrayOf(Int.MAX_VALUE, 0, 6, 29)[span]
    val q = query.trim().lowercase()
    var list = rides.filter {
        (status == 0 || (status == 1 && it.completed) || (status == 2 && it.cancelled)) && it.ageDays <= maxAge &&
            (q.isEmpty() || listOf(it.pickup, it.drop, it.id, Calendars.dayLabel(false, it.ageDays)).any { s -> s.lowercase().contains(q) })
    }
    list = when (sort) {
        1 -> list.sortedWith(compareByDescending<DriverHistoryRide> { it.ageDays }.thenBy { it.startMinute })
        2 -> list.sortedByDescending { it.amountMinor }
        else -> list.sortedWith(compareBy<DriverHistoryRide> { it.ageDays }.thenByDescending { it.startMinute })
    }
    val grouped = if (sort == 2) listOf("" to list) else list.groupBy { it.ageDays }.map { (age, rs) -> Calendars.dayLabel(false, age) to rs }

    TabScaffold(MainTab.Rides, onTab, unread, initials, onBell, onProfile, modifier) {
        PageTitle(str(R.string.hist_title))
        FilterPills(listOf(str(R.string.filter_all), str(R.string.ride_completed), str(R.string.ride_cancelled)), status, { status = it }, Modifier.padding(top = 12.dp))
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            SearchField(query, { query = it }, str(R.string.hist_search), Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            CircleButton(RideIcons.Filter, str(R.string.hist_filter), { sheet = true }, size = 52.dp)
        }
        if (!loading && !failed && cursor == null && list.isEmpty()) EmptyState(RideIcons.Receipt, str(R.string.hist_empty_title), str(R.string.hist_empty_text))
        grouped.filter { it.second.isNotEmpty() }.forEach { (label, rides) ->
            if (label.isNotEmpty()) Text(label, color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp).semantics { heading() })
            else Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { rides.forEach { RideCard(it) { selected = it.id } } }
        }
        if (failed) {
            Text(str(R.string.ride_history_error), color = c.muted, modifier = Modifier.padding(top = 12.dp))
            RideButton(str(R.string.sys_try_again), { scope.launch { fetch(cursor) } }, style = RideButtonStyle.Text)
        } else if (cursor != null) {
            RideButton(str(R.string.ride_history_more), { scope.launch { fetch(cursor) } }, style = RideButtonStyle.Text,
                enabled = !loading, loading = loading)
        } else if (loading) {
            CircularProgressIndicator(color = c.accent, modifier = Modifier.padding(top = 20.dp))
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
private fun RideCard(r: DriverHistoryRide, onOpen: () -> Unit) {
    val c = RideTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClick = onOpen).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RouteDots(58.dp, Modifier.padding(end = 12.dp))
        Column(Modifier.weight(1f)) {
            Text(Calendars.timeLabel(r.startMinute), color = c.muted, fontSize = 12.sp)
            Text(r.pickup, color = c.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, modifier = Modifier.padding(top = 2.dp))
            Text(r.drop, color = c.muted, fontSize = 13.sp, maxLines = 2, modifier = Modifier.padding(top = 10.dp))
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
            Text(r.fare, color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(historyStatus(r), color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
        RideIcon(RideIcons.Chevron, c.muted, size = 16.dp, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun historyStatus(ride: DriverHistoryRide): String = str(when {
    ride.completed -> R.string.ride_completed
    ride.cancelled -> R.string.ride_cancelled
    else -> R.string.ride_history_active
})

@Composable
private fun HistoryDetails(ride: DriverHistoryRide, onBack: () -> Unit) {
    val c = RideTheme.colors
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        ScreenTop(str(R.string.ride_details), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(historyStatus(ride), color = c.fg, fontSize = 20.sp)
            Text(Calendars.dateTime(false, ride.ageDays, ride.startMinute), color = c.muted)
            Text(str(R.string.ride_history_pickup), color = c.muted)
            Text(ride.pickup, color = c.fg)
            Text(str(R.string.ride_history_destination), color = c.muted)
            Text(ride.drop, color = c.fg)
            Text(ride.category, color = c.fg)
            val fareLabel = if (ride.completed) R.string.ride_history_total else R.string.ride_history_estimate
            Text(androidx.compose.ui.res.stringResource(fareLabel, ride.fare), color = c.fg)
            when (ride.payment) {
                "DUE" -> Text(str(R.string.ride_history_cash_due), color = c.muted)
                "COLLECTED" -> Text(str(R.string.ride_history_cash_collected), color = c.muted)
                "DISPUTED" -> Text(str(R.string.ride_history_cash_disputed), color = c.muted)
                else -> Unit
            }
        }
    }
}
