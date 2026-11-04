package com.ridenow.app.ui.welcome

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridenow.app.R
import com.ridenow.app.ui.components.RideButton
import com.ridenow.app.ui.components.RideButtonStyle
import com.ridenow.app.ui.components.RideIcon
import com.ridenow.app.ui.components.RideIcons
import com.ridenow.app.ui.components.RideMark
import com.ridenow.app.ui.components.drawCityStreets
import com.ridenow.app.ui.components.drawDrifters
import com.ridenow.app.ui.theme.OrbGradient
import com.ridenow.app.ui.theme.RideColors
import com.ridenow.app.ui.theme.RideNowTheme
import com.ridenow.app.ui.theme.RideTheme
import com.ridenow.app.ui.util.entrance
import com.ridenow.app.ui.util.rememberReducedMotion
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun WelcomeScreen(
    onGetStarted: () -> Unit,
    onHaveAccount: () -> Unit,
    onHelp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = RideTheme.colors
    val reduce = rememberReducedMotion()
    val loop = rememberInfiniteTransition(label = "welcome")
    val orbitRaw by loop.animateFloat(0f, 360f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "orbit")
    val breathRaw by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breath")
    val driftRaw by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(40000, easing = LinearEasing)), label = "drift")
    val orbit = if (reduce) 40f else orbitRaw
    val breath = if (reduce) 0f else breathRaw
    val drift = if (reduce) 0f else driftRaw

    Box(modifier.fillMaxSize().background(c.bg)) {
        Canvas(Modifier.fillMaxSize()) {
            drawCityStreets(c, 0.3f)
            drawDrifters(c, drift)
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
            TopBar(onHelp, Modifier.entrance(0))

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().entrance(120), contentAlignment = Alignment.Center) {
                val side = minOf(236.dp, maxHeight, maxWidth)
                Canvas(Modifier.size(side).clearAndSetSemantics { }) { drawWelcomeOrb(c, orbit, breath) }
            }

            Column(Modifier.entrance(220)) {
                Text(
                    stringResource(R.string.welcome_title), color = c.fg, fontSize = 34.sp, fontWeight = FontWeight.Light,
                    lineHeight = 39.sp, letterSpacing = (-0.3).sp, modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(R.string.welcome_subtitle), color = c.muted, fontSize = 15.sp, lineHeight = 22.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            Column(Modifier.padding(top = 22.dp).entrance(320)) {
                HorizontalDivider(color = c.line)
                Feature(RideIcons.Pin, R.string.welcome_feature_pickup)
                Feature(RideIcons.Flag, R.string.welcome_feature_destination)
                Feature(RideIcons.Card, R.string.welcome_feature_pay)
            }

            Column(Modifier.padding(top = 22.dp, bottom = 12.dp).entrance(420), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RideButton(stringResource(R.string.welcome_get_started), onGetStarted)
                RideButton(stringResource(R.string.welcome_have_account), onHaveAccount, style = RideButtonStyle.Text)
            }
        }
    }
}

@Composable
private fun TopBar(onHelp: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    Row(modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        RideMark(c.accent)
        Spacer(Modifier.width(10.dp))
        Text("RideNow", color = c.fg, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp)
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.heightIn(min = 48.dp).clickable(onClick = onHelp).padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) { Text(stringResource(R.string.welcome_help), color = c.muted, fontSize = 14.sp) }
    }
}

@Composable
private fun Feature(icon: List<String>, textRes: Int) {
    val c = RideTheme.colors
    Column {
        Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
            RideIcon(icon, c.accent)
            Spacer(Modifier.width(14.dp))
            Text(stringResource(textRes), color = c.fg, fontSize = 14.sp)
        }
        HorizontalDivider(color = c.line)
    }
}

/** Orb with two rings, a slowly orbiting dot, a soft glow and a gentle breathing scale. */
private fun DrawScope.drawWelcomeOrb(c: RideColors, orbit: Float, breath: Float) {
    val u = size.minDimension / 236f
    val center = Offset(size.width / 2, size.height / 2)
    val outer = 117f * u
    val inner = 87f * u
    val orbR = 52f * u * (1f + 0.03f * breath)

    drawCircle(Brush.radialGradient(listOf(c.accent.copy(alpha = 0.16f + 0.06f * breath), Color.Transparent), center, orbR * 2.3f), orbR * 2.3f, center)
    drawCircle(c.ring1, outer, center, style = Stroke(1.dp.toPx()))
    drawCircle(c.ring2, inner, center, style = Stroke(1.dp.toPx()))

    val a = Math.toRadians(orbit.toDouble() - 90.0)
    val dot = Offset(center.x + cos(a).toFloat() * outer, center.y + sin(a).toFloat() * outer)
    drawCircle(c.accent.copy(alpha = 0.3f), 10.dp.toPx(), dot)
    drawCircle(c.fg, 4.dp.toPx(), dot)

    drawCircle(
        Brush.radialGradient(
            0f to OrbGradient[0], 0.45f to OrbGradient[1], 1f to OrbGradient[2],
            center = Offset(center.x - orbR * 0.2f, center.y - orbR * 0.3f), radius = orbR * 1.5f,
        ),
        orbR, center,
    )
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun WelcomeDarkPreview() = RideNowTheme(darkTheme = true) { WelcomeScreen({}, {}, {}) }

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun WelcomeLightPreview() = RideNowTheme(darkTheme = false) { WelcomeScreen({}, {}, {}) }
