package com.ridenow.app.ui.more

import com.ridenow.app.R
import androidx.compose.ui.res.stringResource

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ridenow.app.ui.booking.Vehicles
import com.ridenow.app.ui.booking.money
import com.ridenow.app.ui.components.*
import com.ridenow.app.ui.theme.RideTheme
import kotlinx.coroutines.delay

private const val EmergencyNumber = "112"

/** Map-like backdrop with a bottom card, shared by the booking-state screens. */
@Composable
private fun MapCardScreen(onBack: (() -> Unit)?, card: @Composable ColumnScope.() -> Unit) {
    val c = RideTheme.colors
    if (onBack != null) BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize().background(c.bg)) {
        Canvas(Modifier.fillMaxSize()) { drawCityStreets(c, 0.5f) }
        SheetCard(Modifier.align(Alignment.BottomCenter), content = card)
    }
}

@Composable
private fun RouteStrip(pickup: String, drop: String) {
    val c = RideTheme.colors
    Column(Modifier.padding(top = 14.dp).fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(10.dp).clip(CircleShape).background(c.accent)); Text("  $pickup", color = c.muted, fontSize = 14.sp) }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(10.dp).background(c.fg)); Text("  $drop", color = c.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun VehicleStrip(vehicle: Int, right: String) {
    val textValueValueSeats = stringResource(R.string.text_value_value_seats)

    val c = RideTheme.colors
    val v = Vehicles[vehicle.coerceIn(0, Vehicles.lastIndex)]
    Row(Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(textValueValueSeats.format(v.name, v.seats), color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold); Text(right, color = c.muted, fontSize = 12.sp) }
        Text(money(v.fare), color = c.fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------- No drivers available ----------

@Composable
fun NoDriversScreen(ctx: MoreCtx, onTryAgain: () -> Unit, onSchedule: () -> Unit, onChangeVehicle: () -> Unit, onBack: () -> Unit) {
    val textSearchCurrent = stringResource(R.string.search_current)
    val textNoDriversNearby = stringResource(R.string.text_no_drivers_nearby)
    val textAllVehiclesAreBusyRightNowTryAgainInAMomentOrScheduleYourR = stringResource(R.string.text_all_vehicles_are_busy_right_now_try_again_in_a_moment_or_schedule_your_r)
    val textPickupUnavailable = stringResource(R.string.text_pickup_unavailable)
    val textTryAgain = stringResource(R.string.text_try_again)
    val textScheduleForLater = stringResource(R.string.text_schedule_for_later)
    val textChangeVehicle = stringResource(R.string.text_change_vehicle)

    val c = RideTheme.colors
    MapCardScreen(onBack) {
        RouteStrip(textSearchCurrent, ctx.dest)
        Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(RideIcons.Car, c.danger, 48.dp)
            Column(Modifier.padding(start = 12.dp)) {
                Text(textNoDriversNearby, color = c.fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
            }
        }
        Text(textAllVehiclesAreBusyRightNowTryAgainInAMomentOrScheduleYourR, color = c.muted, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 8.dp))
        VehicleStrip(ctx.vehicle, textPickupUnavailable)
        RideButton(textTryAgain, onTryAgain, Modifier.padding(top = 16.dp))
        RideButton(textScheduleForLater, onSchedule, style = RideButtonStyle.Text)
        RideButton(textChangeVehicle, onChangeVehicle, style = RideButtonStyle.Text)
    }
}

// ---------- Driver cancelled, finding another ----------

@Composable
fun DriverCancelledScreen(ctx: MoreCtx, onFound: () -> Unit, onCancel: () -> Unit) {
    val textSearchCurrent = stringResource(R.string.search_current)
    val textMiraCancelledTheTripYourRequestIsStillActive = stringResource(R.string.text_mira_cancelled_the_trip_your_request_is_still_active)
    val textFindingYouAnotherDriver = stringResource(R.string.text_finding_you_another_driver)
    val textWeKeptYourPickupAndDropOff = stringResource(R.string.text_we_kept_your_pickup_and_drop_off)
    val textPickupInAbout4Min = stringResource(R.string.text_pickup_in_about_4_min)
    val textFindingCancel = stringResource(R.string.finding_cancel)

    val c = RideTheme.colors
    val spin by rememberInfiniteTransition(label = "spin").animateFloat(0f, 360f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "a")
    LaunchedEffect(Unit) { delay(6000); onFound() }
    MapCardScreen(onCancel) {
        RouteStrip(textSearchCurrent, ctx.dest)
        Row(Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.danger.copy(alpha = 0.12f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RideIcon(RideIcons.Alert, c.danger, size = 18.dp)
            Text(textMiraCancelledTheTripYourRequestIsStillActive, color = c.fg, fontSize = 14.sp, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(28.dp).rotate(spin)) { drawArc(c.accent, 0f, 270f, false, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round)) }
            Text(textFindingYouAnotherDriver, color = c.fg, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
        }
        Text(textWeKeptYourPickupAndDropOff, color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
        VehicleStrip(ctx.vehicle, textPickupInAbout4Min)
        RideButton(textFindingCancel, onCancel, Modifier.padding(top = 16.dp), style = RideButtonStyle.Text)
    }
}

// ---------- Driver arrived and waiting ----------

@Composable
fun DriverWaitingScreen(ctx: MoreCtx, onCall: () -> Unit, onMessage: () -> Unit, onCancel: () -> Unit, onStart: () -> Unit) {
    val textArrived = stringResource(R.string.text_arrived)
    val textMiraHasArrived = stringResource(R.string.text_mira_has_arrived)
    val textMeetAtYourPickupPoint = stringResource(R.string.text_meet_at_your_pickup_point)
    val textValueOfFreeWaitingLeft = stringResource(R.string.text_value_of_free_waiting_left)
    val textValueSilver = stringResource(R.string.text_value_silver)
    val textPin = stringResource(R.string.text_pin)
    val textCall = stringResource(R.string.text_call)
    val textMessage = stringResource(R.string.text_message)
    val textStartTrip = stringResource(R.string.text_start_trip)
    val textTripCancelBtn = stringResource(R.string.trip_cancel_btn)
    val textTripCancelTitle = stringResource(R.string.trip_cancel_title)
    val textMiraIsAlreadyAtYourPickupPoint = stringResource(R.string.text_mira_is_already_at_your_pickup_point)
    val textKeepRide = stringResource(R.string.text_keep_ride)

    val c = RideTheme.colors
    val left = rememberCountdown(150)
    var confirm by remember { mutableStateOf(false) }
    val v = Vehicles[ctx.vehicle.coerceIn(0, Vehicles.lastIndex)]
    MapCardScreen(null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(textArrived, color = c.onAccent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(50)).background(c.accent).padding(horizontal = 10.dp, vertical = 4.dp))
            Text("  ${ctx.pickup}", color = c.muted, fontSize = 13.sp)
        }
        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(stringResource(R.string.driver_initials), 52.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(textMiraHasArrived, color = c.fg, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
                Text(textMeetAtYourPickupPoint, color = c.muted, fontSize = 13.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(mmss(left), color = c.accent, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { contentDescription = textValueOfFreeWaitingLeft.format(mmss(left)) })
                Text(stringResource(R.string.trip_waiting), color = c.muted, fontSize = 11.sp)
            }
        }
        Row(Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(stringResource(R.string.sample_vehicle_plate), color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold); Text(textValueSilver.format(v.name), color = c.muted, fontSize = 12.sp) }
            Column(horizontalAlignment = Alignment.End) { Text(textPin, color = c.muted, fontSize = 11.sp, letterSpacing = 1.1.sp); Text(stringResource(R.string.sample_trip_pin), color = c.fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 3.sp) }
        }
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton(RideIcons.Call, textCall, Modifier.weight(1f), onCall)
            ActionButton(RideIcons.Message, textMessage, Modifier.weight(1f), onMessage)
        }
        RideButton(textStartTrip, onStart, Modifier.padding(top = 10.dp))
        RideButton(textTripCancelBtn, { confirm = true }, style = RideButtonStyle.Text)
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false }, title = { Text(textTripCancelTitle) },
        text = { Text(textMiraIsAlreadyAtYourPickupPoint) },
        confirmButton = { TextButton({ confirm = false; onCancel() }) { Text(textTripCancelBtn, color = c.danger) } },
        dismissButton = { TextButton({ confirm = false }) { Text(textKeepRide) } },
    )
}

