package com.ridenow.driver.ui.system

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.provider.Settings
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ridenow.driver.R
import com.ridenow.driver.ui.components.*
import com.ridenow.driver.ui.theme.RideTheme
import com.ridenow.driver.ui.util.entrance
import com.ridenow.driver.ui.util.openAppNotificationSettings

/** Calls [action] each time the driver returns to the app, for example from Settings. */
@Composable
fun OnResume(action: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val latest by rememberUpdatedState(action)
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) latest() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

/** True while the phone has an internet-capable network. Pass a new [refreshKey] to check again on demand. */
@Composable
fun rememberOnline(refreshKey: Int): Boolean {
    val context = LocalContext.current
    val cm = remember { context.getSystemService(ConnectivityManager::class.java) }
    fun now() = cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    var online by remember(refreshKey) { mutableStateOf(now()) }
    DisposableEffect(cm) {
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { online = true }
            override fun onLost(network: Network) { online = now() }
        }
        cm.registerDefaultNetworkCallback(cb)
        onDispose { cm.unregisterNetworkCallback(cb) }
    }
    return online
}

/** True while the phone's location switch (GPS) is on. Updates when the driver changes it in quick settings or Settings. */
@Composable
fun rememberLocationEnabled(): Boolean {
    val context = LocalContext.current
    val lm = remember { context.getSystemService(LocationManager::class.java) }
    var on by remember { mutableStateOf(LocationManagerCompat.isLocationEnabled(lm)) }
    DisposableEffect(lm) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { on = LocationManagerCompat.isLocationEnabled(lm) }
        }
        val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION).apply { addAction("android.location.MODE_CHANGED") }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    OnResume { on = LocationManagerCompat.isLocationEnabled(lm) }
    return on
}

/** True while notifications are allowed for the app. Re-checked when the driver comes back from Settings. */
@Composable
fun rememberNotificationsEnabled(): Boolean {
    val context = LocalContext.current
    var on by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    OnResume { on = NotificationManagerCompat.from(context).areNotificationsEnabled() }
    return on
}

/** Rings, a round icon, title, text, optional list and buttons. Shared by the system-state screens, same look as the Rider app. */
@Composable
private fun StateLayout(
    icon: List<String>, title: String, subtitle: String, primary: String, onPrimary: () -> Unit, modifier: Modifier = Modifier,
    secondary: String? = null, onSecondary: () -> Unit = {}, extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val c = RideTheme.colors
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.padding(top = 24.dp).size(150.dp).entrance(0), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(c.ring1, 74.dp.toPx(), style = Stroke(1.dp.toPx())); drawCircle(c.ring2, 57.dp.toPx(), style = Stroke(1.dp.toPx()))
                }
                Box(Modifier.size(80.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.14f)).border(1.5.dp, c.accent, CircleShape), contentAlignment = Alignment.Center) { RideIcon(icon, c.accent, size = 34.dp) }
            }
            Text(title, color = c.fg, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Light, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 24.dp).semantics { heading() }.entrance(80))
            Text(subtitle, color = c.muted, fontSize = 14.sp, lineHeight = 21.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp).widthIn(max = 320.dp))
            if (extra != null) Column(Modifier.padding(top = 22.dp, bottom = 12.dp).entrance(160), content = extra)
        }
        RideButton(primary, onPrimary)
        if (secondary != null) RideButton(secondary, onSecondary, style = RideButtonStyle.Text, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun Benefits(items: List<Pair<List<String>, Int>>) {
    val c = RideTheme.colors
    GroupCard {
        items.forEachIndexed { i, (icon, text) ->
            if (i > 0) RowDivider()
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                RideIcon(icon, c.accent, size = 20.dp)
                Text(str(text), color = c.fg, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.weight(1f).padding(start = 14.dp))
            }
        }
    }
}

@Composable
private fun Steps(items: List<Int>) {
    val c = RideTheme.colors
    GroupCard {
        items.forEachIndexed { i, text ->
            if (i > 0) RowDivider()
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(c.fill), contentAlignment = Alignment.Center) { Text("${i + 1}", color = c.onFill, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                Text(str(text), color = c.fg, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.weight(1f).padding(start = 14.dp))
            }
        }
    }
}

/** Shown over the whole app while the phone is offline. Rides and earnings already on the phone stay reachable. */
@Composable
fun NoInternetScreen(onRetry: () -> Unit, onOpenRides: () -> Unit, modifier: Modifier = Modifier) {
    StateLayout(RideIcons.WifiOff, str(R.string.sys_offline_title), str(R.string.sys_offline_text), str(R.string.sys_try_again), onRetry, modifier, extra = {
        SectionTitle(str(R.string.sys_available_offline), Modifier.fillMaxWidth())
        GroupCard { NavRow(RideIcons.Clock, str(R.string.hist_title), onOpenRides, subtitle = str(R.string.sys_offline_rides_sub)) }
    })
}

/**
 * Shown when the phone's location switch is off. A driver cannot receive nearby requests or navigate without it.
 * [onNotNow] hides the screen until the driver tries to go online again.
 */
@Composable
fun LocationOffScreen(onNotNow: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    StateLayout(
        RideIcons.Pin, str(R.string.sys_loc_title), str(R.string.sys_loc_text), str(R.string.sys_loc_turn_on),
        { runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
        modifier, str(R.string.notif_not_now), onNotNow,
        extra = {
            Benefits(listOf(RideIcons.Car to R.string.sys_loc_b1, RideIcons.Navigate to R.string.sys_loc_b2, RideIcons.Shield to R.string.sys_loc_b3))
        },
    )
}

/** Shown when notifications are off for the app. Without them a driver misses requests while the app is closed. */
@Composable
fun NotificationsOffScreen(onNotNow: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    StateLayout(
        RideIcons.Bell, str(R.string.sys_ntf_title), str(R.string.sys_ntf_text), str(R.string.bg_open_settings), { openAppNotificationSettings(context) },
        modifier, str(R.string.notif_not_now), onNotNow,
        extra = {
            Steps(listOf(R.string.sys_ntf_step1, R.string.sys_ntf_step2, R.string.sys_ntf_step3))
        },
    )
}
