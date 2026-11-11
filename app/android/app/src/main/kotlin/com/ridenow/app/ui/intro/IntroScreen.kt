package com.ridenow.app.ui.intro

import com.ridenow.app.R
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import com.ridenow.app.ui.booking.VehicleArt
import com.ridenow.app.ui.components.LogoMark
import com.ridenow.app.ui.components.RideButton
import com.ridenow.app.ui.components.RideButtonStyle
import com.ridenow.app.ui.components.RideIcon
import com.ridenow.app.ui.components.RideIcons
import com.ridenow.app.ui.theme.RideNowTheme
import com.ridenow.app.ui.theme.RideTheme
import kotlinx.coroutines.launch

private class Slide(val title: String, val text: String)


/** Three-slide introduction. [onDone] runs on "Get started" or "Skip". */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun IntroScreen(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val textBookInSeconds = stringResource(R.string.text_book_in_seconds)
    val textPickADestinationChooseAVehicleAndGoPayYourDriverInCash = stringResource(R.string.text_pick_a_destination_choose_a_vehicle_and_go_pay_your_driver_in_cash)
    val textTrackEveryMove = stringResource(R.string.text_track_every_move)
    val textWatchYourDriverArriveOnTheMapWithLiveTimeAndDistance = stringResource(R.string.text_watch_your_driver_arrive_on_the_map_with_live_time_and_distance)
    val textRideWithConfidence = stringResource(R.string.text_ride_with_confidence)
    val textShareYourTripCheckThePlateAndReachEmergencyHelpInOneTap = stringResource(R.string.text_share_your_trip_check_the_plate_and_reach_emergency_help_in_one_tap)
    val textSkip = stringResource(R.string.text_skip)
    val textWelcomeGetStarted = stringResource(R.string.welcome_get_started)
    val textNext = stringResource(R.string.text_next)

    val Slides = listOf(
        Slide(textBookInSeconds, textPickADestinationChooseAVehicleAndGoPayYourDriverInCash),
        Slide(textTrackEveryMove, textWatchYourDriverArriveOnTheMapWithLiveTimeAndDistance),
        Slide(textRideWithConfidence, textShareYourTripCheckThePlateAndReachEmergencyHelpInOneTap),
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
            repeat(Slides.size) { i -> Box(Modifier.height(8.dp).width(if (i == pager.currentPage) 24.dp else 8.dp).clip(CircleShape).background(if (i == pager.currentPage) c.accent else c.line)) }
        }
        RideButton(if (last) textWelcomeGetStarted else textNext, { if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }, modifier = Modifier.padding(bottom = 14.dp))
    }
}

@Composable
private fun Art(page: Int) {
    val textHelixGardenPlaza2 = stringResource(R.string.text_helix_garden_plaza_2)
    val textMiraTanaka = stringResource(R.string.text_mira_tanaka)
    val textOrbitRn21004Min = stringResource(R.string.text_orbit_rn_2100_4_min)
    val textTripShareLive = stringResource(R.string.trip_share_live)
    val textTrustedContacts = stringResource(R.string.text_trusted_contacts)
    val textEmergencyHelp = stringResource(R.string.text_emergency_help)

    val c = RideTheme.colors
    Box(Modifier.fillMaxWidth().aspectRatio(0.95f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            drawCircle(c.ring1, r * 0.95f, style = Stroke(1.dp.toPx())); drawCircle(c.ring2, r * 0.72f, style = Stroke(1.dp.toPx()))
        }
        when (page) {
            0 -> Column(Modifier.fillMaxWidth(0.88f).clip(RoundedCornerShape(26.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(26.dp)).padding(14.dp)) {
                Row(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, c.accent, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(c.accent)); Text(textHelixGardenPlaza2, color = c.fg, fontSize = 14.sp)
                }
                listOf(Triple("Pulse", "$12.40", 0), Triple("Orbit", "$16.80", 1), Triple("Nova XL", "$24.50", 2)).forEach { (n, f, s) ->
                    val on = s == 1
                    Row(Modifier.padding(top = 8.dp).fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp)).background(if (on) c.accent.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent).border(1.dp, if (on) c.accent else c.border, RoundedCornerShape(16.dp)).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        VehicleArt(s, 54.dp); Text("  $n", color = c.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); Text(f, color = c.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            1 -> Box(Modifier.fillMaxWidth(0.88f).aspectRatio(0.9f).clip(RoundedCornerShape(26.dp)).border(1.dp, c.border, RoundedCornerShape(26.dp))) {
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(c.bg)
                    listOf(0.25f, 0.5f, 0.75f).forEach { f -> drawLine(c.street, Offset(0f, size.height * f), Offset(size.width, size.height * f), 5.dp.toPx()); drawLine(c.street, Offset(size.width * f, 0f), Offset(size.width * f, size.height), 5.dp.toPx()) }
                    val p = Path().apply { moveTo(size.width * 0.2f, size.height * 0.75f); cubicTo(size.width * 0.35f, size.height * 0.5f, size.width * 0.5f, size.height * 0.6f, size.width * 0.75f, size.height * 0.2f) }
                    drawPath(p, c.accent, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
                    drawCircle(c.accent, 8.dp.toPx(), Offset(size.width * 0.2f, size.height * 0.75f)); drawCircle(c.fg, 8.dp.toPx(), Offset(size.width * 0.75f, size.height * 0.2f))
                }
                Row(Modifier.align(Alignment.BottomCenter).padding(12.dp).fillMaxWidth().height(60.dp).clip(RoundedCornerShape(18.dp)).background(c.bg).border(1.dp, c.border, RoundedCornerShape(18.dp)).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) { Text(stringResource(R.string.driver_initials), color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    Column(Modifier.weight(1f).padding(start = 10.dp)) { Text(textMiraTanaka, color = c.fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold); Text(textOrbitRn21004Min, color = c.muted, fontSize = 11.sp) }
                    RideIcon(RideIcons.Call, c.accent)
                }
            }
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(Modifier.size(96.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.14f)).border(1.5.dp, c.accent, CircleShape), contentAlignment = Alignment.Center) { RideIcon(RideIcons.Shield, c.accent, size = 44.dp) }
                Chip(RideIcons.Share, textTripShareLive, Modifier.align(Alignment.TopStart).padding(top = 28.dp))
                Chip(RideIcons.Person, textTrustedContacts, Modifier.align(Alignment.CenterEnd).padding(top = 150.dp))
                Chip(RideIcons.Call, textEmergencyHelp, Modifier.align(Alignment.BottomStart).padding(bottom = 28.dp))
            }
        }
    }
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
private fun IntroPreview() = RideNowTheme(darkTheme = true) { IntroScreen({}) }
