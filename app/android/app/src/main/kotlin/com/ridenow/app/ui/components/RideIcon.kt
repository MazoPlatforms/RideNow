package com.ridenow.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Line icons on the 20 px RideNow grid (1.5 stroke, round caps), drawn from SVG path data. */
object RideIcons {
    val Pin = listOf("M10 18s6-5.2 6-9.5a6 6 0 1 0-12 0C4 12.8 10 18 10 18z", "M8,8.5a2,2 0 1,0 4,0a2,2 0 1,0 -4,0")
    val Flag = listOf("M5 17V3m0 1h10l-2 3.5 2 3.5H5")
    val Back = listOf("M12.5 4L6.5 10l6 6")
    val ChevronDown = listOf("M5 7.5l5 5 5-5")
    val Check = listOf("M4.5 10.5l3.5 3.5 7.5-8")
    val Close = listOf("M5 5l10 10M15 5L5 15")
    val Lock = listOf("M5,9h10v8h-10z", "M7 9V7a3 3 0 0 1 6 0v2")
    val Search = listOf("M9,9m-6,0a6,6 0 1,0 12,0a6,6 0 1,0 -12,0", "M13.5 13.5L17 17")
    val Card = listOf("M4.5,4.5h11a2.5,2.5 0 0 1 2.5,2.5v6a2.5,2.5 0 0 1 -2.5,2.5h-11a2.5,2.5 0 0 1 -2.5,-2.5v-6a2.5,2.5 0 0 1 2.5,-2.5z", "M2 8.5h16")
}

@Composable
fun RideIcon(paths: List<String>, tint: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    val parsed = remember(paths) { paths.map { PathParser().parsePathString(it).toPath() } }
    Canvas(modifier.size(size)) {
        val s = this.size.width / 20f
        scale(s, Offset.Zero) {
            parsed.forEach { drawPath(it, tint, style = Stroke(1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
        }
    }
}

/** The RideNow mark: ring with a centre dot. */
@Composable
fun RideMark(tint: Color, modifier: Modifier = Modifier, size: Dp = 26.dp) {
    Canvas(modifier.size(size)) {
        val u = this.size.width / 26f
        drawCircle(tint, 11f * u, style = Stroke(1.6f * u))
        drawCircle(tint, 4f * u)
    }
}
