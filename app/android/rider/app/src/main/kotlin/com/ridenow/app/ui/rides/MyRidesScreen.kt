package com.ridenow.app.ui.rides

import com.ridenow.app.ui.components.localizedLabel

import com.ridenow.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import com.ridenow.app.ui.components.RideButton
import com.ridenow.app.ui.components.ScreenTop
import com.ridenow.app.ui.components.SectionLabel
import com.ridenow.app.ui.theme.RideNowTheme
import com.ridenow.app.ui.theme.RideTheme
import com.ridenow.app.ui.util.entrance

enum class RideStatus(val label: String) { Completed("Completed"), Scheduled("Scheduled"), Cancelled("Cancelled"), Active("Active") }
data class RideItem(
    val id: String, val date: String, val pickup: String, val destination: String, val meta: String,
    val fare: String, val status: RideStatus, val payment: String?, val distanceMeters: Double?,
)

@Composable
fun MyRidesScreen(onBack: () -> Unit, onBook: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val preview = LocalInspectionMode.current
    val scope = rememberCoroutineScope()
    var rides by remember { mutableStateOf<List<RideItem>>(emptyList()) }
    var cursor by remember { mutableStateOf<String?>(null) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(!preview) }

    suspend fun fetch(next: String? = null) {
        loading = true
        loadError = false
        try {
            val page = RideHistoryApi.list(context, next)
            rides = if (next == null) page.items else (rides + page.items).distinctBy { it.id }
            cursor = page.nextCursor
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            loadError = true
        } finally {
            loading = false
        }
    }
    LaunchedEffect(preview) { if (!preview) fetch() }

    val ride = rides.firstOrNull { it.id == selected }
    if (ride != null) {
        androidx.activity.compose.BackHandler { selected = null }
        HistoryDetails(ride, { selected = null }) {
            rides = rides.map { if (it.id == ride.id) it.copy(status = RideStatus.Cancelled) else it }
        }
        return
    }
    val textMenuRides = stringResource(R.string.menu_rides)
    val textAll = stringResource(R.string.text_all)
    val textNoRidesYet = stringResource(R.string.text_no_rides_yet)
    val textYourTripsAndReceiptsWillAppearHere = stringResource(R.string.text_your_trips_and_receipts_will_appear_here)
    val textBookARide = stringResource(R.string.text_book_a_ride)
    val textUpcoming = stringResource(R.string.text_upcoming)
    val textPast = stringResource(R.string.text_past)

    val c = RideTheme.colors
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = rides.filter { filter == null || it.status.label == filter }
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        ScreenTop(textMenuRides, onBack)
        Row(Modifier.padding(top = 14.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(textAll, filter == null) { filter = null }
            RideStatus.values().forEach { s -> Pill(statusLabel(s), filter == s.label) { filter = s.label } }
        }
        if (rides.isEmpty() && (loading || loadError)) {
            HistoryLoadState(loading) { scope.launch { fetch() } }
        } else if (shown.isEmpty() && cursor == null) {
            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(textNoRidesYet, color = c.fg, fontSize = 24.sp, fontWeight = FontWeight.Light)
                Text(textYourTripsAndReceiptsWillAppearHere, color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp))
                Box(Modifier.width(220.dp)) { RideButton(textBookARide, onBook) }
            }
        } else Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).entrance(60)) {
            val upcoming = shown.filter { it.status == RideStatus.Scheduled || it.status == RideStatus.Active }
            val past = shown - upcoming.toSet()
            if (upcoming.isNotEmpty()) { SectionLabel(textUpcoming); upcoming.forEach { RideCard(it) { selected = it.id }; Spacer(Modifier.height(8.dp)) } }
            if (past.isNotEmpty()) { SectionLabel(textPast); past.forEach { RideCard(it) { selected = it.id }; Spacer(Modifier.height(8.dp)) } }
            if (loadError) HistoryLoadState(false) { scope.launch { fetch(cursor) } }
            else if (cursor != null) RideButton(stringResource(R.string.ride_history_more), { scope.launch { fetch(cursor) } }, enabled = !loading, loading = loading)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun Pill(text: String, on: Boolean, onClick: () -> Unit) {
    val c = RideTheme.colors
    Box(
        Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(if (on) c.accent else androidx.compose.ui.graphics.Color.Transparent)
            .border(1.dp, if (on) c.accent else c.border, RoundedCornerShape(20.dp)).clickable(role = Role.Tab, onClick = onClick).padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (on) c.onAccent else c.fg, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal) }
}

