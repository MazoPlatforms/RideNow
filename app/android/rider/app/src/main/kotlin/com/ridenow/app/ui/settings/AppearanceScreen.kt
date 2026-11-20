package com.ridenow.app.ui.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.ridenow.app.R
import com.ridenow.app.ui.components.*
import com.ridenow.app.ui.more.MoreScaffold
import com.ridenow.app.ui.theme.*
import com.ridenow.app.ui.util.AppIconChoice
import com.ridenow.app.ui.util.currentAppIcon
import com.ridenow.app.ui.util.setAppIcon
import kotlinx.coroutines.launch

@Composable
private fun Heading(title: String, sub: String) {
    val c = RideTheme.colors
    Column(Modifier.padding(top = 20.dp, bottom = 10.dp)) {
        Text(title, color = c.fg, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        if (sub.isNotEmpty()) Text(sub, color = c.muted, fontSize = 13.sp)
    }
}

@Composable
private fun SelectCard(selected: Boolean, label: String, onClick: () -> Unit, modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = RideTheme.colors
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.clip(shape).background(c.field).border(if (selected) 2.dp else 1.dp, if (selected) c.accent else c.border, shape).selectable(selected, role = Role.RadioButton, onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(92.dp), content = content)
        Row(Modifier.padding(top = 8.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = if (selected) c.accent else c.fg, fontSize = 13.sp, lineHeight = 16.sp, textAlign = TextAlign.Center, maxLines = 2, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
        }
    }
}

@Composable
private fun CheckBadge(modifier: Modifier = Modifier, size: Dp = 22.dp) {
    val c = RideTheme.colors
    Box(modifier.size(size).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) { RideIcon(RideIcons.Check, c.onAccent, size = (size.value * 0.7f).dp) }
}

@Composable
internal fun RadioDot(selected: Boolean) {
    val c = RideTheme.colors
    Box(Modifier.size(22.dp).clip(CircleShape).border(2.dp, if (selected) c.accent else c.border, CircleShape), contentAlignment = Alignment.Center) {
        if (selected) Box(Modifier.size(11.dp).clip(CircleShape).background(c.accent))
    }
}

/** A settings row: icon, title, current value and a chevron. */
@Composable
private fun ValueRow(icon: List<String>, title: String, value: String, onClick: () -> Unit) = NavRow(
    icon, title, onClick,
    trailing = {
        Text(value, color = RideTheme.colors.muted, fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.widthIn(max = 130.dp).padding(end = 6.dp), maxLines = 2)
        RideIcon(listOf("M7 4l6 6-6 6"), RideTheme.colors.muted, size = 18.dp)
    },
)

@Composable
private fun SwitchRow(icon: List<String>, title: String, on: Boolean, onChange: (Boolean) -> Unit) =
    NavRow(icon, title, { onChange(!on) }, trailing = { RideSwitch(on, title, onChange) })

/** Bottom sheet in the app style. [content] gets a function that slides the sheet away. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppearanceSheet(title: String, subtitle: String, onDismiss: () -> Unit, content: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    val c = RideTheme.colors
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close = { scope.launch { state.hide() }.invokeOnCompletion { onDismiss() }; Unit }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = c.bg, contentColor = c.fg, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Text(title, color = c.fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = c.muted, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
            content(close)
        }
    }
}

// ---------------------------------------------------------------- Previews

private class MapPalette(val land: Color, val road: Color, val major: Color, val water: Color, val park: Color, val build: Color)

private fun palette(style: MapStyle) = when (style) {
    MapStyle.Default -> MapPalette(Color(0xFFE9EEF2), Color.White, Color(0xFFF6D56B), Color(0xFFB9D8F0), Color(0xFFCDE6C6), Color(0xFFDCE3E9))
    MapStyle.Dark -> MapPalette(Color(0xFF131921), Color(0xFF212A36), Color(0xFF3A4658), Color(0xFF0E2A44), Color(0xFF14301F), Color(0xFF18202A))
    MapStyle.Satellite -> MapPalette(Color(0xFF2E3B2C), Color(0xFF7D7B6C), Color(0xFFB7B29B), Color(0xFF1D4868), Color(0xFF41603A), Color(0xFF3A4636))
}

/** Draws land, water, parks, blocks and roads in the given map style. */
private fun DrawScope.drawMapBase(style: MapStyle) {
    val p = palette(style)
    drawRect(p.land)
    val w = size.width; val h = size.height
    drawRoundRect(p.water, Offset(w * 0.78f, -h * 0.1f), Size(w * 0.45f, h * 1.2f), CornerRadius(w * 0.2f))
    drawRoundRect(p.park, Offset(w * 0.04f, h * 0.56f), Size(w * 0.26f, h * 0.3f), CornerRadius(18.dp.toPx()))
    drawRoundRect(p.park, Offset(w * 0.52f, h * 0.04f), Size(w * 0.16f, h * 0.18f), CornerRadius(14.dp.toPx()))
    val bw = 46.dp.toPx(); val bh = 34.dp.toPx(); val gap = 11.dp.toPx()
    var y = 4.dp.toPx(); var row = 0
    while (y < h) {
        var x = (if (row % 2 == 0) 6.dp.toPx() else -14.dp.toPx())
        while (x < w * 0.8f) {
            if ((row + (x / bw).toInt()) % 3 != 1) drawRoundRect(p.build, Offset(x, y), Size(bw, bh), CornerRadius(5.dp.toPx()))
            x += bw + gap
        }
        y += bh + gap; row++
    }
    val roadW = 5.dp.toPx()
    var ry = 30.dp.toPx(); while (ry < h) { drawLine(p.road, Offset(0f, ry), Offset(w, ry + 8.dp.toPx()), roadW, StrokeCap.Round); ry += 78.dp.toPx() }
    var rx = 40.dp.toPx(); while (rx < w) { drawLine(p.road, Offset(rx, 0f), Offset(rx + 12.dp.toPx(), h), roadW, StrokeCap.Round); rx += 92.dp.toPx() }
    drawLine(p.major, Offset(-10f, h * 0.9f), Offset(w * 0.62f, -10f), 9.dp.toPx(), StrokeCap.Round)
    drawLine(p.major, Offset(0f, h * 0.5f), Offset(w, h * 0.42f), 8.dp.toPx(), StrokeCap.Round)
}

/** Preview of one map style used by the picker. */
@Composable
private fun MapStyleTile(style: MapStyle) {
    Canvas(Modifier.fillMaxSize()) {
        clipRect { drawMapBase(style) }
        val p = Path().apply { moveTo(size.width * 0.25f, size.height * 0.85f); cubicTo(size.width * 0.35f, size.height * 0.4f, size.width * 0.6f, size.height * 0.7f, size.width * 0.8f, size.height * 0.2f) }
        drawPath(p, Color(0xFF2EC4F1), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(Color.White, 6.dp.toPx(), Offset(size.width * 0.5f, size.height * 0.55f)); drawCircle(Color(0xFF2EC4F1), 3.5.dp.toPx(), Offset(size.width * 0.5f, size.height * 0.55f))
    }
}

/** A phone drawn in light, dark or half and half. */
@Composable
private fun ThemePreview(mode: ThemeMode, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val pw = size.width * 0.52f; val ph = size.height
        val left = (size.width - pw) / 2f
        fun screen(dark: Boolean) {
            val bg = if (dark) Color(0xFF0B0D11) else Color.White
            val line = if (dark) Color(0xFF39414D) else Color(0xFFD6DCE3)
            drawRoundRect(bg, Offset(left + 3.dp.toPx(), 3.dp.toPx()), Size(pw - 6.dp.toPx(), ph - 6.dp.toPx()), CornerRadius(8.dp.toPx()))
            drawRoundRect(line, Offset(left + 8.dp.toPx(), 10.dp.toPx()), Size(pw * 0.4f, 4.dp.toPx()), CornerRadius(2.dp.toPx()))
            drawRoundRect(Color(0xFFFEC604), Offset(left + 8.dp.toPx(), ph * 0.42f), Size(pw - 16.dp.toPx(), 9.dp.toPx()), CornerRadius(4.dp.toPx()))
            for (i in 0..2) drawRoundRect(line, Offset(left + 8.dp.toPx(), ph * 0.58f + i * 9.dp.toPx()), Size(pw - 16.dp.toPx(), 5.dp.toPx()), CornerRadius(2.dp.toPx()))
        }
        drawRoundRect(Color(0xFF0A0C10), Offset(left, 0f), Size(pw, ph), CornerRadius(10.dp.toPx()))
        when (mode) {
            ThemeMode.Light -> screen(false)
            ThemeMode.Dark -> screen(true)
            ThemeMode.System -> {
                screen(false)
                clipRect(left + pw / 2f, 0f, left + pw, ph) { screen(true) }
            }
        }
    }
}

// ---------------------------------------------------------------- Screen

/** Appearance: theme, map style, accent colour, text size and app icon. Every choice applies at once and is remembered. */
@Composable
fun AppearanceScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state = LocalAppearance.current ?: return
    val c = RideTheme.colors
    val context = LocalContext.current
    var textSheet by remember { mutableStateOf(false) }
    var iconSheet by remember { mutableStateOf(false) }
    var icon by remember { mutableStateOf(currentAppIcon(context)) }
    MoreScaffold(stringResource(R.string.settings_appearance), onBack, modifier, subtitle = stringResource(R.string.settings_appearance_sub)) {
        Heading(stringResource(R.string.ap_theme), stringResource(R.string.ap_theme_sub))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ThemeMode.entries.forEach { m ->
                SelectCard(state.mode == m, stringResource(m.labelRes), { state.setMode(m) }, Modifier.weight(1f)) {
                    ThemePreview(m, Modifier.fillMaxSize())
                    if (state.mode == m) CheckBadge(Modifier.align(Alignment.TopEnd), 20.dp)
                }
            }
        }
        Heading(stringResource(R.string.ap_map), stringResource(R.string.ap_map_sub))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MapStyle.entries.forEach { s ->
                SelectCard(state.mapStyle == s, stringResource(s.labelRes), { state.setMapStyle(s) }, Modifier.weight(1f)) {
                    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))) { MapStyleTile(s) }
                    if (state.mapStyle == s) CheckBadge(Modifier.align(Alignment.TopEnd).padding(4.dp), 20.dp)
                }
            }
        }
        Heading(stringResource(R.string.ap_accent), stringResource(R.string.ap_accent_sub))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AccentChoice.entries.forEach { a ->
                val on = state.accent == a
                val label = stringResource(a.labelRes)
                Box(
                    Modifier.size(48.dp).clip(CircleShape).then(if (on) Modifier.border(2.dp, c.fg, CircleShape).padding(4.dp) else Modifier).clip(CircleShape).background(a.fill)
                        .selectable(on, role = Role.RadioButton) { state.setAccent(a) }.semantics { contentDescription = label },
                    contentAlignment = Alignment.Center,
                ) { if (on) RideIcon(RideIcons.Check, a.onFill, size = 22.dp) }
            }
        }
        Heading(stringResource(R.string.ap_display), "")
        GroupCard {
            SwitchRow(RideIcons.TextSize, stringResource(R.string.ap_system_font), state.systemFont) { state.setSystemFont(it) }; RowDivider()
            ValueRow(RideIcons.TextSize, stringResource(R.string.ap_text_size), stringResource(state.textSize.labelRes)) { textSheet = true }
        }
        Heading(stringResource(R.string.ap_icon), "")
        GroupCard(Modifier.padding(bottom = 16.dp)) { ValueRow(RideIcons.Grid, stringResource(R.string.ap_change_icon), stringResource(icon.labelRes)) { iconSheet = true } }
    }
    if (textSheet) AppearanceSheet(stringResource(R.string.ap_text_size), stringResource(R.string.ap_text_size_sub), { textSheet = false }) { close ->
        GroupCard {
            TextSize.entries.forEachIndexed { i, t ->
                if (i > 0) RowDivider()
                val on = !state.systemFont && state.textSize == t
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(on, role = Role.RadioButton) { state.setTextSize(t); state.setSystemFont(false); close() }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(t.labelRes), color = c.fg, fontSize = (15 * t.scale).sp, modifier = Modifier.weight(1f)); RadioDot(on)
                }
            }
        }
    }
    if (iconSheet) AppearanceSheet(stringResource(R.string.ap_change_icon), stringResource(R.string.ap_icon_sub), { iconSheet = false }) { close ->
        GroupCard {
            AppIconChoice.entries.forEachIndexed { i, ch ->
                if (i > 0) RowDivider()
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).selectable(icon == ch, role = Role.RadioButton) { setAppIcon(context, ch); icon = ch; close() }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(colorResource(ch.colorRes)), contentAlignment = Alignment.Center) { LogoMark(30.dp) }
                    Text(stringResource(ch.labelRes), color = c.fg, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(start = 14.dp)); RadioDot(icon == ch)
                }
            }
        }
    }
}
