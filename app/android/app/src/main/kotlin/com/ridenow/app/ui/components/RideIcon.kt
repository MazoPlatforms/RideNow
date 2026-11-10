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
    val Star = listOf("M10 2.5l2.3 4.7 5.2.8-3.8 3.6.9 5.2L10 14.3l-4.6 2.5.9-5.2L2.5 8l5.2-.8z")
    val Call = listOf("M5 3h3l1.5 4-2 1.2a9 9 0 0 0 4.3 4.3L13 10.5l4 1.5v3a2 2 0 0 1-2 2A12 12 0 0 1 3 5a2 2 0 0 1 2-2z")
    val Message = listOf("M3 5.5A2.5 2.5 0 0 1 5.5 3h9A2.5 2.5 0 0 1 17 5.5v6a2.5 2.5 0 0 1-2.5 2.5H9l-4 3v-3a2.5 2.5 0 0 1-2-2.5z")
    val Share = listOf("M15,4.5m-2,0a2,2 0 1,0 4,0a2,2 0 1,0 -4,0", "M5,10m-2,0a2,2 0 1,0 4,0a2,2 0 1,0 -4,0", "M15,15.5m-2,0a2,2 0 1,0 4,0a2,2 0 1,0 -4,0", "M6.8 9l6.4-3.5M6.8 11l6.4 3.5")
    val Download = listOf("M10 3v9M6 9l4 4 4-4M4 16.5h12")
    val Globe = listOf("M10,10m-7,0a7,7 0 1,0 14,0a7,7 0 1,0 -14,0", "M3 10h14M10 3c2.5 2 2.5 12 0 14M10 3c-2.5 2-2.5 12 0 14")
    val Moon = listOf("M16 11.5A6.5 6.5 0 1 1 8.5 4a5 5 0 0 0 7.5 7.5z")
    val Help = listOf("M10,10m-7,0a7,7 0 1,0 14,0a7,7 0 1,0 -14,0", "M8 8.2a2 2 0 1 1 3 1.7c-.7.4-1 .8-1 1.6M10 14v.01")
    val Info = listOf("M10,10m-7,0a7,7 0 1,0 14,0a7,7 0 1,0 -14,0", "M10 9v5M10 6v.01")
    val Sliders = listOf("M4 6h12M4 14h12", "M8,6m-2,0a2,2 0 1,0 4,0a2,2 0 1,0 -4,0", "M13,14m-2,0a2,2 0 1,0 4,0a2,2 0 1,0 -4,0")
    val Logout = listOf("M8 3H5a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h3M12 6l4 4-4 4M16 10H7")
    val Cash = listOf("M4,5h12a2,2 0 0 1 2,2v6a2,2 0 0 1 -2,2h-12a2,2 0 0 1 -2,-2v-6a2,2 0 0 1 2,-2z", "M10,10m-2.5,0a2.5,2.5 0 1,0 5,0a2.5,2.5 0 1,0 -5,0")
    val Wallet = listOf("M3,6h13a1,1 0 0 1 1,1v9a1,1 0 0 1 -1,1h-13z", "M3 6V5a1 1 0 0 1 1-1h11", "M13.5,11m-1,0a1,1 0 1,0 2,0a1,1 0 1,0 -2,0")
    val Phone = listOf("M7,2.5h6a2,2 0 0 1 2,2v11a2,2 0 0 1 -2,2h-6a2,2 0 0 1 -2,-2v-11a2,2 0 0 1 2,-2z", "M9 15h2")
    val Calendar = listOf("M5.5,4.5h9a2.5,2.5 0 0 1 2.5,2.5v8a2.5,2.5 0 0 1 -2.5,2.5h-9a2.5,2.5 0 0 1 -2.5,-2.5v-8a2.5,2.5 0 0 1 2.5,-2.5z", "M3 8.5h14M7 3v3M13 3v3")
    val Tag = listOf("M3 10.5V4h6.5L17 11.5 11.5 17z", "M6.5,7.5m-1,0a1,1 0 1,0 2,0a1,1 0 1,0 -2,0")
    val More = listOf("M10,4.5m-1.2,0a1.2,1.2 0 1,0 2.4,0a1.2,1.2 0 1,0 -2.4,0", "M10,10m-1.2,0a1.2,1.2 0 1,0 2.4,0a1.2,1.2 0 1,0 -2.4,0", "M10,15.5m-1.2,0a1.2,1.2 0 1,0 2.4,0a1.2,1.2 0 1,0 -2.4,0")
    val Book = listOf("M3 4.5h5a2 2 0 0 1 2 2V17a2 2 0 0 0-2-2H3zM17 4.5h-5a2 2 0 0 0-2 2V17a2 2 0 0 1 2-2h5z")
    val Doc = listOf("M5 2.5h7l3 3V17.5H5z", "M12 2.5v3h3M8 9h4M8 12h4")
    val WifiOff = listOf("M2.5 8a11 11 0 0 1 15 0M5 11a7 7 0 0 1 10 0M8 14a3 3 0 0 1 4 0M3 3l14 14")
    val Alert = listOf("M10 3l8 14H2z", "M10 8v4M10 14.5v.01")
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