@Composable
fun ActionButton(icon: List<String>, label: String, modifier: Modifier, onClick: () -> Unit, tint: Color? = null) {
    val c = RideTheme.colors
    Row(
        modifier.height(52.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, c.border, RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) { RideIcon(icon, tint ?: c.accent, size = 20.dp); Text("  $label", color = tint ?: c.fg, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
}

// ---------- Ride cancelled ----------

@Composable
fun RideCancelledScreen(ctx: MoreCtx, reason: String, onBook: () -> Unit, onHome: () -> Unit) {
    val textBookAnotherRide = stringResource(R.string.text_book_another_ride)
    val textBackToHome = stringResource(R.string.text_back_to_home)
    val textRideCancelled = stringResource(R.string.text_ride_cancelled)
    val textYouCanBookAgainAnyTime = stringResource(R.string.text_you_can_book_again_any_time)
    val textTrip8f3k2q = stringResource(R.string.text_trip_8f3k2q)
    val textCancelled = stringResource(R.string.text_cancelled)
    val textSearchCurrent = stringResource(R.string.search_current)
    val textReason = stringResource(R.string.text_reason)

    val c = RideTheme.colors
    BackHandler(onBack = onHome)
    MoreScaffold("", onHome, bottom = { RideButton(textBookAnotherRide, onBook); RideButton(textBackToHome, onHome, style = RideButtonStyle.Text) }) {
        Hero(RideIcons.Close, textRideCancelled, textYouCanBookAgainAnyTime, danger = true)
        GroupCard(Modifier.padding(top = 24.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(textTrip8f3k2q, color = c.muted, fontSize = 11.sp, letterSpacing = 1.1.sp, modifier = Modifier.weight(1f))
                    Text(textCancelled, color = c.danger, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(textSearchCurrent, color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
                Text(ctx.dest, color = c.fg, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
                HorizontalDivider(color = c.line, modifier = Modifier.padding(vertical = 12.dp))
                InfoRow(textReason, reason)
            }
        }
    }
}

// ---------- Change drop-off during a trip ----------

@Composable
fun ChangeDropScreen(ctx: MoreCtx, onBack: () -> Unit, onConfirm: (String) -> Unit) {
    val textAuroraMedicalCentre = stringResource(R.string.text_aurora_medical_centre)
    val textSector3SkylineWay = stringResource(R.string.text_sector_3_skyline_way)
    val textOrbitRingStation = stringResource(R.string.text_orbit_ring_station)
    val textSector1RingRoad = stringResource(R.string.text_sector_1_ring_road)
    val textChangeDropOff = stringResource(R.string.text_change_drop_off)
    val textCurrentlyGoingToValue = stringResource(R.string.text_currently_going_to_value)
    val textConfirmNewDropOff = stringResource(R.string.text_confirm_new_drop_off)
    val textKeepCurrentDropOff = stringResource(R.string.text_keep_current_drop_off)
    val textNewEstimate = stringResource(R.string.text_new_estimate)
    val textFare2 = stringResource(R.string.text_fare_2)
    val textTime = stringResource(R.string.text_time)
    val textValueMinValue = stringResource(R.string.text_value_min_value)
    val textYourDriverWillBeToldAboutTheNewDropOff = stringResource(R.string.text_your_driver_will_be_told_about_the_new_drop_off)

    val c = RideTheme.colors
    val options = listOf(textAuroraMedicalCentre to textSector3SkylineWay, textOrbitRingStation to textSector1RingRoad)
    var pick by rememberSaveable { mutableIntStateOf(0) }
    val extra = listOf(2.10 to 3, 3.40 to 5)[pick]
    MoreScaffold(textChangeDropOff, onBack, subtitle = textCurrentlyGoingToValue.format(ctx.dest), bottom = {
        RideButton(textConfirmNewDropOff, { onConfirm(options[pick].first) }); RideButton(textKeepCurrentDropOff, onBack, style = RideButtonStyle.Text)
    }) {
        GroupCard(Modifier.padding(top = 16.dp)) {
            options.forEachIndexed { i, (n, s) -> if (i > 0) RowDivider(); Column(Modifier.padding(horizontal = 16.dp)) { RadioRow(n, pick == i, { pick = i }, s) } }
        }
        SectionLabel(textNewEstimate)
        GroupCard {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                InfoRow(textFare2, "${money(16.80 + extra.first)}  +${money(extra.first)}", strong = true)
                InfoRow(textTime, textValueMinValue.format(19 + extra.second, extra.second))
            }
        }
        Text(textYourDriverWillBeToldAboutTheNewDropOff, color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
    }
}

// ---------- Emergency call confirmation ----------

@Composable
fun EmergencyCountdownScreen(ctx: MoreCtx, onCancel: () -> Unit) {
    val textCallingInValueSeconds = stringResource(R.string.text_calling_in_value_seconds)
    val textCallingEmergencyServices = stringResource(R.string.text_calling_emergency_services)
    val textYourLiveLocationAndTripDetailsAreSharedWithYourTrustedContact = stringResource(R.string.text_your_live_location_and_trip_details_are_shared_with_your_trusted_contact)
    val textNearMeridianWalkAndHelixAve = stringResource(R.string.text_near_meridian_walk_and_helix_ave)
    val textOrbitRn2100MiraTanaka = stringResource(R.string.text_orbit_rn_2100_mira_tanaka)
    val textCallNow = stringResource(R.string.text_call_now)
    val textPhotoCancel = stringResource(R.string.photo_cancel)

    val c = RideTheme.colors
    val context = LocalContext.current
    val left = rememberCountdown(5)
    var called by remember { mutableStateOf(false) }
    LaunchedEffect(left) { if (left == 0 && !called) { called = true; context.dial(EmergencyNumber); onCancel() } }
    BackHandler(onBack = onCancel)
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(160.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(c.danger.copy(alpha = 0.2f), size.minDimension / 2 - 4.dp.toPx(), style = Stroke(6.dp.toPx()))
                    drawArc(c.danger, -90f, 360f * left / 5f, false, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
                }
                Text("$left", color = c.fg, fontSize = 64.sp, fontWeight = FontWeight.Light, modifier = Modifier.semantics { contentDescription = textCallingInValueSeconds.format(left) })
            }
            Text(textCallingEmergencyServices, color = c.fg, fontSize = 26.sp, fontWeight = FontWeight.Light, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 24.dp).semantics { heading() })
            Text(textYourLiveLocationAndTripDetailsAreSharedWithYourTrustedContact, color = c.muted, fontSize = 14.sp, lineHeight = 21.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
            GroupCard(Modifier.padding(top = 24.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { RideIcon(RideIcons.Pin, c.accent, size = 18.dp); Text(textNearMeridianWalkAndHelixAve, color = c.fg, fontSize = 14.sp) }
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) { RideIcon(RideIcons.Car, c.accent, size = 18.dp); Text(textOrbitRn2100MiraTanaka, color = c.fg, fontSize = 14.sp) }
                }
            }
        }
        RideButton(textCallNow, { if (!called) { called = true; context.dial(EmergencyNumber); onCancel() } })
        RideButton(textPhotoCancel, onCancel, style = RideButtonStyle.Text)
    }
}

// ---------- In-app call (simulated audio screen) ----------

@Composable
fun InAppCallScreen(onEnd: () -> Unit) {
    val textCallViaRidenowNumberStaysPrivate = stringResource(R.string.text_call_via_ridenow_number_stays_private)
    val textMiraTanaka = stringResource(R.string.text_mira_tanaka)
    val textRn2100OrbitSilver = stringResource(R.string.text_rn_2100_orbit_silver)
    val textKeypadIsNotNeededForDriverCalls = stringResource(R.string.text_keypad_is_not_needed_for_driver_calls)
    val textMute = stringResource(R.string.text_mute)
    val textSpeaker = stringResource(R.string.text_speaker)
    val textKeypad = stringResource(R.string.text_keypad)
    val textEndCall = stringResource(R.string.text_end_call)

    val c = RideTheme.colors
    var seconds by remember { mutableIntStateOf(0) }
    var mute by remember { mutableStateOf(false) }
    var speaker by remember { mutableStateOf(false) }
    var keypad by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { while (true) { delay(1000); seconds++ } }
    BackHandler(onBack = onEnd)
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) { RideIcon(RideIcons.Lock, c.muted, size = 14.dp); Text(textCallViaRidenowNumberStaysPrivate, color = c.muted, fontSize = 13.sp) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Avatar(stringResource(R.string.driver_initials), 112.dp)
            Text(textMiraTanaka, color = c.fg, fontSize = 28.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(top = 20.dp).semantics { heading() })
            Text(mmss(seconds), color = c.accent, fontSize = 18.sp, modifier = Modifier.padding(top = 6.dp))
            Text(textRn2100OrbitSilver, color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            if (keypad) Text(textKeypadIsNotNeededForDriverCalls, color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            CallToggle(textMute, RideIcons.Phone, mute) { mute = !mute }
            CallToggle(textSpeaker, RideIcons.Call, speaker) { speaker = !speaker }
            CallToggle(textKeypad, RideIcons.Menu, keypad) { keypad = !keypad }
        }
        Box(Modifier.padding(vertical = 28.dp).size(68.dp).clip(CircleShape).background(c.danger).clickable(role = Role.Button, onClick = onEnd).semantics { contentDescription = textEndCall }, contentAlignment = Alignment.Center) {
            RideIcon(RideIcons.Call, if (c.isDark) Color(0xFF2B0A0A) else Color.White, Modifier.rotate(135f), size = 28.dp)
        }
    }
}

@Composable
private fun CallToggle(label: String, icon: List<String>, on: Boolean, onClick: () -> Unit) {
    val c = RideTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.selectableToggle(on, onClick)) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(if (on) c.accent else c.field).border(1.dp, if (on) c.accent else c.border, CircleShape), contentAlignment = Alignment.Center) { RideIcon(icon, if (on) c.onAccent else c.fg) }
        Text(label, color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

private fun Modifier.selectableToggle(on: Boolean, onClick: () -> Unit): Modifier =
    this.clip(RoundedCornerShape(12.dp)).toggleableCompat(on, onClick)

private fun Modifier.toggleableCompat(on: Boolean, onClick: () -> Unit): Modifier =
    this.clickable(role = Role.Switch, onClick = onClick).semantics { toggleableState = if (on) androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off }

// ---------- Chat with the driver ----------

private class Msg(val text: String, val mine: Boolean, val time: String)

@Composable
fun DriverChatScreen(onBack: () -> Unit) {
    val textIAm2MinutesAwayNearTheHelixAveCorner = stringResource(R.string.text_i_am_2_minutes_away_near_the_helix_ave_corner)
    val textThanksIAmAtThePickupPoint = stringResource(R.string.text_thanks_i_am_at_the_pickup_point)
    val textGreatSeeYouNow = stringResource(R.string.text_great_see_you_now)
    val textSearchBack = stringResource(R.string.search_back)
    val textMiraTanaka = stringResource(R.string.text_mira_tanaka)
    val textArrivingIn1MinOrbitRn2100 = stringResource(R.string.text_arriving_in_1_min_orbit_rn_2100)
    val textPickup128MeridianWalk = stringResource(R.string.text_pickup_128_meridian_walk)
    val textToday938 = stringResource(R.string.text_today_9_38)
    val textIAmAtThePickup = stringResource(R.string.text_i_am_at_the_pickup)
    val textRunning2MinLate = stringResource(R.string.text_running_2_min_late)
    val textPleaseWaitForMe = stringResource(R.string.text_please_wait_for_me)
    val textMessage = stringResource(R.string.text_message)
    val textSend = stringResource(R.string.text_send)

    val c = RideTheme.colors
    val msgs = remember { mutableStateListOf(Msg(textIAm2MinutesAwayNearTheHelixAveCorner, false, "9:38"), Msg(textThanksIAmAtThePickupPoint, true, "9:39"), Msg(textGreatSeeYouNow, false, "9:39")) }
    var draft by remember { mutableStateOf("") }
    val list = rememberLazyListStateCompat()
    val send = { t: String -> if (t.isNotBlank()) { msgs.add(Msg(t.trim(), true, java.time.LocalTime.now().let { "%d:%02d".format(it.hour, it.minute) })); draft = "" } }
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = textSearchBack }, contentAlignment = Alignment.Center) { RideIcon(RideIcons.Back, c.fg, size = 22.dp) }
            Avatar(stringResource(R.string.driver_initials), 40.dp)
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(textMiraTanaka, color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
                Text(textArrivingIn1MinOrbitRn2100, color = c.muted, fontSize = 12.sp)
            }
        }
        HorizontalDivider(color = c.line)
        Column(Modifier.weight(1f).verticalScroll(list).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(textPickup128MeridianWalk, color = c.muted, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text(textToday938, color = c.muted, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            msgs.forEach { m ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start) {
                    Text(m.text, color = if (m.mine) c.onAccent else c.fg, fontSize = 15.sp, lineHeight = 21.sp,
                        modifier = Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(18.dp)).background(if (m.mine) c.accent else c.field).then(if (m.mine) Modifier else Modifier.border(1.dp, c.border, RoundedCornerShape(18.dp))).padding(horizontal = 14.dp, vertical = 10.dp))
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(textIAmAtThePickup, textRunning2MinLate, textPleaseWaitForMe).forEach { Chip(it, false, { send(it) }) }
        }
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            RideTextField(draft, { draft = it.take(300) }, textMessage, RideIcons.Message, Modifier.weight(1f))
            Box(Modifier.padding(start = 8.dp).size(52.dp).clip(CircleShape).background(if (draft.isBlank()) c.line else c.accent).clickable(enabled = draft.isNotBlank(), role = Role.Button) { send(draft) }.semantics { contentDescription = textSend }, contentAlignment = Alignment.Center) {
                RideIcon(RideIcons.Back, if (draft.isBlank()) c.muted else c.onAccent, Modifier.rotate(180f))
            }
        }
    }
}

