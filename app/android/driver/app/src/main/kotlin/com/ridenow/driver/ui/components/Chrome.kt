package com.ridenow.driver.ui.components

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridenow.driver.R
import com.ridenow.driver.ui.home.MainBottomBar
import com.ridenow.driver.ui.home.MainTab
import com.ridenow.driver.ui.theme.RideTheme
import com.ridenow.driver.ui.util.entrance
import kotlinx.coroutines.launch

@Composable fun str(@StringRes id: Int): String = stringResource(id)
@Composable fun str(@StringRes id: Int, vararg args: Any): String = stringResource(id, *args)

fun toast(context: android.content.Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

fun initialsOf(name: String): String =
    name.trim().split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

/** Status colours shared by chips, icon tiles and banners. */
enum class Tone { Good, Danger, Info, Warn, Neutral, Accent, Brand }

@Composable
fun toneColor(tone: Tone): Color {
    val c = RideTheme.colors
    return when (tone) { Tone.Good -> c.good; Tone.Danger -> c.danger; Tone.Info -> c.info; Tone.Warn -> c.warn; Tone.Neutral -> c.muted; Tone.Accent -> c.accent; Tone.Brand -> c.fill }
}

/** Round 44 dp button with an icon. Used for back, bell, call, message and similar actions. */
@Composable
fun CircleButton(icon: List<String>, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 44.dp, tint: Color = RideTheme.colors.fg, filled: Boolean = true) {
    val c = RideTheme.colors
    Box(
        modifier.size(size).clip(CircleShape).background(if (filled) c.field else Color.Transparent).then(if (filled) Modifier.border(1.dp, c.border, CircleShape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { RideIcon(icon, tint, size = 20.dp) }
}

/** Initials in a ring. The ring uses the accent colour. */
@Composable
fun Avatar(initials: String, size: Dp, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, description: String? = null) {
    val c = RideTheme.colors
    val base = modifier.size(size).clip(CircleShape).background(c.accent.copy(alpha = 0.14f)).border(1.5.dp, c.accent, CircleShape)
    Box(
        (if (onClick != null) base.clickable(role = Role.Button, onClick = onClick) else base).then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        contentAlignment = Alignment.Center,
    ) { Text(initials, color = c.accent, fontSize = (size.value * 0.36f).sp, fontWeight = FontWeight.SemiBold) }
}

/** Header for the main tabs: logo on the left, bell with unread dot and profile avatar on the right. There is no side drawer. */
@Composable
fun MainHeader(unread: Int, initials: String, onBell: () -> Unit, onProfile: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    Row(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        LogoMark(34.dp)
        Spacer(Modifier.width(8.dp))
        Wordmark(height = 30.dp)
        Spacer(Modifier.weight(1f))
        Box {
            CircleButton(RideIcons.Bell, str(R.string.home_notifications), onBell)
            if (unread > 0) Box(Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 2.dp).size(11.dp).clip(CircleShape).background(c.danger).border(1.5.dp, c.bg, CircleShape))
        }
        Spacer(Modifier.width(10.dp))
        Avatar(initials, 44.dp, onClick = onProfile, description = str(R.string.nav_account))
    }
}

/** Plain back arrow, the same as the Rider app: no frame, nudged to line up with the screen edge. */
@Composable
fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.offset(x = (-12).dp).size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = "Back" },
        contentAlignment = Alignment.Center,
    ) { RideIcon(RideIcons.Back, RideTheme.colors.fg, size = 22.dp) }
}

/** Back arrow on the left, centred 17 sp title, optional trailing action (Rider app top bar). */
@Composable
fun SubTopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    val c = RideTheme.colors
    Row(modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(36.dp)) { BackButton(onBack) }
        Text(
            title, color = c.fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp).semantics { heading() },
        )
        Box(Modifier.width(36.dp), contentAlignment = Alignment.CenterEnd) { trailing?.invoke() }
    }
}

/** Standard page for pushed screens: top bar, scrolling body, optional pinned bottom area. */
@Composable
fun SubScreen(
    title: String, onBack: () -> Unit, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null,
    bottom: (@Composable ColumnScope.() -> Unit)? = null, scroll: Boolean = true, content: @Composable ColumnScope.() -> Unit,
) {
    val c = RideTheme.colors
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        SubTopBar(title, onBack, Modifier.padding(horizontal = 20.dp), trailing)
        val body = Modifier.weight(1f).fillMaxWidth().let { if (scroll) it.verticalScroll(rememberScrollState()) else it }.padding(horizontal = 20.dp).entrance(60)
        Column(body, content = content)
        if (bottom != null) Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), content = bottom)
    }
}

