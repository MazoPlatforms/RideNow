package com.ridenow.app.ui.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ridenow.app.R
import com.ridenow.app.ui.components.RideButton
import com.ridenow.app.ui.components.RideButtonStyle
import com.ridenow.app.ui.components.RideIcon
import com.ridenow.app.ui.components.RideIcons
import com.ridenow.app.ui.components.Wordmark
import com.ridenow.app.ui.theme.RideNowTheme
import com.ridenow.app.ui.theme.RideTheme
import com.ridenow.app.ui.util.entrance
import com.ridenow.app.ui.util.rememberReducedMotion
import kotlin.math.cos
import kotlin.math.sin

private fun Context.hasLocation() =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun Context.hasNotifications() =
    Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun Context.openAppSettings() = startActivity(
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
)

/** Calls [action] whenever the user returns to the app (for example from Settings). */
@Composable
private fun OnResume(action: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) action() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

/** Asks for location. Calls [onDone] when granted, or when the rider chooses to skip. */
@Composable
fun LocationPermissionScreen(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var denied by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) onDone() else denied = true
    }
    LaunchedEffect(Unit) { if (context.hasLocation()) onDone() }
    OnResume { if (denied && context.hasLocation()) onDone() }

    if (!denied) PermissionLayout(
        icon = RideIcons.Pin, title = R.string.loc_title, subtitle = R.string.loc_subtitle,
        benefits = listOf(RideIcons.Pin to R.string.loc_b1, RideIcons.Car to R.string.loc_b2, RideIcons.Flag to R.string.loc_b3),
        primary = R.string.loc_allow, secondary = R.string.perm_not_now, note = R.string.loc_note,
        onPrimary = { launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
        onSecondary = onDone, modifier = modifier,
    ) else PermissionLayout(
        icon = RideIcons.Pin, title = R.string.loc_off_title, subtitle = R.string.loc_off_subtitle, denied = true,
        steps = listOf(R.string.loc_step1, R.string.loc_step2, R.string.loc_step3),
        primary = R.string.perm_open_settings, secondary = R.string.loc_off_manual,
        onPrimary = { context.openAppSettings() }, onSecondary = onDone, modifier = modifier,
    )
}

/** Asks for notifications (Android 13+; older versions need no permission). */
@Composable
fun NotificationPermissionScreen(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var denied by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) onDone() else denied = true }
    LaunchedEffect(Unit) { if (context.hasNotifications()) onDone() }
    OnResume { if (denied && context.hasNotifications()) onDone() }

    if (!denied) PermissionLayout(
        icon = RideIcons.Bell, title = R.string.ntf_title, subtitle = R.string.ntf_subtitle,
        benefits = listOf(RideIcons.Clock to R.string.ntf_b1, RideIcons.Receipt to R.string.ntf_b2, RideIcons.Shield to R.string.ntf_b3),
        primary = R.string.ntf_allow, secondary = R.string.perm_not_now, note = R.string.ntf_note,
        onPrimary = { if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else onDone() },
        onSecondary = onDone, modifier = modifier,
    ) else PermissionLayout(
        icon = RideIcons.Bell, title = R.string.ntf_off_title, subtitle = R.string.ntf_off_subtitle, denied = true,
        steps = listOf(R.string.loc_step1, R.string.ntf_step2, R.string.ntf_step3),
        primary = R.string.perm_open_settings, secondary = R.string.perm_not_now,
        onPrimary = { context.openAppSettings() }, onSecondary = onDone, modifier = modifier,
    )
}

