package com.ridenow.app.ui.more

import com.ridenow.app.R
import androidx.compose.ui.res.stringResource

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import com.ridenow.app.ui.components.*
import com.ridenow.app.ui.theme.RideTheme
import com.ridenow.app.ui.util.entrance
import com.ridenow.app.ui.util.rememberReducedMotion
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/** Everything the extra screens need from the app shell. */
class MoreCtx(
    val name: String, val phone: String, val dest: String, val pickup: String, val vehicle: Int, val scheduled: String?,
)

/** Navigation callbacks. [back] pops inside the extra screens; [exit] leaves them for the app shell. */
class MoreNav(
    val back: () -> Unit,
    val exit: () -> Unit,
    val open: (String) -> Unit,
    val home: () -> Unit,
    val options: () -> Unit,
    val schedule: () -> Unit,
    val finding: () -> Unit,
    val trip: () -> Unit,
    val complete: () -> Unit,
    val report: () -> Unit,
    val support: () -> Unit,
    val safety: () -> Unit,
    val setPhone: (String) -> Unit,
    val setVehicle: (Int) -> Unit,
    val setScheduled: (String?) -> Unit,
    val confirm: () -> Unit,
    val changeDrop: (String) -> Unit,
    val cancelScheduled: () -> Unit,
    val signedOut: () -> Unit,
)

fun Context.prefs() = getSharedPreferences("ridenow", 0)
fun Context.toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
fun Context.dial(number: String) { runCatching { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }

/** Back button, title, optional subtitle, scrolling body and an optional pinned bottom area. */
@Composable
fun MoreScaffold(
    title: String, onBack: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null,
    bottom: (@Composable ColumnScope.() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit,
) {
    val c = RideTheme.colors
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        ScreenTop(title, onBack, subtitle = subtitle)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).entrance(60), content = content)
        if (bottom != null) Column(Modifier.padding(vertical = 12.dp), content = bottom)
    }
}

/** Closes the nearest [SheetScreen] with an animated exit, then runs the given action. */
val LocalSheetClose = staticCompositionLocalOf<((() -> Unit) -> Unit)?> { null }

/** A bottom sheet over a dimmed screen. Slides up and fades in; tapping outside or Back slides it down first. */
@Composable
fun SheetScreen(onDismiss: () -> Unit, title: String? = null, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val reduce = rememberReducedMotion()
    val progress = remember { Animatable(if (reduce) 1f else 0f) }
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!reduce) progress.animateTo(1f, tween(280, easing = LinearOutSlowInEasing))
    }

    val requestClose: (() -> Unit) -> Unit = { then ->
        if (!closing) {
            closing = true
            scope.launch {
                if (!reduce) progress.animateTo(0f, tween(220, easing = FastOutLinearInEasing))
                then()
            }
        }
    }

    BackHandler { requestClose(onDismiss) }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f * progress.value))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { requestClose(onDismiss) }
        )
        CompositionLocalProvider(LocalSheetClose provides requestClose) {
            SheetCard(
                Modifier.align(Alignment.BottomCenter).graphicsLayer { translationY = (1f - progress.value) * 600.dp.toPx() },
                title, subtitle, content,
            )
        }
    }
}

@Composable
fun SheetCard(modifier: Modifier = Modifier, title: String? = null, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = RideTheme.colors
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(c.field).border(1.dp, c.border, shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .navigationBarsPadding().padding(horizontal = 20.dp).padding(top = 10.dp, bottom = 14.dp),
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(c.border))
        if (title != null) Text(title, color = c.fg, fontSize = 22.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(top = 16.dp).semantics { heading() })
        if (subtitle != null) Text(subtitle, color = c.muted, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp))
        content()
    }
}

@Composable
fun RadioRow(title: String, selected: Boolean, onClick: () -> Unit, sub: String? = null) {
    val c = RideTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected, role = Role.RadioButton, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.fg, fontSize = 16.sp)
            if (sub != null) Text(sub, color = c.muted, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Box(Modifier.size(24.dp).clip(CircleShape).border(2.dp, if (selected) c.accent else c.border, CircleShape), contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(12.dp).clip(CircleShape).background(c.accent))
        }
    }
}

@Composable
fun ToggleRow(title: String, sub: String?, on: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val c = RideTheme.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp).alpha(if (enabled) 1f else 0.4f), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = c.fg, fontSize = 15.sp)
            if (sub != null) Text(sub, color = c.muted, fontSize = 12.sp, lineHeight = 17.sp)
        }
        RideSwitch(on, title) { if (enabled) onChange(it) }
    }
}