/** Page for the five main tabs: header, scrolling body and the shared bottom bar. */
@Composable
fun TabScaffold(
    tab: MainTab, onTab: (MainTab) -> Unit, unread: Int, initials: String, onBell: () -> Unit, onProfile: () -> Unit,
    modifier: Modifier = Modifier, scroll: Boolean = true, bottom: (@Composable ColumnScope.() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit,
) {
    val c = RideTheme.colors
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        MainHeader(unread, initials, onBell, onProfile)
        val body = Modifier.weight(1f).fillMaxWidth().let { if (scroll) it.verticalScroll(rememberScrollState()) else it }.padding(horizontal = 20.dp)
        Column(body, content = content)
        if (bottom != null) Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), content = bottom)
        MainBottomBar(tab, onTab)
    }
}

@Composable
fun PageTitle(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    val c = RideTheme.colors
    Column(modifier.padding(top = 8.dp)) {
        Text(title, color = c.fg, fontSize = 30.sp, fontWeight = FontWeight.Light, modifier = Modifier.semantics { heading() })
        if (subtitle != null) Text(subtitle, color = c.muted, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Small uppercase section label, as in the Rider app, with an optional action on the right. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: String? = null, actionIcon: List<String>? = null, onAction: (() -> Unit)? = null) {
    val c = RideTheme.colors
    Row(modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), color = c.muted, fontSize = 11.sp, letterSpacing = 1.1.sp, modifier = Modifier.weight(1f).semantics { heading() })
        if (action != null && onAction != null) Row(
            Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onAction).heightIn(min = 36.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (actionIcon != null) { RideIcon(actionIcon, c.accent, size = 14.dp); Spacer(Modifier.width(6.dp)) }
            Text(action, color = c.accent, fontSize = 13.sp)
            if (actionIcon == null) RideIcon(RideIcons.Chevron, c.accent, size = 14.dp)
        }
    }
}

/** Single-choice pills in a scrolling row, like the Rider app: accent fill when selected, outline otherwise. */
@Composable
fun FilterPills(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(if (on) c.accent else Color.Transparent).border(1.dp, if (on) c.accent else c.border, RoundedCornerShape(20.dp))
                    .selectable(on, role = Role.Tab) { onSelect(i) }.padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, color = if (on) c.onAccent else c.fg, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, softWrap = false) }
        }
    }
}

/** Rounded search box. The clear button appears when there is text. */
@Composable
fun SearchField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    Row(
        modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(26.dp)).padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RideIcon(RideIcons.Search, c.fg, size = 20.dp)
        Box(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            if (value.isEmpty()) Text(hint, color = c.muted, fontSize = 15.sp, maxLines = 1)
            BasicTextField(
                value, onChange, singleLine = true, textStyle = TextStyle(color = c.fg, fontSize = 15.sp), cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search), modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) Box(Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button) { onChange("") }.semantics { contentDescription = "Clear" }, contentAlignment = Alignment.Center) {
            RideIcon(RideIcons.Close, c.muted, size = 16.dp)
        }
    }
}

@Composable
fun StatusChip(text: String, tone: Tone, modifier: Modifier = Modifier, dot: Boolean = false) {
    val color = toneColor(tone)
    Row(
        modifier.clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) { Box(Modifier.size(7.dp).clip(CircleShape).background(color)); Spacer(Modifier.width(6.dp)) }
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Green pickup dot, line and red drop dot, drawn to match a two-line address block of [height]. */
@Composable
fun RouteDots(height: Dp, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    Canvas(modifier.width(14.dp).height(height)) {
        val x = size.width / 2f
        val r = 4.5.dp.toPx()
        drawLine(c.fill.copy(alpha = 0.9f), Offset(x, r * 2), Offset(x, size.height - r * 2), 2.dp.toPx())
        drawCircle(c.good, r, Offset(x, r))
        drawCircle(c.danger, r, Offset(x, size.height - r))
    }
}

/** Round outlined icon, tinted by [tone] (accent for Neutral), like the notification icons in the Rider app. */
@Composable
fun IconTile(icon: List<String>, tone: Tone, modifier: Modifier = Modifier, size: Dp = 40.dp, solid: Boolean = false) {
    val c = RideTheme.colors
    val color = if (tone == Tone.Neutral) c.accent else toneColor(tone)
    Box(modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.14f)).border(1.dp, c.border, CircleShape), contentAlignment = Alignment.Center) {
        RideIcon(icon, color, size = (size.value * 0.5f).dp)
    }
}