@Composable private fun rememberLazyListStateCompat() = rememberScrollState()

// ---------- Trusted contact tracking page ----------

@Composable
fun TrustedTrackingScreen(ctx: MoreCtx, onBack: () -> Unit, onReport: () -> Unit) {
    val textSearchBack = stringResource(R.string.search_back)
    val textLive = stringResource(R.string.text_live)
    val textSharedTrip = stringResource(R.string.text_shared_trip)
    val textValueIsOnTheWayToValue = stringResource(R.string.text_value_is_on_the_way_to_value)
    val textArrivingIn = stringResource(R.string.text_arriving_in)
    val text12Min = stringResource(R.string.text_text_12_min)
    val textDistanceLeft = stringResource(R.string.text_distance_left)
    val text61Km = stringResource(R.string.text_text_6_1_km)
    val textMiraTanaka = stringResource(R.string.text_mira_tanaka)
    val textOrbitSilver = stringResource(R.string.text_orbit_silver)
    val textCallValue = stringResource(R.string.text_call_value)
    val textReportASafetyConcern = stringResource(R.string.text_report_a_safety_concern)

    val c = RideTheme.colors
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = textSearchBack }, contentAlignment = Alignment.Center) { RideIcon(RideIcons.Back, c.fg, size = 22.dp) }
            Text(stringResource(R.string.sample_trip_link_display), color = c.muted, fontSize = 13.sp, modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).background(c.field).padding(horizontal = 14.dp, vertical = 8.dp))
        }
        Box(Modifier.fillMaxWidth().height(220.dp)) {
            Canvas(Modifier.fillMaxSize()) { drawCityStreets(c, 0.6f) }
            Row(Modifier.align(Alignment.TopStart).padding(12.dp).clip(RoundedCornerShape(50)).background(c.bg.copy(alpha = 0.85f)).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(c.accent)); Text(textLive, color = c.fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text(textSharedTrip, color = c.muted, fontSize = 11.sp, letterSpacing = 1.1.sp)
            Text(textValueIsOnTheWayToValue.format(ctx.name.substringBefore(' '), ctx.dest), color = c.fg, fontSize = 24.sp, fontWeight = FontWeight.Light, lineHeight = 30.sp, modifier = Modifier.padding(top = 6.dp).semantics { heading() })
            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat(textArrivingIn, text12Min, Modifier.weight(1f)); Stat(textDistanceLeft, text61Km, Modifier.weight(1f))
            }
            GroupCard(Modifier.padding(top = 14.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(stringResource(R.string.driver_initials)); Column(Modifier.padding(start = 12.dp).weight(1f)) { Text(textMiraTanaka, color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold); Text(textOrbitSilver, color = c.muted, fontSize = 12.sp) }
                    Text(stringResource(R.string.sample_vehicle_plate), color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            ActionButton(RideIcons.Call, textCallValue.format(ctx.name.substringBefore(' ')), Modifier.padding(top = 14.dp).fillMaxWidth(), { context.dial(ctx.phone.filter { it.isDigit() || it == '+' }) })
            RideButton(textReportASafetyConcern, onReport, Modifier.padding(top = 10.dp), style = RideButtonStyle.Text)
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    val c = RideTheme.colors
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(14.dp)) {
        Text(label, color = c.muted, fontSize = 11.sp, letterSpacing = 1.1.sp); Text(value, color = c.fg, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
    }
}

// ---------- Android lock-screen live trip notification ----------

/** Ongoing notification with progress and Call / Message actions. Appears on the lock screen too. */
object LiveTripNotification {
    private const val CHANNEL = "live_trip"
    private const val ID = 2100

    fun show(context: Context, minutes: Int, driver: String = "Mira", plate: String = "RN 2100", pin: String = "4821") {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.text_live_trip), NotificationManager.IMPORTANCE_HIGH).apply { lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC; description = context.getString(R.string.text_shows_your_driver_and_trip_progress) })
        fun intent(uri: String, req: Int) = PendingIntent.getActivity(context, req, Intent(if (uri.startsWith("tel")) Intent.ACTION_DIAL else Intent.ACTION_SENDTO, Uri.parse(uri)), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE) }
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(context.applicationInfo.icon.takeIf { it != 0 } ?: android.R.drawable.ic_dialog_info)
            .setContentTitle(if (minutes <= 0) context.getString(R.string.text_your_driver_has_arrived) else context.getString(R.string.text_driver_arriving_in_value_min, minutes))
            .setContentText(context.getString(R.string.text_value_orbit_value_pin_value, driver, plate, pin))
            .setCategory(NotificationCompat.CATEGORY_PROGRESS).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true).setOnlyAlertOnce(true).setProgress(10, (10 - minutes).coerceIn(0, 10), false)
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.text_call), intent("tel:", 1)).addAction(0, context.getString(R.string.text_message), intent("smsto:", 2))
            .build()
        nm.notify(ID, n)
    }

    fun cancel(context: Context) { (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(ID) }
}

