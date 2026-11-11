package com.ridenow.driver.ui.intro

import com.ridenow.driver.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.tooling.preview.Preview
import com.ridenow.driver.ui.components.LogoMark
import com.ridenow.driver.ui.components.Orb
import com.ridenow.driver.ui.components.RideButton
import com.ridenow.driver.ui.components.RideIcon
import com.ridenow.driver.ui.components.RideIcons
import com.ridenow.driver.ui.theme.RideNowTheme
import com.ridenow.driver.ui.theme.RideTheme
import kotlinx.coroutines.launch

private class Slide(val title: String, val text: String)


/** Three-slide introduction. [onDone] runs on "Get started" or "Skip". */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun IntroScreen(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val textGoOnlineInOneTap = stringResource(R.string.intro_online_title)
    val textSwitchOnWhenYouAreReady = stringResource(R.string.intro_online_text)
    val textNavigateWithEase = stringResource(R.string.intro_navigate_title)
    val textGetGuidedToThePickup = stringResource(R.string.intro_navigate_text)
    val textKeepWhatYouEarn = stringResource(R.string.intro_earn_title)
    val textTrackEarningsForEveryTrip = stringResource(R.string.intro_earn_text)
    val textSkip = stringResource(R.string.text_skip)
    val textGetStarted = stringResource(R.string.intro_get_started)
    val textNext = stringResource(R.string.text_next)

    val Slides = listOf(
        Slide(textGoOnlineInOneTap, textSwitchOnWhenYouAreReady),
        Slide(textNavigateWithEase, textGetGuidedToThePickup),
        Slide(textKeepWhatYouEarn, textTrackEarningsForEveryTrip),
    )

    val c = RideTheme.colors
    val pager = rememberPagerState { Slides.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == Slides.lastIndex
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            LogoMark(30.dp); Spacer(Modifier.weight(1f))
            Box(Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onDone).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) { Text(textSkip, color = c.muted, fontSize = 14.sp) }
        }
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.Start) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Art(page) }
                Text(Slides[page].title, color = c.fg, fontSize = 30.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(top = 16.dp).semantics { heading() })
                Text(Slides[page].text, color = c.muted, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 10.dp))
            }
        }
        Row(Modifier.padding(vertical = 22.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(Slides.size) { i -> Box(Modifier.height(8.dp).width(if (i == pager.currentPage) 24.dp else 8.dp).clip(CircleShape).background(if (i == pager.currentPage) c.fill else c.line)) }
        }
        RideButton(if (last) textGetStarted else textNext, { if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }, modifier = Modifier.padding(bottom = 14.dp))
    }
}

