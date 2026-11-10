package com.ridenow.app.ui

import androidx.activity.compose.BackHandler
import com.ridenow.app.ui.more.*
import com.ridenow.app.ui.theme.AccentPrefs
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
import com.google.android.gms.maps.model.LatLng
import com.ridenow.app.ui.map.GoogleApi
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
import com.ridenow.app.ui.auth.DetailsScreen
import com.ridenow.app.ui.auth.OtpScreen
import com.ridenow.app.ui.auth.PhoneScreen
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
import com.ridenow.app.ui.search.ChangePickupScreen
import com.ridenow.app.ui.search.FriendScreen
import com.ridenow.app.ui.share.ShareTripScreen
import com.ridenow.app.ui.support.ContactSupportScreen
import com.ridenow.app.ui.support.SupportChatScreen
import androidx.compose.runtime.mutableStateListOf
import com.ridenow.app.ui.payments.PaymentsScreen
import com.ridenow.app.ui.permissions.LocationPermissionScreen
import com.ridenow.app.ui.permissions.NotificationPermissionScreen
import com.ridenow.app.ui.post.RateDriverScreen
import com.ridenow.app.ui.post.ReportProblemScreen
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
    Complete, Rate, Report, Profile, EditProfile, MyRides, Payments, Settings, Intro, Loading, Expired, ServerError, Notifs, Saved, Help, Legal, Doc, Support, Chat, Safety, Contacts, MapPick, ShareTrip, ChangePickup, Friend, AddAddr, Stub,
}

