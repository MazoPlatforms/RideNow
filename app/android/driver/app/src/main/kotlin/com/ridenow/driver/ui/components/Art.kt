package com.ridenow.driver.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridenow.driver.ui.data.DocType
import com.ridenow.driver.ui.theme.LocalMapStyle
import com.ridenow.driver.ui.theme.MapStyle
import com.ridenow.driver.ui.theme.RideTheme
import com.ridenow.driver.ui.theme.ThemeMode
import java.text.NumberFormat
import java.util.Locale

// ---------------------------------------------------------------- Maps

private class MapPalette(val land: Color, val road: Color, val major: Color, val water: Color, val park: Color, val label: Color, val build: Color)

private fun palette(style: MapStyle) = when (style) {
    MapStyle.Default -> MapPalette(Color(0xFFE9EEF2), Color.White, Color(0xFFF6D56B), Color(0xFFB9D8F0), Color(0xFFCDE6C6), Color(0xFF3B4756), Color(0xFFDCE3E9))
    MapStyle.Dark -> MapPalette(Color(0xFF131921), Color(0xFF212A36), Color(0xFF3A4658), Color(0xFF0E2A44), Color(0xFF14301F), Color(0xFFD7DEE6), Color(0xFF18202A))
    MapStyle.Satellite -> MapPalette(Color(0xFF2E3B2C), Color(0xFF7D7B6C), Color(0xFFB7B29B), Color(0xFF1D4868), Color(0xFF41603A), Color(0xFFF2F4F7), Color(0xFF3A4636))
}

/** Text colour that reads on the chosen map style. */
@Composable
fun mapLabelColor(): Color = palette(LocalMapStyle.current).label

/** Draws land, water, parks, blocks and roads. The same picture is used for every map in the app, in the style the driver chose. */
fun DrawScope.drawMapBase(style: MapStyle) {
    val p = palette(style)
    drawRect(p.land)
    val w = size.width; val h = size.height
    // Water along the right edge and a park on the left.
    drawRoundRect(p.water, Offset(w * 0.78f, -h * 0.1f), Size(w * 0.45f, h * 1.2f), CornerRadius(w * 0.2f))
    drawRoundRect(p.park, Offset(w * 0.04f, h * 0.56f), Size(w * 0.26f, h * 0.3f), CornerRadius(18.dp.toPx()))
    drawRoundRect(p.park, Offset(w * 0.52f, h * 0.04f), Size(w * 0.16f, h * 0.18f), CornerRadius(14.dp.toPx()))
    // City blocks.
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
    // Roads.
    val roadW = 5.dp.toPx()
    var ry = 30.dp.toPx(); while (ry < h) { drawLine(p.road, Offset(0f, ry), Offset(w, ry + 8.dp.toPx()), roadW, StrokeCap.Round); ry += 78.dp.toPx() }
    var rx = 40.dp.toPx(); while (rx < w) { drawLine(p.road, Offset(rx, 0f), Offset(rx + 12.dp.toPx(), h), roadW, StrokeCap.Round); rx += 92.dp.toPx() }
    drawLine(p.major, Offset(-10f, h * 0.9f), Offset(w * 0.62f, -10f), 9.dp.toPx(), StrokeCap.Round)
    drawLine(p.major, Offset(0f, h * 0.5f), Offset(w, h * 0.42f), 8.dp.toPx(), StrokeCap.Round)
}

private fun pinPath(cx: Float, tipY: Float, r: Float): Path = Path().apply {
    moveTo(cx, tipY)
    cubicTo(cx - r * 1.1f, tipY - r * 1.5f, cx - r, tipY - r * 3f, cx, tipY - r * 3f)
    cubicTo(cx + r, tipY - r * 3f, cx + r * 1.1f, tipY - r * 1.5f, cx, tipY)
    close()
}

/**
 * A route between two places drawn on the map. [pickup] and [drop] are the place names shown beside the pins.
 * [corner] is placed in the top-right (for the open-in-maps button).
 */
