package com.ridenow.app.ui.more

import com.ridenow.app.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import com.ridenow.app.ui.components.*
import com.ridenow.app.ui.theme.RideTheme

private class Article(val title: String, val intro: String, val steps: List<String>)

@Composable
private fun helpTopics(): Map<String, List<Article>> {
    val textSignUpInUnderAMinute = stringResource(R.string.text_sign_up_in_under_a_minute)
    val textEnterYourPhoneNumber = stringResource(R.string.text_enter_your_phone_number)
    val textTypeThe6DigitCodeWeSendYou = stringResource(R.string.text_type_the_6_digit_code_we_send_you)
    val textAddYourNameAndDetails = stringResource(R.string.text_add_your_name_and_details)
    val textAllowLocationSoWeCanFindYourPickup = stringResource(R.string.text_allow_location_so_we_can_find_your_pickup)
    val textFromTheHomeScreenToADriver = stringResource(R.string.text_from_the_home_screen_to_a_driver)
    val textTapWhereToAndChooseADestination = stringResource(R.string.text_tap_where_to_and_choose_a_destination)
    val textPickAVehicle = stringResource(R.string.text_pick_a_vehicle)
    val textReviewTheFareAndConfirm = stringResource(R.string.text_review_the_fare_and_confirm)
    val textWatchYourDriverArriveOnTheMap = stringResource(R.string.text_watch_your_driver_arrive_on_the_map)
    val textWhatMakesUpYourFare = stringResource(R.string.text_what_makes_up_your_fare)
    val textTheFareIsABaseFarePlusDistanceAndTime = stringResource(R.string.text_the_fare_is_a_base_fare_plus_distance_and_time)
    val textTaxesAndFeesAreAddedAtTheEnd = stringResource(R.string.text_taxes_and_fees_are_added_at_the_end)
    val textFaresCanRiseWhenDemandIsHigh = stringResource(R.string.text_fares_can_rise_when_demand_is_high)
    val textYouAlwaysSeeTheEstimateBeforeYouConfirm = stringResource(R.string.text_you_always_see_the_estimate_before_you_confirm)
    val textKeepYourDetailsUpToDate = stringResource(R.string.text_keep_your_details_up_to_date)
    val textOpenTheMenuAndTapProfile = stringResource(R.string.text_open_the_menu_and_tap_profile)
    val textTapEditProfile = stringResource(R.string.text_tap_edit_profile)
    val textChangeYourDetails = stringResource(R.string.text_change_your_details)
    val textTapSave = stringResource(R.string.text_tap_save)
    val textMoveToANewNumberSafely = stringResource(R.string.text_move_to_a_new_number_safely)
    val textOpenSettingsThenPrivacySecurity = stringResource(R.string.text_open_settings_then_privacy_security)
    val textTapChangePhoneNumber = stringResource(R.string.text_tap_change_phone_number)
    val textEnterTheNewNumberAndTheCodeWeSend = stringResource(R.string.text_enter_the_new_number_and_the_code_we_send)
    val textYourOldNumberStaysActiveUntilYouVerify = stringResource(R.string.text_your_old_number_stays_active_until_you_verify)
    val textRemoveYourDataForGood = stringResource(R.string.text_remove_your_data_for_good)
    val textFinishAnyPendingRidesOrPayments = stringResource(R.string.text_finish_any_pending_rides_or_payments)
    val textOpenPrivacySecurityAndTapDeleteAccount = stringResource(R.string.text_open_privacy_security_and_tap_delete_account)
    val textEnterTheCodeWeSendYou = stringResource(R.string.text_enter_the_code_we_send_you)
    val textConfirmTheDeletion = stringResource(R.string.text_confirm_the_deletion)
    val textRidenowRidesArePaidInCash = stringResource(R.string.text_ridenow_rides_are_paid_in_cash)
    val textCheckTheAmountDueAtTheEndOfYourTrip = stringResource(R.string.text_check_the_amount_due_at_the_end_of_your_trip)
    val textHandTheCashToYourDriver = stringResource(R.string.text_hand_the_cash_to_your_driver)
    val textWaitForYourDriverToConfirmPayment = stringResource(R.string.text_wait_for_your_driver_to_confirm_payment)
    val textFindAnyTripReceipt = stringResource(R.string.text_find_any_trip_receipt)
    val textOpenYourRides = stringResource(R.string.text_open_your_rides)
    val textTapTheTrip = stringResource(R.string.text_tap_the_trip)
    val textTapDownloadReceiptToSaveAPdf = stringResource(R.string.text_tap_download_receipt_to_save_a_pdf)
    val textTellUsAboutAWrongCharge = stringResource(R.string.text_tell_us_about_a_wrong_charge)
    val textOpenTheTripInYourRides = stringResource(R.string.text_open_the_trip_in_your_rides)
    val textTapReportATripProblem = stringResource(R.string.text_tap_report_a_trip_problem)
    val textChoosePaymentIssueAndDescribeIt = stringResource(R.string.text_choose_payment_issue_and_describe_it)
    val textGetARideNow = stringResource(R.string.text_get_a_ride_now)
    val textTapWhereToOnTheHomeScreen = stringResource(R.string.text_tap_where_to_on_the_home_screen)
    val textChooseADestination = stringResource(R.string.text_choose_a_destination)
    val textReviewTheDetailsAndConfirm = stringResource(R.string.text_review_the_details_and_confirm)
    val textPlanYourPickupAhead = stringResource(R.string.text_plan_your_pickup_ahead)
    val textTapLaterOnTheHomeScreen = stringResource(R.string.text_tap_later_on_the_home_screen)
    val textPickADateAndTimeForYourPickup = stringResource(R.string.text_pick_a_date_and_time_for_your_pickup)
    val textChooseADestinationAndAVehicle = stringResource(R.string.text_choose_a_destination_and_a_vehicle)
    val textCancelBeforeYourDriverArrives = stringResource(R.string.text_cancel_before_your_driver_arrives)
    val textOpenYourTrip = stringResource(R.string.text_open_your_trip)
    val textTapCancelRide = stringResource(R.string.text_tap_cancel_ride)
    val textChooseAReasonAndConfirm = stringResource(R.string.text_choose_a_reason_and_confirm)
    val textUpdateWhereYouAreGoing = stringResource(R.string.text_update_where_you_are_going)
    val textOpenYourTripWhileItIsInProgress = stringResource(R.string.text_open_your_trip_while_it_is_in_progress)
    val textTapChangeDropOff = stringResource(R.string.text_tap_change_drop_off)
    val textChooseTheNewPlaceAndCheckTheNewEstimate = stringResource(R.string.text_choose_the_new_place_and_check_the_new_estimate)
    val textConfirmTheChange = stringResource(R.string.text_confirm_the_change)
    val textSendARideToSomeoneElse = stringResource(R.string.text_send_a_ride_to_someone_else)
    val textChooseBookForAFriendWhenYouSetYourPickup = stringResource(R.string.text_choose_book_for_a_friend_when_you_set_your_pickup)
    val textPickAContact = stringResource(R.string.text_pick_a_contact)
    val textTheyGetTheDriverAndTripDetails = stringResource(R.string.text_they_get_the_driver_and_trip_details)
    val textLetPeopleFollowYourRide = stringResource(R.string.text_let_people_follow_your_ride)
    val textTapShare = stringResource(R.string.text_tap_share)
    val textPickATrustedContact = stringResource(R.string.text_pick_a_trusted_contact)
    val textTheySeeALiveTrackingPage = stringResource(R.string.text_they_see_a_live_tracking_page)
    val textChooseWhoIsToldInAnEmergency = stringResource(R.string.text_choose_who_is_told_in_an_emergency)
    val textOpenTheSafetyCenter = stringResource(R.string.text_open_the_safety_center)
    val textTapTrustedContacts = stringResource(R.string.text_tap_trusted_contacts)
    val textAddAContactFromYourPhone = stringResource(R.string.text_add_a_contact_from_your_phone)
    val textGetHelpFast = stringResource(R.string.text_get_help_fast)
    val textTapTheSafetyButtonDuringATrip = stringResource(R.string.text_tap_the_safety_button_during_a_trip)
    val textTapEmergency = stringResource(R.string.text_tap_emergency)
    val textYourLocationIsSharedWithYourTrustedContacts = stringResource(R.string.text_your_location_is_shared_with_your_trusted_contacts)
    val textLeftSomethingInTheVehicle = stringResource(R.string.text_left_something_in_the_vehicle)
    val textChooseLostItemAndDescribeIt = stringResource(R.string.text_choose_lost_item_and_describe_it)
    val textReachYourDriverAfterATrip = stringResource(R.string.text_reach_your_driver_after_a_trip)
    val textUseCallOrMessageInTheFirstHourAfterTheTrip = stringResource(R.string.text_use_call_or_message_in_the_first_hour_after_the_trip)
    val textHowWeFollowUp = stringResource(R.string.text_how_we_follow_up)
    val textWeContactTheDriverForYou = stringResource(R.string.text_we_contact_the_driver_for_you)
    val textYouGetANotificationWithTheResult = stringResource(R.string.text_you_get_a_notification_with_the_result)
    val textIfFoundYouAgreeOnAPickupSpot = stringResource(R.string.text_if_found_you_agree_on_a_pickup_spot)

    return linkedMapOf(
    "Getting started" to listOf(
        Article("Create your account", textSignUpInUnderAMinute, listOf(textEnterYourPhoneNumber, textTypeThe6DigitCodeWeSendYou, textAddYourNameAndDetails, textAllowLocationSoWeCanFindYourPickup)),
        Article("Book your first ride", textFromTheHomeScreenToADriver, listOf(textTapWhereToAndChooseADestination, textPickAVehicle, textReviewTheFareAndConfirm, textWatchYourDriverArriveOnTheMap)),
        Article("Understand fares", textWhatMakesUpYourFare, listOf(textTheFareIsABaseFarePlusDistanceAndTime, textTaxesAndFeesAreAddedAtTheEnd, textFaresCanRiseWhenDemandIsHigh, textYouAlwaysSeeTheEstimateBeforeYouConfirm)),
    ),
    "Account & profile" to listOf(
        Article("Edit your profile", textKeepYourDetailsUpToDate, listOf(textOpenTheMenuAndTapProfile, textTapEditProfile, textChangeYourDetails, textTapSave)),
        Article("Change your phone number", textMoveToANewNumberSafely, listOf(textOpenSettingsThenPrivacySecurity, textTapChangePhoneNumber, textEnterTheNewNumberAndTheCodeWeSend, textYourOldNumberStaysActiveUntilYouVerify)),
        Article("Delete your account", textRemoveYourDataForGood, listOf(textFinishAnyPendingRidesOrPayments, textOpenPrivacySecurityAndTapDeleteAccount, textEnterTheCodeWeSendYou, textConfirmTheDeletion)),
    ),
    "Payments & wallet" to listOf(
        Article("Pay in cash", textRidenowRidesArePaidInCash, listOf(textCheckTheAmountDueAtTheEndOfYourTrip, textHandTheCashToYourDriver, textWaitForYourDriverToConfirmPayment)),
        Article("Read your receipt", textFindAnyTripReceipt, listOf(textOpenYourRides, textTapTheTrip, textTapDownloadReceiptToSaveAPdf)),
        Article("Report a payment problem", textTellUsAboutAWrongCharge, listOf(textOpenTheTripInYourRides, textTapReportATripProblem, textChoosePaymentIssueAndDescribeIt)),
    ),
    "Ride bookings" to listOf(
        Article("How to book a ride", textGetARideNow, listOf(textTapWhereToOnTheHomeScreen, textChooseADestination, textPickAVehicle, textReviewTheDetailsAndConfirm)),
        Article("How to schedule a ride", textPlanYourPickupAhead, listOf(textTapLaterOnTheHomeScreen, textPickADateAndTimeForYourPickup, textChooseADestinationAndAVehicle, textReviewTheDetailsAndConfirm)),
        Article("How to cancel a ride", textCancelBeforeYourDriverArrives, listOf(textOpenYourTrip, textTapCancelRide, textChooseAReasonAndConfirm)),
        Article("Change your drop-off", textUpdateWhereYouAreGoing, listOf(textOpenYourTripWhileItIsInProgress, textTapChangeDropOff, textChooseTheNewPlaceAndCheckTheNewEstimate, textConfirmTheChange)),
        Article("Book for a friend", textSendARideToSomeoneElse, listOf(textChooseBookForAFriendWhenYouSetYourPickup, textPickAContact, textTheyGetTheDriverAndTripDetails)),
    ),
    "Safety & security" to listOf(
        Article("Share your trip", textLetPeopleFollowYourRide, listOf(textOpenYourTrip, textTapShare, textPickATrustedContact, textTheySeeALiveTrackingPage)),
        Article("Add trusted contacts", textChooseWhoIsToldInAnEmergency, listOf(textOpenTheSafetyCenter, textTapTrustedContacts, textAddAContactFromYourPhone)),
        Article("Contact emergency services", textGetHelpFast, listOf(textTapTheSafetyButtonDuringATrip, textTapEmergency, textYourLocationIsSharedWithYourTrustedContacts)),
    ),
    "Lost items" to listOf(
        Article("Report a lost item", textLeftSomethingInTheVehicle, listOf(textOpenTheTripInYourRides, textTapReportATripProblem, textChooseLostItemAndDescribeIt)),
        Article("Contact your driver", textReachYourDriverAfterATrip, listOf(textOpenTheTripInYourRides, textUseCallOrMessageInTheFirstHourAfterTheTrip)),
        Article("What happens next", textHowWeFollowUp, listOf(textWeContactTheDriverForYou, textYouGetANotificationWithTheResult, textIfFoundYouAgreeOnAPickupSpot)),
    ),
)
}



