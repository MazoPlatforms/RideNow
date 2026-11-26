package com.ridenow.app.ui

import com.ridenow.app.ui.components.localizedLabel

import com.ridenow.app.R
import androidx.compose.ui.res.stringResource

import androidx.activity.compose.BackHandler
import com.ridenow.app.ui.more.*
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import com.ridenow.app.ui.map.LatLng
import com.ridenow.app.ui.map.SifricApi
import com.ridenow.app.ui.map.RouteInfo
import com.ridenow.app.ui.map.rememberUserLocation
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.ridenow.app.ui.auth.formatPhoneForDisplay
import com.ridenow.app.ui.auth.DetailsScreen
import com.ridenow.app.ui.auth.OtpScreen
import com.ridenow.app.ui.auth.PhoneScreen
import com.ridenow.app.ui.auth.PhoneAuthApi
import com.ridenow.app.ui.auth.PhoneAuthException
import com.ridenow.app.ui.booking.ConfirmScreen
import com.ridenow.app.ui.booking.FindingDriverScreen
import com.ridenow.app.ui.booking.RideOptionsScreen
import com.ridenow.app.ui.booking.ScheduleScreen
import com.ridenow.app.ui.booking.Vehicles
import com.ridenow.app.ui.booking.fareFor
import com.ridenow.app.ui.home.HomeScreen
import com.ridenow.app.ui.help.HelpScreen
import com.ridenow.app.ui.intro.IntroScreen
import com.ridenow.app.ui.system.*
import androidx.compose.foundation.layout.Box as LayoutBox
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalContext
import com.ridenow.app.ui.legal.DocScreen
import com.ridenow.app.ui.legal.LegalScreen
import com.ridenow.app.ui.menu.MenuDrawer
import com.ridenow.app.ui.notifications.NotificationsScreen
import com.ridenow.app.ui.booking.MapPickerScreen
import com.ridenow.app.ui.safety.Contact
import com.ridenow.app.ui.safety.SafetyCenterScreen
import com.ridenow.app.ui.safety.TrustedContactsScreen
import com.ridenow.app.ui.saved.AddAddressScreen
import com.ridenow.app.ui.saved.SavedAddressesScreen
import com.ridenow.app.ui.saved.SavedAddress
import com.ridenow.app.ui.saved.RiderSettingsApi
import com.ridenow.app.ui.search.ChangePickupScreen
import com.ridenow.app.ui.search.FriendScreen
import com.ridenow.app.ui.share.ShareTripScreen
import com.ridenow.app.ui.support.ContactSupportScreen
import com.ridenow.app.ui.post.ReportProblemScreen
import com.ridenow.app.ui.support.SupportEntry
import com.ridenow.app.ui.support.SupportWorkspaceScreen
import androidx.compose.runtime.mutableStateListOf
import com.ridenow.app.ui.payments.PaymentsScreen
import com.ridenow.app.ui.permissions.LocationPermissionScreen
import com.ridenow.app.ui.permissions.RiderLocationOffScreen
import com.ridenow.app.ui.permissions.deviceLocationEnabled
import com.ridenow.app.ui.permissions.rememberDeviceLocationEnabled
import com.ridenow.app.ui.permissions.NotificationPermissionScreen
import com.ridenow.app.ui.post.RateDriverScreen
import com.ridenow.app.ui.post.RideCompleteScreen
import com.ridenow.app.ui.profile.EditProfileScreen
import com.ridenow.app.ui.profile.ProfileScreen
import com.ridenow.app.ui.rides.MyRidesScreen
import com.ridenow.app.ui.search.SearchScreen
import com.ridenow.app.ui.settings.SettingsScreen
import com.ridenow.app.ui.splash.SplashScreen
import com.ridenow.app.ui.theme.RideTheme
import com.ridenow.app.ui.theme.ThemeMode
import com.ridenow.app.ui.trip.TripScreen
import com.ridenow.app.ui.welcome.WelcomeScreen

