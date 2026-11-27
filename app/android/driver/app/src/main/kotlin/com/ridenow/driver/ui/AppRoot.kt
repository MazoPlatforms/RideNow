package com.ridenow.driver.ui

import com.ridenow.driver.R
import androidx.compose.ui.res.stringResource

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.ridenow.driver.ui.auth.PhoneAuthApi
import com.ridenow.driver.ui.auth.PhoneAuthException
import com.ridenow.driver.ui.registration.DriverOnboardingApi
import com.ridenow.driver.ui.registration.LocalDriverDraft
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import kotlinx.coroutines.launch
import com.ridenow.driver.ui.auth.formatPhoneForDisplay
import com.ridenow.driver.ui.account.AccountScreen
import com.ridenow.driver.ui.earnings.EarningsScreen
import com.ridenow.driver.ui.home.HomeScreen
import com.ridenow.driver.ui.home.LocalNavBadges
import com.ridenow.driver.ui.home.MainTab
import com.ridenow.driver.ui.intro.IntroScreen
import com.ridenow.driver.ui.registration.ApplicationViewModel
import com.ridenow.driver.ui.registration.BackgroundLocationScreen
import com.ridenow.driver.ui.registration.BankAccountScreen
import com.ridenow.driver.ui.registration.IdentityScreen
import com.ridenow.driver.ui.profile.DriverProfileScreen
import com.ridenow.driver.ui.profile.EditProfileScreen
import com.ridenow.driver.ui.registration.LicenseScreen
import com.ridenow.driver.ui.registration.VehicleDocsScreen
import com.ridenow.driver.ui.registration.VehiclePhotosScreen
import com.ridenow.driver.ui.registration.AvailabilityScreen
import com.ridenow.driver.ui.registration.ServiceAreaScreen
import com.ridenow.driver.ui.registration.NotificationPermissionScreen
import com.ridenow.driver.ui.registration.PersonalInfoScreen
import com.ridenow.driver.ui.registration.ReviewItem
import com.ridenow.driver.ui.registration.ReviewScreen
import com.ridenow.driver.ui.registration.TermsScreen
import com.ridenow.driver.ui.registration.VehicleScreen
import com.ridenow.driver.ui.splash.SplashScreen
import com.ridenow.driver.ui.status.ActionRequiredScreen
import com.ridenow.driver.ui.status.ApprovedScreen
import com.ridenow.driver.ui.status.RejectedScreen
import com.ridenow.driver.ui.theme.RideTheme
import com.ridenow.driver.ui.theme.ThemeMode
import com.ridenow.driver.ui.trip.*
import com.ridenow.driver.ui.earnings.EarningsApi
import com.ridenow.driver.ui.earnings.EarningsPeriod
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.util.UUID
import kotlinx.coroutines.delay
import com.ridenow.driver.ui.welcome.WelcomeScreen
import com.ridenow.driver.ui.components.initialsOf
import com.ridenow.driver.ui.components.toast
import com.ridenow.driver.ui.data.BankAccountInfo
import com.ridenow.driver.ui.data.DriverVehicleSnapshot
import com.ridenow.driver.ui.data.Notice
import com.ridenow.driver.ui.legal.LegalAboutScreen
import com.ridenow.driver.ui.legal.LegalDocScreen
import com.ridenow.driver.ui.legal.LegalDocs
import com.ridenow.driver.ui.more.LanguageScreen
import com.ridenow.driver.ui.notifications.NotificationsScreen
import com.ridenow.driver.ui.notifications.loadDriverNotices
import com.ridenow.driver.ui.payouts.BankAccountManageScreen
import com.ridenow.driver.ui.payouts.BankFormScreen
import com.ridenow.driver.ui.payouts.DriverPayoutApi
import com.ridenow.driver.ui.payouts.PayoutBank
import com.ridenow.driver.ui.payouts.PayoutsScreen
import com.ridenow.driver.ui.earnings.EarningsDetailsScreen
import com.ridenow.driver.ui.rides.RideDetailsScreen
import com.ridenow.driver.ui.rides.RideHistoryScreen
import com.ridenow.driver.ui.settings.AppearanceScreen
import com.ridenow.driver.ui.settings.DeleteAccountScreen
import com.ridenow.driver.ui.settings.NotificationSettingsScreen
import com.ridenow.driver.ui.settings.PaymentMethodsScreen
import com.ridenow.driver.ui.settings.PrivacySecurityScreen
import com.ridenow.driver.ui.settings.ManageDevicesScreen
import com.ridenow.driver.ui.settings.TwoStepScreen
import com.ridenow.driver.ui.settings.DownloadDataScreen
import com.ridenow.driver.ui.settings.SettingsScreen
import com.ridenow.driver.ui.components.MainHeader
import com.ridenow.driver.ui.home.MainBottomBar
import com.ridenow.driver.ui.support.SupportEntry
import com.ridenow.driver.ui.support.SupportWorkspaceScreen
import com.ridenow.driver.ui.support.EmergencyAssistScreen
import com.ridenow.driver.ui.support.FaqScreen
import com.ridenow.driver.ui.support.HelpSafetyScreen
import com.ridenow.driver.ui.support.SafetyCenterScreen
import com.ridenow.driver.ui.support.ShareTripScreen
import com.ridenow.driver.ui.support.SupportCenterScreen
import com.ridenow.driver.ui.system.LocationOffScreen
import com.ridenow.driver.ui.system.NoInternetScreen
import com.ridenow.driver.ui.system.NotificationsOffScreen
import com.ridenow.driver.ui.system.rememberLocationEnabled
import com.ridenow.driver.ui.system.rememberNotificationsEnabled
import com.ridenow.driver.ui.system.rememberOnline
import com.ridenow.driver.ui.theme.AppearanceState
import com.ridenow.driver.ui.theme.MapStyle
import com.ridenow.driver.ui.vehicle.VehicleDetailsScreen
import com.ridenow.driver.ui.vehicle.VehicleDocumentsScreen