/** Tinted card with an icon, bold title and a line of text. Used for the notes at the top of settings screens. */
@Composable
fun InfoBanner(icon: List<String>, title: String, text: String, modifier: Modifier = Modifier, tone: Tone = Tone.Accent) {
    val color = toneColor(tone)
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(color.copy(alpha = 0.12f)).border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(18.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) { RideIcon(icon, color, size = 22.dp) }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, color = RideTheme.colors.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(text, color = RideTheme.colors.muted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** Icon, label on the left and value on the right. Used for profile, vehicle and bank details. */
@Composable
fun DetailRow(icon: List<String>, label: String, value: String, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    Row(modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RideIcon(icon, c.accent, size = 20.dp)
        Text(label, color = c.fg, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(start = 14.dp))
        Text(value, color = c.muted, fontSize = 14.sp, textAlign = TextAlign.End, maxLines = 2, modifier = Modifier.weight(1.1f))
    }
}

/** Title with optional text, a switch and an optional chevron that opens more options. */
@Composable
fun ToggleRow(icon: List<String>, title: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, subtitle: String? = null, onMore: (() -> Unit)? = null) {
    val c = RideTheme.colors
    Row(modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RideIcon(icon, c.accent, size = 20.dp)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, color = c.fg, fontSize = 15.sp)
            if (subtitle != null) Text(subtitle, color = c.muted, fontSize = 12.sp, lineHeight = 17.sp)
        }
        RideSwitch(checked, title, onChange)
        if (onMore != null) Box(Modifier.size(36.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onMore).semantics { contentDescription = title }, contentAlignment = Alignment.Center) { RideIcon(RideIcons.Chevron, c.muted, size = 18.dp) }
    }
}

/** Same as the Rider app list row: accent icon, title, subtitle and chevron. [tone] is kept for callers and ignored. */
@Composable
@Suppress("UNUSED_PARAMETER")
fun TileRow(icon: List<String>, tone: Tone, title: String, subtitle: String?, onClick: () -> Unit, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) =
    NavRow(icon, title, onClick, modifier, subtitle = subtitle, trailing = trailing)

@Composable
fun EmptyState(icon: List<String>, title: String, text: String, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    Column(modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(c.field).border(1.dp, c.border, CircleShape), contentAlignment = Alignment.Center) { RideIcon(icon, c.muted, size = 28.dp) }
        Text(title, color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp), textAlign = TextAlign.Center)
        Text(text, color = c.muted, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 4.dp), textAlign = TextAlign.Center)
    }
}

/** Soft grey button used for secondary actions such as Edit details and Share. */
@Composable
fun SoftButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: List<String>? = null, danger: Boolean = false) {
    val c = RideTheme.colors
    val color = if (danger) c.danger else c.fg
    Row(
        modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(if (danger) c.danger.copy(alpha = 0.14f) else c.field)
            .border(1.dp, if (danger) c.danger.copy(alpha = 0.3f) else c.border, RoundedCornerShape(26.dp)).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) { RideIcon(icon, color, size = 18.dp); Spacer(Modifier.width(10.dp)) }
        Text(text, color = color, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Bottom sheet in the app style. [content] gets the sheet column. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideSheet(title: String, onDismiss: () -> Unit, subtitle: String? = null, content: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    val c = RideTheme.colors
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close = { scope.launch { state.hide() }.invokeOnCompletion { onDismiss() }; Unit }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = c.bg, contentColor = c.fg, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp).verticalScroll(rememberScrollState())) {
            Text(title, color = c.fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, color = c.muted, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(12.dp))
            content(close)
        }
    }
}

/** Radio dot used inside lists. */
@Composable
fun RadioDot(selected: Boolean, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    Box(modifier.size(22.dp).clip(CircleShape).border(2.dp, if (selected) c.fill else c.border, CircleShape), contentAlignment = Alignment.Center) {
        if (selected) Box(Modifier.size(11.dp).clip(CircleShape).background(c.fill))
    }
}

/** Small round check used on selected cards. */
@Composable
fun CheckBadge(modifier: Modifier = Modifier, size: Dp = 22.dp) {
    val c = RideTheme.colors
    Box(modifier.size(size).clip(CircleShape).background(c.fill), contentAlignment = Alignment.Center) { RideIcon(RideIcons.Check, c.onFill, size = (size.value * 0.7f).dp) }
}

/** Thin divider inside cards that matches [RowDivider] but indented past the icon. */
@Composable
fun InsetDivider(start: Dp = 58.dp) = HorizontalDivider(Modifier.padding(start = start), color = RideTheme.colors.line)