enum class Screen {
    Splash, Welcome, Phone, Otp, Details, Location, Notifications, Home, Search, Options, Schedule, Confirm, Finding, Trip,
    Complete, Rate, Report, ReportDraft, Profile, EditProfile, MyRides, Payments, Settings, Intro, Loading, Expired, ServerError, Notifs, Saved, Help, Legal, Doc, Support, Chat, Safety, Contacts, MapPick, ShareTrip, ChangePickup, Friend, AddAddr, ReportIssue, Stub,
}

/** Survives activity recreation within one app process, so the splash does not replay on rotation or theme changes. */
private object SessionFlags { var splashDone = false }

@Composable
fun AppRoot(themeMode: ThemeMode, onThemeMode: (ThemeMode) -> Unit) {
    val textAlexMorgan = stringResource(R.string.text_alex_morgan)
    val textAna = stringResource(R.string.text_ana)
    val textBen = stringResource(R.string.text_ben)
    val text128MeridianWalk = stringResource(R.string.text_text_128_meridian_walk)
    val text128MeridianWalkSector7 = stringResource(R.string.text_text_128_meridian_walk_sector_7)
    val textNovaTerminalGate4 = stringResource(R.string.text_nova_terminal_gate_4)
    val textSector12OrbitRing = stringResource(R.string.text_sector_12_orbit_ring)
    val textHelixGardenPlaza = stringResource(R.string.text_helix_garden_plaza)
    val textSector9GardenLoop = stringResource(R.string.text_sector_9_garden_loop)
    val textAuroraMedicalCentre = stringResource(R.string.text_aurora_medical_centre)
    val textSector3SkylineWay = stringResource(R.string.text_sector_3_skyline_way)
    val textSetPickup = stringResource(R.string.text_set_pickup)
    val textPickupConfirm = stringResource(R.string.pickup_confirm)
    val textMapSetDrop = stringResource(R.string.map_set_drop)
    val textSavedPlace = stringResource(R.string.text_saved_place)
    val textValueScreenGoesHere = stringResource(R.string.text_value_screen_goes_here)

    // Do not restore an authenticated navigation destination from Android saved state; a new process must revalidate its server session.
    var screen by remember { mutableStateOf(Screen.Splash) }
    var restoreRequested by remember { mutableStateOf(SessionFlags.splashDone) }
    var phone by rememberSaveable { mutableStateOf("") }
    var challengeId by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf(textAlexMorgan) }
    var email by rememberSaveable { mutableStateOf("") }
    var profileComplete by remember { mutableStateOf(false) }
    var profileDetails by remember { mutableStateOf(PhoneAuthApi.ProfileDetails()) }
    var profilePhoto by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var stub by rememberSaveable { mutableStateOf("") }
    var stubBack by rememberSaveable { mutableStateOf(Screen.Home) }
    var dest by rememberSaveable { mutableStateOf("") }
    var isReturn by rememberSaveable { mutableStateOf(false) }
    var vehicle by rememberSaveable { mutableStateOf(1) }
    var rating by rememberSaveable { mutableStateOf(0) }
    var scheduled by rememberSaveable { mutableStateOf<String?>(null) }
    var scheduleFrom by rememberSaveable { mutableStateOf(Screen.Home) }
    var drawer by rememberSaveable { mutableStateOf(false) }
    val backStack = remember { mutableStateListOf<Screen>() }
    val back = { screen = backStack.removeLastOrNull() ?: Screen.Home }
    var doc by rememberSaveable { mutableStateOf("") }
    val contacts = remember { mutableStateListOf<Contact>() }
    val scope = rememberCoroutineScope()
    val locationOn = rememberDeviceLocationEnabled()
    var locationDismissed by rememberSaveable(locationOn) { mutableStateOf(false) }
    var locationRequested by remember(screen, locationOn) { mutableStateOf(false) }
    val showLocationOff = !locationOn && (locationRequested || (!locationDismissed && screen in setOf(
        Screen.Location, Screen.Home, Screen.Search, Screen.Options, Screen.Confirm, Screen.Finding,
        Screen.Trip, Screen.MapPick, Screen.ChangePickup, Screen.ShareTrip,
    )))
    val dismissLocationOff = {
        locationDismissed = true
        locationRequested = false
        if (screen == Screen.Location) screen = Screen.Notifications
    }
    val context = LocalContext.current
    val me = rememberUserLocation(locationOn = locationOn)
    var destLat by rememberSaveable { mutableStateOf(0.0) }
    var destLng by rememberSaveable { mutableStateOf(0.0) }
    val destLL = if (destLat != 0.0 || destLng != 0.0) LatLng(destLat, destLng) else null
    var pickupPoint by rememberSaveable { mutableStateOf<LatLng?>(null) }
    val routePickup = pickupPoint ?: me.position
    var routeInfo by remember { mutableStateOf<RouteInfo?>(null) }
    LaunchedEffect(destLL, routePickup, vehicle, isReturn) {
        routeInfo = null
        val category = listOf("TUK", "CAR", "VAN").getOrElse(vehicle) { "CAR" }
        destLL?.let { routeInfo = SifricApi.route(context, routePickup, it, category, isReturn) }
    }
    val pickDest = { name: String, placeId: String?, ll: LatLng? ->
        dest = name; destLat = 0.0; destLng = 0.0
        scope.launch {
            val p = ll ?: (placeId?.let { SifricApi.placeLatLng(context, it) } ?: SifricApi.geocode(context, name))
            if (p != null) { destLat = p.latitude; destLng = p.longitude }
        }
        Unit
    }
    val prefs = remember { context.getSharedPreferences("ridenow", 0) }
    val allowCurrentLocation = {
        deviceLocationEnabled(context).also { enabled -> if (!enabled) locationRequested = true }
    }
    suspend fun loadProfilePhoto(url: String?): android.graphics.Bitmap? {
        if (url == null) return null
        return try { PhoneAuthApi.downloadProfilePhoto(url) }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { null }
    }
    suspend fun restoreAuth() {
        val restoredProfile = PhoneAuthApi.restoreSession(context)
        if (restoredProfile != null) {
            phone = restoredProfile.phone
            name = restoredProfile.name
            email = restoredProfile.email
            profileComplete = restoredProfile.profileComplete
            profileDetails = restoredProfile.details
            profilePhoto = loadProfilePhoto(restoredProfile.profilePhotoUrl)
            screen = if (profileComplete) Screen.Home else Screen.Details
            return
        }
        screen = if (prefs.getBoolean("intro_seen", false)) Screen.Welcome else Screen.Intro
    }
    suspend fun restoreAuthSafely() {
        try { restoreAuth() }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { screen = Screen.ServerError }
    }
    LaunchedEffect(restoreRequested) {
        if (restoreRequested) restoreAuthSafely()
    }
    var netKey by remember { mutableIntStateOf(0) }
    val online = rememberOnline(netKey)
    var pickup by rememberSaveable { mutableStateOf(text128MeridianWalk) }
    val savedAddresses = remember { mutableStateListOf<SavedAddress>() }
    val home = savedAddresses.firstOrNull { it.kind == "HOME" }
    val work = savedAddresses.firstOrNull { it.kind == "WORK" }
    var friend by rememberSaveable { mutableStateOf<String?>(null) }
    var mapFor by rememberSaveable { mutableStateOf("drop") }
    var addLabel by rememberSaveable { mutableStateOf("Home") }
    var editAddress by remember { mutableStateOf<SavedAddress?>(null) }
    var pendingRemove by remember { mutableStateOf<SavedAddress?>(null) }
    val settingsErrorText = stringResource(R.string.saved_settings_failed)
    var settingsError by remember { mutableStateOf(false) }
    var syncingSettings by remember { mutableStateOf(false) }
    suspend fun syncSettings() {
        if (phone.isBlank() || !profileComplete || syncingSettings) return
        syncingSettings = true
        try {
            val currentPhone = phone
            val settings = RiderSettingsApi.load(context)
            if (phone == currentPhone && profileComplete) {
                savedAddresses.clear()
                savedAddresses.addAll(settings.addresses)
                contacts.clear()
                contacts.addAll(settings.contacts)
                profileDetails = profileDetails.copy(city = settings.city)
                settingsError = false
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            settingsError = true
        } finally { syncingSettings = false }
    }
    LaunchedEffect(phone, profileComplete) {
        if (phone.isBlank() || !profileComplete) {
            savedAddresses.clear()
            contacts.clear()
            settingsError = false
        } else syncSettings()
    }
    LaunchedEffect(screen, stub) {
        if (screen in setOf(Screen.Saved, Screen.Contacts) || (screen == Screen.Stub && stub == "Current city")) syncSettings()
    }
    val nav = { to: Screen -> backStack.add(screen); screen = to }
    fun openAddress(kind: String, address: SavedAddress?) {
        addLabel = kind
        editAddress = address
        nav(Screen.AddAddr)
    }
    fun chooseAddress(address: SavedAddress) {
        pickDest(address.address, null, address.point)
        isReturn = false
        screen = Screen.Options
    }
    fun quickAddress(kind: String) {
        val address = savedAddresses.firstOrNull { it.kind == kind }
        if (address == null) openAddress(kind, null) else chooseAddress(address)
    }

    val shownPhone = formatPhoneForDisplay(phone.ifBlank { "+1 555 012 3456" })
    var reportSubject by rememberSaveable { mutableStateOf("") }
    var reportMessage by rememberSaveable { mutableStateOf("") }
    val open = { label: String -> if (label == "Report an issue") nav(Screen.ReportIssue) else { stubBack = screen; stub = label; screen = Screen.Stub }; Unit }
    // Opens a destination by its menu or settings title; unknown titles show a placeholder.
    val go = { label: String ->
        drawer = false
        when (label) {
            "Profile" -> screen = Screen.Profile
            "Your Rides" -> screen = Screen.MyRides
            "Payments" -> screen = Screen.Payments
            "Settings" -> screen = Screen.Settings
            "Notifications" -> nav(Screen.Notifs)
            "Saved Places", "Saved addresses" -> nav(Screen.Saved)
            "Help & Support" -> nav(Screen.Help)
            "Contact support" -> nav(Screen.Support)
            "Safety center" -> nav(Screen.Safety)
            "Trusted contacts" -> nav(Screen.Contacts)
            "About RideNow", "Legal & About", "Terms & Policies" -> nav(Screen.Legal)
            else -> open(label)
        }
        Unit
    }

    val moreNav = MoreNav(
        back = { screen = stubBack }, exit = { screen = stubBack }, open = { open(it) }, home = { screen = Screen.Home }, options = { screen = Screen.Options },
        schedule = { scheduleFrom = Screen.Options; screen = Screen.Schedule }, finding = { screen = Screen.Finding }, trip = { screen = Screen.Trip },
        complete = { rating = 0; screen = Screen.Complete }, report = { nav(Screen.Report) }, support = { nav(Screen.Support) }, safety = { nav(Screen.Safety) },
        setPhone = { phone = it }, setVehicle = { vehicle = it }, setScheduled = { scheduled = it }, confirm = { screen = Screen.Confirm },
        changeDrop = { pickDest(it, null, null) }, cancelScheduled = { scheduled = null; screen = Screen.Home }, signedOut = {
            phone = ""; name = textAlexMorgan; email = ""; profilePhoto = null
            profileDetails = PhoneAuthApi.ProfileDetails(); profileComplete = false
            challengeId = ""; dest = ""; destLat = 0.0; destLng = 0.0
            friend = null; pickup = text128MeridianWalk; pickupPoint = null; scheduled = null
            contacts.clear(); savedAddresses.clear(); backStack.clear(); drawer = false
            com.ridenow.app.ui.support.TicketStore.tickets.clear()
            com.ridenow.app.ui.support.TicketStore.selectedId = null
            screen = Screen.Welcome
        },
    )
    Box(Modifier.fillMaxSize()) {
    pendingRemove?.let { item ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text(stringResource(R.string.text_remove)) },
            text = { Text(item.address) },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { pendingRemove = null }) {
                Text(stringResource(R.string.two_step_cancel))
            } },
            confirmButton = { androidx.compose.material3.TextButton(onClick = {
                pendingRemove = null
                scope.launch {
                    try {
                        RiderSettingsApi.deleteAddress(context, item.id)
                        savedAddresses.removeAll { it.id == item.id }
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: Exception) { settingsError = true }
                }
            }) { Text(stringResource(R.string.text_remove)) } },
        )
    }
    Crossfade(targetState = screen, animationSpec = tween(400), label = "root") { current ->
        when (current) {
            Screen.Splash -> SplashScreen(onFinished = { SessionFlags.splashDone = true; restoreRequested = true })
            Screen.Intro -> IntroScreen(onDone = { prefs.edit().putBoolean("intro_seen", true).commit(); screen = Screen.Welcome })
            Screen.Loading -> LoadingScreen(onDone = { screen = Screen.Home })
            Screen.Expired -> SessionExpiredScreen(name, shownPhone, onSendCode = { screen = Screen.Phone }, onOther = { phone = ""; screen = Screen.Phone })
            Screen.ServerError -> ServerErrorScreen("8F3K-2Q-500", onRetry = { scope.launch { restoreAuthSafely() } }, onContact = { nav(Screen.Support) })
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
                suspend fun acceptLogin(profile: PhoneAuthApi.RiderProfile) {
                    phone = profile.phone
                    name = profile.name
                    email = profile.email
                    profileComplete = profile.profileComplete
                    profileDetails = profile.details
                    profilePhoto = loadProfilePhoto(profile.profilePhotoUrl)
                }
                OtpScreen(
                    phone = shownPhone,
                    challengeId = challengeId,
                    onBack = { screen = Screen.Phone },
                    onResend = { PhoneAuthApi.requestCode(context, phone) },
                    onVerify = { id, code -> acceptLogin(PhoneAuthApi.verifyCode(context, id, code)) },
                    onVerifySecond = { token, code -> acceptLogin(PhoneAuthApi.verifySecondStep(context, token, code)) },
                    onVerified = { screen = if (profileComplete) Screen.Home else Screen.Details },
                )
            }
            Screen.Details -> {
                BackHandler { screen = Screen.Otp }
                DetailsScreen(onBack = { screen = Screen.Otp }, onDone = { n, e, photo ->
                    try {
                        val profile = PhoneAuthApi.saveProfile(context, n, e, photo)
                        phone = profile.phone
                        name = profile.name
                        email = profile.email
                        profileComplete = profile.profileComplete
                        profileDetails = profile.details
                        if (photo != null) profilePhoto = photo
                        screen = Screen.Location
                    } catch (failure: Exception) {
                        if (failure is PhoneAuthException && failure.status == 401) screen = Screen.Welcome
                        throw failure
                    }
                })
            }
            Screen.Location -> {
                BackHandler { screen = Screen.Details }
                if (!showLocationOff) LocationPermissionScreen(onDone = { screen = Screen.Notifications })
            }
            Screen.Notifications -> {
                BackHandler { screen = Screen.Location }
                NotificationPermissionScreen(onDone = { screen = Screen.Loading })
            }
            Screen.Home -> Box(Modifier.fillMaxSize()) {
                HomeScreen(
                    onMenu = { drawer = true }, onNotifications = { nav(Screen.Notifs) },
                    onSearch = { screen = Screen.Search }, onLater = { scheduleFrom = Screen.Home; screen = Screen.Schedule },
                    onEditPickup = { nav(Screen.ChangePickup) }, onSaved = { quickAddress(it.uppercase(java.util.Locale.ROOT)) }, pickup = pickup,
                    onRecent = { pickDest(it, null, null); isReturn = false; screen = Screen.Options },
                )
                MenuDrawer(
                    open = drawer, name = name, phone = shownPhone, onClose = { drawer = false },
                    onProfile = { go("Profile") }, onItem = { go(it) },
                    onSignOut = { drawer = false; phone = ""; profilePhoto = null; scope.launch { runCatching { PhoneAuthApi.signOut(context) } }; screen = Screen.Welcome },
                )
            }
            Screen.Search -> {
                BackHandler { scheduled = null; screen = Screen.Home }
                SearchScreen(
                    onBack = { scheduled = null; screen = Screen.Home }, scheduled = scheduled,
                    onPlace = { place, ret -> pickDest(place.name, place.placeId, null); isReturn = ret; screen = Screen.Options },
                    onFriend = { nav(Screen.Friend) }, onEditPickup = { nav(Screen.ChangePickup) }, pickup = pickup, friend = friend, near = me.position,
                    onSaved = { nav(Screen.Saved) }, onMap = { mapFor = "drop"; nav(Screen.MapPick) }, onAdd = { quickAddress(it.uppercase(java.util.Locale.ROOT)) },
                    savedHome = home?.address, savedWork = work?.address,
                )
            }
            Screen.Options -> {
                BackHandler { screen = Screen.Search }
                RideOptionsScreen(
                    destination = dest, returnTrip = isReturn, initial = vehicle, onBack = { screen = Screen.Search },
                    onContinue = { vehicle = it; screen = Screen.Confirm },
                    onLater = { vehicle = it; scheduleFrom = Screen.Options; screen = Screen.Schedule },
                    route = routeInfo, from = routePickup, to = destLL,
                )
            }
            Screen.Schedule -> {
                BackHandler { screen = scheduleFrom }
                ScheduleScreen(onBack = { screen = scheduleFrom }, onContinue = { label ->
                    scheduled = label
                    screen = if (scheduleFrom == Screen.Options) Screen.Confirm else Screen.Search
                })
            }
            Screen.Confirm -> {
                BackHandler { screen = Screen.Options }
                ConfirmScreen(
                    dest, vehicle, isReturn, whenLabel = scheduled, onBack = { screen = Screen.Options },
                    onBook = { if (scheduled != null) open("Scheduled ride details") else screen = Screen.Finding },
                    route = routeInfo, from = routePickup, to = destLL,
                )
            }
            Screen.Finding -> FindingDriverScreen(dest, vehicle, isReturn, onFound = { screen = Screen.Trip }, onCancel = { scheduled = null; screen = Screen.Home }, me = me.position)
            Screen.Trip -> {
                BackHandler { }
                TripScreen(
                    dest, vehicle, onCancelled = { scheduled = null; screen = Screen.Home }, onComplete = { rating = 0; open("Pay in cash") },
                    onCall = { open("In-app call") }, onMessage = { open("Chat with driver") },
                    onShare = { nav(Screen.ShareTrip) }, onSafety = { nav(Screen.Safety) },
                    pickup = me.position, drop = destLL ?: LatLng(me.position.latitude + 0.012, me.position.longitude + 0.01), route = routeInfo,
                )
            }
            Screen.Complete -> {
                BackHandler { }
                RideCompleteScreen(
                    dest, Vehicles[vehicle], Vehicles[vehicle].fareFor(isReturn), rating,
                    onReceipt = { open("Receipt PDF") }, onRate = { screen = Screen.Rate }, onReport = { nav(Screen.Report) },
                    onDone = { scheduled = null; rating = 0; screen = Screen.Home },
                )
            }
            Screen.Rate -> {
                BackHandler { screen = Screen.Complete }
                RateDriverScreen(onBack = { screen = Screen.Complete }, onSubmit = { rating = it; screen = Screen.Complete })
            }
            Screen.Report -> ReportProblemScreen(onBack = { back() }, onDone = { back() },
                onCompose = { subject, message ->
                    reportSubject = subject
                    reportMessage = message
                    nav(Screen.ReportDraft)
                })
            Screen.ReportDraft -> SupportWorkspaceScreen(SupportEntry.COMPLAINT, { back() },
                initialSubject = reportSubject, initialMessage = reportMessage)
            Screen.Profile -> {
                BackHandler { screen = Screen.Home }
                ProfileScreen(
                    name, shownPhone, themeMode, onThemeMode, onBack = { screen = Screen.Home }, onEdit = { screen = Screen.EditProfile },
                    onOpen = { go(it) }, onLogout = { phone = ""; profilePhoto = null; scope.launch { runCatching { PhoneAuthApi.signOut(context) } }; screen = Screen.Welcome },
                    photo = profilePhoto,
                )
            }
            Screen.EditProfile -> {
                BackHandler { screen = Screen.Profile }
                EditProfileScreen(
                    name, email, shownPhone, onBack = { screen = Screen.Profile },
                    onSave = { n, e, replacement, details ->
                        try {
                            val profile = PhoneAuthApi.saveProfile(context, n, e, replacement, details)
                            if (replacement != null) profilePhoto = loadProfilePhoto(profile.profilePhotoUrl) ?: replacement
                            name = profile.name
                            email = profile.email
                            profileComplete = profile.profileComplete
                            profileDetails = profile.details
                            screen = Screen.Profile
                        } catch (failure: Exception) {
                            if (failure is PhoneAuthException && failure.status == 401) screen = Screen.Welcome
                            throw failure
                        }
                    }, onChangePhone = { open("Change phone number") }, photo = profilePhoto, details = profileDetails,
                )
            }
            Screen.MyRides -> {
                BackHandler { screen = Screen.Home }
                MyRidesScreen(onBack = { screen = Screen.Home }, onBook = { screen = Screen.Search })
            }
            Screen.Payments -> {
                BackHandler { screen = Screen.Home }
                PaymentsScreen(onBack = { screen = Screen.Home })
            }
            Screen.Settings -> {
                BackHandler { screen = Screen.Home }
                SettingsScreen(themeMode, onThemeMode, onBack = { screen = Screen.Home }, onOpen = { go(it) })
            }
            Screen.Notifs -> {
                BackHandler { back() }
                NotificationsScreen(onBack = { back() })
            }
            Screen.Saved -> {
                BackHandler { back() }
                SavedAddressesScreen(
                    savedAddresses, onBack = { back() }, onEdit = ::openAddress,
                    onPick = if (backStack.lastOrNull() == Screen.Search) ::chooseAddress else null,
                    error = settingsErrorText.takeIf { settingsError }, onRetry = { scope.launch { syncSettings() } },
                    onRemove = { pendingRemove = it },

                )
            }
            Screen.Help -> {
                BackHandler { back() }
                HelpScreen(onBack = { back() }, onOpen = { t -> if (t == "Terms & Policies") nav(Screen.Legal) else if (t == "Contact support") nav(Screen.Support) else open(t) })
            }
            Screen.Legal -> {
                BackHandler { back() }
                LegalScreen(onBack = { back() }, onOpen = { doc = it; screen = Screen.Doc })
            }
            Screen.Doc -> {
                BackHandler { screen = Screen.Legal }
                DocScreen(doc, onBack = { screen = Screen.Legal })
            }
            Screen.Support -> {
                BackHandler { back() }
                ContactSupportScreen(
                    onBack = { back() }, onChat = { screen = Screen.Chat }, onReport = { open("Report an issue") },
                    onTickets = { open("Support tickets") }, onTripReport = { nav(Screen.Report) },
                )
            }
            Screen.ReportIssue -> {
                BackHandler { back() }
                SupportWorkspaceScreen(SupportEntry.COMPLAINT, { back() }, categories = listOf(
                    R.string.help_report_cat_trip, R.string.help_report_cat_payment,
                    R.string.help_report_cat_app, R.string.help_report_cat_other,
                ))
            }
            Screen.Chat -> {
                BackHandler { screen = Screen.Support }
                SupportWorkspaceScreen(SupportEntry.CHAT, { screen = Screen.Support })
            }
            Screen.Safety -> {
                BackHandler { back() }
                SafetyCenterScreen(contacts.size, onBack = { back() }, onShare = { nav(Screen.ShareTrip) }, onContacts = { screen = Screen.Contacts }, onReport = { open("Report a safety issue") })
            }
            Screen.Contacts -> {
                BackHandler { back() }
                TrustedContactsScreen(contacts, onAdd = {
                    val saved = RiderSettingsApi.saveContact(context, it)
                    val index = contacts.indexOfFirst { item -> item.id == saved.id }
                    if (index < 0) contacts.add(saved) else contacts[index] = saved
                }, onRemove = {
                    RiderSettingsApi.deleteContact(context, requireNotNull(it.id))
                    contacts.remove(it)
                }, onBack = { back() }, error = settingsErrorText.takeIf { settingsError },
                    onRetry = { scope.launch { syncSettings() } })
            }
            Screen.MapPick -> {
                BackHandler { back() }
                if (mapFor == "pickup") MapPickerScreen(textSetPickup, textPickupConfirm, onBack = { back() }, onConfirm = { addr, point -> pickup = addr; pickupPoint = point; back() }, onLocationOff = { locationRequested = true })
                else MapPickerScreen(textMapSetDrop, textMapSetDrop, onBack = { back() }, onConfirm = { addr, ll -> pickDest(addr, null, ll); isReturn = false; screen = Screen.Options }, onLocationOff = { locationRequested = true })
            }
            Screen.ShareTrip -> {
                BackHandler { back() }
                ShareTripScreen(contacts, onBack = { back() }, onManage = { nav(Screen.Contacts) })
            }
            Screen.ChangePickup -> {
                BackHandler { back() }
                val places = buildList {
                    if (me.real) add("Current location" to "%.6f, %.6f".format(java.util.Locale.ROOT, me.position.latitude, me.position.longitude))
                    savedAddresses.forEach { item ->
                        val label = when (item.kind) { "HOME" -> "Home"; "WORK" -> "Work"; else -> item.label ?: item.address.substringBefore(',') }
                        add(label to item.address)
                    }
                }
                ChangePickupScreen(pickup, places, onBack = { back() }, onConfirm = { address ->
                    pickup = address
                    val saved = savedAddresses.firstOrNull { it.address == address }
                    pickupPoint = saved?.point ?: me.position.takeIf { me.real && address == places.firstOrNull { it.first == "Current location" }?.second }
                    if (pickupPoint == null && saved != null) scope.launch {
                        val found = SifricApi.geocode(context, address)
                        if (pickup == address) pickupPoint = found
                    }
                    back()
                }, onMap = { mapFor = "pickup"; screen = Screen.MapPick }, allowCurrentLocation = allowCurrentLocation)
            }
            Screen.Friend -> {
                BackHandler { back() }
                FriendScreen(onBack = { back() }, onDone = { friend = it; back() })
            }
            Screen.AddAddr -> {
                BackHandler { back() }
                AddAddressScreen(addLabel, onBack = { back() }, onSave = { kind, label, address, point ->
                    val saved = RiderSettingsApi.saveAddress(context, editAddress?.id, kind, label, address, point)
                    savedAddresses.removeAll { it.id == saved.id }
                    savedAddresses.add(0, saved)
                    back()
                }, allowCurrentLocation = allowCurrentLocation, near = me.position.takeIf { me.real }, existing = editAddress,
                    onRemove = editAddress?.let { item -> suspend {
                        RiderSettingsApi.deleteAddress(context, item.id)
                        savedAddresses.removeAll { it.id == item.id }
                        back()
                    } })

            }
            Screen.Stub -> if (MoreRoutes.handles(stub)) {
                key(stub) { MoreHost(stub, MoreCtx(name, shownPhone, dest.ifBlank { textHelixGardenPlaza }, pickup, vehicle, scheduled, profileDetails.city,
                    { city -> profileDetails = profileDetails.copy(city = city) }), moreNav) }
            } else {
                BackHandler { screen = stubBack }
                val c = RideTheme.colors
                Box(Modifier.fillMaxSize().background(c.bg), contentAlignment = Alignment.Center) {
                    Text(textValueScreenGoesHere.format(localizedLabel(stub)), color = c.muted, fontSize = 14.sp)
                }
            }
        }
    }
    if (!online && screen != Screen.Splash && screen != Screen.Intro) {
        BackHandler { }
        NoInternetScreen(onRetry = { netKey++ }, onOpenRides = { })
    } else if (showLocationOff) {
        BackHandler(onBack = dismissLocationOff)
        RiderLocationOffScreen(onManual = dismissLocationOff)
    }
    }
}
