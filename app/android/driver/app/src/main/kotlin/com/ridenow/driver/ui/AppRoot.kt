package com.ridenow.driver.ui

import com.ridenow.driver.R
import androidx.compose.ui.res.stringResource

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ridenow.driver.ui.auth.OtpScreen
import com.ridenow.driver.ui.auth.PhoneScreen
import com.ridenow.driver.ui.auth.formatPhoneForDisplay
import com.ridenow.driver.ui.account.AccountScreen
import com.ridenow.driver.ui.earnings.EarningsScreen
import com.ridenow.driver.ui.home.HomeScreen
import com.ridenow.driver.ui.home.LocalNavBadges
import com.ridenow.driver.ui.home.MainTab
import com.ridenow.driver.ui.inbox.InboxScreen
import com.ridenow.driver.ui.intro.IntroScreen
import com.ridenow.driver.ui.registration.ApplicationStatus
import com.ridenow.driver.ui.registration.ApplicationViewModel
import com.ridenow.driver.ui.registration.BackgroundLocationScreen
import com.ridenow.driver.ui.registration.BankAccountScreen
import com.ridenow.driver.ui.registration.IdentityLicenseScreen
import com.ridenow.driver.ui.registration.NotificationPermissionScreen
import com.ridenow.driver.ui.registration.VehicleMediaScreen
import com.ridenow.driver.ui.registration.WorkPlanScreen
import com.ridenow.driver.ui.registration.PersonalInfoScreen
import com.ridenow.driver.ui.registration.ReviewItem
import com.ridenow.driver.ui.registration.ReviewScreen
import com.ridenow.driver.ui.registration.TermsScreen
import com.ridenow.driver.ui.registration.VehicleScreen
import com.ridenow.driver.ui.preview.PreviewConfig
import com.ridenow.driver.ui.splash.SplashScreen
import com.ridenow.driver.ui.status.ActionRequiredScreen
import com.ridenow.driver.ui.status.ApprovedScreen
import com.ridenow.driver.ui.status.RejectedScreen
import com.ridenow.driver.ui.status.SubmittedScreen
import com.ridenow.driver.ui.theme.RideTheme
import com.ridenow.driver.ui.theme.ThemeMode
import com.ridenow.driver.ui.trip.TripCompleteScreen
import com.ridenow.driver.ui.trip.TripPhase
import com.ridenow.driver.ui.trip.TripScreen
import kotlinx.coroutines.delay
import com.ridenow.driver.ui.welcome.WelcomeScreen

enum class Screen {
    Splash, Intro, Welcome, Phone, Otp, Terms, Background, Notifications, Personal, IdentityLicense, Vehicle,
    VehicleMedia, WorkPlan, Bank, Review, Submitted,
    ActionRequired, Rejected, Approved, Home, Earnings, Inbox, Account, Trip, TripComplete,
}

/** Survives activity recreation within one app process, so the splash does not replay on rotation or theme changes. */
private object SessionFlags { var splashDone = false }

// Step numbers used as keys for the Review summaries.
private const val STEP_TERMS = 1
private const val STEP_BACKGROUND = 2
private const val STEP_NOTIFICATIONS = 12
private const val STEP_PERSONAL = 3
private const val STEP_LICENSE = 5
private const val STEP_VEHICLE = 6
private const val STEP_PHOTOS = 8
private const val STEP_AVAILABILITY = 9
private const val STEP_AREA = 10