@Composable
fun AppRoot(themeMode: ThemeMode, onThemeMode: (ThemeMode) -> Unit) {
    var screen by rememberSaveable { mutableStateOf(Screen.Splash) }
    var phone by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("Alex Morgan") }
    var email by rememberSaveable { mutableStateOf("") }
    var stub by rememberSaveable { mutableStateOf("") }
    var stubBack by rememberSaveable { mutableStateOf(Screen.Home) }
    var dest by rememberSaveable { mutableStateOf("") }
    var isReturn by rememberSaveable { mutableStateOf(false) }
    var vehicle by rememberSaveable { mutableStateOf(1) }
    var rating by rememberSaveable { mutableStateOf(0) }
    var scheduled by rememberSaveable { mutableStateOf<String?>(null) }
    var scheduleFrom by rememberSaveable { mutableStateOf(Screen.Home) }
    var drawer by rememberSaveable { mutableStateOf(false) }
    var screenHistory by remember { mutableStateOf(emptyList<Screen>()) }
    var doc by rememberSaveable { mutableStateOf("") }
    val contacts = remember { mutableStateListOf(Contact("Ana", "+15550101"), Contact("Ben", "+15550102")) }
    val scope = rememberCoroutineScope()
    val me = rememberUserLocation()
    var destLat by rememberSaveable { mutableStateOf(0.0) }
    var destLng by rememberSaveable { mutableStateOf(0.0) }
    val destLL = if (destLat != 0.0 || destLng != 0.0) LatLng(destLat, destLng) else null
    var routeInfo by remember { mutableStateOf<RouteInfo?>(null) }
    LaunchedEffect(destLat, destLng, me.position) { routeInfo = null; destLL?.let { routeInfo = GoogleApi.route(me.position, it) } }
    val pickDest = { name: String, placeId: String?, ll: LatLng? ->
        dest = name; destLat = 0.0; destLng = 0.0
        scope.launch {
            val p = ll ?: (placeId?.let { GoogleApi.placeLatLng(it) } ?: GoogleApi.geocode(name))
            if (p != null) { destLat = p.latitude; destLng = p.longitude }
        }
        Unit
    }
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ridenow", 0) }
    var netKey by remember { mutableIntStateOf(0) }
    val online = rememberOnline(netKey)
    var pickup by rememberSaveable { mutableStateOf("128 Meridian Walk") }
    var home by rememberSaveable { mutableStateOf<String?>("128 Meridian Walk, Sector 7") }
    var work by rememberSaveable { mutableStateOf<String?>(null) }
    var friend by rememberSaveable { mutableStateOf<String?>(null) }
    var mapFor by rememberSaveable { mutableStateOf("drop") }
    var addLabel by rememberSaveable { mutableStateOf("Home") }
    val others = remember { mutableStateListOf("Nova Terminal, Gate 4" to "Sector 12, Orbit Ring", "Helix Garden Plaza" to "Sector 9, Garden Loop", "Aurora Medical Centre" to "Sector 3, Skyline Way") }
    val nav = { to: Screen ->
        if (screen != to) {
            screenHistory = screenHistory + screen
            screen = to
        }
        Unit
    }
    val goBack = {
        val previous = screenHistory.lastOrNull()
        if (previous != null) {
            screenHistory = screenHistory.dropLast(1)
            screen = previous
        } else screen = Screen.Home
        Unit
    }
    val shownPhone = phone.ifBlank { "+1 555 012 3456" }
    val open = { label: String -> stubBack = screen; stub = label; screen = Screen.Stub }
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

    LaunchedEffect(Unit) { AccentPrefs.name = prefs.getString("accent", "Default") ?: "Default" }
    val moreNav = MoreNav(
        back = { screen = stubBack }, exit = { screen = stubBack }, open = { open(it) }, home = { screen = Screen.Home }, options = { screen = Screen.Options },
        schedule = { scheduleFrom = Screen.Options; screen = Screen.Schedule }, finding = { screen = Screen.Finding }, trip = { screen = Screen.Trip },
        complete = { rating = 0; screen = Screen.Complete }, report = { screen = Screen.Report }, support = { nav(Screen.Support) }, safety = { nav(Screen.Safety) },
        setPhone = { phone = it.removePrefix("+1 ") }, setVehicle = { vehicle = it }, setScheduled = { scheduled = it }, confirm = { screen = Screen.Confirm },
        changeDrop = { pickDest(it, null, null) }, cancelScheduled = { scheduled = null; screen = Screen.Home }, signedOut = { screen = Screen.Welcome },
    )
    Box(Modifier.fillMaxSize()) {
    Crossfade(targetState = screen, animationSpec = tween(400), label = "root") { current ->
        when (current) {
            Screen.Splash -> SplashScreen(onFinished = { screen = if (prefs.getBoolean("intro_seen", false)) Screen.Welcome else Screen.Intro })
            Screen.Intro -> {
                BackHandler { screen = Screen.Welcome }
                IntroScreen(onDone = { prefs.edit().putBoolean("intro_seen", true).apply(); screen = Screen.Welcome })
            }
            Screen.Loading -> {
                BackHandler { screen = Screen.Home }
                LoadingScreen(onDone = { screen = Screen.Home })
            }
            Screen.Expired -> {
                BackHandler { screen = Screen.Phone }
                SessionExpiredScreen(name, shownPhone, onSendCode = { screen = Screen.Phone }, onOther = { phone = ""; screen = Screen.Phone })
            }
            Screen.ServerError -> {
                BackHandler { screen = Screen.Home }
                ServerErrorScreen("8F3K-2Q-500", onRetry = { screen = Screen.Home }, onContact = { nav(Screen.Support) })
            }
            Screen.Welcome -> WelcomeScreen(onGetStarted = { screen = Screen.Phone }, onHaveAccount = { screen = Screen.Phone }, onHelp = { })
            Screen.Phone -> {
                BackHandler { screen = Screen.Welcome }
                PhoneScreen(onBack = { screen = Screen.Welcome }, onCodeSent = { phone = it; screen = Screen.Otp })
            }
            Screen.Otp -> {
                BackHandler { screen = Screen.Phone }
                OtpScreen(phone = shownPhone, onBack = { screen = Screen.Phone }, onVerified = { screen = Screen.Details })
            }
            Screen.Details -> {
                BackHandler { screen = Screen.Otp }
                DetailsScreen(onBack = { screen = Screen.Otp }, onDone = { n, e -> name = n; email = e; screen = Screen.Location })
            }
            Screen.Location -> {
                BackHandler { screen = Screen.Details }
                LocationPermissionScreen(onDone = { screen = Screen.Notifications })
            }
            Screen.Notifications -> {
                BackHandler { screen = Screen.Location }
                NotificationPermissionScreen(onDone = { screen = Screen.Loading })
            }
            Screen.Home -> Box(Modifier.fillMaxSize()) {
                HomeScreen(
                    onMenu = { drawer = true }, onNotifications = { nav(Screen.Notifs) },
                    onSearch = { screen = Screen.Search }, onLater = { scheduleFrom = Screen.Home; screen = Screen.Schedule },
                    onEditPickup = { nav(Screen.ChangePickup) }, onSaved = { nav(Screen.Saved) }, pickup = pickup,
                    onRecent = { pickDest(it, null, null); isReturn = false; screen = Screen.Options },
                )
                MenuDrawer(
                    open = drawer, name = name, phone = shownPhone, onClose = { drawer = false },
                    onProfile = { go("Profile") }, onItem = { go(it) },
                    onSignOut = { drawer = false; phone = ""; screen = Screen.Welcome },
                )
            }
            Screen.Search -> {
                BackHandler { scheduled = null; screen = Screen.Home }
                SearchScreen(
                    onBack = { scheduled = null; screen = Screen.Home }, scheduled = scheduled,
                    onPlace = { place, ret -> pickDest(place.name, place.placeId, null); isReturn = ret; screen = Screen.Options },
                    onFriend = { nav(Screen.Friend) }, onEditPickup = { nav(Screen.ChangePickup) }, pickup = pickup, friend = friend, near = me.position,
                    onSaved = { nav(Screen.Saved) }, onMap = { mapFor = "drop"; nav(Screen.MapPick) }, onAdd = { open("Add $it address") },
                )
            }
            Screen.Options -> {
                BackHandler { screen = Screen.Search }
                RideOptionsScreen(
                    destination = dest, returnTrip = isReturn, initial = vehicle, onBack = { screen = Screen.Search },
                    onContinue = { vehicle = it; screen = Screen.Confirm },
                    onLater = { vehicle = it; scheduleFrom = Screen.Options; screen = Screen.Schedule },
                    route = routeInfo, from = me.position, to = destLL,
                )
            }
            Screen.Schedule -> {
                BackHandler { screen = scheduleFrom }
                ScheduleScreen(onBack = { screen = scheduleFrom }, passengerName = name, onContinue = { label ->
                    scheduled = label
                    screen = if (scheduleFrom == Screen.Options) Screen.Confirm else Screen.Search
                })
            }
            Screen.Confirm -> {
                BackHandler { screen = Screen.Options }
                ConfirmScreen(
                    dest, vehicle, isReturn, whenLabel = scheduled, onBack = { screen = Screen.Options },
                    onBook = { if (scheduled != null) open("Scheduled ride details") else screen = Screen.Finding },
                    route = routeInfo, from = me.position, to = destLL,
                )
            }
            Screen.Finding -> {
                BackHandler { scheduled = null; screen = Screen.Home }
                FindingDriverScreen(dest, vehicle, isReturn, onFound = { screen = Screen.Trip }, onCancel = { scheduled = null; screen = Screen.Home }, me = me.position)
            }
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
                    onReceipt = { open("Receipt PDF") }, onRate = { screen = Screen.Rate }, onReport = { screen = Screen.Report },
                    onDone = { scheduled = null; rating = 0; screen = Screen.Home },
                )
            }
            Screen.Rate -> {
                BackHandler { screen = Screen.Complete }
                RateDriverScreen(onBack = { screen = Screen.Complete }, onSubmit = { rating = it; screen = Screen.Complete })
            }
            Screen.Report -> ReportProblemScreen(onBack = { screen = Screen.Complete }, onDone = { screen = Screen.Complete })
            Screen.Profile -> {
                BackHandler { screen = Screen.Home }
                ProfileScreen(
                    name, shownPhone, themeMode, onThemeMode, onBack = { screen = Screen.Home }, onEdit = { screen = Screen.EditProfile },
                    onOpen = { go(it) }, onLogout = { phone = ""; screen = Screen.Welcome },
                )
            }
            Screen.EditProfile -> {
                BackHandler { screen = Screen.Profile }
                EditProfileScreen(
                    name, email, shownPhone, onBack = { screen = Screen.Profile },
                    onSave = { n, e -> name = n; email = e; screen = Screen.Profile }, onChangePhone = { open("Change phone number") },
                )
            }
            Screen.MyRides -> {
                BackHandler { screen = Screen.Home }
                MyRidesScreen(onBack = { screen = Screen.Home }, onBook = { screen = Screen.Search }, onOpen = { open("Ride details") })
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
                BackHandler { goBack() }
                NotificationsScreen(onBack = { goBack() })
            }
            Screen.Saved -> {
                BackHandler { goBack() }
                SavedAddressesScreen(
                    home, work, others, onBack = { goBack() },
                    onEdit = { l -> when (l) { "Home address" -> { addLabel = "Home"; nav(Screen.AddAddr) }; "Work address" -> { addLabel = "Work"; nav(Screen.AddAddr) }; "Add address" -> { addLabel = "Other"; nav(Screen.AddAddr) }; else -> open(l) }; Unit },
                )
            }
            Screen.Help -> {
                BackHandler { goBack() }
                HelpScreen(onBack = { goBack() }, onOpen = { t -> if (t == "Terms & Policies") nav(Screen.Legal) else if (t == "Contact support") nav(Screen.Support) else open(t) })
            }
            Screen.Legal -> {
                BackHandler { goBack() }
                LegalScreen(onBack = { goBack() }, onOpen = { doc = it; screen = Screen.Doc })
            }
            Screen.Doc -> {
                BackHandler { screen = Screen.Legal }
                DocScreen(doc, onBack = { screen = Screen.Legal })
            }
            Screen.Support -> {
                BackHandler { goBack() }
                ContactSupportScreen(onBack = { goBack() }, onChat = { screen = Screen.Chat }, onReport = { open("Report an issue") })
            }
            Screen.Chat -> {
                BackHandler { screen = Screen.Support }
                SupportChatScreen(onBack = { screen = Screen.Support })
            }
            Screen.Safety -> {
                BackHandler { goBack() }
                SafetyCenterScreen(contacts.size, onBack = { goBack() }, onShare = { nav(Screen.ShareTrip) }, onContacts = { nav(Screen.Contacts) }, onReport = { open("Report a safety issue") })
            }
            Screen.Contacts -> {
                BackHandler { goBack() }
                TrustedContactsScreen(contacts, onAdd = { contacts.add(it) }, onRemove = { contacts.remove(it) }, onBack = { goBack() })
            }
            Screen.MapPick -> {
                BackHandler { goBack() }
                if (mapFor == "pickup") MapPickerScreen("Set pickup", "Confirm pickup", onBack = { goBack() }, onConfirm = { addr, _ -> pickup = addr; goBack() })
                else MapPickerScreen("Set drop location", "Set drop location", onBack = { goBack() }, onConfirm = { addr, ll -> pickDest(addr, null, ll); isReturn = false; goBack(); screen = Screen.Options })
            }
            Screen.ShareTrip -> {
                BackHandler { goBack() }
                ShareTripScreen(contacts, onBack = { goBack() }, onManage = { nav(Screen.Contacts) })
            }
            Screen.ChangePickup -> {
                BackHandler { goBack() }
                val places = buildList {
                    add("Current location" to "128 Meridian Walk"); home?.let { add("Home" to it) }; work?.let { add("Work" to it) }; others.forEach { add(it.first to it.second) }
                }
                ChangePickupScreen(pickup, places, onBack = { goBack() }, onConfirm = { pickup = it; goBack() }, onMap = { mapFor = "pickup"; nav(Screen.MapPick) })
            }
            Screen.Friend -> {
                BackHandler { goBack() }
                FriendScreen(onBack = { goBack() }, onDone = { friend = it; goBack() })
            }
            Screen.AddAddr -> {
                BackHandler { goBack() }
                AddAddressScreen(addLabel, onBack = { goBack() }, onSave = { l, a ->
                    when (l) { "Home" -> home = a; "Work" -> work = a; else -> others.add(0, a.substringBefore(",") to a.substringAfter(", ", "Saved place")) }
                    goBack()
                })
            }
            Screen.Stub -> if (MoreRoutes.handles(stub)) {
                key(stub) { MoreHost(stub, MoreCtx(name, shownPhone, dest.ifBlank { "Helix Garden Plaza" }, pickup, vehicle, scheduled), moreNav) }
            } else {
                BackHandler { screen = stubBack }
                val c = RideTheme.colors
                Box(Modifier.fillMaxSize().background(c.bg), contentAlignment = Alignment.Center) {
                    Text("$stub screen goes here", color = c.muted, fontSize = 14.sp)
                }
            }
        }
    }
    if (!online && screen != Screen.Splash && screen != Screen.Intro) {
        BackHandler { }
        NoInternetScreen(onRetry = { netKey++ }, onOpenRides = { })
    }
    }
}
