package com.ridenow.app.ui.notifications

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ridenow.app.ui.auth.PhoneAuthApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

import com.ridenow.app.ui.components.offsetByStartDirection

import com.ridenow.app.ui.components.localizedLabel

import com.ridenow.app.R

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
import androidx.compose.ui.tooling.preview.Preview
import com.ridenow.app.ui.components.RideIcon
import com.ridenow.app.ui.components.RideIcons
import com.ridenow.app.ui.components.SectionLabel
import com.ridenow.app.ui.theme.RideNowTheme
import com.ridenow.app.ui.theme.RideTheme
import com.ridenow.app.ui.util.entrance

private enum class Kind(val label: String) { Rides("Rides"), Promotions("Promotions"), Updates("Updates"), Safety("Safety"), Account("Account") }
private class Notice(val id: String, val kind: Kind, val icon: List<String>, val title: String, val body: String, val time: String, val today: Boolean)



/** Account notifications with filters and persisted read state. */
@Composable
fun NotificationsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val textSearchBack = stringResource(R.string.search_back)
    val textMenuNotifications = stringResource(R.string.menu_notifications)
    val textMarkAllRead = stringResource(R.string.text_mark_all_read)
    val textAll = stringResource(R.string.text_all)
    val textYouAreAllCaughtUp = stringResource(R.string.text_you_are_all_caught_up)
    val textNothingNewRightNow = stringResource(R.string.text_nothing_new_right_now)
    val textToday = stringResource(R.string.text_today)
    val textEarlier = stringResource(R.string.text_earlier)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val errorText = stringResource(R.string.saved_settings_failed)
    val labels = mapOf(
        Kind.Rides to stringResource(R.string.text_ride_updates),
        Kind.Promotions to stringResource(R.string.text_promotions_offers),
        Kind.Updates to stringResource(R.string.text_account_updates),
        Kind.Safety to stringResource(R.string.trip_safety),
        Kind.Account to stringResource(R.string.text_account_updates),
    )
    var all by remember { mutableStateOf(emptyList<Notice>()) }
    var unread by remember { mutableStateOf(emptySet<String>()) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        failed = false
        scope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { busy = false }
        }
    }
    fun reload() = run {
        val list = PhoneAuthApi.authorized(context, "GET", "/v1/me/notifications?app=RIDER&limit=100") { it.getJSONArray("items") }
        val readPending = mutableSetOf<String>()
        all = List(list.length()) { index ->
            val item = list.getJSONObject(index)
            val id = item.getString("id")
            val kind = when (item.getString("type").substringBefore('.')) {
                "ride", "payment" -> Kind.Rides
                "promotion" -> Kind.Promotions
                "safety" -> Kind.Safety
                "account" -> Kind.Account
                else -> Kind.Updates
            }
            val time = Instant.parse(item.getString("created_at"))
            val icon = when (kind) {
                Kind.Rides -> RideIcons.Car
                Kind.Promotions -> RideIcons.Tag
                Kind.Updates -> RideIcons.Clock
                Kind.Safety -> RideIcons.Shield
                Kind.Account -> RideIcons.Person
            }
            val state = item.optJSONObject("payload")?.optString("state").orEmpty()
            if (item.isNull("read_at")) readPending.add(id)
            Notice(id, kind, icon, labels.getValue(kind), state.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase),
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date.from(time)),
                time.atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now())
        }
        unread = readPending
    }
    LaunchedEffect(Unit) { reload() }
    fun mark(id: String) = run {
        PhoneAuthApi.authorized(context, "POST", "/v1/me/notifications/$id/read") { Unit }
        unread = unread - id
    }

    val c = RideTheme.colors
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = all.filter { filter == null || it.kind.label == filter }
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.offsetByStartDirection((-12).dp).size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = textSearchBack }, contentAlignment = Alignment.Center) { RideIcon(RideIcons.Back, c.fg, size = 22.dp) }
            Spacer(Modifier.weight(1f))
            Text(textMenuNotifications, color = c.fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.weight(1f))
            Box(Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, enabled = unread.isNotEmpty() && !busy) {
                run {
                    PhoneAuthApi.authorized(context, "POST", "/v1/me/notifications/read-all", JSONObject().put("app", "RIDER")) { Unit }
                    unread = emptySet()
                }
            }.padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                Text(textMarkAllRead, color = if (unread.isEmpty()) c.muted else c.accent, fontSize = 13.sp)
            }
        }
        Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(textAll, filter == null) { filter = null }
            Kind.values().forEach { k -> Pill(localizedLabel(k.label), filter == k.label) { filter = k.label } }
        }
        if (failed) TextButton(onClick = ::reload, enabled = !busy) { Text(errorText, color = c.danger) }
        if (busy && all.isEmpty()) Text(stringResource(R.string.saved_settings_loading), color = c.muted)
        if (shown.isEmpty() && !busy && !failed) Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(textYouAreAllCaughtUp, color = c.fg, fontSize = 24.sp, fontWeight = FontWeight.Light)
            Text(textNothingNewRightNow, color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        } else Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).entrance(60)) {
            val today = shown.filter { it.today }; val earlier = shown.filter { !it.today }
            if (today.isNotEmpty()) { SectionLabel(textToday); today.forEach { NoticeRow(it, it.id in unread) { if (it.id in unread) mark(it.id) } } }
            if (earlier.isNotEmpty()) { SectionLabel(textEarlier); earlier.forEach { NoticeRow(it, it.id in unread) { if (it.id in unread) mark(it.id) } } }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Pill(text: String, on: Boolean, onClick: () -> Unit) {
    val c = RideTheme.colors
    Box(
        Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(if (on) c.accent else androidx.compose.ui.graphics.Color.Transparent).border(1.dp, if (on) c.accent else c.border, RoundedCornerShape(20.dp))
            .clickable(role = Role.Tab, onClick = onClick).padding(horizontal = 18.dp), contentAlignment = Alignment.Center,
    ) { Text(text, color = if (on) c.onAccent else c.fg, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal) }
}

@Composable
private fun NoticeRow(n: Notice, unread: Boolean, onClick: () -> Unit) {
    val textUnread = stringResource(R.string.text_unread)

    val c = RideTheme.colors
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(if (unread) c.accent.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent).border(1.dp, c.border, CircleShape), contentAlignment = Alignment.Center) { RideIcon(n.icon, c.accent) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(n.title, color = c.fg, fontSize = 15.sp, fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal)
                Text(n.body, color = c.muted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(n.time, color = c.muted, fontSize = 12.sp)
                if (unread) Box(Modifier.padding(top = 8.dp).size(8.dp).clip(CircleShape).background(c.accent).semantics { contentDescription = textUnread })
            }
        }
        HorizontalDivider(color = c.line)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun NotificationsPreview() = RideNowTheme(darkTheme = true) { NotificationsScreen({}) }