@Composable
fun AppRoot(themeMode: ThemeMode, onThemeMode: (ThemeMode) -> Unit) {
    var screen by rememberSaveable { mutableStateOf(Screen.Splash) }
    var phone by rememberSaveable { mutableStateOf("") }
    // True while the driver is fixing one section from the Review screen: finishing it returns to Review.
    var editing by rememberSaveable { mutableStateOf(false) }
    // Set when Bank account was opened from Home or Account (name of that screen): finishing returns there.
    var bankReturn by rememberSaveable { mutableStateOf<String?>(null) }
    val app: ApplicationViewModel = viewModel()
    val saveable = rememberSaveableStateHolder()
    val shownPhone = formatPhoneForDisplay(phone.ifBlank { "+1 555 012 3456" })
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ridenowdriver", 0) }

    // Text for the Review rows that do not come from a screen.
    val accepted = stringResource(R.string.review_accepted)
    val bgOn = stringResource(R.string.review_bg_on)
    val bgOff = stringResource(R.string.review_bg_off)
    val notifOn = stringResource(R.string.review_notif_on)
    val notifOff = stringResource(R.string.review_notif_off)
    val bothSides = stringResource(R.string.review_both_sides)
    val docsDone = stringResource(R.string.review_docs)
    val bankAdded = stringResource(R.string.review_bank_added)
    val bankSkipped = stringResource(R.string.review_bank_skipped)
    val dash = "\u2014"
    val reviewItems = listOf(
        ReviewItem(stringResource(R.string.review_row_terms), "$accepted \u00b7 ${app.summaries[STEP_BACKGROUND] ?: bgOff} \u00b7 ${app.summaries[STEP_NOTIFICATIONS] ?: notifOff}"),
        ReviewItem(stringResource(R.string.review_row_personal), app.summaries[STEP_PERSONAL] ?: dash),
        ReviewItem(stringResource(R.string.review_row_identity), bothSides),
        ReviewItem(stringResource(R.string.review_row_license), app.summaries[STEP_LICENSE] ?: dash),
        ReviewItem(stringResource(R.string.review_row_vehicle), app.summaries[STEP_VEHICLE] ?: dash),
        ReviewItem(stringResource(R.string.review_row_docs), docsDone),
        ReviewItem(stringResource(R.string.review_row_photos), app.summaries[STEP_PHOTOS] ?: dash),
        ReviewItem(stringResource(R.string.review_row_avail), "${app.summaries[STEP_AVAILABILITY] ?: dash} \u00b7 ${app.summaries[STEP_AREA] ?: dash}"),
        ReviewItem(stringResource(R.string.review_row_bank), if (app.bankSaved) bankAdded else bankSkipped, warn = !app.bankSaved, add = !app.bankSaved),
    )
    val editTargets = listOf(
        Screen.Terms, Screen.Personal, Screen.IdentityLicense, Screen.IdentityLicense, Screen.Vehicle,
        Screen.VehicleMedia, Screen.VehicleMedia, Screen.WorkPlan, Screen.Bank,
    )

    /** Goes to [target], or back to Review when a section was opened for editing. */
    fun advance(target: Screen) {
        if (editing) { editing = false; screen = Screen.Review } else screen = target
    }

    fun finishBank(saved: Boolean) {
        if (saved) app.bankSaved = true else if (bankReturn == null) app.bankSaved = false
        val back = bankReturn
        if (back != null) { bankReturn = null; screen = Screen.valueOf(back) } else advance(Screen.Review)
    }
    fun backFromBank() {
        val back = bankReturn
        if (back != null) { bankReturn = null; screen = Screen.valueOf(back) } else advance(Screen.WorkPlan)
    }

    LaunchedEffect(Unit) {
        if (SessionFlags.splashDone && screen == Screen.Splash) {
            screen = if (prefs.getBoolean("intro_seen", false)) Screen.Welcome else Screen.Intro
        }
    }
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalNavBadges provides (if (app.inboxUnread) setOf(MainTab.Inbox) else emptySet())) {
        Crossfade(targetState = screen, animationSpec = tween(400), label = "root") { current ->
            // Keeps typed text, dates and choices when the driver goes back to a screen.
            saveable.SaveableStateProvider(current.name) {
                when (current) {
                    Screen.Splash -> SplashScreen(onFinished = { SessionFlags.splashDone = true; screen = if (prefs.getBoolean("intro_seen", false)) Screen.Welcome else Screen.Intro })
                    Screen.Intro -> IntroScreen(onDone = { prefs.edit().putBoolean("intro_seen", true).commit(); screen = Screen.Welcome })
                    Screen.Welcome -> WelcomeScreen(onGetStarted = { screen = Screen.Phone }, onHaveAccount = { screen = Screen.Phone }, onHelp = { })
                    Screen.Phone -> {
                        BackHandler { screen = Screen.Welcome }
                        PhoneScreen(onBack = { screen = Screen.Welcome }, onCodeSent = { phone = it; screen = Screen.Otp })
                    }
                    Screen.Otp -> {
                        BackHandler { screen = Screen.Phone }
                        OtpScreen(phone = shownPhone, onBack = { screen = Screen.Phone }, onVerified = { screen = Screen.Terms })
                    }
                    Screen.Terms -> {
                        BackHandler { advance(Screen.Otp) }
                        TermsScreen(
                            onBack = { advance(Screen.Otp) },
                            onAccept = { prefs.edit().putBoolean("terms_accepted", true).apply(); app.summaries[STEP_TERMS] = accepted; screen = Screen.Background },
                            onDecline = { editing = false; screen = Screen.Welcome },
                        )
                    }
                    Screen.Background -> {
                        BackHandler { screen = Screen.Terms }
                        BackgroundLocationScreen(
                            onBack = { screen = Screen.Terms },
                            onAllowed = { app.summaries[STEP_BACKGROUND] = bgOn; screen = Screen.Notifications },
                            onSkip = { app.summaries[STEP_BACKGROUND] = bgOff; screen = Screen.Notifications },
                        )
                    }
                    Screen.Notifications -> {
                        BackHandler { screen = Screen.Background }
                        NotificationPermissionScreen(
                            onBack = { screen = Screen.Background },
                            onDone = { on -> app.summaries[STEP_NOTIFICATIONS] = if (on) notifOn else notifOff; advance(Screen.Personal) },
                        )
                    }
                    Screen.Personal -> {
                        BackHandler { advance(Screen.Notifications) }
                        PersonalInfoScreen(onBack = { advance(Screen.Notifications) }, onDone = { app.summaries[STEP_PERSONAL] = it; advance(Screen.IdentityLicense) })
                    }
                    Screen.IdentityLicense -> {
                        BackHandler { advance(Screen.Personal) }
                        IdentityLicenseScreen(onBack = { advance(Screen.Personal) }, onDone = { app.summaries[STEP_LICENSE] = it; advance(Screen.Vehicle) })
                    }
                    Screen.Vehicle -> {
                        BackHandler { advance(Screen.IdentityLicense) }
                        VehicleScreen(onBack = { advance(Screen.IdentityLicense) }, onDone = { app.summaries[STEP_VEHICLE] = it; advance(Screen.VehicleMedia) })
                    }
                    Screen.VehicleMedia -> {
                        BackHandler { advance(Screen.Vehicle) }
                        VehicleMediaScreen(onBack = { advance(Screen.Vehicle) }, onDone = { app.summaries[STEP_PHOTOS] = it; advance(Screen.WorkPlan) })
                    }
                    Screen.WorkPlan -> {
                        BackHandler { advance(Screen.VehicleMedia) }
                        WorkPlanScreen(
                            onBack = { advance(Screen.VehicleMedia) },
                            onDone = { availability, area -> app.summaries[STEP_AVAILABILITY] = availability; app.summaries[STEP_AREA] = area; advance(Screen.Bank) },
                        )
                    }
                    Screen.Bank -> {
                        BackHandler { backFromBank() }
                        BankAccountScreen(onBack = { backFromBank() }, onSaved = { finishBank(true) }, onSkip = { finishBank(false) })
                    }
                    Screen.Review -> {
                        BackHandler { screen = Screen.Bank }
                        ReviewScreen(
                            items = reviewItems,
                            onEdit = { i -> editing = true; screen = editTargets[i] },
                            onSubmit = { prefs.edit().putBoolean("application_submitted", true).apply(); screen = Screen.Submitted },
                            onBack = { screen = Screen.Bank },
                        )
                    }
                    Screen.Submitted -> {
                        // UI preview: after a few seconds, jump to Home as if the application had been approved.
                        var secondsLeft by remember { mutableIntStateOf(PreviewConfig.SUBMITTED_TO_HOME_SECONDS) }
                        if (PreviewConfig.ENABLED) LaunchedEffect(Unit) {
                            while (secondsLeft > 0) { delay(1000); secondsLeft-- }
                            app.approveDemo(); screen = Screen.Home
                        }
                        SubmittedScreen(
                            name = app.summaries[STEP_PERSONAL].orEmpty().substringBefore(" "), reference = app.reference,
                            onCheckStatus = { app.checkStatus(); screen = screenFor(app.status) }, onSupport = { },
                            previewSecondsLeft = if (PreviewConfig.ENABLED) secondsLeft else null,
                        )
                    }
                    Screen.ActionRequired -> ActionRequiredScreen(
                        // Fixing returns to Review, then Submit sends the application again.
                        onFix = { i -> editing = true; screen = if (i == 0) Screen.IdentityLicense else Screen.VehicleMedia },
                        onUpdate = { editing = true; screen = Screen.IdentityLicense }, onSupport = { },
                    )
                    Screen.Rejected -> RejectedScreen(onReapply = { editing = false; screen = Screen.Terms }, onSupport = { })
                    Screen.Approved -> ApprovedScreen(onOpenHome = { screen = Screen.Home }, onExplore = { screen = Screen.Home })
                    Screen.Home -> HomeScreen(
                        online = app.online, bankSaved = app.bankSaved, driverName = app.summaries[STEP_PERSONAL].orEmpty(),
                        onlineSince = app.onlineSince, bankedMillis = app.bankedMillis,
                        onToggle = { if (app.online) app.goOffline() else if (app.bankSaved) app.goOnline() else { bankReturn = Screen.Home.name; screen = Screen.Bank } },
                        onAddBank = { bankReturn = Screen.Home.name; screen = Screen.Bank },
                        onTab = { screen = tabScreen(it) },
                        earnings = app.earnings, trips = app.history.size, request = app.request,
                        onOfferRequest = { app.offerRequest() }, onDecline = { app.declineRequest() },
                        onAccept = { app.acceptRequest(); screen = Screen.Trip },
                    )
                    Screen.Earnings -> {
                        BackHandler { screen = Screen.Home }
                        EarningsScreen(onlineMinutes = app.onlineMinutes(), earnings = app.earnings, history = app.history, onTab = { screen = tabScreen(it) })
                    }
                    Screen.Inbox -> {
                        BackHandler { screen = Screen.Home }
                        LaunchedEffect(Unit) { app.markInboxRead() }
                        InboxScreen(onTab = { screen = tabScreen(it) })
                    }
                    Screen.Trip -> {
                        val trip = app.trip
                        if (trip == null) LaunchedEffect(Unit) { screen = Screen.Home } else {
                            BackHandler { }
                            TripScreen(
                                trip = trip, phase = app.phase,
                                onAdvance = { if (app.phase == TripPhase.InTrip) screen = Screen.TripComplete else app.advanceTrip() },
                                onCancel = { app.cancelTrip(); screen = Screen.Home },
                            )
                        }
                    }
                    Screen.TripComplete -> {
                        val trip = app.trip
                        if (trip == null) LaunchedEffect(Unit) { screen = Screen.Home } else {
                            BackHandler { }
                            TripCompleteScreen(trip = trip, onDone = { app.finishTrip(); screen = Screen.Home })
                        }
                    }
                    Screen.Account -> {
                        BackHandler { screen = Screen.Home }
                        AccountScreen(
                            name = app.summaries[STEP_PERSONAL].orEmpty(), phone = shownPhone, vehicle = app.summaries[STEP_VEHICLE].orEmpty(),
                            availability = app.summaries[STEP_AVAILABILITY].orEmpty(), bankSaved = app.bankSaved,
                            mode = themeMode, onMode = onThemeMode,
                            onAddBank = { bankReturn = Screen.Account.name; screen = Screen.Bank },
                            onSignOut = {
                                app.reset(); phone = ""; editing = false; bankReturn = null
                                prefs.edit().remove("terms_accepted").remove("application_submitted").apply()
                                screen = Screen.Welcome
                            },
                            onTab = { screen = tabScreen(it) },
                        )
                    }
                }
            }
        }
        }
    }
}

private fun tabScreen(tab: MainTab): Screen = when (tab) {
    MainTab.Home -> Screen.Home
    MainTab.Earnings -> Screen.Earnings
    MainTab.Inbox -> Screen.Inbox
    MainTab.Account -> Screen.Account
}

private fun screenFor(status: ApplicationStatus): Screen = when (status) {
    ApplicationStatus.Pending -> Screen.Submitted
    ApplicationStatus.ActionRequired -> Screen.ActionRequired
    ApplicationStatus.Rejected -> Screen.Rejected
    ApplicationStatus.Approved -> Screen.Approved
}
