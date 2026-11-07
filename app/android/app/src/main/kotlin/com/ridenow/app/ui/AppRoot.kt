package com.ridenow.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.ridenow.app.ui.legal.DocScreen
import com.ridenow.app.ui.legal.LegalScreen
import com.ridenow.app.ui.menu.MenuDrawer
import com.ridenow.app.ui.notifications.NotificationsScreen
import com.ridenow.app.ui.booking.MapPickerScreen
import com.ridenow.app.ui.safety.Contact
import com.ridenow.app.ui.safety.SafetyCenterScreen
import com.ridenow.app.ui.safety.TrustedContactsScreen
import com.ridenow.app.ui.saved.SavedAddressesScreen
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
    Complete, Rate, Report, Profile, EditProfile, MyRides, Payments, Settings, Notifs, Saved, Help, Legal, Doc, Support, Chat, Safety, Contacts, MapPick, Stub,
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
    var returnTo by rememberSaveable { mutableStateOf(Screen.Home) }
    var doc by rememberSaveable { mutableStateOf("") }
    val contacts = remember { mutableStateListOf(Contact("Ana", "+15550101"), Contact("Ben", "+15550102")) }
    val nav = { to: Screen -> returnTo = screen; screen = to }
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

    Crossfade(targetState = screen, animationSpec = tween(400), label = "root") { current ->
        when (current) {
            Screen.Splash -> SplashScreen(onFinished = { screen = Screen.Welcome })
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
                NotificationPermissionScreen(onDone = { screen = Screen.Home })
            }
            Screen.Home -> Box(Modifier.fillMaxSize()) {
                HomeScreen(
                    onMenu = { drawer = true }, onNotifications = { nav(Screen.Notifs) },
                    onSearch = { screen = Screen.Search }, onLater = { scheduleFrom = Screen.Home; screen = Screen.Schedule },
                    onEditPickup = { open("Change pickup") }, onSaved = { nav(Screen.Saved) },
                    onRecent = { dest = it; isReturn = false; screen = Screen.Options },
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
                    onPlace = { place, ret -> dest = place.name; isReturn = ret; screen = Screen.Options },
                    onFriend = { open("Book for a friend") }, onEditPickup = { open("Change pickup") },
                    onSaved = { nav(Screen.Saved) }, onMap = { nav(Screen.MapPick) }, onAdd = { open("Add $it address") },
                )
            }
            Screen.Options -> {
                BackHandler { screen = Screen.Search }
                RideOptionsScreen(
                    destination = dest, returnTrip = isReturn, initial = vehicle, onBack = { screen = Screen.Search },
                    onContinue = { vehicle = it; screen = Screen.Confirm },
                    onLater = { vehicle = it; scheduleFrom = Screen.Options; screen = Screen.Schedule },
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
                )
            }
            Screen.Finding -> FindingDriverScreen(dest, vehicle, isReturn, onFound = { screen = Screen.Trip }, onCancel = { scheduled = null; screen = Screen.Home })
            Screen.Trip -> {
                BackHandler { }
                TripScreen(
                    dest, vehicle, onCancelled = { scheduled = null; screen = Screen.Home }, onComplete = { rating = 0; screen = Screen.Complete },
                    onShare = { open("Share live trip") }, onSafety = { nav(Screen.Safety) },
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
                BackHandler { screen = returnTo }
                NotificationsScreen(onBack = { screen = returnTo })
            }
            Screen.Saved -> {
                BackHandler { screen = returnTo }
                SavedAddressesScreen(onBack = { screen = returnTo }, onEdit = { open(it) })
            }
            Screen.Help -> {
                BackHandler { screen = returnTo }
                HelpScreen(onBack = { screen = returnTo }, onOpen = { t -> if (t == "Terms & Policies") nav(Screen.Legal) else if (t == "Contact support") nav(Screen.Support) else open(t) })
            }
            Screen.Legal -> {
                BackHandler { screen = returnTo }
                LegalScreen(onBack = { screen = returnTo }, onOpen = { doc = it; screen = Screen.Doc })
            }
            Screen.Doc -> {
                BackHandler { screen = Screen.Legal }
                DocScreen(doc, onBack = { screen = Screen.Legal })
            }
            Screen.Support -> {
                BackHandler { screen = returnTo }
                ContactSupportScreen(onBack = { screen = returnTo }, onChat = { screen = Screen.Chat }, onReport = { open("Report an issue") })
            }
            Screen.Chat -> {
                BackHandler { screen = Screen.Support }
                SupportChatScreen(onBack = { screen = Screen.Support })
            }
            Screen.Safety -> {
                BackHandler { screen = returnTo }
                SafetyCenterScreen(contacts.size, onBack = { screen = returnTo }, onShare = { open("Share live trip") }, onContacts = { screen = Screen.Contacts }, onReport = { open("Report a safety issue") })
            }
            Screen.Contacts -> {
                BackHandler { screen = if (returnTo == Screen.Safety) Screen.Safety else returnTo }
                TrustedContactsScreen(contacts, onAdd = { contacts.add(it) }, onRemove = { contacts.remove(it) }, onBack = { screen = if (returnTo == Screen.Safety) Screen.Safety else returnTo })
            }
            Screen.MapPick -> {
                BackHandler { screen = returnTo }
                MapPickerScreen("Set drop location", "Set drop location", onBack = { screen = returnTo }, onConfirm = { dest = it; isReturn = false; screen = Screen.Options })
            }
            Screen.Stub -> {
                BackHandler { screen = stubBack }
                val c = RideTheme.colors
                Box(Modifier.fillMaxSize().background(c.bg), contentAlignment = Alignment.Center) {
                    Text("$stub screen goes here", color = c.muted, fontSize = 14.sp)
                }
            }
        }
    }
}
