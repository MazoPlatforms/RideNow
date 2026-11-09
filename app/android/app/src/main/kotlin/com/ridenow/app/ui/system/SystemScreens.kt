package com.ridenow.app.ui.system

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
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.animation.core.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import com.ridenow.app.ui.components.GroupCard
import com.ridenow.app.ui.components.NavRow
import com.ridenow.app.ui.components.RideButton
import com.ridenow.app.ui.components.RideButtonStyle
import com.ridenow.app.ui.components.RideIcon
import com.ridenow.app.ui.components.RideIcons
import com.ridenow.app.ui.components.RowDivider
import com.ridenow.app.ui.components.SectionLabel
import com.ridenow.app.ui.profile.initialsOf
import com.ridenow.app.ui.theme.RideNowTheme
import com.ridenow.app.ui.theme.RideTheme
import com.ridenow.app.ui.util.entrance
import com.ridenow.app.ui.util.rememberReducedMotion
import kotlinx.coroutines.delay

/** True while the phone has an internet-capable network. Pass a new [refreshKey] to re-check on demand. */
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

/** Rings, a round icon, title, text and buttons. Used by the system-state screens. */
@Composable
private fun StateLayout(
    icon: List<String>, title: String, subtitle: String, primary: String, onPrimary: () -> Unit, modifier: Modifier = Modifier,
    secondary: String? = null, onSecondary: () -> Unit = {}, danger: Boolean = false, hero: (@Composable () -> Unit)? = null, extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val c = RideTheme.colors
    val tone = if (danger) c.danger else c.accent
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(150.dp).entrance(0), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(c.ring1, 74.dp.toPx(), style = Stroke(1.dp.toPx())); drawCircle(c.ring2, 57.dp.toPx(), style = Stroke(1.dp.toPx()))
                }
                Box(Modifier.size(80.dp).clip(CircleShape).background(tone.copy(alpha = 0.14f)).border(1.5.dp, tone, CircleShape), contentAlignment = Alignment.Center) { RideIcon(icon, tone, size = 34.dp) }
            }
            Text(title, color = c.fg, fontSize = 28.sp, fontWeight = FontWeight.Light, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 24.dp).semantics { heading() }.entrance(80))
            Text(subtitle, color = c.muted, fontSize = 14.sp, lineHeight = 21.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp).widthIn(max = 300.dp))
            if (extra != null) Column(Modifier.padding(top = 22.dp).entrance(160), content = extra)
        }
        RideButton(primary, onPrimary)
        if (secondary != null) RideButton(secondary, onSecondary, style = RideButtonStyle.Text, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(14.dp))
    }
}

/** Shown over the whole app while the phone is offline. */
@Composable
fun NoInternetScreen(onRetry: () -> Unit, onOpenRides: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    StateLayout(RideIcons.WifiOff, "You are offline.", "Check your connection. Booking needs the internet.", "Try again", onRetry, modifier, extra = {
        SectionLabel("AVAILABLE OFFLINE", Modifier.align(Alignment.Start))
        GroupCard { NavRow(RideIcons.Clock, "Your Rides", onOpenRides, subtitle = "Past trips and receipts") }
    })
}

/** Generic failure screen with a copyable reference code. */
@Composable
fun ServerErrorScreen(reference: String, onRetry: () -> Unit, onContact: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    StateLayout(RideIcons.Alert, "We hit a bump.", "Something went wrong on our side. Your account and rides are safe.", "Try again", onRetry, modifier, "Contact support", onContact, danger = true, extra = {
        Row(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("REFERENCE", color = c.muted, fontSize = 11.sp, letterSpacing = 1.1.sp); Text(reference, color = c.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp) }
            Box(Modifier.heightIn(min = 48.dp).clickable(role = Role.Button) { clipboard.setText(AnnotatedString(reference)); copied = true }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                Text(if (copied) "Copied" else "Copy", color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    })
}

/** Signed out for inactivity: shows the account and asks for a new code. */
@Composable
fun SessionExpiredScreen(name: String, phone: String, onSendCode: () -> Unit, onOther: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    StateLayout(RideIcons.Lock, "Please sign in again.", "For your security, we ended your session. We will send a code to confirm it is you.", "Send me a code", onSendCode, modifier, "Use a different number", onOther, extra = {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(24.dp)).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(72.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.14f)).border(1.5.dp, c.accent, CircleShape), contentAlignment = Alignment.Center) { Text(initialsOf(name), color = c.accent, fontSize = 24.sp, fontWeight = FontWeight.SemiBold) }
            Text(name, color = c.fg, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
            Text(phone, color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
            Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) { RideIcon(RideIcons.Clock, c.muted, size = 16.dp); Text("  Signed out after inactivity", color = c.muted, fontSize = 13.sp) }
        }
    })
}

/** Shimmering skeleton of the Home screen. Calls [onDone] after about a second (stand-in for real loading). */
@Composable
fun LoadingScreen(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val reduce = rememberReducedMotion()
    LaunchedEffect(Unit) { delay(1100); onDone() }
    val x by rememberInfiniteTransition(label = "shimmer").animateFloat(-300f, 900f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "x")
    val brush = Brush.linearGradient(listOf(c.line, c.border, c.line), Offset(if (reduce) 0f else x, 0f), Offset((if (reduce) 0f else x) + 300f, 0f))
    @Composable fun Bone(w: Dp?, h: Dp, r: Dp = 8.dp, mod: Modifier = Modifier) = Box((if (w != null) mod.width(w) else mod.fillMaxWidth()).height(h).clip(RoundedCornerShape(r)).background(brush))
    Box(modifier.fillMaxSize().background(c.bg).semantics { contentDescription = "Loading" }) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) { Bone(48.dp, 48.dp, 24.dp); Bone(48.dp, 48.dp, 24.dp) }
        Box(Modifier.align(Alignment.Center).clip(RoundedCornerShape(22.dp)).background(c.bg).border(1.dp, c.border, RoundedCornerShape(22.dp)).padding(horizontal = 18.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(16.dp), color = c.accent, strokeWidth = 2.dp); Text("  Finding vehicles near you", color = c.fg, fontSize = 13.sp) }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(c.bg).border(1.dp, c.line, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).navigationBarsPadding().padding(24.dp)) {
            Bone(40.dp, 4.dp, 2.dp, Modifier.align(Alignment.CenterHorizontally))
            Bone(150.dp, 28.dp, mod = Modifier.padding(top = 20.dp)); Bone(210.dp, 14.dp, 6.dp, Modifier.padding(top = 12.dp))
            Bone(null, 60.dp, 16.dp, Modifier.padding(top = 16.dp))
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { Bone(null, 52.dp, 16.dp, Modifier.weight(1f)); Bone(null, 52.dp, 16.dp, Modifier.weight(1f)) }
            repeat(2) { Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) { Bone(40.dp, 40.dp, 20.dp); Column(Modifier.padding(start = 14.dp)) { Bone(160.dp, 14.dp, 6.dp); Bone(100.dp, 12.dp, 6.dp, Modifier.padding(top = 8.dp)) } } }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun OfflinePreview() = RideNowTheme(darkTheme = true) { NoInternetScreen({}, {}) }
