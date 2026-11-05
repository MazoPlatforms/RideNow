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
    val Camera = listOf("M3 7h2.5L7 5h6l1.5 2H17v9H3z", "M10,11m-2.5,0a2.5,2.5 0 1,0 5,0a2.5,2.5 0 1,0 -5,0")
    val Image = listOf("M5.5,4h9a2.5,2.5 0 0 1 2.5,2.5v7a2.5,2.5 0 0 1 -2.5,2.5h-9a2.5,2.5 0 0 1 -2.5,-2.5v-7a2.5,2.5 0 0 1 2.5,-2.5z", "M4 15l4.5-4 3 2.5 2.5-2L17 14")
    val Trash = listOf("M4 6h12M8 6V4h4v2M6 6l.7 10h6.6L14 6")
    val Person = listOf("M10,7m-3,0a3,3 0 1,0 6,0a3,3 0 1,0 -6,0", "M4 17c.8-3.2 3.2-5 6-5s5.2 1.8 6 5")
    val Mail = listOf("M4.5,4.5h11a2,2 0 0 1 2,2v7a2,2 0 0 1 -2,2h-11a2,2 0 0 1 -2,-2v-7a2,2 0 0 1 2,-2z", "M3 6l7 5 7-5")
    val Bell = listOf("M5 14V9a5 5 0 1 1 10 0v5l1.5 2h-13z", "M8 18h4")
    val Car = listOf("M3 11.5l1.6-4.2A2 2 0 0 1 6.5 6h7a2 2 0 0 1 1.9 1.3L17 11.5V15H3z", "M3 11.5h14")
    val Receipt = listOf("M5 3h10v14l-2.5-1.5L10 17l-2.5-1.5L5 17z", "M8 7h4M8 10h4")
    val Shield = listOf("M10 2l6 2.5v5c0 4-2.5 6.5-6 8-3.5-1.5-6-4-6-8v-5z")
    val Clock = listOf("M10,10m-7,0a7,7 0 1,0 14,0a7,7 0 1,0 -14,0", "M10 6v4l2.5 1.5")
    val Menu = listOf("M3 5.5h14M3 10h14M3 14.5h9")
    val Pencil = listOf("M12.5 3.5l4 4L7 17H3v-4z", "M10.5 5.5l4 4")
    val Home = listOf("M3 9.5L10 3l7 6.5V17H3z", "M8 17v-5h4v5")
    val Work = listOf("M4,6h12a1,1 0 0 1 1,1v8a1,1 0 0 1 -1,1h-12a1,1 0 0 1 -1,-1v-8a1,1 0 0 1 1,-1z", "M7.5 6V4.5h5V6")
    val Plus = listOf("M10 4v12M4 10h12")
    val Heart = listOf("M10 16.5S3 12 3 7.5A3.5 3.5 0 0 1 10 6a3.5 3.5 0 0 1 7 1.5C17 12 10 16.5 10 16.5z")
    val Map = listOf("M3 5l5-2 4 2 5-2v12l-5 2-4-2-5 2z", "M8 3v12M12 5v12")
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
