package com.ridenow.app.ui.more

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ridenow.app.ui.booking.Vehicles
import com.ridenow.app.ui.booking.money
import com.ridenow.app.ui.components.*
import com.ridenow.app.ui.theme.RideTheme
import kotlinx.coroutines.delay

private const val EmergencyNumber = "112"

/** Map-like backdrop with a bottom card, shared by the booking-state screens. */
@Composable
private fun MapCardScreen(onBack: (() -> Unit)?, card: @Composable ColumnScope.() -> Unit) {
    val c = RideTheme.colors
    if (onBack != null) BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize().background(c.bg)) {
        Canvas(Modifier.fillMaxSize()) { drawCityStreets(c, 0.5f) }
        SheetCard(Modifier.align(Alignment.BottomCenter), content = card)
    }
}

@Composable
private fun RouteStrip(pickup: String, drop: String) {
    val c = RideTheme.colors
    Column(Modifier.padding(top = 14.dp).fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(10.dp).clip(CircleShape).background(c.accent)); Text("  $pickup", color = c.muted, fontSize = 14.sp) }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(10.dp).background(c.fg)); Text("  $drop", color = c.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun VehicleStrip(vehicle: Int, right: String) {
    val c = RideTheme.colors
    val v = Vehicles[vehicle.coerceIn(0, Vehicles.lastIndex)]
    Row(Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text("${v.name} · ${v.seats} seats", color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold); Text(right, color = c.muted, fontSize = 12.sp) }
        Text(money(v.fare), color = c.fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------- No drivers available ----------

@Composable
fun NoDriversScreen(ctx: MoreCtx, onTryAgain: () -> Unit, onSchedule: () -> Unit, onChangeVehicle: () -> Unit, onBack: () -> Unit) {
    val c = RideTheme.colors
    MapCardScreen(onBack) {
        RouteStrip("Current location", ctx.dest)
        Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(RideIcons.Car, c.danger, 48.dp)
            Column(Modifier.padding(start = 12.dp)) {
                Text("No drivers nearby", color = c.fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
            }
        }
        Text("All vehicles are busy right now. Try again in a moment, or schedule your ride for later.", color = c.muted, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 8.dp))
        VehicleStrip(ctx.vehicle, "Pickup unavailable")
        RideButton("Try again", onTryAgain, Modifier.padding(top = 16.dp))
        RideButton("Schedule for later", onSchedule, style = RideButtonStyle.Text)
        RideButton("Change vehicle", onChangeVehicle, style = RideButtonStyle.Text)
    }
}

// ---------- Driver cancelled, finding another ----------

@Composable
fun DriverCancelledScreen(ctx: MoreCtx, onFound: () -> Unit, onCancel: () -> Unit) {
    val c = RideTheme.colors
    val spin by rememberInfiniteTransition(label = "spin").animateFloat(0f, 360f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "a")
    LaunchedEffect(Unit) { delay(6000); onFound() }
    MapCardScreen(onCancel) {
        RouteStrip("Current location", ctx.dest)
        Row(Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.danger.copy(alpha = 0.12f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RideIcon(RideIcons.Alert, c.danger, size = 18.dp)
            Text("  Mira cancelled the trip. Your request is still active.", color = c.fg, fontSize = 14.sp, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(28.dp).rotate(spin)) { drawArc(c.accent, 0f, 270f, false, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round)) }
            Text("  Finding you another driver…", color = c.fg, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
        }
        Text("We kept your pickup and drop-off.", color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
        VehicleStrip(ctx.vehicle, "Pickup in about 4 min")
        RideButton("Cancel request", onCancel, Modifier.padding(top = 16.dp), style = RideButtonStyle.Text)
    }
}

// ---------- Driver arrived and waiting ----------

@Composable
fun DriverWaitingScreen(ctx: MoreCtx, onCall: () -> Unit, onMessage: () -> Unit, onCancel: () -> Unit, onStart: () -> Unit) {
    val c = RideTheme.colors
    val left = rememberCountdown(150)
    var confirm by remember { mutableStateOf(false) }
    val v = Vehicles[ctx.vehicle.coerceIn(0, Vehicles.lastIndex)]
    MapCardScreen(null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Arrived", color = c.onAccent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(50)).background(c.accent).padding(horizontal = 10.dp, vertical = 4.dp))
            Text("  ${ctx.pickup}", color = c.muted, fontSize = 13.sp)
        }
        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar("MT", 52.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("Mira has arrived", color = c.fg, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
                Text("Meet at your pickup point", color = c.muted, fontSize = 13.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(mmss(left), color = c.accent, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { contentDescription = "${mmss(left)} of free waiting left" })
                Text("waiting", color = c.muted, fontSize = 11.sp)
            }
        }
        Row(Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("RN 2100", color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold); Text("${v.name} · Silver", color = c.muted, fontSize = 12.sp) }
            Column(horizontalAlignment = Alignment.End) { Text("PIN", color = c.muted, fontSize = 11.sp, letterSpacing = 1.1.sp); Text("4821", color = c.fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 3.sp) }
        }
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton(RideIcons.Call, "Call", Modifier.weight(1f), onCall)
            ActionButton(RideIcons.Message, "Message", Modifier.weight(1f), onMessage)
        }
        RideButton("Start trip", onStart, Modifier.padding(top = 10.dp))
        RideButton("Cancel ride", { confirm = true }, style = RideButtonStyle.Text)
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false }, title = { Text("Cancel this ride?") },
        text = { Text("Mira is already at your pickup point.") },
        confirmButton = { TextButton({ confirm = false; onCancel() }) { Text("Cancel ride", color = c.danger) } },
        dismissButton = { TextButton({ confirm = false }) { Text("Keep ride") } },
    )
}

@Composable
fun ActionButton(icon: List<String>, label: String, modifier: Modifier, onClick: () -> Unit, tint: Color? = null) {
    val c = RideTheme.colors
    Row(
        modifier.height(52.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, c.border, RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) { RideIcon(icon, tint ?: c.accent, size = 20.dp); Text("  $label", color = tint ?: c.fg, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
}

// ---------- Ride cancelled ----------

@Composable
fun RideCancelledScreen(ctx: MoreCtx, reason: String, onBook: () -> Unit, onHome: () -> Unit) {
    val c = RideTheme.colors
    BackHandler(onBack = onHome)
    MoreScaffold("", onHome, bottom = { RideButton("Book another ride", onBook); RideButton("Back to home", onHome, style = RideButtonStyle.Text) }) {
        Hero(RideIcons.Close, "Ride cancelled.", "You can book again any time.", danger = true)
        GroupCard(Modifier.padding(top = 24.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("TRIP #8F3K2Q", color = c.muted, fontSize = 11.sp, letterSpacing = 1.1.sp, modifier = Modifier.weight(1f))
                    Text("Cancelled", color = c.danger, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Text("Current location", color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
                Text(ctx.dest, color = c.fg, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
                HorizontalDivider(color = c.line, modifier = Modifier.padding(vertical = 12.dp))
                InfoRow("Reason", reason)
            }
        }
    }
}

// ---------- Change drop-off during a trip ----------

@Composable
fun ChangeDropScreen(ctx: MoreCtx, onBack: () -> Unit, onConfirm: (String) -> Unit) {
    val c = RideTheme.colors
    val options = listOf("Aurora Medical Centre" to "Sector 3, Skyline Way", "Orbit Ring Station" to "Sector 1, Ring Road")
    var pick by rememberSaveable { mutableIntStateOf(0) }
    val extra = listOf(2.10 to 3, 3.40 to 5)[pick]
    MoreScaffold("Change drop-off", onBack, subtitle = "Currently going to ${ctx.dest}.", bottom = {
        RideButton("Confirm new drop-off", { onConfirm(options[pick].first) }); RideButton("Keep current drop-off", onBack, style = RideButtonStyle.Text)
    }) {
        GroupCard(Modifier.padding(top = 16.dp)) {
            options.forEachIndexed { i, (n, s) -> if (i > 0) RowDivider(); Column(Modifier.padding(horizontal = 16.dp)) { RadioRow(n, pick == i, { pick = i }, s) } }
        }
        SectionLabel("NEW ESTIMATE")
        GroupCard {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                InfoRow("Fare", "${money(16.80 + extra.first)}  +${money(extra.first)}", strong = true)
                InfoRow("Time", "${19 + extra.second} min  +${extra.second}")
            }
        }
        Text("Your driver will be told about the new drop-off.", color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
    }
}

// ---------- Emergency call confirmation ----------

@Composable
fun EmergencyCountdownScreen(ctx: MoreCtx, onCancel: () -> Unit) {
    val c = RideTheme.colors
    val context = LocalContext.current
    val left = rememberCountdown(5)
    var called by remember { mutableStateOf(false) }
    LaunchedEffect(left) { if (left == 0 && !called) { called = true; context.dial(EmergencyNumber); onCancel() } }
    BackHandler(onBack = onCancel)
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(160.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(c.danger.copy(alpha = 0.2f), size.minDimension / 2 - 4.dp.toPx(), style = Stroke(6.dp.toPx()))
                    drawArc(c.danger, -90f, 360f * left / 5f, false, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
                }
                Text("$left", color = c.fg, fontSize = 64.sp, fontWeight = FontWeight.Light, modifier = Modifier.semantics { contentDescription = "Calling in $left seconds" })
            }
            Text("Calling emergency services", color = c.fg, fontSize = 26.sp, fontWeight = FontWeight.Light, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 24.dp).semantics { heading() })
            Text("Your live location and trip details are shared with your trusted contacts.", color = c.muted, fontSize = 14.sp, lineHeight = 21.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
            GroupCard(Modifier.padding(top = 24.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { RideIcon(RideIcons.Pin, c.accent, size = 18.dp); Text("  Near Meridian Walk and Helix Ave", color = c.fg, fontSize = 14.sp) }
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) { RideIcon(RideIcons.Car, c.accent, size = 18.dp); Text("  Orbit · RN 2100 · Mira Tanaka", color = c.fg, fontSize = 14.sp) }
                }
            }
        }
        RideButton("Call now", { if (!called) { called = true; context.dial(EmergencyNumber); onCancel() } })
        RideButton("Cancel", onCancel, style = RideButtonStyle.Text)
    }
}

// ---------- In-app call (simulated audio screen) ----------

@Composable
fun InAppCallScreen(onEnd: () -> Unit) {
    val c = RideTheme.colors
    var seconds by remember { mutableIntStateOf(0) }
    var mute by remember { mutableStateOf(false) }
    var speaker by remember { mutableStateOf(false) }
    var keypad by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { while (true) { delay(1000); seconds++ } }
    BackHandler(onBack = onEnd)
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) { RideIcon(RideIcons.Lock, c.muted, size = 14.dp); Text("  Call via RideNow · number stays private", color = c.muted, fontSize = 13.sp) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Avatar("MT", 112.dp)
            Text("Mira Tanaka", color = c.fg, fontSize = 28.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(top = 20.dp).semantics { heading() })
            Text(mmss(seconds), color = c.accent, fontSize = 18.sp, modifier = Modifier.padding(top = 6.dp))
            Text("RN 2100 · Orbit · Silver", color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            if (keypad) Text("Keypad is not needed for driver calls.", color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            CallToggle("Mute", RideIcons.Phone, mute) { mute = !mute }
            CallToggle("Speaker", RideIcons.Call, speaker) { speaker = !speaker }
            CallToggle("Keypad", RideIcons.Menu, keypad) { keypad = !keypad }
        }
        Box(Modifier.padding(vertical = 28.dp).size(68.dp).clip(CircleShape).background(c.danger).clickable(role = Role.Button, onClick = onEnd).semantics { contentDescription = "End call" }, contentAlignment = Alignment.Center) {
            RideIcon(RideIcons.Call, if (c.isDark) Color(0xFF2B0A0A) else Color.White, Modifier.rotate(135f), size = 28.dp)
        }
    }
}

@Composable
private fun CallToggle(label: String, icon: List<String>, on: Boolean, onClick: () -> Unit) {
    val c = RideTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.selectableToggle(on, onClick)) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(if (on) c.accent else c.field).border(1.dp, if (on) c.accent else c.border, CircleShape), contentAlignment = Alignment.Center) { RideIcon(icon, if (on) c.onAccent else c.fg) }
        Text(label, color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

private fun Modifier.selectableToggle(on: Boolean, onClick: () -> Unit): Modifier =
    this.clip(RoundedCornerShape(12.dp)).toggleableCompat(on, onClick)

private fun Modifier.toggleableCompat(on: Boolean, onClick: () -> Unit): Modifier =
    this.clickable(role = Role.Switch, onClick = onClick).semantics { toggleableState = if (on) androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off }

// ---------- Chat with the driver ----------

private class Msg(val text: String, val mine: Boolean, val time: String)

@Composable
fun DriverChatScreen(onBack: () -> Unit) {
    val c = RideTheme.colors
    val msgs = remember { mutableStateListOf(Msg("I am 2 minutes away, near the Helix Ave corner.", false, "9:38"), Msg("Thanks, I am at the pickup point.", true, "9:39"), Msg("Great, see you now.", false, "9:39")) }
    var draft by remember { mutableStateOf("") }
    val list = rememberLazyListStateCompat()
    val send = { t: String -> if (t.isNotBlank()) { msgs.add(Msg(t.trim(), true, java.time.LocalTime.now().let { "%d:%02d".format(it.hour, it.minute) })); draft = "" } }
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = "Back" }, contentAlignment = Alignment.Center) { RideIcon(RideIcons.Back, c.fg, size = 22.dp) }
            Avatar("MT", 40.dp)
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text("Mira Tanaka", color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
                Text("Arriving in 1 min · Orbit · RN 2100", color = c.muted, fontSize = 12.sp)
            }
        }
        HorizontalDivider(color = c.line)
        Column(Modifier.weight(1f).verticalScroll(list).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pickup: 128 Meridian Walk", color = c.muted, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text("Today 9:38", color = c.muted, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            msgs.forEach { m ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start) {
                    Text(m.text, color = if (m.mine) c.onAccent else c.fg, fontSize = 15.sp, lineHeight = 21.sp,
                        modifier = Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(18.dp)).background(if (m.mine) c.accent else c.field).then(if (m.mine) Modifier else Modifier.border(1.dp, c.border, RoundedCornerShape(18.dp))).padding(horizontal = 14.dp, vertical = 10.dp))
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("I am at the pickup", "Running 2 min late", "Please wait for me").forEach { Chip(it, false, { send(it) }) }
        }
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            RideTextField(draft, { draft = it.take(300) }, "Message", RideIcons.Message, Modifier.weight(1f))
            Box(Modifier.padding(start = 8.dp).size(52.dp).clip(CircleShape).background(if (draft.isBlank()) c.line else c.accent).clickable(enabled = draft.isNotBlank(), role = Role.Button) { send(draft) }.semantics { contentDescription = "Send" }, contentAlignment = Alignment.Center) {
                RideIcon(RideIcons.Back, if (draft.isBlank()) c.muted else c.onAccent, Modifier.rotate(180f))
            }
        }
    }
}

