package com.ridenow.app.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.ridenow.app.ui.theme.RideColors

/** Faint city grid used behind onboarding screens. */
fun DrawScope.drawCityStreets(c: RideColors, alpha: Float = 0.5f) {
    val minor = c.street.copy(alpha = alpha)
    var y = 60.dp.toPx()
    while (y < size.height) { drawLine(minor, Offset(0f, y), Offset(size.width, y), 5.dp.toPx(), StrokeCap.Round); y += 85.dp.toPx() }
    var x = 55.dp.toPx()
    while (x < size.width) { drawLine(minor, Offset(x, 0f), Offset(x, size.height), 5.dp.toPx(), StrokeCap.Round); x += 95.dp.toPx() }
    val major = c.street2.copy(alpha = alpha)
    drawLine(major, Offset(-10f, size.height - 40.dp.toPx()), Offset(size.width + 10f, 30.dp.toPx()), 9.dp.toPx(), StrokeCap.Round)
    drawLine(major, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 9.dp.toPx(), StrokeCap.Round)
}

/** Three tiny vehicles drifting slowly along streets. [phase] runs 0..1. */
fun DrawScope.drawDrifters(c: RideColors, phase: Float) {
    listOf(Triple(2, 1f, 0.1f), Triple(5, -0.7f, 0.6f), Triple(7, 0.5f, 0.3f)).forEach { (row, speed, offset) ->
        val t = (((phase * speed + offset) % 1f) + 1f) % 1f
        val p = Offset(t * size.width, (60 + 85 * row).dp.toPx())
        drawCircle(c.accent.copy(alpha = 0.15f), 9.dp.toPx(), p)
        drawCircle(c.accent.copy(alpha = 0.6f), 3.dp.toPx(), p)
    }
}
