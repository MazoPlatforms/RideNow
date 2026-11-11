package com.ridenow.app.ui.post

import com.ridenow.app.R
import androidx.compose.ui.res.stringResource

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridenow.app.ui.components.RideButton
import com.ridenow.app.ui.components.RideButtonStyle
import com.ridenow.app.ui.components.RideIcon
import com.ridenow.app.ui.components.RideIcons
import com.ridenow.app.ui.theme.BrandYellow
import com.ridenow.app.ui.theme.Ink
import com.ridenow.app.ui.theme.RideNowTheme
import com.ridenow.app.ui.theme.RideTheme
import com.ridenow.app.ui.util.entrance

private class Category(val icon: List<String>, val title: String, val hint: String, val options: List<String>)



/** Report a trip problem: choose a category, add details, submit. [onDone] is called after submitting or leaving. */
@Composable
fun ReportProblemScreen(onBack: () -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val textDriverBehavior = stringResource(R.string.text_driver_behavior)
    val textRudeUnsafeOrInappropriateConduct = stringResource(R.string.text_rude_unsafe_or_inappropriate_conduct)
    val textUnsafeDriving = stringResource(R.string.text_unsafe_driving)
    val textRudeOrAggressive = stringResource(R.string.text_rude_or_aggressive)
    val textMadeMeUncomfortable = stringResource(R.string.text_made_me_uncomfortable)
    val textSomethingElse = stringResource(R.string.text_something_else)
    val textIncorrectRoute = stringResource(R.string.text_incorrect_route)
    val textTheDriverTookADifferentRoute = stringResource(R.string.text_the_driver_took_a_different_route)
    val textTookALongerRoute = stringResource(R.string.text_took_a_longer_route)
    val textIAskedForADifferentRoute = stringResource(R.string.text_i_asked_for_a_different_route)
    val textRouteDidNotMatchTheApp = stringResource(R.string.text_route_did_not_match_the_app)
    val textPaymentIssue = stringResource(R.string.text_payment_issue)
    val textWrongChargeOrFareDispute = stringResource(R.string.text_wrong_charge_or_fare_dispute)
    val textPaidMoreThanTheEstimate = stringResource(R.string.text_paid_more_than_the_estimate)
    val textPaidForACancelledRide = stringResource(R.string.text_paid_for_a_cancelled_ride)
    val textWrongAmountCollected = stringResource(R.string.text_wrong_amount_collected)
    val textReasonOther = stringResource(R.string.reason_other)
    val textPickupOrDropOffIssue = stringResource(R.string.text_pickup_or_drop_off_issue)
    val textWrongSpotOrTroubleMeeting = stringResource(R.string.text_wrong_spot_or_trouble_meeting)
    val textDriverCouldNotFindMe = stringResource(R.string.text_driver_could_not_find_me)
    val textDriverStoppedFarAway = stringResource(R.string.text_driver_stopped_far_away)
    val textDroppedOffInTheWrongPlace = stringResource(R.string.text_dropped_off_in_the_wrong_place)
    val textTheSpotFeltUnsafe = stringResource(R.string.text_the_spot_felt_unsafe)
    val textLostItem = stringResource(R.string.text_lost_item)
    val textLeftSomethingInTheVehicle2 = stringResource(R.string.text_left_something_in_the_vehicle_2)
    val textPhone = stringResource(R.string.text_phone)
    val textWallet = stringResource(R.string.text_wallet)
    val textBag = stringResource(R.string.text_bag)
    val textKeys = stringResource(R.string.text_keys)
    val textVehicleIssue = stringResource(R.string.text_vehicle_issue)
    val textCleanlinessOrCondition = stringResource(R.string.text_cleanliness_or_condition)
    val textDirtyInterior = stringResource(R.string.text_dirty_interior)
    val textBadSmell = stringResource(R.string.text_bad_smell)
    val textDamagedSeatOrDoor = stringResource(R.string.text_damaged_seat_or_door)
    val textNotTheCarShownInTheApp = stringResource(R.string.text_not_the_car_shown_in_the_app)
    val textOtherIssue = stringResource(R.string.text_other_issue)
    val textSomethingElseWentWrong = stringResource(R.string.text_something_else_went_wrong)
    val textSearchBack = stringResource(R.string.search_back)
    val textWhatWentWrong2 = stringResource(R.string.text_what_went_wrong_2)
    val textChooseTheClosestMatchForYourTrip = stringResource(R.string.text_choose_the_closest_match_for_your_trip)
    val textInAnEmergencyOpenTheSafetyCenter = stringResource(R.string.text_in_an_emergency_open_the_safety_center)
    val textTellUsWhatHappened = stringResource(R.string.text_tell_us_what_happened)
    val textSelectAllThatApply = stringResource(R.string.text_select_all_that_apply)
    val textDescribeTheIssue = stringResource(R.string.text_describe_the_issue)
    val textAddDetailsOptional = stringResource(R.string.text_add_details_optional)
    val textSubmitReport = stringResource(R.string.text_submit_report)

    val Categories = listOf(
        Category(RideIcons.Person, textDriverBehavior, textRudeUnsafeOrInappropriateConduct, listOf(textUnsafeDriving, textRudeOrAggressive, textMadeMeUncomfortable, textSomethingElse)),
        Category(RideIcons.Map, textIncorrectRoute, textTheDriverTookADifferentRoute, listOf(textTookALongerRoute, textIAskedForADifferentRoute, textRouteDidNotMatchTheApp)),
        Category(RideIcons.Card, textPaymentIssue, textWrongChargeOrFareDispute, listOf(textPaidMoreThanTheEstimate, textPaidForACancelledRide, textWrongAmountCollected, textReasonOther)),
        Category(RideIcons.Pin, textPickupOrDropOffIssue, textWrongSpotOrTroubleMeeting, listOf(textDriverCouldNotFindMe, textDriverStoppedFarAway, textDroppedOffInTheWrongPlace, textTheSpotFeltUnsafe)),
        Category(listOf("M4 7h12l-1 10H5z", "M7.5 7V5.5a2.5 2.5 0 0 1 5 0V7"), textLostItem, textLeftSomethingInTheVehicle2, listOf(textPhone, textWallet, textBag, textKeys, textSomethingElse)),
        Category(RideIcons.Car, textVehicleIssue, textCleanlinessOrCondition, listOf(textDirtyInterior, textBadSmell, textDamagedSeatOrDoor, textNotTheCarShownInTheApp)),
        Category(RideIcons.Flag, textOtherIssue, textSomethingElseWentWrong, emptyList()),
    )

    val c = RideTheme.colors
    var cat by rememberSaveable { mutableIntStateOf(-1) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    var picked by remember { mutableStateOf(setOf<String>()) }
    var details by rememberSaveable { mutableStateOf("") }
    BackHandler { if (submitted) onDone() else if (cat >= 0) { cat = -1; picked = emptySet() } else onBack() }

    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 20.dp)) {
        if (submitted) { Submitted(onDone); return@Column }
        Box(Modifier.offset(x = (-12).dp).size(48.dp).clip(CircleShape).clickable(role = Role.Button) { if (cat >= 0) { cat = -1; picked = emptySet() } else onBack() }.semantics { contentDescription = textSearchBack }, contentAlignment = Alignment.Center) {
            RideIcon(RideIcons.Back, c.fg, size = 22.dp)
        }
        if (cat < 0) {
            Text(textWhatWentWrong2, color = c.fg, fontSize = 28.sp, fontWeight = FontWeight.Light, modifier = Modifier.semantics { heading() }.entrance(0))
            Text(textChooseTheClosestMatchForYourTrip, color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).entrance(80)) {
                Categories.forEachIndexed { i, k ->
                    Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { cat = i }) {
                        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(36.dp).clip(CircleShape).border(1.dp, c.border, CircleShape), contentAlignment = Alignment.Center) { RideIcon(k.icon, c.accent, size = 18.dp) }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) { Text(k.title, color = c.fg, fontSize = 15.sp); Text(k.hint, color = c.muted, fontSize = 12.sp) }
                            RideIcon(listOf("M7 4l6 6-6 6"), c.muted, size = 18.dp)
                        }
                        HorizontalDivider(color = c.line)
                    }
                }
            }
            Row(Modifier.padding(vertical = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                RideIcon(RideIcons.Shield, c.accent); Spacer(Modifier.width(12.dp))
                Text(textInAnEmergencyOpenTheSafetyCenter, color = c.muted, fontSize = 13.sp)
            }
        } else {
            val k = Categories[cat]
            Text(k.title, color = c.fg, fontSize = 28.sp, fontWeight = FontWeight.Light, modifier = Modifier.semantics { heading() })
            Text(if (k.options.isEmpty()) textTellUsWhatHappened else textSelectAllThatApply, color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                if (k.options.isNotEmpty()) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(20.dp))) {
                    k.options.forEachIndexed { i, o ->
                        val on = o in picked
                        if (i > 0) HorizontalDivider(color = c.line)
                        Row(Modifier.fillMaxWidth().height(56.dp).clickable(role = Role.Checkbox) { picked = if (on) picked - o else picked + o }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(o, color = c.fg, fontSize = 15.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
                            Box(Modifier.size(22.dp).clip(CircleShape).then(if (on) Modifier.background(BrandYellow) else Modifier.border(1.5.dp, c.muted, CircleShape)), contentAlignment = Alignment.Center) { if (on) RideIcon(RideIcons.Check, Ink, size = 14.dp) }
                        }
                    }
                }
                BasicTextField(
                    details, { details = it.take(500) }, textStyle = TextStyle(color = c.fg, fontSize = 15.sp), cursorBrush = SolidColor(c.accent),
                    modifier = Modifier.padding(top = 14.dp).fillMaxWidth().height(if (k.options.isEmpty()) 160.dp else 90.dp),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(14.dp)) {
                            if (details.isEmpty()) Text(if (k.options.isEmpty()) textDescribeTheIssue else textAddDetailsOptional, color = c.muted, fontSize = 15.sp)
                            inner()
                        }
                    },
                )
            }
            RideButton(textSubmitReport, { submitted = true }, enabled = picked.isNotEmpty() || details.isNotBlank(), modifier = Modifier.padding(vertical = 12.dp))
        }
    }
}

@Composable
private fun Submitted(onDone: () -> Unit) {
    val textReportSubmitted = stringResource(R.string.text_report_submitted)
    val textThankYouWeWillReviewItAndReplyInTheApp = stringResource(R.string.text_thank_you_we_will_review_it_and_reply_in_the_app)
    val textReportNumber = stringResource(R.string.text_report_number)
    val textDone = stringResource(R.string.text_done)

    val c = RideTheme.colors
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(88.dp).clip(CircleShape).background(BrandYellow), contentAlignment = Alignment.Center) { RideIcon(RideIcons.Check, Ink, size = 40.dp) }
        Text(textReportSubmitted, color = c.fg, fontSize = 28.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(top = 22.dp).semantics { heading() })
        Text(textThankYouWeWillReviewItAndReplyInTheApp, color = c.muted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        Column(Modifier.padding(top = 22.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(16.dp)) {
            Text(textReportNumber, color = c.muted, fontSize = 11.sp, letterSpacing = 1.1.sp)
            Text(stringResource(R.string.sample_report_number), color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.weight(1f))
        RideButton(textDone, onDone, modifier = Modifier.padding(bottom = 12.dp))
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ReportPreview() = RideNowTheme(darkTheme = true) { ReportProblemScreen({}, {}) }