@Composable private fun rememberLazyListStateCompat() = rememberScrollState()

// ---------- Trusted contact tracking page ----------

@Composable
fun TrustedTrackingScreen(ctx: MoreCtx, onBack: () -> Unit, onReport: () -> Unit) {
    val c = RideTheme.colors
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = "Back" }, contentAlignment = Alignment.Center) { RideIcon(RideIcons.Back, c.fg, size = 22.dp) }
            Text("ridenow.app/t/8F3K2Q", color = c.muted, fontSize = 13.sp, modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).background(c.field).padding(horizontal = 14.dp, vertical = 8.dp))
        }
        Box(Modifier.fillMaxWidth().height(220.dp)) {
            Canvas(Modifier.fillMaxSize()) { drawCityStreets(c, 0.6f) }
            Row(Modifier.align(Alignment.TopStart).padding(12.dp).clip(RoundedCornerShape(50)).background(c.bg.copy(alpha = 0.85f)).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(c.accent)); Text("  Live", color = c.fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text("SHARED TRIP", color = c.muted, fontSize = 11.sp, letterSpacing = 1.1.sp)
            Text("${ctx.name.substringBefore(' ')} is on the way to ${ctx.dest}.", color = c.fg, fontSize = 24.sp, fontWeight = FontWeight.Light, lineHeight = 30.sp, modifier = Modifier.padding(top = 6.dp).semantics { heading() })
            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("ARRIVING IN", "12 min", Modifier.weight(1f)); Stat("DISTANCE LEFT", "6.1 km", Modifier.weight(1f))
            }
            GroupCard(Modifier.padding(top = 14.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar("MT"); Column(Modifier.padding(start = 12.dp).weight(1f)) { Text("Mira Tanaka", color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold); Text("Orbit · Silver", color = c.muted, fontSize = 12.sp) }
                    Text("RN 2100", color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            ActionButton(RideIcons.Call, "Call ${ctx.name.substringBefore(' ')}", Modifier.padding(top = 14.dp).fillMaxWidth(), { context.dial(ctx.phone.filter { it.isDigit() || it == '+' }) })
            RideButton("Report a safety concern", onReport, Modifier.padding(top = 10.dp), style = RideButtonStyle.Text)
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    val c = RideTheme.colors
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(14.dp)) {
        Text(label, color = c.muted, fontSize = 11.sp, letterSpacing = 1.1.sp); Text(value, color = c.fg, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
    }
}

// ---------- Android lock-screen live trip notification ----------

/** Ongoing notification with progress and Call / Message actions. Appears on the lock screen too. */
object LiveTripNotification {
    private const val CHANNEL = "live_trip"
    private const val ID = 2100

    fun show(context: Context, minutes: Int, driver: String = "Mira", plate: String = "RN 2100", pin: String = "4821") {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Live trip", NotificationManager.IMPORTANCE_HIGH).apply { lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC; description = "Shows your driver and trip progress" })
        fun intent(uri: String, req: Int) = PendingIntent.getActivity(context, req, Intent(if (uri.startsWith("tel")) Intent.ACTION_DIAL else Intent.ACTION_SENDTO, Uri.parse(uri)), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE) }
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(context.applicationInfo.icon.takeIf { it != 0 } ?: android.R.drawable.ic_dialog_info)
            .setContentTitle(if (minutes <= 0) "Your driver has arrived" else "Driver arriving in $minutes min")
            .setContentText("$driver · Orbit · $plate · PIN $pin")
            .setCategory(NotificationCompat.CATEGORY_PROGRESS).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true).setOnlyAlertOnce(true).setProgress(10, (10 - minutes).coerceIn(0, 10), false)
            .setContentIntent(open)
            .addAction(0, "Call", intent("tel:", 1)).addAction(0, "Message", intent("smsto:", 2))
            .build()
        nm.notify(ID, n)
    }

    fun cancel(context: Context) { (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(ID) }
}

/** Preview of the notification as it looks on the lock screen; also posts the real one. */
@Composable
fun LockScreenTripScreen(onBack: () -> Unit) {
    val c = RideTheme.colors
    val context = LocalContext.current
    LaunchedEffect(Unit) { LiveTripNotification.show(context, 4) }
    MoreScaffold("Live trip notification", onBack, subtitle = "A persistent notification keeps your trip on the lock screen.") {
        Column(Modifier.padding(top = 20.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(24.dp)).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text("RideNow · now", color = c.muted, fontSize = 12.sp) }
            Text("Driver arriving in 4 min", color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
            Text("Mira · Orbit · RN 2100 · PIN 4821", color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
            Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.line)) { Box(Modifier.fillMaxWidth(0.6f).height(4.dp).background(c.accent)) }
            Row(Modifier.padding(top = 12.dp)) {
                Text("Call", color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.heightIn(min = 40.dp).padding(end = 24.dp))
                Text("Message", color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Text("Ongoing notification with live progress and actions.", color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
        RideButton("Remove notification", { LiveTripNotification.cancel(context) }, Modifier.padding(top = 16.dp), style = RideButtonStyle.Text)
    }
}