/** Preview of the notification as it looks on the lock screen; also posts the real one. */
@Composable
fun LockScreenTripScreen(onBack: () -> Unit) {
    val textLiveTripNotification = stringResource(R.string.text_live_trip_notification)
    val textAPersistentNotificationKeepsYourTripOnTheLockScreen = stringResource(R.string.text_a_persistent_notification_keeps_your_trip_on_the_lock_screen)
    val textRidenowNow = stringResource(R.string.text_ridenow_now)
    val textDriverArrivingIn4Min = stringResource(R.string.text_driver_arriving_in_4_min)
    val textMiraOrbitRn2100Pin4821 = stringResource(R.string.text_mira_orbit_rn_2100_pin_4821)
    val textCall = stringResource(R.string.text_call)
    val textMessage = stringResource(R.string.text_message)
    val textOngoingNotificationWithLiveProgressAndActions = stringResource(R.string.text_ongoing_notification_with_live_progress_and_actions)
    val textRemoveNotification = stringResource(R.string.text_remove_notification)

    val c = RideTheme.colors
    val context = LocalContext.current
    LaunchedEffect(Unit) { LiveTripNotification.show(context, 4) }
    MoreScaffold(textLiveTripNotification, onBack, subtitle = textAPersistentNotificationKeepsYourTripOnTheLockScreen) {
        Column(Modifier.padding(top = 20.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(24.dp)).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(textRidenowNow, color = c.muted, fontSize = 12.sp) }
            Text(textDriverArrivingIn4Min, color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
            Text(textMiraOrbitRn2100Pin4821, color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
            Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.line)) { Box(Modifier.fillMaxWidth(0.6f).height(4.dp).background(c.accent)) }
            Row(Modifier.padding(top = 12.dp)) {
                Text(textCall, color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.heightIn(min = 40.dp).padding(end = 24.dp))
                Text(textMessage, color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Text(textOngoingNotificationWithLiveProgressAndActions, color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
        RideButton(textRemoveNotification, { LiveTripNotification.cancel(context) }, Modifier.padding(top = 16.dp), style = RideButtonStyle.Text)
    }
}
