package com.ridenow.driver.ui.home

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridenow.driver.R
import com.ridenow.driver.ui.components.RideIcon
import com.ridenow.driver.ui.components.RideIcons
import com.ridenow.driver.ui.components.drawCityStreets
import com.ridenow.driver.ui.theme.RideNowTheme
import com.ridenow.driver.ui.theme.RideTheme
import com.ridenow.driver.ui.util.hasBackgroundLocation
import com.ridenow.driver.ui.util.rememberReducedMotion
import kotlinx.coroutines.delay
import java.text.NumberFormat
import kotlinx.coroutines.isActive

/**
 * Home: a map with the driver's position, the earnings pill, and a bottom sheet with the GO / END button.
 * GO stays locked until a bank account is added. Time online is measured from [onlineSince]
 * (an elapsedRealtime value) plus [bankedMillis] from earlier sessions today.
 */
@Composable
fun HomeScreen(
    online: Boolean, bankSaved: Boolean, driverName: String, onlineSince: Long, bankedMillis: Long,
    onToggle: () -> Unit, onAddBank: () -> Unit, onTab: (MainTab) -> Unit = {}, modifier: Modifier = Modifier,
) {
    val c = RideTheme.colors
    val context = LocalContext.current
    val reduceMotion = rememberReducedMotion()
    val locationOk = hasBackgroundLocation(context)

    // Re-read the clock every 15 seconds while online so "Online time" keeps moving.
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(online) {
        while (online && isActive) { now = SystemClock.elapsedRealtime(); delay(15_000) }
        now = SystemClock.elapsedRealtime()
    }
    val totalMinutes = ((bankedMillis + if (online) (now - onlineSince).coerceAtLeast(0) else 0L) / 60_000L).toInt()
    val money = remember { NumberFormat.getCurrencyInstance().format(0) }

    val pulse by rememberInfiniteTransition(label = "home").animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearOutSlowInEasing)), label = "pulse")
    val ping = if (reduceMotion) 0f else pulse

    Box(modifier.fillMaxSize().background(c.bg)) {
        // Map
        Canvas(Modifier.fillMaxSize()) {
            drawCityStreets(c, 0.9f)
            val me = Offset(size.width / 2f, size.height * 0.34f)
            if (online) {
                val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 14f))
                for ((pos, r) in listOf(Offset(size.width * 0.28f, size.height * 0.46f) to 62.dp, Offset(size.width * 0.78f, size.height * 0.22f) to 46.dp)) {
                    drawCircle(c.accent.copy(alpha = 0.12f), r.toPx(), pos)
                    drawCircle(c.accent, r.toPx(), pos, style = Stroke(1.5.dp.toPx(), pathEffect = dash))
                }
                drawCircle(c.accent.copy(alpha = 0.25f * (1f - ping)), (24.dp + 46.dp * ping).toPx(), me)
            }
            drawCircle(c.accent.copy(alpha = 0.2f), 22.dp.toPx(), me)
            rotate(-25f, me) {
                drawRoundRect(c.fg, Offset(me.x - 9.dp.toPx(), me.y - 16.dp.toPx()), Size(18.dp.toPx(), 32.dp.toPx()), CornerRadius(7.dp.toPx()))
                drawRoundRect(c.bg, Offset(me.x - 6.dp.toPx(), me.y - 8.dp.toPx()), Size(12.dp.toPx(), 9.dp.toPx()), CornerRadius(3.dp.toPx()))
            }
        }

        // Top bar and chips
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundButton(contentDescription = stringResource(R.string.nav_account)) {
                    Text(driverName.take(1).uppercase().ifEmpty { "?" }, color = c.accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(22.dp)).padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(money, color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.home_today), color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
                }
                Spacer(Modifier.weight(1f))
                RoundButton(contentDescription = stringResource(R.string.home_notifications)) { RideIcon(RideIcons.Bell, c.fg, size = 20.dp) }
            }
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(
                    stringResource(if (online) R.string.home_status_online else R.string.home_status_offline),
                    if (online) c.accent else c.muted, filled = online,
                )
                val settings = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                }
                Row(
                    Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(c.field).border(1.dp, if (locationOk) c.border else c.warn, RoundedCornerShape(16.dp))
                        .then(if (locationOk) Modifier else Modifier.clickable(role = Role.Button, onClick = settings)).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RideIcon(RideIcons.Pin, if (locationOk) c.accent else c.warn, size = 14.dp)
                    Text(
                        stringResource(if (!locationOk) R.string.home_loc_needed else if (online) R.string.home_loc_on else R.string.home_loc_allowed),
                        color = if (locationOk) c.fg else c.warn, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }

        // Bottom sheet
        val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(c.field, sheetShape).border(1.dp, c.border, sheetShape)) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 56.dp)) {
                Text(
                    stringResource(if (online) R.string.home_online else R.string.home_offline), color = c.fg, fontSize = 22.sp, fontWeight = FontWeight.Light,
                    modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Text(
                    stringResource(if (online) R.string.home_online_sub else if (bankSaved) R.string.home_offline_ready else R.string.home_offline_sub),
                    color = c.muted, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 14.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                if (online) {
                    InfoRow(stringResource(R.string.home_waiting), c.accent, c.border, c.bg)
                } else if (!bankSaved) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.warn.copy(alpha = 0.12f)).border(1.dp, c.warn, RoundedCornerShape(16.dp)).padding(start = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RideIcon(RideIcons.Card, c.warn, size = 18.dp)
                        Text(stringResource(R.string.home_bank_banner), color = c.fg, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(start = 10.dp))
                        Box(Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onAddBank).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.home_add), color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Row(Modifier.padding(top = 14.dp).fillMaxWidth()) {
                    Stat(money, stringResource(R.string.home_earnings), Modifier.weight(1f))
                    Stat("0", stringResource(R.string.home_trips), Modifier.weight(1f))
                    Stat(stringResource(R.string.home_time_fmt, totalMinutes / 60, totalMinutes % 60), stringResource(R.string.home_online_time), Modifier.weight(1f))
                }
                MainNavBar(MainTab.Home, onTab, Modifier.padding(top = 6.dp))
            }
            GoButton(online, bankSaved, onToggle, Modifier.align(Alignment.TopCenter).offset(y = (-44).dp))
        }
    }
}