@Composable
fun RouteMap(
    pickup: String, drop: String, modifier: Modifier = Modifier, areaLabel: String? = null, showCar: Boolean = true,
    corner: (@Composable BoxScope.() -> Unit)? = null,
) {
    val c = RideTheme.colors
    val style = LocalMapStyle.current
    val pal = palette(style)
    BoxWithConstraints(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))) {
        val w = maxWidth; val h = maxHeight
        Canvas(Modifier.fillMaxSize()) {
            drawMapBase(style)
            val a = Offset(size.width * 0.30f, size.height * 0.26f)
            val b = Offset(size.width * 0.66f, size.height * 0.74f)
            val path = Path().apply {
                moveTo(a.x, a.y)
                cubicTo(size.width * 0.30f, size.height * 0.62f, size.width * 0.58f, size.height * 0.38f, b.x, b.y)
            }
            drawPath(path, Color.Black.copy(alpha = 0.18f), style = Stroke(9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, c.fill, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            val r = 7.dp.toPx()
            drawPath(pinPath(a.x, a.y + r * 1.3f, r), Color(0xFF22B35A)); drawCircle(Color.White, r * 0.45f, Offset(a.x, a.y + r * 1.3f - r * 2.1f))
            drawPath(pinPath(b.x, b.y + r * 1.3f, r), Color(0xFFE5393B)); drawCircle(Color.White, r * 0.45f, Offset(b.x, b.y + r * 1.3f - r * 2.1f))
            if (showCar) {
                val m = PathMeasure().apply { setPath(path, false) }
                val mid = m.getPosition(m.length * 0.5f)
                val t = m.getTangent(m.length * 0.5f)
                val deg = Math.toDegrees(Math.atan2(t.y.toDouble(), t.x.toDouble())).toFloat() + 90f
                rotate(deg, mid) {
                    drawRoundRect(Color(0xFF0A0C10), Offset(mid.x - 7.dp.toPx(), mid.y - 12.dp.toPx()), Size(14.dp.toPx(), 24.dp.toPx()), CornerRadius(5.dp.toPx()))
                    drawRoundRect(Color(0xFFBFD6E8), Offset(mid.x - 5.dp.toPx(), mid.y - 6.dp.toPx()), Size(10.dp.toPx(), 7.dp.toPx()), CornerRadius(2.dp.toPx()))
                }
            }
        }
        Text(pickup, color = pal.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.offset(x = w * 0.30f + 14.dp, y = h * 0.26f - 14.dp))
        Text(drop, color = pal.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.offset(x = w * 0.66f + 14.dp, y = h * 0.74f - 14.dp))
        if (areaLabel != null) Text(areaLabel, color = pal.label.copy(alpha = 0.8f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.offset(x = w * 0.08f, y = h * 0.4f))
        if (corner != null) Box(Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.TopEnd) { corner() }
    }
}

/** Preview of one map style used by the Appearance picker. */
@Composable
fun MapStyleTile(style: MapStyle, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        clipRect { drawMapBase(style) }
        val p = Path().apply { moveTo(size.width * 0.25f, size.height * 0.85f); cubicTo(size.width * 0.35f, size.height * 0.4f, size.width * 0.6f, size.height * 0.7f, size.width * 0.8f, size.height * 0.2f) }
        drawPath(p, Color(0xFF2EC4F1), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(Color.White, 6.dp.toPx(), Offset(size.width * 0.5f, size.height * 0.55f)); drawCircle(Color(0xFF2EC4F1), 3.5.dp.toPx(), Offset(size.width * 0.5f, size.height * 0.55f))
    }
}

// ---------------------------------------------------------------- Vehicle and documents

/** A green three-wheeler, drawn in vectors. */
@Composable
fun TukTukArt(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = minOf(size.width / 160f, size.height / 110f)
        val ox = (size.width - 160f * s) / 2f; val oy = (size.height - 110f * s) / 2f
        fun o(x: Float, y: Float) = Offset(ox + x * s, oy + y * s)
        fun sz(w: Float, h: Float) = Size(w * s, h * s)
        val green = Color(0xFF1F8A43); val dark = Color(0xFF14171C)
        // shadow
        drawOval(Color.Black.copy(alpha = 0.18f), o(18f, 94f), sz(126f, 12f))
        // canopy
        drawRoundRect(dark, o(26f, 12f), sz(86f, 22f), CornerRadius(10f * s))
        // posts + interior
        drawRoundRect(Color(0xFF2B3138), o(32f, 30f), sz(70f, 48f), CornerRadius(4f * s))
        // body
        drawRoundRect(green, o(22f, 56f), sz(110f, 32f), CornerRadius(8f * s))
        drawRoundRect(Color(0xFFF2C230), o(22f, 70f), sz(110f, 3f), CornerRadius(1.5f * s))
        // windshield + front
        drawRoundRect(Color(0xFFBFD8EA), o(104f, 26f), sz(30f, 34f), CornerRadius(5f * s))
        drawRoundRect(green, o(112f, 56f), sz(28f, 30f), CornerRadius(8f * s))
        drawCircle(Color(0xFFFFF3B0), 4f * s, o(138f, 70f))
        // wheels
        for (wx in listOf(40f, 96f, 130f)) { drawCircle(dark, 12f * s, o(wx, 90f)); drawCircle(Color(0xFF9AA3AE), 4.5f * s, o(wx, 90f)) }
    }
}

/** Small stand-in picture for a document or the vehicle photos. */
@Composable
fun DocThumb(type: DocType, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFFF4F6F8)).border(1.dp, c.border, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
        if (type == DocType.Photos) TukTukArt(Modifier.fillMaxSize().padding(2.dp))
        else Canvas(Modifier.fillMaxSize().padding(6.dp)) {
            val ink = Color(0xFF9AA6B2)
            if (type == DocType.License) {
                drawRoundRect(Color(0xFFE7C9CF), Offset.Zero, Size(size.width, size.height * 0.2f), CornerRadius(3.dp.toPx()))
                drawRoundRect(Color(0xFFC3CCD6), Offset(0f, size.height * 0.3f), Size(size.width * 0.32f, size.height * 0.55f), CornerRadius(3.dp.toPx()))
                drawCircle(Color(0xFF5B6673), size.height * 0.09f, Offset(size.width * 0.16f, size.height * 0.48f))
                for (i in 0..2) drawLine(ink, Offset(size.width * 0.42f, size.height * (0.4f + i * 0.17f)), Offset(size.width * 0.95f, size.height * (0.4f + i * 0.17f)), 2.dp.toPx())
            } else {
                drawCircle(Color(0xFFB9C4D0), size.height * 0.08f, Offset(size.width / 2f, size.height * 0.12f))
                for (i in 0..5) drawLine(ink, Offset(size.width * 0.12f, size.height * (0.3f + i * 0.12f)), Offset(size.width * (if (i % 3 == 2) 0.6f else 0.88f), size.height * (0.3f + i * 0.12f)), 2.dp.toPx())
            }
        }
    }
}

