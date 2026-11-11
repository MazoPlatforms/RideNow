package com.ridenow.app.ui.more

import com.ridenow.app.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.ridenow.app.ui.booking.MapPickerScreen
import java.time.LocalDate

/** Labels the app shell can open through [MoreHost]. Anything else keeps its placeholder. */
object MoreRoutes {
    private val names = setOf(
        "Your route", "Vehicle info", "High demand", "Pick up later", "Passenger", "Repeat", "Reminder", "Pick another date", "Set stop location", "Stop on map",
        "No drivers available", "Driver cancelled", "Driver arrived", "Ride cancelled", "Change drop-off", "Emergency call", "In-app call", "Chat with driver",
        "Trusted contact tracking", "Live trip notification", "Pay in cash", "Ride details", "Receipt PDF", "Scheduled ride details", "Edit scheduled ride", "Cancel scheduled ride",
        "Change phone number", "Verify new number", "Current city", "Transaction History", "Notification settings", "App color", "Map style", "Language",
        "Privacy & Security", "Manage Devices", "Two-step verification", "App data and permissions", "Location data", "Download my data",
        "Delete account", "Confirm deletion", "Account deleted", "Help Center", "Report a safety issue",
    )
    fun handles(label: String) = label in names || label.startsWith("Help topic:") || label.startsWith("Help article:")
}

/** Small navigator for the extra screens: labels stack, so sheets and chained screens go back correctly. */
@Composable
fun MoreHost(start: String, ctx: MoreCtx, root: MoreNav) {
    var stack by rememberSaveable { mutableStateOf(arrayListOf(start)) }
    val label = stack.last()
    val push = { l: String -> stack = ArrayList(stack + l) }
    val pop = { if (stack.size > 1) stack = ArrayList(stack.dropLast(1)) else root.exit() }
    // Always handled: on the first screen, pop() hands control back to the app shell instead of exiting.
    BackHandler { pop() }
    val nav = MoreNav(
        back = pop, exit = root.exit, open = push, home = root.home, options = root.options, schedule = root.schedule, finding = root.finding, trip = root.trip,
        complete = root.complete, report = root.report, support = root.support, safety = root.safety, setPhone = root.setPhone, setVehicle = root.setVehicle,
        setScheduled = root.setScheduled, confirm = root.confirm, changeDrop = root.changeDrop, cancelScheduled = root.cancelScheduled, signedOut = root.signedOut,
    )
    key(label) { MoreScreen(label, ctx, nav) }
}