@Composable
private fun RideCard(r: RideItem, onClick: () -> Unit) {
    val c = RideTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(20.dp)).clickable(role = Role.Button, onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp))) {
            drawRect(c.bg)
            listOf(0.3f, 0.7f).forEach { f -> drawLine(c.street, Offset(0f, size.height * f), Offset(size.width, size.height * f), 3.dp.toPx()); drawLine(c.street, Offset(size.width * f, 0f), Offset(size.width * f, size.height), 3.dp.toPx()) }
            val p = Path().apply { moveTo(size.width * 0.2f, size.height * 0.8f); cubicTo(size.width * 0.35f, size.height * 0.5f, size.width * 0.55f, size.height * 0.7f, size.width * 0.8f, size.height * 0.22f) }
            drawPath(p, c.accent, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            drawCircle(c.accent, 3.5.dp.toPx(), Offset(size.width * 0.2f, size.height * 0.8f)); drawCircle(c.fg, 3.5.dp.toPx(), Offset(size.width * 0.8f, size.height * 0.22f))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(r.date, color = c.muted, fontSize = 12.sp)
            Text(r.destination, color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(r.meta, color = c.muted, fontSize = 12.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(r.fare, color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            val on = r.status != RideStatus.Cancelled
            Text(
                statusLabel(r.status), color = if (on) c.accent else c.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(50)).then(if (r.status == RideStatus.Completed) Modifier.background(c.accent.copy(alpha = 0.14f)) else Modifier.border(1.dp, if (on) c.accent else c.border, RoundedCornerShape(50))).padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun RidesPreview() = RideNowTheme(darkTheme = true) { MyRidesScreen({}, {}) }


@Composable
private fun statusLabel(status: RideStatus): String =
    if (status == RideStatus.Active) stringResource(R.string.ride_history_active) else localizedLabel(status.label)

@Composable
private fun HistoryLoadState(loading: Boolean, retry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (loading) CircularProgressIndicator(color = RideTheme.colors.accent)
        else {
            Text(stringResource(R.string.ride_history_error), color = RideTheme.colors.muted)
            TextButton(onClick = retry) { Text(stringResource(R.string.text_try_again), color = RideTheme.colors.accent) }
        }
    }
}

@Composable
private fun HistoryDetails(ride: RideItem, back: () -> Unit, onCancelled: () -> Unit) {
    val c = RideTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirm by rememberSaveable(ride.id) { mutableStateOf(false) }
    var busy by remember(ride.id) { mutableStateOf(false) }
    var error by remember(ride.id) { mutableStateOf(false) }
    val cancellationKey = rememberSaveable(ride.id) { UUID.randomUUID().toString() }
    val receipt = rememberRideReceiptDownload(ride.id)

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        ScreenTop(stringResource(R.string.text_ride_details), back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel(statusLabel(ride.status))
            Text(ride.date, color = c.muted)
            Text(stringResource(R.string.text_pickup), color = c.muted)
            Text(ride.pickup, color = c.fg)
            Text(stringResource(R.string.ride_history_destination), color = c.muted)
            Text(ride.destination, color = c.fg)
            Text(ride.meta, color = c.fg)
            ride.distanceMeters?.let { Text(stringResource(R.string.ride_history_distance, it / 1000), color = c.muted) }
            Text(stringResource(if (ride.payment == null) R.string.ride_history_estimate else R.string.ride_history_total, ride.fare), color = c.fg)
            when (ride.payment) {
                "DUE" -> Text(stringResource(R.string.ride_history_cash_due), color = c.muted)
                "COLLECTED" -> Text(stringResource(R.string.ride_history_cash_collected), color = c.muted)
                "DISPUTED" -> Text(stringResource(R.string.ride_history_cash_disputed), color = c.muted)
                else -> Unit
            }
            receipt.feedback?.let { Text(stringResource(it), color = c.fg) }
            if (error) Text(stringResource(R.string.ride_history_cancel_failed), color = c.fg)
        }
        if (ride.payment == "COLLECTED") {
            RideButton(stringResource(R.string.ride_history_receipt), receipt.start, enabled = !receipt.busy, loading = receipt.busy)
        }
        if (ride.status == RideStatus.Scheduled) {
            RideButton(stringResource(R.string.ride_history_cancel), { confirm = true }, enabled = !busy, loading = busy)
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.text_cancel_scheduled_ride)) },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.text_keep_ride)) } },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    busy = true
                    error = false
                    scope.launch {
                        try {
                            RideHistoryApi.cancelScheduled(context, ride.id, cancellationKey)
                            onCancelled()
                        } catch (failure: CancellationException) {
                            throw failure
                        } catch (_: Exception) {
                            error = true
                        } finally {
                            busy = false
                        }
                    }
                }) { Text(stringResource(R.string.ride_history_cancel)) }
            },
        )
    }
}