@Composable
private fun GoButton(online: Boolean, bankSaved: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val c = RideTheme.colors
    val label = stringResource(if (online) R.string.home_end_desc else if (bankSaved) R.string.home_go_desc else R.string.home_go_locked_desc)
    val bg = if (online) c.bg else if (bankSaved) c.fill else c.line
    val border = if (online) c.danger else if (bankSaved) c.fill else c.border
    val fg = if (online) c.danger else if (bankSaved) c.onFill else c.muted
    Column(
        modifier.size(88.dp).clip(CircleShape).background(bg).border(if (online) 2.dp else 1.dp, border, CircleShape)
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        if (!online && !bankSaved) RideIcon(RideIcons.Lock, fg, size = 20.dp)
        Text(stringResource(if (online) R.string.home_end else R.string.home_go), color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RoundButton(contentDescription: String, content: @Composable () -> Unit) {
    val c = RideTheme.colors
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(c.field).border(1.dp, c.border, CircleShape)
            .clickable(role = Role.Button) { }.semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun StatusChip(text: String, color: Color, filled: Boolean) {
    val c = RideTheme.colors
    Row(
        Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(if (filled) color.copy(alpha = 0.14f) else c.field)
            .border(1.dp, if (filled) color else c.border, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(text, color = if (filled) color else c.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun InfoRow(text: String, dot: Color, border: Color, bg: Color) {
    val c = RideTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bg).border(1.dp, border, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (a in listOf(1f, 0.6f, 0.3f)) Box(Modifier.padding(end = 4.dp).size(6.dp).clip(CircleShape).background(dot.copy(alpha = a)))
        Text(text, color = c.fg, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    val c = RideTheme.colors
    Column(modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = c.fg, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = c.muted, fontSize = 11.sp)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun HomeOfflinePreview() = RideNowTheme(darkTheme = true) { HomeScreen(false, false, "Alex", 0L, 0L, {}, {}) }

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun HomeOnlinePreview() = RideNowTheme(darkTheme = false) { HomeScreen(true, true, "Alex", SystemClock.elapsedRealtime(), 0L, {}, {}) }