@Composable
private fun PermissionLayout(
    icon: List<String>, title: Int, subtitle: Int, primary: Int, secondary: Int,
    onPrimary: () -> Unit, onSecondary: () -> Unit, modifier: Modifier = Modifier,
    denied: Boolean = false, benefits: List<Pair<List<String>, Int>> = emptyList(), steps: List<Int> = emptyList(), note: Int? = null,
) {
    val c = RideTheme.colors
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
        Box(Modifier.height(48.dp).entrance(0), contentAlignment = Alignment.CenterStart) { Wordmark(24.dp) }
        PermissionHero(icon, denied, Modifier.weight(1f).fillMaxWidth().entrance(100))
        Column(Modifier.entrance(200)) {
            Text(stringResource(title), color = c.fg, fontSize = 30.sp, fontWeight = FontWeight.Light, lineHeight = 36.sp, modifier = Modifier.semantics { heading() })
            Text(stringResource(subtitle), color = c.muted, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 10.dp))
        }
        Column(
            Modifier.padding(top = 18.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.field)
                .border(1.dp, c.border, RoundedCornerShape(20.dp)).entrance(300),
        ) {
            benefits.forEachIndexed { i, (ic, text) ->
                if (i > 0) HorizontalDivider(color = c.line)
                Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RideIcon(ic, c.accent); Spacer(Modifier.width(14.dp)); Text(stringResource(text), color = c.fg, fontSize = 14.sp)
                }
            }
            steps.forEachIndexed { i, text ->
                if (i > 0) HorizontalDivider(color = c.line)
                Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(26.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.14f)).border(1.dp, c.accent, CircleShape), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.width(14.dp)); Text(stringResource(text), color = c.fg, fontSize = 14.sp)
                }
            }
        }
        Column(Modifier.padding(top = 20.dp).entrance(400), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            RideButton(stringResource(primary), onPrimary)
            RideButton(stringResource(secondary), onSecondary, style = RideButtonStyle.Text)
            if (note != null) Text(stringResource(note), color = c.muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp))
            else Spacer(Modifier.height(10.dp))
        }
    }
}

/** Rings, a pulse and an orbiting dot around a round icon. Denied state drops the pulse and crosses the icon out. */
@Composable
private fun PermissionHero(icon: List<String>, denied: Boolean, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val reduce = rememberReducedMotion()
    val loop = rememberInfiniteTransition(label = "perm")
    val orbitRaw by loop.animateFloat(0f, 360f, infiniteRepeatable(tween(12000, easing = LinearEasing)), label = "orbit")
    val pulseRaw by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearOutSlowInEasing)), label = "pulse")
    val orbit = if (reduce) 40f else orbitRaw
    val pulse = if (reduce) 0f else pulseRaw
    val tone = if (denied) c.danger else c.accent
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = minOf(236.dp, maxHeight, maxWidth)
        Box(Modifier.size(side).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val u = size.minDimension / 236f
                val center = Offset(size.width / 2, size.height / 2)
                drawCircle(Brush.radialGradient(listOf(tone.copy(alpha = 0.14f), Color.Transparent), center, 130f * u), 130f * u, center)
                drawCircle(c.ring1, 117f * u, center, style = Stroke(1.dp.toPx()))
                drawCircle(c.ring2, 87f * u, center, style = Stroke(1.dp.toPx()))
                if (!denied) drawCircle(c.accent.copy(alpha = (1f - pulse) * 0.35f), 40f * u + pulse * 70f * u, center, style = Stroke(2.dp.toPx()))
                val a = Math.toRadians(orbit.toDouble() - 90.0)
                val dot = Offset(center.x + cos(a).toFloat() * 117f * u, center.y + sin(a).toFloat() * 117f * u)
                drawCircle(tone.copy(alpha = 0.3f), 10.dp.toPx(), dot); drawCircle(tone, 4.dp.toPx(), dot)
            }
            Box(
                Modifier.size(side * 0.34f).clip(CircleShape).background(tone.copy(alpha = 0.14f)).border(1.5.dp, tone, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                RideIcon(icon, tone, size = side * 0.15f)
                if (denied) Canvas(Modifier.fillMaxSize()) {
                    drawLine(c.danger, Offset(size.width * 0.22f, size.height * 0.78f), Offset(size.width * 0.78f, size.height * 0.22f), 3.dp.toPx(), StrokeCap.Round)
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun LocationPreview() = RideNowTheme(darkTheme = true) { LocationPermissionScreen({}) }

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun NotificationPreviewLight() = RideNowTheme(darkTheme = false) { NotificationPermissionScreen({}) }