// ---------------------------------------------------------------- Appearance previews

/** A phone drawn in light, dark or half and half. */
@Composable
fun ThemePreview(mode: ThemeMode, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val pw = size.width * 0.52f; val ph = size.height
        val left = (size.width - pw) / 2f
        fun screen(dark: Boolean, clip: Boolean) {
            val bg = if (dark) Color(0xFF0B0D11) else Color.White
            val line = if (dark) Color(0xFF39414D) else Color(0xFFD6DCE3)
            drawRoundRect(bg, Offset(left + 3.dp.toPx(), 3.dp.toPx()), Size(pw - 6.dp.toPx(), ph - 6.dp.toPx()), CornerRadius(8.dp.toPx()))
            drawRoundRect(line, Offset(left + 8.dp.toPx(), 10.dp.toPx()), Size(pw * 0.4f, 4.dp.toPx()), CornerRadius(2.dp.toPx()))
            drawRoundRect(Color(0xFFFEC604), Offset(left + 8.dp.toPx(), ph * 0.42f), Size(pw - 16.dp.toPx(), 9.dp.toPx()), CornerRadius(4.dp.toPx()))
            for (i in 0..2) drawRoundRect(line, Offset(left + 8.dp.toPx(), ph * 0.58f + i * 9.dp.toPx()), Size(pw - 16.dp.toPx(), 5.dp.toPx()), CornerRadius(2.dp.toPx()))
        }
        drawRoundRect(Color(0xFF0A0C10), Offset(left, 0f), Size(pw, ph), CornerRadius(10.dp.toPx()))
        when (mode) {
            ThemeMode.Light -> screen(false, false)
            ThemeMode.Dark -> screen(true, false)
            ThemeMode.System -> {
                screen(false, false)
                clipRect(left + pw / 2f, 0f, left + pw, ph) { screen(true, true) }
            }
        }
    }
}

// ---------------------------------------------------------------- Chart

private fun niceMax(max: Float): Float {
    val steps = listOf(50f, 100f, 250f, 500f, 1000f, 2500f, 5000f, 10000f, 25000f, 50000f, 100000f, 250000f)
    val raw = max.coerceAtLeast(1f)
    return (steps.firstOrNull { it * 4f >= raw } ?: steps.last()) * 4f
}

/** Bars with a value axis on the left. [xLabels] has one entry per bar; blank entries are skipped so a long day stays readable. */
@Composable
fun BarChart(values: List<Float>, xLabels: List<String>, modifier: Modifier = Modifier, highlight: Int = -1, description: String = "") {
    val c = RideTheme.colors
    val max = remember(values) { niceMax(values.maxOrNull() ?: 0f) }
    val nf = remember { NumberFormat.getIntegerInstance(Locale.US) }
    Row(modifier.fillMaxWidth()) {
        Column(Modifier.height(140.dp).width(48.dp), verticalArrangement = Arrangement.SpaceBetween) {
            for (i in 4 downTo 0) Text(nf.format((max / 4f * i).toLong()), color = c.muted, fontSize = 10.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth().padding(end = 6.dp))
        }
        Column(Modifier.weight(1f)) {
            Canvas(Modifier.fillMaxWidth().height(140.dp)) {
                for (i in 0..4) { val y = size.height * (1f - i / 4f); drawLine(c.line, Offset(0f, y), Offset(size.width, y), 1.dp.toPx()) }
                val slot = size.width / values.size
                val bw = slot * 0.62f
                values.forEachIndexed { i, v ->
                    val bh = (size.height * (v / max)).coerceAtLeast(if (v > 0f) 2.dp.toPx() else 0f)
                    val color = if (highlight >= 0 && i != highlight) c.fill.copy(alpha = 0.55f) else c.fill
                    drawRoundRect(color, Offset(slot * i + (slot - bw) / 2f, size.height - bh), Size(bw, bh), CornerRadius(2.dp.toPx()))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                xLabels.forEach { l -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text(l, color = c.muted, fontSize = 10.sp, softWrap = false, maxLines = 1, modifier = Modifier.wrapContentWidth(unbounded = true)) } }
            }
        }
    }
}
