package com.ridenow.driver.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridenow.driver.R
import com.ridenow.driver.ui.theme.RideColors
import com.ridenow.driver.ui.theme.RideNowTheme
import com.ridenow.driver.ui.theme.RideTheme
import com.ridenow.driver.ui.util.rememberReducedMotion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * Animated splash: a route draws across a faint city map toward the RideNow Driver orb,
 * a dot orbits the halo, then the orb pulses while the wordmark fades in.
 * Respects the system "remove animations" setting.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val reduceMotion = rememberReducedMotion()

    val route = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val intro = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduceMotion) coroutineScope {
            launch { route.animateTo(1f, tween(1800, easing = FastOutSlowInEasing)) }
            launch { intro.animateTo(1f, tween(900, delayMillis = 300, easing = FastOutSlowInEasing)) }
        }
        delay(if (reduceMotion) 1500 else 1100)
        onFinished()
    }

    val loop = rememberInfiniteTransition(label = "splash")
    val orbitRaw by loop.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "orbit")
    val pulseRaw by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearOutSlowInEasing)), label = "pulse")
    val orbit = if (reduceMotion) 0f else orbitRaw
    val pulse = if (reduceMotion) 0f else pulseRaw
    val description = stringResource(R.string.splash_description)

    BoxWithConstraints(
        modifier.fillMaxSize().background(c.bg).semantics { contentDescription = description },
    ) {
        val textTop = maxHeight * 0.60f

        Canvas(Modifier.fillMaxSize()) {
            drawStreets(c)
            drawRoute(c, route.value)
            drawHalo(c, intro.value, orbit, pulse, route.value)
        }

        // Stacked RideNow Driver lockup (symbol, wordmark, DRIVER) sits inside the halo.
        Image(
            painter = painterResource(if (c.isDark) R.drawable.driver_splash_wordmark_dark else R.drawable.driver_splash_wordmark_light),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = maxHeight * 0.426f - 89.dp).width(190.dp).aspectRatio(771f / 720f).graphicsLayer {
                alpha = intro.value
                scaleX = 0.8f + 0.2f * intro.value
                scaleY = 0.8f + 0.2f * intro.value
            },
        )

        Text(
            stringResource(R.string.splash_tagline), color = c.muted, fontSize = 14.sp,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = textTop).graphicsLayer {
                alpha = intro.value
                translationY = (1f - intro.value) * 16.dp.toPx()
            },
        )

        Text(
            stringResource(R.string.splash_version), color = c.muted, fontSize = 12.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp).graphicsLayer { alpha = intro.value },
        )
    }
}

private fun DrawScope.drawStreets(c: RideColors) {
    val minor = c.street.copy(alpha = 0.55f)
    var y = 60.dp.toPx()
    while (y < size.height) {
        drawLine(minor, Offset(0f, y), Offset(size.width, y), 5.dp.toPx(), StrokeCap.Round); y += 85.dp.toPx()
    }
    var x = 55.dp.toPx()
    while (x < size.width) {
        drawLine(minor, Offset(x, 0f), Offset(x, size.height), 5.dp.toPx(), StrokeCap.Round); x += 95.dp.toPx()
    }
    val major = c.street2.copy(alpha = 0.55f)
    drawLine(major, Offset(-10f, size.height - 40.dp.toPx()), Offset(size.width + 10f, 30.dp.toPx()), 9.dp.toPx(), StrokeCap.Round)
    drawLine(major, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 9.dp.toPx(), StrokeCap.Round)
}

private fun orbCenter(size: androidx.compose.ui.geometry.Size) = Offset(size.width / 2, size.height * 0.426f)

private fun DrawScope.drawRoute(c: RideColors, progress: Float) {
    if (progress <= 0f) return
    val sx = size.width / 390f
    val sy = size.height / 844f
    val path = Path().apply {
        moveTo(-10 * sx, 790 * sy)
        cubicTo(80 * sx, 730 * sy, 120 * sx, 570 * sy, 160 * sx, 440 * sy)
        cubicTo(200 * sx, 310 * sy, 190 * sx, 390 * sy, 195 * sx, 372 * sy)
    }
    val measure = PathMeasure().apply { setPath(path, false) }
    val end = measure.length * progress
    val segment = Path().also { measure.getSegment(0f, end, it, true) }
    drawPath(segment, c.accent.copy(alpha = 0.2f), style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
    drawPath(segment, c.accent.copy(alpha = 0.75f), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
    if (progress < 0.98f) {
        val p = measure.getPosition(end)
        drawCircle(c.accent.copy(alpha = 0.25f), 12.dp.toPx(), p)
        drawCircle(c.accent, 5.dp.toPx(), p)
    }
}

private fun DrawScope.drawHalo(c: RideColors, intro: Float, orbit: Float, pulse: Float, route: Float) {
    val center = orbCenter(size)
    val ringOuter = 128.dp.toPx()
    val ringInner = 96.dp.toPx()
    drawCircle(Brush.radialGradient(listOf(c.accent.copy(alpha = 0.18f * intro), androidx.compose.ui.graphics.Color.Transparent), center, 150.dp.toPx()), 150.dp.toPx(), center)
    drawCircle(c.ring1.copy(alpha = intro), ringOuter, center, style = Stroke(1.dp.toPx()))
    drawCircle(c.ring2.copy(alpha = intro), ringInner, center, style = Stroke(1.dp.toPx()))
    if (route > 0.95f) {
        drawCircle(c.accent.copy(alpha = (1f - pulse) * 0.35f), ringInner * 0.6f + pulse * 90.dp.toPx(), center, style = Stroke(2.dp.toPx()))
    }
    val angle = Math.toRadians(orbit.toDouble() - 90.0)
    val dot = Offset(center.x + cos(angle).toFloat() * ringOuter, center.y + sin(angle).toFloat() * ringOuter)
    drawCircle(c.accent.copy(alpha = 0.3f * intro), 11.dp.toPx(), dot)
    drawCircle(c.accent.copy(alpha = intro), 4.dp.toPx(), dot)
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SplashDarkPreview() = RideNowTheme(darkTheme = true) { SplashScreen(onFinished = {}) }

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SplashLightPreview() = RideNowTheme(darkTheme = false) { SplashScreen(onFinished = {}) }