@Composable
private fun MoreScreen(label: String, ctx: MoreCtx, nav: MoreNav) {
    val textSearchCurrent = stringResource(R.string.search_current)
    val textChooseAContact = stringResource(R.string.text_choose_a_contact)
    val textSetStopLocation = stringResource(R.string.text_set_stop_location)
    val textReasonPlans = stringResource(R.string.reason_plans)

    when {
        label.startsWith("Help topic:") -> HelpTopicScreen(label.removePrefix("Help topic:"), nav.back, nav.open)
        label.startsWith("Help article:") -> HelpArticleScreen(label.removePrefix("Help article:"), nav.back, nav.support)
        else -> when (label) {
            // Booking
            "Your route" -> YourRouteScreen(textSearchCurrent, ctx.dest, nav.exit)
            "Vehicle info" -> VehicleInfoSheet(ctx.vehicle, { nav.setVehicle(it); nav.exit() }, nav.exit)
            "High demand" -> HighDemandSheet(ctx.vehicle, nav.exit, { nav.exit(); nav.schedule() }, nav.exit)
            "Pick up later" -> PickUpLaterSheet({ nav.setScheduled(it); nav.confirm() }, nav.exit, { nav.open("Pick another date") })
            "Passenger" -> ChoiceSheet(stringResource(R.string.passenger_title), passengerOptions(), 0, { nav.exit() }, nav.exit, listOf(ctx.name, textChooseAContact), icons = listOf(com.ridenow.app.ui.components.RideIcons.Person, com.ridenow.app.ui.components.RideIcons.Group))
            "Repeat" -> ChoiceSheet(stringResource(R.string.repeat_title), repeatOptions(), 0, { nav.exit() }, nav.exit, icons = List(5) { com.ridenow.app.ui.components.RideIcons.Calendar })
            "Reminder" -> ChoiceSheet(stringResource(R.string.reminder_title), reminderOptions(), 2, { nav.exit() }, nav.exit, icons = List(5) { com.ridenow.app.ui.components.RideIcons.Clock })
            "Pick another date" -> CalendarSheet(LocalDate.now(), { nav.back() }, nav.back)
            "Set stop location" -> StopLocationScreen(textSearchCurrent, ctx.dest, nav.exit, { nav.open("Stop on map") }, { nav.exit() })
            "Stop on map" -> MapPickerScreen(textSetStopLocation, textSetStopLocation, nav.back, onConfirm = { _, _ -> nav.exit() })
            // During the trip
            "No drivers available" -> NoDriversScreen(ctx, nav.finding, nav.schedule, nav.options, nav.home)
            "Driver cancelled" -> DriverCancelledScreen(ctx, nav.trip, nav.home)
            "Driver arrived" -> DriverWaitingScreen(ctx, { nav.open("In-app call") }, { nav.open("Chat with driver") }, { nav.open("Ride cancelled") }, nav.trip)
            "Ride cancelled" -> RideCancelledScreen(ctx, textReasonPlans, nav.options, nav.home)
            "Change drop-off" -> ChangeDropScreen(ctx, nav.exit) { nav.changeDrop(it); nav.exit() }
            "Emergency call" -> EmergencyCountdownScreen(ctx, nav.exit)
            "In-app call" -> InAppCallScreen(nav.back)
            "Chat with driver" -> DriverChatScreen(nav.back)
            "Trusted contact tracking" -> TrustedTrackingScreen(ctx, nav.exit) { nav.open("Report a safety issue") }
            "Live trip notification" -> LockScreenTripScreen(nav.exit)
            // After the ride
            "Pay in cash" -> PayCashScreen(ctx, nav.complete, nav.report)
            "Ride details" -> RideDetailsScreen(ctx, nav.exit, { nav.open("Receipt PDF") }, nav.report)
            "Receipt PDF" -> ReceiptScreen(ctx, nav.back)
            "Scheduled ride details" -> ScheduledDetailsScreen(ctx, nav.exit, { nav.open("Edit scheduled ride") }, { nav.open("Cancel scheduled ride") })
            "Edit scheduled ride" -> EditScheduledScreen(ctx, nav.back, { nav.setScheduled(it); nav.back() }, { nav.open("Your route") })
            "Cancel scheduled ride" -> CancelScheduledScreen(ctx, nav.back) { nav.cancelScheduled() }
            // Account and settings
            "Change phone number" -> ChangePhoneScreen(nav.exit) { nav.open("Verify new number") }
            "Verify new number" -> VerifyNewNumberScreen(nav.back) { nav.setPhone(it); nav.exit() }
            "Current city" -> CityPickerScreen(nav.exit)
            "Transaction History" -> TransactionHistoryScreen(nav.exit)
            "Notification settings" -> NotificationSettingsScreen(nav.exit)
            "App color" -> AppColorScreen(nav.exit)
            "Map style" -> MapStyleScreen(nav.exit)
            "Language" -> LanguageScreen(nav.exit)
            "Privacy & Security" -> PrivacyHubScreen(nav.exit, nav.open)
            "Manage Devices" -> ManageDevicesScreen(nav.back)
            "Two-step verification" -> TwoStepScreen(nav.back, ctx.phone)
            "App data and permissions" -> AppDataScreen(nav.back)
            "Location data" -> LocationDataScreen(nav.back)
            "Download my data" -> DownloadDataScreen(nav.back)
            "Delete account" -> DeleteAccountScreen(nav.back) { nav.open("Confirm deletion") }
            "Confirm deletion" -> ConfirmDeletionScreen(ctx.phone, nav.back) { nav.open("Account deleted") }
            "Account deleted" -> AccountDeletedScreen(nav.signedOut)
            // Help
            "Help Center" -> HelpCenterScreen(nav.exit, nav.open)
            "Report a safety issue" -> ReportSafetyScreen(nav.back, nav.safety)
        }
    }
}