enum class Screen {
    Splash, Intro, Welcome, Phone, Otp, Terms, Background, Notifications, Personal, Identity, License, Vehicle,
    VehicleDocs, VehiclePhotos, Availability, ServiceArea, Bank, Review, Submitted,
    ActionRequired, Rejected, Approved, Home, Earnings, Rides, Support, Account, Trip, TripComplete, DriverProfile, EditProfile,
    NotificationCenter, EarningsDetails, RideDetails, VehicleDetails, VehicleDocuments, Payouts, BankManage, BankForm,
    Settings, Appearance, LanguageSettings, NotificationSettings, Privacy, DeleteAccount, PaymentMethods, ManageDevices, TwoStep, DownloadData,
    Faq, Tickets, Conversation, NewTicket, SupportChat, HelpSafety, SafetyCenter, Emergency, ShareTrip, ReportIssue, Legal, LegalDoc,
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
fun AppRoot(appearance: AppearanceState) {
    val app: ApplicationViewModel = viewModel()
    var screen by app.currentScreen
    var phone by rememberSaveable { mutableStateOf("") }
    var challengeId by rememberSaveable { mutableStateOf("") }
    // True while the driver is fixing one section from the Review screen: finishing it returns to Review.
    var editing by rememberSaveable { mutableStateOf(false) }
    // Set when Bank account was opened from Home or Account (name of that screen): finishing returns there.
    var bankReturn by rememberSaveable { mutableStateOf<String?>(null) }
    val saveable = rememberSaveableStateHolder()
    val shownPhone = formatPhoneForDisplay(phone.ifBlank { "+1 555 012 3456" })
    val context = LocalContext.current
    var netKey by remember { mutableIntStateOf(0) }
    var offlineSkip by rememberSaveable { mutableStateOf(false) }
    val hasInternet = rememberOnline(netKey)
    val locationOn = rememberLocationEnabled()
    val notificationsOn = rememberNotificationsEnabled()
    var locDismissed by rememberSaveable(locationOn) { mutableStateOf(false) }
    var onboardingLocationDismissed by rememberSaveable(screen, locationOn) { mutableStateOf(false) }
    var ntfDismissed by rememberSaveable { mutableStateOf(false) }
    val prefs = remember { context.getSharedPreferences("ridenowdriver", 0) }
    val scope = rememberCoroutineScope()
    val activityError = stringResource(R.string.onboarding_connection_error)
    val incompleteError = stringResource(R.string.onboarding_incomplete)
    val scanWaiting = stringResource(R.string.onboarding_scan_wait)
    val scanFailed = stringResource(R.string.onboarding_scan_failed)
    val uploadProgress = stringResource(R.string.onboarding_upload_progress)
    val retryText = stringResource(R.string.onboarding_retry)
    val signOutText = stringResource(R.string.onboarding_sign_out)
    val pendingText = stringResource(R.string.onboarding_pending)
    val rejectedText = stringResource(R.string.onboarding_changes)
    val suspendedText = stringResource(R.string.onboarding_suspended)
    val editText = stringResource(R.string.onboarding_edit)
    var busy by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var confirmReapplication by remember { mutableStateOf(false) }
    var proposedIdentity by remember { mutableStateOf<Pair<String, String>?>(null) }
    var reviewStatus by app.onboardingStatus
    var reviewReason by app.onboardingReason

    suspend fun checkStatus() {
        val data = DriverOnboardingApi.status(context)
        val profile = data?.optJSONObject("profile")
        val snapshot = DriverVehicleSnapshot.from(data ?: JSONObject())
        app.store.vehicle = snapshot.vehicle
        app.store.docs.clear()
        app.store.docs.addAll(snapshot.documents)
        val payoutMethod = try { DriverPayoutApi.method(context) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
        app.store.banks.clear()
        payoutMethod?.let { app.store.banks.add(BankAccountInfo(1, it.holder, it.bank, it.number, it.branch, it.type, verified = false)) }
        reviewStatus = profile?.optString("status").orEmpty()
        reviewReason = profile?.opt("review_reason") as? String ?: ""
        val contact = data?.optJSONObject("contact")
        data?.optJSONObject("application")?.let { application ->
            app.bankSaved = payoutMethod != null || application.optJSONObject("bank") != null
            if (contact == null) {
                app.profileName = application.optString("name")
                app.profileEmail = application.optString("email")
            }
        }
        contact?.let {
            app.profileName = contact.optString("name")
            app.profileEmail = contact.optString("email")
            app.profileAddress = contact.optString("address")
            app.store.prefLanguage = contact.optString("preferredLanguage").ifBlank { app.store.prefLanguage }
            app.store.dob = (contact.opt("dateOfBirth") as? String).orEmpty()
            app.store.nic = (contact.opt("identityNumber") as? String).orEmpty()
            val photoUrl = contact.opt("photoUrl") as? String
            val imageKey = photoUrl?.substringBefore('?')
            if (imageKey == null) {
                app.store.photo = null
                app.store.photoSource = null
            } else if (app.store.photoSource != imageKey) {
                val image = try {
                    DriverOnboardingApi.downloadProfilePhoto(photoUrl)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
                if (image != null) {
                    app.store.photo = image
                    app.store.photoSource = imageKey
                }
            }
        }
        val pending = PhoneAuthApi.pendingApplication(context)
        if (reviewStatus == "APPROVED") {
            if (pending != null) PhoneAuthApi.savePendingApplication(context, null)
            app.draft = com.ridenow.driver.ui.registration.DriverDraft()
            when (screen) {
                Screen.Submitted -> screen = Screen.Approved
                Screen.Splash, Screen.Review -> screen = Screen.Home
                else -> Unit
            }
        } else if (reviewStatus in setOf("PENDING", "SUSPENDED") || (reviewStatus == "REJECTED" && pending == null)) {
            if (pending != null) PhoneAuthApi.savePendingApplication(context, null)
            screen = Screen.Submitted
        } else if (pending != null) {
            val ids = pending.getJSONArray("documentIds")
            val docs = data?.optJSONArray("documents")
            val byId = (0 until (docs?.length() ?: 0)).associate { index ->
                val doc = requireNotNull(docs).getJSONObject(index)
                doc.getString("id") to doc.optString("scan_status")
            }
            val statuses = (0 until ids.length()).map { byId[ids.getString(it)] }
            screen = Screen.Submitted
            reviewStatus = "SCANNING"
            if (statuses.any { it == null || it == "FAILED" || it == "INFECTED" }) {
                PhoneAuthApi.savePendingApplication(context, null)
                app.draft.clearUploads()
                reviewStatus = "SCAN_FAILED"
                feedback = scanFailed
            } else if (statuses.all { it == "CLEAN" }) {
                try {
                    DriverOnboardingApi.submit(context, pending)
                    PhoneAuthApi.savePendingApplication(context, null)
                    app.draft = com.ridenow.driver.ui.registration.DriverDraft()
                    reviewStatus = "PENDING"
                } catch (error: PhoneAuthException) {
                    if (error.code !in setOf("APPLICATION_DOCUMENTS_NOT_READY", "APPLICATION_DOCUMENT_EXPIRY_MISMATCH")) throw error
                    PhoneAuthApi.savePendingApplication(context, null)
                    app.draft.clearUploads()
                    reviewStatus = "SCAN_FAILED"
                    feedback = scanFailed
                }
            }
        } else if (reviewStatus != "SCAN_FAILED") {
            if (!app.draft.acceptedTerms) DriverOnboardingApi.prefillPersonalDetails(context, app.draft)
            screen = Screen.Terms
        }
    }

    fun runOnboarding(request: suspend () -> Unit) {
        if (busy) return
        busy = true
        feedback = null
        scope.launch {
            try {
                request()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                when {
                    error is PhoneAuthException && error.status == 401 -> {
                        app.reset()
                        phone = ""; challengeId = ""; editing = false; bankReturn = null
                        Screen.entries.forEach { saveable.removeState(it.name) }
                        screen = Screen.Welcome
                    }
                    error.message == "INCOMPLETE_DRIVER_FORM" -> feedback = incompleteError
                    else -> feedback = activityError
                }
            } finally {
                busy = false
            }
        }
    }

    suspend fun restoreAuth() {
        val restoredPhone = PhoneAuthApi.restoreSession(context)
        if (restoredPhone == null) {
            screen = if (prefs.getBoolean("intro_seen", false)) Screen.Welcome else Screen.Intro
        } else {
            phone = restoredPhone
            checkStatus()
        }
    }
    val store = app.store
    val stack = app.stack
    val driverName = app.profileName.ifBlank { app.summaries[STEP_PERSONAL].orEmpty() }
    val initials = initialsOf(driverName)
    var selRide by rememberSaveable { mutableStateOf("") }
    var selTopic by rememberSaveable { mutableStateOf(-1) }
    var selDoc by rememberSaveable { mutableStateOf("") }
    var reportCat by rememberSaveable { mutableStateOf(0) }
    var editBankId by rememberSaveable { mutableStateOf(-1) }

    /** Opens a pushed screen and remembers where the driver came from. */
    fun go(target: Screen) { stack.add(screen); screen = target }
    /** Returns to the screen that opened the current one, or Home. */
    fun back() { screen = stack.removeLastOrNull() ?: Screen.Home }
    /** Switches tab and forgets the pushed screens. */
    fun tab(t: MainTab) { stack.clear(); screen = tabScreen(t) }
    val rideLifecycle = LocalLifecycleOwner.current.lifecycle
    var liveRide by remember(phone) { mutableStateOf<DriverRide?>(null) }
    var liveOffer by remember(phone) { mutableStateOf<DispatchOffer?>(null) }
    var liveError by remember(phone) { mutableStateOf<Int?>(null) }
    var liveStale by remember(phone) { mutableStateOf(false) }
    var liveBusy by remember(phone) { mutableStateOf(false) }
    var dismissedRide by remember(phone) { mutableStateOf<String?>(null) }
    var today by remember(phone) { mutableStateOf<EarningsPeriod?>(null) }
    var retryOperation by remember(phone) { mutableStateOf<Pair<String, String>?>(null) }

    suspend fun refreshRide() {
        val status = DriverRideApi.current(context)
        liveStale = false
        liveRide = status.ride
        val available = status.availability == "AVAILABLE"
        if (available && !app.online) app.goOnline()
        if (!available && app.online) app.goOffline()
        if (available || status.ride?.needsLocation == true) {
            if (DriverGps.permitted(context) && rideLifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                DriverTrackingService.start(context)
            }
        } else DriverTrackingService.stop(context)
        liveOffer = if (available && status.ride == null) DriverRideApi.offer(context) else null
        val ride = status.ride
        if (ride != null && ride.id != dismissedRide && screen == Screen.Home) screen = Screen.Trip
        if (ride == null && screen == Screen.Trip) screen = Screen.Home
    }

    fun runRideOperation(operation: String, action: suspend (String) -> Unit) {
        if (liveBusy) return
        liveBusy = true
        liveError = null
        val key = retryOperation?.takeIf { it.first == operation }?.second ?: UUID.randomUUID().toString()
        retryOperation = operation to key
        scope.launch {
            try {
                action(key)
                retryOperation = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (failure is PhoneAuthException && failure.status < 500) retryOperation = null
                liveError = driverRideError(failure)
            } finally {
                try { refreshRide() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { liveStale = true }
                finally { liveBusy = false }
            }
        }
    }

    fun changeOnline(available: Boolean) = runRideOperation("availability:$available") {
        if (!available) {
            DriverRideApi.availability(context, false)
            DriverTrackingService.stop(context)
        } else if (!DriverGps.publish(context)) {
            liveError = R.string.live_gps
        } else {
            DriverRideApi.availability(context, true)
            try { DriverTrackingService.start(context) }
            catch (failure: Exception) {
                DriverRideApi.availability(context, false)
                throw failure
            }
        }
    }

    fun respondToOffer(offer: DispatchOffer, accept: Boolean) = runRideOperation("offer:${offer.id}:$accept") { key ->
        DriverRideApi.respond(context, offer, accept, key)
        liveOffer = null
    }

    fun advanceRide(ride: DriverRide, action: String, pin: String?) = runRideOperation("${ride.id}:${ride.version}:$action:$pin") { key ->
        if (action in setOf("ARRIVE", "AT_STOP", "COMPLETE", "NO_SHOW") && !DriverGps.publish(context)) {
            liveError = R.string.live_gps
        } else if (action == "CANCEL") {
            DriverRideApi.cancel(context, ride, key)
            screen = Screen.Home
        } else {
            DriverRideApi.action(context, ride, action, key, pin)
            if (action == "CASH") screen = Screen.Home
        }
    }

    LaunchedEffect(phone, reviewStatus, rideLifecycle) {
        if (phone.isBlank() || reviewStatus != "APPROVED") return@LaunchedEffect
        rideLifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                if (!liveBusy) {
                    try { refreshRide() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { liveStale = true; liveOffer = null }
                }
                delay(if (app.online || liveRide != null) 2_500L else 7_000L)
            }
        }
    }

    LaunchedEffect(screen, phone, reviewStatus) {
        if (screen == Screen.Home && phone.isNotBlank() && reviewStatus == "APPROVED") {
            try { today = EarningsApi.summary(context).first() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { }
        }
    }
    fun signOut() = runOnboarding {
        try {
            if (reviewStatus == "APPROVED") {
                try { DriverRideApi.availability(context, false) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { }
            }
            DriverTrackingService.stop(context)
            DriverGps.reset()
            PhoneAuthApi.signOut(context)
        } finally {
            app.reset()
            phone = ""; challengeId = ""; editing = false; bankReturn = null
            reviewStatus = ""; reviewReason = ""
            Screen.entries.forEach { saveable.removeState(it.name) }
            prefs.edit().remove("phone_verified").remove("auth_onboarding_complete")
                .remove("terms_accepted").remove("application_submitted").apply()
            screen = Screen.Welcome
        }
    }
    fun openNotice(n: Notice) {
        when { n.icon == "wallet" -> go(Screen.Payouts); n.icon == "person" -> tab(MainTab.Home); n.icon == "check" -> tab(MainTab.Rides) }
    }

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

    // Permissions already granted on this device skip their screens. Re-checked at each step.
    fun backgroundOk(): Boolean = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q ||
        androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
    fun notificationsOk(): Boolean = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
    // Where the flow goes after the background-location step (or straight after Terms when that step is skipped).
    fun afterBackground(): Screen {
        if (!notificationsOk()) return Screen.Notifications
        app.summaries[STEP_NOTIFICATIONS] = notifOn
        return Screen.Personal
    }
    fun afterTerms(): Screen {
        if (!backgroundOk()) return Screen.Background
        app.summaries[STEP_BACKGROUND] = bgOn
        return afterBackground()
    }
    val reviewItems = listOf(
        ReviewItem(stringResource(R.string.review_row_terms), "$accepted \u00b7 ${app.summaries[STEP_BACKGROUND] ?: bgOff} \u00b7 ${app.summaries[STEP_NOTIFICATIONS] ?: notifOff}"),
        ReviewItem(stringResource(R.string.review_row_personal), app.summaries[STEP_PERSONAL] ?: dash),
        ReviewItem(stringResource(R.string.review_row_identity), bothSides),
        ReviewItem(stringResource(R.string.review_row_license), app.summaries[STEP_LICENSE] ?: dash),
        ReviewItem(stringResource(R.string.review_row_vehicle), app.summaries[STEP_VEHICLE] ?: dash),
        ReviewItem(stringResource(R.string.review_row_docs), docsDone),
        ReviewItem(stringResource(R.string.review_row_photos), app.summaries[STEP_PHOTOS] ?: dash),
        ReviewItem(stringResource(R.string.review_row_avail), app.summaries[STEP_AVAILABILITY] ?: dash),
        ReviewItem(stringResource(R.string.onboarding_service_area), app.summaries[STEP_AREA] ?: dash),
        ReviewItem(stringResource(R.string.review_row_bank), if (app.bankSaved) bankAdded else bankSkipped, warn = !app.bankSaved, add = !app.bankSaved),
    )
    val editTargets = listOf(
        Screen.Terms, Screen.Personal, Screen.Identity, Screen.License, Screen.Vehicle,
        Screen.VehicleDocs, Screen.VehiclePhotos, Screen.Availability, Screen.ServiceArea, Screen.Bank,
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
        if (back != null) { bankReturn = null; screen = Screen.valueOf(back) } else advance(Screen.ServiceArea)
    }

    LaunchedEffect(Unit) {
        if (SessionFlags.splashDone && screen == Screen.Splash) runOnboarding { restoreAuth() }
    }
    val inboxRide = stringResource(R.string.nc_ride_update)
    val inboxApplication = stringResource(R.string.nc_application_update)
    val inboxAccount = stringResource(R.string.nc_account_update)
    LaunchedEffect(phone, reviewStatus, screen) {
        if (phone.isBlank() || reviewStatus != "APPROVED" || screen != Screen.Home) return@LaunchedEffect
        try {
            val notices = loadDriverNotices(context, inboxRide, inboxApplication, inboxAccount)
            store.notices.clear()
            store.notices.addAll(notices)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            store.notices.clear()
        }
    }
    LaunchedEffect(screen, reviewStatus) {
        if (screen in setOf(Screen.DriverProfile, Screen.VehicleDetails, Screen.VehicleDocuments)) {
            runOnboarding { checkStatus() }
        } else if (screen == Screen.Submitted && reviewStatus in setOf("PENDING", "SCANNING")) {
            while (true) {
                delay(if (reviewStatus == "SCANNING") 15_000 else 30_000)
                runOnboarding { checkStatus() }
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalNavBadges provides emptySet(), LocalDriverDraft provides app.draft) {
        Crossfade(targetState = screen, animationSpec = tween(400), label = "root") { current ->
            // Keeps typed text, dates and choices when the driver goes back to a screen.
            saveable.SaveableStateProvider(current.name) {
                when (current) {
                    Screen.Splash -> if (feedback != null) {
                        com.ridenow.driver.ui.status.OnboardingStateScreen(
                            title = retryText, message = feedback.orEmpty(), primary = retryText,
                            onPrimary = { runOnboarding { restoreAuth() } }, secondary = signOutText, onSecondary = ::signOut, busy = busy,
                        )
                    } else SplashScreen(onFinished = { SessionFlags.splashDone = true; runOnboarding { restoreAuth() } })
                    Screen.Intro -> IntroScreen(onDone = { prefs.edit().putBoolean("intro_seen", true).commit(); screen = Screen.Welcome })
                    Screen.Welcome -> WelcomeScreen(onGetStarted = { screen = Screen.Phone }, onHaveAccount = { screen = Screen.Phone }, onHelp = { })
                    Screen.Phone -> {
                        BackHandler { screen = Screen.Welcome }
                        PhoneScreen(onBack = { screen = Screen.Welcome }, onCodeSent = { submittedPhone ->
                            phone = submittedPhone
                            challengeId = PhoneAuthApi.requestCode(context, submittedPhone)
                            screen = Screen.Otp
                        })
                    }
                    Screen.Otp -> {
                        BackHandler { screen = Screen.Phone }
                        OtpScreen(
                            phone = shownPhone,
                            challengeId = challengeId,
                            onBack = { screen = Screen.Phone },
                            onResend = { PhoneAuthApi.requestCode(context, phone) },
                            onVerify = { id, code -> PhoneAuthApi.verifyCode(context, phone, id, code) },
                            onVerifySecond = { token, code -> PhoneAuthApi.verifySecondStep(context, phone, token, code) },
                            onVerified = { screen = Screen.Splash; runOnboarding { checkStatus() } },
                        )
                    }
                    Screen.Terms -> {
                        BackHandler { signOut() }
                        TermsScreen(
                            onBack = ::signOut,
                            onAccept = { app.draft.acceptedTerms = true; app.summaries[STEP_TERMS] = accepted; advance(afterTerms()) },
                            onDecline = ::signOut,
                        )
                    }
                    Screen.Background -> {
                        BackHandler { screen = Screen.Terms }
                        BackgroundLocationScreen(
                            onBack = { screen = Screen.Terms },
                            onAllowed = { app.summaries[STEP_BACKGROUND] = bgOn; screen = afterBackground() },
                            onSkip = { app.summaries[STEP_BACKGROUND] = bgOff; screen = afterBackground() },
                        )
                    }
                    Screen.Notifications -> {
                        BackHandler { screen = Screen.Background }
                        NotificationPermissionScreen(
                            onBack = { screen = if (backgroundOk()) Screen.Terms else Screen.Background },
                            onDone = { on -> app.summaries[STEP_NOTIFICATIONS] = if (on) notifOn else notifOff; advance(Screen.Personal) },
                        )
                    }
                    Screen.Personal -> {
                        BackHandler { advance(Screen.Notifications) }
                        PersonalInfoScreen(onBack = { advance(if (!notificationsOk()) (if (backgroundOk()) Screen.Terms else Screen.Background) else Screen.Notifications) }, onDone = { app.summaries[STEP_PERSONAL] = it; advance(Screen.Identity) })
                    }
                    Screen.Identity -> {
                        BackHandler { advance(Screen.Personal) }
                        IdentityScreen(onBack = { advance(Screen.Personal) }, onDone = { advance(Screen.License) })
                    }
                    Screen.License -> {
                        BackHandler { advance(Screen.Identity) }
                        LicenseScreen(onBack = { advance(Screen.Identity) }, onDone = { app.summaries[STEP_LICENSE] = it; advance(Screen.Vehicle) })
                    }
                    Screen.Vehicle -> {
                        BackHandler { advance(Screen.License) }
                        VehicleScreen(onBack = { advance(Screen.License) }, onDone = { app.summaries[STEP_VEHICLE] = it; advance(Screen.VehicleDocs) })
                    }
                    Screen.VehicleDocs -> {
                        BackHandler { advance(Screen.Vehicle) }
                        VehicleDocsScreen(onBack = { advance(Screen.Vehicle) }, onDone = { advance(Screen.VehiclePhotos) })
                    }
                    Screen.VehiclePhotos -> {
                        BackHandler { advance(Screen.VehicleDocs) }
                        VehiclePhotosScreen(onBack = { advance(Screen.VehicleDocs) }, onDone = { app.summaries[STEP_PHOTOS] = it; advance(Screen.Availability) })
                    }
                    Screen.Availability -> {
                        BackHandler { advance(Screen.VehiclePhotos) }
                        AvailabilityScreen(onBack = { advance(Screen.VehiclePhotos) }, onDone = { app.summaries[STEP_AVAILABILITY] = it; advance(Screen.ServiceArea) })
                    }
                    Screen.ServiceArea -> {
                        BackHandler { advance(Screen.Availability) }
                        ServiceAreaScreen(onBack = { advance(Screen.Availability) }, onDone = { app.summaries[STEP_AREA] = it; advance(Screen.Bank) })
                    }
                    Screen.Bank -> {
                        BackHandler { backFromBank() }
                        BankAccountScreen(onBack = { backFromBank() }, onSaved = { h, b, br, n ->
                            if (bankReturn == null) {
                                app.draft.fields.put("bank", JSONObject().put("holder", h).put("bank", b).put("branch", br).put("number", n))
                            } else store.addBank(h, b, br, n)
                            finishBank(true)
                        }, onSkip = {
                            if (bankReturn == null) app.draft.fields.remove("bank")
                            finishBank(false)
                        })
                    }
                    Screen.Review -> {
                        BackHandler { if (!busy) screen = Screen.Bank }
                        ReviewScreen(
                            items = reviewItems,
                            onEdit = { i -> if (!busy) { feedback = null; editing = true; screen = editTargets[i] } },
                            onSubmit = { runOnboarding {
                                DriverOnboardingApi.queue(context, app.draft) { index, total ->
                                    feedback = String.format(uploadProgress, index, total)
                                }
                                checkStatus()
                                feedback = null
                            } },
                            onBack = { if (!busy) screen = Screen.Bank },
                            busy = busy, feedback = feedback,
                        )
                    }
                    Screen.Submitted -> {
                        val canEdit = reviewStatus in setOf("REJECTED", "SCAN_FAILED")
                        BackHandler { if (!busy) signOut() }
                        val message = when (reviewStatus) {
                            "SCANNING" -> scanWaiting
                            "SCAN_FAILED" -> scanFailed
                            "SUSPENDED" -> suspendedText
                            "REJECTED" -> rejectedText
                            else -> pendingText
                        }
                        com.ridenow.driver.ui.status.OnboardingStateScreen(
                            title = stringResource(R.string.onboarding_status_title),
                            message = listOfNotNull(message, reviewReason.takeIf { it.isNotBlank() }, feedback).distinct().joinToString("\n\n"),
                            primary = if (canEdit) editText else retryText,
                            onPrimary = {
                                if (canEdit) {
                                    feedback = null; reviewReason = ""; editing = false
                                    screen = if (app.draft.acceptedTerms && app.draft.vehicle != null) Screen.Review else Screen.Terms
                                } else runOnboarding { checkStatus() }
                            },
                            secondary = signOutText, onSecondary = ::signOut, busy = busy,
                        )
                    }
                    Screen.ActionRequired -> ActionRequiredScreen(
                        // Fixing returns to Review, then Submit sends the application again.
                        onFix = { i -> editing = true; screen = if (i == 0) Screen.Identity else Screen.VehicleDocs },
                        onUpdate = { editing = true; screen = Screen.Identity }, onSupport = { go(Screen.Tickets) },
                    )
                    Screen.Rejected -> RejectedScreen(onReapply = { editing = false; screen = Screen.Terms }, onSupport = { go(Screen.Tickets) })
                    Screen.Approved -> ApprovedScreen(onOpenHome = { screen = Screen.Home }, onExplore = { screen = Screen.Home })
                    Screen.Home -> {
                        HomeScreen(
                        online = app.online, bankSaved = app.bankSaved, driverName = driverName,
                        onlineSince = app.onlineSince, bankedMillis = app.bankedMillis,
                        onToggle = {
                            if (app.online) changeOnline(false)
                            else if (!locationOn) locDismissed = false
                            else if (!app.bankSaved) { bankReturn = Screen.Home.name; screen = Screen.Bank }
                            else changeOnline(true)
                        },
                        onAddBank = { bankReturn = Screen.Home.name; screen = Screen.Bank },
                        onTab = { tab(it) },
                        earnings = today?.total?.div(100.0), trips = today?.rideCount,
                        onActiveTrip = liveRide?.let { { dismissedRide = null; liveError = null; screen = Screen.Trip } },
                        problem = liveError ?: if (liveStale) R.string.live_connection else null,
                        unread = store.unreadCount, onBell = { go(Screen.NotificationCenter) }, onProfile = { go(Screen.DriverProfile) },
                        onCycleMap = { appearance.setMapStyle(MapStyle.entries[(appearance.mapStyle.ordinal + 1) % MapStyle.entries.size]) },
                    )
                        liveOffer?.let { offer -> LiveOfferSheet(offer, liveBusy, { respondToOffer(offer, it) }) }
                    }
                    Screen.Earnings -> {
                        BackHandler { tab(MainTab.Home) }
                        EarningsScreen(
                            app.onlineMinutes(), store.unreadCount, initials, { tab(it) }, { go(Screen.NotificationCenter) }, { go(Screen.DriverProfile) },
                            onSeeAll = { tab(MainTab.Rides) },
                        )
                    }
                    Screen.Rides -> {
                        BackHandler { tab(MainTab.Home) }
                        RideHistoryScreen(store.unreadCount, initials, { tab(it) }, { go(Screen.NotificationCenter) }, { go(Screen.DriverProfile) })
                    }
                    Screen.Support -> {
                        BackHandler { tab(MainTab.Home) }
                        SupportCenterScreen(
                            store.unreadCount, initials, { tab(it) }, { go(Screen.NotificationCenter) }, { go(Screen.DriverProfile) },
                            onTopic = { selTopic = it; go(Screen.Faq) }, onContact = { go(Screen.SupportChat) }, onTickets = { go(Screen.Tickets) }, onFaqs = { selTopic = -1; go(Screen.Faq) },
                            onReport = { reportCat = -1; go(Screen.ReportIssue) },
                        )
                    }
                    Screen.Trip, Screen.TripComplete -> {
                        val trip = liveRide
                        if (trip != null) LiveDriverTrip(trip, liveBusy, liveStale, liveError,
                            onBack = { dismissedRide = trip.id; liveError = null; screen = Screen.Home },
                            onAction = { action, pin -> advanceRide(trip, action, pin) })
                    }
                    Screen.DriverProfile -> {
                        BackHandler { back() }
                        DriverProfileScreen(
                            store, driverName, shownPhone, app.profileEmail, app.profileAddress, reviewStatus == "APPROVED",
                            onBack = { back() }, onEdit = { go(Screen.EditProfile) }, onVehicleDetails = { go(Screen.VehicleDetails) }, onDocs = { go(Screen.VehicleDocuments) },
                        )
                    }
                    Screen.EditProfile -> {
                        BackHandler { back() }
                        EditProfileScreen(
                            store, driverName, shownPhone, app.profileEmail, app.profileAddress, onBack = { back() },
                            onSave = { edits ->
                                val body = JSONObject().put("name", edits.name).put("email", edits.email)
                                    .put("address", edits.address).put("preferredLanguage", edits.language)
                                edits.dateOfBirth.takeIf(String::isNotBlank)?.let { body.put("dateOfBirth", it) }
                                edits.identityNumber.takeIf(String::isNotBlank)?.let { body.put("identityNumber", it) }
                                if (edits.photoChanged) {
                                    edits.photo?.let { DriverOnboardingApi.replaceProfilePhoto(context, it) }
                                    body.put("photoVisible", edits.photo != null)
                                }
                                val contact = DriverOnboardingApi.updateContact(context, body)
                                app.profileName = contact.getString("name")
                                app.profileEmail = contact.optString("email")
                                app.profileAddress = contact.optString("address")
                                store.prefLanguage = contact.optString("preferredLanguage")
                                if (edits.photoChanged) {
                                    store.photo = edits.photo
                                    store.photoSource = null
                                }
                                back()
                            },
                            onPhoneVerified = { phone = it },
                            onReviewIdentityChange = { dob, nic ->
                                proposedIdentity = dob to nic
                                confirmReapplication = true
                            },
                        )
                    }
                    Screen.Account -> {
                        BackHandler { tab(MainTab.Home) }
                        AccountScreen(
                            store, driverName, shownPhone, app.online, app.bankSaved, store.unreadCount, { tab(it) }, { go(Screen.NotificationCenter) },
                            onProfile = { go(Screen.DriverProfile) }, onVehicle = { go(Screen.VehicleDetails) }, onDocuments = { go(Screen.VehicleDocuments) },
                            onPayouts = { go(Screen.Payouts) }, onBank = { go(Screen.BankManage) }, onSettings = { go(Screen.Settings) },
                            onNotificationSettings = { go(Screen.NotificationSettings) }, onHelpSafety = { go(Screen.HelpSafety) }, onLegal = { go(Screen.Legal) },
                            onGoOffline = { changeOnline(false) }, onSignOut = { signOut() },
                        )
                    }
                    Screen.NotificationCenter -> { BackHandler { back() }; NotificationsScreen(store, { back() }, { openNotice(it) }) }
                    Screen.EarningsDetails -> {
                        BackHandler { back() }
                        val ride = store.rides.firstOrNull { it.id == selRide }
                        if (ride == null) LaunchedEffect(Unit) { back() } else EarningsDetailsScreen(ride, store.sample, { back() }, { reportCat = 0; go(Screen.ReportIssue) })
                    }
                    Screen.RideDetails -> {
                        BackHandler { back() }
                        val ride = store.rides.firstOrNull { it.id == selRide }
                        if (ride == null) LaunchedEffect(Unit) { back() } else RideDetailsScreen(ride, store.sample, { back() }, { reportCat = 0; go(Screen.ReportIssue) })
                    }
                    Screen.VehicleDetails -> {
                        BackHandler { back() }
                        VehicleDetailsScreen(store, { back() }, { go(Screen.VehicleDocuments) },
                            onUpdate = { confirmReapplication = true })
                    }
                    Screen.VehicleDocuments -> { BackHandler { back() }; VehicleDocumentsScreen(store, { back() }, { selTopic = 1; go(Screen.Faq) },
                        onUpdate = { confirmReapplication = true }) }
                    Screen.Payouts -> { BackHandler { back() }; PayoutsScreen(store, { back() }, { selTopic = 2; go(Screen.Faq) }, { editBankId = -1; go(Screen.BankForm) }) }
                    Screen.BankManage -> {
                        BackHandler { back() }
                        BankAccountManageScreen(
                            store, { back() }, { selTopic = 2; go(Screen.Faq) }, onEdit = { editBankId = it.id; go(Screen.BankForm) },
                            onAdd = { editBankId = -1; go(Screen.BankForm) }, onRemoved = {
                                DriverPayoutApi.remove(context)
                                store.banks.clear(); app.bankSaved = false
                                if (app.online) changeOnline(false)
                            },
                        )
                    }
                    Screen.BankForm -> {
                        BackHandler { back() }
                        val existing = store.banks.firstOrNull { it.id == editBankId }
                        BankFormScreen(existing, { back() }, onSave = { h, b, br, n, t ->
                            val type = if (t == context.getString(R.string.bank_current)) "Current" else "Savings"
                            val saved = DriverPayoutApi.save(context, PayoutBank(h, b, br, n, type))
                            store.banks.clear()
                            store.banks.add(BankAccountInfo(1, saved.holder, saved.bank, saved.number, saved.branch, saved.type, verified = false))
                            app.bankSaved = true; back()
                        })
                    }
                    Screen.Settings -> {
                        BackHandler { back() }
                        SettingsScreen(
                            store, driverName, shownPhone, appearance.mode, { back() }, onProfile = { go(Screen.DriverProfile) }, onAppearance = { go(Screen.Appearance) },
                            onLanguage = { go(Screen.LanguageSettings) }, onNotifications = { go(Screen.NotificationSettings) }, onPrivacy = { go(Screen.Privacy) },
                            onPaymentMethods = { go(Screen.PaymentMethods) }, onBank = { go(Screen.BankManage) }, onPayouts = { go(Screen.Payouts) },
                            onHelp = { go(Screen.HelpSafety) }, onTickets = { go(Screen.Tickets) }, onAbout = { go(Screen.Legal) }, onSignOut = { signOut() },
                        )
                    }
                    Screen.Appearance -> { BackHandler { back() }; AppearanceScreen(appearance, { back() }) }
                    Screen.LanguageSettings -> { BackHandler { back() }; LanguageScreen { back() } }
                    Screen.NotificationSettings -> { BackHandler { back() }; NotificationSettingsScreen({ back() }) }
                    Screen.Privacy -> {
                        BackHandler { back() }
                        PrivacySecurityScreen({ back() }, onPersonalData = { go(Screen.DriverProfile) }, onDelete = { go(Screen.DeleteAccount) }, onDevices = { go(Screen.ManageDevices) }, onTwoStep = { go(Screen.TwoStep) }, onDownload = { go(Screen.DownloadData) })
                    }
                    Screen.ManageDevices -> { BackHandler { back() }; ManageDevicesScreen({ back() }, onSessionExpired = { signOut() }) }
                    Screen.TwoStep -> { BackHandler { back() }; TwoStepScreen(shownPhone, { back() }) }
                    Screen.DownloadData -> { BackHandler { back() }; DownloadDataScreen({ back() }) }
                    Screen.PaymentMethods -> { BackHandler { back() }; PaymentMethodsScreen(store, { back() }, { go(Screen.BankManage) }) }
                    Screen.DeleteAccount -> {
                        BackHandler { back() }
                        val requested = stringResource(R.string.priv_download_done)
                        DeleteAccountScreen(
                            { back() }, onDownload = { toast(context, requested) }, onGoOffline = { changeOnline(false); tab(MainTab.Home) },
                            onDelete = {
                                prefs.edit().clear().apply()
                                DriverTrackingService.stop(context)
                                app.reset()
                                phone = ""; challengeId = ""; editing = false; bankReturn = null
                                reviewStatus = ""; reviewReason = ""
                                Screen.entries.forEach { saveable.removeState(it.name) }
                                screen = Screen.Welcome
                            },
                            phone = shownPhone,
                        )
                    }
                    Screen.Faq -> { BackHandler { back() }; FaqScreen(selTopic, { back() }, { go(Screen.NewTicket) }) }
                    Screen.Tickets, Screen.Conversation -> SupportWorkspaceScreen(SupportEntry.LIST, { back() },
                        chrome = { header ->
                            if (header) MainHeader(store.unreadCount, initials, { go(Screen.NotificationCenter) }, { go(Screen.DriverProfile) })
                            else MainBottomBar(MainTab.Support, { tab(it) })
                        })
                    Screen.NewTicket -> SupportWorkspaceScreen(SupportEntry.TICKET, { back() })
                    Screen.SupportChat -> SupportWorkspaceScreen(SupportEntry.CHAT, { back() })
                    Screen.HelpSafety -> {
                        BackHandler { back() }
                        HelpSafetyScreen(
                            { back() }, onTopic = { selTopic = it; go(Screen.Faq) }, onSafetyCenter = { go(Screen.SafetyCenter) }, onEmergency = { go(Screen.Emergency) },
                            onShareTrip = { go(Screen.ShareTrip) }, onReportSafety = { reportCat = 5; go(Screen.ReportIssue) }, onContact = { go(Screen.SupportChat) },
                        )
                    }
                    Screen.SafetyCenter -> { BackHandler { back() }; SafetyCenterScreen({ back() }, { go(Screen.Emergency) }, { go(Screen.ShareTrip) }, { reportCat = 5; go(Screen.ReportIssue) }) }
                    Screen.Emergency -> { BackHandler { back() }; EmergencyAssistScreen({ back() }, { reportCat = 5; go(Screen.ReportIssue) }) }
                    Screen.ShareTrip -> { BackHandler { back() }; ShareTripScreen({ back() }) }
                    Screen.ReportIssue -> SupportWorkspaceScreen(
                        if (reportCat == 5) SupportEntry.SAFETY else SupportEntry.COMPLAINT, { back() },
                        categories = if (reportCat == 5) listOf(R.string.cat_safety) else listOf(
                            R.string.cat_ride, R.string.cat_payments, R.string.cat_account, R.string.cat_maps,
                            R.string.cat_bug, R.string.cat_safety, R.string.cat_service, R.string.cat_other,
                        ),
                        initialCategory = if (reportCat == 5) 0 else reportCat,
                    )
                    Screen.Legal -> { BackHandler { back() }; LegalAboutScreen({ back() }, { selDoc = it; go(Screen.LegalDoc) }) }
                    Screen.LegalDoc -> {
                        BackHandler { back() }
                        val doc = LegalDocs.find(selDoc)
                        if (doc == null) LaunchedEffect(Unit) { back() } else LegalDocScreen(doc, { back() })
                    }
                }
            }
        }
        }
        if (confirmReapplication) AlertDialog(
            onDismissRequest = {
                if (!busy) {
                    proposedIdentity = null
                    confirmReapplication = false
                }
            },
            title = { Text(stringResource(R.string.driver_start_review)) },
            text = {
                Text(feedback ?: stringResource(R.string.driver_reapplication_notice))
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    runOnboarding {
                        try {
                            DriverOnboardingApi.beginReapplication(context)
                        } catch (error: PhoneAuthException) {
                            if (error.status == 401) confirmReapplication = false
                            throw error
                        }
                        confirmReapplication = false
                        PhoneAuthApi.savePendingApplication(context, null)
                        DriverTrackingService.stop(context)
                        app.goOffline()
                        app.draft = com.ridenow.driver.ui.registration.DriverDraft()
                        app.summaries.clear()
                        app.draft.fields.put("name", app.profileName).put("email", app.profileEmail)
                        val (dob, nic) = proposedIdentity ?: (app.store.dob to app.store.nic)
                        if (dob.isNotBlank()) app.draft.fields.put("dateOfBirth", dob)
                        if (nic.isNotBlank()) app.draft.fields.put("identity", JSONObject().put("number", nic))
                        proposedIdentity = null
                        app.draft.setPhoto("PROFILE_PHOTO", app.store.photo)
                        Screen.entries.forEach { saveable.removeState(it.name) }
                        editing = false
                        app.stack.clear()
                        screen = Screen.Terms
                    }
                }) { Text(stringResource(R.string.driver_start_review)) }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = {
                    feedback = null
                    proposedIdentity = null
                    confirmReapplication = false
                }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )
        // Full-screen states shown over everything, in this order of priority.
        val inApp = screen in setOf(Screen.Home, Screen.Earnings, Screen.Rides, Screen.Support, Screen.Account, Screen.Trip)
        val onboardingUsesLocation = screen in setOf(Screen.Background, Screen.Personal, Screen.ServiceArea)
        val needsLocation = when {
            onboardingUsesLocation -> !onboardingLocationDismissed
            inApp || screen == Screen.ShareTrip -> !locDismissed
            else -> false
        }
        if (!hasInternet && !offlineSkip && screen != Screen.Splash && screen != Screen.Intro) {
            BackHandler { }
            NoInternetScreen(onRetry = { netKey++ }, onOpenRides = { offlineSkip = true; tab(MainTab.Rides) })
        } else if (!locationOn && needsLocation) {
            val dismissLocation = {
                if (onboardingUsesLocation) onboardingLocationDismissed = true else locDismissed = true
            }
            BackHandler(onBack = dismissLocation)
            LocationOffScreen(onNotNow = dismissLocation)
        } else if (inApp && !notificationsOn && !ntfDismissed) {
            BackHandler { ntfDismissed = true }
            NotificationsOffScreen(onNotNow = { ntfDismissed = true })
        }
    }
}

private fun tabScreen(tab: MainTab): Screen = when (tab) {
    MainTab.Home -> Screen.Home
    MainTab.Earnings -> Screen.Earnings
    MainTab.Rides -> Screen.Rides
    MainTab.Support -> Screen.Support
    MainTab.Account -> Screen.Account
}