@Composable
fun HelpCenterScreen(onBack: () -> Unit, open: (String) -> Unit) {
    val textHelpCenter = stringResource(R.string.text_help_center)
    val textFindAnswersToCommonQuestions = stringResource(R.string.text_find_answers_to_common_questions)
    val textSearchHelp = stringResource(R.string.text_search_help)
    val textBrowseByTopic = stringResource(R.string.text_browse_by_topic)
    val textTopArticles = stringResource(R.string.text_top_articles)
    val textResults = stringResource(R.string.text_results)
    val textNoArticlesMatchYourSearch = stringResource(R.string.text_no_articles_match_your_search)

    val Topics = helpTopics()
    val AllArticles = Topics.values.flatten()
    val c = RideTheme.colors
    var query by rememberSaveable { mutableStateOf("") }
    val hits = AllArticles.filter { localizedLabel(it.title).contains(query.trim(), ignoreCase = true) }
    MoreScaffold(textHelpCenter, onBack, subtitle = textFindAnswersToCommonQuestions) {
        RideTextField(query, { query = it.take(40) }, textSearchHelp, RideIcons.Search, Modifier.padding(top = 16.dp))
        if (query.isBlank()) {
            SectionLabel(textBrowseByTopic)
            GroupCard { Topics.keys.forEachIndexed { i, t -> if (i > 0) RowDivider(); NavRow(RideIcons.Book, localizedLabel(t), { open("Help topic:$t") }) } }
            SectionLabel(textTopArticles)
            GroupCard { listOf("How to schedule a ride", "How to cancel a ride").forEachIndexed { i, t -> if (i > 0) RowDivider(); NavRow(RideIcons.Doc, localizedLabel(t), { open("Help article:$t") }) } }
        } else {
            SectionLabel(textResults)
            if (hits.isEmpty()) Text(textNoArticlesMatchYourSearch, color = c.muted, fontSize = 14.sp)
            else GroupCard { hits.forEachIndexed { i, a -> if (i > 0) RowDivider(); NavRow(RideIcons.Doc, localizedLabel(a.title), { open("Help article:${a.title}") }) } }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun HelpTopicScreen(topic: String, onBack: () -> Unit, open: (String) -> Unit) {
    val textBookScheduleAndCancelRides = stringResource(R.string.text_book_schedule_and_cancel_rides)
    val textLearnTheBasics = stringResource(R.string.text_learn_the_basics)
    val textManageYourDetailsAndSecurity = stringResource(R.string.text_manage_your_details_and_security)
    val textCashPaymentsAndReceipts = stringResource(R.string.text_cash_payments_and_receipts)
    val textStaySafeOnEveryTrip = stringResource(R.string.text_stay_safe_on_every_trip)
    val textFindSomethingYouLeftBehind = stringResource(R.string.text_find_something_you_left_behind)
    val textArticles = stringResource(R.string.text_articles)

    val Topics = helpTopics()
    val list = Topics[topic].orEmpty()
    val sub = when (topic) { "Ride bookings" -> textBookScheduleAndCancelRides; "Getting started" -> textLearnTheBasics; "Account & profile" -> textManageYourDetailsAndSecurity; "Payments & wallet" -> textCashPaymentsAndReceipts; "Safety & security" -> textStaySafeOnEveryTrip; else -> textFindSomethingYouLeftBehind }
    MoreScaffold(localizedLabel(topic), onBack, subtitle = sub) {
        SectionLabel(textArticles)
        GroupCard { list.forEachIndexed { i, a -> if (i > 0) RowDivider(); NavRow(RideIcons.Doc, localizedLabel(a.title), { open("Help article:${a.title}") }) } }
    }
}

@Composable
fun HelpArticleScreen(title: String, onBack: () -> Unit, onSupport: () -> Unit) {
    val textWasThisHelpful = stringResource(R.string.text_was_this_helpful)
    val textYes = stringResource(R.string.text_yes)
    val textNo = stringResource(R.string.text_no)
    val textThanksForYourFeedback = stringResource(R.string.text_thanks_for_your_feedback)
    val textSorryAboutThatOurTeamCanHelp = stringResource(R.string.text_sorry_about_that_our_team_can_help)
    val textContactSupport = stringResource(R.string.text_contact_support)

    val c = RideTheme.colors
    val AllArticles = helpTopics().values.flatten()
    val a = AllArticles.firstOrNull { it.title == title } ?: Article(title, "", emptyList())
    var vote by rememberSaveable { mutableStateOf("") }
    MoreScaffold(localizedLabel(a.title), onBack, subtitle = a.intro) {
        Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            a.steps.forEachIndexed { i, s ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.14f)).border(1.dp, c.accent, CircleShape), contentAlignment = Alignment.Center) { Text("${i + 1}", color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                    Text(s, color = c.fg, fontSize = 16.sp, lineHeight = 24.sp, modifier = Modifier.padding(start = 14.dp, top = 3.dp))
                }
            }
        }
        SectionLabel(textWasThisHelpful)
        if (vote.isEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Chip(textYes, false, { vote = "yes" }); Chip(textNo, false, { vote = "no" }) }
        else Text(if (vote == "yes") textThanksForYourFeedback else textSorryAboutThatOurTeamCanHelp, color = c.muted, fontSize = 14.sp, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        RideButton(textContactSupport, onSupport, Modifier.padding(vertical = 20.dp), style = RideButtonStyle.Text)
    }
}

@Composable
fun ReportSafetyScreen(onBack: () -> Unit, onSafety: () -> Unit) {
    val textUnsafeDriving = stringResource(R.string.text_unsafe_driving)
    val textHarassment = stringResource(R.string.text_harassment)
    val textAccident = stringResource(R.string.text_accident)
    val textVehicleOrDriverMismatch = stringResource(R.string.text_vehicle_or_driver_mismatch)
    val textReasonOther = stringResource(R.string.reason_other)
    val textReportASafetyIssue = stringResource(R.string.text_report_a_safety_issue)
    val textDone = stringResource(R.string.text_done)
    val textOpenSafetyCenter = stringResource(R.string.text_open_safety_center)
    val textReportReceived = stringResource(R.string.text_report_received)
    val textOurSafetyTeamWillReviewItAndContactYouWithin24Hours = stringResource(R.string.text_our_safety_team_will_review_it_and_contact_you_within_24_hours)
    val textTellUsWhatHappenedInAnEmergencyCallEmergencyServicesFirst = stringResource(R.string.text_tell_us_what_happened_in_an_emergency_call_emergency_services_first)
    val textSubmitReport = stringResource(R.string.text_submit_report)
    val textWhatHappened = stringResource(R.string.text_what_happened)
    val textDetails = stringResource(R.string.text_details)
    val textDescribeWhatHappened = stringResource(R.string.text_describe_what_happened)
    val textIncludeTripDetails = stringResource(R.string.text_include_trip_details)
    val textShareTheRouteDriverAndPlateWithOurSafetyTeam = stringResource(R.string.text_share_the_route_driver_and_plate_with_our_safety_team)

    val c = RideTheme.colors
    val kinds = listOf(textUnsafeDriving, textHarassment, textAccident, textVehicleOrDriverMismatch, textReasonOther)
    var kind by rememberSaveable { mutableIntStateOf(-1) }
    var text by rememberSaveable { mutableStateOf("") }
    var withTrip by rememberSaveable { mutableStateOf(true) }
    var sent by rememberSaveable { mutableStateOf(false) }
    if (sent) {
        MoreScaffold(textReportASafetyIssue, onBack, bottom = { RideButton(textDone, onBack); RideButton(textOpenSafetyCenter, onSafety, style = RideButtonStyle.Text) }) {
            Hero(RideIcons.Shield, textReportReceived, textOurSafetyTeamWillReviewItAndContactYouWithin24Hours)
        }
        return
    }
    MoreScaffold(textReportASafetyIssue, onBack, subtitle = textTellUsWhatHappenedInAnEmergencyCallEmergencyServicesFirst, bottom = {
        RideButton(textSubmitReport, { sent = true }, enabled = kind >= 0 && text.isNotBlank())
    }) {
        SectionLabel(textWhatHappened)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { kinds.chunked(2).forEach { r -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { r.forEach { k -> Chip(k, kinds.indexOf(k) == kind, { kind = kinds.indexOf(k) }) } } } }
        SectionLabel(textDetails)
        BasicTextField(
            text, { text = it.take(500) }, Modifier.fillMaxWidth().heightIn(min = 120.dp), textStyle = TextStyle(color = c.fg, fontSize = 15.sp), cursorBrush = SolidColor(c.accent),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(16.dp)) {
                    if (text.isEmpty()) Text(textDescribeWhatHappened, color = c.muted, fontSize = 15.sp)
                    inner()
                }
            },
        )
        GroupCard(Modifier.padding(top = 16.dp)) { ToggleRow(textIncludeTripDetails, textShareTheRouteDriverAndPlateWithOurSafetyTeam, withTrip, { withTrip = it }) }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun ReportIssueScreen(onBack: () -> Unit, onDone: () -> Unit) {
    val textReportAnIssue = stringResource(R.string.text_report_an_issue)
    val textTellUsWhatHappened = stringResource(R.string.label_tell_us_what_happened)
    val textCategory = stringResource(R.string.help_report_category)
    val textDescribeTheIssue = stringResource(R.string.help_report_describe)
    val textWhatWentWrong = stringResource(R.string.help_report_placeholder)
    val textTripProblem = stringResource(R.string.help_report_cat_trip)
    val textPaymentProblem = stringResource(R.string.help_report_cat_payment)
    val textAppProblem = stringResource(R.string.help_report_cat_app)
    val textSomethingElse = stringResource(R.string.help_report_cat_other)
    val textSubmitReport = stringResource(R.string.text_submit_report)
    val textDone = stringResource(R.string.text_done)
    val textReportReceived = stringResource(R.string.help_report_received_title)
    val textReviewWithin24Hours = stringResource(R.string.help_report_received_body)

    val c = RideTheme.colors
    val kinds = listOf(textTripProblem, textPaymentProblem, textAppProblem, textSomethingElse)
    var kind by rememberSaveable { mutableIntStateOf(-1) }
    var text by rememberSaveable { mutableStateOf("") }
    var sent by rememberSaveable { mutableStateOf(false) }
    if (sent) {
        MoreScaffold(textReportAnIssue, onBack, bottom = { RideButton(textDone, onDone) }) {
            Hero(RideIcons.Flag, textReportReceived, textReviewWithin24Hours)
        }
        return
    }
    MoreScaffold(textReportAnIssue, onBack, subtitle = textTellUsWhatHappened, bottom = {
        RideButton(textSubmitReport, { sent = true }, enabled = kind >= 0 && text.isNotBlank())
    }) {
        SectionLabel(textCategory)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { kinds.chunked(2).forEach { r -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { r.forEach { k -> Chip(k, kinds.indexOf(k) == kind, { kind = kinds.indexOf(k) }) } } } }
        SectionLabel(textDescribeTheIssue)
        BasicTextField(
            text, { text = it.take(500) }, Modifier.fillMaxWidth().heightIn(min = 120.dp), textStyle = TextStyle(color = c.fg, fontSize = 15.sp), cursorBrush = SolidColor(c.accent),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(16.dp)) {
                    if (text.isEmpty()) Text(textWhatWentWrong, color = c.muted, fontSize = 15.sp)
                    inner()
                }
            },
        )
        Spacer(Modifier.height(12.dp))
    }
}