@Composable
fun InfoRow(label: String, value: String, strong: Boolean = false, valueColor: Color? = null) {
    val c = RideTheme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = if (strong) c.fg else c.muted, fontSize = 14.sp, fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
        Text(value, color = valueColor ?: c.fg, fontSize = if (strong) 17.sp else 14.sp, fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    Box(
        modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp)).background(if (selected) c.accent else Color.Transparent)
            .border(1.dp, if (selected) c.accent else c.border, RoundedCornerShape(16.dp))
            .selectable(selected, role = Role.RadioButton, onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (selected) c.onAccent else c.fg, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
}

@Composable
fun Avatar(initials: String, size: Dp = 48.dp) {
    val c = RideTheme.colors
    Box(Modifier.size(size).clip(CircleShape).background(c.accent.copy(alpha = 0.14f)).border(1.dp, c.accent, CircleShape), contentAlignment = Alignment.Center) {
        Text(initials, color = c.accent, fontSize = (size.value * 0.32f).sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun IconBadge(icon: List<String>, tint: Color, size: Dp = 80.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(tint.copy(alpha = 0.14f)).border(1.5.dp, tint, CircleShape), contentAlignment = Alignment.Center) { RideIcon(icon, tint, size = size / 2.3f) }
}

/** Big centered icon, title and text, used by confirmation-style screens. */
@Composable
fun Hero(icon: List<String>, title: String, subtitle: String, danger: Boolean = false, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    Column(modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconBadge(icon, if (danger) c.danger else c.accent)
        Text(title, color = c.fg, fontSize = 28.sp, fontWeight = FontWeight.Light, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 22.dp).semantics { heading() })
        Text(subtitle, color = c.muted, fontSize = 14.sp, lineHeight = 21.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp).widthIn(max = 300.dp))
    }
}

/** Pickup and drop-off lines joined by a dotted rail. */
@Composable
fun RouteBlock(pickup: String, drop: String, modifier: Modifier = Modifier, stops: List<String> = emptyList()) {
    val textConfirmPickup = stringResource(R.string.confirm_pickup)
    val textSearchStop = stringResource(R.string.search_stop)
    val textConfirmDrop = stringResource(R.string.confirm_drop)

    val c = RideTheme.colors
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(20.dp)).padding(16.dp)) {
        Text(textConfirmPickup, color = c.muted, fontSize = 11.sp, letterSpacing = 1.1.sp)
        Text(pickup, color = c.fg, fontSize = 15.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
        stops.forEach { s ->
            Text(textSearchStop, color = c.muted, fontSize = 11.sp, letterSpacing = 1.1.sp)
            Text(s, color = c.fg, fontSize = 15.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
        }
        Text(textConfirmDrop, color = c.muted, fontSize = 11.sp, letterSpacing = 1.1.sp)
        Text(drop, color = c.fg, fontSize = 15.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

/** Six digit style code entry drawn as boxes over one hidden text field. */
@Composable
fun CodeField(value: String, onChange: (String) -> Unit, length: Int = 6) {
    val textVerificationCodeValueOfValueDigitsEntered = stringResource(R.string.text_verification_code_value_of_value_digits_entered)

    val c = RideTheme.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BasicTextField(
        value = value, onValueChange = { onChange(it.filter(Char::isDigit).take(length)) },
        modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = textVerificationCodeValueOfValueDigitsEntered.format(value.length, length) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        cursorBrush = SolidColor(Color.Transparent), textStyle = TextStyle(color = Color.Transparent),
        decorationBox = { inner ->
            Box {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(length) { i ->
                        val active = i == value.length
                        Box(
                            Modifier.weight(1f).height(60.dp).clip(RoundedCornerShape(14.dp)).background(c.field).border(1.5.dp, if (active) c.accent else c.border, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center,
                        ) { Text(value.getOrNull(i)?.toString() ?: "", color = c.fg, fontSize = 24.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
                Box(Modifier.size(1.dp).alpha(0f)) { inner() }
            }
        },
    )
}

/** Counts down once per second from [from] to 0 while [running]. */
@Composable
fun rememberCountdown(from: Int, running: Boolean = true): Int {
    var left by remember { mutableIntStateOf(from) }
    LaunchedEffect(running) { while (running && left > 0) { delay(1000); left-- } }
    return left
}

fun mmss(seconds: Int) = "%d:%02d".format(seconds / 60, seconds % 60)

/** Outlined destructive action. */
@Composable
fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = RideTheme.colors
    Box(
        modifier.fillMaxWidth().height(56.dp).alpha(if (enabled) 1f else 0.4f).clip(RoundedCornerShape(28.dp)).background(c.danger.copy(alpha = 0.12f))
            .border(1.5.dp, c.danger, RoundedCornerShape(28.dp)).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = c.danger, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
}