@Composable
private fun Art(page: Int) {
    val textYouAreOnline = stringResource(R.string.intro_you_are_online)
    val textNewRequest = stringResource(R.string.intro_new_request)
    val textRequestMeta = stringResource(R.string.intro_request_meta)
    val textRequestFare = stringResource(R.string.intro_request_fare)
    val textPickupName = stringResource(R.string.intro_pickup_name)
    val textDropName = stringResource(R.string.intro_drop_name)
    val textDecline = stringResource(R.string.intro_decline)
    val textAccept = stringResource(R.string.intro_accept)
    val textRiderName = stringResource(R.string.intro_rider_name)
    val textRiderInitials = stringResource(R.string.intro_rider_initials)
    val textRiderPickup = stringResource(R.string.intro_rider_pickup)
    val textEta = stringResource(R.string.intro_eta)
    val textChipCash = stringResource(R.string.intro_chip_cash)
    val textChipWeekly = stringResource(R.string.intro_chip_weekly)
    val textChipHistory = stringResource(R.string.intro_chip_history)

    val c = RideTheme.colors
    Box(Modifier.fillMaxWidth().aspectRatio(0.95f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            drawCircle(c.ring1, r * 0.95f, style = Stroke(1.dp.toPx())); drawCircle(c.ring2, r * 0.72f, style = Stroke(1.dp.toPx()))
        }
        when (page) {
            0 -> Column(Modifier.fillMaxWidth(0.88f).clip(RoundedCornerShape(26.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(26.dp)).padding(14.dp)) {
                Row(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(16.dp)).background(c.bg).border(1.dp, c.accent, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(c.accent))
                    Text(textYouAreOnline, color = c.fg, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(start = 10.dp))
                    Box(Modifier.width(40.dp).height(24.dp).clip(CircleShape).background(c.fill).padding(3.dp), contentAlignment = Alignment.CenterEnd) {
                        Box(Modifier.size(18.dp).clip(CircleShape).background(c.onFill))
                    }
                }
                Text(textNewRequest, color = c.muted, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(top = 14.dp))
                Column(Modifier.padding(top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.accent.copy(alpha = 0.14f)).border(1.dp, c.accent, RoundedCornerShape(16.dp)).padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(textRequestMeta, color = c.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text(textRequestFare, color = c.fg, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RideIcon(RideIcons.Pin, c.accent, size = 18.dp); Text(textPickupName, color = c.fg, fontSize = 14.sp, modifier = Modifier.padding(start = 10.dp))
                    }
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        RideIcon(RideIcons.Flag, c.accent, size = 18.dp); Text(textDropName, color = c.fg, fontSize = 14.sp, modifier = Modifier.padding(start = 10.dp))
                    }
                }
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton(textDecline, filled = false, Modifier.weight(1f))
                    PillButton(textAccept, filled = true, Modifier.weight(1f))
                }
            }
            1 -> Box(Modifier.fillMaxWidth(0.88f).aspectRatio(0.9f).clip(RoundedCornerShape(26.dp)).border(1.dp, c.border, RoundedCornerShape(26.dp))) {
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(c.bg)
                    listOf(0.25f, 0.5f, 0.75f).forEach { f -> drawLine(c.street, Offset(0f, size.height * f), Offset(size.width, size.height * f), 5.dp.toPx()); drawLine(c.street, Offset(size.width * f, 0f), Offset(size.width * f, size.height), 5.dp.toPx()) }
                    val p = Path().apply { moveTo(size.width * 0.2f, size.height * 0.75f); cubicTo(size.width * 0.35f, size.height * 0.5f, size.width * 0.5f, size.height * 0.6f, size.width * 0.75f, size.height * 0.2f) }
                    drawPath(p, c.accent.copy(alpha = 0.2f), style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
                    drawPath(p, c.accent, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
                    drawCircle(c.fill, 8.dp.toPx(), Offset(size.width * 0.2f, size.height * 0.75f)); drawCircle(c.fg, 8.dp.toPx(), Offset(size.width * 0.75f, size.height * 0.2f))
                    // The driver's own vehicle, heading up the route.
                    val car = Offset(size.width * 0.5f, size.height * 0.56f)
                    drawCircle(c.accent.copy(alpha = 0.18f), 26.dp.toPx(), car)
                    rotate(-25f, car) {
                        drawRoundRect(c.fg, Offset(car.x - 10.dp.toPx(), car.y - 18.dp.toPx()), Size(20.dp.toPx(), 36.dp.toPx()), CornerRadius(8.dp.toPx()))
                        drawRoundRect(c.bg, Offset(car.x - 7.dp.toPx(), car.y - 9.dp.toPx()), Size(14.dp.toPx(), 10.dp.toPx()), CornerRadius(3.dp.toPx()))
                    }
                }
                Box(Modifier.align(Alignment.TopEnd).padding(top = 92.dp, end = 64.dp).clip(CircleShape).background(c.fg).padding(horizontal = 12.dp, vertical = 7.dp)) {
                    Text(textEta, color = c.bg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Row(Modifier.align(Alignment.BottomCenter).padding(12.dp).fillMaxWidth().height(60.dp).clip(RoundedCornerShape(18.dp)).background(c.bg).border(1.dp, c.border, RoundedCornerShape(18.dp)).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.14f)).border(1.dp, c.accent, CircleShape), contentAlignment = Alignment.Center) { Text(textRiderInitials, color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    Column(Modifier.weight(1f).padding(start = 10.dp)) { Text(textRiderName, color = c.fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold); Text(textRiderPickup, color = c.muted, fontSize = 11.sp) }
                    RideIcon(RideIcons.Call, c.accent)
                }
            }
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Orb(96.dp) { RideIcon(RideIcons.Wallet, c.onFill, size = 44.dp) }
                Chip(RideIcons.Card, textChipCash, Modifier.align(Alignment.TopStart).padding(top = 28.dp))
                Chip(RideIcons.Chart, textChipWeekly, Modifier.align(Alignment.CenterEnd).padding(top = 150.dp))
                Chip(RideIcons.Clock, textChipHistory, Modifier.align(Alignment.BottomStart).padding(bottom = 28.dp))
            }
        }
    }
}

@Composable
private fun PillButton(text: String, filled: Boolean, modifier: Modifier) {
    val c = RideTheme.colors
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier.height(44.dp).clip(shape).background(if (filled) c.fill else androidx.compose.ui.graphics.Color.Transparent)
            .then(if (filled) Modifier else Modifier.border(1.dp, c.border, shape)),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (filled) c.onFill else c.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun Chip(icon: List<String>, text: String, modifier: Modifier) {
    val c = RideTheme.colors
    Row(modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(22.dp)).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        RideIcon(icon, c.accent, size = 18.dp); Text("  $text", color = c.fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun IntroDarkPreview() = RideNowTheme(darkTheme = true) { IntroScreen({}) }

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun IntroLightPreview() = RideNowTheme(darkTheme = false) { IntroScreen({}) }
